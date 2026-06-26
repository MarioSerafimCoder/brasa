import Button from "../shared/button.js";

export default function Hero({

    title = "Título do Filme",

    subtitle = "Uma breve descrição do filme.",

    backdrop = "",

    year = "",

    duration = "",

    genres = [],

    rating = "",

    quality = "",

    progress = 0

} = {}) {

    const hero = document.querySelector("#hero");

    if (!hero) return;

    hero.innerHTML = `

        <section class="hero">

            <div class="hero__background">

                <img
                    src="${backdrop}"
                    alt="${title}"
                >

            </div>

            <div class="hero__overlay"></div>

            <div class="hero__content">

                <div class="hero__meta">

                    <span>${year}</span>

                    <span>${duration}</span>

                    ${
                        rating
                        ? `<span>★ ${rating}</span>`
                        : ""
                    }

                    ${
                        quality
                        ? `<span>${quality}</span>`
                        : ""
                    }

                </div>

                <h1 class="hero__title">

                    ${title}

                </h1>

                <p class="hero__description">

                    ${subtitle}

                </p>

                <div class="hero__genres">

                    ${genres.map(g => `
                        <span>${g}</span>
                    `).join("")}

                </div>

                <div class="hero__actions">

                    ${Button({
                        text: "Assistir",
                        icon: "▶",
                        variant: "primary"
                    })}

                    ${Button({
                        text: "Detalhes",
                        variant: "secondary"
                    })}

                </div>

                ${
                    progress > 0
                    ? `
                    <div class="hero__progress">

                        <div
                            class="hero__progress-bar"
                            style="width:${progress}%">
                        </div>

                    </div>
                    `
                    : ""
                }

            </div>

        </section>

    `;

}