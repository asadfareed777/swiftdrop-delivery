package com.asadfareed.swiftdrop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Swift {
    val Brand = Color(0xFFFF5A36)
    val BrandDark = Color(0xFFE23E1B)
    val Background = Color(0xFFF7F7F8)
    val Surface = Color.White
    val Border = Color(0xFFECECEF)
    val Ink = Color(0xFF16161D)
    val Muted = Color(0xFF6B6B78)
    val Green = Color(0xFF16A34A)
    val Amber = Color(0xFFF59E0B)

    // Map palette
    val Land = Color(0xFFEEF1F4)
    val Block = Color(0xFFE3E7EC)
    val Street = Color.White
    val Park = Color(0xFFCFEBD6)
    val River = Color(0xFFBFDCF5)
    val RouteDone = Color(0xFFB8BCC6)
}

private val Sans = FontFamily.SansSerif

@Composable
fun SwiftdropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Swift.Brand,
            onPrimary = Color.White,
            secondary = Swift.Ink,
            background = Swift.Background,
            onBackground = Swift.Ink,
            surface = Swift.Surface,
            onSurface = Swift.Ink,
            surfaceVariant = Swift.Background,
            onSurfaceVariant = Swift.Muted,
            outline = Swift.Border,
        ),
        typography = Typography(
            headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp),
            titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp),
            labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        ),
        content = content,
    )
}
