import { ForbiddenError } from "./app-errors.mjs";
import {
  publicProfile,
  publicProfiles,
  validateProfileState,
} from "./profile-contract.mjs";
import {
  normalizeTvCatalogItem,
  normalizeTvProfile,
  normalizeTvProgressMap,
} from "./tv-contract.mjs";
import { resolveTvCollectionMovies } from "./tv-collections.mjs";
import {
  buildPersonalizedHome,
  decorateSeriesContinuity,
  searchCatalog,
} from "./tv-personalization.mjs";

export function createTvCatalogService({ readUserState, tvLibraryCache }) {
  async function tvProfiles(device) {
    const state = await readUserState();
    return publicProfiles(state.profiles)
      .filter(
        (profile) =>
          !device.allowedProfileIds.length ||
          device.allowedProfileIds.includes(profile.id),
      )
      .map(normalizeTvProfile);
  }

  async function tvCatalog(device, profileId) {
    const state = await readUserState(),
      profile = state.profiles.find((item) => item.id === profileId);
    if (
      !profile ||
      (device.allowedProfileIds.length &&
        !device.allowedProfileIds.includes(profileId))
    )
      throw new ForbiddenError("Perfil não autorizado.");
    const library = await tvLibraryCache.load();
    const profileState = validateProfileState(state.states[profileId] || {});
    const availableMovies = library.movies.filter((item) =>
      canTvAccess(item, profile),
    );
    const movies = availableMovies.map((item) =>
      tvMovie(item, profileId, profileState),
    );
    const series = library.series
      .filter((item) => canTvAccess(item, profile))
      .map((item) => tvSeries(item, profileId, profile, profileState));
    const collections = library.collections
      .filter((item) => item.banner)
      .map((collection) => ({
        id: collection.id,
        title: collection.title,
        subtitle: collection.subtitle || "",
        banner: collection.banner || "",
        items: resolveTvCollectionMovies(collection, availableMovies).map(
          (movie) => tvMovie(movie, profileId, profileState),
        ),
      }))
      .filter((collection) => collection.items.length);
    return {
      profile: normalizeTvProfile(publicProfile(profile)),
      movies,
      series,
      collections,
      favorites: profileState.favorites,
      progress: normalizeTvProgressMap(profileState.progress),
      updatedAt: new Date().toISOString(),
    };
  }

  async function tvHome(device, profileId) {
    const startedAt = performance.now();
    const catalog = await tvCatalog(device, profileId);
    const state = await readUserState();
    const home = buildPersonalizedHome(
      catalog,
      validateProfileState(state.states[profileId] || {}),
    );
    return {
      ...home,
      metrics: {
        generatedMs: Math.round((performance.now() - startedAt) * 10) / 10,
        payloadBytes: Buffer.byteLength(JSON.stringify(home)),
      },
    };
  }

  async function tvSearch(device, profileId, query) {
    return searchCatalog(await tvCatalog(device, profileId), query);
  }

  function canTvAccess(item, profile) {
    const sourceOffline = item?.fileStatus === "source-offline";
    if (
      !item ||
      (!sourceOffline &&
        (item.playable === false || item.fileStatus === "missing-file")) ||
      (!Array.isArray(item.seasons) && !item.video)
    )
      return false;
    if (profile.kind !== "kids")
      return item.audience !== "adult" || profile.kind === "adult";
    const audience = item.audience || (item.kids ? "kids" : "general");
    if (audience === "kids") return true;
    if (audience === "adult") return false;
    const level = tvRatingLevel(item.contentRating);
    return level !== null && level <= Number(profile.maxContentRating ?? 10);
  }
  function tvRatingLevel(value) {
    const key = String(value || "")
        .trim()
        .toUpperCase()
        .replace(/\s+/g, ""),
      levels = {
        L: 0,
        LIVRE: 0,
        G: 0,
        "TV-Y": 0,
        "TV-Y7": 7,
        "TV-G": 0,
        10: 10,
        "10ANOS": 10,
        12: 12,
        14: 14,
        16: 16,
        18: 18,
        PG: 12,
        "PG-13": 13,
        R: 17,
        "TV-PG": 12,
        "TV-14": 14,
        "TV-MA": 18,
      };
    return Object.prototype.hasOwnProperty.call(levels, key)
      ? levels[key]
      : null;
  }
  function tvMovie(item, profileId, state) {
    const mediaKey = `movie:${item.id}`;
    return normalizeTvCatalogItem({
      id: String(item.id),
      mediaKey,
      type: "movie",
      title: item.title,
      originalTitle: item.originalTitle,
      year: item.year,
      duration: item.duration,
      durationMinutes: durationMinutes(item),
      rating: item.rating,
      contentRating: item.contentRating,
      genres: item.genres || [],
      cast: item.cast || [],
      directors: item.directors || item.direction || [],
      themes: item.themes || item.keywords || [],
      franchise: item.franchise || "",
      overview: item.overview || "",
      poster: item.poster || "",
      backdrop: item.backdrop || item.poster || "",
      addedAt: item.addedAt || item.fileModifiedAt || item.lastIndexedAt || "",
      subtitles: item.subtitles || [],
      favorite:
        state.favorites.includes(mediaKey) ||
        state.favorites.includes(String(item.id)),
      reaction: state.ratings[mediaKey] || "",
      hiddenSuggestion: state.hiddenSuggestions.includes(mediaKey),
      progress: state.progress[mediaKey] || null,
      streamUrl: `/api/tv/stream/${encodeURIComponent(mediaKey)}?profileId=${encodeURIComponent(profileId)}`,
    });
  }
  function tvSeries(item, profileId, profile, state) {
    const mediaKey = `series:${item.id}`;
    return decorateSeriesContinuity(
      normalizeTvCatalogItem({
        id: String(item.id),
        mediaKey,
        type: "series",
        title: item.title,
        originalTitle: item.originalTitle,
        year: item.year,
        rating: item.rating,
        contentRating: item.contentRating,
        genres: item.genres || [],
        cast: item.cast || [],
        directors: item.directors || item.direction || [],
        themes: item.themes || item.keywords || [],
        franchise: item.franchise || "",
        overview: item.overview || "",
        poster: item.poster || "",
        backdrop: item.backdrop || item.poster || "",
        addedAt: item.addedAt || "",
        favorite:
          state.favorites.includes(mediaKey) ||
          state.favorites.includes(String(item.id)),
        reaction: state.ratings[mediaKey] || "",
        hiddenSuggestion: state.hiddenSuggestions.includes(mediaKey),
        seasons: (item.seasons || []).map((season) => ({
          seasonNumber: season.seasonNumber,
          episodes: (season.episodes || [])
            .filter((episode) => canTvAccess(episode, profile))
            .map((episode) => tvEpisode(episode, item, profileId, state)),
        })),
      }),
    );
  }
  function tvEpisode(episode, series, profileId, state) {
    const mediaKey = `episode:${episode.id}`;
    return normalizeTvCatalogItem({
      id: String(episode.id),
      mediaKey,
      type: "episode",
      seriesId: String(series.id),
      title: episode.title,
      seasonNumber: episode.seasonNumber,
      episodeNumber: episode.episodeNumber,
      duration: episode.duration,
      durationMinutes: durationMinutes(episode),
      overview: episode.overview || "",
      poster: episode.thumbnail || series.poster || "",
      backdrop: episode.backdrop || series.backdrop || "",
      addedAt: episode.addedAt || "",
      subtitles: episode.subtitles || [],
      reaction: state.ratings[mediaKey] || "",
      progress: state.progress[mediaKey] || null,
      streamUrl: `/api/tv/stream/${encodeURIComponent(mediaKey)}?profileId=${encodeURIComponent(profileId)}`,
    });
  }
  function durationMinutes(item) {
    const direct = Number(item?.durationMinutes);
    if (Number.isFinite(direct) && direct > 0) return Math.round(direct);
    const match = String(item?.duration || "").match(
      /(?:(\d+)\s*h)?\s*(?:(\d+)\s*min)?/i,
    );
    return match && (match[1] || match[2])
      ? Number(match[1] || 0) * 60 + Number(match[2] || 0)
      : null;
  }

  return {
    tvProfiles,
    tvCatalog,
    tvHome,
    tvSearch,
    canTvAccess,
    tvMovie,
    tvSeries,
    tvEpisode,
  };
}
