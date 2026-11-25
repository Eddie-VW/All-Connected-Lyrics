# All Connected Lyrics

An Android app that floats time-synced lyrics over your screen for whatever is playing in
Spotify, Apple Music or any other music app that shows media controls, with optional
Simplified Chinese translation.

## Features

- **Finds the song playing right now** through Android's media controls (needs *notification
  access*; the app only reads what's playing, never your notifications).
- **Synced lyrics** from [LRCLIB](https://lrclib.net), then NetEase Cloud Music, then Kugou.
  Versions much longer or shorter than the song are skipped.
- **Floating window** over other apps: drag to move, tap to open the app, long-press to hide.
- **译 Chinese translation** (off until you turn it on):
  1. Human translations from NetEase, QQ Music and Kugou, matched to the lyrics line by line,
     even when a site's copy is timed a little differently.
  2. Lines none of them translated can be filled in by Claude, using your own API key from
     [console.anthropic.com](https://console.anthropic.com). The translation's tone follows
     the song's genre (punchy for rap, plain-spoken for ballads, ...). The key is stored
     encrypted on the phone, and each line is only paid for once.

## Getting the app

Every push is built and tested by GitHub Actions: open the **Actions** tab → **Android app** →
the latest run → **Artifacts** → `AutosyncedLyrics-android`. Unzip it and open `app-debug.apk`
on the phone (allow installing from your browser or files app when Android asks). All builds
share one signing key (`app/debug.keystore`), so a newer build installs over the old one.

## Code

| File | Purpose |
| --- | --- |
| `NowPlaying.kt` / `MediaListenerService.kt` | What's playing and where, from Android's media sessions |
| `Lyrics.kt` | LRC parsing and the lyric sources |
| `LyricsRepo.kt` | Loads lyrics, then translations, when the song changes |
| `Translations.kt` | Site translations and matching them to our lines; which lines need one |
| `AiTranslator.kt` | Claude translation of the lines left over, its cache and cost log |
| `ApiKeyStore.kt` | The Claude API key, encrypted with Android's Keystore |
| `FloatingLyricsService.kt` | The floating lyrics window |
| `ui/` | The main screen and the 译 settings panel |

Tests (`app/src/test`) run on a PC: `./gradlew testDebugUnitTest`.

Lyrics are copyrighted; this app shows them for personal use and stores none permanently.
