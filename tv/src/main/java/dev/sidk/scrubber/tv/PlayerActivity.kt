package dev.sidk.scrubber.tv

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import kotlin.concurrent.thread

class PlayerActivity : Activity() {
    private lateinit var movie: Movie
    private val main = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var badge: LinearLayout
    private lateinit var badgeText: TextView
    private lateinit var logo: ImageView
    private var cues = emptyList<Cue>()
    private var ticks = 0

    private val tick = object : Runnable {
        override fun run() {
            Remote.broadcast(JSONObject()
                .put("type", "state").put("id", movie.id).put("title", movie.title)
                .put("pos", player.currentPosition)
                .put("dur", player.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
                .put("playing", player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)))
            if (++ticks % 20 == 0) savePosition()
            main.postDelayed(this, 500)
        }
    }
    private val endWhatSay = Runnable { setSubtitles(false); badge.visibility = View.GONE }
    private val hideBadge = Runnable { badge.visibility = View.GONE }
    private val hideLogo = Runnable { logo.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        movie = Catalog.byId(intent.getStringExtra("id"))
        Remote.start()

        player = ExoPlayer.Builder(this).setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(15_000).build()
        // Fire TV logo top-left: shown for a few seconds at launch, then whenever the player controls are up.
        logo = ImageView(this).apply { setImageResource(R.drawable.firetv_logo); adjustViewBounds = true }
        val playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                main.removeCallbacks(hideLogo)
                logo.visibility = v
            })
        }
        badgeText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 22f }.figtree(600)
        badge = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(20, 16, 32, 16)
            visibility = View.GONE
            addView(ImageView(context).apply { setImageResource(R.drawable.firetv_icon) },
                LinearLayout.LayoutParams(56, 56).apply { marginEnd = 20 })
            addView(badgeText)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(-1, -1))
            addView(logo, FrameLayout.LayoutParams(-2, 72, Gravity.TOP or Gravity.START).apply { setMargins(48, 48, 48, 48) })
            addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(48, 48, 48, 48) })
        })
        main.postDelayed(hideLogo, 5_000)

        val item = MediaItem.Builder().setUri(movie.videoUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(movie.title).build())
        movie.subtitleAsset?.let { srt ->
            item.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse("asset:///$srt"))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            cues = Srt.parse(assets.open(srt).bufferedReader().use { it.readText() })
        }
        player.setMediaItem(item.build(), intent.getLongExtra("pos", movie.startMs))
        setSubtitles(false)
        player.prepare()
        player.play()
    }

    override fun onStart() {
        super.onStart()
        Remote.handler = ::onCommand
        main.post(tick)
    }

    override fun onPause() {
        savePosition()
        super.onPause()
    }

    override fun onStop() {
        main.removeCallbacks(tick)
        Remote.handler = null
        Remote.broadcast(Remote.idle)
        player.pause()
        super.onStop()
    }

    private fun savePosition() {
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        resume.edit().apply { if (pos > dur - 120_000) remove(movie.id) else putLong(movie.id, pos) }.apply()
    }

    private fun setSubtitles(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on).build()
    }

    private fun showBadge(text: String, ms: Long, then: Runnable) {
        main.removeCallbacks(endWhatSay)
        main.removeCallbacks(hideBadge)
        badgeText.text = text
        badge.visibility = View.VISIBLE
        main.postDelayed(then, ms)
    }

    private fun onCommand(cmd: JSONObject, reply: (JSONObject) -> Unit) {
        when (cmd.optString("cmd")) {
            "play" -> player.play()
            "pause" -> player.pause()
            "toggle" -> if (player.playWhenReady) player.pause() else player.play()
            "seek" -> player.seekTo(cmd.optLong("ms"))
            "back" -> player.seekBack()
            "fwd" -> player.seekForward()
            "whatSay" -> {
                player.seekTo(maxOf(0, player.currentPosition - 10_000))
                player.play()
                if (movie.subtitleAsset == null) showBadge("↺ 10s", 3_000, hideBadge)
                else { setSubtitles(true); showBadge("↺ 10s · subtitles on", 12_000, endWhatSay) }
            }
            "catchUp" -> catchUp(reply)
        }
    }

    private fun catchUp(reply: (JSONObject) -> Unit) {
        val msg = JSONObject().put("type", "recap")
        if (movie.subtitleAsset == null) return reply(msg.put("error", "Catch me up needs English subtitles — try Charade."))
        // Don't clobber an active whatSay (it would leave subtitles on); its badge stays instead.
        if (badge.visibility != View.VISIBLE) showBadge("Catching someone up…", 3_000, hideBadge)
        val pos = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.let(Srt::clock).orEmpty()
        val transcript = Srt.transcriptUpTo(cues, pos)
        when {
            transcript.isEmpty() -> reply(msg.put("text", "Nothing's been said yet — you haven't missed anything."))
            BuildConfig.RECAP_URL.isBlank() -> reply(msg.put("error", "Recap service isn't set up yet."))
            else -> thread {
                val result = try { msg.put("text", Recap.fetch(BuildConfig.RECAP_URL, movie.title, Srt.clock(pos), duration, transcript)) }
                catch (e: Exception) { msg.put("error", e.message ?: "Recap failed") }
                main.post { reply(result) }
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
