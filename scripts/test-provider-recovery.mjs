import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { checkProviderHealth, loadProviderHealth, saveProviderHealth } from "../server/provider-health.mjs";
import { createMetadataRetryStore } from "../server/metadata-retry-store.mjs";
import { resolveMovieAvailability } from "./sync-movies.mjs";
import { chooseSearchResult, findMovieOnOmdb } from "../server/sync-metadata-providers.mjs";

const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "brasa-recovery-"));
let now = Date.parse("2026-07-11T12:00:00.000Z");

try {
    assert.equal(chooseSearchResult([], "2001"), null);
    const candidates = [{ imdbID: "first", Year: "1990" }, { imdbID: "exact", Year: "2001" }];
    assert.equal(chooseSearchResult(candidates, "2001").imdbID, "exact");
    assert.equal(chooseSearchResult(candidates).imdbID, "first");
    const originalFetch = globalThis.fetch;
    const requests = [];
    try {
        globalThis.fetch = async url => {
            requests.push(new URL(url));
            const params = new URL(url).searchParams;
            const data = params.has("s") ? { Search: candidates }
                : params.has("i") ? { Type: "movie", imdbID: params.get("i") }
                : { Response: "False" };
            return { ok: true, json: async () => data };
        };
        const found = await findMovieOnOmdb({ apiKey: "test", parsed: { title: "Example", year: "2001", candidates: [] }, override: {} });
        assert.equal(found.imdbID, "exact", "fallback por busca deve preservar a escolha pelo ano");
        assert.equal(requests.at(-1).searchParams.get("i"), "exact");
    } finally { globalThis.fetch = originalFetch; }
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

    assert.deepEqual(
        resolveMovieAvailability(
            { video: "assets/movies/filme.mkv" },
            { sourceAvailability: new Map([["assets/movies", false]]) },
        ),
        { fileStatus: "source-offline", playable: false },
        "uma unidade desconectada não pode transformar todo o catálogo em arquivos removidos",
    );
    assert.deepEqual(
        resolveMovieAvailability(
            { video: "assets/movies/filme.mkv" },
            {
                sourceAvailability: new Map([["assets/movies", true]]),
                availablePaths: new Set(["assets/movies/filme.mkv"]),
            },
        ),
        { fileStatus: "available", playable: true },
    );

    console.log("Recuperação automática: 11 cenários aprovados.");
} finally {
    await fs.rm(rootDir, { recursive: true, force: true });
}
