// ==========================================================
// BRasa
// Application Bootstrap
// ==========================================================

import { render } from "./utils/renderer.js";

import Sidebar from "../components/layout/sidebar.js";
import Header from "../components/layout/header.js";
import { applyPreferences } from "./utils/preferences.js";
import { bindLibrarySync } from "./utils/library-sync.js";

import HomePage from "./pages/home.js";

document.addEventListener("DOMContentLoaded", init);

/* ==========================================================
   INIT
========================================================== */

function init() {

    applyPreferences();

    renderLayout();

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
        window.location.href = "pages/search.html";
    });

    bindLibrarySync();

}
