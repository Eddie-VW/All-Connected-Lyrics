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
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.FloatStyle
import com.autosynced.lyrics.FloatingLyricsService
import com.autosynced.lyrics.LockStyle
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class LockColor(val en: String, val zh: String) {
    CURRENT("Current line", "当前行"), OTHER("Other lines", "其他行"), TRANSLATION("Translation", "翻译"),
    ACCENT("Clock, title & buttons", "时钟、歌名和按钮"), BACKGROUND("Background colour", "背景颜色");
    val title: String get() = tr(en, zh)
}

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
        title = track?.title ?: tr("Preview Song", "预览歌曲"), artist = track?.artist ?: tr("Sample Artist", "示例歌手"), art = playback.art,
        playing = true, lines = SAMPLE, index = sample, message = null,
        positionMs = 70_000L + sample * 9_000L, durationMs = 200_000L, translate = translateOn,
    )

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop(tr("Lock screen lyrics", "锁屏歌词"), onBack) {
            TextButton(onClick = { set { LockStyle(enabled = it.enabled) } }) { Text(tr("Reset", "重置"), color = pal.sub) }
        }
        Box(Modifier.fillMaxWidth().height(300.dp).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            LockScreenPreview(style, content, Modifier.fillMaxSize())
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section(tr("Lock screen lyrics", "锁屏歌词")) {
                SwitchRow(tr("Show lyrics on the lock screen", "在锁屏上显示歌词"), style.enabled,
                    tr("When the screen turns off while music plays, the lyrics are waiting over the lock screen " +
                        "when you wake the phone. Swipe up to unlock.",
                        "播放音乐时关闭屏幕，再次唤醒手机时歌词会显示在锁屏上。上滑解锁。")) { on ->
                    set { it.copy(enabled = on) }
                    if (on && !FloatingLyricsService.canDraw(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")))
                    }
                    FloatingLyricsService.refresh(context)
                }
                if (style.enabled && !canDraw) {
                    Hint(tr("⚠ Needs “Display over other apps” (Android only lets the app open the lyrics with it). " +
                        "Allow it on the screen that opened, then come back.",
                        "⚠ 需要“显示在其他应用上层”权限（安卓只允许有此权限的应用打开锁屏歌词）。请在打开的页面中允许，然后返回。"))
                }
                if (style.enabled) {
                    Hint(tr("Some phones (Xiaomi, OPPO, vivo, Huawei…) also need “Show on lock screen” and " +
                        "“Open new windows while running in the background” allowed for this app.",
                        "部分手机（小米、OPPO、vivo、华为等）还需要为本应用允许“锁屏显示”和“后台弹出界面”。"))
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text(tr("Open this app's permissions", "打开本应用的权限设置"), color = pal.accent) }
                }
                SwitchRow(tr("Only while music is playing", "仅在播放音乐时"), style.onlyWhenPlaying,
                    tr("Off: also when the song is paused.", "关闭时，暂停时也显示。")) { on -> set { it.copy(onlyWhenPlaying = on) } }
                SwitchRow(tr("Keep the screen on", "保持屏幕常亮"), style.keepScreenOn,
                    tr("Stops the screen going dark while the lyrics show (uses more battery).", "显示歌词时屏幕不会变暗（更耗电）。")) { on -> set { it.copy(keepScreenOn = on) } }
            }

            Section(tr("Background", "背景")) {
                ValueChips(LockStyle.BACKGROUNDS, style.background) { v -> set { it.copy(background = v) } }
                if (style.background == "blur") {
                    SliderRow(tr("Blur", "模糊程度"), "${style.blur}", style.blur.toFloat(), 0f..100f, 19) { v -> set { it.copy(blur = v.roundToInt()) } }
                }
                if (style.background == "solid") {
                    ColorRow(tr("Background colour", "背景颜色"), style.bgColor) { picking = LockColor.BACKGROUND }
                }
                if (style.background == "wallpaper") Hint(tr("Shows your phone's wallpaper behind the lyrics.", "在歌词后面显示手机壁纸。"))
                SliderRow(tr("Darken background", "背景压暗"), "${(style.dim * 100).roundToInt()}%", style.dim, 0f..0.9f, 17) { v ->
                    set { it.copy(dim = v) } }
            }

            Section(tr("Transparency", "透明度")) {
                SliderRow(tr("Clock, cover, lyrics & buttons", "时钟、封面、歌词和按钮"), "${(style.opacity * 100).roundToInt()}%", style.opacity, 0.3f..1f, 13) { v ->
                    set { it.copy(opacity = v) } }
                val otherAlpha = (style.colors().other ushr 24) / 255f
                SliderRow(tr("Other lines", "其他行"), "${(otherAlpha * 100).roundToInt()}%", otherAlpha, 0.1f..1f, 17) { v ->
                    set { s -> s.own().let { o -> o.copy(otherColor = (((v * 255).roundToInt() and 0xFF) shl 24) or (o.otherColor and 0xFFFFFF)) } }
                }
            }

            Section(tr("Clock", "时钟")) {
                SwitchRow(tr("Show the clock", "显示时钟"), style.showClock) { on -> set { it.copy(showClock = on) } }
                if (style.showClock) {
                    SliderRow(tr("Clock size", "时钟大小"), "${style.clockSize}", style.clockSize.toFloat(), 32f..110f, 38) { v ->
                        set { it.copy(clockSize = v.roundToInt()) } }
                    SwitchRow(tr("Bold clock", "时钟加粗"), style.clockBold) { on -> set { it.copy(clockBold = on) } }
                    SwitchRow(tr("Show the date", "显示日期"), style.showDate) { on -> set { it.copy(showDate = on) } }
                }
            }

            Section(tr("Song", "歌曲")) {
                Label(tr("Cover", "封面"))
                ValueChips(LockStyle.COVERS, style.cover) { v -> set { it.copy(cover = v) } }
                if (style.cover != "none") {
                    SliderRow(tr("Cover size", "封面大小"), "${style.coverSize}", style.coverSize.toFloat(), 48f..260f, 52) { v ->
                        set { it.copy(coverSize = v.roundToInt()) } }
                }
                SwitchRow(tr("Song title & artist", "歌名和歌手"), style.showTitle) { on -> set { it.copy(showTitle = on) } }
            }

            Section(tr("Lyrics", "歌词")) {
                SliderRow(tr("Lines before the current one", "当前行之前的行数"), "${style.linesBefore}", style.linesBefore.toFloat(),
                    0f..LockStyle.MAX_AROUND.toFloat(), LockStyle.MAX_AROUND - 1) { v -> set { it.copy(linesBefore = v.roundToInt()) } }
                SliderRow(tr("Lines after the current one", "当前行之后的行数"), "${style.linesAfter}", style.linesAfter.toFloat(),
                    0f..LockStyle.MAX_AROUND.toFloat(), LockStyle.MAX_AROUND - 1) { v -> set { it.copy(linesAfter = v.roundToInt()) } }
                SwitchRow(tr("Translation", "翻译"), translateOn) { Prefs.setTranslate(context, it) }
                Label(tr("Font", "字体"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (family in FloatStyle.FONTS) {
                        Chip(optionLabel(family), style.font == family, Typeface.create(family, Typeface.NORMAL)) { set { it.copy(font = family) } }
                    }
                    val custom = Prefs.floatStyle.value.font
                    if (custom.startsWith(FloatStyle.CUSTOM_FONT)) {
                        Chip(tr("Your font", "我的字体"), style.font == custom, null) { set { it.copy(font = custom) } }
                    }
                }
                Hint(tr("“Your font” appears here after you pick a font file in the floating window's settings.", "在悬浮歌词设置中选择字体文件后，“我的字体”会出现在这里。"))
                SliderRow(tr("Current line size", "当前行字号"), "${style.currentSize}", style.currentSize.toFloat(), 16f..44f, 27) { v ->
                    set { it.copy(currentSize = v.roundToInt()) } }
                SliderRow(tr("Other lines size", "其他行字号"), "${style.otherSize}", style.otherSize.toFloat(), 11f..32f, 20) { v ->
                    set { it.copy(otherSize = v.roundToInt()) } }
                SliderRow(tr("Translation size", "翻译字号"), "${style.translationPct}%", style.translationPct.toFloat(), 40f..110f, 13) { v ->
                    set { it.copy(translationPct = v.roundToInt()) } }
                SliderRow(tr("Space between lines", "行间距"), "${style.lineSpacing}", style.lineSpacing.toFloat(), 0f..36f, 17) { v ->
                    set { it.copy(lineSpacing = v.roundToInt()) } }
                SwitchRow(tr("Bold current line", "当前行加粗"), style.boldCurrent) { on -> set { it.copy(boldCurrent = on) } }
                GlowSettings(GlowLook(style.glow, style.glowColor, style.glowStrength, style.glowBlur)) { g ->
                    set { it.copy(glow = g.on, glowColor = g.color, glowStrength = g.strength, glowBlur = g.blur) } }
                Label(tr("Alignment", "对齐"))
                ValueChips(listOf("center", "left"), style.align) { v -> set { it.copy(align = v) } }
                Label(tr("When the line changes", "换行效果"))
                ValueChips(LockStyle.ANIMATIONS, style.animation, { if (it == "none") tr("No effect", "无效果") else optionLabel(it) }) { v -> set { it.copy(animation = v) } }
            }

            Section(tr("Colours", "颜色")) {
                val c = style.colors()
                ColorRow(tr("Current line", "当前行"), c.current) { picking = LockColor.CURRENT }
                ColorRow(tr("Other lines", "其他行"), c.other) { picking = LockColor.OTHER }
                ColorRow(tr("Translation", "翻译"), c.translation) { picking = LockColor.TRANSLATION }
                ColorRow(tr("Clock, title & buttons", "时钟、歌名和按钮"), c.accent) { picking = LockColor.ACCENT }
                SwitchRow(tr("Classic white", "经典白色"), style.styleColors,
                    tr("White text that reads well on any background (picking a colour above turns this off).", "白色文字在任何背景上都清晰（选择上面任一颜色会自动关闭）。")) { on ->
                    set { if (on) it.copy(styleColors = true) else it.own() } }
            }

            Section(tr("Extras", "其他")) {
                SwitchRow(tr("Play / pause and skip buttons", "播放 / 暂停和切歌按钮"), style.showControls) { on -> set { it.copy(showControls = on) } }
                SwitchRow(tr("Song progress line", "歌曲进度条"), style.showProgress) { on -> set { it.copy(showProgress = on) } }
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
        TextButton(onClick = onBack) { Text(tr("‹ Back", "‹ 返回"), color = pal.accent, fontSize = 16.sp) }
        Text(title, color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}
