package `in`.voltkit.livedemo.kotlin.data

import android.os.Handler
import android.os.Looper
import `in`.voltkit.livedemo.kotlin.Config
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The backend's own live connection, for what happens outside any room: the 1v1 line, room list updates and inbox notices.
 * Everything inside a room goes over VoltKit instead.
 *
 * The backend runs Socket.IO 4. This speaks its protocol (Engine.IO 4 over a WebSocket) directly on OkHttp, the SDK's own HTTP
 * client: what the app needs is small, and a second client library would bring a second, older OkHttp.
 */
object LiveSocket {
    private val main = Handler(Looper.getMainLooper())
    private val handlers = mutableMapOf<String, MutableSet<(Any?) -> Unit>>()
    private var socket: WebSocket? = null
    private var token: String? = null
    private var attempt = 0
    private var generation = 0
    private var pingTimeoutMs = 45_000L
    private val watchdog = Runnable { socket?.cancel() }

    fun connect(token: String) {
        this.token = token
        attempt = 0
        open()
    }

    fun disconnect() {
        token = null
        generation++
        main.removeCallbacksAndMessages(null)
        socket?.close(1000, null)
        socket = null
    }

    /** Listens until the returned function is called, across reconnects and a new session. */
    fun on(event: String, handler: (Any?) -> Unit): () -> Unit {
        handlers.getOrPut(event) { mutableSetOf() }.add(handler)
        return { handlers[event]?.remove(handler) }
    }

    fun emit(event: String, data: Any? = null) {
        val packet = JSONArray().put(event).apply { if (data != null) put(data) }
        socket?.send("42$packet")
    }

    private fun dispatch(event: String, payload: Any?) {
        main.post { handlers[event]?.toList()?.forEach { it(payload) } }
    }

    private fun open() {
        val token = token ?: return
        val mine = ++generation
        socket?.cancel()
        val url = Config.apiUrl.replaceFirst("http", "ws") + "/socket.io/?EIO=4&transport=websocket"
        socket = Api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (mine != generation) return
                when (text.firstOrNull()) {
                    '0' -> { // Engine.IO open: say who we are
                        val open = JSONObject(text.substring(1))
                        pingTimeoutMs = open.optLong("pingInterval", 25_000) + open.optLong("pingTimeout", 20_000)
                        armWatchdog()
                        webSocket.send("40" + JSONObject().put("token", token))
                    }
                    '2' -> { // ping
                        armWatchdog()
                        webSocket.send("3")
                    }
                    '4' -> onPacket(text.substring(1))
                    '1' -> webSocket.cancel()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (mine == generation) retry()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (mine == generation) retry()
            }
        })
    }

    private fun onPacket(packet: String) {
        when (packet.firstOrNull()) {
            '0' -> { // connected
                attempt = 0
                dispatch("connect", null)
            }
            '2' -> { // an event: ["name", payload]
                val array = JSONTokener(packet.substring(1)).nextValue() as? JSONArray ?: return
                dispatch(array.optString(0), array.opt(1))
            }
            '4' -> dispatch("connect_error", packet.substring(1))
            '1' -> socket?.cancel()
        }
    }

    private fun armWatchdog() {
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, pingTimeoutMs)
    }

    private fun retry() {
        main.removeCallbacks(watchdog)
        if (token == null) return
        val delay = minOf(8_000L, 500L shl minOf(attempt++, 4))
        val mine = generation
        main.postDelayed({ if (mine == generation) open() }, delay)
    }
}
