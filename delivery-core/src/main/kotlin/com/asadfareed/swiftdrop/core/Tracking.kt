package com.asadfareed.swiftdrop.core

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Order lifecycle, in the only order it may advance. */
enum class OrderStatus(val label: String) {
    PLACED("Order placed"),
    ACCEPTED("Restaurant accepted"),
    PREPARING("Preparing your food"),
    READY_FOR_PICKUP("Ready for pickup"),
    PICKED_UP("Picked up"),
    ARRIVING("Arriving"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    val isTerminal: Boolean get() = this == DELIVERED || this == CANCELLED
}

/** Messages pushed by the tracking service. `at` is epoch millis on the server clock. */
sealed interface TrackingEvent {
    val at: Long

    data class StatusChanged(val status: OrderStatus, override val at: Long) : TrackingEvent
    data class CourierMoved(val x: Double, val y: Double, val bearing: Double, override val at: Long) : TrackingEvent
    data class EtaUpdated(val seconds: Int, override val at: Long) : TrackingEvent
}

data class TrackingState(
    val status: OrderStatus = OrderStatus.PLACED,
    val timeline: Map<OrderStatus, Long> = emptyMap(),
    val courier: CourierPosition? = null,
    val courierAt: Long = Long.MIN_VALUE,
    val etaSeconds: Int? = null,
) {
    /**
     * Pure reducer. Network messages can arrive late, twice or out of order, so:
     * statuses only move forward, stale locations are dropped, and nothing changes
     * after a terminal status.
     */
    fun reduce(event: TrackingEvent): TrackingState {
        if (status.isTerminal) return this
        return when (event) {
            is TrackingEvent.StatusChanged -> {
                val next = event.status
                val allowed = when (next) {
                    OrderStatus.CANCELLED -> status < OrderStatus.PICKED_UP
                    else -> next > status
                }
                if (!allowed) this
                else copy(
                    status = next,
                    timeline = timeline + (next to event.at),
                    etaSeconds = if (next.isTerminal) 0 else etaSeconds,
                )
            }
            is TrackingEvent.CourierMoved ->
                if (event.at < courierAt) this
                else copy(courier = CourierPosition(Point(event.x, event.y), event.bearing), courierAt = event.at)
            is TrackingEvent.EtaUpdated -> copy(etaSeconds = event.seconds.coerceAtLeast(0))
        }
    }

    /** 0..1 progress for the status timeline UI. */
    val progress: Float
        get() = if (status == OrderStatus.CANCELLED) 0f
        else status.ordinal / OrderStatus.DELIVERED.ordinal.toFloat()
}

/** ETA = remaining prep time (if not collected yet) + travel time at the courier's speed. */
object Eta {
    fun seconds(remainingMeters: Double, speedMps: Double, remainingPrepSeconds: Double = 0.0): Int {
        require(speedMps > 0)
        return (remainingPrepSeconds.coerceAtLeast(0.0) + remainingMeters / speedMps).toInt()
    }

    fun label(seconds: Int): String = when {
        seconds <= 45 -> "Any moment"
        seconds < 60 * 60 -> "${(seconds + 59) / 60} min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }
}

/**
 * Exponential reconnect backoff with jitter (1 s, 2 s, 4 s … capped at 30 s, ±20 %).
 * Jitter spreads reconnects so thousands of clients don't hammer the server at once.
 */
class Backoff(
    private val baseMillis: Long = 1_000,
    private val maxMillis: Long = 30_000,
    private val factor: Double = 2.0,
    private val jitter: Double = 0.2,
    private val random: Random = Random.Default,
) {
    fun delayFor(attempt: Int): Long {
        val raw = min(maxMillis.toDouble(), baseMillis * factor.pow(attempt.coerceAtLeast(0)))
        val spread = raw * jitter
        return (raw - spread + random.nextDouble() * 2 * spread).toLong().coerceAtLeast(0)
    }
}
