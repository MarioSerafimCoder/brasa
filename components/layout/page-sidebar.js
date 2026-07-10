import { escapeAttribute, escapeHtml } from "../../js/utils/html.js";

const items = [
    { id: "home", href: "../index.html", label: "Início" },
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
            <button class="profile-switcher" type="button" data-profile-switcher>
                <span class="profile-switcher__avatar" data-profile-initials>?</span>
                <span class="profile-switcher__name" data-profile-name>Perfis</span>
                <i data-lucide="chevron-down"></i>
            </button>
        </div>
    `;
}
