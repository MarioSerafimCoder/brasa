package com.brasa.tv.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val BrasaOrange = Color(0xFFFF641E)
val BrasaOrangeSoft = Color(0xFFFF8A4C)
val BrasaRed = Color(0xFFEF3829)
val BrasaBackground = Color(0xFF03070D)
val BrasaSurface = Color(0xFF10151D)
val BrasaSurfaceElevated = Color(0xFF181E28)
val BrasaBorder = Color(0xFF303844)
val BrasaFocus = Color(0xFFFFFFFF)
val BrasaText = Color(0xFFF7F8FA)
val BrasaTextMuted = Color(0xFFA7AFBA)
val BrasaSuccess = Color(0xFF4ED18B)
val BrasaDisabled = Color(0xFF626A75)

object BrasaSpacing {
    val x1 = 8.dp
    val x2 = 16.dp
    val x3 = 24.dp
    val x4 = 32.dp
    val x6 = 48.dp
    val x8 = 64.dp
    val safe = 72.dp
    val safeWide = 96.dp
}

object BrasaType {
    val hero = 60.sp
    val page = 48.sp
    val section = 32.sp
    val body = 24.sp
    val metadata = 20.sp
    val button = 24.sp
}

@Composable
fun BrasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrasaOrange,
            secondary = BrasaRed,
            background = BrasaBackground,
            surface = BrasaSurface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = BrasaText,
            onSurface = BrasaText,
        ),
        content = content,
    )
}
