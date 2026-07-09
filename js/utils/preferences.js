const STORAGE_KEY = "brasa:preferences";

const defaultPreferences = {
    theme: "dark",
    accent: "brasa",
    density: "normal",
    posterStyle: "poster-info",
    skipIntro: true,
    skipCredits: true,
    subtitleLanguage: "pt-br",
    subtitleSize: "medium",
    subtitleFont: "Inter",
    subtitleColor: "#FFFFFF",
    subtitleBackground: "#000000",
    subtitleOpacity: 70,
    subtitleOutline: true,
    subtitlePosition: "bottom",
    subtitleSync: 0,
    audioLanguage: "original",
    maxVolume: 100,
    normalizeVolume: false,
    preferSurround: true,
    libraryView: "grid",
    kidsMode: false,
    kidsPin: "",
    kidsMaxRating: "PG"
};

export function getPreferences() {
    try {
        return {
            ...defaultPreferences,
            ...JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}")
        };
    } catch {
        return { ...defaultPreferences };
    }
}

export function savePreferences(preferences) {
    try {
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                ...getPreferences(),
                ...preferences
            })
        );
    } catch {
        // Preferences persist only when storage is available.
    }

    applyPreferences();
}

export function applyPreferences() {
    const preferences = getPreferences();
    const root = document.documentElement;

    root.dataset.theme = preferences.theme;
    root.dataset.accent = preferences.accent;
    root.dataset.density = preferences.density;
    root.dataset.posterStyle = preferences.posterStyle;
    root.dataset.libraryView = preferences.libraryView;
    root.dataset.kidsMode = preferences.kidsMode ? "on" : "off";
    root.dataset.subtitlePosition = preferences.subtitlePosition;
    root.dataset.subtitleOutline = preferences.subtitleOutline ? "on" : "off";

    root.style.setProperty("--user-subtitle-color", preferences.subtitleColor);
    root.style.setProperty("--user-subtitle-bg", preferences.subtitleBackground);
    root.style.setProperty("--user-subtitle-bg-rgb", hexToRgb(preferences.subtitleBackground));
    root.style.setProperty("--user-subtitle-opacity", String(Number(preferences.subtitleOpacity) / 100));

    return preferences;
}

export { defaultPreferences };

function hexToRgb(value) {
    const normalized = String(value || "#000000").replace("#", "");
    const full = normalized.length === 3
        ? normalized.split("").map((char) => `${char}${char}`).join("")
        : normalized.padEnd(6, "0").slice(0, 6);
    const number = Number.parseInt(full, 16);

    if (!Number.isFinite(number)) {
        return "0,0,0";
    }

    return [
        (number >> 16) & 255,
        (number >> 8) & 255,
        number & 255
    ].join(",");
}
