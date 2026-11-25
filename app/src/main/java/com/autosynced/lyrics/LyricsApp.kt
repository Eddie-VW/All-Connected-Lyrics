package com.autosynced.lyrics

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.load(this)
        NowPlaying.start(this)
        LyricsRepo.start(this)
    }
}

/** Small saved settings (style, floating lyrics on/off, where the floating bar sits, translation). */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Chinese translation under each line (off until the user turns it on), and whether
    // Claude may fill in lines no music site has translated (only does anything with an API key).
    private val _translate = MutableStateFlow(false)
    val translate: StateFlow<Boolean> = _translate
    private val _aiTranslate = MutableStateFlow(true)
    val aiTranslate: StateFlow<Boolean> = _aiTranslate

    fun load(c: Context) {
        _translate.value = p(c).getBoolean("translate", false)
        _aiTranslate.value = p(c).getBoolean("ai_translate", true)
    }
    fun setTranslate(c: Context, v: Boolean) { _translate.value = v; p(c).edit().putBoolean("translate", v).apply() }
    fun setAiTranslate(c: Context, v: Boolean) { _aiTranslate.value = v; p(c).edit().putBoolean("ai_translate", v).apply() }

    fun style(c: Context): String = p(c).getString("style", "night") ?: "night"
    fun setStyle(c: Context, v: String) = p(c).edit().putString("style", v).apply()
    fun floating(c: Context): Boolean = p(c).getBoolean("floating", false)
    fun setFloating(c: Context, v: Boolean) = p(c).edit().putBoolean("floating", v).apply()
    fun floatX(c: Context): Int = p(c).getInt("float_x", 40)
    fun floatY(c: Context): Int = p(c).getInt("float_y", 300)
    fun setFloatPos(c: Context, x: Int, y: Int) = p(c).edit().putInt("float_x", x).putInt("float_y", y).apply()
}
