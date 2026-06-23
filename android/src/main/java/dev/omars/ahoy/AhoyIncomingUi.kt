package dev.omars.ahoy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

// The ONE call notification, run by the foreground service. It reflects the
// current call state: a ringing call → full-screen incoming CallStyle
// (Answer/Decline); otherwise the ongoing-call style (Hang up). CallStyle is
// API 31+; older devices fall back to a high-priority notification with actions.
object AhoyIncomingUi {

  const val CALL_NOTIFICATION_ID = 0xA401
  private const val CHANNEL_ID = "ahoy_calls"

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val mgr = context.getSystemService(NotificationManager::class.java) ?: return
    if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Calls",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = "Incoming and ongoing calls"
      setShowBadge(false)
    }
    mgr.createNotificationChannel(channel)
  }

  // Build the notification for whatever the current call state is.
  fun buildCallNotification(context: Context): Notification {
    ensureChannel(context)
    val ringing = AhoyCallRegistry.ringingConnection()
    return if (ringing != null) buildIncoming(context, ringing) else buildOngoing(context)
  }

  private fun buildIncoming(context: Context, connection: AhoyConnection): Notification {
    val uuid = AhoyCallRegistry.idOf(connection) ?: ""
    val name = connection.displayLabel()
    val person = Person.Builder().setName(name).setImportant(true).build()
    val answer = actionIntent(context, AhoyCallActionReceiver.ACTION_ANSWER, uuid)
    val decline = actionIntent(context, AhoyCallActionReceiver.ACTION_DECLINE, uuid)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setOngoing(true)
      .setAutoCancel(false)
      .setFullScreenIntent(fullScreenIntent(context), true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
    } else {
      builder
        .setContentTitle(name)
        .setContentText("Incoming call")
        .addAction(android.R.drawable.sym_call_incoming, "Answer", answer)
        .addAction(android.R.drawable.sym_call_missed, "Decline", decline)
    }
    return builder.build()
  }

  private fun buildOngoing(context: Context): Notification {
    // Title from the active call (caller name or number), not a generic label.
    val name = AhoyCallRegistry.activeConnection()?.displayLabel() ?: "Ongoing call"
    val person = Person.Builder().setName(name).build()
    // No baked-in uuid: ACTION_HANGUP resolves the active call when tapped.
    val hangup = actionIntent(context, AhoyCallActionReceiver.ACTION_HANGUP, "")

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_phone_call)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setContentTitle(name)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangup))
    } else {
      builder.addAction(android.R.drawable.sym_call_missed, "Hang up", hangup)
    }
    return builder.build()
  }

  private fun actionIntent(context: Context, action: String, uuid: String): PendingIntent {
    val intent = Intent(context, AhoyCallActionReceiver::class.java).apply {
      this.action = action
      putExtra(AhoyCallActionReceiver.EXTRA_UUID, uuid)
    }
    return PendingIntent.getBroadcast(
      context,
      (action + uuid).hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun fullScreenIntent(context: Context): PendingIntent? {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(
      context,
      0,
      launch,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}
