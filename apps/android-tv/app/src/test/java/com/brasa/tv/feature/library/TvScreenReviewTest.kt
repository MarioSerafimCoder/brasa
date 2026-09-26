package com.brasa.tv.feature.library

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.*
import com.brasa.tv.data.storage.AppSettingsStore
import com.brasa.tv.designsystem.BrasaTheme
import com.brasa.tv.designsystem.BrasaTopBar
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.brasa.tv.feature.search.SearchScreen
import com.brasa.tv.feature.settings.SettingsScreen
import com.brasa.tv.feature.details.DetailsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi", application = Application::class)
class TvScreenReviewTest {
    @get:Rule val compose = createComposeRule()
    private val settings get() = AppSettingsStore(RuntimeEnvironment.getApplication())

    @Test fun detailsContrastLayerCoversTheWholeHeroAtEveryScale() {
        var scale by mutableStateOf(.8f)
        val movie = CatalogItem(mediaKey = "movie:contrast", title = "Título visível", streamUrl = "/test",
            genres = listOf("Ação", "Aventura"), overview = "Uma sinopse longa para testar a altura dos detalhes. ".repeat(18))
        compose.setContent { BrasaTheme(uiScale = scale) {
            RemoteInputMode()
            DetailsScreen(BrasaUiState(selected = movie), {}, {}, {}, {}, {}, { _, _ -> }, {})
        } }
        listOf(.8f, .9f, 1f, 1.1f).forEach {
            compose.runOnIdle { scale = it }
            compose.onNodeWithText("Assistir", substring = false).assertIsDisplayed()
            val hero = compose.onNodeWithTag("details-hero").fetchSemanticsNode().size
            val scrim = compose.onNodeWithTag("details-scrim").fetchSemanticsNode().size
            assertTrue(hero.height > 0)
            assertEquals(hero, scrim)
        }
    }

    @Test fun allNavigationLabelsRemainReachableAtRealTvDensity() {
        var scale by mutableStateOf(.8f)
        compose.setContent { BrasaTheme(uiScale = scale) {
            RemoteInputMode()
            BrasaTopBar(Modifier.padding(horizontal = 54.dp), onHome = {}, onMovies = {}, onSeries = {},
                onCollections = {}, onMyList = {}, onSearch = {}, onSettings = {}, onProfiles = {}, profileInitials = "MS")
        } }
        listOf(.8f, .9f, 1f, 1.1f).forEach {
            compose.runOnIdle { scale = it }
            compose.onNodeWithText("Início").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
            listOf("Filmes", "Séries", "Coleções", "Minha lista", "Buscar", "Configurações", "MS").forEach { label ->
                compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
                compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
                assertLabelFits(label)
            }
        }
    }

    @Test fun searchStartsOnFieldAndHomeHasItsOwnDestination() {
        var home = false
        var back = false
        compose.setContent { BrasaTheme {
            RemoteInputMode()
            SearchScreen(BrasaUiState(), settings, {}, {}, {}, {}, {}, {}, {}, { back = true }, { home = true })
        } }
        compose.onNodeWithTag("search-field").assertIsFocused().assert(hasSetTextAction().not())
            .performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("search-field").assert(hasSetTextAction())
        compose.onNodeWithTag("search-field").performTextInput("Witch")
        compose.onNodeWithTag("search-field").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Todos").assertIsFocused()
        compose.onNodeWithText("Início").performClick()
        compose.runOnIdle { assertTrue(home); assertFalse(back) }
    }

    @Test fun searchResultsAndFieldFitAllFourScales() {
        var scale by mutableStateOf(.8f)
        val movie = CatalogItem(mediaKey = "movie:one", title = "Resultado visível", genres = listOf("Ação"))
        compose.setContent { BrasaTheme(uiScale = scale) {
            RemoteInputMode()
            SearchScreen(BrasaUiState(catalog = CatalogResponse(movies = listOf(movie))), settings,
                {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        listOf(.8f, .9f, 1f, 1.1f).forEach {
            compose.runOnIdle { scale = it }
            compose.onNodeWithTag("search-field").assertIsDisplayed()
            compose.onNodeWithText("Resultado visível").assertIsDisplayed()
            assertLabelFits("Resultado visível")
        }
    }

    @Test fun remoteReachesSettingsContentAndLabelsFitAllScales() {
        var scale by mutableStateOf(.8f)
        compose.setContent { BrasaTheme(uiScale = scale) {
            RemoteInputMode()
            SettingsScreen(BrasaUiState(), settings, 0, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        listOf(.8f, .9f, 1f, 1.1f).forEach {
            compose.runOnIdle { scale = it }
            compose.onNodeWithText("Reprodução").performClick()
            assertLabelFits("Próximo episódio automático — Desativado")
            compose.onNodeWithText("Interface").performClick()
            assertLabelFits("Confortável — Ativado")
            compose.onNodeWithText("Conta e aplicativo").performClick()
            compose.onNodeWithText("Verificar atualização").assertIsDisplayed()
        }
        compose.onNodeWithText("Geral").performClick().performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Abrir diagnóstico de rede").assertIsFocused()
    }

    private fun assertLabelFits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Texto deve ser medido: $text", layouts.isNotEmpty())
        assertFalse("Texto cortado: $text", layouts.any { it.hasVisualOverflow })
    }
}

@Composable
internal fun RemoteInputMode() {
    val input = LocalInputModeManager.current
    SideEffect { input.requestInputMode(InputMode.Keyboard) }
}
