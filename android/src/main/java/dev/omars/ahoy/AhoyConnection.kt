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

  // Telecom asks us (self-managed) to post our own incoming UI.
  override fun onShowIncomingCallUi() {
    val id = uuid()
    AhoyLog.d("onShowIncomingCallUi uuid=$id -> post CallStyle + start phoneCall FGS")
    AhoyEventBridge.incoming(id, address?.schemeSpecificPart ?: "")
    AhoyIncomingUi.postIncomingCall(appContext, id, callerDisplayName)
    AhoyCallForegroundService.start(appContext)
  }

  override fun onAnswer() {
    AhoyLog.d("onAnswer uuid=${uuid()} -> setActive")
    AhoyEventBridge.answer(uuid())
    setActive()
    AhoyIncomingUi.cancelIncoming(appContext)
    AhoyCallForegroundService.start(appContext)
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
    AhoyEventBridge.toggleHold(uuid(), false)
    setActive()
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
    AhoyIncomingUi.cancelIncoming(appContext)
    val remaining = AhoyCallRegistry.uuids()
    if (remaining.isEmpty()) {
      AhoyLog.d("cleanup uuid=$id -> registry empty, stopping FGS")
      AhoyCallForegroundService.stop(appContext)
    } else {
      AhoyLog.d("cleanup uuid=$id -> ${remaining.size} call(s) still active: $remaining (FGS stays)")
    }
  }
}
