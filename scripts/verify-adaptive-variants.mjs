import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { buildHlsArgs } from "../server/hls-session.mjs";
import { createAdaptiveLadder, encoderFor } from "../server/transcoding-profiles.mjs";
import { getMediaToolsStatus } from "../server/media-tools.mjs";

const input = process.argv[2];
if (!input) throw new Error("Informe um arquivo local de vídeo para a verificação.");
const startSeconds = Number(process.argv[3] || 0);
const cpu = process.argv.includes("--cpu");
const tools = await getMediaToolsStatus(process.cwd());
const ffprobe = path.join(path.dirname(tools.ffmpegPath), process.platform === "win32" ? "ffprobe.exe" : "ffprobe");
const source = JSON.parse(await run(ffprobe, ["-v", "error", "-show_streams", "-of", "json", input]));
const video = source.streams.find(stream => stream.codec_type === "video");
const [num, den] = video.avg_frame_rate.split("/").map(Number);
const probe = { video: { codec: video.codec_name, width: video.width, height: video.height, frameRate: num / den, hdr: ["smpte2084", "arib-std-b67"].includes(video.color_transfer) }, audioTracks: source.streams.filter(stream => stream.codec_type === "audio") };
const settings = cpu ? { acceleration: "cpu" } : {};
const encoder = encoderFor(settings, tools.hardwareAcceleration);
const ladder = createAdaptiveLadder(probe, settings, tools.hardwareAcceleration);
const directory = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-abr-verify-"));
try {
    for (const quality of ladder) await fs.mkdir(path.join(directory, quality.id));
    const args = buildHlsArgs(input, directory, ladder, encoder, probe, { startPositionSeconds: startSeconds, segmentSeconds: 2 });
    args.splice(args.indexOf("-filter_complex"), 0, "-t", "24");
    const startedAt = Date.now();
    await run(tools.ffmpegPath, args);
    const speed = 24 / ((Date.now() - startedAt) / 1000);
    const master = await fs.readFile(path.join(directory, "master.m3u8"), "utf8");
    assert.equal((master.match(/#EXT-X-STREAM-INF/g) || []).length, ladder.length);
    const timestamps = [];
    for (const quality of ladder) {
        const playlist = path.join(directory, quality.id, "index.m3u8");
        const text = await fs.readFile(playlist, "utf8");
        assert.ok(text.includes("#EXT-X-ENDLIST"));
        const segments = text.split(/\r?\n/).filter(line => line.endsWith(".ts"));
        const durations = [...text.matchAll(/#EXTINF:([\d.]+)/g)].map(match => Number(match[1]));
        assert.ok(Math.max(...durations) < 2.2, "segmentos curtos em todas as variantes");
        assert.ok(durations.reduce((a, b) => a + b, 0) >= 23.8);
        await run(tools.ffmpegPath, ["-v", "error", "-xerror", "-i", playlist, "-map", "0:v:0", "-map", "0:a:0?", "-f", "null", "-"]);
        const pts = [];
        for (const segment of segments.slice(0, 4)) {
            const data = JSON.parse(await run(ffprobe, ["-v", "error", "-select_streams", "v:0", "-read_intervals", "%+#1", "-show_packets", "-show_entries", "packet=pts_time,flags", "-of", "json", path.join(directory, quality.id, segment)]));
            assert.ok(data.packets[0].flags.includes("K"), "variante inicia cada segmento com keyframe");
            pts.push(Number(data.packets[0].pts_time));
        }
        timestamps.push(pts);
    }
    for (const pts of timestamps) pts.forEach((value, index) => assert.ok(Math.abs(value - timestamps[0][index]) < .002, "variantes alinhadas para troca sem salto"));
    console.log(JSON.stringify({ encoder, variants: ladder.map(q => q.id), sourceStartSeconds: startSeconds, decodedSecondsPerVariant: 24, alignedKeyframes: true, encodingSpeed: Number(speed.toFixed(2)) }, null, 2));
} finally { await fs.rm(directory, { recursive: true, force: true }); }

function run(command, args) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { windowsHide: true });
        let output = "", error = "";
        const timeout = setTimeout(() => { child.kill(); reject(new Error("Verificação excedeu cinco minutos.")); }, 300_000);
        child.stdout.on("data", chunk => output = (output + chunk).slice(-1_000_000));
        child.stderr.on("data", chunk => error = (error + chunk).slice(-6000));
        child.on("error", issue => { clearTimeout(timeout); reject(issue); });
        child.on("close", code => { clearTimeout(timeout); code === 0 ? resolve(output) : reject(new Error(error)); });
    });
}
