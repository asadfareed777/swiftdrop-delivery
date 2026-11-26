package com.asadfareed.swiftdrop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asadfareed.swiftdrop.core.DemoCity
import com.asadfareed.swiftdrop.core.Pricing
import com.asadfareed.swiftdrop.data.Catalog
import com.asadfareed.swiftdrop.data.Restaurant
import com.asadfareed.swiftdrop.ui.ShopViewModel
import com.asadfareed.swiftdrop.ui.components.CartBar
import com.asadfareed.swiftdrop.ui.components.gradient
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift

@Composable
fun HomeScreen(vm: ShopViewModel, onRestaurant: (String) -> Unit, onCart: () -> Unit) {
    val restaurants by vm.restaurants.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Swift.Background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp)) {
                    Text("Deliver to", color = Swift.Muted, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, null, tint = Swift.Brand, modifier = Modifier.size(20.dp))
                        Text(
                            "${Catalog.home.label} · ${Catalog.home.line}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Swift.Ink)
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search dishes or restaurants") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Swift.Border,
                        ),
                    )
                }
            }
            item { PromoBanner() }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Catalog.categories) { c ->
                        val on = c == category
                        Text(
                            c,
                            color = if (on) Color.White else Swift.Ink,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) Swift.Ink else Color.White)
                                .border(1.dp, if (on) Swift.Ink else Swift.Border, RoundedCornerShape(50))
                                .clickable { vm.setCategory(c) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    "Restaurants near you",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            items(restaurants, key = { it.id }) { r ->
                RestaurantCard(r, onClick = { onRestaurant(r.id) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        if (summary.count > 0) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                CartBar(summary.count, summary.subtotal, "View cart · ${summary.restaurant?.name}", onCart)
            }
        }
    }
}

@Composable
private fun PromoBanner() {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Swift.Brand, Color(0xFFFF8A3D))))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("20% OFF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text("on orders over Rs 800", color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(10.dp))
            Text(
                "Code: SWIFT20",
                color = Swift.Brand,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text("🛵", fontSize = 56.sp)
    }
}

@Composable
fun RestaurantCard(r: Restaurant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val distance = DemoCity.map.shortestPath(r.nodeId, Catalog.home.nodeId)?.length ?: 0.0
    val fee = Pricing.quote(emptyList(), distance).deliveryFee
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Swift.Border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().height(130.dp).background(gradient(r.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(r.emoji, fontSize = 64.sp)
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = Swift.Ink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${r.prepMinutes.first + 10}–${r.prepMinutes.last + 15} min", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text("${r.cuisine} · ${fee.rupees()} delivery", color = Swift.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Swift.Background).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Star, null, tint = Swift.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${r.rating}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
