const MAX_HISTORY = 500, MAX_ITEMS = 5000;
const ALLOWED_PREFERENCES = new Set(["skipIntro", "skipCredits", "subtitleLanguage", "subtitleSize", "subtitleFont", "subtitleColor", "subtitleBackground", "subtitleOpacity", "subtitleOutline", "subtitlePosition", "subtitleSync", "audioLanguage", "maxVolume", "normalizeVolume", "preferSurround"]);
export function emptyProfileState() { return { favorites: [], progress: {}, history: [], completed: [], preferences: {}, updatedAt: "" }; }
export function normalizeProfileState(input = {}, { validateMediaKey = () => true, validateProgress = (value) => ({ ...value }), validateHistory = (value) => ({ ...value }) } = {}) {
    const source = input && typeof input === "object" && !Array.isArray(input) ? input : {};
    const progress = Object.fromEntries(Object.entries(source.progress && typeof source.progress === "object" && !Array.isArray(source.progress) ? source.progress : {}).filter(([key]) => validateMediaKey(key)).slice(0, MAX_ITEMS).map(([key, value]) => [key, validateProgress(value, key)]));
    const history = Array.isArray(source.history) ? source.history.filter((item) => validateMediaKey(item?.mediaKey)).slice(0, MAX_HISTORY).map(validateHistory) : [];
    const preferences = source.preferences && typeof source.preferences === "object" && !Array.isArray(source.preferences) ? Object.fromEntries(Object.entries(source.preferences).filter(([key]) => ALLOWED_PREFERENCES.has(key))) : {};
    return { favorites: unique(source.favorites, (id) => /^[a-zA-Z0-9._-]{1,120}$/.test(id)), progress, history, completed: unique(source.completed, validateMediaKey), preferences, updatedAt: validDate(source.updatedAt) || new Date().toISOString() };
}
function unique(value, valid) { return Array.isArray(value) ? [...new Set(value.filter((item) => typeof item === "string").filter(valid))].slice(0, MAX_ITEMS) : []; }
function validDate(value) { return typeof value === "string" && Number.isFinite(Date.parse(value)) ? value : ""; }
