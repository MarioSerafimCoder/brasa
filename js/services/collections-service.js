import { collections as legacyCollections } from "../../data/collections.js";
import { isFavorite } from "../utils/favorites.js";
import { withProgressState } from "../utils/progress.js";
import { isCompletedForActive, wasRecentlyWatchedForActive } from "./profile-service.js";

const API_URL = "/api/collections";
const FALLBACK_KEY = "brasa:user-collections";

export function getSystemCollections() {
    return legacyCollections.map((collection) => ({
        id: collection.id,
        title: collection.title,
        description: collection.subtitle || "",
        type: "smart",
        source: "system",
        scope: "shared",
        profileId: null,
        banner: collection.banner || "",
        imdbIds: collection.imdbIds || [],
        titlePatterns: collection.titlePatterns || [],
        keywords: collection.keywords || [],
        movieIds: [],
        rules: {
            match: "any",
            items: (collection.keywords || []).map((value) => ({ field: "search", operator: "contains", value }))
        },
        sort: { field: "title", direction: "asc" },
        createdAt: "2026-07-01T00:00:00.000Z",
        updatedAt: "2026-07-10T00:00:00.000Z"
    }));
}

export async function loadCollections() {
    const userCollections = await request(API_URL).catch(loadFallback);
    return [...getSystemCollections(), ...(Array.isArray(userCollections) ? userCollections : [])];
}

export async function createCollection(input) {
    const collection = await request(API_URL, { method: "POST", body: input }).catch(() => saveFallback(input));
    return collection;
}

export async function updateCollection(id, input) {
    return request(`${API_URL}/${encodeURIComponent(id)}`, { method: "PUT", body: input })
        .catch(() => updateFallback(id, input));
}

export async function deleteCollection(id) {
    return request(`${API_URL}/${encodeURIComponent(id)}`, { method: "DELETE" })
        .catch(() => deleteFallback(id));
}

export async function duplicateCollection(collection, profileId) {
    const now = new Date().toISOString();
    return createCollection({
        ...collection,
        id: createId(`${collection.title}-copia`),
        title: `${collection.title} — cópia`,
        source: "user",
        scope: "profile",
        profileId,
        createdAt: now,
        updatedAt: now
    });
}

export function getCollectionMovies(collection, movies) {
    const enriched = withProgressState(movies).map((movie) => ({ ...movie, favorite: isFavorite(movie), completed: isCompletedForActive(`movie:${movie.id}`), recentlyWatched: wasRecentlyWatchedForActive(`movie:${movie.id}`) }));
    let result = collection.type === "manual"
        ? collection.movieIds.map((id) => enriched.find((movie) => String(movie.id) === String(id))).filter(Boolean)
        : enriched.filter((movie) => collection.source === "system" ? matchesSystemCollection(movie, collection) : evaluateRules(movie, collection.rules));

    return sortCollectionMovies(result, collection.sort);
}

export function matchesSystemCollection(movie, collection) {
    if ((collection.imdbIds || []).includes(movie.imdbId)) return true;
    const titles = normalize(`${movie.title || ""} ${movie.originalTitle || ""}`);
    if ((collection.titlePatterns || []).some((pattern) => titles.includes(normalize(pattern)))) return true;
    const safeKeywords = (collection.keywords || []).filter((keyword) => normalize(keyword).length >= 5 && !["fast", "classic", "oscar", "marvel"].includes(normalize(keyword)));
    const haystack = normalize(`${movie.title || ""} ${movie.originalTitle || ""} ${movie.overview || ""} ${(movie.genres || []).join(" ")}`);
    return safeKeywords.some((keyword) => haystack.includes(normalize(keyword)));
}

export function evaluateRules(movie, rules = {}) {
    const items = Array.isArray(rules.items) ? rules.items : [];
    if (!items.length) return false;
    const outcomes = items.map((rule) => evaluateRule(movie, rule));
    return rules.match === "any" ? outcomes.some(Boolean) : outcomes.every(Boolean);
}

export function filterCollectionsForProfile(collections, profile) {
    return collections.filter((collection) => {
        if (collection.source === "system") return true;
        if (!collection.profileId && collection.scope !== "shared") return profile?.id === "mario";
        if (collection.scope === "profile") return collection.profileId === profile?.id;
        return profile?.kind !== "kids";
    });
}

export function createId(value) {
    const base = normalize(value).replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "colecao";
    return `${base}-${Date.now().toString(36)}`;
}

function evaluateRule(movie, rule) {
    const actual = getFieldValue(movie, rule.field);
    const expected = rule.value;
    const operator = rule.operator || "equals";
    if (operator === "true") return Boolean(actual);
    if (operator === "false") return !actual;
    if (operator === "contains") return normalize(Array.isArray(actual) ? actual.join(" ") : actual).includes(normalize(expected));
    if (operator === "notContains") return !normalize(Array.isArray(actual) ? actual.join(" ") : actual).includes(normalize(expected));
    if (operator === "gte") return Number(actual) >= Number(expected);
    if (operator === "lte") return Number(actual) <= Number(expected);
    if (operator === "between") {
        const [min, max] = String(expected).split(",").map(Number);
        return Number(actual) >= min && Number(actual) <= max;
    }
    if (operator === "notEquals") return normalize(actual) !== normalize(expected);
    return typeof actual === "boolean" ? actual === (expected === true || expected === "true") : normalize(actual) === normalize(expected);
}

function getFieldValue(movie, field) {
    if (field === "search") return [movie.title, movie.originalTitle, movie.overview, ...(movie.genres || [])].join(" ");
    if (field === "title") return movie.title;
    if (field === "genres") return movie.genres || [];
    if (field === "year") return movie.year;
    if (field === "rating") return movie.rating;
    if (field === "quality") return movie.quality;
    if (field === "contentRating") return movie.contentRating;
    if (field === "kids") return movie.kids === true;
    if (field === "favorite") return movie.favorite === true;
    if (field === "progress") return Number(movie.progress || 0);
    if (field === "available") return Boolean(movie.video);
    if (field === "completed") return movie.completed === true;
    if (field === "recentlyWatched") return movie.recentlyWatched === true;
    return movie[field];
}

function sortCollectionMovies(movies, sort = {}) {
    const direction = sort.direction === "desc" ? -1 : 1;
    const field = sort.field || "title";
    if (field === "manual") return [...movies];
    return [...movies].sort((a, b) => {
        if (field === "year" || field === "rating" || field === "progress") return (Number(a[field] || 0) - Number(b[field] || 0)) * direction;
        return String(a[field] || "").localeCompare(String(b[field] || ""), "pt-BR") * direction;
    });
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        method: options.method || "GET",
        headers: { "X-BRasa-Request": "1", ...(options.body ? { "Content-Type": "application/json" } : {}) },
        body: options.body ? JSON.stringify(options.body) : undefined
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || "Não foi possível salvar a coleção.");
    return payload.collection ?? payload.collections ?? payload;
}

function loadFallback() {
    try { return JSON.parse(localStorage.getItem(FALLBACK_KEY) || "[]"); } catch { return []; }
}

function writeFallback(items) {
    localStorage.setItem(FALLBACK_KEY, JSON.stringify(items));
}

function saveFallback(input) {
    const items = loadFallback();
    items.push(input);
    writeFallback(items);
    return input;
}

function updateFallback(id, input) {
    const items = loadFallback();
    const index = items.findIndex((item) => item.id === id);
    if (index < 0) throw new Error("Coleção não encontrada.");
    items[index] = input;
    writeFallback(items);
    return input;
}

function deleteFallback(id) {
    writeFallback(loadFallback().filter((item) => item.id !== id));
    return { ok: true };
}

function normalize(value) {
    return String(value ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}
