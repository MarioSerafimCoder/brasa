package com.brasa.tv.app

import com.brasa.tv.core.model.CatalogItem

/** Update matching identities without restoring a stale selected page or overwriting progress. */
internal fun BrasaUiState.withFavorite(mediaKey: String, favorite: Boolean): BrasaUiState {
    fun update(item: CatalogItem): CatalogItem {
        val current =
            if (item.mediaKey == mediaKey) item.copy(favorite = favorite, inMyList = favorite)
            else item
        return current.copy(
            seasons =
                current.seasons.map { season ->
                    season.copy(episodes = season.episodes.map(::update))
                }
        )
    }
    return copy(
        selected = selected?.let(::update),
        playbackItem = playbackItem?.let(::update),
        selectedRow = selectedRow?.let { it.copy(items = it.items.map(::update)) },
        searchResults = searchResults.map(::update),
        home =
            home?.let {
                it.copy(rows = it.rows.map { row -> row.copy(items = row.items.map(::update)) })
            },
        catalog =
            catalog?.let {
                it.copy(
                    movies = it.movies.map(::update),
                    series = it.series.map(::update),
                    collections =
                        it.collections.map { collection ->
                            collection.copy(items = collection.items.map(::update))
                        },
                    favorites =
                        if (favorite) (it.favorites + mediaKey).distinct()
                        else it.favorites - mediaKey,
                )
            },
    )
}
