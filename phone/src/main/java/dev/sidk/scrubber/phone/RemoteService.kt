package dev.sidk.scrubber.phone

import android.os.Bundle
import android.util.Base64
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONObject
import java.io.File

class RemoteService : MediaSessionService() {
    companion object {
        val WHAT_SAY = SessionCommand("WHAT_SAY", Bundle.EMPTY)
        val CATCH_UP = SessionCommand("CATCH_UP", Bundle.EMPTY)
    }

    private lateinit var link: TvLink
    private lateinit var player: TvPlayer
    private lateinit var session: MediaSession
    private val pendingRecaps = mutableListOf<SettableFuture<SessionResult>>()
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        player = TvPlayer { link.send(it) }
        link = TvLink(BuildConfig.TV_HOST, ::onTvMessage, { setConnected(true) }) {
            setConnected(false)
            player.update(null)
            artRequested.clear() // ask again after reconnecting if a reply was lost
            finishRecaps(Bundle().apply { putString("error", "Not connected to the TV") })
        }
        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setDisplayName("Back 15 seconds")
                        .setSlots(CommandButton.SLOT_BACK)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setDisplayName("Forward 15 seconds")
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                        .setCustomIconResId(R.drawable.ic_what_say)
                        .setSessionCommand(WHAT_SAY)
                        .setDisplayName("What did they say?")
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build(),
                )
            )
            .build()
    }

    /** Thumbnails are pulled from the TV once per title and kept in the app's cache dir. */
    private val artDir by lazy { File(cacheDir, "art").apply { mkdirs() } }
    private val artRequested = mutableSetOf<String>()
    private var artShown = ""

    private fun ensureArt(id: String) {
        if (id.isEmpty() || id == artShown) return
        val file = File(artDir, "$id.jpg")
        if (file.exists()) { player.setArt(id, file.readBytes()); artShown = id }
        else if (artRequested.add(id)) link.send(JSONObject().put("cmd", "art").put("id", id))
    }

    private fun onTvMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "state" -> { player.update(msg); ensureArt(player.id) }
            "art" -> {
                val id = msg.optString("id")
                val jpeg = Base64.decode(msg.optString("jpeg"), Base64.DEFAULT)
                File(artDir, "$id.jpg").writeBytes(jpeg)
                player.setArt(id, jpeg)
                if (id == player.id) artShown = id
            }
            "idle" -> player.update(null) // connected, nothing playing
            "recap" -> finishRecaps(Bundle().apply {
                if (msg.has("text")) putString("text", msg.getString("text"))
                else putString("error", msg.optString("error", "No recap"))
            })
        }
    }

    /** Published to controllers as session extras {"connected": Boolean}. */
    private fun setConnected(c: Boolean) {
        if (c == connected) return
        connected = c
        session.setSessionExtras(Bundle().apply { putBoolean("connected", c) })
    }

    private fun finishRecaps(extras: Bundle) {
        pendingRecaps.forEach { it.set(SessionResult(SessionResult.RESULT_SUCCESS, extras)) }
        pendingRecaps.clear()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(WHAT_SAY).add(CATCH_UP).build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            WHAT_SAY.customAction -> {
                link.send(JSONObject().put("cmd", "whatSay"))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CATCH_UP.customAction -> SettableFuture.create<SessionResult>().also {
                pendingRecaps += it
                link.send(JSONObject().put("cmd", "catchUp"))
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        link.stop()
        finishRecaps(Bundle().apply { putString("error", "Remote stopped") })
        session.release()
        player.release()
        super.onDestroy()
    }
}
