package com.brasa.tv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.Season
import com.brasa.tv.core.model.playableItem
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaFocus
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.GenreChip
import com.brasa.tv.designsystem.MessagePanel
import com.brasa.tv.designsystem.metadata
import kotlinx.coroutines.delay

@Composable
fun DetailsScreen(
    state: BrasaUiState,
    onPlay: (CatalogItem) -> Unit,
    onPlayFromStart: (CatalogItem) -> Unit,
    onPrefetch: (CatalogItem) -> Unit,
    onCancelPreload: () -> Unit,
    onFavorite: () -> Unit,
    onSignal: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val item = state.selected ?: return MessagePanel("Conteúdo indisponível", "Volte e escolha outro item.", "Voltar", onBack)
    val playFocus = remember { FocusRequester() }
    val continuation = item.playableItem()
    var selectedSeasonNumber by rememberSaveable(item.mediaKey) { mutableStateOf(continuation.seasonNumber ?: item.seasons.firstOrNull()?.seasonNumber ?: 0) }
    val focusMemory = rememberCatalogFocus("details:${state.profile?.id}:${item.mediaKey}")
    var initialFocusSet by rememberSaveable(item.mediaKey) { mutableStateOf(false) }
    val selectedSeason = item.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: item.seasons.firstOrNull()
    val firstPlayable = if (item.type == "series") continuation else item
    var keepPreload by remember(item.mediaKey) { mutableStateOf(false) }
    var expandedOverview by rememberSaveable(item.mediaKey) { mutableStateOf(false) }

    LaunchedEffect(item.mediaKey, selectedSeasonNumber) {
        onPrefetch(firstPlayable)
        if (!initialFocusSet) { runCatching { playFocus.requestFocus() }; initialFocusSet = true }
    }
    DisposableEffect(item.mediaKey) { onDispose { if (!keepPreload) onCancelPreload() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BrasaBackground),
        contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(if (item.type == "series") 470.dp else 620.dp)) {
                AsyncImage(
                    model = item.backdrop.ifBlank { item.poster },
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(BrasaBackground, BrasaBackground.copy(alpha = .93f), BrasaBackground.copy(alpha = .22f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrasaBackground.copy(alpha = .08f), Color.Transparent, BrasaBackground))))
                Row(Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrasaButton("‹  Voltar", onBack, style = BrasaButtonStyle.Ghost)
                    Spacer(Modifier.width(16.dp))
                    BrasaLogo()
                }
                Column(
                    Modifier.align(Alignment.CenterStart).width(680.dp).padding(start = BrasaSpacing.safe, top = 54.dp),
                ) {
                    Text(metadata(item), color = BrasaTextMuted, fontSize = BrasaType.metadata, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(item.title, color = Color.White, fontSize = BrasaType.hero, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.genres.isNotEmpty()) {
                        Spacer(Modifier.height(13.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.genres.take(4).forEach { GenreChip(it) } }
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(item.overview.ifBlank { "Sinopse ainda não disponível." }, color = BrasaText.copy(alpha = .88f), fontSize = BrasaType.body, lineHeight = 27.sp, maxLines = if (expandedOverview) 10 else 4, overflow = TextOverflow.Ellipsis)
                    if (item.overview.length > 220) BrasaButton(if (expandedOverview) "Recolher sinopse" else "Ler sinopse completa", { expandedOverview = !expandedOverview }, style = BrasaButtonStyle.Ghost)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrasaButton(
                            continueLabel(item, firstPlayable),
                            { focusMemory.select("play"); keepPreload = true; onPlay(firstPlayable) },
                            focusMemory.modifier("play").focusRequester(playFocus),
                            enabled = firstPlayable.streamUrl.isNotBlank(),
                            style = BrasaButtonStyle.Primary,
                            leading = "▶",
                        )
                        BrasaButton(if (item.favorite || item.inMyList) "Remover da lista" else "Minha lista", onFavorite, leading = if (item.favorite || item.inMyList) "✓" else "+")
                        BrasaButton("Assistir do início", { keepPreload = true; onPlayFromStart(firstPlayable) })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrasaButton(if (item.reaction == "like") "✓ Gostei" else "Gostei", { onSignal("like", item.reaction != "like") }, style = if (item.reaction == "like") BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
                        BrasaButton(if (item.reaction == "not-for-me") "✓ Não é para mim" else "Não é para mim", { onSignal("not-for-me", item.reaction != "not-for-me") }, style = if (item.reaction == "not-for-me") BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
                        BrasaButton(if(item.hiddenSuggestion) "Desfazer ocultação" else "Ocultar sugestão", { onSignal("hide", !item.hiddenSuggestion) }, style = BrasaButtonStyle.Ghost)
                    }
                    if ((item.progress?.percentage ?: 0.0) > 0.0 || item.type == "series") {
                        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            BrasaButton("Remover de Continuar assistindo", { onSignal("dismiss-continue", true) }, style=BrasaButtonStyle.Ghost)
                            BrasaButton("Marcar como assistido", { onSignal("mark-watched", true) }, style=BrasaButtonStyle.Ghost)
                        }
                    }
                    if (item.cast.isNotEmpty() || item.directors.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(listOfNotNull(item.directors.takeIf { it.isNotEmpty() }?.let { "Direção: ${it.joinToString()}" }, item.cast.takeIf { it.isNotEmpty() }?.let { "Elenco: ${it.take(5).joinToString()}" }).joinToString("  ·  "), color = BrasaTextMuted, fontSize = BrasaType.metadata, maxLines = 2)
                    }
                }
            }
        }
        if (item.seasons.isNotEmpty()) {
            item {
                Text("Temporadas", modifier = Modifier.padding(horizontal = BrasaSpacing.safe), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(item.seasons, key = Season::seasonNumber) { season ->
                        BrasaButton(
                            "Temporada ${season.seasonNumber.toString().padStart(2, '0')}  ·  ${season.episodes.size} episódios",
                            { selectedSeasonNumber = season.seasonNumber },
                            style = if (season.seasonNumber == selectedSeasonNumber) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Episódios da temporada ${selectedSeasonNumber.toString().padStart(2, '0')}",
                    modifier = Modifier.padding(horizontal = BrasaSpacing.safe),
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(selectedSeason?.episodes.orEmpty(), key = { it.mediaKey }) { episode ->
                        EpisodeCard(episode, modifier = focusMemory.modifier(episode.mediaKey), onFocused = { onPrefetch(episode) }) {
                            focusMemory.select(episode.mediaKey)
                            keepPreload = true
                            onPlay(episode)
                        }
                    }
                }
            }
        }
        val similar = state.catalog?.let { catalog -> (catalog.movies + catalog.series).filter { candidate -> candidate.mediaKey != item.mediaKey && candidate.genres.any(item.genres::contains) }.take(12) }.orEmpty()
        if (similar.isNotEmpty()) item {
            Text("Títulos semelhantes", modifier = Modifier.padding(horizontal = BrasaSpacing.safe), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(similar, key = { it.mediaKey }) { candidate -> com.brasa.tv.designsystem.MediaCard(candidate, { onPlay(candidate.playableItem()) }, format = if (candidate.type == "series") com.brasa.tv.designsystem.MediaCardFormat.Landscape else com.brasa.tv.designsystem.MediaCardFormat.Poster, onFocused = { onPrefetch(candidate.playableItem()) }) }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: CatalogItem, modifier: Modifier = Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, episode.mediaKey) { if (focused) { delay(180); onFocused() } }
    Column(
        modifier.width(330.dp).graphicsLayer { scaleX = if (focused) 1.035f else 1f; scaleY = if (focused) 1.035f else 1f }
            .background(BrasaSurface, RoundedCornerShape(16.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(BrasaBackground)) {
            AsyncImage(model = episode.backdrop.ifBlank { episode.poster }, contentDescription = episode.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                "EP ${episode.episodeNumber?.toString()?.padStart(2, '0') ?: "--"}",
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(BrasaBackground.copy(alpha = .86f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                color = BrasaOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 116.dp).padding(13.dp)) {
            Text(episode.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(episode.overview.ifBlank { "Resumo sem spoilers em preparação." }, color = BrasaTextMuted, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun continueLabel(parent: CatalogItem, item: CatalogItem): String {
    if (parent.actionLabel.isNotBlank()) return parent.actionLabel + (parent.remainingMinutes?.let { " — faltam $it min" } ?: "")
    val seconds = item.progress?.currentTime?.toLong() ?: 0L
    if (seconds <= 0) return "Assistir"
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    val time = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
    return "Continuar de $time"
}
