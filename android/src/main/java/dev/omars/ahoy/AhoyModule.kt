package dev.omars.ahoy

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap

// T3: Android self-managed ConnectionService. Implements the cross-platform Ahoy
// spec against android.telecom — startCall -> placeCall, displayIncomingCall ->
// addNewIncomingCall, answer/end/reject/hold -> Connection lifecycle. Telecom
// callbacks flow back to JS through AhoyEventBridge -> the generated emitOn… here.
class AhoyModule(reactContext: ReactApplicationContext) :
  NativeAhoySpec(reactContext) {

  init {
    AhoyEventBridge.attach(this)
  }

  override fun getName(): String = NAME

  override fun invalidate() {
    AhoyEventBridge.detach(this)
    super.invalidate()
  }

  private val telecom: TelecomManager
    get() = reactApplicationContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

  private fun phoneAccountHandle(): PhoneAccountHandle =
    PhoneAccountHandle(
      ComponentName(reactApplicationContext, AhoyConnectionService::class.java),
      ACCOUNT_ID
    )

  // ---- setup: register the self-managed PhoneAccount (auto-enabled, persists) ----

  override fun setup(options: ReadableMap, promise: Promise) {
    try {
      val label = if (options.hasKey("label")) options.getString("label") ?: "Ahoy" else "Ahoy"
      val account = PhoneAccount.builder(phoneAccountHandle(), label)
        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
        .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_SIP))
        .build()
      telecom.registerPhoneAccount(account)
      AhoyLog.d("setup: registered self-managed PhoneAccount id=$ACCOUNT_ID label=$label")
      promise.resolve(null)
    } catch (e: Exception) {
      AhoyLog.d("setup FAILED: ${e.message}")
      promise.reject("ahoy_setup_failed", e)
    }
  }

  // ---- outgoing ----

  override fun startCall(opts: ReadableMap, promise: Promise) {
    val uuid = opts.getString("uuid") ?: return promise.reject("ahoy_bad_args", "missing uuid")
    val handle = opts.getString("handle") ?: ""
    try {
      val accountHandle = phoneAccountHandle()
      if (!telecom.isOutgoingCallPermitted(accountHandle)) {
        AhoyLog.d("startCall NOT permitted (another call active?) uuid=$uuid")
        return promise.reject("ahoy_not_permitted", "outgoing call not permitted")
      }
      AhoyLog.d("startCall -> placeCall uuid=$uuid handle=$handle")
      val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, handle, null)
      val extras = Bundle().apply {
        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle) // MANDATORY
        putBundle(
          TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
          Bundle().apply { putString(AhoyConnectionService.EXTRA_UUID, uuid) }
        )
      }
      telecom.placeCall(uri, extras) // -> onCreateOutgoingConnection
      promise.resolve(null)
    } catch (e: SecurityException) {
      AhoyLog.d("startCall SecurityException (MANAGE_OWN_CALLS missing?): ${e.message}")
      promise.reject("ahoy_security", e)
    }
  }

  override fun reportConnectedOutgoingCall(uuid: String) {
    AhoyLog.d("reportConnectedOutgoingCall uuid=$uuid -> setActive")
    AhoyCallRegistry.byId(uuid)?.markActive()
  }

  // ---- incoming ----

  override fun displayIncomingCall(opts: ReadableMap, promise: Promise) {
    val uuid = opts.getString("uuid") ?: return promise.reject("ahoy_bad_args", "missing uuid")
    val handle = opts.getString("handle") ?: ""
    val displayName =
      if (opts.hasKey("localizedCallerName")) opts.getString("localizedCallerName") else null
    try {
      val accountHandle = phoneAccountHandle()
      if (!telecom.isIncomingCallPermitted(accountHandle)) {
        AhoyLog.d("displayIncomingCall NOT permitted uuid=$uuid")
        return promise.reject("ahoy_not_permitted", "incoming call not permitted")
      }
      AhoyLog.d("displayIncomingCall -> addNewIncomingCall uuid=$uuid handle=$handle")
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
      telecom.addNewIncomingCall(accountHandle, extras) // -> onCreateIncomingConnection
      promise.resolve(null)
    } catch (e: SecurityException) {
      AhoyLog.d("displayIncomingCall SecurityException (MANAGE_OWN_CALLS missing?): ${e.message}")
      promise.reject("ahoy_security", e)
    }
  }

  // ---- local actions ----

  override fun answerIncomingCall(uuid: String) {
    AhoyCallRegistry.byId(uuid)?.onAnswer()
  }

  override fun endCall(uuid: String) {
    AhoyCallRegistry.byId(uuid)?.disconnect(remote = false)
  }

  override fun endAllCalls() {
    AhoyCallRegistry.uuids().forEach { AhoyCallRegistry.byId(it)?.disconnect(remote = false) }
  }

  override fun rejectCall(uuid: String) {
    AhoyCallRegistry.byId(uuid)?.onReject()
  }

  override fun reportEndCallWithUUID(uuid: String, reason: Double) {
    AhoyCallRegistry.byId(uuid)?.disconnect(remote = true)
  }

  override fun updateDisplay(uuid: String, displayName: String, handle: String) {
    AhoyCallRegistry.byId(uuid)?.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
  }

  override fun setMutedCall(uuid: String, muted: Boolean) {
    // Self-managed apps own their audio; surface the intent to JS. Actual mute is
    // the consumer's audio responsibility (no Connection.setMuted on Telecom).
    sendToggleMute(uuid, muted)
  }

  override fun setOnHold(uuid: String, hold: Boolean) {
    val connection = AhoyCallRegistry.byId(uuid) ?: return
    if (hold) connection.onHold() else connection.onUnhold()
  }

  // ---- platform-guarded ----

  override fun setAvailable(available: Boolean, promise: Promise) {
    // Self-managed accounts are auto-enabled; no availability toggle needed.
    promise.resolve(null)
  }

  override fun checkIsInManagedCall(promise: Promise) {
    // "Managed" = a SIM/other-app call. TelecomManager.isInCall needs
    // READ_PHONE_STATE (not declared), so guard and default to false.
    promise.resolve(
      try {
        telecom.isInCall
      } catch (e: SecurityException) {
        false
      }
    )
  }

  override fun isCallActive(uuid: String, promise: Promise) {
    promise.resolve(AhoyCallRegistry.byId(uuid) != null)
  }

  // ---- event emitters (called by AhoyEventBridge from Telecom callbacks) ----

  fun sendAnswerCall(uuid: String) =
    emitOnAnswerCall(Arguments.createMap().apply { putString("uuid", uuid) })

  fun sendEndCall(uuid: String) =
    emitOnEndCall(Arguments.createMap().apply { putString("uuid", uuid) })

  fun sendDisplayIncomingCall(uuid: String, handle: String) =
    emitOnDisplayIncomingCall(Arguments.createMap().apply {
      putString("uuid", uuid)
      putString("handle", handle)
      putBoolean("fromPushKit", false)
    })

  fun sendStartCallAction(uuid: String, handle: String) =
    emitOnStartCallAction(Arguments.createMap().apply {
      putString("uuid", uuid)
      putString("handle", handle)
    })

  fun sendToggleHold(uuid: String, onHold: Boolean) =
    emitOnToggleHold(Arguments.createMap().apply {
      putString("uuid", uuid)
      putBoolean("onHold", onHold)
    })

  fun sendToggleMute(uuid: String, muted: Boolean) =
    emitOnToggleMute(Arguments.createMap().apply {
      putString("uuid", uuid)
      putBoolean("muted", muted)
    })

  companion object {
    const val NAME = NativeAhoySpec.NAME

    // Stable, non-PII handle id. Telecom matches ComponentName + id; regenerating
    // it per launch would orphan in-flight calls.
    const val ACCOUNT_ID = "ahoy-self-managed-account"
  }
}
