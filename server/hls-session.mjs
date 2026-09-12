import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { createQualityLadder, createAdaptiveLadder, encoderFor, estimateHlsCacheBytes, HLS_LIMITS } from "./transcoding-profiles.mjs";

export function createHlsSessionManager({ rootDir, store, getTools, spawnProcess = spawn }) {
    const root = path.join(rootDir, "data", "prepared-media", "hls");
    const active = new Map();
    const currentByMedia = new Map();
    const pendingByMedia = new Map();
    const lastAccess = new Map();

    function ensure(mediaKey, input, probe, settings = {}, plan = {}) {
        // Serialize setup: simultaneous polls must not launch encoders that
        // recreate the same output directory underneath one another.
        const previous = pendingByMedia.get(mediaKey) || Promise.resolve();
        const pending = previous.catch(() => {}).then(() => ensureSession(mediaKey, input, probe, settings, plan));
        pendingByMedia.set(mediaKey, pending);
        const cleanup = () => { if (pendingByMedia.get(mediaKey) === pending) pendingByMedia.delete(mediaKey); };
        pending.then(cleanup, cleanup);
        return pending;
    }

    async function ensureSession(mediaKey, input, probe, settings = {}, plan = {}) {
        const mode = plan.mode === "remux" ? "remux" : "transcode";
        const segmentSeconds = clamp(settings.hlsSegmentSeconds, 1, 6, HLS_LIMITS.segmentSeconds);
        const tools = await getTools();
        const adaptiveLadder = createAdaptiveLadder(probe, settings, tools.hardwareAcceleration || {}, plan.capabilities);
        const profileKey = `v5-abr:${mode}:${plan.audioAction || "aac"}:${plan.stripDolbyVision ? "hdr10" : "native"}:${segmentSeconds}:${adaptiveLadder.map(q => `${q.width}x${q.height}`).join(",")}`;
        const startPositionMs = normalizeStartPosition(plan.startPositionMs, probe?.duration, segmentSeconds);
        const id = hlsSessionId(mediaKey, probe?.fingerprint, profileKey, startPositionMs);
        currentByMedia.set(mediaKey, id);
        cancelOtherSessions(id, mediaKey);
        const directory = path.join(root, id);
        let existing = active.get(id);
        if (existing?.cancelled) { await existing.completion; existing = active.get(id); }
        if (existing) return await hasStartBuffer(existing.directory, existing.ladder, existing.startSegments) ? markPlayable(existing) : publicState(existing);
        const saved = await readState(directory);
        if (saved.state === "ready") {
            const ladder = saved.ladder || createQualityLadder(probe);
            const expectedDurationSeconds = Number(saved.expectedDurationSeconds || Math.max(0, Number(probe?.duration || 0) - Number(saved.startPositionMs || startPositionMs) / 1000));
            const output = await inspectHlsOutput(directory, ladder, { complete: true, expectedDurationSeconds });
            if (output.valid) {
                return publicState({ ...saved, id, mediaKey, directory, state: "ready", progress: 100, ladder: saved.ladder || createQualityLadder(probe), startPositionMs: Number(saved.startPositionMs || 0), error: "", stderr: "" });
            }
            console.warn(`BRasa HLS ${mediaKey}: cache incompleto descartado (${output.reason}).`);
        }
        if (!tools.ffmpegAvailable) return { id, mediaKey, state: "failed", progress: 0, qualities: [], errorType: "processing", error: "FFmpeg não está disponível para preparar este vídeo." };
        const ladder = mode === "remux"
            ? [{ id: "original", width: Number(probe?.video?.width || 0), height: Number(probe?.video?.height || 0), bitrate: Number(probe?.bitrate || 0), audioBitrate: 192_000 }]
            : adaptiveLadder;
        const minimumStartBufferSeconds = mode === "remux" ? 8 : 12;
        const startBufferSeconds = clamp(Math.max(Number(settings.hlsStartBufferSeconds || 0), minimumStartBufferSeconds), segmentSeconds, 30, minimumStartBufferSeconds);
        const session = {
            id, mediaKey, directory, mode, ladder, capabilities: plan.capabilities,
            audioAction: plan.audioAction || "aac",
            stripDolbyVision: plan.stripDolbyVision === true,
            segmentSeconds,
            startPositionMs,
            expectedDurationSeconds: Math.max(0, Number(probe?.duration || 0) - startPositionMs / 1000),
            startSegments: Math.max(1, Math.ceil(startBufferSeconds / segmentSeconds)),
            state: "preparing", progress: 0, error: "", errorType: "", stderr: "", fallbackReason: "", child: null,
            startedAt: new Date().toISOString(), firstFrameAt: "", firstPlayableAt: "",
        };
        active.set(id, session);
        session.completion = run(session, input, probe, settings, tools).catch((error) => console.error(`BRasa HLS ${mediaKey}:`, error.message)).finally(() => {
            if (active.get(id) === session) active.delete(id);
        });
        return publicState(session);
    }

    async function run(session, input, probe, settings, tools) {
        await recreateDirectory(session);
        await persist(session);
        const initialEncoder = session.mode === "remux" ? "copy" : encoderFor(settings, tools.hardwareAcceleration || {});
        await updateCurrent(session, { status: "processing", playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, progress: 0, error: "" });
        try {
            if (session.cancelled) throw new Error("cancelled");
            try {
                await runEncoder(session, tools.ffmpegPath, input, probe, initialEncoder);
            } catch (error) {
                if (!session.cancelled && !session.firstPlayableAt && session.mode === "transcode" && settings.cpuFallback !== false && initialEncoder !== "libx264") {
                    session.state = "preparing";
                    session.error = "";
                    session.errorType = "";
                    session.progress = 0;
                    session.fallbackReason = error.message;
                    session.stderr = "";
                    session.ladder = createAdaptiveLadder(probe, { acceleration: "cpu" }, {}, session.capabilities);
                    await recreateDirectory(session);
                    await persist(session);
                    await updateCurrent(session, { status: "processing", progress: 0, encoder: "libx264", decoder: "software", pipeline: "fallback por CPU", error: "" });
                    if (session.cancelled) throw new Error("cancelled");
                    await runEncoder(session, tools.ffmpegPath, input, probe, "libx264");
                } else throw error;
            }
            const output = await inspectHlsOutput(session.directory, session.ladder, { complete: true, expectedDurationSeconds: session.expectedDurationSeconds });
            if (!output.valid) throw new Error(`Saída HLS incompleta: ${output.reason}`);
            session.state = "ready";
            session.progress = 100;
            session.completedAt = new Date().toISOString();
            session.error = "";
            await persist(session);
            await updateCurrent(session, { status: "ready", progress: 100, playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, hlsQualities: session.ladder.map((item) => item.id), completedAt: session.completedAt, error: "" });
        } catch (error) {
            if (session.cancelled) {
                session.state = "cancelled";
                session.progress = 0;
                session.error = "";
                session.errorType = "";
                await persist(session).catch(() => {});
                if (isCurrent(session)) await store.update(session.mediaKey, { status: "pending", progress: 0, error: "" }).catch(() => {});
                return;
            }
            session.state = "failed";
            session.errorType = "processing";
            session.error = "O servidor não conseguiu preparar o streaming adaptativo.";
            session.stderr = error.message || session.stderr;
            await persist(session).catch(() => {});
            await updateCurrent(session, { status: "failed", error: session.error, ffmpegStderr: session.stderr.slice(-1200), encoder: session.encoder || initialEncoder }).catch(() => {});
            throw error;
        }
    }

    function runEncoder(session, command, input, probe, encoder) {
        return new Promise((resolve, reject) => {
            const args = session.mode === "remux"
                ? buildRemuxHlsArgs(input, session.directory, probe, { audioAction: session.audioAction, stripDolbyVision: session.stripDolbyVision, segmentSeconds: session.segmentSeconds, startPositionSeconds: session.startPositionMs / 1000 })
                : buildHlsArgs(input, session.directory, session.ladder, encoder, probe, { segmentSeconds: session.segmentSeconds, startPositionSeconds: session.startPositionMs / 1000 });
            const pipeline = session.mode === "remux"
                ? `video copy + audio ${session.audioAction}`
                : encoder === "h264_nvenc"
                    ? (probe?.video?.hdr ? "NVDEC/CUDA scale + CPU HDR tone map + NVENC" : "NVDEC/CUDA + NVENC")
                    : `software decode + ${encoder}`;
            session.encoder = encoder;
            session.decoder = encoder === "h264_nvenc"
                ? (["hevc", "h265"].includes(String(probe?.video?.codec || "").toLowerCase()) ? "hevc_cuvid" : "cuda")
                : encoder === "copy" ? "copy" : "software";
            session.pipeline = pipeline;
            session.attemptStartedAt = new Date().toISOString();
            persist(session).catch(() => {});
            const child = spawnProcess(command, args, { windowsHide: true, shell: false });
            session.child = child;
            let out = "", stderr = "", lastSave = 0, videoFrames = 0, stoppedForNoVideo = false;
            child.stdout.on("data", (chunk) => {
                out += chunk;
                const lines = out.split(/\r?\n/); out = lines.pop() || "";
                for (const line of lines) {
                    const [name, value] = line.split("=");
                    if (name === "frame") {
                        videoFrames = Math.max(videoFrames, Number(value) || 0);
                        if (videoFrames && !session.firstFrameAt) session.firstFrameAt = new Date().toISOString();
                        continue;
                    }
                    if (name === "speed") { session.encodingSpeed = Math.max(0, Number(String(value).replace(/x$/, "")) || 0); continue; }
                    if (name !== "out_time_ms") continue;
                    const outputSeconds = Number(value) / 1e6;
                    session.outputSeconds = Math.max(0, outputSeconds || 0);
                    if (session.mode !== "remux" && !videoFrames && outputSeconds >= 20 && !stoppedForNoVideo) {
                        stoppedForNoVideo = true;
                        stderr += "\nNenhum quadro de vídeo foi decodificado nos primeiros 20 segundos.";
                        child.kill("SIGKILL");
                        continue;
                    }
                    if (session.mode !== "remux" && !videoFrames) continue;
                    const remainingDuration = Math.max(1, Number(probe.duration || 1) - session.startPositionMs / 1000);
                    session.progress = Math.min(99, Math.max(0, outputSeconds / remainingDuration * 100));
                    if (Date.now() - lastSave > 1500) {
                        lastSave = Date.now();
                        persist(session).catch(() => {});
                        updateCurrent(session, { status: "processing", playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, progress: Number(session.progress.toFixed(1)), encoder, decoder: session.decoder, pipeline, ffmpegStderr: stderr.slice(-1200) }).catch(() => {});
                    }
                }
            });
            child.stderr.on("data", (chunk) => { stderr = (stderr + chunk).slice(-6000); session.stderr = stderr; });
            child.on("error", reject);
            child.on("close", (code) => {
                session.child = null;
                if (code === 0) return resolve();
                reject(new Error(stderr.slice(-1200) || `FFmpeg terminou com código ${code}.`));
            });
        });
    }

    async function status(id) {
        const current = active.get(id);
        if (current) return await hasStartBuffer(current.directory, current.ladder, current.startSegments) ? markPlayable(current) : publicState(current);
        const directory = safeDirectory(id);
        const saved = await readState(directory);
        if (!saved.id) return null;
        if (saved.state === "ready") {
            const output = await inspectHlsOutput(directory, saved.ladder || [], { complete: true, expectedDurationSeconds: saved.expectedDurationSeconds });
            if (!output.valid) return publicState({ ...saved, directory, state: "failed", progress: 0, errorType: "playlist", error: "A lista de reprodução ficou incompleta e será refeita." });
        }
        return publicState({ ...saved, directory });
    }

    async function resolve(id, requested) {
        const directory = safeDirectory(id);
        const relative = String(requested || "master.m3u8").replace(/\\/g, "/");
        if (!/^(?:[a-z0-9-]+\/)?(?:master|index|seg-\d+)\.(?:m3u8|ts|m4s)$/i.test(relative)) return null;
        const file = path.resolve(directory, relative);
        if (!file.startsWith(directory + path.sep) && file !== path.join(directory, "master.m3u8")) return null;
        if (path.extname(file).toLowerCase() === ".m3u8") await synchronizeHlsPlaylists(directory);
        const stat = await fs.stat(file).catch(() => null);
        if (!stat?.isFile()) return null;
        lastAccess.set(id, Date.now());
        const current = active.get(id);
        const saved = current ? publicState(current) : await readState(directory);
        return { file, stat, session: saved };
    }

    // Diagnostic uploads must not re-scan thousands of already validated segments.
    async function diagnostics(id) {
        const session = active.get(id) || await readState(safeDirectory(id));
        return session.id ? publicState(session) : null;
    }

    async function remove(id) { const directory = safeDirectory(id);const session=active.get(id);if(session){cancelSession(session);await session.completion;}if(session&&currentByMedia.get(session.mediaKey)===id)currentByMedia.delete(session.mediaKey);lastAccess.delete(id);await fs.rm(directory, { recursive: true, force: true }); }
    function protectedSessionIds() {
        for (const [id, accessedAt] of lastAccess) if (Date.now() - accessedAt > 180_000) lastAccess.delete(id);
        return new Set([...active.keys(), ...lastAccess.keys()]);
    }
    function cancelOtherSessions(keepId, mediaKey) { for (const session of active.values()) if (session.id !== keepId && session.mediaKey === mediaKey) cancelSession(session); }
    function cancelSession(session) { session.cancelled=true;session.child?.kill("SIGKILL"); }
    function isCurrent(session) { return currentByMedia.get(session.mediaKey) === session.id; }
    async function updateCurrent(session, patch) { if (isCurrent(session)) await store.update(session.mediaKey, patch); }
    function safeDirectory(id) { if (!/^[a-f0-9]{24}$/.test(id)) throw new Error("Sessão HLS inválida."); return path.join(root, id); }
    return { ensure, status, diagnostics, resolve, remove, protectedSessionIds, estimate: (probe) => estimateHlsCacheBytes(probe.duration, createQualityLadder(probe)), snapshot:()=>[...active.values()].map((session)=>({id:session.id,mediaKey:session.mediaKey,state:session.state,progress:session.progress})) };
}

export function buildHlsArgs(input, directory, ladder, encoder = "libx264", probe = {}, options = {}) {
    const cuda = encoder === "h264_nvenc";
    const hasAudio = Boolean(probe?.audioTracks?.length);
    const segmentSeconds = clamp(options.segmentSeconds, 1, 6, HLS_LIMITS.segmentSeconds);
    const filters = [`[0:v:0]split=${ladder.length}${ladder.map((_, index) => `[v${index}]`).join("")}`];
    ladder.forEach((quality, index) => {
        const size = `w=${quality.width}:h=${quality.height}:force_original_aspect_ratio=decrease:force_divisible_by=2`;
        if (cuda && probe?.video?.hdr) filters.push(`[v${index}]scale_cuda=${size}:format=p010le,hwdownload,format=p010le,zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p[v${index}o]`);
        else if (cuda) filters.push(`[v${index}]scale_cuda=${size}:format=yuv420p[v${index}o]`);
        else {
            const hdrToSdr = probe?.video?.hdr ? "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv," : "";
            filters.push(`[v${index}]${hdrToSdr}scale=${size}[v${index}o]`);
        }
    });
    const cudaDecoder = cuda && ["hevc", "h265"].includes(String(probe?.video?.codec || "").toLowerCase()) ? ["-c:v", "hevc_cuvid"] : [];
    const seek = inputSeek(options.startPositionSeconds);
    const args = ["-y", ...(cuda ? ["-hwaccel", "cuda", "-hwaccel_output_format", "cuda", ...cudaDecoder] : []), ...seek, "-i", input, "-filter_complex", filters.join(";"), "-map_metadata", "-1"];
    ladder.forEach((quality, index) => {
        args.push("-map", `[v${index}o]`, "-map", "0:a:0?", `-c:v:${index}`, encoder, `-b:v:${index}`, String(quality.bitrate), `-maxrate:v:${index}`, String(quality.maxrate), `-bufsize:v:${index}`, String(quality.buffer));
        const gop = String(Math.max(24, Math.round(Number(probe?.video?.frameRate || 24) * segmentSeconds)));
        args.push(`-g:v:${index}`, gop, `-keyint_min:v:${index}`, gop, `-sc_threshold:v:${index}`, "0");
        args.push(`-force_key_frames:v:${index}`, `expr:gte(t,n_forced*${segmentSeconds})`);
        if (!cuda || probe?.video?.hdr) args.push(`-pix_fmt:v:${index}`, "yuv420p");
        if (hasAudio) args.push(`-c:a:${index}`, "aac", `-b:a:${index}`, String(quality.audioBitrate), `-ac:a:${index}`, "2");
        if (encoder === "libx264") args.push(`-preset:v:${index}`, "superfast", `-profile:v:${index}`, "high");
        if (encoder === "h264_nvenc") args.push(`-preset:v:${index}`, "p1", `-tune:v:${index}`, "ll", `-rc:v:${index}`, "vbr", `-cq:v:${index}`, "25", `-spatial-aq:v:${index}`, "1", `-forced-idr:v:${index}`, "1", `-no-scenecut:v:${index}`, "1", `-strict_gop:v:${index}`, "1");
    });
    const map = ladder.map((quality, index) => `v:${index},${hasAudio ? `a:${index},` : ""}name:${quality.id}`).join(" ");
    return [...args, "-force_key_frames", `expr:gte(t,n_forced*${segmentSeconds})`, "-f", "hls", "-hls_time", String(segmentSeconds), "-hls_list_size", "0", "-hls_playlist_type", "event", "-hls_flags", "independent_segments+temp_file", "-master_pl_name", "master.m3u8", "-var_stream_map", map, "-hls_segment_filename", path.join(directory, "%v", "seg-%06d.ts"), path.join(directory, "%v", "index.m3u8"), "-progress", "pipe:1", "-nostats"];
}

export function buildRemuxHlsArgs(input, directory, probe = {}, options = {}) {
    const segmentSeconds = clamp(options.segmentSeconds, 1, 6, HLS_LIMITS.segmentSeconds);
    const hasAudio = Boolean(probe?.audioTracks?.length);
    const audioAction = options.audioAction === "copy" ? "copy" : "aac";
    const args = ["-y", ...inputSeek(options.startPositionSeconds), "-i", input, "-map", "0:v:0", ...(hasAudio ? ["-map", "0:a:0"] : []), "-c:v", "copy", ...(options.stripDolbyVision ? ["-bsf:v", "dovi_rpu=strip=1"] : [])];
    if (hasAudio) args.push("-c:a", audioAction, ...(audioAction === "aac" ? ["-b:a", "192k", "-ac", "2"] : []));
    const map = hasAudio ? "v:0,a:0,name:original" : "v:0,name:original";
    return [...args, "-map_metadata", "-1", "-f", "hls", "-hls_time", String(segmentSeconds), "-hls_list_size", "0", "-hls_playlist_type", "event", "-hls_flags", "independent_segments+temp_file", "-master_pl_name", "master.m3u8", "-var_stream_map", map, "-hls_segment_filename", path.join(directory, "%v", "seg-%06d.ts"), path.join(directory, "%v", "index.m3u8"), "-progress", "pipe:1", "-nostats"];
}

export function hlsSessionId(mediaKey, fingerprint = {}, profileKey = "transcode:aac", startPositionMs = 0) {
    const start = Math.max(0, Math.round(Number(startPositionMs) || 0));
    return crypto.createHash("sha256").update(`${mediaKey}:${fingerprint.size || 0}:${fingerprint.mtimeMs || 0}:${profileKey}:${start}`).digest("hex").slice(0, 24);
}
async function hasStartBuffer(directory, ladder, minimumSegments = 2) {
    return (await inspectHlsOutput(directory, ladder, { minimumSegments })).valid;
}

export async function synchronizeHlsPlaylists(directory, ladder = []) {
    const files = await hlsPlaylistFiles(directory, ladder);
    for (const file of files) {
        const [publishedText, pendingText] = await Promise.all([
            fs.readFile(file, "utf8").catch(() => ""),
            fs.readFile(`${file}.tmp`, "utf8").catch(() => ""),
        ]);
        const published = parsePlaylist(publishedText);
        const pending = parsePlaylist(pendingText);
        const shouldPromote = pending.valid && (!published.valid || pending.segmentNames.length > published.segmentNames.length || (pending.endList && !published.endList));
        const selected = shouldPromote ? pending : published;
        if (!selected.valid) continue;
        const normalized = selected.text.replace(/\\/g, "/");
        if (normalized !== publishedText) await replacePlaylist(file, normalized);
    }
}

export async function inspectHlsOutput(directory, ladder = [], options = {}) {
    await synchronizeHlsPlaylists(directory, ladder);
    const master = parsePlaylist(await fs.readFile(path.join(directory, "master.m3u8"), "utf8").catch(() => ""));
    if (!master.valid) return { valid: false, reason: "manifesto principal ausente ou inválido" };
    const qualities = ladder.length ? ladder : await discoverLadder(directory);
    if (!qualities.length) return { valid: false, reason: "nenhuma qualidade de vídeo foi criada" };
    let shortestDuration = Number.POSITIVE_INFINITY;
    for (const quality of qualities) {
        const qualityDirectory = path.join(directory, quality.id);
        const playlist = parsePlaylist(await fs.readFile(path.join(qualityDirectory, "index.m3u8"), "utf8").catch(() => ""));
        if (!playlist.valid) return { valid: false, reason: `playlist ${quality.id} ausente ou inválida` };
        if (playlist.segmentNames.length < Math.max(1, Number(options.minimumSegments || 1))) return { valid: false, reason: `buffer ${quality.id} ainda insuficiente` };
        if (options.complete && !playlist.endList) return { valid: false, reason: `playlist ${quality.id} não foi finalizada` };
        for (const segmentName of playlist.segmentNames) {
            if (!/^[a-z0-9._-]+\.(?:ts|m4s)$/i.test(segmentName)) return { valid: false, reason: `segmento inválido em ${quality.id}` };
            const stat = await fs.stat(path.join(qualityDirectory, segmentName)).catch(() => null);
            if (!stat?.isFile() || stat.size <= 0) return { valid: false, reason: `segmento ${segmentName} ausente em ${quality.id}` };
        }
        shortestDuration = Math.min(shortestDuration, playlist.durationSeconds);
    }
    const expected = Math.max(0, Number(options.expectedDurationSeconds || 0));
    const tolerance = Math.max(6, Number(qualities[0]?.segmentSeconds || 0) * 3);
    if (options.complete && expected > 0 && shortestDuration + tolerance < expected) {
        return { valid: false, reason: `duração preparada ${shortestDuration.toFixed(1)}s menor que a esperada ${expected.toFixed(1)}s` };
    }
    return { valid: true, reason: "", durationSeconds: Number.isFinite(shortestDuration) ? shortestDuration : 0 };
}

async function hlsPlaylistFiles(directory, ladder = []) {
    const qualities = ladder.length ? ladder : await discoverLadder(directory);
    return [path.join(directory, "master.m3u8"), ...qualities.map((quality) => path.join(directory, quality.id, "index.m3u8"))];
}

async function discoverLadder(directory) {
    const entries = await fs.readdir(directory, { withFileTypes: true }).catch(() => []);
    return entries.filter((entry) => entry.isDirectory() && /^[a-z0-9-]+$/i.test(entry.name)).map((entry) => ({ id: entry.name }));
}

function parsePlaylist(input) {
    const text = String(input || "").replace(/\r/g, "");
    const lines = text.split("\n").map((line) => line.trim()).filter(Boolean);
    if (lines[0] !== "#EXTM3U") return { valid: false, text, segmentNames: [], durationSeconds: 0, endList: false };
    const segmentNames = [];
    let durationSeconds = 0;
    for (let index = 0; index < lines.length; index++) {
        if (!lines[index].startsWith("#EXTINF:")) continue;
        const duration = Number(lines[index].slice(8).split(",")[0]);
        const segment = lines[index + 1] || "";
        if (!Number.isFinite(duration) || duration <= 0 || !segment || segment.startsWith("#")) return { valid: false, text, segmentNames: [], durationSeconds: 0, endList: false };
        durationSeconds += duration;
        segmentNames.push(path.posix.basename(segment.replace(/\\/g, "/").split("?")[0]));
    }
    return { valid: true, text, segmentNames, durationSeconds, endList: lines.includes("#EXT-X-ENDLIST") };
}

async function replacePlaylist(file, text) {
    const staged = `${file}.${process.pid}.${crypto.randomBytes(4).toString("hex")}.brasa.tmp`;
    await fs.writeFile(staged, text);
    try {
        await fs.rename(staged, file);
    } catch (error) {
        if (!["EEXIST", "EPERM", "EACCES"].includes(error?.code)) throw error;
        await fs.copyFile(staged, file);
        await fs.rm(staged, { force: true });
    }
}
async function readState(directory) { return fs.readFile(path.join(directory, "session.json"), "utf8").then(JSON.parse).catch(() => ({})); }
async function recreateDirectory(session) { await session.persistQueue?.catch(() => {});await fs.rm(session.directory, { recursive: true, force: true });await fs.mkdir(session.directory, { recursive: true });for (const quality of session.ladder) await fs.mkdir(path.join(session.directory, quality.id), { recursive: true }); }
function persist(session) {
    const diagnostics = { encodingSpeed: session.encodingSpeed || 0, outputSeconds: session.outputSeconds || 0 };
    const data = { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress.toFixed(1)), ladder: session.ladder, mode: session.mode, stripDolbyVision: session.stripDolbyVision === true, encoder: session.encoder || "", decoder: session.decoder || "", pipeline: session.pipeline || "", segmentSeconds: session.segmentSeconds, startSegments: session.startSegments, startPositionMs: Number(session.startPositionMs || 0), expectedDurationSeconds: Number(session.expectedDurationSeconds || 0), error: session.error, errorType: session.errorType, stderr: session.stderr?.slice(-1200) || "", fallbackReason: session.fallbackReason?.slice(-1200) || "", startedAt: session.startedAt, attemptStartedAt: session.attemptStartedAt || "", firstFrameAt: session.firstFrameAt || "", firstPlayableAt: session.firstPlayableAt || "", completedAt: session.completedAt || "", estimatedBytes: session.estimatedBytes || 0 };
    session.persistQueue = (session.persistQueue || Promise.resolve()).catch(() => {}).then(async () => {
        const file = path.join(session.directory, "session.json"), temp = `${file}.${process.pid}.tmp`;
        await fs.writeFile(temp, `${JSON.stringify({ ...data, ...diagnostics }, null, 2)}\n`);
        await fs.rename(temp, file);
    });
    return session.persistQueue;
}
function publicState(session) { return { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress || 0), qualities: (session.ladder || []).map((item) => item.id), startPositionMs: Number(session.startPositionMs || 0), encoder: session.encoder || "", encodingSpeed: Number(session.encodingSpeed || 0), outputSeconds: Number(session.outputSeconds || 0), error: session.error || "", errorType: session.errorType || "", technical: session.state === "failed" ? { ffmpegStderr: session.stderr?.slice(-800) || "" } : undefined }; }
function markPlayable(session) { if (!session.firstPlayableAt) { session.firstPlayableAt = new Date().toISOString();persist(session).catch(() => {}); }return publicState({ ...session, state: "ready" }); }
function clamp(value, minimum, maximum, fallback) { const number=Number(value);return Number.isFinite(number)?Math.round(Math.min(maximum,Math.max(minimum,number))):fallback; }
function inputSeek(value) { const seconds=Number(value);return Number.isFinite(seconds)&&seconds>0?["-ss",String(Number(seconds.toFixed(3)))]:[]; }
function normalizeStartPosition(value, durationSeconds, segmentSeconds) { const requested=Math.max(0,Number(value)||0),durationMs=Math.max(0,Number(durationSeconds||0)*1000),segmentMs=Math.max(1000,Number(segmentSeconds||1)*1000),maximum=Math.max(0,durationMs-segmentMs);return Math.floor(Math.min(requested,maximum)/segmentMs)*segmentMs; }
