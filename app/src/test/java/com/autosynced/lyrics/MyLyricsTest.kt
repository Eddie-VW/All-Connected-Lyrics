package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs on the PC: your own lyrics are kept and read back; the editor's changes do the right thing. */
class MyLyricsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun keepsYourLyricsPerSong() {
        val mine = MyLyrics(tmp.root)
        assertNull(mine.load("Song", "Artist"))
        val lines = listOf(Line(1_000, "Hello", "你好"), Line(4_500, "World"))
        mine.save("Song", "Artist", lines)
        val back = mine.load(" song ", "ARTIST")!!          // same song, however it's spelt
        assertEquals(lines, back.lines)
        assertTrue(back.synced)
        assertEquals(MyLyrics.SOURCE, back.source)
        assertNull(mine.load("Other", "Artist"))
        mine.delete("Song", "Artist")
        assertFalse(mine.has("Song", "Artist"))
    }

    @Test fun editsWork() {
        val lines = listOf(Line(500, "a"), Line(2_000, "b"))
        assertEquals(listOf(0L, 1_200L), LyricEdits.shift(lines, -800).map { it.timeMs })   // never below 0
        assertEquals(listOf("first", "", "second"), LyricEdits.fromText("\n  first \r\n\nsecond\n\n").map { it.text })
        assertFalse(LyricEdits.isSynced(LyricEdits.fromText("a\nb")))
        assertEquals(listOf("x", "y", "z"), LyricEdits.sorted(listOf(Line(5, "y"), Line(1, "x"), Line(5, "z"))).map { it.text })
        assertEquals("01:23.45", LyricEdits.stamp(83_450))
        assertEquals("00:00.00", LyricEdits.stamp(-5))
    }
}
