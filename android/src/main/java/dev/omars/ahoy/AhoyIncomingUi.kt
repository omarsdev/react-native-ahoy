package dev.omars.ahoy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

// The ONE call notification, run by the foreground service. It reflects the
// current call state: a ringing call → full-screen incoming CallStyle
// (Answer/Decline); otherwise the ongoing-call style (Hang up). CallStyle is
// API 31+; older devices fall back to a high-priority notification with actions.
object AhoyIncomingUi {

  const val CALL_NOTIFICATION_ID = 0xA401
  // TWO channels on purpose: the ringing channel rings + vibrates (incoming), the
  // ongoing channel is SILENT. The ongoing call notification lives on its own
  // ID + channel so answering does NOT re-alert on the ringing channel (the bug
  // where the ringtone kept playing after Answer). Importance/sound are immutable
  // after creation, so a channel-behaviour change needs a new id (…_v2).
  private const val RINGING_CHANNEL_ID = "ahoy_calls_v2"
  private const val ONGOING_CHANNEL_ID = "ahoy_ongoing_v2"

  private fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val mgr = context.getSystemService(NotificationManager::class.java) ?: return

    if (mgr.getNotificationChannel(RINGING_CHANNEL_ID) == null) {
      val ringing = NotificationChannel(
        RINGING_CHANNEL_ID,
        "Incoming calls",
        NotificationManager.IMPORTANCE_HIGH // mandatory: lower never rings / launches an FSI
      ).apply {
        description = "Ringing for incoming calls"
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 1000, 1000)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audio = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setSound(ringtone, audio)
      }
      mgr.createNotificationChannel(ringing)
    }

    if (mgr.getNotificationChannel(ONGOING_CHANNEL_ID) == null) {
      val ongoing = NotificationChannel(
        ONGOING_CHANNEL_ID,
        "Ongoing calls",
        NotificationManager.IMPORTANCE_LOW // no heads-up, no sound
      ).apply {
        description = "The active call in progress"
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setSound(null, null) // SILENT — never ring on the ongoing call
        enableVibration(false)
      }
      mgr.createNotificationChannel(ongoing)
    }
  }

  // T5: on Android 14+ USE_FULL_SCREEN_INTENT is auto-granted only to calling/alarm
  // apps; everyone else defaults to false and must deep-link to Settings. Below 34
  // the manifest permission is always in effect.
  fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val mgr = context.getSystemService(NotificationManager::class.java) ?: return false
    return mgr.canUseFullScreenIntent()
  }

  // Build the notification for whatever the current call state is.
  fun buildCallNotification(context: Context): Notification {
    ensureChannels(context)
    val ringing = AhoyCallRegistry.ringingConnection()
    return if (ringing != null) {
      buildIncoming(context, AhoyCallRegistry.idOf(ringing) ?: "", ringing.displayLabel())
    } else {
      buildOngoing(context)
    }
  }

  // T4: incoming notification built straight from the push payload, before any
  // Telecom connection exists (cold-start). Answer/Decline target the push uuid.
  fun buildIncomingFromPush(context: Context, uuid: String, callerName: String): Notification {
    ensureChannels(context)
    return buildIncoming(context, uuid, callerName.ifEmpty { "Incoming call" })
  }

  private fun buildIncoming(context: Context, uuid: String, name: String): Notification {
    val person = Person.Builder().setName(name).setImportant(true).build()
    val answer = actionIntent(context, AhoyCallActionReceiver.ACTION_ANSWER, uuid)
    val decline = actionIntent(context, AhoyCallActionReceiver.ACTION_DECLINE, uuid)

    val builder = NotificationCompat.Builder(context, RINGING_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setOngoing(true)
      .setAutoCancel(false)
      .setOnlyAlertOnce(true) // ring once on first post, not on every FGS refresh
      // FSI wakes the screen over the keyguard via IncomingCallActivity. When the
      // permission is revoked on API 34+ this silently degrades to a heads-up — so
      // tell JS (it can prompt + deep-link) while still posting the notification.
      .setFullScreenIntent(fullScreenIntent(context, uuid, name), true)

    if (!canUseFullScreenIntent(context)) {
      AhoyLog.d("full-screen intent NOT granted; degrading to heads-up uuid=$uuid")
      AhoyEventBridge.fullScreenIntentNotGranted()
    }

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
    val active = AhoyCallRegistry.activeConnection()
    // Title from the active call (caller name or number), not a generic label.
    val name = active?.displayLabel() ?: "Ongoing call"
    val person = Person.Builder().setName(name).build()
    // No baked-in uuid: ACTION_HANGUP resolves the active call when tapped.
    val hangup = actionIntent(context, AhoyCallActionReceiver.ACTION_HANGUP, "")

    val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_phone_call)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setOnlyAlertOnce(true) // never re-alert when answering / refreshing
      .setContentTitle(name)

    // Running call-duration timer: chronometer counts up from the connect time.
    // CallStyle renders it as the elapsed call duration once both are set.
    val connectedAt = active?.connectedAtMillis ?: 0L
    if (connectedAt > 0L) {
      builder.setWhen(connectedAt).setShowWhen(true).setUsesChronometer(true)
    }

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

  // Targets our own IncomingCallActivity (showWhenLocked/turnScreenOn) rather than
  // the app launch intent, so the full-screen ring shows OVER the keyguard and
  // wakes the screen instead of launching behind the lock screen.
  private fun fullScreenIntent(context: Context, uuid: String, name: String): PendingIntent {
    val intent = Intent(context, IncomingCallActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(IncomingCallActivity.EXTRA_UUID, uuid)
      putExtra(IncomingCallActivity.EXTRA_CALLER, name)
    }
    return PendingIntent.getActivity(
      context,
      uuid.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}
