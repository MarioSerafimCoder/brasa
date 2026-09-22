package com.brasa.tv.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.data.storage.AppSettingsStore
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
import com.brasa.tv.designsystem.BrasaIcon
import com.brasa.tv.core.model.genreIdentity
import com.brasa.tv.core.model.genreLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SearchScreen(
    state: BrasaUiState,
    settingsStore: AppSettingsStore,
    onSearch: (String) -> Unit,
    onItem: (CatalogItem) -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onCollections: () -> Unit,
    onMyList: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(state.profile?.id) { if (state.profile != null) onRefresh() }
    var query by rememberSaveable(state.profile?.id) { mutableStateOf("") }
    var selectedGenre by rememberSaveable(state.profile?.id) { mutableStateOf("Todos") }
    val deviceSettings by settingsStore.values.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceIntent = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); putExtra(RecognizerIntent.EXTRA_PROMPT, "O que você quer assistir?") } }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken -> query = spoken; onSearch(spoken); scope.launch { state.profile?.id?.let { settingsStore.addRecentSearch(it, spoken) } } }
    }
    val focusMemory = rememberCatalogFocus("search:${state.profile?.id}")
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(state.profile?.id) {
        if (focusMemory.selectedKey.isBlank()) {
            withFrameNanos { }
            searchFocus.requestFocus()
        }
    }
    LaunchedEffect(state.profile?.id) { if (query.isNotBlank()) onSearch(query) }
    val catalogItems = remember(state.catalog) { state.catalog?.let { it.movies + it.series }.orEmpty() }
    val genres = remember(catalogItems) { catalogItems.flatMap(CatalogItem::genres).filter(String::isNotBlank).distinctBy(::genreIdentity).sortedBy(::genreLabel) }
    val results = remember(query, selectedGenre, state.searchResults, catalogItems) {
        val base = if (query.isBlank()) {
            if (selectedGenre == "Todos") catalogItems.take(12) else catalogItems
        } else state.searchResults
        if (selectedGenre == "Todos") base else base.filter { item -> item.genres.any { genreIdentity(it) == genreIdentity(selectedGenre) } }
    }
    val cardDensity = LocalCardDensity.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    focusMemory.RestoreItems(results.map { it.mediaKey }) { gridState.scrollToItem(it) }
    LaunchedEffect(query, state.profile?.id) { if (query.trim().length >= 2) { delay(800); state.profile?.id?.let { settingsStore.addRecentSearch(it, query) } } }

    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = BrasaSpacing.safe)) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = "Buscar",
            onHome = onHome,
            onMovies = onMovies,
            onSeries = onSeries,
            onCollections = onCollections,
            onMyList = onMyList,
            onSearch = {},
            profileInitials = state.profile?.initials.orEmpty(),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Buscar", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Filmes, séries e episódios", color = BrasaTextMuted, fontSize = BrasaType.metadata, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactSearch = this.maxWidth < 900.dp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrasaTextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    modifier = Modifier.weight(1f).focusRequester(searchFocus).testTag("search-field"),
                    placeholder = "Buscar na biblioteca",
                )
                if (query.isNotBlank()) BrasaButton("Limpar", { query = ""; onSearch("") })
                BrasaButton(if (compactSearch) "Voz" else "Busca por voz", { if (voiceAvailable) voiceLauncher.launch(voiceIntent) }, enabled = voiceAvailable, leadingIcon = BrasaIcon.Mic)
            }
        }
        if (!voiceAvailable) Text("A busca por voz não está disponível nesta TV; use o teclado ou os filtros.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
        if (query.isBlank() && deviceSettings.recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(deviceSettings.recentSearches.take(8), key = { it }) { recent -> BrasaButton(recent, { query = recent; onSearch(recent) }, style = BrasaButtonStyle.Ghost) }
                item { BrasaButton("Limpar histórico", { scope.launch { state.profile?.id?.let { settingsStore.clearRecentSearches(it) } } }, style = BrasaButtonStyle.Ghost) }
            }
        }
        Spacer(Modifier.height(9.dp))
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Todos") + genres, key = { it }) { genre ->
                BrasaButton(genreLabel(genre), { selectedGenre = genre }, style = if (genreIdentity(selectedGenre) == genreIdentity(genre)) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.searching) {
            Text("Buscando…", color = BrasaTextMuted, fontSize = BrasaType.body)
        } else if (state.searchError.isNotBlank()) {
            Text(state.searchError, color = Color(0xFFFF8A80), fontSize = BrasaType.body)
        } else if ((query.isNotBlank() || selectedGenre != "Todos") && results.isEmpty()) {
            Text("Nenhum conteúdo encontrado para esta busca.", color = BrasaTextMuted, fontSize = BrasaType.body)
        } else if (query.isNotBlank() || selectedGenre != "Todos") {
            Text("${results.size} resultado(s)", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Sugestões para começar", color = BrasaTextMuted, fontSize = BrasaType.metadata)
        }
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Adaptive(250.dp * cardDensity),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BrasaSpacing.x6),
        ) {
            items(results, key = { it.mediaKey }) { item ->
                MediaCard(item, { focusMemory.select(item.mediaKey); onItem(item) }, modifier = focusMemory.modifier(item.mediaKey), format = MediaCardFormat.Landscape)
            }
        }
    }
}
