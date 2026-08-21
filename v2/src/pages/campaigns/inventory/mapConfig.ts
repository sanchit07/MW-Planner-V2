import type { MapControlsConfig } from "@components/ui/Mapbox";
import type { Map as MapboxMap } from "mapbox-gl";

/**
 * Shared read-only map config for the inventory View / Manual-edit maps:
 * 3D toggle only — no drawing tools, no search, no styles.
 */
export const readOnlyMapConfig: MapControlsConfig = {
  showDrawingTools: false,
  enableSelect: false,
  enablePolygon: false,
  enableCircle: false,
  enableLine: false,
  enableDelete: false,
  showViewTools: true,
  enableMountainView: false,
  enable3D: true,
  showMapStyles: false,
  enabledStyles: [],
  search: {
    enabled: false,
    showResults: false,
    searchTypes: ["place", "poi", "address"],
    limit: 5,
    showPOIFilter: false,
  },
};

/**
 * Apply ambient + directional lighting (mapbox-gl v3 lights API). Guarded so it
 * no-ops on versions/styles that don't support setLights.
 */
export const applyMapLights = (map: MapboxMap) => {
  try {
    const m = map as unknown as { setLights?: (lights: unknown) => void };
    if (typeof m.setLights !== "function") return;
    m.setLights([
      {
        id: "ambient",
        type: "ambient",
        properties: { color: "white", intensity: 0.6 },
      },
      {
        id: "directional",
        type: "directional",
        properties: {
          color: "white",
          intensity: 0.5,
          direction: [210, 30],
          "cast-shadows": true,
        },
      },
    ]);
  } catch (error) {
    console.error("Failed to set map lights:", error);
  }
};
