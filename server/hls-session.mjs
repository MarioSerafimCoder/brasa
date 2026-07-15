import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { createQualityLadder, encoderFor, estimateHlsCacheBytes, HLS_LIMITS } from "./transcoding-profiles.mjs";

export function createHlsSessionManager({ rootDir, store, getTools }) {
    const root = path.join(rootDir, "data", "prepared-media", "hls");
    const active = new Map();

    async function ensure(mediaKey, input, probe, settings = {}) {
        const id = sessionId(mediaKey, probe?.fingerprint);
        const directory = path.join(root, id);
        const master = path.join(directory, "master.m3u8");
        const existing = active.get(id);
        if (existing) return await hasStartBuffer(existing.directory, existing.ladder) ? publicState({ ...existing, state: "ready" }) : publicState(existing);
        if (await isReady(master)) {
            const saved = await readState(directory);
            if (saved.state === "ready") { await normalizePlaylists(directory);return publicState({ id, mediaKey, directory, state: "ready", progress: 100, ladder: saved.ladder || createQualityLadder(probe), error: "", stderr: "" }); }
        }
        const tools = await getTools();
        if (!tools.ffmpegAvailable) return { id, mediaKey, state: "failed", progress: 0, qualities: [], errorType: "processing", error: "FFmpeg não está disponível para preparar este vídeo." };
        const ladder = createQualityLadder(probe);
        const session = { id, mediaKey, directory, state: "preparing", progress: 0, ladder, error: "", errorType: "", stderr: "", child: null, startedAt: new Date().toISOString() };
        active.set(id, session);
        run(session, input, probe, settings, tools).finally(() => active.delete(id));
        return publicState(session);
    }

    async function run(session, input, probe, settings, tools) {
        await fs.rm(session.directory, { recursive: true, force: true });
        await fs.mkdir(session.directory, { recursive: true });
        for (const quality of session.ladder) await fs.mkdir(path.join(session.directory, quality.id), { recursive: true });
        await persist(session);
        const hardware = tools.hardwareAcceleration || {};
        const initialEncoder = encoderFor(settings, hardware);
        if (initialEncoder === "libx264" && session.ladder.length > 1) session.ladder = [session.ladder[0]];
        try {
            await runEncoder(session, tools.ffmpegPath, input, probe, initialEncoder);
        } catch (error) {
            if (settings.cpuFallback !== false && initialEncoder !== "libx264") {
                session.error = "A aceleração por hardware falhou; continuando pela CPU.";
                session.stderr = error.message;
                await fs.rm(session.directory, { recursive: true, force: true });
                await fs.mkdir(session.directory, { recursive: true });
                session.ladder = [session.ladder[0]];
                for (const quality of session.ladder) await fs.mkdir(path.join(session.directory, quality.id), { recursive: true });
                await runEncoder(session, tools.ffmpegPath, input, probe, "libx264");
            } else throw error;
        }
        session.state = "ready";
        session.progress = 100;
        session.completedAt = new Date().toISOString();
        session.error = "";
        await normalizePlaylists(session.directory);
        await persist(session);
        await store.update(session.mediaKey, { status: "ready", progress: 100, playbackStrategy: "hls", hlsSessionId: session.id, hlsQualities: session.ladder.map((item) => item.id), completedAt: session.completedAt, error: "" });
    }

    function runEncoder(session, command, input, probe, encoder) {
        return new Promise((resolve, reject) => {
            const args = buildHlsArgs(input, session.directory, session.ladder, encoder, probe);
            const child = spawn(command, args, { windowsHide: true, shell: false });
            session.child = child;
            let out = "", stderr = "", lastSave = 0;
            child.stdout.on("data", (chunk) => {
                out += chunk;
                const lines = out.split(/\r?\n/); out = lines.pop() || "";
                for (const line of lines) {
                    const [name, value] = line.split("=");
                    if (name !== "out_time_ms") continue;
                    session.progress = Math.min(99, Math.max(0, Number(value) / 1e6 / Number(probe.duration || 1) * 100));
                    if (Date.now() - lastSave > 1500) { lastSave = Date.now(); persist(session).catch(() => {}); store.update(session.mediaKey, { status: "processing", playbackStrategy: "hls", hlsSessionId: session.id, progress: Number(session.progress.toFixed(1)), encoder, ffmpegStderr: stderr.slice(-1200) }).catch(() => {}); }
                }
            });
            child.stderr.on("data", (chunk) => { stderr = (stderr + chunk).slice(-6000); session.stderr = stderr; });
            child.on("error", reject);
            child.on("close", async (code) => {
                session.child = null;
                if (code === 0) return resolve();
                session.state = "failed"; session.errorType = "processing"; session.error = "O servidor não conseguiu preparar o streaming adaptativo.";
                await persist(session).catch(() => {});
                await store.update(session.mediaKey, { status: "failed", error: session.error, ffmpegStderr: stderr.slice(-1200), encoder }).catch(() => {});
                reject(new Error(stderr.slice(-1200) || `FFmpeg terminou com código ${code}.`));
            });
        });
    }

    async function status(id) {
        const current = active.get(id);
        if (current) return await hasStartBuffer(current.directory, current.ladder) ? publicState({ ...current, state: "ready" }) : publicState(current);
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

export function buildHlsArgs(input, directory, ladder, encoder = "libx264", probe = {}) {
    const filters = [`[0:v:0]split=${ladder.length}${ladder.map((_, index) => `[v${index}]`).join("")}`];
    const hdrToSdr = probe?.video?.hdr ? "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv," : "";
    ladder.forEach((quality, index) => filters.push(`[v${index}]${hdrToSdr}scale=w=${quality.width}:h=${quality.height}:force_original_aspect_ratio=decrease:force_divisible_by=2[v${index}o]`));
    const args = ["-y", "-i", input, "-filter_complex", filters.join(";"), "-map_metadata", "-1"];
    ladder.forEach((quality, index) => {
        args.push("-map", `[v${index}o]`, "-map", "0:a:0?", `-c:v:${index}`, encoder, `-b:v:${index}`, String(quality.bitrate), `-maxrate:v:${index}`, String(quality.maxrate), `-bufsize:v:${index}`, String(quality.buffer), `-pix_fmt:v:${index}`, "yuv420p", `-c:a:${index}`, "aac", `-b:a:${index}`, String(quality.audioBitrate), `-ac:a:${index}`, "2");
        if (encoder === "libx264") args.push(`-preset:v:${index}`, "superfast", `-profile:v:${index}`, "high");
    });
    const map = ladder.map((quality, index) => `v:${index},a:${index},name:${quality.id}`).join(" ");
    return [...args, "-force_key_frames", `expr:gte(t,n_forced*${HLS_LIMITS.segmentSeconds})`, "-f", "hls", "-hls_time", String(HLS_LIMITS.segmentSeconds), "-hls_list_size", "0", "-hls_playlist_type", "event", "-hls_flags", "independent_segments+temp_file", "-master_pl_name", "master.m3u8", "-var_stream_map", map, "-hls_segment_filename", path.join(directory, "%v", "seg-%06d.ts"), path.join(directory, "%v", "index.m3u8"), "-progress", "pipe:1", "-nostats"];
}

function sessionId(mediaKey, fingerprint = {}) { return crypto.createHash("sha256").update(`${mediaKey}:${fingerprint.size || 0}:${fingerprint.mtimeMs || 0}`).digest("hex").slice(0, 24); }
async function isReady(master) { const text = await fs.readFile(master, "utf8").catch(() => ""); return text.includes("#EXTM3U"); }
async function hasStartBuffer(directory, ladder) { if (!await isReady(path.join(directory, "master.m3u8"))) return false;for (const quality of ladder || []) { const files=await fs.readdir(path.join(directory,quality.id)).catch(()=>[]);if(!files.includes("index.m3u8")||files.filter((file)=>file.endsWith(".ts")).length<2)return false;}await normalizePlaylists(directory);return true; }
async function normalizePlaylists(directory) { for(const file of [path.join(directory,"master.m3u8"),...await fs.readdir(directory,{withFileTypes:true}).then((entries)=>entries.filter((entry)=>entry.isDirectory()).map((entry)=>path.join(directory,entry.name,"index.m3u8"))).catch(()=>[])]){const text=await fs.readFile(file,"utf8").catch(()=>"");if(!text||!text.includes("\\"))continue;const temp=`${file}.${process.pid}.normalize.tmp`;await fs.writeFile(temp,text.replace(/\\/g,"/"));await fs.rename(temp,file);} }
async function readState(directory) { return fs.readFile(path.join(directory, "session.json"), "utf8").then(JSON.parse).catch(() => ({})); }
async function persist(session) { const data = { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress.toFixed(1)), ladder: session.ladder, error: session.error, errorType: session.errorType, stderr: session.stderr?.slice(-1200) || "", startedAt: session.startedAt, completedAt: session.completedAt || "", estimatedBytes: session.estimatedBytes || 0 }; const file = path.join(session.directory, "session.json"), temp = `${file}.${process.pid}.tmp`; await fs.writeFile(temp, `${JSON.stringify(data, null, 2)}\n`); await fs.rename(temp, file); }
function publicState(session) { return { id: session.id, mediaKey: session.mediaKey, state: session.state, progress: Number(session.progress || 0), qualities: (session.ladder || []).map((item) => item.id), error: session.error || "", errorType: session.errorType || "", technical: session.state === "failed" ? { ffmpegStderr: session.stderr?.slice(-800) || "" } : undefined }; }
