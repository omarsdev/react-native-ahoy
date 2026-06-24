package dev.omars.ahoy

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

// Shared Telecom helpers usable WITHOUT the TurboModule (i.e. from the FCM push
// path on a cold start, before JS/AhoyModule exist). Uses the same account id +
// ComponentName as AhoyModule so the handle matches.
object AhoyTelecom {

  private fun telecom(ctx: Context) =
    ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

  fun phoneAccountHandle(ctx: Context): PhoneAccountHandle =
    PhoneAccountHandle(ComponentName(ctx, AhoyConnectionService::class.java), AhoyModule.ACCOUNT_ID)

  // Self-managed accounts persist across reboot; re-registering is safe/idempotent.
  fun ensureRegistered(ctx: Context, label: String = "Ahoy") {
    val account = PhoneAccount.builder(phoneAccountHandle(ctx), label)
      .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
      .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_SIP))
      .build()
    telecom(ctx).registerPhoneAccount(account)
  }

  // Hand a push-delivered incoming call to Telecom → onCreateIncomingConnection.
  fun addIncomingCall(ctx: Context, uuid: String, handle: String, displayName: String?) {
    val tm = telecom(ctx)
    val accountHandle = phoneAccountHandle(ctx)
    if (!tm.isIncomingCallPermitted(accountHandle)) {
      AhoyLog.d("addIncomingCall (push): not permitted uuid=$uuid")
      return
    }
    val extras = Bundle().apply {
      putParcelable(
        TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
        Uri.fromParts(PhoneAccount.SCHEME_TEL, handle, null)
      )
      putBundle(
        TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
        Bundle().apply {
          putString(AhoyConnectionService.EXTRA_UUID, uuid)
          putString(AhoyConnectionService.EXTRA_DISPLAY_NAME, displayName)
        }
      )
    }
    try {
      tm.addNewIncomingCall(accountHandle, extras)
      AhoyLog.d("addIncomingCall (push) -> addNewIncomingCall uuid=$uuid")
    } catch (e: SecurityException) {
      AhoyLog.d("addIncomingCall (push) SecurityException: ${e.message}")
    }
  }
}
