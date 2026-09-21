import { spawn } from "node:child_process";

// Copying video cannot create a keyframe. Start at a real keyframe and expose
// the difference as the player's local resume position instead of losing time.
export function precedingKeyframe(raw, targetMs) {
    const origin = Number(raw.format?.start_time || 0);
    const points = (raw.frames || []).map(frame => Math.round((Number(frame.best_effort_timestamp_time) - origin) * 1000))
        .filter(value => Number.isFinite(value) && value >= 0 && value <= targetMs);
    return points.length ? Math.max(...points) : null;
}

export function findRemuxKeyframe(command, input, targetMs) {
    if (targetMs === 0) return Promise.resolve(0);
    if (!command) return Promise.resolve(null);
    return new Promise(resolve => {
        const start = Math.max(0, targetMs / 1000 - 30);
        const child = spawn(command, ["-v", "error", "-select_streams", "v:0", "-skip_frame", "nokey",
            "-read_intervals", `${start}%+35`, "-show_frames", "-show_format", "-show_entries",
            "frame=best_effort_timestamp_time:format=start_time", "-of", "json", input], { windowsHide: true, shell: false });
        let output = "", finished = false;
        const finish = value => { if (finished) return; finished = true; clearTimeout(timer); resolve(value); };
        const timer = setTimeout(() => { child.kill(); finish(null); }, 10_000);
        child.stdout.on("data", chunk => { output += chunk; if (output.length > 1_000_000) { child.kill(); finish(null); } });
        child.stderr.resume();
        child.on("error", () => finish(null));
        child.on("close", code => {
            try { finish(code === 0 ? precedingKeyframe(JSON.parse(output), targetMs) : null); }
            catch { finish(null); }
        });
    });
}
