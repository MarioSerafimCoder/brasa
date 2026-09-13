import crypto from "node:crypto";
import { spawn } from "node:child_process";

export function thumbnailBucket(positionMs, durationMs) {
    const value = Number(positionMs);
    if (!Number.isFinite(value) || value < 0 || !Number.isFinite(durationMs) || durationMs <= 0) return null;
    return Math.floor(Math.min(value, Math.max(0, durationMs - 1000)) / 10_000) * 10_000;
}

/** Small on-demand frames. One worker, bounded memory/time, no background batch conversion. */
export function createTvThumbnails({ busy = () => false, extract = extractFrame } = {}) {
    const cache = new Map();
    let working = false;
    return {
        async get({ file, revision, positionMs, durationMs, ffmpeg, allowed, signal }) {
            const position = thumbnailBucket(positionMs, durationMs);
            if (position === null) return null;
            const key = crypto.createHash("sha256").update(`${file}:${revision}:${position}`).digest("hex");
            if (cache.has(key)) { const bytes = cache.get(key); cache.delete(key); cache.set(key, bytes); return bytes; }
            if (!allowed || working || busy() || signal?.aborted) return null;
            working = true;
            try {
                const bytes = await extract(ffmpeg, file, position, () => busy() || signal?.aborted);
                if (!bytes || bytes.length > 128 * 1024 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null;
                cache.set(key, bytes);
                while (cache.size > 128) cache.delete(cache.keys().next().value);
                return bytes;
            } catch { return null; }
            finally { working = false; }
        },
    };
}

function extractFrame(ffmpeg, file, positionMs, cancelled) {
    return new Promise((resolve, reject) => {
        const child = spawn(ffmpeg, ["-v", "error", "-nostdin", "-threads", "1", "-ss", String(positionMs / 1000), "-i", file,
            "-map", "0:v:0", "-an", "-sn", "-frames:v", "1", "-vf", "scale=256:144:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=256:144:(ow-iw)/2:(oh-ih)/2", "-filter_threads", "1", "-threads", "1", "-c:v", "mjpeg", "-q:v", "6", "-f", "image2pipe", "pipe:1"], { windowsHide: true, shell: false, stdio: ["ignore", "pipe", "ignore"] });
        let bytes = Buffer.alloc(0);
        const timeout = setTimeout(() => child.kill(), 2500);
        const guard = setInterval(() => { if (cancelled()) child.kill(); }, 100);
        const cleanup = () => { clearTimeout(timeout); clearInterval(guard); };
        child.stdout.on("data", chunk => { if (bytes.length + chunk.length > 128 * 1024) child.kill(); else bytes = Buffer.concat([bytes, chunk]); });
        child.on("error", error => { cleanup(); reject(error); });
        child.on("close", code => { cleanup(); resolve(code === 0 && !cancelled() ? bytes : null); });
    });
}
