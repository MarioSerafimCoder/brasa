import { isFavoriteForActive, setFavoriteForActive } from "../services/profile-service.js";
export function isFavorite(movieOrId){const id=typeof movieOrId==="object"?movieOrId?.id:movieOrId;return id!=null&&isFavoriteForActive(id);}
export function toggleFavorite(movieOrId){const id=typeof movieOrId==="object"?movieOrId?.id:movieOrId;if(id==null)return false;return setFavoriteForActive(id,!isFavorite(id));}
export function withFavoriteState(movies){return movies.map((movie)=>({...movie,favorite:isFavorite(movie)}));}
export function getFavoriteMovies(movies){return withFavoriteState(movies).filter((movie)=>movie.favorite);}
