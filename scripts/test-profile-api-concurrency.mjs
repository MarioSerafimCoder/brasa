import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import net from "node:net";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
import { createDeviceStore } from "../server/device-store.mjs";

// Real HTTP routes, disposable library: never touch the user's profiles or credentials.
const source = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-profile-api-"));
let child, stopped, output = "";
try {
    await Promise.all(["scripts", "server", "js"].map(folder => fs.cp(path.join(source, folder), path.join(root, folder), { recursive: true })));
    await fs.writeFile(path.join(root, "package.json"), '{"type":"module"}');
    await fs.mkdir(path.join(root, "data"));
    await fs.writeFile(path.join(root, "data/movies.js"), `export const getMovies = () => ${JSON.stringify(Array.from({ length: 12 }, (_, i) => ({ id: String(i + 1), title: `Test ${i + 1}`, audience: "general", video: `videos/test-${i + 1}.mp4`, playable: true })))};`);
    await fs.writeFile(path.join(root, "data/series.js"), 'export const getSeries = () => [];');
    await fs.writeFile(path.join(root, "data/collections.js"), 'export const collections = [];');
    const device = await createDeviceStore(root).create({ name: "Test TV", type: "tv" });
    const reserve = net.createServer(); reserve.listen(0, "127.0.0.1"); await once(reserve, "listening");
    const port = reserve.address().port; await new Promise(resolve => reserve.close(resolve));
    child = spawn(process.execPath, [path.join(root, "scripts/brasa-server.mjs")], { cwd: root, windowsHide: true,
        env: { ...process.env, BRASA_PORT: String(port), BRASA_HOST: "127.0.0.1", BRASA_SKIP_STARTUP_SYNC: "1", BRASA_WATCH_LIBRARY: "0", BRASA_DAILY_RECOVERY: "0" }, stdio: ["ignore", "pipe", "pipe"] });
    stopped = once(child, "exit");
    child.stdout.on("data", data => output += data); child.stderr.on("data", data => output += data);
    let state;
    for (let attempt = 0; attempt < 100; attempt++) {
        if (child.exitCode !== null) throw new Error(output);
        state = await fs.readFile(path.join(root, ".brasa-server.json"), "utf8").then(JSON.parse).catch(() => null);
        if (state) break;
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.ok(state, output);
    const base = `http://127.0.0.1:${state.port}`;
    async function call(url, method = "GET", body, tv = false, expected = 200) {
        const response = await fetch(base + url, { method, signal: AbortSignal.timeout(10_000),
            headers: { "Content-Type": "application/json", "x-brasa-request": "1", ...(tv ? { "x-brasa-device-token": device.token } : {}) },
            ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
        const payload = await response.json(); assert.equal(response.status, expected, JSON.stringify(payload)); return payload;
    }
    const initial = (await call("/api/profiles/mario/state")).state;
    await Promise.all(Array.from({ length: 12 }, (_, i) => [
        call(`/api/tv/profiles/mario/progress/movie:${i + 1}`, "PUT", { currentTime: i + 10, duration: 100, percentage: i + 10 }, true),
        call(`/api/profiles/mario/favorites/${i + 1}`, "PUT", { enabled: true }),
    ]).flat().concat([
        call("/api/profiles/mario/preferences", "PUT", { skipIntro: true }),
        call("/api/v1/tv/profiles/mario/preferences", "PUT", { autoplayNext: true }, true),
    ]));
    const current = (await call("/api/profiles/mario/state")).state;
    assert.equal(Object.keys(current.progress).length, 12); assert.equal(current.favorites.length, 12);
    assert.equal(current.preferences.skipIntro, true); assert.equal(current.preferences.autoplayNext, true);
    await call("/api/profiles/mario/state", "PUT", initial, false, 409);
    await call("/api/profiles/mario/state", "PATCH", { ...initial, favorites: ["movie:99"], preferences: { autoplayNext: false } });
    const imported = (await call("/api/profiles/mario/state")).state;
    assert.equal(imported.favorites.length, 13); assert.equal(Object.keys(imported.progress).length, 12);
    assert.equal(imported.preferences.autoplayNext, true);
    await call("/api/tv/profiles/mario/progress/movie:1", "PUT", { currentTime: 1, updatedAt: "2020-01-01T00:00:00.000Z" }, true);
    assert.equal((await call("/api/tv/profiles/mario/progress/movie:1", "GET", undefined, true)).data.currentTime, 10);
    console.log("HTTP real isolado: progresso TV + favoritos web + preferências simultâneas, importação e recusa de dados antigos aprovados.");
} finally {
    if (child && child.exitCode === null) { child.kill(); await stopped; }
    const checked = path.resolve(root);
    if (path.dirname(checked) !== path.resolve(os.tmpdir()) || !path.basename(checked).startsWith("brasa-profile-api-")) throw new Error("Unsafe temporary directory");
    await fs.rm(checked, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
