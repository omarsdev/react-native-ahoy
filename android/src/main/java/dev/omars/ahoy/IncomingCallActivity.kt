package dev.omars.ahoy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// T5: the full-screen-intent target. When a CallStyle notification's FSI fires on
// a LOCKED / screen-off device, the system launches this activity full-screen.
// showWhenLocked + turnScreenOn make it ring OVER the keyguard and wake the
// screen. It's a self-contained NATIVE ringing UI (no ReactActivity dependency),
// so the library works regardless of the consumer's JS entry point. Answer/Decline
// route through the same AhoyCallActionReceiver as the notification buttons.
class IncomingCallActivity : Activity() {

  private var uuid: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    // Set BOTH, BEFORE super/setContentView, or the FSI launches behind the
    // keyguard or the screen never wakes.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
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
    super.onCreate(savedInstanceState)
    bind(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.let {
      setIntent(it)
      bind(it)
    }
  }

  override fun onResume() {
    super.onResume()
    // If the call was already answered/declined elsewhere (notification, remote
    // cancel) before the user reached this screen, don't show a dead UI.
    if (uuid.isNotEmpty() && AhoyCallRegistry.byId(uuid) == null) {
      AhoyLog.d("IncomingCallActivity: call $uuid no longer live, finishing")
      finish()
    }
  }

  private fun bind(intent: Intent) {
    uuid = intent.getStringExtra(EXTRA_UUID) ?: ""
    val caller = intent.getStringExtra(EXTRA_CALLER)?.ifEmpty { "Incoming call" } ?: "Incoming call"
    AhoyLog.d("IncomingCallActivity shown uuid=$uuid caller=$caller")
    setContentView(buildUi(caller))
  }

  private fun buildUi(caller: String): View {
    val dp = resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setBackgroundColor(Color.parseColor("#0B1221"))
      setPadding(px(24), px(72), px(24), px(48))
    }

    root.addView(TextView(this).apply {
      text = caller
      setTextColor(Color.WHITE)
      textSize = 28f
      gravity = Gravity.CENTER
    })
    root.addView(TextView(this).apply {
      text = "Incoming call"
      setTextColor(Color.parseColor("#9AA4B2"))
      textSize = 16f
      gravity = Gravity.CENTER
      setPadding(0, px(8), 0, 0)
    })

    // Push the action row to the bottom.
    root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
    }
    row.addView(actionButton("Decline", Color.parseColor("#E5484D")) { act(AhoyCallActionReceiver.ACTION_DECLINE) })
    row.addView(View(this), LinearLayout.LayoutParams(px(32), 1))
    row.addView(actionButton("Answer", Color.parseColor("#30A46C")) { act(AhoyCallActionReceiver.ACTION_ANSWER) })
    root.addView(row)
    return root
  }

  private fun actionButton(label: String, color: Int, onClick: () -> Unit): Button =
    Button(this).apply {
      text = label
      setTextColor(Color.WHITE)
      setBackgroundColor(color)
      setOnClickListener { onClick() }
    }

  // Reuse the notification's answer/decline path (single source of truth for the
  // Telecom lifecycle). On answer, dismiss the keyguard so a secure post-answer
  // flow can proceed (showWhenLocked alone does NOT unlock a secure device).
  private fun act(action: String) {
    if (uuid.isEmpty()) return finish()
    if (action == AhoyCallActionReceiver.ACTION_ANSWER) {
      val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km?.isKeyguardLocked == true) {
        km.requestDismissKeyguard(this, null)
      }
    }
    sendBroadcast(
      Intent(this, AhoyCallActionReceiver::class.java).apply {
        this.action = action
        putExtra(AhoyCallActionReceiver.EXTRA_UUID, uuid)
        setPackage(packageName)
      }
    )
    finish()
  }

  companion object {
    const val EXTRA_UUID = "ahoy_uuid"
    const val EXTRA_CALLER = "ahoy_caller"
  }
}
