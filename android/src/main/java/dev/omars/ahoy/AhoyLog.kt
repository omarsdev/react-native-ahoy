package dev.omars.ahoy

import android.util.Log

// All Ahoy native logs go through one tag so they're easy to filter on device:
//   adb logcat -s Ahoy:V
// (mirrors the iOS "[Ahoy]" prefix).
object AhoyLog {
  private const val TAG = "Ahoy"

  fun d(message: String) {
    Log.i(TAG, message)
  }
}
