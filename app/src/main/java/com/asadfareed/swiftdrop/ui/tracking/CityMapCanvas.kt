package com.asadfareed.swiftdrop.ui.tracking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.asadfareed.swiftdrop.core.CityMap
import com.asadfareed.swiftdrop.core.CourierPosition
import com.asadfareed.swiftdrop.core.Point
import com.asadfareed.swiftdrop.core.Route
import com.asadfareed.swiftdrop.ui.theme.Swift
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Draws the city, the delivery route and a courier marker. Courier fixes arrive about
 * once a second; the marker is animated linearly between them so it glides instead of
 * jumping. The camera frames the restaurant, the customer and the courier.
 */
@Composable
fun CityMapCanvas(
    city: CityMap,
    route: Route,
    restaurantEmoji: String,
    courier: CourierPosition?,
    pickedUp: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val ax = remember { Animatable(courier?.point?.x?.toFloat() ?: route.points.first().x.toFloat()) }
    val ay = remember { Animatable(courier?.point?.y?.toFloat() ?: route.points.first().y.toFloat()) }
    LaunchedEffect(courier) {
        val target = courier ?: return@LaunchedEffect
        val spec = tween<Float>(durationMillis = 950, easing = LinearEasing)
        launch { ax.animateTo(target.point.x.toFloat(), spec) }
        launch { ay.animateTo(target.point.y.toFloat(), spec) }
    }

    Canvas(modifier) {
        val focus = route.points + listOfNotNull(courier?.point)
        val minX = focus.minOf { it.x } - 260
        val maxX = focus.maxOf { it.x } + 260
        val minY = focus.minOf { it.y } - 420 // extra headroom for pins under the status bar
        val maxY = focus.maxOf { it.y } + 260
        val scale = min(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
        val ox = (size.width - (maxX - minX) * scale) / 2f - minX.toFloat() * scale
        val oy = (size.height - (maxY - minY) * scale) / 2f - minY.toFloat() * scale
        fun map(p: Point) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
        fun map(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)

        drawRect(Swift.Block)
        city.parks.forEach { park ->
            val tl = map(Point(park.left, park.top))
            val br = map(Point(park.right, park.bottom))
            drawRoundRect(Swift.Park, tl, Size(br.x - tl.x, br.y - tl.y), CornerRadius(6 * density))
        }
        val river = Path().apply {
            city.river.forEachIndexed { i, p -> val o = map(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        drawPath(river, Swift.River, style = Stroke(70f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val streetWidth = 26f * scale
        city.edges.forEach { (a, b) ->
            drawLine(Swift.Street, map(a.position), map(b.position), streetWidth, StrokeCap.Round)
        }

        drawRoute(route, ::map, pickedUp, courier, scale)

        // Pins
        pin(map(route.points.first()), Swift.Ink, restaurantEmoji, measurer)
        pin(map(route.points.last()), Swift.Brand, "🏠", measurer)

        // Courier
        if (courier != null) {
            val c = map(ax.value, ay.value)
            drawCircle(Swift.Brand.copy(alpha = 0.18f), 30 * density, c)
            drawCircle(Color.White, 17 * density, c)
            drawCircle(Swift.Brand, 14 * density, c)
            rotate(courier.bearing.toFloat(), pivot = c) {
                val arrow = Path().apply {
                    moveTo(c.x, c.y - 8 * density)
                    lineTo(c.x + 6 * density, c.y + 6 * density)
                    lineTo(c.x, c.y + 3 * density)
                    lineTo(c.x - 6 * density, c.y + 6 * density)
                    close()
                }
                drawPath(arrow, Color.White)
            }
        }
    }
}

private fun DrawScope.drawRoute(
    route: Route,
    map: (Point) -> Offset,
    pickedUp: Boolean,
    courier: CourierPosition?,
    scale: Float,
) {
    val path = Path().apply {
        route.points.forEachIndexed { i, p -> val o = map(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    }
    val width = 9f * density
    if (!pickedUp || courier == null) {
        drawPath(path, Swift.Brand, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    // Travelled part greyed and dashed; remaining part in brand colour.
    drawPath(
        path, Swift.RouteDone,
        style = Stroke(width * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
    )
    val travelled = route.length - route.remainingFrom(courier.point)
    val remaining = Path()
    var started = false
    val steps = 80
    for (i in 0..steps) {
        val d = travelled + (route.length - travelled) * i / steps
        val o = map(route.positionAt(d).point)
        if (!started) { remaining.moveTo(o.x, o.y); started = true } else remaining.lineTo(o.x, o.y)
    }
    drawPath(remaining, Swift.Brand, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.pin(at: Offset, color: Color, emoji: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    val r = 20 * density
    val tip = Offset(at.x, at.y)
    val head = Offset(at.x, at.y - r * 1.6f)
    val body = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(head.x - r * 0.7f, head.y + r * 0.6f)
        lineTo(head.x + r * 0.7f, head.y + r * 0.6f)
        close()
    }
    drawPath(body, color)
    drawCircle(color, r, head)
    drawCircle(Color.White, r * 0.78f, head)
    val text = measurer.measure(emoji, TextStyle(fontSize = 16.sp))
    drawText(text, topLeft = Offset(head.x - text.size.width / 2f, head.y - text.size.height / 2f))
}
