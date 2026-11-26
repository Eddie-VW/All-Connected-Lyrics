package com.autosynced.lyrics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.AppLyricsStyle
import com.autosynced.lyrics.CoverStyle
import com.autosynced.lyrics.FloatStyle
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.tr
import kotlin.math.roundToInt

/** A round icon button with a text glyph (filled when [on]). */
@Composable
internal fun IconText(glyph: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) pal.accent.copy(alpha = 0.85f) else Color.Transparent)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = if (on) pal.accentInk else pal.text, fontSize = if (glyph.length > 2) 12.sp else 18.sp,
            fontWeight = FontWeight.Bold)
    }
}

/**
 * Full screen's ⚙: a half-height panel with the quick changes (cover preset, lyric font and size,
 * glow, background animation), and a way to the background animation's full settings.
 */
@Composable
fun QuickPanel(open: Boolean, onClose: () -> Unit, onMore: () -> Unit) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val style by Prefs.appLyrics.collectAsState()
    val cover by Prefs.cover.collectAsState()
    fun set(f: (AppLyricsStyle) -> AppLyricsStyle) = Prefs.setAppLyrics(context, f(Prefs.appLyrics.value))
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClose))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(open, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.5f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(pal.surface)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}   // taps here don't close it
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Section(tr("Album cover", "专辑封面")) {
                    ValueChips(CoverStyle.PRESETS.map { it.first }, cover.presetName() ?: "") { name ->
                        CoverStyle.PRESETS.firstOrNull { it.first == name }?.let { (_, p) ->
                            Prefs.setCover(context, Prefs.cover.value.withPreset(p)) }
                    }
                }
                Section(tr("Lyric text", "歌词文字")) {
                    ValueChips(FloatStyle.FONTS, style.font) { v -> set { it.copy(font = v) } }
                    SliderRow(tr("Current line size", "当前行字号"), "${style.fontSize}", style.fontSize.toFloat(), 14f..40f, 25) { v ->
                        set { it.copy(fontSize = v.roundToInt()) } }
                    SwitchRow(tr("Glow around the current line", "当前行发光"), style.glow) { on -> set { it.copy(glow = on) } }
                }
                Section(tr("Background animation", "背景动画")) {
                    ValueChips(AppLyricsStyle.BACKDROPS, style.backdrop, { backdropName(it) }) { v -> set { it.copy(backdrop = v) } }
                    TextButton(onClick = onMore) {
                        Text(tr("More background settings ›", "更多背景设置 ›"), color = pal.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
