package com.brasa.tv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.GenreChip
import com.brasa.tv.designsystem.MessagePanel
import com.brasa.tv.designsystem.metadata

@Composable
fun DetailsScreen(
    state: BrasaUiState,
    onPlay: (CatalogItem) -> Unit,
    onPrefetch: (CatalogItem) -> Unit,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val item = state.selected ?: return MessagePanel("Conteúdo indisponível", "Volte e escolha outro item.", "Voltar", onBack)
    val playFocus = remember { FocusRequester() }
    val firstEpisode = item.seasons.flatMap { it.episodes }.firstOrNull()
    LaunchedEffect(item.mediaKey) { onPrefetch(firstEpisode ?: item); runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(BrasaBackground)) {
        AsyncImage(
            model = item.backdrop.ifBlank { item.poster },
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to BrasaBackground,
                    .38f to BrasaBackground.copy(alpha = .94f),
                    .7f to BrasaBackground.copy(alpha = .36f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(BrasaBackground.copy(alpha = .08f), Color.Transparent, BrasaBackground.copy(alpha = .94f))),
            ),
        )
        BrasaLogo(Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 28.dp))
        Column(
            Modifier.fillMaxHeight().width(650.dp).padding(start = 56.dp, top = 84.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(metadata(item), color = BrasaTextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(
                item.title,
                color = Color.White,
                fontSize = 49.sp,
                lineHeight = 51.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.genres.take(4).forEach { GenreChip(it) } }
            }
            Spacer(Modifier.height(17.dp))
            Text(
                item.overview.ifBlank { "Sinopse ainda não disponível." },
                color = BrasaText.copy(alpha = .86f),
                fontSize = 18.sp,
                lineHeight = 26.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(23.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrasaButton(
                    if ((item.progress?.percentage ?: 0.0) > 0) "Continuar" else "Assistir",
                    { onPlay(firstEpisode ?: item) },
                    Modifier.focusRequester(playFocus),
                    enabled = item.streamUrl.isNotBlank() || firstEpisode != null,
                    style = BrasaButtonStyle.Primary,
                    leading = "▶",
                )
                if (item.type == "movie") BrasaButton(if (item.favorite) "Remover da lista" else "Minha lista", onFavorite, leading = if (item.favorite) "✓" else "+")
                BrasaButton("Voltar", onBack)
            }
            if (item.seasons.isNotEmpty()) {
                Spacer(Modifier.height(25.dp))
                Text("Episódios", color = BrasaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(11.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(item.seasons.flatMap { it.episodes }, key = { it.mediaKey }) { episode ->
                        BrasaButton("T${episode.seasonNumber}E${episode.episodeNumber} · ${episode.title}", { onPlay(episode) })
                    }
                }
            }
        }
    }
}
