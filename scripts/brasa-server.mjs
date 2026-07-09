import http from "node:http";
import fs from "node:fs/promises";
import { createReadStream, rmSync } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
await loadEnvFile();

const host = "127.0.0.1";
const preferredPort = Number(process.env.BRASA_PORT || 4173);
const stateFile = path.join(rootDir, ".brasa-server.json");
const tmdbImageCache = new Map();

let isSyncing = false;
let syncStatus = {
    state: "idle",
    message: "Biblioteca pronta.",
    startedAt: "",
    finishedAt: "",
    output: ""
};

const contentTypes = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".svg": "image/svg+xml; charset=utf-8",
    ".webp": "image/webp",
    ".vtt": "text/vtt; charset=utf-8",
    ".mp4": "video/mp4",
    ".mkv": "video/x-matroska",
    ".webm": "video/webm"
};

const server = http.createServer(async (request, response) => {
    try {
        const url = new URL(request.url, `http://${request.headers.host}`);

        if (request.method === "GET" && url.pathname === "/api/sync/status") {
            sendJson(response, 200, syncStatus);
            return;
        }

        if (request.method === "POST" && url.pathname === "/api/sync") {
            await handleSync(response);
            return;
        }

        if ((request.method === "GET" || request.method === "HEAD") && url.pathname === "/api/tmdb/image") {
            await handleTmdbImage(url, response);
            return;
        }

        if (request.method !== "GET" && request.method !== "HEAD") {
            sendJson(response, 405, { ok: false, message: "Metodo nao permitido." });
            return;
        }

        await serveStatic(url.pathname, request, response);
    } catch (error) {
        sendJson(response, 500, { ok: false, message: error.message });
    }
});

listen(preferredPort);
runStartupSync();

async function loadEnvFile() {
    const envPath = path.join(rootDir, ".env");
    const content = await fs.readFile(envPath, "utf8").catch(() => "");

    content.split(/\r?\n/).forEach((line) => {
        const trimmed = line.trim();

        if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) return;

        const separator = trimmed.indexOf("=");
        const key = trimmed.slice(0, separator).trim();
        let value = trimmed.slice(separator + 1).trim();

        if (!key || process.env[key]) return;

        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.slice(1, -1);
        }

        process.env[key] = value;
    });
}

async function runStartupSync() {
    console.log("BRasa: abrindo com a biblioteca atual e sincronizando em segundo plano...");
    await syncLibrary("Sincronizacao inicial concluida.", "A sincronizacao inicial falhou; usando a biblioteca atual.");
}

function listen(port) {
    server.once("error", (error) => {
        if (error.code === "EADDRINUSE" && port < preferredPort + 100) {
            listen(port + 1);
            return;
        }

        throw error;
    });

    server.listen(port, host, () => {
        writeServerState(port);
        console.log(`BRasa rodando em http://${host}:${port}/`);
    });
}

async function writeServerState(port) {
    await fs.writeFile(
        stateFile,
        JSON.stringify({
            pid: process.pid,
            port,
            url: `http://${host}:${port}/`,
            startedAt: new Date().toISOString()
        }, null, 4),
        "utf8"
    );
}

async function removeServerState() {
    await fs.rm(stateFile, { force: true }).catch(() => {});
}

process.on("exit", () => {
    try {
        rmSync(stateFile, { force: true });
    } catch {
        // Ignore shutdown cleanup failures.
    }
});

process.on("SIGINT", async () => {
    await removeServerState();
    process.exit(0);
});

process.on("SIGTERM", async () => {
    await removeServerState();
    process.exit(0);
});

async function handleSync(response) {
    if (isSyncing) {
        sendJson(response, 409, syncStatus);
        return;
    }

    const result = await syncLibrary("Biblioteca atualizada.", "A atualizacao falhou.");
    sendJson(response, result.code === 0 ? 200 : 500, syncStatus);
}

async function handleTmdbImage(url, response) {
    try {
        const imageUrl = await resolveTmdbImageUrl(url.searchParams);

        if (!imageUrl) {
            response.writeHead(404, {
                "Cache-Control": "no-store"
            });
            response.end();
            return;
        }

        response.writeHead(302, {
            "Location": imageUrl,
            "Cache-Control": "public, max-age=86400"
        });
        response.end();
    } catch (error) {
        console.log(`BRasa: TMDb indisponivel (${error.message}).`);
        response.writeHead(502, {
            "Cache-Control": "no-store"
        });
        response.end();
    }
}

async function resolveTmdbImageUrl(params) {
    const credentials = getTmdbCredentials();

    if (!credentials) return "";

    const cacheKey = params.toString();

    if (tmdbImageCache.has(cacheKey)) {
        return tmdbImageCache.get(cacheKey);
    }

    const media = params.get("media") === "tv" ? "tv" : "movie";
    const type = params.get("type") === "backdrop" ? "backdrop" : "poster";
    const tmdbId = params.get("tmdbId") || "";
    const imdbId = params.get("imdbId") || "";
    const title = params.get("title") || "";
    const year = String(params.get("year") || "").match(/\d{4}/)?.[0] || "";
    const size = params.get("size") || (type === "backdrop" ? "w1280" : "w780");
    const language = params.get("language") || "pt-BR";
    const item = await resolveTmdbItem({ credentials, media, tmdbId, imdbId, title, year, language });

    if (!item?.id) {
        tmdbImageCache.set(cacheKey, "");
        return "";
    }

    const imagePath = await resolveTmdbImagePath({
        credentials,
        media,
        id: item.id,
        type,
        language
    }) || item[type === "backdrop" ? "backdrop_path" : "poster_path"] || item[type === "backdrop" ? "poster_path" : "backdrop_path"] || "";

    const imageUrl = imagePath ? `https://image.tmdb.org/t/p/${encodeURIComponent(size)}${imagePath}` : "";
    tmdbImageCache.set(cacheKey, imageUrl);
    return imageUrl;
}

async function resolveTmdbItem({ credentials, media, tmdbId, imdbId, title, year, language }) {
    if (tmdbId) {
        return fetchTmdb(credentials, `/${media}/${tmdbId}`, { language }).catch(() => null);
    }

    if (imdbId) {
        const found = await fetchTmdb(credentials, `/find/${imdbId}`, {
            external_source: "imdb_id",
            language
        }).catch(() => null);
        const results = media === "tv" ? found?.tv_results : found?.movie_results;

        if (results?.[0]) {
            return results[0];
        }
    }

    if (!title) return null;

    const searchParams = {
        query: title,
        include_adult: "false",
        language
    };

    if (year) {
        searchParams[media === "tv" ? "first_air_date_year" : "year"] = year;
    }

    const search = await fetchTmdb(credentials, `/search/${media}`, searchParams);
    return chooseBestTmdbResult(search?.results || [], title, year, media);
}

async function resolveTmdbImagePath({ credentials, media, id, type, language }) {
    const imageType = type === "backdrop" ? "backdrops" : "posters";
    const languageCode = language.split("-")[0];
    const images = await fetchTmdb(credentials, `/${media}/${id}/images`, {
        include_image_language: `${languageCode},en,null`
    }).catch(() => null);
    const candidates = images?.[imageType] || [];

    if (!candidates.length) return "";

    return candidates
        .map((candidate) => ({
            ...candidate,
            languageScore: getImageLanguageScore(candidate.iso_639_1, languageCode),
            qualityScore: Number(candidate.vote_count || 0) * 2 + Number(candidate.vote_average || 0) + Number(candidate.width || 0) / 1000
        }))
        .sort((a, b) => a.languageScore - b.languageScore || b.qualityScore - a.qualityScore)[0]?.file_path || "";
}

function chooseBestTmdbResult(results, title, year, media) {
    const normalizedTitle = normalizeTmdbText(title);

    return [...results]
        .map((item) => {
            const candidateTitle = media === "tv" ? item.name || item.original_name : item.title || item.original_title;
            const originalTitle = media === "tv" ? item.original_name : item.original_title;
            const candidateYear = String(media === "tv" ? item.first_air_date : item.release_date).match(/\d{4}/)?.[0] || "";

            return {
                item,
                score:
                    (normalizeTmdbText(candidateTitle) === normalizedTitle ? 30 : 0) +
                    (normalizeTmdbText(originalTitle) === normalizedTitle ? 20 : 0) +
                    (year && candidateYear === year ? 15 : 0) +
                    Number(item.popularity || 0)
            };
        })
        .sort((a, b) => b.score - a.score)[0]?.item || null;
}

function getImageLanguageScore(value, preferred) {
    if (value === preferred) return 0;
    if (value === "en") return 1;
    if (!value) return 2;
    return 3;
}

async function fetchTmdb(credentials, endpoint, params = {}) {
    const url = new URL(`https://api.themoviedb.org/3${endpoint}`);

    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== "") {
            url.searchParams.set(key, String(value));
        }
    });

    const headers = {
        "Accept": "application/json"
    };

    if (credentials.readToken) {
        headers.Authorization = `Bearer ${credentials.readToken}`;
    } else {
        url.searchParams.set("api_key", credentials.apiKey);
    }

    const tmdbResponse = await fetch(url, { headers });

    if (!tmdbResponse.ok) {
        throw new Error(`TMDb respondeu ${tmdbResponse.status}`);
    }

    return tmdbResponse.json();
}

function getTmdbCredentials() {
    const readToken = process.env.TMDB_READ_TOKEN || process.env.TMDB_BEARER_TOKEN || "";
    const apiKey = process.env.TMDB_API_KEY || "";

    if (!readToken && !apiKey) return null;

    return { readToken, apiKey };
}

function normalizeTmdbText(value) {
    return String(value || "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-z0-9]+/gi, " ")
        .toLowerCase()
        .trim();
}

async function syncLibrary(successMessage, failureMessage) {
    isSyncing = true;
    syncStatus = {
        state: "syncing",
        message: "Atualizando biblioteca...",
        startedAt: new Date().toISOString(),
        finishedAt: "",
        output: ""
    };

    try {
        const result = await runSync();
        const ok = result.code === 0;

        if (result.output) {
            console.log(result.output);
        }

        if (!ok) {
            console.log(`BRasa: ${failureMessage}`);
        }

        syncStatus = {
            ...syncStatus,
            state: ok ? "complete" : "error",
            message: ok ? successMessage : failureMessage,
            finishedAt: new Date().toISOString(),
            output: result.output
        };

        return result;
    } finally {
        isSyncing = false;
    }
}

function runSync() {
    return new Promise((resolve) => {
        let output = "";
        let child = null;

        try {
            const scriptPath = path.join(rootDir, "scripts", "sync-movies.ps1");
            child = spawn("powershell.exe", [
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptPath
            ], {
                cwd: rootDir,
                windowsHide: true
            });
        } catch (error) {
            resolve({
                code: 1,
                output: `Nao foi possivel iniciar a atualizacao: ${error.message}`
            });
            return;
        }

        child.stdout.on("data", (chunk) => {
            output += chunk.toString();
        });

        child.stderr.on("data", (chunk) => {
            output += chunk.toString();
        });

        child.on("error", (error) => {
            resolve({
                code: 1,
                output: `Nao foi possivel executar a atualizacao: ${error.message}`
            });
        });

        child.on("close", (code) => {
            resolve({
                code,
                output: output.trim()
            });
        });
    });
}

async function serveStatic(pathname, request, response) {
    const safePath = decodeURIComponent(pathname).replace(/^\/+/, "") || "index.html";
    const absolutePath = path.resolve(rootDir, safePath);

    if (!absolutePath.startsWith(rootDir)) {
        response.writeHead(403);
        response.end("Forbidden");
        return;
    }

    const stat = await fs.stat(absolutePath).catch(() => null);

    if (!stat) {
        response.writeHead(404);
        response.end("Not found");
        return;
    }

    if (stat.isDirectory()) {
        await serveStatic(path.join(safePath, "index.html"), request, response);
        return;
    }

    const extension = path.extname(absolutePath).toLowerCase();
    const etag = createEtag(stat);
    const headers = {
        "Content-Type": contentTypes[extension] || "application/octet-stream",
        "Cache-Control": getCacheControl(absolutePath, extension),
        "ETag": etag,
        "Last-Modified": stat.mtime.toUTCString()
    };

    if (request.headers["if-none-match"] === etag) {
        response.writeHead(304, headers);
        response.end();
        return;
    }

    if (isMediaFile(extension)) {
        await serveMediaFile(absolutePath, stat, request, response, headers);
        return;
    }

    response.writeHead(200, {
        ...headers,
        "Content-Length": stat.size
    });

    if (request.method === "HEAD") return response.end();

    const file = await fs.readFile(absolutePath);
    response.end(file);
}

function isMediaFile(extension) {
    return [".mp4", ".mkv", ".webm", ".mov", ".avi"].includes(extension);
}

function createEtag(stat) {
    return `W/"${stat.size}-${Math.round(stat.mtimeMs)}"`;
}

function getCacheControl(absolutePath, extension) {
    const relativePath = path.relative(rootDir, absolutePath).replace(/\\/g, "/");

    if (relativePath.startsWith("assets/")) {
        return "public, max-age=604800";
    }

    if (relativePath.startsWith("data/") || extension === ".html") {
        return "no-cache";
    }

    if ([".css", ".js", ".mjs", ".svg"].includes(extension)) {
        return "no-cache";
    }

    return "no-cache";
}

async function serveMediaFile(absolutePath, stat, request, response, headers) {
    const range = request.headers.range;
    const size = stat.size;

    headers["Accept-Ranges"] = "bytes";

    if (!range) {
        response.writeHead(200, {
            ...headers,
            "Content-Length": size
        });

        if (request.method === "HEAD") return response.end();

        createReadStream(absolutePath).pipe(response);
        return;
    }

    const match = /^bytes=(\d*)-(\d*)$/.exec(range);

    if (!match) {
        response.writeHead(416, {
            ...headers,
            "Content-Range": `bytes */${size}`
        });
        response.end();
        return;
    }

    let start = match[1] === "" ? 0 : Number(match[1]);
    let end = match[2] === "" ? size - 1 : Number(match[2]);

    if (match[1] === "" && match[2] !== "") {
        const suffixLength = Number(match[2]);
        start = Math.max(size - suffixLength, 0);
        end = size - 1;
    }

    if (!Number.isFinite(start) || !Number.isFinite(end) || start > end || start >= size) {
        response.writeHead(416, {
            ...headers,
            "Content-Range": `bytes */${size}`
        });
        response.end();
        return;
    }

    end = Math.min(end, size - 1);

    const length = end - start + 1;

    response.writeHead(206, {
        ...headers,
        "Content-Length": length,
        "Content-Range": `bytes ${start}-${end}/${size}`
    });

    if (request.method === "HEAD") {
        response.end();
        return;
    }

    createReadStream(absolutePath, { start, end }).pipe(response);
}

function sendJson(response, status, body) {
    response.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
    });
    response.end(JSON.stringify(body));
}
