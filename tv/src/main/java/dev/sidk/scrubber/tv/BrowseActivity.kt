package dev.sidk.scrubber.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams

class BrowseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Remote.start()
        val row = LinearLayout(this).apply {
            clipChildren = false
            Catalog.movies.forEach { m ->
                addView(card(m), LayoutParams(dp(264), -2).apply { marginEnd = dp(28) })
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(BG)
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(ImageView(context).apply { setImageResource(R.drawable.firetv_logo); adjustViewBounds = true },
                LayoutParams(-2, dp(40)))
            addView(label("Movies", 28f, 700), LayoutParams(-2, -2).apply { topMargin = dp(36); bottomMargin = dp(20) })
            addView(row)
        })
        row.getChildAt(0).requestFocus()
    }

    private fun card(m: Movie) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val art = thumb(m, 12)
        addView(art, LayoutParams(-1, dp(264 * 9 / 16)))
        addView(label(m.title, 18f, 600).apply { setSingleLine() }, LayoutParams(-2, -2).apply { topMargin = dp(12) })
        addView(label(m.meta, 14f, color = MUTED))
        tvFocus(12, art) { startActivity(Intent(context, DetailsActivity::class.java).putExtra("id", m.id)) }
    }
}
