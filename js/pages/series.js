import { getSeries, getSeriesById } from "../../data/series.js";
import { applyPreferences } from "../utils/preferences.js";
import { filterContentByProfile, initializeProfiles } from "../utils/profiles.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions, navigateTo } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";
import { installTmdbImageFallbacks, seriesImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";

const seriesGrid = document.getElementById("seriesGrid");
const seriesDetail = document.getElementById("seriesDetail");
const params = new URLSearchParams(window.location.search);

let selectedSeriesId = params.get("id") || "";

init();

async function init() {
    applyPreferences();
    installPageTransitions();
    installTmdbImageFallbacks();
    installPageSidebar("series");
    await initializeProfiles();
    renderPage();

    seriesGrid.addEventListener("click", (event) => {
        const card = event.target.closest("[data-series-id]");
        if (!card) return;

        selectedSeriesId = card.dataset.seriesId;
        renderPage();
        history.replaceState(null, "", `series.html?id=${encodeURIComponent(selectedSeriesId)}`);
    });

    seriesDetail.addEventListener("click", (event) => {
        const episode = event.target.closest("[data-episode-id]");
        if (!episode) return;

        navigateTo(`movie.html?episode=${encodeURIComponent(episode.dataset.episodeId)}`);
    });

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;

        const seriesCard = event.target.closest("[data-series-id]");
        const episodeCard = event.target.closest("[data-episode-id]");

        if (seriesCard) {
            event.preventDefault();
            selectedSeriesId = seriesCard.dataset.seriesId;
            renderPage();
            history.replaceState(null, "", `series.html?id=${encodeURIComponent(selectedSeriesId)}`);
        }

        if (episodeCard) {
            event.preventDefault();
            navigateTo(`movie.html?episode=${encodeURIComponent(episodeCard.dataset.episodeId)}`);
        }
    });
}

function renderPage() {
    const items = getVisibleSeries();

    if (!items.length) {
        seriesGrid.innerHTML = "";
        seriesDetail.innerHTML = `
            <div class="empty-state">
                <p>Nenhuma série foi encontrada. Coloque episódios em assets/series ou assets/kids-series e abra o BRasa novamente.</p>
            </div>
        `;
        refreshIcons();
        return;
    }

    if (!items.some((item) => item.id === selectedSeriesId)) {
        selectedSeriesId = items[0].id;
    }

    seriesGrid.innerHTML = items.map(SeriesCard).join("");
    renderSelectedSeries(items);
    refreshIcons();
}

function getVisibleSeries() {
    return filterContentByProfile(getSeries());
}

function SeriesCard(item) {
    const active = item.id === selectedSeriesId ? " is-active" : "";
    const seasons = pluralize(item.seasonCount, "temporada", "temporadas");
    const episodes = pluralize(item.episodeCount, "episódio", "episódios");
    const image = item.backdrop || item.poster || "";
    const cardImage = seriesImageUrl(item, {
        type: "backdrop",
        size: "w780",
        fallback: image ? `../${image}` : ""
    });

    return `
        <article class="series-card${active}" data-series-id="${escapeAttribute(item.id)}" role="button" tabindex="0" aria-label="Abrir ${escapeAttribute(item.title)}">
            <div class="series-card__art">
                ${cardImage ? `<img src="${escapeAttribute(cardImage)}" alt="${escapeAttribute(item.title)}" loading="lazy"${tmdbImageFallbackAttributes(image ? `../${image}` : "")}>` : `<strong>${escapeHtml(item.title)}</strong>`}
                <span><i data-lucide="tv"></i>${escapeHtml(episodes)}</span>
            </div>
            <div class="series-card__body">
                <h2>${escapeHtml(item.title)}</h2>
                <p>${escapeHtml(seasons)}</p>
            </div>
        </article>
    `;
}

function renderSelectedSeries(items) {
    const item = getSeriesById(selectedSeriesId) || items[0];
    const image = item.backdrop || item.poster || "";
    const heroImage = seriesImageUrl(item, {
        type: "backdrop",
        size: "w1280",
        fallback: image ? `../${image}` : ""
    });
    const fallbackHero = image ? `../${image}` : "";
    const heroLayers = [
        heroImage ? `url('${escapeAttribute(heroImage)}')` : "",
        fallbackHero && fallbackHero !== heroImage ? `url('${escapeAttribute(fallbackHero)}')` : ""
    ].filter(Boolean).join(", ");
    const style = heroLayers ? ` style="--series-hero-image:${heroLayers}"` : "";

    seriesDetail.innerHTML = `
        <div class="series-detail__hero"${style}>
            <div>
                <p>Série local</p>
                <h2>${escapeHtml(item.title)}</h2>
                <span>${escapeHtml(pluralize(item.seasonCount, "temporada", "temporadas"))} · ${escapeHtml(pluralize(item.episodeCount, "episódio", "episódios"))}</span>
            </div>
        </div>

        <div class="season-stack">
            ${(item.seasons || []).map(SeasonBlock).join("")}
        </div>
    `;
}

function SeasonBlock(season) {
    return `
        <section class="season-block">
            <h3>Temporada ${escapeHtml(season.seasonNumber)}</h3>
            <div class="episode-list">
                ${(season.episodes || []).map(EpisodeCard).join("")}
            </div>
        </section>
    `;
}

function EpisodeCard(episode) {
    const thumbnail = episode.thumbnail || episode.backdrop || "";
    const fallback = episode.backdrop && episode.backdrop !== thumbnail ? episode.backdrop : "";

    return `
        <article class="episode-card" data-episode-id="${escapeAttribute(episode.id)}" role="button" tabindex="0" aria-label="Abrir ${escapeAttribute(episode.title)}">
            <div class="episode-card__thumb">
                <span aria-hidden="true">${escapeHtml(episode.episodeNumber)}</span>
                ${thumbnail ? `<img src="../${escapeAttribute(thumbnail)}" alt="${escapeAttribute(episode.title)}" loading="lazy"${tmdbImageFallbackAttributes(fallback ? `../${fallback}` : "")}>` : ""}
            </div>
            <div>
                <h4>${escapeHtml(episode.title)}</h4>
                <p>Temporada ${escapeHtml(episode.seasonNumber)} · ${escapeHtml(episode.quality || "Local")}</p>
            </div>
            <i data-lucide="play"></i>
        </article>
    `;
}

function pluralize(value, singular, plural) {
    const count = Number(value) || 0;
    return `${count} ${count === 1 ? singular : plural}`;
}

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

