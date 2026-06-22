package dev.omars.ahoy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

// Builds the self-managed call notifications: a full-screen CallStyle incoming
// notification (Answer / Decline) and the ongoing-call notification the
// phoneCall foreground service runs with. CallStyle is API 31+; older devices
// fall back to a high-priority notification with action buttons.
object AhoyIncomingUi {

  const val ONGOING_NOTIFICATION_ID = 0xA401
  private const val INCOMING_NOTIFICATION_ID = 0xA402
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

  fun postIncomingCall(context: Context, uuid: String, callerName: CharSequence?) {
    ensureChannel(context)
    val name = (callerName?.toString()?.takeIf { it.isNotEmpty() }) ?: "Incoming call"
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

    NotificationManagerCompat.from(context).notify(INCOMING_NOTIFICATION_ID, builder.build())
  }

  fun buildOngoingCallNotification(context: Context): android.app.Notification {
    ensureChannel(context)
    val uuid = AhoyCallRegistry.uuids().firstOrNull() ?: ""
    val person = Person.Builder().setName("Ongoing call").build()
    val hangup = actionIntent(context, AhoyCallActionReceiver.ACTION_DECLINE, uuid)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_outgoing)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setContentTitle("Ongoing call")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangup))
    } else {
      builder.addAction(android.R.drawable.sym_call_missed, "Hang up", hangup)
    }
    return builder.build()
  }

  fun cancelIncoming(context: Context) {
    NotificationManagerCompat.from(context).cancel(INCOMING_NOTIFICATION_ID)
  }
}
