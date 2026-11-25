package com.autosynced.lyrics.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.FloatStyle
import com.autosynced.lyrics.FloatingLyricsService
import com.autosynced.lyrics.LockStyle
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class LockColor(val title: String) { CURRENT("Current line"), OTHER("Other lines"),
    TRANSLATION("Translation"), ACCENT("Clock, title & buttons"), BACKGROUND("Background colour") }

/** Keeps [old]'s see-through-ness when a new colour is picked (the picker chooses solid colours). */
private fun keepAlpha(old: Int, new: Int) = (old and 0xFF000000.toInt()) or (new and 0xFFFFFF)

/** Settings → Lock screen: on/off and every detail of how the lock-screen lyrics look, with a live preview. */
@Composable
fun LockSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val style by Prefs.lockStyle.collectAsState()
    val translateOn by Prefs.translate.collectAsState()
    val playback by NowPlaying.state.collectAsState()
    fun set(change: (LockStyle) -> LockStyle) {
        Prefs.setLockStyle(context, change(Prefs.lockStyle.value))
    }
    var picking by remember { mutableStateOf<LockColor?>(null) }
    var canDraw by remember { mutableStateOf(FloatingLyricsService.canDraw(context)) }
    LaunchedEffect(Unit) { while (true) { delay(1500); canDraw = FloatingLyricsService.canDraw(context) } }

    // the preview steps through the sample lines, with the song that's playing if there is one
    var sample by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { while (true) { delay(2600); sample = (sample + 1) % SAMPLE.size } }
    val track = playback.track
    val content = LockContent(
        title = track?.title ?: "Preview Song", artist = track?.artist ?: "Sample Artist", art = playback.art,
        playing = true, lines = SAMPLE, index = sample, message = null,
        positionMs = 70_000L + sample * 9_000L, durationMs = 200_000L, translate = translateOn,
    )

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop("Lock screen lyrics", onBack) {
            TextButton(onClick = { set { LockStyle(enabled = it.enabled) } }) { Text("Reset", color = pal.sub) }
        }
        Box(Modifier.fillMaxWidth().height(300.dp).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            LockScreenPreview(style, content, Modifier.fillMaxSize())
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section("Lock screen lyrics 歌词锁屏") {
                SwitchRow("Show lyrics on the lock screen", style.enabled,
                    "When the screen turns off while music plays, the lyrics are waiting over the lock screen " +
                        "when you wake the phone. Swipe up to unlock.") { on ->
                    set { it.copy(enabled = on) }
                    if (on && !FloatingLyricsService.canDraw(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")))
                    }
                    FloatingLyricsService.refresh(context)
                }
                if (style.enabled && !canDraw) {
                    Hint("⚠ Needs “Display over other apps” (Android only lets the app open the lyrics with it). " +
                        "Allow it on the screen that opened, then come back.")
                }
                if (style.enabled) {
                    Hint("Some phones (Xiaomi, OPPO, vivo, Huawei…) also need “Show on lock screen” / 锁屏显示 and " +
                        "“Open new windows while running in the background” / 后台弹出界面 allowed for this app.")
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text("Open this app's permissions", color = pal.accent) }
                }
                SwitchRow("Only while music is playing", style.onlyWhenPlaying,
                    "Off: also when the song is paused.") { on -> set { it.copy(onlyWhenPlaying = on) } }
                SwitchRow("Keep the screen on", style.keepScreenOn,
                    "Stops the screen going dark while the lyrics show (uses more battery).") { on -> set { it.copy(keepScreenOn = on) } }
            }

            Section("Background") {
                ChipRow(LockStyle.BACKGROUNDS, style.background) { v -> set { it.copy(background = v) } }
                if (style.background == "blur") {
                    SliderRow("Blur", "${style.blur}", style.blur.toFloat(), 0f..100f, 19) { v -> set { it.copy(blur = v.roundToInt()) } }
                }
                if (style.background == "solid") {
                    ColorRow("Background colour", style.bgColor) { picking = LockColor.BACKGROUND }
                }
                if (style.background == "wallpaper") Hint("Shows your phone's wallpaper behind the lyrics.")
                SliderRow("Darken background", "${(style.dim * 100).roundToInt()}%", style.dim, 0f..0.9f, 17) { v ->
                    set { it.copy(dim = v) } }
            }

            Section("Transparency") {
                SliderRow("Clock, cover, lyrics & buttons", "${(style.opacity * 100).roundToInt()}%", style.opacity, 0.3f..1f, 13) { v ->
                    set { it.copy(opacity = v) } }
                val otherAlpha = (style.colors().other ushr 24) / 255f
                SliderRow("Other lines", "${(otherAlpha * 100).roundToInt()}%", otherAlpha, 0.1f..1f, 17) { v ->
                    set { s -> s.own().let { o -> o.copy(otherColor = (((v * 255).roundToInt() and 0xFF) shl 24) or (o.otherColor and 0xFFFFFF)) } }
                }
            }

            Section("Clock") {
                SwitchRow("Show the clock", style.showClock) { on -> set { it.copy(showClock = on) } }
                if (style.showClock) {
                    SliderRow("Clock size", "${style.clockSize}", style.clockSize.toFloat(), 32f..110f, 38) { v ->
                        set { it.copy(clockSize = v.roundToInt()) } }
                    SwitchRow("Bold clock", style.clockBold) { on -> set { it.copy(clockBold = on) } }
                    SwitchRow("Show the date", style.showDate) { on -> set { it.copy(showDate = on) } }
                }
            }

            Section("Song") {
                Label("Cover")
                ChipRow(LockStyle.COVERS, style.cover) { v -> set { it.copy(cover = v) } }
                if (style.cover != "none") {
                    SliderRow("Cover size", "${style.coverSize}", style.coverSize.toFloat(), 48f..260f, 52) { v ->
                        set { it.copy(coverSize = v.roundToInt()) } }
                }
                SwitchRow("Song title & artist", style.showTitle) { on -> set { it.copy(showTitle = on) } }
            }

            Section("Lyrics") {
                SliderRow("Lines before the current one", "${style.linesBefore}", style.linesBefore.toFloat(),
                    0f..LockStyle.MAX_AROUND.toFloat(), LockStyle.MAX_AROUND - 1) { v -> set { it.copy(linesBefore = v.roundToInt()) } }
                SliderRow("Lines after the current one", "${style.linesAfter}", style.linesAfter.toFloat(),
                    0f..LockStyle.MAX_AROUND.toFloat(), LockStyle.MAX_AROUND - 1) { v -> set { it.copy(linesAfter = v.roundToInt()) } }
                SwitchRow("Chinese translation", translateOn) { Prefs.setTranslate(context, it) }
                Label("Font")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, family) in FloatStyle.FONTS) {
                        Chip(label, style.font == family, Typeface.create(family, Typeface.NORMAL)) { set { it.copy(font = family) } }
                    }
                    val custom = Prefs.floatStyle.value.font
                    if (custom.startsWith(FloatStyle.CUSTOM_FONT)) {
                        Chip("Your font", style.font == custom, null) { set { it.copy(font = custom) } }
                    }
                }
                Hint("“Your font” appears here after you pick a font file in the floating window's settings.")
                SliderRow("Current line size", "${style.currentSize}", style.currentSize.toFloat(), 16f..44f, 27) { v ->
                    set { it.copy(currentSize = v.roundToInt()) } }
                SliderRow("Other lines size", "${style.otherSize}", style.otherSize.toFloat(), 11f..32f, 20) { v ->
                    set { it.copy(otherSize = v.roundToInt()) } }
                SliderRow("Translation size", "${style.translationPct}%", style.translationPct.toFloat(), 40f..110f, 13) { v ->
                    set { it.copy(translationPct = v.roundToInt()) } }
                SliderRow("Space between lines", "${style.lineSpacing}", style.lineSpacing.toFloat(), 0f..36f, 17) { v ->
                    set { it.copy(lineSpacing = v.roundToInt()) } }
                SwitchRow("Bold current line", style.boldCurrent) { on -> set { it.copy(boldCurrent = on) } }
                SwitchRow("Glow around the current line", style.glow) { on -> set { it.copy(glow = on) } }
                Label("Alignment")
                ChipRow(listOf("Centre" to "center", "Left" to "left"), style.align) { v -> set { it.copy(align = v) } }
                Label("When the line changes")
                ChipRow(LockStyle.ANIMATIONS, style.animation) { v -> set { it.copy(animation = v) } }
            }

            Section("Colours") {
                val c = style.colors()
                ColorRow("Current line", c.current) { picking = LockColor.CURRENT }
                ColorRow("Other lines", c.other) { picking = LockColor.OTHER }
                ColorRow("Translation", c.translation) { picking = LockColor.TRANSLATION }
                ColorRow("Clock, title & buttons", c.accent) { picking = LockColor.ACCENT }
                SwitchRow("Classic white", style.styleColors,
                    "White text that reads well on any background (picking a colour above turns this off).") { on ->
                    set { if (on) it.copy(styleColors = true) else it.own() } }
            }

            Section("Extras") {
                SwitchRow("Play / pause and skip buttons", style.showControls) { on -> set { it.copy(showControls = on) } }
                SwitchRow("Song progress line", style.showProgress) { on -> set { it.copy(showProgress = on) } }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    picking?.let { target ->
        val c = style.colors()
        val initial = when (target) {
            LockColor.CURRENT -> c.current; LockColor.OTHER -> c.other; LockColor.TRANSLATION -> c.translation
            LockColor.ACCENT -> c.accent; LockColor.BACKGROUND -> style.bgColor
        }
        ColorPickerDialog(target.title, initial or 0xFF000000.toInt(), onDismiss = { picking = null }) { new ->
            set {
                val o = if (target == LockColor.BACKGROUND) it else it.own()
                when (target) {
                    LockColor.CURRENT -> o.copy(currentColor = keepAlpha(o.currentColor, new))
                    LockColor.OTHER -> o.copy(otherColor = keepAlpha(o.otherColor, new))
                    LockColor.TRANSLATION -> o.copy(translationColor = keepAlpha(o.translationColor, new))
                    LockColor.ACCENT -> o.copy(accentColor = keepAlpha(o.accentColor, new))
                    LockColor.BACKGROUND -> o.copy(bgColor = new)
                }
            }
            picking = null
        }
    }
}

/** A row of chips to pick one of several (label, value) choices. */
@Composable
internal fun ChipRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in options) Chip(label, selected == value, null) { onPick(value) }
    }
}

/** The top bar of a settings page: ‹ Back, the title, and something on the right (e.g. Reset). */
@Composable
internal fun PageTop(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Back", color = pal.accent, fontSize = 16.sp) }
        Text(title, color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}
