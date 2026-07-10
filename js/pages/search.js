import { getMovies } from "../../data/movies.js";
import { applyPreferences } from "../utils/preferences.js";
import { filterContentByProfile, initializeProfiles } from "../utils/profiles.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";
import { installTmdbImageFallbacks } from "../utils/tmdb-images.js";
import {
    bindMovieGridNavigation,
    filterMovies,
    getGenres,
    renderMovieGrid
} from "./library-utils.js?v=streaming-20260709a";

applyPreferences();

let movies = [];
const searchInput = document.getElementById("searchInput");
const clearSearch = document.getElementById("clearSearch");
const genreFilter = document.getElementById("genreFilter");
const sortFilter = document.getElementById("sortFilter");
const movieGrid = document.getElementById("movieGrid");
const resultSummary = document.getElementById("resultSummary");

init();

async function init() {
    installPageTransitions();
    installTmdbImageFallbacks();
    installPageSidebar("search");
    await initializeProfiles();
    movies = readMovies();
    fillGenres();
    bindEvents();
    render();

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function readMovies() {
    return filterContentByProfile(getMovies());
}

function fillGenres() {
    genreFilter.innerHTML = [
        `<option value="all">Todos</option>`,
        ...getGenres(movies).map((genre) => `<option value="${escapeAttribute(genre)}">${escapeHtml(genre)}</option>`)
    ].join("");
}

function bindEvents() {
    searchInput.addEventListener("input", render);
    clearSearch.addEventListener("click", () => {
        searchInput.value = "";
        searchInput.focus();
        render();
    });
    genreFilter.addEventListener("change", render);
    sortFilter.addEventListener("change", render);
    movieGrid.addEventListener("click", (event) => {
        const action = event.target.closest("[data-empty-action]");

        if (action?.dataset.emptyAction === "clear-search") {
            searchInput.value = "";
            genreFilter.value = "all";
            render();
            searchInput.focus();
        }
    });
    bindMovieGridNavigation(movieGrid);
}

function render() {
    const filtered = filterMovies(movies, {
        query: searchInput.value,
        genre: genreFilter.value,
        sort: sortFilter.value
    });

    renderMovieGrid(
        movieGrid,
        filtered,
        {
            emptyMessage: "Nada apareceu com esses filtros.",
            emptyActionLabel: "Limpar busca",
            emptyAction: "clear-search"
        }
    );

    clearSearch.hidden = !searchInput.value.trim();
    resultSummary.textContent = formatSummary(filtered.length, movies.length);
    refreshIcons();
}

function formatSummary(visible, total) {
    const visibleLabel = visible === 1 ? "1 filme" : `${visible} filmes`;

    if (visible === total) {
        return `${visibleLabel} na biblioteca`;
    }

    return `${visibleLabel} encontrados de ${total}`;
}

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

