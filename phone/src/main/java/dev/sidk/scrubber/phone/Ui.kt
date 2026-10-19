package dev.sidk.scrubber.phone

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.R as M

const val BG = 0xFF000000.toInt()
const val CARD = 0xFF1B2530.toInt()
const val CARD2 = 0xFF26323E.toInt()
const val ORANGE = 0xFFFF9900.toInt()
const val MUTED = 0xFF8D9BA8.toInt()
const val TILE = 0xFF2A2A2C.toInt()

fun Context.dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Number) =
    GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

fun Context.label(s: CharSequence, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

/** Figtree (OFL, bundled in res/font) at a given weight — the closest open match to Spotify's lyrics type. */
fun <T : TextView> T.figtree(weight: Int) = apply {
    typeface = resources.getFont(R.font.figtree)
    fontVariationSettings = "'wght' $weight"
}

fun Context.iconButton(res: Int, sizeDp: Int, bg: Int = Color.TRANSPARENT, onClick: () -> Unit) =
    ImageButton(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(if (bg == Color.WHITE) BG else Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val p = dp(sizeDp) / 5
        setPadding(p, p, p, p)
        setOnClickListener { onClick() }
    }

/** Title artwork on a rounded tile; falls back to an orange play glyph until the TV's thumbnail arrives. */
class ArtView(ctx: Context, radiusDp: Int, glyphSp: Float, crop: Boolean) : FrameLayout(ctx) {
    private val image = ImageView(ctx).apply {
        scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        visibility = GONE
    }
    private var shown: ByteArray? = null

    init {
        background = ctx.rounded(CARD2, radiusDp)
        clipToOutline = true
        addView(ctx.label("▶", glyphSp, ORANGE).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -1))
        addView(image, LayoutParams(-1, -1))
    }

    /** Called on every UI tick; only decodes when the artwork actually changes. */
    fun show(jpeg: ByteArray?) {
        if (jpeg === shown || (jpeg != null && jpeg.size == shown?.size)) return
        shown = jpeg
        image.setImageBitmap(jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
        image.visibility = if (jpeg == null) GONE else VISIBLE
    }
}

fun Context.art(radiusDp: Int, glyphSp: Float, crop: Boolean = true) = ArtView(this, radiusDp, glyphSp, crop)

fun ProgressBar.orange() {
    progressTintList = ColorStateList.valueOf(ORANGE)
    progressBackgroundTintList = ColorStateList.valueOf(0xFF3A4754.toInt())
    if (this is android.widget.SeekBar) thumbTintList = ColorStateList.valueOf(ORANGE)
}

val PLAY = M.drawable.media3_icon_play
val PAUSE = M.drawable.media3_icon_pause
val BACK15 = M.drawable.media3_icon_skip_back_15
val FWD15 = M.drawable.media3_icon_skip_forward_15

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun Activity.connectController(
    listener: MediaController.Listener? = null,
    onReady: (MediaController) -> Unit,
): ListenableFuture<MediaController> {
    val token = SessionToken(this, ComponentName(this, RemoteService::class.java))
    return MediaController.Builder(this, token).apply { listener?.let(::setListener) }.buildAsync().also { f ->
        f.addListener({ runCatching { f.get() }.onSuccess(onReady) }, mainExecutor)
    }
}

fun MediaController.togglePlay() = if (playWhenReady) pause() else play()

fun Activity.send(c: MediaController?, cmd: SessionCommand, then: (Bundle) -> Unit = {}) {
    val r = c?.sendCustomCommand(cmd, Bundle.EMPTY) ?: return then(Bundle().apply { putString("error", "Not connected") })
    r.addListener({ then(r.get().extras) }, mainExecutor)
}
