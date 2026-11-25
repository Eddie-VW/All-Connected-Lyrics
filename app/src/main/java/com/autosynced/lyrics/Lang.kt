package com.autosynced.lyrics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * The app's language: one of [LANGUAGES], never mixed. Every text is written in the code as
 * tr("English", "简体中文"); the other languages' texts come from assets/i18n/<code>.json, which
 * map the English text to its translation (English is shown for anything missing).
 * [current] is watched by the screens, so switching it changes every text in place.
 */
object Lang {
    const val EN = "en"
    const val ZH = "zh"                // Simplified Chinese (the code predates the other languages)
    const val ZH_HANT = "zh-Hant"

    /** (code, the language's name in itself). */
    val LANGUAGES = listOf(
        EN to "English", ZH to "简体中文", ZH_HANT to "繁體中文", "ja" to "日本語", "ko" to "한국어",
        "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt" to "Português", "ru" to "Русский",
        "id" to "Bahasa Indonesia", "th" to "ไทย", "vi" to "Tiếng Việt", "ar" to "العربية",
    )

    var current by mutableStateOf(EN)
    private var table: Map<String, String> = emptyMap()
    private var loadedFor = ""
    private var appContext: Context? = null

    val zh: Boolean get() = current == ZH
    /** Chinese of either script: the translation button reads 译 / 譯 instead of A/文. */
    val chinese: Boolean get() = current == ZH || current == ZH_HANT
    val rtl: Boolean get() = current == "ar"

    fun init(c: Context) { appContext = c.applicationContext }

    /** The phone's language, if the app has it; otherwise English. */
    fun systemDefault(): String {
        val l = Locale.getDefault()
        return when {
            l.language == "zh" && (l.script == "Hant" || l.country in setOf("TW", "HK", "MO")) -> ZH_HANT
            l.language == "zh" -> ZH
            l.language == "in" -> "id"            // Android's old code for Indonesian
            LANGUAGES.any { it.first == l.language } -> l.language
            else -> EN
        }
    }

    fun nameOf(code: String) = LANGUAGES.firstOrNull { it.first == code }?.second ?: code

    /** The translations for the current language (read once per language). */
    internal fun lookup(en: String): String? {
        if (loadedFor != current) {
            loadedFor = current
            table = try {
                val text = appContext?.assets?.open("i18n/$current.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                val j = JSONObject(text ?: "{}")
                j.keys().asSequence().associateWith { j.getString(it) }
            } catch (_: Exception) { emptyMap() }
        }
        return table[en]
    }
}

/** The text in the app's language: [en] and [zh] are written here; the others come from the language files. */
fun tr(en: String, zh: String): String = when (Lang.current) {
    Lang.EN -> en
    Lang.ZH -> zh
    else -> Lang.lookup(en) ?: en
}

/** Like [tr], for texts with values in them, written {0}, {1}… in both languages. */
fun trf(en: String, zh: String, vararg args: Any?): String {
    var s = tr(en, zh)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

/** The translation button's label: 译 / 譯 in Chinese, A/文 in every other language. */
fun translateIcon(): String = when (Lang.current) {
    Lang.ZH -> "译"
    Lang.ZH_HANT -> "譯"
    else -> "A/文"
}
