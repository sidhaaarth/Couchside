package dev.sidk.scrubber.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Movie(
    val id: String,
    val title: String,
    val year: Int,
    val runtime: String,
    val bio: String,
    val videoUrl: String,
    val thumbUrl: String,
    val subtitleAsset: String?,
    val startMs: Long,
) {
    val meta get() = "$year · $runtime"
}

/** Public-domain films streamed from archive.org. */
object Catalog {
    val movies = listOf(
        Movie(
            "charade", "Charade", 1963, "1h 57m",
            "A Paris widow discovers her late husband stole a fortune — and that several dangerous men, and one very charming stranger, want it back. Cary Grant and Audrey Hepburn star in Stanley Donen's romantic thriller.",
            BuildConfig.VIDEO_URL, "https://archive.org/download/batvwi-Charade_1963/batvwi-Charade_1963.thumbs/Charade_1963_001857.jpg",
            "charade-en.srt", 223_000, // the archive.org copy opens with a presenter's intro
        ),
        Movie(
            "his-girl-friday", "His Girl Friday", 1940, "1h 32m",
            "A newspaper editor schemes to stop his star reporter — and ex-wife — from remarrying by luring her into one last story: a death-row case. Cary Grant and Rosalind Russell trade some of the fastest dialogue ever filmed.",
            "https://archive.org/download/his_girl_friday/his_girl_friday.mp4", "https://archive.org/services/img/his_girl_friday",
            null, 0,
        ),
        Movie(
            "night-of-the-living-dead", "Night of the Living Dead", 1968, "1h 36m",
            "Strangers barricade themselves in a Pennsylvania farmhouse as the recently dead return to life. George A. Romero's low-budget classic defined the modern zombie film.",
            "https://archive.org/download/night-of-the-living-dead-1968-english_20260713/Night%20of%20the%20Living%20Dead%20(1968)%20English.mp4",
            "https://archive.org/services/img/night-of-the-living-dead-1968-english_20260713",
            null, 0,
        ),
    )

    fun byId(id: String?) = movies.firstOrNull { it.id == id } ?: movies[0]
}

/** Saved playback position per movie id. */
val Context.resume get() = getSharedPreferences("resume", Context.MODE_PRIVATE)

object Thumbs {
    private val cache = HashMap<String, Bitmap>()
    private val jpegs = HashMap<String, ByteArray>()
    private val main = Handler(Looper.getMainLooper())

    /** Raw image bytes for the phone (main-thread callback; null on failure). */
    fun bytes(url: String, then: (ByteArray?) -> Unit) {
        jpegs[url]?.let { return then(it) }
        thread(isDaemon = true) {
            val data = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try { conn.inputStream.use { it.readBytes() } } finally { conn.disconnect() }
            } catch (_: Exception) { null }
            main.post { if (data != null) jpegs[url] = data; then(data) }
        }
    }

    fun load(url: String, into: ImageView) {
        cache[url]?.let { return into.setImageBitmap(it) }
        thread(isDaemon = true) {
            val bmp = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try { conn.inputStream.use(BitmapFactory::decodeStream) } finally { conn.disconnect() }
            } catch (_: Exception) { null }
            main.post { if (bmp != null) { cache[url] = bmp; into.setImageBitmap(bmp) } }
        }
    }
}
