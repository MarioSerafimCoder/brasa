import os from "node:os";

export function getPrivateNetworkAddresses(port, interfaces = os.networkInterfaces()) {
    const addresses = [];
    for (const [name, entries] of Object.entries(interfaces || {})) {
        for (const entry of entries || []) {
            const ip = String(entry?.address || "");
            if (entry?.internal || !isIpv4(entry) || !isPrivateIpv4(ip)) continue;
            addresses.push({ interface: name, ip, url: `http://${ip}:${port}` });
        }
    }
    return addresses.filter((item, index, all) => all.findIndex((other) => other.ip === item.ip) === index);
}

export function isPrivateIpv4(ip) {
    const parts = String(ip).split(".").map(Number);
    if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) return false;
    return parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168);
}

function isIpv4(entry) { return entry?.family === "IPv4" || entry?.family === 4; }
