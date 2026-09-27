package com.brasa.tv.app

import com.brasa.tv.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CatalogMutationsTest {
    @Test
    fun favoriteUpdatesCollectionsAndNestedEpisodes() {
        val episode = CatalogItem(mediaKey = "episode:a")
        val series =
            CatalogItem(mediaKey = "series:a", seasons = listOf(Season(episodes = listOf(episode))))
        val state =
            BrasaUiState(
                    catalog =
                        CatalogResponse(
                            series = listOf(series),
                            collections =
                                listOf(com.brasa.tv.core.model.Collection(items = listOf(episode))),
                        )
                )
                .withFavorite(episode.mediaKey, true)
        assertTrue(state.catalog!!.series.single().seasons.single().episodes.single().favorite)
        assertTrue(state.catalog.collections.single().items.single().favorite)
        assertFalse(
            state
                .withFavorite(episode.mediaKey, false)
                .catalog!!
                .collections
                .single()
                .items
                .single()
                .favorite
        )
    }

    @Test
    fun favoriteRollbackKeepsNewSelectionAndNewProgress() {
        val original = CatalogItem(mediaKey = "movie:a", title = "A")
        val other = CatalogItem(mediaKey = "movie:b", title = "B")
        val initial =
            BrasaUiState(
                selected = original,
                catalog = CatalogResponse(movies = listOf(original, other)),
            )
        val optimistic = initial.withFavorite(original.mediaKey, true)
        assertTrue(optimistic.selected!!.favorite)
        assertEquals(listOf(original.mediaKey), optimistic.catalog!!.favorites)
        val progress = WatchProgress(currentTime = 123.0)
        val later =
            optimistic
                .copy(selected = other, playbackItem = original.copy(progress = progress))
                .withFavorite(original.mediaKey, false)
        assertEquals(other, later.selected)
        assertEquals(progress, later.playbackItem!!.progress)
        assertFalse(later.playbackItem.favorite)
        assertTrue(later.catalog!!.favorites.isEmpty())
    }
}
