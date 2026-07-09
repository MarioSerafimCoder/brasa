let installed = false;
let lastState = "";
let hideTimer = null;

export function installSyncStatus() {
    if (installed) return;
    installed = true;

    const status = document.createElement("div");
    status.className = "sync-status";
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    status.hidden = true;
    document.body.appendChild(status);

    pollSyncStatus(status);
    window.setInterval(() => pollSyncStatus(status), 3500);
}

async function pollSyncStatus(element) {
    try {
        const response = await fetch("/api/sync/status", { cache: "no-store" });
        if (!response.ok) return;

        const status = await response.json();
        updateSyncStatus(element, status);
    } catch {
        element.hidden = true;
    }
}

function updateSyncStatus(element, status) {
    if (!status?.state || status.state === "idle") {
        if (!lastState) {
            element.hidden = true;
        }
        return;
    }

    const changed = status.state !== lastState;

    if (status.state !== "syncing" && !changed) {
        return;
    }

    lastState = status.state;
    element.textContent = status.message || "Atualizando biblioteca...";
    element.dataset.state = status.state;
    element.hidden = false;

    window.clearTimeout(hideTimer);

    if (status.state !== "syncing" && changed) {
        hideTimer = window.setTimeout(() => {
            element.hidden = true;
        }, 4200);
    }
}
