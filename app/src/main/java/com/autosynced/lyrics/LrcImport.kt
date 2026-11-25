package com.autosynced.lyrics

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lyrics synced in the PC app, brought to the phone: the .lrc files its My Lyrics page saves
 * (one by one, or all in the zip its "Send to phone" button makes). Each becomes one of your
 * own lyrics ([MyLyrics]), so it wins over the online ones and can be edited here.
 * Pure (no Android), so it's tested on a PC.
 */
object LrcImport {
    private const val MAX_FILE = 512 * 1024          // a .lrc is a few KB; anything huge isn't one
    private val tag = Regex("""^\[(ti|ar|al|length|by|offset|re|ve|au):(.*)]\s*$""", RegexOption.IGNORE_CASE)

    data class Song(val title: String, val artist: String, val lines: List<Line>)

    /** Title and singer from the [ti:] / [ar:] tags, else from a "Singer - Title.lrc" file name (the PC app's naming). */
    fun parse(fileName: String, bytes: ByteArray): Song? {
        if (bytes.size > MAX_FILE) return null
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").replace("\r", "")
        val tags = text.lineSequence().take(15).mapNotNull { tag.find(it.trim()) }
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').replace(Regex("""\.(lrc|txt)$""", RegexOption.IGNORE_CASE), "")
        val dash = base.indexOf(" - ")
        val title = tags["ti"]?.ifBlank { null } ?: if (dash > 0) base.substring(dash + 3).trim() else return null
        val artist = tags["ar"]?.ifBlank { null } ?: if (dash > 0) base.substring(0, dash).trim() else return null
        val synced = LrcParser.parse(text)
        val lines = synced.ifEmpty {
            text.lines().map { it.trim() }.filter { it.isNotEmpty() && !tag.matches(it) }.map { Line(0, it) }
        }
        return if (lines.isEmpty() || title.isEmpty() || artist.isEmpty()) null else Song(title, artist, lines)
    }

    /** Every song in a picked file: a zip of .lrc files, or one .lrc. Files that aren't lyrics are counted in [skipped]. */
    fun read(fileName: String, input: InputStream): Result {
        val songs = ArrayList<Song>()
        var skipped = 0
        if (fileName.endsWith(".zip", true)) {
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (!e.name.endsWith(".lrc", true)) { skipped++; continue }
                    val song = readCapped(z)?.let { parse(e.name, it) }
                    if (song != null) songs.add(song) else skipped++
                }
            }
        } else if (fileName.endsWith(".lrc", true) || fileName.endsWith(".txt", true)) {
            val song = readCapped(input)?.let { parse(fileName, it) }
            if (song != null) songs.add(song) else skipped++
        } else skipped++
        return Result(songs, skipped)
    }

    data class Result(val songs: List<Song>, val skipped: Int)

    /** Reads at most MAX_FILE bytes (null = bigger, so not a lyrics file). */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_FILE) return null
        }
        return out.toByteArray()
    }
}
