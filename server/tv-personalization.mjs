const COMPLETED_PERCENTAGE = 95;

export function mergeWatchProgress(previous = null, incoming = {}) {
    const explicitRestart = Number(incoming.currentTime || 0) <= 5 && Number(incoming.percentage || 0) <= 5;
    return { ...incoming, completed: explicitRestart ? false : Boolean(previous?.completed || incoming.completed) };
}

export function orderedEpisodes(series = {}) {
    return (series.seasons || []).flatMap((season) => season.episodes || []).sort((left, right) => {
        const leftSeason = Number(left.seasonNumber ?? 0), rightSeason = Number(right.seasonNumber ?? 0);
        const leftSpecial = leftSeason === 0 ? 1 : 0, rightSpecial = rightSeason === 0 ? 1 : 0;
        return leftSpecial - rightSpecial || leftSeason - rightSeason || Number(left.episodeNumber ?? 0) - Number(right.episodeNumber ?? 0) || String(left.id).localeCompare(String(right.id));
    });
}

export function resolveSeriesContinuation(series = {}) {
    const episodes = orderedEpisodes(series);
    if (!episodes.length) return { episode: null, completed: false, watched: false };
    const isCompleted = (episode) => episode.progress?.completed === true || Number(episode.progress?.percentage || 0) >= COMPLETED_PERCENTAGE;
    const partial = episodes.filter((episode) => episode.progress && Number(episode.progress.percentage || 0) > 0 && !isCompleted(episode))
        .sort((left, right) => Date.parse(right.progress?.updatedAt || 0) - Date.parse(left.progress?.updatedAt || 0))[0];
    if (partial) return { episode: partial, completed: false, watched: true };
    const completedIndexes = episodes.map((episode, index) => isCompleted(episode) ? index : -1).filter((index) => index >= 0);
    if (!completedIndexes.length) return { episode: episodes[0], completed: false, watched: false };
    const next = episodes[completedIndexes.at(-1) + 1] || episodes.find((episode) => !isCompleted(episode)) || null;
    return { episode: next, completed: !next, watched: true };
}

export function decorateSeriesContinuity(series = {}) {
    const resolved = resolveSeriesContinuation(series);
    const episode = resolved.episode;
    const remainingMinutes = episode?.progress?.duration > 0
        ? Math.max(0, Math.ceil((episode.progress.duration - episode.progress.currentTime) / 60))
        : null;
    const episodeLabel = episode ? `T${pad(episode.seasonNumber)} · E${pad(episode.episodeNumber)}` : "";
    return {
        ...series,
        progress: episode?.progress || (resolved.completed ? { percentage: 100, completed: true } : null),
        resumeMediaKey: episode?.mediaKey || "",
        actionLabel: resolved.completed ? "Assistida" : `${episode?.progress && Number(episode.progress.percentage || 0) > 0 ? "Continuar" : "Assistir"}${episodeLabel ? ` ${episodeLabel}` : ""}`,
        remainingMinutes,
        completed: resolved.completed,
    };
}

export function buildPersonalizedHome(catalog, state = {}) {
    const movies = catalog.movies || [];
    const series = (catalog.series || []).map(decorateSeriesContinuity);
    const all = [...movies, ...series];
    const byKey = new Map(all.map((item) => [item.mediaKey, item]));
    const dismissed = state.continueDismissed || {};
    const progressDate = (item) => item.type === "series"
        ? orderedEpisodes(item).map((episode) => episode.progress?.updatedAt || "").sort().at(-1) || ""
        : item.progress?.updatedAt || "";
    const continueItems = all.filter((item) => {
        if (item.type === "series") {
            const resolved = resolveSeriesContinuation(item);
            if (!resolved.watched || resolved.completed) return false;
        } else if (!item.progress || item.progress.completed || Number(item.progress.percentage || 0) <= 0 || Number(item.progress.percentage || 0) >= COMPLETED_PERCENTAGE) return false;
        return Date.parse(dismissed[item.mediaKey] || 0) < Date.parse(progressDate(item) || 0);
    }).sort((a, b) => Date.parse(progressDate(b) || 0) - Date.parse(progressDate(a) || 0));

    const favoriteKeys = new Set(state.favorites || []);
    const hidden = new Set(state.hiddenSuggestions || []);
    const ratings = state.ratings || {};
    const positiveSeeds = all.filter((item) => ratings[item.mediaKey] === "like" || favoriteKeys.has(item.mediaKey) || isSignificant(item));
    const featureWeights = new Map();
    for (const seed of positiveSeeds) {
        const weight = ratings[seed.mediaKey] === "like" ? 6 : favoriteKeys.has(seed.mediaKey) ? 3 : 1;
        for (const feature of features(seed)) featureWeights.set(feature, (featureWeights.get(feature) || 0) + weight);
    }
    const availableSuggestions = all.filter((item) => !hidden.has(item.mediaKey) && ratings[item.mediaKey] !== "not-for-me" && !isCompleted(item));
    const scored = availableSuggestions.map((item, index) => ({ item, index, score: features(item).reduce((sum, feature) => sum + (featureWeights.get(feature) || 0), 0) + (favoriteKeys.has(item.mediaKey) ? 2 : 0) }))
        .sort((a, b) => b.score - a.score || Date.parse(b.item.addedAt || 0) - Date.parse(a.item.addedAt || 0) || a.index - b.index);
    const forYou = diverse(scored.filter((entry) => entry.score > 0).map((entry) => ({ ...entry.item, recommendationReason: recommendationReason(entry.item, positiveSeeds) })), 24);
    const engagedSeries = series.filter((item) => resolveSeriesContinuation(item).watched || favoriteKeys.has(item.mediaKey));
    const newEpisodes = engagedSeries.filter((item) => {
        const latestAdded = orderedEpisodes(item).map((episode) => Date.parse(episode.addedAt || 0)).reduce((max, value) => Math.max(max, value || 0), 0);
        const latestWatch = orderedEpisodes(item).map((episode) => Date.parse(episode.progress?.updatedAt || 0)).reduce((max, value) => Math.max(max, value || 0), 0);
        return latestAdded > latestWatch && orderedEpisodes(item).some((episode) => !isCompleted(episode));
    }).map((item) => ({ ...item, newEpisode: true }));
    const likedSeed = positiveSeeds.find((item) => ratings[item.mediaKey] === "like");
    const because = likedSeed ? forYou.filter((item) => item.mediaKey !== likedSeed.mediaKey && features(item).some((feature) => features(likedSeed).includes(feature))).slice(0, 18) : [];
    const recentlyAdded = [...all].filter((item) => item.addedAt).sort((a, b) => Date.parse(b.addedAt) - Date.parse(a.addedAt));
    const matchingNew = diverse(recentlyAdded.filter((item) => forYou.some((candidate) => candidate.mediaKey === item.mediaKey)), 18);
    const favorites = all.filter((item) => favoriteKeys.has(item.mediaKey) || favoriteKeys.has(item.id)).map((item) => ({ ...item, favorite: true, inMyList: true }));
    const knownGenres = new Set(positiveSeeds.flatMap((item) => item.genres || []));
    const different = diverse(availableSuggestions.filter((item) => !(item.genres || []).some((genre) => knownGenres.has(genre))), 18);
    const recentlyWatched = all.filter((item) => item.type === "series" ? resolveSeriesContinuation(item).watched : Number(item.progress?.percentage || 0) > 0)
        .sort((a, b) => Date.parse(progressDate(b) || 0) - Date.parse(progressDate(a) || 0)).slice(0, 24);
    const rows = [
        row("continue-watching", "Continuar assistindo", "continue", continueItems),
        row("for-you", "Para você", "recommendation", forYou.length ? forYou : diverse(availableSuggestions, 24)),
        row("new-episodes", "Novos episódios das suas séries", "new-episodes", newEpisodes),
        row("because-liked", likedSeed ? `Porque você gostou de ${likedSeed.title}` : "", "recommendation", because),
        row("matching-new", "Novidades que combinam com você", "recommendation", matchingNew),
        row("favorites", "Minha lista", "favorites", favorites),
        row("explore-different", "Explore algo diferente", "recommendation", different),
        row("recently-watched", "Assistidos recentemente", "history", recentlyWatched),
    ].filter((candidate) => candidate.title && candidate.items.length);
    return { profile: catalog.profile, rows };
}

export function searchCatalog(catalog, query) {
    const term = normalized(query);
    if (!term) return [];
    const episodeParents = new Map();
    for (const series of catalog.series || []) for (const episode of orderedEpisodes(series)) episodeParents.set(episode.mediaKey, series);
    const candidates = [...(catalog.movies || []), ...(catalog.series || []), ...episodeParents.keys()].map((value) => typeof value === "string" ? { item: findEpisode(catalog.series, value), parent: episodeParents.get(value) } : { item: value, parent: null });
    const matched = candidates.map(({ item, parent }, index) => ({ item: parent || item, index, rank: searchRank(item, term), episode: parent ? item : null })).filter((entry) => entry.rank < 99)
        .sort((a, b) => a.rank - b.rank || a.index - b.index);
    const unique = [];
    for (const entry of matched) if (!unique.some((candidate) => candidate.mediaKey === entry.item.mediaKey)) unique.push(entry.episode ? { ...entry.item, searchHint: `Inclui ${episodeCode(entry.episode)} · ${entry.episode.title}` } : entry.item);
    return unique.slice(0, 80);
}

function searchRank(item, term) {
    const title = normalized(item?.title), original = normalized(item?.originalTitle);
    if (title === term || original === term) return 0;
    if (title.startsWith(term) || original.startsWith(term)) return 1;
    const values = [title, original, normalized(item?.overview), ...(item?.genres || []).map(normalized), ...(item?.cast || []).map(normalized), ...(item?.directors || []).map(normalized)];
    if (values.some((value) => value.includes(term))) return 2;
    if (term.length >= 5 && [title, original].some((value) => value.split(/\s+/).some((token) => editDistanceAtMostOne(token, term)))) return 3;
    return 99;
}

function editDistanceAtMostOne(left, right) {
    if (Math.abs(left.length - right.length) > 1) return false;
    let i = 0, j = 0, edits = 0;
    while (i < left.length && j < right.length) {
        if (left[i] === right[j]) { i++; j++; continue; }
        if (++edits > 1) return false;
        if (left.length > right.length) i++; else if (right.length > left.length) j++; else { i++; j++; }
    }
    return edits + (i < left.length || j < right.length ? 1 : 0) <= 1;
}

function features(item) { return [...(item.genres || []).map((value) => `genre:${normalized(value)}`), ...(item.themes || []).map((value) => `theme:${normalized(value)}`), item.franchise ? `franchise:${normalized(item.franchise)}` : ""].filter(Boolean); }
function recommendationReason(item, seeds) { const seed = seeds.find((candidate) => candidate.mediaKey !== item.mediaKey && features(item).some((feature) => features(candidate).includes(feature))); return seed ? `Com base em ${seed.title}` : "Seleção variada para este perfil"; }
function diverse(items, limit) { const counts = new Map(); return items.filter((item) => { const genre = item.genres?.[0] || "outros"; const count = counts.get(genre) || 0; if (count >= 4) return false; counts.set(genre, count + 1); return true; }).slice(0, limit); }
function isCompleted(item) { return item.completed === true || item.progress?.completed === true || Number(item.progress?.percentage || 0) >= COMPLETED_PERCENTAGE; }
function isSignificant(item) { const percentage = Number(item.progress?.percentage || 0); return percentage >= 35 || item.progress?.completed === true; }
function row(id, title, type, items) { return { id, title, type, items }; }
function normalized(value) { return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR").replace(/[^a-z0-9]+/g, " ").trim(); }
function pad(value) { return String(Number(value) || 0).padStart(2, "0"); }
function episodeCode(item) { return `T${pad(item.seasonNumber)} · E${pad(item.episodeNumber)}`; }
function findEpisode(series, key) { return series.flatMap(orderedEpisodes).find((episode) => episode.mediaKey === key); }
