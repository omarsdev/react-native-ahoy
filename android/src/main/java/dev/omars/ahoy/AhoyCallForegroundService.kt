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
    val notification = AhoyIncomingUi.buildOngoingCallNotification(this)
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // API 34
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
      else 0
    ServiceCompat.startForeground(this, AhoyIncomingUi.ONGOING_NOTIFICATION_ID, notification, type)
    AhoyLog.d("phoneCall foreground service started (type=$type)")
    return START_NOT_STICKY
  }

  companion object {
    fun start(ctx: Context?) {
      ctx ?: return
      ContextCompat.startForegroundService(
        ctx,
        Intent(ctx, AhoyCallForegroundService::class.java)
      )
    }

    fun stop(ctx: Context?) {
      ctx ?: return
      ctx.stopService(Intent(ctx, AhoyCallForegroundService::class.java))
    }
  }
}
