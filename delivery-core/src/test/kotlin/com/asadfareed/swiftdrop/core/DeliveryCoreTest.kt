package com.asadfareed.swiftdrop.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class CityRoutingTest {

    private val city = DemoCity.map
    private fun id(r: Int, c: Int) = DemoCity.id(r, c)

    @Test
    fun `every street corner can reach every other`() {
        val ids = city.nodes.keys.toList()
        ids.forEach { assertThat(city.shortestPath(ids.first(), it)).isNotNull() }
    }

    @Test
    fun `straight run along a street is the block distance`() {
        val route = city.shortestPath(id(0, 0), id(0, 4))!!
        assertThat(route.length).isEqualTo(600.0)
    }

    @Test
    fun `diagonal boulevard is used when it is shorter`() {
        val route = city.shortestPath(id(12, 0), id(8, 4))!!
        assertThat(abs(route.length - 4 * sqrt(2.0) * 150) < 1e-6).isTrue()
    }

    @Test
    fun `river can only be crossed on a bridge`() {
        val route = city.shortestPath(id(5, 4), id(8, 4))!!
        val crossing = route.points.zipWithNext().first { (a, b) -> a.y < 6.5 * 150 && b.y > 6.5 * 150 }
        assertThat(crossing.first.x == 300.0 || crossing.first.x == 900.0).isTrue()
        assertThat(route.length).isEqualTo(450.0 + 2 * 300.0)
    }

    @Test
    fun `park interior has no streets`() {
        assertThat(city.nodes.containsKey(id(3, 4))).isFalse()
        val route = city.shortestPath(id(2, 4), id(4, 4))!!
        assertThat(route.length).isEqualTo(600.0)
    }

    @Test
    fun `positions are interpolated with compass bearings`() {
        val route = Route(listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0)))
        val east = route.positionAt(50.0)
        val south = route.positionAt(150.0)
        assertThat(east.point).isEqualTo(Point(50.0, 0.0))
        assertThat(east.bearing).isEqualTo(90.0)
        assertThat(south.point).isEqualTo(Point(100.0, 50.0))
        assertThat(south.bearing).isEqualTo(180.0)
        assertThat(route.positionAt(999.0).point).isEqualTo(Point(100.0, 100.0))
        assertThat(Route.bearing(Point(0.0, 0.0), Point(0.0, -10.0))).isEqualTo(0.0)
    }

    @Test
    fun `remaining distance is measured from the nearest point on the route`() {
        val route = Route(listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0)))
        assertThat(route.remainingFrom(Point(40.0, 3.0))).isEqualTo(160.0)
        assertThat(route.remainingFrom(Point(100.0, 100.0))).isEqualTo(0.0)
    }
}

class TrackingStateTest {

    private fun status(s: OrderStatus, at: Long) = TrackingEvent.StatusChanged(s, at)

    @Test
    fun `statuses only move forward and duplicates are ignored`() {
        val state = TrackingState()
            .reduce(status(OrderStatus.PREPARING, 10))
            .reduce(status(OrderStatus.ACCEPTED, 11))   // late, out of order
            .reduce(status(OrderStatus.PREPARING, 12))  // duplicate
        assertThat(state.status).isEqualTo(OrderStatus.PREPARING)
        assertThat(state.timeline).isEqualTo(mapOf(OrderStatus.PREPARING to 10L))
    }

    @Test
    fun `stale courier fixes are dropped`() {
        val state = TrackingState()
            .reduce(TrackingEvent.CourierMoved(10.0, 0.0, 90.0, at = 2_000))
            .reduce(TrackingEvent.CourierMoved(5.0, 0.0, 90.0, at = 1_000))
        assertThat(state.courier?.point).isEqualTo(Point(10.0, 0.0))
    }

    @Test
    fun `cancel is allowed before pickup but not after`() {
        val early = TrackingState().reduce(status(OrderStatus.PREPARING, 1)).reduce(status(OrderStatus.CANCELLED, 2))
        assertThat(early.status).isEqualTo(OrderStatus.CANCELLED)

        val late = TrackingState().reduce(status(OrderStatus.PICKED_UP, 1)).reduce(status(OrderStatus.CANCELLED, 2))
        assertThat(late.status).isEqualTo(OrderStatus.PICKED_UP)
    }

    @Test
    fun `nothing changes after delivery`() {
        val done = TrackingState().reduce(status(OrderStatus.DELIVERED, 1))
        val after = done.reduce(TrackingEvent.CourierMoved(1.0, 1.0, 0.0, 5)).reduce(TrackingEvent.EtaUpdated(99, 6))
        assertThat(after).isEqualTo(done)
        assertThat(done.etaSeconds).isEqualTo(0)
        assertThat(done.progress).isEqualTo(1f)
    }

    @Test
    fun `eta labels read naturally`() {
        assertThat(Eta.label(30)).isEqualTo("Any moment")
        assertThat(Eta.label(61)).isEqualTo("2 min")
        assertThat(Eta.label(3_900)).isEqualTo("1 h 5 min")
        assertThat(Eta.seconds(900.0, 9.0, remainingPrepSeconds = 20.0)).isEqualTo(120)
    }
}

class BackoffTest {

    @Test
    fun `delays grow exponentially and are capped`() {
        val noJitter = Backoff(jitter = 0.0)
        assertThat((0..6).map(noJitter::delayFor)).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L).inOrder()
    }

    @Test
    fun `jitter stays within twenty percent and is reproducible`() {
        val a = Backoff(random = Random(42))
        val b = Backoff(random = Random(42))
        repeat(50) { attempt ->
            val d = a.delayFor(attempt % 6)
            assertThat(d).isEqualTo(b.delayFor(attempt % 6))
            val nominal = minOf(30_000.0, 1_000.0 * (1 shl (attempt % 6)))
            assertThat(d >= (nominal * 0.8).toLong() && d <= (nominal * 1.2).toLong()).isTrue()
        }
    }
}

class PricingTest {

    private val burger = CartLine("b1", "Smash burger", 890, 2)
    private val fries = CartLine("f1", "Fries", 290, 1)

    @Test
    fun `fees depend on distance and subtotal`() {
        val q = Pricing.quote(listOf(burger, fries), distanceMeters = 2_400.0)
        assertThat(q.subtotal).isEqualTo(2_070L)
        assertThat(q.deliveryFee).isEqualTo(185L) // 99 + 2.4 km × 35 = 183 → rounded to 5
        assertThat(q.serviceFee).isEqualTo(104L)
        assertThat(q.smallOrderFee).isEqualTo(0L)
        assertThat(q.total).isEqualTo(2_070L + 185 + 104)
    }

    @Test
    fun `large orders get free delivery and the service fee is capped`() {
        val q = Pricing.quote(listOf(CartLine("x", "Platter", 3_500, 1)), 5_000.0)
        assertThat(q.deliveryFee).isEqualTo(0L)
        assertThat(q.serviceFee).isEqualTo(150L)
    }

    @Test
    fun `small orders pay a small order fee`() {
        val q = Pricing.quote(listOf(CartLine("c", "Coffee", 350, 1)), 800.0)
        assertThat(q.smallOrderFee).isEqualTo(50L)
    }

    @Test
    fun `promo codes apply, validate and are case insensitive`() {
        val applied = Pricing.quote(listOf(burger, fries), 1_000.0, promoCode = " swift20 ", tip = 100)
        assertThat(applied.promo).isInstanceOf(PromoResult.Applied::class.java)
        assertThat(applied.discount).isEqualTo(300L) // 20% of 2,070 = 414, capped at 300
        assertThat(applied.total).isEqualTo(applied.subtotal + applied.deliveryFee + applied.serviceFee - 300 + 100)

        val tooSmall = Pricing.quote(listOf(fries), 1_000.0, promoCode = "SWIFT20")
        assertThat(tooSmall.promo).isEqualTo(PromoResult.Invalid("SWIFT20", "Minimum order Rs 800"))
        assertThat(tooSmall.discount).isEqualTo(0L)

        assertThat(Pricing.quote(listOf(fries), 1_000.0, promoCode = "FREEDEL").deliveryFee).isEqualTo(0L)
        assertThat(Pricing.quote(listOf(fries), 1_000.0, promoCode = "NOPE").promo)
            .isInstanceOf(PromoResult.Invalid::class.java)
    }
}

class CourierSimulatorTest {

    private val city = DemoCity.map
    private val script = CourierSimulator(city).script(
        courierStartId = DemoCity.id(5, 4),
        restaurantId = DemoCity.id(2, 1),
        homeId = DemoCity.id(11, 6),
        startAt = 1_000_000,
    )

    @Test
    fun `statuses arrive in lifecycle order exactly once`() {
        val statuses = script.mapNotNull { (it.event as? TrackingEvent.StatusChanged)?.status }
        assertThat(statuses).containsExactly(
            OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.PICKED_UP, OrderStatus.ARRIVING, OrderStatus.DELIVERED,
        ).inOrder()
    }

    @Test
    fun `events are time ordered and stamped from the start time`() {
        val offsets = script.map { it.offsetMillis }
        assertThat(offsets).isEqualTo(offsets.sorted())
        script.forEach { assertThat(it.event.at).isEqualTo(1_000_000 + it.offsetMillis) }
    }

    @Test
    fun `replaying the script ends delivered with the courier at the door`() {
        val final = script.fold(TrackingState()) { s, e -> s.reduce(e.event) }
        assertThat(final.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(final.courier?.point).isEqualTo(city.node(DemoCity.id(11, 6)).position)
        assertThat(final.timeline.keys).hasSize(6)
    }

    @Test
    fun `eta counts down during delivery`() {
        val pickedAt = script.first { (it.event as? TrackingEvent.StatusChanged)?.status == OrderStatus.PICKED_UP }.offsetMillis
        val etas = script.filter { it.offsetMillis > pickedAt }.mapNotNull { (it.event as? TrackingEvent.EtaUpdated)?.seconds }
        assertThat(etas).isEqualTo(etas.sortedDescending())
        assertThat(etas.last()).isEqualTo(0)
    }
}
