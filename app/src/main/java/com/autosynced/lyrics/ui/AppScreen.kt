package com.autosynced.lyrics.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf
import com.autosynced.lyrics.translateIcon
import com.autosynced.lyrics.CoverStyle
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.LyricsState
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.delay

@Composable
fun AppScreen(
    hasAccess: Boolean,
    floatingOn: Boolean,
    style: String,
    onGrantAccess: () -> Unit,
    onToggleStyle: () -> Unit,
    onToggleFloating: () -> Unit,
    requestedPage: String? = null,
    onPageOpened: () -> Unit = {},
) {
    val pal = LocalPalette.current
    val playback by NowPlaying.state.collectAsState()
    val tint = remember(playback.art) { dominantColor(playback.art) }
    val translateOn by Prefs.translate.collectAsState()
    // settings pages: the list (HUB) leads to each; Back goes to where you came from
    var page by remember { mutableStateOf<Page?>(null) }
    var fromHub by remember { mutableStateOf(false) }
    LaunchedEffect(requestedPage) {
        if (requestedPage == com.autosynced.lyrics.MainActivity.PAGE_FLOAT_SETTINGS) { page = Page.FLOAT; fromHub = false; onPageOpened() }
    }
    val back = { page = if (fromHub && page != Page.HUB) Page.HUB else null }
    // pages slide and fade: deeper pages come in from the end side, going back slides the other way
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = depth(targetState) > depth(initialState)
            val dir = if (deeper) 1 else -1
            (androidx.compose.animation.slideInHorizontally(tween(320)) { w -> dir * w / 4 } +
                androidx.compose.animation.fadeIn(tween(320))) togetherWith
                (androidx.compose.animation.slideOutHorizontally(tween(260)) { w -> -dir * w / 8 } +
                    androidx.compose.animation.fadeOut(tween(200)))
        },
        label = "pages",
    ) { p ->
        when (p) {
            Page.HUB -> SettingsHub(onBack = { page = null }) { page = it; fromHub = true }
            Page.FLOAT -> FloatSettingsScreen(back)
            Page.LOCK -> LockSettingsScreen(back)
            Page.MAIN -> MainScreenSettings(back)
            Page.TRANSLATION -> TranslationScreen(back)
            Page.EDITOR -> LyricsEditorScreen(back)
            Page.ABOUT -> AboutScreen(back)
            null -> MainScreen(hasAccess, floatingOn, style, onGrantAccess, onToggleStyle, onToggleFloating,
                open = { target, viaHub -> page = target; fromHub = viaHub })
        }
    }
}

/** How deep a page is: main screen 0, the settings list 1, a settings page 2. */
private fun depth(p: Page?): Int = when (p) { null -> 0; Page.HUB -> 1; else -> 2 }

@Composable
private fun MainScreen(
    hasAccess: Boolean, floatingOn: Boolean, style: String, onGrantAccess: () -> Unit, onToggleStyle: () -> Unit,
    onToggleFloating: () -> Unit, open: (Page, Boolean) -> Unit,
) {
    val pal = LocalPalette.current
    val playback by NowPlaying.state.collectAsState()
    val tint = remember(playback.art) { dominantColor(playback.art) }
    val translateOn by Prefs.translate.collectAsState()
    val bgTop by animateColorAsState(
        if (pal.night) (tint ?: Color(0xFF3A3A3A)) else (tint?.copy(alpha = .22f) ?: Color(0x22FA2D48)),
        tween(600), label = "tint")
    // full screen: the lyrics panel's background (e.g. the blurred cover) fills the whole screen, lock-screen style
    val context = androidx.compose.ui.platform.LocalContext.current
    val appLyrics by Prefs.appLyrics.collectAsState()
    val full = appLyrics.fullScreen && hasAccess
    val toggleFull = { Prefs.setAppLyrics(context, Prefs.appLyrics.value.let { it.copy(fullScreen = !it.fullScreen) }) }
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        if (full) {
            LyricsPanel(appLyrics, playback.art, Modifier.fillMaxSize(), corner = 0.dp) {}
            if (appLyrics.lively) LivelyGlow(playback.art, playback.playing, Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (full) Modifier else Modifier
                    .background(Brush.verticalGradient(listOf(bgTop, pal.bg), endY = 1400f))
                    .background(if (pal.night) Color.Transparent else pal.bg.copy(alpha = .55f)))
                .systemBarsPadding()
        ) {
            if (full) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text(tr("⤡ Exit full screen", "⤡ 退出全屏"), color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(pal.surface.copy(alpha = 0.45f))
                            .clickable(onClick = toggleFull).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            } else {
                TopBar(style, floatingOn, translateOn, onToggleStyle, onToggleFloating,
                    onTranslation = { open(Page.TRANSLATION, false) }, onFloatSettings = { open(Page.HUB, false) })
            }
            if (!hasAccess) {
                AccessCard(onGrantAccess)
            } else {
                NowPlayingScreen(tint, translateOn, full, onEdit = { open(Page.EDITOR, false) }, onFullScreen = toggleFull)
            }
        }
    }
}

// ---- top bar --------------------------------------------------------------------------------
@Composable
private fun TopBar(
    style: String, floatingOn: Boolean, translateOn: Boolean,
    onToggleStyle: () -> Unit, onToggleFloating: () -> Unit, onTranslation: () -> Unit, onFloatSettings: () -> Unit,
) {
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Lyrics Master", "歌词大师"), color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // translation: on/off and its settings
        Pill(translateIcon(), translateOn, onTranslation)
        Spacer(Modifier.width(8.dp))
        Pill(if (floatingOn) tr("Floating on", "悬浮：开") else tr("Floating off", "悬浮：关"), floatingOn, onToggleFloating)
        // floating window settings (size, colours, font, lines…)
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onFloatSettings),
            contentAlignment = Alignment.Center,
        ) { TuneIcon(pal.text) }
        // Night / Day switch
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onToggleStyle),
            contentAlignment = Alignment.Center,
        ) {
            if (style == "day") SunIcon(pal.text) else MoonIcon(pal.text)
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(animateColorAsState(if (on) pal.accent else pal.surface2, tween(250), label = "pill").value)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) pal.accentInk else pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---- first run: ask for notification access -------------------------------------------------
@Composable
private fun AccessCard(onGrantAccess: () -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(tr("One step to start", "开始前还差一步"), color = pal.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Text(tr("To see which song is playing in Spotify, YouTube Music, NetEase, QQ Music or any other " +
            "music app, Android needs you to turn on notification access for this app.\n\n" +
            "The app only reads what's playing; it never reads your notifications.",
            "为了知道 Spotify、YouTube Music、网易云音乐、QQ 音乐或其他音乐应用正在播放什么，安卓需要你为本应用开启通知使用权。\n\n" +
            "本应用只读取正在播放的歌曲，从不读取你的通知内容。"),
            color = pal.sub, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(pal.accent)
                .clickable(onClick = onGrantAccess),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("Turn on notification access", "开启通知使用权"), color = pal.accentInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("In the list that opens, find Lyrics Master, switch it on, then come back.", "在打开的列表中找到“歌词大师”并开启，然后返回。"),
            color = pal.faint, fontSize = 13.sp)
    }
}

// ---- now playing ----------------------------------------------------------------------------
@Composable
private fun ColumnScope.NowPlayingScreen(tint: Color?, translateOn: Boolean, full: Boolean, onEdit: () -> Unit,
                                         onFullScreen: () -> Unit) {
    val pal = LocalPalette.current
    val appLyrics by Prefs.appLyrics.collectAsState()
    val playback by NowPlaying.state.collectAsState()
    val lyricsState by LyricsRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {          // song position, smooth
        while (true) { now = NowPlaying.state.value.position() + Prefs.floatStyle.value.offsetMs; delay(50) }
    }
    val track = playback.track

    // header: the album cover (as set in Settings → Main screen) + title; a large cover sits above the title
    val cover by Prefs.cover.collectAsState()
    val titleBlock: @Composable (Boolean) -> Unit = { centered ->
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(track?.title ?: tr("Nothing playing", "没有正在播放的歌曲"), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
            Text(track?.artist ?: tr("Play a song in any music app", "在任意音乐应用中播放一首歌"), color = if (pal.night) pal.sub else pal.accent,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track != null && track.album.isNotBlank()) Text(track.album, color = pal.sub, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (track != null) Text(trf("on {0}", "来自 {0}", track.player), color = pal.faint, fontSize = 12.sp)
        }
    }
    val coverShown = cover.source != "none"
    if (coverShown && cover.size >= CoverStyle.LARGE) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumCover(cover, playback.art, playback.playing, cover.size.dp, track?.title ?: "", track?.title ?: "")
            Spacer(Modifier.height(8.dp))
            titleBlock(true)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (coverShown) {
                AlbumCover(cover, playback.art, playback.playing, cover.size.dp, track?.title ?: "", track?.title ?: "")
                Spacer(Modifier.width(14.dp))
            }
            Box(Modifier.weight(1f)) { titleBlock(false) }
        }
    }

    // translation status: "finding…" or why some lines have none
    val found = lyricsState as? LyricsState.Found
    val status = when {
        !translateOn || found == null -> null
        found.translating -> tr("Finding translations…", "正在查找翻译…")
        else -> found.note
    }
    // translation status on the left; edit (or add) the lyrics on the right
    if (track != null || status != null || !full) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status ?: "", color = pal.sub, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            if (!full) Text(tr("⤢ Full screen", "⤢ 全屏"), color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onFullScreen).padding(horizontal = 10.dp, vertical = 6.dp))
            if (track != null && lyricsState !is LyricsState.Loading) {
                val has = lyricsState is LyricsState.Found
                Text(if (has) tr("✎ Edit lyrics", "✎ 编辑歌词") else tr("＋ Add lyrics", "＋ 添加歌词"), color = pal.accent,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onEdit).padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }

    // lyrics
    // in full screen the background is already behind everything, so the lyrics need no box of their own
    LyricsPanel(if (full) appLyrics.copy(panel = "none") else appLyrics, playback.art,
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        androidx.compose.animation.Crossfade(targetState = lyricsState::class, animationSpec = tween(350), label = "lyrics") { _ ->
        when (val s = lyricsState) {
            is LyricsState.Found -> LyricsList(s.lyrics, now, playback.canSeek, translateOn)
            is LyricsState.Loading -> Message(tr("Loading lyrics…", "正在加载歌词…"))
            is LyricsState.NotFound -> Message(tr("No synced lyrics found for this song yet", "暂时没有找到这首歌的同步歌词"))
            is LyricsState.NetworkError -> Message(tr("Network error, unable to fetch lyrics", "网络错误，无法获取歌词"))
            is LyricsState.Idle -> Message(tr("Play a song and its lyrics will appear here, line by line.", "播放一首歌，歌词会在这里逐行显示。"))
        }
        }
    }

    // progress + controls
    Controls(playback.track?.durationMs ?: 0L, now, playback.playing, playback.canSeek)
}

@Composable
private fun Message(text: String) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (pal.night) Color.White.copy(alpha = .8f) else pal.sub, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp)
    }
}

@Composable
private fun LyricsList(lyrics: com.autosynced.lyrics.Lyrics, now: Long, canSeek: Boolean, translateOn: Boolean) {
    val pal = LocalPalette.current
    val appLyrics by Prefs.appLyrics.collectAsState()
    val c = appLyrics.colors(pal.night, pal.text.toArgb())
    val listState = rememberLazyListState()
    val current = if (lyrics.synced) lyrics.indexAt(now) else -1
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val third = with(LocalDensity.current) { (maxHeight * 0.32f).roundToPx() }
        LaunchedEffect(current) {       // keep the current line about a third of the way down
            if (current >= 0) listState.animateScrollToItem(current, -third)
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = maxHeight),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val isNow = i == current
                val color = Color(when {
                    !lyrics.synced || isNow -> c.current
                    i < current -> c.past
                    else -> c.upcoming
                })
                val scale by animateFloatAsState(if (isNow) 1f else .96f, tween(300), label = "scale")
                Column(Modifier.fillMaxWidth()
                    .clickable(enabled = lyrics.synced && canSeek) { NowPlaying.seekTo(line.timeMs) }) {
                    val soft = appLyrics.softFocus && lyrics.synced && !isNow
                    Text(
                        line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = (if (lyrics.synced) 24 else 18).sp * scale,
                        fontWeight = if (lyrics.synced) FontWeight.ExtraBold else FontWeight.SemiBold,
                        lineHeight = 30.sp,
                        style = glowStyle(GlowLook(appLyrics.glow && isNow && lyrics.synced, appLyrics.glowColor,
                            appLyrics.glowStrength, appLyrics.glowBlur), color),
                        modifier = Modifier.softFocus(soft),
                    )
                    val lineTr = line.translation
                    if (translateOn && lineTr != null) {
                        Text(
                            lineTr,
                            color = if (isNow || !lyrics.synced) Color(c.translation)
                                else Color(c.translation).copy(alpha = color.alpha * .85f),
                            fontSize = 17.sp * scale,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 23.sp,
                            modifier = Modifier.padding(top = 2.dp).softFocus(soft),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(durationMs: Long, now: Long, playing: Boolean, canSeek: Boolean) {
    val pal = LocalPalette.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val frac = if (durationMs > 0) (now.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
        Slider(
            value = if (dragging) dragValue else frac,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (canSeek && durationMs > 0) NowPlaying.seekTo((dragValue * durationMs).toLong())
                dragging = false
            },
            enabled = canSeek && durationMs > 0,
            colors = SliderDefaults.colors(thumbColor = pal.text, activeTrackColor = pal.text,
                inactiveTrackColor = pal.faint.copy(alpha = .4f), disabledThumbColor = pal.faint,
                disabledActiveTrackColor = pal.faint, disabledInactiveTrackColor = pal.faint.copy(alpha = .3f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(if (dragging) (dragValue * durationMs).toLong() else now), color = pal.sub, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(fmt(durationMs), color = pal.sub, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            RoundButton(56.dp, Color.Transparent, { NowPlaying.previous() }) { SkipIcon(pal.text, forward = false) }
            Spacer(Modifier.width(24.dp))
            RoundButton(68.dp, pal.accent, { NowPlaying.playPause() }) {
                if (playing) PauseIcon(pal.accentInk) else PlayIcon(pal.accentInk)
            }
            Spacer(Modifier.width(24.dp))
            RoundButton(56.dp, Color.Transparent, { NowPlaying.next() }) { SkipIcon(pal.text, forward = true) }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun RoundButton(size: androidx.compose.ui.unit.Dp, color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

// ---- spinning record ------------------------------------------------------------------------
@Composable
fun SpinningRecord(art: Bitmap?, playing: Boolean, size: androidx.compose.ui.unit.Dp) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing) {
            withFrameNanos { t ->
                if (last != 0L) angle = (angle + (t - last) / 1e9f * 360f / 7f) % 360f   // one turn per 7 s
                last = t
            }
        }
    }
    Box(Modifier.size(size).rotate(angle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(Color(0xFF0D0D10), r)
            for (k in 1..7) drawCircle(Color(0x14FFFFFF), r * (0.95f - k * 0.07f), style = Stroke(1f))
            drawCircle(Color(0x33FFFFFF), r, style = Stroke(1.5f))
        }
        val labelMod = Modifier.size(size * 0.56f).clip(CircleShape)
        if (art != null) {
            Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = labelMod)
        } else {
            Box(labelMod.background(Color(0xFF3A3A3A)))
        }
        Box(Modifier.size(size * 0.06f).clip(CircleShape).background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0x55FFFFFF), CircleShape))
    }
}

// ---- small drawn icons (play, pause, skip, sun, moon) ------------------------------------------
/** Three sliders: "adjust the floating window". */
@Composable private fun TuneIcon(c: Color) = Canvas(Modifier.size(22.dp)) {
    val w = size.width; val h = size.height; val stroke = w * 0.09f
    for ((i, knob) in listOf(0.3f, 0.68f, 0.45f).withIndex()) {
        val y = h * (0.22f + i * 0.28f)
        drawLine(c, Offset(w * 0.08f, y), Offset(w * 0.92f, y), stroke)
        drawCircle(c, w * 0.11f, Offset(w * knob, y))
    }
}

@Composable internal fun PlayIcon(c: Color) = Canvas(Modifier.size(26.dp)) {
    val p = Path().apply { moveTo(size.width * .28f, size.height * .16f); lineTo(size.width * .86f, size.height * .5f)
        lineTo(size.width * .28f, size.height * .84f); close() }
    drawPath(p, c)
}
@Composable internal fun PauseIcon(c: Color) = Canvas(Modifier.size(26.dp)) {
    val w = size.width * .22f
    drawRect(c, Offset(size.width * .22f, size.height * .16f), androidx.compose.ui.geometry.Size(w, size.height * .68f))
    drawRect(c, Offset(size.width * .56f, size.height * .16f), androidx.compose.ui.geometry.Size(w, size.height * .68f))
}
@Composable internal fun SkipIcon(c: Color, forward: Boolean) = Canvas(Modifier.size(28.dp)) {
    val w = size.width; val h = size.height
    val p = Path()
    if (forward) { p.moveTo(w * .2f, h * .2f); p.lineTo(w * .68f, h * .5f); p.lineTo(w * .2f, h * .8f) }
    else { p.moveTo(w * .8f, h * .2f); p.lineTo(w * .32f, h * .5f); p.lineTo(w * .8f, h * .8f) }
    p.close(); drawPath(p, c)
    val bx = if (forward) w * .7f else w * .18f
    drawRect(c, Offset(bx, h * .2f), androidx.compose.ui.geometry.Size(w * .12f, h * .6f))
}
@Composable private fun MoonIcon(c: Color) = Canvas(Modifier.size(22.dp)
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
    drawCircle(c, size.minDimension * .42f)
    drawCircle(Color.Black, size.minDimension * .34f, Offset(size.width * .66f, size.height * .36f),
        blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
}
@Composable private fun SunIcon(c: Color) = Canvas(Modifier.size(22.dp)) {
    val ctr = center; val r = size.minDimension
    drawCircle(c, r * .2f)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val s = Offset((ctr.x + Math.cos(a) * r * .32f).toFloat(), (ctr.y + Math.sin(a) * r * .32f).toFloat())
        val e = Offset((ctr.x + Math.cos(a) * r * .46f).toFloat(), (ctr.y + Math.sin(a) * r * .46f).toFloat())
        drawLine(c, s, e, strokeWidth = r * .08f)
    }
}
