package com.asadfareed.swiftdrop.core

data class TimedEvent(val offsetMillis: Long, val event: TrackingEvent)

/**
 * Produces a realistic, deterministic stream of tracking events for one order:
 * restaurant accepts → prepares while the courier rides over → pickup → delivery
 * with a location fix every [tickMillis]. Used by the in-app demo mode and by tests;
 * the Node server in /server implements the same protocol over a real WebSocket.
 */
class CourierSimulator(
    private val city: CityMap,
    private val speedMps: Double = 9.0,
    private val tickMillis: Long = 1_000,
    private val prepSeconds: Int = 18,
    private val arrivingWithinMeters: Double = 180.0,
) {

    fun script(courierStartId: String, restaurantId: String, homeId: String, startAt: Long = 0): List<TimedEvent> {
        val toRestaurant = city.shortestPath(courierStartId, restaurantId) ?: Route(listOf(city.node(restaurantId).position))
        val toHome = requireNotNull(city.shortestPath(restaurantId, homeId)) { "Home unreachable" }
        val events = mutableListOf<TimedEvent>()
        fun add(offset: Long, make: (Long) -> TrackingEvent) = events.add(TimedEvent(offset, make(startAt + offset)))

        add(1_500) { TrackingEvent.StatusChanged(OrderStatus.ACCEPTED, it) }
        add(3_000) { TrackingEvent.StatusChanged(OrderStatus.PREPARING, it) }

        val readyAt = 3_000L + prepSeconds * 1_000L
        var t = 3_000L
        var travelled = 0.0
        var readySent = false
        // Leg 1: courier rides to the restaurant while the food is prepared.
        while (true) {
            if (!readySent && t >= readyAt) {
                add(readyAt) { TrackingEvent.StatusChanged(OrderStatus.READY_FOR_PICKUP, it) }
                readySent = true
            }
            val pos = toRestaurant.positionAt(travelled)
            add(t) { TrackingEvent.CourierMoved(pos.point.x, pos.point.y, pos.bearing, it) }
            val prepLeft = ((readyAt - t) / 1000.0).coerceAtLeast(0.0)
            val rideLeft = (toRestaurant.length - travelled) / speedMps
            add(t) { TrackingEvent.EtaUpdated(Eta.seconds(toHome.length, speedMps, maxOf(prepLeft, rideLeft)), it) }
            if (travelled >= toRestaurant.length && readySent) break
            t += tickMillis
            travelled = (travelled + speedMps * tickMillis / 1000.0).coerceAtMost(toRestaurant.length)
        }
        t += tickMillis
        add(t) { TrackingEvent.StatusChanged(OrderStatus.PICKED_UP, it) }

        // Leg 2: restaurant → customer.
        travelled = 0.0
        var arrivingSent = false
        while (true) {
            t += tickMillis
            travelled = (travelled + speedMps * tickMillis / 1000.0).coerceAtMost(toHome.length)
            val pos = toHome.positionAt(travelled)
            add(t) { TrackingEvent.CourierMoved(pos.point.x, pos.point.y, pos.bearing, it) }
            val remaining = toHome.length - travelled
            add(t) { TrackingEvent.EtaUpdated(Eta.seconds(remaining, speedMps), it) }
            if (!arrivingSent && remaining <= arrivingWithinMeters) {
                arrivingSent = true
                add(t) { TrackingEvent.StatusChanged(OrderStatus.ARRIVING, it) }
            }
            if (remaining <= 0.0) break
        }
        add(t + 2_000) { TrackingEvent.StatusChanged(OrderStatus.DELIVERED, it) }
        return events.sortedBy { it.offsetMillis }
    }
}
