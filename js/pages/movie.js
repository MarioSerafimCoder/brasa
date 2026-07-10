// ==========================================================
// BRasa
// Movie Page Controller
// ==========================================================

import { getMovieById, getMovies } from "../../data/movies.js";
import { getEpisodeById, getSeriesById } from "../../data/series.js";
import { applyPreferences, getPreferences } from "../utils/preferences.js";
import { isFavorite, toggleFavorite } from "../utils/favorites.js";
import { filterContentByProfile, initializeProfiles } from "../utils/profiles.js";
import { escapeAttribute, escapeHtml } from "../utils/html.js";
import { installPageTransitions, navigateTo } from "../utils/navigation.js";
import { clearWatchProgress, getWatchProgress, saveWatchProgress } from "../utils/progress.js";
import { installTmdbImageFallbacks, movieImageUrl, seriesImageUrl, tmdbImageFallbackAttributes } from "../utils/tmdb-images.js";

const playerShell = document.getElementById("playerShell");
const moviePage = document.querySelector(".movie-page");
const titleElement = document.getElementById("movie-title");
const player = document.getElementById("moviePlayer");
const source = document.getElementById("movieSource");
const status = document.getElementById("movieStatus");
const movieLanding = document.getElementById("movieLanding");
const movieLandingBackdrop = document.getElementById("movieLandingBackdrop");
const movieLandingTitle = document.getElementById("movieLandingTitle");
const movieLandingMeta = document.getElementById("movieLandingMeta");
const movieLandingOverview = document.getElementById("movieLandingOverview");
const landingPlayButton = document.getElementById("landingPlayButton");
const landingFavoriteButton = document.getElementById("landingFavoriteButton");
const watchNextGrid = document.getElementById("watchNextGrid");
const playerBackButton = document.getElementById("playerBackButton");
const playerCloseButton = document.getElementById("playerCloseButton");

const year = document.getElementById("movieYear");
const duration = document.getElementById("movieDuration");
const quality = document.getElementById("movieQuality");
const genres = document.getElementById("movieGenres");
const overview = document.getElementById("movieOverview");

const playerInfoTitle = document.getElementById("playerInfoTitle");
const playerInfoMeta = document.getElementById("playerInfoMeta");
const playerInfoOverview = document.getElementById("playerInfoOverview");

const playPauseButton = document.getElementById("playPauseButton");
const rewindButton = document.getElementById("rewindButton");
const forwardButton = document.getElementById("forwardButton");
const progressSeek = document.getElementById("progressSeek");
const timeDisplay = document.getElementById("timeDisplay");
const muteButton = document.getElementById("muteButton");
const volumeSlider = document.getElementById("volumeSlider");
const fullscreenButton = document.getElementById("fullscreenButton");

const audioOption = document.getElementById("audioOption");
const audioTrackSelect = document.getElementById("audioTrackSelect");
const subtitleOption = document.getElementById("subtitleOption");
const subtitleTrackSelect = document.getElementById("subtitleTrackSelect");
const subtitleSizeOption = document.getElementById("subtitleSizeOption");
const subtitleSizeSelect = document.getElementById("subtitleSizeSelect");
const subtitleSyncOption = document.getElementById("subtitleSyncOption");
const subtitleDelayDown = document.getElementById("subtitleDelayDown");
const subtitleDelayUp = document.getElementById("subtitleDelayUp");
const subtitleDelayValue = document.getElementById("subtitleDelayValue");

const params = new URLSearchParams(window.location.search);
const movieId = Number(params.get("id"));
const episodeId = params.get("episode");

let currentMovie = null;
let controlsTimer = null;
let isSeeking = false;
let savedProgressRestored = false;
let lastProgressSave = 0;
let subtitleDelay = 0;
let introSkipped = false;
let creditsCountdownStarted = false;
let nextEpisodeCountdown = null;
let preferences = applyPreferences();
installPageTransitions();
installTmdbImageFallbacks();

await initializeProfiles();

function loadMovie() {
    if (!movieId && !episodeId) {
        showUnavailableMovie("Filme não encontrado");
        return;
    }

    const movie = episodeId ? buildEpisodeMovie(episodeId) : getMovieById(movieId);

    if (!movie) {
        showUnavailableMovie("Filme não encontrado");
        return;
    }

    currentMovie = movie;
    preferences = getPreferences();

    if (!filterContentByProfile([movie]).length) {
        showUnavailableMovie("Este conteúdo não está disponível para o perfil Laura.");
        return;
    }

    document.title = `BRasa • ${movie.title}`;
    titleElement.textContent = movie.title;
    renderLanding(movie);
    renderWatchNext(movie);
    year.textContent = movie.year;
    duration.textContent = movie.duration;
    quality.textContent = movie.quality;
    overview.textContent = movie.overview;

    playerInfoTitle.textContent = movie.title;
    playerInfoMeta.textContent = buildInfoMeta(movie);
    playerInfoOverview.textContent = movie.overview;

    if (movie.poster) {
        const posterFallback = resolveAssetUrl(movie.poster);
        player.poster = getDisplayImageUrl(movie, "poster", posterFallback);
        setImageFallback(player, posterFallback);
    }

    renderGenres(movie.genres || []);

    if (!movie.video) {
        showPlayerStatus("Este filme ainda não tem um arquivo de vídeo local cadastrado.");
        player.removeAttribute("src");
        source.removeAttribute("src");
        player.load();
        return;
    }

    clearSubtitleTracks();
    renderSubtitleTracks(movie.subtitles || []);
    playerShell.dataset.subtitleSize = preferences.subtitleSize;
    playerShell.dataset.subtitlePosition = preferences.subtitlePosition;
    playerShell.dataset.subtitleOutline = preferences.subtitleOutline ? "on" : "off";
    playerShell.style.setProperty("--player-subtitle-font", preferences.subtitleFont || "Inter");
    subtitleSizeSelect.value = preferences.subtitleSize;
    subtitleDelay = Number(preferences.subtitleSync || 0);
    subtitleDelayValue.textContent = `${subtitleDelay.toFixed(1)}s`;

    source.src = resolveAssetUrl(movie.video);
    source.type = getVideoMimeType(movie.video);
    player.load();

    showPlayerStatus("");
    configureSubtitleControls();
    updatePlayPauseState();
    refreshIcons();
}

function renderGenres(movieGenres) {
    genres.innerHTML = "";

    movieGenres.forEach((genre) => {
        const tag = document.createElement("span");
        tag.className = "genre";
        tag.textContent = genre;
        genres.appendChild(tag);
    });
}

function buildEpisodeMovie(id) {
    const episode = getEpisodeById(id);

    if (!episode) return null;

    const series = getSeriesById(episode.seriesId) || episode.series || {};
    const seriesTitle = series.title || episode.seriesTitle || "Serie";

    return {
        id: episode.id,
        type: "episode",
        isEpisode: true,
        seriesId: episode.seriesId,
        kids: Boolean(series.kids),
        title: `${seriesTitle} - T${episode.seasonNumber}E${episode.episodeNumber}`,
        year: "",
        duration: "",
        contentRating: series.contentRating || "",
        quality: episode.quality || "Local",
        genres: series.genres || [],
        overview: episode.title,
        poster: episode.thumbnail || series.poster || "",
        backdrop: episode.backdrop || episode.thumbnail || series.backdrop || series.poster || "",
        video: episode.video,
        progress: episode.progress || 0,
        favorite: false,
        subtitles: episode.subtitles || [],
        nextEpisodeId: episode.nextEpisodeId || "",
        nextEpisodeUrl: episode.nextEpisodeId ? `movie.html?episode=${encodeURIComponent(episode.nextEpisodeId)}` : "",
        imdbId: series.imdbId || "",
        tmdbId: series.tmdbId || "",
        originalTitle: series.originalTitle || series.title || ""
    };
}

function renderLanding(movie) {
    const heroImage = movie.backdrop || movie.poster || "";
    const favorite = isFavorite(movie.id);
    const heroFallback = heroImage ? resolveAssetUrl(heroImage) : "";

    movieLandingTitle.textContent = movie.title;
    movieLandingOverview.textContent = movie.overview || "";
    movieLandingMeta.innerHTML = buildLandingMeta(movie);
    movieLandingBackdrop.src = getDisplayImageUrl(movie, "backdrop", heroFallback);
    setImageFallback(movieLandingBackdrop, heroFallback);
    movieLandingBackdrop.alt = movie.title;
    updateLandingFavoriteButton(favorite);
}

function buildLandingMeta(movie) {
    return [
        movie.year,
        movie.contentRating,
        movie.duration,
        movie.quality,
        ...(movie.genres || []).slice(0, 2)
    ].filter(Boolean).map((item) => `<span>${escapeHtml(item)}</span>`).join("");
}

function updateLandingFavoriteButton(favorite) {
    landingFavoriteButton.classList.toggle("is-active", favorite);
    landingFavoriteButton.setAttribute("aria-pressed", String(favorite));
    landingFavoriteButton.querySelector("span").textContent = favorite ? "Favorito" : "Favoritar";
}

function renderWatchNext(movie) {
    if (movie.isEpisode) {
        const series = getSeriesById(movie.seriesId);
        const episodes = (series?.seasons || [])
            .flatMap((season) => season.episodes || [])
            .filter((episode) => episode.id !== movie.id)
            .slice(0, 8);

        if (!episodes.length) {
            watchNextGrid.innerHTML = `<p class="watch-next__empty">Adicione mais episodios desta serie para continuar assistindo.</p>`;
            return;
        }

        watchNextGrid.innerHTML = episodes.map((episode) => `
            <article class="watch-next-card" data-next-episode-id="${escapeAttribute(episode.id)}" tabindex="0" role="button" aria-label="Abrir episodio ${escapeAttribute(episode.episodeNumber)}">
                <div class="watch-next-card__poster watch-next-card__poster--episode ${episode.thumbnail ? "" : "watch-next-card__poster--placeholder"}">
                    ${episode.thumbnail ? `<img src="${escapeAttribute(resolveAssetUrl(episode.thumbnail))}" alt="${escapeAttribute(episode.title)}" loading="lazy">` : `<span>T${escapeHtml(episode.seasonNumber)}E${escapeHtml(episode.episodeNumber)}</span>`}
                </div>
                <div class="watch-next-card__body">
                    <h3>${escapeHtml(episode.title)}</h3>
                    <p>Temporada ${escapeHtml(episode.seasonNumber)} - ${escapeHtml(episode.quality || "Local")}</p>
                </div>
            </article>
        `).join("");
        return;
    }

    const currentGenres = new Set(movie.genres || []);
    const recommendations = filterContentByProfile(getMovies())
        .filter((candidate) => candidate.id !== movie.id)
        .map((candidate) => ({
            ...candidate,
            genreScore: (candidate.genres || []).filter((genre) => currentGenres.has(genre)).length
        }))
        .filter((candidate) => candidate.genreScore > 0)
        .sort((a, b) => b.genreScore - a.genreScore || Number(b.id || 0) - Number(a.id || 0))
        .slice(0, 8);

    if (!recommendations.length) {
        watchNextGrid.innerHTML = `<p class="watch-next__empty">Adicione mais filmes para receber recomendações por gênero.</p>`;
        return;
    }

    watchNextGrid.innerHTML = recommendations.map((recommendation) => {
        const fallbackPoster = resolveAssetUrl(recommendation.poster);
        const poster = movieImageUrl(recommendation, {
            type: "poster",
            size: "w780",
            fallback: fallbackPoster
        });

        return `
        <article class="watch-next-card" data-next-movie-id="${escapeAttribute(recommendation.id)}" tabindex="0" role="button" aria-label="Abrir ${escapeAttribute(recommendation.title)}">
            <div class="watch-next-card__poster">
                <img src="${escapeAttribute(poster)}" alt="${escapeAttribute(recommendation.title)}" loading="lazy"${tmdbImageFallbackAttributes(fallbackPoster)}>
            </div>
            <div class="watch-next-card__body">
                <h3>${escapeHtml(recommendation.title)}</h3>
                <p>${escapeHtml([recommendation.year, ...(recommendation.genres || []).slice(0, 2)].filter(Boolean).join(" • "))}</p>
            </div>
        </article>
    `;
    }).join("");
}

function showPlayer() {
    enterWatchMode();
    movieLanding.hidden = true;
    playerShell.classList.remove("is-hidden");
    showControlsTemporarily();

    if (currentMovie?.video) {
        player.play().catch(() => showControlsTemporarily());
    }
}

function enterWatchMode() {
    moviePage?.classList.add("is-watch-mode");
    document.body.classList.add("is-watch-mode");
}

function exitWatchMode() {
    player.pause();
    setHeaderPlayingState(false);
    hideControls();
    movieLanding.hidden = false;
    playerShell.classList.add("is-hidden");
    moviePage?.classList.remove("is-watch-mode");
    document.body.classList.remove("is-watch-mode");
    window.scrollTo({ top: 0, behavior: "auto" });
}

function closePlayerWindow() {
    player.pause();
    window.close();

    window.setTimeout(() => {
        navigateTo("../index.html", { instant: true });
    }, 120);
}

function setHeaderPlayingState(isPlaying) {
    moviePage?.classList.toggle("is-video-playing", isPlaying);
}

function renderSubtitleTracks(subtitles) {
    subtitles.forEach((subtitle) => {
        const track = document.createElement("track");
        track.kind = "subtitles";
        track.label = subtitle.label;
        track.srclang = subtitle.srclang;
        track.src = resolveAssetUrl(subtitle.src);

        if (subtitle.default) {
            track.default = true;
        }

        player.appendChild(track);
    });
}

function clearSubtitleTracks() {
    player.querySelectorAll("track").forEach((track) => track.remove());
}

function configureSubtitleControls() {
    window.setTimeout(() => {
        const tracks = Array.from(player.textTracks || []);

        subtitleTrackSelect.innerHTML = "";

        if (!tracks.length) {
            subtitleOption.hidden = true;
            subtitleSizeOption.hidden = true;
            subtitleSyncOption.hidden = true;
            return;
        }

        subtitleOption.hidden = false;
        subtitleSizeOption.hidden = false;
        subtitleSyncOption.hidden = false;

        subtitleTrackSelect.appendChild(new Option("Desativadas", "off"));

        tracks.forEach((track, index) => {
            subtitleTrackSelect.appendChild(
                new Option(track.label || track.language || `Legenda ${index + 1}`, String(index))
            );

            track.mode = track.mode === "showing" ? "showing" : "disabled";
        });

        const showingIndex = tracks.findIndex((track) => track.mode === "showing");
        const preferredIndex = tracks.findIndex((track) => track.language === preferences.subtitleLanguage);

        if (preferences.subtitleLanguage === "off") {
            setSubtitleTrack("off");
            subtitleTrackSelect.value = "off";
        } else if (preferredIndex >= 0) {
            setSubtitleTrack(String(preferredIndex));
            subtitleTrackSelect.value = String(preferredIndex);
        } else if (showingIndex >= 0) {
            subtitleTrackSelect.value = String(showingIndex);
        } else {
            subtitleTrackSelect.value = "off";
        }

        if (subtitleDelay) {
            shiftSubtitleCues(subtitleDelay);
        }

        applySubtitleCuePreferences();
    }, 250);
}

function configureAudioControls() {
    const tracks = player.audioTracks ? Array.from(player.audioTracks) : [];

    audioTrackSelect.innerHTML = "";

    if (!tracks.length) {
        audioOption.hidden = true;
        return;
    }

    audioOption.hidden = false;

    tracks.forEach((track, index) => {
        audioTrackSelect.appendChild(
            new Option(track.label || track.language || `Faixa ${index + 1}`, String(index))
        );

    });

    selectPreferredAudioTrack(tracks);
}

function togglePlay() {
    if (player.paused) {
        player.play().catch(() => showControlsTemporarily());
        return;
    }

    player.pause();
}

function skip(seconds) {
    seekTo(player.currentTime + seconds);
}

function updateProgress() {
    if (!isSeeking) {
        const ratio = player.duration ? player.currentTime / player.duration : 0;
        progressSeek.value = String(Math.round(ratio * 1000));
    }

    timeDisplay.textContent = `${formatTime(player.currentTime)} / ${formatTime(player.duration)}`;
    saveProgressThrottled();
    maybeStartCreditsSkip();
}

function seekFromInput() {
    if (!player.duration) return;

    const ratio = Number(progressSeek.value) / 1000;
    const target = ratio * player.duration;

    seekTo(target);
    timeDisplay.textContent = `${formatTime(target)} / ${formatTime(player.duration)}`;
}

function seekTo(time) {
    if (!player.duration) return;

    const target = clamp(time, 0, player.duration);

    if (typeof player.fastSeek === "function") {
        player.fastSeek(target);
    } else {
        player.currentTime = target;
    }

    showControlsTemporarily();
}

function saveProgressThrottled() {
    if (!currentMovie || !player.duration) return;

    const now = Date.now();
    if (now - lastProgressSave < 2500 && !player.paused) return;

    lastProgressSave = now;

    saveWatchProgress(currentMovie, player.currentTime, player.duration);
}

function restoreProgress() {
    if (savedProgressRestored || !currentMovie || !player.duration) return;

    savedProgressRestored = true;

    const saved = getWatchProgress(currentMovie);

    if (saved) {
        player.currentTime = saved.currentTime;
        showPlayerStatus(`Continuando de ${formatTime(saved.currentTime)}.`);
        window.setTimeout(() => showPlayerStatus(""), 2600);
    }
}

function toggleMute() {
    player.muted = !player.muted;
    updateVolumeState();
}

function setVolume(value) {
    const maxVolume = Number(preferences.maxVolume || 100) / 100;
    player.volume = Math.min(Number(value), maxVolume);
    player.muted = player.volume === 0;
    updateVolumeState();
}

function updateVolumeState() {
    volumeSlider.value = player.muted ? "0" : String(player.volume);
    setButtonIcon(muteButton, player.muted || player.volume === 0 ? "volume-x" : "volume-2");
}

function updatePlayPauseState() {
    setButtonIcon(playPauseButton, player.paused ? "play" : "pause");
    playPauseButton.setAttribute("aria-label", player.paused ? "Reproduzir" : "Pausar");
    playerShell.classList.toggle("is-paused", player.paused);
}

function setSubtitleTrack(value) {
    const tracks = Array.from(player.textTracks || []);

    if (value === "off") {
        tracks.forEach((track) => {
            track.mode = "disabled";
        });
        return;
    }

    const index = Number(value);

    tracks.forEach((track, currentIndex) => {
        track.mode = currentIndex === index ? "showing" : "disabled";
    });
}

function setSubtitleSize(size) {
    playerShell.dataset.subtitleSize = size;
}

function setSubtitlePosition(position) {
    playerShell.dataset.subtitlePosition = position;
    applySubtitleCuePreferences();
}

function adjustSubtitleDelay(amount) {
    subtitleDelay = Number((subtitleDelay + amount).toFixed(1));
    subtitleDelayValue.textContent = `${subtitleDelay.toFixed(1)}s`;
    shiftSubtitleCues(amount);
}

function shiftSubtitleCues(amount) {
    Array.from(player.textTracks || []).forEach((track) => {
        Array.from(track.cues || []).forEach((cue) => {
            cue.startTime = Math.max(0, cue.startTime + amount);
            cue.endTime = Math.max(cue.startTime + 0.1, cue.endTime + amount);
        });
    });
}

function applySubtitleCuePreferences() {
    const lineByPosition = {
        bottom: "auto",
        middle: 50,
        top: 12
    };

    Array.from(player.textTracks || []).forEach((track) => {
        Array.from(track.cues || []).forEach((cue) => {
            cue.line = lineByPosition[preferences.subtitlePosition] ?? "auto";
        });
    });
}

function setAudioTrack(index) {
    const tracks = player.audioTracks ? Array.from(player.audioTracks) : [];

    tracks.forEach((track, currentIndex) => {
        track.enabled = currentIndex === index;
    });
}

function selectPreferredAudioTrack(tracks) {
    if (!tracks.length) return;

    const preferredLanguage = String(preferences.audioLanguage || "original").toLowerCase();
    const surroundPattern = /(5\.1|7\.1|surround|dts|dolby|atmos)/i;
    let index = -1;

    if (preferences.preferSurround) {
        index = tracks.findIndex((track) => surroundPattern.test(`${track.label || ""} ${track.language || ""}`));
    }

    if (index < 0 && preferredLanguage !== "original") {
        index = tracks.findIndex((track) => String(track.language || "").toLowerCase() === preferredLanguage);
    }

    if (index < 0) {
        index = tracks.findIndex((track) => track.enabled);
    }

    if (index < 0) {
        index = 0;
    }

    setAudioTrack(index);
    audioTrackSelect.value = String(index);
}

async function toggleFullscreen() {
    try {
        if (document.fullscreenElement) {
            await document.exitFullscreen();
            return;
        }

        await playerShell.requestFullscreen();
    } catch {
        showControlsTemporarily();
    }
}

function updateFullscreenState() {
    setButtonIcon(fullscreenButton, document.fullscreenElement ? "minimize" : "maximize");
}

function showControlsTemporarily() {
    playerShell.classList.add("is-active");
    clearTimeout(controlsTimer);

    controlsTimer = window.setTimeout(() => {
        playerShell.classList.remove("is-active");
    }, 2600);
}

function hideControls() {
    clearTimeout(controlsTimer);
    playerShell.classList.remove("is-active");
}

function hideControlsWhenPointerLeavesPage(event) {
    const rect = playerShell.getBoundingClientRect();
    const isInside =
        event.clientX >= rect.left &&
        event.clientX <= rect.right &&
        event.clientY >= rect.top &&
        event.clientY <= rect.bottom;

    if (!isInside) {
        hideControls();
    }
}

function buildInfoMeta(movie) {
    return [
        movie.year,
        movie.contentRating,
        movie.duration,
        movie.quality,
    ].filter(Boolean).join(" • ");
}

function getDisplayImageUrl(item, type, fallback) {
    const options = {
        type,
        size: type === "backdrop" ? "w1280" : "w780",
        fallback
    };

    return item?.isEpisode ? seriesImageUrl(item, options) : movieImageUrl(item, options);
}

function setImageFallback(element, fallback) {
    if (!fallback) {
        element.removeAttribute("data-fallback-src");
        return;
    }

    element.dataset.fallbackSrc = fallback;
    element.dataset.fallbackApplied = "false";
}

function resolveAssetUrl(assetPath) {
    return new URL(`../${assetPath}`, window.location.href).href;
}

function getVideoMimeType(videoPath) {
    const extension = videoPath.split(".").pop().toLowerCase();

    const types = {
        mp4: "video/mp4",
        webm: "video/webm",
        mov: "video/quicktime",
        avi: "video/x-msvideo",
        mkv: "video/x-matroska"
    };

    return types[extension] || "";
}

function formatTime(value) {
    if (!Number.isFinite(value)) return "0:00";

    const totalSeconds = Math.max(0, Math.floor(value));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours) {
        return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
    }

    return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}

function setButtonIcon(button, icon) {
    button.innerHTML = `<i data-lucide="${icon}"></i>`;
    refreshIcons();
}

function maybeSkipIntro() {
    if (introSkipped || !preferences.skipIntro || !currentMovie?.isEpisode || !player.duration) return;
    if (player.currentTime > 5 || player.duration < 600) return;

    introSkipped = true;
    seekTo(Math.min(85, player.duration - 30));
    showPlayerStatus("Abertura pulada.");
    window.setTimeout(() => showPlayerStatus(""), 1800);
}

function maybeStartCreditsSkip() {
    if (creditsCountdownStarted || !preferences.skipCredits || !currentMovie?.nextEpisodeId || !player.duration) return;
    if (player.duration - player.currentTime > 45) return;

    creditsCountdownStarted = true;
    startNextEpisodeCountdown();
}

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function showUnavailableMovie(message) {
    titleElement.textContent = message;
    movieLandingTitle.textContent = message;
    movieLandingOverview.textContent = "Volte para a biblioteca e tente abrir outro item.";
    movieLandingMeta.innerHTML = "";
    landingPlayButton.disabled = true;
    landingFavoriteButton.disabled = true;
    showPlayerStatus(message);
    player.hidden = true;
}

function showPlayerStatus(message) {
    if (!status) return;

    status.textContent = message;
    status.hidden = !message;
}

landingPlayButton.addEventListener("click", showPlayer);
playerBackButton.addEventListener("click", exitWatchMode);
playerCloseButton.addEventListener("click", closePlayerWindow);

landingFavoriteButton.addEventListener("click", () => {
    if (!currentMovie) return;
    updateLandingFavoriteButton(toggleFavorite(currentMovie));
});

watchNextGrid.addEventListener("click", (event) => {
    const episodeCard = event.target.closest("[data-next-episode-id]");

    if (episodeCard) {
        navigateTo(`movie.html?episode=${encodeURIComponent(episodeCard.dataset.nextEpisodeId)}`);
        return;
    }

    const card = event.target.closest("[data-next-movie-id]");

    if (!card) return;

    navigateTo(`movie.html?id=${card.dataset.nextMovieId}`);
});

watchNextGrid.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") return;

    const episodeCard = event.target.closest("[data-next-episode-id]");

    if (episodeCard) {
        event.preventDefault();
        navigateTo(`movie.html?episode=${encodeURIComponent(episodeCard.dataset.nextEpisodeId)}`);
        return;
    }

    const card = event.target.closest("[data-next-movie-id]");

    if (!card) return;

    event.preventDefault();
    navigateTo(`movie.html?id=${card.dataset.nextMovieId}`);
});

player.addEventListener("loadedmetadata", () => {
    configureAudioControls();
    restoreProgress();
    updateProgress();
});

player.addEventListener("timeupdate", updateProgress);
player.addEventListener("play", () => {
    maybeSkipIntro();
    updatePlayPauseState();
    setHeaderPlayingState(true);
});
player.addEventListener("pause", () => {
    updatePlayPauseState();
    setHeaderPlayingState(false);
    saveProgressThrottled();
    showControlsTemporarily();
});
player.addEventListener("ended", () => {
    setHeaderPlayingState(false);
    clearWatchProgress(currentMovie);
    startNextEpisodeCountdown();
});

player.addEventListener("error", () => {
    showPlayerStatus("Não foi possível reproduzir este arquivo no navegador. Se for MKV, talvez seja necessário converter para MP4.");
});

playerShell.addEventListener("mousemove", showControlsTemporarily);
playerShell.addEventListener("mouseleave", () => {
    hideControls();
});
document.addEventListener("mousemove", hideControlsWhenPointerLeavesPage);

playPauseButton.addEventListener("click", togglePlay);
player.addEventListener("click", togglePlay);
rewindButton.addEventListener("click", () => skip(-15));
forwardButton.addEventListener("click", () => skip(15));

progressSeek.addEventListener("pointerdown", () => {
    isSeeking = true;
});

progressSeek.addEventListener("touchstart", () => {
    isSeeking = true;
});

progressSeek.addEventListener("input", () => {
    isSeeking = true;
    seekFromInput();
    updateProgress();
});

function finishProgressSeek() {
    seekFromInput();
    isSeeking = false;
    updateProgress();
    saveProgressThrottled();
}

progressSeek.addEventListener("change", finishProgressSeek);
progressSeek.addEventListener("pointerup", finishProgressSeek);
progressSeek.addEventListener("touchend", finishProgressSeek);

progressSeek.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;

    window.requestAnimationFrame(() => {
        seekFromInput();
        updateProgress();
    });
});

muteButton.addEventListener("click", toggleMute);
volumeSlider.addEventListener("input", () => setVolume(volumeSlider.value));
fullscreenButton.addEventListener("click", toggleFullscreen);
document.addEventListener("fullscreenchange", updateFullscreenState);

subtitleTrackSelect.addEventListener("change", () => setSubtitleTrack(subtitleTrackSelect.value));
subtitleSizeSelect.addEventListener("change", () => setSubtitleSize(subtitleSizeSelect.value));
subtitleDelayDown.addEventListener("click", () => adjustSubtitleDelay(-0.5));
subtitleDelayUp.addEventListener("click", () => adjustSubtitleDelay(0.5));
audioTrackSelect.addEventListener("change", () => setAudioTrack(Number(audioTrackSelect.value)));

document.addEventListener("keydown", (event) => {
    if (event.target.matches("input, select, button")) return;

    if (event.code === "Space") {
        event.preventDefault();
        togglePlay();
    }

    if (event.key === "ArrowLeft") skip(-15);
    if (event.key === "ArrowRight") skip(15);
    if (event.key.toLowerCase() === "m") toggleMute();
    if (event.key.toLowerCase() === "f") toggleFullscreen();
});

loadMovie();

function startNextEpisodeCountdown() {
    if (!currentMovie?.nextEpisodeId) return;
    if (nextEpisodeCountdown) return;

    let remaining = 8;

    showPlayerStatus(`Próximo episódio em ${remaining}s.`);

    nextEpisodeCountdown = window.setInterval(() => {
        remaining -= 1;
        showPlayerStatus(`Próximo episódio em ${remaining}s.`);

        if (remaining <= 0) {
            window.clearInterval(nextEpisodeCountdown);
            nextEpisodeCountdown = null;
            navigateTo(currentMovie.nextEpisodeUrl || `movie.html?episode=${encodeURIComponent(currentMovie.nextEpisodeId)}`);
        }
    }, 1000);
}
