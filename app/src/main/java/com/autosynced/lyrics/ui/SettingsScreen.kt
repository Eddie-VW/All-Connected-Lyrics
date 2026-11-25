package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The settings pages, opened from the main screen's top bar (or straight from the floating window). */
enum class Page { HUB, FLOAT, LOCK, MAIN_LYRICS, TRANSLATION }

/** Settings: one list leading to each page. */
@Composable
fun SettingsHub(onBack: () -> Unit, open: (Page) -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val lock by Prefs.lockStyle.collectAsState()
    val translate by Prefs.translate.collectAsState()
    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(6.dp))
            HubRow("Floating lyrics", "Size, lines, font, colours, outline, buttons, lock, timing") { open(Page.FLOAT) }
            HubRow("Lock screen lyrics 歌词锁屏", if (lock.enabled) "On · background, clock, cover, lyrics, colours"
                else "Off · show lyrics over the lock screen") { open(Page.LOCK) }
            HubRow("Main screen lyrics", "Colours of the lyrics in this app and their panel") { open(Page.MAIN_LYRICS) }
            HubRow("Chinese translation 中文翻译", if (translate) "On · services and keys" else "Off · services and keys") {
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

private enum class MainColor(val title: String) { CURRENT("Current line"), PAST("Lines already sung"),
    UPCOMING("Lines to come"), TRANSLATION("Translation"), PANEL("Panel colour") }

/** Settings → Main screen lyrics: their colours and the panel behind them, with a small preview. */
@Composable
fun MainLyricsSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val style by Prefs.appLyrics.collectAsState()
    val translateOn by Prefs.translate.collectAsState()
    fun set(change: (AppLyricsStyle) -> AppLyricsStyle) = Prefs.setAppLyrics(context, change(Prefs.appLyrics.value))
    val text = pal.text.toArgb()
    val c = style.colors(pal.night, text)
    var picking by remember { mutableStateOf<MainColor?>(null) }
    var index by remember { mutableIntStateOf(2) }
    LaunchedEffect(Unit) { while (true) { delay(2600); index = (index + 1) % SAMPLE.size } }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop("Main screen lyrics", onBack) {
            TextButton(onClick = { set { AppLyricsStyle() } }) { Text("Reset", color = pal.sub) }
        }
        // preview: a few lines on the panel, like the main screen
        val panel = when {
            style.panel == "none" -> Color.Transparent
            style.panel == "colour" -> Color(style.panelColor)
            pal.night -> Color(0xFF3A3A3A)
            else -> Color.Transparent
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(panel).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in -1..1) {
                val i = (index + k).mod(SAMPLE.size)
                val color = Color(when { k == 0 -> c.current; k < 0 -> c.past; else -> c.upcoming })
                Text(SAMPLE[i].text, color = color, fontSize = if (k == 0) 22.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
                val tr = SAMPLE[i].translation
                if (translateOn && tr != null && k == 0) Text(tr, color = Color(c.translation), fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section("Colours") {
                ColorRow("Current line", c.current) { picking = MainColor.CURRENT }
                ColorRow("Lines already sung", c.past) { picking = MainColor.PAST }
                ColorRow("Lines to come", c.upcoming) { picking = MainColor.UPCOMING }
                ColorRow("Translation", c.translation) { picking = MainColor.TRANSLATION }
                SwitchRow("Match the app's Night / Day style", style.styleColors,
                    "Picking a colour above turns this off.") { on ->
                    set { if (on) it.copy(styleColors = true) else it.own(pal.night, text) } }
            }
            Section("See-through") {
                val rows: List<Triple<String, Int, (AppLyricsStyle, Int) -> AppLyricsStyle>> = listOf(
                    Triple("Lines already sung", c.past, { st, v -> st.copy(past = v) }),
                    Triple("Lines to come", c.upcoming, { st, v -> st.copy(upcoming = v) }),
                )
                for ((label, value, put) in rows) {
                    val alpha = (value ushr 24) / 255f
                    SliderRow(label, "${(alpha * 100).roundToInt()}%", alpha, 0.1f..1f, 17) { v ->
                        set { s -> s.own(pal.night, text).let { o ->
                            put(o, (((v * 255).roundToInt() and 0xFF) shl 24) or (value and 0xFFFFFF)) } }
                    }
                }
            }
            Section("Panel behind the lyrics") {
                ChipRow(AppLyricsStyle.PANELS, style.panel) { v -> set { it.copy(panel = v) } }
                if (style.panel == "colour") ColorRow("Panel colour", style.panelColor) { picking = MainColor.PANEL }
                Hint("“Album colour” tints the panel with the song's cover (in Night style).")
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
