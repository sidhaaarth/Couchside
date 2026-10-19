package dev.sidk.scrubber.phone

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject

/** A Player that mirrors the TV's playback; controls are forwarded as commands. */
class TvPlayer(private val send: (JSONObject) -> Unit) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var connected = false
    var id = ""; private set
    private var art: ByteArray? = null
    private var title = ""
    private var dur = 0L
    private var playing = false
    private var pos = 0L
    private var posAt = 0L

    fun update(state: JSONObject?) {
        connected = state != null
        if (state != null) {
            val newId = state.optString("id")
            if (newId != id) { id = newId; art = null }
            title = state.optString("title")
            dur = state.optLong("dur")
            playing = state.optBoolean("playing")
            setPos(state.optLong("pos"))
        }
        invalidateState()
    }

    /** Thumbnail for the current title (shown in the app, the notification and the lock screen). */
    fun setArt(forId: String, jpeg: ByteArray) {
        if (forId != id) return
        art = jpeg
        invalidateState()
    }

    private fun setPos(ms: Long) {
        pos = ms
        posAt = SystemClock.elapsedRealtime()
    }

    private fun currentPos() = if (playing) pos + SystemClock.elapsedRealtime() - posAt else pos

    override fun getState(): State {
        val b = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    COMMAND_PLAY_PAUSE, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK,
                    COMMAND_SEEK_FORWARD, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE,
                    COMMAND_GET_METADATA,
                ).build()
            )
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
        if (!connected) return b.setPlaybackState(STATE_IDLE).setPlaylist(emptyList()).build()
        val item = MediaItemData.Builder("tv")
            .setMediaItem(MediaItem.Builder().setMediaId(id.ifEmpty { "tv" }).build())
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title)
                    .setArtist("On your Fire TV").setSubtitle("On your Fire TV")
                    .apply { art?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
                    .build()
            )
            .setDurationUs(if (dur > 0) dur * 1000 else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
        val p = currentPos()
        return b.setPlaylist(listOf(item))
            .setPlaybackState(STATE_READY)
            .setPlayWhenReady(playing, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setContentPositionMs(
                if (playing) PositionSupplier.getExtrapolating(p, 1f) else PositionSupplier.getConstant(p)
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        setPos(currentPos())
        playing = playWhenReady
        send(JSONObject().put("cmd", if (playWhenReady) "play" else "pause"))
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        setPos(positionMs.coerceAtLeast(0))
        send(JSONObject().put("cmd", "seek").put("ms", pos))
        return Futures.immediateVoidFuture()
    }
}
