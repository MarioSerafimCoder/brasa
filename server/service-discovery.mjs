import dgram from "node:dgram";
import os from "node:os";

const MULTICAST_ADDRESS = "224.0.0.251";
const MDNS_PORT = 5353;
const RESPONSE_COOLDOWN_MS = 1000;

export async function startServiceDiscovery({ enabled, name = "BRasa", port, createPublisher } = {}) {
    if (!enabled) return { active: false, stop: async () => {} };
    try {
        const publisher = createPublisher ? await createPublisher() : createNativePublisher({ name, port });
        await publisher.start({ name, port, type: "_brasa._tcp.local", txt: { api: "1", pairing: "required" } });
        return { active: true, stop: () => publisher.stop() };
    } catch (error) {
        console.warn(`BRasa: descoberta automática indisponível (${error.message}). Use o endereço manual.`);
        return { active: false, stop: async () => {} };
    }
}

function createNativePublisher({ name, port }) {
    const socket = dgram.createSocket({ type: "udp4", reuseAddr: true });
    const safeName = String(name || "BRasa").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^A-Za-z0-9-]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "BRasa";
    const serviceType = "_brasa._tcp.local", instance = `${safeName}.${serviceType}`, hostname = `${safeName}.local`, address = privateIpv4();
    let timer, lastResponseAt = 0;
    const announce = (ttl = 120) => { const packet = responsePacket({ serviceType, instance, hostname, port, address, ttl }); socket.send(packet, MDNS_PORT, MULTICAST_ADDRESS, () => {}); };
    return {
        start: () => new Promise((resolve, reject) => {
            socket.once("error", reject);
            socket.bind(MDNS_PORT, "0.0.0.0", () => {
                try { socket.addMembership(MULTICAST_ADDRESS); socket.setMulticastTTL(255); socket.setMulticastLoopback(false); }
                catch (error) { reject(error); return; }
                socket.removeListener("error", reject);socket.on("error", (error) => console.warn(`BRasa mDNS: ${error.message}`));
                socket.on("message", (message) => {
                    if (!isServiceDiscoveryQuery(message, { serviceType, instance, hostname })) return;
                    const now = Date.now();
                    if (now - lastResponseAt < RESPONSE_COOLDOWN_MS) return;
                    lastResponseAt = now;announce();
                });
                announce();timer = setInterval(announce, 60_000);timer.unref?.();resolve();
            });
        }),
        stop: async () => { clearInterval(timer);announce(0);await new Promise((resolve) => setTimeout(resolve, 25));socket.close(); }
    };
}

export function isServiceDiscoveryQuery(message, names = {}) {
    if (!Buffer.isBuffer(message) || message.length < 12) return false;
    const flags = message.readUInt16BE(2), questionCount = message.readUInt16BE(4);
    if ((flags & 0x8000) !== 0 || questionCount < 1 || questionCount > 20) return false;
    const accepted = new Map([
        [String(names.serviceType || "_brasa._tcp.local").toLowerCase(), new Set([12, 255])],
        [String(names.instance || "").toLowerCase(), new Set([16, 33, 255])],
        [String(names.hostname || "").toLowerCase(), new Set([1, 255])]
    ]);
    let offset = 12;
    try {
        for (let index = 0; index < questionCount; index++) {
            const parsed = readDnsName(message, offset);offset = parsed.next;
            if (offset + 4 > message.length) return false;
            const type = message.readUInt16BE(offset);offset += 4;
            if (accepted.get(parsed.value.toLowerCase())?.has(type)) return true;
        }
    } catch { return false; }
    return false;
}

function readDnsName(message, start) {
    const labels = [];let offset = start, next = -1, jumps = 0;
    while (offset < message.length) {
        const length = message[offset];
        if (length === 0) { offset++;return { value: labels.join("."), next: next < 0 ? offset : next }; }
        if ((length & 0xc0) === 0xc0) {
            if (offset + 1 >= message.length || ++jumps > 10) throw new Error("Nome DNS invÃ¡lido.");
            if (next < 0) next = offset + 2;
            offset = ((length & 0x3f) << 8) | message[offset + 1];continue;
        }
        if ((length & 0xc0) !== 0 || length > 63 || offset + 1 + length > message.length) throw new Error("Nome DNS invÃ¡lido.");
        labels.push(message.subarray(offset + 1, offset + 1 + length).toString("utf8"));offset += length + 1;
    }
    throw new Error("Nome DNS incompleto.");
}

function privateIpv4() { for (const entries of Object.values(os.networkInterfaces())) for (const item of entries || []) if (!item.internal && (item.family === "IPv4" || item.family === 4) && isPrivate(item.address)) return item.address; return "127.0.0.1"; }
function isPrivate(value) { const parts = String(value).split(".").map(Number); return parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168); }
function name(value) { return Buffer.concat(String(value).split(".").map((label) => { const bytes = Buffer.from(label); return Buffer.concat([Buffer.from([bytes.length]), bytes]); }).concat(Buffer.from([0]))); }
function record(owner, type, klass, ttl, data) { const header = Buffer.alloc(10);header.writeUInt16BE(type, 0);header.writeUInt16BE(klass, 2);header.writeUInt32BE(ttl, 4);header.writeUInt16BE(data.length, 8);return Buffer.concat([name(owner), header, data]); }
function responsePacket({ serviceType, instance, hostname, port, address, ttl }) { const header = Buffer.alloc(12);header.writeUInt16BE(0x8400, 2);header.writeUInt16BE(4, 6);const ptr = record(serviceType, 12, 1, ttl, name(instance));const srvData = Buffer.alloc(6);srvData.writeUInt16BE(port, 4);const srv = record(instance, 33, 0x8001, ttl, Buffer.concat([srvData, name(hostname)]));const values = ["api=1", "pairing=required"].map((value) => Buffer.concat([Buffer.from([Buffer.byteLength(value)]), Buffer.from(value)]));const txt = record(instance, 16, 0x8001, ttl, Buffer.concat(values));const ip = Buffer.from(address.split(".").map(Number));const a = record(hostname, 1, 0x8001, ttl, ip);return Buffer.concat([header, ptr, srv, txt, a]); }
