package dev.omars.ahoy

import java.util.concurrent.ConcurrentHashMap

// Maps our JS-facing call uuid <-> the live AhoyConnection. Telecom hands back
// Connection objects, but JS speaks in uuids, so we bridge the two here.
// Single source of truth for "is any call active?" (drives FGS start/stop).
object AhoyCallRegistry {

  private val byId = ConcurrentHashMap<String, AhoyConnection>()

  fun register(uuid: String, connection: AhoyConnection) {
    byId[uuid] = connection
  }

  fun byId(uuid: String): AhoyConnection? = byId[uuid]

  fun idOf(connection: AhoyConnection): String? =
    byId.entries.firstOrNull { it.value === connection }?.key

  fun remove(uuid: String) {
    byId.remove(uuid)
  }

  fun uuids(): List<String> = byId.keys.toList()

  fun isEmpty(): Boolean = byId.isEmpty()
}
