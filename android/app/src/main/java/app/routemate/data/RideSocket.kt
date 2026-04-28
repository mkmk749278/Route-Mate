package app.routemate.data

import app.routemate.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val socket: WebSocket,
    private val json: Json,
) {
    private val _events = MutableSharedFlow<WsEnvelope>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

    internal fun emit(env: WsEnvelope) { _events.tryEmit(env) }

    fun sendChat(body: String) {
        socket.send(json.encodeToString(WsEnvelope.serializer(), WsEnvelope(type = "chat", body = body)))
    }

    fun sendLocation(lat: Double, lng: Double, ts: Long) {
        socket.send(
            json.encodeToString(
                WsEnvelope.serializer(),
                WsEnvelope(type = "location", lat = lat, lng = lng, ts = ts),
            )
        )
    }

    fun close() { socket.cancel() }
}

@Singleton
class RideSocket @Inject constructor(
    private val client: OkHttpClient,
    private val store: AuthStore,
    private val json: Json,
) {
    suspend fun connect(rideId: String): RideConnection {
        val token = store.token() ?: error("not signed in")
        val wsBase = BuildConfig.API_BASE_URL.removeSuffix("/")
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/v1/ws/ride/$rideId?token=$token"

        lateinit var conn: RideConnection
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(ws: WebSocket, text: String) {
                    runCatching { json.decodeFromString<WsEnvelope>(text) }
                        .onSuccess { conn.emit(it) }
                }
                override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {}
                override fun onClosed(ws: WebSocket, code: Int, reason: String) {}
            }
        )
        conn = RideConnection(socket, json)
        return conn
    }
}
