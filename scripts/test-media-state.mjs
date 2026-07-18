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
    assert.equal(state.version, 3);
    assert.equal(state.settings.hlsSegmentSeconds, 2);
    assert.equal(state.settings.hlsStartBufferSeconds, 4);
    assert.equal(state.settings.hlsTargetBufferSeconds, 30);
    assert.equal(state.settings.hlsMaxBufferSeconds, 90);
    console.log("Estado de mídia: migração para buffers HLS de baixa latência aprovada.");
} finally {
    await fs.rm(root, { recursive: true, force: true });
}
