import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { createDeviceStore } from "../server/device-store.mjs";

const mediaKey = process.argv[2] || "movie:437";
const profileName = process.argv[3] || "Mario";
const requestedPositionMs = Number.isFinite(Number(process.argv[4])) ? Math.max(0, Math.round(Number(process.argv[4]))) : null;
const forceHls = process.argv.includes("--hls");
const baseUrl = process.env.BRASA_URL || "http://127.0.0.1:4173";
const store = createDeviceStore(process.cwd());
const created = await store.create({ name: "Diagnóstico temporário de reprodução", type: "tv" });
const headers = {
    "x-brasa-device-token": created.token,
    "x-brasa-playback-containers": "matroska,mp4,hls",
    "x-brasa-video-codecs": "h264,hevc",
    "x-brasa-audio-codecs": "aac,ac3,eac3",
    "x-brasa-hdr-types": "hdr10",
    "x-brasa-max-video-width": "3840",
    "x-brasa-max-video-height": "2160",
    "x-brasa-video-capabilities": JSON.stringify([
        { codec: "h264", maxWidth: 3840, maxHeight: 2160, maxBitrate: 80_000_000, hardware: true, profiles: [] },
        { codec: "hevc", maxWidth: 3840, maxHeight: 2160, maxBitrate: 80_000_000, hardware: true, profiles: ["main10"] },
    ]),
};

try {
    const profilesResponse = await fetch(`${baseUrl}/api/tv/profiles`, { headers });
    const profilesPayload = await profilesResponse.json();
    const profile = profilesPayload.data?.find((item) => item.name === profileName) || profilesPayload.data?.[0];
    if (!profile) throw new Error("Nenhum perfil disponível para o teste.");

    let playback;
    for (let attempt = 0; attempt < 90; attempt++) {
        const position = (requestedPositionMs == null ? "" : `&positionMs=${requestedPositionMs}`) + (forceHls ? "&fallback=transcode" : "");
        const response = await fetch(`${baseUrl}/api/v1/tv/playback/${mediaKey}?profileId=${encodeURIComponent(profile.id)}${position}`, { headers });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error?.message || "Falha ao preparar a reprodução.");
        playback = payload.data;
        if (["ready", "failed"].includes(playback.preparationStatus)) break;
        await new Promise((resolve) => setTimeout(resolve, 1_000));
    }
    if (playback?.preparationStatus !== "ready") throw new Error(playback?.errorMessage || "A preparação não ficou pronta.");
    if (process.argv.includes("--diagnostics")) {
        const historyUrl = `${baseUrl}/api/v1/tv/playback-history?profileId=${encodeURIComponent(profile.id)}`;
        if ((await fetch(historyUrl)).status !== 401) throw new Error("Histórico deve exigir autenticação.");
        const id = randomUUID();
        const batch = { id, mediaKey: playback.mediaKey, events: [
            { sequence: 1, elapsedMs: 0, kind: "start", mode: playback.playbackMode },
            { sequence: 2, elapsedMs: 1, kind: "sample", mode: playback.playbackMode, hlsSessionId: playback.hlsSessionId || "" },
            { sequence: 3, elapsedMs: 2, kind: "end", mode: playback.playbackMode },
        ] };
        const posted = await fetch(historyUrl, { method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify(batch) });
        if (!posted.ok) throw new Error(`Diagnóstico recusado: ${posted.status} ${await posted.text()}`);
        const list = await (await fetch(historyUrl, { headers })).json();
        if (!list.data?.some(entry => entry.id === id)) throw new Error("Histórico não retornou a sessão gravada.");
        const detail = await (await fetch(`${baseUrl}/api/v1/tv/playback-history/${id}?profileId=${encodeURIComponent(profile.id)}`, { headers })).json();
        if (detail.data?.events?.length !== 3 || !detail.data.ended || JSON.stringify(detail).includes(created.token)) throw new Error("Detalhes do diagnóstico inválidos.");
        if (playback.hlsSessionId && !detail.data.events[1].server?.encoder) throw new Error("Conversão não correlacionada com o histórico.");
        console.log("Histórico HTTP: gravação, consulta, detalhes, autenticação e contexto do encoder aprovados (dispositivo temporário).");
    }
    if (playback.playbackMode === "direct") {
        if (forceHls) throw new Error("O fallback explícito não retornou HLS.");
        const url = new URL(playback.playbackUrl, `${baseUrl}/`).toString();
        const range = await fetch(url, { headers: { ...headers, Range: "bytes=0-1023" } });
        const body = await range.arrayBuffer();
        if (range.status !== 206 || body.byteLength !== 1024) throw new Error("Range progressivo inválido.");
        await decodeHttp(path.resolve("tools", "ffmpeg", "ffmpeg.exe"), url, created.token, Number(requestedPositionMs || 0) / 1000);
        console.log(JSON.stringify({ mediaKey, playbackMode: playback.playbackMode, requestedPositionMs, resumePosition: playback.resumePosition, rangeVerified: true, decodedThroughServer: true }, null, 2));
    } else {
    if (playback.playbackMode !== "hls") throw new Error(`Modo de reprodução inesperado: ${playback.playbackMode}.`);

    const manifestUrl = new URL(playback.playbackUrl, `${baseUrl}/`).toString();
    for (let attempt = 0; attempt < 8; attempt++) {
        const response = await fetch(manifestUrl, { headers });
        const body = Buffer.from(await response.arrayBuffer());
        if (!response.ok || Number(response.headers.get("content-length")) !== body.length || !body.toString().startsWith("#EXTM3U")) throw new Error("Manifesto HLS truncado ou inválido.");
        for (const relative of body.toString().split(/\r?\n/).filter(line => line && !line.startsWith("#"))) {
            const variant = await fetch(new URL(relative, manifestUrl), { headers });
            const bytes = Buffer.from(await variant.arrayBuffer());
            const text = bytes.toString();
            if (!variant.ok || Number(variant.headers.get("content-length")) !== bytes.length || !text.startsWith("#EXTM3U") || !text.endsWith("\n")) throw new Error("Playlist de segmentos truncada ou inválida.");
        }
    }

    const sessionId = playback.playbackUrl.match(/\/hls\/([a-f0-9]{24})\//)?.[1];
    if (!sessionId) throw new Error("A sessão HLS não foi identificada.");
    const sessionRoot = path.resolve("data", "prepared-media", "hls", sessionId);
    const session = JSON.parse(await fs.readFile(path.join(sessionRoot, "session.json"), "utf8"));
    if (requestedPositionMs != null && Math.abs(Number(session.startPositionMs || 0) - requestedPositionMs) > 6_000) {
        throw new Error(`A sessão iniciou em ${session.startPositionMs}ms, longe do ponto pedido ${requestedPositionMs}ms.`);
    }
    const playlistPath = path.join(sessionRoot, session.ladder[0].id, "index.m3u8");
    const playlist = await fs.readFile(playlistPath, "utf8");
    const durations = [...playlist.matchAll(/#EXTINF:([\d.]+)/g)].map((match) => Number(match[1]));
    const maximumSegmentSeconds = Math.max(...durations);
    if (!durations.length || maximumSegmentSeconds > 6) {
        throw new Error(`Segmentação insegura: maior segmento com ${maximumSegmentSeconds || 0}s.`);
    }
    const verificationPlaylist = path.resolve("data", `.verification-${process.pid}.m3u8`);
    const snapshotLines = [];
    let snapshotSegments = 0;
    for (const line of playlist.split(/\r?\n/)) {
        if (line === "#EXT-X-ENDLIST") continue;
        snapshotLines.push(/\.ts$/.test(line)
            ? path.relative(path.dirname(verificationPlaylist), path.join(path.dirname(playlistPath), line)).replaceAll("\\", "/")
            : line);
        if (/\.ts$/.test(line)) snapshotSegments++;
        if (snapshotSegments >= 10) break;
    }
    snapshotLines.push("#EXT-X-ENDLIST", "");
    await fs.writeFile(verificationPlaylist, snapshotLines.join("\n"), "utf8");
    try {
        await decode(path.resolve("tools", "ffmpeg", "ffmpeg.exe"), verificationPlaylist);
        await decodeHttp(
            path.resolve("tools", "ffmpeg", "ffmpeg.exe"),
            new URL(playback.playbackUrl, `${baseUrl}/`).toString(),
            created.token,
        );
    } finally {
        await fs.rm(verificationPlaylist, { force: true });
    }

    console.log(JSON.stringify({
        mediaKey,
        profile: profile.name,
        preparationStatus: playback.preparationStatus,
        playbackMode: playback.playbackMode,
        requestedPositionMs,
        playbackOffset: playback.playbackOffset,
        pipeline: session.pipeline,
        encoder: session.encoder,
        segmentCount: durations.length,
        maximumSegmentSeconds,
        decodedSegments: snapshotSegments,
        decodedThroughServer: true,
        subtitles: playback.subtitles?.length || 0,
        sessionRoot,
        playlistPath,
    }, null, 2));
    }
} finally {
    await store.remove(created.device.id);
}

function decode(ffmpegPath, playlistPath) {
    return new Promise((resolve, reject) => {
        const child = spawn(ffmpegPath, ["-v", "error", "-xerror", "-i", playlistPath, "-f", "null", "NUL"], {
            windowsHide: true,
            stdio: ["ignore", "ignore", "pipe"],
        });
        let errorText = "";
        child.stderr.on("data", (chunk) => { errorText += chunk.toString(); });
        child.once("error", reject);
        child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(errorText.trim() || `FFmpeg encerrou com código ${code}.`)));
    });
}

function decodeHttp(ffmpegPath, playbackUrl, token, startSeconds = 0) {
    return new Promise((resolve, reject) => {
        const child = spawn(ffmpegPath, [
            "-v", "error", "-xerror",
            "-headers", `X-BRasa-Device-Token: ${token}\r\n`,
            ...(startSeconds > 0 ? ["-ss", String(startSeconds)] : []),
            "-i", playbackUrl,
            "-t", "60",
            "-f", "null", "NUL",
        ], {
            windowsHide: true,
            stdio: ["ignore", "ignore", "pipe"],
        });
        let errorText = "";
        const timeout = setTimeout(() => { child.kill(); reject(new Error("Teste HTTP excedeu três minutos.")); }, 180_000);
        child.once("close", () => clearTimeout(timeout));
        child.stderr.on("data", (chunk) => { errorText += chunk.toString(); });
        child.once("error", reject);
        child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(errorText.trim() || `FFmpeg HTTP encerrou com código ${code}.`)));
    });
}
