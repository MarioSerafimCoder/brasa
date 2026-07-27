import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { createDeviceStore } from "../server/device-store.mjs";

const mediaKey = process.argv[2] || "movie:437";
const profileName = process.argv[3] || "Mario";
const requestedPositionMs = Number.isFinite(Number(process.argv[4])) ? Math.max(0, Math.round(Number(process.argv[4]))) : null;
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
        const position = requestedPositionMs == null ? "" : `&positionMs=${requestedPositionMs}`;
        const response = await fetch(`${baseUrl}/api/v1/tv/playback/${mediaKey}?profileId=${encodeURIComponent(profile.id)}${position}`, { headers });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error?.message || "Falha ao preparar a reprodução.");
        playback = payload.data;
        if (["ready", "failed"].includes(playback.preparationStatus)) break;
        await new Promise((resolve) => setTimeout(resolve, 1_000));
    }
    if (playback?.preparationStatus !== "ready") throw new Error(playback?.errorMessage || "A preparação não ficou pronta.");
    if (playback.playbackMode !== "hls") throw new Error(`O teste esperava HLS, mas recebeu ${playback.playbackMode}.`);

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
} finally {
    await store.remove(created.device.id);
}

function decode(ffmpegPath, playlistPath) {
    return new Promise((resolve, reject) => {
        const child = spawn(ffmpegPath, ["-v", "error", "-i", playlistPath, "-f", "null", "NUL"], {
            windowsHide: true,
            stdio: ["ignore", "ignore", "pipe"],
        });
        let errorText = "";
        child.stderr.on("data", (chunk) => { errorText += chunk.toString(); });
        child.once("error", reject);
        child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(errorText.trim() || `FFmpeg encerrou com código ${code}.`)));
    });
}

function decodeHttp(ffmpegPath, playbackUrl, token) {
    return new Promise((resolve, reject) => {
        const child = spawn(ffmpegPath, [
            "-v", "error",
            "-headers", `X-BRasa-Device-Token: ${token}\r\n`,
            "-i", playbackUrl,
            "-t", "60",
            "-f", "null", "NUL",
        ], {
            windowsHide: true,
            stdio: ["ignore", "ignore", "pipe"],
        });
        let errorText = "";
        child.stderr.on("data", (chunk) => { errorText += chunk.toString(); });
        child.once("error", reject);
        child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(errorText.trim() || `FFmpeg HTTP encerrou com código ${code}.`)));
    });
}
