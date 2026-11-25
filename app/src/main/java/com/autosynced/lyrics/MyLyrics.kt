package com.autosynced.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Lyrics you edited or synced yourself, kept for good in [dir] (one file per song), and always
 * used instead of the online ones (like the PC app's my_lyrics folder). Lyrics are copyrighted:
 * these are for your own use on this phone.
 */
class MyLyrics(private val dir: File) {
    private fun file(title: String, artist: String): File {
        val key = "${title.trim().lowercase()}\n${artist.trim().lowercase()}"
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    fun load(title: String, artist: String): Lyrics? = try {
        val j = JSONObject(file(title, artist).readText())
        val arr = j.getJSONArray("lines")
        val lines = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Line(o.getLong("t"), o.optString("text"), o.optString("tr").ifEmpty { null })
        }
        Lyrics(lines, LyricEdits.isSynced(lines), SOURCE)
    } catch (_: Exception) { null }

    fun has(title: String, artist: String) = file(title, artist).exists()

    fun save(title: String, artist: String, lines: List<Line>) {
        dir.mkdirs()
        val arr = JSONArray()
        for (l in lines) arr.put(JSONObject().put("t", l.timeMs).put("text", l.text).put("tr", l.translation ?: ""))
        val f = file(title, artist)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(JSONObject().put("title", title).put("artist", artist).put("lines", arr).toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun delete(title: String, artist: String) { file(title, artist).delete() }

    /** How many songs have your own lyrics. */
    fun count(): Int = dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    companion object {
        const val SOURCE = "Mine"
    }
}

/** The lyrics editor's changes (pure, so they're tested on a PC). */
object LyricEdits {
    /** Timed if any line has a real time (all at 0 = plain text, not synced yet). */
    fun isSynced(lines: List<Line>): Boolean = lines.any { it.timeMs > 0 }

    /** Every line [ms] later (negative = earlier), never before 0. */
    fun shift(lines: List<Line>, ms: Long): List<Line> = lines.map { it.copy(timeMs = (it.timeMs + ms).coerceAtLeast(0)) }

    /** Plain pasted text -> untimed lines (blank lines kept as breaks, the edges trimmed). */
    fun fromText(text: String): List<Line> =
        text.replace("\r", "").lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
            .map { Line(0, it) }

    /** In time order, as they'll be shown (lines tied on time keep their order). */
    fun sorted(lines: List<Line>): List<Line> = lines.withIndex().sortedWith(compareBy({ it.value.timeMs }, { it.index })).map { it.value }

    /** "01:23.45" for a time in ms. */
    fun stamp(ms: Long): String {
        val cs = (ms.coerceAtLeast(0) + 5) / 10
        return "%02d:%02d.%02d".format(cs / 6000, cs / 100 % 60, cs % 100)
    }
}
