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
    libraryView: "grid"
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
    localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
            ...getPreferences(),
            ...preferences
        })
    );

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

    root.style.setProperty("--user-subtitle-color", preferences.subtitleColor);
    root.style.setProperty("--user-subtitle-bg", preferences.subtitleBackground);
    root.style.setProperty("--user-subtitle-opacity", String(Number(preferences.subtitleOpacity) / 100));

    return preferences;
}

export { defaultPreferences };
