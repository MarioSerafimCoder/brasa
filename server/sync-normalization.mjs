import fs from "node:fs/promises";
import path from "node:path";

export function parseMovieFileName(fileName) {
  const baseName = path.basename(fileName, path.extname(fileName));
  const yearMatch = baseName.match(
    /(?:^|[\s[(._-])((?:19|20)\d{2})(?:[\s\])._-]|$)/,
  );
  const year = yearMatch?.[1] || "";

  const clean = baseName
    .replace(/[._]+/g, " ")
    .replace(/\[(?:19|20)\d{2}\]|\((?:19|20)\d{2}\)|(?:19|20)\d{2}/g, " ")
    .replace(/^\s*(?:comando\s*to|torrentdosfilmes\s*se)\s*[-–—]\s*/i, " ")
    .replace(/\s+audio\s+encoder\s+by\s+.*$/i, " ")
    .replace(
      /\b(4k|uhd|2160p|1080p|720p|480p|bluray|blu-ray|brrip|bdrip|webrip|web-rip|web-dl|webdl|remux|x264|x265|h264|h265|hevc|av1|dv|dolby\s*vision|hdr10\+?|hdr10plus|hdr|sdr|dublado|dub|legendado|dual|multi|audio|aac|ac3|eac3|ddp?\+?|atmos|truehd|dts(?:-hd)?|5[._ ]1|7[._ ]1|2[._ ]0|6ch|10bit|3d|hsbs|extended|fullscreen|repack|imax|full\s*hd|fullhd|hdtc|mp4|mkv|avi|mov|webm|torrent|xbrfilmestorrent|seroes|zoiudo)\b/gi,
      " ",
    )
    .replace(
      /\b(?:www\s*)?(?:bludv(?:\s*(?:tv|com))?|wolverdonfilmes(?:\s*com)?|torrentdosfilmes(?:\s*(?:se|com))?|comandotorrents(?:\s*com)?|starckfilmes|lapumia|ricksz|brshares|mld|ramontpb|johnl|sf)\b.*$/gi,
      " ",
    )
    .replace(/\[[^\]]*]|\([^)]*\)/g, " ")
    .replace(/\s+-\s+$/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  const dashParts = clean
    .split(/\s+-\s+/)
    .map((part) => part.trim())
    .filter(Boolean);
  const candidates = [
    clean,
    dashParts.join(" "),
    dashParts[0],
    dashParts.at(-1),
  ].filter(Boolean);

  return {
    baseName,
    title: candidates[0] || baseName,
    year,
    candidates: [...new Set(candidates)],
  };
}

export function extractImdbId(fileName) {
  return fileName.match(/\b(tt\d{7,10})\b/i)?.[1]?.toLowerCase() || "";
}

export function createLocalMovieMetadata(parsed, override, imdbId) {
  return {
    Title: override.title || parsed.title,
    Year: override.year || parsed.year || "",
    Runtime: "",
    imdbRating: "",
    Rated: "",
    Genre: "",
    Plot: "",
    imdbID: imdbId || "",
    Poster: "",
  };
}

export function assessMovieIdentification({
  omdb,
  parsed,
  override,
  fileImdbId,
}) {
  if (omdb?.Type && omdb.Type !== "movie")
    return {
      confidence: "low",
      reason: `A API retornou o tipo ${omdb.Type}.`,
      status: "incomplete",
    };
  if (
    omdb &&
    (override.imdbId || fileImdbId) &&
    omdb.imdbID === (override.imdbId || fileImdbId)
  )
    return {
      confidence: "high",
      reason: "IMDb ID confirmado.",
      status: "complete",
    };
  if (omdb && parsed.year && String(omdb.Year || "").includes(parsed.year))
    return {
      confidence: "high",
      reason: "Titulo e ano confirmados pelo provedor.",
      status: "complete",
    };
  if (omdb)
    return {
      confidence: "medium",
      reason:
        "Titulo confirmado, mas o ano nao estava disponivel para validacao.",
      status: "complete",
    };
  if (parsed.title && parsed.year)
    return {
      confidence: "low",
      reason: "Indexado pelo nome local; metadados externos indisponiveis.",
      status: "incomplete",
    };
  return {
    confidence: "unidentified",
    reason: "Nao foi possivel confirmar titulo e ano.",
    status: "incomplete",
  };
}

export function formatRuntime(runtime) {
  const minutes = Number(runtime?.match(/\d+/)?.[0]);
  if (!minutes) return runtime && runtime !== "N/A" ? runtime : "";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${rest}min`;
  return `${hours}h ${String(rest).padStart(2, "0")}min`;
}

export function inferQuality(fileName) {
  if (/4k|2160p|uhd/i.test(fileName)) return "4K";
  if (/1080p/i.test(fileName)) return "1080p";
  if (/720p/i.test(fileName)) return "720p";
  return "Local";
}

export function translateGenres(genreList = "") {
  const dictionary = {
    Action: "Acao",
    Adventure: "Aventura",
    Animation: "Animacao",
    Comedy: "Comedia",
    Crime: "Crime",
    Documentary: "Documentario",
    Drama: "Drama",
    Family: "Familia",
    Fantasy: "Fantasia",
    Horror: "Terror",
    Mystery: "Misterio",
    Romance: "Romance",
    "Sci-Fi": "Ficcao Cientifica",
    Thriller: "Suspense",
    War: "Guerra",
    Western: "Faroeste",
  };

  return genreList
    .split(",")
    .map((genre) => genre.trim())
    .filter((genre) => genre && genre !== "N/A")
    .map((genre) => dictionary[genre] || genre);
}

export function parseSubtitleLanguages(value) {
  const labels = {
    "pt-br": "Portugues (Brasil)",
    pt: "Portugues",
    en: "English",
    es: "Espanol",
  };

  return value
    .split(",")
    .map((language) => language.trim().toLowerCase())
    .filter(Boolean)
    .map((language) => ({
      code: language,
      searchCode: language,
      label: labels[language] || language,
    }));
}

export function toWebVtt(content) {
  const normalized = content
    .replace(/^\uFEFF/, "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2");

  if (normalized.trimStart().startsWith("WEBVTT")) {
    return normalized;
  }

  return `WEBVTT\n\n${normalized}`;
}

export function hasSubtitleCue(content) {
  return /\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}/.test(
    content,
  );
}

export function normalizePath(value) {
  return value.replace(/\\/g, "/");
}

export function slugify(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function sanitizeFileName(value) {
  const sanitized = String(value)
    .normalize("NFKC")
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, "")
    .replace(/\s+/g, " ")
    .replace(/[. ]+$/g, "")
    .trim();

  return sanitized || "Filme";
}

export async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}
