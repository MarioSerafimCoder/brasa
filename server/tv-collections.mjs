export function resolveTvCollectionMovies(collection, movies) {
    return sortMovies(dedupeMovies((movies || []).filter((movie) => matchesCollection(movie, collection))), collection?.sort);
}

export function matchesCollection(movie, collection) {
    const titles = normalize(`${movie?.title || ""} ${movie?.originalTitle || ""}`);
    if ((collection?.excludePatterns || []).some((pattern) => titles.includes(normalize(pattern)))) return false;
    if ((collection?.imdbIds || []).includes(movie?.imdbId)) return true;
    if ((collection?.titlePatterns || []).some((pattern) => titles.includes(normalize(pattern)))) return true;
    const genres = (movie?.genres || []).map(normalize);
    if ((collection?.genrePatterns || []).some((pattern) => genres.includes(normalize(pattern)))) return true;
    const ignored = new Set(["fast", "classic", "oscar", "marvel"]);
    const keywords = (collection?.keywords || []).filter((keyword) => normalize(keyword).length >= 5 && !ignored.has(normalize(keyword)));
    const searchable = normalize(`${movie?.title || ""} ${movie?.originalTitle || ""} ${movie?.overview || ""} ${(movie?.genres || []).join(" ")}`);
    return keywords.some((keyword) => searchable.includes(normalize(keyword)));
}

function sortMovies(movies, sort = {}) {
    const direction = sort?.direction === "desc" ? -1 : 1;
    const field = sort?.field || "title";
    return [...movies].sort((left, right) => field === "year" || field === "rating"
        ? (Number(left[field] || 0) - Number(right[field] || 0)) * direction
        : String(left[field] || "").localeCompare(String(right[field] || ""), "pt-BR") * direction);
}

function dedupeMovies(movies) {
    const result = [];
    for (const movie of movies) {
        const imdbId = String(movie?.imdbId || "").toLowerCase();
        const video = normalize(movie?.video);
        const title = normalize(movie?.title);
        const year = Number(movie?.year || 0);
        const duplicateIndex = result.findIndex((existing) => {
            if (video && video === normalize(existing?.video)) return true;
            const existingImdb = String(existing?.imdbId || "").toLowerCase();
            if (imdbId && existingImdb) return imdbId === existingImdb;
            const existingTitle = normalize(existing?.title);
            const existingYear = Number(existing?.year || 0);
            const sameYear = !year || !existingYear || year === existingYear;
            return sameYear && title.length >= 5 && existingTitle.length >= 5
                && (title.includes(existingTitle) || existingTitle.includes(title));
        });
        if (duplicateIndex < 0) result.push(movie);
        else if (imdbId && !result[duplicateIndex]?.imdbId) result[duplicateIndex] = movie;
    }
    return result;
}

function normalize(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim(); }
