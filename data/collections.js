export const collections = [
    {
        id: "mcu",
        title: "Universo Marvel (MCU)",
        subtitle: "Heróis, sagas cósmicas e equipes conectadas.",
        banner: "../assets/collections/marvel.png",
        imdbIds: ["tt6263850", "tt2250912", "tt6320628", "tt10872600", "tt10676052"],
        titlePatterns: [
            "Avengers", "Vingadores", "Iron Man", "Homem de Ferro", "Captain America", "Capitão América", "Guardians of the Galaxy",
            "Deadpool & Wolverine", "Deadpool e Wolverine",
            "Spider-Man: Homecoming", "Homem-Aranha De Volta ao Lar", "Homem Aranha De Volta ao Lar",
            "Spider-Man: Far from Home", "Homem-Aranha Longe de Casa",
            "Spider-Man: No Way Home", "Homem-Aranha - Sem Volta para Casa",
            "The Fantastic Four: First Steps", "Quarteto Fantástico: Primeiros Passos"
        ],
        keywords: []
    },
    {
        id: "dc",
        title: "Universo DC",
        subtitle: "Batman, Superman, Mulher-Maravilha e grandes mundos sombrios.",
        banner: "../assets/backdrops/the-batman-2022-backdrop.jpg",
        imdbIds: ["tt5950044", "tt0372784", "tt1117563", "tt2313197", "tt1877830", "tt6159030", "tt0468569", "tt0770828"],
        titlePatterns: ["Batman", "Superman", "Man of Steel", "Homem de Aço", "Mulher-Maravilha", "Wonder Woman", "Justice League", "Liga da Justiça"],
        keywords: []
    },
    {
        id: "spider-man",
        title: "Homem-Aranha",
        subtitle: "Diferentes gerações, universos e heróis unidos pelo sentido de responsabilidade.",
        banner: "../assets/backdrops/spider-man-no-way-home-2021-backdrop.jpg",
        imdbIds: ["tt9362722", "tt2250912", "tt6320628", "tt10872600", "tt0145487", "tt0316654", "tt0413300", "tt4633694"],
        titlePatterns: ["Spider-Man", "Homem-Aranha", "Homem Aranha"],
        keywords: [],
        sort: { field: "year", direction: "asc" }
    },
    {
        id: "star-wars",
        title: "Star Wars",
        subtitle: "A galáxia, a Força e aventuras entre Jedi e Sith.",
        banner: "../assets/collections/star-wars.png",
        imdbIds: ["tt0076759", "tt3748528"],
        titlePatterns: ["Star Wars"],
        keywords: ["Star Wars", "Jedi", "Sith"]
    },
    {
        id: "lotr",
        title: "O Senhor dos Anéis & O Hobbit",
        subtitle: "Jornadas pela Terra Média.",
        banner: "../assets/collections/senhor-dos-aneis.png",
        titlePatterns: ["Senhor dos Anéis", "Lord of the Rings", "Hobbit"],
        keywords: ["Senhor dos Anéis", "Lord of the Rings", "Hobbit"]
    },
    {
        id: "harry-potter",
        title: "Harry Potter",
        subtitle: "Magia, Hogwarts e aventuras do mundo bruxo.",
        banner: "../assets/collections/harry-potter.png",
        titlePatterns: ["Harry Potter", "Fantastic Beasts", "Animais Fantásticos"],
        keywords: ["Harry Potter", "Hogwarts", "Animais Fantásticos"]
    },
    {
        id: "jurassic",
        title: "Jurassic Park / Jurassic World",
        subtitle: "Dinossauros, parques impossíveis e sobrevivência.",
        banner: null,
        keywords: ["Jurassic", "Dinossauro"]
    },
    {
        id: "mission-impossible",
        title: "Missão: Impossível",
        subtitle: "Operações secretas e ação de alto risco.",
        banner: null,
        keywords: ["Missão: Impossível", "Mission: Impossible"]
    },
    {
        id: "science-fiction",
        title: "Ficção Científica",
        subtitle: "Outros mundos, futuros possíveis e aventuras além da imaginação.",
        banner: "../assets/backdrops/interstellar.png",
        genrePatterns: ["Ficção científica", "Science Fiction", "Sci-Fi"],
        keywords: [],
        sort: { field: "year", direction: "desc" }
    },
    {
        id: "classics",
        title: "Clássicos do cinema",
        subtitle: "Filmes essenciais para revisitar sempre.",
        banner: "../assets/collections/classicos-do-cinema.png",
        imdbIds: ["tt0076759", "tt0075148", "tt0101414", "tt0103639", "tt0029583", "tt0042332"],
        titlePatterns: [
            "Star Wars IV: Uma Nova Esperança", "Star Wars: Episode IV - A New Hope",
            "Rocky - Um Lutador", "Beauty and the Beast", "A Bela e a Fera",
            "Aladdin", "Snow White and the Seven Dwarfs", "Branca de Neve e os Sete Anões",
            "Cinderella", "Cinderela"
        ],
        keywords: []
    },
    {
        id: "fast-furious",
        title: "Velozes & Furiosos",
        subtitle: "Corridas, família e ação sobre rodas.",
        banner: "../assets/collections/velozes-e-furiosos.png",
        imdbIds: ["tt0232500", "tt0322259", "tt0463985", "tt1013752", "tt1596343", "tt1905041", "tt2820852"],
        titlePatterns: ["Velozes e Furiosos", "Fast & Furious", "Fast and Furious"],
        keywords: [],
        sort: { field: "year", direction: "asc" }
    },
    {
        id: "pirates-caribbean",
        title: "Piratas do Caribe",
        subtitle: "Aventuras, maldições e lendas pelos sete mares.",
        banner: "../assets/collections/piratas-do-caribe.png",
        titlePatterns: ["Piratas do Caribe", "Pirates of the Caribbean"],
        keywords: ["Piratas do Caribe", "Pirates of the Caribbean", "Jack Sparrow"]
    },
    {
        id: "rocky",
        title: "Rocky & Creed",
        subtitle: "Legado, superação e grandes lutas dentro e fora do ringue.",
        banner: "../assets/collections/rocky.png",
        imdbIds: ["tt0075148", "tt0079817", "tt0084602", "tt0089927", "tt0100507", "tt0479143", "tt3076658", "tt6343314"],
        titlePatterns: ["Rocky", "Creed"],
        keywords: [],
        sort: { field: "year", direction: "asc" }
    },
    {
        id: "pixar",
        title: "Pixar",
        subtitle: "Animações emocionantes para todas as idades.",
        banner: "../assets/collections/pixar.png",
        imdbIds: [
            "tt0120623", "tt2096673", "tt22022452", "tt7146812", "tt15789038", "tt10298810",
            "tt0198781", "tt0266543", "tt0382932", "tt2948372", "tt1217209", "tt2380307"
        ],
        titlePatterns: [
            "Toy Story", "A Bug's Life", "Vida de Inseto", "Inside Out", "Divertida Mente",
            "Onward", "Dois Irmãos", "Elemental", "Elementos", "Lightyear",
            "Monsters, Inc.", "Monstros S A", "Finding Nemo", "Procurando Nemo",
            "Ratatouille", "Soul", "Brave", "Valente", "Coco", "Viva A Vida é uma Festa"
        ],
        keywords: []
    },
    {
        id: "disney-classics",
        title: "Disney Clássicos",
        subtitle: "Princesas, música, aventura e fantasia.",
        banner: "../assets/collections/disney.png",
        imdbIds: [
            "tt11655566", "tt0101414", "tt0120917", "tt0103639", "tt0043274", "tt0230011",
            "tt0029583", "tt0042332", "tt0119282", "tt3521164", "tt0061852", "tt0120762",
            "tt0116583", "tt2245084", "tt0133240", "tt5109280", "tt0348124", "tt0120855", "tt26443597"
        ],
        titlePatterns: [
            "Lilo & Stitch", "A Bela e a Fera", "Beauty and the Beast", "A Nova Onda do Imperador", "The Emperor's New Groove",
            "Aladdin", "Alice no País das Maravilhas", "Alice in Wonderland", "Atlantis: The Lost Empire", "Atlantis O Reino Perdido",
            "Branca de Neve e os Sete Anões", "Snow White and the Seven Dwarfs", "Cinderela", "Cinderella",
            "Hercules", "Moana", "Mogli O Menino Lobo", "The Jungle Book", "Mulan",
            "O Corcunda de Notre Dame", "The Hunchback of Notre Dame", "Operação Big Hero", "Big Hero 6",
            "Planeta do Tesouro", "Treasure Planet", "Raya e o Último Dragão", "Raya and the Last Dragon",
            "Stitch-O Filme", "Stitch! The Movie", "Tarzan", "Zootopia"
        ],
        keywords: []
    },
    {
        id: "dreamworks",
        title: "DreamWorks",
        subtitle: "Comédias animadas, criaturas e grandes amizades.",
        banner: "../assets/collections/dreamworks.png",
        imdbIds: [
            "tt1446192", "tt0351283", "tt0479952", "tt0441773", "tt1302011", "tt2267968",
            "tt0120794", "tt0307453", "tt30017619", "tt0481499", "tt2850386", "tt26743210"
        ],
        titlePatterns: [
            "Shrek", "Madagascar", "Kung Fu Panda", "A Origem dos Guardiões", "Rise of the Guardians",
            "O Príncipe do Egito", "The Prince of Egypt", "O Espanta Tubarões", "Shark Tale",
            "Os Caras Malvados", "The Bad Guys", "Os Croods", "The Croods", "Como Treinar o Seu Dragão"
        ],
        excludePatterns: ["1XBET", "promo", "No Pedaço"],
        keywords: []
    },
    {
        id: "ghibli",
        title: "Studio Ghibli",
        subtitle: "Mundos delicados, fantasia e cinema de animação autoral.",
        banner: "../assets/backdrops/spirited-away-2003-backdrop.jpg",
        imdbIds: ["tt0245429", "tt0347149"],
        titlePatterns: ["Spirited Away", "A Viagem de Chihiro", "Sen to Chihiro no Kamikakushi", "Howl's Moving Castle", "O Castelo Animado"],
        keywords: []
    },
    {
        id: "best-picture",
        title: "Oscar de Melhor Filme",
        subtitle: "Todos os vencedores do prêmio principal da Academia.",
        banner: "../assets/collections/oscar.png",
        imdbIds: ["tt0075148", "tt10366460"],
        titlePatterns: ["Rocky - Um Lutador", "CODA"],
        keywords: []
    }
];
