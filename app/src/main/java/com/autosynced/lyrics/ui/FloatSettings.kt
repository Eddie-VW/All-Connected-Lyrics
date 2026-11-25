package com.autosynced.lyrics.ui

import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.autosynced.lyrics.FloatStyle
import com.autosynced.lyrics.FloatingLyricsView
import com.autosynced.lyrics.Line
import com.autosynced.lyrics.LyricRows
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

// Made-up sample lines for the preview (with translations, to show how those look too).
private val SAMPLE = listOf(
    Line(0, "City lights are fading slow", "城市灯火缓缓暗去"),
    Line(1, "I keep your name inside my coat", "我把你的名字藏在大衣里"),
    Line(2, "Every train is going home", "每一班列车都驶向归途"),
    Line(3, "But I'm still waiting on my own", "而我仍独自等候"),
    Line(4, "Sing it louder, let it go", "唱得再响一些，放手吧"),
)

/** Settings → Floating lyrics: everything about how the floating window looks, with a live preview. */
@Composable
fun FloatSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val style by Prefs.floatStyle.collectAsState()
    val translateOn by Prefs.translate.collectAsState()
    fun set(change: (FloatStyle) -> FloatStyle) = Prefs.setFloatStyle(context, change(Prefs.floatStyle.value))
    var picking by remember { mutableStateOf<ColorTarget?>(null) }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = FloatingLyricsView.customFontFile(context)
        val ok = try {
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            Typeface.createFromFile(file) != null
        } catch (_: Exception) { false }
        if (ok) set { it.copy(font = FloatStyle.CUSTOM_FONT + ":" + System.currentTimeMillis()) }
        else Toast.makeText(context, "That file isn't a font Android can use (pick a .ttf or .otf file)", Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = pal.accent, fontSize = 16.sp) }
            Text("Floating lyrics", color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { set { FloatStyle() } }) { Text("Reset", color = pal.sub) }
        }
        Preview(style, translateOn)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section("Size") {
                SliderRow("Window width", "${style.widthPct}%", style.widthPct.toFloat(), 40f..100f, 11) {
                    set { s -> s.copy(widthPct = it.roundToInt()) } }
                SliderRow("Opacity", "${(style.opacity * 100).roundToInt()}%", style.opacity, 0.2f..1f, 15) {
                    set { s -> s.copy(opacity = it) } }
                SliderRow("Background", if (style.bgOpacity < 0.01f) "none" else "${(style.bgOpacity * 100).roundToInt()}%",
                    style.bgOpacity, 0f..1f, 19) { set { s -> s.copy(bgOpacity = it) } }
                SliderRow("Rounded corners", "${style.cornerDp}", style.cornerDp.toFloat(), 0f..32f, 15) {
                    set { s -> s.copy(cornerDp = it.roundToInt()) } }
            }

            Section("Lines") {
                SliderRow("Lines before the current one", "${style.linesBefore}", style.linesBefore.toFloat(),
                    0f..FloatStyle.MAX_AROUND.toFloat(), FloatStyle.MAX_AROUND - 1) { set { s -> s.copy(linesBefore = it.roundToInt()) } }
                SliderRow("Lines after the current one", "${style.linesAfter}", style.linesAfter.toFloat(),
                    0f..FloatStyle.MAX_AROUND.toFloat(), FloatStyle.MAX_AROUND - 1) { set { s -> s.copy(linesAfter = it.roundToInt()) } }
                SwitchRow("Song title & artist on top", style.showHeader) { set { s -> s.copy(showHeader = it) } }
                SwitchRow("Chinese translation under the current line", translateOn) { Prefs.setTranslate(context, it) }
            }

            Section("Text") {
                Label("Font")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, family) in FloatStyle.FONTS) {
                        Chip(label, style.font == family, Typeface.create(family, Typeface.NORMAL)) {
                            set { s -> s.copy(font = family) } }
                    }
                    Chip(if (style.font.startsWith(FloatStyle.CUSTOM_FONT)) "Your font ✓" else "Your font…",
                        style.font.startsWith(FloatStyle.CUSTOM_FONT), null) { fontPicker.launch(arrayOf("*/*")) }
                }
                Hint("“Your font…” picks a .ttf or .otf file from your phone, e.g. a Chinese font you downloaded.")
                SliderRow("Current line size", "${style.currentSize}", style.currentSize.toFloat(), 12f..36f, 23) {
                    set { s -> s.copy(currentSize = it.roundToInt()) } }
                SwitchRow("Bold current line", style.boldCurrent) { set { s -> s.copy(boldCurrent = it) } }
                SliderRow("Translation size", "${style.translationPct}%", style.translationPct.toFloat(), 50f..110f, 11) {
                    set { s -> s.copy(translationPct = it.roundToInt()) } }
                SliderRow("Other lines size", "${style.otherSize}", style.otherSize.toFloat(), 10f..28f, 17) {
                    set { s -> s.copy(otherSize = it.roundToInt()) } }
                if (style.showHeader) SliderRow("Title size", "${style.headerSize}", style.headerSize.toFloat(), 9f..20f, 10) {
                    set { s -> s.copy(headerSize = it.roundToInt()) } }
                Label("Alignment")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Centre", style.align == "center", null) { set { s -> s.copy(align = "center") } }
                    Chip("Left", style.align == "left", null) { set { s -> s.copy(align = "left") } }
                }
                SwitchRow("Outline around the letters", style.outline,
                    "Keeps the words readable over any background, e.g. with no background box.") {
                    set { s -> s.copy(outline = it) } }
                if (style.outline) {
                    SliderRow("Outline thickness", "${style.outlineWidth}", style.outlineWidth.toFloat(), 1f..5f, 3) {
                        set { s -> s.copy(outlineWidth = it.roundToInt()) } }
                    ColorRow("Outline colour", style.outlineColor) { picking = ColorTarget.OUTLINE }
                }
            }

            Section("Colours") {
                val shown = style.colors(pal.night)
                ColorRow("Current line (lyrics)", shown.current) { picking = ColorTarget.CURRENT }
                ColorRow("Translation", shown.translation) { picking = ColorTarget.TRANSLATION }
                ColorRow("Other lines", shown.other) { picking = ColorTarget.OTHER }
                ColorRow("Title & artist", shown.header) { picking = ColorTarget.HEADER }
                ColorRow("Background", shown.bg) { picking = ColorTarget.BG }
                SwitchRow("Match the app's Night / Day style", style.styleColors,
                    "On: colours follow the app's look (picking any colour above turns this off).") {
                    set { s -> if (it) s.copy(styleColors = true) else s.ownColors(pal.night) } }
            }

            Section("Behaviour") {
                Label("When the line changes")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, value) in listOf("No effect" to "none", "Fade" to "fade", "Slide up" to "slide")) {
                        Chip(label, style.animation == value, null) { set { s -> s.copy(animation = value) } }
                    }
                }
                SwitchRow("Lock the window", style.locked,
                    "Touches go through it to the app underneath, and it can't be moved. Unlock from the " +
                        "notification or here. (Android keeps a locked window a little see-through.)") {
                    set { s -> s.copy(locked = it) } }
                val secs = style.offsetMs / 1000.0
                SliderRow("Timing",
                    when {
                        style.offsetMs == 0L -> "as is"
                        style.offsetMs > 0 -> String.format(Locale.US, "%.1f s earlier", secs)
                        else -> String.format(Locale.US, "%.1f s later", -secs)
                    },
                    style.offsetMs / 1000f, -3f..3f, 59) { set { s -> s.copy(offsetMs = (it * 10).roundToInt() * 100L) } }
                Hint("If the lyrics come too late (e.g. with Bluetooth headphones), move this to the right.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { target ->
        ColorPickerDialog(target.title, target.get(style.ownColors(pal.night)), onDismiss = { picking = null }) { c ->
            set { target.set(it.ownColors(pal.night), c) }
            picking = null
        }
    }
}

private enum class ColorTarget(val title: String, val get: (FloatStyle) -> Int, val set: (FloatStyle, Int) -> FloatStyle) {
    BG("Background", { it.bgColor }, { s, c -> s.copy(bgColor = c) }),
    CURRENT("Current line", { it.currentColor }, { s, c -> s.copy(currentColor = c) }),
    TRANSLATION("Translation", { it.translationColor }, { s, c -> s.copy(translationColor = c) }),
    OTHER("Other lines", { it.otherColor }, { s, c -> s.copy(otherColor = c) }),
    HEADER("Title & artist", { it.headerColor }, { s, c -> s.copy(headerColor = c) }),
    OUTLINE("Outline", { it.outlineColor }, { s, c -> s.copy(outlineColor = c) }),
}

/** The real floating card, on a stand-in background, stepping through the sample lines. */
@Composable
private fun Preview(style: FloatStyle, translateOn: Boolean) {
    val pal = LocalPalette.current
    var index by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { while (true) { delay(2600); index = (index + 1) % SAMPLE.size } }
    val screenDp = LocalConfiguration.current.screenWidthDp
    Box(
        Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B5BDB), Color(0xFFE8590C), Color(0xFF2B8A3E)))),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { FloatingLyricsView(it) },
            update = { v ->
                v.apply(style, pal.night)
                v.alpha = style.opacity
                v.show("Preview Song — Sample Artist", LyricRows.build(SAMPLE, index, style, translateOn))
            },
            modifier = Modifier.width((screenDp * style.widthPct / 100f).dp),
        )
    }
}

// ---- little building blocks ------------------------------------------------------------------
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
internal fun Label(text: String) {
    Text(text, color = LocalPalette.current.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = LocalPalette.current.faint, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun SliderRow(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
                      onChange: (Float) -> Unit) {
    val pal = LocalPalette.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = pal.sub, fontSize = 14.sp)
        }
        Slider(value = current.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent,
                inactiveTrackColor = pal.surface2))
    }
}

@Composable
internal fun SwitchRow(label: String, on: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = pal.text, fontSize = 15.sp)
            if (detail != null) Text(detail, color = pal.faint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = pal.accent, checkedThumbColor = pal.accentInk))
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, typeface: Typeface?, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (selected) pal.accent else pal.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) pal.accentInk else pal.text, fontSize = 14.sp,
            fontFamily = typeface?.let { androidx.compose.ui.text.font.FontFamily(it) })
    }
}

@Composable
private fun ColorRow(label: String, color: Int, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(color)).border(1.dp, pal.line, CircleShape))
    }
}

private val PRESETS = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFFA7A7A7, 0xFF6E6E73, 0xFF2C2C2E, 0xFF000000,
    0xFFFF6B6B, 0xFFFFA94D, 0xFFFFD43B, 0xFF69DB7C, 0xFF1ED760, 0xFF38D9A9,
    0xFF4DABF7, 0xFF748FFC, 0xFFB197FC, 0xFFF783AC, 0xFFFA2D48, 0xFF8B5A2B,
).map { it.toInt() }

/** Ready-made colours plus hue / saturation / brightness sliders for any other. */
@Composable
private fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val pal = LocalPalette.current
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val chosen = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    fun take(c: Int) { val a = FloatArray(3); android.graphics.Color.colorToHSV(c, a); h = a[0]; s = a[1]; v = a[2] }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        title = { Text(title, color = pal.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in PRESETS.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in row) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == chosen) 3.dp else 1.dp, if (c == chosen) pal.accent else pal.line, CircleShape)
                                .clickable { take(c) })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(chosen))
                    .border(1.dp, pal.line, RoundedCornerShape(10.dp)))
                HsvSlider("Colour", h, 0f..360f, Brush.horizontalGradient(
                    (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })) { h = it }
                HsvSlider("Strength", s, 0f..1f, Brush.horizontalGradient(listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0f, v))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, v)))))) { s = it }
                HsvSlider("Brightness", v, 0f..1f, Brush.horizontalGradient(listOf(Color.Black,
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f)))))) { v = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text("Use this colour", color = pal.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = pal.sub) } },
    )
}

@Composable
private fun HsvSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    val pal = LocalPalette.current
    Column {
        Text(label, color = pal.sub, fontSize = 13.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(5.dp)).background(track))
            Slider(value = value, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent))
        }
    }
}
