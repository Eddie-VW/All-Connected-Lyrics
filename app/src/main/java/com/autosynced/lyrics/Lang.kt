package com.autosynced.lyrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's language: English or Simplified Chinese, never both at once. Every text in the
 * app is written as tr("English", "中文"). [current] is watched by the screens, so switching it
 * changes every text in place (you stay on the page you're on).
 */
object Lang {
    const val EN = "en"
    const val ZH = "zh"

    var current by mutableStateOf(EN)

    /** The phone's language decides until the user picks one. */
    fun systemDefault(): String = if (Locale.getDefault().language == "zh") ZH else EN

    val zh: Boolean get() = current == ZH
}

/** [en] or [zh], whichever language the app is in. */
fun tr(en: String, zh: String): String = if (Lang.zh) zh else en
