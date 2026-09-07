const MAX_HISTORY = 500, MAX_ITEMS = 5000, MAX_EVENTS = 1000;
const ALLOWED_PREFERENCES = new Set(["skipIntro", "skipCredits", "subtitleLanguage", "subtitleSize", "subtitleFont", "subtitleColor", "subtitleBackground", "subtitleOpacity", "subtitleOutline", "subtitlePosition", "subtitleSync", "audioLanguage", "maxVolume", "normalizeVolume", "preferSurround", "autoplayNext"]);
const REACTIONS = new Set(["like", "not-for-me"]);
export function emptyProfileState() { return { favorites: [], progress: {}, history: [], completed: [], preferences: {}, ratings: {}, hiddenSuggestions: [], continueDismissed: {}, activityEvents: [], updatedAt: "" }; }
export function normalizeProfileState(input = {}, { validateMediaKey = () => true, validateProgress = (value) => ({ ...value }), validateHistory = (value) => ({ ...value }) } = {}) {
    const source = input && typeof input === "object" && !Array.isArray(input) ? input : {};
    const progress = Object.fromEntries(Object.entries(source.progress && typeof source.progress === "object" && !Array.isArray(source.progress) ? source.progress : {}).filter(([key]) => validateMediaKey(key)).slice(0, MAX_ITEMS).map(([key, value]) => [key, validateProgress(value, key)]));
    const history = Array.isArray(source.history) ? source.history.filter((item) => validateMediaKey(item?.mediaKey)).slice(0, MAX_HISTORY).map(validateHistory) : [];
    const preferences = source.preferences && typeof source.preferences === "object" && !Array.isArray(source.preferences) ? Object.fromEntries(Object.entries(source.preferences).filter(([key]) => ALLOWED_PREFERENCES.has(key))) : {};
    const validFavorite = (id) => /^[a-zA-Z0-9._-]{1,120}$/.test(id) || validateMediaKey(id);
    const ratings = Object.fromEntries(Object.entries(record(source.ratings)).filter(([key, value]) => validateMediaKey(key) && REACTIONS.has(value)).slice(0, MAX_ITEMS));
    const continueDismissed = Object.fromEntries(Object.entries(record(source.continueDismissed)).filter(([key, value]) => validateMediaKey(key) && validDate(value)).slice(0, MAX_ITEMS));
    const activityEvents = Array.isArray(source.activityEvents) ? source.activityEvents.filter((event) => validateMediaKey(event?.mediaKey) && typeof event?.type === "string" && validDate(event?.at)).slice(-MAX_EVENTS).map((event) => ({ mediaKey: event.mediaKey, type: String(event.type).slice(0, 32), at: event.at, seconds: Math.max(0, Math.round(Number(event.seconds) || 0)) })) : [];
    return { favorites: unique(source.favorites, validFavorite), progress, history, completed: unique(source.completed, validateMediaKey), preferences, ratings, hiddenSuggestions: unique(source.hiddenSuggestions, validateMediaKey), continueDismissed, activityEvents, updatedAt: validDate(source.updatedAt) || new Date().toISOString() };
}
function unique(value, valid) { return Array.isArray(value) ? [...new Set(value.filter((item) => typeof item === "string").filter(valid))].slice(0, MAX_ITEMS) : []; }
function validDate(value) { return typeof value === "string" && Number.isFinite(Date.parse(value)) ? value : ""; }
function record(value) { return value && typeof value === "object" && !Array.isArray(value) ? value : {}; }
