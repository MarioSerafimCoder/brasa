package com.brasa.tv.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val BrasaOrange=Color(0xFFF97316)
val BrasaRed=Color(0xFFEF2D23)
val BrasaBackground=Color(0xFF07090D)
val BrasaSurface=Color(0xFF151922)

@Composable fun BrasaTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme=darkColorScheme(primary=BrasaOrange,secondary=BrasaRed,background=BrasaBackground,surface=BrasaSurface,onPrimary=Color.White,onBackground=Color.White)){content()}
}
