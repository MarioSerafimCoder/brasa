package com.brasa.tv.app

import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.HomeResponse
import com.brasa.tv.core.model.HomeRow
import com.brasa.tv.core.model.PairingStatus
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.Profile
import com.brasa.tv.core.model.WatchProgress

object PreviewCatalog {
    private const val ASSET = "file:///android_asset/preview"
    private val profile = Profile(id = "preview", name = "Mario", initials = "MS", hasPin = true)
    private val profiles = listOf(
        profile,
        Profile(id = "familia", name = "Família", initials = "FA"),
        Profile(id = "kids", name = "Kids", initials = "KI", kind = "kids"),
    )

    private fun movie(
        id: String,
        title: String,
        year: Int,
        duration: String,
        rating: Double,
        genres: List<String>,
        overview: String,
        progress: Double = 0.0,
    ) = CatalogItem(
        id = id,
        mediaKey = "preview:$id",
        title = title,
        year = year,
        duration = duration,
        rating = rating,
        contentRating = "12",
        genres = genres,
        overview = overview,
        poster = "$ASSET/${id}_poster.jpg",
        backdrop = "$ASSET/${id}_backdrop.jpg",
        streamUrl = "preview://$id",
        progress = progress.takeIf { it > 0 }?.let {
            WatchProgress(mediaType = "movie", mediaId = id, percentage = it)
        },
    )

    val movies = listOf(
        movie("dune", "Duna", 2021, "2h 35min", 8.0, listOf("Ficção científica", "Aventura"), "Paul Atreides viaja para o planeta mais perigoso do universo para proteger o futuro de sua família e de seu povo.", 37.0),
        movie("interstellar", "Interestelar", 2014, "2h 49min", 8.7, listOf("Ficção científica", "Drama"), "Exploradores atravessam um buraco de minhoca em busca de um novo lar para a humanidade."),
        movie("blade_runner", "Blade Runner 2049", 2017, "2h 44min", 8.0, listOf("Ficção científica", "Suspense"), "Um novo blade runner descobre um segredo capaz de mergulhar o que resta da sociedade no caos."),
        movie("star_wars", "Star Wars: Uma Nova Esperança", 1977, "2h 1min", 8.6, listOf("Aventura", "Fantasia"), "Luke Skywalker inicia uma jornada que mudará o destino da galáxia."),
        movie("superman", "Superman", 2025, "2h 9min", 7.5, listOf("Ação", "Aventura"), "Um herói tenta conciliar sua herança kryptoniana com sua vida humana e a esperança que representa."),
        movie("ad_astra", "Ad Astra", 2019, "2h 3min", 6.5, listOf("Ficção científica", "Drama"), "Um astronauta viaja aos limites do sistema solar para encontrar seu pai desaparecido."),
    )

    private val home = HomeResponse(
        profile = profile,
        rows = listOf(
            HomeRow("continue-watching", "Continuar assistindo", items = movies.take(3)),
            HomeRow("all-movies", "Todos os filmes", items = movies),
            HomeRow("sci-fi", "Ficção científica", items = movies.reversed()),
        ),
    )

    fun state(page: String = "home") = BrasaUiState(
        server = com.brasa.tv.core.model.ServerInfo(name = "Modo Preview"),
        pairing = PairingStatus(requestId = "preview", code = "482 731", status = "pending", remainingMs = 180_000),
        profiles = profiles,
        profile = profile,
        home = home,
        searchResults = movies,
        selected = movies.first().takeIf { page == "details" || page == "player" },
        playback = playback(movies.first()).takeIf { page == "player" },
        paired = true,
        previewMode = true,
    )

    fun search(query: String) = if (query.isBlank()) movies else movies.filter {
        it.title.contains(query, ignoreCase = true) || it.genres.any { genre -> genre.contains(query, ignoreCase = true) }
    }

    fun playback(item: CatalogItem) = PlaybackInfo(
        mediaId = item.id,
        mediaKey = item.mediaKey,
        playbackUrl = item.streamUrl,
        duration = 7_200_000,
    )
}
