package dev.omars.ahoy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

// T6 (the moat): there is NO official API to disable OEM autostart / protected-apps
// / app-sleep — only vendor-proprietary Settings activities that drift per ROM. The
// single safe pattern is: normalize Build.MANUFACTURER -> ordered candidate
// ComponentNames -> resolveActivity() BEFORE startActivity() -> try/catch -> fall
// back to app-details. Component strings are community-sourced (AutoStarter /
// dontkillmyapp); keep them here so a ROM rename is a one-line patch.
object OemSettings {

  fun manufacturerKey(): String = manufacturerKey(Build.MANUFACTURER ?: "")

  // Pure classification (no Build access) so it's unit-testable on the JVM.
  fun manufacturerKey(raw: String): String =
    raw.lowercase().let {
      when {
        it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") -> "xiaomi"
        it.contains("oppo") || it.contains("realme") -> "oppo"
        it.contains("oneplus") -> "oneplus"
        it.contains("vivo") || it.contains("iqoo") -> "vivo"
        it.contains("huawei") -> "huawei"
        it.contains("honor") -> "honor"
        it.contains("samsung") -> "samsung"
        it.contains("asus") -> "asus"
        it.contains("letv") -> "letv"
        it.contains("nokia") -> "nokia"
        else -> "generic"
      }
    }

  // For unit tests / callers that want the candidate list of a given key.
  fun candidateCountFor(key: String): Int = candidates[key]?.size ?: 0

  // Ordered candidates per OEM key — try each until one resolves on THIS ROM.
  private val candidates: Map<String, List<ComponentName>> = mapOf(
    "xiaomi" to listOf(
      cn("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    ),
    "oppo" to listOf(
      cn("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
      cn("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
      cn("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
    ),
    "oneplus" to listOf(
      cn("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
    ),
    "vivo" to listOf(
      cn("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
      cn("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    ),
    "huawei" to listOf(
      cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
      cn("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
    ),
    "honor" to listOf(
      cn("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
    ),
    "samsung" to listOf(
      cn("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
      cn("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
    ),
    "asus" to listOf(
      cn("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
    ),
    "letv" to listOf(
      cn("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
    ),
    "nokia" to listOf(
      cn("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
    )
  )

  // dontkillmyapp.com is the community-maintained per-OEM reference. Linking out
  // beats hard-coding every ROM's step-by-step (vendor UIs are undocumented).
  fun dontKillMyAppUrl(): String {
    val key = manufacturerKey()
    return if (key == "generic") "https://dontkillmyapp.com/"
    else "https://dontkillmyapp.com/$key"
  }

  // True if a known OEM autostart/protected-apps screen RESOLVES on this device.
  fun hasAutostartSettings(ctx: Context): Boolean = resolved(ctx) != null

  // Opens the OEM autostart screen; on any failure falls back to app-details and
  // returns false so the caller can show the dontkillmyapp link instead.
  fun openAutostartSettings(ctx: Context): Boolean {
    val intent = resolved(ctx)
    if (intent != null) {
      return try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        AhoyLog.d("openAutostartSettings -> ${intent.component?.flattenToShortString()}")
        true
      } catch (e: Exception) {
        AhoyLog.d("openAutostartSettings launch failed (${e.message}); app-details fallback")
        openAppDetails(ctx)
        false
      }
    }
    AhoyLog.d("openAutostartSettings: no resolvable OEM screen for ${manufacturerKey()}; app-details fallback")
    openAppDetails(ctx)
    return false
  }

  // resolve-BEFORE-launch — the core OEM safety guard (avoids ActivityNotFoundException).
  private fun resolved(ctx: Context): Intent? {
    val pm = ctx.packageManager
    candidates[manufacturerKey()]?.forEach { comp ->
      val intent = Intent().setComponent(comp)
      @Suppress("DEPRECATION")
      if (pm.resolveActivity(intent, 0) != null) return intent
    }
    return null
  }

  private fun openAppDetails(ctx: Context) {
    try {
      ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (e: Exception) {
      AhoyLog.d("openAppDetails failed: ${e.message}")
    }
  }

  private fun cn(pkg: String, cls: String) = ComponentName(pkg, cls)
}
