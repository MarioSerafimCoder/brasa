package com.brasa.tv.feature.library

import com.brasa.tv.core.model.CatalogItem
import java.text.Collator
import java.util.Locale

enum class CatalogOrder(val label: String) { ORIGINAL("Padrão"), NAME("Nome A–Z"), YEAR("Ano: recentes"), ADDED("Adicionados recentemente") }

fun orderCatalog(items: List<CatalogItem>, order: CatalogOrder, unwatchedOnly: Boolean): List<CatalogItem> {
    val filtered = if (unwatchedOnly) items.filterNot {
        it.progress?.completed == true || (it.progress?.percentage ?: 0.0) >= 95.0
    } else items
    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
    val nameComparator = Comparator<CatalogItem> { a, b -> collator.compare(a.title, b.title) }
    return when (order) {
        CatalogOrder.ORIGINAL -> filtered
        CatalogOrder.NAME -> filtered.sortedWith(nameComparator)
        CatalogOrder.YEAR -> filtered.sortedWith(compareByDescending<CatalogItem> { it.year ?: 0 }.then(nameComparator))
        CatalogOrder.ADDED -> filtered.sortedWith(compareByDescending<CatalogItem> { it.addedAt }.then(nameComparator))
    }
}
