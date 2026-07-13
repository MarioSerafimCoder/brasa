package com.brasa.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat

@Composable
fun CatalogScreen(state: BrasaUiState, onItem: (CatalogItem) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val row = state.selectedRow
    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = BrasaSpacing.safe)) {
        BrasaTopBar(Modifier.padding(top = BrasaSpacing.x2), active = row?.title.orEmpty(), onHome = onBack, profileInitials = state.profile?.initials.orEmpty())
        Spacer(Modifier.height(BrasaSpacing.x4))
        Text(row?.title ?: "Biblioteca", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
        Text("${row?.items?.size ?: 0} títulos", color = BrasaTextMuted, fontSize = BrasaType.metadata)
        Spacer(Modifier.height(BrasaSpacing.x3))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(188.dp),
            contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
            horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(BrasaSpacing.x4),
        ) {
            items(row?.items.orEmpty(), key = { it.mediaKey.ifBlank { it.id } }) { item ->
                MediaCard(item, { onItem(item) }, format = MediaCardFormat.Poster)
            }
        }
    }
}
