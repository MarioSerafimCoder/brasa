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
                <button class="profile-pill profile-pill--kids topbar-kids" type="button" data-kids-toggle aria-pressed="false" aria-label="Perfil Kids">
                    <i data-lucide="smile"></i>
                    <span data-kids-label>Kids</span>
                </button>
                <button class="topbar-avatar" type="button" aria-label="Perfil Mário">
                    <i data-lucide="user"></i>
                </button>
            </div>
        </aside>
    `;
}
