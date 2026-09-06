package com.brasa.tv.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.*
import com.brasa.tv.designsystem.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class NavigationRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoresGenreSortAndSelectedCardAfterDetails() {
        val profile = Profile(id = "test")
        val items = listOf(CatalogItem(id="a", mediaKey="movie:a", title="Filme Z", genres=listOf("Aventura")),
            CatalogItem(id="b", mediaKey="movie:b", title="Filme A", genres=listOf("Aventura")),
            CatalogItem(id="c", mediaKey="movie:c", title="Filme C", genres=listOf("Drama")))
        val state = BrasaUiState(profile = profile, catalog = CatalogResponse(profile = profile, movies = items))
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            var details by remember { mutableStateOf(false) }
            BrasaTheme {
                if (details) BrasaButton("Voltar aos filmes", { details = false })
                else holder.SaveableStateProvider("library") {
                    LibraryScreen(state, "movie", onItem={details=true}, onHome={},onMovies={},onSeries={},onCollections={},onSearch={},onProfiles={},onRefresh={})
                }
            }
        }
        compose.onNodeWithText("Aventura").performClick()
        compose.onNodeWithText("Ordem: Padrão").performClick()
        compose.onNodeWithText("Filme C").assertDoesNotExist()
        compose.onNodeWithText("Filme Z").performClick()
        compose.onNodeWithText("Voltar aos filmes").performClick()
        compose.onNodeWithText("Ordem: Nome A–Z").assertExists()
        compose.onNodeWithText("Filme C").assertDoesNotExist()
        compose.onNodeWithText("Filme Z").assertIsFocused()
    }

    @Test fun restoresFocusToKeyEvenWhenItemsChangeOrder() {
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            var details by remember { mutableStateOf(false) }
            var reversed by remember { mutableStateOf(false) }
            BrasaTheme {
                if (details) BrasaButton("Voltar", { reversed=true;details=false })
                else holder.SaveableStateProvider("cards") {
                    val memory = rememberCatalogFocus("test")
                    Column {
                        (if(reversed) listOf("B","A") else listOf("A","B")).forEach { id ->
                            BrasaButton(id, {memory.select(id);details=true},memory.modifier(id))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("B").performClick()
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithText("B").assertIsFocused()
    }

    @Test fun restoresScrolledLibraryAndSelectedCard() {
        val profile = Profile(id = "scroll-test")
        val movies = (1..80).map { CatalogItem(id="$it", mediaKey="movie:$it", title="Título $it") }
        val state = BrasaUiState(profile=profile, catalog=CatalogResponse(profile=profile, movies=movies))
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            var details by remember { mutableStateOf(false) }
            BrasaTheme {
                if (details) BrasaButton("Voltar à posição", { details=false })
                else holder.SaveableStateProvider("scrolled-library") {
                    LibraryScreen(state,"movie",onItem={details=true},onHome={},onMovies={},onSeries={},onCollections={},onSearch={},onProfiles={},onRefresh={})
                }
            }
        }
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(64)
        compose.onNodeWithText("Título 65").performClick()
        compose.onNodeWithText("Voltar à posição").performClick()
        compose.onNodeWithText("Título 65").assertIsDisplayed().assertIsFocused()
    }
}
