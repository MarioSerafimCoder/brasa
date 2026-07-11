import path from "node:path";

export const LIBRARY_ROOTS = Object.freeze([
    Object.freeze({ id: "movies", type: "movie", audience: "general", relativePath: "assets/movies" }),
    Object.freeze({ id: "kids-movies", type: "movie", audience: "kids", relativePath: "assets/kids-movies" }),
    Object.freeze({ id: "series", type: "series", audience: "general", relativePath: "assets/series" }),
    Object.freeze({ id: "kids-series", type: "series", audience: "kids", relativePath: "assets/kids-series" })
]);

export const VIDEO_EXTENSIONS = Object.freeze([".mp4", ".mkv", ".webm", ".mov", ".avi"]);
export const TEMPORARY_FILE_PATTERN = /(?:^~)|\.(?:part|tmp|crdownload|download|partial|bak)$/i;

export function absoluteLibraryRoots(rootDir, filter = {}) {
    return LIBRARY_ROOTS.filter((item) => !filter.type || item.type === filter.type).map((item) => ({ ...item, absolutePath: path.resolve(rootDir, item.relativePath) }));
}

export function resolvePathInsideLibrary(rootDir, relativePath, filter = {}) {
    const value = String(relativePath || "");
    if (!value || path.isAbsolute(value) || value.includes("\0")) throw new Error("Caminho de biblioteca inválido.");
    const resolved = path.resolve(rootDir, value);
    const root = absoluteLibraryRoots(rootDir, filter).find((item) => resolved === item.absolutePath || resolved.startsWith(`${item.absolutePath}${path.sep}`));
    if (!root) throw new Error("Caminho fora da biblioteca.");
    return { path: resolved, root, relativePath: path.relative(rootDir, resolved).replace(/\\/g, "/") };
}

export function isProcessableVideo(fileName, size = 1) {
    return Number(size) > 0 && !TEMPORARY_FILE_PATTERN.test(path.basename(String(fileName || ""))) && VIDEO_EXTENSIONS.includes(path.extname(String(fileName || "")).toLowerCase());
}
