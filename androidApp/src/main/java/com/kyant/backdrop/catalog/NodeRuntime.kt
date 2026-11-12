package com.kyant.backdrop.catalog

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Starts the child Node resolver and waits for its localhost health endpoint. */
internal object NodeRuntime {
    private const val PORT = 32123
    @Volatile var lastError: String? = null
        private set

    fun ensure(context: Context): Boolean = synchronized(this) {
        if (healthy()) return true
        runCatching { NodeProcess.start(context) }
            .onFailure { lastError = "start node: ${it.message}" }
        val deadline = System.currentTimeMillis() + 12_000L
        while (System.currentTimeMillis() < deadline) {
            if (healthy()) return true
            try { Thread.sleep(120) } catch (_: InterruptedException) { return false }
        }
        false
    }

    fun restart(context: Context): Boolean = synchronized(this) {
        NodeProcess.stop()
        lastError = null
        ensure(context)
    }

    private fun healthy(): Boolean = runCatching {
        val connection = URL("http://127.0.0.1:$PORT/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 300
        connection.readTimeout = 300
        connection.requestMethod = "GET"
        val ok = connection.responseCode == 200
        connection.disconnect()
        ok
    }.getOrDefault(false)
}
