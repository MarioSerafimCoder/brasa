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
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.MediaCard
import com.brasa.tv.designsystem.MediaCardFormat
import com.brasa.tv.designsystem.SectionHeading
import kotlinx.coroutines.delay

private val KidsBackground = Color(0xFF07152A)
private val KidsBlue = Color(0xFF36B8FF)
private val KidsPurple = Color(0xFF7758FF)
private val KidsYellow = Color(0xFFFFD95A)

@Composable
fun KidsHomeScreen(
    state: BrasaUiState,
    onItem: (CatalogItem) -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onPrefetch: (CatalogItem) -> Unit,
    onSearch: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onCollections: () -> Unit,
    onProfiles: () -> Unit,
    onSeeMore: (HomeRow) -> Unit,
) {
    val home = state.home ?: return
    val heroes = remember(home, state.profile?.id) {
        home.rows.flatMap(HomeRow::items)
            .filter { it.backdrop.isNotBlank() || it.poster.isNotBlank() }
            .distinctBy { it.mediaKey.ifBlank { it.id } }
            .shuffled()
    }
    var heroIndex by remember(home, state.profile?.id) { mutableIntStateOf(0) }
    val hero = heroes.getOrNull(heroIndex % heroes.size.coerceAtLeast(1))
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusMemory = rememberCatalogFocus("kids-home:${state.profile?.id}")
    var initialFocusSet by rememberSaveable(state.profile?.id) { mutableStateOf(false) }
    LaunchedEffect(heroes, state.profile?.id) {
        if (heroes.size > 1) while (true) {
            delay(12_000)
            heroIndex = (heroIndex + 1) % heroes.size
        }
    }
    LaunchedEffect(state.profile?.id, heroes.isNotEmpty()) {
        if (initialFocusSet || heroes.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(0)
        delay(80)
        runCatching { firstFocus.requestFocus() }
        initialFocusSet = true
    }
    LaunchedEffect(hero?.mediaKey) { hero?.let(onPrefetch) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF17386A), KidsBackground, KidsBackground), radius = 1250f),
        ),
        state = listState,
        contentPadding = PaddingValues(bottom = 54.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = BrasaSpacing.safe, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrasaLogo()
                    Spacer(Modifier.width(24.dp))
                    Text("Oi, ${state.profile?.name ?: "Laura"}! ✨", color = KidsYellow, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    BrasaButton("Filmes", onMovies, style = BrasaButtonStyle.Ghost)
                    BrasaButton("Séries", onSeries, style = BrasaButtonStyle.Ghost)
                    BrasaButton("Coleções", onCollections, style = BrasaButtonStyle.Ghost)
                    BrasaButton("Buscar", onSearch, style = BrasaButtonStyle.Ghost, leading = "⌕")
                    BrasaButton(state.profile?.initials.orEmpty().ifBlank { "L" }, onProfiles, style = BrasaButtonStyle.Secondary)
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(330.dp).background(
                        Brush.linearGradient(listOf(KidsPurple.copy(alpha = .72f), KidsBlue.copy(alpha = .34f), Color.Transparent)),
                        androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    ),
                ) {
                    if (hero != null) {
                        AsyncImage(
                            model = hero.backdrop.ifBlank { hero.poster },
                            contentDescription = hero.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(KidsBackground.copy(alpha = .98f), KidsBackground.copy(alpha = .72f), Color.Transparent))))
                        Column(Modifier.align(Alignment.CenterStart).padding(34.dp).width(520.dp)) {
                            Text("UMA AVENTURA PARA VOCÊ", color = KidsYellow, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(7.dp))
                            Text(hero.title, color = Color.White, fontSize = 43.sp, lineHeight = 47.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(12.dp))
                            Text(hero.overview, color = Color.White.copy(alpha = .86f), fontSize = 17.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BrasaButton("Assistir", { focusMemory.select("hero-play"); onPlay(hero) }, focusMemory.modifier("hero-play").focusRequester(firstFocus), style = BrasaButtonStyle.Primary, leading = "▶")
                                BrasaButton("Ver detalhes", { focusMemory.select("hero-details"); onItem(hero) }, modifier = focusMemory.modifier("hero-details"))
                            }
                        }
                    }
                }
            }
        }
        items(home.rows, key = { "kids-${it.id}" }) { row ->
            if (row.items.isNotEmpty()) {
                SectionHeading(
                    kidsTitle(row.title),
                    Modifier.padding(start = BrasaSpacing.safe, end = BrasaSpacing.safe, top = 18.dp, bottom = 8.dp),
                    action = "Ver tudo  ›",
                    onAction = { focusMemory.select("row:${row.id}"); onSeeMore(row) },
                    actionModifier = focusMemory.modifier("row:${row.id}"),
                )
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(row.items, key = { it.mediaKey.ifBlank { it.id } }) { item ->
                        val key = "${row.id}:${item.mediaKey.ifBlank { item.id }}"
                        MediaCard(item, { focusMemory.select(key); onItem(item) }, modifier = focusMemory.modifier(key), format = MediaCardFormat.Landscape, onFocused = { onPrefetch(item) })
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun kidsTitle(value: String) = when {
    value.contains("continuar", true) -> "Continuar a diversão"
    value.contains("recent", true) || value.contains("adicionado", true) -> "Novas aventuras"
    value.contains("filme", true) -> "Filmes para curtir"
    value.contains("série", true) -> "Séries para maratonar"
    value.contains("lista", true) || value.contains("favorit", true) -> "Meus favoritos"
    else -> value
}
