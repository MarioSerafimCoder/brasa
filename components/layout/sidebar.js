export default function Sidebar() {

    return `

        <aside class="sidebar">

            <div class="sidebar__logo">

                <h1>BRasa</h1>

            </div>

            <nav class="sidebar__nav">

                <ul>

                    <li class="active">

                        <a href="index.html">

                            <i data-lucide="house"></i>

                            <span>Início</span>

                        </a>

                    </li>

                    <li>

                        <a href="pages/search.html">

                            <i data-lucide="search"></i>

                            <span>Buscar</span>

                        </a>

                    </li>

                    <li>

                        <a href="pages/movies.html">

                            <i data-lucide="film"></i>

                            <span>Filmes</span>

                        </a>

                    </li>

                    <li>

                        <a href="#">

                            <i data-lucide="tv"></i>

                            <span>Séries</span>

                        </a>

                    </li>

                    <li>

                        <a href="#content">

                            <i data-lucide="heart"></i>

                            <span>Favoritos</span>

                        </a>

                    </li>

                    <li>

                        <button class="sidebar__sync" type="button" data-library-sync>

                            <i data-lucide="refresh-cw"></i>

                            <span>Atualizar</span>

                        </button>

                    </li>

                    <li>

                        <a href="pages/collection.html">

                            <i data-lucide="library"></i>

                            <span>Coleções</span>

                        </a>

                    </li>

                </ul>

            </nav>

            <div class="sidebar__footer">

                <a href="pages/settings.html">

                    <i data-lucide="settings"></i>

                    <span>Configurações</span>

                </a>

            </div>

        </aside>

    `;

}
