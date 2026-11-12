package com.kyant.backdrop.catalog

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

internal object BatteryAssist {
    /** Doze whitelist + no background restriction + run-in-background app-op allowed. */
    fun isFullyAllowed(context: Context): Boolean {
        val pkg = context.packageName
        val ignoring = runCatching {
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(pkg)
        }.getOrDefault(false)
        val restricted = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
            } else false
        }.getOrDefault(false)
        val opAllowed = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(
                    "android:run_in_background",
                    context.applicationInfo.uid,
                    pkg
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    "android:run_in_background",
                    context.applicationInfo.uid,
                    pkg
                )
            }
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        }.getOrDefault(true)
        return ignoring && !restricted && opAllowed
    }

    fun openSettings(context: Context) {
        val pkg = context.packageName
        val oem = oemBatteryIntent(context, pkg)
        if (oem != null && runCatching { context.startActivity(oem) }.isSuccess) return
        val ignoring = runCatching {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
        }.getOrDefault(false)
        val intent = if (!ignoring) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun oemBatteryIntent(context: Context, pkg: String): Intent? {
        val candidates = buildList {
            if (Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.equals("Redmi", true)) {
                add(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            }
            if (Build.MANUFACTURER.equals("OPPO", true) || Build.MANUFACTURER.equals("OnePlus", true) ||
                Build.MANUFACTURER.equals("realme", true)
            ) {
                add(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
            }
        }
        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .putExtra("package_name", pkg)
                .putExtra("extra_pkgname", pkg)
                .putExtra("pkg_name", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return null
    }
}
