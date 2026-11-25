package com.autosynced.lyrics.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autosynced.lyrics.tr

/** The name shown for a setting's value (fonts, backgrounds, shapes…), in the app's language. */
fun optionLabel(value: String): String = when (value) {
    // fonts
    "sans-serif" -> tr("Default", "默认")
    "sans-serif-medium" -> tr("Medium", "中粗")
    "sans-serif-light" -> tr("Light", "细体")
    "sans-serif-condensed" -> tr("Condensed", "窄体")
    "serif" -> tr("Serif", "衬线")
    "monospace" -> tr("Mono", "等宽")
    "casual" -> tr("Casual", "休闲")
    "cursive" -> tr("Handwriting", "手写")
    // backgrounds and panels
    "blur" -> tr("Blurred cover", "模糊封面")
    "album" -> tr("Cover colours", "封面配色")
    "solid", "colour" -> tr("Your colour", "自选颜色")
    "wallpaper" -> tr("Wallpaper", "壁纸")
    "glass" -> tr("Frosted glass", "毛玻璃")
    "none" -> tr("None", "无")
    // cover picture
    "gradient" -> tr("Colour mix", "混合色")
    // shapes
    "square" -> tr("Square", "方形")
    "rounded" -> tr("Rounded", "圆角")
    "circle" -> tr("Circle", "圆形")
    "vinyl" -> tr("Vinyl", "黑胶")
    "cd" -> tr("CD", "CD")
    "polaroid" -> tr("Polaroid", "拍立得")
    // movements
    "still" -> tr("Still", "静止")
    "spin" -> tr("Spin", "旋转")
    "float" -> tr("Float", "漂浮")
    "sway" -> tr("Sway", "摇摆")
    "pulse" -> tr("Pulse", "呼吸")
    "tilt" -> tr("Tilt (drag it)", "倾斜（拖动）")
    // cover presets
    "spinning" -> tr("Spinning picture", "旋转图片")
    "floating" -> tr("Floating", "悬浮")
    "breathing" -> tr("Breathing", "呼吸")
    "mirror" -> tr("Mirror", "镜面")
    "classic" -> tr("Classic", "经典")
    // line-change effects
    "fade" -> tr("Fade", "淡入")
    "slide" -> tr("Slide up", "上滑")
    "zoom" -> tr("Zoom", "缩放")
    // alignment
    "center" -> tr("Centre", "居中")
    "left" -> tr("Left", "靠左")
    else -> value
}

/** A row of chips to pick one of [values], each named by [optionLabel] (or [label]). */
@Composable
internal fun ValueChips(values: List<String>, selected: String, label: (String) -> String = ::optionLabel,
                        onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in values) Chip(label(v), selected == v, null) { onPick(v) }
    }
}
