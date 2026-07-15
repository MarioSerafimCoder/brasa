import { ForbiddenError, NotFoundError } from "./app-errors.mjs";
import { isLoopbackAddress, isPrivateClientAddress } from "./network-access.mjs";

export function createDeviceController({ pairing, auth, settingsStore, deviceStore, networkInfo, tvServices, updateService, networkDiagnostics, networkInspector, getPort = () => 4173, readBody, send }) {
    async function handle(request, response, url) {
        const path = decodeURIComponent(url.pathname), method = request.method;
        if (!isPrivateClientAddress(request.socket?.remoteAddress)) throw new ForbiddenError("O modo TV aceita somente dispositivos da rede privada.");
        const currentSettings = await settingsStore.load();
        if (!currentSettings.lanAccessEnabled && !isLoopbackAddress(request.socket?.remoteAddress)) throw new ForbiddenError("O acesso pela rede está desativado.");
        if (path === "/api/v1/bootstrap" && method === "GET") return success(response, {
            name: currentSettings.serverName || "BRasa", apiVersion: 1, serverVersion: "1.0.0",
            lanEnabled: currentSettings.lanAccessEnabled, pairingRequired: true,
            capabilities: { pairing: true, profiles: true, catalog: true, homeRows: true, search: true, progressivePlayback: true, adaptiveHls: true, rangeRequests: true, subtitles: true, audioTracks: true }
        });
        if (path === "/api/device-pairing/start" && method === "POST") { const body = await readBody(request); return success(response, await pairing.start({ name: body.name, type: body.type, ip: request.socket?.remoteAddress || "" }), 201); }
        const pairingStatus = path.match(/^\/api\/device-pairing\/status\/([A-Za-z0-9_-]{12,80})$/);
        if (pairingStatus && method === "GET") return success(response, pairing.status(pairingStatus[1]));
        const androidUpdate=path==="/api/v1/android-tv/update",androidApk=path==="/api/v1/android-tv/update/apk";
        const networkRoute = path.startsWith("/api/v1/network/");
        if (!path.startsWith("/api/tv/") && !path.startsWith("/api/v1/tv/")&&!androidUpdate&&!androidApk&&!networkRoute) throw new NotFoundError("Rota de dispositivo não encontrada.");
        const device = await auth.requireDevice(request);
        if(androidUpdate&&method==="GET")return success(response,await updateService.check(request,device));
        if(androidApk&&["GET","HEAD"].includes(method))return updateService.download(request,response,device);
        if (path === "/api/v1/network/status" && method === "GET") return success(response, { server: await networkInspector.inspect(getPort()), firewall: await networkInspector.firewall(getPort()) });
        if (path === "/api/v1/network/interfaces" && method === "GET") return success(response, { active: await networkInspector.inspect(getPort()), addresses: networkInfo(getPort()) });
        if (path === "/api/v1/network/test" && method === "POST") return success(response, networkDiagnostics.start(device.id, await readBody(request)), 201);
        const networkTest = path.match(/^\/api\/v1\/network\/test\/([A-Za-z0-9_-]{12,80})(?:\/(stream|cancel))?$/);
        if (networkTest) { const [, id, action] = networkTest; if (method === "GET" && !action) return success(response, networkDiagnostics.status(id, device.id)); if (method === "GET" && action === "stream") return networkDiagnostics.stream(id, device.id, request, response); if (method === "POST" && action === "cancel") return success(response, networkDiagnostics.cancel(id, device.id)); }
        if (path === "/api/tv/session" && method === "POST") { const token = String(request.headers?.["x-brasa-device-token"] || ""); response.setHeader("Set-Cookie", `brasa_device_token=${encodeURIComponent(token)}; HttpOnly; SameSite=Strict; Path=/api/tv; Max-Age=2592000`); return success(response, { authenticated: true, device }); }
        if (path === "/api/tv/profiles" && method === "GET") return success(response, await tvServices.profiles(device));
        if (path === "/api/tv/catalog" && method === "GET") { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return success(response, await tvServices.catalog(device, profileId)); }
        if (path === "/api/v1/tv/home" && method === "GET") { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return success(response, await tvServices.home(device, profileId)); }
        if (path === "/api/v1/tv/search" && method === "GET") { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return success(response, await tvServices.search(device, profileId, url.searchParams.get("q") || "")); }
        const playback = path.match(/^\/api\/v1\/tv\/playback\/(movie|episode):([^/]+)$/);
        if (playback && method === "GET") { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return success(response, await tvServices.playback(device, profileId, `${playback[1]}:${playback[2]}`)); }
        const progress = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/progress\/(movie|episode):([^/]+)$/);
        if (progress) { const profileId = auth.requireProfile(device, progress[1]), mediaKey = `${progress[2]}:${progress[3]}`; if (method === "GET") return success(response, await tvServices.progress(profileId, mediaKey)); if (method === "PUT") return success(response, await tvServices.saveProgress(profileId, mediaKey, await readBody(request))); }
        const favorite = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/favorites\/(movie|episode):([^/]+)$/);
        if (favorite && ["PUT", "DELETE"].includes(method)) { const profileId = auth.requireProfile(device, favorite[1]), mediaKey = `${favorite[2]}:${favorite[3]}`; return success(response, await tvServices.saveFavorite(profileId, mediaKey, method === "PUT")); }
        const verifyPin = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/verify-pin$/);
        if (verifyPin && method === "POST") { const profileId = auth.requireProfile(device, verifyPin[1]); return success(response, { valid: await tvServices.verifyPin(profileId, (await readBody(request)).pin) }); }
        const stream = path.match(/^\/api\/tv\/stream\/(movie|episode):([^/]+)$/);
        if (stream && ["GET", "HEAD"].includes(method)) { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return tvServices.stream(request, response, `${stream[1]}:${stream[2]}`, profileId); }
        const hls = path.match(/^\/api\/v1\/tv\/hls\/([a-f0-9]{24})\/(.+)$/);
        if (hls && ["GET", "HEAD"].includes(method)) return tvServices.hls(request, response, device, hls[1], hls[2]);
        throw new NotFoundError("Recurso da TV não encontrado.");
    }
    const admin = {
        async network(port) { const settings = await settingsStore.load(); return { ...settings, enabled: settings.lanAccessEnabled, host: settings.lanAccessEnabled ? "0.0.0.0" : "127.0.0.1", port, addresses: networkInfo(port), activeInterface: await networkInspector.inspect(port), firewall: await networkInspector.firewall(port), restartRequired: false }; },
        async updateNetwork(input, port, currentHost) { const settings = await settingsStore.save(input); return { ...await this.network(port), restartRequired: (settings.lanAccessEnabled ? "0.0.0.0" : "127.0.0.1") !== currentHost }; },
        pairings: () => pairing.list(), approve: async (id, input) => pairing.approve(id, input.allowedProfileIds || []), reject: (id) => pairing.reject(id),
        devices: () => deviceStore.list(),updateStatus:()=>updateService.status(), updateDevice: (id, input) => deviceStore.update(id, input), revokeDevice: (id) => deviceStore.revoke(id), removeDevice: (id) => deviceStore.remove(id)
    };
    return { handle, admin };
    function success(response, data, status = 200) { send(response, status, { ok: true, data }); }
}
