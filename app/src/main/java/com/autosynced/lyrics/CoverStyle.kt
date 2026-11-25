package com.autosynced.lyrics

import org.json.JSONObject

/**
 * How the album cover looks on the main screen (like the PC app's cover: a picture, a shape,
 * a movement and extras). Presets set several at once, with the same names and values as the PC app.
 */
data class CoverStyle(
    val source: String = "album",     // album (the art) / gradient (a colour mix) / none (no cover)
    val shape: String = "vinyl",      // square / rounded / circle / vinyl / cd / polaroid
    val motion: String = "spin",      // still / spin / float / sway / pulse / tilt
    val speed: Int = 7,               // seconds per turn / per movement
    val arm: Boolean = true,          // tonearm on vinyl and CD
    val glow: Boolean = false,        // soft glow in the album's colour
    val reflect: Boolean = false,     // mirror reflection underneath
    val size: Int = 72,               // dp on the main screen; from LARGE up it sits above the title
) {
    companion object {
        const val LARGE = 120
        val SOURCES = listOf("album", "gradient", "none")
        val SHAPES = listOf("square", "rounded", "circle", "vinyl", "cd", "polaroid")
        val MOTIONS = listOf("still", "spin", "float", "sway", "pulse", "tilt")

        /** The PC app's presets: name -> (shape, motion, speed, arm, glow, reflect). */
        val PRESETS: List<Pair<String, CoverStyle>> = listOf(
            "vinyl" to CoverStyle(shape = "vinyl", motion = "spin", speed = 7, arm = true, glow = false, reflect = false),
            "spinning" to CoverStyle(shape = "circle", motion = "spin", speed = 12, arm = false, glow = false, reflect = false),
            "cd" to CoverStyle(shape = "cd", motion = "spin", speed = 4, arm = false, glow = true, reflect = false),
            "polaroid" to CoverStyle(shape = "polaroid", motion = "sway", speed = 9, arm = false, glow = false, reflect = false),
            "floating" to CoverStyle(shape = "rounded", motion = "float", speed = 6, arm = false, glow = true, reflect = false),
            "breathing" to CoverStyle(shape = "circle", motion = "pulse", speed = 7, arm = false, glow = true, reflect = false),
            "mirror" to CoverStyle(shape = "square", motion = "tilt", speed = 7, arm = false, glow = false, reflect = true),
            "classic" to CoverStyle(shape = "square", motion = "still", speed = 7, arm = false, glow = false, reflect = false),
        )

        fun fromJson(text: String?): CoverStyle {
            val d = CoverStyle()
            val j = try { JSONObject(text ?: return d) } catch (_: Exception) { return d }
            fun pick(k: String, v: String, allowed: List<String>) = j.optString(k, v).takeIf { it in allowed } ?: v
            return CoverStyle(
                source = pick("source", d.source, SOURCES), shape = pick("shape", d.shape, SHAPES),
                motion = pick("motion", d.motion, MOTIONS), speed = j.optInt("speed", d.speed).coerceIn(2, 30),
                arm = j.optBoolean("arm", d.arm), glow = j.optBoolean("glow", d.glow),
                reflect = j.optBoolean("reflect", d.reflect), size = j.optInt("size", d.size).coerceIn(56, 240),
            )
        }
    }

    /** This style with a preset's look (the picture source and size stay). */
    fun withPreset(p: CoverStyle) = copy(shape = p.shape, motion = p.motion, speed = p.speed, arm = p.arm,
        glow = p.glow, reflect = p.reflect)

    /** Which preset this matches, if any. */
    fun presetName(): String? = PRESETS.firstOrNull { (_, p) -> withPreset(p) == this }?.first

    fun toJson(): String = JSONObject().put("source", source).put("shape", shape).put("motion", motion)
        .put("speed", speed).put("arm", arm).put("glow", glow).put("reflect", reflect).put("size", size).toString()
}
