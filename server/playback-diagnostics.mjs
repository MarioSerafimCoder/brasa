import fs from "node:fs/promises";
import path from "node:path";
import { ValidationError, NotFoundError, ForbiddenError } from "./app-errors.mjs";

const kinds = new Set(["start", "source", "preparing", "preparation_error", "first_frame", "sample", "buffer_start", "buffer_end", "pause", "resume", "seek", "error", "quality", "dropped_frames", "retry", "conversion", "ended", "end"]);
const modes = new Set(["direct", "hls", "remux", "transcode", "prepare", ""]);
const reasons = new Set(["", "network", "recovery", "user", "seek", "startup"]);
const DAY = 86_400_000;

export function validatePlaybackBatch(input) {
    if (!input || !/^[a-f0-9-]{36}$/.test(input.id || "") || !/^(movie|episode):[a-zA-Z0-9_-]{1,300}$/.test(input.mediaKey || "") || !Array.isArray(input.events) || !input.events.length || input.events.length > 40) throw new ValidationError("Lote de diagnóstico inválido.");
    const events = input.events.map(event => {
        if (!event || !kinds.has(event.kind)) throw new ValidationError("Evento de reprodução inválido.");
        const clean = { kind: event.kind };
        for (const [key, max] of Object.entries({ sequence: 1_000_000, elapsedMs: 7 * DAY, durationMs: 7 * DAY, positionMs: DAY, bufferMs: DAY, bandwidthBps: 10_000_000_000, bitrate: 1_000_000_000, height: 8640, droppedFrames: 1_000_000, errorCode: 100_000 })) {
            const value = event[key] ?? 0;
            if (!Number.isSafeInteger(value) || value < 0 || value > max) throw new ValidationError(`Valor inválido: ${key}.`);
            clean[key] = value;
        }
        if (!clean.sequence) throw new ValidationError("Sequência inválida.");
        clean.mode = modes.has(event.mode) ? event.mode : "";
        clean.reason = reasons.has(event.reason) ? event.reason : "";
        clean.hlsSessionId = /^[a-f0-9]{24}$/.test(event.hlsSessionId || "") ? event.hlsSessionId : "";
        return clean;
    });
    for (let i = 1; i < events.length; i++) if (events[i].sequence <= events[i - 1].sequence || events[i].elapsedMs < events[i - 1].elapsedMs) throw new ValidationError("Eventos fora de ordem.");
    return { id: input.id, mediaKey: input.mediaKey, events };
}

export function createPlaybackDiagnostics({ rootDir, now = Date.now, maximumSessions = 100, maximumEvents = 600 }) {
    const file = path.join(rootDir, "data", "diagnostics", "playback-history.json");
    let queue = Promise.resolve();
    const owned = (item, deviceId, profileId) => item.deviceId === deviceId && item.profileId === profileId;
    async function read() {
        try { return JSON.parse(await fs.readFile(file, "utf8")).filter(item => item.updatedAt >= now() - 30 * DAY); }
        catch (error) { if (error.code === "ENOENT") return []; throw error; }
    }
    function append(deviceId, profileId, title, input, server = null) {
        const batch = validatePlaybackBatch(input);
        const work = queue.catch(() => {}).then(async () => {
            const history = await read();
            let item = history.find(entry => entry.id === batch.id);
            if (item && (!owned(item, deviceId, profileId) || item.mediaKey !== batch.mediaKey)) throw new ForbiddenError("Histórico indisponível.");
            if (!item) {
                item = { id: batch.id, deviceId, profileId, mediaKey: batch.mediaKey, title: String(title).slice(0, 200), startedAt: now(), updatedAt: now(), lastSequence: 0, elapsedMs: 0, bufferCount: 0, bufferDurationMs: 0, pauseDurationMs: 0, errors: 0, conversions: 0, ended: false, events: [], indications: [] };
                history.push(item);
            }
            for (const event of batch.events.filter(event => event.sequence > item.lastSequence)) {
                if (event.elapsedMs < item.elapsedMs) throw new ValidationError("Tempo de reprodução fora de ordem.");
                item.lastSequence = event.sequence;
                item.elapsedMs = event.elapsedMs;
                if (event.kind === "buffer_start") { item.bufferCount++; item.bufferOpen = true; }
                if (event.kind === "buffer_end") { item.bufferOpen = false; item.bufferDurationMs += event.durationMs; }
                if (event.kind === "resume") item.pauseDurationMs += event.durationMs;
                if (["error", "preparation_error"].includes(event.kind)) item.errors++;
                if (event.kind === "conversion") item.conversions++;
                if (event.kind === "end") item.ended = true;
                const indications = new Set(item.indications);
                if ([2001, 2002].includes(event.errorCode) || (event.kind === "buffer_end" && event.bandwidthBps > 0 && event.bitrate > event.bandwidthBps)) indications.add("Rede: conexão insuficiente ou interrompida.");
                if (event.errorCode >= 3000 && event.errorCode < 4000) indications.add("Arquivo: falha de leitura do formato; verificar a integridade da mídia.");
                if ((event.errorCode >= 4000 && event.errorCode < 5000) || event.droppedFrames >= 30) indications.add("TV: erro de decodificação ou quadros perdidos.");
                const context = server?.id === event.hlsSessionId ? { state: server.state, encoder: server.encoder, encodingSpeed: server.encodingSpeed, outputSeconds: server.outputSeconds } : undefined;
                if (context?.state === "preparing" && context.outputSeconds >= 20 && context.encodingSpeed > 0 && context.encodingSpeed < 1) indications.add("Servidor: conversão abaixo da velocidade de reprodução.");
                if (context?.state === "failed") indications.add("Servidor: falha ao preparar o vídeo.");
                item.indications = [...indications];
                item.events.push({ ...event, ...(context ? { server: context } : {}) });
            }
            item.events = item.events.slice(-maximumEvents);
            item.updatedAt = now();
            history.sort((a, b) => b.updatedAt - a.updatedAt);
            await fs.mkdir(path.dirname(file), { recursive: true });
            await fs.writeFile(`${file}.tmp`, JSON.stringify(history.slice(0, maximumSessions)));
            await fs.rename(`${file}.tmp`, file);
            return { accepted: item.lastSequence };
        });
        queue = work;
        return work;
    }
    async function list(deviceId, profileId) { await queue.catch(() => {}); return (await read()).filter(item => owned(item, deviceId, profileId)).map(({ deviceId, profileId, events, ...item }) => item); }
    async function detail(deviceId, profileId, id) {
        await queue.catch(() => {});
        const item = (await read()).find(item => item.id === id && owned(item, deviceId, profileId));
        if (!item) throw new NotFoundError("Reprodução não encontrada.");
        const { deviceId: _device, profileId: _profile, ...result } = item;
        return result;
    }
    return { append, list, detail };
}
