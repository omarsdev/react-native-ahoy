package dev.omars.ahoy

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap

// T1 skeleton: every method is a stub mapped to the ticket that implements it.
// No ConnectionService / TelecomManager / foreground-service logic yet — that
// lands in T3/T5. Signatures mirror the generated NativeAhoySpec abstract class
// (android/build/.../codegen/.../NativeAhoySpec.java) exactly.
class AhoyModule(reactContext: ReactApplicationContext) :
  NativeAhoySpec(reactContext) {

  override fun getName(): String = NAME

  // ---- lifecycle (both platforms) ----

  override fun setup(options: ReadableMap, promise: Promise) {
    // TODO(T5): register self-managed PhoneAccount (MANAGE_OWN_CALLS)
    promise.resolve(null)
  }

  override fun startCall(opts: ReadableMap, promise: Promise) {
    // TODO(T3): TelecomManager.placeCall with a self-managed PhoneAccountHandle
    promise.resolve(null)
  }

  override fun displayIncomingCall(opts: ReadableMap, promise: Promise) {
    // TODO(T5): TelecomManager.addNewIncomingCall + ConnectionService
    promise.resolve(null)
  }

  override fun answerIncomingCall(uuid: String) {
    // TODO(T3): map to Connection.onAnswer; then emit:
    // emitOnAnswerCall(Arguments.createMap().apply { putString("uuid", uuid) })
  }

  override fun endCall(uuid: String) {
    // TODO(T3): Connection.setDisconnected + destroy
  }

  override fun endAllCalls() {
    // TODO(T3): disconnect every tracked Connection
  }

  override fun rejectCall(uuid: String) {
    // TODO(T3): Connection.onReject
  }

  override fun reportEndCallWithUUID(uuid: String, reason: Double) {
    // TODO(T3): Connection.setDisconnected with a DisconnectCause from the reason code
  }

  override fun reportConnectedOutgoingCall(uuid: String) {
    // TODO(T3): Connection.setActive once the outgoing call connects
  }

  override fun updateDisplay(uuid: String, displayName: String, handle: String) {
    // TODO(T3): Connection.setCallerDisplayName / setAddress
  }

  override fun setMutedCall(uuid: String, muted: Boolean) {
    // TODO(T3): Connection.onMuteStateChanged
  }

  override fun setOnHold(uuid: String, hold: Boolean) {
    // TODO(T3): Connection.onHold / onUnhold
  }

  // ---- platform-guarded (Android-only) ----

  override fun setAvailable(available: Boolean, promise: Promise) {
    // TODO(T3): toggle self-managed PhoneAccount availability
    promise.resolve(null)
  }

  override fun checkIsInManagedCall(promise: Promise) {
    // TODO(T3): query TelecomManager.isInManagedCall
    promise.resolve(false)
  }

  // ---- platform-guarded (iOS-only: no-op stub on Android) ----

  override fun isCallActive(uuid: String, promise: Promise) {
    // iOS-only (CXCallObserver). No-op on Android.
    promise.resolve(false)
  }

  companion object {
    const val NAME = NativeAhoySpec.NAME
  }
}
