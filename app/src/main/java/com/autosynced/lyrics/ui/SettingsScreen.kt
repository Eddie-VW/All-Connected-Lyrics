package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The settings pages, opened from the main screen's top bar (or straight from the floating window). */
enum class Page { HUB, FLOAT, LOCK, MAIN, TRANSLATION, EDITOR, ABOUT, CLOUD }

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
                ValueChips(Lang.LANGUAGES.map { it.first }, language, { Lang.nameOf(it) }) {
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
            HubRow(tr("Translation", "翻译"),
                if (translate) trf("On · into {0} · services and keys", "已开启 · 翻译成{0} · 服务和密钥", Lang.nameOf(Prefs.targetLang.value))
                else tr("Off · language, services and keys", "未开启 · 语言、服务和密钥")) {
                open(Page.TRANSLATION) }
            PcLyricsRow()
            HubRow(tr("Sync with the PC", "与电脑同步"),
                if (com.autosynced.lyrics.CloudSync.enabled(context)) tr("On · lyrics and translations from the PC app, through the cloud", "已开启 · 通过云端获取电脑版的歌词和翻译")
                else tr("Off · get the PC app's lyrics automatically, through a free cloud folder", "未开启 · 通过免费云盘自动获取电脑版的歌词")) { open(Page.CLOUD) }
            HubRow(tr("About", "关于"), trf("Version {0} · updates · developer", "版本 {0} · 检查更新 · 开发者",
                com.autosynced.lyrics.BuildConfig.VERSION_NAME)) { open(Page.ABOUT) }
        }
    }
}

/** Import the lyrics synced in the PC app (its My Lyrics page → Send to phone makes a zip of them). */
@Composable
private fun PcLyricsRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(-1) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { count = withContext(Dispatchers.IO) { LyricsRepo.mineCount() } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        note = tr("Importing…", "正在导入…")
        scope.launch {
            val (added, skipped) = withContext(Dispatchers.IO) { LyricsRepo.importFromPc(context, uris) }
            count = withContext(Dispatchers.IO) { LyricsRepo.mineCount() }
            note = trf("Songs imported: {0}", "已导入 {0} 首", added) +
                (if (skipped > 0) trf(" · files skipped (not lyrics): {0}", " · 跳过非歌词文件：{0} 个", skipped) else "")
        }
    }
    val total = when {
        count < 0 -> ""
        count == 0 -> tr("None yet", "还没有")
        else -> trf("Songs with your own lyrics: {0}", "有你自己歌词的歌曲：{0} 首", count)
    }
    HubRow(tr("Lyrics from the PC", "电脑上的歌词"),
        (note?.let { "$it · " } ?: "") + tr("On the PC: My Lyrics → Send to phone, then pick the zip here. ", "电脑上：我的歌词 → 发送到手机，然后在这里选择该 zip。") + total) {
        pick.launch(arrayOf("application/zip", "application/octet-stream", "text/*", "*/*"))
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
                            maxLines = 1, modifier = if (k != 0) Modifier.softFocus(style.softFocus) else Modifier,
                            style = glowStyle(GlowLook(style.glow && k == 0, style.glowColor, style.glowStrength,
                                (style.glowBlur * 0.7f).toInt()), color))
                        val line = SAMPLE[i].translation
                        if (translateOn && line != null && k == 0) Text(line, color = Color(c.translation),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            ThisSong()
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
                        SliderRow(tr("Speed (seconds per movement)", "速度（每次动作的秒数）"), trf("{0} s", "{0} 秒", cover.speed),
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
                    Hint(trf("From {0} up, the cover sits above the song title.", "{0} 以上时，封面显示在歌名上方。", CoverStyle.LARGE))
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
                GlowSettings(GlowLook(style.glow, style.glowColor, style.glowStrength, style.glowBlur)) { g ->
                    set { it.copy(glow = g.on, glowColor = g.color, glowStrength = g.strength, glowBlur = g.blur) } }
                SwitchRow(tr("Full screen", "全屏"), style.fullScreen,
                    tr("The panel's background fills the whole screen, like the lock screen. Also: the ⤢ button above the lyrics.",
                        "面板背景铺满整个屏幕，像锁屏一样。也可以用歌词上方的 ⤢ 按钮切换。")) { on -> set { it.copy(fullScreen = on) } }
                if (style.fullScreen) {
                    Label(tr("Background animation", "背景动画"))
                    ValueChips(AppLyricsStyle.BACKDROPS, style.backdrop, ::backdropLabel) { v -> set { it.copy(backdrop = v) } }
                    Hint(backdropHint(style.backdrop))
                    // a live sample of the chosen animation, on the song playing (or sample colours)
                    LyricsPanel(style, playback.art, Modifier.fillMaxWidth().height(120.dp)) {
                        Backdrop(style.backdrop, playback.art, playing = true, speed = style.backdropSpeed,
                            strength = style.backdropStrength, modifier = Modifier.fillMaxSize())
                    }
                    if (style.backdrop != "none") {
                        SliderRow(tr("Animation speed", "动画速度"), "${(style.backdropSpeed * 100).roundToInt()}%",
                            style.backdropSpeed, 0.25f..3f, 10) { v -> set { it.copy(backdropSpeed = v) } }
                        SliderRow(tr("Animation intensity", "动画强度"), "${(style.backdropStrength * 100).roundToInt()}%",
                            style.backdropStrength, 0.1f..1f, 17) { v -> set { it.copy(backdropStrength = v) } }
                    }
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

/** Settings → Main screen → This song: its lyrics file (import / export) and its own cover picture. */
@Composable
private fun ThisSong() {
    val pal = LocalPalette.current
    val playback by NowPlaying.state.collectAsState()
    val track = playback.track
    val files = rememberFileActions(
        onImported = { lines -> com.autosynced.lyrics.LyricsRepo.saveMine(lines) },
        toExport = { (com.autosynced.lyrics.LyricsRepo.state.value as? com.autosynced.lyrics.LyricsState.Found)?.lyrics?.lines.orEmpty() },
    )
    Section(tr("This song", "当前歌曲")) {
        if (track == null) { Hint(tr("Play a song to import its lyrics or change its cover.", "播放一首歌，即可导入它的歌词或更换封面。")); return@Section }
        Text(track.title, color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "), color = pal.sub, fontSize = 13.sp)
        Label(tr("Lyrics file", "歌词文件"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Import…", "导入…"), false, null) { files.importLyrics() }
            for (type in com.autosynced.lyrics.LyricFiles.EXPORT_TYPES) Chip(trf("Export .{0}", "导出 .{0}", type), false, null) {
                files.exportLyrics(type) }
        }
        Hint(tr("Import .lrc, .srt, .vtt, .ass or .txt: it becomes this song's lyrics (kept on this phone, edit them any time).",
            "可导入 .lrc、.srt、.vtt、.ass 或 .txt，作为这首歌的歌词（保存在本机，随时可编辑）。"))
        Label(tr("Cover picture", "封面图片"))
        val hasCustom = com.autosynced.lyrics.CustomCovers.has(track.title, track.artist, track.album)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Choose a picture…", "选择图片…"), false, null) { files.pickCover() }
            if (hasCustom) Chip(tr("Use the original", "恢复原封面"), false, null) {
                com.autosynced.lyrics.CustomCovers.remove(track.title, track.artist, track.album); NowPlaying.reload() }
        }
        Hint(if (track.album.isNotBlank()) trf("Used for every song on the album “{0}”.", "用于专辑《{0}》中的所有歌曲。", track.album)
            else tr("This song has no album name, so the picture is used for this song only.", "这首歌没有专辑名，图片只用于这首歌。"))
    }
}

private fun backdropLabel(v: String): String = when (v) {
    "none" -> tr("Off", "关闭")
    "mesh" -> tr("Mesh gradient", "网格渐变")
    "fluid" -> tr("Fluid artwork", "流动封面")
    "glow" -> tr("Soft glow", "柔光")
    "aurora" -> tr("Aurora", "极光")
    "breathing" -> tr("Breathing", "呼吸")
    else -> v
}

private fun backdropHint(v: String): String = when (v) {
    "none" -> tr("The background stays still.", "背景保持静止。")
    "mesh" -> tr("Colours sampled from the cover flow slowly into each other, like Apple Music.",
        "从封面取色，颜色缓缓交融流动，类似 Apple Music。")
    "fluid" -> tr("The cover itself, heavily blurred and enlarged, slowly turning in two layers.",
        "封面本身被强烈模糊并放大，分两层缓缓旋转。")
    "glow" -> tr("Soft glowing clouds in the cover's colours drift behind the lyrics.", "封面配色的柔和光团在歌词后面漂移。")
    "aurora" -> tr("Waving bands of the cover's colours, like northern lights.", "封面配色的光带如极光般摆动。")
    "breathing" -> tr("The blurred cover slowly swells and brightens, like breathing.", "模糊的封面缓缓放大、变亮，像在呼吸。")
    else -> ""
} + " " + tr("It slows almost to a stop while the music is paused.", "音乐暂停时，动画几乎停下。")
