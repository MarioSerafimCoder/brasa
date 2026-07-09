const DEFAULT_LANGUAGE = "pt-BR";

export function movieImageUrl(movie, {
    type = "poster",
    size,
    fallback = ""
} = {}) {
    return tmdbImageUrl({
        media: "movie",
        type,
        size,
        fallback,
        tmdbId: movie?.tmdbId,
        imdbId: movie?.imdbId,
        title: movie?.originalTitle || movie?.title,
        year: movie?.year
    });
}

export function seriesImageUrl(series, {
    type = "poster",
    size,
    fallback = ""
} = {}) {
    return tmdbImageUrl({
        media: "tv",
        type,
        size,
        fallback,
        tmdbId: series?.tmdbId,
        imdbId: series?.imdbId,
        title: series?.originalTitle || series?.title,
        year: series?.year
    });
}

export function tmdbImageFallbackAttributes(fallback) {
    if (!fallback) return "";
    return ` data-fallback-src="${escapeAttribute(fallback)}"`;
}

export function installTmdbImageFallbacks(root = document) {
    const target = root === document ? document : root;

    if (target.__brasaTmdbFallbacksInstalled) return;

    target.__brasaTmdbFallbacksInstalled = true;
    target.addEventListener("error", (event) => {
        const image = event.target;

        if (!image || image.tagName !== "IMG") return;

        const fallback = image.dataset.fallbackSrc;

        if (!fallback || image.dataset.fallbackApplied === "true") return;

        image.dataset.fallbackApplied = "true";
        image.src = fallback;
    }, true);
}

function tmdbImageUrl({
    media,
    type,
    size,
    fallback,
    tmdbId,
    imdbId,
    title,
    year
}) {
    if (!canUseTmdb() || (!tmdbId && !imdbId && !title)) {
        return fallback || "";
    }

    const params = new URLSearchParams({
        media,
        type,
        language: DEFAULT_LANGUAGE,
        size: size || (type === "backdrop" ? "w1280" : "w780")
    });

    if (tmdbId) params.set("tmdbId", tmdbId);
    if (imdbId) params.set("imdbId", imdbId);
    if (title) params.set("title", title);
    if (year) params.set("year", String(year));

    return `/api/tmdb/image?${params.toString()}`;
}

function canUseTmdb() {
    return typeof window !== "undefined" && window.location.protocol !== "file:";
}

function escapeAttribute(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
