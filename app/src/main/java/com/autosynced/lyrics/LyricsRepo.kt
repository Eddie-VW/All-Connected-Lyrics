package com.autosynced.lyrics

import android.content.Context
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PaymentRequiredException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed class LyricsState {
    data object Idle : LyricsState()
    data object Loading : LyricsState()
    /** [translating]: translations are still being looked up · [note]: why some lines have none. */
    data class Found(val lyrics: Lyrics, val translating: Boolean = false, val note: String? = null) : LyricsState()
    data object NotFound : LyricsState()
    data object NetworkError : LyricsState()
}

/**
 * Fetches lyrics whenever the song changes (kept in memory for songs already played), then,
 * if Chinese translation is on, their translations: music sites first, Claude for the rest.
 */
object LyricsRepo {
    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cache = HashMap<String, Lyrics?>()
    private val translated = HashMap<String, Lyrics>()   // songs whose sites were already asked
    private var job: Job? = null
    private var translateJob: Job? = null
    private var started = false
    private lateinit var appContext: Context
    private val ai by lazy { AiTranslator(File(appContext.filesDir, "ai")) }

    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        scope.launch {
            NowPlaying.state.distinctUntilChangedBy { it.track?.key }.collect { p ->
                val track = p.track
                job?.cancel()
                translateJob?.cancel()
                if (track == null) { _state.value = LyricsState.Idle; return@collect }
                if (cache.containsKey(track.key)) {
                    cache[track.key]?.let { show(track, it) } ?: run { _state.value = LyricsState.NotFound }
                    return@collect
                }
                _state.value = LyricsState.Loading
                job = launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            Result.success(LyricsSources.fetch(track.title, track.artist, track.album, track.durationMs / 1000.0))
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    if (NowPlaying.state.value.track?.key != track.key) return@launch
                    result.onSuccess {
                        cache[track.key] = it
                        if (it != null) show(track, it) else _state.value = LyricsState.NotFound
                    }.onFailure { _state.value = LyricsState.NetworkError }
                }
            }
        }
        // Switching translation on fetches it for the song that's playing.
        scope.launch { Prefs.translate.drop(1).collect { if (it) retranslate() } }
        scope.launch { Prefs.aiTranslate.drop(1).collect { if (it) retranslate() } }
        // A different service: its own go at the lines the previous one translated.
        scope.launch { Prefs.provider.drop(1).collect { retranslate(dropAi = true) } }
    }

    private fun show(track: Track, lyrics: Lyrics) {
        _state.value = LyricsState.Found(translated[track.key] ?: lyrics)
        translate(track, lyrics)
    }

    /** Look for translations again for the current song (e.g. after an API key was added). */
    fun retranslate(dropAi: Boolean = false) {
        if (dropAi) for ((k, l) in translated.entries.toList()) {
            translated[k] = l.copy(lines = l.lines.map { if (it.aiTranslated) it.copy(translation = null, aiTranslated = false) else it })
        }
        val track = NowPlaying.state.value.track ?: return
        val raw = cache[track.key] ?: return
        translate(track, raw)
    }

    private fun needsTranslation(lines: List<Line>): Boolean {
        val japanese = TextRules.hasKana(lines)
        return lines.any { it.translation == null && TextRules.worthTranslating(it.text, japanese) }
    }

    private fun translate(track: Track, raw: Lyrics) {
        translateJob?.cancel()
        if (!Prefs.translate.value || !raw.synced || TextRules.isChineseSong(raw.lines)) return
        translateJob = scope.launch {
            var lines = translated[track.key]?.lines ?: raw.lines
            fun emit(busy: Boolean, note: String? = null) {
                if (NowPlaying.state.value.track?.key == track.key) {
                    _state.value = LyricsState.Found(raw.copy(lines = lines), busy, note)
                }
            }
            if (!needsTranslation(lines)) { emit(false); return@launch }
            emit(true)
            // 1. Human translations from NetEase / QQ Music / Kugou (asked once per song).
            if (!translated.containsKey(track.key)) {
                lines = try {
                    TranslationSources.attach(lines, track.title, track.artist, track.durationMs / 1000.0)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { lines }
                translated[track.key] = raw.copy(lines = lines)
                emit(true)
            }
            // 2. Claude for the lines still missing, if the user allows it and has a key.
            var note: String? = null
            if (needsTranslation(lines) && Prefs.aiTranslate.value) {
                val cfg = withContext(Dispatchers.IO) { Prefs.providerConfig(appContext) }
                if (!cfg.ready) {
                    note = "Some lines have no translation yet. Add a ${cfg.provider.label} key in 译 settings to fill them in."
                } else {
                    try {
                        val before = lines
                        lines = TranslationMatcher.share(withContext(Dispatchers.IO) {
                            ai.fill(track.title, track.artist, before, cfg)
                        })
                        translated[track.key] = raw.copy(lines = lines)
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        note = "AI translation didn't work: ${reason(e)}"
                    }
                }
            }
            emit(false, note)
        }
    }

    private fun reason(e: Exception): String = when (e) {
        is UnauthorizedException -> "the API key wasn't accepted. Check it in 译 settings."
        is PermissionDeniedException -> "this API key isn't allowed to use Claude."
        is PaymentRequiredException -> "the Claude account is out of credit."
        is RateLimitException -> "too many requests right now. Try again in a minute."
        is OverloadedException -> "Claude is busy right now. Try again in a minute."
        is AnthropicServiceException -> "Claude's server answered with error ${e.statusCode()}."
        is TranslatorException -> when (e.status) {
            401, 403, 52003 -> "the key wasn't accepted. Check it in 译 settings."
            429, 54003 -> "too many requests right now. Try again in a minute."
            456, 402, 54004 -> "the service's free allowance or credit is used up."
            else -> e.message ?: "the service answered with error ${e.status}."
        }
        is AnthropicIoException, is IOException ->
            "couldn't reach the service (no internet, or it's blocked where you are)."
        else -> e.message ?: "unknown error."
    }

    /** Total spent on AI translation (US$) and number of requests, for the settings screen. */
    fun aiSpent(): Pair<Double, Int> = ai.spent()
}
