import fs from "node:fs/promises";
import path from "node:path";
import { AsyncLocalStorage } from "node:async_hooks";

/** One lock covers the entire read/change/write operation across TV, web and admin. */
export function createUserStateStore({ file, backupFile, defaults, normalize = value => value, io = fs }) {
    const context = new AsyncLocalStorage();
    let tail = Promise.resolve();
    function run(task) {
        if (context.getStore()) return Promise.resolve().then(task);
        const next = tail.catch(() => {}).then(() => context.run(true, task));
        tail = next.catch(() => {});
        return next;
    }
    async function persist(state, preserveBackup = true) {
        const text = JSON.stringify(state, null, 2) + "\n";
        await io.mkdir(path.dirname(file), { recursive: true });
        if (preserveBackup) {
            const existing = await io.readFile(file, "utf8").catch(error => { if (error.code === "ENOENT") return ""; throw error; });
            if (existing) { JSON.parse(existing); await io.writeFile(backupFile, existing, "utf8"); }
        }
        const temporary = `${file}.${process.pid}.tmp`;
        await io.writeFile(temporary, text, "utf8");
        await io.rename(temporary, file);
    }
    async function load() {
        const content = await io.readFile(file, "utf8").catch(error => { if (error.code === "ENOENT") return ""; throw error; });
        if (!content) { const initial = normalize(defaults()); await persist(initial, false); return initial; }
        try { return normalize(JSON.parse(content)); }
        catch (error) {
            if (!(error instanceof SyntaxError || error instanceof TypeError)) throw error;
            await io.copyFile(file, `${file}.corrupt-${Date.now()}`);
            let recovered;
            try { recovered = normalize(JSON.parse(await io.readFile(backupFile, "utf8"))); }
            catch (backupError) {
                if (backupError.code !== "ENOENT" && !(backupError instanceof SyntaxError || backupError instanceof TypeError)) throw backupError;
                recovered = normalize(defaults());
            }
            await persist(recovered, false);
            return recovered;
        }
    }
    return {
        run,
        read: () => context.getStore() ? load() : run(load),
        write: state => {
            if (!context.getStore()) throw new Error("Alterações de perfil exigem uma transação.");
            return persist(state);
        },
    };
}

/** A delayed offline update must not replace a more recent viewing position. */
export function progressWriteTime(previous, input, now = Date.now()) {
    const supplied = Date.parse(input?.updatedAt || "");
    const observed = Number.isFinite(supplied) ? Math.min(supplied, now) : now;
    const prior = Date.parse(previous?.updatedAt || "");
    return { stale: Number.isFinite(prior) && observed < prior, updatedAt: new Date(observed).toISOString() };
}

export function mergeImportedProfileState(current, imported) {
    const progress = { ...current.progress };
    for (const [key, value] of Object.entries(imported.progress || {})) {
        if (!progress[key] || !progressWriteTime(progress[key], value).stale) progress[key] = value;
    }
    const history = new Map();
    for (const entry of [...(imported.history || []), ...(current.history || [])].sort((a, b) => (Date.parse(a.lastWatchedAt) || 0) - (Date.parse(b.lastWatchedAt) || 0))) history.set(entry.mediaKey, entry);
    return { ...current, progress, favorites: [...new Set([...current.favorites, ...imported.favorites])],
        completed: [...new Set([...current.completed, ...imported.completed])], history: [...history.values()].reverse().slice(0, 500),
        preferences: { ...imported.preferences, ...current.preferences }, updatedAt: new Date().toISOString() };
}
