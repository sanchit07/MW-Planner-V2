import { extractLocalNameWithType, pickPrimaryType } from "./place-type-utils";

declare global {
  interface Window {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    google: any;
  }
}

/** Result from Places Text Search — includes coordinates and enriched display data */
export interface GooglePlaceSearchResult {
  lat: number;
  lng: number;
  name?: string;
  formatted_address?: string;
  place_id?: string;
  types?: string[];
  /** Human-readable local region name (e.g. "Selangor", "Kuala Lumpur") */
  localName?: string;
  /** Address-component type that produced localName (e.g. "locality", "administrative_area_level_1") */
  localNameType?: string;
  /** Most descriptive place type key (e.g. "restaurant", "hospital") */
  primaryTypeKey?: string;
  /** Human-readable label for the primary type (e.g. "Restaurant") */
  primaryTypeLabel?: string;
  /**
   * Photo getter function (opt-in, lazy).
   * Call getPhotoUrl() to fetch the image — avoids billing until user sees the result.
   */
  getPhotoUrl?: () => string | null;
}

/**
 * Search places by text using Google Places Text Search (PlacesService.textSearch).
 * Returns enriched results with localName, primaryType, and a lazy photo getter.
 */
export const searchPlacesByText = async (
  input: string,
  country: string,
  language?: string,
): Promise<GooglePlaceSearchResult[]> => {
  if (!window.google) {
    console.error("Google Maps API is not loaded.");
    return [];
  }
  const limit = 8;
  const request = {
    textQuery: input.trim(),
    fields: [
      "displayName",
      "location",
      "formattedAddress",
      "id",
      "types",
      "primaryType",
      "primaryTypeDisplayName",
      "addressComponents",
      "photos",
    ],
    includedType: "",
    useStrictTypeFiltering: false,
    isOpenNow: false,
    language: language || "en-US",
    maxResultCount: limit,
    region: country,
  };

  try {
    const { places } =
      await window.google.maps.places.Place.searchByText(request);

    if (!places || places.length === 0) return [];

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const placesData: GooglePlaceSearchResult[] = places.map((r: any) => {
      const types: string[] = r.types ?? [];
      const addressComponents = r.addressComponents ?? [];

      // Pick the most specific type key
      const primaryTypeKey =
        r.primaryType ?? pickPrimaryType(types) ?? types[0];

      // Human-readable label from Google (e.g. "Hospital") or our own mapping
      const primaryTypeLabel =
        r.primaryTypeDisplayName?.text ?? r.primaryTypeDisplayName ?? null;

      // Extract local region name + its address-component type from address components
      const localNameEntry = extractLocalNameWithType(addressComponents);
      const localName = localNameEntry?.name ?? r.formattedAddress ?? null;
      const localNameType = localNameEntry?.type ?? undefined;

      // Lazy photo getter — only fetches URI when called
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const photos: any[] = r.photos ?? [];
      const getPhotoUrl =
        photos.length > 0
          ? () => {
              try {
                return (
                  photos[0].getURI?.({ maxWidth: 120, maxHeight: 120 }) ?? null
                );
              } catch {
                return null;
              }
            }
          : undefined;

      return {
        lat: r.location?.lat() ?? 0,
        lng: r.location?.lng() ?? 0,
        name: r.displayName,
        formatted_address: r.formattedAddress,
        place_id: r.id,
        types,
        localName,
        localNameType,
        primaryTypeKey,
        primaryTypeLabel,
        getPhotoUrl,
      } satisfies GooglePlaceSearchResult;
    });

    return placesData.filter((p) => p.lat !== 0 && p.lng !== 0);
  } catch (err) {
    console.error("searchPlacesByText error:", err);
    return [];
  }
};

export const fetchNearbyPlaces = async (
  location: {
    lat: number;
    lng: number;
    radius: number;
  },
  searchType?: string,
  language?: string,
) => {
  if (!window.google) {
    console.error("Google Maps API is not loaded.");
    return;
  }
  const mapLocation = new window.google.maps.LatLng(location.lat, location.lng);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const request: any = {
    fields: ["*"],
    locationRestriction: {
      center: mapLocation,
      radius: location.radius,
    },
  };
  if (searchType) {
    request["includedPrimaryTypes"] = [searchType];
  }
  if (language) {
    request["language"] = language;
  }
  try {
    const { places } =
      await window.google.maps.places.Place.searchNearby(request);
    return places;
  } catch (err) {
    console.error(err);
    return {
      error: "Error fetching data from Google Places API.",
    };
  }
};

// Promise-level cache: simultaneous hover+click for the same pin share one request.
const poiPhotoCache = new Map<string, Promise<string | null>>();

/**
 * Lazily fetch the first photo URL for a POI by text + location bias.
 * Returns null when the Google Maps SDK isn't available, when no photo
 * exists, or when the request errors — never throws.
 */
export const getPOIPhotoUrl = (poi: {
  displayName: string;
  locationLat: number;
  locationLng: number;
}): Promise<string | null> => {
  if (!window.google?.maps?.places?.Place) return Promise.resolve(null);

  const key = `${poi.displayName}|${poi.locationLat}|${poi.locationLng}`;
  const cached = poiPhotoCache.get(key);
  if (cached) return cached;

  const fetchPromise = (async () => {
    try {
      const { places } = await window.google.maps.places.Place.searchByText({
        textQuery: poi.displayName,
        locationBias: new window.google.maps.LatLng(
          poi.locationLat,
          poi.locationLng,
        ),
        fields: ["photos"],
        maxResultCount: 1,
      });
      const photo = places?.[0]?.photos?.[0];
      return photo?.getURI?.({ maxWidth: 240, maxHeight: 160 }) ?? null;
    } catch {
      return null;
    }
  })();

  poiPhotoCache.set(key, fetchPromise);
  return fetchPromise;
};

/** Test-only: clear the photo cache between runs. Exported intentionally. */
export const __resetPOIPhotoCacheForTests = () => {
  poiPhotoCache.clear();
};
