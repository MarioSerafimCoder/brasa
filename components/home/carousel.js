import MovieCard from "./movie-card.js";
import { escapeAttribute, escapeHtml } from "../../js/utils/html.js";

export default function Carousel({
    title = "Coleção",
    movies = [],
    id = "",
    variant = "poster",
    moreHref = "pages/movies.html"
} = {}) {
    return `
        <section class="carousel carousel--${escapeAttribute(variant)}"${id ? ` id="${escapeAttribute(id)}"` : ""}>
            <div class="carousel__header">
                <h2>${escapeHtml(title)}</h2>
                <a class="carousel__more" href="${escapeAttribute(moreHref)}">
                    Veja mais
                    <i data-lucide="chevron-right"></i>
                </a>
            </div>
            <div class="carousel__track">
                ${movies.map((movie) => MovieCard(movie)).join("")}
            </div>
        </section>
    `;
}
