export default function Header() {

    return `

        <header class="header">

            <div class="header__left">

                <div>

                    <p class="header__greeting">

                        Boa noite

                    </p>

                    <h2>

                        Mário

                    </h2>

                </div>

            </div>

            <div class="header__right">

                <button
                    class="header__search"
                    aria-label="Pesquisar">

                    <i data-lucide="search"></i>

                </button>

                <button
                    class="header__notification"
                    aria-label="Notificações">

                    <i data-lucide="bell"></i>

                </button>

                <button
                    class="header__profile"
                    aria-label="Perfil">

                    <i data-lucide="user"></i>

                </button>

            </div>

        </header>

    `;

}
