package dev.omars.ahoy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// T6: re-arm reliability state after a reboot. Self-managed PhoneAccounts USUALLY
// persist across reboot, but re-registering is idempotent and cheap insurance so a
// post-reboot push can still hand the call to Telecom. IMPORTANT: do NOT start the
// phoneCall foreground service from here — launching phoneCall/microphone/
// mediaPlayback FGS from BOOT_COMPLETED throws on API 35+. Restoration of an actual
// ringing call only happens via a fresh FCM push or user interaction.
class AhoyBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    AhoyLog.d("BOOT_COMPLETED -> re-arm self-managed PhoneAccount (no FGS start)")
    try {
      AhoyTelecom.ensureRegistered(context)
    } catch (e: Exception) {
      AhoyLog.d("boot re-arm failed: ${e.message}")
    }
  }
}
