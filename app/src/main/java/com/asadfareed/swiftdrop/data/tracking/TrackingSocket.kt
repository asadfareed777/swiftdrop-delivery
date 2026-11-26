package com.asadfareed.swiftdrop.data.tracking

import com.asadfareed.swiftdrop.core.CourierSimulator
import com.asadfareed.swiftdrop.core.DemoCity
import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.core.TrackingEvent
import com.asadfareed.swiftdrop.data.Catalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What a tracking connection reports to the repository. */
sealed interface SocketEvent {
    data object Connected : SocketEvent
    data class Message(val event: TrackingEvent) : SocketEvent
    data class Closed(val reason: String, val error: Boolean) : SocketEvent
}

data class TrackingRequest(val orderId: String, val restaurantNode: String, val homeNode: String)

/** Streams live updates for one order. The flow completes when the connection ends. */
interface TrackingSocket {
    fun connect(request: TrackingRequest): Flow<SocketEvent>
}

/** Wire format shared with server/tracking-server.js. Unknown fields are ignored for forward compatibility. */
@Serializable
data class TrackingMessage(
    val type: String,
    val at: Long,
    val status: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val bearing: Double? = null,
    val seconds: Int? = null,
)

object TrackingProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null for malformed or unknown messages instead of crashing the stream. */
    fun decode(text: String): TrackingEvent? {
        val m = runCatching { json.decodeFromString<TrackingMessage>(text) }.getOrNull() ?: return null
        return when (m.type) {
            "status" -> m.status
                ?.let { s -> OrderStatus.entries.firstOrNull { it.name == s } }
                ?.let { TrackingEvent.StatusChanged(it, m.at) }
            "location" -> if (m.x != null && m.y != null) TrackingEvent.CourierMoved(m.x, m.y, m.bearing ?: 0.0, m.at) else null
            "eta" -> m.seconds?.let { TrackingEvent.EtaUpdated(it, m.at) }
            else -> null
        }
    }
}

/** Real WebSocket client (OkHttp). OkHttp handles ping/pong keep-alives on its own thread. */
class OkHttpTrackingSocket(
    private val client: OkHttpClient,
    private val baseUrl: () -> String,
) : TrackingSocket {

    override fun connect(request: TrackingRequest): Flow<SocketEvent> = callbackFlow {
        val url = "${baseUrl().trimEnd('/')}/orders/${request.orderId}" +
            "?restaurant=${request.restaurantNode}&home=${request.homeNode}"
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trySend(SocketEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    TrackingProtocol.decode(text)?.let { trySend(SocketEvent.Message(it)) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    trySend(SocketEvent.Closed(reason.ifBlank { "Closed ($code)" }, error = code != 1000))
                    close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    trySend(SocketEvent.Closed(t.message ?: "Connection failed", error = true))
                    close()
                }
            },
        )
        awaitClose { socket.close(1000, "Client left") }
    }
}

/**
 * Offline demo mode: replays [CourierSimulator] with real-time delays, through exactly
 * the same [SocketEvent] interface, so the UI can't tell the difference.
 */
class SimulatedTrackingSocket(
    private val now: () -> Long = System::currentTimeMillis,
) : TrackingSocket {

    override fun connect(request: TrackingRequest): Flow<SocketEvent> = flow {
        delay(400) // handshake
        emit(SocketEvent.Connected)
        val start = now()
        val script = CourierSimulator(DemoCity.map)
            .script(Catalog.COURIER_START, request.restaurantNode, request.homeNode, startAt = start)
        var elapsed = 0L
        for (timed in script) {
            if (timed.offsetMillis > elapsed) {
                delay(timed.offsetMillis - elapsed)
                elapsed = timed.offsetMillis
            }
            emit(SocketEvent.Message(timed.event))
        }
        emit(SocketEvent.Closed("Order complete", error = false))
    }
}
