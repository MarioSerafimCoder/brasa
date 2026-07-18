import { isPrivateIpv4 } from "./network-interfaces.mjs";

export function normalizeRemoteAddress(value) { return String(value || "").replace(/^::ffff:/, ""); }
export function isLoopbackAddress(value) { return ["127.0.0.1", "::1"].includes(normalizeRemoteAddress(value)); }
export function isLocalIpv6(value) { const address = normalizeRemoteAddress(value).toLowerCase().split("%")[0]; return address === "::1" || address.startsWith("fc") || address.startsWith("fd") || /^fe[89ab][0-9a-f]:/.test(address); }
export function isPrivateClientAddress(value) { const address = normalizeRemoteAddress(value); return isLoopbackAddress(address) || isPrivateIpv4(address) || isLocalIpv6(address); }
export function canAccessLegacyApi({ remoteAddress, pathname }) { return isLoopbackAddress(remoteAddress) || !String(pathname || "").startsWith("/api/"); }
