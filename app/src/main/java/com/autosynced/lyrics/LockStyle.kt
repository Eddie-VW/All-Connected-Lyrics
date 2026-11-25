package com.autosynced.lyrics

import org.json.JSONObject

/**
 * Lock-screen lyrics (歌词锁屏): whether they're on, and how they look.
 * Saved as one JSON text; missing or odd values fall back to the defaults.
 */
data class LockStyle(
    // when
    val enabled: Boolean = false,
    val onlyWhenPlaying: Boolean = true,     // show only while music is playing (not just paused)
    val keepScreenOn: Boolean = false,       // stop the screen from going dark while they show
    // background
    val background: String = "blur",         // wallpaper / blur (album cover) / album (its colours) / solid
    val bgColor: Int = 0xFF101014.toInt(),
    val blur: Int = 60,                      // 0..100, for "blur"
    val dim: Float = 0.45f,                  // darkening over the background, 0..0.9
    val opacity: Float = 1f,                 // everything drawn on top (clock, cover, lyrics, buttons), 0.3..1
    // clock
    val showClock: Boolean = true,
    val clockSize: Int = 64,                 // sp
    val clockBold: Boolean = false,
    val showDate: Boolean = true,
    // song
    val cover: String = "rounded",           // none / square / rounded / circle / vinyl / cd (both spin) / polaroid
    val coverSize: Int = 96,                 // dp
    val showTitle: Boolean = true,
    // lyrics
    val align: String = "center",            // center / left
    val linesBefore: Int = 1,
    val linesAfter: Int = 2,
    val font: String = "sans-serif",         // as FloatStyle.font
    val currentSize: Int = 26,
    val otherSize: Int = 17,
    val translationPct: Int = 70,            // % of the current line's size
    val boldCurrent: Boolean = true,
    val glow: Boolean = false,               // soft glow around the current line
    val lineSpacing: Int = 12,               // dp between lines
    val animation: String = "slide",         // none / fade / slide / zoom
    // colours (white with see-through others by default, which suits any background)
    val styleColors: Boolean = true,
    val currentColor: Int = 0xFFFFFFFF.toInt(),
    val otherColor: Int = 0x80FFFFFF.toInt(),
    val translationColor: Int = 0xCCFFFFFF.toInt(),
    val accentColor: Int = 0xFFFFFFFF.toInt(), // clock, buttons, progress
    // extras
    val showControls: Boolean = true,
    val showProgress: Boolean = true,
) {
    data class Colors(val current: Int, val other: Int, val translation: Int, val accent: Int)

    fun colors(): Colors = if (styleColors) Colors(0xFFFFFFFF.toInt(), 0x80FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        else Colors(currentColor, otherColor, translationColor, accentColor)

    /** Switches to your own colours, starting from the ones showing now. */
    fun own(): LockStyle {
        if (!styleColors) return this
        val c = colors()
        return copy(styleColors = false, currentColor = c.current, otherColor = c.other,
            translationColor = c.translation, accentColor = c.accent)
    }

    fun toJson(): String = JSONObject()
        .put("enabled", enabled).put("onlyWhenPlaying", onlyWhenPlaying).put("keepScreenOn", keepScreenOn)
        .put("background", background).put("bgColor", bgColor).put("blur", blur).put("dim", dim.toDouble())
        .put("opacity", opacity.toDouble()).put("showClock", showClock).put("clockSize", clockSize)
        .put("clockBold", clockBold).put("showDate", showDate).put("cover", cover).put("coverSize", coverSize)
        .put("showTitle", showTitle).put("align", align).put("linesBefore", linesBefore).put("linesAfter", linesAfter)
        .put("font", font).put("currentSize", currentSize).put("otherSize", otherSize)
        .put("translationPct", translationPct).put("boldCurrent", boldCurrent).put("glow", glow)
        .put("lineSpacing", lineSpacing).put("animation", animation).put("styleColors", styleColors)
        .put("currentColor", currentColor).put("otherColor", otherColor).put("translationColor", translationColor)
        .put("accentColor", accentColor).put("showControls", showControls).put("showProgress", showProgress)
        .toString()

    companion object {
        const val MAX_AROUND = 5
        val BACKGROUNDS = listOf("blur", "album", "solid", "wallpaper")
        val COVERS = listOf("none", "square", "rounded", "circle", "vinyl", "cd", "polaroid")
        val ANIMATIONS = listOf("none", "fade", "slide", "zoom")

        fun fromJson(text: String?): LockStyle {
            val d = LockStyle()
            val j = try { JSONObject(text ?: return d) } catch (_: Exception) { return d }
            fun i(k: String, v: Int) = if (j.has(k)) j.optInt(k, v) else v
            fun f(k: String, v: Float) = if (j.has(k)) j.optDouble(k, v.toDouble()).toFloat() else v
            fun b(k: String, v: Boolean) = if (j.has(k)) j.optBoolean(k, v) else v
            fun s(k: String, v: String, allowed: List<String>? = null): String {
                val got = if (j.has(k)) j.optString(k, v) else v
                return if (allowed == null || got in allowed) got else v
            }
            return LockStyle(
                enabled = b("enabled", d.enabled), onlyWhenPlaying = b("onlyWhenPlaying", d.onlyWhenPlaying),
                keepScreenOn = b("keepScreenOn", d.keepScreenOn),
                background = s("background", d.background, BACKGROUNDS), bgColor = i("bgColor", d.bgColor),
                blur = i("blur", d.blur).coerceIn(0, 100), dim = f("dim", d.dim).coerceIn(0f, 0.9f),
                opacity = f("opacity", d.opacity).coerceIn(0.3f, 1f),
                showClock = b("showClock", d.showClock), clockSize = i("clockSize", d.clockSize).coerceIn(28, 120),
                clockBold = b("clockBold", d.clockBold), showDate = b("showDate", d.showDate),
                cover = s("cover", d.cover, COVERS), coverSize = i("coverSize", d.coverSize).coerceIn(48, 280),
                showTitle = b("showTitle", d.showTitle),
                align = s("align", d.align, listOf("center", "left")),
                linesBefore = i("linesBefore", d.linesBefore).coerceIn(0, MAX_AROUND),
                linesAfter = i("linesAfter", d.linesAfter).coerceIn(0, MAX_AROUND),
                font = s("font", d.font), currentSize = i("currentSize", d.currentSize).coerceIn(14, 48),
                otherSize = i("otherSize", d.otherSize).coerceIn(10, 36),
                translationPct = i("translationPct", d.translationPct).coerceIn(40, 120),
                boldCurrent = b("boldCurrent", d.boldCurrent), glow = b("glow", d.glow),
                lineSpacing = i("lineSpacing", d.lineSpacing).coerceIn(0, 40),
                animation = s("animation", d.animation, ANIMATIONS),
                styleColors = b("styleColors", d.styleColors), currentColor = i("currentColor", d.currentColor),
                otherColor = i("otherColor", d.otherColor), translationColor = i("translationColor", d.translationColor),
                accentColor = i("accentColor", d.accentColor),
                showControls = b("showControls", d.showControls), showProgress = b("showProgress", d.showProgress),
            )
        }
    }
}

/** The lock screen's lines around the current one: (line index, how far from the current line). */
object LockRows {
    fun window(count: Int, index: Int, before: Int, after: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (k in -before..after) {
            val i = index + k
            if (i in 0 until count) out.add(i to k)
        }
        return out
    }
}
