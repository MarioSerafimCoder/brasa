package com.brasa.tv.app

import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.model.seriesContinuation

/** Playback never changes the identity of the page underneath it. */
fun BrasaUiState.beginPlayback(item: CatalogItem, fromBeginning: Boolean = false): BrasaUiState {
    val next = if (fromBeginning) item.copy(progress = null, completed = false) else item
    val origin = if (fromBeginning) withPlaybackProgress(item.mediaKey, WatchProgress(), preserveCompleted = false) else this
    return origin.copy(playbackItem = next, playback = null, message = "")
}

fun BrasaUiState.withPlaybackProgress(mediaKey: String, progress: WatchProgress, preserveCompleted: Boolean = true): BrasaUiState {
    val stamped = if (progress.updatedAt.isBlank()) progress.copy(updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())) else progress
    fun update(item: CatalogItem): CatalogItem {
        if (item.mediaKey == mediaKey) {
            val merged = if (preserveCompleted && item.progress?.completed == true && !stamped.completed) stamped.copy(completed = true) else stamped
            return item.copy(progress = merged, completed = merged.completed)
        }
        if (item.seasons.none { season -> season.episodes.any { it.mediaKey == mediaKey } }) return item
        val changed = item.copy(seasons = item.seasons.map { season -> season.copy(episodes = season.episodes.map(::update)) })
        val continuation = changed.seriesContinuation()
        val episode = continuation.episode
        return changed.copy(
            resumeMediaKey = episode?.mediaKey.orEmpty(), completed = continuation.completed,
            progress = episode?.progress,
            actionLabel = if (episode == null) "Assistir novamente" else if (continuation.watched)
                "Continuar T${(episode.seasonNumber ?: 0).toString().padStart(2, '0')} · E${(episode.episodeNumber ?: 0).toString().padStart(2, '0')}" else "Assistir",
            remainingMinutes = episode?.progress?.let { ((it.duration - it.currentTime).coerceAtLeast(0.0) / 60).toInt() },
        )
    }
    return copy(selected = selected?.let(::update), playbackItem = playbackItem?.let(::update),
        searchResults = searchResults.map(::update), selectedRow = selectedRow?.let { it.copy(items = it.items.map(::update)) },
        catalog = catalog?.let { it.copy(movies = it.movies.map(::update), series = it.series.map(::update)) },
        home = home?.let { it.copy(rows = it.rows.map { row -> row.copy(items = row.items.map(::update)) }) })
}
