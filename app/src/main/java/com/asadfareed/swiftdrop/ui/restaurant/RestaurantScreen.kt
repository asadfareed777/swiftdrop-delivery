package com.asadfareed.swiftdrop.ui.restaurant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asadfareed.swiftdrop.data.Catalog
import com.asadfareed.swiftdrop.data.MenuItem
import com.asadfareed.swiftdrop.ui.ShopViewModel
import com.asadfareed.swiftdrop.ui.components.CartBar
import com.asadfareed.swiftdrop.ui.components.Stepper
import com.asadfareed.swiftdrop.ui.components.gradient
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift

@Composable
fun RestaurantScreen(vm: ShopViewModel, restaurantId: String, onBack: () -> Unit, onCart: () -> Unit) {
    val r = Catalog.restaurant(restaurantId)
    val cart by vm.cart.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Swift.Background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(230.dp).background(gradient(r.colors))) {
                    Text(r.emoji, fontSize = 96.sp, modifier = Modifier.align(Alignment.Center))
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.statusBarsPadding().padding(12.dp).clip(CircleShape).background(Color.White),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                }
            }
            item {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 0.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(Color.White)
                        .padding(20.dp),
                ) {
                    Text(r.name, style = MaterialTheme.typography.headlineSmall)
                    Text(r.cuisine, color = Swift.Muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = Swift.Amber, modifier = Modifier.size(18.dp))
                            Text(" ${r.rating}", fontWeight = FontWeight.Bold)
                            Text(" (${"%,d".format(r.reviews)})", color = Swift.Muted)
                        }
                        Text("${r.prepMinutes.first + 10}–${r.prepMinutes.last + 15} min", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            r.menu.forEach { section ->
                item {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                items(section.items, key = { it.id }) { item ->
                    MenuRow(item, r.colors, cart[item.id] ?: 0, onAdd = { vm.add(item.id) }, onRemove = { vm.remove(item.id) })
                }
            }
        }
        if (summary.count > 0) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                CartBar(summary.count, summary.subtotal, "View cart", onCart)
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, colors: Pair<Long, Long>, quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (item.popular) {
                Text(
                    "🔥 Popular",
                    color = Swift.Brand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(item.description, color = Swift.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(item.price.rupees(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(gradient(colors)),
                contentAlignment = Alignment.Center,
            ) { Text(item.emoji, fontSize = 36.sp) }
            Spacer(Modifier.height(8.dp))
            Stepper(quantity, onAdd, onRemove)
        }
    }
}
