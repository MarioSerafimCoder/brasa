import assert from "node:assert/strict";
import { createDeviceController } from "../server/device-controller.mjs";
import { startServiceDiscovery } from "../server/service-discovery.mjs";

let payload;
const sent = [];
const send = (_response, status, body) => sent.push({ status, body });
const services = {
    profiles: async () => [{ id: "adult", name: "Adulto" }],
    catalog: async () => ({ movies: [] }),
    home: async () => ({ profile: { id: "adult" }, rows: [{ id: "movies", title: "Filmes", items: [] }] }),
    search: async (_device, _profile, query) => [{ mediaKey: "movie:42", title: query }],
    playback: async (_device, _profile, key) => ({ mediaKey: key, playbackUrl: `/api/tv/stream/${key}` }),
    progress: async () => null, saveProgress: async () => ({}), saveFavorite: async () => ({ favorite: true }), verifyPin: async () => true, stream: async () => {}
};
const controller = createDeviceController({
    pairing: { start: async () => ({}), status: () => ({}), list: () => [], approve: async () => ({}), reject: () => ({}) },
    auth: { requireDevice: async () => ({ id: "tv", allowedProfileIds: ["adult"] }), requireProfile: (_device, id) => id },
    settingsStore: { load: async () => ({ lanAccessEnabled: true, serverName: "BRasa Sala" }), save: async (value) => value },
    deviceStore: { list: async () => [], update: async () => ({}), revoke: async () => ({}), remove: async () => ({}) },
    networkInfo: () => [],
    networkInspector: { inspect: async () => ({ available: true, type: "ethernet", ip: "192.168.1.10" }), firewall: async () => ({ configured: true }) },
    networkDiagnostics: { start: () => ({ id: "network-test-1", state: "ready" }), status: () => ({ id: "network-test-1", state: "running" }), cancel: () => ({ id: "network-test-1", state: "cancelled" }) },
    tvServices: services, readBody: async () => payload || {}, send
});
const request = { method: "GET", headers: {}, socket: { remoteAddress: "192.168.1.25" } };
const response = { setHeader() {} };

await controller.handle(request, response, new URL("http://brasa/api/v1/bootstrap"));
assert.equal(sent.pop().body.data.apiVersion, 1);
await controller.handle(request, response, new URL("http://brasa/api/v1/network/status"));
assert.equal(sent.pop().body.data.server.type, "ethernet");
request.method = "POST";payload = { profile: "1080p" };await controller.handle(request, response, new URL("http://brasa/api/v1/network/test"));assert.equal(sent.pop().status, 201);
request.method = "GET";await controller.handle(request, response, new URL("http://brasa/api/v1/network/test/network-test-1"));assert.equal(sent.pop().body.data.state, "running");
request.method = "POST";await controller.handle(request, response, new URL("http://brasa/api/v1/network/test/network-test-1/cancel"));assert.equal(sent.pop().body.data.state, "cancelled");request.method = "GET";
await controller.handle(request, response, new URL("http://brasa/api/v1/tv/home?profileId=adult"));
assert.equal(sent.pop().body.data.rows[0].id, "movies");
await controller.handle(request, response, new URL("http://brasa/api/v1/tv/search?profileId=adult&q=Superman"));
assert.equal(sent.pop().body.data[0].title, "Superman");
await controller.handle(request, response, new URL("http://brasa/api/v1/tv/playback/movie:42?profileId=adult"));
assert.equal(sent.pop().body.data.mediaKey, "movie:42");

let published, stopped = false;
const discovery = await startServiceDiscovery({ enabled: true, name: "BRasa Sala", port: 4173, createPublisher: async () => ({
    async start(options) { published = options; }, async stop() { stopped = true; }
}) });
assert.equal(discovery.active, true);
assert.deepEqual(published.txt, { api: "1", pairing: "required" });assert.equal(published.type, "_brasa._tcp.local");
await discovery.stop();
assert.equal(stopped, true);
assert.equal((await startServiceDiscovery({ enabled: false })).active, false);
console.log("API Android TV: bootstrap, home, playback e descoberta aprovados.");
