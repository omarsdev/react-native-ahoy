package dev.omars.ahoy

// The Telecom classes (ConnectionService / Connection / notification actions) are
// instantiated by the system, not by us, so they can't reach the TurboModule
// directly. AhoyModule registers itself here on init; these helpers forward
// Telecom callbacks to the generated typed emitters on the module.
//
// Maps the cross-platform spec events to Android Telecom callbacks:
//   onAnswerCall  <- Connection.onAnswer
//   onEndCall     <- onDisconnect / onReject / onAbort
//   onDisplayIncomingCall <- onShowIncomingCallUi
//   onStartCallAction     <- onCreateOutgoingConnection
//   onToggleHold  <- onHold / onUnhold
//   onToggleMute  <- onCallAudioStateChanged (mute)
object AhoyEventBridge {

  @Volatile
  private var module: AhoyModule? = null

  fun attach(m: AhoyModule) {
    module = m
  }

  fun detach(m: AhoyModule) {
    if (module === m) module = null
  }

  fun answer(uuid: String) = module?.sendAnswerCall(uuid)
  fun end(uuid: String) = module?.sendEndCall(uuid)
  fun incoming(uuid: String, handle: String) = module?.sendDisplayIncomingCall(uuid, handle)
  fun startCallAction(uuid: String, handle: String) = module?.sendStartCallAction(uuid, handle)
  fun toggleHold(uuid: String, onHold: Boolean) = module?.sendToggleHold(uuid, onHold)
  fun toggleMute(uuid: String, muted: Boolean) = module?.sendToggleMute(uuid, muted)
}
