package com.brasa.tv.feature.library

import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class CatalogOrderingTest {
    private val older = CatalogItem(id = "1", title = "Zorro", year = 2000, addedAt = "2026-01-02T00:00:00Z")
    private val newer = CatalogItem(id = "2", title = "Avatar", year = 2020, addedAt = "2026-01-01T00:00:00Z")
    @Test fun preservesDefaultAndSortsWithoutMutatingSource() {
        val items = listOf(older, newer)
        assertEquals(items, orderCatalog(items, CatalogOrder.ORIGINAL, false))
        assertEquals(listOf(newer, older), orderCatalog(items, CatalogOrder.NAME, false))
        assertEquals(listOf(newer, older), orderCatalog(items, CatalogOrder.YEAR, false))
        assertEquals(listOf(older, newer), orderCatalog(items, CatalogOrder.ADDED, false))
        assertEquals("1", items.first().id)
    }
    @Test fun unwatchedIncludesUnstartedAndInProgressButNotCompleted() {
        val partial = newer.copy(progress = WatchProgress(percentage = 50.0))
        val finished = newer.copy(id = "3", progress = WatchProgress(completed = true, percentage = 90.0))
        val nearEnd = newer.copy(id = "4", progress = WatchProgress(percentage = 99.0))
        assertEquals(listOf(older, partial), orderCatalog(listOf(older, partial, finished, nearEnd), CatalogOrder.ORIGINAL, true))
    }
}
