import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { createLibraryScan } from "../server/library-scan.mjs";
import { createSyncCoordinator } from "../server/sync-coordinator.mjs";
import { createDeviceController } from "../server/device-controller.mjs";
import { ForbiddenError } from "../server/app-errors.mjs";

const tick = () => new Promise((resolve) => setImmediate(resolve));
let resolveRun, runs = 0, progress = 42;
const coordinator = createSyncCoordinator({ runSync: () => {
    runs++;
    return new Promise((resolve) => { resolveRun = resolve; });
} });
const scan = createLibraryScan({ coordinator, getProgress: () => ({ state: "syncing", progress, output: "private path" }) });
assert.equal(scan.status().state, "idle");
const started = scan.request("launcher");
assert.equal(started.state, "syncing");
assert.ok(started.id);
assert.equal(scan.request("tv").id, started.id);
await tick();
assert.equal(runs, 1);
assert.equal(scan.status().progress, 42);
assert.equal(scan.status().output, undefined);
progress = 100;
assert.equal(scan.status().progress, 99);
resolveRun({ code: 0 });
await tick();
assert.equal(scan.status().state, "complete");
assert.equal(scan.status().progress, 100);
assert.notEqual(scan.request("tv").id, started.id);
await tick();
resolveRun({ code: 1, output: "C:/secret/failure" });
await tick();
assert.equal(scan.status().state, "error");
assert.doesNotMatch(JSON.stringify(scan.status()), /secret/);
scan.request("retry");
await tick();
resolveRun({ code: 0 });
await tick();
assert.equal(scan.status().state, "complete");

let authorized = true, enabled = true, calls = 0, sent;
const controller = createDeviceController({
    auth: { requireDevice: async () => { if (!authorized) throw new ForbiddenError("revoked"); return { id: "tv" }; } },
    settingsStore: { load: async () => ({ lanAccessEnabled: enabled }) },
    tvServices: { scan: () => { calls++; return scan.request("tv"); }, scanStatus: scan.status },
    send: (_, status, body) => { sent = { status, body }; },
});
const url = new URL("http://brasa/api/v1/tv/library/scan");
const request = { method: "POST", socket: { remoteAddress: "192.168.1.20" }, headers: {} };
await controller.handle(request, {}, url);
assert.equal(sent.status, 202);
assert.equal(sent.body.data.state, "syncing");
await controller.handle({ ...request, method: "GET" }, {}, url);
assert.equal(sent.status, 200);
assert.equal(calls, 1);
authorized = false;
for (const method of ["GET", "POST"]) await assert.rejects(controller.handle({ ...request, method }, {}, url), ForbiddenError);
authorized = true; enabled = false;
await assert.rejects(controller.handle(request, {}, url), ForbiddenError);
enabled = true;
await assert.rejects(controller.handle({ ...request, socket: { remoteAddress: "8.8.8.8" } }, {}, url), ForbiddenError);
assert.equal(calls, 1);
await tick(); resolveRun({ code: 0 }); await tick();

const launcher = await fs.readFile("scripts/start-brasa-network.ps1", "utf8");
assert.match(launcher, /Invoke-RestMethod -Method Post -Uri "\$baseUrl\/api\/library\/scan"/);
assert.ok(launcher.indexOf('$baseUrl =') > launcher.indexOf('Start-Process $node'));
const settings = await fs.readFile("apps/android-tv/app/src/main/java/com/brasa/tv/feature/settings/SettingsScreen.kt", "utf8");
assert.match(settings, /Buscar novos títulos/);
assert.match(settings, /!state.libraryScanning/);
console.log("Busca de títulos: execução assíncrona, concorrência, progresso, falha/retry e autorização aprovados.");
