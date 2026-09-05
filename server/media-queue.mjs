import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { probeMedia } from "./media-probe.mjs";
import { decideMediaStrategy, isProbeStillValid } from "./media-compatibility.mjs";
import { resolvePathInsideLibrary } from "./library-config.mjs";

export function createMediaQueue({ rootDir, store, getTools, resolveMedia, probe = probeMedia, spawnProcess = spawn, decideStrategy = decideMediaStrategy }) {
    const pending = [];
    const active = new Map();
    let paused = false;
    const safeKey = (key) => String(key).replace(/[^a-zA-Z0-9._-]/g, "-");

    async function analyze(key, { prepare = false, priority = 0 } = {}) {
        if (active.has(key) || pending.some((job) => job.key === key)) throw new Error("Este item já está na fila.");
        const media = await resolveMedia(key);
        if (!media) throw new Error("Mídia não encontrada.");
        const original = resolveOriginal(media.originalPath);
        const tools = await getTools();
        if (!tools.ffprobeAvailable) throw new Error("FFprobe não está disponível.");
        await store.update(key, {
            mediaKey: key,
            mediaType: media.mediaType,
            mediaId: String(media.mediaId),
            originalPath: media.originalPath,
            status: "queued",
            strategy: "pending",
            progress: 0,
            error: "",
            createdAt: new Date().toISOString()
        });
        pending.push({ key, original, media, prepare, priority, kind: "analyze" });
        sortPending();
        schedule();
        return store.get(key);
    }

    async function prepare(key, { priority = 10 } = {}) {
        if (active.has(key) || pending.some((job) => job.key === key)) throw new Error("Este item já está na fila.");
        const item = await store.get(key);
        const media = await resolveMedia(key);
        if (!media) throw new Error("Mídia não encontrada.");
        pending.push({ key, original: resolveOriginal(media.originalPath), media, priority, kind: item?.probe ? "prepare" : "analyze", prepare: true });
        sortPending();
        await store.update(key, { status: "queued", progress: 0, error: "" });
        schedule();
        return store.get(key);
    }

    function sortPending() {
        pending.sort((left, right) => Number(right.priority || 0) - Number(left.priority || 0));
    }

    function schedule() {
        if (paused || active.size) return;
        const job = pending.shift();
        if (!job) return;
        run(job).finally(() => {
            active.delete(job.key);
            schedule();
        });
    }

    async function run(job) {
        active.set(job.key, { job, child: null, preempted: false, cancelled: false });
        try {
            let item = await store.get(job.key);
            let probeData = item?.probe;
            const stat = await fs.stat(job.original);
            const reusablePrepared = isProbeStillValid(item, stat) && await preparedExists(item);
            if (!probeData || Number(probeData.schemaVersion || 0) < 3 || !isProbeStillValid(item, stat)) {
                await store.update(job.key, { status: "analyzing", progress: 0 });
                const tools = await getTools();
                probeData = await probe(job.original, tools.ffprobePath);
                const settings = { ...(await store.settings()), hardwareAcceleration: tools.hardwareAcceleration };
                const decision = decideStrategy(probeData, settings);
                item = await store.update(job.key, {
                    probe: probeData,
                    fingerprint: probeData.fingerprint,
                    strategy: decision.strategy,
                    reason: decision.reason,
                    decision,
                    status: reusablePrepared || decision.strategy === "direct-play" ? "ready" : decision.strategy === "corrupted" ? "corrupted" : "pending",
                    progress: reusablePrepared || decision.strategy === "direct-play" ? 100 : 0,
                    lastAnalyzedAt: new Date().toISOString()
                });
                if (item.status === "ready" || !job.prepare) return;
            }
            if (!job.prepare) {
                const decision = item.decision || decideStrategy(probeData, await store.settings());
                await store.update(job.key, {
                    status: decision.strategy === "direct-play" ? "ready" : decision.strategy === "corrupted" ? "corrupted" : "pending",
                    progress: decision.strategy === "direct-play" ? 100 : 0,
                    error: ""
                });
                return;
            }
            await prepareOutput(job.key, job.original, probeData, item.decision || decideStrategy(probeData, await store.settings()));
        } catch (error) {
            const cancelled = error.message === "cancelled";
            const preempted = error.message === "preempted";
            if (preempted) {
                if (job.prepare) {
                    await store.update(job.key, { status: "queued", progress: 0, error: "" });
                    pending.push(job);
                    sortPending();
                } else {
                    await store.update(job.key, { status: "pending", progress: 0, error: "" });
                }
            } else {
                await store.update(job.key, {
                    status: cancelled ? "cancelled" : "failed",
                    error: cancelled ? "Processamento cancelado." : error.message.slice(0, 500)
                });
            }
        }
    }

    async function prepareOutput(key, original, probeData, decision) {
        if (["unsupported", "corrupted"].includes(decision.strategy)) throw new Error("Este arquivo não pode ser preparado automaticamente.");
        const tools = await getTools();
        if (!tools.ffmpegAvailable) throw new Error("FFmpeg não está disponível.");
        const settings = await store.settings();
        await ensureSpace(original, settings);
        const type = key.startsWith("episode:") ? "episodes" : "movies";
        const directory = path.join(rootDir, "data", "prepared-media", type);
        await fs.mkdir(directory, { recursive: true });
        const output = path.join(directory, `${safeKey(key)}.browser.mp4`);
        const partial = `${output}.part`;
        await fs.rm(partial, { force: true });
        const args = buildMediaPreparationArgs(original, partial, decision, settings);
        await store.update(key, { status: "processing", progress: 0, preparedPath: relative(output), startedAt: new Date().toISOString() });
        try {
            await runFfmpeg(key, tools.ffmpegPath, args, probeData.duration);
        } catch (error) {
            await fs.rm(partial, { force: true });
            if (["cancelled", "preempted"].includes(error.message)) throw error;
            if (settings.cpuFallback && ["h264_nvenc", "h264_qsv", "h264_amf"].includes(decision.videoAction)) {
                await store.update(key, { error: "A aceleração por hardware falhou; tentando pela CPU." });
                await runFfmpeg(key, tools.ffmpegPath, buildMediaPreparationArgs(original, partial, { ...decision, videoAction: "libx264" }, settings), probeData.duration);
            } else throw error;
        }
        await fs.rename(partial, output);
        const stat = await fs.stat(output);
        await generateAssets(key, original, probeData, tools.ffmpegPath);
        await store.update(key, {
            status: "ready",
            progress: 100,
            preparedPath: relative(output),
            output: {
                container: "mp4",
                videoCodec: decision.videoAction === "copy" ? probeData.video.codec : "h264",
                audioCodec: decision.audioAction === "copy" ? probeData.audioTracks[0]?.codec || "" : "aac",
                size: stat.size
            },
            completedAt: new Date().toISOString(),
            error: ""
        });
    }

    function runFfmpeg(key, command, args, duration) {
        return new Promise((resolve, reject) => {
            const child = spawnProcess(command, args, { windowsHide: true, shell: false });
            const entry = active.get(key);
            entry.child = child;
            let buffer = "", lastSave = 0, lastError = "";
            child.stdout.on("data", (chunk) => {
                buffer += chunk;
                const lines = buffer.split(/\r?\n/);
                buffer = lines.pop() || "";
                for (const line of lines) {
                    const [name, value] = line.split("=");
                    if (name !== "out_time_ms") continue;
                    const percent = Math.min(99, Math.max(0, Number(value) / 1e6 / duration * 100));
                    if (Date.now() - lastSave > 2000) {
                        lastSave = Date.now();
                        store.update(key, { progress: Number(percent.toFixed(1)) }).catch(() => {});
                    }
                }
            });
            child.stderr.on("data", (chunk) => { lastError = (lastError + chunk).slice(-4000); });
            child.on("error", reject);
            child.on("close", (code) => {
                if (entry.cancelled) return reject(new Error("cancelled"));
                if (entry.preempted) return reject(new Error("preempted"));
                code === 0 ? resolve() : reject(new Error(lastError.slice(-800) || `FFmpeg terminou com código ${code}.`));
            });
        });
    }

    async function generateAssets(key, input, probeData, ffmpeg) {
        const settings = await store.settings();
        const base = safeKey(key);
        const seek = String(Math.max(10, probeData.duration * .2));
        const generated = { subtitles: [] };
        if (settings.generateThumbnails) {
            const kind = key.startsWith("episode:") ? "thumbnails" : "backdrops";
            const directory = path.join(rootDir, "data", "prepared-media", kind);
            const output = path.join(directory, `${base}.jpg`);
            await fs.mkdir(directory, { recursive: true });
            const size = key.startsWith("episode:") ? "640:360" : "1280:720";
            if (await spawnAndWait(ffmpeg, ["-y", "-ss", seek, "-i", input, "-frames:v", "1", "-vf", `scale=${size}:force_original_aspect_ratio=decrease,pad=${size}:(ow-iw)/2:(oh-ih)/2`, output]).then(() => true).catch(() => false)) generated[kind === "thumbnails" ? "thumbnail" : "backdrop"] = relative(output);
        }
        if (settings.extractSubtitles) {
            const tracks = probeData.subtitleTracks.filter((subtitle) => subtitle.extractable).slice(0, 4);
            const directory = path.join(rootDir, "data", "prepared-media", "subtitles");
            await fs.mkdir(directory, { recursive: true });
            for (const track of tracks) {
                const output = path.join(directory, `${base}-${track.language}-${track.index}.vtt`);
                if (await spawnAndWait(ffmpeg, ["-y", "-i", input, "-map", `0:${track.index}`, output]).then(() => true).catch(() => false)) generated.subtitles.push({ label: track.title || track.language, srclang: normalizeLanguage(track.language), src: relative(output), default: track.default, source: "embedded" });
            }
        }
        await store.update(key, generated);
    }

    function cancel(key) {
        const entry = active.get(key);
        if (entry) {
            entry.cancelled = true;
            entry.child?.kill("SIGKILL");
            return true;
        }
        const index = pending.findIndex((job) => job.key === key);
        if (index >= 0) {
            pending.splice(index, 1);
            store.update(key, { status: "cancelled" });
            return true;
        }
        return false;
    }

    function prioritize(key, priority = 100, { prepare: requestedPrepare, preemptOther = false } = {}) {
        const requestedPriority = Number(priority || 0);
        const runningForKey = active.get(key);
        if (runningForKey) {
            runningForKey.job.priority = Math.max(Number(runningForKey.job.priority || 0), requestedPriority);
            if (requestedPrepare === false && runningForKey.job.prepare) {
                runningForKey.job.prepare = false;
                if (runningForKey.child) {
                    runningForKey.preempted = true;
                    runningForKey.child.kill("SIGKILL");
                }
            }
            return true;
        }
        const job = pending.find((candidate) => candidate.key === key);
        if (job) {
            job.priority = Math.max(Number(job.priority || 0), requestedPriority);
            if (requestedPrepare === false) job.prepare = false;
            sortPending();
        }
        const running = [...active.values()][0];
        if (running && running.job.key !== key && (job || preemptOther) && Number(running.job.priority || 0) <= requestedPriority) {
            running.job.prepare = false;
            if (running.child) {
                running.preempted = true;
                running.child.kill("SIGKILL");
            }
        }
        return Boolean(job);
    }

    async function preparedExists(item) {
        if (!item?.preparedPath) return false;
        const file = path.resolve(rootDir, item.preparedPath);
        return insidePrepared(file) && Boolean(await fs.stat(file).then((stat) => stat.isFile() && stat.size > 0).catch(() => false));
    }

    async function removePrepared(key) {
        if (active.has(key)) throw new Error("Cancele o processamento antes de remover.");
        const item = await store.get(key);
        if (item?.preparedPath) {
            const file = path.resolve(rootDir, item.preparedPath);
            if (!insidePrepared(file)) throw new Error("Caminho preparado inválido.");
            await fs.rm(file, { force: true });
        }
        return store.update(key, { preparedPath: "", status: item?.strategy === "direct-play" ? "ready" : "pending", progress: 0, output: null });
    }

    async function restore() {
        const state = await store.all();
        const settings = await store.settings();
        const tools = await getTools();
        paused = Boolean(settings.paused);
        if (!tools.ffprobeAvailable) {
            for (const item of Object.values(state.items || {})) if (/ffprobe/i.test(item.error || "") || ["queued", "analyzing"].includes(item.status)) await store.update(item.mediaKey, { status: "not-analyzed", error: "", progress: 0 });
            return;
        }
        for (const item of Object.values(state.items || {})) {
            if (!["queued", "analyzing", "processing", "finalizing"].includes(item.status)) continue;
            if (!await resolveMedia(item.mediaKey)) {
                await store.update(item.mediaKey, { status: "failed", progress: 0, error: "Mídia não encontrada." });
                continue;
            }
            await analyze(item.mediaKey, { prepare: false, priority: -100 }).catch((error) => console.error(`BRasa mídia ${item.mediaKey} restore ${new Date().toISOString()}:`, error.message));
        }
    }

    function normalizeLanguage(value) { return ({ por: "pt-br", pt: "pt-br", eng: "en", spa: "es" })[String(value).toLowerCase()] || String(value || "und").toLowerCase(); }
    function resolveOriginal(relativePath) { return resolvePathInsideLibrary(rootDir, relativePath).path; }
    function insidePrepared(file) { return file.startsWith(path.join(rootDir, "data", "prepared-media") + path.sep); }
    function relative(file) { return path.relative(rootDir, file).replace(/\\/g, "/"); }
    async function ensureSpace(file, settings) { if (!fs.statfs) return;const stat = await fs.statfs(path.dirname(file));const free = Number(stat.bavail) * Number(stat.bsize);if (free < Number(settings.minimumFreeGb || 5) * 1024 ** 3) throw new Error("Espaço livre insuficiente para preparar a mídia."); }
    function spawnAndWait(command, args) { return new Promise((resolve, reject) => { const child = spawnProcess(command, args, { windowsHide: true, shell: false, stdio: "ignore" });child.on("error", reject);child.on("close", (code) => code === 0 ? resolve() : reject(new Error("Processo auxiliar falhou."))); }); }

    return {
        analyze,
        prepare,
        prioritize,
        cancel,
        retry: (key) => prepare(key, { priority: 20 }),
        removePrepared,
        restore,
        pause: () => { paused = true;store.setSettings({ paused: true }); },
        resume: () => { paused = false;store.setSettings({ paused: false });schedule(); },
        snapshot: () => ({ paused, queued: pending.map((job) => ({ mediaKey: job.key, type: job.kind, priority: job.priority, prepare: job.prepare })), active: [...active.values()].map((entry) => ({ mediaKey: entry.job.key, type: entry.job.kind, priority: entry.job.priority, prepare: entry.job.prepare })) })
    };
}

export function buildMediaPreparationArgs(input, output, decision, settings) {
    const nvenc = decision.videoAction === "h264_nvenc" && decision.strategy === "video-transcode";
    const args = ["-y", ...(nvenc ? ["-hwaccel", "cuda", "-hwaccel_output_format", "cuda"] : []), "-i", input, "-map", "0:v:0", "-map", "0:a:0?", "-map_metadata", "0"];
    if (decision.strategy === "remux") args.push("-c", "copy");
    else if (decision.strategy === "audio-transcode") args.push("-c:v", "copy", "-c:a", "aac", "-b:a", "256k");
    else {
        const quality = { economy: 26, balanced: 22, high: 18 }[settings.quality] || 22;
        if (nvenc) args.push("-vf", "scale_cuda=passthrough=0:format=yuv420p");
        args.push("-c:v", decision.videoAction || "libx264");
        if (decision.videoAction === "libx264") args.push("-preset", "medium", "-crf", String(quality), "-pix_fmt", "yuv420p");
        else if (nvenc) args.push("-preset", "p1", "-tune", "ll", "-rc", "vbr", "-cq", String(Math.max(quality, 24)), "-spatial-aq", "1");
        else args.push("-cq", String(quality), "-pix_fmt", "yuv420p");
        args.push("-c:a", "aac", "-b:a", "256k");
    }
    return [...args, "-movflags", "+faststart", "-progress", "pipe:1", "-nostats", "-f", "mp4", output];
}
