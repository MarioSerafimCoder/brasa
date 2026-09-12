import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { createHlsSessionManager } from "../server/hls-session.mjs";
import { createMediaCache } from "../server/media-cache.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-hls-lifecycle-"));
const children = [];
const store = { update: async () => {}, settings: async () => ({ maxHlsGb: 1 }) };
const manager = createHlsSessionManager({
    rootDir: root, store,
    getTools: async () => ({ ffmpegAvailable: true, ffmpegPath: "ffmpeg", hardwareAcceleration: { nvenc: true } }),
    spawnProcess: () => {
        const child = new EventEmitter();
        child.stdout = new PassThrough(); child.stderr = new PassThrough();
        child.kill = () => { queueMicrotask(() => child.emit("close", 1)); };
        children.push(child);
        return child;
    },
});
const probe = { duration: 120, fingerprint: { size: 1000, mtimeMs: 1 }, video: { codec: "h264", width: 1920, height: 1080 }, audioTracks: [{ codec: "aac" }] };
const cache = createMediaCache({ rootDir: root, store, protectedHlsSessions: manager.protectedSessionIds });
try {
    const sessions = await Promise.all(Array.from({ length: 8 }, () => manager.ensure("movie:race", "source.mkv", probe)));
    assert.equal(new Set(sessions.map(s => s.id)).size, 1);
    await waitFor(() => children.length === 1);
    await new Promise(resolve => setTimeout(resolve, 50));
    assert.equal(children.length, 1, "pedidos simultâneos só podem criar um encoder");
    const id = sessions[0].id;
    const directory = path.join(root, "data", "prepared-media", "hls", id);
    const variant = path.join(directory, "1080p");
    await fs.writeFile(path.join(directory, "master.m3u8"), "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=6500000\n1080p/index.m3u8\n");
    let playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-PLAYLIST-TYPE:EVENT\n";
    for (let n = 0; n < 6; n++) {
        const name = `seg-${String(n).padStart(6, "0")}.ts`;
        playlist += `#EXTINF:2.000,\n${name}\n`;
        await fs.writeFile(path.join(variant, name), Buffer.alloc(32, 1));
    }
    await fs.writeFile(path.join(variant, "index.m3u8"), playlist);
    await fs.writeFile(path.join(variant, "seg-000006.ts.tmp"), "still encoding");
    assert.equal((await manager.ensure("movie:race", "source.mkv", probe)).state, "ready");
    assert.ok(await manager.resolve(id, "1080p/seg-000000.ts"));
    await cache.cleanup({ maxBytes: 1 });
    await cache.clearHls();
    assert.ok((await fs.stat(path.join(variant, "seg-000006.ts.tmp"))).size, "limpeza deve preservar escrita ativa");
    assert.ok((await fs.stat(path.join(variant, "seg-000000.ts"))).size, "limpeza deve preservar reprodução ativa");
    children[0].stderr.write("simulated encoder failure after playback started");
    children[0].emit("close", 1);
    await waitFor(() => manager.snapshot().length === 0);
    assert.equal(children.length, 1, "falha após publicar vídeo não pode sobrescrever segmentos usando outro encoder");
    assert.ok((await fs.stat(path.join(variant, "seg-000000.ts"))).size);
    assert.ok(manager.protectedSessionIds().has(id), "sessão recentemente lida deve permanecer protegida após fim do encoder");
    await manager.remove(id);
    console.log("Ciclo HLS: concorrência, cache ativo e falha de encoder sem sobrescrita aprovados.");
} finally {
    for (const session of manager.snapshot()) await manager.remove(session.id);
    await fs.rm(root, { recursive: true, force: true });
}

async function waitFor(predicate) {
    const deadline = Date.now() + 3000;
    while (!predicate()) {
        if (Date.now() >= deadline) throw new Error("Tempo excedido aguardando sessão HLS.");
        await new Promise(resolve => setTimeout(resolve, 10));
    }
}
