package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Runs on the PC: lock-screen and main-screen lyric settings are saved, read back and kept in range. */
class LockStyleTest {
    @Test fun savesAndLoadsEveryLockSetting() {
        val s = LockStyle(enabled = true, onlyWhenPlaying = false, keepScreenOn = true, background = "solid",
            bgColor = 0xFF223344.toInt(), blur = 20, dim = 0.3f, opacity = 0.8f, showClock = false, clockSize = 90,
            clockBold = true, showDate = false, cover = "vinyl", coverSize = 200, showTitle = false, align = "left",
            linesBefore = 3, linesAfter = 4, font = "serif", currentSize = 30, otherSize = 15, translationPct = 90,
            boldCurrent = false, glow = true, lineSpacing = 20, animation = "zoom", styleColors = false,
            currentColor = 0xFFFFD43B.toInt(), otherColor = 0x66FFFFFF, translationColor = 0xCC69DB7C.toInt(),
            accentColor = 0xFF4DABF7.toInt(), showControls = false, showProgress = false)
        assertEquals(s, LockStyle.fromJson(s.toJson()))
    }

    @Test fun oddLockSettingsFallBackSafely() {
        assertEquals(LockStyle(), LockStyle.fromJson(null))
        val odd = LockStyle.fromJson("""{"background":"lava","linesAfter":99,"opacity":0,"align":"right","dim":5}""")
        assertEquals("blur", odd.background)
        assertEquals(LockStyle.MAX_AROUND, odd.linesAfter)
        assertEquals(0.3f, odd.opacity)
        assertEquals("center", odd.align)
        assertEquals(0.9f, odd.dim)
    }

    @Test fun ownColoursStartFromWhatShows() {
        val own = LockStyle().own()
        assertFalse(own.styleColors)
        assertEquals(LockStyle().colors(), own.colors())
    }

    @Test fun picksTheLinesAroundTheCurrentOne() {
        assertEquals(listOf(1 to -1, 2 to 0, 3 to 1, 4 to 2), LockRows.window(10, 2, 1, 2))
        assertEquals(listOf(0 to 0, 1 to 1), LockRows.window(2, 0, 3, 3))        // start of the song
        assertEquals(emptyList<Pair<Int, Int>>(), LockRows.window(0, 0, 1, 1))
    }

    @Test fun fullScreenBackgroundChoices() {
        val s = AppLyricsStyle(backdrop = "aurora", backdropSpeed = 2f, backdropStrength = 0.4f)
        assertEquals(s, AppLyricsStyle.fromJson(s.toJson()))
        assertEquals("mesh", AppLyricsStyle.fromJson("{}").backdrop)                     // new installs
        assertEquals("glow", AppLyricsStyle.fromJson("""{"lively":true}""").backdrop)     // 1.0 users keep their look
        assertEquals("none", AppLyricsStyle.fromJson("""{"lively":false}""").backdrop)    // … and "off" stays off
        assertEquals(3f, AppLyricsStyle.fromJson("""{"backdrop":"mesh","backdropSpeed":9}""").backdropSpeed)
    }

    @Test fun mainScreenColoursRoundTrip() {
        val s = AppLyricsStyle(styleColors = false, current = 0xFFFFD43B.toInt(), past = 0x40FFFFFF,
            upcoming = 0x30FFFFFF, translation = 0xCC4DABF7.toInt(), panel = "colour", panelColor = 0xFF102030.toInt(),
            panelOpacity = 0.4f, panelBlur = 30, softFocus = true, glow = true, fullScreen = true)
        assertEquals(s, AppLyricsStyle.fromJson(s.toJson()))
        assertEquals("glass", AppLyricsStyle.fromJson("""{"panel":"glitter"}""").panel)
        // Day style: the style's text colour, with the others see-through
        val day = AppLyricsStyle().colors(night = false, text = 0xFF1D1D1F.toInt())
        assertEquals(0xFF1D1D1F.toInt(), day.current)
        assertEquals(0x4C1D1D1F, day.past)
    }
}
