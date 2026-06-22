package dev.omars.ahoy

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

// System Telecom binds this (see AndroidManifest). It turns placeCall /
// addNewIncomingCall requests into self-managed Connections.
class AhoyConnectionService : ConnectionService() {

  override fun onCreateOutgoingConnection(
    handle: PhoneAccountHandle?,
    request: ConnectionRequest?
  ): Connection {
    val connection = buildSelfManagedConnection()
    connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
    val uuid = uuidFrom(request, TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
    AhoyCallRegistry.register(uuid, connection)
    connection.setDialing()
    if (uuid.isEmpty()) AhoyLog.d("WARN onCreateOutgoingConnection got empty uuid (extras=${request?.extras})")
    // Show the ongoing-call notification for the whole outgoing call (dialing →
    // active), not just once it connects. Foreground-start is allowed here because
    // placeCall was triggered by the foregrounded app.
    AhoyCallForegroundService.start(applicationContext)
    AhoyLog.d("onCreateOutgoingConnection uuid=$uuid -> setDialing + start FGS")
    AhoyEventBridge.startCallAction(uuid, request?.address?.schemeSpecificPart ?: "")
    return connection
  }

  override fun onCreateOutgoingConnectionFailed(
    handle: PhoneAccountHandle?,
    request: ConnectionRequest?
  ) {
    AhoyLog.d("onCreateOutgoingConnectionFailed (check EXTRA_PHONE_ACCOUNT_HANDLE / isOutgoingCallPermitted)")
    AhoyEventBridge.end(uuidFrom(request, TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS))
  }

  override fun onCreateIncomingConnection(
    handle: PhoneAccountHandle?,
    request: ConnectionRequest?
  ): Connection {
    val connection = buildSelfManagedConnection()
    connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
    stringFrom(request, TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, EXTRA_DISPLAY_NAME)?.let {
      connection.setCallerDisplayName(it, TelecomManager.PRESENTATION_ALLOWED)
    }
    val uuid = uuidFrom(request, TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
    AhoyCallRegistry.register(uuid, connection)
    connection.setRinging()
    AhoyLog.d("onCreateIncomingConnection uuid=$uuid -> setRinging")
    return connection
  }

  override fun onCreateIncomingConnectionFailed(
    handle: PhoneAccountHandle?,
    request: ConnectionRequest?
  ) {
    AhoyLog.d("onCreateIncomingConnectionFailed (check PROPERTY_SELF_MANAGED / MANAGE_OWN_CALLS / FGS within ~5s)")
    AhoyEventBridge.end(uuidFrom(request, TelecomManager.EXTRA_INCOMING_CALL_EXTRAS))
  }

  // PROPERTY_SELF_MANAGED is load-bearing: it tells Telecom we own the call UI.
  // Without it Telecom falls back to managed behavior and incoming creation fails.
  private fun buildSelfManagedConnection(): AhoyConnection {
    val c = AhoyConnection(applicationContext)
    c.connectionProperties = Connection.PROPERTY_SELF_MANAGED
    c.connectionCapabilities =
      Connection.CAPABILITY_HOLD or Connection.CAPABILITY_SUPPORT_HOLD
    c.audioModeIsVoip = true
    c.setInitialized()
    return c
  }

  private fun uuidFrom(request: ConnectionRequest?, nestedKey: String): String =
    stringFrom(request, nestedKey, EXTRA_UUID) ?: ""

  // Telecom delivers placeCall's EXTRA_OUTGOING_CALL_EXTRAS flattened into
  // request.extras, but addNewIncomingCall's EXTRA_INCOMING_CALL_EXTRAS stays
  // nested. Read both ways so outgoing and incoming both resolve our keys.
  private fun stringFrom(
    request: ConnectionRequest?,
    nestedKey: String,
    key: String
  ): String? {
    val extras = request?.extras ?: return null
    return extras.getString(key) ?: extras.getBundle(nestedKey)?.getString(key)
  }

  companion object {
    const val EXTRA_UUID = "dev.omars.ahoy.UUID"
    const val EXTRA_DISPLAY_NAME = "dev.omars.ahoy.DISPLAY_NAME"
  }
}
