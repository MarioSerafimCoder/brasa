const GB = 1024 ** 3;
const MBPS = 1_000_000;

export const HLS_LIMITS = Object.freeze({
    preferredSize: 12 * GB,
    requiredSize: 20 * GB,
    highBitrate: 20 * MBPS,
    segmentSeconds: 2,
});

const incompatibleVideo = new Set(["hevc", "h265", "vc1", "mpeg2video"]);
const incompatibleAudio = new Set(["dts", "dca", "truehd", "mlp", "eac3"]);

export function shouldUseAdaptiveHls(probe, capabilities = {}) {
    const video = String(probe?.video?.codec || "").toLowerCase();
    const audio = String(probe?.audioTracks?.[0]?.codec || "").toLowerCase();
    const size = Number(probe?.size || probe?.fingerprint?.size || 0);
    const bitrate = Number(probe?.bitrate || 0);
    const width = Number(probe?.video?.width || 0);
    const height = Number(probe?.video?.height || 0);
    const history = Number(capabilities.directPlayFailures || 0);
    const browserCompatible = capabilities.browserCompatible !== false;
    const reasons = [];
    if (size >= HLS_LIMITS.requiredSize) reasons.push("arquivo acima de 20 GB");
    else if (size >= HLS_LIMITS.preferredSize) reasons.push("arquivo acima de 12 GB");
    if (bitrate > HLS_LIMITS.highBitrate) reasons.push("bitrate acima de 20 Mb/s");
    if (width >= 3840 || height >= 2160) reasons.push("resolução 4K");
    if (incompatibleVideo.has(video)) reasons.push(`vídeo ${video}`);
    if (incompatibleAudio.has(audio)) reasons.push(`áudio ${audio}`);
    if (history > 0) reasons.push("histórico de falha no direct play");
    if (!browserCompatible) reasons.push("cliente incompatível com a fonte original");
    return { useHls: reasons.length > 0, required: size >= HLS_LIMITS.requiredSize, reasons };
}

export function normalizePlaybackCapabilities(input = {}) {
    const tokens = (values, allowed) => [...new Set((Array.isArray(values) ? values : []).map((value) => String(value || "").trim().toLowerCase()).filter((value) => allowed.has(value)))];
    const containers = tokens(input.containers, new Set(["mp4", "matroska", "webm", "mpegts", "hls"]));
    const videoCodecs = tokens(input.videoCodecs, new Set(["h264", "hevc", "hevc-main10", "dolby-vision", "vp8", "vp9", "av1", "mpeg2video"]));
    const audioCodecs = tokens(input.audioCodecs, new Set(["aac", "mp3", "ac3", "eac3", "ac4", "dts", "dts-hd", "truehd", "opus", "vorbis"]));
    const hdrTypes = tokens(input.hdrTypes, new Set(["hdr10", "hdr10-plus", "hlg", "dolby-vision"]));
    return {
        containers, videoCodecs, audioCodecs, hdrTypes,
        maxWidth: bounded(input.maxWidth, 320, 16384),
        maxHeight: bounded(input.maxHeight, 240, 8640),
        reported: containers.length > 0 && videoCodecs.length > 0,
    };
}

export function selectTvPlaybackPlan(probe, rawCapabilities = {}) {
    const capabilities = normalizePlaybackCapabilities(rawCapabilities);
    if (!capabilities.reported) return { mode: "legacy", videoAction: "transcode", audioAction: "transcode", reasons: ["capacidades do cliente não informadas"], capabilities };
    const container = normalizeContainer(probe?.container);
    const video = normalizeVideo(probe?.video?.codec);
    const audio = normalizeAudio(probe?.audioTracks?.[0]?.codec);
    const bitDepth = Number(probe?.video?.bitDepth || 8);
    const hdrType = String(probe?.video?.hdrType || (probe?.video?.hdr ? "hdr10" : "")).toLowerCase();
    const containerSupported = capabilities.containers.includes(container);
    const dimensionsSupported = (!capabilities.maxWidth || Number(probe?.video?.width || 0) <= capabilities.maxWidth) && (!capabilities.maxHeight || Number(probe?.video?.height || 0) <= capabilities.maxHeight);
    const baseVideoSupported = capabilities.videoCodecs.includes(video);
    const main10Supported = video !== "hevc" || bitDepth <= 8 || capabilities.videoCodecs.includes("hevc-main10") || capabilities.videoCodecs.includes("dolby-vision");
    const hdrSupported = !hdrType || capabilities.hdrTypes.includes(hdrType) || (hdrType === "hdr10" && capabilities.hdrTypes.includes("hdr10-plus"));
    const dolbyVisionSupported = !probe?.video?.dolbyVision || capabilities.videoCodecs.includes("dolby-vision") || capabilities.hdrTypes.includes("dolby-vision");
    const videoSupported = baseVideoSupported && main10Supported && hdrSupported && dolbyVisionSupported && dimensionsSupported;
    const audioSupported = !audio || capabilities.audioCodecs.includes(audio);
    const reasons = [];
    if (!containerSupported) reasons.push(`contêiner ${container || "desconhecido"} incompatível`);
    if (!videoSupported) reasons.push(`vídeo ${video || "desconhecido"} incompatível`);
    if (!audioSupported) reasons.push(`áudio ${audio || "desconhecido"} incompatível`);
    if (containerSupported && videoSupported && audioSupported) return { mode: "direct", videoAction: "copy", audioAction: "copy", reasons: ["cliente suporta o arquivo original"], capabilities };
    if (videoSupported) return { mode: "remux", videoAction: "copy", audioAction: audioSupported ? "copy" : "aac", reasons, capabilities };
    return { mode: "transcode", videoAction: "h264", audioAction: "aac", reasons, capabilities };
}

export function createQualityLadder(probe) {
    const sourceHeight = Math.max(1, Number(probe?.video?.height || 720));
    const sourceWidth = Math.max(1, Number(probe?.video?.width || Math.round(sourceHeight * 16 / 9)));
    const candidates = [
        { id: "720p", height: 720, bitrate: 3_200_000, maxrate: 3_800_000, buffer: 6_400_000 },
        { id: "1080p", height: 1080, bitrate: 6_500_000, maxrate: 7_800_000, buffer: 13_000_000 },
        { id: "2160p", height: 2160, bitrate: 16_000_000, maxrate: 19_000_000, buffer: 32_000_000 },
    ];
    const available = candidates.filter((quality) => quality.height <= sourceHeight || (quality.id === "2160p" && sourceWidth >= 3840 && sourceHeight >= 2000));
    if (!available.length) available.push({ id: `${sourceHeight}p`, height: sourceHeight, bitrate: 2_500_000, maxrate: 3_000_000, buffer: 5_000_000 });
    return available.map((quality) => ({
        ...quality,
        width: even(Math.min(sourceWidth, quality.height === sourceHeight ? sourceWidth : Math.round(quality.height * sourceWidth / sourceHeight))),
        height: even(Math.min(sourceHeight, quality.height)),
        audioBitrate: 192_000,
    }));
}

export function createStartupLadder(probe) {
    const ladder = createQualityLadder(probe);
    if (probe?.video?.hdr) return [ladder[0]];
    return [ladder.find((quality) => quality.height >= 1080) || ladder.at(-1)];
}

export function encoderFor(settings = {}, hardware = {}) {
    const mode = settings.acceleration || "auto";
    if ((mode === "auto" || mode === "nvidia") && hardware.nvenc) return "h264_nvenc";
    if ((mode === "auto" || mode === "intel") && hardware.qsv) return "h264_qsv";
    if ((mode === "auto" || mode === "amd") && hardware.amf) return "h264_amf";
    return "libx264";
}

export function estimateHlsCacheBytes(durationSeconds, ladder) {
    const totalBitsPerSecond = ladder.reduce((sum, quality) => sum + quality.bitrate + quality.audioBitrate, 0);
    return Math.ceil(Number(durationSeconds || 0) * totalBitsPerSecond / 8 * 1.04);
}

function even(value) { const rounded = Math.max(2, Math.round(value)); return rounded % 2 ? rounded - 1 : rounded; }
function bounded(value, minimum, maximum) { const number = Number(value || 0);return Number.isFinite(number) && number >= minimum ? Math.min(maximum, Math.round(number)) : 0; }
function normalizeContainer(value) { const text=String(value||"").toLowerCase();if(/matroska|mkv/.test(text))return "matroska";if(/mov|mp4|m4v/.test(text))return "mp4";if(/webm/.test(text))return "webm";if(/mpegts|mpeg-ts/.test(text))return "mpegts";return text.split(",")[0]; }
function normalizeVideo(value) { const text=String(value||"").toLowerCase();if(["h264","avc1"].includes(text))return "h264";if(["hevc","h265"].includes(text))return "hevc";return text; }
function normalizeAudio(value) { const text=String(value||"").toLowerCase();if(["dca","dts"].includes(text))return "dts";if(["mlp","truehd"].includes(text))return "truehd";return text; }
