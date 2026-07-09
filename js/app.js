// ==========================================================
// BRasa
// Application Bootstrap
// ==========================================================

import { render } from "./utils/renderer.js";

import Sidebar from "../components/layout/sidebar.js?v=streaming-20260709a";
import Header from "../components/layout/header.js?v=streaming-20260709a";
import { applyPreferences } from "./utils/preferences.js";
import { bindKidsModeToggle } from "./utils/kids-mode.js?v=streaming-20260709a";
import { installPageTransitions, navigateTo } from "./utils/navigation.js";
import { installTmdbImageFallbacks } from "./utils/tmdb-images.js";

import HomePage from "./pages/home.js?v=streaming-20260709a";

document.addEventListener("DOMContentLoaded", init);
const splashFallbackMs = 9000;

/* ==========================================================
   INIT
========================================================== */

function init() {

    applyPreferences();
    installPageTransitions();
    installTmdbImageFallbacks();

    renderLayout();

    HomePage();

    finishSplash();

}

function finishSplash() {

    const splash = document.getElementById("appSplash");
    const skipSplash = Boolean(window.brasaSkipSplash);
    const shouldPlayIntro = Boolean(window.brasaShouldPlayIntro);

    if (skipSplash || !shouldPlayIntro || !splash) {

        document.documentElement.classList.add("brasa-app-ready");
        document.documentElement.classList.remove("brasa-splash-pending", "brasa-splash-skip");
        splash?.remove();
        return;

    }

    const video = splash.querySelector("video");
    const skipButton = splash.querySelector("[data-splash-skip]");
    let finished = false;
    let fallbackTimer = null;

    const finish = () => {
        if (finished) return;

        finished = true;
        window.clearTimeout(fallbackTimer);

        video?.pause();

        document.documentElement.classList.add("brasa-app-ready");
        document.documentElement.classList.remove("brasa-splash-pending");

        splash.classList.add("app-splash--done");
        window.setTimeout(() => splash.remove(), 520);
    };

    fallbackTimer = window.setTimeout(finish, splashFallbackMs);
    skipButton?.addEventListener("click", finish, { once: true });
    document.addEventListener("brasa:splash-skip", finish, { once: true });

    if (window.brasaSplashSkipRequested) {
        finish();
        return;
    }

    if (!video) {
        finish();
        return;
    }

    video.addEventListener("ended", finish, { once: true });
    video.addEventListener("error", finish, { once: true });
    video.addEventListener("stalled", () => {
        window.setTimeout(finish, 1400);
    }, { once: true });

    const playPromise = video.play?.();

    if (playPromise?.catch) {
        playPromise.catch(finish);
    }

}

/* ==========================================================
   LAYOUT
========================================================== */

function renderLayout() {

    render(
        "#sidebar",
        Sidebar()
    );

    render(
        "#header",
        Header()
    );

    document.querySelector(".header__search")?.addEventListener("click", () => {
        navigateTo("pages/search.html");
    });

    bindKidsModeToggle();

}

