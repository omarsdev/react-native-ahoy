import Foundation
import PushKit
import os

// T4: owns the PushKit VoIP registry. Must be set up at app launch (from the
// AppDelegate) so it exists for a cold-start push, before JS/the TurboModule.
// On an incoming VoIP push it reports to CallKit SYNCHRONOUSLY (iOS 13+ kills the
// app otherwise) via the shared AhoyCallKit provider.
@objc public final class AhoyVoipPushManager: NSObject, PKPushRegistryDelegate {

  @objc public static let shared = AhoyVoipPushManager()

  private var registry: PKPushRegistry?
  // Latest hex VoIP token; read by getVoipPushToken() from JS.
  @objc public private(set) var currentToken: String?

  private func log(_ message: String) {
    ahoyLog.notice("[Ahoy] \(message, privacy: .public)")
  }

  // Call from AppDelegate didFinishLaunching (and it's safe to call again).
  @objc public func register() {
    if registry != nil { return }
    let registry = PKPushRegistry(queue: .main)
    registry.delegate = self            // delegate BEFORE desiredPushTypes
    registry.desiredPushTypes = [.voIP] // setting this triggers registration
    self.registry = registry
    log("VoIP PushKit registry created (desiredPushTypes=[.voIP])")
  }

  // MARK: - PKPushRegistryDelegate

  public func pushRegistry(_ registry: PKPushRegistry,
                           didUpdate pushCredentials: PKPushCredentials,
                           for type: PKPushType) {
    let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
    currentToken = token
    log("VoIP token updated: \(token)")
    AhoyCallKit.shared.emitOrBuffer("onVoipPushToken", ["token": token])
  }

  public func pushRegistry(_ registry: PKPushRegistry,
                           didInvalidatePushTokenFor type: PKPushType) {
    currentToken = nil
    log("VoIP token invalidated")
  }

  public func pushRegistry(_ registry: PKPushRegistry,
                           didReceiveIncomingPushWith payload: PKPushPayload,
                           for type: PKPushType,
                           completion: @escaping () -> Void) {
    // Our call fields live in our own top-level keys (NOT under "aps").
    let dict = payload.dictionaryPayload
    let uuid = (dict["callUUID"] as? String) ?? UUID().uuidString
    let handle = dict["handle"] as? String ?? "Unknown"
    let callerName = dict["callerName"] as? String ?? ""
    let hasVideo = (dict["hasVideo"] as? Bool) ?? false
    log("didReceiveIncomingPush uuid=\(uuid) handle=\(handle)")

    // MUST report synchronously here. Call PushKit completion only after CallKit's.
    AhoyCallKit.shared.reportPushIncomingCall(uuid, handle: handle,
                                              callerName: callerName, hasVideo: hasVideo) { _ in
      completion()
    }
  }
}
