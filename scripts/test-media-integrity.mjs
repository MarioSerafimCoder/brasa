import assert from "node:assert/strict";
import path from "node:path";
import { mediaDiagnostics, quarantinePath, scanFingerprint } from "../server/media-integrity.mjs";

assert.equal(mediaDiagnostics("", 0).suspicious, false);
assert.equal(mediaDiagnostics("0x00 at pos 123 invalid as first byte of an EBML number", 0).suspicious, true);
assert.equal(mediaDiagnostics("error while decoding MB 67 60, bytestream -15", 0).suspicious, true);
assert.equal(mediaDiagnostics("", 1).suspicious, true);
const destination = quarantinePath("F:\\BRasa\\Series", "series", "X-Men '97\\Temporada 02\\episodio.mkv");
assert.equal(destination, path.resolve("F:\\BRasa\\Quarentena BRasa\\series\\X-Men '97\\Temporada 02\\episodio.mkv"));
assert.throws(() => quarantinePath("F:\\BRasa\\Series", "series", "..\\fora.mkv"));
assert.equal(scanFingerprint("video.mkv", { size: 10, mtimeMs: 20.4 }), "video.mkv|10|20");
console.log("Integridade de mídia: classificação, fingerprint e quarentena aprovados.");
