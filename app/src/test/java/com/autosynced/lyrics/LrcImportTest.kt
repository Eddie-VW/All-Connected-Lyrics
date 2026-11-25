package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs on the PC: lyrics files from the PC app's "Send to phone" zip become your own lyrics. */
class LrcImportTest {
    private val lrc = "﻿[ti:Song Title]\r\n[ar:Some Singer]\r\n[length:03:20]\r\n[00:01.00]First line\r\n[00:04.50]Second line\r\n"

    @Test fun readsTagsAndTimes() {
        val s = LrcImport.parse("whatever.lrc", lrc.toByteArray())!!
        assertEquals("Song Title", s.title)
        assertEquals("Some Singer", s.artist)
        assertEquals(listOf(1_000L, 4_500L), s.lines.map { it.timeMs })
        assertEquals("First line", s.lines[0].text)
    }

    @Test fun namesFromTheFileWhenNoTags() {
        val s = LrcImport.parse("Some Singer - Song Title.lrc", "[00:02.00]Hi".toByteArray())!!
        assertEquals("Song Title", s.title)
        assertEquals("Some Singer", s.artist)
    }

    @Test fun plainTextIsKeptUntimed() {
        val s = LrcImport.parse("A - B.lrc", "[ti:B]\n[ar:A]\nOne\n\nTwo\n".toByteArray())!!
        assertEquals(listOf("One", "Two"), s.lines.map { it.text })
        assertEquals(false, LyricEdits.isSynced(s.lines))
    }

    @Test fun skipsWhatIsntLyrics() {
        assertNull(LrcImport.parse("notes.lrc", "[00:01.00]no title anywhere".toByteArray()))
        assertNull(LrcImport.parse("A - B.lrc", "[ti:B]\n[ar:A]\n".toByteArray()))
    }

    @Test fun readsTheZip() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            z.putNextEntry(ZipEntry("Some Singer - Song Title.lrc")); z.write(lrc.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("B - Other.lrc")); z.write("[00:01.00]x".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("readme.txt")); z.write("hello".toByteArray()); z.closeEntry()
        }
        val r = LrcImport.read("AutosyncedLyrics-my-lyrics.zip", zip.toByteArray().inputStream())
        assertEquals(listOf("Song Title", "Other"), r.songs.map { it.title })
        assertEquals(1, r.skipped)
        assertEquals(1, LrcImport.read("x.pdf", "x".toByteArray().inputStream()).skipped)
    }

    @Test fun importedSongsAreYourOwnLyrics() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val mine = MyLyrics(dir)
        val s = LrcImport.parse("x.lrc", lrc.toByteArray())!!
        mine.save(s.title, s.artist, s.lines)
        assertEquals(1, mine.count())
        assertEquals(2, mine.load("song title", "some singer")!!.lines.size)
        dir.deleteRecursively()
    }
}
