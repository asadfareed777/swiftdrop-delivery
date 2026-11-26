package com.asadfareed.swiftdrop.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
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
import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.ui.SettingsViewModel
import com.asadfareed.swiftdrop.ui.ShopViewModel
import com.asadfareed.swiftdrop.ui.components.Card
import com.asadfareed.swiftdrop.ui.components.EmojiTile
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(vm: ShopViewModel, settings: SettingsViewModel, onTrack: (String) -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val format = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.US)

    LazyColumn(
        Modifier.fillMaxSize().background(Swift.Background).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Your orders", style = MaterialTheme.typography.headlineSmall) }
        items(orders, key = { it.id }) { order ->
            val status = statuses[order.id]
            val active = status == null || !status.isTerminal
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, if (active) Swift.Brand else Swift.Border, RoundedCornerShape(20.dp))
                    .clickable { onTrack(order.id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiTile(order.restaurant.emoji, order.restaurant.colors, 56.dp, 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(order.restaurant.name, fontWeight = FontWeight.SemiBold)
                    Text(format.format(Date(order.placedAt)), color = Swift.Muted, fontSize = 13.sp)
                    Text("${order.lines.sumOf { it.quantity }} items · ${order.price.total.rupees()}", color = Swift.Muted, fontSize = 13.sp)
                }
                Text(
                    if (active) "Track" else (status ?: OrderStatus.DELIVERED).label,
                    color = if (active) Color.White else Swift.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Swift.Brand else Swift.Green.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        item { DeveloperSettings(settings) }
    }
}

@Composable
private fun DeveloperSettings(vm: SettingsViewModel) {
    val useServer by vm.useServer.collectAsStateWithLifecycle()
    val url by vm.serverUrl.collectAsStateWithLifecycle()
    Card(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live tracking server", fontWeight = FontWeight.SemiBold)
                Text(
                    if (useServer) "Streaming from WebSocket server" else "Using built-in simulator (offline)",
                    color = Swift.Muted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = useServer,
                onCheckedChange = vm::setUseServer,
                colors = SwitchDefaults.colors(checkedTrackColor = Swift.Brand),
            )
        }
        if (useServer) {
            OutlinedTextField(
                value = url,
                onValueChange = vm::setServerUrl,
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Text("Run `npm start` in /server · emulator host is 10.0.2.2", color = Swift.Muted, fontSize = 12.sp)
        }
    }
}
