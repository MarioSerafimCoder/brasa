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
import androidx.compose.runtime.saveable.rememberSaveable
import com.brasa.tv.designsystem.rememberCatalogFocus
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    LaunchedEffect(state.profile?.id) { if (query.isNotBlank()) onSearch(query) }
    val catalogItems = remember(state.catalog) { state.catalog?.let { it.movies + it.series }.orEmpty() }
    val genres = remember(catalogItems) { catalogItems.flatMap(CatalogItem::genres).filter(String::isNotBlank).distinct().sorted() }
    val results = remember(query, selectedGenre, state.searchResults, catalogItems) {
        val base = if (query.isBlank()) {
            if (selectedGenre == "Todos") catalogItems.take(12) else catalogItems
        } else state.searchResults
        if (selectedGenre == "Todos") base else base.filter { selectedGenre in it.genres }
    }
    val cardDensity = LocalCardDensity.current
    LaunchedEffect(query, state.profile?.id) { if (query.trim().length >= 2) { delay(800); state.profile?.id?.let { settingsStore.addRecentSearch(it, query) } } }

    Column(Modifier.fillMaxSize().background(BrasaBackground).padding(horizontal = BrasaSpacing.safe)) {
        BrasaTopBar(
            modifier = Modifier.padding(top = BrasaSpacing.x2),
            active = "Buscar",
            onHome = onBack,
            onMovies = onMovies,
            onSeries = onSeries,
            onCollections = onCollections,
            onMyList = onMyList,
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
            BrasaButton("Busca por voz", { if (voiceAvailable) voiceLauncher.launch(voiceIntent) }, enabled = voiceAvailable, leading = "🎙")
        }
        if (!voiceAvailable) Text("A busca por voz não está disponível nesta TV; use o teclado ou os filtros.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
        if (query.isBlank() && deviceSettings.recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                deviceSettings.recentSearches.take(5).forEach { recent -> BrasaButton(recent, { query = recent; onSearch(recent) }, style = BrasaButtonStyle.Ghost) }
                BrasaButton("Limpar buscas", { scope.launch { state.profile?.id?.let { settingsStore.clearRecentSearches(it) } } }, style = BrasaButtonStyle.Ghost)
            }
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
            modifier = Modifier.fillMaxWidth(),
            columns = GridCells.Adaptive(188.dp * cardDensity),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(results, key = { it.mediaKey }) { item ->
                MediaCard(item, { focusMemory.select(item.mediaKey); onItem(item) }, modifier = focusMemory.modifier(item.mediaKey), format = if (item.type == "series") MediaCardFormat.Landscape else MediaCardFormat.Poster)
            }
        }
    }
}
