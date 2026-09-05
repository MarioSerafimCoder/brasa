export function sortRecentlyAdded(items, limit = 36) {
    return (Array.isArray(items) ? items : [])
        .map((item, index) => ({ item, index, timestamp: Date.parse(String(item?.addedAt || "")) }))
        .filter((entry) => Number.isFinite(entry.timestamp))
        .sort((left, right) => right.timestamp - left.timestamp || left.index - right.index)
        .slice(0, Math.max(0, Number(limit) || 0))
        .map((entry) => entry.item);
}
