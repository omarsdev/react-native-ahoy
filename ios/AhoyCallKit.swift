import Foundation
import CallKit
import AVFoundation
import os

// Single logger for the whole module. Messages are marked .public so they show
// in full (not "<private>") in idevicesyslog / Console on a real device:
//   idevicesyslog -p AhoyExample -m "[Ahoy]"
// Module-internal so all Ahoy Swift files share one logger.
let ahoyLog = Logger(subsystem: "dev.omars.ahoy", category: "ahoy")

// The Obj-C++ TurboModule (Ahoy.mm) implements this and routes each event to the
// generated typed emitter (emitOnAnswerCall, emitOnToggleMute, …). Keeping the
// delegate generic means the Swift core never imports the codegen header.
@objc public protocol AhoyEventDelegate: AnyObject {
  @objc func sendEvent(_ name: String, body: [String: Any])
}

// T2: iOS CallKit core. One global CXProvider + one CXCallController. All
// CallKit logic is Swift, @objc-exposed, and driven from Ahoy.mm. Presentation
// and lifecycle only — no media. Consumers start/stop audio in
// onDidActivateAudioSession / onDidDeactivateAudioSession.
//
// All lifecycle steps are logged with the "[Ahoy]" prefix. To capture on a
// device: Xcode console, or Console.app filtered by "[Ahoy]", or
//   xcrun devicectl / `idb log` — and on the simulator:
//   xcrun simctl spawn booted log stream --predicate 'eventMessage CONTAINS "[Ahoy]"'
@objc public class AhoyCallKit: NSObject {

  // Shared instance: the CXProvider must exist at app launch (for a VoIP-push
  // cold start, before the TurboModule/JS exist), so it lives here, not per
  // module. The TurboModule and the VoIP push manager both use this.
  @objc public static let shared = AhoyCallKit()

  // Set by the TurboModule once JS is up. On cold start it's nil, so push-time
  // events are buffered. NOTE: don't flush here — the module's generated event
  // emitter isn't wired until RN calls setEventEmitterCallback (later than init);
  // emitting before that calls an empty std::function and crashes. Ahoy.mm calls
  // flushBufferedEvents() from setEventEmitterCallback instead.
  @objc public weak var eventDelegate: AhoyEventDelegate?

  private let provider: CXProvider
  private let callController = CXCallController()
  // True between requesting an outgoing call and the request completing — closes
  // the rapid-tap race window for the single-outgoing protector.
  private var placingOutgoing = false
  // Events emitted before JS attached (cold-start VoIP push). Replayed on attach.
  private var bufferedEvents: [(String, [String: Any])] = []

  // Emit to JS, or buffer until the bridge attaches (cold-start replay).
  func emitOrBuffer(_ name: String, _ body: [String: Any]) {
    if let delegate = eventDelegate {
      delegate.sendEvent(name, body: body)
    } else {
      log("buffering \(name) until JS attaches")
      bufferedEvents.append((name, body))
    }
  }

  // Called from Ahoy.mm once the TurboModule event emitter is connected.
  @objc public func flushBufferedEvents() {
    guard eventDelegate != nil, !bufferedEvents.isEmpty else { return }
    log("flushing \(bufferedEvents.count) buffered event(s) to JS")
    let pending = bufferedEvents
    bufferedEvents.removeAll()
    pending.forEach { eventDelegate?.sendEvent($0.0, body: $0.1) }
  }

  private func log(_ message: String) {
    ahoyLog.notice("[Ahoy] \(message, privacy: .public)")
  }

  @objc public override init() {
    let config = CXProviderConfiguration() // iOS 14+: name comes from the app display name
    config.supportedHandleTypes = [.generic, .phoneNumber, .emailAddress]
    config.maximumCallsPerCallGroup = 1
    // Allow two independent calls so an incoming call presents as call-waiting
    // over an active call (decline the incoming → the active call survives).
    // The T2 runbook used 1 (single-call happy path); 2 enables call-waiting.
    config.maximumCallGroups = 2
    config.supportsVideo = false
    config.includesCallsInRecents = true
    self.provider = CXProvider(configuration: config)
    super.init()
    self.provider.setDelegate(self, queue: nil) // nil == private serial queue
    self.callController.callObserver.setDelegate(self, queue: nil)
    log("init: CXProvider configured (handleTypes=generic/phone/email, video=false)")
    logCalls("init")
    // Zombie cleanup: CallKit state lives in the OS daemon and survives a JS
    // reload, so calls left un-ended by a previous run linger as "active" in the
    // system Call UI. End any pre-existing calls on startup.
    let existing = callController.callObserver.calls.filter { !$0.hasEnded }
    if !existing.isEmpty {
      log("init: ending \(existing.count) leftover call(s) from a previous run")
      existing.forEach { request(CXEndCallAction(call: $0.uuid)) }
    }
  }

  // Logs what CallKit itself believes is active (vs. what JS thinks).
  private func logCalls(_ context: String) {
    let calls = callController.callObserver.calls
    let desc = calls
      .map { "\($0.uuid.uuidString) outgoing=\($0.isOutgoing) connected=\($0.hasConnected) ended=\($0.hasEnded)" }
      .joined(separator: " | ")
    log("\(context): CallKit has \(calls.count) call(s) [\(desc)]")
  }

  // MARK: - Outgoing

  // Returns false (→ JS rejects with "ahoy_busy") if a call already exists or one
  // is being placed. Mirrors the Android native single-outgoing protector.
  @objc public func startCall(_ uuid: String, handle: String, hasVideo: Bool) -> Bool {
    let hasCall = callController.callObserver.calls.contains { !$0.hasEnded }
    if placingOutgoing || hasCall {
      log("startCall BLOCKED: already in a call / placing uuid=\(uuid)")
      return false
    }
    guard let id = UUID(uuidString: uuid) else { log("startCall: bad uuid"); return false }
    log("startCall <- JS  uuid=\(uuid) handle=\(handle) hasVideo=\(hasVideo)")
    placingOutgoing = true // closes the race before the call reaches the observer
    let cxHandle = CXHandle(type: .generic, value: handle)
    let action = CXStartCallAction(call: id, handle: cxHandle)
    action.isVideo = hasVideo
    callController.request(CXTransaction(action: action)) { [weak self] error in
      self?.placingOutgoing = false
      if let error = error { self?.log("startCall request error: \(error.localizedDescription)") }
    }
    return true
  }

  // Call once your signaling reports the remote side answered.
  @objc public func reportConnectedOutgoingCall(_ uuid: String) {
    log("reportConnectedOutgoingCall <- JS  uuid=\(uuid)")
    guard let id = UUID(uuidString: uuid) else { log("reportConnectedOutgoingCall: bad uuid"); return }
    provider.reportOutgoingCall(with: id, connectedAt: nil)
  }

  // MARK: - Incoming (JS-driven in T2; PushKit wakeup is a later ticket)

  @objc public func displayIncomingCall(_ uuid: String, handle: String,
                                        localizedCallerName: String, hasVideo: Bool) {
    log("displayIncomingCall <- JS  uuid=\(uuid) handle=\(handle) caller=\(localizedCallerName) hasVideo=\(hasVideo)")
    guard let id = UUID(uuidString: uuid) else { log("displayIncomingCall: bad uuid"); return }
    let update = CXCallUpdate()
    update.remoteHandle = CXHandle(type: .generic, value: handle)
    update.localizedCallerName = localizedCallerName.isEmpty ? nil : localizedCallerName
    update.hasVideo = hasVideo
    update.supportsHolding = true // defaults false → hold button is dead without this
    update.supportsDTMF = false
    update.supportsGrouping = false
    provider.reportNewIncomingCall(with: id, update: update) { [weak self] error in
      if let error = error as NSError? {
        // Decode CXErrorCodeIncomingCallError so the reason is obvious in logs.
        let reason: String
        switch error.code {
        case CXErrorCodeIncomingCallError.unentitled.rawValue:
          reason = "unentitled — missing 'voip' UIBackgroundMode / Push Notifications capability (wire it in Xcode → Signing & Capabilities)"
        case CXErrorCodeIncomingCallError.callUUIDAlreadyExists.rawValue:
          reason = "callUUIDAlreadyExists"
        case CXErrorCodeIncomingCallError.filteredByDoNotDisturb.rawValue:
          reason = "filteredByDoNotDisturb — turn OFF Focus/Do Not Disturb"
        case CXErrorCodeIncomingCallError.filteredByBlockList.rawValue:
          reason = "filteredByBlockList"
        default:
          reason = "code=\(error.code) \(error.localizedDescription)"
        }
        self?.log("reportNewIncomingCall FAILED: \(reason)  [domain=\(error.domain)]")
      } else {
        self?.log("reportNewIncomingCall OK  uuid=\(uuid) (CallKit UI should present)")
      }
    }
  }

  // T4: report an incoming call from a VoIP push. MUST run synchronously inside
  // the PushKit delegate. Emits onDisplayIncomingCall (fromPushKit=true), buffered
  // if JS isn't attached yet (cold start). `completion` fires after CallKit's.
  @objc public func reportPushIncomingCall(_ uuid: String, handle: String,
                                           callerName: String, hasVideo: Bool,
                                           completion: @escaping (Error?) -> Void) {
    log("reportPushIncomingCall (PushKit)  uuid=\(uuid) handle=\(handle) caller=\(callerName)")
    guard let id = UUID(uuidString: uuid) else {
      completion(NSError(domain: "Ahoy", code: -1)); return
    }
    let update = CXCallUpdate()
    update.remoteHandle = CXHandle(type: .generic, value: handle)
    update.localizedCallerName = callerName.isEmpty ? nil : callerName
    update.hasVideo = hasVideo
    update.supportsHolding = true
    provider.reportNewIncomingCall(with: id, update: update) { [weak self] error in
      if let error = error {
        self?.log("reportPushIncomingCall FAILED: \(error.localizedDescription)")
      } else {
        self?.emitOrBuffer("onDisplayIncomingCall",
                           ["uuid": uuid, "handle": handle, "fromPushKit": true])
      }
      completion(error)
    }
  }

  // MARK: - Local actions from JS (go through transactions)

  @objc public func answerCall(_ uuid: String) {
    log("answerCall <- JS  uuid=\(uuid)")
    guard let id = UUID(uuidString: uuid) else { log("answerCall: bad uuid"); return }
    request(CXAnswerCallAction(call: id))
  }

  @objc public func endCall(_ uuid: String) {
    log("endCall <- JS  uuid=\(uuid)")
    logCalls("endCall")
    guard let id = UUID(uuidString: uuid) else { log("endCall: bad uuid"); return }
    request(CXEndCallAction(call: id))
  }

  @objc public func endAllCalls() {
    let calls = callController.callObserver.calls
    log("endAllCalls <- JS  CallKit count=\(calls.count)")
    for call in calls {
      request(CXEndCallAction(call: call.uuid))
    }
  }

  @objc public func setMuted(_ uuid: String, muted: Bool) {
    log("setMuted <- JS  uuid=\(uuid) muted=\(muted)")
    guard let id = UUID(uuidString: uuid) else { log("setMuted: bad uuid"); return }
    request(CXSetMutedCallAction(call: id, muted: muted))
  }

  @objc public func setHeld(_ uuid: String, onHold: Bool) {
    log("setHeld <- JS  uuid=\(uuid) onHold=\(onHold)")
    guard let id = UUID(uuidString: uuid) else { log("setHeld: bad uuid"); return }
    request(CXSetHeldCallAction(call: id, onHold: onHold))
  }

  // MARK: - Remote / network-driven UI updates (NOT transactions)

  // Remote hangup path. CXCallEndedReason raw values: failed=1, remoteEnded=2,
  // unanswered=3, answeredElsewhere=4, declinedElsewhere=5.
  @objc public func reportEndCall(_ uuid: String, reason: Int) {
    log("reportEndCall <- JS  uuid=\(uuid) reason=\(reason)")
    guard let id = UUID(uuidString: uuid),
          let r = CXCallEndedReason(rawValue: reason) else { log("reportEndCall: bad uuid/reason"); return }
    provider.reportCall(with: id, endedAt: nil, reason: r)
    resumeHeldCall(except: id) // call waiting: bring back the held call
  }

  // After the active call ends, resume a call that was on hold (call waiting).
  private func resumeHeldCall(except endedUuid: UUID?) {
    let held = callController.callObserver.calls.first {
      $0.uuid != endedUuid && $0.isOnHold && !$0.hasEnded
    }
    guard let held = held else { return }
    log("auto-resume uuid=\(held.uuid.uuidString) after other call ended")
    request(CXSetHeldCallAction(call: held.uuid, onHold: false))
  }

  @objc public func updateDisplay(_ uuid: String, displayName: String, handle: String) {
    log("updateDisplay <- JS  uuid=\(uuid) name=\(displayName) handle=\(handle)")
    guard let id = UUID(uuidString: uuid) else { log("updateDisplay: bad uuid"); return }
    let update = CXCallUpdate()
    update.localizedCallerName = displayName.isEmpty ? nil : displayName
    update.remoteHandle = CXHandle(type: .generic, value: handle)
    provider.reportCall(with: id, updated: update)
  }

  @objc public func isCallActive(_ uuid: String) -> Bool {
    guard let id = UUID(uuidString: uuid) else { return false }
    let active = callController.callObserver.calls.contains { $0.uuid == id && !$0.hasEnded }
    log("isCallActive <- JS  uuid=\(uuid) -> \(active)")
    return active
  }

  // MARK: - Helpers

  private func request(_ action: CXAction) {
    callController.request(CXTransaction(action: action)) { [weak self] error in
      if let error = error {
        self?.log("transaction FAILED: \(error.localizedDescription)")
      } else {
        self?.log("transaction OK: \(type(of: action))")
      }
    }
  }

  // Configure only. CallKit owns activation: it calls provider(_:didActivate:)
  // once the system has elevated audio priority. Never setActive(true) here.
  private func configureAudioSession() {
    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.playAndRecord, mode: .voiceChat,
                              options: [.allowBluetooth, .allowBluetoothA2DP])
      log("audioSession configured (playAndRecord/voiceChat) — not activated (CallKit owns activation)")
    } catch {
      log("audioSession configure FAILED: \(error.localizedDescription)")
    }
  }
}

// MARK: - CXProviderDelegate

extension AhoyCallKit: CXProviderDelegate {

  // The only REQUIRED delegate method: discard zombie calls after a daemon reset.
  public func providerDidReset(_ provider: CXProvider) {
    log("delegate providerDidReset -> emit onProviderReset")
    eventDelegate?.sendEvent("onProviderReset", body: [:])
  }

  public func provider(_ p: CXProvider, perform action: CXStartCallAction) {
    log("delegate perform CXStartCallAction uuid=\(action.callUUID.uuidString) -> emit onStartCallAction")
    configureAudioSession() // configure only — no media here
    eventDelegate?.sendEvent("onStartCallAction",
                             body: ["uuid": action.callUUID.uuidString,
                                    "handle": action.handle.value])
    p.reportOutgoingCall(with: action.callUUID, startedConnectingAt: nil)
    // Mark the outgoing call holdable so CallKit offers "Hold & Accept" when a
    // second call arrives (call waiting). Incoming calls set this in their update.
    let update = CXCallUpdate()
    update.supportsHolding = true
    p.reportCall(with: action.callUUID, updated: update)
    action.fulfill()
    log("CXStartCallAction fulfilled (supportsHolding=true)")
  }

  public func provider(_ p: CXProvider, perform action: CXAnswerCallAction) {
    log("delegate perform CXAnswerCallAction uuid=\(action.callUUID.uuidString) -> emit onAnswerCall")
    configureAudioSession()
    eventDelegate?.sendEvent("onAnswerCall", body: ["uuid": action.callUUID.uuidString])
    action.fulfill() // media starts in didActivate
    // No explicit hold of the other call: CallKit holds it automatically when a
    // second call becomes active (and emits its own CXSetHeldCallAction).
    log("CXAnswerCallAction fulfilled (await didActivate)")
  }

  public func provider(_ p: CXProvider, perform action: CXEndCallAction) {
    log("delegate perform CXEndCallAction uuid=\(action.callUUID.uuidString) -> emit onEndCall")
    eventDelegate?.sendEvent("onEndCall", body: ["uuid": action.callUUID.uuidString])
    action.fulfill()
    log("CXEndCallAction fulfilled")
    // No explicit resume: CallKit auto-resumes the held call when the active one
    // ends (it fires its own CXSetHeldCallAction onHold=false).
  }

  public func provider(_ p: CXProvider, perform action: CXSetMutedCallAction) {
    log("delegate perform CXSetMutedCallAction uuid=\(action.callUUID.uuidString) muted=\(action.isMuted) -> emit onToggleMute")
    eventDelegate?.sendEvent("onToggleMute",
                             body: ["uuid": action.callUUID.uuidString, "muted": action.isMuted])
    action.fulfill()
  }

  public func provider(_ p: CXProvider, perform action: CXSetHeldCallAction) {
    log("delegate perform CXSetHeldCallAction uuid=\(action.callUUID.uuidString) onHold=\(action.isOnHold) -> emit onToggleHold")
    eventDelegate?.sendEvent("onToggleHold",
                             body: ["uuid": action.callUUID.uuidString, "onHold": action.isOnHold])
    action.fulfill()
  }

  public func provider(_ p: CXProvider, didActivate audioSession: AVAudioSession) {
    log("delegate didActivate audioSession -> emit onDidActivateAudioSession (consumer starts media here)")
    eventDelegate?.sendEvent("onDidActivateAudioSession", body: [:])
  }

  public func provider(_ p: CXProvider, didDeactivate audioSession: AVAudioSession) {
    log("delegate didDeactivate audioSession -> emit onDidDeactivateAudioSession")
    eventDelegate?.sendEvent("onDidDeactivateAudioSession", body: [:])
  }
}

// MARK: - CXCallObserverDelegate (authoritative system call state)

// Fires whenever CallKit's own view of a call changes — the source of truth for
// "is iOS still showing this call as active?". If ended=true never appears here
// after an end, the call is NOT leaving the system.
extension AhoyCallKit: CXCallObserverDelegate {
  public func callObserver(_ observer: CXCallObserver, callChanged call: CXCall) {
    log("observer callChanged uuid=\(call.uuid.uuidString) outgoing=\(call.isOutgoing) connected=\(call.hasConnected) ended=\(call.hasEnded) onHold=\(call.isOnHold)")
  }
}
