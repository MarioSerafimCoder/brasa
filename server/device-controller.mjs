import { ForbiddenError, NotFoundError } from "./app-errors.mjs";
import { isLoopbackAddress, isPrivateClientAddress } from "./network-access.mjs";

export function createDeviceController({ pairing, auth, settingsStore, deviceStore, networkInfo, tvServices, readBody, send }) {
    async function handle(request, response, url) {
        const path = decodeURIComponent(url.pathname), method = request.method;
        if (!isPrivateClientAddress(request.socket?.remoteAddress)) throw new ForbiddenError("O modo TV aceita somente dispositivos da rede privada.");
        const currentSettings = await settingsStore.load();
        if (!currentSettings.lanAccessEnabled && !isLoopbackAddress(request.socket?.remoteAddress)) throw new ForbiddenError("O acesso pela rede está desativado.");
        if (path === "/api/device-pairing/start" && method === "POST") { const body = await readBody(request); return success(response, await pairing.start({ name: body.name, type: body.type, ip: request.socket?.remoteAddress || "" }), 201); }
        const pairingStatus = path.match(/^\/api\/device-pairing\/status\/([A-Za-z0-9_-]{12,80})$/);
        if (pairingStatus && method === "GET") return success(response, pairing.status(pairingStatus[1]));
        if (!path.startsWith("/api/tv/")) throw new NotFoundError("Rota de dispositivo não encontrada.");
        const device = await auth.requireDevice(request);
        if (path === "/api/tv/session" && method === "POST") { const token = String(request.headers?.["x-brasa-device-token"] || ""); response.setHeader("Set-Cookie", `brasa_device_token=${encodeURIComponent(token)}; HttpOnly; SameSite=Strict; Path=/api/tv; Max-Age=2592000`); return success(response, { authenticated: true, device }); }
        if (path === "/api/tv/profiles" && method === "GET") return success(response, await tvServices.profiles(device));
        if (path === "/api/tv/catalog" && method === "GET") { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return success(response, await tvServices.catalog(device, profileId)); }
        const progress = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/progress\/(movie|episode):([^/]+)$/);
        if (progress) { const profileId = auth.requireProfile(device, progress[1]), mediaKey = `${progress[2]}:${progress[3]}`; if (method === "GET") return success(response, await tvServices.progress(profileId, mediaKey)); if (method === "PUT") return success(response, await tvServices.saveProgress(profileId, mediaKey, await readBody(request))); }
        const favorite = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/favorites\/(movie|episode):([^/]+)$/);
        if (favorite && ["PUT", "DELETE"].includes(method)) { const profileId = auth.requireProfile(device, favorite[1]), mediaKey = `${favorite[2]}:${favorite[3]}`; return success(response, await tvServices.saveFavorite(profileId, mediaKey, method === "PUT")); }
        const verifyPin = path.match(/^\/api\/tv\/profiles\/([a-z0-9-]+)\/verify-pin$/);
        if (verifyPin && method === "POST") { const profileId = auth.requireProfile(device, verifyPin[1]); return success(response, { valid: await tvServices.verifyPin(profileId, (await readBody(request)).pin) }); }
        const stream = path.match(/^\/api\/tv\/stream\/(movie|episode):([^/]+)$/);
        if (stream && ["GET", "HEAD"].includes(method)) { const profileId = auth.requireProfile(device, url.searchParams.get("profileId")); return tvServices.stream(request, response, `${stream[1]}:${stream[2]}`, profileId); }
        throw new NotFoundError("Recurso da TV não encontrado.");
    }
    const admin = {
        async network(port) { const settings = await settingsStore.load(); return { ...settings, enabled: settings.lanAccessEnabled, host: settings.lanAccessEnabled ? "0.0.0.0" : "127.0.0.1", port, addresses: networkInfo(port), restartRequired: false }; },
        async updateNetwork(input, port, currentHost) { const settings = await settingsStore.save(input); return { ...await this.network(port), restartRequired: (settings.lanAccessEnabled ? "0.0.0.0" : "127.0.0.1") !== currentHost }; },
        pairings: () => pairing.list(),
        approve: async (id, input) => pairing.approve(id, input.allowedProfileIds || []),
        reject: (id) => pairing.reject(id),
        devices: () => deviceStore.list(),
        updateDevice: (id, input) => deviceStore.update(id, input),
        revokeDevice: (id) => deviceStore.revoke(id),
        removeDevice: (id) => deviceStore.remove(id)
    };
    return { handle, admin };
    function success(response, data, status = 200) { send(response, status, { ok: true, data }); }
}
