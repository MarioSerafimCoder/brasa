export default function Sidebar() {
    return `
        <aside class="sidebar">
            <div class="sidebar__logo">
                <img src="Logo/Logo%20colorida.png" alt="BRasa">
            </div>

            <nav class="sidebar__nav" aria-label="Navegação principal">
                <ul>
                    <li class="active">
                        <a href="index.html">
                            <span>Início</span>
                        </a>
                    </li>
                    <li>
                        <a href="pages/movies.html">
                            <span>Filmes</span>
                        </a>
                    </li>
                    <li>
                        <a href="pages/series.html">
                            <span>Séries</span>
                        </a>
                    </li>
                    <li>
                        <a href="#favorites">
                            <span>Favoritos</span>
                        </a>
                    </li>
                    <li>
                        <a href="pages/collection.html">
                            <span>Coleções</span>
                        </a>
                    </li>
                </ul>
            </nav>

            <div class="sidebar__footer" aria-label="Ações rápidas">
                <button class="header__search topbar-icon" type="button" aria-label="Pesquisar">
                    <i data-lucide="search"></i>
                </button>
                <a class="topbar-icon" href="pages/collection.html" aria-label="Coleções">
                    <i data-lucide="grid-3x3"></i>
                </a>
                <a class="topbar-icon" href="pages/settings.html" aria-label="Configurações">
                    <i data-lucide="settings"></i>
                </a>
                <button class="profile-switcher" type="button" data-profile-switcher>
                    <span class="profile-switcher__avatar" data-profile-initials>?</span>
                    <span class="profile-switcher__name" data-profile-name>Perfis</span>
                    <i data-lucide="chevron-down"></i>
                </button>
            </div>
        </aside>
    `;
}
