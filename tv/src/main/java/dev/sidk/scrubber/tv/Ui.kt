package dev.sidk.scrubber.tv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

const val BG = 0xFF000000.toInt()
const val TILE = 0xFF2A2A2C.toInt()
const val ORANGE = 0xFFFF9900.toInt()
const val MUTED = 0xFF8D9BA8.toInt()

fun Context.dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Number, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    if (strokeColor != 0) setStroke(dp(3), strokeColor)
}

fun <T : TextView> T.figtree(weight: Int) = apply {
    typeface = resources.getFont(R.font.figtree)
    fontVariationSettings = "'wght' $weight"
}

fun Context.label(s: CharSequence, sp: Float, weight: Int = 400, color: Int = Color.WHITE) =
    TextView(this).apply { text = s; textSize = sp; setTextColor(color) }.figtree(weight)

/** D-pad focus state: scale up and draw a 3dp outline on [outline]. */
fun View.tvFocus(radiusDp: Int, outline: View = this, color: Int = ORANGE, onClick: () -> Unit) {
    isFocusable = true
    isClickable = true
    setOnClickListener { onClick() }
    setOnFocusChangeListener { _, f ->
        animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
        outline.foreground = if (f) context.rounded(Color.TRANSPARENT, radiusDp, color) else null
    }
}

/** Rounded 16:9 thumbnail: dark tile with an orange play glyph until the image arrives. */
fun Context.thumb(movie: Movie, radiusDp: Int) = FrameLayout(this).apply {
    background = rounded(TILE, radiusDp)
    clipToOutline = true
    addView(label("▶", 40f, color = ORANGE).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
    addView(ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        Thumbs.load(movie.thumbUrl, this)
    }, FrameLayout.LayoutParams(-1, -1))
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
