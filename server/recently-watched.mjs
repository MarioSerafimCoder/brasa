export function sortRecentlyWatched(items = []) {
    return [...items]
        .map((item, index) => ({ item, index, watchedAt: watchTimestamp(item?.progress?.updatedAt) }))
        .filter(({ item }) => item?.progress && Number(item.progress.percentage || 0) > 0)
        .sort((left, right) => right.watchedAt - left.watchedAt || left.index - right.index)
        .map(({ item }) => item);
}

export function preserveWatchTimestamp(value, fallback = new Date().toISOString()) {
    return watchTimestamp(value) > 0 ? String(value) : fallback;
}

function watchTimestamp(value) {
    const parsed = Date.parse(String(value || ""));
    return Number.isFinite(parsed) ? parsed : 0;
}
