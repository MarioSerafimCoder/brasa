import { getPreferences } from "../utils/preferences.js";
import { navigateTo } from "../utils/navigation.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { withProgressState } from "../utils/progress.js";
import { movieImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";

export function getGenres(movies) {
    return [...new Set(movies.flatMap((movie) => movie.genres || []))]
        .filter(Boolean)
        .sort((a, b) => a.localeCompare(b, "pt-BR"));
}

export function sortMovies(movies, sort) {
    const sorted = [...movies];

    if (sort === "alpha") {
        return sorted.sort((a, b) => a.title.localeCompare(b.title, "pt-BR"));
    }

    if (sort === "year") {
        return sorted.sort((a, b) => Number(b.year || 0) - Number(a.year || 0));
    }

    return sorted.sort((a, b) => Number(b.id || 0) - Number(a.id || 0));
}

export function filterMovies(movies, { query = "", genre = "all", sort = "recent" } = {}) {
    const normalizedQuery = normalize(query);

    return sortMovies(
        movies.filter((movie) => {
            const matchesQuery = !normalizedQuery || normalize([
                movie.title,
                movie.originalTitle,
                movie.year,
                movie.overview,
                ...(movie.genres || [])
            ].join(" ")).includes(normalizedQuery);

            const matchesGenre = genre === "all" || (movie.genres || []).includes(genre);

            return matchesQuery && matchesGenre;
        }),
        sort
    );
}

export function renderMovieGrid(container, movies, options = {}) {
    const preferences = getPreferences();
    const moviesWithProgress = withProgressState(movies);
    const {
        emptyMessage = "Nenhum filme encontrado.",
        emptyActionLabel = "",
        emptyAction = ""
    } = options;

    container.classList.toggle("is-list", preferences.libraryView === "list");

    if (!moviesWithProgress.length) {
        container.innerHTML = `
            <div class="empty-state">
                <p>${escapeHtml(emptyMessage)}</p>
                ${emptyActionLabel && emptyAction ? `<button class="empty-state__action" type="button" data-empty-action="${escapeAttribute(emptyAction)}">${escapeHtml(emptyActionLabel)}</button>` : ""}
            </div>
        `;
        return;
    }

    container.innerHTML = moviesWithProgress.map((movie) => movieCard(movie)).join("");
}

export function bindMovieGridNavigation(container) {
    container.addEventListener("click", (event) => {
        const card = event.target.closest("[data-movie-id]");

        if (!card) return;

        navigateTo(`movie.html?id=${card.dataset.movieId}`);
    });

    container.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;

        const card = event.target.closest("[data-movie-id]");

        if (!card) return;

        event.preventDefault();
        navigateTo(`movie.html?id=${card.dataset.movieId}`);
    });
}

function movieCard(movie) {
    const progress = Number(movie.progress || 0);
    const fallbackPoster = `../${movie.poster}`;
    const poster = movieImageUrl(movie, {
        type: "poster",
        size: "w780",
        fallback: fallbackPoster
    });

    return `
        <article class="movie-card-lite" data-movie-id="${escapeAttribute(movie.id)}" role="button" tabindex="0" aria-label="Abrir ${escapeAttribute(movie.title)}">
            <div class="movie-card-lite__poster">
                <img src="${escapeAttribute(poster)}" alt="${escapeAttribute(movie.title)}" loading="lazy"${tmdbImageFallbackAttributes(fallbackPoster)}>
            </div>
            <div class="movie-card-lite__body">
                <h3>${escapeHtml(movie.title)}</h3>
                <div class="movie-card-lite__meta">
                    <span>${escapeHtml(movie.year || "")}</span>
                    <span>${escapeHtml(movie.duration || "")}</span>
                    ${movie.rating ? `<span>IMDb ${escapeHtml(movie.rating)}</span>` : ""}
                    ${(movie.genres || []).slice(0, 2).map((genre) => `<span>${escapeHtml(genre)}</span>`).join("")}
                </div>
                ${progress > 0 ? `
                    <div class="movie-card-lite__progress" aria-label="${progress}% assistido">
                        <span style="width:${Math.min(100, Math.max(0, progress))}%"></span>
                    </div>
                ` : ""}
            </div>
        </article>
    `;
}

function normalize(value) {
    return String(value)
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .trim();
}
