const GB = 1024 ** 3;
const MBPS = 1_000_000;

export const HLS_LIMITS = Object.freeze({
    preferredSize: 12 * GB,
    requiredSize: 20 * GB,
    highBitrate: 20 * MBPS,
    segmentSeconds: 4,
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
