const STORAGE_PREFIX = "brasa:movie-progress:";
const RESUME_AFTER_SECONDS = 10;
const COMPLETE_BEFORE_END_SECONDS = 20;

export function getWatchProgress(itemOrId) {
    const id = getProgressId(itemOrId);
    if (!id) return null;

    try {
        const saved = JSON.parse(localStorage.getItem(progressKey(id)) || "{}");
        const currentTime = Number(saved.currentTime);
        const duration = Number(saved.duration);

        if (!Number.isFinite(currentTime) || !Number.isFinite(duration) || duration <= 0) {
            return null;
        }

        if (saved.completed || currentTime < RESUME_AFTER_SECONDS || currentTime >= duration - COMPLETE_BEFORE_END_SECONDS) {
            return null;
        }

        return {
            currentTime,
            duration,
            progress: Math.min(99, Math.max(1, Math.round((currentTime / duration) * 100))),
            updatedAt: saved.updatedAt || ""
        };
    } catch {
        clearWatchProgress(id);
        return null;
    }
}

export function saveWatchProgress(itemOrId, currentTime, duration) {
    const id = getProgressId(itemOrId);
    const time = Number(currentTime);
    const total = Number(duration);

    if (!id || !Number.isFinite(time) || !Number.isFinite(total) || total <= 0) return;

    if (time < RESUME_AFTER_SECONDS || time >= total - COMPLETE_BEFORE_END_SECONDS) {
        clearWatchProgress(id);
        return;
    }

    try {
        localStorage.setItem(progressKey(id), JSON.stringify({
            currentTime: time,
            duration: total,
            updatedAt: new Date().toISOString()
        }));
    } catch {
        // Progress is nice to have; playback should never fail because storage did.
    }
}

export function clearWatchProgress(itemOrId) {
    const id = getProgressId(itemOrId);
    if (!id) return;

    try {
        localStorage.removeItem(progressKey(id));
    } catch {
        // Ignore storage failures.
    }
}

export function withProgressState(items = []) {
    return items.map((item) => {
        const saved = getWatchProgress(item);

        if (!saved) {
            return item;
        }

        return {
            ...item,
            progress: saved.progress,
            progressCurrentTime: saved.currentTime,
            progressDuration: saved.duration,
            progressUpdatedAt: saved.updatedAt
        };
    });
}

export function getContinueWatching(items = []) {
    return withProgressState(items)
        .filter((item) => Number(item.progress || 0) > 0)
        .sort((a, b) => {
            const aTime = Date.parse(a.progressUpdatedAt || "") || 0;
            const bTime = Date.parse(b.progressUpdatedAt || "") || 0;
            return bTime - aTime || Number(b.id || 0) - Number(a.id || 0);
        });
}

function progressKey(id) {
    return `${STORAGE_PREFIX}${id}`;
}

function getProgressId(itemOrId) {
    return typeof itemOrId === "object" ? itemOrId?.id : itemOrId;
}
