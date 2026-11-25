package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.AppLyricsStyle
import com.autosynced.lyrics.CoverStyle
import com.autosynced.lyrics.Lang
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.tr
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The settings pages, opened from the main screen's top bar (or straight from the floating window). */
enum class Page { HUB, FLOAT, LOCK, MAIN, TRANSLATION, EDITOR }

/** Settings: the language, then one row leading to each page. */
@Composable
fun SettingsHub(onBack: () -> Unit, open: (Page) -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val lock by Prefs.lockStyle.collectAsState()
    val translate by Prefs.translate.collectAsState()
    val language by Prefs.language.collectAsState()
    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop(tr("Settings", "设置"), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(6.dp))
            // each language's name is written in that language, so either can be found
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(tr("Language", "语言"), color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ValueChips(listOf(Lang.EN, Lang.ZH), language, { if (it == Lang.ZH) "简体中文" else "English" }) {
                    Prefs.setLanguage(context, it)
                }
            }
            HubRow(tr("Main screen", "主界面"),
                tr("Album cover look, lyric colours and the panel behind them", "专辑封面样式、歌词颜色和歌词面板")) { open(Page.MAIN) }
            HubRow(tr("Floating lyrics", "悬浮歌词"),
                tr("Size, lines, font, colours, outline, buttons, lock, timing", "大小、行数、字体、颜色、描边、按钮、锁定、时间偏移")) {
                open(Page.FLOAT) }
            HubRow(tr("Lock screen lyrics", "锁屏歌词"),
                if (lock.enabled) tr("On · background, clock, cover, lyrics, colours", "已开启 · 背景、时钟、封面、歌词、颜色")
                else tr("Off · show lyrics over the lock screen", "未开启 · 在锁屏上显示歌词")) { open(Page.LOCK) }
            HubRow(tr("Chinese translation", "中文翻译"),
                if (translate) tr("On · services and keys", "已开启 · 服务和密钥") else tr("Off · services and keys", "未开启 · 服务和密钥")) {
                open(Page.TRANSLATION) }
        }
    }
}

@Composable
private fun HubRow(title: String, detail: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.surface).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = pal.sub, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text("›", color = pal.faint, fontSize = 24.sp)
    }
}

private enum class MainColor(val en: String, val zh: String) {
    CURRENT("Current line", "当前行"), PAST("Lines already sung", "已唱过的行"), UPCOMING("Lines to come", "未唱的行"),
    TRANSLATION("Translation", "翻译"), PANEL("Panel colour", "面板颜色");
    val title: String get() = tr(en, zh)
}

/** Settings → Main screen: the album cover's look, the lyric colours and the panel behind them, with previews. */
@Composable
fun MainScreenSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val style by Prefs.appLyrics.collectAsState()
    val cover by Prefs.cover.collectAsState()
    val translateOn by Prefs.translate.collectAsState()
    val playback by NowPlaying.state.collectAsState()
    fun set(change: (AppLyricsStyle) -> AppLyricsStyle) = Prefs.setAppLyrics(context, change(Prefs.appLyrics.value))
    fun setCover(change: (CoverStyle) -> CoverStyle) = Prefs.setCover(context, change(Prefs.cover.value))
    val text = pal.text.toArgb()
    val c = style.colors(pal.night, text)
    var picking by remember { mutableStateOf<MainColor?>(null) }
    var index by remember { mutableIntStateOf(2) }
    LaunchedEffect(Unit) { while (true) { delay(2600); index = (index + 1) % SAMPLE.size } }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop(tr("Main screen", "主界面"), onBack) {
            TextButton(onClick = { set { AppLyricsStyle() }; setCover { CoverStyle() } }) { Text(tr("Reset", "重置"), color = pal.sub) }
        }
        // preview: the cover (playing), and a few lines on the panel, as on the main screen
        Row(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(130.dp), contentAlignment = Alignment.Center) {
                if (cover.source == "none") Text(tr("No cover", "不显示封面"), color = pal.faint, fontSize = 13.sp)
                else AlbumCover(cover, playback.art, playing = true, size = if (cover.reflect) 86.dp else 112.dp,
                    title = playback.track?.title ?: tr("Preview Song", "预览歌曲"), seed = playback.track?.title ?: "preview")
            }
            Spacer(Modifier.width(10.dp))
            LyricsPanel(style, playback.art, Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in -1..1) {
                        val i = (index + k).mod(SAMPLE.size)
                        val color = Color(when { k == 0 -> c.current; k < 0 -> c.past; else -> c.upcoming })
                        Text(SAMPLE[i].text, color = color, fontSize = if (k == 0) 16.sp else 14.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, modifier = if (k != 0) Modifier.softFocus(style.softFocus) else Modifier)
                        val line = SAMPLE[i].translation
                        if (translateOn && line != null && k == 0) Text(line, color = Color(c.translation),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section(tr("Album cover", "专辑封面")) {
                Label(tr("Presets", "预设"))
                ValueChips(CoverStyle.PRESETS.map { it.first }, cover.presetName() ?: "") { name ->
                    CoverStyle.PRESETS.firstOrNull { it.first == name }?.let { (_, p) -> setCover { it.withPreset(p) } }
                }
                Label(tr("Picture", "图片"))
                ValueChips(CoverStyle.SOURCES, cover.source, { if (it == "album") tr("Album art", "专辑封面") else optionLabel(it) }) { v ->
                    setCover { it.copy(source = v) } }
                if (cover.source != "none") {
                    Label(tr("Shape", "形状"))
                    ValueChips(CoverStyle.SHAPES, cover.shape) { v -> setCover { it.copy(shape = v) } }
                    Label(tr("Movement while playing", "播放时的动效"))
                    ValueChips(CoverStyle.MOTIONS, cover.motion) { v -> setCover { it.copy(motion = v) } }
                    if (cover.motion != "still" && cover.motion != "tilt") {
                        SliderRow(tr("Speed (seconds per movement)", "速度（每次动作的秒数）"), tr("${cover.speed} s", "${cover.speed} 秒"),
                            cover.speed.toFloat(), 2f..20f, 17) { v -> setCover { it.copy(speed = v.roundToInt()) } }
                    }
                    if (cover.shape == "vinyl" || cover.shape == "cd") {
                        SwitchRow(tr("Tonearm", "唱臂"), cover.arm, tr("Rests on the disc while playing.", "播放时落在唱片上。")) { on ->
                            setCover { it.copy(arm = on) } }
                    }
                    SwitchRow(tr("Glow", "光晕"), cover.glow, tr("A soft glow in the album's colour.", "专辑主色的柔和光晕。")) { on ->
                        setCover { it.copy(glow = on) } }
                    SwitchRow(tr("Mirror reflection", "镜面倒影"), cover.reflect,
                        tr("The cover reflected underneath, as on a shiny surface.", "封面下方出现倒影，像放在光亮的台面上。")) { on ->
                        setCover { it.copy(reflect = on) } }
                    SliderRow(tr("Size on the main screen", "主界面上的大小"), "${cover.size}", cover.size.toFloat(), 56f..220f, 40) { v ->
                        setCover { it.copy(size = v.roundToInt()) } }
                    Hint(tr("From ${CoverStyle.LARGE} up, the cover sits above the song title.", "${CoverStyle.LARGE} 以上时，封面显示在歌名上方。"))
                }
            }

            Section(tr("Panel behind the lyrics", "歌词面板")) {
                ValueChips(AppLyricsStyle.PANELS, style.panel) { v -> set { it.copy(panel = v) } }
                when (style.panel) {
                    "glass" -> Hint(tr("Frosted glass: the album cover blurred behind a light (Day) or dark (Night) veil.",
                        "毛玻璃：专辑封面模糊后衬在浅色（日间）或深色（夜间）薄层之下。"))
                    "album" -> Hint(tr("Tinted with the album cover's main colour.", "使用专辑封面的主色调。"))
                    "colour" -> ColorRow(tr("Panel colour", "面板颜色"), style.panelColor) { picking = MainColor.PANEL }
                }
                if (style.panel != "none") {
                    SliderRow(tr("Panel opacity", "面板不透明度"), "${(style.panelOpacity * 100).roundToInt()}%", style.panelOpacity,
                        0f..1f, 19) { v -> set { it.copy(panelOpacity = v) } }
                }
                if (style.panel == "glass") {
                    SliderRow(tr("Blur", "模糊程度"), "${style.panelBlur}", style.panelBlur.toFloat(), 0f..100f, 19) { v ->
                        set { it.copy(panelBlur = v.roundToInt()) } }
                }
                SwitchRow(tr("Soft-focus lyrics", "柔焦歌词"), style.softFocus,
                    tr("Lines other than the current one are slightly blurred (Android 12 and newer).",
                        "当前行以外的歌词略微模糊（安卓 12 及以上）。")) { on -> set { it.copy(softFocus = on) } }
            }

            Section(tr("Lyric colours", "歌词颜色")) {
                ColorRow(tr("Current line", "当前行"), c.current) { picking = MainColor.CURRENT }
                ColorRow(tr("Lines already sung", "已唱过的行"), c.past) { picking = MainColor.PAST }
                ColorRow(tr("Lines to come", "未唱的行"), c.upcoming) { picking = MainColor.UPCOMING }
                ColorRow(tr("Translation", "翻译"), c.translation) { picking = MainColor.TRANSLATION }
                SwitchRow(tr("Match the app's Night / Day style", "跟随应用的夜间 / 日间风格"), style.styleColors,
                    tr("Picking a colour above turns this off.", "选择上面任一颜色会自动关闭。")) { on ->
                    set { if (on) it.copy(styleColors = true) else it.own(pal.night, text) } }
                val rows: List<Triple<String, Int, (AppLyricsStyle, Int) -> AppLyricsStyle>> = listOf(
                    Triple(tr("Lines already sung: opacity", "已唱过的行：不透明度"), c.past, { st, v -> st.copy(past = v) }),
                    Triple(tr("Lines to come: opacity", "未唱的行：不透明度"), c.upcoming, { st, v -> st.copy(upcoming = v) }),
                )
                for ((label, value, put) in rows) {
                    val alpha = (value ushr 24) / 255f
                    SliderRow(label, "${(alpha * 100).roundToInt()}%", alpha, 0.1f..1f, 17) { v ->
                        set { s -> s.own(pal.night, text).let { o ->
                            put(o, (((v * 255).roundToInt() and 0xFF) shl 24) or (value and 0xFFFFFF)) } }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    picking?.let { target ->
        val initial = when (target) {
            MainColor.CURRENT -> c.current; MainColor.PAST -> c.past; MainColor.UPCOMING -> c.upcoming
            MainColor.TRANSLATION -> c.translation; MainColor.PANEL -> style.panelColor
        }
        ColorPickerDialog(target.title, initial or 0xFF000000.toInt(), onDismiss = { picking = null }) { new ->
            fun keep(old: Int) = (old and 0xFF000000.toInt()) or (new and 0xFFFFFF)
            set {
                val o = if (target == MainColor.PANEL) it else it.own(pal.night, text)
                when (target) {
                    MainColor.CURRENT -> o.copy(current = keep(o.current))
                    MainColor.PAST -> o.copy(past = keep(o.past))
                    MainColor.UPCOMING -> o.copy(upcoming = keep(o.upcoming))
                    MainColor.TRANSLATION -> o.copy(translation = keep(o.translation))
                    MainColor.PANEL -> o.copy(panelColor = new)
                }
            }
            picking = null
        }
    }
}
