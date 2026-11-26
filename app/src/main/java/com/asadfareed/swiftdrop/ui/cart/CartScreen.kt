package com.asadfareed.swiftdrop.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asadfareed.swiftdrop.core.PromoResult
import com.asadfareed.swiftdrop.data.Catalog
import com.asadfareed.swiftdrop.ui.ShopViewModel
import com.asadfareed.swiftdrop.ui.components.Card
import com.asadfareed.swiftdrop.ui.components.Stepper
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift

@Composable
fun CartScreen(vm: ShopViewModel, onBack: () -> Unit, onOrderPlaced: (String) -> Unit) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val quote by vm.quote.collectAsStateWithLifecycle()
    val promoInput by vm.promoInput.collectAsStateWithLifecycle()
    val tip by vm.tip.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Swift.Background)) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column {
                Text("Checkout", style = MaterialTheme.typography.titleLarge)
                summary.restaurant?.let { Text(it.name, color = Swift.Muted, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        val q = quote
        if (summary.count == 0 || q == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛒", fontSize = 64.sp)
                    Text("Your cart is empty", style = MaterialTheme.typography.titleMedium)
                }
            }
            return
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Swift.Brand.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Home, null, tint = Swift.Brand) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Deliver to ${Catalog.home.label}", fontWeight = FontWeight.SemiBold)
                        Text(Catalog.home.line, color = Swift.Muted)
                    }
                    TextButton(onClick = {}) { Text("Change", color = Swift.Brand) }
                }
            }
            Card {
                summary.lines.forEachIndexed { i, line ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Catalog.item(line.itemId).second.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontWeight = FontWeight.SemiBold)
                            Text(line.total.rupees(), color = Swift.Muted)
                        }
                        Stepper(cart[line.itemId] ?: 0, { vm.add(line.itemId) }, { vm.remove(line.itemId) })
                    }
                }
            }
            Card {
                Text("Promo code", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = promoInput,
                        onValueChange = vm::setPromoInput,
                        placeholder = { Text("e.g. SWIFT20") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Swift.Border),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = vm::applyPromo,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Swift.Ink),
                    ) { Text("Apply") }
                }
                when (val p = q.promo) {
                    is PromoResult.Applied -> PromoNote(true, "${p.code}: ${p.description}")
                    is PromoResult.Invalid -> PromoNote(false, "${p.code}: ${p.reason}")
                    PromoResult.None -> Unit
                }
            }
            Card {
                Text("Tip your rider", fontWeight = FontWeight.SemiBold)
                Text("100% of the tip goes to your rider", color = Swift.Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L, 50L, 100L, 150L).forEach { amount ->
                        val on = tip == amount
                        Text(
                            if (amount == 0L) "No tip" else amount.rupees(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) Color.White else Swift.Ink,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Swift.Brand else Color.White)
                                .border(1.dp, if (on) Swift.Brand else Swift.Border, RoundedCornerShape(12.dp))
                                .clickable { vm.setTip(amount) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CreditCard, null, tint = Swift.Ink)
                    Spacer(Modifier.width(10.dp))
                    Text("Visa •••• 4242", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Demo", color = Swift.Muted)
                }
            }
            Card {
                PriceRow("Subtotal", q.subtotal.rupees())
                PriceRow("Delivery fee", if (q.deliveryFee == 0L) "Free" else q.deliveryFee.rupees())
                PriceRow("Service fee", q.serviceFee.rupees())
                if (q.smallOrderFee > 0) PriceRow("Small order fee", q.smallOrderFee.rupees())
                if (q.discount > 0) PriceRow("Discount", "−" + q.discount.rupees(), Swift.Green)
                if (q.tip > 0) PriceRow("Rider tip", q.tip.rupees())
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(q.total.rupees(), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { vm.placeOrder()?.let(onOrderPlaced) },
            modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Swift.Brand),
        ) { Text("Place order · ${q.total.rupees()}", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun PromoNote(ok: Boolean, text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            null,
            tint = if (ok) Swift.Green else Color(0xFFDC2626),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (ok) Swift.Green else Color(0xFFDC2626), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PriceRow(label: String, value: String, color: Color = Swift.Ink) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Swift.Muted, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Medium)
    }
}
