import assert from "node:assert/strict";
import { normalizeProfileState } from "../server/profile-state.mjs";
import { contentRatingLevel, canAccessContent } from "../js/utils/profiles.js";
import { preserveWatchTimestamp, sortRecentlyWatched } from "../server/recently-watched.mjs";
const empty = normalizeProfileState(null); assert.deepEqual(Object.keys(empty), ["favorites", "progress", "history", "completed", "preferences", "ratings", "hiddenSuggestions", "continueDismissed", "activityEvents", "updatedAt"]);
const state = normalizeProfileState({ favorites: ["1", "1", "movie:1", 2], progress: [], history: "bad", completed: ["movie:1"], preferences: { skipIntro: true, autoplayNext: true, injected: true }, ratings: { "movie:1": "like", "movie:2": "invalid" }, hiddenSuggestions: ["movie:1"], updatedAt: "invalid" }, { validateMediaKey: (key) => /^(movie|series):/.test(key) });
assert.deepEqual(state.favorites, ["1", "movie:1"]); assert.deepEqual(state.progress, {}); assert.deepEqual(state.history, []); assert.deepEqual(state.completed, ["movie:1"]); assert.deepEqual(state.preferences, { skipIntro: true, autoplayNext: true });
assert.deepEqual(state.ratings, { "movie:1": "like" }); assert.deepEqual(state.hiddenSuggestions, ["movie:1"]);
const persisted = JSON.parse(JSON.stringify({ states: { adult: state, guest: normalizeProfileState({ favorites: ["series:show"] }, { validateMediaKey: (key) => key.startsWith("series:") }) } }));
const adultReloaded = normalizeProfileState(persisted.states.adult, { validateMediaKey: (key) => /^(movie|series):/.test(key) });
const guestReloaded = normalizeProfileState(persisted.states.guest, { validateMediaKey: (key) => /^(movie|series):/.test(key) });
assert.deepEqual(adultReloaded.favorites, ["1", "movie:1"]); assert.deepEqual(guestReloaded.favorites, ["series:show"]); assert.ok(!guestReloaded.favorites.includes("movie:1"));
assert.equal(contentRatingLevel("Livre"), 0); assert.equal(contentRatingLevel("PG"), 12); assert.equal(canAccessContent({ audience: "general", contentRating: "PG" }, { kind: "kids", maxContentRating: 10 }), false); assert.equal(canAccessContent({ audience: "kids" }, { kind: "kids", maxContentRating: 10 }), true); assert.equal(canAccessContent({ audience: "adult" }, { kind: "kids" }), false); assert.equal(canAccessContent({ audience: "general" }, { kind: "adult" }), true);
const recent = sortRecentlyWatched([
    { mediaKey: "movie:old", progress: { percentage: 40, updatedAt: "2026-07-20T10:00:00.000Z" } },
    { mediaKey: "series:new", progress: { percentage: 100, completed: true, updatedAt: "2026-07-22T10:00:00.000Z" } },
    { mediaKey: "movie:middle", progress: { percentage: 5, updatedAt: "2026-07-21T10:00:00.000Z" } },
]);
assert.deepEqual(recent.map((item) => item.mediaKey), ["series:new", "movie:middle", "movie:old"]);
assert.equal(preserveWatchTimestamp("2026-07-19T12:00:00.000Z", "fallback"), "2026-07-19T12:00:00.000Z");
assert.equal(preserveWatchTimestamp("inválido", "fallback"), "fallback");
console.log("Perfis: favoritos canônicos, migração, recarga e isolamento aprovados.");
