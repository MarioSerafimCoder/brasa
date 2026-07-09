import Button from "../shared/button.js";
import { escapeAttribute, escapeHtml } from "../../js/utils/html.js";
import { movieImageUrl, tmdbImageFallbackAttributes } from "../../js/utils/tmdb-images.js";

export default function Hero({

    id = "",

    title = "Título do Filme",

    overview = "Uma breve descrição do filme.",

    backdrop = "",

    poster = "",

    year = "",

    duration = "",

    genres = [],

    rating = "",

    quality = "",

    progress = 0

} = {}) {
    const fallbackBackdrop = backdrop || poster || "";
    const heroImage = movieImageUrl(arguments[0] || {}, {
        type: "backdrop",
        size: "w1280",
        fallback: fallbackBackdrop
    });

    return `

        <section class="hero" data-movie-id="${escapeAttribute(id)}">

            <div class="hero__background">

                <img
                    src="${escapeAttribute(heroImage)}"
                    alt="${escapeAttribute(title)}"
                    ${tmdbImageFallbackAttributes(fallbackBackdrop)}
                >

            </div>

            <div class="hero__overlay"></div>

            <div class="hero__content">

                <div class="hero__meta">

                    <span>${escapeHtml(year)}</span>

                    <span>${escapeHtml(duration)}</span>

                    ${
                        rating
                        ? `<span>★ ${rating}</span>`
                        : ""
                    }

                    ${
                        quality
                        ? `<span>${escapeHtml(quality)}</span>`
                        : ""
                    }

                </div>

                <h1 class="hero__title">

                    ${escapeHtml(title)}

                </h1>

                <p class="hero__description">

                    ${escapeHtml(overview)}

                </p>

                <div class="hero__genres">

                    ${genres.map(g => `
                        <span>${escapeHtml(g)}</span>
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
                        <span class="hero__progress-label">${Math.round(Math.min(100, Math.max(0, Number(progress) || 0)))}% assistido</span>

                        <div
                            class="hero__progress-bar"
                             style="width:${Math.min(100, Math.max(0, Number(progress) || 0))}%">
                        </div>

                    </div>
                    `
                    : ""
                }

            </div>

        </section>

    `;

}

export function HeroSlider({ movies = [] } = {}) {
    const slides = movies.filter(Boolean).slice(0, 4);

    if (!slides.length) return Hero();

    return `
        <section class="hero hero--slider" data-hero-slider data-active-slide="0">
            <div class="hero-slider__slides">
                ${slides.map((movie, index) => HeroSlide(movie, index)).join("")}
            </div>

            <div class="hero-slider__dots" aria-label="Filmes em destaque">
                ${slides.map((movie, index) => `
                    <button
                        class="hero-slider__dot ${index === 0 ? "is-active" : ""}"
                        type="button"
                        data-hero-slide="${index}"
                        aria-label="Mostrar ${escapeAttribute(movie.title)}"
                        aria-pressed="${index === 0 ? "true" : "false"}">
                    </button>
                `).join("")}
            </div>
        </section>
    `;
}

function HeroSlide(movie, index) {
    const {
        id = "",
        title = "Titulo do Filme",
        overview = "Uma breve descricao do filme.",
        backdrop = "",
        poster = "",
        year = "",
        duration = "",
        genres = [],
        rating = "",
        quality = "",
        progress = 0
    } = movie || {};
    const fallbackBackdrop = backdrop || poster || "";
    const heroImage = movieImageUrl(movie || {}, {
        type: "backdrop",
        size: "w1280",
        fallback: fallbackBackdrop
    });

    return `
        <article class="hero__slide ${index === 0 ? "is-active" : ""}" data-movie-id="${escapeAttribute(id)}" data-hero-slide-panel="${index}" aria-hidden="${index === 0 ? "false" : "true"}">
            <div class="hero__background">
                <img
                    src="${escapeAttribute(heroImage)}"
                    alt="${escapeAttribute(title)}"
                    ${index === 0 ? 'fetchpriority="high"' : 'loading="lazy"'}
                    ${tmdbImageFallbackAttributes(fallbackBackdrop)}
                >
            </div>

            <div class="hero__overlay"></div>

            <div class="hero__content">
                <div class="hero__meta">
                    <span>${escapeHtml(year)}</span>
                    <span>${escapeHtml(duration)}</span>
                    ${rating ? `<span>★ ${escapeHtml(rating)}</span>` : ""}
                    ${quality ? `<span>${escapeHtml(quality)}</span>` : ""}
                </div>

                <h1 class="hero__title">${escapeHtml(title)}</h1>
                <p class="hero__description">${escapeHtml(overview)}</p>

                <div class="hero__genres">
                    ${genres.map(g => `<span>${escapeHtml(g)}</span>`).join("")}
                </div>

                <div class="hero__actions">
                    ${Button({
                        text: "Assistir",
                        icon: "▶",
                        variant: "primary",
                        classes: "js-open-movie",
                        id: `hero-play-${escapeAttribute(id)}`
                    })}

                    ${Button({
                        text: "Detalhes",
                        variant: "secondary",
                        classes: "js-open-movie",
                        id: `hero-details-${escapeAttribute(id)}`
                    })}
                </div>

                ${
                    progress > 0
                    ? `
                    <div class="hero__progress">
                        <span class="hero__progress-label">${Math.round(Math.min(100, Math.max(0, Number(progress) || 0)))}% assistido</span>
                        <div class="hero__progress-bar" style="width:${Math.min(100, Math.max(0, Number(progress) || 0))}%"></div>
                    </div>
                    `
                    : ""
                }
            </div>
        </article>
    `;
}
