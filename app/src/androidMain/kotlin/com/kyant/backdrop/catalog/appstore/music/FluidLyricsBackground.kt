package com.kyant.backdrop.catalog.appstore.music

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Animated fluid cover backdrop for the immersive lyric page.
 *
 * The cover is sampled by an AGSL domain-warp shader; there is no video and no
 * pre-rendered loop. A separate blur layer softens the result into a wallpaper.
 */
@Composable
internal fun FluidLyricsBackground(
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    val tuning = MusicServices.state
    var cover by remember(coverUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(coverUrl) {
        cover = coverUrl?.let { url ->
            runCatching { MusicServices.bridge.image(url) }.getOrNull()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF090B10))) {
        when {
            cover != null && Build.VERSION.SDK_INT >= 33 -> {
                AnimatedFluidCover(
                    cover = cover!!,
                    blurDp = tuning.fluidBlurDp,
                    warp = tuning.fluidWarp,
                    speed = tuning.fluidSpeed,
                    modifier = Modifier.fillMaxSize()
                )
            }
            cover != null -> {
                FallbackCover(cover!!, Modifier.fillMaxSize())
            }
            else -> {
                AnimatedFallbackGradient(Modifier.fillMaxSize())
            }
        }

        // Keep the white lyric text readable without flattening the animation.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.52f * tuning.fluidDark),
                        0.28f to Color.Black.copy(alpha = 0.14f * tuning.fluidDark),
                        0.58f to Color.Black.copy(alpha = 0.10f * tuning.fluidDark),
                        0.82f to Color.Black.copy(alpha = 0.34f * tuning.fluidDark),
                        1.00f to Color.Black.copy(alpha = 0.72f * tuning.fluidDark)
                    )
                )
        )
    }
}

@Composable
private fun AnimatedFluidCover(
    cover: ImageBitmap,
    blurDp: Float,
    warp: Float,
    speed: Float,
    modifier: Modifier
) {
    val bitmap = remember(cover) { cover.asAndroidBitmap() }
    val shader = remember { RuntimeShader(FLUID_AGSL) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .blur(blurDp.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height) * 1.10f
        val dx = (size.width - bitmap.width * scale) * 0.5f
        val dy = (size.height - bitmap.height * scale) * 0.5f
        val contentShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            })
        }
        shader.setInputShader("uContent", contentShader)
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uWarp", warp)
        shader.setFloatUniform("uSpeed", speed)
        paint.shader = shader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
private fun FallbackCover(cover: ImageBitmap, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "cover-fallback")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cover-drift"
    )
    Image(
        bitmap = cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer {
                scaleX = 1.10f + drift * 0.05f
                scaleY = 1.10f + drift * 0.05f
                translationX = (drift - 0.5f) * -28f
                translationY = (drift - 0.5f) * 20f
            }
            .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier)
    )
}

@Composable
private fun AnimatedFallbackGradient(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "gradient-fallback")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradient-shift"
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF18233A),
                    Color(0xFF40263B),
                    Color(0xFF1B2C36),
                    Color(0xFF0B0D14)
                ),
                start = Offset(0f, -200f + shift * 500f),
                end = Offset(900f, 2200f - shift * 500f)
            )
        )
    )
}

private const val FLUID_AGSL = """
uniform shader uContent;
uniform float2 uSize;
uniform float uTime;
uniform float uWarp;
uniform float uSpeed;

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise21(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise21(p);
        p = p * 2.03 + 17.0;
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime * uSpeed * 0.11;
    float2 p = uv * 2.35;

    float n1 = fbm(p + float2(t * 0.9, -t * 0.6));
    float n2 = fbm(p * 1.7 + float2(-t * 0.7, t * 0.8) + n1);
    float2 warp = (float2(n1, n2) - 0.5) * 0.34 * uWarp;
    warp += float2(sin(uv.y * 7.0 + t * 2.4), cos(uv.x * 6.0 - t * 1.9)) * 0.032 * uWarp;

    float2 suv = clamp(uv + warp, 0.002, 0.998);
    half4 color = uContent.eval(suv * uSize);
    float breathe = 0.82 + 0.18 * sin((uv.x + uv.y) * 2.6 + t * 1.8);
    color.rgb *= breathe;
    return color;
}
""";
