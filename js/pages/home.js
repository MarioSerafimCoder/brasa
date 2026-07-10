import { render, renderMany } from "../utils/renderer.js";
import { installPageTransitions, navigateTo } from "../utils/navigation.js";
import { filterContentByProfile, getActiveProfile, isKidsProfile } from "../utils/profiles.js";
import { HeroSlider } from "../../components/home/hero.js?v=streaming-20260709a";
import Carousel from "../../components/home/carousel.js?v=streaming-20260709a";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { getFavoriteMovies, toggleFavorite, withFavoriteState } from "../utils/favorites.js";
import { getContinueWatching as getProgressContinueWatching, withProgressState } from "../utils/progress.js";
import { movieImageUrl, seriesImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";
import { getRecentlyAddedMovies, getMovies, getAvailableMovies } from "../../data/movies.js";
import { getRecentlyAddedSeries } from "../../data/series.js";

let isBound = false;
let heroSliderTimer = null;

export default function HomePage() {
    installPageTransitions();
    renderHero();
    renderCarousels();

    if (!isBound) {
        bindMovieNavigation();
        isBound = true;
    }
}

function renderHero() {
    stopHeroSlider();
    const recentMovies = filterContentByProfile(withFavoriteState(withProgressState(getRecentlyAddedMovies(4))));

    if (!recentMovies.length) {
        render("#hero", ProfileEmptyHero());
        return;
    }

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
        heroSliderTimer = window.setInterval(() => setActiveSlide(activeIndex + 1), 6200);
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

function renderCarousels() {
    const movies = filterContentByProfile(withFavoriteState(withProgressState(getMovies())));
    const availableMovies = filterContentByProfile(withFavoriteState(withProgressState(getAvailableMovies())));
    const continueWatching = withFavoriteState(getProgressContinueWatching(movies));
    const recentSeries = filterContentByProfile(getRecentlyAddedSeries(10));

    if (!movies.length && !recentSeries.length) {
        const profile = getActiveProfile();
        render("#content", `
            <section class="profile-library-empty">
                <h2>${isKidsProfile(profile) ? "Ainda não há conteúdo infantil" : "Biblioteca vazia"}</h2>
                <p>${isKidsProfile(profile) ? "Adicione filmes ou séries na área kids para que a Laura possa assistir." : "Adicione vídeos à biblioteca para começar."}</p>
            </section>
        `);
        return;
    }

    renderMany("#content", [
        Carousel({ title: "Disponível no Computador", movies: availableMovies, variant: "wide" }),
        RecentSeriesSection(recentSeries),
        Carousel({ title: "Todos os Filmes", movies, variant: "poster" }),
        Carousel({ title: "Continue Assistindo", movies: continueWatching, variant: "wide" }),
        Carousel({ title: "Favoritos", movies: getFavoriteMovies(movies), id: "favorites", variant: "poster" })
    ]);
}

function ProfileEmptyHero() {
    const profile = getActiveProfile();
    const isKids = isKidsProfile(profile);

    return `
        <section class="hero profile-empty-hero">
            <div class="hero__content">
                <p class="profile-selector__eyebrow">${isKids ? "Perfil da Laura" : "BRasa"}</p>
                <h1 class="hero__title">${isKids ? "Nenhum conteúdo infantil disponível" : "Sua biblioteca está pronta para começar"}</h1>
                <p class="hero__description">${isKids ? "Conteúdos marcados como kids aparecerão aqui." : "Adicione filmes ou séries locais para ver seus destaques aqui."}</p>
            </div>
        </section>
    `;
}

function RecentSeriesSection(series) {
    if (!series.length) return "";

    return `
        <section class="series-rail">
            <div class="carousel__header">
                <h2>Séries adicionadas recentemente</h2>
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
    const episodeLabel = item.episodeCount === 1 ? "1 episódio" : `${item.episodeCount} episódios`;
    const image = item.backdrop || item.poster || "";
    const seriesImage = seriesImageUrl(item, { type: "backdrop", size: "w780", fallback: image });

    return `
        <article class="series-home-card" data-series-id="${escapeAttribute(item.id)}" role="button" tabindex="0" aria-label="Abrir ${escapeAttribute(item.title)}">
            <div class="series-home-card__art">
                ${seriesImage ? `<img src="${escapeAttribute(seriesImage)}" alt="${escapeAttribute(item.title)}" loading="lazy"${tmdbImageFallbackAttributes(image)}>` : `<strong>${escapeHtml(item.title)}</strong>`}
                <span><i data-lucide="tv"></i>${episodeLabel}</span>
            </div>
            <div class="series-home-card__body">
                <h3>${escapeHtml(item.title)}</h3>
                <p>${latestSeason ? `Temporada ${latestSeason.seasonNumber}` : "Série local"}</p>
            </div>
        </article>
    `;
}

function bindMovieNavigation() {
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

        const seriesTarget = event.target.closest("[data-series-id]");
        if (seriesTarget) {
            navigateTo(`pages/series.html?id=${seriesTarget.dataset.seriesId}`);
            return;
        }

        const target = event.target.closest("[data-movie-id]");
        if (target?.dataset.movieId) {
            navigateTo(`pages/movie.html?id=${target.dataset.movieId}`);
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;

        const target = event.target.closest(".movie-card[data-movie-id], .series-home-card[data-series-id]");
        if (!target) return;

        event.preventDefault();
        navigateTo(target.dataset.seriesId
            ? `pages/series.html?id=${target.dataset.seriesId}`
            : `pages/movie.html?id=${target.dataset.movieId}`);
    });
}
