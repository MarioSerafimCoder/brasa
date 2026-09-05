import { getSeries, getSeriesById } from "../../data/series.js";
import { applyPreferences } from "../utils/preferences.js";
import { filterContentByProfile, initializeProfiles } from "../utils/profiles.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions, navigateTo } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";
import { installTmdbImageFallbacks, seriesImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";

const seriesGrid = document.getElementById("seriesGrid");
const seriesDetail = document.getElementById("seriesDetail");
const seriesHeader = document.getElementById("seriesHeader");

let selectedSeriesId = readSelectedSeriesId();
let selectedSeasonNumber = readSelectedSeasonNumber();

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

        openSeries(card.dataset.seriesId);
    });

    seriesDetail.addEventListener("click", (event) => {
        const back = event.target.closest("[data-series-back]");
        if (back) {
            showSeriesList();
            return;
        }

        const seasonTab = event.target.closest("[data-season-number]");
        if (seasonTab) {
            selectSeason(Number(seasonTab.dataset.seasonNumber));
            return;
        }

        const episode = event.target.closest("[data-episode-id]");
        if (!episode) return;

        navigateTo(`movie.html?episode=${encodeURIComponent(episode.dataset.episodeId)}`);
    });

    document.addEventListener("keydown", (event) => {
        const seasonTab = event.target.closest("[data-season-number]");
        if (seasonTab && ["ArrowLeft", "ArrowRight"].includes(event.key)) {
            event.preventDefault();
            moveBetweenSeasons(seasonTab, event.key === "ArrowRight" ? 1 : -1);
            return;
        }

        if (event.key !== "Enter" && event.key !== " ") return;

        const seriesCard = event.target.closest("[data-series-id]");
        const episodeCard = event.target.closest("[data-episode-id]");

        if (seriesCard) {
            event.preventDefault();
            openSeries(seriesCard.dataset.seriesId);
        }

        if (episodeCard) {
            event.preventDefault();
            navigateTo(`movie.html?episode=${encodeURIComponent(episodeCard.dataset.episodeId)}`);
        }
    });

    window.addEventListener("popstate", () => {
        selectedSeriesId = readSelectedSeriesId();
        selectedSeasonNumber = readSelectedSeasonNumber();
        renderPage();
    });
}

function renderPage() {
    const items = getVisibleSeries();

    if (!items.length) {
        seriesHeader.hidden = false;
        seriesGrid.hidden = false;
        seriesDetail.hidden = false;
        seriesGrid.innerHTML = "";
        seriesDetail.innerHTML = `
            <div class="empty-state">
                <p>Nenhuma série foi encontrada. Coloque episódios em assets/series ou assets/kids-series e abra o BRasa novamente.</p>
            </div>
        `;
        refreshIcons();
        return;
    }

    const selectedItem = items.find((item) => item.id === selectedSeriesId);
    const isDetailView = Boolean(selectedItem);

    seriesHeader.hidden = isDetailView;
    seriesGrid.hidden = isDetailView;
    seriesDetail.hidden = !isDetailView;
    seriesGrid.innerHTML = isDetailView ? "" : items.map(SeriesCard).join("");
    seriesDetail.innerHTML = "";

    if (selectedItem) {
        renderSelectedSeries(selectedItem);
    } else {
        selectedSeriesId = "";
    }

    refreshIcons();
}

function readSelectedSeriesId() {
    return new URLSearchParams(window.location.search).get("id") || "";
}

function readSelectedSeasonNumber() {
    return Number(new URLSearchParams(window.location.search).get("season")) || 0;
}

function openSeries(seriesId) {
    selectedSeriesId = seriesId || "";
    selectedSeasonNumber = 0;
    const url = new URL(window.location.href);
    url.searchParams.set("id", selectedSeriesId);
    url.searchParams.delete("season");
    history.pushState(null, "", url);
    renderPage();
    seriesDetail.querySelector("[data-series-back]")?.focus();
}

function showSeriesList() {
    selectedSeriesId = "";
    selectedSeasonNumber = 0;
    const url = new URL(window.location.href);
    url.searchParams.delete("id");
    history.pushState(null, "", url);
    renderPage();
    seriesGrid.querySelector("[data-series-id]")?.focus();
}

function selectSeason(seasonNumber) {
    const item = getSeriesById(selectedSeriesId);
    if (!item?.seasons?.some((season) => season.seasonNumber === seasonNumber)) return;
    selectedSeasonNumber = seasonNumber;
    const url = new URL(window.location.href);
    url.searchParams.set("season", String(seasonNumber));
    history.replaceState(null, "", url);
    renderSelectedSeries(item);
    refreshIcons();
    seriesDetail.querySelector(`[data-season-number="${seasonNumber}"]`)?.focus();
}

function moveBetweenSeasons(currentTab, direction) {
    const tabs = Array.from(seriesDetail.querySelectorAll("[data-season-number]"));
    const currentIndex = tabs.indexOf(currentTab);
    if (currentIndex < 0 || !tabs.length) return;
    const next = tabs[(currentIndex + direction + tabs.length) % tabs.length];
    selectSeason(Number(next.dataset.seasonNumber));
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

function renderSelectedSeries(selectedItem) {
    const item = getSeriesById(selectedItem.id) || selectedItem;
    const seasons = item.seasons || [];
    const selectedSeason = seasons.find((season) => season.seasonNumber === selectedSeasonNumber) || seasons[0];
    selectedSeasonNumber = selectedSeason?.seasonNumber || 0;
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
        <button class="series-detail__back" type="button" data-series-back>
            <i data-lucide="arrow-left"></i>
            Voltar para séries
        </button>

        <div class="series-detail__hero"${style}>
            <div>
                <p>Série local</p>
                <h2>${escapeHtml(item.title)}</h2>
                <span>${escapeHtml(pluralize(item.seasonCount, "temporada", "temporadas"))} · ${escapeHtml(pluralize(item.episodeCount, "episódio", "episódios"))}</span>
            </div>
        </div>

        <nav class="season-tabs" aria-label="Temporadas de ${escapeAttribute(item.title)}" role="tablist">
            ${seasons.map((season) => SeasonTab(season, selectedSeasonNumber)).join("")}
        </nav>

        <div class="season-stack" role="tabpanel" aria-label="Episódios da temporada ${escapeAttribute(selectedSeasonNumber)}">
            ${selectedSeason ? SeasonBlock(selectedSeason) : ""}
        </div>
    `;
}

function SeasonTab(season, activeSeasonNumber) {
    const active = season.seasonNumber === activeSeasonNumber;
    const paddedNumber = String(season.seasonNumber).padStart(2, "0");
    return `
        <button class="season-tab${active ? " is-active" : ""}" type="button" role="tab" aria-selected="${active}" tabindex="${active ? "0" : "-1"}" data-season-number="${escapeAttribute(season.seasonNumber)}">
            Temporada ${escapeHtml(paddedNumber)}
            <span>${escapeHtml(pluralize(season.episodes?.length, "episódio", "episódios"))}</span>
        </button>
    `;
}

function SeasonBlock(season) {
    return `
        <section class="season-block">
            <h3>Episódios da temporada ${escapeHtml(String(season.seasonNumber).padStart(2, "0"))}</h3>
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
            <div class="episode-card__content">
                <p class="episode-card__meta">Episódio ${escapeHtml(String(episode.episodeNumber).padStart(2, "0"))} · ${escapeHtml(episode.quality || "Local")}</p>
                <h4>${escapeHtml(episode.title)}</h4>
                <p class="episode-card__overview">${escapeHtml(episode.overview || "Resumo sem spoilers em preparação.")}</p>
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
