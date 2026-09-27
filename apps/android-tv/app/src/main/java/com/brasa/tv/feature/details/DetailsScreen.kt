package com.brasa.tv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.platform.testTag
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
import com.brasa.tv.core.model.isWatched
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaIcon
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
@OptIn(ExperimentalLayoutApi::class)
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
    val detailsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val episodesState = androidx.compose.foundation.lazy.rememberLazyListState()
    val episodeKeys = selectedSeason?.episodes.orEmpty().map { it.mediaKey }
    val similar = state.catalog?.let { catalog -> (catalog.movies + catalog.series).filter { candidate -> candidate.mediaKey != item.mediaKey && candidate.genres.any(item.genres::contains) }.take(12) }.orEmpty()
    val similarState = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonState = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonKeys = item.seasons.map { "season:" + it.seasonNumber }
    val similarKeys = similar.map { "similar:" + it.mediaKey }
    val focusKeys = listOf("play", "more") + seasonKeys + episodeKeys + similarKeys
    focusMemory.RestoreItems(focusKeys) { index ->
        val key = focusKeys[index]
        val section = when { key in similarKeys -> if (item.seasons.isEmpty()) 1 else 2; key in seasonKeys || key in episodeKeys -> 1; else -> 0 }
        if (detailsListState.layoutInfo.visibleItemsInfo.none { it.index == section }) detailsListState.scrollToItem(section)
        val row = when { key in similarKeys -> similarState to similarKeys.indexOf(key); key in episodeKeys -> episodesState to episodeKeys.indexOf(key); key in seasonKeys -> seasonState to seasonKeys.indexOf(key); else -> null }
        row?.let { (list, target) -> if (list.layoutInfo.visibleItemsInfo.none { it.index == target }) list.scrollToItem(target) }
    }
    val firstPlayable = if (item.type == "series") continuation else item
    var keepPreload by remember(item.mediaKey) { mutableStateOf(false) }
    var expandedOverview by rememberSaveable(item.mediaKey) { mutableStateOf(false) }
    var showMoreOptions by rememberSaveable(item.mediaKey) { mutableStateOf(false) }

    LaunchedEffect(item.mediaKey, selectedSeasonNumber) {
        onPrefetch(firstPlayable)
        if (!initialFocusSet && focusMemory.selectedKey.isBlank()) {
            withFrameNanos { }
            initialFocusSet = runCatching { playFocus.requestFocus() }.getOrDefault(false)
            if (initialFocusSet) {
                withFrameNanos { }
                detailsListState.scrollToItem(0)
            }
        }
    }
    DisposableEffect(item.mediaKey) { onDispose { if (!keepPreload) onCancelPreload() } }

    LazyColumn(
        state = detailsListState,
        modifier = Modifier.fillMaxSize().background(BrasaBackground),
        contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
    ) {
        item {
            Box(Modifier.fillMaxWidth().heightIn(min = if (item.type == "series") 530.dp else 560.dp).testTag("details-hero")) {
                AsyncImage(
                    model = item.backdrop.ifBlank { item.poster },
                    contentDescription = item.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().testTag("details-scrim").background(Brush.horizontalGradient(listOf(BrasaBackground, BrasaBackground.copy(alpha = .93f), BrasaBackground.copy(alpha = .22f), Color.Transparent))))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(BrasaBackground.copy(alpha = .08f), Color.Transparent, BrasaBackground))))
                Row(Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrasaButton("Voltar", onBack, style = BrasaButtonStyle.Ghost, leadingIcon = BrasaIcon.Back)
                    Spacer(Modifier.width(16.dp))
                    BrasaLogo()
                }
                Column(
                    Modifier.align(Alignment.TopStart).widthIn(max = 760.dp).padding(start = BrasaSpacing.safe, top = 102.dp, bottom = 34.dp),
                ) {
                    Text(metadata(item), color = BrasaTextMuted, fontSize = BrasaType.metadata, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(item.title, color = Color.White, fontSize = BrasaType.hero, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.genres.isNotEmpty()) {
                        Spacer(Modifier.height(13.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item.genres.take(4).forEach { GenreChip(it) } }
                    }
                    Spacer(Modifier.height(20.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrasaButton(
                            continueLabel(item, firstPlayable),
                            { focusMemory.select("play"); keepPreload = true; if (firstPlayable.isWatched()) onPlayFromStart(firstPlayable) else onPlay(firstPlayable) },
                            focusMemory.modifier("play").focusRequester(playFocus),
                            enabled = firstPlayable.streamUrl.isNotBlank(),
                            style = BrasaButtonStyle.Primary,
                            leadingIcon = BrasaIcon.Play,
                        )
                        BrasaButton(if (item.favorite || item.inMyList) "Remover da lista" else "Minha lista", onFavorite, leadingIcon = if (item.favorite || item.inMyList) BrasaIcon.Check else BrasaIcon.Add)
                        BrasaButton("Mais opções", { focusMemory.select("more"); showMoreOptions = true }, focusMemory.modifier("more"), style = BrasaButtonStyle.Ghost, leadingIcon = BrasaIcon.More)
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(item.overview.ifBlank { "Sinopse ainda não disponível." }, color = BrasaText.copy(alpha = .88f), fontSize = BrasaType.body, lineHeight = 27.sp, maxLines = if (expandedOverview) 10 else 4, overflow = TextOverflow.Ellipsis)
                    if (item.overview.length > 220) BrasaButton(if (expandedOverview) "Recolher sinopse" else "Ler sinopse completa", { expandedOverview = !expandedOverview }, style = BrasaButtonStyle.Ghost)
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
                    state = seasonState,
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(item.seasons, key = Season::seasonNumber) { season ->
                        BrasaButton(
                            "Temporada ${season.seasonNumber.toString().padStart(2, '0')}  ·  ${season.episodes.size} episódios",
                            { selectedSeasonNumber = season.seasonNumber },
                            modifier = focusMemory.modifier("season:" + season.seasonNumber),
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
                    state = episodesState,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(selectedSeason?.episodes.orEmpty(), key = { it.mediaKey }) { episode ->
                        EpisodeCard(episode, highlighted = episode.mediaKey == continuation.mediaKey, modifier = focusMemory.modifier(episode.mediaKey), onFocused = { onPrefetch(episode) }) {
                            focusMemory.select(episode.mediaKey)
                            keepPreload = true
                            onPlay(episode)
                        }
                    }
                }
            }
        }
        if (similar.isNotEmpty()) item {
            Text("Títulos semelhantes", modifier = Modifier.padding(horizontal = BrasaSpacing.safe), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            LazyRow(state = similarState, contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(similar, key = { it.mediaKey }) { candidate -> com.brasa.tv.designsystem.MediaCard(candidate, { focusMemory.select("similar:" + candidate.mediaKey); keepPreload = true; onPlay(candidate.playableItem()) }, modifier = focusMemory.modifier("similar:" + candidate.mediaKey), format = if (candidate.type == "series") com.brasa.tv.designsystem.MediaCardFormat.Landscape else com.brasa.tv.designsystem.MediaCardFormat.Poster, onFocused = { onPrefetch(candidate.playableItem()) }) }
            }
        }
    }
    if (showMoreOptions) MoreOptionsDialog(item, onSignal,
        onPlayFromStart = { showMoreOptions = false; focusMemory.select("more"); keepPreload = true; onPlayFromStart(firstPlayable) },
        onDismiss = { showMoreOptions = false; focusMemory.restore("more") })
}

@Composable
private fun MoreOptionsDialog(item: CatalogItem, onSignal: (String, Boolean) -> Unit, onPlayFromStart: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(580.dp).heightIn(max = maxHeight - 32.dp).background(BrasaSurface, RoundedCornerShape(20.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState()).padding(28.dp)) {
                Text("Mais opções", color = BrasaText, fontSize = BrasaType.section, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                BrasaButton("Assistir do início", onPlayFromStart, Modifier.fillMaxWidth().focusRequester(first), leadingIcon = BrasaIcon.Play)
                Spacer(Modifier.height(8.dp))
                BrasaButton(if (item.reaction == "like") "Gostei — Ativado" else "Gostei — Desativado", { onSignal("like", item.reaction != "like"); onDismiss() }, Modifier.fillMaxWidth(), style = if (item.reaction == "like") BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary)
                Spacer(Modifier.height(8.dp))
                BrasaButton(if (item.reaction == "not-for-me") "Não é para mim — Ativado" else "Não é para mim — Desativado", { onSignal("not-for-me", item.reaction != "not-for-me"); onDismiss() }, Modifier.fillMaxWidth(), style = if (item.reaction == "not-for-me") BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary)
                Spacer(Modifier.height(8.dp))
                BrasaButton(if (item.hiddenSuggestion) "Restaurar sugestão" else "Ocultar sugestão", { onSignal("hide", !item.hiddenSuggestion); onDismiss() }, Modifier.fillMaxWidth())
                if ((item.progress?.percentage ?: 0.0) > 0.0 || item.type == "series") {
                    Spacer(Modifier.height(8.dp))
                    BrasaButton("Remover de Continuar assistindo", { onSignal("dismiss-continue", true); onDismiss() }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    BrasaButton("Marcar como assistido", { onSignal("mark-watched", true); onDismiss() }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(14.dp))
                BrasaButton("Fechar", onDismiss, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Ghost)
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
}

@Composable
private fun EpisodeCard(episode: CatalogItem, highlighted: Boolean, modifier: Modifier = Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, episode.mediaKey) { if (focused) { delay(180); onFocused() } }
    Column(
        modifier.width(330.dp).graphicsLayer { scaleX = if (focused) 1.035f else 1f; scaleY = if (focused) 1.035f else 1f }
            .background(if (highlighted) BrasaSurface.copy(alpha = .98f) else BrasaSurface, RoundedCornerShape(16.dp))
            .border(if (focused || highlighted) 3.dp else 1.dp, if (focused) BrasaFocus else if (highlighted) BrasaOrange else BrasaBorder, RoundedCornerShape(16.dp))
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
            val watched = episode.isWatched()
            if (watched) Text("ASSISTIDO", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(BrasaBackground.copy(alpha = .9f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp), color = BrasaText, fontSize = 11.sp, fontWeight = FontWeight.Black)
            else if (highlighted) Text("CONTINUAR", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(BrasaOrange, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
            episode.progress?.takeIf { it.percentage > 0 }?.let { progress ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = .2f))) {
                    Box(Modifier.fillMaxWidth((progress.percentage / 100).toFloat().coerceIn(0f, 1f)).height(5.dp).background(BrasaOrange))
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 116.dp).padding(13.dp)) {
            Text(episode.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(episode.overview.ifBlank { "Resumo sem spoilers em preparação." }, color = BrasaTextMuted, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val remaining = episode.progress?.takeIf { it.duration > 0 }?.let { kotlin.math.ceil((it.duration - it.currentTime).coerceAtLeast(0.0) / 60).toInt() } ?: episode.remainingMinutes
            if (remaining != null && remaining > 0 && !episode.isWatched()) {
                Spacer(Modifier.height(5.dp))
                Text("Faltam $remaining min", color = if (highlighted) BrasaOrange else BrasaTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun continueLabel(parent: CatalogItem, item: CatalogItem): String {
    if (item.isWatched()) return "Assistir novamente"
    if (parent.actionLabel.isNotBlank()) return parent.actionLabel + (parent.remainingMinutes?.let { " — faltam $it min" } ?: "")
    val seconds = item.progress?.currentTime?.toLong() ?: 0L
    if (seconds <= 0) return "Assistir"
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    val time = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
    return "Continuar de $time"
}
