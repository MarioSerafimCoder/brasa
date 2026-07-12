import dgram from "node:dgram";
import os from "node:os";

const MULTICAST_ADDRESS = "224.0.0.251";
const MDNS_PORT = 5353;

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
    let timer;
    const announce = (ttl = 120) => { const packet = responsePacket({ serviceType, instance, hostname, port, address, ttl }); socket.send(packet, MDNS_PORT, MULTICAST_ADDRESS, () => {}); };
    return {
        start: () => new Promise((resolve, reject) => {
            socket.once("error", reject);
            socket.bind(MDNS_PORT, "0.0.0.0", () => {
                try { socket.addMembership(MULTICAST_ADDRESS); socket.setMulticastTTL(255); }
                catch (error) { reject(error); return; }
                socket.removeListener("error", reject);socket.on("error", (error) => console.warn(`BRasa mDNS: ${error.message}`));
                socket.on("message", (message) => { if (message.includes(Buffer.from("_brasa"))) announce(); });
                announce();timer = setInterval(announce, 60_000);timer.unref?.();resolve();
            });
        }),
        stop: async () => { clearInterval(timer);announce(0);await new Promise((resolve) => setTimeout(resolve, 25));socket.close(); }
    };
}

function privateIpv4() { for (const entries of Object.values(os.networkInterfaces())) for (const item of entries || []) if (!item.internal && (item.family === "IPv4" || item.family === 4) && isPrivate(item.address)) return item.address; return "127.0.0.1"; }
function isPrivate(value) { const parts = String(value).split(".").map(Number); return parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168); }
function name(value) { return Buffer.concat(String(value).split(".").map((label) => { const bytes = Buffer.from(label); return Buffer.concat([Buffer.from([bytes.length]), bytes]); }).concat(Buffer.from([0]))); }
function record(owner, type, klass, ttl, data) { const header = Buffer.alloc(10);header.writeUInt16BE(type, 0);header.writeUInt16BE(klass, 2);header.writeUInt32BE(ttl, 4);header.writeUInt16BE(data.length, 8);return Buffer.concat([name(owner), header, data]); }
function responsePacket({ serviceType, instance, hostname, port, address, ttl }) { const header = Buffer.alloc(12);header.writeUInt16BE(0x8400, 2);header.writeUInt16BE(4, 6);const ptr = record(serviceType, 12, 1, ttl, name(instance));const srvData = Buffer.alloc(6);srvData.writeUInt16BE(port, 4);const srv = record(instance, 33, 0x8001, ttl, Buffer.concat([srvData, name(hostname)]));const values = ["api=1", "pairing=required"].map((value) => Buffer.concat([Buffer.from([Buffer.byteLength(value)]), Buffer.from(value)]));const txt = record(instance, 16, 0x8001, ttl, Buffer.concat(values));const ip = Buffer.from(address.split(".").map(Number));const a = record(hostname, 1, 0x8001, ttl, ip);return Buffer.concat([header, ptr, srv, txt, a]); }
