import { installSyncStatus } from "./sync-status.js";

const transitionMs = 90;
let installed = false;
let isNavigating = false;

export function installPageTransitions() {
    if (installed) return;
    installed = true;

    markAppLoaded();
    installSyncStatus();

    window.addEventListener("pageshow", () => {
        document.documentElement.classList.remove("brasa-page-leaving");
        document.documentElement.classList.add("brasa-page-ready", "brasa-page-entering");

        window.setTimeout(() => {
            document.documentElement.classList.remove("brasa-page-entering");
        }, transitionMs + 80);
    });

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

    if (options.instant || prefersReducedMotion()) {
        window.location.href = target.href;
        return;
    }

    isNavigating = true;
    document.documentElement.classList.remove("brasa-page-entering");
    document.documentElement.classList.add("brasa-page-leaving");

    window.setTimeout(() => {
        window.location.href = target.href;
    }, transitionMs);
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

function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function markAppLoaded() {
    try {
        window.sessionStorage.setItem("brasa:app-loaded", "true");
    } catch {
        // Storage may be unavailable in restricted browser modes.
    }
}
