import fs from "node:fs/promises";
import path from "node:path";

export function createMediaStateStore(root) {
    const file = path.join(root, "data", "media-state.json");
    const backup = path.join(root, "data", "media-state.backup.json");
    let state = null;
    let queue = Promise.resolve();
    const defaults = () => ({
        version: 5,
        settings: {
            autoAnalyze: true,
            autoPrepare: false,
            generateThumbnails: true,
            extractSubtitles: true,
            keepOriginal: true,
            cpuFallback: true,
            maxConcurrent: 1,
            acceleration: "auto",
            quality: "balanced",
            audioLanguage: "pt-br",
            subtitleLanguages: ["pt-br", "en"],
            minimumFreeGb: 5,
            maxPreparedGb: 250,
            maxHlsGb: 160,
            removePartialOnFailure: true,
            retainAdvancedPreparation: true,
            hlsSegmentSeconds: 2,
            hlsStartBufferSeconds: 12,
            hlsTargetBufferSeconds: 30,
            hlsMaxBufferSeconds: 90,
            paused: false
        },
        items: {},
        updatedAt: ""
    });

    async function load() {
        if (state) return state;
        const text = await fs.readFile(file, "utf8").catch(() => "");
        if (!text) {
            state = defaults();
            await save();
            return state;
        }
        try {
            const parsed = JSON.parse(text);
            const base = defaults();
            const version = Number(parsed.version || 1);
            const legacy = version < 2 ? { autoAnalyze: true, autoPrepare: true, cpuFallback: true, acceleration: "auto" } : {};
            const lowLatency = version < 3 ? { hlsSegmentSeconds: 2, hlsStartBufferSeconds: 4, hlsTargetBufferSeconds: 30, hlsMaxBufferSeconds: 90 } : {};
            const playbackFirst = version < 4 ? { autoPrepare: false } : {};
            const stablePlayback = version < 5 ? { hlsStartBufferSeconds: 12 } : {};
            state = {
                ...base,
                ...parsed,
                version: 5,
                settings: { ...base.settings, ...parsed.settings, ...legacy, ...lowLatency, ...playbackFirst, ...stablePlayback }
            };
            if (version < 5) await save();
        } catch {
            await fs.copyFile(file, `${file}.corrupt-${Date.now()}`).catch(() => {});
            const recovered = await fs.readFile(backup, "utf8").then(JSON.parse).catch(defaults);
            state = { ...defaults(), ...recovered, version: 5, settings: { ...defaults().settings, ...recovered.settings, autoPrepare: false, hlsStartBufferSeconds: 12 } };
            await save();
        }
        return state;
    }

    function save() {
        queue = queue.then(async () => {
            const temporary = `${file}.${process.pid}.tmp`;
            const previous = await fs.readFile(file, "utf8").catch(() => "");
            if (previous) await fs.writeFile(backup, previous, "utf8");
            state.updatedAt = new Date().toISOString();
            await fs.writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`);
            await fs.rename(temporary, file);
        });
        return queue;
    }

    return {
        load,
        save,
        get: async (key) => (await load()).items[key],
        all: load,
        update: async (key, patch) => {
            const current = await load();
            current.items[key] = { ...(current.items[key] || {}), ...patch, updatedAt: new Date().toISOString() };
            await save();
            return current.items[key];
        },
        remove: async (key) => {
            const current = await load();
            delete current.items[key];
            await save();
        },
        settings: async () => (await load()).settings,
        setSettings: async (input) => {
            const current = await load();
            current.settings = { ...current.settings, ...input };
            await save();
            return current.settings;
        }
    };
}
