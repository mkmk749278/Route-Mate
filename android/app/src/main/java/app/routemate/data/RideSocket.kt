package app.routemate.data

import android.util.Log
import app.routemate.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
data class WsEnvelope(
    val type: String,
    val id: String? = null,
    val from: String? = null,
    val body: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val ts: Long? = null,
    val at: String? = null,
)

class RideConnection internal constructor(
    private val json: Json,
    private val socketRef: () -> WebSocket?,
    private val onClose: () -> Unit,
) {
    private val _events = MutableSharedFlow<WsEnvelope>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

    internal fun emit(env: WsEnvelope) { _events.tryEmit(env) }

    fun sendChat(body: String) {
        val payload = json.encodeToString(WsEnvelope.serializer(), WsEnvelope(type = "chat", body = body))
        socketRef()?.send(payload)
    }

    fun sendLocation(lat: Double, lng: Double, ts: Long) {
        val payload = json.encodeToString(
            WsEnvelope.serializer(),
            WsEnvelope(type = "location", lat = lat, lng = lng, ts = ts),
        )
        socketRef()?.send(payload)
    }

    fun close() { onClose() }
}

@Singleton
class RideSocket @Inject constructor(
    private val client: OkHttpClient,
    private val store: AuthStore,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Returns a RideConnection backed by a single logical socket that
     * auto-reconnects with exponential backoff (1s → 30s, +/- 25% jitter)
     * until close() is called. Each successful reconnect re-uses the same
     * RideConnection so subscribers don't see flow interruption.
     */
    suspend fun connect(rideId: String): RideConnection {
        val token = store.token() ?: error("not signed in")
        val wsBase = BuildConfig.API_BASE_URL.removeSuffix("/")
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/v1/ws/ride/$rideId?token=$token"

        @Volatile var current: WebSocket? = null
        val cancelled = AtomicBoolean(false)
        lateinit var conn: RideConnection

        val loop: Job = scope.launch {
            var delayMs = 1_000L
            while (!cancelled.get()) {
                val ws = client.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) {
                            delayMs = 1_000L
                        }
                        override fun onMessage(ws: WebSocket, text: String) {
                            runCatching { json.decodeFromString<WsEnvelope>(text) }
                                .onSuccess { conn.emit(it) }
                        }
                        override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                            Log.w("RideSocket", "ws failure: ${t.message}")
                            if (current === ws) current = null
                        }
                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            Log.i("RideSocket", "ws closed: $code $reason")
                            if (current === ws) current = null
                        }
                        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                            if (current === ws) current = null
                        }
                    }
                )
                current = ws

                // Wait for the listener to null out `current` on close /
                // failure. Polling beats the no-future shape of OkHttp WS.
                while (!cancelled.get() && current === ws) {
                    delay(500)
                }
                if (cancelled.get()) break

                val jitter = (delayMs * (0.75 + Random.nextDouble() * 0.5)).toLong()
                delay(jitter)
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }

        conn = RideConnection(
            json = json,
            socketRef = { current },
            onClose = {
                cancelled.set(true)
                current?.cancel()
                current = null
                loop.cancel()
            },
        )
        return conn
    }
}
