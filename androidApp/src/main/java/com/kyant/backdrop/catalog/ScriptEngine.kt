package com.kyant.backdrop.catalog

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Reads source metadata. Playback execution lives in the Node runtime. */
object ScriptEngine {

    data class Info(val name: String, val version: String, val description: String)

    private val nameRe = Regex("@name\\s+([^\\r\\n*]+)")
    private val versionRe = Regex("@version\\s+([^\\r\\n*]+)")
    private val descriptionRe = Regex("@description\\s+([^\\r\\n*]+)")

    fun info(source: String): Info? {
        val headerName = nameRe.find(source)?.groupValues?.getOrNull(1)?.trim()
        if (!headerName.isNullOrBlank()) {
            return Info(
                name = headerName,
                version = versionRe.find(source)?.groupValues?.getOrNull(1)?.trim() ?: "1.0",
                description = descriptionRe.find(source)?.groupValues?.getOrNull(1)?.trim() ?: "LX Music 音源"
            )
        }
        return runCatching {
            LxRuntime(source).use { it.simpleInfo() }
        }.getOrNull()
    }

    private const val POLYFILL =
        "var globalThis = this;\n" +
            "if (typeof JSON === 'undefined') { var JSON = { parse: function(s) { return eval('(' + s + ')'); } }; }"

    private val LX_SHIM = """
        globalThis.lx = {
          EVENT_NAMES: { request: 'request', inited: 'inited', updateAlert: 'updateAlert', error: 'error', download: 'download', log: 'log', progress: 'progress' },
          on: function() {},
          send: function() {},
          request: function() {},
          utils: {},
          env: {},
          version: '2.9.0',
          currentScriptInfo: { name: '', version: '', description: '', rawScript: '' }
        };
    """.trimIndent()

    private class LxRuntime(private val source: String) : AutoCloseable {
        private val cx: Context = Context.enter()
        private val scope: Scriptable

        init {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            scope = cx.initStandardObjects()
            cx.evaluateString(scope, POLYFILL, "polyfill.js", 1, null)
            cx.evaluateString(scope, LX_SHIM, "lx-shim.js", 1, null)
            cx.evaluateString(scope, source, "plugin.js", 1, null)
        }

        override fun close() {
            Context.exit()
        }

        fun simpleInfo(): Info? {
            val obj = cx.evaluateString(
                scope,
                "typeof sourceInfo === 'function' ? sourceInfo() : null",
                "info.js",
                1,
                null
            )
            if (obj is Scriptable) {
                return Info(
                    ScriptableObject.getProperty(obj, "name")?.toString() ?: "未命名音源",
                    ScriptableObject.getProperty(obj, "version")?.toString() ?: "1.0",
                    ScriptableObject.getProperty(obj, "description")?.toString() ?: ""
                )
            }
            return null
        }
    }
}
