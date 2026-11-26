package com.asadfareed.swiftdrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asadfareed.swiftdrop.ui.rupees
import com.asadfareed.swiftdrop.ui.theme.Swift

fun gradient(colors: Pair<Long, Long>) =
    Brush.linearGradient(listOf(Color(colors.first.toInt()), Color(colors.second.toInt())))

/** Food art without image assets: emoji on a brand gradient tile. */
@Composable
fun EmojiTile(emoji: String, colors: Pair<Long, Long>, size: Dp, corner: Dp = 18.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(gradient(colors)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.48f).sp) }
}

@Composable
fun Stepper(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    if (quantity == 0) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Swift.Brand)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = "Add", tint = Color.White) }
        return
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Swift.Border, RoundedCornerShape(50))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = "Remove", tint = Swift.Ink, modifier = Modifier.size(18.dp))
        }
        Text("$quantity", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.size(36.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = "Add", tint = Swift.Brand, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CartBar(count: Int, total: Long, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Swift.Brand)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text("$count", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(total.rupees(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Swift.Surface)
            .border(1.dp, Swift.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}
