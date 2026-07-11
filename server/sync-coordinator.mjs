export function createSyncCoordinator({ runSync, afterSync = async () => {}, onStatusChange = () => {} }) {
    let running = false, pending = false, pendingReasons = new Set(), currentPromise = null;
    let status = { state: "idle", message: "Biblioteca pronta.", reasons: [], startedAt: "", finishedAt: "", output: "" };

    function publish(patch) { status = { ...status, ...patch }; onStatusChange({ ...status }); }
    function requestSync(reason = "manual") {
        pendingReasons.add(String(reason));
        if (running) { pending = true; publish({ pending: true, reasons: [...pendingReasons] }); return currentPromise; }
        currentPromise = drain(); return currentPromise;
    }
    async function drain() {
        let lastResult;
        do {
            running = true; pending = false;
            const reasons = [...pendingReasons]; pendingReasons.clear();
            publish({ state: "syncing", message: "Atualizando biblioteca...", reasons, pending: false, startedAt: new Date().toISOString(), finishedAt: "" });
            try {
                lastResult = await runSync(reasons);
                const ok = lastResult?.code === undefined || lastResult.code === 0;
                publish({ state: ok ? "complete" : "error", message: ok ? "Biblioteca atualizada." : "A atualização falhou.", output: lastResult?.output || "", finishedAt: new Date().toISOString() });
                if (ok) await afterSync(lastResult, reasons);
            } catch (error) {
                lastResult = { code: 1, error, output: error.message };
                publish({ state: "error", message: "A atualização falhou.", output: error.message, finishedAt: new Date().toISOString() });
            }
        } while (pending || pendingReasons.size);
        running = false; currentPromise = null; publish({ pending: false }); return lastResult;
    }
    return { requestSync, getStatus: () => ({ ...status }), isRunning: () => running, hasPendingSync: () => pending || pendingReasons.size > 0 };
}
