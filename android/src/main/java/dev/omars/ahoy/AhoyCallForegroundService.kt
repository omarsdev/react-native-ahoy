package dev.omars.ahoy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

// phoneCall-typed foreground service that keeps an active call alive. phoneCall
// is the correct, least-restrictive type for a self-managed app holding
// MANAGE_OWN_CALLS — never use `microphone` (it can't be started from the
// background, which breaks the later killed/locked wakeup).
class AhoyCallForegroundService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_INCOMING_PUSH) {
      handleIncomingPush(intent)
      return START_NOT_STICKY
    }
    // Reflects the current call state (incoming CallStyle while ringing, else ongoing).
    startForegroundWith(AhoyIncomingUi.buildCallNotification(this))
    return START_NOT_STICKY
  }

  // T4 cold-start push: bring the FGS up IMMEDIATELY (exemption window), then
  // hand the call to Telecom and wake JS. The call rings natively even if no JS runs.
  private fun handleIncomingPush(intent: Intent) {
    val uuid = intent.getStringExtra(EXTRA_UUID) ?: return
    val handle = intent.getStringExtra(EXTRA_HANDLE) ?: ""
    val caller = intent.getStringExtra(EXTRA_CALLER) ?: ""
    AhoyLog.d("FGS from push uuid=$uuid handle=$handle caller=$caller")
    // Start foreground first with the incoming notification built from the push.
    startForegroundWith(AhoyIncomingUi.buildIncomingFromPush(this, uuid, caller))
    // Register the self-managed account (may be the first run after a kill) + hand to Telecom.
    AhoyTelecom.ensureRegistered(this)
    AhoyTelecom.addIncomingCall(this, uuid, handle, caller)
    // Wake backgrounded/killed JS via Headless JS.
    AhoyHeadlessTaskService.start(this, uuid, handle, caller)
  }

  private fun startForegroundWith(notification: android.app.Notification) {
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // API 34
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
      else 0
    ServiceCompat.startForeground(this, AhoyIncomingUi.CALL_NOTIFICATION_ID, notification, type)
    AhoyLog.d("phoneCall foreground service started (type=$type)")
  }

  override fun onDestroy() {
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  companion object {
    const val ACTION_INCOMING_PUSH = "dev.omars.ahoy.INCOMING_PUSH"
    private const val EXTRA_UUID = "uuid"
    private const val EXTRA_HANDLE = "handle"
    private const val EXTRA_CALLER = "callerName"

    fun start(ctx: Context?) {
      ctx ?: return
      ContextCompat.startForegroundService(
        ctx,
        Intent(ctx, AhoyCallForegroundService::class.java)
      )
    }

    // Called from the FCM service on a push. Starts the FGS with the call payload.
    fun startIncomingFromPush(ctx: Context, data: Map<String, String>) {
      val uuid = data["callUUID"] ?: data["uuid"] ?: java.util.UUID.randomUUID().toString()
      val intent = Intent(ctx, AhoyCallForegroundService::class.java).apply {
        action = ACTION_INCOMING_PUSH
        putExtra(EXTRA_UUID, uuid)
        putExtra(EXTRA_HANDLE, data["handle"] ?: "")
        putExtra(EXTRA_CALLER, data["callerName"] ?: "")
      }
      ContextCompat.startForegroundService(ctx, intent)
    }

    fun stop(ctx: Context?) {
      ctx ?: return
      ctx.stopService(Intent(ctx, AhoyCallForegroundService::class.java))
    }
  }
}
