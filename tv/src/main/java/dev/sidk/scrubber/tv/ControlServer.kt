package dev.sidk.scrubber.tv

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Newline-delimited JSON over TCP. Commands arrive on the main thread; all writes happen on [writer]. */
class ControlServer(
    port: Int,
    private val onConnect: (send: (JSONObject) -> Unit) -> Unit,
    private val onCommand: (cmd: JSONObject, reply: (JSONObject) -> Unit) -> Unit,
) {
    private val server = ServerSocket(port)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val writer = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    init {
        thread(name = "control-accept", isDaemon = true) {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (e: Exception) { break }
                clients += s
                main.post { onConnect { send(s, it) } }
                thread(name = "control-client", isDaemon = true) { read(s) }
            }
        }
    }

    private fun read(s: Socket) {
        try {
            s.getInputStream().bufferedReader().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val cmd = try { JSONObject(line) } catch (e: Exception) { return@forEachLine }
                main.post { onCommand(cmd) { send(s, it) } }
            }
        } catch (_: Exception) {
        }
        drop(s)
    }

    private fun send(s: Socket, msg: JSONObject) {
        val bytes = (msg.toString() + "\n").toByteArray()
        writer.execute {
            try { s.getOutputStream().apply { write(bytes); flush() } } catch (e: Exception) { drop(s) }
        }
    }

    fun broadcast(msg: JSONObject) = clients.forEach { send(it, msg) }

    private fun drop(s: Socket) {
        clients -= s
        try { s.close() } catch (_: Exception) {}
    }

    fun stop() {
        try { server.close() } catch (_: Exception) {}
        clients.forEach(::drop)
        writer.shutdownNow()
    }
}

/** Process-wide remote: stays up while browsing; the foreground player installs [handler]. Main thread only. */
object Remote {
    private var server: ControlServer? = null
    private val main = Handler(Looper.getMainLooper())
    var handler: ((cmd: JSONObject, reply: (JSONObject) -> Unit) -> Unit)? = null
    val idle get() = JSONObject().put("type", "idle")
    private val idleTick = object : Runnable {
        override fun run() {
            if (handler == null) broadcast(idle)
            main.postDelayed(this, 2_000)
        }
    }

    fun start() {
        if (server != null) return
        server = try {
            ControlServer(8765, { send -> if (handler == null) send(idle) }) { cmd, reply ->
                val h = handler
                when {
                    // The phone asks once per title and caches the JPEG itself; works while browsing too.
                    cmd.optString("cmd") == "art" -> Catalog.movies.firstOrNull { it.id == cmd.optString("id") }?.let { m ->
                        Thumbs.bytes(m.thumbUrl) { jpeg ->
                            if (jpeg != null) reply(JSONObject().put("type", "art").put("id", m.id)
                                .put("jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP)))
                        }
                    }
                    h != null -> h(cmd, reply)
                    cmd.optString("cmd") == "catchUp" -> reply(JSONObject().put("type", "recap").put("error", "Nothing is playing on the TV."))
                }
            }
        } catch (e: Exception) { Log.e("Scrubber", "server", e); return }
        main.post(idleTick)
    }

    fun broadcast(msg: JSONObject) { server?.broadcast(msg) }
}
