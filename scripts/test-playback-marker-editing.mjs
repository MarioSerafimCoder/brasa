import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createPlaybackMarkerStore, validatePlaybackMarkers } from "../server/playback-markers.mjs";
import { createAdminController } from "../server/admin-controller.mjs";
import { formatMarkerTime, parseMarkerTime } from "../admin/playback-markers-editor.js";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-marker-edit-"));
const embedded = [{ kind: "intro", startMs: 0, endMs: 60000, source: "embedded-chapter" }];
const probe = { duration: 1200, fingerprint: { size: 1234, mtimeMs: 5 }, chapterMarkers: embedded };
const markers = [{ kind: "intro", startMs: 45000, endMs: 95000 }, { kind: "credits", startMs: 1100000, endMs: 1150000 }];
try {
    const store = createPlaybackMarkerStore(root);
    assert.deepEqual((await store.get("episode:a", probe)).markers, embedded);
    await store.save("episode:a", markers, probe);
    const saved = await createPlaybackMarkerStore(root).get("episode:a", probe);
    assert.equal(saved.mode, "manual");
    assert.equal(saved.markers[1].endMs, 1150000, "preserva os 50s de cena pós-créditos");
    assert.ok(saved.markers.every(marker => marker.source === "manual"));
    await store.save("episode:a", [], probe);
    assert.deepEqual((await store.get("episode:a", probe)).markers, [], "lista vazia desativa os pulos automáticos");
    await store.save("episode:a", null, probe);
    assert.deepEqual((await store.get("episode:a", probe)).markers, embedded);
    await Promise.all(Array.from({ length: 8 }, (_, index) => store.save(`movie:${index}`, markers, probe)));
    for (let index = 0; index < 8; index++) assert.equal((await store.get(`movie:${index}`, probe)).mode, "manual");
    const changed = await store.get("movie:0", { ...probe, fingerprint: { ...probe.fingerprint, size: 4444 } });
    assert.equal(changed.stale, true);
    assert.deepEqual(changed.markers, embedded, "arquivo substituído não usa tempos antigos");
    for (const invalid of [[{kind:"intro",startMs:-1,endMs:10}], [{kind:"intro",startMs:20,endMs:10}], [{kind:"credits",startMs:0,endMs:1200001}], [markers[0], markers[0]], [{...markers[0],endMs:1150000},markers[1]], [{kind:"unknown",startMs:0,endMs:1}]]) {
        assert.throws(() => validatePlaybackMarkers(invalid, 1200000));
    }
    assert.throws(() => validatePlaybackMarkers(markers, 0));
    assert.equal(parseMarkerTime("125:30.250"), 7530250);
    assert.equal(parseMarkerTime("1:30,5"), 90500);
    assert.equal(formatMarkerTime(7530250), "125:30.250");
    assert.throws(() => parseMarkerTime("1:90"));

    let writes = 0, sent;
    const controller = createAdminController({
        auth: { requireSession: (request, {csrf}) => { if (!request.loggedIn || (csrf && !request.csrf)) throw new Error("unauthorized"); return {}; } },
        logs: { add: async () => {} }, readBody: async request => request.body,
        send: (_response, status, body) => { sent = {status,body}; },
        services: { playbackMarkers: async (key, values) => { if (values !== undefined) writes++; return values === undefined ? store.get(key, probe) : store.save(key, values, probe); } },
    });
    const url = new URL("http://localhost/api/admin/library/episode:a/markers");
    for (const method of ["PUT", "DELETE"]) {
        await assert.rejects(() => controller({method,loggedIn:true,body:{markers}}, {}, url));
        await assert.rejects(() => controller({method,csrf:true,body:{markers}}, {}, url));
    }
    assert.equal(writes,0);
    await controller({method:"PUT",loggedIn:true,csrf:true,body:{markers}}, {}, url);
    assert.equal(sent.status,200);
    assert.equal(sent.body.data.mode,"manual");
    await controller({method:"DELETE",loggedIn:true,csrf:true}, {}, url);
    assert.equal(sent.body.data.mode,"automatic");
    console.log("Marcações: edição, desativação, restauração, concorrência, arquivo substituído e autorização aprovados.");
} finally { await fs.rm(root, { recursive: true, force: true }); }
