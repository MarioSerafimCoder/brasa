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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    val cardDensity=LocalCardDensity.current
    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = BrasaSpacing.safe)) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = "Buscar",
            onHome = onBack,
            onSearch = {},
        )
        Spacer(Modifier.height(32.dp))
        Text("Buscar na biblioteca", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("Encontre filmes, séries e episódios disponíveis no computador.", color = BrasaTextMuted, fontSize = BrasaType.body)
        Spacer(Modifier.height(19.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrasaTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                modifier = Modifier.width(780.dp),
                placeholder = "⌕  Buscar filmes, séries e episódios",
            )
            if (query.isNotBlank()) BrasaButton("Limpar", { query = ""; onSearch("") })
        }
        Spacer(Modifier.height(24.dp))
        if (query.isNotBlank() && state.searchResults.isEmpty()) {
            Text("Nenhum conteúdo encontrado. Atualize a biblioteca no computador.", color = BrasaTextMuted, fontSize = BrasaType.body)
        } else if (query.isNotBlank()) {
            Text("${state.searchResults.size} resultado(s)", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(12.dp))
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Adaptive(188.dp*cardDensity),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.searchResults, key = { it.mediaKey }) {
                MediaCard(it, { onItem(it) }, format = MediaCardFormat.Poster)
            }
        }
    }
}
