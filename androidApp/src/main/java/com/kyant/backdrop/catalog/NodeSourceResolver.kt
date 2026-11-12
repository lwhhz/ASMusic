package com.kyant.backdrop.catalog

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal data class NodeScriptResult(
    val id: String,
    val url: String?,
    val error: String?,
    val latencyMs: Long
)

/** Android-side client for the persistent Node source server. */
internal object NodeSourceResolver {
    private const val TAG = "NodeResolver"
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(
        context: Context,
        sources: List<Pair<String, String>>,
        songJson: String,
        quality: String
    ): List<NodeScriptResult>? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext emptyList()
        if (!NodeRuntime.ensure(context)) {
            Log.e(TAG, "node unhealthy: ${NodeRuntime.lastError}")
            return@withContext null
        }
        val dir = File(context.filesDir, "node_sources").apply { mkdirs() }
        val entries = JSONArray()
        sources.forEach { (id, content) ->
            val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(dir, "$safe.js")
            if (!file.exists() || file.length() != content.toByteArray().size.toLong() || file.readText() != content) {
                file.writeText(content)
            }
            entries.put(JSONObject().put("id", id).put("path", file.absolutePath))
        }
        val body = JSONObject()
            .put("song", JSONObject(songJson))
            .put("quality", quality)
            .put("sources", entries)
            .toString()
        val request = Request.Builder()
            .url("http://127.0.0.1:32123/resolve")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "resolve http ${response.code}")
                    return@use null
                }
                val json = JSONObject(response.body?.string() ?: return@use null)
                val results = json.optJSONArray("results") ?: return@use emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val item = results.optJSONObject(i) ?: return@mapNotNull null
                    NodeScriptResult(
                        id = item.optString("id"),
                        url = item.optString("url").takeIf { it.isNotBlank() },
                        error = item.optString("error").takeIf { it.isNotBlank() },
                        latencyMs = item.optLong("latencyMs", 0L)
                    )
                }
            }
        }.getOrNull()
    }
}
