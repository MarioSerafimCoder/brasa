import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const moviesDir = path.join(rootDir, "assets", "movies");
const postersDir = path.join(rootDir, "assets", "posters");
const subtitlesDir = path.join(rootDir, "assets", "subtitles");
const dataFile = path.join(rootDir, "data", "movies.js");
const overridesFile = path.join(rootDir, "data", "movie-overrides.json");
const envFile = path.join(rootDir, ".env");

const videoExtensions = new Set([".mp4", ".mkv", ".webm", ".mov", ".avi"]);
const args = new Set(process.argv.slice(2));
const isDryRun = args.has("--dry-run");

await main();

async function main() {
    await loadEnv();
    await syncMovies();
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

                const poster = await resolvePoster(omdb, file.name);
                const movie = buildMovie({
                    id: nextId++,
                    fileName: file.name,
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
    return entries
        .filter((entry) => entry.isFile() && videoExtensions.has(path.extname(entry.name).toLowerCase()))
        .map((entry) => ({ name: entry.name }))
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

function buildMovie({ id, fileName, omdb, parsed, override, poster }) {
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
