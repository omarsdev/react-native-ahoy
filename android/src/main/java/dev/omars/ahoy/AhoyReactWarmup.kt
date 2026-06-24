package dev.omars.ahoy

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.facebook.react.ReactApplication

// Perf: kick off the React runtime + JS bundle load the INSTANT a push arrives,
// so a killed-app cold start overlaps RN boot with the native call setup (FGS,
// Telecom registration, the FSI activity launch) instead of only starting when
// IncomingCallActivity attaches. reactHost.start() is idempotent — the activity
// and the Headless JS task reuse the same warming host, so the branded RN call
// screen paints noticeably sooner. No-op on a non-ReactApplication / old arch.
object AhoyReactWarmup {

  fun start(context: Context) {
    val app = context.applicationContext as? ReactApplication ?: return
    // ReactHost.start() must run on the UI thread; onMessageReceived is a bg thread.
    Handler(Looper.getMainLooper()).post {
      try {
        app.reactHost?.start()
        AhoyLog.d("React host warmup started")
      } catch (e: Throwable) {
        AhoyLog.d("React warmup skipped: ${e.message}")
      }
    }
  }
}
