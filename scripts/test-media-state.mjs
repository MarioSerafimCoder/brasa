import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createMediaStateStore } from "../server/media-state.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-media-state-"));
try {
    await fs.mkdir(path.join(root, "data"), { recursive: true });
    await fs.writeFile(path.join(root, "data", "media-state.json"), JSON.stringify({ version: 2, settings: { hlsSegmentSeconds: 4, hlsStartBufferSeconds: 12 }, items: {} }));
    const state = await createMediaStateStore(root).all();
    assert.equal(state.version, 5);
    assert.equal(state.settings.autoPrepare, false);
    assert.equal(state.settings.hlsSegmentSeconds, 2);
    assert.equal(state.settings.hlsStartBufferSeconds, 12);
    assert.equal(state.settings.hlsTargetBufferSeconds, 30);
    assert.equal(state.settings.hlsMaxBufferSeconds, 90);
    const persisted = JSON.parse(await fs.readFile(path.join(root, "data", "media-state.json"), "utf8"));
    assert.equal(persisted.version, 5);
    assert.equal(persisted.settings.hlsStartBufferSeconds, 12);
    console.log("Estado de mídia: baixa latência e prioridade da reprodução aprovadas.");
} finally {
    await fs.rm(root, { recursive: true, force: true });
}
