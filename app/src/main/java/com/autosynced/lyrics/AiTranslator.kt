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
 * Chinese translations for lyric lines no music site has translated (like the PC app's
 * ai_translate.py), from the service chosen in the 译 page: Claude, another AI, or a classic
 * machine translator (see [Provider]). What follows describes Claude; the other AIs get the same
 * instructions, and every service's results are remembered the same way.
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

        /** English names of the languages lyrics can be translated into (Lang codes). */
        val LANGUAGE_NAMES = mapOf("zh" to "Simplified Chinese", "zh-Hant" to "Traditional Chinese", "en" to "English",
            "ja" to "Japanese", "ko" to "Korean", "es" to "Spanish", "fr" to "French", "de" to "German",
            "pt" to "Portuguese", "ru" to "Russian", "id" to "Indonesian", "th" to "Thai", "vi" to "Vietnamese",
            "ar" to "Arabic")

        /** The instructions for translating into [target]: the Chinese ones above, or the same ideas for any language. */
        fun systemPrompt(target: String): String {
            if (target == "zh") return SYSTEM
            val lang = LANGUAGE_NAMES[target] ?: "English"
            return """
                You translate song lyrics into $lang for a karaoke-style lyrics display.

                First decide what kind of song this is - its genre, era and mood (for example: hip-hop, pop, rock, punk, R&B, folk, ballad, musical, anime song, EDM) - and who is singing to whom. Then write the $lang the way a gifted $lang-speaking lyricist working in that same genre would, so a listener feels the song rather than reads a gloss of it:
                - Rap and hip-hop: punchy, rhythmic, street-level language; slang becomes natural $lang slang; keep the swagger and the flow. Rhyme where it comes naturally.
                - Pop: natural, catchy, everyday spoken $lang.
                - Rock and punk: raw, blunt, direct.
                - Ballads, folk and love songs: tender and lyrical, but in plain words.
                - Musicals, art songs and genuinely poetic lyrics: more literary phrasing is welcome.
                Match the original's register: casual stays casual, poetic stays poetic.

                Avoid both failure modes:
                - Too bland: stiff word-for-word translationese, flat literal phrasing, explaining instead of singing.
                - Too forced: piling on idioms or flowery, poetic phrasing that the original does not have; making a plain line sound grand.

                Rules:
                - One line of $lang per lyric line, about as long as the original; it is read in a second or two while the song plays.
                - Carry the mood, imagery and wordplay; idioms become natural $lang equivalents, not literal ones.
                - Keep names and brands as they are commonly written in $lang. Pure vocal sounds (oh, yeah, la la) stay as they are.
                - A word repeated as an echo or backing vocal ("dead, dead, dead") is translated naturally, not as one word repeated mechanically.
                - A line that is already in $lang is returned unchanged.
                - Where human translations of nearby lines are given, match their wording and tone.
                - Do not censor or soften explicit language; translate it faithfully.
            """.trimIndent()
        }

        private val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                // Naming the genre and tone first keeps every line in the same voice.
                "style" to mapOf("type" to "string",
                    "description" to "The song's genre and mood, and the tone chosen for the translation, in a few words"),
                "lines" to mapOf("type" to "array", "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("n" to mapOf("type" to "integer"), "tr" to mapOf("type" to "string")),
                    "required" to listOf("n", "tr"), "additionalProperties" to false)),
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

    /** Where a song's translations are kept: by song, and by service (Claude's had no tag at first). */
    private fun songKey(title: String, artist: String, tag: String) =
        "${title.trim().lowercase()}\n${artist.trim().lowercase()}" + (if (tag == Provider.CLAUDE.id) "" else "\n$tag")

    /** {original line: translation} made earlier for this song by the service tagged [tag]. */
    fun known(title: String, artist: String, tag: String = Provider.CLAUDE.id): Map<String, String> = synchronized(lock) {
        val song = readJson(storeFile).optJSONObject(songKey(title, artist, tag)) ?: return emptyMap()
        song.keys().asSequence().associateWith { TextRules.tidy(song.getString(it)) }
    }

    private fun remember(title: String, artist: String, tag: String, pairs: Map<String, String>) = synchronized(lock) {
        val all = readJson(storeFile)
        val k = songKey(title, artist, tag)
        val song = all.optJSONObject(k) ?: JSONObject().also { all.put(k, it) }
        pairs.forEach { (line, zh) -> song.put(line, zh) }
        writeAtomically(storeFile, all.toString())
    }

    /** Total spent on Claude so far (US$, other services' prices aren't known) and the number of requests. */
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
     * [lines] with translations from [cfg]'s service added to the lines worth translating that have none.
     * Throws on network / key / API errors, for the caller to report.
     */
    fun fill(title: String, artist: String, lines: List<Line>, cfg: ProviderConfig): List<Line> {
        val japanese = TextRules.hasKana(lines)
        val lyric = lines.indices.filter { TextRules.worthTranslatingInto(lines[it].text, cfg.target, japanese) }
        val want = lyric.filter { lines[it].translation == null }
        if (want.isEmpty()) return lines

        val done = known(title, artist, cfg.storeTag)
        val out = lines.toMutableList()
        for (i in want) done[lines[i].text]?.let { out[i] = out[i].copy(translation = it, aiTranslated = true) }
        val todo = want.filter { out[it].translation == null }
        if (todo.isEmpty()) return out

        val got: Map<Int, String> = when (cfg.provider) {
            Provider.CLAUDE -> askClaude(title, artist, lines, lyric, todo, cfg.key.orEmpty(), cfg.target)
            Provider.OPENAI -> askOtherAi(title, artist, lines, lyric, todo, cfg)
            else -> askTranslator(title, artist, lines, todo, cfg)
        }
        for ((n, zh) in got) out[n] = out[n].copy(translation = zh, aiTranslated = true)
        if (got.isNotEmpty()) remember(title, artist, cfg.storeTag, got.mapKeys { lines[it.key].text })
        return out
    }

    /** The whole song, numbered, with the human translations there are, and which lines to do. */
    private fun ask(title: String, artist: String, lines: List<Line>, lyric: List<Int>, todo: List<Int>): String {
        val song = lyric.joinToString("\n") { i ->
            "$i. ${lines[i].text}" + (lines[i].translation?.let { "    [human translation: $it]" } ?: "")
        }
        return "Song: $title - $artist\n\nLyrics (numbered):\n$song\n\n" +
            "Translate these line numbers, which have no translation yet: ${todo.joinToString(", ")}.\n" +
            "Return one entry per line number."
    }

    private fun askOtherAi(title: String, artist: String, lines: List<Line>, lyric: List<Int>, todo: List<Int>,
                           cfg: ProviderConfig): Map<Int, String> {
        val system = systemPrompt(cfg.target) + "\n\nAnswer with only a JSON object, no other text: " +
            "{\"style\": \"<the song's genre and mood, and the Chinese tone chosen>\", " +
            "\"lines\": [{\"n\": <line number>, \"tr\": \"<translation>\"}, ...]}"
        val (text, tokens) = Translators.openAiChat(cfg, system, ask(title, artist, lines, lyric, todo))
        logUsage(JSONObject()
            .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("title", title).put("artist", artist).put("model", cfg.model).put("lines", todo.size)
            .put("tokens", tokens).put("cost", 0.0))
        return parse(Translators.jsonIn(text), todo.toSet(), lines, cfg.target)
    }

    /** Classic translators: each line on its own (in batches), plain style. */
    private fun askTranslator(title: String, artist: String, lines: List<Line>, todo: List<Int>,
                              cfg: ProviderConfig): Map<Int, String> {
        val got = LinkedHashMap<Int, String>()
        for (batch in todo.chunked(40)) {
            val result = Translators.translate(cfg, batch.map { lines[it].text })
            batch.forEachIndexed { k, n ->
                result.getOrNull(k)?.let { zh -> if (TextRules.squash(zh) != TextRules.squash(lines[n].text)) got[n] = tidyFor(zh, cfg.target) }
            }
        }
        logUsage(JSONObject()
            .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("title", title).put("artist", artist).put("model", cfg.provider.id).put("lines", todo.size)
            .put("chars", todo.sumOf { lines[it].text.length }).put("cost", 0.0))
        return got
    }

    private fun askClaude(title: String, artist: String, lines: List<Line>, lyric: List<Int>, todo: List<Int>,
                          apiKey: String, target: String): Map<Int, String> {
        val model = chooseModel(todo.size, lyric.size)
        val ask = ask(title, artist, lines, lyric, todo)
        val format = JsonOutputFormat.builder().schema(
            JsonOutputFormat.Schema.builder().apply { SCHEMA.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build()
        ).build()
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(systemPrompt(target))
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
        return parse(text, todo.toSet(), lines, target)
    }

    /** {line number: translation} from Claude's JSON answer, only for the numbers asked for. */
    /** Chinese gets its repeated-character clean-up; other languages are kept as written. */
    private fun tidyFor(text: String, target: String) = if (target == "zh" || target == "zh-Hant") TextRules.tidy(text) else text

    internal fun parse(text: String, asked: Set<Int>, lines: List<Line>? = null, target: String = "zh"): Map<Int, String> {
        val arr = try { JSONObject(text).optJSONArray("lines") } catch (_: Exception) { null } ?: return emptyMap()
        val got = LinkedHashMap<Int, String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val n = item.optInt("n", -1)
            val zh = item.optString("tr").ifEmpty { item.optString("zh") }.trim()
            // a line given back unchanged was already in the target language: no translation needed
            val same = lines != null && n in lines.indices && TextRules.squash(zh) == TextRules.squash(lines[n].text)
            if (n in asked && zh.isNotEmpty() && !same) got[n] = tidyFor(zh, target)
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
