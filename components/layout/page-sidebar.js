import { escapeAttribute, escapeHtml } from "../../js/utils/html.js";

const items = [
    { id: "home", href: "../index.html?intro=skip", label: "Início" },
    { id: "search", href: "search.html", label: "Buscar" },
    { id: "movies", href: "movies.html", label: "Filmes" },
    { id: "series", href: "series.html", label: "Séries" },
    { id: "collection", href: "collection.html", label: "Coleções" },
    { id: "settings", href: "settings.html", label: "Configurações" }
];

export default function PageSidebar(active = "") {
    return `
        <div class="page-sidebar__logo">
            <img src="../Logo/Logo%20colorida.png" alt="BRasa">
        </div>
        <nav aria-label="Navegação principal">
            ${items.map((item) => `
                <a class="${item.id === active ? "active" : ""}" href="${escapeAttribute(item.href)}">
                    ${escapeHtml(item.label)}
                </a>
            `).join("")}
        </nav>
        <div class="page-sidebar__actions" aria-label="Ações rápidas">
            <a class="topbar-icon" href="search.html" aria-label="Buscar">
                <i data-lucide="search"></i>
            </a>
            <button class="profile-pill profile-pill--kids topbar-kids" type="button" data-kids-toggle aria-pressed="false" aria-label="Perfil Kids">
                <i data-lucide="smile"></i>
                <span data-kids-label>Kids</span>
            </button>
            <button class="topbar-avatar" type="button" aria-label="Perfil Mário">
                <i data-lucide="user"></i>
            </button>
        </div>
    `;
}
