import fs from "node:fs/promises";
import path from "node:path";
import { isProcessableVideo, VIDEO_EXTENSIONS } from "./library-config.mjs";
import { normalizePath } from "./sync-normalization.mjs";

export function createMovieScanner({ rootDir, movieSources }) {
  const videoExtensions = new Set(VIDEO_EXTENSIONS);
  const toAssetPath = (absolutePath) =>
    path.relative(rootDir, absolutePath).replace(/\\/g, "/");
  async function inspectMovieSources() {
    const availability = new Map();
    for (const source of movieSources) {
      const key = normalizePath(toAssetPath(source.dir));
      try {
        const stat = await fs.stat(source.dir);
        if (!stat.isDirectory()) throw new Error("a origem não é uma pasta");
        await fs.readdir(source.dir);
        availability.set(key, true);
      } catch {
        availability.set(key, false);
        console.log(
          `BRasa: fonte de filmes indisponível (${toAssetPath(source.dir)}). O catálogo anterior será preservado.`,
        );
      }
    }
    return availability;
  }

  function resolveMovieAvailability(
    movie,
    {
      availablePaths = new Set(),
      emptyPaths = new Set(),
      sourceAvailability = new Map(),
    } = {},
  ) {
    const video = normalizePath(movie?.video || "");
    const source = [...sourceAvailability.entries()].find(
      ([prefix]) => video === prefix || video.startsWith(`${prefix}/`),
    );
    if (source?.[1] === false)
      return { fileStatus: "source-offline", playable: false };
    const available = availablePaths.has(video);
    const empty = emptyPaths.has(video);
    return {
      fileStatus: available
        ? "available"
        : empty
          ? "empty-file"
          : "missing-file",
      playable: available,
    };
  }

  async function listVideoFiles(sourceAvailability) {
    const withStats = [];
    for (const source of movieSources) {
      if (
        sourceAvailability.get(normalizePath(toAssetPath(source.dir))) === false
      )
        continue;
      const entries = await fs.readdir(source.dir, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isFile()) continue;
        const absolutePath = path.join(source.dir, entry.name),
          stats = await fs.stat(absolutePath);
        if (!isProcessableVideo(entry.name, stats.size)) continue;
        withStats.push({
          name: entry.name,
          mtime: stats.mtime,
          size: stats.size,
          assetPath: toAssetPath(absolutePath),
          audience: source.audience,
        });
      }
    }

    return withStats.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
  }

  async function listEmptyVideoFiles(sourceAvailability) {
    const found = [];
    for (const source of movieSources) {
      if (
        sourceAvailability.get(normalizePath(toAssetPath(source.dir))) === false
      )
        continue;
      const entries = await fs.readdir(source.dir, { withFileTypes: true });
      for (const entry of entries) {
        if (
          !entry.isFile() ||
          !videoExtensions.has(path.extname(entry.name).toLowerCase())
        )
          continue;
        const absolutePath = path.join(source.dir, entry.name);
        const stats = await fs.stat(absolutePath);
        if (stats.size !== 0) continue;
        found.push({
          name: entry.name,
          mtime: stats.mtime,
          size: 0,
          assetPath: toAssetPath(absolutePath),
          audience: source.audience,
        });
      }
    }
    return found.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
  }

  return {
    inspectMovieSources,
    listVideoFiles,
    listEmptyVideoFiles,
    resolveMovieAvailability,
  };
}
