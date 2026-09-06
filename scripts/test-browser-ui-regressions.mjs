import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { parseMovieFileName } from "./sync-movies.mjs";
import { getMovies } from "../data/movies.js";
import { collections } from "../data/collections.js";
import { matchesSystemCollection } from "../js/services/collections-service.js";

for (const [file, title] of [
    ["A.Nova.Onda.do.Imperador.6CH-WWW.BLUDV.COM.2000.mkv", "A Nova Onda do Imperador"],
    ["COMANDO.TO-Madagascar.2.A.Grande.Escapada.2008.1080p.mkv", "Madagascar 2 A Grande Escapada"],
    ["F1.O.Filme.FULLHD-SF.2025.mkv", "F1 O Filme"]
]) {
    assert.equal(parseMovieFileName(file).title, title);
}

const [movieCard, styles, images, sync, seriesPage, seriesHtml, profiles] = await Promise.all([
    fs.readFile("components/home/movie-card.js", "utf8"),
    fs.readFile("css/style.css", "utf8"),
    fs.readFile("js/utils/tmdb-images.js", "utf8"),
    fs.readFile("scripts/sync-movies.mjs", "utf8"),
    fs.readFile("js/pages/series.js", "utf8"),
    fs.readFile("pages/series.html", "utf8"),
    fs.readFile("js/utils/profiles.js", "utf8")
]);

assert.match(movieCard, /showQuality/);
assert.match(styles, /flex:0 0 var\(--movie-width\)/);
assert.match(styles, /is-image-unavailable/);
assert.match(images, /classList\.add\("is-image-unavailable"\)/);
assert.match(sync, /movie\.fileStatus !== "missing-file"/);
assert.match(seriesPage, /history\.pushState/);
assert.match(seriesPage, /window\.addEventListener\("popstate"/);
assert.match(seriesPage, /seriesGrid\.hidden = isDetailView/);
assert.match(seriesPage, /data-series-back/);
assert.match(seriesPage, /data-season-number/);
assert.match(seriesPage, /episode-card__overview/);
assert.match(seriesPage, /moveBetweenSeasons/);
assert.match(seriesHtml, /id="seriesHeader"/);
assert.match(seriesHtml, /id="seriesDetail" class="series-detail" hidden/);
assert.match(profiles, /let selecting=false/);
assert.match(profiles, /requestExitPin\(active,next\)/);
assert.match(profiles, /Abrir o perfil \$\{nextProfile\?\.name\|\|"adulto"\}/);
assert.match(profiles, /autocomplete="new-password"/);

const fastCollection = collections.find((collection) => collection.id === "fast-furious");
const rockyCollection = collections.find((collection) => collection.id === "rocky");
const collectionCatalog = getMovies().filter((movie) => movie.fileStatus === "available" || movie.fileStatus === "empty-file");
const fastMovies = collectionCatalog.filter((movie) => matchesSystemCollection(movie, fastCollection));
const rockyMovies = collectionCatalog.filter((movie) => matchesSystemCollection(movie, rockyCollection));
assert.ok(fastMovies.some((movie) => movie.imdbId === "tt0232500"));
assert.ok(rockyMovies.some((movie) => movie.imdbId === "tt0075148"));
assert.equal(fastMovies.some((movie) => movie.title === "Kung Fu Panda"), false);
assert.ok(rockyMovies.some((movie) => movie.playable !== false));

console.log("Interface web: nomes, cartões, capas e filtros aprovados.");
