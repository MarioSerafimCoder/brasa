// ==========================================================
// BRasa
// Movie Page Controller
// ==========================================================

import { getMovieById } from "../../data/movies.js";
import { applyPreferences, getPreferences } from "../utils/preferences.js";

const playerShell = document.getElementById("playerShell");
const titleElement = document.getElementById("movie-title");
const player = document.getElementById("moviePlayer");
const source = document.getElementById("movieSource");
const status = document.getElementById("movieStatus");

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

let currentMovie = null;
let controlsTimer = null;
let isSeeking = false;
let savedProgressRestored = false;
let lastProgressSave = 0;
let subtitleDelay = 0;
let preferences = applyPreferences();

function loadMovie() {
    if (!movieId) {
        showUnavailableMovie("Filme não encontrado");
        return;
    }

    const movie = getMovieById(movieId);

    if (!movie) {
        showUnavailableMovie("Filme não encontrado");
        return;
    }

    currentMovie = movie;

    document.title = `BRasa • ${movie.title}`;
    titleElement.textContent = movie.title;
    year.textContent = movie.year;
    duration.textContent = movie.duration;
    quality.textContent = movie.quality;
    overview.textContent = movie.overview;

    playerInfoTitle.textContent = movie.title;
    playerInfoMeta.textContent = buildInfoMeta(movie);
    playerInfoOverview.textContent = movie.overview;

    if (movie.poster) {
        player.poster = resolveAssetUrl(movie.poster);
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
    preferences = getPreferences();
    playerShell.dataset.subtitleSize = preferences.subtitleSize;
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

        if (track.enabled) {
            audioTrackSelect.value = String(index);
        }
    });
}

function togglePlay() {
    if (player.paused) {
        player.play();
        return;
    }

    player.pause();
}

function skip(seconds) {
    player.currentTime = clamp(player.currentTime + seconds, 0, player.duration || 0);
}

function updateProgress() {
    if (!isSeeking) {
        const ratio = player.duration ? player.currentTime / player.duration : 0;
        progressSeek.value = String(Math.round(ratio * 1000));
    }

    timeDisplay.textContent = `${formatTime(player.currentTime)} / ${formatTime(player.duration)}`;
    saveProgressThrottled();
}

function seekFromInput() {
    if (!player.duration) return;

    const ratio = Number(progressSeek.value) / 1000;
    player.currentTime = ratio * player.duration;
}

function saveProgressThrottled() {
    if (!currentMovie || !player.duration) return;

    const now = Date.now();
    if (now - lastProgressSave < 2500 && !player.paused) return;

    lastProgressSave = now;

    localStorage.setItem(progressKey(), JSON.stringify({
        currentTime: player.currentTime,
        duration: player.duration,
        updatedAt: new Date().toISOString()
    }));
}

function restoreProgress() {
    if (savedProgressRestored || !currentMovie || !player.duration) return;

    savedProgressRestored = true;

    try {
        const saved = JSON.parse(localStorage.getItem(progressKey()) || "{}");

        if (saved.currentTime > 10 && saved.currentTime < player.duration - 20) {
            player.currentTime = saved.currentTime;
            showPlayerStatus(`Continuando de ${formatTime(saved.currentTime)}.`);
            window.setTimeout(() => showPlayerStatus(""), 2600);
        }
    } catch {
        localStorage.removeItem(progressKey());
    }
}

function progressKey() {
    return `brasa:movie-progress:${currentMovie.id}`;
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

function setAudioTrack(index) {
    const tracks = player.audioTracks ? Array.from(player.audioTracks) : [];

    tracks.forEach((track, currentIndex) => {
        track.enabled = currentIndex === index;
    });
}

async function toggleFullscreen() {
    if (document.fullscreenElement) {
        await document.exitFullscreen();
        return;
    }

    await playerShell.requestFullscreen();
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

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

function showUnavailableMovie(message) {
    titleElement.textContent = message;
    showPlayerStatus(message);
    player.hidden = true;
}

function showPlayerStatus(message) {
    if (!status) return;

    status.textContent = message;
    status.hidden = !message;
}

player.addEventListener("loadedmetadata", () => {
    configureAudioControls();
    restoreProgress();
    updateProgress();
});

player.addEventListener("timeupdate", updateProgress);
player.addEventListener("play", updatePlayPauseState);
player.addEventListener("pause", () => {
    updatePlayPauseState();
    saveProgressThrottled();
    showControlsTemporarily();
});
player.addEventListener("ended", saveProgressThrottled);

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

progressSeek.addEventListener("input", () => {
    isSeeking = true;
    updateProgress();
});
progressSeek.addEventListener("change", () => {
    seekFromInput();
    isSeeking = false;
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
