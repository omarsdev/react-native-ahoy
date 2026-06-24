package dev.omars.ahoy

import android.content.Context
import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

// T4: runs the JS "AhoyIncomingCall" task even while the app is backgrounded or
// killed, so JS can react to a push-delivered call. The native call UI (CallKit-
// equivalent ConnectionService + notification) is owned natively and rings
// regardless of whether this JS task runs.
class AhoyHeadlessTaskService : HeadlessJsTaskService() {

  override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
    val extras = intent?.extras ?: return null
    return HeadlessJsTaskConfig(
      "AhoyIncomingCall",
      Arguments.fromBundle(extras),
      30_000, // 30s timeout
      true // allowedInForeground
    )
  }

  companion object {
    fun start(ctx: Context, uuid: String, handle: String, callerName: String) {
      val intent = Intent(ctx, AhoyHeadlessTaskService::class.java).apply {
        putExtra("uuid", uuid)
        putExtra("handle", handle)
        putExtra("callerName", callerName)
        putExtra("fromPushKit", true)
      }
      try {
        ctx.startService(intent)
        acquireWakeLockNow(ctx)
      } catch (e: Exception) {
        // Background-start can be refused on some OS versions; the native call UI
        // still rang. JS will get the event on next bridge attach.
        AhoyLog.d("headless task start failed: ${e.message}")
      }
    }
  }
}
