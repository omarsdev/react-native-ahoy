package dev.omars.ahoy

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import java.lang.ref.WeakReference

// T6: the full-screen-intent target renders the CONSUMER'S React component
// (registered from JS as "AhoyIncomingCall") instead of a hard-coded native
// screen. It still wakes over the keyguard (showWhenLocked/turnScreenOn) on a
// killed app with the screen off, and uses a dark window background so the
// pre-JS frame is dark rather than a white flash while the JS bundle boots.
//
// The native ring is independent and instant: the CallStyle notification +
// ringtone (via the phoneCall FGS) fire the moment the push arrives, regardless
// of how long React takes to render. This activity just paints the branded UI.
class IncomingCallActivity : ReactActivity() {

  private var uuid: String = ""

  override fun getMainComponentName(): String = COMPONENT_NAME

  override fun onCreate(savedInstanceState: Bundle?) {
    // Set BOTH before super, or the activity launches behind the keyguard /
    // the screen never wakes.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
          or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      )
    }
    uuid = intent?.getStringExtra(EXTRA_UUID) ?: ""
    super.onCreate(savedInstanceState)
    register(uuid, this)
    AhoyLog.d("IncomingCallActivity (RN '$COMPONENT_NAME') shown uuid=$uuid")
  }

  override fun onDestroy() {
    unregister(uuid, this)
    super.onDestroy()
  }

  // The call data becomes the JS component's initial root props.
  override fun createReactActivityDelegate(): ReactActivityDelegate =
    object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
      override fun getLaunchOptions(): Bundle =
        Bundle().apply {
          putString("uuid", intent?.getStringExtra(EXTRA_UUID) ?: "")
          putString("callerName", intent?.getStringExtra(EXTRA_CALLER) ?: "")
          putString("handle", intent?.getStringExtra(EXTRA_HANDLE) ?: "")
        }
    }

  private fun dismissKeyguard() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      if (km?.isKeyguardLocked == true) km.requestDismissKeyguard(this, null)
    }
  }

  companion object {
    const val COMPONENT_NAME = "AhoyIncomingCall"
    const val EXTRA_UUID = "ahoy_uuid"
    const val EXTRA_CALLER = "ahoy_caller"
    const val EXTRA_HANDLE = "ahoy_handle"

    private val live = mutableMapOf<String, WeakReference<IncomingCallActivity>>()

    @Synchronized
    private fun register(uuid: String, a: IncomingCallActivity) {
      if (uuid.isNotEmpty()) live[uuid] = WeakReference(a)
    }

    @Synchronized
    private fun unregister(uuid: String, a: IncomingCallActivity) {
      if (live[uuid]?.get() === a) live.remove(uuid)
    }

    // Call ANSWERED (WhatsApp-style): go INTO the call. Dismiss the keyguard (on a
    // no-password lock this is instant) but KEEP the activity, so the same React
    // component re-renders as the in-call screen. Does not finish.
    @Synchronized
    fun onAnswered(uuid: String) {
      val a = live[uuid]?.get() ?: return
      a.runOnUiThread { a.dismissKeyguard() }
    }

    // Call DECLINED / ended / cancelled: close the lock-screen UI. Never dismisses
    // the keyguard — rejecting a call must not unlock the phone; the screen returns
    // to the lock screen. Fired from RN buttons, the notification, or a remote end.
    @Synchronized
    fun finishFor(uuid: String) {
      val a = live.remove(uuid)?.get() ?: return
      a.runOnUiThread { a.finish() }
    }
  }
}
