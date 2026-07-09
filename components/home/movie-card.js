// ==========================================================
// BRasa
// Movie Card
// ==========================================================

import { escapeAttribute, escapeHtml } from "../../js/utils/html.js";
import { movieImageUrl, tmdbImageFallbackAttributes } from "../../js/utils/tmdb-images.js";

export default function MovieCard({

    id = "",

    title = "Sem título",

    poster = "",

    year = "",

    rating = "",

    quality = "",

    progress = 0,

    favorite = false,

    video = ""

} = {}) {
    const movie = arguments[0] || {};
    const posterImage = movieImageUrl(movie, {
        type: "poster",
        size: "w780",
        fallback: poster
    });

    return `

        <article
            class="movie-card"
            data-movie-id="${escapeAttribute(id)}"
            role="button"
            tabindex="0"
            aria-label="Abrir ${escapeAttribute(title)}">

            <div class="movie-card__poster">

                <img
                    src="${escapeAttribute(posterImage)}"
                    alt="${escapeAttribute(title)}"
                    loading="lazy"
                    ${tmdbImageFallbackAttributes(poster)}
                >

                <div class="movie-card__overlay">

                    <div class="movie-card__actions">

                        <button
                            class="movie-card__button movie-card__button--play"
                            data-action="play"
                            data-movie-id="${escapeAttribute(id)}"
                            aria-label="Assistir ${escapeAttribute(title)}">

                            <i data-lucide="play"></i>

                        </button>

                        <button
                            class="movie-card__button movie-card__button--favorite ${favorite ? "is-active" : ""}"
                            data-action="favorite"
                            data-movie-id="${escapeAttribute(id)}"
                            aria-pressed="${favorite ? "true" : "false"}"
                            aria-label="${favorite ? "Remover dos favoritos" : "Favoritar"} ${escapeAttribute(title)}">

                            <i
                                data-lucide="heart">
                            </i>

                        </button>

                    </div>

                </div>

                ${quality
                    ? `
                        <span class="movie-card__quality">

                            ${escapeHtml(quality)}

                        </span>
                    `
                    : ""
                }

                ${video
                    ? `
                        <span class="movie-card__available">

                            Local

                        </span>
                    `
                    : ""
                }

            </div>

            <div class="movie-card__info">

                <h3 class="movie-card__title">

                    ${escapeHtml(title)}

                </h3>

                <div class="movie-card__meta">

                    <span>

                        ${escapeHtml(year)}

                    </span>

                    ${rating
                        ? `
                            <span>

                                ★ ${rating}

                            </span>
                        `
                        : ""
                    }

                </div>

                ${progress > 0
                    ? `
                        <div class="movie-card__progress">

                            <div
                                class="movie-card__progress-bar"
                                style="width:${Math.min(100, Math.max(0, Number(progress) || 0))}%">
                            </div>

                        </div>
                    `
                    : ""
                }

            </div>

        </article>

    `;

}
