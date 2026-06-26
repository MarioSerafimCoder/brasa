// ==========================================================
// BRasa
// Application Bootstrap
// ==========================================================

import { render } from "./utils/renderer.js";

import Sidebar from "../components/layout/sidebar.js";
import Header from "../components/layout/header.js";

import HomePage from "./pages/home.js";

document.addEventListener("DOMContentLoaded", init);

/* ==========================================================
   INIT
========================================================== */

function init() {

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

}