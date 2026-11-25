package com.autosynced.lyrics

import com.autosynced.lyrics.LyricsSources.optStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.max

/** A line from a music site's copy of the song, with that site's Chinese translation (if any). */
data class SiteLine(val timeMs: Long, val text: String, val translation: String?)

/**
 * Human Chinese translations from NetEase Cloud Music, QQ Music and Kugou (like the PC app's
 * sources.py). Each site has its own copy of the song, timed its own way, so the lines are
 * matched up with ours by [TranslationMatcher].
 */
object TranslationSources {
    private val NETEASE = mapOf("Referer" to "https://music.163.com/")
    private val QQ = mapOf("Referer" to "https://y.qq.com/")

    /**
     * Our lines with translations filled in from the sites. All three are asked at once;
     * NetEase is preferred, then QQ Music, and Kugou fills any lines still missing.
     */
    suspend fun attach(lines: List<Line>, title: String, artist: String, durationSec: Double): List<Line> =
        coroutineScope {
            fun ask(find: () -> List<SiteLine>?) = async(Dispatchers.IO) { try { find() } catch (_: Exception) { null } }
            val netease = ask { netease(title, artist, durationSec) }
            val qq = ask { qqMusic(title, artist, durationSec) }
            val kugou = ask { kugou(title, artist, durationSec) }
            var out = lines
            val best = netease.await() ?: qq.await()
            if (best != null) out = TranslationMatcher.attach(out, best)
            if (out.any { it.translation == null && TextRules.worthTranslating(it.text) }) {
                kugou.await()?.let { out = TranslationMatcher.attach(out, it) }
            } else kugou.cancel()
            TranslationMatcher.share(out)
        }

    // ---- NetEase -----------------------------------------------------------------------
    fun netease(title: String, artist: String, durationSec: Double): List<SiteLine>? {
        val res = LyricsSources.getJson("https://music.163.com/api/search/get?" + LyricsSources.query(
            "s" to "$title $artist", "type" to "1", "limit" to "10", "offset" to "0"), NETEASE) as? JSONObject ?: return null
        val songs = res.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val candidates = (0 until songs.length()).map { i ->
            val s = songs.getJSONObject(i)
            Candidate(s.get("id").toString(), s.optString("name"), names(s.optJSONArray("artists")),
                s.optDouble("duration", 0.0) / 1000)
        }
        for (c in LyricsSources.pick(candidates, title, artist, durationSec)) {
            val lyr = LyricsSources.getJson("https://music.163.com/api/song/lyric?" + LyricsSources.query(
                "id" to c.id, "lv" to "-1", "tv" to "-1"), NETEASE) as? JSONObject ?: continue
            pair(lyr.optJSONObject("lrc")?.optStringOrNull("lyric"),
                lyr.optJSONObject("tlyric")?.optStringOrNull("lyric"))?.let { return it }
        }
        return null
    }

    // ---- QQ Music ----------------------------------------------------------------------
    fun qqMusic(title: String, artist: String, durationSec: Double): List<SiteLine>? {
        val search = JSONObject().put("req", JSONObject()
            .put("module", "music.search.SearchCgiService").put("method", "DoSearchForQQMusicDesktop")
            .put("param", JSONObject().put("query", "$title $artist").put("num_per_page", 10)
                .put("page_num", 1).put("search_type", 0)))
        val res = LyricsSources.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", search, QQ) as? JSONObject ?: return null
        val songs = res.optJSONObject("req")?.optJSONObject("data")?.optJSONObject("body")
            ?.optJSONObject("song")?.optJSONArray("list") ?: return null
        val candidates = (0 until songs.length()).map { i ->
            val s = songs.getJSONObject(i)
            Candidate(s.optString("mid"), s.optString("title").ifEmpty { s.optString("name") },
                names(s.optJSONArray("singer")), s.optDouble("interval", 0.0))
        }
        for (c in LyricsSources.pick(candidates, title, artist, durationSec)) {
            val ask = JSONObject().put("comm", JSONObject().put("ct", 24).put("cv", 0)).put("req", JSONObject()
                .put("module", "music.musichallSong.PlayLyricInfo").put("method", "GetPlayLyricInfo")
                .put("param", JSONObject().put("songMID", c.id).put("trans", 1).put("roma", 0)
                    .put("crypt", 0).put("type", 0)))
            val d = (LyricsSources.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", ask, QQ) as? JSONObject)
                ?.optJSONObject("req")?.optJSONObject("data") ?: continue
            pair(qqText(d.optString("lyric")), qqText(d.optString("trans")))?.let { return it }
        }
        return null
    }

    /** QQ sends lyrics as base64 unless it honours the plain-text option; HTML entities either way. */
    private fun qqText(v: String): String {
        var t = v
        if (t.isNotEmpty() && '[' !in t) {
            t = try { String(LyricsSources.base64Decode(t), Charsets.UTF_8) } catch (_: Exception) { "" }
        }
        return t.replace("&apos;", "'").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }

    /** Lyrics LRC + translation LRC (lines on the same time stamps) -> lines; null if under 3 translated. */
    fun pair(lrc: String?, tlrc: String?): List<SiteLine>? {
        if (lrc.isNullOrBlank() || tlrc.isNullOrBlank()) return null
        val lines = timed(lrc).filter { it.second.isNotEmpty() }
        val tr = HashMap<Long, String>()
        for ((t, w) in timed(tlrc)) if (w.isNotEmpty() && w != "//" && w != "/") tr[t / 10] = w
        if (lines.size < 3 || tr.isEmpty()) return null
        val out = lines.map { (t, w) -> SiteLine(t, w, tr[t / 10]) }
        return if (out.count { it.translation != null } >= 3) out else null
    }

    private val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    /** Every (time ms, words) in an LRC text, nothing dropped (unlike [LrcParser]). */
    private fun timed(lrc: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val words = stamp.replace(raw, "").trim()
            for (m in stamp.findAll(raw)) {
                out.add(m.groupValues[1].toLong() * 60_000 + Math.round(m.groupValues[2].toDouble() * 1000) to words)
            }
        }
        return out.sortedBy { it.first }
    }

    // ---- Kugou (KRC format: compressed, and may carry a translation) -------------------
    private val KRC_KEY = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105)

    fun kugou(title: String, artist: String, durationSec: Double): List<SiteLine>? {
        for (c in LyricsSources.kugouCandidates(title, artist, durationSec)) {
            val (id, key) = c.id.split("\u0000")
            val d = LyricsSources.getJson("https://lyrics.kugou.com/download?" + LyricsSources.query("ver" to "1",
                "client" to "pc", "id" to id, "accesskey" to key, "fmt" to "krc", "charset" to "utf8")) as? JSONObject ?: continue
            val content = d.optStringOrNull("content") ?: continue
            val text = decodeKrc(LyricsSources.base64Decode(content)) ?: continue
            val lines = parseKrc(text)
            if (lines.count { it.translation != null } >= 3) return lines
        }
        return null
    }

    fun decodeKrc(raw: ByteArray): String? {
        if (raw.size < 5 || String(raw, 0, 4, Charsets.US_ASCII) != "krc1") return null
        val body = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor KRC_KEY[i % 16].toInt()).toByte() }
        return try {
            val inf = Inflater()
            inf.setInput(body)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inf.end()
            out.toString("UTF-8")
        } catch (_: Exception) { null }
    }

    private val krcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val krcWord = Regex("""<\d+,\d+,\d+>([^<]*)""")
    private val krcLanguage = Regex("""^\[language:([A-Za-z0-9+/=]+)]""", RegexOption.MULTILINE)
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun parseKrc(text: String): List<SiteLine> {
        val shift = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val rows = ArrayList<Pair<Long, String>>()
        for (raw in text.lines()) {
            val m = krcLine.find(raw.trim()) ?: continue
            val words = krcWord.findAll(m.groupValues[3]).joinToString("") { it.groupValues[1] }
            rows.add(m.groupValues[1].toLong() - shift to words)
        }
        val tr = arrayOfNulls<String>(rows.size)
        krcLanguage.find(text)?.let { lm ->
            try {
                val blocks = JSONObject(String(LyricsSources.base64Decode(lm.groupValues[1]), Charsets.UTF_8))
                    .getJSONArray("content")
                for (b in 0 until blocks.length()) {
                    val block = blocks.getJSONObject(b)
                    if (block.optInt("type") != 1) continue
                    val items = block.optJSONArray("lyricContent") ?: break
                    for (i in 0 until minOf(items.length(), rows.size)) {
                        val parts = items.optJSONArray(i) ?: continue
                        val t = (0 until parts.length()).joinToString("") { parts.optString(it) }.trim()
                        tr[i] = t.ifEmpty { null }
                    }
                    break
                }
            } catch (_: Exception) {}
        }
        return rows.mapIndexed { i, (t, w) -> SiteLine(t, w, tr[i]) }
    }

    private fun names(arr: JSONArray?): String =
        (0 until (arr?.length() ?: 0)).joinToString(" ") { arr!!.getJSONObject(it).optString("name") }
}

private typealias Candidate = LyricsSources.Candidate

/** Lines up a site's lines with ours by words and time, and copies their translations across. */
object TranslationMatcher {
    /** [ours] with translations from [theirs] added where a line has none yet. */
    fun attach(ours: List<Line>, theirsIn: List<SiteLine>): List<Line> {
        // Their copy may be timed differently (longer intro, another edit): find out by how much
        // from lines with the same words, so a few seconds' difference doesn't lose every match.
        val shift = timingOffset(ours, theirsIn)
        val theirs = if (shift != 0L) theirsIn.map { it.copy(timeMs = it.timeMs - shift) } else theirsIn
        val out = ours.toMutableList()
        val squashed = theirs.map { TextRules.squash(it.text) }

        fun score(js: List<Int>, key: String, t: Long): Double {
            val kk = js.joinToString("") { squashed[it] }
            val s = if (kk == key) 1.0 else ratio(kk, key)
            return s - abs(theirs[js[0]].timeMs - t) / 30_000.0     // the closer one wins a tie
        }

        for (i in ours.indices) {
            val (t, text) = ours[i].let { it.timeMs to it.text }
            if (out[i].translation != null) continue
            val key = TextRules.squash(text)
            if (key.isEmpty()) continue
            val tNext = ours.getOrNull(i + 1)?.timeMs ?: (t + 15_000)
            // One nearby line of theirs, or a run of them (sites often split a long line in two).
            val near = theirs.indices.filter { abs(theirs[it].timeMs - t) <= 3_000 }.map { listOf(it) }
            val options = near.toMutableList()
            val run = theirs.indices.filter { theirs[it].timeMs >= t - 1_000 && theirs[it].timeMs < tNext - 500 }
            if (run.size > 1 && run == (run.first()..run.last()).toList()) options.add(run)
            if (options.isEmpty()) continue
            val best = options.maxBy { score(it, key, t) }
            if (score(best, key, t) < 0.75) {
                // Maybe the other way round: one of their lines = this line + our next one.
                // Then its translation goes under this (first) line.
                val nextKey = ours.getOrNull(i + 1)?.let { TextRules.squash(it.text) } ?: continue
                val pairKey = key + nextKey
                val hit = near.maxByOrNull { score(it, pairKey, t) } ?: continue
                val tr = theirs[hit[0]].translation
                if (score(hit, pairKey, t) >= 0.75 && tr != null && TextRules.squash(tr) != pairKey) {
                    out[i] = out[i].copy(translation = tr.trimEnd('，', ',', '。', ' '))
                }
                continue
            }
            val parts = best.mapNotNull { theirs[it].translation }
            if (parts.isEmpty()) continue
            val tr = parts.joinToString("，") { it.trimEnd('，', ',', '。', ' ') }
            if (TextRules.squash(tr) != key) out[i] = out[i].copy(translation = tr)
        }
        return out
    }

    /** A line with no translation gets the one of another line with exactly the same words
     *  (a repeated chorus line), so it isn't sent to the AI translator. */
    fun share(lines: List<Line>): List<Line> {
        val byText = HashMap<String, Line>()
        for (l in lines) {
            val k = TextRules.squash(l.text)
            if (k.isNotEmpty() && l.translation != null && k !in byText) byText[k] = l
        }
        return lines.map { l ->
            if (l.translation != null) l
            else byText[TextRules.squash(l.text)]?.let { l.copy(translation = it.translation, aiTranslated = it.aiTranslated) } ?: l
        }
    }

    /** How much later their lines come than ours (ms), from lines whose words appear once on each
     *  side. 0 if under half a second or there's too little to go on. */
    fun timingOffset(ours: List<Line>, theirs: List<SiteLine>): Long {
        fun once(pairs: List<Pair<String, Long>>): Map<String, Long> {
            val seen = LinkedHashMap<String, MutableList<Long>>()
            for ((text, t) in pairs) {
                val k = TextRules.squash(text)
                if (k.length >= 6) seen.getOrPut(k) { ArrayList() }.add(t)
            }
            return seen.filterValues { it.size == 1 }.mapValues { it.value[0] }
        }
        val a = once(ours.map { it.text to it.timeMs })
        val b = once(theirs.map { it.text to it.timeMs })
        val diffs = a.keys.intersect(b.keys).map { b.getValue(it) - a.getValue(it) }.sorted()
        if (diffs.size < 3) return 0
        val mid = diffs[diffs.size / 2]
        val close = diffs.filter { abs(it - mid) <= 1_000 }
        if (close.size < max(3, diffs.size / 2)) return 0
        val off = close.sum() / close.size
        return if (abs(off) >= 500) off else 0
    }

    /** How alike two strings are, 0..1 (twice the longest common subsequence over both lengths). */
    fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val prev = IntArray(b.length + 1)
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else max(prev[j], cur[j - 1])
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return 2.0 * prev[b.length] / (a.length + b.length)
    }
}

/** Which lines need translating (ported from the PC app's ai_translate.py). */
object TextRules {
    private val letters = Regex("""[\p{L}\p{N}]""")
    private const val HAN = "\\u4e00-\\u9fff"
    private const val KANA = "\\u3040-\\u30ff"
    private const val HANGUL = "\\uac00-\\ud7af"

    /** Letters and digits only, lower case: for comparing lines. */
    fun squash(s: String): String = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "")

    /** Mostly Chinese characters and no kana / hangul: a Chinese song, nothing to translate. */
    fun isChineseSong(lines: List<Line>): Boolean {
        val text = lines.joinToString("") { it.text }
        if (Regex("[$KANA$HANGUL]").containsMatchIn(text)) return false
        val han = Regex("[$HAN]").findAll(text).count()
        val all = letters.findAll(text).count()
        return all > 0 && han.toDouble() / all > 0.3
    }

    fun hasKana(lines: List<Line>): Boolean = lines.any { Regex("[$KANA]").containsMatchIn(it.text) }

    // Pure vocal sounds: a line is skipped only if EVERY word in it is one of these.
    private val syllable = Regex("^(?:o+h*|a+h*|e+h*|u+h*|h?m+|hm+|ye+a*h*|ye+h+|ya+h*|yo+|la+|na+|da+|do+|du+m+|" +
        "ba+|be+|bo+p+|bu+m+|bi+ng|ra+|ta+|sha+|shoo+|wo+p+|wo+|woo+|wee+|hoo+|ho+|ha+|" +
        "he+y*|ey+|ay+e*|whoa+|woah+|ooh+|huh|aha+|uh|sk+r+t+|br+|sh+|tra+|" +
        "di+|dee+|di+n+g*|du+n+|do+n+g|ba+m+|da+m+|pa+m*|pu+m+|ti+|tu+|tss+|" +
        "ri+|ru+|li+|lu+|ni+|nu+|bi+|bu+|ki+|ku+|zi+|zu+)$")
    private val repeated = Regex("^((?:la|na|da|ha|he|ho|oh|ah|eh|uh|yeah|ya|yo|ra|ta|sha|doo|do|ba|wo|woo|hey|ay)h*)\\1+$")
    private val cjkSound = Regex("^[ラナアオウエイララーッらなあおうえいんンー라랄나날아오우에이야예啊哦喔呀啦嗯哈嘿呜噢\\s、，,!！~～…·・-]+$")

    /** Only vocal sounds ("ohh ohh", "la-la-la", "shoo-be-doo-wop", "ラララ"). */
    fun isSounds(text: String): Boolean {
        val t = text.trim()
        if (Regex("[^\\x00-\\x7f]").containsMatchIn(t)) return cjkSound.matches(t) && letters.containsMatchIn(t)
        val parts = t.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return parts.isNotEmpty() && parts.all { syllable.matches(it) || repeated.matches(it) }
    }

    /** One word 3+ times and nothing else ("dead, dead, dead"): an echo, clear as it is. */
    fun isEcho(text: String): Boolean {
        val words = Regex("""\p{L}+(?:'\p{L}+)?""").findAll(text.lowercase()).map { it.value }.toList()
        return words.size >= 3 && words.toSet().size == 1
    }

    private val credit = Regex("""(作词|作曲|编曲|lyrics by|composed by)\s*[:：]""", RegexOption.IGNORE_CASE)

    /** A real lyric line in any language but Chinese. [japaneseSong]: kanji-only lines are Japanese. */
    fun worthTranslating(text: String, japaneseSong: Boolean = false): Boolean {
        val t = text.trim()
        if (!letters.containsMatchIn(t) || credit.containsMatchIn(t)) return false
        if (isSounds(t) || isEcho(t)) return false
        if (japaneseSong) return true
        val han = Regex("[$HAN]").findAll(t).count()
        val kana = Regex("[$KANA]").containsMatchIn(t)
        return !(han > 0 && !kana && han >= letters.findAll(t).count() * 0.5)
    }

    // The same Chinese character 3+ times in a row (死死死死) is kept once; laughs and sung sounds stay.
    private val charRun = Regex("([$HAN])(?:[\\s,，、.。!！~～…-]*\\1){2,}")
    private const val SOUND_CHARS = "哈啦呀哦啊嘿嗯呜喔噢耶嘻呵吧哒嘟咚当叮滴"

    fun tidy(zh: String): String =
        charRun.replace(zh) { m -> if (m.groupValues[1][0] in SOUND_CHARS) m.value else m.groupValues[1] }
}
