package com.autosynced.lyrics

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * Drag to move · tap to open the app · long-press to turn it off. When it's locked, touches go
 * through to the app underneath; unlock it from the notification or the settings.
 * Runs as a foreground service (with a small notification) so Android keeps it on screen.
 */
class FloatingLyricsService : Service() {
    companion object {
        private const val CHANNEL = "floating"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_TOGGLE_LOCK = "toggle_lock"
        // Android only lets touches pass through a window that's at most this opaque.
        private const val MAX_PASS_THROUGH_ALPHA = 0.8f

        fun canDraw(c: Context) = Settings.canDrawOverlays(c)

        fun start(c: Context) {
            if (!canDraw(c)) return
            val i = Intent(c, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) { c.stopService(Intent(c, FloatingLyricsService::class.java)) }
    }

    private lateinit var wm: WindowManager
    private var card: FloatingLyricsView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var appliedStyle: FloatStyle? = null
    private var appliedNight = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!canDraw(this)) { stopSelf(); return }
        NowPlaying.start(this)
        LyricsRepo.start(this)
        addOverlay()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_LOCK) {
            val s = Prefs.floatStyle.value
            Prefs.setFloatStyle(this, s.copy(locked = !s.locked))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        card?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        card = null
        super.onDestroy()
    }

    private fun notification(locked: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val lock = PendingIntent.getService(this, 1,
            Intent(this, FloatingLyricsService::class.java).setAction(ACTION_TOGGLE_LOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (locked) "Floating lyrics is on (locked)" else "Floating lyrics is on")
            .setContentText(if (locked) "Touches go through the lyrics. Tap Unlock to move them again."
                else "Tap to open the app. Long-press the lyrics to turn them off.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_lock_lock),
                if (locked) "Unlock" else "Lock", lock).build())
            .setOngoing(true)
            .build()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating lyrics", NotificationManager.IMPORTANCE_MIN))
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
            Toast.makeText(this, "Floating lyrics turned off", Toast.LENGTH_SHORT).show()
            stopSelf()
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
                    else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            true
        }
        applyStyle()
        wm.addView(box, params)
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
        if (track == null) { box.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        val style = Prefs.floatStyle.value
        val rows = when (val s = LyricsRepo.state.value) {
            is LyricsState.Loading -> LyricRows.message(track.title, "Loading lyrics…")
            is LyricsState.NotFound -> LyricRows.message(track.title, "No synced lyrics for this song")
            is LyricsState.NetworkError -> LyricRows.message(track.title, "Network error, unable to fetch lyrics")
            is LyricsState.Found ->
                if (!s.lyrics.synced) LyricRows.message(track.title, "These lyrics have no timing")
                else LyricRows.build(s.lyrics.lines, s.lyrics.indexAt(p.position() + style.offsetMs), style,
                    Prefs.translate.value)
            else -> listOf(Row("♪", Row.Kind.CURRENT))
        }
        val title = if (track.artist.isBlank()) track.title else "${track.title} — ${track.artist}"
        box.show(title, rows)
    }
}
