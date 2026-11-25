package com.autosynced.lyrics

import java.security.MessageDigest
import kotlin.math.max

/**
 * Lyrics files in and out: .lrc, .srt, .vtt, .ass / .ssa and .txt (pure, so it's tested on a PC).
 * A second line on the same time stamp (LRC), or a second subtitle line in another script
 * (SRT / VTT), is taken as that line's translation.
 */
object LyricFiles {
    val IMPORT_TYPES = listOf("lrc", "srt", "vtt", "ass", "ssa", "txt")
    val EXPORT_TYPES = listOf("lrc", "srt", "txt")

    /** The lines in a file's [text]; [name] (e.g. "song.srt") decides how it's read. */
    fun read(name: String, text: String): List<Line> {
        val clean = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return when (name.substringAfterLast('.', "").lowercase()) {
            "lrc" -> lrc(clean)
            "srt", "vtt" -> subtitles(clean)
            "ass", "ssa" -> ass(clean)
            else -> if (stamp.containsMatchIn(clean)) lrc(clean) else LyricEdits.fromText(clean)
        }
    }

    private val stamp = Regex("""\[(\d+):(\d+(?:[.:]\d+)?)]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    private fun lrc(text: String): List<Line> {
        val shift = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = ArrayList<Pair<Long, String>>()
        for (raw in text.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = stamp.replace(raw, "").trim()
            for (m in stamps) {
                val secs = m.groupValues[2].replace(':', '.').toDouble()
                timed.add(max(0L, m.groupValues[1].toLong() * 60_000 + Math.round(secs * 1000) - shift) to words)
            }
        }
        // stable sort keeps "line, then its translation" on the same time
        val sorted = timed.withIndex().sortedWith(compareBy({ it.value.first }, { it.index })).map { it.value }
        val out = ArrayList<Line>()
        for ((t, w) in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.timeMs == t && last.text.isNotEmpty() && last.translation == null && w.isNotEmpty()) {
                out[out.size - 1] = last.copy(translation = w)
            } else out.add(Line(t, w))
        }
        return out
    }

    private val cueTime = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->""")

    private fun subtitles(text: String): List<Line> {
        val out = ArrayList<Line>()
        for (block in text.split(Regex("\n\\s*\n"))) {
            val rows = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val at = rows.indexOfFirst { cueTime.containsMatchIn(it) }
            if (at < 0) continue
            val m = cueTime.find(rows[at])!!
            val (h, mm, ss, frac) = m.destructured
            val ms = (h.ifEmpty { "0" }.toLong() * 3600 + mm.toLong() * 60 + ss.toLong()) * 1000 + frac.padEnd(3, '0').toLong()
            val words = rows.drop(at + 1).map { it.replace(Regex("<[^>]+>"), "").trim() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            // two lines in different scripts = the lyric and its translation; otherwise one wrapped line
            if (words.size >= 2 && hasHan(words[0]) != hasHan(words[1])) {
                out.add(Line(ms, words[0], words.drop(1).joinToString(" ")))
            } else out.add(Line(ms, words.joinToString(" ")))
        }
        return LyricEdits.sorted(out)
    }

    private val assTime = Regex("""(\d+):(\d{2}):(\d{2})[.:](\d{1,3})""")

    private fun ass(text: String): List<Line> {
        val out = ArrayList<Line>()
        for (raw in text.lines()) {
            if (!raw.startsWith("Dialogue:", ignoreCase = true)) continue
            val parts = raw.substringAfter(':').split(',', limit = 10)
            if (parts.size < 10) continue
            val m = assTime.find(parts[1]) ?: continue
            val (h, mm, ss, frac) = m.destructured
            val ms = (h.toLong() * 3600 + mm.toLong() * 60 + ss.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
            val words = parts[9].replace(Regex("""\{[^}]*}"""), "").replace("\\N", " ").replace("\\n", " ").trim()
            if (words.isNotEmpty()) out.add(Line(ms, words))
        }
        return LyricEdits.sorted(out)
    }

    private fun hasHan(s: String) = s.any { it in '一'..'鿿' }

    /** The lines as a file of [type] (lrc / srt / txt). */
    fun write(type: String, lines: List<Line>, title: String = "", artist: String = ""): String {
        val b = StringBuilder()
        when (type) {
            "srt" -> lines.filter { it.text.isNotBlank() }.forEachIndexed { i, l ->
                val end = lines.firstOrNull { it.timeMs > l.timeMs }?.timeMs ?: (l.timeMs + 5_000)
                b.append(i + 1).append('\n').append(srtTime(l.timeMs)).append(" --> ").append(srtTime(end)).append('\n')
                b.append(l.text).append('\n')
                l.translation?.let { b.append(it).append('\n') }
                b.append('\n')
            }
            "txt" -> lines.forEach { l -> b.append(l.text).append('\n'); l.translation?.let { b.append(it).append('\n') } }
            else -> {
                if (title.isNotEmpty()) b.append("[ti:").append(title).append("]\n")
                if (artist.isNotEmpty()) b.append("[ar:").append(artist).append("]\n")
                for (l in lines) {
                    val s = "[" + LyricEdits.stamp(l.timeMs) + "]"
                    b.append(s).append(l.text).append('\n')
                    l.translation?.let { b.append(s).append(it).append('\n') }
                }
            }
        }
        return b.toString()
    }

    private fun srtTime(ms: Long): String =
        "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
}

/** Which custom cover a song uses: its album's (same album, same main artist) or, with no album, its own. */
object CoverKey {
    fun of(title: String, artist: String, album: String): String {
        val main = artist.split(Regex("""\s*(,|&|、|/|;| feat\.? | ft\.? | x )\s*""", RegexOption.IGNORE_CASE))
            .firstOrNull().orEmpty().trim().lowercase()
        val key = if (album.isNotBlank()) "album\n${album.trim().lowercase()}\n$main"
            else "song\n${title.trim().lowercase()}\n${artist.trim().lowercase()}"
        return MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun isAlbum(album: String) = album.isNotBlank()
}
