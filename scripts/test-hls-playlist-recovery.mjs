import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { inspectHlsOutput, synchronizeHlsPlaylists } from "../server/hls-session.mjs";

const root = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-hls-playlist-"));
const qualityDirectory = path.join(root, "1080p");

try {
    await fs.mkdir(qualityDirectory, { recursive: true });
    await fs.writeFile(path.join(root, "master.m3u8"), "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=6500000\n1080p/index.m3u8\n");
    await fs.writeFile(path.join(qualityDirectory, "index.m3u8"), playlist([2], false));
    await fs.writeFile(path.join(qualityDirectory, "index.m3u8.tmp"), playlist([2, 2, 1.5], true));
    for (let index = 0; index < 3; index++) {
        await fs.writeFile(path.join(qualityDirectory, `seg-${String(index).padStart(6, "0")}.ts`), Buffer.alloc(32, index + 1));
    }

    await synchronizeHlsPlaylists(root, [{ id: "1080p" }]);
    const published = await fs.readFile(path.join(qualityDirectory, "index.m3u8"), "utf8");
    assert.match(published, /seg-000002\.ts/);
    assert.match(published, /#EXT-X-ENDLIST/);

    const complete = await inspectHlsOutput(root, [{ id: "1080p" }], { complete: true, expectedDurationSeconds: 5.5 });
    assert.equal(complete.valid, true, complete.reason);
    assert.equal(complete.durationSeconds, 5.5);

    await fs.rm(path.join(qualityDirectory, "seg-000002.ts"));
    const broken = await inspectHlsOutput(root, [{ id: "1080p" }], { complete: true, expectedDurationSeconds: 5.5 });
    assert.equal(broken.valid, false);
    assert.match(broken.reason, /seg-000002\.ts ausente/);

    console.log("Recuperação HLS: playlist temporária promovida e segmentos validados.");
} finally {
    await fs.rm(root, { recursive: true, force: true });
}

function playlist(durations, complete) {
    const segments = durations.map((duration, index) => `#EXTINF:${duration.toFixed(3)},\nseg-${String(index).padStart(6, "0")}.ts`).join("\n");
    return `#EXTM3U\n#EXT-X-VERSION:6\n#EXT-X-TARGETDURATION:2\n#EXT-X-PLAYLIST-TYPE:EVENT\n${segments}\n${complete ? "#EXT-X-ENDLIST\n" : ""}`;
}
