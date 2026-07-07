import Button from "../shared/button.js";

export default function Hero({

    id = "",

    title = "Título do Filme",

    overview = "Uma breve descrição do filme.",

    backdrop = "",

    year = "",

    duration = "",

    genres = [],

    rating = "",

    quality = "",

    progress = 0

} = {}) {

    return `

        <section class="hero" data-movie-id="${id}">

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

                    ${overview}

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
                        variant: "primary",
                        classes: "js-open-movie",
                        id: "hero-play"
                    })}

                    ${Button({
                        text: "Detalhes",
                        variant: "secondary",
                        classes: "js-open-movie",
                        id: "hero-details"
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
