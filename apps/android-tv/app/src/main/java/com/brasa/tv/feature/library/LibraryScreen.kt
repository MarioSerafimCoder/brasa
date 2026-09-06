package com.brasa.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.LocalCardDensity
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat
import com.brasa.tv.designsystem.MessagePanel

@Composable
fun LibraryScreen(
    state: BrasaUiState,
    type: String,
    onItem: (CatalogItem) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onCollections: () -> Unit,
    onSearch: () -> Unit,
    onProfiles: () -> Unit,
    onRefresh: () -> Unit,
) {
    BackHandler(onBack = onHome)
    val catalog = state.catalog
    LaunchedEffect(type, state.profile?.id) { if (state.profile != null) onRefresh() }
    if (catalog == null) {
        MessagePanel("Carregando biblioteca", "Buscando os títulos disponíveis no computador.", "Tentar novamente", onRefresh)
        return
    }
    val title = if (type == "series") "Séries" else "Filmes"
    val source = if (type == "series") catalog.series else catalog.movies
    val genres = remember(source) { source.flatMap(CatalogItem::genres).filter(String::isNotBlank).distinct().sorted() }
    var selectedGenre by rememberSaveable(type, state.profile?.id) { mutableStateOf("Todos") }
    var order by rememberSaveable(type, state.profile?.id) { mutableStateOf(CatalogOrder.ORIGINAL) }
    var unwatchedOnly by rememberSaveable(type, state.profile?.id) { mutableStateOf(false) }
    val focusMemory = rememberCatalogFocus("$type:${state.profile?.id}")
    val items = remember(source, selectedGenre, order, unwatchedOnly) {
        orderCatalog(if (selectedGenre == "Todos") source else source.filter { selectedGenre in it.genres }, order, unwatchedOnly)
    }
    val kids = state.profile?.kind == "kids"
    val cardDensity = LocalCardDensity.current

    Column(
        Modifier.fillMaxSize().background(
            if (kids) Brush.radialGradient(listOf(Color(0xFF17386A), Color(0xFF07152A), BrasaBackground), radius = 1200f)
            else Brush.verticalGradient(listOf(Color(0xFF111926), BrasaBackground)),
        ).padding(horizontal = BrasaSpacing.safe),
    ) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = title,
            onHome = onHome,
            onMovies = onMovies,
            onSeries = onSeries,
            onCollections = onCollections,
            onSearch = onSearch,
            onProfiles = onProfiles,
            profileInitials = state.profile?.initials.orEmpty(),
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 24.dp)) {
                Text(
                    if (kids) if (type == "series") "Séries para se divertir" else "Filmes para toda aventura" else title,
                    color = Color.White,
                    fontSize = BrasaType.page,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("${items.size} ${if (items.size == 1) "título" else "títulos"}", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            ) {
                items(listOf("Todos") + genres, key = { it }) { genre ->
                    BrasaButton(
                        genre,
                        { selectedGenre = genre },
                        style = if (selectedGenre == genre) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrasaButton("Ordem: ${order.label}", { order = CatalogOrder.entries[(order.ordinal + 1) % CatalogOrder.entries.size] })
            BrasaButton(if (unwatchedOnly) "✓ Não assistidos" else "Não assistidos", { unwatchedOnly = !unwatchedOnly },
                style = if (unwatchedOnly) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = if (type == "movie") GridCells.Fixed(8) else GridCells.Adaptive(188.dp * cardDensity),
            contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
            horizontalArrangement = Arrangement.spacedBy(if (type == "movie") 10.dp else BrasaSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(if (type == "movie") 14.dp else BrasaSpacing.x4),
        ) {
            items(items, key = { it.mediaKey.ifBlank { it.id } }) { item ->
                val key = item.mediaKey.ifBlank { item.id }
                MediaCard(item, { focusMemory.select(key); onItem(item) }, modifier = focusMemory.modifier(key), format = if (type == "series") MediaCardFormat.Landscape else MediaCardFormat.CompactPoster)
            }
        }
    }
}
