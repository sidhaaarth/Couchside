package dev.sidk.scrubber.phone

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** Line-delimited JSON over TCP to the TV app. Callbacks run on the main thread. */
class TvLink(
    private val host: String,
    private val onMessage: (JSONObject) -> Unit,
    private val onConnect: () -> Unit,
    private val onDisconnect: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var socket: Socket? = null
    @Volatile private var running = true

    private val thread = Thread {
        while (running) {
            try {
                Socket().use { s ->
                    socket = s
                    s.connect(InetSocketAddress(host, 8765), 3000)
                    main.post { onConnect() }
                    s.getInputStream().bufferedReader().forEachLine { line ->
                        runCatching { JSONObject(line) }.onSuccess { main.post { onMessage(it) } }
                    }
                }
            } catch (_: Exception) {
            }
            socket = null
            main.post { onDisconnect() }
            try { Thread.sleep(2000) } catch (_: InterruptedException) {}
        }
    }.apply { isDaemon = true; start() }

    fun send(msg: JSONObject) = writer.execute {
        try {
            socket?.takeIf { it.isConnected }?.getOutputStream()?.write("$msg\n".toByteArray())
        } catch (_: Exception) {
            reconnect()
        }
    }

    fun reconnect() {
        runCatching { socket?.close() }
    }

    fun stop() {
        running = false
        reconnect()
        thread.interrupt()
        writer.shutdownNow()
    }
}
