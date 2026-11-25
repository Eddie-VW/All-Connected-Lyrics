# All Connected Lyrics

An Android app that floats time-synced lyrics over your screen for whatever is playing in
Spotify, Apple Music or any other music app that shows media controls, with optional
Simplified Chinese translation.

## Features

- **Finds the song playing right now** through Android's media controls (needs *notification
  access*; the app only reads what's playing, never your notifications).
- **Synced lyrics** from [LRCLIB](https://lrclib.net), then NetEase Cloud Music, then Kugou.
  Versions much longer or shorter than the song are skipped.
- **Floating window** over other apps: drag to move, long-press to hide, and **tap for buttons**:
  one compact row with previous / play-pause / next, 译 on/off, text size A− / A+, quick text
  colours, 🔒 lock (touches pass through; unlock from the notification), ⚙ settings and close.
  The sliders button next to *Floating* adjusts it, with a live preview: width, opacity,
  background, corners, how many lines before/after the current one, song title on top, font
  (built-in or your own .ttf/.otf), text sizes, bold, alignment, outline, your own colours,
  line-change effect, lock (touches pass through) and timing.
- **Lock-screen lyrics 歌词锁屏** (Settings → Lock screen lyrics): when the screen turns off
  while music plays, the lyrics wait over the lock screen; swipe up to unlock. Adjustable with a
  live preview: background (blurred cover, cover colours, your colour, wallpaper), blur, darkening,
  transparency, clock and date, cover shape (incl. spinning vinyl) and size, lines before/after,
  font, sizes, spacing, bold, glow, alignment, line-change effect, colours, buttons and progress.
  Needs "Display over other apps"; some phones also need 锁屏显示 / 后台弹出界面 allowed.
- **Main screen lyrics colours** (Settings → Main screen lyrics): current, sung, coming and
  translation colours, their see-through, and the panel behind them.
- **译 Chinese translation** (off until you turn it on):
  1. Human translations from NetEase, QQ Music and Kugou, matched to the lyrics line by line,
     even when a site's copy is timed a little differently.
  2. Lines none of them translated are filled in by the service you choose on the 译 page:
     - **AI, matching the song's genre** (punchy for rap, plain-spoken for ballads, ...):
       Claude, or another AI with an OpenAI-style API (presets for DeepSeek, SiliconFlow
       硅基流动, Gemini, Groq, OpenRouter, or any custom one).
     - **Classic translators, plain style:** DeepL, Microsoft Translator, Baidu 百度翻译, or
       Google Translate with no key at all.

     Keys are your own, stored encrypted on the phone (one per service), and each line is only
     translated once per service.

## Getting the app

Every push to `main` is built, tested and published on the **Releases** page. The newest build
is always at
[releases/latest/download/AllConnectedLyrics.apk](https://github.com/Eddie-VW/All-Connected-Lyrics/releases/latest/download/AllConnectedLyrics.apk):
open that on the phone and install it (allow installing from your browser when Android asks).
All builds share one signing key (`app/debug.keystore`), so a newer build installs over the old
one as an update.

## Code

| File | Purpose |
| --- | --- |
| `NowPlaying.kt` / `MediaListenerService.kt` | What's playing and where, from Android's media sessions |
| `Lyrics.kt` | LRC parsing and the lyric sources |
| `LyricsRepo.kt` | Loads lyrics, then translations, when the song changes |
| `Translations.kt` | Site translations and matching them to our lines; which lines need one |
| `AiTranslator.kt` | Translating the lines left over (Claude, or via `Translators.kt`), its cache and log |
| `Translators.kt` | The other services: OpenAI-style AIs, DeepL, Microsoft, Baidu, Google |
| `ApiKeyStore.kt` | The services' API keys, encrypted with Android's Keystore |
| `FloatingLyricsService.kt` | The floating lyrics window |
| `FloatStyle.kt` / `FloatingLyricsView.kt` | Its settings, which lines it shows, and how it draws them |
| `LockStyle.kt` / `LockScreenActivity.kt` | Lock-screen lyrics: their settings, and the screen over the lock screen |
| `AppLyricsStyle.kt` | Colours of the main screen's lyrics |
| `ui/` | The main screen, the lock screen design, and the settings pages |

Tests (`app/src/test`) run on a PC: `./gradlew testDebugUnitTest`.

Lyrics are copyrighted; this app shows them for personal use and stores none permanently.
