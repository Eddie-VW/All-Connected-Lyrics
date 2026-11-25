package com.autosynced.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Lyrics from the PC app, through the cloud: the PC puts AutosyncedLyrics/sync.json.gz in a
 * WebDAV folder (any service, e.g. the free plans at Koofr or InfiniCLOUD) and this app reads it.
 * It holds the lyrics you synced on the PC and the lyrics the PC found online, with their
 * translations (also the AI ones, so they aren't paid for twice).
 *
 * Read-only: nothing is uploaded from the phone. Checked when the app starts and then at most
 * every 30 minutes as songs change; the download only happens when the file changed (ETag),
 * otherwise the check is one tiny request. The password is kept encrypted ([ApiKeyStore]).
 */
object CloudSync {
    private const val PREFS = "cloud_sync"
    private const val SLOT = "webdav_password"
    private const val FILE = "pc_sync.json.gz"
    private const val CHECK_EVERY_MS = 30 * 60_000L
    private const val MINE_TOLERANCE = 5.0        // s; like the PC: a longer/shorter version is another song
    private const val FOUND_TOLERANCE = 3.0
    const val SOURCE_MINE = "Mine (PC)"
    const val SOURCE_FOUND = "PC"

    class Song(val durationSec: Int, val mine: Boolean, val lyrics: Lyrics)

    @Volatile private var songs: Map<String, Song>? = null
    @Volatile var status: String = ""
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(c: Context) = File(c.filesDir, FILE)

    fun key(title: String, artist: String) = "${title.trim().lowercase()}\n${artist.trim().lowercase()}"

    // ---- settings ------------------------------------------------------------------------
    fun enabled(c: Context) = prefs(c).getBoolean("on", false)
    fun url(c: Context) = prefs(c).getString("url", "") ?: ""
    fun user(c: Context) = prefs(c).getString("user", "") ?: ""
    fun hasPassword(c: Context) = ApiKeyStore.has(c, SLOT)

    fun save(c: Context, on: Boolean, url: String, user: String, password: String?) {
        prefs(c).edit().putBoolean("on", on).putString("url", url.trim()).putString("user", user.trim())
            .remove("etag").putLong("checked", 0).apply()
        if (!password.isNullOrBlank()) ApiKeyStore.set(c, password, SLOT)
    }

    /** Turns sync off and forgets the PC's lyrics on this phone. */
    fun clear(c: Context) {
        prefs(c).edit().clear().apply()
        ApiKeyStore.set(c, null, SLOT)
        file(c).delete()
        songs = emptyMap()
        status = ""
    }

    // ---- reading the PC's file -------------------------------------------------------------
    /** The songs in the PC's file (pure, so it's tested on a PC). */
    fun parse(gz: ByteArray): Map<String, Song> {
        val text = GZIPInputStream(gz.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
        val root = JSONObject(text)
        if (root.optInt("v") != 1) return emptyMap()
        val arr = root.getJSONArray("songs")
        val out = HashMap<String, Song>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val ls = s.getJSONArray("lines")
            val lines = (0 until ls.length()).map { j ->
                val l = ls.getJSONArray(j)
                val tr = if (l.length() > 2 && !l.isNull(2)) l.getString(2).ifBlank { null } else null
                Line(l.getLong(0), l.getString(1), tr, aiTranslated = tr != null && l.length() > 3 && l.getInt(3) == 1)
            }
            if (lines.isEmpty()) continue
            val mine = s.optBoolean("mine")
            val k = key(s.getString("ti"), s.getString("ar"))
            if (mine || out[k]?.mine != true)                 // your own lyrics win over found ones
                out[k] = Song(s.optInt("d"), mine, Lyrics(lines, s.optBoolean("synced", true), if (mine) SOURCE_MINE else SOURCE_FOUND))
        }
        return out
    }

    /** The PC's lyrics for this song, or null (also null when the length says it's another version). */
    fun find(map: Map<String, Song>, title: String, artist: String, durationSec: Double): Lyrics? {
        val s = map[key(title, artist)] ?: return null
        val tolerance = if (s.mine) MINE_TOLERANCE else FOUND_TOLERANCE
        if (s.durationSec > 0 && durationSec > 0 && abs(s.durationSec - durationSec) > tolerance) return null
        return s.lyrics
    }

    /** For the song playing (call off the main thread: reads the file the first time). */
    fun find(c: Context, title: String, artist: String, durationSec: Double): Lyrics? {
        if (!enabled(c)) return null
        val map = songs ?: load(c)
        return find(map, title, artist, durationSec)
    }

    private fun load(c: Context): Map<String, Song> =
        (try { parse(file(c).readBytes()) } catch (_: Exception) { emptyMap() }).also { songs = it }

    fun count(c: Context): Int = (songs ?: load(c)).size

    // ---- downloading -----------------------------------------------------------------------
    /** Checks the cloud if it's time (or [now]). Returns true if new lyrics came down. Call off the main thread. */
    fun sync(c: Context, now: Boolean = false): Boolean {
        val p = prefs(c)
        if (!enabled(c)) return false
        if (!now && System.currentTimeMillis() - p.getLong("checked", 0) < CHECK_EVERY_MS) return false
        val url = url(c).trimEnd('/')
        val pw = ApiKeyStore.get(c, SLOT)
        if (url.isEmpty() || user(c).isEmpty() || pw == null) {
            status = tr("Enter the WebDAV address, user name and app password.", "请填写 WebDAV 地址、用户名和应用密码。")
            return false
        }
        p.edit().putLong("checked", System.currentTimeMillis()).apply()
        return try {
            val conn = URL("$url/AutosyncedLyrics/sync.json.gz").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            val auth = java.util.Base64.getEncoder().encodeToString("${user(c)}:$pw".toByteArray(Charsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $auth")
            p.getString("etag", null)?.let { conn.setRequestProperty("If-None-Match", it) }
            when (val code = conn.responseCode) {
                304 -> { status = trf("Up to date · {0} songs from the PC", "已是最新 · 来自电脑的 {0} 首", count(c)); false }
                200 -> {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val map = parse(bytes)                       // check it before keeping it
                    val tmp = File(c.filesDir, "$FILE.tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(file(c))) { file(c).delete(); tmp.renameTo(file(c)) }
                    songs = map
                    p.edit().putString("etag", conn.getHeaderField("ETag")).apply()
                    status = trf("Synced · {0} songs from the PC", "已同步 · 来自电脑的 {0} 首", map.size)
                    true
                }
                401, 403 -> { status = tr("The cloud said no: check the user name and app password.", "云端拒绝访问：请检查用户名和应用密码。"); false }
                404 -> { status = tr("Nothing there yet: turn on Sync to phone in the PC app first.", "云端还没有文件：请先在电脑版里打开“同步到手机”。"); false }
                else -> { status = trf("The cloud answered {0}. Check the WebDAV address.", "云端返回 {0}。请检查 WebDAV 地址。", code); false }
            }
        } catch (e: Exception) {
            status = tr("Couldn't reach the cloud (no internet?).", "无法连接云端（没有网络？）。")
            false
        }
    }
}
