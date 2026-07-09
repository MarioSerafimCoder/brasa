import { getPreferences, savePreferences } from "./preferences.js";

const ratingOrder = {
    "G": 0,
    "TV-Y": 0,
    "TV-G": 0,
    "PG": 1,
    "TV-Y7": 1,
    "TV-PG": 1,
    "PG-13": 2,
    "TV-14": 2,
    "R": 3,
    "NC-17": 4,
    "TV-MA": 4
};

export function bindKidsModeToggle(root = document) {
    root.querySelectorAll("[data-kids-toggle]").forEach((button) => {
        button.addEventListener("click", async () => {
            const preferences = getPreferences();
            const nextValue = !preferences.kidsMode;

            if (preferences.kidsMode && !(await canExitKidsMode(preferences))) {
                return;
            }

            if (nextValue && !preferences.kidsPin) {
                const pin = await requestKidsPin({
                    title: "Criar PIN Kids",
                    label: "PIN de 4 números",
                    action: "Ativar"
                });

                if (!pin) {
                    return;
                }

                savePreferences({ kidsMode: true, kidsPin: pin });
            } else {
                savePreferences({ kidsMode: nextValue });
            }

            updateKidsModeButtons(document);
            document.dispatchEvent(new CustomEvent("brasa:kids-mode-change", {
                detail: { enabled: nextValue }
            }));
        });
    });

    updateKidsModeButtons(root);
}

export function updateKidsModeButtons(root = document) {
    const enabled = getPreferences().kidsMode;

    root.querySelectorAll("[data-kids-toggle]").forEach((button) => {
        button.classList.toggle("is-active", enabled);
        button.setAttribute("aria-pressed", String(enabled));

        const label = button.querySelector("[data-kids-label]");
        if (label) {
            label.textContent = "Kids";
        }
    });

    if (window.lucide) {
        window.lucide.createIcons();
    }
}

export function filterKidsMovies(movies) {
    const preferences = getPreferences();
    const maxRating = ratingOrder[preferences.kidsMaxRating] ?? ratingOrder.PG;

    return movies.filter((movie) => {
        const rating = ratingOrder[String(movie.contentRating || "").toUpperCase()];
        return rating !== undefined && rating <= maxRating;
    });
}

export function getKidsThemes(movies = []) {
    const availableGenres = new Set(movies.flatMap((movie) => movie.genres || []));

    return [
        { id: "adventure", label: "Aventura", icon: "rocket", genres: ["Aventura"] },
        { id: "heroes", label: "Super-heróis", icon: "shield", genres: ["Ação", "Aventura"] },
        { id: "animals", label: "Animais", icon: "paw-print", genres: ["Animação", "Família"] },
        { id: "dinosaurs", label: "Dinossauros", icon: "egg", genres: ["Aventura", "Família"] },
        { id: "princesses", label: "Princesas", icon: "crown", genres: ["Fantasia", "Família"] },
        { id: "space", label: "Espaço", icon: "star", genres: ["Ficção Científica", "Aventura"] }
    ].map((theme) => ({
        ...theme,
        active: theme.genres.some((genre) => availableGenres.has(genre))
    }));
}

export function getMoviesByKidsTheme(movies, themeId) {
    const theme = getKidsThemes(movies).find((item) => item.id === themeId);

    if (!theme) return movies;

    return movies.filter((movie) =>
        (movie.genres || []).some((genre) => theme.genres.includes(genre))
    );
}

export function chooseRandomMovie(movies) {
    if (!movies.length) return null;
    return movies[Math.floor(Math.random() * movies.length)];
}

async function canExitKidsMode(preferences) {
    if (!preferences.kidsPin) return true;

    const pin = await requestKidsPin({
        title: "Sair do modo Kids",
        label: "Digite o PIN",
        action: "Sair"
    });

    if (pin === preferences.kidsPin) {
        return true;
    }

    showKidsPinMessage("PIN incorreto.");
    return false;
}

function requestKidsPin({ title, label, action }) {
    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        overlay.className = "kids-pin-modal";
        overlay.innerHTML = `
            <form class="kids-pin-modal__dialog" aria-label="${title}">
                <h2>${title}</h2>
                <label>
                    <span>${label}</span>
                    <input type="password" inputmode="numeric" maxlength="4" pattern="\\d{4}" autocomplete="off">
                </label>
                <p class="kids-pin-modal__error" hidden>Use exatamente 4 números.</p>
                <div>
                    <button type="button" data-action="cancel">Cancelar</button>
                    <button type="submit">${action}</button>
                </div>
            </form>
        `;

        const form = overlay.querySelector("form");
        const input = overlay.querySelector("input");
        const error = overlay.querySelector(".kids-pin-modal__error");
        const close = (value) => {
            overlay.remove();
            resolve(value);
        };

        form.addEventListener("submit", (event) => {
            event.preventDefault();

            if (!/^\d{4}$/.test(input.value)) {
                error.hidden = false;
                input.focus();
                return;
            }

            close(input.value);
        });

        overlay.querySelector("[data-action='cancel']").addEventListener("click", () => close(""));
        overlay.addEventListener("click", (event) => {
            if (event.target === overlay) {
                close("");
            }
        });

        document.body.appendChild(overlay);
        input.focus();
    });
}

function showKidsPinMessage(message) {
    const toast = document.createElement("div");
    toast.className = "sync-status";
    toast.dataset.state = "error";
    toast.setAttribute("role", "status");
    toast.textContent = message;
    document.body.appendChild(toast);

    window.setTimeout(() => toast.remove(), 2400);
}
