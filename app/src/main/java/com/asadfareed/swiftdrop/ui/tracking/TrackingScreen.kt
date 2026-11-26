package com.asadfareed.swiftdrop.ui.tracking

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asadfareed.swiftdrop.core.DemoCity
import com.asadfareed.swiftdrop.core.Eta
import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.data.Connection
import com.asadfareed.swiftdrop.data.Order
import com.asadfareed.swiftdrop.ui.TrackingViewModel
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val steps = listOf(
    OrderStatus.ACCEPTED to "Confirmed",
    OrderStatus.PREPARING to "Cooking",
    OrderStatus.PICKED_UP to "Picked up",
    OrderStatus.ARRIVING to "Arriving",
    OrderStatus.DELIVERED to "Delivered",
)

@Composable
fun TrackingScreen(vm: TrackingViewModel, onBack: () -> Unit) {
    val order = vm.order
    val flow = vm.live
    if (order == null || flow == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Order not found") }
        return
    }
    val live by flow.collectAsStateWithLifecycle()
    val tracking = live.tracking
    val route = order.route

    Box(Modifier.fillMaxSize().background(Swift.Background)) {
        CityMapCanvas(
            city = DemoCity.map,
            route = route,
            restaurantEmoji = order.restaurant.emoji,
            courier = tracking.courier,
            pickedUp = tracking.status >= OrderStatus.PICKED_UP,
            modifier = Modifier.fillMaxWidth().height(470.dp),
        )
        Row(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.White)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            ConnectionChip(live.connection)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 430.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(Swift.Border))
            Spacer(Modifier.height(14.dp))
            val eta = tracking.etaSeconds
            Text(
                when (tracking.status) {
                    OrderStatus.DELIVERED -> "Enjoy your meal! 🎉"
                    OrderStatus.CANCELLED -> "Order cancelled"
                    else -> eta?.let { if (it <= 45) "Arriving any moment" else "Arriving in ${Eta.label(it)}" } ?: "Finding your rider…"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(tracking.status.label, color = Swift.Muted)
            Spacer(Modifier.height(18.dp))
            StatusSteps(tracking.status)
            Spacer(Modifier.height(20.dp))
            CourierCard(visible = tracking.courier != null)
            Spacer(Modifier.height(16.dp))
            OrderSummary(order)
        }
    }
}

@Composable
private fun ConnectionChip(connection: Connection) {
    val (label, color) = when (connection) {
        Connection.Connecting -> "Connecting…" to Swift.Amber
        is Connection.Live -> (if (connection.viaServer) "Live · WebSocket" else "Live · Demo") to Swift.Green
        is Connection.Reconnecting -> "Reconnecting in ${(connection.inMillis + 999) / 1000}s" to Swift.Amber
        Connection.Finished -> "Completed" to Swift.Muted
    }
    val dot by animateColorAsState(color, label = "dot")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (connection is Connection.Connecting || connection is Connection.Reconnecting) {
            CircularProgressIndicator(Modifier.size(12.dp), color = dot, strokeWidth = 2.dp)
        } else {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun StatusSteps(status: OrderStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEachIndexed { i, (step, label) ->
            val done = status >= step && status != OrderStatus.CANCELLED
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(4.dp)
                            .background(if (i == 0) Color.Transparent else if (done) Swift.Brand else Swift.Border),
                    )
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(if (done) Swift.Brand else Swift.Border),
                    )
                    Box(
                        Modifier.weight(1f).height(4.dp)
                            .background(
                                if (i == steps.lastIndex) Color.Transparent
                                else if (status >= steps[i + 1].first && status != OrderStatus.CANCELLED) Swift.Brand
                                else Swift.Border,
                            ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 11.sp, color = if (done) Swift.Ink else Swift.Muted, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CourierCard(visible: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Swift.Background).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Swift.Brand.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text("🛵", fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (visible) "Hamza is your rider" else "Assigning a rider…", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = Swift.Amber, modifier = Modifier.size(14.dp))
                Text(" 4.9 · LEB-2741", color = Swift.Muted, fontSize = 13.sp)
            }
        }
        RoundAction(Icons.AutoMirrored.Rounded.Chat)
        Spacer(Modifier.width(8.dp))
        RoundAction(Icons.Rounded.Call)
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Swift.Brand, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun OrderSummary(order: Order) {
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(order.placedAt))
    Column {
        Text("${order.restaurant.name} · ${order.id}", fontWeight = FontWeight.SemiBold)
        Text("Placed at $time", color = Swift.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        order.lines.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("${line.quantity}×  ${line.name}", modifier = Modifier.weight(1f))
                Text(line.total.rupees(), color = Swift.Muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text("Total paid", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(order.price.total.rupees(), fontWeight = FontWeight.Bold)
        }
    }
}
