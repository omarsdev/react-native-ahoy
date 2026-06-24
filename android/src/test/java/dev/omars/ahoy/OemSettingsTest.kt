package dev.omars.ahoy

import org.junit.Assert.assertEquals
import org.junit.Test

// T6: the OEM classification + candidate-fallback logic is the part that must NOT
// regress when ROM component strings are patched. Build.MANUFACTURER can't be set
// on the JVM, so we test the pure manufacturerKey(raw) overload + the candidate map.
class OemSettingsTest {

  @Test
  fun classifies_known_manufacturers() {
    val cases = mapOf(
      "Xiaomi" to "xiaomi",
      "Redmi" to "xiaomi",
      "POCO" to "xiaomi",
      "OPPO" to "oppo",
      "realme" to "oppo",
      "OnePlus" to "oneplus",
      "vivo" to "vivo",
      "iQOO" to "vivo",
      "HUAWEI" to "huawei",
      "Honor" to "honor",
      "samsung" to "samsung",
      "asus" to "asus",
      "LeMobile" to "generic", // "letv" token not present in this brand string
      "Nokia" to "nokia"
    )
    cases.forEach { (raw, key) ->
      assertEquals("manufacturerKey(\"$raw\")", key, OemSettings.manufacturerKey(raw))
    }
  }

  @Test
  fun unknown_and_empty_fall_back_to_generic() {
    assertEquals("generic", OemSettings.manufacturerKey("Google"))
    assertEquals("generic", OemSettings.manufacturerKey("Fairphone"))
    assertEquals("generic", OemSettings.manufacturerKey(""))
  }

  @Test
  fun classification_is_case_insensitive() {
    assertEquals("samsung", OemSettings.manufacturerKey("SAMSUNG"))
    assertEquals("xiaomi", OemSettings.manufacturerKey("xIAomI"))
  }

  @Test
  fun oem_keys_have_candidates_generic_has_none() {
    // Every classified OEM must have at least one autostart candidate to try…
    listOf("xiaomi", "oppo", "oneplus", "vivo", "huawei", "honor", "samsung", "asus", "letv", "nokia")
      .forEach { key ->
        assert(OemSettings.candidateCountFor(key) >= 1) { "$key should have >=1 candidate" }
      }
    // …and the generic bucket must have none (so resolve() returns null → app-details fallback).
    assertEquals(0, OemSettings.candidateCountFor("generic"))
  }

  @Test
  fun samsung_and_oppo_ship_ordered_fallbacks() {
    // Real-device finding: Samsung's first candidate is stale on some One UI builds,
    // so the second must exist as the fallback the resolver skips to.
    assert(OemSettings.candidateCountFor("samsung") >= 2)
    assert(OemSettings.candidateCountFor("oppo") >= 2)
  }
}
