package com.autosynced.lyrics

import org.json.JSONObject

/**
 * How the floating lyrics window looks and behaves (like the PC app's lyrics bar settings).
 * Saved as one JSON text in the app's settings; unknown or missing keys fall back to defaults.
 */
data class FloatStyle(
    // size & see-through
    val widthPct: Int = 88,               // window width, % of the screen's width
    val opacity: Float = 1f,              // the whole window
    val bgOpacity: Float = 0.92f,         // the background box only; 0 = text floats on its own
    val cornerDp: Int = 18,
    // which lines
    val linesBefore: Int = 0,             // earlier lines above the current one
    val linesAfter: Int = 1,              // coming lines below it
    val showHeader: Boolean = false,      // "song — artist" on top
    // text
    val font: String = "sans-serif",      // an Android font family, or "custom:…" (a font file you picked)
    val currentSize: Int = 17,            // sp
    val boldCurrent: Boolean = true,
    val otherSize: Int = 13,
    val headerSize: Int = 11,
    val translationPct: Int = 85,         // translation size, % of the current line's
    val align: String = "center",         // center / left
    val outline: Boolean = false,
    val outlineWidth: Int = 2,            // px at 1x; scaled by screen density
    val outlineColor: Int = 0xFF000000.toInt(),
    // colours: the app's Night / Day style, or your own
    val styleColors: Boolean = true,
    val bgColor: Int = 0xFF181818.toInt(),
    val currentColor: Int = 0xFFFFFFFF.toInt(),
    val translationColor: Int = 0xFFE0E0E0.toInt(),
    val otherColor: Int = 0xFFA7A7A7.toInt(),
    val headerColor: Int = 0xFFB3B3B3.toInt(),
    // behaviour
    val animation: String = "fade",       // none / fade / slide, when the line changes
    val locked: Boolean = false,          // touches go through to the app below; can't be dragged
    val offsetMs: Long = 0,               // + = lyrics show earlier (e.g. for Bluetooth delay)
) {
    companion object {
        const val CUSTOM_FONT = "custom"

        /** Font choices offered in settings: (label, Android font family). */
        val FONTS = listOf(
            "Default" to "sans-serif", "Medium" to "sans-serif-medium", "Light" to "sans-serif-light",
            "Condensed" to "sans-serif-condensed", "Serif" to "serif", "Mono" to "monospace",
            "Casual" to "casual", "Handwriting" to "cursive",
        )

        fun fromJson(text: String?): FloatStyle {
            val d = FloatStyle()
            val j = try { JSONObject(text ?: return d) } catch (_: Exception) { return d }
            fun i(k: String, v: Int) = if (j.has(k)) j.optInt(k, v) else v
            fun f(k: String, v: Float) = if (j.has(k)) j.optDouble(k, v.toDouble()).toFloat() else v
            fun b(k: String, v: Boolean) = if (j.has(k)) j.optBoolean(k, v) else v
            fun s(k: String, v: String) = if (j.has(k)) j.optString(k, v) else v
            fun l(k: String, v: Long) = if (j.has(k)) j.optLong(k, v) else v
            return FloatStyle(
                widthPct = i("widthPct", d.widthPct).coerceIn(30, 100),
                opacity = f("opacity", d.opacity).coerceIn(0.2f, 1f),
                bgOpacity = f("bgOpacity", d.bgOpacity).coerceIn(0f, 1f),
                cornerDp = i("cornerDp", d.cornerDp).coerceIn(0, 40),
                linesBefore = i("linesBefore", d.linesBefore).coerceIn(0, MAX_AROUND),
                linesAfter = i("linesAfter", d.linesAfter).coerceIn(0, MAX_AROUND),
                showHeader = b("showHeader", d.showHeader),
                font = s("font", d.font),
                currentSize = i("currentSize", d.currentSize).coerceIn(10, 48),
                boldCurrent = b("boldCurrent", d.boldCurrent),
                otherSize = i("otherSize", d.otherSize).coerceIn(8, 40),
                headerSize = i("headerSize", d.headerSize).coerceIn(8, 30),
                translationPct = i("translationPct", d.translationPct).coerceIn(40, 120),
                align = s("align", d.align).takeIf { it == "center" || it == "left" } ?: d.align,
                outline = b("outline", d.outline),
                outlineWidth = i("outlineWidth", d.outlineWidth).coerceIn(1, 6),
                outlineColor = i("outlineColor", d.outlineColor),
                styleColors = b("styleColors", d.styleColors),
                bgColor = i("bgColor", d.bgColor),
                currentColor = i("currentColor", d.currentColor),
                translationColor = i("translationColor", d.translationColor),
                otherColor = i("otherColor", d.otherColor),
                headerColor = i("headerColor", d.headerColor),
                animation = s("animation", d.animation).takeIf { it in setOf("none", "fade", "slide") } ?: d.animation,
                locked = b("locked", d.locked),
                offsetMs = l("offsetMs", d.offsetMs).coerceIn(-5_000, 5_000),
            )
        }

        const val MAX_AROUND = 3
    }

    fun toJson(): String = JSONObject()
        .put("widthPct", widthPct).put("opacity", opacity.toDouble()).put("bgOpacity", bgOpacity.toDouble())
        .put("cornerDp", cornerDp).put("linesBefore", linesBefore).put("linesAfter", linesAfter)
        .put("showHeader", showHeader).put("font", font).put("currentSize", currentSize)
        .put("boldCurrent", boldCurrent).put("otherSize", otherSize).put("headerSize", headerSize)
        .put("translationPct", translationPct).put("align", align).put("outline", outline)
        .put("outlineWidth", outlineWidth).put("outlineColor", outlineColor).put("styleColors", styleColors)
        .put("bgColor", bgColor).put("currentColor", currentColor).put("translationColor", translationColor)
        .put("otherColor", otherColor).put("headerColor", headerColor).put("animation", animation)
        .put("locked", locked).put("offsetMs", offsetMs)
        .toString()

    /** The colours actually used: your own, or the Night / Day style's. */
    fun colors(night: Boolean): Colors = when {
        !styleColors -> Colors(bgColor, currentColor, translationColor, otherColor, headerColor)
        night -> Colors(0xFF181818.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFFA7A7A7.toInt(), 0xFFB3B3B3.toInt())
        else -> Colors(0xFFF7F7F9.toInt(), 0xFF1D1D1F.toInt(), 0xFF3A3A3C.toInt(), 0xFF6E6E73.toInt(), 0xFF8E8E93.toInt())
    }

    data class Colors(val bg: Int, val current: Int, val translation: Int, val other: Int, val header: Int)
}

/** One row of text in the floating window. */
data class Row(val text: String, val kind: Kind) {
    enum class Kind { PREVIOUS, CURRENT, TRANSLATION, NEXT }
}

/** Which rows the floating window shows for the line at [index] (pure, so it's tested on a PC). */
object LyricRows {
    fun build(lines: List<Line>, index: Int, style: FloatStyle, translate: Boolean): List<Row> {
        val rows = ArrayList<Row>()
        for (k in style.linesBefore downTo 1) {
            lines.getOrNull(index - k)?.let { rows.add(Row(it.text.ifBlank { "♪" }, Row.Kind.PREVIOUS)) }
        }
        rows.add(Row(lines.getOrNull(index)?.text?.ifBlank { "♪" } ?: "♪", Row.Kind.CURRENT))
        if (translate) lines.getOrNull(index)?.translation?.let { rows.add(Row(it, Row.Kind.TRANSLATION)) }
        for (k in 1..style.linesAfter) {
            lines.getOrNull(index + k)?.let { rows.add(Row(it.text.ifBlank { "♪" }, Row.Kind.NEXT)) }
        }
        return rows
    }

    /** A message instead of lyrics (loading, not found…): the song's title, then the message. */
    fun message(title: String, text: String): List<Row> =
        listOf(Row(title, Row.Kind.CURRENT), Row(text, Row.Kind.NEXT))
}
