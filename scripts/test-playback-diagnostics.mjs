import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { createPlaybackDiagnostics, validatePlaybackBatch } from "../server/playback-diagnostics.mjs";
import { createAdaptiveLadder } from "../server/transcoding-profiles.mjs";
import { buildHlsArgs } from "../server/hls-session.mjs";
import { createDeviceController } from "../server/device-controller.mjs";
import { ForbiddenError, UnauthorizedError } from "../server/app-errors.mjs";
const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-playback-history-"));
let now = Date.now();
const history = createPlaybackDiagnostics({ rootDir: root, now: () => now, maximumSessions: 3, maximumEvents: 3 });
const event = (sequence, kind, extra = {}) => ({ sequence, elapsedMs: sequence * 1000, kind, ...extra });
const batch = { id: randomUUID(), mediaKey: "episode:test", events: [event(1, "start"), event(2, "buffer_start"), event(3, "buffer_end", { durationMs: 2300, bandwidthBps: 500000, bitrate: 6500000, token: "SECRET", url: "http://secret" })] };
try {
    let payload, result;
    const controller = createDeviceController({
        auth: {
            requireDevice: async request => { if (request.headers.token !== "test-token") throw new UnauthorizedError(); return { id: "tv1" }; },
            requireProfile: (_device, profile) => { if (profile !== "p1") throw new ForbiddenError(); return profile; },
        }, settingsStore: { load: async () => ({ lanAccessEnabled: true }) },
        tvServices: { playbackDiagnostics: {
            append: (device, profile, batch) => history.append(device.id, profile, "Teste", batch),
            read: (device, profile, id) => id ? history.detail(device.id, profile, id) : history.list(device.id, profile),
        } }, readBody: async () => payload, send: (_response, _status, body) => { result = body.data; },
    });
    const request = { method: "GET", socket: { remoteAddress: "192.168.1.2" }, headers: {} };
    const historyUrl = new URL("http://brasa/api/v1/tv/playback-history?profileId=p1");
    await assert.rejects(controller.handle(request, {}, historyUrl), { status: 401 });
    request.headers.token = "test-token";
    await assert.rejects(controller.handle(request, {}, new URL("http://brasa/api/v1/tv/playback-history?profileId=p2")), { status: 403 });
    payload = batch; request.method = "POST";
    await controller.handle(request, {}, historyUrl); assert.equal(result.accepted, 3);
    request.method = "GET";
    await controller.handle(request, {}, historyUrl); assert.equal(result.length, 1);
    await controller.handle(request, {}, new URL(`http://brasa/api/v1/tv/playback-history/${batch.id}?profileId=p1`)); assert.equal(result.id, batch.id);
    await Promise.all([history.append("tv1", "p1", "Teste", batch), history.append("tv1", "p1", "Teste", batch)]);
    let detail = await history.detail("tv1", "p1", batch.id);
    assert.equal(detail.bufferCount, 1); assert.equal(detail.bufferDurationMs, 2300);
    assert.equal(detail.indications.length, 1);
    assert.equal(JSON.stringify(detail).includes("SECRET"), false);
    assert.equal(JSON.stringify(detail).includes("http://"), false);
    assert.deepEqual(await history.list("tv2", "p1"), []);
    assert.deepEqual(await history.list("tv1", "p2"), []);
    await assert.rejects(history.detail("tv2", "p1", batch.id), { status: 404 });
    await assert.rejects(history.append("tv2", "p1", "Teste", batch), { status: 403 });
    await assert.rejects(history.append("tv1", "p1", "Teste", { ...batch, mediaKey: "movie:other" }), { status: 403 });
    const sid = "a".repeat(24);
    await history.append("tv1", "p1", "Teste", { ...batch, events: [event(4, "conversion"), event(5, "sample", { hlsSessionId: sid }), event(6, "error", { errorCode: 4003 }), event(7, "end")] }, { id: sid, state: "preparing", encoder: "libx264", encodingSpeed: .6, outputSeconds: 35 });
    detail = await history.detail("tv1", "p1", batch.id);
    assert.equal(detail.events.length, 3); assert.equal(detail.bufferCount, 1);
    assert.equal(detail.errors, 1); assert.equal(detail.conversions, 1); assert.equal(detail.ended, true);
    assert.ok(detail.indications.some(text => text.startsWith("Servidor:")));
    assert.ok(detail.indications.some(text => text.startsWith("TV:")));
    assert.equal((await createPlaybackDiagnostics({ rootDir: root }).list("tv1", "p1")).length, 1, "persiste após reinício");
    assert.throws(() => validatePlaybackBatch({ ...batch, events: [event(1, "error", { durationMs: -1 })] }), { status: 400 });
    assert.throws(() => validatePlaybackBatch({ ...batch, events: Array(41).fill(event(1, "sample")) }), { status: 400 });
    assert.throws(() => validatePlaybackBatch({ ...batch, events: [event(2, "sample"), event(1, "sample")] }), { status: 400 });
    await assert.rejects(history.append("tv1", "p1", "Teste", { ...batch, events: [event(8, "sample", { elapsedMs: 1 })] }), { status: 400 });
    for (let n = 0; n < 4; n++) { now++; await history.append("tv1", "p1", "Teste", { ...batch, id: randomUUID() }); }
    assert.equal((await history.list("tv1", "p1")).length, 3);
    now += 31 * 86400000; assert.equal((await history.list("tv1", "p1")).length, 0);
    const probe = { video: { width: 1920, height: 1080, frameRate: 23.976 }, audioTracks: [{}] };
    const gpu = createAdaptiveLadder(probe, {}, { nvenc: true });
    assert.deepEqual(gpu.map(q => q.id), ["480p", "720p", "1080p"]);
    assert.deepEqual(createAdaptiveLadder(probe).map(q => q.id), ["480p", "720p"]);
    assert.deepEqual(createAdaptiveLadder(probe, { acceleration: "cpu" }, { nvenc: true }).map(q => q.id), ["480p", "720p"]);
    assert.deepEqual(createAdaptiveLadder(probe, {}, { nvenc: true }, { maxHeight: 720 }).map(q => q.id), ["480p", "720p"]);
    assert.deepEqual(createAdaptiveLadder(probe, {}, { nvenc: true }, { maxHeight: 2160, videoCapabilities: [{ codec: "h264", hardware: true, maxHeight: 720, maxWidth: 1280 }] }).map(q => q.id), ["480p", "720p"]);
    const small = createAdaptiveLadder({ video: { width: 640, height: 360 } });
    assert.ok(small.every(q => q.height <= 360 && q.width <= 640));
    const args = buildHlsArgs("source.mkv", "output", gpu, "h264_nvenc", probe);
    for (let i = 0; i < 3; i++) assert.ok(args.includes(`-force_key_frames:v:${i}`), "todas as variantes alinham keyframes");
    console.log("Histórico: privacidade, validação, repetição, concorrência, retenção e persistência; ABR: GPU, CPU e limites aprovados.");
} finally { await fs.rm(root, { recursive: true, force: true }); }
