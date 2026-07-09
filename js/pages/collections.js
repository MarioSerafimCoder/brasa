import { collections } from "../../data/collections.js";
import { getMovies } from "../../data/movies.js";
import { applyPreferences } from "../utils/preferences.js";
import { bindKidsModeToggle } from "../utils/kids-mode.js?v=streaming-20260709a";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions } from "../utils/navigation.js";
import { installPageSidebar } from "../utils/page-layout.js?v=streaming-20260709a";
import { installTmdbImageFallbacks } from "../utils/tmdb-images.js";
import { bindMovieGridNavigation, renderMovieGrid } from "./library-utils.js?v=streaming-20260709a";

applyPreferences();

const movies = getMovies();
const grid = document.getElementById("collectionsGrid");
const title = document.getElementById("collectionTitle");
const kicker = document.getElementById("collectionKicker");
const count = document.getElementById("collectionCount");
const movieGrid = document.getElementById("collectionMovies");

let visibleCollections = getVisibleCollections();
let selectedCollection = visibleCollections[0] || null;

init();

function init() {
    installPageTransitions();
    installTmdbImageFallbacks();
    installPageSidebar("collection");
    renderCollections();
    renderSelectedCollection();
    bindEvents();
    bindKidsModeToggle();
    refreshIcons();
}

function bindEvents() {
    grid.addEventListener("click", (event) => {
        const card = event.target.closest("[data-collection-id]");

        if (!card) return;

        selectCollection(card.dataset.collectionId);
    });

    grid.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;

        const card = event.target.closest("[data-collection-id]");

        if (!card) return;

        event.preventDefault();
        selectCollection(card.dataset.collectionId);
    });

    bindMovieGridNavigation(movieGrid);
}

function selectCollection(collectionId) {
    selectedCollection = visibleCollections.find((collection) => collection.id === collectionId) || visibleCollections[0] || null;
    renderCollections();
    renderSelectedCollection();
    refreshIcons();
}

function renderCollections() {
    visibleCollections = getVisibleCollections();

    if (!visibleCollections.length) {
        selectedCollection = null;
        grid.innerHTML = `
            <div class="empty-state">
                <p>Nenhuma coleção tem filmes detectados ainda.</p>
            </div>
        `;
        return;
    }

    if (!visibleCollections.some((collection) => collection.id === selectedCollection?.id)) {
        selectedCollection = visibleCollections[0];
    }

    grid.innerHTML = groupCollections(visibleCollections).map((group) => `
        <section class="collection-group">
            <h2>${escapeHtml(group.title)}</h2>
            <div class="collection-group__grid">
                ${group.collections.map(CollectionCard).join("")}
            </div>
        </section>
    `).join("");

    hydrateCollectionBanners();
}

function CollectionCard(collection) {
    const collectionMovies = getCollectionMovies(collection);

    return `
        <article class="collection-card ${selectedCollection?.id === collection.id ? "is-active" : ""}" data-collection-id="${escapeAttribute(collection.id)}" role="button" tabindex="0" aria-label="Abrir coleção ${escapeAttribute(collection.title)}">
            <div class="collection-card__art" data-banner-src="${escapeAttribute(collection.banner)}" aria-hidden="true"></div>
            <div class="collection-card__shade"></div>
            <div class="collection-card__content">
                <h3>${escapeHtml(collection.title)}</h3>
                <p>${escapeHtml(collection.subtitle)}</p>
                <span>${collectionMovies.length} ${collectionMovies.length === 1 ? "filme" : "filmes"}</span>
            </div>
        </article>
    `;
}

function renderSelectedCollection() {
    if (!selectedCollection) {
        kicker.textContent = "Coleções";
        title.textContent = "Nada encontrado";
        count.textContent = "";
        movieGrid.innerHTML = `
            <div class="empty-state">
                <p>Adicione filmes na pasta local e abra o BRasa novamente para popular as coleções.</p>
            </div>
        `;
        return;
    }

    const collectionMovies = getCollectionMovies(selectedCollection);

    kicker.textContent = "Coleção";
    title.textContent = selectedCollection.title;
    count.textContent = `${collectionMovies.length} ${collectionMovies.length === 1 ? "item" : "itens"}`;

    renderMovieGrid(movieGrid, collectionMovies, {
        emptyMessage: "Nenhum filme desta coleção foi encontrado ainda."
    });
}

function getVisibleCollections() {
    return collections.filter((collection) => getCollectionMovies(collection).length);
}

function groupCollections(items) {
    const groups = [
        { id: "universes", title: "Universos", collections: [] },
        { id: "sagas", title: "Sagas", collections: [] },
        { id: "animation", title: "Animação", collections: [] },
        { id: "special", title: "Listas especiais", collections: [] }
    ];

    items.forEach((collection) => {
        const group = groups.find((item) => item.id === getCollectionGroup(collection.id)) || groups[3];
        group.collections.push(collection);
    });

    return groups.filter((group) => group.collections.length);
}

function getCollectionGroup(id) {
    if (["mcu", "dc"].includes(id)) return "universes";
    if (["pixar", "disney-classics", "dreamworks", "ghibli"].includes(id)) return "animation";
    if (["classics", "best-picture"].includes(id)) return "special";
    return "sagas";
}

function getCollectionMovies(collection) {
    const keywords = collection.keywords.map(normalize);

    return movies.filter((movie) => {
        const haystack = normalize([
            movie.title,
            movie.originalTitle,
            movie.overview,
            ...(movie.genres || [])
        ].join(" "));

        return keywords.some((keyword) => haystack.includes(keyword));
    });
}

function normalize(value) {
    return String(value)
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .trim();
}

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

async function hydrateCollectionBanners() {
    const bannerElements = Array.from(document.querySelectorAll("[data-banner-src]"));

    await Promise.all(bannerElements.map(async (element) => {
        try {
            const response = await fetch(element.dataset.bannerSrc);

            if (!response.ok) return;

            const svg = await response.text();

            if (!svg.trim().startsWith("<svg")) return;

            element.innerHTML = svg;
        } catch {
            element.classList.add("is-missing");
        }
    }));
}

