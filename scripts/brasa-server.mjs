import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const host = "127.0.0.1";
const preferredPort = Number(process.env.BRASA_PORT || 4173);

let isSyncing = false;

const contentTypes = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".vtt": "text/vtt; charset=utf-8",
    ".mp4": "video/mp4",
    ".mkv": "video/x-matroska",
    ".webm": "video/webm"
};

const server = http.createServer(async (request, response) => {
    try {
        const url = new URL(request.url, `http://${request.headers.host}`);

        if (request.method === "POST" && url.pathname === "/api/sync") {
            await handleSync(response);
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

function listen(port) {
    server.once("error", (error) => {
        if (error.code === "EADDRINUSE" && port < preferredPort + 20) {
            listen(port + 1);
            return;
        }

        throw error;
    });

    server.listen(port, host, () => {
        console.log(`BRasa rodando em http://${host}:${port}/`);
    });
}

async function handleSync(response) {
    if (isSyncing) {
        sendJson(response, 409, {
            ok: false,
            message: "Uma atualizacao ja esta em andamento."
        });
        return;
    }

    isSyncing = true;

    try {
        const result = await runSync();
        sendJson(response, result.code === 0 ? 200 : 500, {
            ok: result.code === 0,
            output: result.output,
            message: result.code === 0
                ? "Biblioteca atualizada."
                : "A atualizacao falhou."
        });
    } finally {
        isSyncing = false;
    }
}

function runSync() {
    return new Promise((resolve) => {
        const scriptPath = path.join(rootDir, "scripts", "sync-movies.ps1");
        const child = spawn("powershell.exe", [
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            scriptPath
        ], {
            cwd: rootDir,
            windowsHide: true
        });

        let output = "";

        child.stdout.on("data", (chunk) => {
            output += chunk.toString();
        });

        child.stderr.on("data", (chunk) => {
            output += chunk.toString();
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
    const headers = {
        "Content-Type": contentTypes[extension] || "application/octet-stream",
        "Cache-Control": "no-store"
    };

    response.writeHead(200, headers);

    if (request.method === "HEAD") return response.end();

    const file = await fs.readFile(absolutePath);
    response.end(file);
}

function sendJson(response, status, body) {
    response.writeHead(status, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
    });
    response.end(JSON.stringify(body));
}
