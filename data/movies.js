// ==========================================================
// BRasa
// Movies Repository
// ==========================================================

const movies = [

    {

        id: 1,

        title: "Blade Runner 2049",

        originalTitle: "Blade Runner 2049",

        year: 2017,

        duration: "2h 44min",

        rating: 8.0,

        quality: "4K HDR",

        genres: [

            "Ficção Científica",
            "Drama"

        ],

        overview:
            "Um novo blade runner descobre um segredo capaz de mudar toda a sociedade.",

        poster:
            "assets/posters/blade-runner-2049.webp",

        backdrop:
            "assets/backdrops/blade-runner-2049.webp",

        progress: 58,

        favorite: true,

        featured: true

    },

    {

        id: 2,

        title: "Duna",

        originalTitle: "Dune",

        year: 2021,

        duration: "2h 35min",

        rating: 8.1,

        quality: "4K HDR",

        genres: [

            "Ficção Científica",
            "Aventura"

        ],

        overview:
            "Paul Atreides embarca em uma jornada para proteger seu povo e cumprir seu destino.",

        poster:
            "assets/posters/dune.webp",

        backdrop:
            "assets/backdrops/dune.webp",

        progress: 24,

        favorite: false,

        featured: false

    },

    {

        id: 3,

        title: "Interestelar",

        originalTitle: "Interstellar",

        year: 2014,

        duration: "2h 49min",

        rating: 8.7,

        quality: "4K",

        genres: [

            "Ficção Científica",
            "Drama"

        ],

        overview:
            "Uma equipe viaja através de um buraco de minhoca em busca de um novo lar para a humanidade.",

        poster:
            "assets/posters/interstellar.webp",

        backdrop:
            "assets/backdrops/interstellar.webp",

        progress: 91,

        favorite: true,

        featured: false

    }

];

/* ==========================================================
   GETTERS
========================================================== */

export function getMovies(){

    return movies;

}

export function getFeaturedMovie(){

    return movies.find(

        movie => movie.featured

    );

}

export function getFavorites(){

    return movies.filter(

        movie => movie.favorite

    );

}

export function getContinueWatching(){

    return movies.filter(

        movie => movie.progress > 0

    );

}

export function getMovieById(id){

    return movies.find(

        movie => movie.id === id

    );

}