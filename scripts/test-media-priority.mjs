import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { createMediaQueue } from "../server/media-queue.mjs";
import { createHlsSessionManager } from "../server/hls-session.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-media-priority-"));
try {
    const movieDirectory = path.join(root, "assets", "movies");
    await fs.mkdir(movieDirectory, { recursive: true });
    await fs.writeFile(path.join(movieDirectory, "a.mkv"), Buffer.alloc(128));
    await fs.writeFile(path.join(movieDirectory, "b.mkv"), Buffer.alloc(128));

    const items = {};
    const store = {
        get: async (key) => items[key],
        update: async (key, patch) => (items[key] = { ...(items[key] || {}), ...patch }),
        settings: async () => ({ quality: "balanced", minimumFreeGb: 0, generateThumbnails: false, extractSubtitles: false }),
        setSettings: async () => ({}),
        all: async () => ({ items })
    };
    const media = {
        a: { mediaType: "movie", mediaId: "a", originalPath: "assets/movies/a.mkv" },
        b: { mediaType: "movie", mediaId: "b", originalPath: "assets/movies/b.mkv" }
    };
    let releaseFirstProbe;
    let firstProbeStarted;
    const firstProbeGate = new Promise((resolve) => { releaseFirstProbe = resolve; });
    const firstProbeSignal = new Promise((resolve) => { firstProbeStarted = resolve; });
    let first = true;
    const probe = async (file) => {
        if (first) {
            first = false;
            firstProbeStarted();
            await firstProbeGate;
        }
        const stat = await fs.stat(file);
        return { schemaVersion: 3, duration: 100, fingerprint: { size: stat.size, mtimeMs: Math.round(stat.mtimeMs) }, video: { codec: "h264" }, audioTracks: [{ codec: "dts" }], subtitleTracks: [] };
    };
    let spawned = 0;
    const queue = createMediaQueue({
        rootDir: root,
        store,
        getTools: async () => ({ ffprobeAvailable: true, ffmpegAvailable: true, ffprobePath: "ffprobe", ffmpegPath: "ffmpeg", hardwareAcceleration: {} }),
        resolveMedia: async (key) => media[key],
        probe,
        spawnProcess: () => { spawned++;throw new Error("A preparação completa não deveria iniciar."); },
        decideStrategy: () => ({ strategy: "audio-transcode", videoAction: "copy", audioAction: "aac", reason: "teste" })
    });

    await queue.analyze("a", { prepare: true, priority: 0 });
    await firstProbeSignal;
    await queue.analyze("b", { prepare: true, priority: 0 });
    queue.prioritize("b", 100, { prepare: false, preemptOther: true });
    let snapshot = queue.snapshot();
    assert.equal(snapshot.active[0].mediaKey, "a");
    assert.equal(snapshot.active[0].prepare, false, "trabalho antigo deve ceder à reprodução");
    assert.equal(snapshot.queued[0].mediaKey, "b");
    assert.equal(snapshot.queued[0].priority, 100);
    assert.equal(snapshot.queued[0].prepare, false, "pedido da TV deve analisar sem converter o filme inteiro");

    releaseFirstProbe();
    await waitFor(() => queue.snapshot().active.length === 0 && queue.snapshot().queued.length === 0);
    assert.equal(spawned, 0);
    assert.equal(items.a.status, "pending");
    assert.equal(items.b.status, "pending");

    const children = [];
    const hls = createHlsSessionManager({
        rootDir: root,
        store,
        getTools: async () => ({ ffmpegAvailable: true, ffmpegPath: "ffmpeg", hardwareAcceleration: {} }),
        spawnProcess: () => {
            const child = new EventEmitter();
            child.stdout = new PassThrough();
            child.stderr = new PassThrough();
            child.killed = false;
            child.kill = () => { if (!child.killed) { child.killed = true;queueMicrotask(() => child.emit("close", 1)); } };
            children.push(child);
            return child;
        }
    });
    const hlsProbe = { schemaVersion: 3, duration: 100, fingerprint: { size: 128, mtimeMs: 1 }, video: { codec: "h264", width: 1920, height: 1080, frameRate: 24 }, audioTracks: [{ codec: "aac" }] };
    await hls.ensure("movie:a", path.join(movieDirectory, "a.mkv"), hlsProbe, { hlsSegmentSeconds: 2, hlsStartBufferSeconds: 4 }, { mode: "transcode" });
    await waitFor(() => children.length === 1);
    const secondSession = await hls.ensure("movie:b", path.join(movieDirectory, "b.mkv"), hlsProbe, { hlsSegmentSeconds: 2, hlsStartBufferSeconds: 4 }, { mode: "transcode" });
    await waitFor(() => children.length === 2);
    assert.equal(children[0].killed, false, "outro título não pode interromper o filme em reprodução");
    const replacementSession = await hls.ensure("movie:a", path.join(movieDirectory, "a.mkv"), hlsProbe, { hlsSegmentSeconds: 2, hlsStartBufferSeconds: 4 }, { mode: "transcode", startPositionMs: 10_000 });
    await waitFor(() => children[0].killed && children.length === 3);
    await hls.remove(secondSession.id);
    await hls.remove(replacementSession.id);
    assert.equal(children[0].killed, true, "um novo ponto do mesmo título deve substituir somente sua sessão anterior");
    console.log("Prioridade de mídia: fila avança e sessões HLS de títulos diferentes permanecem isoladas.");
} finally {
    await fs.rm(root, { recursive: true, force: true });
}

async function waitFor(predicate, timeout = 3000) {
    const deadline = Date.now() + timeout;
    while (!predicate()) {
        if (Date.now() > deadline) throw new Error("A fila não concluiu no prazo do teste.");
        await new Promise((resolve) => setTimeout(resolve, 20));
    }
}
