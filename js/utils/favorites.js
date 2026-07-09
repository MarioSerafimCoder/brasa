const STORAGE_KEY = "brasa:favorites";

function getFavoriteOverrides() {
    try {
        const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");

        if (Array.isArray(saved)) {
            return Object.fromEntries(saved.map((id) => [String(id), true]));
        }

        return saved && typeof saved === "object" ? saved : {};
    } catch {
        return {};
    }
}

export function isFavorite(movieOrId) {
    const id = typeof movieOrId === "object" ? movieOrId?.id : movieOrId;
    const overrides = getFavoriteOverrides();
    const key = String(id);

    if (Object.hasOwn(overrides, key)) {
        return Boolean(overrides[key]);
    }

    return Boolean(typeof movieOrId === "object" && movieOrId?.favorite);
}

export function toggleFavorite(movieOrId) {
    const id = typeof movieOrId === "object" ? movieOrId?.id : movieOrId;
    const overrides = getFavoriteOverrides();
    const key = String(id);

    if (!id) return false;

    overrides[key] = !isFavorite(movieOrId);

    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(overrides));
    } catch {
        return isFavorite(movieOrId);
    }

    return Boolean(overrides[key]);
}

export function withFavoriteState(movies) {
    return movies.map((movie) => ({
        ...movie,
        favorite: isFavorite(movie)
    }));
}

export function getFavoriteMovies(movies) {
    return withFavoriteState(movies).filter((movie) => movie.favorite);
}
