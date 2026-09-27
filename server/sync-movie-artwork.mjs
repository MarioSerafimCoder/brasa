import fs from "node:fs/promises";
import path from "node:path";
import { slugify, fileExists } from "./sync-normalization.mjs";

export function createMovieArtwork({
  rootDir,
  postersDir,
  backdropsDir,
  isDryRun,
}) {
  async function resolveMovieArtwork({ omdb, tmdb, fileName }) {
    let poster = "",
      backdrop = "";
    if (tmdb?.poster_path) {
      console.log(`BRasa: baixando poster de "${fileName}"...`);
      poster = await downloadTmdbImage(
        tmdb.poster_path,
        "poster",
        omdb.Title,
        omdb.Year,
      );
    }
    if (!poster) poster = await resolvePoster(omdb, fileName);
    if (tmdb?.backdrop_path) {
      console.log(`BRasa: baixando backdrop de "${fileName}"...`);
      backdrop = await downloadTmdbImage(
        tmdb.backdrop_path,
        "backdrop",
        omdb.Title,
        omdb.Year,
      );
    }
    return { poster, backdrop };
  }

  async function downloadTmdbImage(imagePath, type, title, year) {
    const relativePath = `assets/${type === "poster" ? "posters" : "backdrops"}/${slugify(`${title}-${year || ""}-${type}`)}.jpg`,
      absolutePath = path.join(rootDir, relativePath);
    if ((await fileExists(absolutePath)) || isDryRun) return relativePath;
    const response = await fetch(
        `https://image.tmdb.org/t/p/${type === "poster" ? "w780" : "w1280"}${imagePath}`,
      ),
      mime = response.headers.get("content-type") || "";
    if (!response.ok || !mime.startsWith("image/")) return "";
    await fs.mkdir(type === "poster" ? postersDir : backdropsDir, {
      recursive: true,
    });
    await fs.writeFile(absolutePath, Buffer.from(await response.arrayBuffer()));
    return relativePath;
  }

  async function resolvePoster(omdb, fileName) {
    if (!omdb.Poster || omdb.Poster === "N/A") return "";

    const slug = slugify(`${omdb.Title}-${omdb.Year || ""}`);
    const extension = path.extname(new URL(omdb.Poster).pathname) || ".jpg";
    const relativePath = `assets/posters/${slug}${extension}`;
    const absolutePath = path.join(rootDir, relativePath);

    if (await fileExists(absolutePath)) return relativePath;
    if (isDryRun) return relativePath;

    const response = await fetch(omdb.Poster);
    const mime = response.headers.get("content-type") || "";
    if (!response.ok || !mime.startsWith("image/")) {
      console.log(`BRasa: nao consegui baixar poster de "${fileName}".`);
      return "";
    }

    const bytes = Buffer.from(await response.arrayBuffer());
    await fs.mkdir(postersDir, { recursive: true });
    await fs.writeFile(absolutePath, bytes);
    return relativePath;
  }

  return { resolveMovieArtwork, downloadTmdbImage, resolvePoster };
}
