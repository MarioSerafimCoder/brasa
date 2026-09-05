import assert from "node:assert/strict";
import { resolveByteRange } from "../server/http-range.mjs";

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
