import http from "node:http";
import fs from "node:fs/promises";
import { createReadStream, rmSync } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";
import crypto from "node:crypto";
import { getMediaToolsStatus } from "../server/media-tools.mjs";
import { createMediaStateStore } from "../server/media-state.mjs";
import { createMediaQueue } from "../server/media-queue.mjs";
import { createLibraryHealthStore } from "../server/library-health-store.mjs";
import { createSyncHistory } from "../server/sync-history.mjs";
import { createLibraryHealth } from "../server/library-health.mjs";
import { startLibraryWatcher } from "../server/library-watcher.mjs";
import { createSyncCoordinator } from "../server/sync-coordinator.mjs";
import { absoluteLibraryRoots, isProcessableVideo, resolvePathInsideLibrary } from "../server/library-config.mjs";
import { AppError, ForbiddenError, NotFoundError, PayloadTooLargeError, ValidationError } from "../server/app-errors.mjs";
import { validateLocalWriteRequest } from "../server/local-security.mjs";
import { normalizeProfileState } from "../server/profile-state.mjs";
import { createAdminAuthService } from "../server/admin-auth.mjs";
import { createAdminLogService } from "../server/admin-log.mjs";
import { createAdminServices } from "../server/admin-services.mjs";
import { createAdminController } from "../server/admin-controller.mjs";
import { createMetadataRetryStore } from "../server/metadata-retry-store.mjs";
import { createNetworkConfigStore, hostForNetworkSettings } from "../server/network-config.mjs";
import { getPrivateNetworkAddresses } from "../server/network-interfaces.mjs";
import { createDeviceStore } from "../server/device-store.mjs";
import { createPairingService } from "../server/pairing-service.mjs";
import { createDeviceAuth } from "../server/device-auth.mjs";
import { createDeviceController } from "../server/device-controller.mjs";
import { startServiceDiscovery } from "../server/service-discovery.mjs";
import { createAndroidTvUpdateService } from "../server/android-tv-update-service.mjs";
import { createTvLibraryCache } from "../server/tv-library-cache.mjs";
import { canAccessLegacyApi, isLoopbackAddress } from "../server/network-access.mjs";
import { resolveByteRange } from "../server/http-range.mjs";
import { ensureEnvFile } from "./setup-env.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
await ensureEnvFile({ rootDir });
await loadEnvFile();

const networkConfigStore = createNetworkConfigStore(rootDir);
const startupNetworkSettings = await networkConfigStore.load();
const host = hostForNetworkSettings(startupNetworkSettings);
const preferredPort = Number(process.env.BRASA_PORT || 4173);
let activePort = preferredPort;
const stateFile = path.join(rootDir, ".brasa-server.json");
const userCollectionsFile = path.join(rootDir, "data", "user-collections.json");
const userStateFile = path.join(rootDir, "data", "user-state.json");
const userStateBackupFile = path.join(rootDir, "data", "user-state.backup.json");
const androidTvUpdatesRoot=path.join(rootDir,"data","android-tv-updates");
const streamChunkSize = 256 * 1024;
const systemCollectionIds = new Set(["mcu", "dc", "star-wars", "lotr", "harry-potter", "jurassic", "mission-impossible", "classics", "fast-furious", "pirates-caribbean", "rocky", "pixar", "disney-classics", "dreamworks", "ghibli", "best-picture"]);
const tmdbImageCache = new Map();
const tvStreamFileCache = new Map();
const pinAttempts = new Map();
let userStateWriteQueue = Promise.resolve();
let profileRequestQueue = Promise.resolve();
let userCollectionsWriteQueue = Promise.resolve();
let libraryWatcher = null;
let dailyRecoveryTimer = null;
let pendingRecoveryTimer = null;
let serviceDiscovery = null;
let activeMediaStreams = 0;
const mediaStore = createMediaStateStore(rootDir);
const tvLibraryCache = createTvLibraryCache(rootDir);
const metadataRetryStore = createMetadataRetryStore(rootDir);
const mediaQueue = createMediaQueue({ rootDir, store: mediaStore, getTools: () => getMediaToolsStatus(rootDir), resolveMedia });
const libraryHealthStore = createLibraryHealthStore(rootDir);
const syncHistoryStore = createSyncHistory(rootDir);
const libraryHealth = createLibraryHealth({ rootDir, store: libraryHealthStore, mediaStore, mediaQueue, syncHistory: syncHistoryStore });
const deviceStore = createDeviceStore(rootDir);
const pairingService = createPairingService({ deviceStore, getSettings: () => networkConfigStore.load() });
const deviceAuth = createDeviceAuth(deviceStore);
const androidTvUpdateService=createAndroidTvUpdateService({updatesRoot:androidTvUpdatesRoot,deviceStore,serveFile:serveMediaFile});
const deviceController = createDeviceController({ pairing: pairingService, auth: deviceAuth, settingsStore: networkConfigStore, deviceStore, networkInfo: getPrivateNetworkAddresses, tvServices: { profiles: tvProfiles, catalog: tvCatalog, home: tvHome, search: tvSearch, playback: tvPlayback, progress: tvProgress, saveProgress: tvSaveProgress, saveFavorite: tvSaveFavorite, verifyPin: tvVerifyPin, stream: tvStream },updateService:androidTvUpdateService, readBody: readJsonBody, send: sendJson });

let isSyncing = false;
let syncStatus = {
    state: "idle",
    message: "Biblioteca pronta.",
    startedAt: "",
    finishedAt: "",
    output: ""
};
const syncCoordinator = createSyncCoordinator({ runSync: (reasons) => syncLibrary(`Biblioteca atualizada (${reasons.join(", ") || "automático"}).`, "A atualização falhou."), afterSync: () => scheduleAutomaticMediaAnalysis(), onStatusChange: (status) => { syncStatus = status; } });
const adminAuth = createAdminAuthService({ rootDir });
const adminLogs = createAdminLogService(rootDir);
const getAdminTools=async()=>{const tools=await getMediaToolsStatus(rootDir);return{...tools,ffmpegPath:tools.ffmpegPath?path.basename(tools.ffmpegPath):"",ffprobePath:tools.ffprobePath?path.basename(tools.ffprobePath):""};};
const adminServices = createAdminServices({ rootDir, mediaStore, mediaQueue, libraryHealth, libraryHealthStore, syncHistoryStore, syncCoordinator, getTools: getAdminTools, profileAdapter: { list: adminListProfiles, create: adminCreateProfile, update: adminUpdateProfile, remove: adminRemoveProfile, clear: adminClearProfile, relocate: adminRelocate }, collectionAdapter: { list: readUserCollections, create: adminCreateCollection, update: adminUpdateCollection, remove: adminRemoveCollection }, watcherStatus: () => ({ enabled: process.env.BRASA_WATCH_LIBRARY !== "0", active: Boolean(libraryWatcher), observedFiles: libraryWatcher?.getStates?.().length || 0 }) });
const handleAdminApi = createAdminController({ auth: adminAuth, logs: adminLogs, services: adminServices, readBody: readJsonBody, send: sendJson, syncCoordinator, libraryHealth, mediaQueue, mediaStore, deviceAdmin: deviceController.admin, getPort: () => activePort, getHost: () => host });

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
        const isDeviceRoute = url.pathname === "/api/v1/bootstrap" || url.pathname.startsWith("/api/v1/tv/") ||url.pathname.startsWith("/api/v1/android-tv/")|| url.pathname.startsWith("/api/device-pairing/") || url.pathname.startsWith("/api/tv/");
        if (!isDeviceRoute && !canAccessLegacyApi({ lanAccessEnabled: startupNetworkSettings.lanAccessEnabled, remoteAddress: request.socket?.remoteAddress, pathname: url.pathname })) throw new ForbiddenError("Esta API está disponível somente no computador do BRasa.");
        if (!isDeviceRoute) validateLocalWriteRequest(request, { port: activePort });

        if (isDeviceRoute) {
            await deviceController.handle(request, response, url);
            return;
        }

        if (url.pathname === "/api/admin/status" || url.pathname.startsWith("/api/admin/")) {
            await handleAdminApi(request, response, url);
            return;
        }

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

        if (url.pathname === "/api/collections" || url.pathname.startsWith("/api/collections/")) {
            await handleCollectionsApi(request, response, url);
            return;
        }

        if (url.pathname === "/api/profiles" || url.pathname.startsWith("/api/profiles/")) {
            if (requiresAdminProfileSession(request, url)) adminAuth.requireSession(request, { csrf: true });
            await queueProfileRequest(() => handleProfilesApi(request, response, url));
            return;
        }

        if (url.pathname === "/api/media-tools/status" || url.pathname === "/api/media/status" || url.pathname.startsWith("/api/media/")) {
            await handleMediaApi(request, response, url);
            return;
        }

        if (url.pathname === "/api/library/health" || url.pathname.startsWith("/api/library/")) {
            await handleLibraryHealthApi(request, response, url);
            return;
        }

        if (request.method !== "GET" && request.method !== "HEAD") {
            sendJson(response, 405, { ok: false, message: "Metodo nao permitido." });
            return;
        }

        if (url.pathname.startsWith("/admin/") && !path.extname(url.pathname)) {
            await serveStatic("/admin/index.html", request, response);
            return;
        }

        await serveStatic(url.pathname, request, response);
    } catch (error) {
        const expected = error instanceof AppError;
        console.error(`[BRasa ${request.method} ${request.url}]`, error.stack || error);
        sendJson(response, expected ? error.status : 500, { ok: false, code: expected ? error.code : "INTERNAL_ERROR", message: expected ? error.publicMessage : "Não foi possível concluir a operação.", ...(process.env.BRASA_DEBUG === "1" ? { details: error.message } : {}) });
    }
});
server.keepAliveTimeout = 30_000;
server.headersTimeout = 35_000;

listen(preferredPort);
mediaQueue.restore().catch((error) => console.log(`BRasa: não foi possível restaurar a fila (${error.message}).`));

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
    await syncCoordinator.requestSync("startup");
}

async function enableLibraryWatcher() {
    if (process.env.BRASA_WATCH_LIBRARY === "0") return;
    const requestSync = debounce((event) => syncCoordinator.requestSync(`watcher:${event.event}`), 1200);
    libraryWatcher = await startLibraryWatcher({ rootDir, onStableChange: requestSync, onError: (error, directory) => console.error(`BRasa watcher (${directory}):`, error.message) });
    console.log("BRasa: observando alteracoes nas pastas da biblioteca.");
}

function enableDailyMetadataRecovery() {
    if (dailyRecoveryTimer || process.env.BRASA_DAILY_RECOVERY === "0") return;
    pendingRecoveryTimer = setInterval(async () => {
        const recovery = await metadataRetryStore.summary().catch(() => ({ pendingItems: 0 }));
        if (recovery.pendingItems > 0) syncCoordinator.requestSync("scheduled-metadata-recovery").catch((error) => console.error("BRasa recuperação programada:", error.message));
    }, 5 * 60 * 1000);
    pendingRecoveryTimer.unref?.();
    dailyRecoveryTimer = setInterval(() => {
        syncCoordinator.requestSync("daily-metadata-recovery").catch((error) => console.error("BRasa recuperação diária:", error.message));
    }, 24 * 60 * 60 * 1000);
    dailyRecoveryTimer.unref?.();
}

function debounce(callback, delay) {
    let timer;
    return (...values) => {
        clearTimeout(timer);
        timer = setTimeout(() => callback(...values), delay);
    };
}

function listen(port) {
    server.once("error", (error) => {
        if (error.code === "EADDRINUSE" && port < preferredPort + 100) {
            listen(port + 1);
            return;
        }

        throw error;
    });

    server.listen(port, host, async () => {
        activePort = port;
        writeServerState(port);
        console.log(`BRasa rodando em http://${host}:${port}/`);
        serviceDiscovery = await startServiceDiscovery({ enabled: startupNetworkSettings.lanAccessEnabled, name: startupNetworkSettings.serverName || "BRasa", port });
        await enableLibraryWatcher().catch((error) => console.log(`BRasa: watcher indisponivel (${error.message}).`));
        enableDailyMetadataRecovery();
        if (process.env.BRASA_SKIP_STARTUP_SYNC !== "1") runStartupSync().catch((error) => console.error("BRasa startup sync:", error));
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
    await serviceDiscovery?.stop();
    await removeServerState();
    process.exit(0);
});

process.on("SIGTERM", async () => {
    await serviceDiscovery?.stop();
    await removeServerState();
    process.exit(0);
});

async function handleSync(response) {
    const wasRunning = syncCoordinator.isRunning();
    await syncCoordinator.requestSync("manual");
    sendJson(response, wasRunning ? 202 : syncStatus.state === "complete" ? 200 : 500, syncStatus);
}

async function handleCollectionsApi(request, response, url) {
    const id = decodeURIComponent(url.pathname.slice("/api/collections/".length));
    const collections = await readUserCollections();

    if (request.method === "GET" && url.pathname === "/api/collections") {
        sendJson(response, 200, { collections });
        return;
    }

    if (request.method === "POST" && url.pathname === "/api/collections") {
        const input = validateCollection(await readJsonBody(request));
        if (systemCollectionIds.has(input.id) || collections.some((item) => item.id === input.id)) {
            sendJson(response, 409, { ok: false, message: "Já existe uma coleção com este identificador." });
            return;
        }
        await writeUserCollections([...collections, input]);
        sendJson(response, 201, { collection: input });
        return;
    }

    if (!isValidCollectionId(id)) {
        sendJson(response, 400, { ok: false, message: "Identificador de coleção inválido." });
        return;
    }

    if (systemCollectionIds.has(id)) {
        sendJson(response, 403, { ok: false, message: "Coleções do sistema não podem ser alteradas ou excluídas." });
        return;
    }

    const index = collections.findIndex((item) => item.id === id);
    if (index < 0) {
        sendJson(response, 404, { ok: false, message: "Coleção não encontrada." });
        return;
    }

    if (request.method === "PUT") {
        const input = validateCollection(await readJsonBody(request), id);
        collections[index] = input;
        await writeUserCollections(collections);
        sendJson(response, 200, { collection: input });
        return;
    }

    if (request.method === "DELETE") {
        collections.splice(index, 1);
        await writeUserCollections(collections);
        sendJson(response, 200, { ok: true });
        return;
    }

    sendJson(response, 405, { ok: false, message: "Método não permitido." });
}

async function handleMediaApi(request,response,url){const pathName=url.pathname;
    if(request.method==="GET"&&pathName==="/api/media-tools/status"){const tools=await getMediaToolsStatus(rootDir);return sendJson(response,200,{...tools,ffmpegPath:tools.ffmpegPath?path.basename(tools.ffmpegPath):"",ffprobePath:tools.ffprobePath?path.basename(tools.ffprobePath):""});}
    if(request.method==="GET"&&pathName==="/api/media/status")return sendJson(response,200,await mediaStore.all());
    if(request.method==="GET"&&pathName==="/api/media/queue")return sendJson(response,200,mediaQueue.snapshot());
    if(request.method==="GET"&&pathName==="/api/media/settings")return sendJson(response,200,{settings:await mediaStore.settings()});
    if(request.method==="PUT"&&pathName==="/api/media/settings"){const input=await readJsonBody(request);const allowed=["autoAnalyze","autoPrepare","generateThumbnails","extractSubtitles","keepOriginal","cpuFallback","maxConcurrent","acceleration","quality","audioLanguage","subtitleLanguages","minimumFreeGb","maxPreparedGb","removePartialOnFailure","paused"];const clean=Object.fromEntries(Object.entries(input).filter(([key])=>allowed.includes(key)));return sendJson(response,200,{settings:await mediaStore.setSettings(clean)});}
    const parts=pathName.split("/").filter(Boolean),action=parts[2]||"",key=decodeURIComponent(parts.slice(3).join("/"));
    if(action==="status"&&request.method==="GET"){if(!isValidMediaKey(key))return sendJson(response,400,{ok:false,message:"Identificador de mídia inválido."});let item=await mediaStore.get(key);if(item){const media=await resolveMedia(key);if(media){const stat=await fs.stat(path.resolve(rootDir,media.originalPath)).catch(()=>null);if(stat&&item.fingerprint&&(item.fingerprint.size!==stat.size||item.fingerprint.mtimeMs!==Math.round(stat.mtimeMs)))item=await mediaStore.update(key,{status:"pending",preparedPath:"",progress:0,error:"O arquivo original mudou e precisa ser analisado novamente.",probe:null,fingerprint:null});}}return item?sendJson(response,200,{item}):sendJson(response,404,{ok:false,message:"Mídia ainda não analisada."});}
    if(action==="analyze"&&request.method==="POST"){const body=key?{}:await readJsonBody(request),mediaKey=key||String(body.mediaKey||"");if(!isValidMediaKey(mediaKey))return sendJson(response,400,{ok:false,message:"Identificador de mídia inválido."});return sendJson(response,202,{item:await mediaQueue.analyze(mediaKey,{prepare:Boolean(body.prepare),priority:20})});}
    if(["prepare","retry","cancel"].includes(action)&&request.method==="POST"){if(!isValidMediaKey(key))return sendJson(response,400,{ok:false,message:"Identificador de mídia inválido."});if(action==="cancel")return sendJson(response,mediaQueue.cancel(key)?200:404,{ok:true});return sendJson(response,202,{item:await mediaQueue[action](key)});}
    if(action==="prepared"&&request.method==="DELETE"){if(!isValidMediaKey(key))return sendJson(response,400,{ok:false,message:"Identificador de mídia inválido."});return sendJson(response,200,{item:await mediaQueue.removePrepared(key)});}
    if(action==="queue"&&request.method==="POST"&&["pause","resume"].includes(parts[3])){mediaQueue[parts[3]]();return sendJson(response,200,{ok:true,queue:mediaQueue.snapshot()});}
    sendJson(response,405,{ok:false,message:"Método não permitido."});
}

async function handleLibraryHealthApi(request,response,url){const adminProfile=await getAdultLibraryProfile(request);if(!adminProfile)return sendJson(response,403,{ok:false,message:"A área de diagnóstico está disponível somente para perfis adultos."});const p=url.pathname;
    if(request.method==="GET"&&p==="/api/library/health")return sendJson(response,200,await libraryHealth.health());
    if(request.method==="GET"&&p==="/api/library/health/summary")return sendJson(response,200,{summary:(await libraryHealth.health()).summary,lastAuditAt:(await libraryHealth.health()).lastAuditAt,activeAudit:libraryHealth.status()});
    if(request.method==="GET"&&p==="/api/library/issues")return sendJson(response,200,{issues:await libraryHealth.issues()});
    if(request.method==="GET"&&p==="/api/library/audits")return sendJson(response,200,{audits:(await libraryHealth.health()).auditHistory,active:libraryHealth.status()});
    if(request.method==="GET"&&p==="/api/library/sync-history")return sendJson(response,200,await syncHistoryStore.list());
    if(request.method==="GET"&&p==="/api/library/storage")return sendJson(response,200,{storage:await libraryHealth.storage()});
    const parts=p.split("/").filter(Boolean),section=parts[1],action=parts[2],id=decodeURIComponent(parts.slice(3).join("/"));
    if(request.method==="GET"&&section==="library"&&action==="issues"){const issue=await libraryHealthStore.issue(id);return issue?sendJson(response,200,{issue}):sendJson(response,404,{ok:false,message:"Problema não encontrado."});}
    if(request.method==="POST"&&action==="audit"&&["quick","full"].includes(id))return sendJson(response,202,{operation:await libraryHealth.audit(id)});
    if(request.method==="POST"&&p==="/api/library/audit/cancel")return sendJson(response,libraryHealth.cancel()?200:409,{ok:true});
    if(request.method==="POST"&&action==="issues"){const issueAction=parts[4];if(!/^[a-z0-9-]{8,40}$/.test(parts[3]||""))return sendJson(response,400,{ok:false,message:"Identificador inválido."});if(issueAction==="retry")return sendJson(response,202,{result:await libraryHealth.retry(parts[3])});if(["ignore","restore","resolve"].includes(issueAction))return sendJson(response,200,{issue:await libraryHealth.setIssue(parts[3],issueAction)});}
    if(request.method==="POST"&&action==="items"&&isValidMediaKey(decodeURIComponent(parts[3]||""))&&parts[4]==="locate"){const mediaKey=decodeURIComponent(parts[3]),body=await readJsonBody(request),candidates=await findLibraryCandidates(mediaKey);if(!body.candidateId)return sendJson(response,200,{candidates});const selected=candidates.find((candidate)=>candidate.id===body.candidateId);if(!selected||selected.confidence==="low")return sendJson(response,400,{ok:false,message:"Candidato inválido ou com baixa confiança."});resolvePathInsideLibrary(rootDir,selected.relativePath);const current=await mediaStore.get(mediaKey);await mediaStore.update(mediaKey,{...(current||{}),mediaKey,originalPath:selected.relativePath,preparedPath:"",status:"pending",probe:null,fingerprint:null,error:""});return sendJson(response,200,{ok:true,relativePath:selected.relativePath,score:selected.score,confidence:selected.confidence,reasons:selected.reasons});}
    if(request.method==="POST"&&p==="/api/library/sync"){await handleSync(response);return;}
    if(request.method==="DELETE"&&action==="prepared"&&isValidMediaKey(id)){if(adminProfile.pinHash&&!verifyPinHash(adminProfile,String(request.headers["x-brasa-admin-pin"]||"")))return sendJson(response,403,{ok:false,message:"Confirmação administrativa inválida."});return sendJson(response,200,{item:await mediaQueue.removePrepared(id)});}
    sendJson(response,405,{ok:false,message:"Ação de biblioteca não disponível."});
}
async function getAdultLibraryProfile(request){const id=String(request.headers["x-brasa-profile-id"]||"");if(!isValidProfileId(id))return null;const state=await readUserState();return state.profiles.find((profile)=>profile.id===id&&profile.kind==="adult")||null;}

async function resolveMedia(mediaKey){const [type,id]=mediaKey.split(":"),stateItem=await mediaStore.get(mediaKey),library=await tvLibraryCache.load();if(type==="movie"){const movie=library.movieById.get(id);return movie?.video?{mediaType:"movie",mediaId:id,originalPath:stateItem?.originalPath||movie.originalVideo||movie.video,title:movie.title,year:movie.year,audience:movie.audience,fileSize:movie.fileSize}:null;}if(type==="episode"){const episode=library.episodeById.get(id)?.episode;return episode?.video?{mediaType:"episode",mediaId:id,originalPath:stateItem?.originalPath||episode.originalVideo||episode.video,title:episode.title,audience:episode.audience,seasonNumber:episode.seasonNumber,episodeNumber:episode.episodeNumber,fileSize:episode.fileSize}:null;}return null;}

async function tvProfiles(device) {
    const state = await readUserState();
    return publicProfiles(state.profiles).filter((profile) => !device.allowedProfileIds.length || device.allowedProfileIds.includes(profile.id));
}

async function tvCatalog(device, profileId) {
    const state = await readUserState(), profile = state.profiles.find((item) => item.id === profileId);
    if (!profile || (device.allowedProfileIds.length && !device.allowedProfileIds.includes(profileId))) throw new ForbiddenError("Perfil não autorizado.");
    const library = await tvLibraryCache.load();
    const profileState = validateProfileState(state.states[profileId] || {});
    const movies = library.movies.filter((item) => canTvAccess(item, profile)).map((item) => tvMovie(item, profileId, profileState));
    const series = library.series.filter((item) => canTvAccess(item, profile)).map((item) => tvSeries(item, profileId, profile, profileState));
    const collections = library.collections.filter((item) => item.banner).map((item) => ({ id: item.id, title: item.title, subtitle: item.subtitle || "", banner: item.banner || "" }));
    return { profile: publicProfile(profile), movies, series, collections, favorites: profileState.favorites, progress: profileState.progress, updatedAt: new Date().toISOString() };
}

async function tvHome(device, profileId) {
    const catalog = await tvCatalog(device, profileId);
    const episodes = catalog.series.flatMap((series) => series.seasons.flatMap((season) => season.episodes));
    const continueWatching = [...catalog.movies, ...episodes].filter((item) => item.progress && !item.progress.completed && Number(item.progress.percentage) > 0);
    const favorites = [...catalog.movies, ...episodes].filter((item) => item.favorite || catalog.favorites.includes(item.mediaKey));
    const recentMovies = [...catalog.movies].sort((a, b) => Number(b.year || 0) - Number(a.year || 0));
    const rows = [
        { id: "continue", title: "Continuar assistindo", type: "continue", items: continueWatching },
        { id: "recent", title: "Adicionados recentemente", type: "catalog", items: recentMovies },
        { id: "movies", title: "Filmes", type: "catalog", items: catalog.movies },
        { id: "series", title: "Séries", type: "catalog", items: catalog.series },
        { id: "favorites", title: "Minha lista", type: "favorites", items: favorites }
    ].filter((row) => row.items.length);
    return { profile: catalog.profile, rows };
}

async function tvSearch(device, profileId, query) {
    const term = String(query || "").trim().toLocaleLowerCase("pt-BR");
    if (!term) return [];
    const catalog = await tvCatalog(device, profileId), episodes = catalog.series.flatMap((series) => series.seasons.flatMap((season) => season.episodes));
    return [...catalog.movies, ...catalog.series, ...episodes].filter((item) =>
        [item.title, item.originalTitle, item.overview, ...(item.genres || [])].some((value) => String(value || "").toLocaleLowerCase("pt-BR").includes(term))
    ).slice(0, 80);
}

async function tvPlayback(device, profileId, mediaKey) {
    const state = await readUserState(), profile = state.profiles.find((candidate) => candidate.id === profileId);
    if (!profile || (device.allowedProfileIds.length && !device.allowedProfileIds.includes(profileId))) throw new ForbiddenError("Perfil não autorizado.");
    const library = await tvLibraryCache.load(), profileState = validateProfileState(state.states[profileId] || {}), [type, id] = mediaKey.split(":");
    let item = null, seriesEpisodes = [];
    if (type === "movie") { const movie = library.movieById.get(id); if (movie && canTvAccess(movie, profile)) item = tvMovie(movie, profileId, profileState); }
    if (type === "episode") {
        const record = library.episodeById.get(id);
        if (record?.episode && canTvAccess(record.episode, profile)) {
            item = tvEpisode(record.episode, record.series, profileId, profileState);
            seriesEpisodes = (record.series.seasons || []).flatMap((season) => season.episodes || []).filter((episode) => canTvAccess(episode, profile)).map((episode) => tvEpisode(episode, record.series, profileId, profileState));
        }
    }
    if (!item) throw new NotFoundError("Conteúdo não encontrado.");
    const source = await resolveMedia(mediaKey), extension = path.extname(source?.originalPath || "").toLowerCase();
    const currentEpisodeIndex = seriesEpisodes.findIndex((episode) => episode.mediaKey === mediaKey), nextEpisode = currentEpisodeIndex >= 0 ? seriesEpisodes[currentEpisodeIndex + 1] || null : null;
    return {
        mediaId: item.id, mediaKey, playbackUrl: item.streamUrl,
        mimeType: contentTypes[extension] || "video/*", container: extension.replace(".", ""), supportsRange: true,
        resumePosition: Math.max(0, Math.round(Number(item.progress?.currentTime || 0) * 1000)),
        subtitles: (item.subtitles || []).map((track) => ({ ...track, src: String(track.src || "").startsWith("/") ? track.src : `/${track.src}` })),
        audioTracks: [], nextEpisode, preparationStatus: "ready"
    };
}

async function tvProgress(profileId, mediaKey) { const state = await readUserState(); if (!state.profiles.some((item) => item.id === profileId)) throw new NotFoundError("Perfil não encontrado."); return validateProfileState(state.states[profileId] || {}).progress[mediaKey] || null; }
async function tvSaveProgress(profileId, mediaKey, input) { const state = await readUserState(), profile = state.profiles.find((item) => item.id === profileId); if (!profile) throw new NotFoundError("Perfil não encontrado."); const media = await getTvMediaItem(mediaKey); if (!media || !canTvAccess(media, profile)) throw new ForbiddenError("Conteúdo indisponível para este perfil."); const current = validateProfileState(state.states[profileId] || {}), progress = { ...current.progress, [mediaKey]: validateProgress(input, mediaKey) }; state.states[profileId] = { ...current, progress, updatedAt: new Date().toISOString() }; await queueUserStateWrite(state); return progress[mediaKey]; }
async function tvSaveFavorite(profileId, mediaKey, enabled) { const state = await readUserState(), profile = state.profiles.find((item) => item.id === profileId), media = await getTvMediaItem(mediaKey); if (!profile || !media || !canTvAccess(media, profile)) throw new ForbiddenError("Conteúdo indisponível para este perfil."); const current = validateProfileState(state.states[profileId] || {}), legacyId = mediaKey.split(":").slice(1).join(":"), favorites = enabled ? [...new Set([...current.favorites.filter((item) => item !== legacyId), mediaKey])] : current.favorites.filter((item) => item !== mediaKey && item !== legacyId); state.states[profileId] = { ...current, favorites, updatedAt: new Date().toISOString() }; await queueUserStateWrite(state); return { favorite: enabled }; }
async function tvVerifyPin(profileId, pin) { const state = await readUserState(), profile = state.profiles.find((item) => item.id === profileId); if (!profile) throw new NotFoundError("Perfil não encontrado."); return !profile.pinHash || verifyPinHash(profile, String(pin || "")); }
async function tvStream(request, response, mediaKey, profileId) { const state = await readUserState(), profile = state.profiles.find((item) => item.id === profileId), item = await getTvMediaItem(mediaKey); if (!profile || !item || !canTvAccess(item, profile)) throw new ForbiddenError("Conteúdo indisponível para este perfil."); const cached=tvStreamFileCache.get(mediaKey);let resolved=cached?.expiresAt>Date.now()?cached.path:"",stat=cached?.expiresAt>Date.now()?cached.stat:null;if(!resolved||!stat){const media = await resolveMedia(mediaKey), candidates = [...new Set([media?.originalPath, item.originalVideo, item.video].filter(Boolean))];for (const candidate of candidates) { const safe = resolvePathInsideLibrary(rootDir, candidate).path, candidateStat = await fs.stat(safe).catch(() => null); if (candidateStat?.isFile()) { resolved = safe; stat = candidateStat; break; } }if(resolved&&stat)tvStreamFileCache.set(mediaKey,{path:resolved,stat,expiresAt:Date.now()+30_000});} if (!resolved || !stat) throw new NotFoundError("Arquivo de mídia não encontrado."); return serveMediaFile(resolved, stat, request, response, { "Content-Type": contentTypes[path.extname(resolved).toLowerCase()] || "application/octet-stream", "Cache-Control": "private, no-transform" }); }
async function getTvMediaItem(mediaKey) { const [type, id] = String(mediaKey).split(":"), library = await tvLibraryCache.load(); if (type === "movie") return library.movieById.get(id) || null; if (type === "episode") return library.episodeById.get(id)?.episode || null; return null; }
function canTvAccess(item, profile) { if (!item || item.playable === false || item.fileStatus === "missing-file" || (!Array.isArray(item.seasons) && !item.video)) return false; if (profile.kind !== "kids") return item.audience !== "adult" || profile.kind === "adult"; const audience = item.audience || (item.kids ? "kids" : "general"); if (audience === "kids") return true; if (audience === "adult") return false; const level = tvRatingLevel(item.contentRating); return level !== null && level <= Number(profile.maxContentRating ?? 10); }
function tvRatingLevel(value) { const key = String(value || "").trim().toUpperCase().replace(/\s+/g, ""), levels = { L:0,LIVRE:0,G:0,"TV-Y":0,"TV-Y7":7,"TV-G":0,10:10,"10ANOS":10,12:12,14:14,16:16,18:18,PG:12,"PG-13":13,R:17,"TV-PG":12,"TV-14":14,"TV-MA":18 }; return Object.prototype.hasOwnProperty.call(levels, key) ? levels[key] : null; }
function tvMovie(item, profileId, state) { const mediaKey = `movie:${item.id}`; return { id: String(item.id), mediaKey, type: "movie", title: item.title, year: item.year, duration: item.duration, rating: item.rating, contentRating: item.contentRating, genres: item.genres || [], overview: item.overview || "", poster: item.poster || "", backdrop: item.backdrop || item.poster || "", subtitles: item.subtitles || [], favorite: state.favorites.includes(mediaKey) || state.favorites.includes(String(item.id)), progress: state.progress[mediaKey] || null, streamUrl: `/api/tv/stream/${encodeURIComponent(mediaKey)}?profileId=${encodeURIComponent(profileId)}` }; }
function tvSeries(item, profileId, profile, state) { return { id: String(item.id), mediaKey: `series:${item.id}`, type: "series", title: item.title, year: item.year, contentRating: item.contentRating, genres: item.genres || [], overview: item.overview || "", poster: item.poster || "", backdrop: item.backdrop || item.poster || "", seasons: (item.seasons || []).map((season) => ({ seasonNumber: season.seasonNumber, episodes: (season.episodes || []).filter((episode) => canTvAccess(episode, profile)).map((episode) => tvEpisode(episode, item, profileId, state)) })) }; }
function tvEpisode(episode, series, profileId, state) { const mediaKey = `episode:${episode.id}`; return { id: String(episode.id), mediaKey, type: "episode", seriesId: String(series.id), title: episode.title, seasonNumber: episode.seasonNumber, episodeNumber: episode.episodeNumber, duration: episode.duration, overview: episode.overview || "", poster: episode.thumbnail || series.poster || "", backdrop: episode.backdrop || series.backdrop || "", subtitles: episode.subtitles || [], progress: state.progress[mediaKey] || null, streamUrl: `/api/tv/stream/${encodeURIComponent(mediaKey)}?profileId=${encodeURIComponent(profileId)}` }; }
async function findLibraryCandidates(mediaKey){const media=await resolveMedia(mediaKey),files=[];for(const root of absoluteLibraryRoots(rootDir))for(const entry of await walkLibraryFiles(root.absolutePath)){const stat=await fs.stat(entry).catch(()=>null);if(!stat||!isProcessableVideo(entry,stat.size))continue;const relative=path.relative(rootDir,entry).replace(/\\/g,"/"),id=crypto.createHash("sha1").update(relative).digest("hex").slice(0,16),match=relocationScore(media,entry,root,stat);files.push({id,name:path.basename(entry),relativePath:relative,size:stat.size,...match});}return files.filter((item)=>item.confidence!=="low").sort((a,b)=>b.score-a.score).slice(0,50);}
async function walkLibraryFiles(dir){const files=[];for(const entry of await fs.readdir(dir,{withFileTypes:true}).catch(()=>[])){const item=path.join(dir,entry.name);entry.isDirectory()?files.push(...await walkLibraryFiles(item)):files.push(item);}return files;}
function relocationScore(media,file,root,stat){const normalized=(value)=>String(value||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase(),name=normalized(path.basename(file)),title=normalized(media?.title),tokens=title.split(/[^a-z0-9]+/).filter((token)=>token.length>2),reasons=[];let score=0;const matched=tokens.filter((token)=>name.includes(token));if(tokens.length&&matched.length===tokens.length){score+=50;reasons.push("título correspondente");}else if(matched.length){score+=Math.round(30*matched.length/tokens.length);reasons.push("título parcialmente correspondente");}const year=String(media?.year||"").match(/\d{4}/)?.[0];if(year&&name.includes(year)){score+=15;reasons.push("mesmo ano");}const expectedType=String(media?.mediaType||"");if((expectedType==="movie"&&root.type==="movie")||(expectedType==="episode"&&root.type==="series")){score+=15;reasons.push("mesmo tipo de mídia");}if(media?.audience&&media.audience===root.audience){score+=10;reasons.push("mesma audiência");}if(media?.originalPath&&path.extname(media.originalPath).toLowerCase()===path.extname(file).toLowerCase()){score+=5;reasons.push("mesma extensão");}if(media?.fileSize&&Math.abs(Number(media.fileSize)-stat.size)/Math.max(1,Number(media.fileSize))<.02){score+=5;reasons.push("tamanho semelhante");}return{score:Math.min(100,score),confidence:score>=80?"high":score>=55?"medium":"low",reasons};}
async function scheduleAutomaticMediaAnalysis(){const tools=await getMediaToolsStatus(rootDir),settings=await mediaStore.settings();if(!settings.autoAnalyze||!tools.ffprobeAvailable)return;const moviesModule=await import(`${pathToFileURL(path.join(rootDir,"data","movies.js")).href}?t=${Date.now()}`),seriesModule=await import(`${pathToFileURL(path.join(rootDir,"data","series.js")).href}?t=${Date.now()}`);const keys=[...moviesModule.getMovies().filter((m)=>m.video&&m.playable!==false).map((m)=>`movie:${m.id}`),...seriesModule.getSeries().flatMap((s)=>(s.seasons||[]).flatMap((season)=>(season.episodes||[]).filter((e)=>e.video).map((e)=>`episode:${e.id}`)))];for(const key of keys){const item=await mediaStore.get(key),media=await resolveMedia(key);if(!media)continue;const source=resolvePathInsideLibrary(rootDir,media.originalPath).path,stat=await fs.stat(source).catch(()=>null);if(!stat)continue;if(item?.fingerprint&&item.fingerprint.size===stat.size&&item.fingerprint.mtimeMs===Math.round(stat.mtimeMs))continue;mediaQueue.analyze(key,{prepare:settings.autoPrepare,priority:0}).catch((error)=>console.error(`BRasa mídia ${key} analyze tentativa 1 ${new Date().toISOString()}:`,error.message));}}

async function readUserCollections() {
    await fs.mkdir(path.dirname(userCollectionsFile), { recursive: true });
    const content = await fs.readFile(userCollectionsFile, "utf8").catch(async (error) => {
        if (error.code !== "ENOENT") throw error;
        await fs.writeFile(userCollectionsFile, "[]\n", "utf8");
        return "[]";
    });
    try{const parsed=JSON.parse(content||"[]");return Array.isArray(parsed)?parsed:[];}catch(error){const corrupt=`${userCollectionsFile}.corrupt-${Date.now()}`;await fs.rename(userCollectionsFile,corrupt).catch(()=>{});const backup=await fs.readFile(`${userCollectionsFile}.backup.json`,"utf8").then(JSON.parse).catch(()=>[]);console.error(`BRasa: coleções corrompidas preservadas em ${corrupt}.`,error.message);const recovered=Array.isArray(backup)?backup:[];await writeUserCollections(recovered);return recovered;}
}

async function writeUserCollections(collections) {
    userCollectionsWriteQueue=userCollectionsWriteQueue.then(async()=>{const temporaryFile = `${userCollectionsFile}.${process.pid}.tmp`,backup=`${userCollectionsFile}.backup.json`,previous=await fs.readFile(userCollectionsFile,"utf8").catch(()=>"");if(previous)await fs.writeFile(backup,previous,"utf8");await fs.writeFile(temporaryFile, `${JSON.stringify(collections, null, 2)}\n`, "utf8");await fs.rename(temporaryFile, userCollectionsFile);});
    return userCollectionsWriteQueue;
}

async function readJsonBody(request) {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
        size += chunk.length;
        if (size > 256 * 1024) throw new PayloadTooLargeError();
        chunks.push(chunk);
    }
    try { return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"); }
    catch { throw new ValidationError("JSON inválido."); }
}

function validateCollection(input, forcedId = "") {
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("Dados da coleção inválidos.");
    const id = forcedId || String(input.id || "");
    if (!isValidCollectionId(id)) throw new Error("Identificador de coleção inválido.");
    const title = String(input.title || "").trim().slice(0, 80);
    if (!title) throw new Error("Informe o nome da coleção.");
    const type = input.type === "smart" ? "smart" : "manual";
    const scope = input.scope === "shared" ? "shared" : "profile";
    const movieIds = Array.isArray(input.movieIds) ? [...new Set(input.movieIds.map(String))].slice(0, 2000) : [];
    const ruleItems = Array.isArray(input.rules?.items) ? input.rules.items.slice(0, 20).map((rule) => ({
        field: String(rule.field || "title").slice(0, 32),
        operator: String(rule.operator || "contains").slice(0, 24),
        value: typeof rule.value === "boolean" ? rule.value : String(rule.value ?? "").slice(0, 160)
    })) : [];
    return {
        id, title,
        description: String(input.description || "").trim().slice(0, 300),
        type, source: "user", scope,
        profileId: scope === "profile" ? String(input.profileId || "").slice(0, 48) : null,
        banner: String(input.banner || "").slice(0, 500),
        movieIds: type === "manual" ? movieIds : [],
        rules: type === "smart" ? { match: input.rules?.match === "any" ? "any" : "all", items: ruleItems } : null,
        sort: { field: String(input.sort?.field || "title").slice(0, 24), direction: input.sort?.direction === "desc" ? "desc" : "asc" },
        createdAt: String(input.createdAt || new Date().toISOString()),
        updatedAt: new Date().toISOString()
    };
}

function isValidCollectionId(id) {
    return /^[a-z0-9][a-z0-9-]{2,63}$/.test(String(id || ""));
}

function queueProfileRequest(task) {
    const next = profileRequestQueue.then(task, task);
    profileRequestQueue = next.catch((error) => console.error("BRasa fila de perfil:", error.stack || error));
    return next;
}

async function handleProfilesApi(request, response, url) {
    const state = await readUserState();
    const parts = url.pathname.split("/").filter(Boolean);
    const profileId = decodeURIComponent(parts[2] || "");
    const resource = parts[3] || "";
    const resourceId = decodeURIComponent(parts.slice(4).join("/"));

    if (request.method === "GET" && url.pathname === "/api/profiles") return sendJson(response, 200, { profiles: publicProfiles(state.profiles) });
    if (request.method === "POST" && url.pathname === "/api/profiles") {
        const input = await readJsonBody(request); const profile = validateProfile(input);
        if (state.profiles.some((item) => item.id === profile.id)) return sendJson(response, 409, { ok:false, message:"Já existe um perfil com este identificador." });
        state.profiles.push(profile); state.states[profile.id] = emptyProfileState(); await queueUserStateWrite(state);
        return sendJson(response, 201, { profile: publicProfile(profile) });
    }
    if (!isValidProfileId(profileId) || !state.profiles.some((item) => item.id === profileId)) return sendJson(response, 404, { ok:false, message:"Perfil não encontrado." });
    const profileIndex = state.profiles.findIndex((item) => item.id === profileId);
    if (request.method === "GET" && resource === "state") return sendJson(response, 200, { state: state.states[profileId] || emptyProfileState() });
    if (request.method === "PUT" && resource === "state") { state.states[profileId] = validateProfileState(await readJsonBody(request)); await queueUserStateWrite(state); return sendJson(response, 200, { state:state.states[profileId] }); }
    if (request.method === "PUT" && !resource) { state.profiles[profileIndex] = validateProfile({ ...state.profiles[profileIndex], ...(await readJsonBody(request)), id:profileId }, profileId); await queueUserStateWrite(state); return sendJson(response,200,{profile:publicProfile(state.profiles[profileIndex])}); }
    if (request.method === "DELETE" && !resource) {
        if (state.profiles[profileIndex].kind === "adult" && state.profiles.filter((item)=>item.kind==="adult").length === 1) return sendJson(response,409,{ok:false,message:"O último perfil adulto não pode ser excluído."});
        state.profiles.splice(profileIndex,1); delete state.states[profileId]; await queueUserStateWrite(state); return sendJson(response,200,{ok:true});
    }
    if (resource === "favorites" && isValidMediaId(resourceId)) {
        const current = state.states[profileId], list = current.favorites;
        const favorites = request.method === "PUT" ? [...new Set([...list,resourceId])].slice(0,5000) : list.filter((id)=>id!==resourceId);
        state.states[profileId] = {...current,favorites,updatedAt:new Date().toISOString()};
        await queueUserStateWrite(state); return sendJson(response,200,{ok:true,favorites:state.states[profileId].favorites});
    }
    if (resource === "progress" && isValidMediaKey(resourceId)) {
        const current=state.states[profileId],progress={...current.progress};
        if (request.method === "PUT") progress[resourceId] = validateProgress(await readJsonBody(request), resourceId);
        if (request.method === "DELETE") delete progress[resourceId];
        state.states[profileId]={...current,progress,updatedAt:new Date().toISOString()};
        await queueUserStateWrite(state); return sendJson(response,200,{ok:true});
    }
    if (resource === "history") {
        if (request.method === "POST") { const entry=validateHistory(await readJsonBody(request)); state.states[profileId].history=[entry,...state.states[profileId].history.filter((item)=>item.mediaKey!==entry.mediaKey)].slice(0,500); await queueUserStateWrite(state); return sendJson(response,200,{ok:true}); }
        if (request.method === "DELETE") { state.states[profileId].history=[]; await queueUserStateWrite(state); return sendJson(response,200,{ok:true}); }
    }
    if (resource === "pin" && request.method === "PUT") { const {pin="",currentPin=""}=await readJsonBody(request); const profile=state.profiles[profileIndex]; if(profile.pinHash&&!verifyPinHash(profile,currentPin))return sendJson(response,403,{ok:false,message:"Confirme o PIN atual antes de alterar ou remover."}); profile.pinHash=pin ? hashPin(pin) : ""; await queueUserStateWrite(state); return sendJson(response,200,{ok:true}); }
    if (resource === "verify-pin" && request.method === "POST") return verifyProfilePin(response,state.profiles[profileIndex],await readJsonBody(request));
    sendJson(response,405,{ok:false,message:"Método não permitido."});
}

function requiresAdminProfileSession(request,url){if(request.method==="POST"&&url.pathname==="/api/profiles")return true;const parts=url.pathname.split("/").filter(Boolean),resource=parts[3]||"";return ((request.method==="PUT"||request.method==="DELETE")&&!resource)||(request.method==="PUT"&&resource==="pin");}

function defaultUserState() { const now=new Date().toISOString(); const profiles=[{id:"mario",name:"Mário",initials:"M",kind:"adult",avatar:{type:"initials",value:"M",color:"blue"},pinHash:"",createdAt:now,updatedAt:now},{id:"isabele",name:"Isabele",initials:"I",kind:"adult",avatar:{type:"initials",value:"I",color:"purple"},pinHash:"",createdAt:now,updatedAt:now},{id:"laura",name:"Laura",initials:"L",kind:"kids",maxContentRating:10,avatar:{type:"initials",value:"L",color:"pink"},pinHash:"",createdAt:now,updatedAt:now}]; return {version:1,profiles,states:Object.fromEntries(profiles.map((p)=>[p.id,emptyProfileState()]))}; }
function emptyProfileState(){return {favorites:[],progress:{},history:[],completed:[],preferences:{},updatedAt:""};}
async function readUserState(){
    const content=await fs.readFile(userStateFile,"utf8").catch((error)=>error.code==="ENOENT"?"":"__ERROR__");
    if (!content){const initial=defaultUserState();await queueUserStateWrite(initial);return initial;}
    try {const parsed=JSON.parse(content);if(!parsed?.profiles||!parsed?.states)throw new Error();parsed.states=Object.fromEntries(parsed.profiles.map((profile)=>[profile.id,validateProfileState(parsed.states[profile.id]||{})]));return parsed;} catch {
        await fs.copyFile(userStateFile,`${userStateFile}.corrupt-${Date.now()}`).catch(()=>{});
        const backup=await fs.readFile(userStateBackupFile,"utf8").then(JSON.parse).catch(()=>null); const recovered=backup?.profiles?backup:defaultUserState(); await queueUserStateWrite(recovered); return recovered;
    }
}
function queueUserStateWrite(state){userStateWriteQueue=userStateWriteQueue.then(async()=>{const temp=`${userStateFile}.${process.pid}.tmp`;const existing=await fs.readFile(userStateFile,"utf8").catch(()=>"");if(existing)await fs.writeFile(userStateBackupFile,existing,"utf8");await fs.writeFile(temp,`${JSON.stringify(state,null,2)}\n`,"utf8");await fs.rename(temp,userStateFile);});return userStateWriteQueue;}
function publicProfile(profile){const {pinHash,...safe}=profile;return {...safe,hasPin:Boolean(pinHash)};} function publicProfiles(items){return items.map(publicProfile);}
function validateProfile(input,forcedId=""){const id=forcedId||String(input.id||"");if(!isValidProfileId(id))throw new ValidationError("Identificador de perfil inválido.");const name=String(input.name||"").trim().slice(0,40);if(!name)throw new ValidationError("Informe o nome do perfil.");const now=new Date().toISOString(),kind=input.kind==="kids"?"kids":"adult";return {id,name,initials:String(input.initials||name[0]).trim().slice(0,2).toUpperCase(),kind,maxContentRating:kind==="kids"?Math.min(18,Math.max(0,Number(input.maxContentRating??10))):undefined,avatar:{type:"initials",value:String(input.initials||name[0]).slice(0,2).toUpperCase(),color:String(input.avatar?.color||"blue").slice(0,16)},pinHash:String(input.pinHash||""),createdAt:String(input.createdAt||now),updatedAt:now};}
function validateProfileState(input){return normalizeProfileState(input,{validateMediaKey:isValidMediaKey,validateProgress,validateHistory});}
function validateProgress(input,key){return {mediaType:key.startsWith("episode:")?"episode":"movie",mediaId:String(input.mediaId||key.split(":").slice(1).join(":")),seriesId:String(input.seriesId||""),currentTime:Number(input.currentTime||0),duration:Number(input.duration||0),percentage:Math.min(100,Math.max(0,Number(input.percentage||0))),completed:Boolean(input.completed),updatedAt:new Date().toISOString()};}
function validateHistory(input){if(!isValidMediaKey(input.mediaKey))throw new Error("Conteúdo inválido.");return {mediaKey:input.mediaKey,mediaType:input.mediaKey.startsWith("episode:")?"episode":"movie",mediaId:String(input.mediaId||""),title:String(input.title||"").slice(0,160),startedAt:String(input.startedAt||new Date().toISOString()),lastWatchedAt:new Date().toISOString(),completedAt:String(input.completedAt||"")};}
function isValidProfileId(id){return /^[a-z0-9][a-z0-9-]{1,47}$/.test(id);} function isValidMediaId(id){return /^[a-zA-Z0-9._-]{1,120}$/.test(id);} function isValidMediaKey(key){return /^(movie|episode):[a-zA-Z0-9._-]{1,120}$/.test(key);}
function hashPin(pin){if(!/^\d{4}$/.test(String(pin)))throw new Error("O PIN deve ter exatamente quatro dígitos.");const salt=crypto.randomBytes(16).toString("hex");const hash=crypto.scryptSync(String(pin),salt,32).toString("hex");return `scrypt$${salt}$${hash}`;}
function verifyPinHash(profile,pin){const parts=String(profile.pinHash||"").split("$");if(parts.length!==3||!/^\d{4}$/.test(String(pin||"")))return false;try{return crypto.timingSafeEqual(crypto.scryptSync(String(pin),parts[1],32),Buffer.from(parts[2],"hex"));}catch{return false;}}
function verifyProfilePin(response,profile,input){const record=pinAttempts.get(profile.id)||{count:0,blockedUntil:0};if(Date.now()<record.blockedUntil)return sendJson(response,429,{ok:false,message:"Não foi possível verificar o PIN. Tente novamente em 30 segundos."});let valid=verifyPinHash(profile,input.pin);if(valid){pinAttempts.delete(profile.id);return sendJson(response,200,{ok:true});}record.count++;if(record.count>=5){record.blockedUntil=Date.now()+30000;record.count=0;}pinAttempts.set(profile.id,record);sendJson(response,401,{ok:false,message:"PIN inválido."});}

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

async function adminListProfiles(){const state=await readUserState();return publicProfiles(state.profiles);}
async function adminCreateProfile(input){const state=await readUserState(),profile=validateProfile(input);if(state.profiles.some((item)=>item.id===profile.id))throw new ValidationError("Já existe um perfil com este identificador.");if(input.pin)profile.pinHash=hashPin(input.pin);state.profiles=[...state.profiles,profile];state.states={...state.states,[profile.id]:emptyProfileState()};await queueUserStateWrite(state);return publicProfile(profile);}
async function adminUpdateProfile(id,input){if(!isValidProfileId(id))throw new ValidationError("Perfil inválido.");const state=await readUserState(),index=state.profiles.findIndex((item)=>item.id===id);if(index<0)throw new ValidationError("Perfil não encontrado.");const current=state.profiles[index],profile=validateProfile({...current,...input,id},id);profile.pinHash=input.pin===undefined?current.pinHash:input.pin?hashPin(input.pin):"";state.profiles=state.profiles.map((item)=>item.id===id?profile:item);await queueUserStateWrite(state);return publicProfile(profile);}
async function adminRemoveProfile(id){const state=await readUserState(),profile=state.profiles.find((item)=>item.id===id);if(!profile)throw new ValidationError("Perfil não encontrado.");if(profile.kind==="adult"&&state.profiles.filter((item)=>item.kind==="adult").length===1)throw new ValidationError("O último perfil adulto não pode ser removido.");state.profiles=state.profiles.filter((item)=>item.id!==id);const states={...state.states};delete states[id];state.states=states;await queueUserStateWrite(state);return{removed:true};}
async function adminClearProfile(id,action){const state=await readUserState();if(!state.profiles.some((item)=>item.id===id))throw new ValidationError("Perfil não encontrado.");const current=validateProfileState(state.states[id]||{}),next={...current};if(action==="clear-favorites")next.favorites=[];if(action==="clear-progress"){next.progress={};next.completed=[];}if(action==="clear-history")next.history=[];if(action==="reset-preferences")next.preferences={};next.updatedAt=new Date().toISOString();state.states={...state.states,[id]:next};await queueUserStateWrite(state);return{cleared:true,action};}
async function adminRelocate(mediaKey,candidateId){const candidates=await findLibraryCandidates(mediaKey),selected=candidates.find((item)=>item.id===candidateId);if(!selected||selected.confidence==="low")throw new ValidationError("Candidato inválido ou com baixa confiança.");resolvePathInsideLibrary(rootDir,selected.relativePath);await mediaStore.update(mediaKey,{mediaKey,originalPath:selected.relativePath,preparedPath:"",status:"pending",probe:null,fingerprint:null,error:""});return selected;}
async function adminCreateCollection(input){const collections=await readUserCollections(),collection=validateCollection(input);if(systemCollectionIds.has(collection.id)||collections.some((item)=>item.id===collection.id))throw new ValidationError("Já existe uma coleção com este identificador.");await writeUserCollections([...collections,collection]);return collection;}
async function adminUpdateCollection(id,input){if(systemCollectionIds.has(id))throw new ValidationError("Coleções do sistema são somente leitura.");const collections=await readUserCollections(),index=collections.findIndex((item)=>item.id===id);if(index<0)throw new ValidationError("Coleção não encontrada.");const collection=validateCollection({...input,id},id),next=[...collections];next[index]=collection;await writeUserCollections(next);return collection;}
async function adminRemoveCollection(id){if(systemCollectionIds.has(id))throw new ValidationError("Coleções do sistema são somente leitura.");const collections=await readUserCollections();if(!collections.some((item)=>item.id===id))throw new ValidationError("Coleção não encontrada.");await writeUserCollections(collections.filter((item)=>item.id!==id));return{removed:true};}

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

        const completedAt = syncStatus.finishedAt;
        const relevantErrors = String(result.output || "").split(/\r?\n/).filter((line) => /erro|falhou|error/i.test(line)).slice(0, 10).map((message) => ({ category: "sync", message: message.slice(0, 240) }));
        await syncHistoryStore.add({ id: `sync-${Date.now().toString(36)}`, startedAt: syncStatus.startedAt, completedAt, durationMs: Math.max(0, Date.parse(completedAt) - Date.parse(syncStatus.startedAt)), status: ok ? "success" : "error", summary: { filesScanned: 0, itemsAdded: countOutput(result.output, /adicionado/gi), itemsUpdated: countOutput(result.output, /atualizado/gi), itemsRemoved: 0, filesRenamed: countOutput(result.output, /renomeado/gi), metadataUpdated: 0, subtitlesAdded: countOutput(result.output, /legenda/gi), errors: relevantErrors.length, warnings: 0 }, errors: relevantErrors });
        libraryHealth.audit("quick").catch((error) => console.error("BRasa auditoria após sync:", error.message));

        return { ...result, summary: structuredSyncSummary(result.output) };
    } finally {
        isSyncing = false;
    }
}

function countOutput(value, pattern) { return (String(value || "").match(pattern) || []).length; }
function structuredSyncSummary(output){const lines=String(output||"").split(/\r?\n/).filter(Boolean),pick=(pattern)=>lines.filter((line)=>pattern.test(line));return{added:pick(/adicionado/i),updated:pick(/atualizado|renomeado/i),removed:pick(/missing-file|removido/i),moved:pick(/renomeado/i),failed:pick(/erro|falhou|indisponivel/i),unchanged:pick(/nenhum|nada para atualizar/i)};}

function runSync() {
    return new Promise((resolve) => {
        let output = "";
        let child = null;
        let lineBuffer = "";

        try {
            const scriptPath = path.join(rootDir, "scripts", "sync-movies.mjs");
            child = spawn(process.execPath, [scriptPath], {
                cwd: rootDir,
                windowsHide: true,
                shell: false
            });
        } catch (error) {
            resolve({
                code: 1,
                output: `Nao foi possivel iniciar a atualizacao: ${error.message}`
            });
            return;
        }

        child.stdout.on("data", (chunk) => {
            const text = chunk.toString(); output += text; lineBuffer += text;
            const lines = lineBuffer.split(/\r?\n/); lineBuffer = lines.pop() || "";
            lines.filter(Boolean).forEach(updateSyncProgress);
        });

        child.stderr.on("data", (chunk) => {
            const text = chunk.toString(); output += text;
            text.split(/\r?\n/).filter(Boolean).forEach(updateSyncProgress);
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

function updateSyncProgress(line){const value=String(line||"").trim();if(!value)return;const stages=[[/verificando biblioteca|nenhum filme novo/i,12,"Verificando biblioteca..."],[/encontrei|detectad|filme novo/i,22,value.replace(/^BRasa:\s*/i,"")],[/identific|OMDb/i,38,value.replace(/^BRasa:\s*/i,"")],[/TMDb|poster|backdrop/i,55,value.replace(/^BRasa:\s*/i,"")],[/legenda|OpenSubtitles/i,70,value.replace(/^BRasa:\s*/i,"")],[/renomeado/i,82,value.replace(/^BRasa:\s*/i,"")],[/data\/movies|data\/series|catálogo|catalogo/i,92,"Atualizando catálogo..."],[/nada para atualizar|atualizado com/i,98,"Finalizando biblioteca..."]];const match=stages.find(([pattern])=>pattern.test(value));if(!match)return;syncStatus={...syncStatus,state:"syncing",progress:Math.max(Number(syncStatus.progress||0),match[1]),message:match[2],currentItem:value.slice(0,240)};}

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
        if (startupNetworkSettings.lanAccessEnabled && !isLoopbackAddress(request.socket?.remoteAddress)) {
            sendJson(response, 401, { ok: false, message: "Use a rota autorizada de reprodução." });
            return;
        }
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
    const size = stat.size;
    headers["Accept-Ranges"] = "bytes";
    const range = resolveByteRange(request.headers.range, size);
    if (!range.satisfiable) {
        response.writeHead(416, { ...headers, "Content-Range": `bytes */${size}`, "Content-Length": 0 });
        response.end();
        return;
    }
    if (!range.partial) {
        response.writeHead(200, {
            ...headers,
            "Content-Length": size
        });
        if (request.method === "HEAD") return response.end();
        pipeMediaStream(absolutePath, {}, request, response);
        return;
    }
    const length = range.end - range.start + 1;
    response.writeHead(206, {
        ...headers,
        "Content-Length": length,
        "Content-Range": `bytes ${range.start}-${range.end}/${size}`
    });
    if (request.method === "HEAD") return response.end();
    pipeMediaStream(absolutePath, { start: range.start, end: range.end }, request, response);
}

function pipeMediaStream(absolutePath, range, request, response) {
    const stream = createReadStream(absolutePath, { ...range, highWaterMark: streamChunkSize });
    activeMediaStreams++;
    if (process.env.BRASA_DEBUG === "1") console.log(`BRasa stream: aberto; ativos=${activeMediaStreams}`);
    let closed = false;
    const destroy = () => { if (!stream.destroyed) stream.destroy(); };
    const cleanup = () => {
        if (closed) return;
        closed = true;
        activeMediaStreams = Math.max(0, activeMediaStreams - 1);
        request.removeListener("aborted", destroy);
        response.removeListener("close", destroy);
        if (process.env.BRASA_DEBUG === "1") console.log(`BRasa stream: encerrado; ativos=${activeMediaStreams}`);
    };
    request.once("aborted", destroy);
    response.once("close", destroy);
    stream.once("error", (error) => {
        console.error(`BRasa stream: erro de leitura (${error.code || error.message}).`);
        if (!response.destroyed) response.destroy(error);
    });
    stream.once("close", cleanup);
    stream.pipe(response);
}

function sendJson(response, status, body) {
    response.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
    });
    response.end(JSON.stringify(body));
}
