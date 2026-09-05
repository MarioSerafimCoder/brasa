import crypto from "node:crypto";
import { NotFoundError, ValidationError } from "./app-errors.mjs";

export const NETWORK_TEST_PROFILES = Object.freeze({
    "1080p": 12,
    "4k-balanced": 25,
    "4k-high": 40
});

export function validateNetworkTest(input = {}) {
    const profile = String(input.profile || "1080p");
    if (!Object.hasOwn(NETWORK_TEST_PROFILES, profile)) throw new ValidationError("Perfil de teste inválido.");
    const durationSeconds = input.durationSeconds === undefined ? 60 : Number(input.durationSeconds);
    if (!Number.isInteger(durationSeconds) || durationSeconds < 5 || durationSeconds > 60) throw new ValidationError("A duração deve ficar entre 5 e 60 segundos.");
    return { profile, bitrateMbps: NETWORK_TEST_PROFILES[profile], durationSeconds };
}

export function classifyNetworkResult({ profile, averageMbps = 0, latencyMs = 0, failures = 0, samples = 0 }) {
    const required = NETWORK_TEST_PROFILES[profile] || 12;
    const lossPercent = samples > 0 ? failures / samples * 100 : failures > 0 ? 100 : 0;
    const ratio = averageMbps / required;
    if (lossPercent > 5 || latencyMs > 120 || ratio < .75) return { level: "unstable", conclusion: "Rede congestionada", recommendedMaxMbps: Math.max(8, Math.floor(averageMbps * .7)) };
    if (profile !== "1080p" && ratio < 1.15) return { level: "limited", conclusion: "Rede adequada somente para 1080p", recommendedMaxMbps: Math.max(10, Math.floor(averageMbps * .75)) };
    const level = latencyMs <= 20 && ratio >= 1.5 && lossPercent === 0 ? "excellent" : "good";
    return { level, conclusion: "Rede adequada", recommendedMaxMbps: Math.max(required, Math.floor(averageMbps * .8)) };
}

export function createNetworkDiagnostics({ now = () => Date.now(), randomId = () => crypto.randomBytes(18).toString("base64url"), setTimer = setInterval, clearTimer = clearInterval } = {}) {
    const sessions = new Map(), deviceSessions = new Map();
    function start(deviceId, input) {
        const config = validateNetworkTest(input);
        const previous = deviceSessions.get(deviceId);
        if (previous) cancel(previous, deviceId);
        const id = randomId(), createdAt = now();
        const session = { id, deviceId, ...config, state: "ready", createdAt, startedAt: 0, finishedAt: 0, bytesSent: 0, interruptions: 0, timer: null, response: null };
        sessions.set(id, session); deviceSessions.set(deviceId, id);
        return publicSession(session, now());
    }
    function status(id, deviceId) { return publicSession(requireSession(id, deviceId), now()); }
    function stream(id, deviceId, request, response) {
        const session = requireSession(id, deviceId);
        if (session.state !== "ready") throw new ValidationError("Este teste já foi iniciado.");
        session.state = "running"; session.startedAt = now(); session.response = response;
        response.writeHead(200, { "Content-Type": "application/octet-stream", "Cache-Control": "no-store", "X-BRasa-Test-Bitrate-Mbps": session.bitrateMbps, "X-Content-Type-Options": "nosniff" });
        const bytesPerTick = Math.floor(session.bitrateMbps * 1_000_000 / 8 / 20), chunk = Buffer.alloc(Math.min(32 * 1024, bytesPerTick));
        const finish = (state = "completed") => {
            if (session.timer) clearTimer(session.timer); session.timer = null;
            if (session.state === "running") session.state = state;
            session.finishedAt = now(); session.response = null;
            if (!response.writableEnded && !response.destroyed) response.end();
        };
        session.timer = setTimer(() => {
            if (now() - session.startedAt >= session.durationSeconds * 1000) return finish();
            let remaining = bytesPerTick;
            while (remaining > 0 && !response.destroyed) {
                const size = Math.min(remaining, chunk.length), writable = response.write(size === chunk.length ? chunk : chunk.subarray(0, size));
                session.bytesSent += size; remaining -= size;
                if (!writable) { session.interruptions++; break; }
            }
        }, 50);
        request.once?.("aborted", () => finish("cancelled"));
        response.once?.("close", () => { if (session.state === "running") finish("cancelled"); });
        return session;
    }
    function cancel(id, deviceId) {
        const session = requireSession(id, deviceId);
        if (session.timer) clearTimer(session.timer); session.timer = null;
        session.state = "cancelled"; session.finishedAt = now();
        if (session.response && !session.response.destroyed) session.response.end();
        session.response = null;
        return publicSession(session, now());
    }
    function requireSession(id, deviceId) {
        const session = sessions.get(String(id));
        if (!session || session.deviceId !== deviceId) throw new NotFoundError("Teste de rede não encontrado.");
        return session;
    }
    return { start, status, stream, cancel };
}

function publicSession(session, currentTime) {
    const elapsedMs = Math.max(0, (session.finishedAt || currentTime) - (session.startedAt || session.createdAt));
    return { id: session.id, profile: session.profile, bitrateMbps: session.bitrateMbps, durationSeconds: session.durationSeconds, state: session.state, createdAt: new Date(session.createdAt).toISOString(), startedAt: session.startedAt ? new Date(session.startedAt).toISOString() : "", finishedAt: session.finishedAt ? new Date(session.finishedAt).toISOString() : "", bytesSent: session.bytesSent, interruptions: session.interruptions, elapsedMs, streamPath: `/api/v1/network/test/${session.id}/stream` };
}
