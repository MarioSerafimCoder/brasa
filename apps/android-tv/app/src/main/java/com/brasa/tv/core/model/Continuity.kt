package com.brasa.tv.core.model

data class SeriesContinuation(val episode: CatalogItem?, val completed: Boolean, val watched: Boolean)

fun CatalogItem.orderedEpisodes(): List<CatalogItem> = seasons.flatMap(Season::episodes).sortedWith(
    compareBy<CatalogItem> { if ((it.seasonNumber ?: 0) == 0) 1 else 0 }
        .thenBy { it.seasonNumber ?: 0 }
        .thenBy { it.episodeNumber ?: 0 }
        .thenBy(CatalogItem::id),
)

fun CatalogItem.seriesContinuation(): SeriesContinuation {
    val episodes = orderedEpisodes()
    if (episodes.isEmpty()) return SeriesContinuation(null, false, false)
    fun finished(item: CatalogItem) = item.progress?.completed == true || (item.progress?.percentage ?: 0.0) >= 95.0
    val partial = episodes.filter { (it.progress?.percentage ?: 0.0) > 0.0 && !finished(it) }.maxByOrNull { it.progress?.updatedAt.orEmpty() }
    if (partial != null) return SeriesContinuation(partial, false, true)
    val completedIndexes = episodes.mapIndexedNotNull { index, item -> index.takeIf { finished(item) } }
    if (completedIndexes.isEmpty()) return SeriesContinuation(episodes.first(), false, false)
    val next = episodes.getOrNull(completedIndexes.last() + 1) ?: episodes.firstOrNull { !finished(it) }
    return SeriesContinuation(next, next == null, true)
}

fun CatalogItem.playableItem(): CatalogItem = if (type != "series") this else {
    orderedEpisodes().find { it.mediaKey == resumeMediaKey } ?: seriesContinuation().episode ?: this
}

fun CatalogItem.isWatched(): Boolean = if (type == "series") completed || seriesContinuation().completed else completed || progress?.completed == true || (progress?.percentage ?: 0.0) >= 95.0
