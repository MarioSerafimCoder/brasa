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
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat

@Composable
fun SearchScreen(
    state: BrasaUiState,
    onSearch: (String) -> Unit,
    onItem: (CatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = 28.dp)) {
        BrasaTopBar(
            modifier = Modifier.padding(top = 16.dp),
            active = "Buscar",
            onHome = onBack,
            onSearch = {},
        )
        Spacer(Modifier.height(32.dp))
        Text("Buscar na biblioteca", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Text("Encontre filmes e séries disponíveis no seu computador.", color = BrasaTextMuted, fontSize = 17.sp)
        Spacer(Modifier.height(19.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrasaTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                modifier = Modifier.width(650.dp),
                placeholder = "Digite um título…",
            )
            if (query.isNotBlank()) BrasaButton("Limpar", { query = ""; onSearch("") })
            BrasaButton("Voltar", onBack, style = BrasaButtonStyle.Ghost)
        }
        Spacer(Modifier.height(24.dp))
        if (query.isNotBlank() && state.searchResults.isEmpty()) {
            Text("Nenhum conteúdo encontrado.", color = BrasaTextMuted, fontSize = 20.sp)
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Adaptive(164.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.searchResults, key = { it.mediaKey }) {
                MediaCard(it, { onItem(it) }, format = MediaCardFormat.Poster)
            }
        }
    }
}
