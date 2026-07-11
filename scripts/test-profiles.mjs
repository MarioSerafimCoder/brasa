import assert from "node:assert/strict";
import { normalizeProfileState } from "../server/profile-state.mjs";
import { contentRatingLevel, canAccessContent } from "../js/utils/profiles.js";
const empty = normalizeProfileState(null); assert.deepEqual(Object.keys(empty), ["favorites", "progress", "history", "completed", "preferences", "updatedAt"]);
const state = normalizeProfileState({ favorites: ["1", "1", 2], progress: [], history: "bad", completed: ["movie:1"], preferences: { skipIntro: true, injected: true }, updatedAt: "invalid" }, { validateMediaKey: (key) => key.startsWith("movie:") });
assert.deepEqual(state.favorites, ["1"]); assert.deepEqual(state.progress, {}); assert.deepEqual(state.history, []); assert.deepEqual(state.completed, ["movie:1"]); assert.deepEqual(state.preferences, { skipIntro: true });
assert.equal(contentRatingLevel("Livre"), 0); assert.equal(contentRatingLevel("PG"), 12); assert.equal(canAccessContent({ audience: "general", contentRating: "PG" }, { kind: "kids", maxContentRating: 10 }), false); assert.equal(canAccessContent({ audience: "kids" }, { kind: "kids", maxContentRating: 10 }), true); assert.equal(canAccessContent({ audience: "adult" }, { kind: "kids" }), false); assert.equal(canAccessContent({ audience: "general" }, { kind: "adult" }), true);
console.log("Perfis: 12 cenários aprovados.");
