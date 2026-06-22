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

    // Prefer the exact call; fall back to any live call so the ongoing-call
    // notification's hang-up still works if its baked-in uuid went stale.
    val connection = uuid?.let { AhoyCallRegistry.byId(it) }
      ?: AhoyCallRegistry.uuids().firstNotNullOfOrNull { AhoyCallRegistry.byId(it) }

    if (connection == null) {
      AhoyLog.d("notification action: no live call to act on")
      return
    }

    when (intent.action) {
      ACTION_ANSWER -> connection.onAnswer()
      ACTION_DECLINE -> connection.onReject()
    }
  }

  companion object {
    const val ACTION_ANSWER = "dev.omars.ahoy.ANSWER"
    const val ACTION_DECLINE = "dev.omars.ahoy.DECLINE"
    const val EXTRA_UUID = "dev.omars.ahoy.ACTION_UUID"
  }
}
