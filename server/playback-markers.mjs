import fs from "node:fs/promises";
import path from "node:path";
import { ValidationError } from "./app-errors.mjs";

export function validatePlaybackMarkers(markers, durationMs) {
    if (!Number.isFinite(durationMs) || durationMs <= 0) throw new ValidationError("Analise a mídia antes de editar as marcações.");
    if (!Array.isArray(markers) || markers.length > 2) throw new ValidationError("Informe no máximo uma abertura e um trecho de créditos.");
    const kinds = new Set();
    const result = markers.map(marker => {
        const { kind, startMs, endMs } = marker || {};
        if (!["intro", "credits"].includes(kind) || kinds.has(kind)) throw new ValidationError("Tipo de marcação inválido ou repetido.");
        if (!Number.isSafeInteger(startMs) || !Number.isSafeInteger(endMs) || startMs < 0 || endMs <= startMs || endMs > durationMs)
            throw new ValidationError("O início deve ser menor que o fim, dentro da duração do vídeo.");
        kinds.add(kind);
        return { kind, startMs, endMs, source: "manual" };
    }).sort((a, b) => a.startMs - b.startMs);
    if (result.length === 2 && result[0].endMs > result[1].startMs) throw new ValidationError("Abertura e créditos não podem se sobrepor.");
    return result;
}

export function createPlaybackMarkerStore(rootDir) {
    const file = path.join(rootDir, "data", "playback-markers.json");
    let queue = Promise.resolve();
    async function read() {
        try { return JSON.parse(await fs.readFile(file, "utf8")); }
        catch (error) { if (error.code === "ENOENT") return {}; throw error; }
    }
    async function get(mediaKey, probe = {}) {
        await queue.catch(() => {});
        const record = (await read())[mediaKey];
        const valid = record && sameFingerprint(record.fingerprint, probe.fingerprint);
        return {
            durationMs: Math.round(Number(probe.duration || 0) * 1000),
            mode: valid ? "manual" : "automatic", stale: Boolean(record && !valid),
            markers: valid ? record.markers : probe.chapterMarkers || [],
        };
    }
    function save(mediaKey, markers, probe) {
        if (!/^(movie|episode):[^/\\]{1,200}$/.test(mediaKey)) throw new ValidationError("Mídia inválida.");
        const validated = markers === null ? null : validatePlaybackMarkers(markers, Math.round(Number(probe?.duration || 0) * 1000));
        if (validated && !probe?.fingerprint) throw new ValidationError("Analise o arquivo antes de salvar as marcações.");
        const task = queue.catch(() => {}).then(async () => {
            const state = await read();
            if (validated === null) delete state[mediaKey];
            else state[mediaKey] = { markers: validated, fingerprint: probe.fingerprint, updatedAt: new Date().toISOString() };
            await fs.mkdir(path.dirname(file), { recursive: true });
            const temp = `${file}.${process.pid}.tmp`;
            await fs.writeFile(temp, JSON.stringify(state, null, 2) + "\n");
            await fs.rename(temp, file);
        });
        queue = task;
        return task.then(() => get(mediaKey, probe));
    }
    return { get, save };
}

export function sameFingerprint(a, b) {
    return Boolean(a && b && a.size === b.size && a.mtimeMs === b.mtimeMs);
}

/** Only explicit chapter labels, never scene detection or guessed fixed timings. Milliseconds. */
export function trustedChapterMarkers(chapters = [], durationSeconds = 0) {
    const duration = Number(durationSeconds) * 1000;
    if (!Number.isFinite(duration) || duration <= 0 || !Array.isArray(chapters)) return [];
    const result = [];
    for (const chapter of chapters) {
        const title = String(chapter.tags?.title || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLowerCase();
        const kind = /^(opening|intro|opening credits|abertura|op)$/.test(title) ? "intro"
            : /^(ending|ending credits|end credits|credits|creditos|encerramento|ed)$/.test(title) ? "credits" : "";
        const startMs = Math.round(Number(chapter.start_time) * 1000), endMs = Math.round(Number(chapter.end_time) * 1000);
        if (!kind || !Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs < 0 || endMs > duration + 1000 || endMs - startMs < 5000) continue;
        if (kind === "intro" && (startMs > Math.min(duration * .3, 600_000) || endMs - startMs > 300_000)) continue;
        if (kind === "credits" && (startMs < duration * .7 || endMs - startMs > 900_000)) continue;
        if (result.some(marker => marker.kind === kind || startMs < marker.endMs && endMs > marker.startMs)) continue;
        result.push({ kind, startMs, endMs: Math.min(endMs, duration), source: "embedded-chapter" });
    }
    return result.sort((a, b) => a.startMs - b.startMs);
}
