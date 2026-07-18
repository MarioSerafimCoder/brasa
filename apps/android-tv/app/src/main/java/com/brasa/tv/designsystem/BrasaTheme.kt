package com.brasa.tv.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
val LocalCardDensity = staticCompositionLocalOf { 1f }

object BrasaSpacing {
    val x1 = 6.dp
    val x2 = 12.dp
    val x3 = 18.dp
    val x4 = 24.dp
    val x6 = 36.dp
    val x8 = 48.dp
    val safe = 54.dp
    val safeWide = 72.dp
}

object BrasaType {
    val hero = 48.sp
    val page = 38.sp
    val section = 25.sp
    val body = 18.sp
    val metadata = 15.sp
    val button = 17.sp
}

@Composable
fun BrasaTheme(uiScale:Float=.9f,cardDensity:Float=1f,content: @Composable () -> Unit) {
    val baseDensity=LocalDensity.current
    val scaledDensity=Density(baseDensity.density*uiScale.coerceIn(.8f,1.1f),baseDensity.fontScale)
    CompositionLocalProvider(LocalDensity provides scaledDensity,LocalCardDensity provides cardDensity.coerceIn(.8f,1.15f)){ MaterialTheme(
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
    ) }
}
