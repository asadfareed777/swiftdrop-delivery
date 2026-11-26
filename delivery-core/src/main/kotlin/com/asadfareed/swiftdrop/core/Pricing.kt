package com.asadfareed.swiftdrop.core

import kotlin.math.min
import kotlin.math.roundToLong

/** Amounts are whole rupees. */
data class CartLine(val itemId: String, val name: String, val unitPrice: Long, val quantity: Int) {
    init {
        require(quantity > 0) { "Quantity must be positive" }
        require(unitPrice >= 0) { "Price can't be negative" }
    }

    val total: Long get() = unitPrice * quantity
}

data class PricingPolicy(
    val baseDeliveryFee: Long = 99,
    val perKm: Long = 35,
    val freeDeliveryOver: Long = 2_500,
    val serviceRate: Double = 0.05,
    val serviceCap: Long = 150,
    val smallOrderBelow: Long = 500,
    val smallOrderFee: Long = 50,
)

sealed interface PromoResult {
    data object None : PromoResult
    data class Applied(val code: String, val description: String) : PromoResult
    data class Invalid(val code: String, val reason: String) : PromoResult
}

data class PriceBreakdown(
    val subtotal: Long,
    val deliveryFee: Long,
    val serviceFee: Long,
    val smallOrderFee: Long,
    val discount: Long,
    val tip: Long,
    val promo: PromoResult,
) {
    val total: Long get() = subtotal + deliveryFee + serviceFee + smallOrderFee - discount + tip
}

object Pricing {

    fun quote(
        lines: List<CartLine>,
        distanceMeters: Double,
        promoCode: String? = null,
        tip: Long = 0,
        policy: PricingPolicy = PricingPolicy(),
    ): PriceBreakdown {
        require(tip >= 0) { "Tip can't be negative" }
        val subtotal = lines.sumOf { it.total }

        var delivery = if (subtotal >= policy.freeDeliveryOver) 0L
        else roundTo5(policy.baseDeliveryFee + distanceMeters / 1000.0 * policy.perKm)
        val service = min((subtotal * policy.serviceRate).roundToLong(), policy.serviceCap)
        val small = if (subtotal in 1 until policy.smallOrderBelow) policy.smallOrderFee else 0L

        var discount = 0L
        val code = promoCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val promo: PromoResult = when (code) {
            null -> PromoResult.None
            "SWIFT20" -> if (subtotal < 800) {
                PromoResult.Invalid(code, "Minimum order Rs 800")
            } else {
                discount = min((subtotal * 0.20).roundToLong(), 300)
                PromoResult.Applied(code, "20% off, up to Rs 300")
            }
            "FREEDEL" -> {
                delivery = 0
                PromoResult.Applied(code, "Free delivery")
            }
            else -> PromoResult.Invalid(code, "Code not recognised")
        }
        return PriceBreakdown(subtotal, delivery, service, small, discount, tip, promo)
    }

    private fun roundTo5(value: Double): Long = ((value / 5.0).roundToLong() * 5)
}
