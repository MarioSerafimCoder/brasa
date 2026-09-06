package com.brasa.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.brasa.tv.core.model.Collection
import com.brasa.tv.designsystem.BrasaBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaFocus
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaType
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat
import com.brasa.tv.designsystem.MessagePanel

@Composable
fun CollectionsScreen(
    state: BrasaUiState,
    onItem: (CatalogItem) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onProfiles: () -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalog
    var selectedId by rememberSaveable(state.profile?.id) { mutableStateOf<String?>(null) }
    val overviewGridState = rememberLazyGridState()
    val movieGridState = rememberSaveable(selectedId, saver = LazyGridState.Saver) { LazyGridState() }
    val focusMemory = rememberCatalogFocus("collections:${state.profile?.id}")
    val selected = catalog?.collections?.firstOrNull { it.id == selectedId }
    BackHandler { if (selected != null) { focusMemory.restore("collection:${selected.id}"); selectedId = null } else onHome() }
    LaunchedEffect(state.profile?.id) { if (state.profile != null) onRefresh() }

    if (catalog == null) {
        MessagePanel("Carregando coleções", "Organizando as franquias e sagas disponíveis.", "Tentar novamente", onRefresh)
        return
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF111926), BrasaBackground)),
        ).padding(horizontal = BrasaSpacing.safe),
    ) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = "Coleções",
            onHome = onHome,
            onMovies = onMovies,
            onSeries = onSeries,
            onCollections = {},
            onSearch = onSearch,
            onProfiles = onProfiles,
            profileInitials = state.profile?.initials.orEmpty(),
        )
        Spacer(Modifier.height(22.dp))
        if (selected == null) {
            Text("Coleções", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
            Text("${catalog.collections.size} franquias e sagas organizadas", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(3),
                state = overviewGridState,
                contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(catalog.collections, key = Collection::id) { collection ->
                    CollectionCard(collection, modifier = focusMemory.modifier("collection:${collection.id}")) { focusMemory.select("collection:${collection.id}"); selectedId = collection.id }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(selected.title, color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selected.subtitle, color = BrasaTextMuted, fontSize = BrasaType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${selected.items.size} filmes", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(8),
                state = movieGridState,
                contentPadding = PaddingValues(bottom = BrasaSpacing.x8),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(selected.items, key = { it.mediaKey.ifBlank { it.id } }) { movie ->
                    MediaCard(movie, { focusMemory.select(movie.mediaKey); onItem(movie) }, modifier = focusMemory.modifier(movie.mediaKey), format = MediaCardFormat.CompactPoster)
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: Collection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, tween(160), label = "collectionScale")
    val artwork = collection.banner.ifBlank { collection.items.firstOrNull()?.backdrop ?: collection.items.firstOrNull()?.poster.orEmpty() }
    Box(
        modifier.fillMaxWidth().aspectRatio(2.35f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 12.dp else 2.dp, RoundedCornerShape(14.dp))
            .background(BrasaBackground, RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(artwork, collection.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BrasaBackground.copy(alpha = .25f), BrasaBackground.copy(alpha = .96f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(collection.title, color = BrasaText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${collection.items.size} filmes  ·  ${collection.subtitle}", color = BrasaTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
