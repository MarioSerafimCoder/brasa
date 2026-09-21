package com.brasa.tv.core.model

import java.text.Normalizer
import java.util.Locale

/** Exact aliases share one identity. Composite categories remain independent. */
fun genreIdentity(value: String): String {
    val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
    return when (normalized) {
        "acao", "action" -> "acao"
        "ficcao cientifica", "science fiction", "sci-fi" -> "ficcao-cientifica"
        "comedia", "comedy" -> "comedia"
        "animacao", "animation" -> "animacao"
        "aventura", "adventure" -> "aventura"
        "action & adventure", "action and adventure", "acao e aventura", "acao & aventura" -> "acao-e-aventura"
        else -> normalized
    }
}

fun genreLabel(value: String): String = when (genreIdentity(value)) {
    "acao" -> "Ação"
    "ficcao-cientifica" -> "Ficção científica"
    "comedia" -> "Comédia"
    "animacao" -> "Animação"
    "aventura" -> "Aventura"
    "acao-e-aventura" -> "Ação e aventura"
    else -> value.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("pt-BR")) else it.toString() }
}

fun List<String>.normalizedGenres(): List<String> = filter(String::isNotBlank).distinctBy(::genreIdentity).map(::genreLabel)
