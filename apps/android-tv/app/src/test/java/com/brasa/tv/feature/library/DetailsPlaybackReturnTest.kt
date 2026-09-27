package com.brasa.tv.feature.library

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import com.brasa.tv.app.*
import com.brasa.tv.core.model.*
import com.brasa.tv.designsystem.*
import com.brasa.tv.feature.details.DetailsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class DetailsPlaybackReturnTest {
    @get:Rule val compose = createComposeRule()
    private fun series() = CatalogItem(id="s",mediaKey="series:s",type="series",title="Série de origem", seasons=(1..2).map { season ->
        Season(season, (1..10).map { episode -> CatalogItem(id="$season-$episode",mediaKey="episode:$season-$episode",type="episode",title="S$season E$episode",seasonNumber=season,episodeNumber=episode,streamUrl="/test") })
    })
    @Test fun returnsToChosenSeasonAndOffscreenEpisodeAfterPlayback() {
        compose.setContent {
            RemoteInputMode()
            var state by remember { mutableStateOf(BrasaUiState(selected=series(),profile=Profile(id="p"))) }
            var playing by remember { mutableStateOf(false) }
            val holder = rememberSaveableStateHolder()
            BrasaTheme {
                if (playing) BrasaButton("Voltar do player", {
                    state=state.withPlaybackProgress(state.playbackItem!!.mediaKey,WatchProgress(currentTime=60.0,duration=1200.0,percentage=5.0))
                    playing=false
                }) else holder.SaveableStateProvider("details") {
                    DetailsScreen(state,onPlay={state=state.beginPlayback(it);playing=true},onPlayFromStart={},onPrefetch={},onCancelPreload={},onFavorite={},onSignal={_,_->},onBack={})
                }
            }
        }
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(1)
        compose.onNodeWithText("Temporada 02  ·  10 episódios").performClick()
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(7)
        compose.onNodeWithText("S2 E8").performClick()
        compose.onNodeWithText("Voltar do player").performClick()
        compose.onNodeWithText("Episódios da temporada 02").assertExists()
        compose.onNodeWithText("S2 E8").assertIsDisplayed().assertIsFocused()
    }
    @Test fun nextEpisodeAndOtherTitlesDoNotReplaceDetails() {
        val origin=series()
        val first=origin.seasons[1].episodes[7]
        val second=origin.seasons[1].episodes[8]
        val state=BrasaUiState(selected=origin).beginPlayback(first).beginPlayback(second)
            .withPlaybackProgress(second.mediaKey,WatchProgress(percentage=10.0,currentTime=10.0,duration=100.0))
        assertEquals(origin.mediaKey,state.selected!!.mediaKey)
        assertEquals(second.mediaKey,state.selected!!.resumeMediaKey)
        val other=state.beginPlayback(CatalogItem(mediaKey="movie:other"))
        assertEquals(origin.mediaKey,other.selected!!.mediaKey)
        assertEquals("movie:other",other.playbackItem!!.mediaKey)
    }
    @Test fun restartExplicitlyClearsCompletedState() {
        val movie=CatalogItem(mediaKey="movie:a",completed=true,progress=WatchProgress(completed=true))
        val state=BrasaUiState(selected=movie).beginPlayback(movie,fromBeginning=true)
            .withPlaybackProgress(movie.mediaKey,WatchProgress(currentTime=10.0,percentage=5.0))
        assertFalse(state.selected!!.completed)
        assertFalse(state.playbackItem!!.completed)
    }

    @Test fun returningFromPlaybackUpdatesStaleRemainingMinutes() {
        val movie = CatalogItem(mediaKey = "movie:a", remainingMinutes = 30)
        val updated = BrasaUiState(selected = movie).withPlaybackProgress(movie.mediaKey,
            WatchProgress(currentTime = 1750.0, duration = 1800.0))
        assertEquals(1, updated.selected!!.remainingMinutes)
        assertNull(updated.withPlaybackProgress(movie.mediaKey, WatchProgress(completed = true)).selected!!.remainingMinutes)
    }

    @Test fun watchedEpisodeDoesNotAlsoDisplayRemainingTime() {
        val watched = CatalogItem(mediaKey = "episode:watched", type = "episode", title = "Concluído",
            completed = true, remainingMinutes = 2, progress = WatchProgress(currentTime = 950.0, duration = 1000.0, percentage = 95.0))
        val show = CatalogItem(mediaKey = "series:watched", type = "series", seasons = listOf(Season(1, listOf(watched))))
        compose.setContent { BrasaTheme {
            DetailsScreen(BrasaUiState(selected = show), {}, {}, {}, {}, {}, { _, _ -> }, {})
        } }
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(1)
        compose.onNodeWithText("ASSISTIDO").assertIsDisplayed()
        compose.onNodeWithText("Faltam 2 min").assertDoesNotExist()
    }

    @Test fun replayOfCompletedSeriesRestartsFirstEpisode() {
        val episode = CatalogItem(mediaKey = "episode:done", type = "episode", title = "Finalizado", streamUrl = "/video", completed = true)
        val show = CatalogItem(mediaKey = "series:done", type = "series", seasons = listOf(Season(1, listOf(episode))))
        var restarted: String? = null
        var resumed = false
        compose.setContent { RemoteInputMode(); BrasaTheme {
            DetailsScreen(BrasaUiState(selected = show), { resumed = true }, { restarted = it.mediaKey }, {}, {}, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Assistir novamente").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("episode:done", restarted); assertFalse(resumed) }
    }

    @Test fun longDetailsKeepAllPrimaryActionsVisibleAtEveryUiScale() {
        var scale by mutableStateOf(.8f)
        val longMovie = CatalogItem(
            id = "long", mediaKey = "movie:long", title = "Um título deliberadamente longo para validar a sala de estar",
            streamUrl = "/movie", year = 2026, genres = listOf("Ação", "Ficção científica", "Drama", "Aventura"),
            overview = "Uma sinopse extensa que ocupa várias linhas e simula o conteúdo real da biblioteca. ".repeat(8),
        )
        compose.setContent {
            RemoteInputMode()
            BrasaTheme(uiScale = scale) {
                DetailsScreen(BrasaUiState(selected = longMovie), onPlay = {}, onPlayFromStart = {}, onPrefetch = {},
                    onCancelPreload = {}, onFavorite = {}, onSignal = { _, _ -> }, onBack = {})
            }
        }
        listOf(.8f, .9f, 1f, 1.1f).forEach { testedScale ->
            compose.runOnIdle { scale = testedScale }
            compose.waitForIdle()
            listOf("Minha lista", "Mais opções").forEach {
                compose.onNodeWithText(it).assertIsDisplayed()
            }
            compose.onNodeWithText("Mais opções").performClick()
            compose.onNodeWithText("Assistir do início").assertIsDisplayed()
            compose.onNodeWithText("Fechar").performScrollTo().assertIsDisplayed().performClick()
        }
    }

    @Test fun remoteMovesAcrossDetailsActionsAndOpensMoreOptions() {
        val movie = CatalogItem(id = "focus", mediaKey = "movie:focus", title = "Filme", streamUrl = "/movie")
        var startRequested = false
        compose.setContent {
            RemoteInputMode()
            BrasaTheme {
                DetailsScreen(BrasaUiState(selected = movie), onPlay = {}, onPlayFromStart = { startRequested = true }, onPrefetch = {},
                    onCancelPreload = {}, onFavorite = {}, onSignal = { _, _ -> }, onBack = {})
            }
        }
        compose.onNodeWithText("Assistir").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        assertTrue(compose.onNodeWithText("Voltar").fetchSemanticsNode().boundsInRoot.top >= 0f)
        compose.onNodeWithText("Minha lista").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Mais opções").assertIsFocused().performClick()
        compose.onNodeWithText("Assistir do início").assertIsDisplayed()
        compose.onNodeWithText("Fechar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Mais opções").assertIsFocused()
        compose.onNodeWithText("Mais opções").performClick()
        compose.onNodeWithText("Assistir do início").performClick()
        compose.runOnIdle { assertTrue(startRequested) }
    }
}
