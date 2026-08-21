/**
 * Indicative cinema line-up for the read-only movie-preview surface in Step 4.
 *
 * This is a small static, read-only catalog copied from `shared/cinema-films.ts`
 * (the v2 build has no `shared/` path alias, so the data is duplicated rather
 * than imported across the boundary). Films are ONLY an indicative preview of
 * the buying environment — never a buy unit — so every surface that renders
 * them must carry an "indicative line-up, as of <date>" freshness label.
 */

export interface CinemaFilm {
  id: string;
  title: string;
  /** Genres, using the same tokens as CINEMA_GENRES in the targeting UI. */
  genres: string[];
  /** Certification, using the same tokens as CINEMA_RATINGS. */
  rating: string;
  /** Feature runtime in minutes. */
  durationMinutes: number;
}

/** The Monday of the line-up window this static catalog represents. */
export const CINEMA_LINEUP_AS_OF = "2026-06-15";

export const CINEMA_FILMS: CinemaFilm[] = [
  {
    id: "avatar-way-of-water",
    title: "Avatar: The Way of Water",
    genres: ["Sci-Fi", "Adventure", "Action"],
    rating: "PG-13",
    durationMinutes: 192,
  },
  {
    id: "inception",
    title: "Inception",
    genres: ["Sci-Fi", "Action", "Thriller"],
    rating: "PG-13",
    durationMinutes: 148,
  },
  {
    id: "interstellar",
    title: "Interstellar",
    genres: ["Sci-Fi", "Adventure", "Drama"],
    rating: "PG-13",
    durationMinutes: 169,
  },
  {
    id: "the-dark-knight",
    title: "The Dark Knight",
    genres: ["Action", "Crime", "Drama"],
    rating: "PG-13",
    durationMinutes: 152,
  },
  {
    id: "top-gun-maverick",
    title: "Top Gun: Maverick",
    genres: ["Action", "Drama"],
    rating: "PG-13",
    durationMinutes: 130,
  },
  {
    id: "avengers-endgame",
    title: "Avengers: Endgame",
    genres: ["Action", "Sci-Fi", "Adventure"],
    rating: "PG-13",
    durationMinutes: 181,
  },
  {
    id: "spider-man-no-way-home",
    title: "Spider-Man: No Way Home",
    genres: ["Action", "Adventure", "Sci-Fi"],
    rating: "PG-13",
    durationMinutes: 148,
  },
  {
    id: "joker",
    title: "Joker",
    genres: ["Crime", "Drama", "Thriller"],
    rating: "R",
    durationMinutes: 122,
  },
  {
    id: "john-wick",
    title: "John Wick",
    genres: ["Action", "Thriller"],
    rating: "R",
    durationMinutes: 101,
  },
  {
    id: "jurassic-world",
    title: "Jurassic World",
    genres: ["Action", "Adventure", "Sci-Fi"],
    rating: "PG-13",
    durationMinutes: 124,
  },
];
