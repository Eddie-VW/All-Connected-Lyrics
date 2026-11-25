package com.autosynced.lyrics

import org.json.JSONObject

/**
 * Colours of the big scrolling lyrics on the app's main screen. With [styleColors] they follow
 * the Night / Day style (as before); otherwise your own. Colours are ARGB, so they may be see-through.
 */
data class AppLyricsStyle(
    val styleColors: Boolean = true,
    val current: Int = 0xFFFFFFFF.toInt(),
    val past: Int = 0x8CFFFFFF.toInt(),          // lines already sung
    val upcoming: Int = 0x61FFFFFF,              // lines still to come
    val translation: Int = 0xD9FFFFFF.toInt(),
    val panel: String = "glass",                 // glass (frosted, cover blurred behind) / album (tinted by the cover) / colour / none
    val panelColor: Int = 0xFF2A2A2A.toInt(),
    val panelOpacity: Float = 0.85f,             // how solid the panel is, 0 = see-through
    val panelBlur: Int = 70,                     // 0..100, for "glass"
    val softFocus: Boolean = false,              // lines other than the current one slightly blurred (as on the PC in Day)
    val glow: Boolean = false,                   // soft glow around the current line
    val glowColor: Int = 0,                      // 0 = the line's own colour
    val glowStrength: Float = 0.75f,             // 0.1..1
    val glowBlur: Int = 14,                      // dp, 2..40
    val fullScreen: Boolean = false,             // the panel's background fills the whole screen (lock-screen style)
    val lively: Boolean = true,                  // full screen: slowly moving glowing colours behind the lyrics
) {
    data class Colors(val current: Int, val past: Int, val upcoming: Int, val translation: Int)

    /** Night / Day style colours ([text] is the style's text colour), or your own. */
    fun colors(night: Boolean, text: Int): Colors = when {
        !styleColors -> Colors(current, past, upcoming, translation)
        night -> Colors(0xFFFFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0x61FFFFFF, 0xD9FFFFFF.toInt())
        else -> Colors(text, withAlpha(text, 0.3f), withAlpha(text, 0.3f), withAlpha(text, 0.8f))
    }

    /** Switches to your own colours, starting from the ones showing now. */
    fun own(night: Boolean, text: Int): AppLyricsStyle {
        if (!styleColors) return this
        val c = colors(night, text)
        return copy(styleColors = false, current = c.current, past = c.past, upcoming = c.upcoming, translation = c.translation)
    }

    fun toJson(): String = JSONObject().put("styleColors", styleColors).put("current", current).put("past", past)
        .put("upcoming", upcoming).put("translation", translation).put("panel", panel).put("panelColor", panelColor)
        .put("panelOpacity", panelOpacity.toDouble()).put("panelBlur", panelBlur).put("softFocus", softFocus)
        .put("glow", glow).put("fullScreen", fullScreen)
        .put("glowColor", glowColor).put("glowStrength", glowStrength.toDouble()).put("glowBlur", glowBlur)
        .put("lively", lively)
        .toString()

    companion object {
        val PANELS = listOf("glass", "album", "colour", "none")

        fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0xFFFFFF)

        fun fromJson(text: String?): AppLyricsStyle {
            val d = AppLyricsStyle()
            val j = try { JSONObject(text ?: return d) } catch (_: Exception) { return d }
            fun i(k: String, v: Int) = if (j.has(k)) j.optInt(k, v) else v
            return AppLyricsStyle(
                styleColors = j.optBoolean("styleColors", d.styleColors),
                current = i("current", d.current), past = i("past", d.past), upcoming = i("upcoming", d.upcoming),
                translation = i("translation", d.translation),
                panel = j.optString("panel", d.panel).takeIf { it in PANELS } ?: d.panel,
                panelColor = i("panelColor", d.panelColor),
                panelOpacity = (if (j.has("panelOpacity")) j.optDouble("panelOpacity", 0.85).toFloat() else d.panelOpacity).coerceIn(0f, 1f),
                panelBlur = i("panelBlur", d.panelBlur).coerceIn(0, 100),
                softFocus = j.optBoolean("softFocus", d.softFocus),
                glow = j.optBoolean("glow", d.glow),
                glowColor = i("glowColor", d.glowColor),
                glowStrength = (if (j.has("glowStrength")) j.optDouble("glowStrength", 0.75).toFloat() else d.glowStrength).coerceIn(0.1f, 1f),
                glowBlur = i("glowBlur", d.glowBlur).coerceIn(2, 40),
                lively = j.optBoolean("lively", d.lively),
                fullScreen = j.optBoolean("fullScreen", d.fullScreen),
            )
        }
    }
}
