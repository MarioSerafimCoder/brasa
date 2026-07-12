import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createNetworkConfigStore, DEFAULT_NETWORK_SETTINGS, hostForNetworkSettings, validateNetworkSettings } from "../server/network-config.mjs";
import { getPrivateNetworkAddresses, isPrivateIpv4 } from "../server/network-interfaces.mjs";
import { createDeviceStore } from "../server/device-store.mjs";
import { createPairingService } from "../server/pairing-service.mjs";
import { createDeviceAuth } from "../server/device-auth.mjs";
import { canAccessLegacyApi, isPrivateClientAddress, isLoopbackAddress } from "../server/network-access.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-tv-network-"));
let scenarios = 0, now = Date.parse("2026-07-12T12:00:00Z");
const ok = () => scenarios++;
try {
    const network = createNetworkConfigStore(root);
    assert.deepEqual(await network.load(), DEFAULT_NETWORK_SETTINGS); ok();
    assert.equal(hostForNetworkSettings(await network.load()), "127.0.0.1"); ok();
    const enabled = await network.save({ ...DEFAULT_NETWORK_SETTINGS, lanAccessEnabled: true, serverName: "Sala" });
    assert.equal(hostForNetworkSettings(enabled), "0.0.0.0"); ok();
    await network.save({ ...enabled, serverName: "Casa" }); assert.equal(JSON.parse(await fs.readFile(network.backup, "utf8")).serverName, "Sala"); ok();
    await fs.writeFile(network.file, "{quebrado"); assert.equal((await network.load()).serverName, "Sala"); ok();
    assert.throws(() => validateNetworkSettings({ lanAccessEnabled: "sim" })); ok();

    const addresses = getPrivateNetworkAddresses(4173, { Loopback: [{ address: "127.0.0.1", family: "IPv4", internal: true }], WiFi: [{ address: "192.168.1.20", family: "IPv4", internal: false }], Ethernet: [{ address: "10.0.0.4", family: 4, internal: false }], Publica: [{ address: "8.8.8.8", family: "IPv4", internal: false }], IPv6: [{ address: "fe80::1", family: "IPv6", internal: false }] });
    assert.deepEqual(addresses.map((item) => item.ip), ["192.168.1.20", "10.0.0.4"]); ok();
    assert.equal(isPrivateIpv4("172.31.4.2"), true); assert.equal(isPrivateIpv4("172.32.4.2"), false); ok();
    assert.equal(isPrivateClientAddress("::ffff:192.168.1.25"), true); assert.equal(isPrivateClientAddress("8.8.8.8"), false); assert.equal(isLoopbackAddress("::ffff:127.0.0.1"), true); ok();
    assert.equal(canAccessLegacyApi({ lanAccessEnabled: true, remoteAddress: "192.168.1.2", pathname: "/api/profiles" }), false); assert.equal(canAccessLegacyApi({ lanAccessEnabled: true, remoteAddress: "127.0.0.1", pathname: "/api/profiles" }), true); ok();

    const devices = createDeviceStore(root, { now: () => now });
    const created = await devices.create({ name: "TV Sala", type: "tv", maxDevices: 2, ip: "192.168.1.30" });
    assert.ok(created.token); assert.equal(JSON.stringify(await devices.load()).includes(created.token), false); ok();
    assert.equal((await devices.authenticate(created.token, "192.168.1.31")).name, "TV Sala"); assert.equal(await devices.authenticate("incorreto"), null); ok();
    const auth = createDeviceAuth(devices); const authorized = await auth.requireDevice({ headers: { "x-brasa-device-token": created.token }, socket: { remoteAddress: "192.168.1.31" } }); assert.equal(authorized.id, created.device.id); await assert.rejects(() => auth.requireDevice({ headers: {}, socket: {} })); ok();
    assert.equal((await devices.update(created.device.id, { name: "TV Quarto", allowedProfileIds: ["laura"] })).allowedProfileIds[0], "laura"); ok();
    await devices.revoke(created.device.id); assert.equal(await devices.authenticate(created.token), null); ok();
    const second = await devices.create({ name: "TV 2", maxDevices: 2 }); await devices.remove(second.device.id); assert.equal((await devices.list()).some((item) => item.id === second.device.id), false); ok();
    await devices.save({ version: 1, devices: [] }); await devices.create({ name: "A", maxDevices: 1 }); await assert.rejects(() => devices.create({ name: "B", maxDevices: 1 })); ok();
    await fs.writeFile(devices.file, "inválido"); assert.ok(Array.isArray((await devices.load()).devices)); ok();

    await devices.save({ version: 1, devices: [] });
    const settings = { ...DEFAULT_NETWORK_SETTINGS, lanAccessEnabled: true };
    const pairing = createPairingService({ deviceStore: devices, getSettings: async () => settings, now: () => now });
    const request = await pairing.start({ name: "Samsung", type: "tv", ip: "192.168.1.40" }); assert.match(request.code, /^[A-Z2-9]{6}$/); ok();
    assert.equal((await pairing.approve(request.requestId, ["laura"])).status, "approved"); const delivery = pairing.status(request.requestId); assert.ok(delivery.token); assert.equal(pairing.status(request.requestId).token, undefined); ok();
    const rejected = await pairing.start({ name: "Outra", ip: "192.168.1.41" }); assert.equal(pairing.reject(rejected.requestId).status, "rejected"); ok();
    const expired = await pairing.start({ name: "Antiga", ip: "192.168.1.42" }); now += 6 * 60_000; assert.equal(pairing.status(expired.requestId).status, "expired"); ok();
    for (let index = 0; index < 5; index++) await pairing.start({ name: `TV ${index}`, ip: "192.168.1.50" }); await assert.rejects(() => pairing.start({ name: "Excesso", ip: "192.168.1.50" })); ok();
    console.log(`Rede e dispositivos: ${scenarios} cenários aprovados.`);
} finally { await fs.rm(root, { recursive: true, force: true }); }
