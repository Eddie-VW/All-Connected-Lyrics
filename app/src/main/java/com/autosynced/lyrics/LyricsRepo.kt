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
    private val mine by lazy { MyLyrics(File(appContext.filesDir, "my_lyrics")) }

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
                            // your own edited lyrics always win over the online ones
                            // then the PC's (through the cloud), then online
                            if (CloudSync.sync(appContext)) forgetNotFound()
                            Result.success(mine.load(track.title, track.artist)
                                ?: CloudSync.find(appContext, track.title, track.artist, track.durationMs / 1000.0)
                                ?: LyricsSources.fetch(track.title, track.artist, track.album, track.durationMs / 1000.0))
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
        scope.launch { Prefs.targetLang.drop(1).collect { retranslate() } }
    }

    private fun show(track: Track, lyrics: Lyrics) {
        _state.value = LyricsState.Found(translated[tkey(track)] ?: lyrics)
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
        val target = Prefs.targetLang.value
        return lines.any { it.translation == null && TextRules.worthTranslatingInto(it.text, target, japanese) }
    }

    /** Translations are remembered per song and per target language. */
    private fun tkey(track: Track) = track.key + "\u0000" + Prefs.targetLang.value

    private fun translate(track: Track, raw: Lyrics) {
        translateJob?.cancel()
        if (!Prefs.translate.value || !raw.synced) return
        if (TextRules.isSongIn(raw.lines, Prefs.targetLang.value)) {
            // already in that language: show the lyrics without another language's translations
            if (NowPlaying.state.value.track?.key == track.key) _state.value = LyricsState.Found(raw)
            return
        }
        // show this language's translations so far (not another language's) while looking for more
        if (NowPlaying.state.value.track?.key == track.key) _state.value = LyricsState.Found(translated[tkey(track)] ?: raw)
        translateJob = scope.launch {
            var lines = translated[tkey(track)]?.lines ?: raw.lines
            fun emit(busy: Boolean, note: String? = null) {
                if (NowPlaying.state.value.track?.key == track.key) {
                    _state.value = LyricsState.Found(raw.copy(lines = lines), busy, note)
                }
            }
            if (!needsTranslation(lines)) { emit(false); return@launch }
            emit(true)
            // 1. Human translations from NetEase / QQ Music / Kugou (asked once per song; they're all Simplified Chinese).
            if (!translated.containsKey(tkey(track))) {
                if (Prefs.targetLang.value == "zh") lines = try {
                    TranslationSources.attach(lines, track.title, track.artist, track.durationMs / 1000.0)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { lines }
                translated[tkey(track)] = raw.copy(lines = lines)
                emit(true)
            }
            // 2. Claude for the lines still missing, if the user allows it and has a key.
            var note: String? = null
            if (needsTranslation(lines) && Prefs.aiTranslate.value) {
                val cfg = withContext(Dispatchers.IO) { Prefs.providerConfig(appContext) }
                if (!cfg.ready) {
                    note = trf("Some lines have no translation yet. Add a {0} key in Translation settings to fill them in.",
                        "部分歌词还没有翻译。在翻译设置中添加 {0} 的密钥即可补全。",
                        if (Lang.chinese) cfg.provider.labelZh else cfg.provider.labelEn)
                } else {
                    try {
                        val before = lines
                        lines = TranslationMatcher.share(withContext(Dispatchers.IO) {
                            ai.fill(track.title, track.artist, before, cfg)
                        })
                        translated[tkey(track)] = raw.copy(lines = lines)
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        note = tr("AI translation didn't work: ", "AI 翻译失败：") + reason(e)
                    }
                }
            }
            emit(false, note)
        }
    }

    private fun reason(e: Exception): String = when (e) {
        is UnauthorizedException -> tr("the API key wasn't accepted. Check it in Translation settings.", "API 密钥无效，请在翻译设置中检查。")
        is PermissionDeniedException -> tr("this API key isn't allowed to use Claude.", "此 API 密钥无权使用 Claude。")
        is PaymentRequiredException -> tr("the Claude account is out of credit.", "Claude 账户余额不足。")
        is RateLimitException -> tr("too many requests right now. Try again in a minute.", "请求太频繁，请一分钟后再试。")
        is OverloadedException -> tr("Claude is busy right now. Try again in a minute.", "Claude 当前繁忙，请一分钟后再试。")
        is AnthropicServiceException -> trf("Claude's server answered with error {0}.", "Claude 服务器返回错误 {0}。", e.statusCode())
        is TranslatorException -> when (e.status) {
            401, 403, 52003 -> tr("the key wasn't accepted. Check it in Translation settings.", "密钥无效，请在翻译设置中检查。")
            429, 54003 -> tr("too many requests right now. Try again in a minute.", "请求太频繁，请一分钟后再试。")
            456, 402, 54004 -> tr("the service's free allowance or credit is used up.", "该服务的免费额度或余额已用完。")
            else -> e.message ?: trf("the service answered with error {0}.", "服务返回错误 {0}。", e.status)
        }
        is AnthropicIoException, is IOException ->
            tr("couldn't reach the service (no internet, or it's blocked where you are).", "无法连接该服务（没有网络，或在你所在地区被屏蔽）。")
        else -> e.message ?: tr("unknown error.", "未知错误。")
    }

    /** Your edited lyrics for the song playing: kept for good, and shown right away. */
    fun saveMine(lines: List<Line>) {
        val track = NowPlaying.state.value.track ?: return
        val clean = LyricEdits.sorted(lines.map { it.copy(text = it.text.trim(), translation = it.translation?.trim()?.ifEmpty { null }) })
        mine.save(track.title, track.artist, clean)
        val lyrics = Lyrics(clean, LyricEdits.isSynced(clean), MyLyrics.SOURCE)
        cache[track.key] = lyrics
        translated.remove(tkey(track))
        show(track, lyrics)
    }

    /** Whether the song playing uses your own lyrics. */
    fun hasMine(): Boolean = NowPlaying.state.value.track?.let { mine.has(it.title, it.artist) } ?: false

    /** Forget your edits for the song playing and look the lyrics up online again. */
    fun useOnline() {
        val track = NowPlaying.state.value.track ?: return
        mine.delete(track.title, track.artist)
        cache.remove(track.key)
        translated.remove(tkey(track))
        job?.cancel(); translateJob?.cancel()
        _state.value = LyricsState.Loading
        job = scope.launch {
            val found = withContext(Dispatchers.IO) {
                try { LyricsSources.fetch(track.title, track.artist, track.album, track.durationMs / 1000.0) } catch (_: Exception) { null }
            }
            if (NowPlaying.state.value.track?.key != track.key) return@launch
            cache[track.key] = found
            if (found != null) show(track, found) else _state.value = LyricsState.NotFound
        }
    }

    /** New lyrics from the PC: songs that had none get another look. */
    private fun forgetNotFound() {
        scope.launch { cache.entries.removeAll { it.value == null } }
    }

    /** Checks the cloud now (Settings → Sync with the PC); shows the PC's lyrics for the song playing if it has them. */
    suspend fun syncNow(): String {
        val got = withContext(Dispatchers.IO) { CloudSync.sync(appContext, now = true) }
        if (got) {
            cache.entries.removeAll { it.value == null || it.value?.source == CloudSync.SOURCE_FOUND || it.value?.source == CloudSync.SOURCE_MINE }
            val track = NowPlaying.state.value.track
            if (track != null && !mine.has(track.title, track.artist)) {
                withContext(Dispatchers.IO) { CloudSync.find(appContext, track.title, track.artist, track.durationMs / 1000.0) }?.let {
                    job?.cancel(); translateJob?.cancel()
                    cache[track.key] = it
                    translated.remove(track.key)
                    show(track, it)
                }
            }
        }
        return CloudSync.status
    }

    /** How many songs have your own lyrics (edited here or brought from the PC). */
    fun mineCount(): Int = mine.count()

    /**
     * Saves the lyrics in the picked files (the PC app's "Send to phone" zip, or .lrc files) as
     * your own, then shows them if one is for the song playing. Returns (songs added, files skipped).
     * Call off the main thread.
     */
    fun importFromPc(context: Context, uris: List<android.net.Uri>): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        for (uri in uris) {
            try {
                val name = displayName(context, uri) ?: uri.lastPathSegment ?: "lyrics.lrc"
                val r = context.contentResolver.openInputStream(uri)?.use { LrcImport.read(name, it) }
                if (r == null) { skipped++; continue }
                for (s in r.songs) mine.save(s.title, s.artist, s.lines)
                added += r.songs.size
                skipped += r.skipped
            } catch (_: Exception) { skipped++ }
        }
        if (added > 0) scope.launch { reloadPlaying() }
        return added to skipped
    }

    private fun displayName(context: Context, uri: android.net.Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    /** The song playing now has your own lyrics (just imported): show them instead of the online ones. */
    private fun reloadPlaying() {
        val track = NowPlaying.state.value.track ?: return
        val own = mine.load(track.title, track.artist) ?: return
        job?.cancel(); translateJob?.cancel()
        cache[track.key] = own
        translated.remove(track.key)
        show(track, own)
    }

    /** Total spent on AI translation (US$) and number of requests, for the settings screen. */
    fun aiSpent(): Pair<Double, Int> = ai.spent()
}
