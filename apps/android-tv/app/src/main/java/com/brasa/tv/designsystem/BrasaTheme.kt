package com.brasa.tv.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val BrasaOrange = Color(0xFFFF641E)
val BrasaOrangeSoft = Color(0xFFFF8A4C)
val BrasaRed = Color(0xFFEF3829)
val BrasaBackground = Color(0xFF03070D)
val BrasaSurface = Color(0xFF10151D)
val BrasaSurfaceElevated = Color(0xFF181E28)
val BrasaBorder = Color(0xFF303844)
val BrasaText = Color(0xFFF7F8FA)
val BrasaTextMuted = Color(0xFFA7AFBA)

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
