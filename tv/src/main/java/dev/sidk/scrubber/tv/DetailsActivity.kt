package dev.sidk.scrubber.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams

class DetailsActivity : Activity() {
    private lateinit var movie: Movie
    private lateinit var buttons: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Remote.start()
        movie = Catalog.byId(intent.getStringExtra("id"))
        val artW = resources.displayMetrics.widthPixels * 45 / 100 - dp(48)
        buttons = LinearLayout(this).apply { clipChildren = false }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(label(movie.title, 40f, 700))
            addView(label(movie.meta, 18f, color = MUTED), LayoutParams(-2, -2).apply { topMargin = dp(4) })
            addView(label(movie.bio, 18f, color = 0xFFC8D0D8.toInt()).apply { setLineSpacing(0f, 1.35f) },
                LayoutParams(-1, -2).apply { topMargin = dp(20) })
            addView(buttons, LayoutParams(-2, -2).apply { topMargin = dp(32) })
        }
        setContentView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(BG)
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(thumb(movie, 16), LayoutParams(artW, artW * 9 / 16))
            addView(info, LayoutParams(0, -2, 1f).apply { marginStart = dp(40) })
        })
    }

    override fun onResume() {
        super.onResume()
        buttons.removeAllViews()
        val saved = resume.getLong(movie.id, 0)
        if (saved > movie.startMs + 30_000) buttons.addView(button("Resume from ${fmt(saved)}", ORANGE, BG, saved))
        buttons.addView(button("Start over", TILE, Color.WHITE, movie.startMs))
        buttons.getChildAt(0).requestFocus()
    }

    private fun button(text: String, bg: Int, fg: Int, startMs: Long) = label(text, 18f, 700, fg).apply {
        background = rounded(bg, 26)
        gravity = Gravity.CENTER
        setPadding(dp(28), 0, dp(28), 0)
        layoutParams = LayoutParams(-2, dp(52)).apply { marginEnd = dp(16) }
        tvFocus(26, color = if (bg == ORANGE) Color.WHITE else ORANGE) {
            startActivity(Intent(context, PlayerActivity::class.java).putExtra("id", movie.id).putExtra("pos", startMs))
        }
    }
}
