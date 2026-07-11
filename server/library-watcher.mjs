import { watch } from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";

const VIDEO_EXTENSIONS = new Set([".mp4", ".mkv", ".webm", ".mov", ".avi"]);

export async function startLibraryWatcher({ rootDir, onStableChange, debounceMs = 1500, stabilityMs = 2000 }) {
    const roots = ["assets/movies", "assets/kids-movies", "assets/series", "assets/kids-series"].map((value) => path.join(rootDir, value));
    const watchers = [], timers = new Map();
    for (const directory of roots) {
        if (!(await exists(directory))) continue;
        try {
            watchers.push(watch(directory, { recursive: true }, (_event, fileName) => {
                if (!fileName || !VIDEO_EXTENSIONS.has(path.extname(String(fileName)).toLowerCase())) return;
                const absolutePath = path.join(directory, String(fileName));
                clearTimeout(timers.get(absolutePath));
                timers.set(absolutePath, setTimeout(async () => {
                    timers.delete(absolutePath);
                    if (await isStable(absolutePath, stabilityMs)) onStableChange(absolutePath);
                    else scheduleRetry(absolutePath);
                }, debounceMs));
            }));
        } catch (error) {
            console.log(`BRasa: observacao indisponivel em ${directory} (${error.message}).`);
        }
    }
    function scheduleRetry(absolutePath) {
        timers.set(absolutePath, setTimeout(async () => {
            timers.delete(absolutePath);
            if (await isStable(absolutePath, stabilityMs)) onStableChange(absolutePath);
        }, stabilityMs));
    }
    return () => { timers.forEach(clearTimeout); watchers.forEach((item) => item.close()); };
}

async function isStable(file, delay) {
    const first = await fs.stat(file).catch(() => null);
    if (!first) return true;
    await new Promise((resolve) => setTimeout(resolve, delay));
    const second = await fs.stat(file).catch(() => null);
    return !second || (first.size === second.size && first.mtimeMs === second.mtimeMs);
}

async function exists(value) { return fs.access(value).then(() => true).catch(() => false); }
