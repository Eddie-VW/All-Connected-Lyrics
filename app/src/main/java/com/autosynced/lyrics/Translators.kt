package com.autosynced.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * The services that can translate lyric lines no music site has translated.
 * [styleAware]: an AI model that follows the "match the song's genre" instructions;
 * the others are classic machine translators (plain, literal style).
 */
enum class Provider(
    val id: String,
    val labelEn: String,
    val labelZh: String,
    val styleAware: Boolean,
    val needsKey: Boolean,
    /** The second field this service needs (Microsoft's region, Baidu's App ID), if any: (English, Chinese). */
    val extraEn: String? = null,
    val extraZh: String? = null,
) {
    CLAUDE("claude", "Claude", "Claude", true, true),
    OPENAI("openai", "Other AI", "其他 AI", true, true),
    DEEPL("deepl", "DeepL", "DeepL", false, true),
    MICROSOFT("microsoft", "Microsoft", "微软翻译", false, true, "Region (e.g. eastasia, global)", "区域（如 eastasia、global）"),
    BAIDU("baidu", "Baidu Translate", "百度翻译", false, true, "App ID", "APP ID"),
    GOOGLE("google", "Google (no key)", "谷歌（免密钥）", false, false);

    companion object {
        fun of(id: String?): Provider = entries.firstOrNull { it.id == id } ?: CLAUDE
    }
}

/** Ready-made settings for AI services that speak the common "OpenAI-style" chat format. */
data class AiPreset(val name: String, val nameZh: String, val baseUrl: String, val model: String, val keyFrom: String,
                    val noteEn: String, val noteZh: String)

val AI_PRESETS = listOf(
    AiPreset("DeepSeek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "platform.deepseek.com",
        "Works in mainland China. Very cheap, often with free credit for new accounts.",
        "中国大陆可用。价格很低，新账户常有免费额度。"),
    AiPreset("SiliconFlow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen3-8B", "cloud.siliconflow.cn",
        "Works in mainland China. Several Qwen models are free to use.",
        "中国大陆可用。多个通义千问（Qwen）模型可免费使用。"),
    AiPreset("Gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest", "aistudio.google.com",
        "Free tier with daily limits. Not reachable from mainland China without a VPN.",
        "有每日限额的免费额度。中国大陆需要 VPN 才能使用。"),
    AiPreset("Groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "console.groq.com",
        "Free tier, very fast. Not reachable from mainland China without a VPN.",
        "有免费额度，速度很快。中国大陆需要 VPN 才能使用。"),
    AiPreset("OpenRouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324:free", "openrouter.ai",
        "Many models; the ones ending in “:free” cost nothing (with daily limits).",
        "模型很多；名称以“:free”结尾的免费（有每日限额）。"),
    AiPreset("Custom", "自定义", "", "", "", "Any service with an OpenAI-compatible chat API: enter its address and model.",
        "任何兼容 OpenAI 聊天接口的服务：填写它的地址和模型名。"),
)

/** Everything needed to call one service: the chosen [provider], its key and extra settings. */
data class ProviderConfig(
    val provider: Provider,
    val key: String? = null,
    val extra: String = "",        // Microsoft region / Baidu App ID
    val baseUrl: String = "",      // Other AI
    val model: String = "",        // Other AI
) {
    /** Separates remembered translations by service (a different AI may word things differently). */
    val storeTag: String get() = if (provider == Provider.OPENAI) "openai:$model" else provider.id
    val ready: Boolean get() = !provider.needsKey || !key.isNullOrBlank()
}

/** An error answer from a translation service (the HTTP status and what it said). */
class TranslatorException(val status: Int, message: String) : Exception(message)

/** Calls to the non-Claude services (plain HTTP; all answers parsed here so they're tested on a PC). */
object Translators {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AllConnectedLyrics/0.4"

    // ---- AI services with an OpenAI-style chat API ---------------------------------------
    /** Claude-style answer text ({"style":…,"lines":[{"n":…,"zh":…}]}) and tokens used. */
    fun openAiChat(cfg: ProviderConfig, system: String, user: String): Pair<String, Int> {
        val body = JSONObject()
            .put("model", cfg.model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.7)
        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val (code, text) = http(url, body.toString(), mapOf("Authorization" to "Bearer ${cfg.key}",
            "Content-Type" to "application/json"), timeoutMs = 120_000)
        if (code !in 200..299) throw TranslatorException(code, errorMessage(text))
        return parseOpenAiChat(text)
    }

    fun parseOpenAiChat(text: String): Pair<String, Int> {
        val j = JSONObject(text)
        val content = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        val tokens = j.optJSONObject("usage")?.optInt("total_tokens", 0) ?: 0
        return content to tokens
    }

    /** The JSON object inside an AI's answer (some wrap it in ```json fences or add a sentence). */
    fun jsonIn(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    // ---- classic machine translators: one Chinese line per input line -------------------
    fun translate(cfg: ProviderConfig, texts: List<String>): List<String?> = when (cfg.provider) {
        Provider.DEEPL -> deepl(cfg, texts)
        Provider.MICROSOFT -> microsoft(cfg, texts)
        Provider.BAIDU -> baidu(cfg, texts)
        Provider.GOOGLE -> google(texts)
        else -> error("${cfg.provider} isn't a classic translator")
    }

    /** DeepL Free keys end in ":fx" and use their own address. */
    fun deeplHost(key: String) = if (key.trim().endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"

    private fun deepl(cfg: ProviderConfig, texts: List<String>): List<String?> {
        val key = cfg.key.orEmpty().trim()
        val body = JSONObject().put("text", JSONArray(texts)).put("target_lang", "ZH-HANS")
        val (code, text) = http(deeplHost(key) + "/v2/translate", body.toString(),
            mapOf("Authorization" to "DeepL-Auth-Key $key", "Content-Type" to "application/json"))
        if (code !in 200..299) throw TranslatorException(code, errorMessage(text))
        val arr = JSONObject(text).optJSONArray("translations") ?: return texts.map { null }
        return texts.indices.map { arr.optJSONObject(it)?.optString("text")?.takeIf { s -> s.isNotBlank() } }
    }

    private fun microsoft(cfg: ProviderConfig, texts: List<String>): List<String?> {
        val body = JSONArray(texts.map { JSONObject().put("Text", it) })
        val headers = mutableMapOf("Ocp-Apim-Subscription-Key" to cfg.key.orEmpty().trim(), "Content-Type" to "application/json")
        if (cfg.extra.isNotBlank() && cfg.extra.trim().lowercase() != "global") headers["Ocp-Apim-Subscription-Region"] = cfg.extra.trim()
        val (code, text) = http("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-Hans",
            body.toString(), headers)
        if (code !in 200..299) throw TranslatorException(code, errorMessage(text))
        return parseMicrosoft(text, texts.size)
    }

    fun parseMicrosoft(text: String, n: Int): List<String?> {
        val arr = JSONArray(text)
        return (0 until n).map { i ->
            arr.optJSONObject(i)?.optJSONArray("translations")?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
        }
    }

    fun baiduSign(appId: String, q: String, salt: String, secret: String): String =
        MessageDigest.getInstance("MD5").digest((appId + q + salt + secret).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun baidu(cfg: ProviderConfig, texts: List<String>): List<String?> {
        val appId = cfg.extra.trim()
        val secret = cfg.key.orEmpty().trim()
        val q = texts.joinToString("\n") { it.replace('\n', ' ') }
        val salt = System.currentTimeMillis().toString()
        val form = listOf("q" to q, "from" to "auto", "to" to "zh", "appid" to appId, "salt" to salt,
            "sign" to baiduSign(appId, q, salt, secret))
            .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val (code, text) = http("https://fanyi-api.baidu.com/api/trans/vip/translate", form,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"))
        if (code !in 200..299) throw TranslatorException(code, errorMessage(text))
        return parseBaidu(text, texts.size)
    }

    fun parseBaidu(text: String, n: Int): List<String?> {
        val j = JSONObject(text)
        j.optString("error_code").takeIf { it.isNotEmpty() && it != "52000" }?.let {
            throw TranslatorException(it.toIntOrNull() ?: 0, "Baidu error $it: ${j.optString("error_msg")}")
        }
        val arr = j.optJSONArray("trans_result") ?: return List(n) { null }
        return (0 until n).map { arr.optJSONObject(it)?.optString("dst")?.takeIf { s -> s.isNotBlank() } }
    }

    private fun google(texts: List<String>): List<String?> {
        // One request for the whole batch, a line per lyric line.
        val q = texts.joinToString("\n") { it.replace('\n', ' ') }
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=" +
            URLEncoder.encode(q, "UTF-8")
        val (code, text) = http(url, null, emptyMap())
        if (code !in 200..299) throw TranslatorException(code, "Google Translate answered $code")
        return parseGoogle(text, texts.size)
    }

    /** Google's answer is a list of [translated, original, …] pieces; joined, they're our lines. */
    fun parseGoogle(text: String, n: Int): List<String?> {
        val pieces = JSONArray(text).optJSONArray(0) ?: return List(n) { null }
        val joined = (0 until pieces.length()).joinToString("") { pieces.optJSONArray(it)?.optString(0).orEmpty() }
        val out = joined.split("\n").map { it.trim().ifEmpty { null } }
        return if (out.size == n) out else List(n) { null }     // lines didn't line up: trust none
    }

    // ---- helpers ------------------------------------------------------------------------
    private fun errorMessage(body: String): String = try {
        val j = JSONObject(body)
        j.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: j.optString("message").takeIf { it.isNotBlank() } ?: body.take(200)
    } catch (_: Exception) { body.take(200) }

    /** (status, body) of a GET (no [body]) or POST. */
    private fun http(url: String, body: String?, headers: Map<String, String>, timeoutMs: Int = 20_000): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", UA)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
