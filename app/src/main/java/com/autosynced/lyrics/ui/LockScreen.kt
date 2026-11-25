package com.autosynced.lyrics.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.FloatingLyricsView
import com.autosynced.lyrics.Line
import com.autosynced.lyrics.LockRows
import com.autosynced.lyrics.LockStyle
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs

/** What the lock screen shows; filled from the music app and lyrics (or sample data in the preview). */
data class LockContent(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val lines: List<Line>,          // empty: show [message] instead
    val index: Int,                 // current line
    val message: String?,
    val positionMs: Long,
    val durationMs: Long,
    val translate: Boolean,
)

/**
 * The lock-screen lyrics, drawn as [style] says. [onUnlock] runs on an upward swipe;
 * [onPrevious] / [onPlayPause] / [onNext] are the media buttons. In the preview, all do nothing.
 */
@Composable
fun LockScreen(
    style: LockStyle,
    content: LockContent,
    onUnlock: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    preview: Boolean = false,
) {
    val colors = style.colors()
    val current = Color(colors.current)
    val accent = Color(colors.accent)
    val context = LocalContext.current
    val family = remember(style.font) { FontFamily(FloatingLyricsView.loadTypeface(context, style.font)) }
    val center = style.align == "center"
    val swipe = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        Modifier.fillMaxSize().pointerInput(preview) {
            if (preview) return@pointerInput
            var dragged = 0f
            detectVerticalDragGestures(onDragStart = { dragged = 0f }, onDragEnd = { if (dragged < -swipe) onUnlock() }) { _, dy ->
                dragged += dy
            }
        }
    ) {
        Background(style, content.art, preview)
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = style.opacity }
                .padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 28.dp),
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (style.showClock) Clock(style, accent, family)
            Spacer(Modifier.height(if (style.showClock) 28.dp else 8.dp))
            SongInfo(style, content, accent, family)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                Lyrics(style, content, family)
            }
            if (style.showProgress && content.durationMs > 0) {
                Progress(content.positionMs, content.durationMs, accent)
                Spacer(Modifier.height(14.dp))
            }
            if (style.showControls) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconSpot(onPrevious) { SkipIcon(accent, forward = false) }
                    IconSpot(onPlayPause, big = true) { if (content.playing) PauseIcon(accent) else PlayIcon(accent) }
                    IconSpot(onNext) { SkipIcon(accent, forward = true) }
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("︿  Swipe up to unlock", color = accent.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// ---- background ---------------------------------------------------------------------------------
@Composable
private fun Background(style: LockStyle, art: Bitmap?, preview: Boolean) {
    // A tiny copy of the cover, stretched, already looks blurred; real blur on top where Android has it.
    val small = remember(art, style.blur) {
        art?.let {
            try {
                val soft = if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
                val w = (200 - style.blur * 1.85f).toInt().coerceIn(8, 200)
                Bitmap.createScaledBitmap(soft, w, w, true)
            } catch (_: Exception) { null }
        }
    }
    val tint = remember(art) { dominantColor(art) } ?: Color(0xFF3A3F55)
    Box(Modifier.fillMaxSize()) {
        when {
            style.background == "solid" -> Box(Modifier.fillMaxSize().background(Color(style.bgColor)))
            style.background == "wallpaper" -> {
                // the real wallpaper shows through the window; the preview stands in a gradient for it
                if (preview) Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                    listOf(Color(0xFF5C7CFA), Color(0xFFE599F7), Color(0xFFFFA94D)))))
            }
            style.background == "blur" && small != null -> Image(small.asImageBitmap(), null,
                Modifier.fillMaxSize().blur((style.blur * 0.4f).dp), contentScale = ContentScale.Crop)
            else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(tint, Color(0xFF0B0B0F)))))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.dim)))
    }
}

// ---- clock ---------------------------------------------------------------------------------------
@Composable
private fun Clock(style: LockStyle, color: Color, family: FontFamily) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val time = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Date(now)).toString()
    val date = DateFormat.format("EEEE, d MMMM", Date(now)).toString()
    Text(time, color = color, fontSize = style.clockSize.sp, fontFamily = family,
        fontWeight = if (style.clockBold) FontWeight.Bold else FontWeight.Light, lineHeight = (style.clockSize * 1.1f).sp)
    if (style.showDate) Text(date, color = color.copy(alpha = 0.75f), fontSize = 15.sp, fontFamily = family)
}

// ---- song ------------------------------------------------------------------------------------------
@Composable
private fun SongInfo(style: LockStyle, c: LockContent, accent: Color, family: FontFamily) {
    val center = style.align == "center"
    val cover: @Composable () -> Unit = { Cover(style.cover, c.art, c.playing, style.coverSize.dp) }
    val text: @Composable () -> Unit = {
        if (style.showTitle) Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            Text(c.title, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = family,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.artist.isNotBlank()) Text(c.artist, color = accent.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = family,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (style.cover != "none") { cover(); Spacer(Modifier.height(12.dp)) }
            text()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (style.cover != "none") { cover(); Spacer(Modifier.width(14.dp)) }
            text()
        }
    }
}

@Composable
private fun Cover(shape: String, art: Bitmap?, playing: Boolean, size: Dp) {
    if (shape == "vinyl") { SpinningRecord(art, playing, size); return }
    val clip = when (shape) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(3.dp)
        else -> RoundedCornerShape(size * 0.14f)
    }
    Box(Modifier.size(size).clip(clip).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("♪", color = Color.White.copy(alpha = 0.7f), fontSize = (size.value * 0.4f).sp)
    }
}

// ---- lyrics ------------------------------------------------------------------------------------------
@Composable
private fun Lyrics(style: LockStyle, c: LockContent, family: FontFamily) {
    val colors = style.colors()
    val align = if (style.align == "center") TextAlign.Center else TextAlign.Start
    if (c.lines.isEmpty()) {
        Text(c.message ?: "♪", color = Color(colors.other), fontSize = style.otherSize.sp, fontFamily = family,
            textAlign = align, modifier = Modifier.fillMaxWidth())
        return
    }
    AnimatedContent(
        targetState = c.index,
        transitionSpec = { transition(style.animation) },
        label = "lyrics",
    ) { index ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(style.lineSpacing.dp)) {
            for ((i, distance) in LockRows.window(c.lines.size, index, style.linesBefore, style.linesAfter)) {
                val line = c.lines[i]
                if (distance == 0) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(line.text.ifBlank { "♪" }, color = Color(colors.current), fontSize = style.currentSize.sp,
                            fontFamily = family, textAlign = align, lineHeight = (style.currentSize * 1.25f).sp,
                            fontWeight = if (style.boldCurrent) FontWeight.Bold else FontWeight.Normal,
                            style = if (style.glow) TextStyle(shadow = Shadow(Color(colors.current).copy(alpha = 0.75f),
                                Offset.Zero, 28f)) else TextStyle.Default,
                            modifier = Modifier.fillMaxWidth())
                        val tr = line.translation
                        if (c.translate && tr != null) {
                            Text(tr, color = Color(colors.translation), fontFamily = family, textAlign = align,
                                fontSize = (style.currentSize * style.translationPct / 100f).sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                } else {
                    // lines further away fade a little more
                    val fade = (1f - (abs(distance) - 1) * 0.18f).coerceIn(0.35f, 1f)
                    Text(line.text.ifBlank { "♪" }, color = Color(colors.other).let { it.copy(alpha = it.alpha * fade) },
                        fontSize = style.otherSize.sp, fontFamily = family, textAlign = align, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun transition(kind: String): ContentTransform = when (kind) {
    "fade" -> fadeIn(tween(350)) togetherWith fadeOut(tween(250))
    "slide" -> (slideInVertically(tween(380)) { it / 4 } + fadeIn(tween(380))) togetherWith
        (slideOutVertically(tween(300)) { -it / 4 } + fadeOut(tween(300)))
    "zoom" -> (scaleIn(tween(350), initialScale = 0.9f) + fadeIn(tween(350))) togetherWith
        (scaleOut(tween(250), targetScale = 1.06f) + fadeOut(tween(250)))
    else -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
}

// ---- progress & buttons -------------------------------------------------------------------------------
@Composable
private fun Progress(position: Long, duration: Long, color: Color) {
    val f = (position.toFloat() / duration).coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width * f, size.height / 2), size.height,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun IconSpot(onClick: () -> Unit, big: Boolean = false, icon: @Composable () -> Unit) {
    Box(Modifier.size(if (big) 64.dp else 52.dp).clip(CircleShape)
        .background(if (big) Color.White.copy(alpha = 0.14f) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { icon() }
}

/**
 * A phone-shaped miniature of the lock screen: drawn at the real screen size, then scaled down,
 * so text sizes and spacing look exactly as they will.
 */
@Composable
fun LockScreenPreview(style: LockStyle, content: LockContent, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val screenW = config.screenWidthDp.dp
        val screenH = config.screenHeightDp.dp
        val scale = minOf(maxWidth / screenW, maxHeight / screenH)
        Box(Modifier.size(screenW * scale, screenH * scale).clip(RoundedCornerShape(22.dp)).background(Color.Black),
            contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(screenW, screenH).graphicsLayer { scaleX = scale; scaleY = scale }) {
                LockScreen(style, content, preview = true)
            }
        }
    }
}
