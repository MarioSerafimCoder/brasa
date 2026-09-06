package com.brasa.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import com.brasa.tv.core.model.HomeRow
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat
import com.brasa.tv.designsystem.MessagePanel
import com.brasa.tv.designsystem.SectionHeading
import com.brasa.tv.designsystem.metadata
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: BrasaUiState,
    onItem: (CatalogItem) -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onPrefetch: (CatalogItem) -> Unit,
    onSearch: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onCollections: () -> Unit,
    onProfiles: () -> Unit,
    onSettings: () -> Unit,
    onSeeMore: (HomeRow) -> Unit,
    onRefresh: () -> Unit,
) {
    val home = state.home
    if (home == null) {
        MessagePanel(
            "Carregando sua biblioteca",
            state.message.ifBlank { "O computador precisa estar ligado e conectado à mesma rede." },
            "Tentar novamente",
            onRefresh,
        )
        return
    }
    if (state.profile?.kind == "kids") {
        KidsHomeScreen(state, onItem, onPlay, onPrefetch, onSearch, onMovies, onSeries, onCollections, onProfiles, onSeeMore)
        return
    }
    val heroCandidates = remember(home, state.profile?.id) {
        home.rows.flatMap(HomeRow::items)
            .filter { it.backdrop.isNotBlank() || it.poster.isNotBlank() }
            .distinctBy { it.mediaKey.ifBlank { it.id } }
            .shuffled()
    }
    var heroIndex by remember(home, state.profile?.id) { mutableIntStateOf(0) }
    val hero = heroCandidates.getOrNull(heroIndex % heroCandidates.size.coerceAtLeast(1))
    val heroFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusMemory = rememberCatalogFocus("home:${state.profile?.id}")
    var initialFocusSet by rememberSaveable(state.profile?.id) { mutableStateOf(false) }
    LaunchedEffect(heroCandidates, state.profile?.id) {
        if (heroCandidates.size > 1) while (true) {
            delay(12_000)
            heroIndex = (heroIndex + 1) % heroCandidates.size
        }
    }
    LaunchedEffect(state.profile?.id, heroCandidates.isNotEmpty()) {
        if (initialFocusSet) return@LaunchedEffect
        listState.scrollToItem(0)
        if (hero != null) {
            runCatching { heroFocus.requestFocus() }
            delay(80)
            listState.scrollToItem(0)
            initialFocusSet = true
        }
    }
    LaunchedEffect(hero?.mediaKey) { hero?.let(onPrefetch) }

    LazyColumn(
        Modifier.fillMaxSize().background(BrasaBackground),
        state = listState,
        contentPadding = PaddingValues(bottom = 54.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                if (hero != null) {
                    AsyncImage(
                        model = hero.backdrop.ifBlank { hero.poster },
                        contentDescription = hero.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(BrasaBackground, BrasaBackground.copy(alpha = .9f), BrasaBackground.copy(alpha = .25f), Color.Transparent),
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(BrasaBackground.copy(alpha = .15f), Color.Transparent, BrasaBackground)),
                    ),
                )
                BrasaTopBar(
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = BrasaSpacing.safe, vertical = BrasaSpacing.x2),
                    onHome = {},
                    onMovies = onMovies,
                    onSeries = onSeries,
                    onCollections = onCollections,
                    onSearch = onSearch,
                    onProfiles = onProfiles,
                    onSettings = onSettings,
                    profileInitials = state.profile?.initials.orEmpty(),
                )
                if (hero != null) {
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(start = BrasaSpacing.safe, top = 56.dp).width(570.dp),
                    ) {
                        Text(metadata(hero), color = BrasaTextMuted, fontSize = BrasaType.metadata, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            hero.title,
                            color = Color.White,
                            fontSize = BrasaType.hero,
                            lineHeight = 51.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            hero.overview,
                            color = BrasaText.copy(alpha = .86f),
                            fontSize = BrasaType.body,
                            lineHeight = 24.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(21.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrasaButton(
                                continueLabel(hero),
                                { focusMemory.select("hero-play"); onPlay(hero) },
                                focusMemory.modifier("hero-play").focusRequester(heroFocus),
                                style = BrasaButtonStyle.Primary,
                                leading = "▶",
                            )
                            BrasaButton("Detalhes", { focusMemory.select("hero-details"); onItem(hero) }, modifier = focusMemory.modifier("hero-details"), leading = "ⓘ")
                        }
                    }
                }
            }
        }
        items(home.rows, key = { it.id }) { row ->
            if (row.items.isNotEmpty()) {
                val posters = row.id.contains("all", ignoreCase = true) || row.title.contains("Todos", ignoreCase = true)
                SectionHeading(
                    row.title,
                    Modifier.padding(start = BrasaSpacing.safe, end = BrasaSpacing.safe, top = BrasaSpacing.x2, bottom = BrasaSpacing.x2),
                    action = "Veja mais  ›",
                    onAction = { focusMemory.select("row:${row.id}"); onSeeMore(row) },
                    actionModifier = focusMemory.modifier("row:${row.id}"),
                )
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (posters) 18.dp else 16.dp),
                ) {
                    items(row.items, key = { it.mediaKey.ifBlank { it.id } }) { item ->
                        val key = "${row.id}:${item.mediaKey.ifBlank { item.id }}"
                        MediaCard(item, { focusMemory.select(key); onItem(item) }, modifier = focusMemory.modifier(key), format = if (posters) MediaCardFormat.Poster else MediaCardFormat.Landscape, onFocused = { onPrefetch(item) })
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

private fun continueLabel(item: CatalogItem): String {
    val seconds = item.progress?.currentTime?.toLong() ?: 0L
    if (seconds <= 0) return "Assistir"
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    val time = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
    return "Continuar de $time"
}
