package com.autosynced.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.tr
import kotlin.math.roundToInt

/** How a glowing line's glow looks: [color] 0 = the line's own colour; [strength] 0.1..1; [blur] in dp. */
data class GlowLook(val on: Boolean, val color: Int, val strength: Float, val blur: Int)

/** The text style that draws [look]'s glow around a line coloured [lineColor] (plain when it's off). */
@Composable
fun glowStyle(look: GlowLook, lineColor: Color): TextStyle {
    if (!look.on) return TextStyle.Default
    val px = with(LocalDensity.current) { look.blur.dp.toPx() }
    val base = if (look.color == 0) lineColor else Color(look.color)
    return TextStyle(shadow = Shadow(base.copy(alpha = look.strength), Offset.Zero, px))
}

/**
 * The glow's settings, the same on every page: on/off, colour (or the line's own),
 * strength and blur. [onChange] gets the whole new look.
 */
@Composable
internal fun GlowSettings(look: GlowLook, onChange: (GlowLook) -> Unit) {
    val pal = LocalPalette.current
    var picking by remember { mutableStateOf(false) }
    SwitchRow(tr("Glow around the current line", "当前行发光"), look.on) { onChange(look.copy(on = it)) }
    if (!look.on) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Glow colour", "光晕颜色"), color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Chip(tr("Same as the line", "与歌词同色"), look.color == 0, null) { onChange(look.copy(color = 0)) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp).clip(CircleShape)
            .background(if (look.color == 0) pal.surface2 else Color(look.color))
            .border(if (look.color != 0) 2.dp else 1.dp, if (look.color != 0) pal.accent else pal.line, CircleShape)
            .clickable { picking = true }, contentAlignment = Alignment.Center) {
            if (look.color == 0) Text("+", color = pal.sub, fontSize = 16.sp)
        }
    }
    SliderRow(tr("Glow strength", "光晕强度"), "${(look.strength * 100).roundToInt()}%", look.strength, 0.1f..1f, 17) {
        onChange(look.copy(strength = it)) }
    SliderRow(tr("Glow blur", "光晕模糊"), "${look.blur}", look.blur.toFloat(), 2f..40f, 37) {
        onChange(look.copy(blur = it.roundToInt())) }
    if (picking) ColorPickerDialog(tr("Glow colour", "光晕颜色"), if (look.color == 0) 0xFFFFFFFF.toInt() else look.color,
        onDismiss = { picking = false }) { c -> onChange(look.copy(color = c or 0xFF000000.toInt())); picking = false }
}
