package com.brasa.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun PreviewPlayerScreen(item: CatalogItem?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = item?.backdrop,
            contentDescription = item?.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent, Color.Black.copy(alpha = .92f))),
            ),
        )
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 34.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrasaLogo()
            Spacer(Modifier.width(22.dp))
            Text(item?.title ?: "Reproduzindo agora", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("PREVIEW DO PLAYER", color = BrasaOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 52.dp, vertical = 30.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("42:18", color = BrasaText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(13.dp))
                Box(Modifier.weight(1f).height(5.dp).background(Color.White.copy(alpha = .24f), RoundedCornerShape(50))) {
                    Box(Modifier.fillMaxWidth(.36f).height(5.dp).background(BrasaOrange, RoundedCornerShape(50)))
                }
                Spacer(Modifier.width(13.dp))
                Text("2:35:00", color = BrasaTextMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                BrasaButton("10s", {}, leading = "↶")
                Spacer(Modifier.width(11.dp))
                BrasaButton("Reproduzir", {}, Modifier.focusRequester(focus), style = BrasaButtonStyle.Primary, leading = "▶")
                Spacer(Modifier.width(11.dp))
                BrasaButton("10s", {}, leading = "↷")
                Spacer(Modifier.width(22.dp))
                BrasaButton("Áudio", {})
                Spacer(Modifier.width(9.dp))
                BrasaButton("Legenda", {})
                Spacer(Modifier.width(9.dp))
                BrasaButton("Sair", onBack, style = BrasaButtonStyle.Ghost)
            }
        }
    }
}
