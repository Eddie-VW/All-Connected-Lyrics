package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs on the PC: album cover looks are saved, read back, and match the PC app's presets. */
class CoverStyleTest {
    @Test fun savesAndLoads() {
        val s = CoverStyle(source = "gradient", shape = "polaroid", motion = "sway", speed = 9, arm = false,
            glow = true, reflect = true, size = 160)
        assertEquals(s, CoverStyle.fromJson(s.toJson()))
        val odd = CoverStyle.fromJson("""{"shape":"hexagon","motion":"wobble","speed":99,"size":5}""")
        assertEquals("vinyl", odd.shape); assertEquals("spin", odd.motion); assertEquals(30, odd.speed); assertEquals(56, odd.size)
    }

    @Test fun presetsMatchThePcApp() {
        val vinyl = CoverStyle().withPreset(CoverStyle.PRESETS.first { it.first == "vinyl" }.second)
        assertEquals("vinyl", vinyl.presetName())
        assertEquals("mirror", CoverStyle(shape = "square", motion = "tilt", speed = 7, arm = false, reflect = true).presetName())
        // a preset keeps the picture source and size you chose
        val mine = CoverStyle(source = "gradient", size = 150).withPreset(CoverStyle.PRESETS.first { it.first == "cd" }.second)
        assertEquals("gradient", mine.source); assertEquals(150, mine.size); assertEquals("cd", mine.presetName())
        assertNull(CoverStyle(shape = "cd", motion = "float").presetName())
        assertEquals(8, CoverStyle.PRESETS.size)
    }
}
