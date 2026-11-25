package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs on the PC: lyrics files read and written in every supported format; cover keys. */
class LyricFilesTest {
    @Test fun readsLrcWithTranslations() {
        val lines = LyricFiles.read("a.lrc", "[ti:x]\n[00:01.50]Hello\n[00:01.50]你好\n[00:04.00]World\n[offset:500]")
        assertEquals(listOf(Line(1000, "Hello", "你好"), Line(3500, "World")), lines)
    }

    @Test fun readsSrtAndVtt() {
        val srt = "1\n00:00:01,200 --> 00:00:03,000\nHello there\n你好\n\n2\n00:00:04,000 --> 00:00:06,000\n<i>a long</i>\nwrapped line\n"
        assertEquals(listOf(Line(1200, "Hello there", "你好"), Line(4000, "a long wrapped line")), LyricFiles.read("x.SRT", srt))
        val vtt = "WEBVTT\n\n00:01.500 --> 00:03.000\nOne\n\n01:00:02.000 --> 01:00:04.000\nTwo\n"
        assertEquals(listOf(Line(1500, "One"), Line(3_602_000, "Two")), LyricFiles.read("x.vtt", vtt))
    }

    @Test fun readsAssAndText() {
        val ass = "[Events]\nDialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,{\\b1}Bold, words\\Nnext\n"
        assertEquals(listOf(Line(2500, "Bold, words next")), LyricFiles.read("x.ass", ass))
        assertEquals(listOf("a", "b"), LyricFiles.read("x.txt", "a\r\nb\n").map { it.text })
        assertEquals(1000L, LyricFiles.read("x.txt", "[00:01.00]timed in a txt")[0].timeMs)
    }

    @Test fun writesAndReadsBack() {
        val lines = listOf(Line(1000, "Hello", "你好"), Line(4500, "World"))
        assertEquals(lines, LyricFiles.read("a.lrc", LyricFiles.write("lrc", lines, "T", "A")))
        assertEquals(lines, LyricFiles.read("a.srt", LyricFiles.write("srt", lines)))
        assertEquals("Hello\n你好\nWorld\n", LyricFiles.write("txt", lines))
    }

    @Test fun coverKeysShareAnAlbum() {
        val a = CoverKey.of("Song 1", "Taylor Swift", "1989")
        assertEquals(a, CoverKey.of("Song 2", "Taylor Swift feat. Someone", "1989"))   // same album, same main artist
        assertNotEquals(a, CoverKey.of("Song 1", "Other Artist", "1989"))
        assertNotEquals(CoverKey.of("Song 1", "A", ""), CoverKey.of("Song 2", "A", ""))  // no album: per song
        assertNull(null)
    }
}
