import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { createQualityLadder, createStartupLadder, encoderFor, estimateHlsCacheBytes, HLS_LIMITS } from "./transcoding-profiles.mjs";

export function createHlsSessionManager({ rootDir, store, getTools }) {
    const root = path.join(rootDir, "data", "prepared-media", "hls");
    const active = new Map();

    async function ensure(mediaKey, input, probe, settings = {}, plan = {}) {
        const mode = plan.mode === "remux" ? "remux" : "transcode";
        const profileKey = `${mode}:${plan.audioAction || "aac"}`;
        const id = sessionId(mediaKey, probe?.fingerprint, profileKey);
        const directory = path.join(root, id);
        const master = path.join(directory, "master.m3u8");
        const existing = active.get(id);
        if (existing) return await hasStartBuffer(existing.directory, existing.ladder, existing.startSegments) ? markPlayable(existing) : publicState(existing);
        if (await isReady(master)) {
            const saved = await readState(directory);
            if (saved.state === "ready") {
                await normalizePlaylists(directory);
                return publicState({ ...saved, id, mediaKey, directory, state: "ready", progress: 100, ladder: saved.ladder || createQualityLadder(probe), error: "", stderr: "" });
            }
        }
        const tools = await getTools();
        if (!tools.ffmpegAvailable) return { id, mediaKey, state: "failed", progress: 0, qualities: [], errorType: "processing", error: "FFmpeg não está disponível para preparar este vídeo." };
        const ladder = mode === "remux"
            ? [{ id: "original", width: Number(probe?.video?.width || 0), height: Number(probe?.video?.height || 0), bitrate: Number(probe?.bitrate || 0), audioBitrate: 192_000 }]
            : createStartupLadder(probe);
        const segmentSeconds = clamp(settings.hlsSegmentSeconds, 1, 6, HLS_LIMITS.segmentSeconds);
        const startBufferSeconds = clamp(settings.hlsStartBufferSeconds, segmentSeconds, 20, 4);
        const session = {
            id, mediaKey, directory, mode, ladder,
            audioAction: plan.audioAction || "aac",
            segmentSeconds,
            startSegments: Math.max(1, Math.ceil(startBufferSeconds / segmentSeconds)),
            state: "preparing", progress: 0, error: "", errorType: "", stderr: "", fallbackReason: "", child: null,
            startedAt: new Date().toISOString(), firstFrameAt: "", firstPlayableAt: "",
        };
        active.set(id, session);
        run(session, input, probe, settings, tools).catch((error) => console.error(`BRasa HLS ${mediaKey}:`, error.message)).finally(() => active.delete(id));
        return publicState(session);
    }

    async function run(session, input, probe, settings, tools) {
        await recreateDirectory(session);
        await persist(session);
        const initialEncoder = session.mode === "remux" ? "copy" : encoderFor(settings, tools.hardwareAcceleration || {});
        await store.update(session.mediaKey, { status: "processing", playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, progress: 0, error: "" });
        try {
            try {
                await runEncoder(session, tools.ffmpegPath, input, probe, initialEncoder);
            } catch (error) {
                if (session.mode === "transcode" && settings.cpuFallback !== false && initialEncoder !== "libx264") {
                    session.state = "preparing";
                    session.error = "";
                    session.errorType = "";
                    session.progress = 0;
                    session.fallbackReason = error.message;
                    session.stderr = "";
                    await recreateDirectory(session);
                    await persist(session);
                    await store.update(session.mediaKey, { status: "processing", progress: 0, encoder: "libx264", decoder: "software", pipeline: "fallback por CPU", error: "" });
                    await runEncoder(session, tools.ffmpegPath, input, probe, "libx264");
                } else throw error;
            }
            session.state = "ready";
            session.progress = 100;
            session.completedAt = new Date().toISOString();
            session.error = "";
            await normalizePlaylists(session.directory);
            await persist(session);
            await store.update(session.mediaKey, { status: "ready", progress: 100, playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, hlsQualities: session.ladder.map((item) => item.id), completedAt: session.completedAt, error: "" });
        } catch (error) {
            session.state = "failed";
            session.errorType = "processing";
            session.error = "O servidor não conseguiu preparar o streaming adaptativo.";
            session.stderr = error.message || session.stderr;
            await persist(session).catch(() => {});
            await store.update(session.mediaKey, { status: "failed", error: session.error, ffmpegStderr: session.stderr.slice(-1200), encoder: session.encoder || initialEncoder }).catch(() => {});
            throw error;
        }
    }

    function runEncoder(session, command, input, probe, encoder) {
        return new Promise((resolve, reject) => {
            const args = session.mode === "remux"
                ? buildRemuxHlsArgs(input, session.directory, probe, { audioAction: session.audioAction, segmentSeconds: session.segmentSeconds })
                : buildHlsArgs(input, session.directory, session.ladder, encoder, probe, { segmentSeconds: session.segmentSeconds });
            const pipeline = session.mode === "remux"
                ? `video copy + audio ${session.audioAction}`
                : encoder === "h264_nvenc"
                    ? (probe?.video?.hdr ? "NVDEC/CUDA scale + CPU HDR tone map + NVENC" : "NVDEC/CUDA + NVENC")
                    : `software decode + ${encoder}`;
            session.encoder = encoder;
            session.decoder = encoder === "h264_nvenc" ? "hevc_cuvid" : encoder === "copy" ? "copy" : "software";
            session.pipeline = pipeline;
            session.attemptStartedAt = new Date().toISOString();
            persist(session).catch(() => {});
            const child = spawn(command, args, { windowsHide: true, shell: false });
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
                    if (name !== "out_time_ms") continue;
                    const outputSeconds = Number(value) / 1e6;
                    if (session.mode !== "remux" && !videoFrames && outputSeconds >= 20 && !stoppedForNoVideo) {
                        stoppedForNoVideo = true;
                        stderr += "\nNenhum quadro de vídeo foi decodificado nos primeiros 20 segundos.";
                        child.kill("SIGKILL");
                        continue;
                    }
                    if (session.mode !== "remux" && !videoFrames) continue;
                    session.progress = Math.min(99, Math.max(0, outputSeconds / Number(probe.duration || 1) * 100));
                    if (Date.now() - lastSave > 1500) {
                        lastSave = Date.now();
                        persist(session).catch(() => {});
                        store.update(session.mediaKey, { status: "processing", playbackStrategy: session.mode === "remux" ? "hls-remux" : "hls", hlsSessionId: session.id, progress: Number(session.progress.toFixed(1)), encoder, decoder: session.decoder, pipeline, ffmpegStderr: stderr.slice(-1200) }).catch(() => {});
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
        return saved.id ? publicState({ ...saved, directory }) : null;
    }

    async function resolve(id, requested) {
        const directory = safeDirectory(id);
        const relative = String(requested || "master.m3u8").replace(/\\/g, "/");
        if (!/^(?:[a-z0-9-]+\/)?(?:master|index|seg-\d+)\.(?:m3u8|ts|m4s)$/i.test(relative)) return null;
        const file = path.resolve(directory, relative);
        if (!file.startsWith(directory + path.sep) && file !== path.join(directory, "master.m3u8")) return null;
        const stat = await fs.stat(file).catch(() => null);
        return stat?.isFile() ? { file, stat, session: await status(id) } : null;
    }

    async function remove(id) { const directory = safeDirectory(id); active.get(id)?.child?.kill("SIGKILL"); active.delete(id); await fs.rm(directory, { recursive: true, force: true }); }
    function safeDirectory(id) { if (!/^[a-f0-9]{24}$/.test(id)) throw new Error("Sessão HLS inválida."); return path.join(root, id); }
    return { ensure, status, resolve, remove, estimate: (probe) => estimateHlsCacheBytes(probe.duration, createQualityLadder(probe)) };
}

export function buildHlsArgs(input, directory, ladder, encoder = "libx264", probe = {}, options = {}) {
    const cuda = encoder === "h264_nvenc";
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
    const args = ["-y", ...(cuda ? ["-hwaccel", "cuda", "-hwaccel_output_format", "cuda", ...cudaDecoder] : []), "-i", input, "-filter_complex", filters.join(";"), "-map_metadata", "-1"];
    ladder.forEach((quality, index) => {
        args.push("-map", `[v${index}o]`, "-map", "0:a:0?", `-c:v:${index}`, encoder, `-b:v:${index}`, String(quality.bitrate), `-maxrate:v:${index}`, String(quality.maxrate), `-bufsize:v:${index}`, String(quality.buffer));
        const gop = String(Math.max(24, Math.round(Number(probe?.video?.frameRate || 24) * segmentSeconds)));
        args.push(`-g:v:${index}`, gop, `-keyint_min:v:${index}`, gop, `-sc_threshold:v:${index}`, "0");
        if (!cuda || probe?.video?.hdr) args.push(`-pix_fmt:v:${index}`, "yuv420p");
        args.push(`-c:a:${index}`, "aac", `-b:a:${index}`, String(quality.audioBitrate), `-ac:a:${index}`, "2");
        if (encoder === "libx264") args.push(`-preset:v:${index}`, "superfast", `-profile:v:${index}`, "high");
        if (encoder === "h264_nvenc") args.push(`-preset:v:${index}`, "p1", `-tune:v:${index}`, "ll", `-rc:v:${index}`, "vbr", `-cq:v:${index}`, "25", `-spatial-aq:v:${index}`, "1", `-forced-idr:v:${index}`, "1", `-no-scenecut:v:${index}`, "1", `-strict_gop:v:${index}`, "1");
    });
    const map = ladder.map((quality, index) => `v:${index},a:${index},name:${quality.id}`).join(" ");
    return [...args, "-force_key_frames", `expr:gte(t,n_forced*${segmentSeconds})`, "-f", "hls", "-hls_time", String(segmentSeconds), "-hls_list_size", "0", "-hls_playlist_type", "event", "-hls_flags", "independent_segments+temp_file", "-master_pl_name", "master.m3u8", "-var_stream_map", map, "-hls_segment_filename", path.join(directory, "%v", "seg-%06d.ts"), path.join(directory, "%v", "index.m3u8"), "-progress", "pipe:1", "-nostats"];
}

export function buildRemuxHlsArgs(input, directory, probe = {}, options = {}) {
    const segmentSeconds = clamp(options.segmentSeconds, 1, 6, HLS_LIMITS.segmentSeconds);
    const hasAudio = Boolean(probe?.audioTracks?.length);
    const audioAction = options.audioAction === "copy" ? "copy" : "aac";
    const args = ["-y", "-i", input, "-map", "0:v:0", ...(hasAudio ? ["-map", "0:a:0"] : []), "-c:v", "copy"];
    if (hasAudio) args.push("-c:a", audioAction, ...(audioAction === "aac" ? ["-b:a", "192k", "-ac", "2"] : []));
    const map = hasAudio ? "v:0,a:0,name:original" : "v:0,name:original";
    return [...args, "-map_metadata", "-1", "-f", "hls", "-hls_time", String(segmentSeconds), "-hls_list_size", "0", "-hls_playlist_type", "event", "-hls_flags", "independent_segments+temp_file", "-master_pl_name", "master.m3u8", "-var_stream_map", map, "-hls_segment_filename", path.join(directory, "%v", "seg-%06d.ts"), path.join(directory, "%v", "index.m3u8"), "-progress", "pipe:1", "-nostats"];
}

function sessionId(mediaKey, fingerprint = {}, profileKey = "transcode:aac") { return crypto.createHash("sha256").update(`${mediaKey}:${fingerprint.size || 0}:${fingerprint.mtimeMs || 0}:${profileKey}`).digest("hex").slice(0, 24); }
async function isReady(master) { const text = await fs.readFile(master, "utf8").catch(() => ""); return text.includes("#EXTM3U"); }
async function hasStartBuffer(directory, ladder, minimumSegments = 2) { if (!await isReady(path.join(directory, "master.m3u8"))) return false;for (const quality of ladder || []) { const files=await fs.readdir(path.join(directory,quality.id)).catch(()=>[]);if(!files.includes("index.m3u8")||files.filter((file)=>file.endsWith(".ts")).length<minimumSegments)return false;}await normalizePlaylists(directory);return true; }
async function normalizePlaylists(directory) { for(const file of [path.join(directory,"master.m3u8"),...await fs.readdir(directory,{withFileTypes:true}).then((entries)=>entries.filter((entry)=>entry.isDirectory()).map((entry)=>path.join(directory,entry.name,"index.m3u8"))).catch(()=>[])]){const text=await fs.readFile(file,"utf8").catch(()=>"");if(!text||!text.includes("\\"))continue;const temp=`${file}.${process.pid}.normalize.tmp`;await fs.writeFile(temp,text.replace(/\\/g,"/"));await fs.rename(temp,file);} }
async function readState(directory) { return fs.readFile(path.join(directory, "session.json"), "utf8").then(JSON.parse).catch(() => ({})); }
async function recreateDirectory(session) { await fs.rm(session.directory, { recursive: true, force: true });await fs.mkdir(session.directory, { recursive: true });for (const quality of session.ladder) await fs.mkdir(path.join(session.directory, quality.id), { recursive: true }); }
async function persist(session) { const data = { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress.toFixed(1)), ladder: session.ladder, mode: session.mode, encoder: session.encoder || "", decoder: session.decoder || "", pipeline: session.pipeline || "", segmentSeconds: session.segmentSeconds, startSegments: session.startSegments, error: session.error, errorType: session.errorType, stderr: session.stderr?.slice(-1200) || "", fallbackReason: session.fallbackReason?.slice(-1200) || "", startedAt: session.startedAt, attemptStartedAt: session.attemptStartedAt || "", firstFrameAt: session.firstFrameAt || "", firstPlayableAt: session.firstPlayableAt || "", completedAt: session.completedAt || "", estimatedBytes: session.estimatedBytes || 0 }; const file = path.join(session.directory, "session.json"), temp = `${file}.${process.pid}.tmp`; await fs.writeFile(temp, `${JSON.stringify(data, null, 2)}\n`); await fs.rename(temp, file); }
function publicState(session) { return { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress || 0), qualities: (session.ladder || []).map((item) => item.id), error: session.error || "", errorType: session.errorType || "", technical: session.state === "failed" ? { ffmpegStderr: session.stderr?.slice(-800) || "" } : undefined }; }
function markPlayable(session) { if (!session.firstPlayableAt) { session.firstPlayableAt = new Date().toISOString();persist(session).catch(() => {}); }return publicState({ ...session, state: "ready" }); }
function clamp(value, minimum, maximum, fallback) { const number=Number(value);return Number.isFinite(number)?Math.round(Math.min(maximum,Math.max(minimum,number))):fallback; }
