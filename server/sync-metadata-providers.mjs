export async function findMovieOnOmdb({ apiKey, parsed, override }) {
  if (override.imdbId) {
    const movie = await fetchOmdb(apiKey, { i: override.imdbId, plot: "full" });
    return movie?.Type === "movie" ? movie : null;
  }

  const titleCandidates = [
    override.title,
    parsed.title,
    ...parsed.candidates,
  ].filter(Boolean);

  for (const title of [...new Set(titleCandidates)]) {
    const movie = await fetchOmdb(apiKey, {
      t: title,
      y: override.year || parsed.year,
      type: "movie",
      plot: "full",
    });

    if (movie?.Type === "movie") return movie;
  }

  for (const title of [...new Set(titleCandidates)]) {
    const search = await fetchOmdb(apiKey, {
      s: title,
      y: override.year || parsed.year,
      type: "movie",
    });

    const best = chooseSearchResult(
      search?.Search || [],
      override.year || parsed.year,
    );
    if (best?.imdbID) {
      const movie = await fetchOmdb(apiKey, { i: best.imdbID, plot: "full" });
      if (movie?.Type === "movie") return movie;
    }
  }

  return null;
}

export async function fetchOmdb(apiKey, params) {
  const url = new URL("https://www.omdbapi.com/");
  url.searchParams.set("apikey", apiKey);
  url.searchParams.set("r", "json");

  for (const [key, value] of Object.entries(params)) {
    if (value) url.searchParams.set(key, value);
  }

  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok) {
    const details = data.Error ? ` ${data.Error}` : "";
    throw new Error(`OMDb retornou HTTP ${response.status}.${details}`);
  }

  if (data.Response === "False") return null;
  return data;
}

export function chooseSearchResult(results, year) {
  if (!results.length) return null;
  if (year) {
    const exactYear = results.find((result) => result.Year === year);
    if (exactYear) return exactYear;
  }
  return results[0];
}

export async function findMovieOnTmdb({ credentials, title, year, imdbId }) {
  let result = null;
  if (imdbId) {
    const found = await fetchTmdb(
      `/find/${encodeURIComponent(imdbId)}`,
      { external_source: "imdb_id", language: "pt-BR" },
      credentials,
    );
    result = found.movie_results?.[0] || null;
  }
  if (!result && title) {
    const found = await fetchTmdb(
      "/search/movie",
      {
        query: title,
        year: String(year || "").match(/\d{4}/)?.[0] || "",
        language: "pt-BR",
        include_adult: "false",
      },
      credentials,
    );
    result = found.results?.[0] || null;
  }
  if (!result?.id) return null;
  const details = await fetchTmdb(
    `/movie/${result.id}`,
    { language: "pt-BR", append_to_response: "external_ids" },
    credentials,
  );
  return { ...result, ...details };
}

export async function fetchTmdb(endpoint, params, credentials) {
  const url = new URL(`https://api.themoviedb.org/3${endpoint}`);
  Object.entries(params || {}).forEach(
    ([key, value]) => value && url.searchParams.set(key, value),
  );
  if (credentials.apiKey) url.searchParams.set("api_key", credentials.apiKey);
  const headers = credentials.readToken
    ? {
        Authorization: `Bearer ${credentials.readToken}`,
        Accept: "application/json",
      }
    : { Accept: "application/json" };
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(`TMDb retornou HTTP ${response.status}.`);
  return response.json();
}

export function mergeMovieMetadata(omdb, tmdb, parsed) {
  if (!tmdb) return omdb;
  const genres = (tmdb.genres || [])
    .map((item) => item.name)
    .filter(Boolean)
    .join(", ");
  return {
    ...omdb,
    Title: omdb.Title || tmdb.title || parsed.title,
    Year:
      omdb.Year || String(tmdb.release_date || "").slice(0, 4) || parsed.year,
    Runtime: omdb.Runtime || (tmdb.runtime ? `${tmdb.runtime} min` : ""),
    imdbRating:
      omdb.imdbRating || (tmdb.vote_average ? String(tmdb.vote_average) : ""),
    Genre: genres || omdb.Genre,
    Plot: tmdb.overview || (omdb.Plot && omdb.Plot !== "N/A" ? omdb.Plot : ""),
    imdbID: omdb.imdbID || tmdb.imdb_id || tmdb.external_ids?.imdb_id || "",
    Poster: omdb.Poster || "",
  };
}
