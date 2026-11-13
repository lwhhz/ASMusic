package com.kyant.backdrop.catalog

import android.content.Context
import com.kyant.backdrop.catalog.appstore.music.PlaylistKind
import com.kyant.backdrop.catalog.appstore.music.snapUiScale
import org.json.JSONArray
import org.json.JSONObject

/** Local settings + login cookie + imported JS sources. */
private const val DEFAULT_TTML_SOURCE = "https://amlldb.bikonoo.com/%p/%s.ttml"

class AppConfig(context: Context) {

    data class ScriptRecord(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val enabled: Boolean,
        val content: String,
        val catalogId: String = "",
        val sourceUrl: String = ""
    )

    /** Stable fake client identity, generated once per install. */
    data class DeviceProfile(
        val nuid: String,
        val wnmcid: String,
        val deviceId: String,
        val createdAt: Long
    )

    private val prefs = context.getSharedPreferences("music_app", Context.MODE_PRIVATE)

    /**
     * true  = talk to music.163.com directly (default, works on the phone alone)
     * false = go through a self-hosted NeteaseCloudMusicApi proxy
     */
    var directMode: Boolean
        get() = prefs.getBoolean("direct_mode", true)
        set(value) { prefs.edit().putBoolean("direct_mode", value).apply() }

    var serverUrl: String
        get() = prefs.getString("server_url", "http://127.0.0.1:3000") ?: "http://127.0.0.1:3000"
        set(value) { prefs.edit().putString("server_url", value.trim()).apply() }

    val deviceProfile: DeviceProfile
        get() {
            val stored = prefs.getString("device_profile", null)
            if (stored != null) {
                val parts = stored.split(",")
                if (parts.size == 4) {
                    return DeviceProfile(
                        nuid = parts[0],
                        wnmcid = parts[1],
                        deviceId = parts[2],
                        createdAt = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                    )
                }
            }
            val hex = "0123456789abcdef"
            fun rand(n: Int) = buildString { repeat(n) { append(hex.random()) } }
            val profile = DeviceProfile(
                nuid = rand(32),
                wnmcid = buildString { repeat(6) { append(('a'..'z').random()) } },
                deviceId = rand(16),
                createdAt = System.currentTimeMillis()
            )
            prefs.edit()
                .putString(
                    "device_profile",
                    "${profile.nuid},${profile.wnmcid},${profile.deviceId},${profile.createdAt}"
                )
                .apply()
            return profile
        }

    var cookie: String?
        get() = prefs.getString("cookie", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("cookie", value).apply() }

    var lastQrKey: String?
        get() = prefs.getString("last_qr_key", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("last_qr_key", value).apply() }

    var todayKinds: List<PlaylistKind>
        get() = prefs.getString("today_kinds", null)
            ?.split(",")
            ?.mapNotNull { runCatching { PlaylistKind.valueOf(it) }.getOrNull() }
            ?.ifEmpty { null }
            ?: listOf(
                PlaylistKind.Daily,
                PlaylistKind.PrivateFm,
                PlaylistKind.Exclusive,
                PlaylistKind.Liked
            )
        set(value) {
            prefs.edit().putString("today_kinds", value.joinToString(",") { it.name }).apply()
        }

    /** Playback capsule docked to the left edge instead of the right one. */
    var capsuleOnLeft: Boolean
        get() = prefs.getBoolean("capsule_on_left", false)
        set(value) { prefs.edit().putBoolean("capsule_on_left", value).apply() }

    /**
     * Corner radius of the capsule's expanded form on the song detail page.
     * 1 = full capsule, smaller = rounded rectangle.
     */
    var capsuleCorner: Float
        get() = prefs.getFloat("capsule_corner", 1f)
        set(value) { prefs.edit().putFloat("capsule_corner", value.coerceIn(0f, 1f)).apply() }

    /** Song-detail sticky header style: Solid or GradientBlur. */
    var hazeHeightDp: Float
        get() = prefs.getFloat("haze_height_dp", 262f).coerceIn(64f, 420f)
        set(value) { prefs.edit().putFloat("haze_height_dp", value.coerceIn(64f, 420f)).apply() }

    var hazeOpaqueDp: Float
        get() = prefs.getFloat("haze_opaque_dp", 150f).coerceIn(64f, 320f)
        set(value) { prefs.edit().putFloat("haze_opaque_dp", value.coerceIn(64f, 320f)).apply() }

    var hazeBlurDp: Float
        get() = prefs.getFloat("haze_blur_dp", 80f).coerceIn(0f, 140f)
        set(value) { prefs.edit().putFloat("haze_blur_dp", value.coerceIn(0f, 140f)).apply() }

    var uiScale: Float
        get() = snapUiScale(prefs.getFloat("ui_scale", 1f))
        set(value) { prefs.edit().putFloat("ui_scale", snapUiScale(value)).apply() }

    var hazeScrim: Float
        get() = prefs.getFloat("haze_scrim", 0.55f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat("haze_scrim", value.coerceIn(0f, 1f)).apply() }

    var detailHideTabs: Boolean
        get() = prefs.getBoolean("detail_hide_tabs", false)
        set(value) { prefs.edit().putBoolean("detail_hide_tabs", value).apply() }

    var detailTopStyle: String
        get() = prefs.getString("detail_top_style", "Solid") ?: "Solid"
        set(value) { prefs.edit().putString("detail_top_style", value).apply() }

    /** Weekly listening goal in minutes. */
    var weekGoalMinutes: Int
        get() = prefs.getInt("week_goal_minutes", 700)
        set(value) { prefs.edit().putInt("week_goal_minutes", value).apply() }

    /** Highest default quality on Wi-Fi / mobile data (AudioQuality.level). */
    var wifiQuality: String
        get() = prefs.getString("wifi_quality", "lossless") ?: "lossless"
        set(value) { prefs.edit().putString("wifi_quality", value).apply() }

    var mobileQuality: String
        get() = prefs.getString("mobile_quality", "exhigh") ?: "exhigh"
        set(value) { prefs.edit().putString("mobile_quality", value).apply() }

    /** Playback order: Sequential / LoopAll / RepeatOne / Shuffle. */
    var playMode: String
        get() = prefs.getString("play_mode", "LoopAll") ?: "LoopAll"
        set(value) { prefs.edit().putString("play_mode", value).apply() }

    // ---- lyric preferences ----
    var wordByWord: Boolean
        get() = prefs.getBoolean("lyric_word_by_word", false)
        set(value) { prefs.edit().putBoolean("lyric_word_by_word", value).apply() }

    var showTranslation: Boolean
        get() = prefs.getBoolean("lyric_translation", true)
        set(value) { prefs.edit().putBoolean("lyric_translation", value).apply() }

    var lyricGlow: Float
        get() = prefs.getFloat("lyric_glow", 0.55f)
        set(value) { prefs.edit().putFloat("lyric_glow", value).apply() }

    var lyricSpring: Float
        get() = prefs.getFloat("lyric_spring", 0.5f)
        set(value) { prefs.edit().putFloat("lyric_spring", value).apply() }

    var audioCacheLimitMb: Int
        get() = prefs.getInt("audio_cache_limit_mb", 512).coerceIn(512, 20480)
        set(value) { prefs.edit().putInt("audio_cache_limit_mb", value.coerceIn(512, 20480)).apply() }

    var fluidBlurDp: Float
        get() = prefs.getFloat("fluid_blur_dp", 30f).coerceIn(0f, 64f)
        set(value) { prefs.edit().putFloat("fluid_blur_dp", value.coerceIn(0f, 64f)).apply() }

    var fluidWarp: Float
        get() = prefs.getFloat("fluid_warp", 1.4f).coerceIn(0f, 3f)
        set(value) { prefs.edit().putFloat("fluid_warp", value.coerceIn(0f, 3f)).apply() }

    var fluidSpeed: Float
        get() = prefs.getFloat("fluid_speed", 1f).coerceIn(0.1f, 3f)
        set(value) { prefs.edit().putFloat("fluid_speed", value.coerceIn(0.1f, 3f)).apply() }

    var fluidDark: Float
        get() = prefs.getFloat("fluid_dark", 0.5f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat("fluid_dark", value.coerceIn(0f, 1f)).apply() }

    var lyricFlow: Boolean
        get() = prefs.getBoolean("lyric_flow", false)
        set(value) { prefs.edit().putBoolean("lyric_flow", value).apply() }

    var lyricFont: Float
        get() = prefs.getFloat("lyric_font", 23f)
        set(value) { prefs.edit().putFloat("lyric_font", value).apply() }

    // ---- immersive (AMLL) typography ----
    var amllFontSize: Float
        get() = prefs.getFloat("amll_font_size", 34f).coerceIn(20f, 64f)
        set(value) { prefs.edit().putFloat("amll_font_size", value.coerceIn(20f, 64f)).apply() }

    var amllFontWeight: Float
        get() = prefs.getFloat("amll_font_weight", 700f).coerceIn(300f, 900f)
        set(value) { prefs.edit().putFloat("amll_font_weight", value.coerceIn(300f, 900f)).apply() }

    /** 0 = system, 1 = sans, 2 = serif, 3 = monospace, 4 = rounded (best effort). */
    var amllFontFamily: Int
        get() = prefs.getInt("amll_font_family", 0).coerceIn(0, 4)
        set(value) { prefs.edit().putInt("amll_font_family", value.coerceIn(0, 4)).apply() }

    /** Master switch for the CC0 TTML lyrics; off = Netease lyric only. */
    var amllTtmlEnabled: Boolean
        get() = prefs.getBoolean("amll_ttml_enabled", true)
        set(value) { prefs.edit().putBoolean("amll_ttml_enabled", value).apply() }

    /** `%p` = directory (ncm-lyrics / raw-lyrics), `%s` = file name without extension. */
    var amllTtmlSource: String
        get() = prefs.getString("amll_ttml_source", DEFAULT_TTML_SOURCE) ?: DEFAULT_TTML_SOURCE
        set(value) {
            val text = value.trim().ifEmpty { DEFAULT_TTML_SOURCE }
            prefs.edit().putString("amll_ttml_source", text).apply()
        }

    /** Imported font file (absolute path), empty = use the system family below. */
    var amllFontFile: String
        get() = prefs.getString("amll_font_file", "") ?: ""
        set(value) { prefs.edit().putString("amll_font_file", value).apply() }

    var amllMaskProfanity: Boolean
        get() = prefs.getBoolean("amll_mask_profanity", true)
        set(value) { prefs.edit().putBoolean("amll_mask_profanity", value).apply() }

    /** Seconds the lyric view keeps the user's scroll before snapping back. */
    /** Download the next queue song into the audio cache while this one plays. */
    /** Keep the AMLL WebView (and its Pixi background) warm while playing. */
    var keepAmllWarm: Boolean
        get() = prefs.getBoolean("keep_amll_warm", true)
        set(value) { prefs.edit().putBoolean("keep_amll_warm", value).apply() }

    /** Estimate per-character timings when the lyric source has no word data. */
    var lyricSynthWords: Boolean
        get() = prefs.getBoolean("lyric_synth_words", true)
        set(value) { prefs.edit().putBoolean("lyric_synth_words", value).apply() }

    /** Keep the already sung part highlighted instead of fading it back. */
    var lyricKeepSung: Boolean
        get() = prefs.getBoolean("lyric_keep_sung", true)
        set(value) { prefs.edit().putBoolean("lyric_keep_sung", value).apply() }

    /**
     * Experimental third-party word-level lyric source (Kugou KRC). Off by default:
     * it talks to Kugou's public lyric endpoints and the data belongs to them.
     */
    var kugouKrcEnabled: Boolean
        get() = prefs.getBoolean("kugou_krc_enabled", false)
        set(value) { prefs.edit().putBoolean("kugou_krc_enabled", value).apply() }

    /** 心动模式：在红心歌单播放时穿插算法推荐（实验性，默认关闭）。 */
    var heartMode: Boolean
        get() = prefs.getBoolean("heart_mode", false)
        set(value) { prefs.edit().putBoolean("heart_mode", value).apply() }

    var prefetchNext: Boolean
        get() = prefs.getBoolean("prefetch_next", true)
        set(value) { prefs.edit().putBoolean("prefetch_next", value).apply() }

    /** Buffer the whole track into the cache before playback starts. */
    var waitForCache: Boolean
        get() = prefs.getBoolean("wait_for_cache", true)
        set(value) { prefs.edit().putBoolean("wait_for_cache", value).apply() }

    var amllResumeDelay: Float
        get() = prefs.getFloat("amll_resume_delay_v2", 1.5f).coerceIn(0f, 10f)
        set(value) { prefs.edit().putFloat("amll_resume_delay_v2", value.coerceIn(0f, 10f)).apply() }

    var translationFont: Float
        get() = prefs.getFloat("lyric_translation_font", 15f)
        set(value) { prefs.edit().putFloat("lyric_translation_font", value).apply() }

    var scripts: List<ScriptRecord>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("scripts", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ScriptRecord(
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("version"),
                    o.optString("description"),
                    o.optBoolean("enabled", true),
                    o.optString("content"),
                    o.optString("catalogId"),
                    o.optString("sourceUrl")
                )
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("version", it.version)
                        .put("description", it.description)
                        .put("enabled", it.enabled)
                        .put("content", it.content)
                        .put("catalogId", it.catalogId)
                        .put("sourceUrl", it.sourceUrl)
                )
            }
            prefs.edit().putString("scripts", arr.toString()).apply()
        }
}
