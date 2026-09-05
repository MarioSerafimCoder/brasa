import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

// Exercise the CLI entry point (importing the module alone misses top-level
// initialization errors when an episode has no downloaded thumbnail).
const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-episode-scan-"));
try {
    for (const folder of ["scripts", "server", "data", "assets/series/Lanternas"]) await fs.mkdir(path.join(root, folder), { recursive: true });
    for (const file of ["scripts/sync-movies.mjs", "server/library-config.mjs", "server/metadata-retry-store.mjs"])
        await fs.copyFile(file, path.join(root, file));
    await fs.writeFile(path.join(root, "scripts/setup-env.mjs"), "export async function ensureEnvFile() {}\n");
    await fs.writeFile(path.join(root, "server/media-tools.mjs"), "export async function getMediaToolsStatus() { return {ffmpegAvailable:false}; }\n");
    await fs.writeFile(path.join(root, "server/provider-health.mjs"), `
        export async function checkProviderHealth() { return {recovered:[],providers:Object.fromEntries(['omdb','tmdb','openSubtitles'].map(name=>[name,{available:false}]))}; }
        export async function saveProviderHealth(_root,value) { return value; }
    `);
    await fs.writeFile(path.join(root, "assets/series/Lanternas/Lanternas.S01E03.mkv"), "fixture");
    const success = await run();
    assert.equal(success.code, 0, success.output);
    assert.doesNotMatch(success.output, /before initialization/);
    const catalog = await fs.readFile(path.join(root, "data/series.js"), "utf8");
    assert.match(catalog, /"episodeNumber": 3/);
    assert.match(catalog, /Lanternas.S01E03.mkv/);
    // A real indexing failure must not be reported as a successful scan.
    await fs.rm(path.join(root, "data/series.js"));
    await fs.mkdir(path.join(root, "data/series.js"));
    const failure = await run();
    assert.notEqual(failure.code, 0, failure.output);
    assert.match(failure.output, /erro ao indexar series/);
    console.log("Novo episódio: CLI indexa sem miniatura e retorna erro quando não consegue salvar o catálogo.");
} finally {
    await fs.rm(root, { recursive: true, force: true });
}

function run() {
    return new Promise((resolve, reject) => {
        const child = spawn(process.execPath, ["scripts/sync-movies.mjs", "--series-only"], { cwd: root, windowsHide: true });
        let output = "";
        child.stdout.on("data", (chunk) => { output += chunk; });
        child.stderr.on("data", (chunk) => { output += chunk; });
        child.on("error", reject);
        child.on("close", (code) => resolve({ code, output }));
    });
}
