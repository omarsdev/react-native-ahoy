package dev.omars.ahoy

import android.telecom.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Maps our JS-facing call uuid <-> the live AhoyConnection. Telecom hands back
// Connection objects, but JS speaks in uuids, so we bridge the two here.
// Single source of truth for "is any call active?" (drives FGS start/stop).
object AhoyCallRegistry {

  private val byId = ConcurrentHashMap<String, AhoyConnection>()

  // Calls WE auto-held for call waiting. Only these are eligible for auto-resume,
  // so a never-answered (ringing) call is never silently promoted to active.
  private val autoHeld = ConcurrentHashMap.newKeySet<String>()

  // A placeCall is in flight (between startCall and the connection registering).
  private val placing = AtomicBoolean(false)

  // Native single-outgoing-call guard. Succeeds only when there is NO other call
  // and no placeCall already in flight — atomically, so rapid taps can't race
  // through the window before onCreateOutgoingConnection registers the call.
  fun tryBeginOutgoing(): Boolean = isEmpty() && placing.compareAndSet(false, true)

  fun endOutgoingAttempt() {
    placing.set(false)
  }

  fun register(uuid: String, connection: AhoyConnection) {
    byId[uuid] = connection
  }

  fun clearAutoHeld(uuid: String) {
    autoHeld.remove(uuid)
  }

  fun byId(uuid: String): AhoyConnection? = byId[uuid]

  // The current foreground call: the active one, else any (e.g. a dialing call).
  // Used by the ongoing-call notification's hang-up, which has no fixed uuid.
  fun activeConnection(): AhoyConnection? =
    byId.values.firstOrNull { it.state == Connection.STATE_ACTIVE }
      ?: byId.values.firstOrNull()

  // A call currently ringing (incoming, not yet answered), if any. Drives whether
  // the call notification shows the incoming (Answer/Decline) or ongoing style.
  fun ringingConnection(): AhoyConnection? =
    byId.values.firstOrNull { it.state == Connection.STATE_RINGING }

  fun idOf(connection: AhoyConnection): String? =
    byId.entries.firstOrNull { it.value === connection }?.key

  fun remove(uuid: String) {
    byId.remove(uuid)
    autoHeld.remove(uuid)
  }

  fun uuids(): List<String> = byId.keys.toList()

  fun isEmpty(): Boolean = byId.isEmpty()

  // Call-waiting: when one call takes focus (answered / placed / resumed), put
  // every other still-active call on hold. Self-managed apps must coordinate
  // this themselves — the framework won't auto-hold.
  fun holdAllExcept(activeUuid: String) {
    byId.forEach { (id, connection) ->
      if (id != activeUuid && connection.holdForCallWaiting()) autoHeld.add(id)
    }
  }

  // Call-waiting: after a call ends, bring back a call WE auto-held (still on
  // hold). Never touches a ringing/never-answered call. No-op if none qualify.
  fun resumeOneHeld() {
    val uuid = autoHeld.firstOrNull { byId[it]?.state == Connection.STATE_HOLDING } ?: return
    autoHeld.remove(uuid)
    byId[uuid]?.resumeFromHold()
  }
}
