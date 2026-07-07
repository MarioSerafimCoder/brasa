import { getPreferences } from "../utils/preferences.js";

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

export function renderMovieGrid(container, movies) {
    const preferences = getPreferences();
    container.classList.toggle("is-list", preferences.libraryView === "list");

    if (!movies.length) {
        container.innerHTML = `<div class="empty-state">Nenhum filme encontrado.</div>`;
        return;
    }

    container.innerHTML = movies.map((movie) => movieCard(movie)).join("");
}

export function bindMovieGridNavigation(container) {
    container.addEventListener("click", (event) => {
        const card = event.target.closest("[data-movie-id]");

        if (!card) return;

        window.location.href = `movie.html?id=${card.dataset.movieId}`;
    });
}

function movieCard(movie) {
    return `
        <article class="movie-card-lite" data-movie-id="${movie.id}">
            <div class="movie-card-lite__poster">
                <img src="../${movie.poster}" alt="${movie.title}" loading="lazy">
            </div>
            <div class="movie-card-lite__body">
                <h3>${movie.title}</h3>
                <div class="movie-card-lite__meta">
                    <span>${movie.year || ""}</span>
                    <span>${movie.duration || ""}</span>
                    ${movie.rating ? `<span>IMDb ${movie.rating}</span>` : ""}
                    ${(movie.genres || []).slice(0, 2).map((genre) => `<span>${genre}</span>`).join("")}
                </div>
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
