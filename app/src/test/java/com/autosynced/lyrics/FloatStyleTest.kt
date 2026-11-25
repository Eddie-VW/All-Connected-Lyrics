package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs on the PC: floating window settings are saved and read back, and the right rows are shown. */
class FloatStyleTest {
    private val lines = listOf(
        Line(0, "one", "一"), Line(1, "two"), Line(2, "three", "三"), Line(3, ""), Line(4, "five"),
    )

    @Test fun savesAndLoadsEverySetting() {
        val s = FloatStyle(widthPct = 70, opacity = 0.6f, bgOpacity = 0f, linesBefore = 2, linesAfter = 3,
            showHeader = true, font = "serif", currentSize = 22, boldCurrent = false, outline = true,
            outlineColor = 0xFF112233.toInt(), styleColors = false, currentColor = 0xFFFFD43B.toInt(),
            animation = "slide", locked = true, offsetMs = -400, align = "left", controlsOpacity = 0.5f)
        assertEquals(s, FloatStyle.fromJson(s.toJson()))
    }

    @Test fun badOrOldSettingsFallBackSafely() {
        assertEquals(FloatStyle(), FloatStyle.fromJson(null))
        assertEquals(FloatStyle(), FloatStyle.fromJson("not json"))
        val odd = FloatStyle.fromJson("""{"widthPct": 500, "linesAfter": -2, "animation": "spin", "opacity": 0}""")
        assertEquals(100, odd.widthPct)
        assertEquals(0, odd.linesAfter)
        assertEquals("fade", odd.animation)
        assertEquals(0.2f, odd.opacity)
        assertEquals(FloatStyle().currentSize, odd.currentSize)     // missing keys keep their defaults
    }

    @Test fun quickButtonsChangeSizeAndColour() {
        val s = FloatStyle(currentSize = 17, otherSize = 13, headerSize = 11)
        assertEquals(listOf(18, 14, 12), s.resized(+1).let { listOf(it.currentSize, it.otherSize, it.headerSize) })
        assertEquals(10, FloatStyle(currentSize = 10).resized(-1).currentSize)          // never below the minimum
        // picking a colour while following Day style keeps Day's other colours
        val own = FloatStyle().ownColors(night = false)
        assertEquals(false, own.styleColors)
        assertEquals(FloatStyle().colors(false), own.colors(false))
    }

    @Test fun picksRowsAroundTheCurrentLine() {
        val style = FloatStyle(linesBefore = 1, linesAfter = 2)
        val rows = LyricRows.build(lines, 2, style, translate = true)
        assertEquals(listOf("two", "three", "三", "♪", "five"), rows.map { it.text })
        assertEquals(listOf(Row.Kind.PREVIOUS, Row.Kind.CURRENT, Row.Kind.TRANSLATION, Row.Kind.NEXT, Row.Kind.NEXT),
            rows.map { it.kind })
        // translation off: no translation row
        assertEquals(listOf("two", "three", "♪", "five"), LyricRows.build(lines, 2, style, translate = false).map { it.text })
    }

    @Test fun handlesTheStartAndEndOfTheSong() {
        val style = FloatStyle(linesBefore = 2, linesAfter = 2)
        assertEquals(listOf("♪", "one", "two"), LyricRows.build(lines, -1, style, false).map { it.text })   // before line 1
        assertEquals(listOf("three", "♪", "five"), LyricRows.build(lines, 4, style, false).map { it.text })
    }
}
