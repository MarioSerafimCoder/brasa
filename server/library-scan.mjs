import crypto from "node:crypto";

// Both launchers and paired TVs use one asynchronous scan. The coordinator still
// serializes startup/watcher events and any follow-up pass they require.
export function createLibraryScan({ coordinator, getProgress = () => ({}) }) {
    let scan = { id: "", state: "idle", message: "Nenhuma busca solicitada.", progress: 0 };

    function status() {
        const progress = getProgress();
        return { ...scan, progress: scan.state === "syncing"
            ? Math.min(99, Math.max(0, progress.state === "syncing" ? Number(progress.progress) || 0 : 0)) : scan.progress };
    }

    function request(reason = "manual") {
        if (scan.state === "syncing") return status();
        scan = { id: crypto.randomUUID(), state: "syncing", message: "Buscando novos títulos no computador…", progress: 0 };
        // Return the acknowledgement before starting expensive work; a TV HTTP
        // request must not stay open for the duration of a full library scan.
        Promise.resolve().then(() => coordinator.requestSync(reason)).then((result) => {
            if (result?.code !== undefined && result.code !== 0) throw new Error("sync failed");
            scan = { ...scan, state: "complete", progress: 100, message: "Busca concluída. Catálogo atualizado." };
        }).catch(() => {
            scan = { ...scan, state: "error", progress: 0, message: "Não foi possível concluir a busca. Confira as pastas no computador e tente novamente." };
        });
        return status();
    }

    return { request, status };
}
