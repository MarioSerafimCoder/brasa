import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";

const ENCODERS = {
    nvenc: ["h264_nvenc", "hevc_nvenc", "av1_nvenc"],
    qsv: ["h264_qsv"],
    amf: ["h264_amf"],
};

let cache = null;

export function clearMediaToolsCache() { cache = null; }

export async function getMediaToolsStatus(rootDir, { refresh = false, runCommand = run, env = process.env, platform = process.platform } = {}) {
    if (cache && !refresh && runCommand === run && env === process.env && platform === process.platform) return cache;
    const ffmpeg = await findTool("ffmpeg", env.FFMPEG_PATH, rootDir, { runCommand, env, platform });
    const ffprobe = await findTool("ffprobe", env.FFPROBE_PATH, rootDir, { runCommand, env, platform });
    const status = {
        ffmpegAvailable: Boolean(ffmpeg.path), ffprobeAvailable: Boolean(ffprobe.path),
        ffmpegPath: ffmpeg.path, ffprobePath: ffprobe.path,
        ffmpegSource: ffmpeg.source, ffprobeSource: ffprobe.source,
        version: "", hwaccels: [], encoders: {}, validation: {},
        hardwareAcceleration: { nvenc: false, qsv: false, amf: false },
        selectedEncoder: "libx264", refreshedAt: new Date().toISOString(),
    };
    if (ffmpeg.path) {
        const versionResult = await runCommand(ffmpeg.path, ["-version"], 7000);
        status.version = combined(versionResult).split(/\r?\n/)[0] || "";
        const encoderResult = await runCommand(ffmpeg.path, ["-hide_banner", "-encoders"], 10000);
        const encoderText = combined(encoderResult);
        const hwaccelResult = await runCommand(ffmpeg.path, ["-hide_banner", "-hwaccels"], 10000);
        status.hwaccels = combined(hwaccelResult).split(/\r?\n/).map((line) => line.trim().toLowerCase()).filter((line) => /^(cuda|qsv|d3d11va|d3d12va|dxva2|vaapi|vulkan|amf)$/.test(line));
        for (const [family, names] of Object.entries(ENCODERS)) {
            status.encoders[family] = Object.fromEntries(names.map((name) => [name, new RegExp(`(?:^|\\s)${name}(?:\\s|$)`, "m").test(encoderText)]));
            const primary = names[0];
            status.validation[family] = status.encoders[family][primary]
                ? await validateEncoder(ffmpeg.path, primary, runCommand)
                : failure("ENCODER_NOT_COMPILED", `${primary} não está incluído neste FFmpeg.`);
            status.hardwareAcceleration[family] = status.validation[family].ok;
        }
        status.selectedEncoder = status.hardwareAcceleration.nvenc ? "h264_nvenc" : status.hardwareAcceleration.qsv ? "h264_qsv" : status.hardwareAcceleration.amf ? "h264_amf" : "libx264";
    } else {
        status.validation.ffmpeg = failure("FFMPEG_NOT_FOUND", "FFmpeg não foi encontrado nos caminhos configurados.");
    }
    if (runCommand === run && env === process.env && platform === process.platform) cache = status;
    return status;
}

async function validateEncoder(command, encoder, runCommand) {
    // 256x256 evita falsos negativos: GPUs NVIDIA recentes recusam dimensões muito pequenas.
    const result = await runCommand(command, ["-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=size=256x256:rate=30", "-frames:v", "3", "-c:v", encoder, "-f", "null", "-"], 15000);
    if (result.code === 0) return { ok: true, code: "OK", message: `${encoder} validado com codificação prática.`, stderr: "" };
    const detail = combined(result).slice(-1600);
    if (result.timedOut) return failure("TIMEOUT", `O teste de ${encoder} excedeu o tempo limite.`, detail);
    if (/cannot load (?:nvcuda|nvencodeapi)|driver does not support|required nvenc api|dll .*failed to open/i.test(detail)) return failure("DRIVER_UNAVAILABLE", "O driver ou a API de aceleração não está disponível.", detail);
    if (/no device|device not found|no capable devices|cannot init cuda|mfx implementation is not supported/i.test(detail)) return failure("GPU_NOT_FOUND", "Nenhuma GPU compatível foi encontrada pelo encoder.", detail);
    if (/too many concurrent|session limit|out of sessions/i.test(detail)) return failure("SESSION_LIMIT", "O limite de sessões simultâneas do encoder foi atingido.", detail);
    if (/invalid|minimum|width|height|dimension/i.test(detail)) return failure("INVALID_PARAMETERS", "O encoder recusou os parâmetros do teste.", detail);
    return failure("ENCODER_TEST_FAILED", `O teste prático de ${encoder} falhou.`, detail);
}

function failure(code, message, stderr = "") { return { ok: false, code, message, stderr }; }
function combined(result = {}) { return `${result.stdout || ""}\n${result.stderr || ""}`.trim(); }

async function findTool(name, configured, root, { runCommand, env, platform }) {
    const exe = platform === "win32" ? `${name}.exe` : name;
    const candidates = [
        { value: configured, source: `env:${name.toUpperCase()}_PATH` },
        { value: path.join(root, "tools", "ffmpeg", exe), source: "bundled" },
        { value: name, source: "PATH" },
    ];
    if (platform === "win32") candidates.push(
        { value: `C:\\ffmpeg\\bin\\${exe}`, source: "C:\\ffmpeg" },
        { value: path.join(env.ProgramFiles || "", "ffmpeg", "bin", exe), source: "ProgramFiles" },
        { value: path.join(env.LOCALAPPDATA || "", "Microsoft", "WinGet", "Links", exe), source: "WinGet" },
    );
    for (const candidate of candidates) {
        const value = normalizePath(candidate.value);
        if (!value) continue;
        if (path.isAbsolute(value)) {
            if (await fs.access(value).then(() => true).catch(() => false)) return { path: path.normalize(value), source: candidate.source };
        } else if ((await runCommand(value, ["-version"], 5000)).code === 0) return { path: value, source: candidate.source };
    }
    return { path: "", source: "" };
}

function normalizePath(value) {
    const clean = String(value || "").trim();
    return clean.length >= 2 && clean[0] === clean.at(-1) && ['"', "'"].includes(clean[0]) ? clean.slice(1, -1).trim() : clean;
}

function run(command, args, timeout = 7000) {
    return new Promise((resolve) => {
        let stdout = "", stderr = "", done = false, timedOut = false, child;
        try { child = spawn(command, args, { windowsHide: true, shell: false }); }
        catch (error) { return resolve({ code: -1, stdout: "", stderr: error.message, timedOut: false }); }
        const timer = setTimeout(() => { timedOut = true; child.kill("SIGKILL"); }, timeout);
        child.stdout?.on("data", (chunk) => stdout = (stdout + chunk).slice(-200000));
        child.stderr?.on("data", (chunk) => stderr = (stderr + chunk).slice(-50000));
        child.on("error", (error) => { if (done) return; done = true; clearTimeout(timer); resolve({ code: -1, stdout, stderr: error.message, timedOut }); });
        child.on("close", (code) => { if (done) return; done = true; clearTimeout(timer); resolve({ code, stdout, stderr, timedOut }); });
    });
}
