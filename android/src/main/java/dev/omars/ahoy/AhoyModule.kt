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
    // Native protector: one outgoing call at a time. Refuse a new placeCall while
    // another call exists or one is already being placed (rapid taps / stacking).
    if (!AhoyCallRegistry.tryBeginOutgoing()) {
      AhoyLog.d("startCall BLOCKED: already in a call / placing uuid=$uuid")
      return promise.reject("ahoy_busy", "already in a call")
    }
    try {
      val accountHandle = phoneAccountHandle()
      if (!telecom.isOutgoingCallPermitted(accountHandle)) {
        AhoyCallRegistry.endOutgoingAttempt()
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
      telecom.placeCall(uri, extras) // -> onCreateOutgoingConnection (clears the placing flag)
      promise.resolve(null)
    } catch (e: SecurityException) {
      AhoyCallRegistry.endOutgoingAttempt()
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

  // Returns the platform push token: FCM token on Android (iOS returns its VoIP
  // token). Resolves null if firebase-messaging isn't on the consumer's classpath.
  // Retries on SERVICE_NOT_AVAILABLE — the first getToken() right after install
  // commonly fails before Play Services is ready, then succeeds seconds later.
  override fun getVoipPushToken(promise: Promise) {
    fetchFcmToken(promise, retriesLeft = 4)
  }

  private fun fetchFcmToken(promise: Promise, retriesLeft: Int) {
    try {
      com.google.firebase.messaging.FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token -> promise.resolve(token) }
        .addOnFailureListener { e ->
          if (retriesLeft > 0) {
            AhoyLog.d("getVoipPushToken failed (${e.message}); retrying, $retriesLeft left")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
              { fetchFcmToken(promise, retriesLeft - 1) },
              2000
            )
          } else {
            AhoyLog.d("getVoipPushToken giving up: ${e.message}")
            promise.reject("ahoy_fcm", e)
          }
        }
    } catch (e: Throwable) {
      AhoyLog.d("getVoipPushToken: firebase-messaging unavailable (${e.message})")
      promise.resolve(null)
    }
  }

  // ---- T5: full-screen-intent permission (Android 14+) ----

  // Android 14+ auto-grants USE_FULL_SCREEN_INTENT only to calling/alarm apps. A
  // self-managed calling app usually qualifies, but the grant is Play-reviewed
  // per consuming app, so always check and let JS prompt + deep-link if missing.
  override fun canUseFullScreenIntent(promise: Promise) {
    promise.resolve(AhoyIncomingUi.canUseFullScreenIntent(reactApplicationContext))
  }

  override fun openFullScreenIntentSettings() {
    if (android.os.Build.VERSION.SDK_INT < 34) return
    val intent = android.content.Intent(
      android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
      Uri.fromParts("package", reactApplicationContext.packageName, null)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      reactApplicationContext.startActivity(intent)
    } catch (e: Exception) {
      AhoyLog.d("openFullScreenIntentSettings failed: ${e.message}")
    }
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

  fun sendVoipPushToken(token: String) =
    emitOnVoipPushToken(Arguments.createMap().apply { putString("token", token) })

  fun sendFullScreenIntentNotGranted() = emitOnFullScreenIntentNotGranted()

  companion object {
    const val NAME = NativeAhoySpec.NAME

    // Stable, non-PII handle id. Telecom matches ComponentName + id; regenerating
    // it per launch would orphan in-flight calls.
    const val ACCOUNT_ID = "ahoy-self-managed-account"
  }
}
