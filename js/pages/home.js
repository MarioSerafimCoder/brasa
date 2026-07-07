// ==========================================================
// BRasa
// Home Page
// ==========================================================

import { render, renderMany } from "../utils/renderer.js";

import Hero from "../../components/home/hero.js";
import Carousel from "../../components/home/carousel.js";

import {

    getFeaturedMovie,

    getContinueWatching,

    getFavorites,

    getMovies,

    getAvailableMovies

} from "../../data/movies.js";

export default function HomePage(){

    renderHero();

    renderCarousels();

    bindMovieNavigation();

}

function renderHero(){

    render(

        "#hero",

        Hero(

            getFeaturedMovie()

        )

    );

}

function renderCarousels(){

    renderMany(

        "#content",

        [

            Carousel({

                title:"Disponível no Computador",

                movies:getAvailableMovies()

            }),

            Carousel({

                title:"Todos os Filmes",

                movies:getMovies()

            }),

            Carousel({

                title:"Continue Assistindo",

                movies:getContinueWatching()

            }),

            Carousel({

                title:"Favoritos",

                movies:getFavorites()

            })

        ]

    );

}

function bindMovieNavigation(){

    document.addEventListener("click", (event) => {

        const target = event.target.closest("[data-movie-id]");

        if (!target) return;

        const movieId = target.dataset.movieId;

        if (!movieId) return;

        window.location.href = `pages/movie.html?id=${movieId}`;

    });

    document.addEventListener("keydown", (event) => {

        if (event.key !== "Enter" && event.key !== " ") return;

        const target = event.target.closest(".movie-card[data-movie-id]");

        if (!target) return;

        event.preventDefault();

        window.location.href = `pages/movie.html?id=${target.dataset.movieId}`;

    });

}
