package com.kyant.backdrop.catalog.appstore.music

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immersive lyric view powered by AMLL (Apple Music-like Lyrics), running inside a
 * local WebView: `assets/amll/` holds an esbuild bundle of @applemusic-like-lyrics/core
 * (AGPL-3.0) plus its Pixi renderer, and the TTML lyric comes from the CC0 amll-ttml-db.
 *
 * The app's player stays the source of truth: every position tick is pushed into the
 * page, and the page never advances time on its own.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun AmlvImmersiveLyrics(
    songId: Long,
    coverUrl: String?,
    lines: List<LyricLine>,
    positionMs: Long,
    playing: Boolean,
    style: LyricTextStyle,
    onSeek: (Long) -> Unit,
    visible: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val seekCallback = rememberUpdatedState(onSeek)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var pageReady by remember { mutableStateOf(false) }
    var ttml by remember(songId) { mutableStateOf<String?>(null) }
    var cover by remember(songId, coverUrl) { mutableStateOf<String?>(null) }
    var lrclib by remember(songId) { mutableStateOf<String?>(null) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    // TTML (CC0) + cover image are fetched off the main thread; both are disk-cached.
    val music = MusicServices.state
    LaunchedEffect(songId, coverUrl, music.amllTtmlEnabled, music.amllTtmlSource) {
        val covered = withContext(Dispatchers.IO) {
            val song = music.currentSong
            val mine = song?.takeIf { it.id == songId }
            val text = AmllSongData.ttml(
                context,
                songId,
                mine?.name.orEmpty(),
                mine?.artist.orEmpty(),
                music.amllTtmlEnabled,
                music.amllTtmlSource
            )
            // Only when Netease gave us nothing at all: ask LRCLIB for line-level lyrics.
            val fallback = if (lines.isEmpty() && text == null && mine != null) {
                AmllSongData.lrclibLines(
                    context,
                    mine.name,
                    mine.artist,
                    mine.album,
                    mine.durationMs
                )
            } else null
            Triple(text, AmllSongData.coverDataUrl(context, coverUrl), fallback)
        }
        ttml = covered.first
        cover = covered.second
        lrclib = covered.third
    }

    DisposableEffect(activity, visible) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        if (visible) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportZoom(false)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    addJavascriptInterface(
                        object {
                            /** Called by the page when the user taps a lyric line. */
                            @JavascriptInterface
                            fun seekTo(ms: Double) {
                                val target = ms.toLong().coerceAtLeast(0L)
                                mainHandler.post { seekCallback.value(target) }
                            }
                        },
                        "AmllHost"
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            pageReady = true
                        }

                        /**
                         * The page lives on `file:///android_asset`, where `file://` font
                         * URLs are blocked. Serve the imported font from a fake https
                         * origin instead so @font-face can load it.
                         */
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url?.toString().orEmpty()
                            if (!url.startsWith(FONT_ORIGIN)) return null
                            val path = MusicServices.state.amllFontFile
                            if (path.isBlank()) return null
                            val file = java.io.File(path)
                            if (!file.isFile) return null
                            val mime = if (path.endsWith(".ttf", true)) "font/ttf" else "font/otf"
                            val headers = mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Cache-Control" to "no-store"
                            )
                            return WebResourceResponse(
                                mime,
                                null,
                                200,
                                "OK",
                                headers,
                                file.inputStream()
                            )
                        }
                    }
                    loadUrl("file:///android_asset/amll/index.html")
                }.also { webView.value = it }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Lyric: TTML when the database has an entry, otherwise the Netease lines (which may
    // still carry yrc word timings).
    LaunchedEffect(pageReady, ttml, lines, lrclib) {
        if (!pageReady) return@LaunchedEffect
        val view = webView.value ?: return@LaunchedEffect
        val linesJson = lrclib ?: AmllSongData.linesJson(lines, music.lyricSynthWords)
        view.evaluateJavascript(
            "amllSetLyric(${jsString(ttml ?: "")}, ${jsString(linesJson)})",
            null
        )
    }

    LaunchedEffect(pageReady, style) {
        if (!pageReady) return@LaunchedEffect
        val view = webView.value ?: return@LaunchedEffect
        val fontUrl = if (music.amllFontFile.isNotBlank()) FONT_ORIGIN else ""
        val json = """{"size":${style.fontSize},"weight":${style.fontWeight},"family":${style.fontFamily},"resume":${style.resumeDelaySec},"fontUrl":${jsString(fontUrl)}}"""
        view.evaluateJavascript("amllSetStyle(${jsString(json)})", null)
        view.evaluateJavascript(
            "amllSetMask(${if (style.maskProfanity) "true" else "false"})",
            null
        )
        // Re-layout once the imported face has finished loading.
        view.evaluateJavascript("amllFontsReady()", null)
    }

    LaunchedEffect(pageReady, cover) {
        if (!pageReady) return@LaunchedEffect
        val view = webView.value ?: return@LaunchedEffect
        val url = cover ?: return@LaunchedEffect
        view.evaluateJavascript("amllSetCover(${jsString(url)})", null)
        view.evaluateJavascript(
            "amllSetStaticBg(${if (music.amllStaticBg) "true" else "false"})",
            null
        )
    }

    LaunchedEffect(pageReady, music.amllStaticBg) {
        if (!pageReady) return@LaunchedEffect
        val view = webView.value ?: return@LaunchedEffect
        view.evaluateJavascript(
            "amllSetStaticBg(${if (music.amllStaticBg) "true" else "false"})",
            null
        )
    }

    LaunchedEffect(pageReady, positionMs) {
        if (!pageReady) return@LaunchedEffect
        webView.value?.evaluateJavascript("amllSetTime(${positionMs.coerceAtLeast(0L)})", null)
    }

    LaunchedEffect(pageReady, playing) {
        if (!pageReady) return@LaunchedEffect
        webView.value?.evaluateJavascript("amllSetPlaying(${if (playing) "true" else "false"})", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.value?.let { view ->
                view.loadUrl("about:blank")
                view.destroy()
            }
            webView.value = null
        }
    }
}

/** JSON/JS string literal for `evaluateJavascript`. */
/** Fake origin used for the imported font (see shouldInterceptRequest). */
private const val FONT_ORIGIN = "https://amll.local/font"

private fun jsString(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\u2028' -> sb.append("\\u2028")
            '\u2029' -> sb.append("\\u2029")
            else ->
                if (ch < ' ') sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else sb.append(ch)
        }
    }
    sb.append('"')
    return sb.toString()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
