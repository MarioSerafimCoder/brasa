import fs from "node:fs/promises";
import path from "node:path";
import { ValidationError } from "./app-errors.mjs";

export const DEFAULT_NETWORK_SETTINGS = Object.freeze({ lanAccessEnabled: false, pairingRequired: true, allowNewDevices: true, serverName: "BRasa", maxAuthorizedDevices: 20 });

export function createNetworkConfigStore(rootDir) {
    const file = path.join(rootDir, "data", "network-settings.json"), backup = path.join(rootDir, "data", "network-settings.backup.json");
    let queue = Promise.resolve();
    async function load() {
        const text = await fs.readFile(file, "utf8").catch((error) => error.code === "ENOENT" ? "" : Promise.reject(error));
        if (!text) return { ...DEFAULT_NETWORK_SETTINGS };
        try { return validateNetworkSettings(JSON.parse(text)); }
        catch (error) {
            await fs.rename(file, `${file}.corrupt-${Date.now()}`).catch(() => {});
            const recovered = await fs.readFile(backup, "utf8").then(JSON.parse).then(validateNetworkSettings).catch(() => ({ ...DEFAULT_NETWORK_SETTINGS }));
            await save(recovered, { skipBackup: true });
            return recovered;
        }
    }
    function save(input, { skipBackup = false } = {}) {
        const value = validateNetworkSettings(input);
        queue = queue.then(async () => {
            await fs.mkdir(path.dirname(file), { recursive: true });
            const previous = await fs.readFile(file, "utf8").catch(() => "");
            if (previous && !skipBackup) await fs.writeFile(backup, previous, "utf8");
            const temporary = `${file}.${process.pid}.tmp`;
            await fs.writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
            await fs.rename(temporary, file);
        });
        return queue.then(() => value);
    }
    return { load, save, file, backup };
}

export function validateNetworkSettings(input = {}) {
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new ValidationError("Configuração de rede inválida.");
    const value = { ...DEFAULT_NETWORK_SETTINGS, ...input };
    for (const key of ["lanAccessEnabled", "pairingRequired", "allowNewDevices"]) if (typeof value[key] !== "boolean") throw new ValidationError(`${key} deve ser verdadeiro ou falso.`);
    value.serverName = String(value.serverName || "").trim();
    if (!value.serverName || value.serverName.length > 60) throw new ValidationError("O nome do servidor deve ter entre 1 e 60 caracteres.");
    value.maxAuthorizedDevices = Number(value.maxAuthorizedDevices);
    if (!Number.isInteger(value.maxAuthorizedDevices) || value.maxAuthorizedDevices < 1 || value.maxAuthorizedDevices > 100) throw new ValidationError("O limite de dispositivos deve estar entre 1 e 100.");
    return Object.fromEntries(Object.keys(DEFAULT_NETWORK_SETTINGS).map((key) => [key, value[key]]));
}

export function hostForNetworkSettings(settings) { return settings?.lanAccessEnabled ? "0.0.0.0" : "127.0.0.1"; }
