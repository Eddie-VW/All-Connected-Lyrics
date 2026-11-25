package com.autosynced.lyrics

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.autosynced.lyrics.ui.DayPalette
import com.autosynced.lyrics.ui.LocalPalette
import com.autosynced.lyrics.ui.NightPalette
import com.autosynced.lyrics.ui.AppScreen

class MainActivity : ComponentActivity() {
    companion object {
        /** Which page to open (sent by the floating window's ⚙ button). */
        const val EXTRA_PAGE = "page"
        const val PAGE_FLOAT_SETTINGS = "float_settings"
    }

    private var requestedPage by mutableStateOf<String?>(null)

    // Refreshed every time you come back to the app (e.g. after granting a permission).
    private var hasAccess by mutableStateOf(false)
    private var canDraw by mutableStateOf(false)
    private var floating by mutableStateOf(false)
    private var style by mutableStateOf("night")

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        style = Prefs.style(this)
        requestedPage = intent?.getStringExtra(EXTRA_PAGE)
        setContent {
            // Arabic reads right to left: the whole layout is mirrored
            CompositionLocalProvider(LocalPalette provides if (style == "day") DayPalette else NightPalette,
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (Lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                AppScreen(
                    hasAccess = hasAccess,
                    floatingOn = floating && canDraw,
                    style = style,
                    onGrantAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onToggleStyle = {
                        style = if (style == "day") "night" else "day"
                        Prefs.setStyle(this, style)
                    },
                    onToggleFloating = { toggleFloating() },
                    requestedPage = requestedPage,
                    onPageOpened = { requestedPage = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PAGE)?.let { requestedPage = it }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = NowPlaying.hasAccess(this)
        canDraw = FloatingLyricsService.canDraw(this)
        floating = Prefs.floating(this)
        if (hasAccess) {
            NowPlaying.start(this)
            LyricsRepo.start(this)
        }
        FloatingLyricsService.refresh(this)     // floating window and/or lock-screen lyrics
    }

    private fun toggleFloating() {
        if (floating && canDraw) {
            floating = false
            Prefs.setFloating(this, false)
            FloatingLyricsService.refresh(this)
            return
        }
        floating = true
        Prefs.setFloating(this, true)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!FloatingLyricsService.canDraw(this)) {
            // Android asks you to allow "display over other apps" on its own settings screen.
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            FloatingLyricsService.start(this)
        }
    }
}
