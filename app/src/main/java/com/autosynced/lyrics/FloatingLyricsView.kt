package com.autosynced.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * The floating lyrics card: an optional "song — artist" header, then rows of lyrics
 * (earlier lines, the current one, its translation, coming lines), drawn as [FloatStyle] says.
 * Used by the floating window and by the live preview in its settings.
 * In the floating window, a tap shows a bar of buttons ([showControls]) that reports taps to [onAction].
 */
class FloatingLyricsView(context: Context) : LinearLayout(context) {
    private val header = OutlineTextView(context)
    private val rowsBox = LinearLayout(context).apply { orientation = VERTICAL }
    private val rowViews = ArrayList<OutlineTextView>()
    private var style = FloatStyle()
    private var night = true
    private var shownRows: List<Row> = emptyList()
    private var lastRows: List<Row> = emptyList()
    private var shownCurrent: String? = null
    private var typeface: Typeface = Typeface.DEFAULT
    private var boldTypeface: Typeface = Typeface.DEFAULT_BOLD

    /** What a button in the control bar asks for. */
    sealed class Action {
        data object PlayPause : Action()
        data object Previous : Action()
        data object Next : Action()
        data object Translate : Action()
        data object Close : Action()
        data object Settings : Action()
        data object Bigger : Action()
        data object Smaller : Action()
        data class Colour(val color: Int) : Action()
    }

    var onAction: (Action) -> Unit = {}
    private val controls = LinearLayout(context).apply { orientation = VERTICAL; visibility = View.GONE }
    private val swatches = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER; visibility = View.GONE }
    private val buttons = ArrayList<View>()            // tinted with the text colour
    private lateinit var playButton: ImageView
    private lateinit var translateButton: TextView
    private lateinit var colourDot: View
    private var playing = false
    private var translateOn = false
    private val hideControls = Runnable { showControls(false) }

    init {
        orientation = VERTICAL
        addView(header)
        addView(rowsBox)
        buildControls()
        addView(controls)
        apply(style, night, force = true)
    }

    // ---- the control bar ----------------------------------------------------------------
    private fun buildControls() {
        val size = dp(38f).toInt()
        fun icon(res: Int, action: Action) = ImageView(context).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7f).toInt(), dp(7f).toInt(), dp(7f).toInt(), dp(7f).toInt())
            layoutParams = LayoutParams(size, size).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() }
            setOnClickListener { tapped(action) }
            buttons.add(this)
        }
        fun text(label: String, action: Action) = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(size, size).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() }
            setOnClickListener { tapped(action) }
            buttons.add(this)
        }
        fun row(vararg views: View) = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4f).toInt(), 0, 0)
            views.forEach { addView(it) }
        }
        playButton = icon(android.R.drawable.ic_media_play, Action.PlayPause)
        translateButton = text("译", Action.Translate)
        colourDot = View(context).apply {
            layoutParams = LayoutParams(size, size).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() }
            setOnClickListener {
                swatches.visibility = if (swatches.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                keepControls()
            }
        }
        controls.addView(row(
            icon(android.R.drawable.ic_media_previous, Action.Previous), playButton,
            icon(android.R.drawable.ic_media_next, Action.Next), translateButton,
            icon(android.R.drawable.ic_menu_close_clear_cancel, Action.Close)))
        controls.addView(row(text("A−", Action.Smaller), text("A+", Action.Bigger), colourDot,
            icon(android.R.drawable.ic_menu_preferences, Action.Settings)))
        val dot = dp(26f).toInt()
        for (c in QUICK_COLOURS) {
            swatches.addView(View(context).apply {
                layoutParams = LayoutParams(dot, dot).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(1f).toInt(), 0x66888888)
                }
                setOnClickListener { tapped(Action.Colour(c)) }
            })
        }
        swatches.setPadding(0, dp(6f).toInt(), 0, dp(2f).toInt())
        controls.addView(swatches)
    }

    private fun tapped(action: Action) {
        keepControls()
        onAction(action)
    }

    /** Keeps the bar open a few more seconds after each tap. */
    private fun keepControls() {
        removeCallbacks(hideControls)
        postDelayed(hideControls, 6_000)
    }

    val controlsShown: Boolean get() = controls.visibility == View.VISIBLE

    fun showControls(show: Boolean) {
        removeCallbacks(hideControls)
        controls.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) swatches.visibility = View.GONE
        if (show) keepControls()
    }

    /** Play / pause icon and the 译 button's on/off look. */
    fun setState(isPlaying: Boolean, translate: Boolean) {
        if (isPlaying != playing) {
            playing = isPlaying
            playButton.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
        if (translate != translateOn) { translateOn = translate; tintControls() }
    }

    private fun tintControls() {
        val c = style.colors(night)
        for (b in buttons) {
            when (b) {
                is ImageView -> b.setColorFilter(c.current)
                is TextView -> b.setTextColor(c.current)
            }
            b.background = null
        }
        // 译 shows whether translation is on: filled when on
        translateButton.background = if (translateOn) GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(withAlpha(c.current, 0.25f))
        } else null
        colourDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(c.current); setStroke(dp(2f).toInt(), withAlpha(c.other, 0.8f))
        }
        colourDot.scaleX = 0.6f; colourDot.scaleY = 0.6f
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    /** Width in pixels this style asks for, on this screen. */
    fun widthPx(): Int = (resources.displayMetrics.widthPixels * style.widthPct / 100f).toInt()

    fun apply(newStyle: FloatStyle, isNight: Boolean, force: Boolean = false) {
        if (!force && newStyle == style && isNight == night) return
        val fontChanged = force || newStyle.font != style.font
        style = newStyle
        night = isNight
        if (fontChanged) {
            typeface = loadTypeface(context, style.font)
            boldTypeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val c = style.colors(night)
        val pad = dp(14f).toInt()
        setPadding(pad, dp(9f).toInt(), pad, dp(10f).toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(style.cornerDp.toFloat())
            setColor(withAlpha(c.bg, style.bgOpacity))
            if (style.bgOpacity > 0.05f) {
                setStroke(dp(1f).toInt(), if (night || !style.styleColors) 0x26FFFFFF else 0x1F000000)
            }
        }
        elevation = if (style.bgOpacity > 0.3f) dp(6f) else 0f
        dress(header, style.headerSize.toFloat(), c.header, bold = false, maxLines = 1)
        header.visibility = if (style.showHeader && header.text.isNotEmpty()) View.VISIBLE else View.GONE
        tintControls()
        shownRows = emptyList()            // restyle the rows on the next show()
        show(header.text.toString(), lastRows, animate = false)
    }

    /** Shows [rows] (and [title] in the header, if it's switched on). */
    fun show(title: String, rows: List<Row>, animate: Boolean = true) {
        lastRows = rows
        if (header.text != title) header.text = title
        header.visibility = if (style.showHeader && title.isNotEmpty()) View.VISIBLE else View.GONE
        if (rows == shownRows) return
        val c = style.colors(night)
        while (rowViews.size < rows.size) {
            val v = OutlineTextView(context)
            rowViews.add(v)
            rowsBox.addView(v)
        }
        for (i in rowViews.indices) {
            val v = rowViews[i]
            val row = rows.getOrNull(i)
            if (row == null) { v.visibility = View.GONE; continue }
            v.visibility = View.VISIBLE
            when (row.kind) {
                Row.Kind.CURRENT -> dress(v, style.currentSize.toFloat(), c.current, style.boldCurrent, 2)
                Row.Kind.TRANSLATION -> dress(v, style.currentSize * style.translationPct / 100f, c.translation, false, 2)
                else -> dress(v, style.otherSize.toFloat(), c.other, false, 1)
            }
            v.setPadding(v.paddingLeft, if (i == 0) 0 else dp(2f).toInt(), v.paddingRight, 0)
            if (v.text != row.text) v.text = row.text
        }
        val current = rows.firstOrNull { it.kind == Row.Kind.CURRENT }?.text
        val lineChanged = shownCurrent != null && current != shownCurrent
        shownRows = rows
        shownCurrent = current
        if (animate && lineChanged) animateChange()
    }

    private fun animateChange() {
        rowsBox.animate().cancel()
        when (style.animation) {
            "fade" -> {
                rowsBox.alpha = 0.15f
                rowsBox.animate().alpha(1f).setDuration(220).start()
            }
            "slide" -> {
                rowsBox.alpha = 0.3f
                rowsBox.translationY = dp(10f)
                rowsBox.animate().alpha(1f).translationY(0f).setDuration(260).start()
            }
        }
    }

    private fun dress(v: OutlineTextView, sizeSp: Float, color: Int, bold: Boolean, maxLines: Int) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        v.setTextColor(color)
        v.typeface = if (bold) boldTypeface else typeface
        v.maxLines = maxLines
        v.ellipsize = TextUtils.TruncateAt.END
        v.gravity = if (style.align == "left") Gravity.START else Gravity.CENTER_HORIZONTAL
        v.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        v.outlineColor = style.outlineColor
        v.outlineWidthPx = if (style.outline) style.outlineWidth * resources.displayMetrics.density else 0f
        val side = v.outlineWidthPx.toInt()
        v.setPadding(side, v.paddingTop, side, v.paddingBottom)
    }

    companion object {
        /** The colour dots in the floating window's quick colour row. */
        val QUICK_COLOURS = listOf(0xFFFFFFFF, 0xFFFFD43B, 0xFF69DB7C, 0xFF4DABF7, 0xFFB197FC, 0xFFF783AC, 0xFFFF6B6B, 0xFF1D1D1F)
            .map { it.toInt() }

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        fun customFontFile(c: Context) = File(c.filesDir, "fonts/custom-font")

        fun loadTypeface(c: Context, font: String): Typeface =
            if (font.startsWith(FloatStyle.CUSTOM_FONT)) {     // "custom:<when it was picked>"
                try { Typeface.createFromFile(customFontFile(c)) } catch (_: Exception) { Typeface.DEFAULT }
            } else Typeface.create(font, Typeface.NORMAL)
    }
}

/** A TextView that can draw an outline around its letters (readable over any background). */
class OutlineTextView(context: Context) : TextView(context) {
    var outlineColor: Int = Color.BLACK
        set(v) { if (field != v) { field = v; invalidate() } }
    var outlineWidthPx: Float = 0f
        set(v) { if (field != v) { field = v; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (outlineWidthPx > 0f && l != null) {
            // Draw the same text once as a thick stroke in the outline colour, then normally on top.
            val p = paint
            val style = p.style
            val width = p.strokeWidth
            p.style = Paint.Style.STROKE
            p.strokeWidth = outlineWidthPx * 2
            p.strokeJoin = Paint.Join.ROUND
            p.color = outlineColor
            canvas.save()
            canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
            l.draw(canvas)
            canvas.restore()
            p.style = style
            p.strokeWidth = width
        }
        super.onDraw(canvas)
    }
}
