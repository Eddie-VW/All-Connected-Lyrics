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

    // How the floating window looks (see FloatStyle); changes show up in it right away.
    private val _floatStyle = MutableStateFlow(FloatStyle())
    val floatStyle: StateFlow<FloatStyle> = _floatStyle
    fun setFloatStyle(c: Context, v: FloatStyle) { _floatStyle.value = v; p(c).edit().putString("float_style", v.toJson()).apply() }

    // Which service translates the lines no music site has (see Provider), and its non-secret
    // settings; keys live encrypted in ApiKeyStore.
    private val _provider = MutableStateFlow(Provider.CLAUDE)
    val provider: StateFlow<Provider> = _provider
    fun setProvider(c: Context, v: Provider) { _provider.value = v; p(c).edit().putString("provider", v.id).apply() }
    fun providerExtra(c: Context, v: Provider): String = p(c).getString("extra_${v.id}", "") ?: ""
    fun setProviderExtra(c: Context, v: Provider, extra: String) = p(c).edit().putString("extra_${v.id}", extra.trim()).apply()
    fun aiPreset(c: Context): String = p(c).getString("ai_preset", AI_PRESETS[0].name) ?: AI_PRESETS[0].name
    fun aiBaseUrl(c: Context): String = p(c).getString("ai_base_url", AI_PRESETS[0].baseUrl) ?: ""
    fun aiModel(c: Context): String = p(c).getString("ai_model", AI_PRESETS[0].model) ?: ""
    fun setAi(c: Context, preset: String, baseUrl: String, model: String) = p(c).edit()
        .putString("ai_preset", preset).putString("ai_base_url", baseUrl.trim()).putString("ai_model", model.trim()).apply()

    /** Everything needed to call the chosen service (reads the encrypted key: not on the main thread). */
    fun providerConfig(c: Context): ProviderConfig {
        val v = provider.value
        return ProviderConfig(v, ApiKeyStore.get(c, ApiKeyStore.slot(v)), providerExtra(c, v), aiBaseUrl(c), aiModel(c))
    }

    fun load(c: Context) {
        _provider.value = Provider.of(p(c).getString("provider", null))
        _floatStyle.value = FloatStyle.fromJson(p(c).getString("float_style", null))
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
