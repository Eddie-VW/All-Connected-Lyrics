package com.autosynced.lyrics

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

/**
 * The floating lyrics window shown over other apps, styled by [FloatStyle] (settings → Floating lyrics).
 * Drag to move · tap for buttons (play/pause, skip, 译, size, colour, settings, close) ·
 * long-press to turn it off. When it's locked, touches go
 * through to the app underneath; unlock it from the notification or the settings.
 * Runs as a foreground service (with a small notification) so Android keeps it on screen.
 *
 * It also runs for lock-screen lyrics: when the screen turns off while music plays, it opens
 * [LockScreenActivity], which shows over the lock screen the next time the phone wakes up.
 * The service stays on while either is switched on ([refresh] re-checks).
 */
class FloatingLyricsService : Service() {
    companion object {
        private const val CHANNEL = "floating"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_TOGGLE_LOCK = "toggle_lock"
        // Android only lets touches pass through a window that's at most this opaque.
        private const val MAX_PASS_THROUGH_ALPHA = 0.8f

        fun canDraw(c: Context) = Settings.canDrawOverlays(c)

        /** Floating window wanted (and allowed), or lock-screen lyrics on? */
        private fun wanted(c: Context) = (Prefs.floating(c) && canDraw(c)) || Prefs.lockStyle.value.enabled

        /** Starts the service, or tells it to re-check its settings (it stops itself when nothing's on). */
        fun refresh(c: Context) {
            val i = Intent(c, FloatingLyricsService::class.java)
            if (!wanted(c)) { c.stopService(i); return }
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun start(c: Context) = refresh(c)
    }

    private lateinit var wm: WindowManager
    private var card: FloatingLyricsView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var appliedStyle: FloatStyle? = null
    private var appliedNight = true
    private var screenOffWatch: BroadcastReceiver? = null
    // While the window is locked, all its touches go through it, so it can't notice a tap; this
    // small badge at its corner is the one part that can be tapped, to unlock it.
    private var lockBadge: android.widget.ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        NowPlaying.start(this)
        LyricsRepo.start(this)
        handler.post(tick)
    }

    /** Adds or removes the floating window and the screen-off watch to match the settings. */
    private fun refreshParts() {
        val floating = Prefs.floating(this) && canDraw(this)
        if (floating && card == null) addOverlay()
        if (!floating) {
            card?.let { try { wm.removeView(it) } catch (_: Exception) {}; card = null; appliedStyle = null }
            removeLockBadge()
        }
        val lock = Prefs.lockStyle.value.enabled
        if (lock && screenOffWatch == null) {
            screenOffWatch = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { screenTurnedOff() }
            }.also { registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        }
        if (!lock) screenOffWatch?.let { try { unregisterReceiver(it) } catch (_: Exception) {}; screenOffWatch = null }
        if (!floating && !lock) { stopSelf(); return }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(Prefs.floatStyle.value.locked))
    }

    /** Screen off: put the lyrics up, so they're there when the phone wakes (over the lock screen). */
    private fun screenTurnedOff() {
        val lock = Prefs.lockStyle.value
        val p = NowPlaying.state.value
        if (!lock.enabled || p.track == null || (lock.onlyWhenPlaying && !p.playing)) return
        try {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        } catch (_: Exception) {}   // e.g. Android didn't allow it: nothing to show this time
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_LOCK) {
            val s = Prefs.floatStyle.value
            Prefs.setFloatStyle(this, s.copy(locked = !s.locked))
        }
        refreshParts()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        card?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        card = null
        removeLockBadge()
        screenOffWatch?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenOffWatch = null
        super.onDestroy()
    }

    private fun notification(lockedIn: Boolean): Notification {
        val floating = Prefs.floating(this) && canDraw(this)
        val lockScreen = Prefs.lockStyle.value.enabled
        val locked = floating && lockedIn
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val lock = PendingIntent.getService(this, 1,
            Intent(this, FloatingLyricsService::class.java).setAction(ACTION_TOGGLE_LOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = when {
            floating && lockScreen -> if (locked) tr("Floating (locked) and lock-screen lyrics are on", "悬浮歌词（已锁定）和锁屏歌词已开启")
                else tr("Floating and lock-screen lyrics are on", "悬浮歌词和锁屏歌词已开启")
            floating -> if (locked) tr("Floating lyrics is on (locked)", "悬浮歌词已开启（已锁定）") else tr("Floating lyrics is on", "悬浮歌词已开启")
            else -> tr("Lock-screen lyrics are on", "锁屏歌词已开启")
        }
        val text = when {
            locked -> tr("Touches go through the lyrics. Tap Unlock to move them again.", "触摸会穿透歌词。点“解锁”即可再次移动。")
            floating -> tr("Tap the lyrics for buttons. Tap here to open the app.", "点歌词显示按钮。点这里打开应用。")
            else -> tr("Lyrics show on the lock screen while music plays. Tap here to open the app.", "播放音乐时在锁屏上显示歌词。点这里打开应用。")
        }
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        if (floating) b.addAction(Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_lock_lock),
            if (locked) tr("Unlock", "解锁") else tr("Lock", "锁定"), lock).build())
        return b.build()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Floating & lock-screen lyrics", "悬浮歌词与锁屏歌词"), NotificationManager.IMPORTANCE_MIN))
        }
        val n = notification(Prefs.floatStyle.value.locked)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val box = FloatingLyricsView(this)
        card = box
        box.onAction = { act(it) }
        params = WindowManager.LayoutParams(
            box.widthPx(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.floatX(this@FloatingLyricsService)
            y = Prefs.floatY(this@FloatingLyricsService)
        }

        // drag to move, tap to open the app, long-press to turn off (not while locked: touches pass through)
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val longPress = Runnable {
            Prefs.setFloating(this, false)
            Toast.makeText(this, tr("Floating lyrics turned off", "已关闭悬浮歌词"), Toast.LENGTH_SHORT).show()
            refreshParts()
        }
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() + 150)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true; handler.removeCallbacks(longPress)
                    }
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(box, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (moved) Prefs.setFloatPos(this, params.x, params.y)
                    else if (e.actionMasked == MotionEvent.ACTION_UP) box.showControls(!box.controlsShown)
                }
            }
            true
        }
        applyStyle()
        wm.addView(box, params)
    }

    private fun showLockBadge() {
        val size = (resources.displayMetrics.density * 30).toInt()
        val badgeParams = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            // the window's top-right corner, slightly outside it
            x = (params.x + params.width - size * 0.7f).toInt()
            y = (params.y - size * 0.35f).toInt().coerceAtLeast(0)
        }
        val badge = lockBadge
        if (badge != null) { try { wm.updateViewLayout(badge, badgeParams) } catch (_: Exception) {}; return }
        lockBadge = android.widget.ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setColorFilter(android.graphics.Color.WHITE)
            val p = size / 5
            setPadding(p, p, p, p)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x99000000.toInt())
            }
            alpha = 0.6f
            contentDescription = tr("Unlock the floating lyrics", "解锁悬浮歌词")
            setOnClickListener {
                Prefs.setFloatStyle(this@FloatingLyricsService, Prefs.floatStyle.value.copy(locked = false))
                Toast.makeText(this@FloatingLyricsService, tr("Floating lyrics unlocked", "悬浮歌词已解锁"), Toast.LENGTH_SHORT).show()
            }
        }.also { try { wm.addView(it, badgeParams) } catch (_: Exception) {} }
    }

    private fun removeLockBadge() {
        lockBadge?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        lockBadge = null
    }

    /** What the floating window's buttons do. */
    private fun act(action: FloatingLyricsView.Action) {
        val style = Prefs.floatStyle.value
        val night = Prefs.style(this) != "day"
        when (action) {
            FloatingLyricsView.Action.PlayPause -> NowPlaying.playPause()
            FloatingLyricsView.Action.Previous -> NowPlaying.previous()
            FloatingLyricsView.Action.Next -> NowPlaying.next()
            FloatingLyricsView.Action.Translate -> Prefs.setTranslate(this, !Prefs.translate.value)
            FloatingLyricsView.Action.Bigger -> Prefs.setFloatStyle(this, style.resized(+1))
            FloatingLyricsView.Action.Smaller -> Prefs.setFloatStyle(this, style.resized(-1))
            FloatingLyricsView.Action.Lock -> {
                card?.showControls(false)
                Prefs.setFloatStyle(this, style.copy(locked = true))
                Toast.makeText(this, tr("Lyrics locked: touches now go through them. Tap the small lock at the corner " +
                    "to unlock.", "歌词已锁定：触摸会穿透歌词。点角落的小锁即可解锁。"), Toast.LENGTH_LONG).show()
            }
            is FloatingLyricsView.Action.Colour ->
                Prefs.setFloatStyle(this, style.ownColors(night).copy(currentColor = action.color))
            FloatingLyricsView.Action.Settings -> {
                card?.showControls(false)
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_FLOAT_SETTINGS))
            }
            FloatingLyricsView.Action.Close -> {
                Prefs.setFloating(this, false)
                Toast.makeText(this, tr("Floating lyrics turned off. Turn them on again in the app.", "已关闭悬浮歌词，可在应用中重新开启。"), Toast.LENGTH_SHORT).show()
                refreshParts()
            }
        }
    }

    /** Re-applies the settings when they (or the app's Night / Day style) change. */
    private fun applyStyle() {
        val box = card ?: return
        val style = Prefs.floatStyle.value
        val night = Prefs.style(this) != "day"
        if (style == appliedStyle && night == appliedNight) return
        val lockChanged = appliedStyle?.locked != style.locked
        appliedStyle = style
        appliedNight = night
        box.apply(style, night)
        params.width = box.widthPx()
        params.alpha = if (style.locked) minOf(style.opacity, MAX_PASS_THROUGH_ALPHA) else style.opacity
        params.flags = if (style.locked) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (box.isAttachedToWindow) try { wm.updateViewLayout(box, params) } catch (_: Exception) {}
        if (style.locked) showLockBadge() else removeLockBadge()
        if (lockChanged) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(style.locked))
    }

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 100)
        }
    }

    private fun update() {
        val box = card ?: return
        applyStyle()
        val p = NowPlaying.state.value
        val track = p.track
        if (track == null) { box.visibility = View.GONE; lockBadge?.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        lockBadge?.visibility = View.VISIBLE
        val style = Prefs.floatStyle.value
        val rows = when (val s = LyricsRepo.state.value) {
            is LyricsState.Loading -> LyricRows.message(track.title, tr("Loading lyrics…", "正在加载歌词…"))
            is LyricsState.NotFound -> LyricRows.message(track.title, tr("No synced lyrics for this song", "这首歌没有同步歌词"))
            is LyricsState.NetworkError -> LyricRows.message(track.title, tr("Network error, unable to fetch lyrics", "网络错误，无法获取歌词"))
            is LyricsState.Found ->
                if (!s.lyrics.synced) LyricRows.message(track.title, tr("These lyrics have no timing", "这份歌词没有时间轴"))
                else LyricRows.build(s.lyrics.lines, s.lyrics.indexAt(p.position() + style.offsetMs), style,
                    Prefs.translate.value)
            else -> listOf(Row("♪", Row.Kind.CURRENT))
        }
        val title = if (track.artist.isBlank()) track.title else "${track.title} — ${track.artist}"
        box.setState(p.playing, Prefs.translate.value)
        box.show(title, rows)
    }
}
