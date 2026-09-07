import assert from "node:assert/strict";
import { buildPersonalizedHome, mergeWatchProgress, resolveSeriesContinuation, searchCatalog } from "../server/tv-personalization.mjs";

const progress = (percentage, updatedAt, completed = false) => ({ percentage, currentTime: percentage * 10, duration: 1000, updatedAt, completed });
const episode = (id, seasonNumber, episodeNumber, watched = null, addedAt = "2026-01-01T00:00:00Z") => ({ id, mediaKey: `episode:${id}`, type: "episode", title: `Episódio ${episodeNumber}`, seasonNumber, episodeNumber, progress: watched, addedAt });
const series = {
    id: "show", mediaKey: "series:show", type: "series", title: "Ação Central", genres: ["Ação"], addedAt: "2026-01-01T00:00:00Z",
    seasons: [
        { seasonNumber: 0, episodes: [episode("special", 0, 1)] },
        { seasonNumber: 1, episodes: [episode("e1", 1, 1, progress(100, "2026-01-02T00:00:00Z", true)), episode("e3", 1, 3, progress(50, "2026-01-03T00:00:00Z"))] },
        { seasonNumber: 2, episodes: [episode("e4", 2, 1)] },
    ],
};
assert.equal(resolveSeriesContinuation(series).episode.mediaKey, "episode:e3", "retoma episódio parcial");
const afterEpisode = structuredClone(series); afterEpisode.seasons[1].episodes[1].progress = progress(100, "2026-01-04T00:00:00Z", true);
assert.equal(resolveSeriesContinuation(afterEpisode).episode.mediaKey, "episode:e4", "avança apesar de episódio ausente");
afterEpisode.seasons[2].episodes[0].progress = progress(100, "2026-01-05T00:00:00Z", true);
assert.equal(resolveSeriesContinuation(afterEpisode).episode.mediaKey, "episode:special", "especiais ficam depois das temporadas regulares");

const catalog = { profile: { id: "adult" }, movies: [
    { id: "liked", mediaKey: "movie:liked", type: "movie", title: "Órbita", genres: ["Ficção"], progress: progress(100, "2026-01-04T00:00:00Z", true) },
    { id: "match", mediaKey: "movie:match", type: "movie", title: "Orbita Dois", genres: ["Ficção"], addedAt: "2026-02-01T00:00:00Z" },
    { id: "other", mediaKey: "movie:other", type: "movie", title: "Drama", genres: ["Drama"] },
], series: [series] };
const state = { favorites: ["series:show"], ratings: { "movie:liked": "like" }, hiddenSuggestions: ["movie:other"], continueDismissed: {} };
const home = buildPersonalizedHome(catalog, state);
assert.equal(home.rows[0].id, "continue-watching");
assert.equal(home.rows.find((row) => row.id === "continue-watching").items[0].resumeMediaKey, "episode:e3");
assert.ok(home.rows.find((row) => row.id === "favorites").items.some((item) => item.mediaKey === "series:show"));
assert.ok(!home.rows.flatMap((row) => row.type === "recommendation" ? row.items : []).some((item) => item.mediaKey === "movie:other"));
const isolated = buildPersonalizedHome(catalog, { favorites: [], ratings: {}, hiddenSuggestions: [], continueDismissed: { "series:show": "2026-02-01T00:00:00Z" } });
assert.ok(!isolated.rows.find((row) => row.id === "continue-watching"), "remoção da fileira não vira rejeição nem vaza de perfil");

assert.equal(searchCatalog(catalog, "orbita")[0].mediaKey, "movie:liked", "busca ignora acentos e prioriza título exato");
assert.equal(searchCatalog(catalog, "orbitaa")[0].mediaKey, "movie:liked", "aceita um pequeno erro");
assert.equal(searchCatalog(catalog, "episodio 3")[0].mediaKey, "series:show", "episódios aparecem agrupados sob a série");
assert.equal(mergeWatchProgress({ completed: true }, { currentTime: 900, percentage: 90, completed: false }).completed, true, "gravação tardia preserva conclusão");
assert.equal(mergeWatchProgress({ completed: true }, { currentTime: 0, percentage: 0, completed: false }).completed, false, "reinício explícito limpa conclusão");
console.log("Personalização TV: continuidade, recomendações, ocultação, lista e busca aprovadas.");
