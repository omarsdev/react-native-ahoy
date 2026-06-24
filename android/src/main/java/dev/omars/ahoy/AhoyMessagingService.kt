package dev.omars.ahoy

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// T4: receives the high-priority, data-only FCM "call invite" and wakes the app.
// A `notification` payload would go to the system tray and skip this in the
// background — the sender MUST use data-only + android priority "high".
class AhoyMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    // The FCM token to register with your push backend (T7). Delivered to JS via
    // onVoipPushToken when the app is alive; also fetchable via getVoipPushToken().
    AhoyLog.d("FCM onNewToken: $token")
    AhoyEventBridge.voipPushToken(token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    // Only a PRIORITY_HIGH message may start a foreground service from the
    // background. FCM downgrades senders that don't reliably show UI.
    if (message.priority != RemoteMessage.PRIORITY_HIGH) {
      AhoyLog.d("FCM message not PRIORITY_HIGH — can't start FGS, ignoring")
      return
    }
    val data = message.data
    AhoyLog.d("FCM data message received: $data")
    // Warm the React runtime FIRST so it boots in parallel with the native call
    // setup below — the branded RN call screen then paints sooner on a cold start.
    AhoyReactWarmup.start(this)
    // Start the phoneCall FGS PROMPTLY — the background-start exemption window is brief.
    AhoyCallForegroundService.startIncomingFromPush(this, data)
  }
}
