import fs from "node:fs/promises";
import { createReadStream } from "node:fs";
import path from "node:path";
import { contentTypes } from "./content-types.mjs";
import { resolveByteRange } from "./http-range.mjs";
import { isLoopbackAddress } from "./network-access.mjs";

export function createStaticFiles({
  rootDir,
  startupNetworkSettings,
  sendJson,
}) {
  const streamChunkSize = 256 * 1024;
  let activeMediaStreams = 0;
  async function serveStatic(pathname, request, response) {
    const safePath =
      decodeURIComponent(pathname).replace(/^\/+/, "") || "index.html";
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
      ETag: etag,
      "Last-Modified": stat.mtime.toUTCString(),
    };

    if (request.headers["if-none-match"] === etag) {
      response.writeHead(304, headers);
      response.end();
      return;
    }

    if (isMediaFile(extension)) {
      if (
        startupNetworkSettings.lanAccessEnabled &&
        !isLoopbackAddress(request.socket?.remoteAddress)
      ) {
        sendJson(response, 401, {
          ok: false,
          message: "Use a rota autorizada de reprodução.",
        });
        return;
      }
      await serveMediaFile(absolutePath, stat, request, response, headers);
      return;
    }

    response.writeHead(200, {
      ...headers,
      "Content-Length": stat.size,
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
    const relativePath = path
      .relative(rootDir, absolutePath)
      .replace(/\\/g, "/");

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

  async function serveMediaFile(
    absolutePath,
    stat,
    request,
    response,
    headers,
  ) {
    const size = stat.size;
    headers["Accept-Ranges"] = "bytes";
    const range = resolveByteRange(request.headers.range, size);
    if (!range.satisfiable) {
      response.writeHead(416, {
        ...headers,
        "Content-Range": `bytes */${size}`,
        "Content-Length": 0,
      });
      response.end();
      return;
    }
    if (!range.partial) {
      response.writeHead(200, {
        ...headers,
        "Content-Length": size,
      });
      if (request.method === "HEAD") return response.end();
      pipeMediaStream(absolutePath, {}, request, response);
      return;
    }
    const length = range.end - range.start + 1;
    response.writeHead(206, {
      ...headers,
      "Content-Length": length,
      "Content-Range": `bytes ${range.start}-${range.end}/${size}`,
    });
    if (request.method === "HEAD") return response.end();
    pipeMediaStream(
      absolutePath,
      { start: range.start, end: range.end },
      request,
      response,
    );
  }

  function pipeMediaStream(absolutePath, range, request, response) {
    const stream = createReadStream(absolutePath, {
      ...range,
      highWaterMark: streamChunkSize,
    });
    activeMediaStreams++;
    if (process.env.BRASA_DEBUG === "1")
      console.log(`BRasa stream: aberto; ativos=${activeMediaStreams}`);
    let closed = false;
    const destroy = () => {
      if (!stream.destroyed) stream.destroy();
    };
    const cleanup = () => {
      if (closed) return;
      closed = true;
      activeMediaStreams = Math.max(0, activeMediaStreams - 1);
      request.removeListener("aborted", destroy);
      response.removeListener("close", destroy);
      if (process.env.BRASA_DEBUG === "1")
        console.log(`BRasa stream: encerrado; ativos=${activeMediaStreams}`);
    };
    request.once("aborted", destroy);
    response.once("close", destroy);
    stream.once("error", (error) => {
      console.error(
        `BRasa stream: erro de leitura (${error.code || error.message}).`,
      );
      if (!response.destroyed) response.destroy(error);
    });
    stream.once("close", cleanup);
    stream.pipe(response);
  }

  return { serveStatic, serveMediaFile, createEtag };
}
