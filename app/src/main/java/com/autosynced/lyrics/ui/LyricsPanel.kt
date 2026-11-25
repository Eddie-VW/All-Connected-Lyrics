package com.autosynced.lyrics.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autosynced.lyrics.AppLyricsStyle

/**
 * The panel behind the main screen's lyrics, in Night and Day alike (like the PC app):
 * frosted glass (the cover blurred behind a dark or light veil), the cover's colour, your colour, or none.
 */
@Composable
fun LyricsPanel(style: AppLyricsStyle, art: Bitmap?, modifier: Modifier = Modifier, corner: Dp = 16.dp,
                content: @Composable BoxScope.() -> Unit) {
    val pal = LocalPalette.current
    val tint = remember(art) { dominantColor(art) }
    val op = style.panelOpacity
    // a tiny copy of the cover already looks blurred when stretched; real blur on top where Android has it
    val small = remember(art, style.panelBlur) {
        art?.let {
            try {
                val soft = if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
                val w = (72 - style.panelBlur * 0.64f).toInt().coerceIn(6, 72)
                Bitmap.createScaledBitmap(soft, w, w, true)
            } catch (_: Exception) { null }
        }
    }
    Box(modifier.clip(RoundedCornerShape(corner))) {
        when (style.panel) {
            "glass" -> {
                if (small != null) Image(small.asImageBitmap(), null,
                    Modifier.fillMaxSize().blur((style.panelBlur * 0.5f).dp, BlurredEdgeTreatment.Rectangle)
                        .graphicsLayer { alpha = op }, contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background((if (pal.night) Color(0xFF2A2A2A) else Color(0xFFDCDCE2)).copy(alpha = op)))
                // the veil: dark at night; by day only a light haze, so the cover's colours still show
                Box(Modifier.fillMaxSize().background(
                    if (pal.night) Color.Black.copy(alpha = 0.38f * op) else Color(0xFFF2F2F5).copy(alpha = 0.3f * op)))
            }
            "album" -> Box(Modifier.fillMaxSize().background(
                if (pal.night) (tint ?: Color(0xFF2A2A2A)).copy(alpha = 0.9f * op)
                else (tint ?: pal.accent).copy(alpha = 0.22f * op)))
            "colour" -> Color(style.panelColor).let { c -> Box(Modifier.fillMaxSize().background(c.copy(alpha = c.alpha * op))) }
        }
        content()
    }
}

/** Slightly blurred lines (the PC app's soft-focus lyrics); only where Android can blur (12 and newer). */
fun Modifier.softFocus(on: Boolean): Modifier = if (on) this.blur(1.4.dp, BlurredEdgeTreatment.Unbounded) else this

/**
 * Full screen's lively background: three soft clouds of light in the cover's colours drifting
 * slowly behind the lyrics, a little livelier while music plays.
 */
@Composable
fun LivelyGlow(art: Bitmap?, playing: Boolean, modifier: Modifier = Modifier) {
    val tint = remember(art) { dominantColor(art) } ?: Color(0xFF7C5CFF)
    val hsv = remember(tint) { FloatArray(3).also { android.graphics.Color.colorToHSV(tint.toArgb(), it) } }
    fun shade(dh: Float) = Color.hsv((hsv[0] + dh + 360f) % 360f, 0.75f, 1f)
    val colors = remember(tint) { listOf(shade(0f), shade(40f), shade(-45f)) }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "lively")
    val speed = if (playing) 1f else 0.4f
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween((26_000 / speed).toInt(),
            easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    val breath by t.animateFloat(0.75f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween((3_200 / speed).toInt()), androidx.compose.animation.core.RepeatMode.Reverse), label = "breath")
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val spots = listOf(
            Offset(w * (0.3f + 0.2f * kotlin.math.cos(phase)), h * (0.28f + 0.12f * kotlin.math.sin(phase * 2))),
            Offset(w * (0.72f + 0.18f * kotlin.math.sin(phase + 1f)), h * (0.55f + 0.15f * kotlin.math.cos(phase))),
            Offset(w * (0.45f + 0.25f * kotlin.math.sin(phase * 0.5f + 2f)), h * (0.82f + 0.08f * kotlin.math.cos(phase + 2f))),
        )
        for ((i, p) in spots.withIndex()) {
            val r = size.minDimension * (0.55f + 0.1f * i) * breath
            drawCircle(Brush.radialGradient(listOf(colors[i].copy(alpha = 0.45f), Color.Transparent), p, r), r, p,
                blendMode = androidx.compose.ui.graphics.BlendMode.Screen)
        }
    }
}
