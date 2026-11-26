package com.asadfareed.swiftdrop.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asadfareed.swiftdrop.core.CartLine
import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.core.PriceBreakdown
import com.asadfareed.swiftdrop.data.Catalog
import com.asadfareed.swiftdrop.data.LiveTracking
import com.asadfareed.swiftdrop.data.Order
import com.asadfareed.swiftdrop.data.OrderRepository
import com.asadfareed.swiftdrop.data.Restaurant
import com.asadfareed.swiftdrop.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

fun Long.rupees(): String = "Rs " + NumberFormat.getIntegerInstance(Locale.US).format(this)

data class CartSummary(
    val restaurant: Restaurant?,
    val lines: List<CartLine>,
    val count: Int,
    val subtotal: Long,
)

/** Activity-scoped: browsing, cart and checkout share one instance. */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: OrderRepository,
) : ViewModel() {

    private val _category = MutableStateFlow("All")
    val category: StateFlow<String> = _category.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val restaurants: StateFlow<List<Restaurant>> = combine(_category, _query) { cat, q ->
        Catalog.restaurants.filter { r ->
            (cat == "All" || r.category == cat) &&
                (q.isBlank() || r.name.contains(q, true) || r.cuisine.contains(q, true) ||
                    r.menu.any { s -> s.items.any { it.name.contains(q, true) } })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Catalog.restaurants)

    val cart: StateFlow<Map<String, Int>> = repository.cart

    val summary: StateFlow<CartSummary> = repository.cart.map { cart ->
        val lines = repository.cartLines(cart)
        CartSummary(repository.cartRestaurant(cart), lines, lines.sumOf { it.quantity }, lines.sumOf { it.total })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartSummary(null, emptyList(), 0, 0))

    // Checkout inputs
    private val _promoInput = MutableStateFlow("")
    val promoInput: StateFlow<String> = _promoInput.asStateFlow()
    private val appliedPromo = MutableStateFlow<String?>(null)
    private val _tip = MutableStateFlow(50L)
    val tip: StateFlow<Long> = _tip.asStateFlow()

    val quote: StateFlow<PriceBreakdown?> = combine(repository.cart, appliedPromo, _tip) { cart, promo, tip ->
        repository.quote(cart, promo, tip)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val orders: StateFlow<List<Order>> = repository.orders
    val statuses: StateFlow<Map<String, OrderStatus>> = repository.statuses

    fun setCategory(c: String) { _category.value = c }
    fun setQuery(q: String) { _query.value = q }
    fun add(itemId: String) = repository.add(itemId)
    fun remove(itemId: String) = repository.remove(itemId)
    fun setPromoInput(v: String) { _promoInput.value = v }
    fun applyPromo() { appliedPromo.value = _promoInput.value.ifBlank { null } }
    fun setTip(v: Long) { _tip.value = v }

    /** Returns the new order id, or null if the cart was empty. */
    fun placeOrder(): String? {
        val order = repository.placeOrder(appliedPromo.value, _tip.value) ?: return null
        appliedPromo.value = null
        _promoInput.value = ""
        repository.track(order) // start streaming immediately
        return order.id
    }
}

@HiltViewModel
class TrackingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: OrderRepository,
) : ViewModel() {
    val order: Order? = savedState.get<String>("orderId")?.let(repository::order)
    val live: StateFlow<LiveTracking>? = order?.let(repository::track)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val useServer: StateFlow<Boolean> = settings.useServer
    val serverUrl: StateFlow<String> = settings.serverUrl
    fun setUseServer(v: Boolean) = settings.setUseServer(v)
    fun setServerUrl(v: String) = settings.setServerUrl(v)
}
