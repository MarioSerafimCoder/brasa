import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

export function createTvLibraryCache(rootDir, { checkIntervalMs = 500, now = () => Date.now(), importModule = (url) => import(url) } = {}) {
    const files = {
        movies: path.join(rootDir, "data", "movies.js"),
        series: path.join(rootDir, "data", "series.js"),
        collections: path.join(rootDir, "data", "collections.js")
    };
    let checkedAt = 0, signature = "", value = null, loading = null;

    async function load() {
        const time = now();
        if (value && time - checkedAt < checkIntervalMs) return value;
        if (loading) return loading;
        loading = (async () => {
            checkedAt = time;
            const stats = await Promise.all(Object.values(files).map((file) => fs.stat(file)));
            const nextSignature = stats.map((stat) => `${stat.size}:${Math.round(stat.mtimeMs)}`).join("|");
            if (value && nextSignature === signature) return value;
            const [movieModule, seriesModule, collectionModule] = await Promise.all(
                Object.values(files).map((file, index) => importModule(`${pathToFileURL(file).href}?v=${encodeURIComponent(`${nextSignature}-${index}`)}`))
            );
            const movies = movieModule.getMovies(), series = seriesModule.getSeries();
            const movieById = new Map(movies.map((item) => [String(item.id), item]));
            const episodeRecords = series.flatMap((parent) => (parent.seasons || []).flatMap((season) =>
                (season.episodes || []).map((episode) => ({ episode, series: parent }))
            ));
            const episodeById = new Map(episodeRecords.map((record) => [String(record.episode.id), record]));
            signature = nextSignature;
            value = { movies, series, collections: collectionModule.collections || [], movieById, episodeById, loadedAt: time };
            return value;
        })().finally(() => { loading = null; });
        return loading;
    }

    function invalidate() { checkedAt = 0; signature = ""; }
    return { load, invalidate };
}
