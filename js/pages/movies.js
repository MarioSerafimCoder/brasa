import { getMovies } from "../../data/movies.js";
import { applyPreferences } from "../utils/preferences.js";
import { bindLibrarySync } from "../utils/library-sync.js";
import {
    bindMovieGridNavigation,
    filterMovies,
    getGenres,
    renderMovieGrid
} from "./library-utils.js";

applyPreferences();

const movies = getMovies();
const genreFilter = document.getElementById("genreFilter");
const sortFilter = document.getElementById("sortFilter");
const movieGrid = document.getElementById("movieGrid");

init();

function init() {
    fillGenres();
    bindEvents();
    bindLibrarySync();
    render();

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function fillGenres() {
    genreFilter.innerHTML = [
        `<option value="all">Todos</option>`,
        ...getGenres(movies).map((genre) => `<option value="${genre}">${genre}</option>`)
    ].join("");
}

function bindEvents() {
    genreFilter.addEventListener("change", render);
    sortFilter.addEventListener("change", render);
    bindMovieGridNavigation(movieGrid);
}

function render() {
    renderMovieGrid(
        movieGrid,
        filterMovies(movies, {
            genre: genreFilter.value,
            sort: sortFilter.value
        })
    );
}
