package dev.sidk.scrubber.phone

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : Activity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var playBtn: ImageButton
    private lateinit var bannerArt: ArtView
    private lateinit var progress: ProgressBar
    private var tvConnected = false
    private var dialogStatus: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, RemoteService::class.java))
        controllerFuture = connectController(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                tvConnected = extras.getBoolean("connected")
                refresh()
            }
        }) { c ->
            controller = c
            tvConnected = c.sessionExtras.getBoolean("connected")
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
            addView(header())
            addView(banner(), LayoutParams(-1, -2).apply { topMargin = dp(12) })
            section("Continue watching", 168, 96,
                "Charade" to 0xFF3B5B7A, "His Girl Friday" to 0xFF4E6B3A, "Night of the Living Dead" to 0xFF6A3F5C)
            section("Your apps", 104, 104,
                "Prime Video" to 0xFF1F4E79, "Netflix" to 0xFF5C1F24, "YouTube" to 0xFF6B2B2B, "Live TV" to 0xFF2E5E5A, "Music" to 0xFF4A3C78)
            section("Recommended for you", 120, 168,
                "The General" to 0xFF35506B, "Nosferatu" to 0xFF5E4B2A, "Metropolis" to 0xFF3E5E3A, "The Kid" to 0xFF4B3B5E)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(BG)
            addView(ScrollView(context).apply { defaultFocusHighlightEnabled = false; addView(content) }, LayoutParams(-1, 0, 1f))
            addView(bottomNav())
        })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(LinearLayout(context).apply {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.firetv_logo)
                adjustViewBounds = true
                contentDescription = "fire tv"
            }, LayoutParams(-2, dp(34)))
        }, LayoutParams(0, -2, 1f))
        addView(label("⚙", 24f, MUTED).apply {
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { showStatus() }
        })
    }

    private fun showStatus() {
        val status = label("", 13f).figtree(500).also { dialogStatus = it; it.paintStatus() }
        Dialog(this).apply {
            setContentView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(TILE, 16)
                setPadding(dp(20), dp(20), dp(20), dp(12))
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(context).apply { setImageResource(R.drawable.ic_wifi) }, LayoutParams(dp(28), dp(28)))
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), 0, 0, 0)
                        addView(label("Home TV", 17f).figtree(600))
                        addView(status)
                    })
                })
                addView(label("Done", 15f, ORANGE).figtree(600).apply {
                    setPadding(dp(12), dp(10), dp(4), dp(6))
                    setOnClickListener { dismiss() }
                }, LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(12) })
            })
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { dialogStatus = null }
            show()
            window?.setLayout(resources.displayMetrics.widthPixels - dp(64), -2)
        }
    }

    private fun TextView.paintStatus() {
        text = if (tvConnected) "● Connected" else "Not connected"
        setTextColor(if (tvConnected) 0xFF4CD964.toInt() else MUTED)
    }

    private fun banner() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 12)
        clipToOutline = true
        setOnClickListener { startActivity(Intent(context, NowPlayingActivity::class.java)) }
        title = label("Nothing playing", 16f, bold = true).apply { setSingleLine() }
        subtitle = label("", 13f, MUTED)
        playBtn = iconButton(PLAY, 40, Color.WHITE) { controller?.togglePlay() }
        progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; orange() }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(art(8, 22f).also { bannerArt = it }, LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(title)
                addView(subtitle)
            }, LayoutParams(0, -2, 1f))
            addView(playBtn, LayoutParams(dp(40), dp(40)))
        })
        addView(progress, LayoutParams(-1, dp(3)))
    }

    private fun LinearLayout.section(name: String, w: Int, h: Int, vararg tiles: Pair<String, Long>) {
        addView(label(name, 18f, bold = true), LayoutParams(-2, -2).apply { topMargin = dp(24); bottomMargin = dp(10) })
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                for ((text, color) in tiles) addView(label(text, 13f, bold = true).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = rounded(color.toInt(), 10)
                }, LayoutParams(dp(w), dp(h)).apply { marginEnd = dp(10) })
            })
        })
    }

    private fun bottomNav() = LinearLayout(this).apply {
        setBackgroundColor(CARD)
        setPadding(0, dp(8), 0, dp(8))
        listOf("⌂" to "Home", "◉" to "Live", "⌕" to "Find", "▤" to "Library", "✥" to "Remote").forEachIndexed { i, (glyph, name) ->
            val color = if (i == 0) ORANGE else MUTED
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(label(glyph, 20f, color), LayoutParams(-2, -2))
                addView(label(name, 11f, color), LayoutParams(-2, -2))
            }, LayoutParams(0, -2, 1f))
        }
    }

    private fun refresh() {
        val c = controller
        val active = c?.currentMediaItem != null
        dialogStatus?.paintStatus()
        title.text = when {
            active -> c!!.mediaMetadata.title ?: "Fire TV"
            tvConnected -> "Nothing playing"
            else -> "Not connected"
        }
        subtitle.text = when {
            active -> "Playing on Fire TV"
            tvConnected -> "Pick a movie on your Fire TV"
            else -> "Make sure your Fire TV is on"
        }
        bannerArt.show(if (active) c!!.mediaMetadata.artworkData else null)
        playBtn.visibility = if (active) View.VISIBLE else View.GONE
        progress.visibility = if (active) View.VISIBLE else View.INVISIBLE
        if (!active) return
        playBtn.setImageResource(if (c!!.playWhenReady) PAUSE else PLAY)
        val dur = c.duration
        progress.progress = if (dur > 0) (c.currentPosition * 1000 / dur).toInt() else 0
    }

    override fun onStart() { super.onStart(); handler.post(tick) }
    override fun onStop() { handler.removeCallbacks(tick); super.onStop() }

    override fun onDestroy() {
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}
