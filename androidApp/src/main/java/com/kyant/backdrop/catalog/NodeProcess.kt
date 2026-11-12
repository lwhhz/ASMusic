package com.kyant.backdrop.catalog

import android.content.Context
import android.util.Log
import java.io.File

/** Child Node process; owned by NodeService. */
internal object NodeProcess {
    private const val PORT = 32123
    private const val ASSET = "node_source_server.js"
    @Volatile private var process: Process? = null

    fun start(context: Context) {
        synchronized(this) {
            if (process?.isAlive == true) return
            runCatching {
                val nativeDir = File(context.applicationInfo.nativeLibraryDir)
                val executable = File(nativeDir, "libnode_exec.so").apply { setExecutable(true) }
                if (!executable.exists()) return
                val script = File(context.filesDir, ASSET)
                val asset = context.assets.open(ASSET).use { it.readBytes() }
                if (!script.exists() || !script.readBytes().contentEquals(asset)) {
                    script.writeBytes(asset)
                }
                val sources = File(context.filesDir, "node_sources").apply { mkdirs() }
                val builder = ProcessBuilder(
                    executable.absolutePath,
                    script.absolutePath,
                    PORT.toString(),
                    sources.absolutePath
                )
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                builder.environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
                val child = builder.start()
                process = child
                Thread({
                    runCatching {
                        child.inputStream.bufferedReader().forEachLine { Log.i("NODEJS-MOBILE", it) }
                        child.waitFor()
                    }
                }, "node-log").apply { isDaemon = true }.start()
            }.onFailure { Log.e("NODEJS-MOBILE", "launch failed", it) }
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun stop() {
        runCatching { process?.destroy() }
        process = null
    }
}
