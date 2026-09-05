package com.brasa.tv.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaTextField
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat
import com.brasa.tv.designsystem.LocalCardDensity

@Composable
fun SearchScreen(
    state: BrasaUiState,
    onSearch: (String) -> Unit,
    onItem: (CatalogItem) -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onCollections: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(state.profile?.id) { if (state.profile != null) onRefresh() }
    var query by remember { mutableStateOf("") }
    var selectedGenre by remember(state.profile?.id) { mutableStateOf("Todos") }
    val catalogItems = remember(state.catalog) { state.catalog?.let { it.movies + it.series }.orEmpty() }
    val genres = remember(catalogItems) { catalogItems.flatMap(CatalogItem::genres).filter(String::isNotBlank).distinct().sorted() }
    val results = remember(query, selectedGenre, state.searchResults, catalogItems) {
        val base = if (query.isBlank()) {
            if (selectedGenre == "Todos") emptyList() else catalogItems
        } else state.searchResults
        if (selectedGenre == "Todos") base else base.filter { selectedGenre in it.genres }
    }
    val cardDensity = LocalCardDensity.current

    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = BrasaSpacing.safe)) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = "Buscar",
            onHome = onBack,
            onMovies = onMovies,
            onSeries = onSeries,
            onCollections = onCollections,
            onSearch = {},
            profileInitials = state.profile?.initials.orEmpty(),
        )
        Spacer(Modifier.height(26.dp))
        Text("Buscar na biblioteca", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Digite um título ou escolha um gênero usando o controle remoto.", color = BrasaTextMuted, fontSize = BrasaType.body)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrasaTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                modifier = Modifier.width(720.dp),
                placeholder = "⌕  Buscar filmes, séries e episódios",
            )
            if (query.isNotBlank()) BrasaButton("Limpar", { query = ""; onSearch("") })
        }
        Spacer(Modifier.height(18.dp))
        Text("Buscar por gênero", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listOf("Todos") + genres, key = { it }) { genre ->
                BrasaButton(genre, { selectedGenre = genre }, style = if (selectedGenre == genre) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
            }
        }
        Spacer(Modifier.height(14.dp))
        if ((query.isNotBlank() || selectedGenre != "Todos") && results.isEmpty()) {
            Text("Nenhum conteúdo encontrado para esta busca.", color = BrasaTextMuted, fontSize = BrasaType.body)
        } else if (query.isNotBlank() || selectedGenre != "Todos") {
            Text("${results.size} resultado(s)", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Adaptive(188.dp * cardDensity),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(results, key = { it.mediaKey }) { item ->
                MediaCard(item, { onItem(item) }, format = if (item.type == "series") MediaCardFormat.Landscape else MediaCardFormat.Poster)
            }
        }
    }
}
