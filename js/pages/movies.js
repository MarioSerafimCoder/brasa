import { getMovies } from "../../data/movies.js";
import { applyPreferences, getPreferences } from "../utils/preferences.js";
import { bindKidsModeToggle } from "../utils/kids-mode.js?v=streaming-20260709a";
import { filterKidsMovies } from "../utils/kids-mode.js?v=streaming-20260709a";
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

let movies = readMovies();
const genreFilter = document.getElementById("genreFilter");
const sortFilter = document.getElementById("sortFilter");
const movieGrid = document.getElementById("movieGrid");
const resultSummary = document.getElementById("resultSummary");

init();

function init() {
    installPageTransitions();
    installTmdbImageFallbacks();
    installPageSidebar("movies");
    fillGenres();
    bindEvents();
    bindKidsModeToggle();
    document.addEventListener("brasa:kids-mode-change", () => {
        movies = readMovies();
        fillGenres();
        render();
    });
    render();

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function readMovies() {
    const allMovies = getMovies();
    return getPreferences().kidsMode ? filterKidsMovies(allMovies) : allMovies;
}

function fillGenres() {
    genreFilter.innerHTML = [
        `<option value="all">Todos</option>`,
        ...getGenres(movies).map((genre) => `<option value="${escapeAttribute(genre)}">${escapeHtml(genre)}</option>`)
    ].join("");
}

function bindEvents() {
    genreFilter.addEventListener("change", render);
    sortFilter.addEventListener("change", render);
    movieGrid.addEventListener("click", (event) => {
        const action = event.target.closest("[data-empty-action]");

        if (action?.dataset.emptyAction === "clear-filters") {
            genreFilter.value = "all";
            render();
        }
    });
    bindMovieGridNavigation(movieGrid);
}

function render() {
    const filtered = filterMovies(movies, {
        genre: genreFilter.value,
        sort: sortFilter.value
    });

    renderMovieGrid(
        movieGrid,
        filtered,
        {
            emptyMessage: "Nenhum filme combina com este gênero.",
            emptyActionLabel: "Ver todos",
            emptyAction: "clear-filters"
        }
    );

    resultSummary.textContent = formatSummary(filtered.length, movies.length);
}

function formatSummary(visible, total) {
    const visibleLabel = visible === 1 ? "1 filme" : `${visible} filmes`;

    if (visible === total) {
        return `${visibleLabel} na biblioteca`;
    }

    return `${visibleLabel} exibidos de ${total}`;
}

