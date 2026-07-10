// ==========================================================
// BRasa
// Application Bootstrap
// ==========================================================

import { render } from "./utils/renderer.js";

import Sidebar from "../components/layout/sidebar.js?v=streaming-20260709a";
import Header from "../components/layout/header.js?v=streaming-20260709a";
import { applyPreferences } from "./utils/preferences.js";
import { initializeProfiles } from "./utils/profiles.js";
import { installPageTransitions, navigateTo } from "./utils/navigation.js";
import { installTmdbImageFallbacks } from "./utils/tmdb-images.js";

import HomePage from "./pages/home.js?v=streaming-20260709a";

document.addEventListener("DOMContentLoaded", init);

/* ==========================================================
   INIT
========================================================== */

async function init() {

    applyPreferences();
    installPageTransitions();
    installTmdbImageFallbacks();

    renderLayout();

    await initializeProfiles();

    HomePage();
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

}

