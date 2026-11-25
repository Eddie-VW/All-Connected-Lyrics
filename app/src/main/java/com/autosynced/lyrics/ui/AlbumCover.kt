package com.autosynced.lyrics.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.CoverStyle
import kotlin.math.PI
import kotlin.math.cos

/**
 * The album cover as [style] says (like the PC app's): a shape (square, rounded, circle, vinyl,
 * CD, polaroid), a movement while music plays (spin, float, sway, pulse, tilt), and extras
 * (tonearm, glow, mirror). [title] is written on a polaroid. Nothing is drawn for source "none".
 */
@Composable
fun AlbumCover(style: CoverStyle, art: Bitmap?, playing: Boolean, size: Dp, title: String = "", seed: String = "") {
    if (style.source == "none") return
    // one clock for the movement, running only while music plays (as on the PC)
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, style.motion) {
        var last = 0L
        while (playing && style.motion != "still" && style.motion != "tilt") {
            withFrameNanos { now -> if (last != 0L) t += (now - last) / 1e9f; last = now }
        }
    }
    var rx by remember { mutableFloatStateOf(0f) }
    var ry by remember { mutableFloatStateOf(0f) }
    val tiltX by animateFloatAsState(rx, tween(180), label = "rx")
    val tiltY by animateFloatAsState(ry, tween(180), label = "ry")
    val armAngle by animateFloatAsState(if (playing) 4f else -24f, tween(900), label = "arm")
    val tint = remember(art) { dominantColor(art) }
    val glow = (tint ?: Color.White).copy(alpha = if (tint != null) 0.55f else 0.25f)

    val motion = Motion.of(style, t)
    val body: @Composable (Boolean) -> Unit = { mirror ->
        Box(
            Modifier.size(size).graphicsLayer {
                rotationZ = motion.rotationZ
                rotationY = motion.rotationY + if (style.motion == "tilt") tiltY else 0f
                rotationX = if (style.motion == "tilt") tiltX else 0f
                translationY = motion.dy * this.size.height
                scaleX = motion.scale; scaleY = motion.scale
                cameraDistance = 12f * density
                if (mirror) { scaleY = -motion.scale }
            }
        ) { Disc(style, art, size, title, seed, tint, if (style.glow) glow else null) }
    }

    val tiltInput = Modifier.pointerInput(style.motion) {
        if (style.motion != "tilt") return@pointerInput
        detectDragGestures(onDragEnd = { rx = 0f; ry = 0f }, onDragCancel = { rx = 0f; ry = 0f }) { change, _ ->
            val p = change.position
            ry = ((p.x / this.size.width) - 0.5f) * 30f
            rx = -((p.y / this.size.height) - 0.5f) * 30f
        }
    }

    Column(tiltInput, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            body(false)
            if (style.arm && (style.shape == "vinyl" || style.shape == "cd")) Tonearm(size, armAngle)
        }
        if (style.reflect) {
            // the mirror stays put under the cover and shows it moving above, fading out downwards
            Box(
                Modifier.padding(top = size * 0.03f).size(size, size * 0.42f).clipToBounds()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)),
                            blendMode = BlendMode.DstIn)
                    }
            ) {
                Box(Modifier.align(Alignment.TopCenter)) { body(true) }
            }
        }
    }
}

/** The cover's movement at time [t] (seconds of playing). */
private data class Motion(val rotationZ: Float = 0f, val rotationY: Float = 0f, val dy: Float = 0f, val scale: Float = 1f) {
    companion object {
        /** 0 → 1 → 0 smoothly over [period] seconds (an "alternate, ease-in-out" CSS animation). */
        private fun wave(t: Float, period: Float) = ((1 - cos(2 * PI * t / (2 * period))) / 2).toFloat()

        fun of(s: CoverStyle, t: Float): Motion = when (s.motion) {
            "spin" -> Motion(rotationZ = (t / s.speed * 360f) % 360f)
            "float" -> wave(t, s.speed * 0.6f).let { Motion(dy = 0.02f - 0.06f * it, rotationZ = -1.5f + 3f * it) }
            "sway" -> wave(t, s.speed * 0.6f).let { Motion(rotationY = -16f + 32f * it, rotationZ = -1f + 2f * it) }
            "pulse" -> wave(t, s.speed * 0.14f).let { Motion(scale = 0.97f + 0.07f * it) }
            else -> Motion()
        }
    }
}

@Composable
private fun Disc(style: CoverStyle, art: Bitmap?, size: Dp, title: String, seed: String, tint: Color?, glow: Color?) {
    val glowMod = if (glow != null) Modifier.drawBehind {
        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), radius = this.size.minDimension * 0.75f),
            radius = this.size.minDimension * 0.75f)
    } else Modifier
    when (style.shape) {
        "polaroid" -> Box(
            glowMod.padding(start = size * 0.09f, end = size * 0.09f, top = size * 0.03f).fillMaxSize().rotate(-3f)
                .shadow(10.dp, RoundedCornerShape(3.dp)).background(Color(0xFFFBFBF8), RoundedCornerShape(3.dp))
        ) {
            Box(Modifier.padding(start = size * 0.05f, end = size * 0.05f, top = size * 0.05f, bottom = size * 0.21f)
                .fillMaxSize().clip(RoundedCornerShape(1.dp))) { Picture(style, art, tint, seed) }
            if (title.isNotEmpty()) Text(title, color = Color(0xFF3A3A3A), fontFamily = FontFamily.Cursive,
                fontSize = (size.value * 0.065f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.04f, start = 6.dp, end = 6.dp))
        }
        "vinyl" -> Box(glowMod.padding(size * 0.04f).fillMaxSize().shadow(12.dp, CircleShape).clip(CircleShape)
            .background(Brush.radialGradient(0f to Color(0xFF1B1B1F), 0.3f to Color(0xFF1B1B1F), 0.305f to Color(0xFF0D0D10),
                1f to Color(0xFF0D0D10)))) {
            Canvas(Modifier.fillMaxSize()) {         // grooves
                val step = 4.dp.toPx()
                var r = this.size.minDimension / 2 * 0.3f
                while (r < this.size.minDimension / 2) {
                    drawCircle(Color.White.copy(alpha = 0.055f), r, style = Stroke(1f)); r += step
                }
            }
            Box(Modifier.padding(size * 0.92f * 0.22f).fillMaxSize().clip(CircleShape)) { Picture(style, art, tint, seed) }
            Sheen(Brush.sweepGradient(0f to Color.Transparent, 0.08f to Color.Transparent, 0.13f to Color.White.copy(alpha = 0.13f),
                0.2f to Color.Transparent, 0.52f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.09f),
                0.65f to Color.Transparent, 1f to Color.Transparent))
            Hole(size, cd = false)
        }
        "cd" -> Box(glowMod.padding(size * 0.04f).fillMaxSize().shadow(12.dp, CircleShape).clip(CircleShape)
            .background(Color(0xFFCFD3DA))) {
            Picture(style, art, tint, seed)
            Sheen(Brush.sweepGradient(listOf(Color(0xFFFF9AD5), Color(0xFF9AD8FF), Color(0xFFB5FFB0), Color(0xFFFFF59A),
                Color(0xFFFFB08A), Color(0xFFD9A6FF), Color(0xFF9AD8FF), Color(0xFFFF9AD5))), blend = BlendMode.Overlay, alpha = 0.9f)
            Hole(size, cd = true)
        }
        else -> {
            val (pad, shape) = when (style.shape) {
                "square" -> 0f to RoundedCornerShape(size * 0.025f)
                "rounded" -> 0.02f to RoundedCornerShape(22)
                else -> 0.04f to CircleShape
            }
            Box(glowMod.padding(size * pad).fillMaxSize().shadow(12.dp, shape).clip(shape)) { Picture(style, art, tint, seed) }
        }
    }
}

/** The album art, or (no art / "colour mix" chosen) a soft mix of colours from the cover or the song's name. */
@Composable
private fun Picture(style: CoverStyle, art: Bitmap?, tint: Color?, seed: String) {
    if (style.source == "album" && art != null) {
        Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        return
    }
    val hue = remember(tint, seed) {
        if (tint != null) FloatArray(3).also { android.graphics.Color.colorToHSV(tint.toArgbCompat(), it) }[0]
        else (seed.fold(0) { h, ch -> (h * 31 + ch.code) % 360 }).toFloat()
    }
    fun hsl(d: Float, l: Float) = Color.hsl((hue + d + 360f) % 360f, 0.85f, l)
    Box(Modifier.fillMaxSize().background(hsl(40f, 0.42f)).drawBehind {
        drawRect(Brush.radialGradient(listOf(hsl(0f, 0.64f), Color.Transparent), Offset(size.width * 0.18f, size.height * 0.22f),
            size.minDimension * 0.6f))
        drawRect(Brush.radialGradient(listOf(hsl(-50f, 0.58f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.8f),
            size.minDimension * 0.7f))
    }, contentAlignment = Alignment.Center) {
        Text("♪", color = Color.White.copy(alpha = 0.82f), fontSize = 22.sp)
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(),
    (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun Sheen(brush: Brush, blend: BlendMode = BlendMode.SrcOver, alpha: Float = 1f) {
    Canvas(Modifier.fillMaxSize()) { drawCircle(brush, alpha = alpha, blendMode = blend) }
}

@Composable
private fun Hole(size: Dp, cd: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val full = this.size.minDimension
        if (cd) {
            // clear plastic centre with its rings
            val r = full * 0.15f
            drawCircle(Color(0xD9E1E6EE.toInt()), r * 0.66f, c)
            drawCircle(Color.White.copy(alpha = 0.75f), r * 0.7f, c, style = Stroke(r * 0.04f))
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.25f, c, style = Stroke(r * 0.04f))
            drawCircle(Color(0xFF26262B), r * 0.22f, c)
        } else {
            drawCircle(Color.White.copy(alpha = 0.18f), full * 0.016f + 4f, c)
            drawCircle(Color(0xFF0A0A0A), full * 0.016f, c)
        }
    }
}

/** The record player's arm (vinyl / CD), resting off the disc when paused, on it while playing. */
@Composable
private fun Tonearm(size: Dp, angle: Float) {
    Canvas(Modifier.size(size).offset(x = size * 0.06f, y = -size * 0.04f)) {
        val w = this.size.width
        val pivot = Offset(w * 0.86f, w * 0.1f)
        rotate(angle, pivot) {
            val rod = Path().apply {
                moveTo(pivot.x, pivot.y)
                lineTo(w * 0.86f, w * 0.52f)
                lineTo(w * 0.74f, w * 0.7f)
            }
            drawPath(rod, Color.Black.copy(alpha = 0.3f), style = Stroke(w * 0.026f, cap = StrokeCap.Round),
                alpha = 1f)
            drawPath(rod, Color(0xFFE6E6EA), style = Stroke(w * 0.018f, cap = StrokeCap.Round))
            drawRoundRect(Color(0xFFC8C8CE), Offset(w * 0.7f, w * 0.68f), androidx.compose.ui.geometry.Size(w * 0.07f, w * 0.1f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.012f))
            drawCircle(Color(0xFFD9D9DE), w * 0.055f, pivot)
            drawCircle(Color(0xFF8E8E96), w * 0.025f, pivot)
        }
    }
}
