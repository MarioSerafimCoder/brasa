// ==========================================================
// BRasa
// Home Page
// ==========================================================

import { render, renderMany } from "../utils/renderer.js";
import { installPageTransitions, navigateTo } from "../utils/navigation.js";

import { HeroSlider } from "../../components/home/hero.js?v=streaming-20260709a";
import Carousel from "../../components/home/carousel.js?v=streaming-20260709a";
import { getPreferences } from "../utils/preferences.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import {
    chooseRandomMovie,
    filterKidsMovies,
    getKidsThemes,
    getMoviesByKidsTheme
} from "../utils/kids-mode.js";
import {
    getFavoriteMovies,
    toggleFavorite,
    withFavoriteState
} from "../utils/favorites.js";
import {
    getContinueWatching as getProgressContinueWatching,
    withProgressState
} from "../utils/progress.js";
import { movieImageUrl, seriesImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";

import {

    getRecentlyAddedMovies,

    getMovies,

    getAvailableMovies

} from "../../data/movies.js";
import { getRecentlyAddedSeries } from "../../data/series.js";

let activeKidsTheme = "all";
let isBound = false;
let heroSliderTimer = null;

export default function HomePage(){
    installPageTransitions();

    renderHero();

    renderCarousels();

    if (!isBound) {
        bindMovieNavigation();
        document.addEventListener("brasa:kids-mode-change", () => {
            activeKidsTheme = "all";
            renderHero();
            renderCarousels();
        });
        isBound = true;
    }

}

function renderHero(){
    stopHeroSlider();

    if (getPreferences().kidsMode) {
        render("#hero", KidsHero());
        return;
    }

    const recentMovies = withFavoriteState(withProgressState(getRecentlyAddedMovies(4)));

    render("#hero", HeroSlider({ movies: recentMovies }));
    bindHeroSlider();

}

function bindHeroSlider() {
    const slider = document.querySelector("[data-hero-slider]");

    if (!slider) return;

    const slides = Array.from(slider.querySelectorAll("[data-hero-slide-panel]"));
    const dots = Array.from(slider.querySelectorAll("[data-hero-slide]"));

    if (slides.length < 2) return;

    let activeIndex = 0;

    const setActiveSlide = (nextIndex) => {
        activeIndex = (nextIndex + slides.length) % slides.length;
        slider.dataset.activeSlide = String(activeIndex);

        slides.forEach((slide, index) => {
            const active = index === activeIndex;
            slide.classList.toggle("is-active", active);
            slide.setAttribute("aria-hidden", active ? "false" : "true");
        });

        dots.forEach((dot, index) => {
            const active = index === activeIndex;
            dot.classList.toggle("is-active", active);
            dot.setAttribute("aria-pressed", active ? "true" : "false");
        });
    };

    const start = () => {
        stopHeroSlider();

        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

        heroSliderTimer = window.setInterval(() => {
            setActiveSlide(activeIndex + 1);
        }, 6200);
    };

    dots.forEach((dot) => {
        dot.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            setActiveSlide(Number(dot.dataset.heroSlide || 0));
            start();
        });
    });

    slider.addEventListener("mouseenter", stopHeroSlider);
    slider.addEventListener("mouseleave", start);
    slider.addEventListener("focusin", stopHeroSlider);
    slider.addEventListener("focusout", start);

    start();
}

function stopHeroSlider() {
    if (!heroSliderTimer) return;

    window.clearInterval(heroSliderTimer);
    heroSliderTimer = null;
}

function renderCarousels(){
    if (getPreferences().kidsMode) {
        renderKidsHome();
        return;
    }

    const movies = withFavoriteState(withProgressState(getMovies()));
    const availableMovies = withFavoriteState(withProgressState(getAvailableMovies()));
    const continueWatching = withFavoriteState(getProgressContinueWatching(movies));
    const recentSeries = getRecentlyAddedSeries(10);

    renderMany(

        "#content",

        [

            Carousel({

                title:"Disponível no Computador",

                movies:availableMovies,

                variant:"wide"

            }),

            RecentSeriesSection(recentSeries),

            Carousel({

                title:"Todos os Filmes",

                movies:movies,

                variant:"poster"

            }),

            Carousel({

                title:"Continue Assistindo",

                movies:continueWatching,

                variant:"wide"

            }),

            Carousel({

                title:"Favoritos",

                movies:getFavoriteMovies(movies),

                id:"favorites",

                variant:"poster"

            })

        ]

    );

}

function RecentSeriesSection(series) {
    if (!series.length) return "";

    return `
        <section class="series-rail">
            <div class="carousel__header">
                <h2>Series adicionadas recentemente</h2>
                <a href="pages/series.html">Ver todas</a>
            </div>
            <div class="series-rail__track">
                ${series.map(SeriesHomeCard).join("")}
            </div>
        </section>
    `;
}

function SeriesHomeCard(item) {
    const latestSeason = [...(item.seasons || [])].sort((a, b) => b.seasonNumber - a.seasonNumber)[0];
    const episodeLabel = item.episodeCount === 1 ? "1 episodio" : `${item.episodeCount} episodios`;
    const image = item.backdrop || item.poster || "";
    const seriesImage = seriesImageUrl(item, {
        type: "backdrop",
        size: "w780",
        fallback: image
    });

    return `
        <article class="series-home-card" data-series-id="${escapeAttribute(item.id)}" role="button" tabindex="0" aria-label="Abrir ${escapeAttribute(item.title)}">
            <div class="series-home-card__art">
                ${seriesImage ? `<img src="${escapeAttribute(seriesImage)}" alt="${escapeAttribute(item.title)}" loading="lazy"${tmdbImageFallbackAttributes(image)}>` : `<strong>${escapeHtml(item.title)}</strong>`}
                <span><i data-lucide="tv"></i>${episodeLabel}</span>
            </div>
            <div class="series-home-card__body">
                <h3>${escapeHtml(item.title)}</h3>
                <p>${latestSeason ? `Temporada ${latestSeason.seasonNumber}` : "Serie local"}</p>
            </div>
        </article>
    `;
}

function renderKidsHome() {
    const allKidsMovies = withFavoriteState(withProgressState(filterKidsMovies(getMovies())));
    const filteredKidsMovies = activeKidsTheme === "all"
        ? allKidsMovies
        : getMoviesByKidsTheme(allKidsMovies, activeKidsTheme);
    const continueWatching = withFavoriteState(getProgressContinueWatching(allKidsMovies));
    const favorites = getFavoriteMovies(allKidsMovies);

    render("#content", `
        <section class="kids-home">
            <div class="kids-theme-bar" aria-label="Busca por tema">
                <button class="kids-theme ${activeKidsTheme === "all" ? "is-active" : ""}" type="button" data-kids-theme="all">
                    <i data-lucide="sparkles"></i>
                    <span>Todos</span>
                </button>
                ${getKidsThemes(allKidsMovies).map((theme) => `
                    <button class="kids-theme ${activeKidsTheme === theme.id ? "is-active" : ""}" type="button" data-kids-theme="${escapeAttribute(theme.id)}" ${theme.active ? "" : "disabled"}>
                        <i data-lucide="${escapeAttribute(theme.icon)}"></i>
                        <span>${escapeHtml(theme.label)}</span>
                    </button>
                `).join("")}
            </div>

            ${continueWatching.length ? KidsSection("Continuar", continueWatching) : ""}
            ${favorites.length ? KidsSection("Meus favoritos", favorites) : ""}
            ${KidsSection("Escolha uma aventura", filteredKidsMovies)}
        </section>
    `);

}

function KidsHero() {
    return `
        <section class="kids-hero">
            <div>
                <p>BRasa Kids</p>
                <h1>O que vamos assistir hoje?</h1>
                <span>Somente filmes permitidos aparecem aqui.</span>
            </div>
            <button class="kids-surprise" type="button" data-action="surprise">
                <i data-lucide="shuffle"></i>
                <span>Surpreenda-me</span>
            </button>
        </section>
    `;
}

function KidsSection(title, movies) {
    if (!movies.length) {
        return `
            <section class="kids-section">
                <h2>${title}</h2>
                <div class="kids-empty">Nenhum filme liberado para este tema.</div>
            </section>
        `;
    }

    return `
        <section class="kids-section">
            <h2>${title}</h2>
            <div class="kids-movie-grid">
                ${movies.map(KidsMovieCard).join("")}
            </div>
        </section>
    `;
}

function KidsMovieCard(movie) {
    const meta = escapeHtml([movie.contentRating, ...(movie.genres || []).slice(0, 1)].filter(Boolean).join(" - "));
    const poster = movieImageUrl(movie, {
        type: "poster",
        size: "w780",
        fallback: movie.poster
    });

    return `
        <article class="kids-movie-card" data-movie-id="${escapeAttribute(movie.id)}" role="button" tabindex="0" aria-label="Assistir ${escapeAttribute(movie.title)}">
            <div class="kids-movie-card__poster">
                <img src="${escapeAttribute(poster)}" alt="${escapeAttribute(movie.title)}" loading="lazy"${tmdbImageFallbackAttributes(movie.poster)}>
                <span><i data-lucide="play"></i></span>
            </div>
            <h3>${escapeHtml(movie.title)}</h3>
            <p>${meta}</p>
        </article>
    `;
}

function bindMovieNavigation(){

    document.addEventListener("click", (event) => {

        const action = event.target.closest("[data-action]");

        if (action?.dataset.action === "favorite") {
            event.preventDefault();
            event.stopPropagation();
            const movie = getMovies().find((item) => String(item.id) === String(action.dataset.movieId));
            toggleFavorite(movie || action.dataset.movieId);
            renderCarousels();
            return;
        }

        if (action?.dataset.action === "surprise") {
            const movie = chooseRandomMovie(filterKidsMovies(getAvailableMovies()));

            if (movie) {
                navigateTo(`pages/movie.html?id=${movie.id}`);
            }
            return;
        }

        const themeButton = event.target.closest("[data-kids-theme]");

        if (themeButton) {
            activeKidsTheme = themeButton.dataset.kidsTheme;
            renderCarousels();
            return;
        }

        const target = event.target.closest("[data-movie-id]");

        const seriesTarget = event.target.closest("[data-series-id]");

        if (seriesTarget) {
            navigateTo(`pages/series.html?id=${seriesTarget.dataset.seriesId}`);
            return;
        }

        if (!target) return;

        const movieId = target.dataset.movieId;

        if (!movieId) return;

        navigateTo(`pages/movie.html?id=${movieId}`);

    });

    document.addEventListener("keydown", (event) => {

        if (event.key !== "Enter" && event.key !== " ") return;

        const target = event.target.closest(".movie-card[data-movie-id], .kids-movie-card[data-movie-id], .series-home-card[data-series-id]");

        if (!target) return;

        event.preventDefault();

        if (target.dataset.seriesId) {
            navigateTo(`pages/series.html?id=${target.dataset.seriesId}`);
            return;
        }

        navigateTo(`pages/movie.html?id=${target.dataset.movieId}`);

    });

}

