import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const moviesDir = path.join(rootDir, "assets", "movies");
const seriesDirs = [
    { dir: path.join(rootDir, "assets", "series"), kids: false },
    { dir: path.join(rootDir, "assets", "kids-series"), kids: true }
];
const postersDir = path.join(rootDir, "assets", "posters");
const seriesPostersDir = path.join(rootDir, "assets", "series-posters");
const subtitlesDir = path.join(rootDir, "assets", "subtitles");
const dataFile = path.join(rootDir, "data", "movies.js");
const seriesDataFile = path.join(rootDir, "data", "series.js");
const overridesFile = path.join(rootDir, "data", "movie-overrides.json");
const envFile = path.join(rootDir, ".env");

const videoExtensions = new Set([".mp4", ".mkv", ".webm", ".mov", ".avi"]);
const args = new Set(process.argv.slice(2));
const isDryRun = args.has("--dry-run");

await main();

async function main() {
    await loadEnv();
    await syncMovies();
    await syncSeries();
}

async function syncMovies() {
    try {
        const omdbApiKey = getArgValue("--api-key") || process.env.OMDB_API_KEY;
        const openSubtitlesApiKey = getArgValue("--opensubtitles-api-key") || process.env.OPENSUBTITLES_API_KEY;
        const subtitleLanguages = parseSubtitleLanguages(process.env.SUBTITLE_LANGUAGES || "pt-br,en");
        const movies = await loadMovies();
        const overrides = await loadOverrides();
        const videoFiles = await listVideoFiles();
        const knownVideos = new Map(
            movies
                .filter((movie) => movie.video)
                .map((movie) => [normalizePath(movie.video), movie])
        );

        let changed = false;
        const added = [];

        const newFiles = videoFiles.filter((file) => {
            const videoPath = normalizePath(toAssetPath(path.join(moviesDir, file.name)));
            return !knownVideos.has(videoPath);
        });

        if (newFiles.length && !omdbApiKey) {
            console.log("BRasa: encontrei filmes novos, mas falta OMDB_API_KEY no .env.");
            newFiles.forEach((file) => console.log(`- ${file.name}`));
        }

        if (newFiles.length && omdbApiKey) {
            let nextId = movies.reduce((max, movie) => Math.max(max, Number(movie.id) || 0), 0) + 1;

            for (const file of newFiles) {
                const override = overrides[file.name] || {};
                const parsed = parseMovieFileName(file.name);
                const omdb = await findMovieOnOmdb({ apiKey: omdbApiKey, parsed, override });

                if (!omdb) {
                    console.log(`BRasa: nao encontrei dados na OMDb para "${file.name}".`);
                    console.log("Adicione uma entrada em data/movie-overrides.json para esse arquivo.");
                    continue;
                }

                const normalizedFile = await normalizeMovieFileName({
                    fileName: file.name,
                    title: omdb.Title,
                    year: omdb.Year,
                    imdbId: omdb.imdbID
                });

                const poster = await resolvePoster(omdb, normalizedFile);
                const movie = buildMovie({
                    id: nextId++,
                    fileName: normalizedFile,
                    addedAt: file.mtime?.toISOString?.() || new Date().toISOString(),
                    omdb,
                    parsed,
                    override,
                    poster
                });

                movies.push(movie);
                added.push(movie);
                changed = true;
                console.log(`BRasa: adicionado "${movie.title}" (${movie.year}).`);
            }
        }

        if (!newFiles.length) {
            console.log("BRasa: nenhum filme novo encontrado.");
        }

        const renamedMovies = await normalizeIndexedMovieFiles(movies);

        if (renamedMovies > 0) {
            changed = true;
            console.log(`BRasa: ${renamedMovies} arquivo(s) renomeado(s) para o padrao da biblioteca.`);
        }

        const downloadedSubtitles = await syncSubtitlesForMovies({
            movies,
            apiKey: openSubtitlesApiKey,
            languages: subtitleLanguages
        });

        if (downloadedSubtitles > 0) {
            changed = true;
        }

        if (!changed) {
            console.log("BRasa: nada para atualizar.");
            return;
        }

        if (isDryRun) {
            console.log("BRasa: dry-run ativo; data/movies.js nao foi alterado.");
            return;
        }

        await writeMovies(movies);

        if (added.length) {
            console.log(`BRasa: data/movies.js atualizado com ${added.length} filme(s).`);
        }

        if (downloadedSubtitles > 0) {
            console.log(`BRasa: ${downloadedSubtitles} legenda(s) vinculada(s).`);
        }
    } catch (error) {
        console.error("BRasa: erro ao sincronizar filmes.");
        console.error(error.message);
    }
}

async function normalizeIndexedMovieFiles(movies) {
    let renamed = 0;

    for (const movie of movies) {
        if (!movie.video || !movie.imdbId || !movie.title || !movie.year) continue;

        const currentPath = path.join(rootDir, movie.video);
        if (!(await fileExists(currentPath))) continue;

        const nextFileName = await normalizeMovieFileName({
            fileName: path.basename(movie.video),
            title: movie.originalTitle || movie.title,
            year: movie.year,
            imdbId: movie.imdbId
        });

        const nextVideoPath = toAssetPath(path.join(moviesDir, nextFileName));

        if (normalizePath(movie.video) !== normalizePath(nextVideoPath)) {
            movie.video = nextVideoPath;
            renamed++;
        }
    }

    return renamed;
}

async function normalizeMovieFileName({ fileName, title, year, imdbId }) {
    if (!title || !year || !imdbId) return fileName;

    const extension = path.extname(fileName);
    const targetName = `${sanitizeFileName(title)} (${String(year).match(/\d{4}/)?.[0] || year}) [${imdbId}]${extension}`;

    if (fileName === targetName) return fileName;

    const sourcePath = path.join(moviesDir, fileName);
    if (!(await fileExists(sourcePath))) return fileName;

    const targetNameAvailable = await getAvailableMovieFileName(targetName, fileName);

    if (targetNameAvailable === fileName) return fileName;
    if (isDryRun) return targetNameAvailable;

    await fs.rename(sourcePath, path.join(moviesDir, targetNameAvailable));
    console.log(`BRasa: renomeado "${fileName}" -> "${targetNameAvailable}".`);

    return targetNameAvailable;
}

async function getAvailableMovieFileName(targetName, currentName) {
    const targetPath = path.join(moviesDir, targetName);

    if (targetName === currentName || !(await fileExists(targetPath))) {
        return targetName;
    }

    const extension = path.extname(targetName);
    const base = path.basename(targetName, extension);

    for (let index = 2; index < 100; index++) {
        const candidate = `${base} (${index})${extension}`;
        const candidatePath = path.join(moviesDir, candidate);

        if (candidate === currentName || !(await fileExists(candidatePath))) {
            return candidate;
        }
    }

    return currentName;
}

async function syncSubtitlesForMovies({ movies, apiKey, languages }) {
    const playableMovies = movies.filter((movie) => movie.video && movie.imdbId);

    if (!playableMovies.length) return 0;

    if (!apiKey) {
        console.log("BRasa: OPENSUBTITLES_API_KEY ausente; legendas nao foram baixadas.");
        return 0;
    }

    let downloaded = 0;

    for (const movie of playableMovies) {
        movie.subtitles = Array.isArray(movie.subtitles) ? movie.subtitles : [];

        for (const language of languages) {
            if (movie.subtitles.some((subtitle) => subtitle.srclang === language.code)) continue;

            const subtitleCandidates = await findSubtitles({
                apiKey,
                imdbId: movie.imdbId,
                language: language.searchCode
            });

            if (!subtitleCandidates.length) {
                console.log(`BRasa: nenhuma legenda ${language.label} encontrada para "${movie.title}".`);
                continue;
            }

            let downloadedSubtitle = null;

            for (const subtitle of subtitleCandidates) {
                downloadedSubtitle = await downloadSubtitle({
                    apiKey,
                    fileId: subtitle.fileId,
                    movie,
                    language
                });

                if (downloadedSubtitle) break;
            }

            if (!downloadedSubtitle) {
                console.log(`BRasa: as legendas ${language.label} encontradas para "${movie.title}" vieram vazias.`);
                continue;
            }

            movie.subtitles.push(downloadedSubtitle);
            downloaded++;
            console.log(`BRasa: legenda ${language.label} baixada para "${movie.title}".`);
        }

        movie.subtitles.sort((a, b) => a.label.localeCompare(b.label, "pt-BR"));
        const defaultSubtitle = movie.subtitles.find((subtitle) => subtitle.srclang === "pt-br") || movie.subtitles[0];
        movie.subtitles = movie.subtitles.map((subtitle) => ({
            ...subtitle,
            default: subtitle.src === defaultSubtitle?.src
        }));
    }

    return downloaded;
}

async function findSubtitles({ apiKey, imdbId, language }) {
    const url = new URL("https://api.opensubtitles.com/api/v1/subtitles");
    url.searchParams.set("imdb_id", imdbId);
    url.searchParams.set("languages", language);
    url.searchParams.set("order_by", "download_count");
    url.searchParams.set("order_direction", "desc");

    const data = await fetchOpenSubtitles(apiKey, url);
    return (data.data || [])
        .flatMap((item) => {
            return (item.attributes?.files || []).map((file) => ({
                fileId: file.file_id,
                release: item.attributes?.release || "",
                language: item.attributes?.language || language
            }));
        })
        .filter((subtitle) => subtitle.fileId)
        .slice(0, 8);
}

async function downloadSubtitle({ apiKey, fileId, movie, language }) {
    const response = await fetch("https://api.opensubtitles.com/api/v1/download", {
        method: "POST",
        headers: openSubtitlesHeaders(apiKey),
        body: JSON.stringify({ file_id: fileId })
    });

    const data = await response.json();
    if (!response.ok) {
        const details = data.message || data.error || response.statusText;
        throw new Error(`OpenSubtitles download retornou HTTP ${response.status}. ${details}`);
    }

    if (!data.link) return null;

    const subtitleResponse = await fetch(data.link);
    if (!subtitleResponse.ok) {
        throw new Error(`Nao consegui baixar o arquivo de legenda HTTP ${subtitleResponse.status}.`);
    }

    const rawSubtitle = await subtitleResponse.text();
    const vtt = toWebVtt(rawSubtitle);

    if (!hasSubtitleCue(vtt)) {
        return null;
    }

    const slug = slugify(`${movie.title}-${movie.year}-${language.code}`);
    const relativePath = `assets/subtitles/${slug}.vtt`;
    const absolutePath = path.join(rootDir, relativePath);

    if (!isDryRun) {
        await fs.mkdir(subtitlesDir, { recursive: true });
        await fs.writeFile(absolutePath, vtt, "utf8");
    }

    return {
        label: language.label,
        srclang: language.code,
        src: relativePath,
        default: false
    };
}

async function fetchOpenSubtitles(apiKey, url) {
    const response = await fetch(url, {
        headers: openSubtitlesHeaders(apiKey)
    });

    const data = await response.json();
    if (!response.ok) {
        const details = data.message || data.error || response.statusText;
        throw new Error(`OpenSubtitles retornou HTTP ${response.status}. ${details}`);
    }

    return data;
}

function openSubtitlesHeaders(apiKey) {
    return {
        "Content-Type": "application/json",
        "Api-Key": apiKey,
        "User-Agent": "BRasa v1.0"
    };
}

async function loadEnv() {
    try {
        const content = await fs.readFile(envFile, "utf8");
        for (const line of content.split(/\r?\n/)) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith("#")) continue;
            const [key, ...valueParts] = trimmed.split("=");
            if (!key || process.env[key]) continue;
            process.env[key] = valueParts.join("=").trim();
        }
    } catch (error) {
        if (error.code !== "ENOENT") throw error;
    }
}

async function loadMovies() {
    const content = await fs.readFile(dataFile, "utf8");
    const match = content.match(/const movies = (\[[\s\S]*?\]);/);
    if (!match) {
        throw new Error("Nao consegui localizar o array const movies em data/movies.js.");
    }

    return Function(`"use strict"; return ${match[1]};`)();
}

async function loadOverrides() {
    try {
        return JSON.parse(await fs.readFile(overridesFile, "utf8"));
    } catch (error) {
        if (error.code === "ENOENT") return {};
        throw error;
    }
}

async function listVideoFiles() {
    const entries = await fs.readdir(moviesDir, { withFileTypes: true });
    const files = entries
        .filter((entry) => entry.isFile() && videoExtensions.has(path.extname(entry.name).toLowerCase()))
        .map((entry) => entry.name);

    const withStats = await Promise.all(files.map(async (name) => {
        const stats = await fs.stat(path.join(moviesDir, name));
        return {
            name,
            mtime: stats.mtime
        };
    }));

    return withStats
        .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
}

function parseMovieFileName(fileName) {
    const baseName = path.basename(fileName, path.extname(fileName));
    const yearMatch = baseName.match(/(?:^|[\s[(._-])((?:19|20)\d{2})(?:[\s\])._-]|$)/);
    const year = yearMatch?.[1] || "";

    const clean = baseName
        .replace(/[._]+/g, " ")
        .replace(/\[(?:19|20)\d{2}\]|\((?:19|20)\d{2}\)|(?:19|20)\d{2}/g, " ")
        .replace(/\b(4k|2160p|1080p|720p|bluray|brrip|webrip|web-dl|x264|x265|h264|h265|dublado|legendado|dual|audio|seroes|zoiudo)\b/gi, " ")
        .replace(/\[[^\]]*]|\([^)]*\)/g, " ")
        .replace(/\s+-\s+$/g, " ")
        .replace(/\s+/g, " ")
        .trim();

    const dashParts = clean.split(/\s+-\s+/).map((part) => part.trim()).filter(Boolean);
    const candidates = [
        clean,
        dashParts.join(" "),
        dashParts[0],
        dashParts.at(-1)
    ].filter(Boolean);

    return {
        baseName,
        title: candidates[0] || baseName,
        year,
        candidates: [...new Set(candidates)]
    };
}

async function findMovieOnOmdb({ apiKey, parsed, override }) {
    if (override.imdbId) {
        return fetchOmdb(apiKey, { i: override.imdbId, plot: "full" });
    }

    const titleCandidates = [
        override.title,
        parsed.title,
        ...parsed.candidates
    ].filter(Boolean);

    for (const title of [...new Set(titleCandidates)]) {
        const movie = await fetchOmdb(apiKey, {
            t: title,
            y: override.year || parsed.year,
            type: "movie",
            plot: "full"
        });

        if (movie) return movie;
    }

    for (const title of [...new Set(titleCandidates)]) {
        const search = await fetchOmdb(apiKey, {
            s: title,
            y: override.year || parsed.year,
            type: "movie"
        });

        const best = chooseSearchResult(search?.Search || [], override.year || parsed.year);
        if (best?.imdbID) {
            return fetchOmdb(apiKey, { i: best.imdbID, plot: "full" });
        }
    }

    return null;
}

async function fetchOmdb(apiKey, params) {
    const url = new URL("https://www.omdbapi.com/");
    url.searchParams.set("apikey", apiKey);
    url.searchParams.set("r", "json");

    for (const [key, value] of Object.entries(params)) {
        if (value) url.searchParams.set(key, value);
    }

    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) {
        const details = data.Error ? ` ${data.Error}` : "";
        throw new Error(`OMDb retornou HTTP ${response.status}.${details}`);
    }

    if (data.Response === "False") return null;
    return data;
}

function chooseSearchResult(results, year) {
    if (!results.length) return null;
    if (year) {
        const exactYear = results.find((result) => result.Year === year);
        if (exactYear) return exactYear;
    }
    return results[0];
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
    if (!response.ok) {
        console.log(`BRasa: nao consegui baixar poster de "${fileName}".`);
        return "";
    }

    const bytes = Buffer.from(await response.arrayBuffer());
    await fs.mkdir(postersDir, { recursive: true });
    await fs.writeFile(absolutePath, bytes);
    return relativePath;
}

function buildMovie({ id, fileName, addedAt, omdb, parsed, override, poster }) {
    return {
        id,
        title: override.displayTitle || omdb.Title || parsed.title,
        originalTitle: omdb.Title || parsed.title,
        year: Number(omdb.Year?.match(/\d{4}/)?.[0] || parsed.year || 0),
        duration: formatRuntime(omdb.Runtime),
        rating: Number.parseFloat(omdb.imdbRating) || "",
        contentRating: omdb.Rated && omdb.Rated !== "N/A" ? omdb.Rated : "",
        quality: inferQuality(fileName),
        genres: translateGenres(omdb.Genre),
        overview: omdb.Plot && omdb.Plot !== "N/A" ? omdb.Plot : "",
        poster,
        backdrop: "",
        video: toAssetPath(path.join(moviesDir, fileName)),
        addedAt,
        progress: 0,
        favorite: false,
        featured: false,
        imdbId: omdb.imdbID || "",
        subtitles: []
    };
}

function formatRuntime(runtime) {
    const minutes = Number(runtime?.match(/\d+/)?.[0]);
    if (!minutes) return runtime && runtime !== "N/A" ? runtime : "";
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    if (!hours) return `${rest}min`;
    return `${hours}h ${String(rest).padStart(2, "0")}min`;
}

function inferQuality(fileName) {
    if (/4k|2160p|uhd/i.test(fileName)) return "4K";
    if (/1080p/i.test(fileName)) return "1080p";
    if (/720p/i.test(fileName)) return "720p";
    return "Local";
}

function translateGenres(genreList = "") {
    const dictionary = {
        Action: "Acao",
        Adventure: "Aventura",
        Animation: "Animacao",
        Comedy: "Comedia",
        Crime: "Crime",
        Documentary: "Documentario",
        Drama: "Drama",
        Family: "Familia",
        Fantasy: "Fantasia",
        Horror: "Terror",
        Mystery: "Misterio",
        Romance: "Romance",
        "Sci-Fi": "Ficcao Cientifica",
        Thriller: "Suspense",
        War: "Guerra",
        Western: "Faroeste"
    };

    return genreList
        .split(",")
        .map((genre) => genre.trim())
        .filter((genre) => genre && genre !== "N/A")
        .map((genre) => dictionary[genre] || genre);
}

async function writeMovies(movies) {
    const content = `// ==========================================================
// BRasa
// Movies Repository
// Generated by scripts/sync-movies.mjs
// ==========================================================

const movies = ${JSON.stringify(movies, null, 4)};

/* ==========================================================
   GETTERS
========================================================== */

export function getMovies(){

    return movies;

}

export function getFeaturedMovie(){

    return movies.find(

        movie => movie.featured

    );

}

export function getRecentlyAddedMovies(limit = 4){

    return [...movies]
        .sort((a, b) => {
            const dateA = a.addedAt ? new Date(a.addedAt).getTime() : 0;
            const dateB = b.addedAt ? new Date(b.addedAt).getTime() : 0;

            return (dateB - dateA) || (Number(b.id || 0) - Number(a.id || 0));
        })
        .slice(0, limit);

}

export function getFavorites(){

    return movies.filter(

        movie => movie.favorite

    );

}

export function getContinueWatching(){

    return movies.filter(

        movie => movie.progress > 0

    );

}

export function getAvailableMovies(){

    return movies.filter(

        movie => movie.video

    );

}

export function getMovieById(id){

    return movies.find(

        movie => movie.id === id

    );

}
`;

    await fs.writeFile(dataFile, content, "utf8");
}

async function syncSeries() {
    try {
        const omdbApiKey = getArgValue("--api-key") || process.env.OMDB_API_KEY;
        const series = await buildSeriesLibrary();
        const enriched = await hydrateSeriesMetadata(series, omdbApiKey);

        if (isDryRun) {
            console.log(`BRasa: dry-run ativo; ${enriched.length} serie(s) detectada(s).`);
            return;
        }

        await writeSeries(enriched);
        const episodeCount = enriched.reduce((total, item) => total + item.episodeCount, 0);
        console.log(`BRasa: data/series.js atualizado com ${enriched.length} serie(s) e ${episodeCount} episodio(s).`);
    } catch (error) {
        console.error("BRasa: erro ao indexar series.");
        console.error(error.message);
    }
}

async function hydrateSeriesMetadata(series, apiKey) {
    if (!series.length) return series;

    if (!apiKey) {
        console.log("BRasa: OMDB_API_KEY ausente; capas e dados de series nao foram baixados.");
        return series;
    }

    for (const item of series) {
        try {
            const omdb = await findSeriesOnOmdb({ apiKey, title: item.title });

            if (!omdb) {
                console.log(`BRasa: nao encontrei dados de serie na OMDb para "${item.title}".`);
                continue;
            }

            const poster = await resolveSeriesPoster(omdb, item);
            applySeriesMetadata(item, omdb, poster);
            console.log(`BRasa: dados de serie atualizados para "${item.title}".`);
        } catch (error) {
            console.log(`BRasa: nao consegui baixar dados de serie para "${item.title}". ${error.message}`);
        }
    }

    return series;
}

async function findSeriesOnOmdb({ apiKey, title }) {
    const direct = await fetchOmdb(apiKey, {
        t: title,
        type: "series",
        plot: "full"
    });

    if (direct) return direct;

    const search = await fetchOmdb(apiKey, {
        s: title,
        type: "series"
    });

    const best = chooseSearchResult(search?.Search || []);
    if (!best?.imdbID) return null;

    return fetchOmdb(apiKey, {
        i: best.imdbID,
        plot: "full"
    });
}

function applySeriesMetadata(item, omdb, poster) {
    item.title = omdb.Title && omdb.Title !== "N/A" ? omdb.Title : item.title;
    item.originalTitle = omdb.Title && omdb.Title !== "N/A" ? omdb.Title : item.title;
    item.year = omdb.Year && omdb.Year !== "N/A" ? omdb.Year : "";
    item.rating = Number.parseFloat(omdb.imdbRating) || "";
    item.contentRating = omdb.Rated && omdb.Rated !== "N/A" ? omdb.Rated : item.contentRating;
    item.genres = translateGenres(omdb.Genre);
    item.overview = omdb.Plot && omdb.Plot !== "N/A" ? omdb.Plot : item.overview;
    item.poster = poster || item.poster;
    item.imdbId = omdb.imdbID || "";
}

async function resolveSeriesPoster(omdb, item) {
    if (!omdb.Poster || omdb.Poster === "N/A") return "";

    const slug = slugify(`${omdb.Title || item.title}-${omdb.Year || "series"}`);
    const extension = path.extname(new URL(omdb.Poster).pathname) || ".jpg";
    const relativePath = `assets/series-posters/${slug}${extension}`;
    const absolutePath = path.join(rootDir, relativePath);

    if (await fileExists(absolutePath)) return relativePath;
    if (isDryRun) return relativePath;

    const response = await fetch(omdb.Poster);
    if (!response.ok) {
        console.log(`BRasa: nao consegui baixar poster de serie "${item.title}".`);
        return "";
    }

    const bytes = Buffer.from(await response.arrayBuffer());
    await fs.mkdir(seriesPostersDir, { recursive: true });
    await fs.writeFile(absolutePath, bytes);
    return relativePath;
}

async function buildSeriesLibrary() {
    const groups = new Map();

    for (const source of seriesDirs) {
        const files = await listSeriesVideoFiles(source.dir);

        for (const file of files) {
            const parsed = parseSeriesFile(file, source);
            const key = `${source.kids ? "kids" : "series"}:${slugify(parsed.seriesTitle)}`;

            if (!groups.has(key)) {
                groups.set(key, {
                    id: slugify(parsed.seriesTitle),
                    title: parsed.seriesTitle,
                    type: "series",
                    kids: source.kids,
                    genres: [],
                    contentRating: source.kids ? "Livre" : "",
                    poster: "",
                    backdrop: "",
                    overview: "Serie local adicionada a biblioteca do BRasa.",
                    addedAt: parsed.addedAt,
                    seasons: []
                });
            }

            const series = groups.get(key);
            if (new Date(parsed.addedAt) > new Date(series.addedAt)) {
                series.addedAt = parsed.addedAt;
            }

            let season = series.seasons.find((item) => item.seasonNumber === parsed.seasonNumber);
            if (!season) {
                season = {
                    seasonNumber: parsed.seasonNumber,
                    episodes: []
                };
                series.seasons.push(season);
            }

            season.episodes.push(parsed.episode);
        }
    }

    const series = Array.from(groups.values())
        .map((item) => {
            item.seasons.sort((a, b) => a.seasonNumber - b.seasonNumber);

            item.seasons.forEach((season) => {
                season.episodes.sort((a, b) => a.episodeNumber - b.episodeNumber || a.title.localeCompare(b.title, "pt-BR"));
            });

            const flatEpisodes = item.seasons.flatMap((season) => season.episodes);
            flatEpisodes.forEach((episode, index) => {
                episode.nextEpisodeId = flatEpisodes[index + 1]?.id || "";
            });

            item.seasonCount = item.seasons.length;
            item.episodeCount = flatEpisodes.length;
            return item;
        })
        .filter((item) => item.episodeCount > 0)
        .sort((a, b) => new Date(b.addedAt) - new Date(a.addedAt) || a.title.localeCompare(b.title, "pt-BR"));

    return series;
}

async function listSeriesVideoFiles(baseDir) {
    if (!(await fileExists(baseDir))) return [];

    const found = [];

    async function walk(currentDir) {
        const entries = await fs.readdir(currentDir, { withFileTypes: true });

        for (const entry of entries) {
            const fullPath = path.join(currentDir, entry.name);

            if (entry.isDirectory()) {
                await walk(fullPath);
                continue;
            }

            if (!entry.isFile() || !videoExtensions.has(path.extname(entry.name).toLowerCase())) continue;

            const stats = await fs.stat(fullPath);
            found.push({
                absolutePath: fullPath,
                relativePath: path.relative(baseDir, fullPath),
                name: entry.name,
                mtime: stats.mtime
            });
        }
    }

    await walk(baseDir);
    return found.sort((a, b) => a.relativePath.localeCompare(b.relativePath, "pt-BR"));
}

function parseSeriesFile(file, source) {
    const parts = file.relativePath.split(path.sep);
    const fileName = parts.at(-1);
    const baseName = path.basename(fileName, path.extname(fileName));
    const folderTitle = parts.length > 1 ? parts[0] : baseName;
    const seasonNumber = parseSeasonNumber(file.relativePath) || 1;
    const episodeNumber = parseEpisodeNumber(file.relativePath) || 1;
    const seriesTitle = cleanSeriesTitle(folderTitle || baseName);
    const episodeTitle = cleanEpisodeTitle(baseName, episodeNumber);
    const episodeId = slugify(`${source.kids ? "kids" : "series"}-${seriesTitle}-s${seasonNumber}-e${episodeNumber}-${baseName}`);

    return {
        seriesTitle,
        seasonNumber,
        addedAt: file.mtime.toISOString(),
        episode: {
            id: episodeId,
            seriesId: slugify(seriesTitle),
            seasonNumber,
            episodeNumber,
            title: episodeTitle,
            video: toAssetPath(file.absolutePath),
            quality: inferQuality(fileName),
            addedAt: file.mtime.toISOString(),
            progress: 0,
            subtitles: []
        }
    };
}

function parseSeasonNumber(value) {
    const normalized = value.replace(/\\/g, "/");
    const patterns = [
        /s(\d{1,2})\s*e\d{1,3}/i,
        /t(\d{1,2})\s*e\d{1,3}/i,
        /(\d{1,2})x\d{1,3}/i,
        /(?:season|temporada)\s*(\d{1,2})/i,
        /(\d{1,2})\s*(?:ª|a|º|o)?\s*temporada/i
    ];

    for (const pattern of patterns) {
        const match = normalized.match(pattern);
        if (match) return Number(match[1]);
    }

    return 0;
}

function parseEpisodeNumber(value) {
    const normalized = value.replace(/\\/g, "/");
    const patterns = [
        /s\d{1,2}\s*e(\d{1,3})/i,
        /t\d{1,2}\s*e(\d{1,3})/i,
        /\d{1,2}x(\d{1,3})/i,
        /(?:episode|episodio|episodio|ep)\s*(\d{1,3})/i,
        /(?:^|[^\d])e(\d{1,3})(?:[^\d]|$)/i
    ];

    for (const pattern of patterns) {
        const match = normalized.match(pattern);
        if (match) return Number(match[1]);
    }

    return 0;
}

function cleanSeriesTitle(value) {
    const cleaned = String(value)
        .replace(/[._]+/g, " ")
        .replace(/\s*[-–—]?\s*(?:season|temporada)\s*\d{1,2}\b.*$/i, "")
        .replace(/\s*[-–—]?\s*\d{1,2}\s*(?:ª|a|º|o)?\s*temporada\b.*$/i, "")
        .replace(/\s+/g, " ")
        .trim();

    return cleaned || "Serie";
}

function cleanEpisodeTitle(value, episodeNumber) {
    const cleaned = String(value)
        .replace(/[._]+/g, " ")
        .replace(/\bs\d{1,2}\s*e\d{1,3}\b/gi, " ")
        .replace(/\bt\d{1,2}\s*e\d{1,3}\b/gi, " ")
        .replace(/\b\d{1,2}x\d{1,3}\b/gi, " ")
        .replace(/\b(?:episode|episodio|ep)\s*\d{1,3}\b/gi, " ")
        .replace(/\b(4k|2160p|1080p|720p|bluray|brrip|webrip|web-dl|x264|x265|h264|h265|dublado|legendado|dual|audio)\b/gi, " ")
        .replace(/\[[^\]]*]|\([^)]*\)/g, " ")
        .replace(/\s+/g, " ")
        .trim();

    return cleaned || `Episodio ${episodeNumber}`;
}

async function writeSeries(series) {
    const content = `// ==========================================================
// BRasa
// Series Repository
// Generated by scripts/sync-movies.mjs
// ==========================================================

const seriesData = ${JSON.stringify(series, null, 4)};

function toArray(value){
    if (Array.isArray(value)) return value;
    return value ? [value] : [];
}

const series = toArray(seriesData).map((item) => ({
    ...item,
    seasons: toArray(item.seasons).map((season) => ({
        ...season,
        episodes: toArray(season.episodes)
    }))
}));

export function getSeries(){
    return series;
}

export function getRecentlyAddedSeries(limit = 12){
    return [...series]
        .sort((a, b) => new Date(b.addedAt) - new Date(a.addedAt))
        .slice(0, limit);
}

export function getSeriesById(id){
    return series.find((item) => String(item.id) === String(id));
}

export function getEpisodeById(id){
    for (const item of series) {
        for (const season of item.seasons || []) {
            const episode = (season.episodes || []).find((candidate) => String(candidate.id) === String(id));

            if (episode) {
                return {
                    ...episode,
                    seriesId: item.id,
                    seriesTitle: item.title,
                    series
                };
            }
        }
    }

    return null;
}
`;

    await fs.writeFile(seriesDataFile, content, "utf8");
}

function parseSubtitleLanguages(value) {
    const labels = {
        "pt-br": "Portugues (Brasil)",
        pt: "Portugues",
        en: "English",
        es: "Espanol"
    };

    return value
        .split(",")
        .map((language) => language.trim().toLowerCase())
        .filter(Boolean)
        .map((language) => ({
            code: language,
            searchCode: language,
            label: labels[language] || language
        }));
}

function toWebVtt(content) {
    const normalized = content
        .replace(/^\uFEFF/, "")
        .replace(/\r\n/g, "\n")
        .replace(/\r/g, "\n")
        .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2");

    if (normalized.trimStart().startsWith("WEBVTT")) {
        return normalized;
    }

    return `WEBVTT\n\n${normalized}`;
}

function hasSubtitleCue(content) {
    return /\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}/.test(content);
}

function toAssetPath(absolutePath) {
    return path.relative(rootDir, absolutePath).replace(/\\/g, "/");
}

function normalizePath(value) {
    return value.replace(/\\/g, "/");
}

function slugify(value) {
    return value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "");
}

function sanitizeFileName(value) {
    const sanitized = String(value)
        .normalize("NFKC")
        .replace(/[<>:"/\\|?*\u0000-\u001F]/g, "")
        .replace(/\s+/g, " ")
        .replace(/[. ]+$/g, "")
        .trim();

    return sanitized || "Filme";
}

async function fileExists(filePath) {
    try {
        await fs.access(filePath);
        return true;
    } catch {
        return false;
    }
}

function getArgValue(name) {
    const prefix = `${name}=`;
    const match = process.argv.find((arg) => arg.startsWith(prefix));
    return match ? match.slice(prefix.length) : "";
}
