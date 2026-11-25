package com.autosynced.lyrics

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Chinese translations, written by Claude, for lyric lines no music site has translated
 * (like the PC app's ai_translate.py).
 *
 * - Only lines without a human translation are sent; the whole song goes along as context,
 *   with the human translations around them so the wording matches.
 * - A song missing just a few lines -> Claude Haiku 5.5 (a fraction of a cent).
 *   A song with no translation (or mostly none) -> Claude Sonnet 5.5, for quality.
 * - Every result is kept in [dir]/ai_translations.json, so a line is only ever paid for once.
 * - Every request's tokens and cost go into [dir]/ai_usage.json (shown in the app's settings).
 */
class AiTranslator(private val dir: File) {
    companion object {
        const val FEW_MODEL = "claude-haiku-5-5"      // a few missing lines
        const val FULL_MODEL = "claude-sonnet-5-5"    // a song with (almost) no translation
        const val FULL_SHARE = 0.5                    // missing more than this share -> FULL_MODEL
        // US$ per million tokens (input, output): Anthropic's list prices.
        val PRICES = mapOf(FEW_MODEL to (0.10 to 0.50), FULL_MODEL to (2.00 to 10.00))

        val SYSTEM = """
            You translate song lyrics into Simplified Chinese for a karaoke-style lyrics display.

            First decide what kind of song this is - its genre, era and mood (for example: hip-hop, pop, rock, punk, R&B, folk, ballad, musical, anime song, EDM) - and who is singing to whom. Then write the Chinese the way a gifted Chinese lyricist working in that same genre would, so a listener feels the song rather than reads a gloss of it:
            - Rap and hip-hop: punchy, rhythmic, street-level Chinese; slang becomes natural Chinese slang; keep the swagger and the flow. Rhyme where it comes naturally.
            - Pop: natural, catchy, everyday spoken Chinese.
            - Rock and punk: raw, blunt, direct.
            - Ballads, folk and love songs: tender and lyrical, but in plain words.
            - Musicals, art songs and genuinely poetic lyrics: more literary phrasing is welcome.
            Match the original's register: casual stays casual, poetic stays poetic.

            Avoid both failure modes:
            - Too bland: stiff word-for-word translationese (翻译腔), flat literal phrasing, explaining instead of singing.
            - Too forced: piling on four-character idioms, classical-poetry flourishes or flowery 文艺腔 that the original does not have; making a plain line sound grand.

            Rules:
            - One line of Chinese per lyric line, about as long as the original; it is read in a second or two while the song plays.
            - Carry the mood, imagery and wordplay; idioms become natural Chinese equivalents, not literal ones.
            - Keep names and brands as they are commonly written in Chinese. Pure vocal sounds (oh, yeah, la la) stay short sounds or stay as they are.
            - A word repeated as an echo or backing vocal ("dead, dead, dead") is translated once or as a natural phrase, never as one Chinese character over and over (not 死死死死).
            - Where human translations of nearby lines are given, match their wording and tone.
            - Do not censor or soften explicit language; translate it faithfully.
        """.trimIndent()

        private val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                // Naming the genre and tone first keeps every line in the same voice.
                "style" to mapOf("type" to "string",
                    "description" to "The song's genre and mood, and the Chinese tone chosen for it, in a few words"),
                "lines" to mapOf("type" to "array", "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("n" to mapOf("type" to "integer"), "zh" to mapOf("type" to "string")),
                    "required" to listOf("n", "zh"), "additionalProperties" to false)),
            ),
            "required" to listOf("style", "lines"), "additionalProperties" to false,
        )

        fun chooseModel(missing: Int, lyricLines: Int): String =
            if (lyricLines > 0 && missing.toDouble() / lyricLines > FULL_SHARE) FULL_MODEL else FEW_MODEL

        fun costOf(model: String, tokensIn: Long, tokensOut: Long): Double {
            val (pIn, pOut) = PRICES[model] ?: (0.0 to 0.0)
            return tokensIn * pIn / 1e6 + tokensOut * pOut / 1e6
        }

        private var client: Pair<String, AnthropicClient>? = null

        @Synchronized
        private fun clientFor(key: String): AnthropicClient {
            client?.let { (k, c) -> if (k == key) return c }
            val c = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofMinutes(3)).build()
            client = key to c
            return c
        }
    }

    private val storeFile = File(dir, "ai_translations.json")
    private val usageFile = File(dir, "ai_usage.json")
    private val lock = Any()

    private fun songKey(title: String, artist: String) = "${title.trim().lowercase()}\n${artist.trim().lowercase()}"

    /** {original line: translation} made earlier for this song. */
    fun known(title: String, artist: String): Map<String, String> = synchronized(lock) {
        val song = readJson(storeFile).optJSONObject(songKey(title, artist)) ?: return emptyMap()
        song.keys().asSequence().associateWith { TextRules.tidy(song.getString(it)) }
    }

    private fun remember(title: String, artist: String, pairs: Map<String, String>) = synchronized(lock) {
        val all = readJson(storeFile)
        val song = all.optJSONObject(songKey(title, artist)) ?: JSONObject().also { all.put(songKey(title, artist), it) }
        pairs.forEach { (k, v) -> song.put(k, v) }
        writeAtomically(storeFile, all.toString())
    }

    /** Total spent on AI translation so far (US$) and the number of requests. */
    fun spent(): Pair<Double, Int> = synchronized(lock) {
        val log = readArray(usageFile)
        (0 until log.length()).sumOf { log.getJSONObject(it).optDouble("cost", 0.0) } to log.length()
    }

    private fun logUsage(entry: JSONObject) = synchronized(lock) {
        val log = readArray(usageFile)
        log.put(entry)
        writeAtomically(usageFile, log.toString())
    }

    /**
     * [lines] with AI translations added to the lines worth translating that have none.
     * Throws on network / key / API errors, for the caller to report.
     */
    fun fill(title: String, artist: String, lines: List<Line>, apiKey: String): List<Line> {
        val japanese = TextRules.hasKana(lines)
        val lyric = lines.indices.filter { TextRules.worthTranslating(lines[it].text, japanese) }
        val want = lyric.filter { lines[it].translation == null }
        if (want.isEmpty()) return lines

        val done = known(title, artist)
        val out = lines.toMutableList()
        for (i in want) done[lines[i].text]?.let { out[i] = out[i].copy(translation = it, aiTranslated = true) }
        val todo = want.filter { out[it].translation == null }
        if (todo.isEmpty()) return out

        val model = chooseModel(todo.size, lyric.size)
        val song = lyric.joinToString("\n") { i ->
            "$i. ${lines[i].text}" + (lines[i].translation?.let { "    [human translation: $it]" } ?: "")
        }
        val ask = "Song: $title - $artist\n\nLyrics (numbered):\n$song\n\n" +
            "Translate these line numbers, which have no translation yet: ${todo.joinToString(", ")}.\n" +
            "Return one entry per line number."

        val format = JsonOutputFormat.builder().schema(
            JsonOutputFormat.Schema.builder().apply { SCHEMA.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build()
        ).build()
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(SYSTEM)
            .addUserMessage(ask)
            .outputConfig(OutputConfig.builder()
                .effort(if (model == FEW_MODEL) OutputConfig.Effort.LOW else OutputConfig.Effort.MEDIUM)
                .format(format)
                .build())
            .apply {
                if (model == FULL_MODEL) {
                    // If Sonnet declines a song (e.g. very explicit lyrics), let the API retry it on another model.
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()
        val response = clientFor(apiKey).messages().create(params)

        val usage = response.usage()
        val tokensIn = usage.inputTokens() + usage.cacheReadInputTokens().orElse(0L) + usage.cacheCreationInputTokens().orElse(0L)
        logUsage(JSONObject()
            .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("title", title).put("artist", artist).put("model", model).put("lines", todo.size)
            .put("in", tokensIn).put("out", usage.outputTokens())
            .put("cost", costOf(model, tokensIn, usage.outputTokens())))
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw IllegalStateException("Claude declined to translate this song")
        }
        val text = response.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("")
        val got = parse(text, todo.toSet())
        for ((n, zh) in got) out[n] = out[n].copy(translation = zh, aiTranslated = true)
        if (got.isNotEmpty()) remember(title, artist, got.mapKeys { lines[it.key].text })
        return out
    }

    /** {line number: translation} from Claude's JSON answer, only for the numbers asked for. */
    internal fun parse(text: String, asked: Set<Int>): Map<Int, String> {
        val arr = try { JSONObject(text).optJSONArray("lines") } catch (_: Exception) { null } ?: return emptyMap()
        val got = LinkedHashMap<Int, String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val n = item.optInt("n", -1)
            val zh = item.optString("zh").trim()
            if (n in asked && zh.isNotEmpty()) got[n] = TextRules.tidy(zh)
        }
        return got
    }

    private fun readJson(f: File): JSONObject = try { JSONObject(f.readText()) } catch (_: Exception) { JSONObject() }
    private fun readArray(f: File): JSONArray = try { JSONArray(f.readText()) } catch (_: Exception) { JSONArray() }

    private fun writeAtomically(f: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
