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

    getFavorites

} from "../../data/movies.js";

export default function HomePage(){

    renderHero();

    renderCarousels();

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