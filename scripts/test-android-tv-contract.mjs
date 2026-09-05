import assert from "node:assert/strict";
import { normalizeTvCatalogItem, normalizeTvProfile, normalizeTvProgress, normalizeTvProgressMap } from "../server/tv-contract.mjs";
import { getMovies } from "../data/movies.js";
import { getSeries } from "../data/series.js";

const movie = normalizeTvCatalogItem({
    id: 10,
    mediaKey: "movie:10",
    title: "Filme sem nota",
    addedAt: "2026-08-02T23:12:41.399Z",
    year: "2025",
    rating: "",
    genres: ["Ação", ""],
    progress: { currentTime: "15.5", duration: "120", percentage: "12,5", completed: "false" }
});
assert.equal(movie.id, "10");
assert.equal(movie.year, 2025);
assert.equal(movie.rating, null);
assert.equal(movie.addedAt, "2026-08-02T23:12:41.399Z");
assert.deepEqual(movie.genres, ["Ação"]);
assert.equal(movie.progress.currentTime, 15.5);
assert.equal(movie.progress.percentage, 12.5);
assert.equal(movie.progress.completed, false);

const series = normalizeTvCatalogItem({
    id: "avatar",
    type: "series",
    year: "2005",
    rating: "9.2",
    seasons: [{ seasonNumber: "2", episodes: [{ id: 7, seasonNumber: "2", episodeNumber: "11", rating: "inválida" }] }]
});
assert.equal(series.year, 2005);
assert.equal(series.rating, 9.2);
assert.equal(series.seasons[0].seasonNumber, 2);
assert.equal(series.seasons[0].episodes[0].episodeNumber, 11);
assert.equal(series.seasons[0].episodes[0].rating, null);

const profile = normalizeTvProfile({ id: 4, maxContentRating: "10", hasPin: "true" });
assert.equal(profile.id, "4");
assert.equal(profile.maxContentRating, 10);
assert.equal(profile.hasPin, true);
assert.equal(normalizeTvProgress(null), null);
assert.equal(normalizeTvProgressMap({ "movie:10": { percentage: "250" } })["movie:10"].percentage, 100);

const library = [
    ...getMovies().map((item) => normalizeTvCatalogItem({ ...item, mediaKey: `movie:${item.id}`, type: "movie" })),
    ...getSeries().map((item) => normalizeTvCatalogItem({ ...item, mediaKey: `series:${item.id}`, type: "series" }))
];
assert.ok(library.length > 100);
for (const item of library) {
    assert.equal(typeof item.id, "string");
    assert.ok(item.year === null || Number.isInteger(item.year));
    assert.ok(item.rating === null || Number.isFinite(item.rating));
    assert.ok(Array.isArray(item.genres));
    for (const season of item.seasons) {
        assert.ok(Number.isInteger(season.seasonNumber));
        for (const episode of season.episodes) {
            assert.ok(episode.seasonNumber === null || Number.isInteger(episode.seasonNumber));
            assert.ok(episode.episodeNumber === null || Number.isInteger(episode.episodeNumber));
        }
    }
}

console.log(`Contrato Android TV: ${library.length} títulos e seus episódios normalizados.`);
