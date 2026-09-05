function numberOrNull(value) {
    if (value === null || value === undefined || String(value).trim() === "") return null;
    const parsed = Number(String(value).trim().replace(",", "."));
    return Number.isFinite(parsed) ? parsed : null;
}

function integerOrNull(value) {
    const parsed = numberOrNull(value);
    return parsed === null ? null : Math.trunc(parsed);
}

function stringValue(value) {
    return value === null || value === undefined ? "" : String(value);
}

function booleanValue(value) {
    return value === true || String(value).trim().toLowerCase() === "true";
}

function stringList(value) {
    return Array.isArray(value) ? value.map(stringValue).map((item) => item.trim()).filter(Boolean) : [];
}

export function normalizeTvProgress(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    return {
        mediaType: stringValue(value.mediaType) || "movie",
        mediaId: stringValue(value.mediaId),
        seriesId: stringValue(value.seriesId),
        currentTime: numberOrNull(value.currentTime) ?? 0,
        duration: numberOrNull(value.duration) ?? 0,
        percentage: Math.min(100, Math.max(0, numberOrNull(value.percentage) ?? 0)),
        completed: booleanValue(value.completed),
        updatedAt: stringValue(value.updatedAt)
    };
}

export function normalizeTvProfile(value) {
    const profile = value && typeof value === "object" && !Array.isArray(value) ? value : {};
    const avatar = profile.avatar && typeof profile.avatar === "object" && !Array.isArray(profile.avatar)
        ? { type: stringValue(profile.avatar.type) || "initials", value: stringValue(profile.avatar.value), color: stringValue(profile.avatar.color) || "orange" }
        : null;
    return {
        ...profile,
        id: stringValue(profile.id),
        name: stringValue(profile.name),
        initials: stringValue(profile.initials),
        kind: stringValue(profile.kind) || "adult",
        maxContentRating: integerOrNull(profile.maxContentRating),
        hasPin: booleanValue(profile.hasPin),
        avatar
    };
}

export function normalizeTvCatalogItem(value) {
    const item = value && typeof value === "object" && !Array.isArray(value) ? value : {};
    const subtitles = Array.isArray(item.subtitles) ? item.subtitles.map((track) => ({
        label: stringValue(track?.label),
        srclang: stringValue(track?.srclang),
        src: stringValue(track?.src),
        mimeType: stringValue(track?.mimeType) || "text/vtt",
        default: booleanValue(track?.default)
    })) : [];
    const seasons = Array.isArray(item.seasons) ? item.seasons.map((season) => ({
        seasonNumber: integerOrNull(season?.seasonNumber) ?? 0,
        episodes: Array.isArray(season?.episodes) ? season.episodes.map(normalizeTvCatalogItem) : []
    })) : [];
    return {
        ...item,
        id: stringValue(item.id),
        mediaKey: stringValue(item.mediaKey),
        type: stringValue(item.type) || "movie",
        title: stringValue(item.title),
        originalTitle: stringValue(item.originalTitle),
        year: integerOrNull(item.year),
        duration: stringValue(item.duration),
        rating: numberOrNull(item.rating),
        contentRating: stringValue(item.contentRating),
        genres: stringList(item.genres),
        overview: stringValue(item.overview),
        poster: stringValue(item.poster),
        backdrop: stringValue(item.backdrop),
        addedAt: stringValue(item.addedAt),
        favorite: booleanValue(item.favorite),
        progress: normalizeTvProgress(item.progress),
        streamUrl: stringValue(item.streamUrl),
        seriesId: stringValue(item.seriesId),
        seasonNumber: integerOrNull(item.seasonNumber),
        episodeNumber: integerOrNull(item.episodeNumber),
        seasons,
        subtitles
    };
}

export function normalizeTvProgressMap(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    return Object.fromEntries(Object.entries(value).map(([key, progress]) => [key, normalizeTvProgress(progress)]).filter(([, progress]) => progress));
}

export const tvContractNumber = numberOrNull;
export const tvContractInteger = integerOrNull;
