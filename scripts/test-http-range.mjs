import assert from "node:assert/strict";
import { resolveByteRange } from "../server/http-range.mjs";
import { createStaticFiles } from "../server/static-files.mjs";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import { once } from "node:events";
import { tmpdir } from "node:os";
import path from "node:path";

const size = 1_000;
assert.deepEqual(resolveByteRange(undefined, size), { satisfiable: true, partial: false, start: 0, end: 999 });
assert.deepEqual(resolveByteRange("bytes=0-499", size), { satisfiable: true, partial: true, start: 0, end: 499 });
assert.deepEqual(resolveByteRange("bytes=500-", size), { satisfiable: true, partial: true, start: 500, end: 999 });
assert.deepEqual(resolveByteRange("bytes=-200", size), { satisfiable: true, partial: true, start: 800, end: 999 });
assert.deepEqual(resolveByteRange("bytes=900-1200", size), { satisfiable: true, partial: true, start: 900, end: 999 });
for (const value of ["bytes=", "bytes=-0", "bytes=1000-", "bytes=500-100", "bytes=0-1,4-5", "items=0-1"]) {
    assert.equal(resolveByteRange(value, size).satisfiable, false, value);
}
console.log("HTTP Range: ausente, aberto, sufixo, limite e inválidos aprovados.");

// Exercise the extracted file service over HTTP, independently of server bootstrap/data.
const fixture = await mkdtemp(path.join(tmpdir(), "brasa-static-test-"));
const files = createStaticFiles({
    rootDir: fixture,
    startupNetworkSettings: { lanAccessEnabled: true },
    sendJson(response, status, body) {
        response.writeHead(status, { "Content-Type": "application/json" });
        response.end(JSON.stringify(body));
    },
});
const server = createServer((request, response) => {
    files.serveStatic(new URL(request.url, "http://localhost").pathname, request, response)
        .catch(error => response.destroy(error));
});
try {
    await writeFile(path.join(fixture, "video.mp4"), Buffer.from("0123456789"));
    await writeFile(path.join(fixture, "index.html"), "<h1>BRasa</h1>");
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    const base = `http://127.0.0.1:${server.address().port}`;
    const response = await fetch(`${base}/video.mp4`, { headers: { Range: "bytes=3-6" } });
    assert.equal(response.status, 206);
    assert.equal(response.headers.get("content-range"), "bytes 3-6/10");
    assert.equal(await response.text(), "3456");
    const head = await fetch(`${base}/video.mp4`, { method: "HEAD" });
    assert.equal(head.status, 200);
    assert.equal(head.headers.get("content-length"), "10");
    assert.equal(await head.text(), "");
    const invalid = await fetch(`${base}/video.mp4`, { headers: { Range: "bytes=10-" } });
    assert.equal(invalid.status, 416);
    assert.equal(invalid.headers.get("content-range"), "bytes */10");
    await invalid.arrayBuffer();
    const page = await fetch(`${base}/`);
    assert.equal(await page.text(), "<h1>BRasa</h1>");
    const cached = await fetch(`${base}/`, { headers: { "If-None-Match": page.headers.get("etag") } });
    assert.equal(cached.status, 304);
    const missing = await fetch(`${base}/missing`);
    assert.equal(missing.status, 404);
    await missing.arrayBuffer();
    console.log("Arquivos extraídos: HTTP real, Range, HEAD, ETag e 404 aprovados.");
} finally {
    server.closeAllConnections();
    if (server.listening) await new Promise(resolve => server.close(resolve));
    const resolved = path.resolve(fixture);
    assert.equal(path.dirname(resolved), path.resolve(tmpdir()));
    assert.ok(path.basename(resolved).startsWith("brasa-static-test-"));
    await rm(resolved, { recursive: true, force: true });
}
