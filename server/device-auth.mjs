import { ForbiddenError, UnauthorizedError } from "./app-errors.mjs";

export function createDeviceAuth(deviceStore) {
    async function requireDevice(request) { const cookies = Object.fromEntries(String(request.headers?.cookie || "").split(";").map((part) => part.trim().split("=")).filter(([key]) => key)); const token = String(request.headers?.["x-brasa-device-token"] || decodeURIComponent(cookies.brasa_device_token || "")); const device = await deviceStore.authenticate(token, request.socket?.remoteAddress || ""); if (!device) throw new UnauthorizedError("Dispositivo não autorizado."); return device; }
    function requireProfile(device, profileId) { const id = String(profileId || ""); if (!id || (device.allowedProfileIds.length && !device.allowedProfileIds.includes(id))) throw new ForbiddenError("Este perfil não está autorizado neste dispositivo."); return id; }
    return { requireDevice, requireProfile };
}
