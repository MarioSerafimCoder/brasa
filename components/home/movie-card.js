// ==========================================================
// BRasa
// Movie Card
// ==========================================================

export default function MovieCard({

    id = "",

    title = "Sem título",

    poster = "",

    year = "",

    rating = "",

    quality = "",

    progress = 0,

    favorite = false

} = {}) {

    return `

        <article
            class="movie-card"
            data-movie-id="${id}">

            <div class="movie-card__poster">

                <img
                    src="${poster}"
                    alt="${title}"
                    loading="lazy"
                >

                <div class="movie-card__overlay">

                    <div class="movie-card__actions">

                        <button
                            class="movie-card__button movie-card__button--play"
                            data-action="play"
                            data-movie-id="${id}"
                            aria-label="Assistir">

                            <i data-lucide="play"></i>

                        </button>

                        <button
                            class="movie-card__button"
                            data-action="details"
                            data-movie-id="${id}"
                            aria-label="Detalhes">

                            <i data-lucide="info"></i>

                        </button>

                        <button
                            class="movie-card__button"
                            data-action="favorite"
                            data-movie-id="${id}"
                            aria-label="Favoritar">

                            <i
                                data-lucide="${
                                    favorite
                                        ? "heart-off"
                                        : "heart"
                                }">
                            </i>

                        </button>

                    </div>

                </div>

                ${quality
                    ? `
                        <span class="movie-card__quality">

                            ${quality}

                        </span>
                    `
                    : ""
                }

            </div>

            <div class="movie-card__info">

                <h3 class="movie-card__title">

                    ${title}

                </h3>

                <div class="movie-card__meta">

                    <span>

                        ${year}

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
                                style="width:${progress}%">
                            </div>

                        </div>
                    `
                    : ""
                }

            </div>

        </article>

    `;

}