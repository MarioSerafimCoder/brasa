import assert from "node:assert/strict";
import { sortRecentlyAdded } from "../server/recently-added.mjs";

const items = [
    { mediaKey: "movie:old", addedAt: "2026-07-01T12:00:00.000Z" },
    { mediaKey: "series:new", addedAt: "2026-08-02T22:52:54.469Z" },
    { mediaKey: "movie:newest", addedAt: "2026-08-02T23:39:01.166Z" },
    { mediaKey: "movie:unknown", addedAt: "" },
];

assert.deepEqual(sortRecentlyAdded(items).map((item) => item.mediaKey), ["movie:newest", "series:new", "movie:old"]);
assert.deepEqual(sortRecentlyAdded(items, 2).map((item) => item.mediaKey), ["movie:newest", "series:new"]);
assert.deepEqual(sortRecentlyAdded(null), []);
console.log("Recém-adicionados: filmes e séries ordenados pela entrada real na biblioteca.");
