package dev.omars.ahoy

import android.content.Context
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause

// A single self-managed call. Every override here can be fired by the system
// UI, a Bluetooth headset, a wearable, or Android Auto — so each is bridged to
// the JS event surface, not just the in-app notification buttons.
class AhoyConnection(private val appContext: Context) : Connection() {

  private fun uuid(): String = AhoyCallRegistry.idOf(this) ?: ""

  // Best label for the notification title: caller name, else the handle/number.
  fun displayLabel(): String =
    callerDisplayName?.toString()?.takeIf { it.isNotEmpty() }
      ?: address?.schemeSpecificPart?.takeIf { it.isNotEmpty() }
      ?: "Call"

  // Telecom asks us (self-managed) to post our own incoming UI.
  override fun onShowIncomingCallUi() {
    val id = uuid()
    AhoyLog.d("onShowIncomingCallUi uuid=$id -> start phoneCall FGS (incoming notification)")
    AhoyEventBridge.incoming(id, address?.schemeSpecificPart ?: "")
    // The FGS notification IS the incoming CallStyle while this call is ringing.
    AhoyCallForegroundService.start(appContext)
  }

  override fun onAnswer() {
    AhoyLog.d("onAnswer uuid=${uuid()} -> setActive")
    AhoyEventBridge.answer(uuid())
    setActive()
    AhoyCallRegistry.holdAllExcept(uuid()) // call waiting: hold the call we were on
    AhoyCallForegroundService.start(appContext) // refresh notification: now ongoing
  }

  override fun onAnswer(videoState: Int) = onAnswer()

  override fun onReject() {
    val id = uuid()
    AhoyLog.d("onReject uuid=$id state=$state -> setDisconnected(REJECTED) + destroy")
    AhoyEventBridge.end(id)
    setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    destroy()
    cleanup(id)
  }

  override fun onDisconnect() = disconnect(remote = false)

  // JS-driven actions (called from AhoyModule).
  fun markActive() {
    setActive()
    AhoyCallForegroundService.start(appContext)
  }

  fun disconnect(remote: Boolean) {
    val id = uuid()
    AhoyLog.d("disconnect uuid=$id remote=$remote -> setDisconnected + destroy")
    AhoyEventBridge.end(id)
    setDisconnected(DisconnectCause(if (remote) DisconnectCause.REMOTE else DisconnectCause.LOCAL))
    destroy()
    cleanup(id)
  }

  override fun onAbort() {
    val id = uuid()
    AhoyEventBridge.end(id)
    setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
    destroy()
    cleanup(id)
  }

  override fun onHold() {
    AhoyEventBridge.toggleHold(uuid(), true)
    setOnHold()
  }

  override fun onUnhold() {
    AhoyLog.d("onUnhold uuid=${uuid()} -> setActive")
    AhoyEventBridge.toggleHold(uuid(), false)
    setActive()
    AhoyCallRegistry.clearAutoHeld(uuid()) // user resumed it; no longer auto-held
    AhoyCallRegistry.holdAllExcept(uuid()) // resuming this call holds the other
  }

  // Auto-hold this call when another takes focus (call waiting). Only acts on a
  // call that is actually active/dialing; ringing/held calls are left alone.
  // Returns true if it actually held (so the registry can track it for resume).
  fun holdForCallWaiting(): Boolean {
    if (state == STATE_ACTIVE || state == STATE_DIALING) {
      setOnHold()
      AhoyEventBridge.toggleHold(uuid(), true)
      AhoyLog.d("auto-hold uuid=${uuid()} for call waiting")
      return true
    }
    return false
  }

  // Auto-resume when the other (active) call ends.
  fun resumeFromHold() {
    if (state == STATE_HOLDING) {
      setActive()
      AhoyEventBridge.toggleHold(uuid(), false)
      AhoyLog.d("auto-resume uuid=${uuid()} after other call ended")
    }
  }

  // Deprecated in API 34 (superseded by onCallEndpointChanged/onMuteStateChanged)
  // but still the portable way to observe mute back to minSdk 24.
  @Suppress("OVERRIDE_DEPRECATION")
  override fun onCallAudioStateChanged(state: CallAudioState?) {
    state ?: return
    AhoyEventBridge.toggleMute(uuid(), state.isMuted)
  }

  // Shared teardown: free the Connection and, if this was the last call, stop the
  // foreground service and clear notifications so Telecom releases audio focus.
  private fun cleanup(id: String) {
    AhoyCallRegistry.remove(id)
    val remaining = AhoyCallRegistry.uuids()
    if (remaining.isEmpty()) {
      AhoyLog.d("cleanup uuid=$id -> registry empty, stopping FGS")
      AhoyCallForegroundService.stop(appContext) // onDestroy removes the notification
    } else {
      AhoyLog.d("cleanup uuid=$id -> ${remaining.size} call(s) still active: $remaining (FGS stays)")
      AhoyCallRegistry.resumeOneHeld() // bring the held call back to active
      AhoyCallForegroundService.start(appContext) // refresh notification for the remaining call
    }
  }
}
