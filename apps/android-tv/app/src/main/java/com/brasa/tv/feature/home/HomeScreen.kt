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
import androidx.compose.runtime.remember
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
    val hero = remember(home) {
        home.rows.firstOrNull { it.id.contains("continue", ignoreCase = true) }?.items?.firstOrNull()
            ?: home.rows.firstNotNullOfOrNull { it.items.firstOrNull() }
    }
    val heroFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(hero?.mediaKey, state.profile?.id) {
        listState.scrollToItem(0)
        if (hero != null) {
            onPrefetch(hero)
            runCatching { heroFocus.requestFocus() }
            delay(80)
            listState.scrollToItem(0)
        }
    }

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
                                { onPlay(hero) },
                                Modifier.focusRequester(heroFocus),
                                style = BrasaButtonStyle.Primary,
                                leading = "▶",
                            )
                            BrasaButton("Detalhes", { onItem(hero) }, leading = "ⓘ")
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
                    onAction = { onSeeMore(row) },
                )
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = BrasaSpacing.safe, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (posters) 18.dp else 16.dp),
                ) {
                    items(row.items, key = { it.mediaKey.ifBlank { it.id } }) { item ->
                        MediaCard(item, { onItem(item) }, format = if (posters) MediaCardFormat.Poster else MediaCardFormat.Landscape, onFocused = { onPrefetch(item) })
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
