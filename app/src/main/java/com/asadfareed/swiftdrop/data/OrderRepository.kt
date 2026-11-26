package com.asadfareed.swiftdrop.data

import android.content.Context
import com.asadfareed.swiftdrop.core.Backoff
import com.asadfareed.swiftdrop.core.CartLine
import com.asadfareed.swiftdrop.core.DemoCity
import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.core.PriceBreakdown
import com.asadfareed.swiftdrop.core.Pricing
import com.asadfareed.swiftdrop.core.Route
import com.asadfareed.swiftdrop.core.TrackingState
import com.asadfareed.swiftdrop.data.tracking.OkHttpTrackingSocket
import com.asadfareed.swiftdrop.data.tracking.SimulatedTrackingSocket
import com.asadfareed.swiftdrop.data.tracking.SocketEvent
import com.asadfareed.swiftdrop.data.tracking.TrackingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Order(
    val id: String,
    val restaurant: Restaurant,
    val lines: List<CartLine>,
    val price: PriceBreakdown,
    val address: DeliveryAddress,
    val placedAt: Long,
) {
    /** Restaurant → customer route, used for the map and delivery fee. */
    val route: Route get() = DemoCity.map.shortestPath(restaurant.nodeId, address.nodeId)!!
}

sealed interface Connection {
    data object Connecting : Connection
    data class Live(val viaServer: Boolean) : Connection
    data class Reconnecting(val inMillis: Long, val attempt: Int) : Connection
    data object Finished : Connection
}

data class LiveTracking(val tracking: TrackingState, val connection: Connection)

/** Simple persisted switch between the offline simulator and a real tracking server. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _useServer = MutableStateFlow(prefs.getBoolean("use_server", false))
    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", DEFAULT_URL) ?: DEFAULT_URL)
    val useServer: StateFlow<Boolean> = _useServer.asStateFlow()
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun setUseServer(value: Boolean) {
        prefs.edit().putBoolean("use_server", value).apply()
        _useServer.value = value
    }

    fun setServerUrl(value: String) {
        prefs.edit().putString("server_url", value).apply()
        _serverUrl.value = value
    }

    companion object {
        /** Emulator alias for the host machine running `npm start` in /server. */
        const val DEFAULT_URL = "ws://10.0.2.2:8080"
    }
}

@Singleton
class OrderRepository @Inject constructor(
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSockets are long-lived
        .build()
    private val serverSocket = OkHttpTrackingSocket(client) { settings.serverUrl.value }
    private val simulatedSocket = SimulatedTrackingSocket()

    // ---- Cart -------------------------------------------------------------

    private val _cart = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cart: StateFlow<Map<String, Int>> = _cart.asStateFlow()

    /** A cart belongs to a single restaurant; adding from another one starts a new cart. */
    fun add(itemId: String) = _cart.update { current ->
        val (restaurant, _) = Catalog.item(itemId)
        val sameRestaurant = current.keys.all { Catalog.item(it).first.id == restaurant.id }
        val base = if (sameRestaurant) current else emptyMap()
        base + (itemId to (base[itemId] ?: 0) + 1)
    }

    fun remove(itemId: String) = _cart.update { current ->
        val qty = (current[itemId] ?: return@update current) - 1
        if (qty <= 0) current - itemId else current + (itemId to qty)
    }

    fun cartLines(cart: Map<String, Int> = _cart.value): List<CartLine> = cart.map { (id, qty) ->
        val (_, item) = Catalog.item(id)
        CartLine(id, item.name, item.price, qty)
    }

    fun cartRestaurant(cart: Map<String, Int> = _cart.value): Restaurant? =
        cart.keys.firstOrNull()?.let { Catalog.item(it).first }

    fun quote(cart: Map<String, Int>, promo: String?, tip: Long): PriceBreakdown? {
        val restaurant = cartRestaurant(cart) ?: return null
        val distance = DemoCity.map.shortestPath(restaurant.nodeId, Catalog.home.nodeId)?.length ?: 0.0
        return Pricing.quote(cartLines(cart), distance, promo, tip)
    }

    // ---- Orders -----------------------------------------------------------

    private val _orders = MutableStateFlow(seedHistory())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val trackers = HashMap<String, StateFlow<LiveTracking>>()
    private val finalStates = MutableStateFlow<Map<String, OrderStatus>>(seedStatuses())
    val statuses: StateFlow<Map<String, OrderStatus>> = finalStates.asStateFlow()

    fun placeOrder(promo: String?, tip: Long): Order? {
        val cart = _cart.value
        val restaurant = cartRestaurant(cart) ?: return null
        val price = quote(cart, promo, tip) ?: return null
        val order = Order(
            id = "SD-" + (System.currentTimeMillis() % 100_000).toString().padStart(5, '0'),
            restaurant = restaurant,
            lines = cartLines(cart),
            price = price,
            address = Catalog.home,
            placedAt = System.currentTimeMillis(),
        )
        _orders.update { listOf(order) + it }
        _cart.value = emptyMap()
        return order
    }

    fun order(id: String): Order? = _orders.value.firstOrNull { it.id == id }

    /**
     * Live tracking with automatic reconnection. Each (re)connection replays into the
     * same [TrackingState] reducer, which ignores duplicates and out-of-order messages,
     * so a reconnect never moves the UI backwards. Shared, so screens can come and go.
     */
    fun track(order: Order): StateFlow<LiveTracking> = synchronized(trackers) {
        trackers.getOrPut(order.id) {
            val initial = TrackingState(timeline = mapOf(OrderStatus.PLACED to order.placedAt))
            channelFlow {
                val backoff = Backoff()
                var state = initial
                var attempt = 0
                val request = TrackingRequest(order.id, order.restaurant.nodeId, order.address.nodeId)
                while (!state.status.isTerminal) {
                    val viaServer = settings.useServer.value
                    send(LiveTracking(state, Connection.Connecting))
                    val socket = if (viaServer) serverSocket else simulatedSocket
                    socket.connect(request).collect { event ->
                        when (event) {
                            SocketEvent.Connected -> {
                                attempt = 0
                                send(LiveTracking(state, Connection.Live(viaServer)))
                            }
                            is SocketEvent.Message -> {
                                state = state.reduce(event.event)
                                send(LiveTracking(state, Connection.Live(viaServer)))
                            }
                            is SocketEvent.Closed -> Unit
                        }
                    }
                    if (state.status.isTerminal) break
                    val wait = backoff.delayFor(attempt++)
                    send(LiveTracking(state, Connection.Reconnecting(wait, attempt)))
                    delay(wait)
                }
                finalStates.update { it + (order.id to state.status) }
                send(LiveTracking(state, Connection.Finished))
            }.stateIn(scope, SharingStarted.Eagerly, LiveTracking(initial, Connection.Connecting))
        }
    }

    private fun seedHistory(): List<Order> {
        val now = System.currentTimeMillis()
        fun past(restaurantId: String, items: List<Pair<String, Int>>, daysAgo: Int, id: String): Order {
            val r = Catalog.restaurant(restaurantId)
            val lines = items.map { (itemId, q) -> val (_, i) = Catalog.item(itemId); CartLine(itemId, i.name, i.price, q) }
            val dist = DemoCity.map.shortestPath(r.nodeId, Catalog.home.nodeId)!!.length
            return Order(id, r, lines, Pricing.quote(lines, dist), Catalog.home, now - daysAgo * 86_400_000L)
        }
        return listOf(
            past("saffron", listOf("s1" to 2, "s3" to 2), 1, "SD-48213"),
            past("nonna", listOf("n2" to 1, "n3" to 1), 4, "SD-47790"),
        )
    }

    private fun seedStatuses() = mapOf("SD-48213" to OrderStatus.DELIVERED, "SD-47790" to OrderStatus.DELIVERED)
}
