import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createTvLibraryCache } from "../server/tv-library-cache.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-tv-cache-")), data = path.join(root, "data");
await fs.mkdir(data);
const write = async (name, body) => fs.writeFile(path.join(data, name), body, "utf8");
try {
    await write("movies.js", "export const getMovies=()=>[{id:'one',title:'Um'}];\n");
    await write("series.js", "export const getSeries=()=>[{id:'show',seasons:[{episodes:[{id:'ep-one'}]}]}];\n");
    await write("collections.js", "export const collections=[{id:'featured'}];\n");
    let clock = 1_000;
    let imports = 0;
    const cache = createTvLibraryCache(root, { checkIntervalMs: 50, now: () => clock, importModule: (url) => { imports++; return import(url); } });
    const first = await cache.load(), repeated = await cache.load();
    assert.equal(first, repeated);
    assert.equal(imports, 3);
    assert.equal(first.movieById.get("one").title, "Um");
    assert.equal(first.episodeById.get("ep-one").series.id, "show");
    await write("movies.js", "export const getMovies=()=>[{id:'two',title:'Dois atualizado'}];\n");
    clock += 60;
    const refreshed = await cache.load();
    assert.notEqual(refreshed, first);
    assert.equal(imports, 6);
    assert.equal(refreshed.movieById.get("two").title, "Dois atualizado");
    console.log("Cache da biblioteca TV: reutilização e invalidação aprovadas.");
} finally { await fs.rm(root, { recursive: true, force: true }); }
