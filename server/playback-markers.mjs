/** Only explicit chapter labels, never scene detection or guessed fixed timings. Milliseconds. */
export function trustedChapterMarkers(chapters = [], durationSeconds = 0) {
    const duration = Number(durationSeconds) * 1000;
    if (!Number.isFinite(duration) || duration <= 0 || !Array.isArray(chapters)) return [];
    const result = [];
    for (const chapter of chapters) {
        const title = String(chapter.tags?.title || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLowerCase();
        const kind = /^(opening|intro|opening credits|abertura|op)$/.test(title) ? "intro"
            : /^(ending|ending credits|end credits|credits|creditos|encerramento|ed)$/.test(title) ? "credits" : "";
        const startMs = Math.round(Number(chapter.start_time) * 1000), endMs = Math.round(Number(chapter.end_time) * 1000);
        if (!kind || !Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs < 0 || endMs > duration + 1000 || endMs - startMs < 5000) continue;
        if (kind === "intro" && (startMs > Math.min(duration * .3, 600_000) || endMs - startMs > 300_000)) continue;
        if (kind === "credits" && (startMs < duration * .7 || endMs - startMs > 900_000)) continue;
        if (result.some(marker => marker.kind === kind || startMs < marker.endMs && endMs > marker.startMs)) continue;
        result.push({ kind, startMs, endMs: Math.min(endMs, duration), source: "embedded-chapter" });
    }
    return result.sort((a, b) => a.startMs - b.startMs);
}
