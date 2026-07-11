import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { checkProviderHealth, loadProviderHealth, saveProviderHealth } from "../server/provider-health.mjs";
import { createMetadataRetryStore } from "../server/metadata-retry-store.mjs";

const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-recovery-"));
let now = Date.parse("2026-07-11T12:00:00.000Z");

try {
    const fakeFetch = async (url) => ({ ok: !String(url).includes("opensubtitles"), status: String(url).includes("opensubtitles") ? 503 : 200 });
    const checked = await checkProviderHealth({ omdbKey: "secret-omdb", tmdbKey: "secret-tmdb", openSubtitlesKey: "secret-subtitle" }, { fetch: fakeFetch });
    assert.equal(checked.providers.omdb.available, true);
    assert.equal(checked.providers.tmdb.available, true);
    assert.equal(checked.providers.openSubtitles.available, false);

    await saveProviderHealth(rootDir, checked);
    const healthText = await fs.readFile(path.join(rootDir, "data", "provider-health.json"), "utf8");
    assert.equal(healthText.includes("secret-"), false, "o diagnóstico nunca deve persistir chaves");

    const recovered = await saveProviderHealth(rootDir, await checkProviderHealth({ omdbKey: "x", tmdbKey: "x", openSubtitlesKey: "x" }, { fetch: async () => ({ ok: true, status: 200 }) }));
    assert.deepEqual(recovered.recovered, ["openSubtitles"]);
    assert.equal((await loadProviderHealth(rootDir)).providers.openSubtitles.available, true);

    const retry = createMetadataRetryStore(rootDir, { now: () => now });
    await retry.fail("movie:1", "metadata", "indisponível");
    assert.equal(await retry.due("movie:1", "metadata"), false);
    now += 5 * 60_000;
    assert.equal(await retry.due("movie:1", "metadata"), true);
    await retry.fail("movie:1", "metadata", "indisponível novamente");
    now += 29 * 60_000;
    assert.equal(await retry.due("movie:1", "metadata"), false);
    now += 60_000;
    assert.equal(await retry.due("movie:1", "metadata"), true);
    await retry.success("movie:1", "metadata");
    assert.equal((await retry.summary()).pendingItems, 0);

    console.log("Recuperação automática: 11 cenários aprovados.");
} finally {
    await fs.rm(rootDir, { recursive: true, force: true });
}
