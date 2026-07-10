import { installSyncStatus } from "./sync-status.js";

let installed = false;
let isNavigating = false;

export function installPageTransitions() {
    if (installed) return;
    installed = true;

    markAppLoaded();
    installSyncStatus();

    resetNavigationState();
    window.addEventListener("pageshow", resetNavigationState);

    document.addEventListener("click", (event) => {
        const link = event.target.closest("a[href]");

        if (!link || !shouldTransitionLink(link, event)) return;

        event.preventDefault();
        navigateTo(link.href);
    });
}

export function navigateTo(url, options = {}) {
    if (!url || isNavigating) return;

    const target = new URL(url, window.location.href);

    if (target.href === window.location.href) return;

    isNavigating = true;

    if (options.replace) {
        window.location.replace(target.href);
        return;
    }

    window.location.assign(target.href);
}

function shouldTransitionLink(link, event) {
    if (event.defaultPrevented) return false;
    if (event.button !== 0) return false;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false;
    if (link.target && link.target !== "_self") return false;
    if (link.hasAttribute("download")) return false;
    if (link.dataset.navigation === "instant") return false;

    const target = new URL(link.href, window.location.href);

    if (target.origin !== window.location.origin) return false;

    const current = new URL(window.location.href);
    const sameDocument =
        target.pathname === current.pathname &&
        target.search === current.search &&
        target.hash;

    return !sameDocument;
}

function resetNavigationState() {
    isNavigating = false;
    document.documentElement.classList.remove(
        "brasa-page-ready",
        "brasa-page-entering",
        "brasa-page-leaving"
    );
}

function markAppLoaded() {
    try {
        window.sessionStorage.setItem("brasa:app-loaded", "true");
    } catch {
        // Storage may be unavailable in restricted browser modes.
    }
}
