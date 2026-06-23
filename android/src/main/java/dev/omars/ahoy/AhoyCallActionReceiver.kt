package dev.omars.ahoy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Backs the Answer / Decline / Hang-up buttons on the call notifications. Routes
// taps into the live AhoyConnection, whose onAnswer()/onReject() emit the JS
// events and drive the Telecom lifecycle (same path as a Bluetooth/Auto answer).
class AhoyCallActionReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val uuid = intent.getStringExtra(EXTRA_UUID)
    AhoyLog.d("notification action=${intent.action} uuid=$uuid")

    when (intent.action) {
      // Per-call buttons target their EXACT call only. A stale button (its call
      // already ended) must do nothing — never fall back to another call, or it
      // would hang up an unrelated active call.
      ACTION_ANSWER -> withCall(uuid) { it.onAnswer() }
      ACTION_DECLINE -> withCall(uuid) { it.onReject() }
      // Ongoing-call notification hang-up: no baked-in uuid — resolve the current
      // active call dynamically so it can't go stale.
      ACTION_HANGUP -> {
        val active = AhoyCallRegistry.activeConnection()
        if (active == null) AhoyLog.d("hangup: no active call")
        else active.onDisconnect()
      }
    }
  }

  private inline fun withCall(uuid: String?, action: (AhoyConnection) -> Unit) {
    val connection = uuid?.let { AhoyCallRegistry.byId(it) }
    if (connection == null) AhoyLog.d("action ignored: no live call $uuid (stale notification)")
    else action(connection)
  }

  companion object {
    const val ACTION_ANSWER = "dev.omars.ahoy.ANSWER"
    const val ACTION_DECLINE = "dev.omars.ahoy.DECLINE"
    const val ACTION_HANGUP = "dev.omars.ahoy.HANGUP"
    const val EXTRA_UUID = "dev.omars.ahoy.ACTION_UUID"
  }
}
