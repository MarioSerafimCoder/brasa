import assert from "node:assert/strict";
import { getMovies } from "../data/movies.js";
import { collections } from "../data/collections.js";
import { matchesCollection, resolveTvCollectionMovies } from "../server/tv-collections.mjs";

const movies = getMovies();
const fast = resolveTvCollectionMovies(collections.find((item) => item.id === "fast-furious"), movies);
const rocky = resolveTvCollectionMovies(collections.find((item) => item.id === "rocky"), movies);
const pixar = resolveTvCollectionMovies(collections.find((item) => item.id === "pixar"), movies);
const disney = resolveTvCollectionMovies(collections.find((item) => item.id === "disney-classics"), movies);
const dreamworks = resolveTvCollectionMovies(collections.find((item) => item.id === "dreamworks"), movies);
const marvel = resolveTvCollectionMovies(collections.find((item) => item.id === "mcu"), movies);
const bestPicture = resolveTvCollectionMovies(collections.find((item) => item.id === "best-picture"), movies);
const dc = resolveTvCollectionMovies(collections.find((item) => item.id === "dc"), movies);
const spiderMan = resolveTvCollectionMovies(collections.find((item) => item.id === "spider-man"), movies);
const scienceFiction = resolveTvCollectionMovies(collections.find((item) => item.id === "science-fiction"), movies);
const ghibli = resolveTvCollectionMovies(collections.find((item) => item.id === "ghibli"), movies);

assert.equal(fast.length, 7);
assert.equal(new Set(fast.map((item) => item.imdbId)).size, 7);
assert.deepEqual(fast.map((item) => Number(item.year)), [2001, 2003, 2006, 2009, 2011, 2013, 2015]);
assert.equal(rocky.length, 8);
assert.equal(new Set(rocky.map((item) => `${item.imdbId}:${item.year}`)).size, 8);
assert.ok(rocky.some((item) => item.title.includes("Creed II")));
assert.ok(pixar.length >= 12);
for (const title of ["A Bug's Life", "Inside Out", "Finding Nemo", "Ratatouille", "Soul", "Coco"]) {
    assert.ok(pixar.some((item) => item.title.includes(title)), `${title} deveria estar na coleção Pixar.`);
}
assert.ok(disney.some((item) => item.title === "Aladdin"));
assert.ok(disney.some((item) => item.title === "Mulan"));
assert.ok(!disney.some((item) => item.title.includes("Star Wars")));
assert.ok(dreamworks.some((item) => item.title === "Kung Fu Panda"));
assert.ok(dreamworks.some((item) => item.title === "The Croods"));
assert.ok(!dreamworks.some((item) => item.title.includes("1XBET")));
assert.ok(!dreamworks.some((item) => /S\d{2}E\d{2}/i.test(item.title)));
assert.ok(marvel.some((item) => item.title === "Deadpool & Wolverine"));
assert.ok(marvel.some((item) => item.title === "The Fantastic Four: First Steps"));
assert.ok(bestPicture.some((item) => item.title === "CODA"));
assert.ok(bestPicture.some((item) => item.imdbId === "tt0075148"));
assert.ok(dc.some((item) => item.title === "The Batman"));
assert.ok(dc.some((item) => item.title === "Superman"));
assert.ok(spiderMan.some((item) => item.title === "Spider-Man"));
assert.ok(spiderMan.some((item) => item.imdbId === "tt4633694"), "Into the Spider-Verse deve permanecer na coleção mesmo com ajuste de pontuação no título.");
assert.ok(scienceFiction.length >= 20);
assert.ok(scienceFiction.every((item) => (item.genres || []).some((genre) => genre.toLowerCase() === "ficção científica")));
assert.ok(ghibli.some((item) => item.title === "Spirited Away"));
assert.ok(ghibli.some((item) => item.title === "Howl's Moving Castle"));

const dreamworksCollection = collections.find((item) => item.id === "dreamworks");
assert.equal(matchesCollection({ title: "1XBET COM promo SHREK dinheiro livre" }, dreamworksCollection), false);
assert.equal(matchesCollection({ title: "Shrek 5", year: 2027 }, dreamworksCollection), true);

console.log("Coleções da TV: franquias, estúdios e vencedores do Oscar vinculados sem falsos positivos conhecidos.");
