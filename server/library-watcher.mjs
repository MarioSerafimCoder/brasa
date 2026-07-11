import { watch as nodeWatch } from "node:fs";
import nodeFs from "node:fs/promises";
import path from "node:path";
import { absoluteLibraryRoots, isProcessableVideo, TEMPORARY_FILE_PATTERN, VIDEO_EXTENSIONS } from "./library-config.mjs";

export async function startLibraryWatcher({
    rootDir, onStableChange, onError = (error) => console.error("BRasa watcher:", error),
    stabilityIntervalMs = Number(process.env.BRASA_WATCH_STABILITY_INTERVAL || 2000),
    requiredStableChecks = Number(process.env.BRASA_WATCH_STABLE_CHECKS || 3),
    maxStabilityWaitMs = Number(process.env.BRASA_WATCH_TIMEOUT || 10 * 60 * 1000),
    reinstallDelayMs = 5000, fs = nodeFs, watch = nodeWatch, now = () => Date.now()
}) {
    const states = new Map(), watchers = new Map(), reinstallTimers = new Map(); let stopped = false;
    const roots = absoluteLibraryRoots(rootDir);

    async function install(root) {
        if (stopped || watchers.has(root.absolutePath)) return;
        if (!await fs.access(root.absolutePath).then(() => true).catch(() => false)) return scheduleReinstall(root);
        try {
            const watcher = watch(root.absolutePath, { recursive: true }, (event, fileName) => {
                Promise.resolve(handleEvent(root, event, fileName)).catch((error) => onError(error, root.absolutePath));
            });
            watcher.on?.("error", (error) => { onError(error, root.absolutePath); watcher.close(); watchers.delete(root.absolutePath); scheduleReinstall(root); });
            watchers.set(root.absolutePath, watcher);
        } catch (error) { onError(error, root.absolutePath); scheduleReinstall(root); }
    }
    function scheduleReinstall(root) {
        if (stopped || reinstallTimers.has(root.absolutePath)) return;
        reinstallTimers.set(root.absolutePath, setTimeout(() => { reinstallTimers.delete(root.absolutePath); install(root); }, reinstallDelayMs));
    }
    async function handleEvent(root, event, fileName) {
        if (!fileName) return;
        const absolutePath = path.resolve(root.absolutePath, String(fileName));
        if (!absolutePath.startsWith(`${root.absolutePath}${path.sep}`)) return;
        if (TEMPORARY_FILE_PATTERN.test(path.basename(absolutePath))) return;
        if (!VIDEO_EXTENSIONS.includes(path.extname(absolutePath).toLowerCase())) return;
        const stat = await fs.stat(absolutePath).catch((error) => error?.code === "ENOENT" ? null : Promise.reject(error));
        if (!stat) { clearState(absolutePath); await onStableChange({ path: absolutePath, root, event: "removed", status: "queued" }); return; }
        if (!isProcessableVideo(absolutePath, stat.size)) return;
        queueCheck(absolutePath, root, stat);
    }
    function queueCheck(file, root, stat) {
        const existing = states.get(file);
        if (existing?.timer || ["stable", "queued", "processing"].includes(existing?.status)) return;
        const state = existing || { path: file, root, firstSeenAt: now(), lastCheckedAt: 0, lastSize: stat.size, lastMtimeMs: stat.mtimeMs, stableChecks: 0, attempts: 0, status: "waiting", timer: null };
        states.set(file, state); state.timer = setTimeout(() => check(state), stabilityIntervalMs);
    }
    async function check(state) {
        state.timer = null; state.status = "checking"; state.attempts++; state.lastCheckedAt = now();
        try {
            const stat = await fs.stat(state.path).catch((error) => error?.code === "ENOENT" ? null : Promise.reject(error));
            if (!stat) { clearState(state.path); await onStableChange({ path: state.path, root: state.root, event: "removed", status: "queued" }); return; }
            if (!isProcessableVideo(state.path, stat.size)) { clearState(state.path); return; }
            state.stableChecks = stat.size === state.lastSize && stat.mtimeMs === state.lastMtimeMs ? state.stableChecks + 1 : 0;
            state.lastSize = stat.size; state.lastMtimeMs = stat.mtimeMs;
            if (state.stableChecks >= requiredStableChecks) {
                state.status = "queued"; await onStableChange({ ...publicState(state), event: "stable" }); state.status = "completed"; clearState(state.path); return;
            }
            if (now() - state.firstSeenAt >= maxStabilityWaitMs) { state.status = "timeout"; onError(new Error(`Tempo limite aguardando arquivo estável: ${state.path}`), state.root.absolutePath); clearState(state.path); return; }
            state.status = "waiting"; state.timer = setTimeout(() => check(state), stabilityIntervalMs);
        } catch (error) { state.status = "error"; onError(error, state.root.absolutePath); clearState(state.path); }
    }
    function publicState(state) { const { timer, ...value } = state; return value; }
    function clearState(file) { const state = states.get(file); if (state?.timer) clearTimeout(state.timer); states.delete(file); }
    for (const root of roots) await install(root);
    return { stop() { stopped = true; states.forEach((state) => state.timer && clearTimeout(state.timer)); states.clear(); reinstallTimers.forEach(clearTimeout); reinstallTimers.clear(); watchers.forEach((item) => item.close()); watchers.clear(); }, getStates: () => [...states.values()].map(publicState) };
}
