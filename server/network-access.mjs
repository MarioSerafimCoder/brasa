import { isPrivateIpv4 } from "./network-interfaces.mjs";

export function normalizeRemoteAddress(value) { return String(value || "").replace(/^::ffff:/, ""); }
export function isLoopbackAddress(value) { return ["127.0.0.1", "::1"].includes(normalizeRemoteAddress(value)); }
export function isPrivateClientAddress(value) { const address = normalizeRemoteAddress(value); return isLoopbackAddress(address) || isPrivateIpv4(address); }
export function canAccessLegacyApi({ lanAccessEnabled, remoteAddress, pathname }) { return !lanAccessEnabled || isLoopbackAddress(remoteAddress) || !String(pathname || "").startsWith("/api/"); }
