import assert from "node:assert/strict";
import { legacyProgressId, normalizeLegacyFavorites } from "../js/services/profile-service.js";
assert.deepEqual(normalizeLegacyFavorites({ 1: true, 2: false }), ["1"]); assert.deepEqual(normalizeLegacyFavorites(["1", "1", "2"]), ["1", "2"]); assert.deepEqual(normalizeLegacyFavorites("inválido"), []);
assert.equal(legacyProgressId("brasa:movie-progress:42"), "42"); assert.equal(legacyProgressId("brasa:movie-progress:perfil:42"), ""); assert.equal(legacyProgressId("brasa:movie-progress:movie:42"), ""); assert.equal(legacyProgressId("brasa:movie-progress:"), "");
console.log("Migrações: 7 cenários aprovados.");
