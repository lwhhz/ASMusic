package com.kyant.backdrop.catalog

import android.content.Context
import android.content.Intent
import com.kyant.backdrop.catalog.appstore.Platform
import com.kyant.backdrop.catalog.appstore.PlatformBridge

/** Android implementation of the commonMain platform services. */
class AndroidBridge(private val context: Context) : PlatformBridge {

    private val prefs by lazy {
        context.getSharedPreferences("appstore_installs", Context.MODE_PRIVATE)
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Share")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    override fun loadInstalled(): Set<String> = runCatching {
        prefs.getStringSet("installed", emptySet())?.toSet() ?: emptySet()
    }.getOrDefault(emptySet())

    override fun saveInstalled(ids: Set<String>) {
        prefs.edit().putStringSet("installed", ids).apply()
    }
}

fun installPlatformBridge(context: Context) {
    Platform.bridge = AndroidBridge(context.applicationContext)
}
