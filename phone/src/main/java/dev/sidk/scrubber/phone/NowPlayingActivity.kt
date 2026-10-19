package dev.sidk.scrubber.phone

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture

class NowPlayingActivity : Activity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val titles = mutableListOf<TextView>()
    private val playButtons = mutableListOf<ImageButton>()
    private val seekBars = mutableListOf<SeekBar>()
    private val elapsedLabels = mutableListOf<TextView>()
    private val remainingLabels = mutableListOf<TextView>()
    private lateinit var root: FrameLayout
    private lateinit var full: View
    private lateinit var bigArt: ArtView
    private lateinit var recapView: View
    private lateinit var recapStamp: TextView
    private lateinit var recapText: TextView
    private var dragging = false
    private var recap = false
    private var backCallback: Any? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controllerFuture = connectController { c ->
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
        }
        full = fullView()
        recapView = recapView().apply { visibility = View.GONE }
        root = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(BG)
            addView(full, FrameLayout.LayoutParams(-1, -1))
            addView(recapView, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
    }

    private fun seekBar() = SeekBar(this).apply {
        orange()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) showTimes(p * 1000L, s.max * 1000L)
            }
            override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar) {
                dragging = false
                controller?.seekTo(s.progress * 1000L)
            }
        })
    }.also { seekBars += it }

    private fun playButton(sizeDp: Int, bg: Int) =
        iconButton(PLAY, sizeDp, bg) { controller?.togglePlay() }.also { playButtons += it }

    private fun whatSay(sizeDp: Int) =
        iconButton(R.drawable.ic_what_say, sizeDp) { send(controller, RemoteService.WHAT_SAY) }

    private fun backButton(onClick: () -> Unit) =
        iconButton(R.drawable.ic_arrow_back, 44, TILE, onClick).apply { contentDescription = "Back" }

    private fun title(sp: Float) = label("Nothing playing", sp, bold = true).apply { setSingleLine() }.also { titles += it }

    private fun fullView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), dp(24))
        addView(backButton { finish() }, LayoutParams(dp(44), dp(44)))
        // TV thumbnail zoomed to fill the whole tile.
        addView(art(16, 72f).also { bigArt = it }, LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(24) })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title(24f))
                addView(label("Playing on Fire TV", 14f, MUTED))
            }, LayoutParams(0, -2, 1f))
            addView(iconButton(R.drawable.ic_info, 44, TILE) { showHowItWorks() }
                .apply { contentDescription = "How it works" }, LayoutParams(dp(44), dp(44)))
        }, LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        addView(controls(), LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
        addView(label("Catch me up", 17f, BG, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(ORANGE, 28)
            setOnClickListener { catchUp() }
        }, LayoutParams(-1, dp(56)))
    }

    /** Scrubber, times, then the transport row: (gap) −15, big play, +15, 💬 — Spotify's bottom layout. */
    private fun controls() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(seekBar().apply { setPadding(0, dp(8), 0, dp(8)) })
        addView(LinearLayout(context).apply {
            addView(label("0:00", 12f, MUTED).also { elapsedLabels += it }, LayoutParams(0, -2, 1f))
            addView(label("-0:00", 12f, MUTED).also { remainingLabels += it })
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context), LayoutParams(0, 0, 1f))
            addView(iconButton(BACK15, 56) { controller?.seekBack() }, LayoutParams(0, dp(56), 1f))
            addView(playButton(72, Color.WHITE), LayoutParams(dp(72), dp(72)).apply { marginStart = dp(8); marginEnd = dp(8) })
            addView(iconButton(FWD15, 56) { controller?.seekForward() }, LayoutParams(0, dp(56), 1f))
            addView(whatSay(56), LayoutParams(0, dp(56), 1f))
        }, LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    /** Spotify-lyrics layout: collapse button + centred titles, recap on a grey rounded tile, controls at the bottom. */
    private fun recapView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
        setPadding(dp(16), dp(8), dp(16), dp(20))
        addView(FrameLayout(context).apply {
            addView(backButton { setRecap(false) },
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER_VERTICAL or Gravity.START))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(label("Catch me up", 15f).figtree(700))
                addView(title(13f).figtree(500).apply { setTextColor(MUTED) })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }, LayoutParams(-1, dp(56)))
        recapStamp = label("", 14f, 0xFFB3B3B3.toInt()).figtree(600)
        recapText = label("", 17f).figtree(700).apply {
            setLineSpacing(dp(7).toFloat(), 1f)
            letterSpacing = -0.01f
        }
        addView(ScrollView(context).apply {
            background = rounded(TILE, 24)
            clipToOutline = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(40))
            defaultFocusHighlightEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(22), dp(22), dp(22))
                addView(recapStamp, LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
                addView(recapText)
            })
        }, LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(16) })
        addView(controls())
    }

    /** Explains the two extras, each next to the icon the user taps (same style as the Home TV dialog). */
    private fun showHowItWorks() = Dialog(this).apply {
        fun item(icon: Int, name: String, what: String) = LinearLayout(context).apply {
            setPadding(0, dp(10), 0, dp(10))
            addView(ImageView(context).apply { setImageResource(icon) },
                LayoutParams(dp(30), dp(30)).apply { topMargin = dp(2); marginEnd = dp(14) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(name, 16f).figtree(700))
                addView(label(what, 14f, 0xFFC7CDD3.toInt()).figtree(400).apply { setLineSpacing(dp(3).toFloat(), 1f) },
                    LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }, LayoutParams(0, -2, 1f))
        }
        setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(TILE, 16)
            setPadding(dp(20), dp(20), dp(20), dp(12))
            addView(label("How it works", 18f).figtree(700), LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
            addView(item(R.drawable.ic_what_say, "What did they say?",
                "Missed a line? Tap it and the TV jumps back 10 seconds with subtitles on for a moment, " +
                    "then carries on. It's on your lock screen too."))
            addView(item(R.drawable.ic_catch_up, "Catch me up",
                "Walked in late? Get a spoiler-free summary of the story so far, right here on your phone, " +
                    "while the movie keeps playing for everyone else."))
            addView(label("Got it", 15f, ORANGE).figtree(600).apply {
                setPadding(dp(12), dp(10), dp(4), dp(6))
                setOnClickListener { dismiss() }
            }, LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(4) })
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        show()
        window?.setLayout(resources.displayMetrics.widthPixels - dp(48), -2)
    }

    private fun catchUp() {
        recapStamp.text = "Up to " + fmt(controller?.currentPosition ?: 0)
        recapText.text = "Catching you up…"
        setRecap(true)
        send(controller, RemoteService.CATCH_UP) { recapText.text = it.getString("text") ?: it.getString("error") }
    }

    private fun setRecap(on: Boolean) {
        if (recap == on) return
        recap = on
        TransitionManager.beginDelayedTransition(root)
        full.visibility = if (on) View.GONE else View.VISIBLE
        recapView.visibility = if (on) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= 33) {
            val d = onBackInvokedDispatcher
            if (on) d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { setRecap(false) }.also { backCallback = it })
            else (backCallback as? OnBackInvokedCallback)?.let(d::unregisterOnBackInvokedCallback)
        }
    }

    @Deprecated("Pre-33 back handling")
    override fun onBackPressed() {
        if (recap) setRecap(false) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun showTimes(pos: Long, dur: Long) {
        elapsedLabels.forEach { it.text = fmt(pos) }
        remainingLabels.forEach { it.text = "-" + fmt(dur - pos) }
    }

    private fun refresh() {
        val c = controller?.takeIf { it.currentMediaItem != null }
        val name = c?.mediaMetadata?.title ?: "Nothing playing"
        titles.forEach { it.text = name }
        bigArt.show(c?.mediaMetadata?.artworkData)
        playButtons.forEach { it.setImageResource(if (c?.playWhenReady == true) PAUSE else PLAY) }
        if (dragging) return
        val dur = c?.duration?.coerceAtLeast(0) ?: 0
        val pos = c?.currentPosition ?: 0
        seekBars.forEach { it.max = (dur / 1000).toInt(); it.progress = (pos / 1000).toInt() }
        showTimes(pos, dur)
    }

    override fun onStart() { super.onStart(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }

    override fun onDestroy() {
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}
