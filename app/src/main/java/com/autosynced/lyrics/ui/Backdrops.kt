package com.autosynced.lyrics.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full screen's moving background, in the spirit of Apple Music's player:
 *  - "mesh": a mesh gradient, colours sampled from the cover flowing into each other
 *  - "fluid": the fluid blurred backdrop, the cover itself, blurred and enlarged, in two slowly turning layers
 *  - "glow": soft clouds of the cover's colours drifting (1.0's lively background)
 *  - "aurora": waving bands of the cover's colours
 *  - "breathing": the blurred cover slowly swelling and brightening
 *  - "none": nothing moves.
 * [speed] and [strength] come from the settings. Movement slows almost to a stop while paused.
 */
@Composable
fun Backdrop(kind: String, art: Bitmap?, playing: Boolean, speed: Float, strength: Float, modifier: Modifier = Modifier,
             brightness: Float = 1f) {
    if (kind == "none") return
    // one clock for all styles; it eases between full and paused speed instead of jumping
    val target by rememberUpdatedState(speed * if (playing) 1f else 0.15f)
    var rate by remember { mutableFloatStateOf(target) }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) {
                val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                rate += (target - rate) * (dt * 2f).coerceAtMost(1f)
                t += dt * rate
            }
            last = now
        }
    }
    val colors = remember(art) { coverColors(art) }
    val deep = remember(art) { coverColors(art, dark = true) }        // the mesh's deeper shades
    val night = LocalPalette.current.night
    val small = remember(art) { art?.let { shrink(it, 48) } }
    Box(modifier.clipToBounds()) {
        when (kind) {
            "mesh" -> Mesh(deep, t, strength)
            "fluid" -> Fluid(small, colors, t, strength)
            "glow" -> LivelyGlow(art, playing, Modifier.fillMaxSize().graphicsLayer { alpha = strength })
            "aurora" -> Aurora(colors, t, strength)
            "breathing" -> Breathing(small, colors, t, strength)
        }
        // tuned per style (light by day, dark at night, matching the main screen), then the user's brightness
        val dim = ((if (night) 0.55f else 0.15f) + (1f - brightness).coerceAtLeast(0f) * 0.6f).coerceIn(0f, 0.9f)
        val lift = (brightness - 1f).coerceAtLeast(0f) * (if (night) 0.3f else 0.45f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        if (lift > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = lift)))
    }
}

/** Five vivid colours from different parts of the cover (or a gentle default set). */
fun coverColors(art: Bitmap?, dark: Boolean = false): List<Color> {
    val fallback = listOf(Color(0xFF7C5CFF), Color(0xFFFF6B9A), Color(0xFF3FC5F0), Color(0xFFFFB86B), Color(0xFF6BE3A6))
    val bmp = art?.let { shrink(it, 3) } ?: return fallback
    val spots = listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
    return spots.map { (x, y) ->
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(bmp.getPixel(x, y), hsv)
        // a little more colour and light than the raw pixel, so the background feels alive, not muddy
        val v = (hsv[2] * 1.1f + 0.15f).coerceIn(0.35f, 1f)
        Color.hsv(hsv[0], (hsv[1] * 1.25f + 0.1f).coerceIn(0f, 0.95f), if (dark) v * 0.62f else v)
    }.let { if (dark && art == null) it.map { c -> c.copy(red = c.red * 0.62f, green = c.green * 0.62f, blue = c.blue * 0.62f) } else it }
}

private fun shrink(src: Bitmap, size: Int): Bitmap? = try {
    val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
    Bitmap.createScaledBitmap(soft, size, size, true)
} catch (_: Exception) { null }

/** Mesh gradient: each colour is a large soft blob travelling its own slow loop, blended over the others. */
@Composable
private fun Mesh(colors: List<Color>, t: Float, strength: Float) {
    Canvas(Modifier.fillMaxSize().blur(40.dp, BlurredEdgeTreatment.Rectangle)) {
        val w = size.width; val h = size.height
        drawRect(colors[2].copy(alpha = 0.55f * strength))
        for ((i, c) in colors.withIndex()) {
            val a = t * 0.11f * (1f + i * 0.17f) + i * 1.3f
            val p = Offset(w * (0.5f + 0.38f * sin(a * 1.3f + i)), h * (0.5f + 0.4f * cos(a + i * 0.7f)))
            val r = size.maxDimension * (0.45f + 0.08f * sin(a * 0.8f))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.85f * strength), Color.Transparent), p, r), r, p)
        }
    }
}

/** Fluid blurred backdrop: the cover, enlarged and blurred, in two layers turning slowly in opposite directions. */
@Composable
private fun Fluid(small: Bitmap?, colors: List<Color>, t: Float, strength: Float) {
    if (small == null) { Mesh(colors, t, strength); return }
    val img = remember(small) { small.asImageBitmap() }
    val vivid = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }) }
    Box(Modifier.fillMaxSize().blur(60.dp, BlurredEdgeTreatment.Rectangle)) {
        Image(img, null, Modifier.fillMaxSize().graphicsLayer {
            rotationZ = t * 6f; scaleX = 2.2f; scaleY = 2.2f
            translationX = size.width * 0.12f * sin(t * 0.21f); alpha = strength
        }, contentScale = ContentScale.Crop, colorFilter = vivid)
        Image(img, null, Modifier.fillMaxSize().graphicsLayer {
            rotationZ = -t * 4.5f + 90f; scaleX = 2.8f; scaleY = 2.8f
            translationY = size.height * 0.1f * cos(t * 0.17f); alpha = 0.6f * strength
        }, contentScale = ContentScale.Crop, colorFilter = vivid)
    }
}

/** Aurora: soft wavy bands of the cover's colours sweeping slowly across. */
@Composable
private fun Aurora(colors: List<Color>, t: Float, strength: Float) {
    Canvas(Modifier.fillMaxSize().blur(30.dp, BlurredEdgeTreatment.Rectangle)) {
        val w = size.width; val h = size.height
        for (band in 0 until 4) {
            val c = colors[band % colors.size]
            val path = Path()
            val top = h * (0.12f + band * 0.2f)
            val thick = h * 0.22f
            val steps = 24
            for (s in 0..steps) {
                val x = w * s / steps
                val y = top + h * 0.06f * sin((s / steps.toFloat()) * 2 * PI.toFloat() * 1.3f + t * (0.5f + band * 0.15f) + band)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (s in steps downTo 0) {
                val x = w * s / steps
                val y = top + thick + h * 0.05f * sin((s / steps.toFloat()) * 2 * PI.toFloat() + t * 0.4f + band * 2)
                path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.7f * strength), Color.Transparent),
                top, top + thick), blendMode = BlendMode.Screen)
        }
    }
}

/** Breathing: the blurred cover slowly swelling and brightening, a little quicker while playing. */
@Composable
private fun Breathing(small: Bitmap?, colors: List<Color>, t: Float, strength: Float) {
    val wave = ((1 - cos(t * 1.4f)) / 2)           // 0 → 1 → 0
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.05f + 0.1f * wave; scaleY = 1.05f + 0.1f * wave }) {
        if (small != null) {
            Image(small.asImageBitmap(), null, Modifier.fillMaxSize().blur(50.dp, BlurredEdgeTreatment.Rectangle)
                .graphicsLayer { alpha = 0.6f * strength }, contentScale = ContentScale.Crop)
        } else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(colors[0], colors[3]))))
        // a glow from the centre that brightens on each breath
        Canvas(Modifier.fillMaxSize()) {
            val r = size.maxDimension * (0.45f + 0.15f * wave)
            drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha = (0.25f + 0.35f * wave) * strength), Color.Transparent),
                center, r), r, center, blendMode = BlendMode.Screen)
        }
    }
}
