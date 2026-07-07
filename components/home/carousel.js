import MovieCard from "./movie-card.js";

export default function Carousel({

    title = "Coleção",

    movies = []

} = {}) {

    return `

        <section class="carousel">

            <div class="carousel__header">

                <h2>

                    ${title}

                </h2>

            </div>

            <div class="carousel__track">

                ${movies.map(movie => MovieCard(movie)).join("")}

            </div>

        </section>

    `;

}
