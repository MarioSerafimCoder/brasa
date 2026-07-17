import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { getMediaToolsStatus } from "../server/media-tools.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-media-tools-"));
try {
    const ffmpeg = path.join(root, "FFmpeg custom", "ffmpeg.exe"), ffprobe = path.join(root, "FFmpeg custom", "ffprobe.exe");
    await fs.mkdir(path.dirname(ffmpeg), { recursive: true });
    await Promise.all([fs.writeFile(ffmpeg, "test"), fs.writeFile(ffprobe, "test")]);
    const calls = [];
    const runner = async (command, args) => {
        calls.push({ command, args });
        if (args.includes("-version")) return { code: 0, stdout: "ffmpeg version test", stderr: "" };
        if (args.includes("-encoders")) return { code: 0, stdout: "", stderr: " V....D h264_nvenc\n V....D hevc_nvenc\n V....D av1_nvenc\n V....D h264_qsv\n V....D h264_amf" };
        if (args.includes("-hwaccels")) return { code: 0, stdout: "Hardware acceleration methods:\ncuda\nqsv", stderr: "" };
        const encoder = args[args.indexOf("-c:v") + 1];
        return encoder === "h264_nvenc" ? { code: 0, stdout: "", stderr: "" } : { code: 1, stdout: "", stderr: encoder === "h264_qsv" ? "No device available" : "Encoder failed" };
    };
    const status = await getMediaToolsStatus(root, { refresh: true, runCommand: runner, env: { FFMPEG_PATH: `"${ffmpeg}"`, FFPROBE_PATH: ffprobe }, platform: "win32" });
    assert.equal(status.ffmpegPath, ffmpeg);
    assert.equal(status.ffmpegSource, "env:FFMPEG_PATH");
    assert.equal(status.hardwareAcceleration.nvenc, true);
    assert.equal(status.hardwareAcceleration.qsv, false);
    assert.equal(status.selectedEncoder, "h264_nvenc");
    assert.equal(status.encoders.nvenc.hevc_nvenc, true);
    assert.ok(calls.find((call) => call.args.includes("h264_nvenc")).args.includes("color=size=256x256:rate=30"));
    const queueSource = await fs.readFile(new URL("../server/media-queue.mjs", import.meta.url), "utf8");
    assert.match(queueSource, /-hwaccel", "cuda", "-hwaccel_output_format", "cuda"/);
    assert.match(queueSource, /scale_cuda=passthrough=0:format=yuv420p/);
    assert.match(queueSource, /shouldUseAdaptiveHls\(item\.probe\)\.useHls/);
    assert.match(queueSource, /restore:restoreAdaptive/);
    assert.match(queueSource, /Number\(probe\.schemaVersion\|\|0\)<3/);
    const hlsSource = await fs.readFile(new URL("../server/hls-session.mjs", import.meta.url), "utf8");
    assert.match(hlsSource, /session\.state = "preparing";[\s\S]*fallbackReason = error\.message/);
    assert.match(hlsSource, /playbackStrategy: session\.mode === "remux" \? "hls-remux" : "hls"/);
    assert.doesNotMatch(hlsSource, /A aceleração por hardware falhou; continuando pela CPU/);
    const driverFailure = async (_command, args) => {
        if (args.includes("-version")) return { code: 0, stdout: "ffmpeg version test", stderr: "" };
        if (args.includes("-encoders")) return { code: 0, stdout: " V....D h264_nvenc", stderr: "" };
        if (args.includes("-hwaccels")) return { code: 0, stdout: "cuda", stderr: "" };
        return { code: 1, stdout: "", stderr: "Cannot load nvEncodeAPI64.dll" };
    };
    const failed = await getMediaToolsStatus(root, { refresh: true, runCommand: driverFailure, env: { FFMPEG_PATH: ffmpeg, FFPROBE_PATH: ffprobe }, platform: "win32" });
    assert.equal(failed.hardwareAcceleration.nvenc, false);
    assert.equal(failed.validation.nvenc.code, "DRIVER_UNAVAILABLE");
    console.log("Ferramentas de mídia: caminhos, NVENC prático e diagnóstico de falha aprovados.");
} finally { await fs.rm(root, { recursive: true, force: true }); }
