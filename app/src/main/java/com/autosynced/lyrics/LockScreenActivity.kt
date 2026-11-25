package com.autosynced.lyrics

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.autosynced.lyrics.ui.LockContent
import com.autosynced.lyrics.ui.LockScreen
import kotlinx.coroutines.delay

/**
 * Lock-screen lyrics (歌词锁屏). Opened by [FloatingLyricsService] when the screen turns off while
 * music plays, so it's there, over the lock screen, when the phone wakes. Swipe up to unlock;
 * it also closes itself when the phone is unlocked another way or the music app goes away.
 */
class LockScreenActivity : ComponentActivity() {
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()
        registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT))

        setContent {
            val style by Prefs.lockStyle.collectAsState()
            val playback by NowPlaying.state.collectAsState()
            val lyricsState by LyricsRepo.state.collectAsState()
            val translate by Prefs.translate.collectAsState()
            var now by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) { now = NowPlaying.state.value.position() + Prefs.floatStyle.value.offsetMs; delay(80) }
            }
            LaunchedEffect(style.keepScreenOn) {
                if (style.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val track = playback.track
            LaunchedEffect(style.enabled, track == null) { if (!style.enabled || track == null) finish() }
            if (track != null) {
            val found = lyricsState as? LyricsState.Found
            val synced = found?.lyrics?.takeIf { it.synced }
            val message = when (lyricsState) {
                is LyricsState.Loading -> "Loading lyrics…"
                is LyricsState.NotFound -> "No synced lyrics for this song"
                is LyricsState.NetworkError -> "Couldn't fetch the lyrics"
                is LyricsState.Found -> if (synced == null) "These lyrics have no timing" else null
                else -> "♪"
            }
            LockScreen(
                style = style,
                content = LockContent(
                    title = track.title, artist = track.artist, art = playback.art, playing = playback.playing,
                    lines = synced?.lines ?: emptyList(), index = synced?.indexAt(now) ?: -1, message = message,
                    positionMs = now, durationMs = track.durationMs, translate = translate,
                ),
                onUnlock = { unlock() },
                onPrevious = { NowPlaying.previous() },
                onPlayPause = { NowPlaying.playPause() },
                onNext = { NowPlaying.next() },
            )
            }
        }
    }

    /** Asks Android to unlock (PIN / fingerprint if there is one), then gets out of the way. */
    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { finish() }
            })
        } else finish()
    }

    override fun onDestroy() {
        try { unregisterReceiver(unlocked) } catch (_: Exception) {}
        super.onDestroy()
    }
}
