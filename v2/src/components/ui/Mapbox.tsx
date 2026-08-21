import { CONFIG } from "@config/index";
import { TolgeeConfig } from "@config/tolgee";
import { useAnnounce } from "@hooks/useAnnounce";
import MapboxDraw from "@mapbox/mapbox-gl-draw";
import { CombinedItem } from "@services/map-marker-lists/mapMarkerLocationsSlice";
import { TolgeeProvider, useTranslate, useTolgee } from "@tolgee/react";
import { filterPOIPlacesInsidePolygon } from "@utils/geofencing-pois";
import {
  fetchNearbyPlaces,
  getPOIPhotoUrl,
  GooglePlaceSearchResult,
  searchPlacesByText,
} from "@utils/google-poi-category-api";
// Keep the POI popup component as a prop rather than a direct import —
// UI primitives should not depend on page-level modules.
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import {
  getPlaceTypeInfo,
  getPlaceTypeLabel,
  pickPrimaryType,
} from "@utils/place-type-utils";
import { getPOIIconUrl } from "@utils/poi-icon-url";
import {
  MapPin,
  Square,
  Route,
  Circle,
  Trash2,
  Mountain,
  Layers3,
  Map as MapIcon,
  Satellite,
  Trees,
  Sun,
  Moon,
  Search,
} from "lucide-react";
import mapboxgl from "mapbox-gl";
import React, {
  useRef,
  useEffect,
  useState,
  useMemo,
  useCallback,
} from "react";
import { createRoot } from "react-dom/client";
import ReactDOMServer from "react-dom/server";
import {
  MapGeometry,
  MapMarkerLocation,
  POIPlaceData,
} from "src/types/campaign.types";
import { MapInventoryItem, InventoryItem } from "src/types/inventory.types";

import { Badge } from "./Badge";
import { Button } from "./Button";
import { Input } from "./Input";
import InventoryCategoryPin from "./InventoryCategoryPin";
import MultiSelect, { TreeNode } from "./MultiSelect";
import POICategoryPin, {
  hasPOIPin,
  resolvePinCategory,
} from "./POICategoryPin";
import { Slider } from "./Slider";

// Primary color for all markers - mw-primary-500
const PRIMARY_MARKER_COLOR = "#2176cc";
const SECONDARY_MARKER_COLOR = "#4CB0E4";
// Selected-for-campaign inventories.
const SELECTED_MARKER_COLOR = "#16a34a";

// Teardrop pin containing a CloudFront POI image inside the circular head.
// Tries `iconUrl` first, then each of `fallbackUrls` in order (semantically
// related categories like primary_school → school). If all fail, renders
// a plain pin derived from the POI's place type — so the marker never shows
// a broken image.
interface POIIconMarkerProps {
  iconUrl: string;
  fallbackUrls?: string[];
  placeType?: string | null;
  primaryTypeDisplayName?: string | null;
  alt: string;
  pinSize?: number;
  isSelected?: boolean;
  /** Inventory is selected for the campaign — render a green pin. */
  selected?: boolean;
}

const POIIconMarker: React.FC<POIIconMarkerProps> = ({
  iconUrl,
  fallbackUrls = [],
  placeType,
  primaryTypeDisplayName,
  alt,
  pinSize = 32,
  isSelected = false,
  selected = false,
}) => {
  const candidates = useMemo(
    () => [iconUrl, ...fallbackUrls],
    [iconUrl, fallbackUrls],
  );
  const [attempt, setAttempt] = useState(0);
  // Reset when the marker is reused for a new POI.
  useEffect(() => {
    setAttempt(0);
  }, [candidates]);

  const allFailed = attempt >= candidates.length;
  const typeInfo = getPlaceTypeInfo(
    placeType ?? primaryTypeDisplayName ?? null,
  );
  const TypeIcon = typeInfo.Icon;

  let fillColor = PRIMARY_MARKER_COLOR;
  if (selected) {
    fillColor = SELECTED_MARKER_COLOR;
  } else if (isSelected) {
    fillColor = SECONDARY_MARKER_COLOR;
  }
  const pinH = Math.round(pinSize * (51 / 36));
  const innerPx = Math.round(pinSize * (22 / 36));
  const innerTop = Math.round(pinSize * (7 / 36));
  const innerLeft = Math.round((pinSize - innerPx) / 2);

  return (
    <div
      style={{
        position: "relative",
        width: pinSize,
        height: pinH,
        filter: isSelected
          ? "drop-shadow(0 0 6px rgba(76,176,228,0.9))"
          : "drop-shadow(0 2px 3px rgba(0,0,0,0.35))",
        flexShrink: 0,
      }}
    >
      <svg
        width={pinSize}
        height={pinH}
        viewBox="0 0 36 51"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        style={{ display: "block" }}
      >
        <path
          d="M18 0C8.06 0 0 8.06 0 18C0 31 18 51 18 51C18 51 36 31 36 18C36 8.06 27.94 0 18 0Z"
          fill={fillColor}
        />
        <circle cx="18" cy="18" r="11" fill="white" />
      </svg>

      <div
        style={{
          position: "absolute",
          top: innerTop,
          left: innerLeft,
          width: innerPx,
          height: innerPx,
          borderRadius: "50%",
          overflow: "hidden",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: fillColor,
          backgroundColor: "white",
        }}
      >
        {allFailed ? (
          <TypeIcon
            style={{
              width: Math.round(innerPx * 0.7),
              height: Math.round(innerPx * 0.7),
              strokeWidth: 2.2,
            }}
          />
        ) : (
          <img
            key={candidates[attempt]}
            src={candidates[attempt]}
            alt={alt}
            width={innerPx}
            height={innerPx}
            decoding="async"
            onError={() => setAttempt((i) => i + 1)}
            style={{
              width: innerPx,
              height: innerPx,
              objectFit: "cover",
              display: "block",
            }}
          />
        )}
      </div>
    </div>
  );
};

interface GooglePlace {
  types?: string[];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any;
}

// Map styles with icons (ids for translation keys)
const mapStyles = [
  {
    id: "streets",
    nameKey: "map.styles.streets",
    style: "mapbox://styles/mapbox/streets-v12",
    icon: MapIcon,
  },
  {
    id: "satellite",
    nameKey: "map.styles.satellite",
    style: "mapbox://styles/mapbox/satellite-streets-v12",
    icon: Satellite,
  },
  {
    id: "outdoors",
    nameKey: "map.styles.outdoors",
    style: "mapbox://styles/mapbox/outdoors-v12",
    icon: Trees,
  },
  {
    id: "light",
    nameKey: "map.styles.light",
    style: "mapbox://styles/mapbox/light-v11",
    icon: Sun,
  },
  {
    id: "dark",
    nameKey: "map.styles.dark",
    style: "mapbox://styles/mapbox/dark-v11",
    icon: Moon,
  },
];

export type SliceType = "mapMarkerLocations" | "mapInventoryList";

// Type alias for item union used throughout the component
export type ItemType = MapInventoryItem | InventoryItem | CombinedItem;

// Helper type for normalized item data
export interface NormalizedItem {
  id: string;
  lat?: number;
  lng?: number;
  name: string;
  address?: string;
  included: boolean;
  radius?: number;
  poi?: Array<string>;
  metadata?: Record<string, string>;
  itemType?: string;
  type?: string;
  coordinates?: number[][];
}

// Helper function to normalize item data from different sources
export const normalizeItem = (item: ItemType): NormalizedItem => {
  // Check if it's an item with detail property (MapInventoryItem or InventoryItem)
  if ("detail" in item && item.detail) {
    // Check if it's InventoryItem (has location.location) or MapInventoryItem (has detail.location).
    // Some data shapes carry no nested location at all — guard instead of crashing the page.
    const loc =
      (item as { location?: { location?: unknown } }).location?.location ??
      (item.detail as { location?: unknown }).location ??
      null;

    return {
      id: item.detail.id,
      lat: loc ? getLatitude(loc as Parameters<typeof getLatitude>[0]) : undefined,
      lng: loc ? getLongitude(loc as Parameters<typeof getLongitude>[0]) : undefined,
      name:
        ("name" in item.detail && item.detail.name) ||
        item.detail.mediaOwnerName ||
        item.detail.id,
      address: (loc as { address?: string } | null)?.address,
      included: item.detail.isSelected,
    };
  }

  // MapMarkerLocation
  const markerItem = item as CombinedItem;
  return {
    id: markerItem.id,
    ...(markerItem.itemType === "location" && {
      lat: markerItem.lat,
      lng: markerItem.lng,
      address: markerItem.address,
    }),
    ...(markerItem.itemType === "geometry" && {
      type: markerItem.type,
      coordinates: markerItem.coordinates,
    }),
    name: markerItem.name,
    included: markerItem.included,
    radius: markerItem.radius,
    poi: markerItem.poi,
    metadata: markerItem.metadata,
    itemType: markerItem.itemType,
  };
};

export interface SearchConfig {
  enabled?: boolean;
  placeholder?: string;
  showResults?: boolean;
  searchTypes?: Array<
    | "place"
    | "poi"
    | "address"
    | "postcode"
    | "locality"
    | "neighborhood"
    | "location"
  >;
  limit?: number; // Number of search results to show (1-10)

  // POI Filter configuration
  showPOIFilter?: boolean;
  poiPlaceholder?: string;
}

export interface MapControlsConfig {
  // Drawing tools
  showDrawingTools?: boolean;
  enableSelect?: boolean;
  enablePolygon?: boolean;
  enableCircle?: boolean;
  enableLine?: boolean;
  enableDelete?: boolean;

  // View tools
  showViewTools?: boolean;
  enableMountainView?: boolean;
  enable3D?: boolean;

  // Map styles
  showMapStyles?: boolean;
  enabledStyles?: Array<
    "streets" | "satellite" | "outdoors" | "light" | "dark"
  >;

  // Search configuration
  search?: SearchConfig;
}

interface MapBoxWrapperProps {
  InfoComponent?: React.FC<{ item: MapMarkerLocation | MapInventoryItem }>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  PopupComponent?: React.ComponentType<any>;
  // Popup component for availablePOIs (Google-Places POI markers, resolved
  // to category icons via `getPOIIconUrl`). Receives { poi, photoUrl } plus
  // any popupExtraProps. Keeping this as a prop avoids coupling the generic
  // Mapbox primitive to geofencing page code.
  POIPopupComponent?: React.ComponentType<{
    poi: POIPlaceData;
    photoUrl?: string | null;
  }>;
  // Extra props spread into PopupComponent on every render (e.g. campaign-level metrics).
  popupExtraProps?: Record<string, unknown>;
  mapboxAccessToken?: string;
  defaultCenter?: [number, number];
  defaultZoom?: number;
  /** Initial base-map style URL. Defaults to streets-v12 when omitted. Set
   * once at map creation (no runtime restyle), so callers that want a
   * different basemap avoid the "style is not done loading" reload race. */
  defaultMapStyleUrl?: string;
  controlsConfig?: MapControlsConfig;
  highlightInListOnClick?: boolean; // Whether clicking marker should update Redux selection (highlight in list)
  // Direct inventory list (alternative to Redux state)
  locationsList?: Array<MapInventoryItem | InventoryItem | CombinedItem>;
  selectedItemId?: string; // For prop-based inventory, specify which item is selected
  addPOI?: boolean; // For enabling
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  availablePOIs?: any[];
  onShapeDrawn?: (shape: MapGeometry) => void;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onLocationSelected?: (location: CombinedItem | any) => void; // Callback when a search result is selected (for prop-based usage)
  onCircleRadiusUpdate?: (id: string, radius: number) => void;
  updateLocationPOIMetadata?: (
    id: string,
    metadata: Record<string, string>,
    poi?: Array<string>,
  ) => void;
  deleteAllShapes?: () => void;
  checkForDuplicate?: boolean;
  onMapReady?: (map: mapboxgl.Map) => void; // Callback when map is ready
  selectedCountry?: string; // For restricting search results by country (ISO 3166-1 alpha-2 code, e.g. "US", "GB")
  onMarkerClick?: (id: string) => void; // Callback when an inventory pin is clicked (id matches selectedItemId)
}

const MapBoxWrapper: React.FC<MapBoxWrapperProps> = ({
  PopupComponent,
  POIPopupComponent,
  popupExtraProps,
  defaultCenter = [-73.9857, 40.7484],
  defaultZoom = 11,
  defaultMapStyleUrl,
  controlsConfig,
  locationsList,
  selectedItemId,
  onLocationSelected,
  onCircleRadiusUpdate,
  checkForDuplicate = false,
  onShapeDrawn,
  addPOI = false,
  availablePOIs,
  updateLocationPOIMetadata,
  deleteAllShapes,
  onMapReady,
  selectedCountry = "",
  onMarkerClick,
}) => {
  // Initialize translation with common namespace
  const { t } = useTranslate("common");
  const tolgee = useTolgee(["language"]);
  const currentLanguage = tolgee.getLanguage() || "en";
  const mapboxAccessToken = CONFIG.MAPBOX_ACCESS_TOKEN;
  const { showError, showWarning } = useAnnounce();
  // Default controls configuration - all features enabled by default
  const defaultSearchConfig: SearchConfig = {
    enabled: true,
    placeholder: t("map.searchPlaceholder"),
    showResults: true,
    searchTypes: ["place", "poi", "address"],
    limit: 5,
    showPOIFilter: false,
    poiPlaceholder: t("map.poiPlaceholder"),
  };

  const config: MapControlsConfig = {
    showDrawingTools: true,
    enableSelect: true,
    enablePolygon: true,
    enableCircle: true,
    enableLine: true,
    enableDelete: true,
    showViewTools: true,
    enableMountainView: true,
    enable3D: true,
    showMapStyles: true,
    enabledStyles: ["streets", "satellite", "outdoors", "light", "dark"],
    search: {
      ...defaultSearchConfig,
      ...controlsConfig?.search, // Override with user-provided search config
    },
    ...controlsConfig, // Override with user-provided config
  };
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);
  const popupRef = useRef<mapboxgl.Popup | null>(null);
  // Latest onMarkerClick held in a ref so the marker-render effect stays stable.
  const onMarkerClickRef = useRef(onMarkerClick);
  onMarkerClickRef.current = onMarkerClick;
  const drawRef = useRef<MapboxDraw | null>(null);

  // Control states
  const [drawMode, setDrawMode] = useState("simple_select");
  const [currentMapStyle, setCurrentMapStyle] = useState(
    defaultMapStyleUrl ?? "mapbox://styles/mapbox/streets-v12",
  );
  const [is3D, setIs3D] = useState(false);
  const [mountainView, setMountainView] = useState(false);
  const [isStyleLoaded, setIsStyleLoaded] = useState(false);
  // Set when the map cannot be created at all (e.g. WebGL unavailable). Instead of
  // throwing and tripping the global error boundary, we render a graceful fallback.
  const [mapInitError, setMapInitError] = useState(false);

  // Circle drawing states
  const [isDrawingCircle, setIsDrawingCircle] = useState(false);
  const [currentCircleId, setCurrentCircleId] = useState<string | null>(null);
  const [circleRadius, setCircleRadius] = useState(500); // Default 500m
  // Ref mirrors circleRadius state — avoids stale closure in sliderMouseUp / debouncedRadiusUpdate
  const circleRadiusRef = useRef<number>(500);
  const circleLat = useRef<number>(1);
  const circleLng = useRef<number>(1);

  // Search location states
  const [searchTerm, setSearchTerm] = useState("");
  const [searchResults, setSearchResults] = useState<GooglePlaceSearchResult[]>(
    [],
  );
  /** Index of the keyboard-focused result in the dropdown (-1 = none) */
  const [focusedSearchIndex, setFocusedSearchIndex] = useState(-1);
  const [selectedPOIs, setSelectedPOIs] = useState<string[]>([]);
  const [categories, setCategories] = useState<TreeNode[]>([]);
  const [addPOIs, setAddPOIs] = useState<boolean>(addPOI);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const searchLocationDebounceRef = useRef<ReturnType<
    typeof setTimeout
  > | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // Counters for incremental IDs (not saved to payload)
  const lineCounterRef = useRef(0);
  const polygonCounterRef = useRef(0);
  const circleCounterRef = useRef(0);
  const locationCounterRef = useRef(0);

  // Track the last applied language to prevent unnecessary updates
  const lastAppliedLanguageRef = useRef<string>("");

  // Store the circle click handler reference for proper cleanup
  const circleClickHandlerRef = useRef<
    ((e: mapboxgl.MapMouseEvent) => void) | null
  >(null);

  // Drag-to-draw circle handler refs
  const circleMouseDownHandlerRef = useRef<
    ((e: mapboxgl.MapMouseEvent) => void) | null
  >(null);
  const circleMouseMoveHandlerRef = useRef<
    ((e: mapboxgl.MapMouseEvent) => void) | null
  >(null);
  const circleMouseUpHandlerRef = useRef<
    ((e: mapboxgl.MapMouseEvent) => void) | null
  >(null);
  const isCircleDraggingRef = useRef<boolean>(false);
  const circleDragCenterRef = useRef<{ lat: number; lng: number } | null>(null);

  // Preview circle layer IDs (transient, only during drag)
  const CIRCLE_PREVIEW_SOURCE = "circle-preview-source";
  const CIRCLE_PREVIEW_FILL = "circle-preview-fill";
  const CIRCLE_PREVIEW_OUTLINE = "circle-preview-outline";

  // Debounce timer for radius updates
  const radiusUpdateTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
    null,
  );

  // Track initial mount to prevent popup on initial load
  const isInitialMountRef = useRef<boolean>(true);
  const prevSelectedItemIdRef = useRef<string | undefined>(undefined);
  const currSelectedItemIdRef = useRef<string | undefined>(undefined);

  // Memoize items and selectedItem to prevent unnecessary recalculations
  const items = useMemo(() => {
    const items: Array<MapInventoryItem | CombinedItem> = locationsList || [];
    let lineCounter = 1;
    let polygonCounter = 1;
    let circleCounter = 1;
    let locationCounter = 1;
    items.forEach((item) => {
      if (!("detail" in item)) {
        if (item.itemType === "location" && item.metadata?.type === "circle") {
          circleCounter += 1;
          locationCounter += 1;
        }
        if (
          item.itemType === "location" &&
          item.metadata &&
          item.metadata.type !== "circle"
        ) {
          locationCounter += 1;
        }
        if (item.itemType === "geometry") {
          if (item.type === "Polygon") {
            polygonCounter += 1;
          } else if (item.type === "LineString") {
            lineCounter += 1;
          }
        }
      }
    });
    lineCounterRef.current = lineCounter;
    polygonCounterRef.current = polygonCounter;
    circleCounterRef.current = circleCounter;
    locationCounterRef.current = locationCounter;
    return items;
  }, [locationsList]);

  const selectedItem = useMemo(() => {
    let selectedItemAssign: MapInventoryItem | CombinedItem | null | undefined =
      null;
    // Update the previous value
    if (!prevSelectedItemIdRef.current) {
      prevSelectedItemIdRef.current = selectedItemId;
    }
    if (
      currSelectedItemIdRef.current &&
      currSelectedItemIdRef.current !== selectedItemId
    ) {
      prevSelectedItemIdRef.current = currSelectedItemIdRef.current;
    }
    currSelectedItemIdRef.current = selectedItemId;
    if (items?.length && selectedItemId) {
      if ("detail" in items[0]) {
        selectedItemAssign =
          items.find(
            (item) => "detail" in item && item.detail?.id === selectedItemId,
          ) || null;
      } else {
        selectedItemAssign =
          items.find((item) => item.id === selectedItemId) || null;
      }
    }
    return selectedItemAssign;
  }, [selectedItemId, items]);

  // Helper function to create popup HTML for a marker (default/non-hook case only)
  const createPopupHtml = useCallback(
    (item: ItemType): string => {
      {
        // Default popup implementation
        const normalizedItem = normalizeItem(item);
        const isIncluded = normalizedItem.included !== false;
        const badgeHtml = ReactDOMServer.renderToString(
          <Badge variant={isIncluded ? "default" : "destructive"} size="sm">
            {isIncluded ? t("map.included") : t("map.excluded")}
          </Badge>,
        );

        return `
          <div style="padding: 12px; min-width: 220px; max-width: 320px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
            <div style="display: flex; align-items: start; justify-content: space-between; gap: 8px; margin-bottom: 10px;">
              <h3 style="margin: 0; font-size: 16px; font-weight: 600; color: #1f2937; line-height: 1.3; flex: 1;">
                ${normalizedItem.name}
              </h3>
              ${badgeHtml}
            </div>
            <div style="display: flex; align-items: start; gap: 6px;">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#6b7280" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink: 0; margin-top: 2px;">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                <circle cx="12" cy="10" r="3"></circle>
              </svg>
              <p style="margin: 0; font-size: 13px; color: #6b7280; line-height: 1.6;">
                ${normalizedItem.address}
              </p>
            </div>
          </div>
        `;
      }
    },
    [t],
  );

  const calculateDistance = (
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
  ) => {
    const R = 6371; // Radius of the Earth in kilometers (use 3958.8 for miles)

    // Convert degrees to radians
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;

    // Convert latitudes to radians for the formula
    const lat1Rad = (lat1 * Math.PI) / 180;
    const lat2Rad = (lat2 * Math.PI) / 180;

    // Haversine formula
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1Rad) *
        Math.cos(lat2Rad) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    const distance = R * c * 1000; // Distance in kilometers

    return Number(distance.toFixed(2));
  };

  // Helper function to calculate centroid of a polygon
  const calculatePolygonCentroid = React.useCallback(
    (coordinates: number[][]): { lat: number; lng: number; radius: number } => {
      let latSum = 0;
      let lngSum = 0;
      const pointCount = coordinates.length;

      coordinates.forEach(([lng, lat]) => {
        lngSum += lng;
        latSum += lat;
      });
      const lat = latSum / pointCount;
      const lng = lngSum / pointCount;
      const radius = calculateDistance(
        coordinates[0][1],
        coordinates[0][0],
        lat,
        lng,
      );
      return {
        lat,
        lng,
        radius: radius,
      };
    },
    [],
  );

  // Helper function to calculate midpoint of a line
  const calculateLineMidpoint = React.useCallback(
    (coordinates: number[][]): { lat: number; lng: number } => {
      const midIndex = Math.floor(coordinates.length / 2);
      const [lng, lat] = coordinates[midIndex];
      return { lat, lng };
    },
    [],
  );

  // Cleanup debounce timers on unmount
  useEffect(() => {
    return () => {
      if (radiusUpdateTimerRef.current) {
        clearTimeout(radiusUpdateTimerRef.current);
      }
      if (searchLocationDebounceRef.current) {
        clearTimeout(searchLocationDebounceRef.current);
      }
    };
  }, []);

  useEffect(() => {
    import("mapbox-gl/dist/mapbox-gl.css");
    import("@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css");
  }, []);

  // Initialize map
  useEffect(() => {
    mapboxgl.accessToken = mapboxAccessToken;

    if (!mapRef.current && mapContainerRef.current) {
      try {
        mapRef.current = new mapboxgl.Map({
          container: mapContainerRef.current,
          style: currentMapStyle,
          center: defaultCenter,
          zoom: defaultZoom,
          pitch: 0,
          bearing: 0,
          antialias: true,
          preserveDrawingBuffer: true,
        });
      } catch (e) {
        // WebGL unavailable or map bootstrap failure: degrade to a fallback panel
        // instead of crashing the whole page through the global error boundary.
        console.error("Mapbox failed to initialize; rendering fallback.", e);
        mapRef.current = null;
        setMapInitError(true);
        return;
      }

      // Initialize drawing controls
      drawRef.current = new MapboxDraw({
        displayControlsDefault: false,
        controls: {},
        defaultMode: "simple_select",
      });

      mapRef.current.addControl(drawRef.current);
      mapRef.current.addControl(
        new mapboxgl.NavigationControl({ showCompass: true, showZoom: true }),
        "bottom-right",
      );
      mapRef.current.addControl(
        new mapboxgl.FullscreenControl(),
        "bottom-right",
      );

      // Ensure draw layers are ordered below symbol layers (after style loads)
      mapRef.current.once("style.load", () => {
        if (!mapRef.current) return;

        // Mark style as loaded to trigger re-rendering of circles
        setIsStyleLoaded(true);

        const firstSymbolId = getFirstSymbolLayerId();
        if (firstSymbolId) {
          // Move all draw layers before the first symbol layer
          const drawLayerIds = [
            "gl-draw-polygon-fill-inactive",
            "gl-draw-polygon-stroke-inactive",
            "gl-draw-polygon-fill-active",
            "gl-draw-polygon-stroke-active",
            "gl-draw-line",
            "gl-draw-point",
          ];

          drawLayerIds.forEach((layerId) => {
            if (mapRef.current?.getLayer(layerId)) {
              try {
                mapRef.current.moveLayer(layerId, firstSymbolId);
              } catch (e) {
                // Layer might not exist yet, ignore
                console.debug(`Could not move layer ${layerId}:`, e);
              }
            }
          });
        }
        // Initial setup (do once)
        mapRef.current.addSource("custom-polygons", {
          type: "geojson",
          data: {
            type: "FeatureCollection",
            features: [],
          },
        });
        type LayerType =
          | "symbol"
          | "fill"
          | "line"
          | "circle"
          | "slot"
          | "heatmap"
          | "fill-extrusion"
          | "building"
          | "raster"
          | "raster-particle"
          | "hillshade"
          | "model"
          | "background"
          | "sky"
          | "clip";
        const styles: Array<
          Omit<mapboxgl.Layer, "source"> & { source: string }
        > = [
          // Polygon fill
          {
            id: "gl-draw-polygon-fill-inactive",
            type: "fill" as LayerType,
            filter: ["==", ["geometry-type"], "Polygon"],
            source: "custom-polygons",
            paint: {
              "fill-color": [
                "case",
                ["==", ["get", "included"], true],
                "#1D65AF",
                ["==", ["get", "included"], false],
                "#C52828", // included = false (red)
                "#1D65AF", // default to blue if no match
              ],
              "fill-outline-color": "#1D65AF",
              "fill-opacity": 0.25,
            },
          },
          // Polygon outline
          {
            id: "gl-draw-polygon-stroke-inactive",
            type: "line" as LayerType,
            filter: ["==", ["geometry-type"], "Polygon"],
            source: "custom-polygons",
            layout: {
              "line-cap": "round",
              "line-join": "round",
            },
            paint: {
              "line-color": [
                "case",
                ["==", ["get", "included"], true],
                "#1D65AF",
                ["==", ["get", "included"], false],
                "#C52828", // included = false (red)
                "#1D65AF", // default to blue if no match
              ],
              "line-dasharray": [0.2, 3],
              "line-width": 2,
            },
          },
          // Active polygon fill
          {
            id: "gl-draw-polygon-fill-active",
            type: "fill" as LayerType,
            filter: ["==", ["geometry-type"], "Polygon"],
            source: "custom-polygons",
            paint: {
              "fill-color": [
                "case",
                ["==", ["get", "included"], true],
                "#1D65AF",
                ["==", ["get", "included"], false],
                "#C52828", // included = false (red)
                "#1D65AF", // default to blue if no match
              ],
              "fill-outline-color": "#1D65AF",
              "fill-opacity": 0.25,
            },
          },
          // Active polygon outline
          {
            id: "gl-draw-polygon-stroke-active",
            type: "line" as LayerType,
            source: "custom-polygons",
            filter: ["==", ["geometry-type"], "Polygon"],
            layout: {
              "line-cap": "round",
              "line-join": "round",
            },
            paint: {
              "line-color": [
                "case",
                ["==", ["get", "included"], true],
                "#1D65AF",
                ["==", ["get", "included"], false],
                "#C52828", // included = false (red)
                "#1D65AF", // default to blue if no match
              ],
              "line-dasharray": [0.2, 3],
              "line-width": 2,
            },
          },
          // Line
          {
            id: "gl-draw-line",
            type: "line" as LayerType,
            filter: ["==", ["geometry-type"], "LineString"],
            source: "custom-polygons",
            layout: {
              "line-cap": "round",
              "line-join": "round",
            },
            paint: {
              "line-color": [
                "case",
                ["==", ["get", "included"], true],
                "#1D65AF",
                ["==", ["get", "included"], false],
                "#C52828", // included = false (red)
                "#1D65AF", // default to blue if no match
              ],
              "line-dasharray": [0.2, 3],
              "line-width": 2,
            },
          },
          // Points
          {
            id: "gl-draw-point",
            type: "circle" as LayerType,
            filter: ["==", ["geometry-type"], "Point"],
            source: "custom-polygons",
            paint: {
              "circle-radius": 5,
              "circle-color": "#1D65AF",
            },
          },
        ];
        styles.forEach((style) => mapRef.current?.addLayer(style));

        // Call onMapReady callback when map is fully loaded
        if (onMapReady && mapRef.current) {
          onMapReady(mapRef.current);
        }
      });
    }

    return () => {
      // Clean up all circle drag-to-draw handlers
      if (circleClickHandlerRef.current && mapRef.current) {
        mapRef.current.off("click", circleClickHandlerRef.current);
        circleClickHandlerRef.current = null;
      }
      if (circleMouseDownHandlerRef.current && mapRef.current) {
        mapRef.current.off("mousedown", circleMouseDownHandlerRef.current);
        circleMouseDownHandlerRef.current = null;
      }
      if (circleMouseMoveHandlerRef.current && mapRef.current) {
        mapRef.current.off("mousemove", circleMouseMoveHandlerRef.current);
        circleMouseMoveHandlerRef.current = null;
      }
      if (circleMouseUpHandlerRef.current && mapRef.current) {
        mapRef.current.off("mouseup", circleMouseUpHandlerRef.current);
        circleMouseUpHandlerRef.current = null;
      }
      if (isCircleDraggingRef.current && mapRef.current) {
        mapRef.current.dragPan.enable();
        isCircleDraggingRef.current = false;
      }
      removeCirclePreview();

      // Clean up markers and popups
      markersRef.current.forEach((marker) => marker.remove());
      popupRef.current?.remove();
    };
  }, [mapboxAccessToken, defaultCenter, defaultZoom, currentMapStyle]);

  // Handle MapboxDraw events (polygon, line creation)
  useEffect(() => {
    if (!mapRef.current || !drawRef.current) return;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handleDrawCreate = (e: any) => {
      // Don't process draw events if we're in circle drawing mode
      if (drawMode === "draw_circle") {
        return;
      }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      e.features.forEach((feature: any) => {
        const geometry = feature.geometry;

        // Flatten coordinates based on geometry type for comparison
        let coordinates = geometry.coordinates;
        let center;
        let shapeNumber = lineCounterRef.current;
        let id = "line-" + shapeNumber;
        if (geometry.type === "Polygon") {
          // Polygon: take only the outer ring (first array)
          coordinates = geometry.coordinates[0];
          center = calculatePolygonCentroid(coordinates);
          shapeNumber = polygonCounterRef.current;
          id = "polygon-" + shapeNumber;
        }

        // Generate incremental ID (not saved to payload, used for MapboxDraw sync)
        const geometryId = id;

        // Set the feature ID in MapboxDraw so we can reference it later
        feature.id = geometryId;
        feature.properties = { included: true };
        if (mapRef.current) {
          const customSource = mapRef.current.getSource(
            "custom-polygons",
          ) as mapboxgl.GeoJSONSource;
          const data = customSource._data;
          if (data && typeof data === "object" && "features" in data) {
            data.features.push(feature);
            customSource.setData(data);
          }
        }
        let shapeName = geometry.type + " " + shapeNumber;
        if (geometry.type === "LineString") {
          shapeName = "Path " + shapeNumber;
        }
        onShapeDrawn?.({
          id: geometryId,
          type: geometry.type as "Polygon" | "LineString" | "Point",
          name: shapeName,
          coordinates: coordinates,
          included: true,
          isShape: true, // Always true for geometries
          poi: [],
          ...(center && { metadata: { center: JSON.stringify(center) } }),
        });
        const features = drawRef.current?.getAll().features;
        features?.forEach((feature) => {
          if (feature.id) {
            drawRef.current?.delete(feature.id.toString());
          }
        });
        if (geometry.type === "Polygon") {
          polygonCounterRef.current += 1;
        } else {
          lineCounterRef.current += 1;
        }
      });

      // Reset drawing mode back to simple_select so the toolbar button deactivates
      // and MapboxDraw itself is cleanly back in selection mode.
      setDrawMode("simple_select");
      if (drawRef.current) {
        try {
          drawRef.current.changeMode("simple_select");
        } catch {
          // ignore if already in simple_select
        }
      }
    };

    mapRef.current.on("draw.create", handleDrawCreate);

    return () => {
      mapRef.current?.off("draw.create", handleDrawCreate);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapRef.current, drawRef.current, drawMode]);

  // Helper function to create a circle polygon from center and radius
  const createCirclePolygon = useCallback(
    (lng: number, lat: number, radiusMeters: number): number[][] => {
      const earthRadius = 6371000; // Earth's radius in meters
      const latRad = (lat * Math.PI) / 180;
      const lngRad = (lng * Math.PI) / 180;
      const angularDistance = radiusMeters / earthRadius;
      const points = 64; // Number of points to approximate the circle

      const coordinates: number[][] = [];
      for (let i = 0; i <= points; i++) {
        const angle = (i * 2 * Math.PI) / points;
        const newLatRad = Math.asin(
          Math.sin(latRad) * Math.cos(angularDistance) +
            Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(angle),
        );
        const newLngRad =
          lngRad +
          Math.atan2(
            Math.sin(angle) * Math.sin(angularDistance) * Math.cos(latRad),
            Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(newLatRad),
          );

        coordinates.push([
          (newLngRad * 180) / Math.PI,
          (newLatRad * 180) / Math.PI,
        ]);
      }

      return coordinates;
    },
    [],
  );

  // Helper function to find the first symbol layer (for proper layer ordering)
  const getFirstSymbolLayerId = useCallback((): string | undefined => {
    if (!mapRef.current?.isStyleLoaded()) return undefined;
    const layers = mapRef.current.getStyle().layers;
    if (!layers) return undefined;

    // Find the first symbol layer (labels, icons)
    for (const layer of layers) {
      if (layer.type === "symbol") {
        return layer.id;
      }
    }
    return undefined;
  }, []);

  // --- Preview circle helpers (transient source/layers during drag-to-draw) ---
  const removeCirclePreview = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;
    if (map.getLayer(CIRCLE_PREVIEW_FILL)) map.removeLayer(CIRCLE_PREVIEW_FILL);
    if (map.getLayer(CIRCLE_PREVIEW_OUTLINE))
      map.removeLayer(CIRCLE_PREVIEW_OUTLINE);
    if (map.getSource(CIRCLE_PREVIEW_SOURCE))
      map.removeSource(CIRCLE_PREVIEW_SOURCE);
  }, [CIRCLE_PREVIEW_FILL, CIRCLE_PREVIEW_OUTLINE, CIRCLE_PREVIEW_SOURCE]);

  const addCirclePreview = useCallback(
    (lng: number, lat: number, radiusMeters: number) => {
      const map = mapRef.current;
      if (!map) return;
      // Safety — remove any stale preview first
      removeCirclePreview();
      const coords = createCirclePolygon(lng, lat, radiusMeters);
      map.addSource(CIRCLE_PREVIEW_SOURCE, {
        type: "geojson",
        data: {
          type: "Feature",
          geometry: { type: "Polygon", coordinates: [coords] },
          properties: {},
        },
      });
      const firstSymbolId = getFirstSymbolLayerId();
      map.addLayer(
        {
          id: CIRCLE_PREVIEW_FILL,
          type: "fill",
          source: CIRCLE_PREVIEW_SOURCE,
          paint: { "fill-color": "#1D65AF", "fill-opacity": 0.15 },
        },
        firstSymbolId,
      );
      map.addLayer(
        {
          id: CIRCLE_PREVIEW_OUTLINE,
          type: "line",
          source: CIRCLE_PREVIEW_SOURCE,
          paint: {
            "line-color": "#1D65AF",
            "line-width": 2,
            "line-dasharray": [3, 3],
          },
        },
        firstSymbolId,
      );
    },
    [
      createCirclePolygon,
      getFirstSymbolLayerId,
      removeCirclePreview,
      CIRCLE_PREVIEW_SOURCE,
      CIRCLE_PREVIEW_FILL,
      CIRCLE_PREVIEW_OUTLINE,
    ],
  );

  const updateCirclePreview = useCallback(
    (lng: number, lat: number, radiusMeters: number) => {
      const map = mapRef.current;
      if (!map) return;
      const src = map.getSource(CIRCLE_PREVIEW_SOURCE) as
        | mapboxgl.GeoJSONSource
        | undefined;
      if (!src) return;
      const coords = createCirclePolygon(lng, lat, radiusMeters);
      src.setData({
        type: "Feature",
        geometry: { type: "Polygon", coordinates: [coords] },
        properties: {},
      });
    },
    [createCirclePolygon, CIRCLE_PREVIEW_SOURCE],
  );

  // Unified marker rendering function
  const renderAllMarkers = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;

    // Clear existing markers. React roots inside marker elements are
    // left to be GC'd with the detached DOM — explicit unmount here
    // interferes with Mapbox's own click handling during re-renders.
    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = [];

    items.forEach((item) => {
      const normalized = normalizeItem(item);

      // Determine marker type and position
      let lat: number | undefined;
      let lng: number | undefined;
      let iconType: "location" | "geometry" | "circle" = "location";
      let iconSize = 24;

      if (normalized.itemType === "location") {
        // Regular location or circle center
        lat = normalized.lat;
        lng = normalized.lng;
        iconType =
          normalized.metadata?.type === "circle" ? "circle" : "location";
      } else if (normalized.itemType === "geometry") {
        // Geometry centroid/midpoint
        if ("coordinates" in item) {
          if (normalized.type === "Polygon") {
            const center = calculatePolygonCentroid(item.coordinates);
            lat = center.lat;
            lng = center.lng;
            iconType = "geometry";
            iconSize = 10; // Smaller for geometry centers
          } else if (normalized.type === "LineString") {
            const center = calculateLineMidpoint(item.coordinates);
            lat = center.lat;
            lng = center.lng;
            iconType = "geometry";
            iconSize = 10;
          }
        }
      } else {
        lat = normalized.lat;
        lng = normalized.lng;
      }

      if (!lat || !lng) return;

      // Check if highlighted (clicked/fly-to) vs selected for the campaign.
      const isSelected = selectedItemId && selectedItemId === normalized.id;
      const isInventorySelected =
        "detail" in item &&
        !!(item as { detail?: { isSelected?: boolean } }).detail?.isSelected;

      // Create marker element based on type
      const el = document.createElement("div");
      el.className = "marker-wrapper";
      el.style.cursor = iconType === "geometry" ? "default" : "pointer";
      el.title = normalized.name;

      // Choose icon based on type
      if (iconType === "circle") {
        // No icon for circle centers (shape is visible)
        el.innerHTML = "";
      } else if (iconType === "geometry") {
        el.innerHTML = ReactDOMServer.renderToString(
          normalized.type === "Polygon" ? (
            <Square
              size={iconSize}
              color={PRIMARY_MARKER_COLOR}
              fill={PRIMARY_MARKER_COLOR}
              strokeWidth={2}
            />
          ) : (
            <Route
              size={iconSize}
              color={PRIMARY_MARKER_COLOR}
              strokeWidth={2}
            />
          ),
        );
      } else {
        // Regular location marker — teardrop pin. Try the CloudFront POI
        // image for the location's place type (e.g. `administrative_area_level_1`
        // for Tokyo) with related-category fallbacks, then finally a Lucide
        // icon on total miss. Mount with `createRoot` so the onError fallback
        // chain actually runs (renderToString can't handle it).
        const locationRoot = createRoot(el);
        const inventoryDetail =
          "detail" in item
            ? (
                item as {
                  detail?: { inventoryType?: string; venueType?: unknown };
                }
              ).detail
            : undefined;
        if (inventoryDetail?.inventoryType) {
          // Inventory marker — category pin (classic/digital × plain/transit/retail).
          locationRoot.render(
            <InventoryCategoryPin
              inventoryType={inventoryDetail.inventoryType}
              venueType={inventoryDetail.venueType}
              selected={isInventorySelected}
              pinSize={40}
            />,
          );
        } else {
          // Geofencing/POI location — teardrop pin with the CloudFront POI image
          // for the place type, falling back to related categories then a Lucide
          // icon. Mounted via `createRoot` so the onError fallback chain runs.
          const placeType = normalized.metadata?.type ?? null;
          const typeLabel = normalized.metadata?.typeLabel ?? null;
          const pinCategory = resolvePinCategory(placeType, typeLabel);
          const locationIcon = getPOIIconUrl(placeType, typeLabel);
          locationRoot.render(
            hasPOIPin(pinCategory) ? (
              <POICategoryPin category={pinCategory} pinSize={40} />
            ) : (
              <POIIconMarker
                iconUrl={locationIcon.src}
                fallbackUrls={locationIcon.fallbackSrcs}
                placeType={placeType}
                primaryTypeDisplayName={typeLabel}
                alt={normalized.name}
                isSelected={!!isSelected}
                selected={isInventorySelected}
                pinSize={isSelected ? 34 : 28}
              />
            ),
          );
        }
      }

      // Create popup. When a `PopupComponent` is supplied we mount it via
      // `createRoot` + `setDOMContent` so React lifecycle (useState, useEffect,
      // onError) actually runs — critical for the image-source fallback chain
      // in `GeoFencingLocationPopup`. Falls back to static HTML otherwise.
      const popup = new mapboxgl.Popup({
        offset: 25,
        maxWidth: "300px",
      });
      if (PopupComponent) {
        const popupEl = document.createElement("div");
        createRoot(popupEl).render(
          <TolgeeProvider tolgee={TolgeeConfig}>
            <PopupComponent item={item} {...(popupExtraProps ?? {})} />
          </TolgeeProvider>,
        );
        popup.setDOMContent(popupEl);
      } else {
        popup.setHTML(createPopupHtml(item));
      }

      const marker = new mapboxgl.Marker(el)
        .setLngLat([lng, lat])
        .setPopup(popup)
        .addTo(map);

      // Mapbox's internal click listener on custom marker elements is unreliable
      // when the element is filled with an SVG pin (the drop-shadow filter +
      // inner paths can swallow the event). Bind the toggle explicitly so
      // clicking a location pin always opens its popup.
      if (iconType !== "geometry") {
        el.addEventListener("click", (ev) => {
          ev.stopPropagation();
          const wasOpen = marker.getPopup()?.isOpen();
          // Single popup at a time: close any other open popups first.
          markersRef.current.forEach((m) => {
            const p = m.getPopup();
            if (p?.isOpen()) p.remove();
          });
          if (!wasOpen) marker.togglePopup();
          onMarkerClickRef.current?.(normalized.id);
        });
      }

      markersRef.current.push(marker);
    });

    // Render POI markers if available
    if (availablePOIs?.length) {
      availablePOIs.forEach((place: POIPlaceData) => {
        const el = document.createElement("div");
        el.className = "marker-wrapper";
        el.style.cursor = "pointer";
        el.title = place.displayName;
        const pinCategory = resolvePinCategory(
          place.primaryType,
          place.primaryTypeDisplayName,
        );
        const icon = getPOIIconUrl(
          place.primaryType,
          place.primaryTypeDisplayName,
        );
        const root = createRoot(el);
        root.render(
          hasPOIPin(pinCategory) ? (
            <POICategoryPin category={pinCategory} />
          ) : (
            <POIIconMarker
              iconUrl={icon.src}
              fallbackUrls={icon.fallbackSrcs}
              placeType={place.primaryType}
              primaryTypeDisplayName={place.primaryTypeDisplayName}
              alt={place.displayName}
            />
          ),
        );

        // If a POI popup component was provided, bind a clickable popup and
        // lazily fetch a photo on first hover. The photo promise is cached at
        // the utility level, so hover + click share a single API call per pin.
        if (POIPopupComponent) {
          const Popup = POIPopupComponent;
          // Track the latest known photo per pin. Photo arrives asynchronously;
          // re-render the popup once it resolves so the user sees the image.
          let currentPhotoUrl: string | null = null;

          // Mount via `createRoot` + `TolgeeProvider` (not renderToString) so
          // the popup gets React context — GeoFencingPOIPopup uses useTranslate.
          const popupEl = document.createElement("div");
          const popupRoot = createRoot(popupEl);
          const renderPopup = () =>
            popupRoot.render(
              <TolgeeProvider tolgee={TolgeeConfig}>
                <Popup poi={place} photoUrl={currentPhotoUrl} />
              </TolgeeProvider>,
            );
          renderPopup();

          const popup = new mapboxgl.Popup({
            offset: 20,
            maxWidth: "320px",
          }).setDOMContent(popupEl);

          const marker = new mapboxgl.Marker(el)
            .setLngLat([place.locationLng, place.locationLat])
            .setPopup(popup)
            .addTo(map);

          let photoPrimed = false;
          const primePhoto = () => {
            photoPrimed = true;
            getPOIPhotoUrl(place).then((url) => {
              if (url) {
                currentPhotoUrl = url;
                renderPopup();
              }
            });
          };
          el.addEventListener("mouseenter", primePhoto, { once: true });

          el.addEventListener("click", (ev) => {
            ev.stopPropagation();
            if (!photoPrimed) primePhoto();
            const wasOpen = marker.getPopup()?.isOpen();
            // Single popup at a time: close any other open popups first.
            markersRef.current.forEach((m) => {
              const p = m.getPopup();
              if (p?.isOpen()) p.remove();
            });
            if (!wasOpen) marker.togglePopup();
          });

          markersRef.current.push(marker);
        } else {
          // Legacy fallback: no popup, just render the icon marker.
          const marker = new mapboxgl.Marker(el)
            .setLngLat([place.locationLng, place.locationLat])
            .addTo(map);
          markersRef.current.push(marker);
        }
      });
    }
  }, [
    items,
    selectedItemId,
    availablePOIs,
    calculatePolygonCentroid,
    calculateLineMidpoint,
    createPopupHtml,
    POIPopupComponent,
    PopupComponent,
    popupExtraProps,
  ]);

  const deleteAllCircleLayers = useCallback(() => {
    if (!mapRef.current) return;
    const map = mapRef.current;
    const style = map.getStyle();
    style.layers?.forEach((layer) => {
      if (
        layer.id.startsWith("circle-fill-") ||
        layer.id.startsWith("circle-outline-")
      ) {
        const locationId = layer.id
          .replace("circle-fill-", "")
          .replace("circle-outline-", "");
        // Remove layer and source
        const sourceId = `circle-${locationId}`;
        const fillLayerId = `circle-fill-${locationId}`;
        const outlineLayerId = `circle-outline-${locationId}`;

        if (map.getLayer(fillLayerId)) {
          map.removeLayer(fillLayerId);
        }
        if (map.getLayer(outlineLayerId)) {
          map.removeLayer(outlineLayerId);
        }
        if (map.getSource(sourceId)) {
          map.removeSource(sourceId);
        }
      }
    });
  }, []);

  // Unified rendering: Use MapboxDraw for all shapes (circles, polygons, lines)
  useEffect(() => {
    if (!drawRef.current || !mapRef.current) return;

    // Wait for style to be loaded
    if (!isStyleLoaded) {
      console.debug("Waiting for style to load before syncing shapes");
      return;
    }

    const syncAndRender = () => {
      if (!drawRef.current || !mapRef.current) return;
      const map = mapRef.current;
      // Get all shape items (geometries AND circles)
      const shapesOtherThanCircles = items.filter((item) => {
        const normalized = normalizeItem(item);
        return normalized.itemType === "geometry";
      });
      const circles = items.filter((item) => {
        const normalized = normalizeItem(item);
        return normalized.metadata?.type === "circle" && normalized.radius;
      });
      deleteAllCircleLayers();
      circles.forEach((circle) => {
        const sourceId = `circle-${circle.id}`;
        const fillLayerId = `circle-fill-${circle.id}`;
        const outlineLayerId = `circle-outline-${circle.id}`;

        try {
          if (map.getLayer(fillLayerId)) {
            map.removeLayer(fillLayerId);
          }
          if (map.getLayer(outlineLayerId)) {
            map.removeLayer(outlineLayerId);
          }
          if (map.getSource(sourceId)) {
            map.removeSource(sourceId);
          }
        } catch (e) {
          console.debug("Error removing circle layers:", e);
        }
        if ("itemType" in circle && circle.itemType === "location") {
          const circleCoords = createCirclePolygon(
            circle.lng,
            circle.lat,
            circle.radius || 500,
          );
          const sourceId = `circle-${circle.id}`;
          const fillLayerId = `circle-fill-${circle.id}`;
          const outlineLayerId = `circle-outline-${circle.id}`;
          const isIncluded = "included" in circle ? circle.included : false;
          // Function to safely add or update circle

          try {
            const source = map.getSource(sourceId) as mapboxgl.GeoJSONSource;

            if (source) {
              // Update existing circle
              source.setData({
                type: "Feature",
                geometry: {
                  type: "Polygon",
                  coordinates: [circleCoords],
                },
                properties: { included: circle.included },
              });
              console.debug(`Circle ${circle.id} updated`);
            } else {
              // Add new circle - check if source already exists to avoid duplicates
              if (map.getSource(sourceId)) {
                console.debug(`Source ${sourceId} already exists, skipping`);
                return;
              }

              // Add source
              map.addSource(sourceId, {
                type: "geojson",
                data: {
                  type: "Feature",
                  geometry: {
                    type: "Polygon",
                    coordinates: [circleCoords],
                  },
                  properties: { included: circle.included },
                },
              });

              // Get the first symbol layer to insert our layers before it
              const firstSymbolId = getFirstSymbolLayerId();

              // Add fill layer before symbol layers to keep markers on top
              if (!map.getLayer(fillLayerId)) {
                map.addLayer(
                  {
                    id: fillLayerId,
                    type: "fill",
                    source: sourceId,
                    paint: {
                      "fill-color": isIncluded ? "#1D65AF" : "#C52828",
                      "fill-opacity": 0.25,
                    },
                  },
                  firstSymbolId, // Insert before first symbol layer
                );
              }

              // Add outline layer before symbol layers
              if (!map.getLayer(outlineLayerId)) {
                map.addLayer(
                  {
                    id: outlineLayerId,
                    type: "line",
                    source: sourceId,
                    paint: {
                      "line-color": isIncluded ? "#1D65AF" : "#C52828",
                      "line-width": 2,
                      "line-dasharray": [2, 2],
                    },
                  },
                  firstSymbolId, // Insert before first symbol layer
                );
              }
            }
          } catch (error) {
            console.debug(`Error adding/updating circle ${circle.id}:`, error);
          }
        }
      });
      const updatedPolygons: GeoJSON.FeatureCollection = {
        type: "FeatureCollection",
        features: [] as Array<{
          id: string | undefined;
          geometry: GeoJSON.Geometry;
          properties: {
            included: boolean;
            name?: string;
          };
          type: "Feature";
        }>,
      };
      // Add or update shapes in draw
      shapesOtherThanCircles.forEach((item) => {
        const normalized = normalizeItem(item);
        let coordinates;
        let type = "Polygon";
        if ("coordinates" in item) {
          coordinates =
            normalized.type === "Polygon"
              ? [item.coordinates]
              : item.coordinates;
          type = normalized.type || "Polygon";
        }

        if (coordinates) {
          const feature = {
            id: item.id,
            type: "Feature" as const,
            properties: {
              included: normalized.included === true,
              name: (item as CombinedItem).name,
            },
            geometry: { type, coordinates },
          } as {
            id: string | undefined;
            geometry: GeoJSON.Geometry;
            properties: {
              included: boolean;
              name?: string;
            };
            type: "Feature";
          };
          updatedPolygons.features.push(feature);
        }
      });
      // Update source - color changes immediately!
      const source = map.getSource("custom-polygons") as mapboxgl.GeoJSONSource;
      if (source) {
        source.setData(updatedPolygons);
      }

      // Render all markers after shapes are synced
      renderAllMarkers();
    };

    syncAndRender();
  }, [
    items,
    selectedItemId,
    isStyleLoaded,
    createCirclePolygon,
    renderAllMarkers,
    getFirstSymbolLayerId,
  ]);

  // Handle selectedItemId changes for prop-based usage (only when user explicitly changes selection)
  useEffect(() => {
    // Skip on initial mount or when using Redux state
    if (isInitialMountRef.current || !items) {
      isInitialMountRef.current = false;
      prevSelectedItemIdRef.current = selectedItemId;
      return;
    }

    // Only proceed if selectedItemId actually changed
    // if (prevSelectedItemIdRef.current === selectedItemId) {
    //   return;
    // }

    const map = mapRef.current;
    if (!map || !selectedItemId) {
      // Close all popups when no item is selected
      markersRef.current.forEach((marker) => {
        const popup = marker.getPopup();
        if (popup?.isOpen()) {
          popup.remove();
        }
      });
      popupRef.current?.remove();
      return;
    }

    // Find the selected item from the inventory list
    const selectedInventoryItem = items.find(
      (item) => normalizeItem(item).id === selectedItemId,
    );

    if (!selectedInventoryItem) {
      return;
    }

    const normalizedSelectedItem = normalizeItem(selectedInventoryItem);
    // Check if selected item is a circle
    const isCircle =
      normalizedSelectedItem.radius &&
      normalizedSelectedItem.name?.startsWith("Circle ");

    // Set or clear currentCircleId based on whether it's a circle
    if (isCircle) {
      setCurrentCircleId(normalizedSelectedItem.id);
    } else {
      setCurrentCircleId(null);
    }

    // Calculate center point for geometries (polygons/lines) if needed
    let centerLat: number | undefined = normalizedSelectedItem.lat;
    let centerLng: number | undefined = normalizedSelectedItem.lng;

    if (!centerLat || !centerLng) {
      // This is a geometry (polygon or line), calculate center
      if (normalizedSelectedItem.coordinates && normalizedSelectedItem.type) {
        if (normalizedSelectedItem.type === "Polygon") {
          const center = calculatePolygonCentroid(
            normalizedSelectedItem.coordinates,
          );
          centerLat = center.lat;
          centerLng = center.lng;
        } else if (normalizedSelectedItem.type === "LineString") {
          const center = calculateLineMidpoint(
            normalizedSelectedItem.coordinates,
          );
          centerLat = center.lat;
          centerLng = center.lng;
        }
      }
    }

    // Find the marker for the selected item
    // For geometries, try to find by calculated center coordinates
    // For locations, use the lat/lng directly
    const markerRef = markersRef.current.find((marker) => {
      const markerLngLat = marker.getLngLat();
      if (centerLng && centerLat) {
        // Use small threshold for comparison (approximately 10 meters)
        const lngDiff = Math.abs(markerLngLat.lng - centerLng);
        const latDiff = Math.abs(markerLngLat.lat - centerLat);
        return lngDiff < 0.0001 && latDiff < 0.0001;
      }
      return false;
    });

    // Handle popup for marker if found
    if (markerRef) {
      // Close all other popups first
      markersRef.current.forEach((marker) => {
        if (marker !== markerRef) {
          const popup = marker.getPopup();
          if (popup?.isOpen()) {
            popup.remove();
          }
        }
      });

      // Remove old popup if exists
      const oldPopup = markerRef.getPopup();
      if (oldPopup) {
        oldPopup.remove();
      }

      // Create new popup with updated data
      const newPopup = new mapboxgl.Popup({
        offset: 25,
        maxWidth: "300px",
      });

      if (PopupComponent) {
        const popupEl = document.createElement("div");
        createRoot(popupEl).render(
          <TolgeeProvider tolgee={TolgeeConfig}>
            <PopupComponent
              item={selectedInventoryItem}
              {...(popupExtraProps ?? {})}
            />
          </TolgeeProvider>,
        );
        newPopup.setDOMContent(popupEl);
      } else {
        newPopup.setHTML(createPopupHtml(selectedInventoryItem));
      }

      // Set the new popup to the marker
      markerRef.setPopup(newPopup);

      // Open the popup
      newPopup.addTo(map);
      popupRef.current = newPopup;
    }

    // Fly to the selected location (works for both locations and geometries)
    if (isStyleLoaded && centerLng && centerLat) {
      const currentZoom = map.getZoom();
      map.flyTo({
        center: [centerLng, centerLat],
        zoom: is3D ? 17 : mountainView ? 22 : currentZoom || 10,
        duration: 1000,
      });
    }
  }, [
    selectedItemId,
    items,
    isStyleLoaded,
    is3D,
    mountainView,
    createPopupHtml,
    calculatePolygonCentroid,
    calculateLineMidpoint,
  ]);

  // Search locations using Google Places Text Search (alternative to Autocomplete – returns places with coordinates)
  const searchLocation = useCallback(
    async (query: string) => {
      if (!query.trim()) {
        setSearchResults([]);
        return;
      }

      try {
        const data: GooglePlaceSearchResult[] = await searchPlacesByText(
          query,
          selectedCountry,
          currentLanguage,
        );
        setSearchResults(data || []);
      } catch (error) {
        console.error("Search error:", error);
        setSearchResults([]);
      }
    },
    [config.search?.limit],
  );

  const SEARCH_DEBOUNCE_MS = 300;

  // Handle search input change (debounced to avoid excessive API calls)
  const handleSearchChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      setSearchTerm(value);
      setFocusedSearchIndex(-1);
      if (searchLocationDebounceRef.current) {
        clearTimeout(searchLocationDebounceRef.current);
        searchLocationDebounceRef.current = null;
      }
      if (value.trim()) {
        searchLocationDebounceRef.current = setTimeout(() => {
          searchLocation(value);
          searchLocationDebounceRef.current = null;
        }, SEARCH_DEBOUNCE_MS);
      } else {
        setSearchResults([]);
      }
    },
    [searchLocation],
  );

  /** Keyboard navigation inside the search dropdown */
  const handleSearchKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (!searchResults.length) return;

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setFocusedSearchIndex((prev) =>
          Math.min(prev + 1, searchResults.length - 1),
        );
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setFocusedSearchIndex((prev) => Math.max(prev - 1, 0));
      } else if (e.key === "Enter") {
        e.preventDefault();
        const idx = focusedSearchIndex >= 0 ? focusedSearchIndex : 0;
        if (searchResults[idx]) {
          selectSearchResult(searchResults[idx]);
          setFocusedSearchIndex(-1);
        }
      } else if (e.key === "Escape") {
        setSearchTerm("");
        setSearchResults([]);
        setFocusedSearchIndex(-1);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [searchResults, focusedSearchIndex],
  );

  // Select search result - add to Redux state (only if using Redux)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const selectSearchResult = (feature: any) => {
    const lng = feature.lng;
    const lat = feature.lat;
    // Check for duplicate location by coordinates (within small threshold)
    if (onLocationSelected) {
      if (items.length > 0 && checkForDuplicate) {
        const isDuplicate = items.some((loc) => {
          if ("lat" in loc && "lng" in loc) {
            const latDiff = Math.abs(loc.lat - lat);
            const lngDiff = Math.abs(loc.lng - lng);
            // Consider duplicate if within ~10 meters
            return latDiff < 0.0001 && lngDiff < 0.0001;
          }
          return false;
        });

        if (isDuplicate) {
          showWarning(t("map.duplicateLocation"));
          return;
        }
      }

      // Generate incremental ID (not saved to payload)
      const locationId = `location-${locationCounterRef.current}`;

      // Resolve the best type key (from enriched result or raw types array)
      const rawTypeKey =
        feature.primaryTypeKey ??
        pickPrimaryType(feature.types) ??
        feature.types?.[0] ??
        "";

      // Human-readable label
      const typeLabel =
        feature.primaryTypeLabel ?? getPlaceTypeLabel(rawTypeKey);

      // Local region name — falls back to formatted_address
      const localName = feature.localName ?? feature.formatted_address ?? "";

      // Lazy photo URL (only call getPhotoUrl when we actually need the URL)
      const photoUrl: string =
        typeof feature.getPhotoUrl === "function"
          ? (feature.getPhotoUrl() ?? "")
          : "";

      const newLocation = {
        id: locationId,
        lat: lat,
        lng: lng,
        name: feature.name,
        address: feature.formatted_address,
        radius: 500, // Default radius for search-based locations
        included: true,
        isShape: false, // Search results are not shapes
        poi: [],
        metadata: {
          type: rawTypeKey,
          typeLabel,
          localName,
          localNameType: feature.localNameType ?? "",
          countryCode: selectedCountry,
          photoUrl,
        },
      };
      onLocationSelected(newLocation);
      locationCounterRef.current += 1;
    }

    // Clear currentCircleId when adding a new search location
    setCurrentCircleId(null);

    // Fly to location
    if (mapRef.current) {
      mapRef.current.flyTo({
        center: [lng, lat],
        zoom: 14,
        duration: 1000,
      });
    }

    setSearchTerm("");
    setSearchResults([]);
  };

  // Handle POI selection change from MultiSelect
  const handlePOIChange = (selectedValues: string[]) => {
    const notInList = selectedValues.filter(
      (selectedValue) =>
        !categories.some((category) => category.value === selectedValue),
    );
    const newCategories = [...categories];
    notInList.forEach((valueNotInList) => {
      newCategories.push({
        label: valueNotInList,
        value: valueNotInList,
        id: valueNotInList,
        disabled: true,
      });
    });
    setCategories(newCategories);
    setSelectedPOIs(selectedValues);
  };

  const onPOISearchChange = useCallback(
    async (searchTerm?: string) => {
      // Handle POI search term change
      if (!selectedItem) return;
      try {
        const normalizedSelectedItem = normalizeItem(selectedItem);
        if (
          normalizedSelectedItem.itemType === "geometry" &&
          normalizedSelectedItem.type === "Polygon"
        ) {
          const center = selectedItem.metadata?.center
            ? JSON.parse(selectedItem.metadata.center)
            : null;
          if (center) {
            normalizedSelectedItem.lat = center.lat;
            normalizedSelectedItem.lng = center.lng;
            normalizedSelectedItem.radius = center.radius;
          }
        }
        const response = await fetchNearbyPlaces(
          {
            lat: normalizedSelectedItem?.lat || 0,
            lng: normalizedSelectedItem?.lng || 0,
            radius: normalizedSelectedItem?.radius || 1500,
          },
          searchTerm,
          currentLanguage,
        );

        if (response.error) {
          setAddPOIs(false);
          return;
        }

        const placesData = response;

        // Extract unique POI categories from the types array of each place
        const allCategories: TreeNode[] = [];

        placesData.forEach((place: GooglePlace) => {
          if (place.primaryType) {
            const categoryExsits =
              allCategories.findIndex(
                (category) => category.value === place.primaryType,
              ) === -1;
            if (categoryExsits) {
              allCategories.push({
                label: place.primaryTypeDisplayName,
                value: place.primaryType,
                id: place.primaryTypeDisplayName,
              });
            }
          }
        });
        const notInList = selectedPOIs.filter(
          (selectedValue) =>
            !allCategories.some((category) => category.value === selectedValue),
        );
        notInList.forEach((valueNotInList) => {
          allCategories.push({
            label: valueNotInList,
            value: valueNotInList,
            id: valueNotInList,
            disabled: true,
          });
        });

        setCategories([...allCategories]); // Set unique categories
      } catch (err) {
        showError(t("map.ExtractingCategoriesFailed"));
        console.log(err);
      }
    },
    [showError, t],
  );

  useEffect(() => {
    if (!addPOI || !selectedItem) {
      if (addPOIs) {
        setAddPOIs(false);
        setSelectedPOIs([]);
      }
      return;
    }

    // Load existing POIs for this item
    if (selectedItem.poi && selectedItem.poi.length > 0) {
      setSelectedPOIs(selectedItem.poi);
    } else {
      setSelectedPOIs([]);
    }

    // Enable POI adding mode
    setAddPOIs(true);

    // Fetch categories for this new location
    onPOISearchChange();
  }, [addPOI, selectedItem]);

  const searchwithDebounceTime = (searchTerm: string) => {
    try {
      // Clear existing debounce timer
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
      abortControllerRef.current?.abort();

      // Create abort controller for this request
      abortControllerRef.current = new AbortController();
      // Set up debounced autosave
      debounceTimerRef.current = setTimeout(async () => {
        onPOISearchChange(searchTerm);
      }, 300);
    } catch (error) {
      console.error(`Failed to search for category:`, error);
    }
  };

  const renderPOIList = async () => {
    if (!selectedItem) return null;
    const normalizedSelectedItem = normalizeItem(selectedItem);
    if (!selectedItem) {
      return;
    }
    if (selectedPOIs?.length) {
      let metadata: Record<string, string> = {};
      if ("metadata" in selectedItem && selectedItem.metadata) {
        metadata = selectedItem.metadata;
      }
      // Preserve identity/presentation metadata (place type, Google photo,
      // locality info, etc.) alongside selected POI entries. The reducer
      // replaces `metadata` wholesale, so anything dropped here is lost.
      const PRESERVED_METADATA_KEYS = new Set([
        "type",
        "typeLabel",
        "photoUrl",
        "localName",
        "localNameType",
        "countryCode",
        "center",
      ]);
      const newMetadata: Record<string, string> = {};
      for (const metadataKey in metadata) {
        if (
          PRESERVED_METADATA_KEYS.has(metadataKey) ||
          selectedPOIs.includes(metadataKey)
        ) {
          newMetadata[metadataKey] = metadata[metadataKey];
        }
      }
      metadata = newMetadata;
      if (
        normalizedSelectedItem.itemType === "geometry" &&
        normalizedSelectedItem.type === "Polygon"
      ) {
        const center = selectedItem.metadata?.center
          ? JSON.parse(selectedItem.metadata.center)
          : null;
        if (center) {
          normalizedSelectedItem.lat = center.lat;
          normalizedSelectedItem.lng = center.lng;
          normalizedSelectedItem.radius = center.radius;
        }
      }
      const poisToRemove: string[] = [];
      for (const element of selectedPOIs) {
        const poi = element;
        if (!metadata[poi]) {
          metadata =
            (await addPOIPlacesData(normalizedSelectedItem, poi, metadata)) ||
            {};
        }
      }
      if (selectedItem.id) {
        let pois = selectedPOIs;
        if (poisToRemove.length) {
          const newSelectedPOIs = selectedPOIs.filter(
            (poi) => !poisToRemove.includes(poi),
          );
          pois = newSelectedPOIs;
        }
        updateLocationPOIMetadata?.(selectedItem.id, metadata, pois);
      }
    }
    setSelectedPOIs([]);
    setAddPOIs(false);
  };

  const addPOIPlacesData = async (
    normalizedSelectedItem: NormalizedItem,
    poi: string,
    metadata: Record<string, string>,
  ) => {
    const response = await fetchNearbyPlaces(
      {
        lat: normalizedSelectedItem?.lat || 0,
        lng: normalizedSelectedItem?.lng || 0,
        radius: normalizedSelectedItem?.radius || 1500,
      },
      poi,
      currentLanguage,
    );
    const poiMetadata = metadata[poi] ? JSON.parse(metadata[poi]) : {};
    if (!response.error) {
      // Google's new Places API surfaces localized fields as
      // `{ text, languageCode }` objects; the legacy API surfaces plain
      // strings. Unwrap either shape so we never store an object in a
      // string field (which would render as the parent location's name
      // or blow up React when rendered directly).
      const toText = (v: unknown): string => {
        if (typeof v === "string") return v;
        if (v && typeof v === "object" && "text" in v) {
          const t = (v as { text?: unknown }).text;
          return typeof t === "string" ? t : "";
        }
        return "";
      };

      const places: Array<POIPlaceData> = [];
      response.forEach((place: GooglePlace) => {
        const displayName = toText(place.displayName);
        // Skip entries with no usable name — they'd render as blank
        // or fall back to the parent location's label.
        if (!displayName) return;
        places.push({
          locationLat: place.location.lat(),
          locationLng: place.location.lng(),
          displayName,
          primaryType: place.primaryType ?? "",
          primaryTypeDisplayName: toText(place.primaryTypeDisplayName),
          address: toText(place.formattedAddress),
          rating: typeof place.rating === "number" ? place.rating : undefined,
        });
      });

      const category = categories.find((category) => category.value === poi);
      if (category) {
        poiMetadata.displayName = category.label;
      }
      // Google Places restricts by circle, so drop POIs that fall inside the
      // polygon's circumscribing circle but outside the actual shape.
      poiMetadata.places =
        normalizedSelectedItem.type === "Polygon"
          ? filterPOIPlacesInsidePolygon(
              places,
              normalizedSelectedItem.coordinates,
            )
          : places;
    } else {
      poiMetadata.displayName = poi;
    }
    metadata = {
      ...metadata,
      [poi]: JSON.stringify(poiMetadata),
    };
    return metadata;
  };

  // Debounced radius update function to prevent lag
  const debouncedRadiusUpdate = useCallback(
    (
      circleId: string,
      newRadius: number,
      lat: number,
      lng: number,
      included: boolean,
    ) => {
      // Clear existing timer
      if (radiusUpdateTimerRef.current) {
        clearTimeout(radiusUpdateTimerRef.current);
      }

      // Set new timer for debounced update
      radiusUpdateTimerRef.current = setTimeout(() => {
        // Update radius in callback
        onCircleRadiusUpdate?.(circleId, newRadius);
      }, 300); // 300ms debounce

      // Update in MapboxDraw immediately for smooth visual feedback
      if (drawRef.current) {
        const draw = drawRef.current;
        try {
          // Remove old circle
          draw.delete(circleId);

          // Create new circle with updated radius
          const circleCoords = createCirclePolygon(lng, lat, newRadius);
          if (mapRef.current) {
            const source = mapRef.current.getSource(
              "circle-" + circleId,
            ) as mapboxgl.GeoJSONSource;

            if (source) {
              // Update existing circle
              source.setData({
                type: "Feature",
                geometry: {
                  type: "Polygon",
                  coordinates: [circleCoords],
                },
                properties: { included: included },
              });
              console.debug(`Circle ${circleId} updated`);
            }
          }
        } catch (e) {
          console.debug("Could not update circle in draw:", e);
        }
      }
    },
    [onCircleRadiusUpdate, createCirclePolygon],
  );

  // Control functions
  // Helper to clean up all circle drag-to-draw handlers and state
  const cleanupCircleDragHandlers = () => {
    const map = mapRef.current;
    if (!map) return;
    if (circleClickHandlerRef.current) {
      map.off("click", circleClickHandlerRef.current);
      circleClickHandlerRef.current = null;
    }
    if (circleMouseDownHandlerRef.current) {
      map.off("mousedown", circleMouseDownHandlerRef.current);
      circleMouseDownHandlerRef.current = null;
    }
    if (circleMouseMoveHandlerRef.current) {
      map.off("mousemove", circleMouseMoveHandlerRef.current);
      circleMouseMoveHandlerRef.current = null;
    }
    if (circleMouseUpHandlerRef.current) {
      map.off("mouseup", circleMouseUpHandlerRef.current);
      circleMouseUpHandlerRef.current = null;
    }
    if (isCircleDraggingRef.current) {
      map.dragPan.enable();
      isCircleDraggingRef.current = false;
    }
    circleDragCenterRef.current = null;
    removeCirclePreview();
  };

  const setDrawingMode = (mode: string) => {
    if (!drawRef.current || !mapRef.current) return;

    // Clean up any existing circle drag handlers and preview
    cleanupCircleDragHandlers();

    if (mode === "draw_circle") {
      setDrawMode("draw_circle");
      setIsDrawingCircle(true);
      setCircleRadius(500);
      circleRadiusRef.current = 500;
      setCurrentCircleId(null);
      mapRef.current.getCanvas().style.cursor = "crosshair";

      // Put MapboxDraw into simple_select mode to disable its polygon/line click handlers
      drawRef.current.changeMode("simple_select");

      // --- Escape key handler to cancel mid-drag ---
      const handleEscapeKey = (ev: KeyboardEvent) => {
        if (ev.key === "Escape" && isCircleDraggingRef.current) {
          // Cancel the drag — remove preview, re-enable panning
          if (mapRef.current) {
            mapRef.current.dragPan.enable();
          }
          isCircleDraggingRef.current = false;
          circleDragCenterRef.current = null;
          removeCirclePreview();
          // Remove mousemove/mouseup but keep mousedown so user can retry
          if (circleMouseMoveHandlerRef.current && mapRef.current) {
            mapRef.current.off("mousemove", circleMouseMoveHandlerRef.current);
            circleMouseMoveHandlerRef.current = null;
          }
          if (circleMouseUpHandlerRef.current && mapRef.current) {
            mapRef.current.off("mouseup", circleMouseUpHandlerRef.current);
            circleMouseUpHandlerRef.current = null;
          }
        }
      };
      document.addEventListener("keydown", handleEscapeKey);

      // --- mousedown: set circle center, show preview, start drag ---
      const handleCircleMouseDown = (e: mapboxgl.MapMouseEvent) => {
        // Only respond to left-click
        if (e.originalEvent.button !== 0) return;

        // Check for duplicate circle by coordinates
        if (items.length > 0 && checkForDuplicate) {
          const isDuplicate = items.some((loc) => {
            if ("lat" in loc && "lng" in loc) {
              const latDiff = Math.abs(loc.lat - e.lngLat.lat);
              const lngDiff = Math.abs(loc.lng - e.lngLat.lng);
              return latDiff < 0.0001 && lngDiff < 0.0001;
            }
            return false;
          });

          if (isDuplicate) {
            showWarning(t("map.duplicateCircle"));
            return;
          }
        }

        // Store the drag center
        circleDragCenterRef.current = {
          lat: e.lngLat.lat,
          lng: e.lngLat.lng,
        };
        isCircleDraggingRef.current = true;

        // Disable map panning while dragging the circle
        mapRef.current?.dragPan.disable();

        // Show initial tiny preview circle
        addCirclePreview(e.lngLat.lng, e.lngLat.lat, 200);

        // Attach mousemove and mouseup for this drag
        circleMouseMoveHandlerRef.current = handleCircleMouseMove;
        circleMouseUpHandlerRef.current = handleCircleMouseUp;
        mapRef.current?.on("mousemove", handleCircleMouseMove);
        mapRef.current?.on("mouseup", handleCircleMouseUp);
      };

      // --- mousemove: update preview circle radius ---
      const handleCircleMouseMove = (e: mapboxgl.MapMouseEvent) => {
        if (!isCircleDraggingRef.current || !circleDragCenterRef.current)
          return;
        const center = circleDragCenterRef.current;
        const rawRadius = calculateDistance(
          center.lat,
          center.lng,
          e.lngLat.lat,
          e.lngLat.lng,
        );
        const clampedRadius = Math.max(200, Math.min(5000, rawRadius));
        circleRadiusRef.current = clampedRadius;
        updateCirclePreview(center.lng, center.lat, clampedRadius);
      };

      // --- mouseup: finalize the circle ---
      const handleCircleMouseUp = (e: mapboxgl.MapMouseEvent) => {
        if (!isCircleDraggingRef.current || !circleDragCenterRef.current)
          return;

        const center = circleDragCenterRef.current;
        isCircleDraggingRef.current = false;

        // Re-enable map panning
        mapRef.current?.dragPan.enable();

        // Calculate final radius (clamped)
        const rawRadius = calculateDistance(
          center.lat,
          center.lng,
          e.lngLat.lat,
          e.lngLat.lng,
        );
        const finalRadius = Math.max(200, Math.min(5000, rawRadius));

        // Remove preview
        removeCirclePreview();

        // Remove mousemove / mouseup / mousedown handlers (one circle per drag)
        if (circleMouseMoveHandlerRef.current && mapRef.current) {
          mapRef.current.off("mousemove", circleMouseMoveHandlerRef.current);
          circleMouseMoveHandlerRef.current = null;
        }
        if (circleMouseUpHandlerRef.current && mapRef.current) {
          mapRef.current.off("mouseup", circleMouseUpHandlerRef.current);
          circleMouseUpHandlerRef.current = null;
        }
        if (circleMouseDownHandlerRef.current && mapRef.current) {
          mapRef.current.off("mousedown", circleMouseDownHandlerRef.current);
          circleMouseDownHandlerRef.current = null;
        }
        // Remove escape key listener
        document.removeEventListener("keydown", handleEscapeKey);

        // --- Create the permanent circle (same logic as the old click handler) ---
        const circleId = `location-${locationCounterRef.current}`;
        setCurrentCircleId(circleId);
        const circleName = `Circle ${circleCounterRef.current}`;

        onLocationSelected?.({
          id: circleId,
          lat: center.lat,
          lng: center.lng,
          name: circleName,
          address: `${center.lat.toFixed(6)}, ${center.lng.toFixed(6)}`,
          radius: finalRadius,
          included: true,
          isShape: true,
          poi: [],
          metadata: { type: "circle" },
        });

        const sourceId = `circle-${circleId}`;
        const fillLayerId = `circle-fill-${circleId}`;
        const outlineLayerId = `circle-outline-${circleId}`;
        const isIncluded = true;
        const circleCoords = createCirclePolygon(
          center.lng,
          center.lat,
          finalRadius,
        );

        circleLat.current = center.lat;
        circleLng.current = center.lng;

        try {
          const source = mapRef.current?.getSource(
            sourceId,
          ) as mapboxgl.GeoJSONSource;

          if (source) {
            source.setData({
              type: "Feature",
              geometry: {
                type: "Polygon",
                coordinates: [circleCoords],
              },
              properties: { included: isIncluded },
            });
          } else {
            if (mapRef.current?.getSource(sourceId)) {
              return;
            }
            mapRef.current?.addSource(sourceId, {
              type: "geojson",
              data: {
                type: "Feature",
                geometry: {
                  type: "Polygon",
                  coordinates: [circleCoords],
                },
                properties: { included: isIncluded },
              },
            });
            const firstSymbolId = getFirstSymbolLayerId();
            if (!mapRef.current?.getLayer(fillLayerId)) {
              mapRef.current?.addLayer(
                {
                  id: fillLayerId,
                  type: "fill",
                  source: sourceId,
                  paint: {
                    "fill-color": isIncluded ? "#1D65AF" : "#C52828",
                    "fill-opacity": 0.25,
                  },
                },
                firstSymbolId,
              );
            }
            if (!mapRef.current?.getLayer(outlineLayerId)) {
              mapRef.current?.addLayer(
                {
                  id: outlineLayerId,
                  type: "line",
                  source: sourceId,
                  paint: {
                    "line-color": isIncluded ? "#1D65AF" : "#C52828",
                    "line-width": 2,
                    "line-dasharray": [2, 2],
                  },
                },
                firstSymbolId,
              );
            }
          }
        } catch (error) {
          console.debug(`Error adding/updating circle ${circleId}:`, error);
        }

        circleCounterRef.current += 1;
        locationCounterRef.current += 1;

        // Update radius state so slider shows actual drawn radius
        setCircleRadius(finalRadius);
        circleRadiusRef.current = finalRadius;

        // Reset cursor and drawing mode
        if (mapRef.current) {
          mapRef.current.getCanvas().style.cursor = "";
        }
        if (drawRef.current) {
          drawRef.current.changeMode("simple_select");
        }
        setIsDrawingCircle(false);
        setDrawMode("simple_select");
        circleDragCenterRef.current = null;
        // Keep currentCircleId so slider stays visible for the newly drawn circle
      };

      // Store and attach the mousedown handler
      circleMouseDownHandlerRef.current = handleCircleMouseDown;
      mapRef.current.on("mousedown", handleCircleMouseDown);
    } else {
      // Switching to another drawing mode (polygon, line, simple_select, etc.)
      setDrawMode(mode);
      setIsDrawingCircle(false);

      // Clear currentCircleId when switching to polygon or line mode
      // Keep it when switching to simple_select (so slider stays visible)
      if (mode !== "simple_select") {
        setCurrentCircleId(null);
      }

      if (mode === "simple_select") {
        mapRef.current.getCanvas().style.cursor = "";
      }
      // Tell MapboxDraw to change mode - this unbinds previous mode's click handlers
      drawRef.current.changeMode(mode);
    }
  };

  const changeMapStyle = (styleUrl: string) => {
    if (!mapRef.current) return;

    // Remember if mountain view and 3D were active, and current view state
    const wasMountainViewActive = mountainView;
    const was3DActive = is3D;
    const currentPitch = mapRef.current.getPitch();
    const currentBearing = mapRef.current.getBearing();
    const currentZoom = mapRef.current.getZoom();

    // Mark style as not loaded when changing styles
    setIsStyleLoaded(false);

    mapRef.current.setStyle(styleUrl);
    setCurrentMapStyle(styleUrl);

    // Always mark style as loaded after it finishes loading
    const handleStyleLoad = () => {
      if (!mapRef.current) return;
      setIsStyleLoaded(true);
    };
    mapRef.current.once("style.load", handleStyleLoad);

    // Re-apply features after style loads if they were active
    if (wasMountainViewActive || was3DActive) {
      mapRef.current.once("style.load", () => {
        if (!mapRef.current) return;

        // Re-add terrain if it was active
        if (wasMountainViewActive) {
          if (!mapRef.current.getSource("mapbox-dem")) {
            mapRef.current.addSource("mapbox-dem", {
              type: "raster-dem",
              url: "mapbox://mapbox.mapbox-terrain-dem-v1",
              tileSize: 512,
              maxzoom: 14,
            });
          }
          mapRef.current.setTerrain({
            source: "mapbox-dem",
            exaggeration: 1.5,
          });
        }

        // Re-add 3D buildings if they were active
        if (was3DActive) {
          // Restore the camera position with smooth animation
          mapRef.current.easeTo({
            pitch: currentPitch,
            bearing: currentBearing,
            zoom: currentZoom,
            duration: 1000,
          });

          if (
            mapRef.current.getSource("composite") &&
            !mapRef.current.getLayer("3d-buildings")
          ) {
            mapRef.current.addLayer({
              id: "3d-buildings",
              source: "composite",
              "source-layer": "building",
              filter: ["==", "extrude", "true"],
              type: "fill-extrusion",
              minzoom: 15,
              paint: {
                "fill-extrusion-color": "#aaa",
                "fill-extrusion-height": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  15,
                  0,
                  15.05,
                  ["get", "height"],
                ],
                "fill-extrusion-base": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  15,
                  0,
                  15.05,
                  ["get", "min_height"],
                ],
                "fill-extrusion-opacity": 0.6,
              },
            });
          }
        }

        // Move draw layers before symbol layers to keep markers on top
        const firstSymbolId = getFirstSymbolLayerId();
        if (firstSymbolId) {
          const drawLayerIds = [
            "gl-draw-polygon-fill-inactive",
            "gl-draw-polygon-stroke-inactive",
            "gl-draw-polygon-fill-active",
            "gl-draw-polygon-stroke-active",
            "gl-draw-line",
            "gl-draw-point",
          ];

          drawLayerIds.forEach((layerId) => {
            if (mapRef.current?.getLayer(layerId)) {
              try {
                mapRef.current.moveLayer(layerId, firstSymbolId);
              } catch (e) {
                console.debug(`Could not move layer ${layerId}:`, e);
              }
            }
          });
        }
      });
    }
  };

  const toggle3D = () => {
    if (!mapRef.current) return;

    const currentZoom = mapRef.current.getZoom();
    const currentCenter = mapRef.current.getCenter();

    if (is3D) {
      // Turning OFF 3D - smooth transition back to normal view
      const normalizedSelectedItem = selectedItem
        ? normalizeItem(selectedItem)
        : null;
      const targetLocation = normalizedSelectedItem
        ? {
            center: [
              normalizedSelectedItem.lng,
              normalizedSelectedItem.lat,
            ] as [number, number],
            zoom: 14,
          }
        : {
            center: [currentCenter.lng, currentCenter.lat] as [number, number],
            zoom: currentZoom < 15 ? currentZoom : 14,
          };

      mapRef.current.flyTo({
        center: targetLocation.center,
        pitch: 0,
        bearing: 0,
        zoom: targetLocation.zoom,
        duration: 1500, // 1.5 second smooth animation
        essential: true,
      });

      // Remove 3D buildings layer after animation starts
      setTimeout(() => {
        if (mapRef.current?.getLayer("3d-buildings")) {
          mapRef.current.removeLayer("3d-buildings");
        }
      }, 200);
    } else {
      // Turning ON 3D - zoom to selected location or current position with realistic zoom
      const normalizedSelectedItem = selectedItem
        ? normalizeItem(selectedItem)
        : null;
      const targetLocation = normalizedSelectedItem
        ? {
            center: [
              normalizedSelectedItem.lng,
              normalizedSelectedItem.lat,
            ] as [number, number],
            zoom: 17, // 3D view zoom level
          }
        : {
            center: [currentCenter.lng, currentCenter.lat] as [number, number],
            zoom: 17, // 3D view zoom level
          };

      mapRef.current.flyTo({
        center: targetLocation.center,
        pitch: 60, // Increased pitch for better 3D effect
        bearing: 60,
        zoom: targetLocation.zoom,
        duration: 2000, // 2 second smooth animation for more dramatic effect
        essential: true,
      });

      // Add 3D buildings layer after zoom starts
      setTimeout(() => {
        if (
          mapRef.current?.getSource("composite") &&
          !mapRef.current?.getLayer("3d-buildings")
        ) {
          mapRef.current.addLayer({
            id: "3d-buildings",
            source: "composite",
            "source-layer": "building",
            filter: ["==", "extrude", "true"],
            type: "fill-extrusion",
            minzoom: 15,
            paint: {
              "fill-extrusion-color": "#aaa",
              "fill-extrusion-height": [
                "interpolate",
                ["linear"],
                ["zoom"],
                15,
                0,
                15.05,
                ["get", "height"],
              ],
              "fill-extrusion-base": [
                "interpolate",
                ["linear"],
                ["zoom"],
                15,
                0,
                15.05,
                ["get", "min_height"],
              ],
              "fill-extrusion-opacity": 0.6,
            },
          });
        }
      }, 200);
    }
    setIs3D(!is3D);
  };

  // Function to update map language
  const setMapLanguage = React.useCallback((language: string) => {
    if (!mapRef.current || !mapRef.current.isStyleLoaded()) return;

    const map = mapRef.current;
    const style = map.getStyle();
    if (!style || !style.layers) return;

    // Update text fields for all symbol layers
    style.layers.forEach((layer) => {
      if (
        layer.type === "symbol" &&
        layer.layout &&
        layer.layout["text-field"]
      ) {
        try {
          // Get the current text-field to check if it needs updating
          const currentTextField = layer.layout["text-field"];

          // Skip if already has the language expression
          if (
            Array.isArray(currentTextField) &&
            currentTextField[0] === "coalesce" &&
            JSON.stringify(currentTextField).includes(`name_${language}`)
          ) {
            return; // Already set to this language
          }

          // Update the text field with language-specific name
          map.setLayoutProperty(layer.id, "text-field", [
            "coalesce",
            ["get", `name_${language}`],
            ["get", "name"],
          ]);
        } catch (error) {
          // Ignore errors for layers that don't support text-field changes
          console.debug(
            `Could not update language for layer: ${layer.id}`,
            error,
          );
        }
      }
    });
  }, []);

  // Update map language when Tolgee language changes or map style changes
  useEffect(() => {
    if (!mapRef.current) return;

    // Only update if language has actually changed
    if (lastAppliedLanguageRef.current === currentLanguage) {
      console.debug("Language unchanged, skipping map language update");
      return;
    }

    const updateLanguage = () => {
      if (mapRef.current?.isStyleLoaded()) {
        setMapLanguage(currentLanguage);
        lastAppliedLanguageRef.current = currentLanguage; // Update the last applied language
      }
    };

    // Try to update immediately if style is loaded
    if (mapRef.current.isStyleLoaded()) {
      updateLanguage();
    } else {
      // If style not loaded yet, wait for it
      mapRef.current.once("style.load", updateLanguage);
    }

    // Also listen for style changes (when user switches map styles)
    const handleStyleData = () => {
      // Small delay to ensure style is fully loaded
      setTimeout(updateLanguage, 100);
    };

    mapRef.current.on("styledata", handleStyleData);

    return () => {
      mapRef.current?.off("styledata", handleStyleData);
    };
  }, [currentLanguage, currentMapStyle, setMapLanguage]); // Removed setMapLanguage as it has empty deps and won't change

  const toggleMountainView = () => {
    if (!mapRef.current) return;

    const currentZoom = mapRef.current.getZoom();
    const currentPitch = mapRef.current.getPitch();
    const currentCenter = mapRef.current.getCenter();

    if (mountainView) {
      // Currently ON, so turn it OFF - smooth transition
      const normalizedSelectedItem = selectedItem
        ? normalizeItem(selectedItem)
        : null;
      const targetLocation = normalizedSelectedItem
        ? {
            center: [
              normalizedSelectedItem.lng,
              normalizedSelectedItem.lat,
            ] as [number, number],
            zoom: 12,
          }
        : {
            center: [currentCenter.lng, currentCenter.lat] as [number, number],
            zoom: currentZoom > 14 ? 12 : currentZoom,
          };

      mapRef.current.flyTo({
        center: targetLocation.center,
        pitch: 0,
        zoom: targetLocation.zoom,
        duration: 1500, // 1.5 second smooth animation
        essential: true,
      });

      // Remove terrain after animation starts
      setTimeout(() => {
        if (mapRef.current) {
          mapRef.current.setTerrain(null);
          if (mapRef.current.getSource("mapbox-dem")) {
            mapRef.current.removeSource("mapbox-dem");
          }
        }
      }, 200);
    } else {
      // Currently OFF, so turn it ON - zoom to selected location or current position
      const normalizedSelectedItem = selectedItem
        ? normalizeItem(selectedItem)
        : null;
      const targetLocation = normalizedSelectedItem
        ? {
            center: [
              normalizedSelectedItem.lng,
              normalizedSelectedItem.lat,
            ] as [number, number],
            zoom: 20, // High zoom for detailed terrain viewing when location selected
            pitch: 65, // Dramatic angle for mountain/terrain view
          }
        : {
            center: [currentCenter.lng, currentCenter.lat] as [number, number],
            zoom: currentZoom < 12 ? 14 : Math.max(currentZoom, 13),
            pitch: currentPitch < 30 ? 60 : Math.max(currentPitch, 50),
          };

      // First, add the terrain source if it doesn't exist
      if (!mapRef.current.getSource("mapbox-dem")) {
        mapRef.current.addSource("mapbox-dem", {
          type: "raster-dem",
          url: "mapbox://mapbox.mapbox-terrain-dem-v1",
          tileSize: 512,
          maxzoom: 14,
        });
      }

      // Apply terrain immediately
      mapRef.current.setTerrain({ source: "mapbox-dem", exaggeration: 1.5 });

      // Then smoothly fly to better viewing angle with location
      mapRef.current.flyTo({
        center: targetLocation.center,
        pitch: targetLocation.pitch,
        zoom: targetLocation.zoom,
        duration: 2000, // 2 second smooth animation for dramatic terrain reveal
        essential: true,
      });
    }

    setMountainView(!mountainView);
  };

  const sliderMouseUp = () => {
    // Use ref value to avoid stale-closure reading of circleRadius state
    const latestRadius = circleRadiusRef.current;
    // Update the circle if it exists in Redux (debounced)
    if (currentCircleId) {
      const location = items.find((loc) => loc.id === currentCircleId);
      if (
        location &&
        "lat" in location &&
        "lng" in location &&
        "included" in location
      ) {
        // Use debounced update to prevent lag
        debouncedRadiusUpdate(
          currentCircleId,
          latestRadius,
          location.lat,
          location.lng,
          location.included,
        );
      } else {
        const map = mapRef.current;
        if (map) {
          const source = map.getSource(
            `circle-${currentCircleId}`,
          ) as mapboxgl.GeoJSONSource;
          if (source) {
            source.setData({
              type: "Feature",
              geometry: {
                type: "Polygon",
                coordinates: [
                  createCirclePolygon(
                    circleLng.current,
                    circleLat.current,
                    latestRadius,
                  ),
                ],
              },
              properties: { included: true },
            });
          }
        }
      }
    }
  };

  if (mapInitError) {
    return (
      <div className="relative flex h-full min-h-[300px] w-full flex-col items-center justify-center gap-2 rounded border border-mw-neutral-200 bg-mw-neutral-50 p-6 text-center">
        <p className="text-sm font-medium text-mw-neutral-700">
          Map unavailable
        </p>
        <p className="max-w-md text-xs text-mw-neutral-500">
          The interactive map could not be loaded in this browser (WebGL is
          unavailable). You can continue working — all other features remain
          functional.
        </p>
      </div>
    );
  }

  return (
    <div className="relative h-full min-h-[300px]">
      {/* Map Container */}
      <div
        id="map-container"
        ref={mapContainerRef}
        className="relative h-full w-full min-h-[300px] rounded overflow-hidden"
      />

      {/* Location Search Bar and POI Filter */}
      {config.search?.enabled && (
        <div className="absolute z-10 top-4 left-auto right-auto w-full p-2">
          <div className="flex gap-2">
            {/* Search Bar */}
            <div
              className={`relative ${config.search?.showPOIFilter ? "flex-1" : "w-full max-w-md"}`}
            >
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-mw-neutral-400 z-10" />
              <Input
                id="map-search-places"
                type="text"
                placeholder={
                  config.search?.placeholder ||
                  t("map.searchDefaultPlaceholder")
                }
                value={searchTerm}
                onChange={handleSearchChange}
                onKeyDown={handleSearchKeyDown}
                className="pl-10 shadow-sm bg-white"
                autoComplete="off"
              />
              {config.search?.showResults && searchResults.length > 0 ? (
                <div className="absolute top-full mt-1 w-full bg-white border border-mw-neutral-200 rounded-md shadow-lg max-h-72 overflow-auto z-20">
                  {searchResults.map((result, index) => {
                    const typeKey =
                      result.primaryTypeKey ??
                      pickPrimaryType(result.types) ??
                      result.types?.[0];
                    const typeInfo = getPlaceTypeInfo(typeKey ?? null);
                    const TypeIcon = typeInfo.Icon;
                    const isFocused = index === focusedSearchIndex;

                    // Lazy-load photo only when we render (opt-in, avoids billing until visible)
                    const photoUrl =
                      typeof result.getPhotoUrl === "function"
                        ? (result.getPhotoUrl() ?? null)
                        : null;

                    return (
                      <button
                        key={index}
                        id={"search-location-" + (index + 1)}
                        onClick={() => {
                          selectSearchResult(result);
                          setFocusedSearchIndex(-1);
                        }}
                        onMouseEnter={() => setFocusedSearchIndex(index)}
                        className={`w-full text-left px-3 py-2.5 border-b border-mw-neutral-100 last:border-b-0 transition-colors flex items-center gap-3 ${
                          isFocused
                            ? "bg-mw-primary-50"
                            : "hover:bg-mw-neutral-50"
                        }`}
                      >
                        {/* Place thumbnail — icon is always the base layer;
                            photo overlays it so if the URL fails the icon still shows */}
                        <div
                          className={`shrink-0 w-10 h-10 rounded-md overflow-hidden relative flex items-center justify-center ${typeInfo.bgClass}`}
                        >
                          {/* Base: icon always rendered */}
                          <TypeIcon
                            className={`h-5 w-5 ${typeInfo.colorClass}`}
                          />
                          {/* Photo layer: absolute overlay, hides itself on error */}
                          {photoUrl && (
                            <img
                              src={photoUrl}
                              alt={result.name ?? ""}
                              className="absolute inset-0 w-full h-full object-cover"
                              onError={(e) => {
                                (
                                  e.currentTarget as HTMLImageElement
                                ).style.display = "none";
                              }}
                            />
                          )}
                        </div>

                        {/* Text info */}
                        <div className="flex-1 min-w-0">
                          <div className="font-medium text-sm text-mw-neutral-900 truncate">
                            {result.name}
                          </div>
                          {result.localName && (
                            <div className="text-xs text-mw-primary-600 font-medium truncate">
                              {result.localName}
                            </div>
                          )}
                          <div className="text-xs text-mw-neutral-400 truncate mt-0.5">
                            {result.formatted_address}
                          </div>
                        </div>

                        {/* Type badge */}
                        <div
                          className={`shrink-0 flex items-center gap-1 px-1.5 py-0.5 rounded-full text-xs font-medium ${typeInfo.bgClass} ${typeInfo.colorClass}`}
                        >
                          <TypeIcon className="h-3 w-3" />
                          <span className="hidden sm:inline">
                            {result.primaryTypeLabel ||
                              (typeInfo.label === "Place"
                                ? t("map.placeType")
                                : typeInfo.label)}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              ) : (
                searchTerm && (
                  <div className="absolute top-full mt-1 w-full bg-white border border-mw-neutral-200 rounded-md shadow-lg max-h-60 overflow-auto z-20">
                    <div className="w-full text-left px-3 py-2 text-sm border-b border-mw-neutral-100 last:border-b-0 transition-colors">
                      <div className="text-s text-mw-neutral-700">
                        {t("map.noSearchResults")}
                      </div>
                      <div className="text-xs text-mw-neutral-400 mt-0.5">
                        {t("map.checkSpellingOrTryAgain")}
                      </div>
                    </div>
                  </div>
                )
              )}
            </div>

            {/* POI Multi-Select Filter */}
            {config.search?.showPOIFilter && (
              <div className="flex-1 min-w-[250px]">
                <MultiSelect
                  id="map-poi-select"
                  options={categories}
                  value={selectedPOIs}
                  onChange={handlePOIChange}
                  onSearchChange={searchwithDebounceTime}
                  placeholder={
                    config.search?.poiPlaceholder || t("map.poiPlaceholder")
                  }
                  searchable={true}
                  clearable={true}
                  closeOnSelect={false}
                  maxHeight="300px"
                  showSelectAll={false}
                  expandable={false}
                  className="w-full"
                  disabled={!addPOI}
                  maxSelections={5}
                  showPlaceHolderWithSearchOption={t(
                    "map.selectionOptionPlaceholder",
                  )}
                  showPlaceHolderWithSearchOptionWithMaxSelectionCount={true}
                  noOptionFoundLabel={t("map.noPOIOption")}
                  showAddOptionIfNoOptionFound={true}
                  renderFooter={(setIsOpen) => (
                    <Button
                      variant="primary"
                      onClick={() => {
                        renderPOIList();
                        setIsOpen(false);
                      }}
                      className="w-full"
                    >
                      {t("map.renderPOIButton")}
                    </Button>
                  )}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* Radius Control Slider */}
      {(() => {
        // Show slider if we're drawing a circle OR have a circle selected (currentCircleId is set)
        const shouldShowSlider = isDrawingCircle || currentCircleId !== null;

        if (!shouldShowSlider) return null;

        return (
          <div className="absolute bottom-20 left-1/2 transform -translate-x-1/2 z-10 bg-white border border-mw-neutral-200 rounded-md p-3 shadow-lg min-w-[300px]">
            {/* <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-mw-neutral-700"></span>
              <span className="text-sm font-semibold text-mw-primary-500">
                {displayRadius}m
              </span>
            </div> */}
            <Slider
              min={200}
              max={5000}
              step={50}
              value={circleRadius}
              showValue={true}
              labelClassName="text-sm font-medium text-mw-neutral-700"
              valueClassName="text-sm font-semibold text-mw-primary-500"
              label={t("map.targetRadius")}
              formatValue={(value) => `${value}m`}
              onChange={(newRadius) => {
                setCircleRadius(newRadius);
                circleRadiusRef.current = newRadius;
                // Live preview: update the map circle shape while dragging
                if (currentCircleId && mapRef.current) {
                  const src = mapRef.current.getSource(
                    `circle-${currentCircleId}`,
                  ) as mapboxgl.GeoJSONSource | undefined;
                  if (src) {
                    const loc = items.find((l) => l.id === currentCircleId);
                    const lat =
                      loc && "lat" in loc ? loc.lat : circleLat.current;
                    const lng =
                      loc && "lng" in loc ? loc.lng : circleLng.current;
                    src.setData({
                      type: "Feature",
                      geometry: {
                        type: "Polygon",
                        coordinates: [createCirclePolygon(lng, lat, newRadius)],
                      },
                      properties: {
                        included:
                          loc && "included" in loc ? loc.included : true,
                      },
                    });
                  }
                }
              }}
              onMouseUp={sliderMouseUp}
              className="w-full h-2 bg-mw-neutral-200 rounded-lg appearance-none cursor-pointer accent-mw-primary-500"
            />
            <div className="flex justify-between text-xs text-mw-neutral-500 mt-1">
              <span>200m</span>
              <span>5000m</span>
            </div>
          </div>
        );
      })()}

      {/* Control Tools - Bottom Middle */}
      <div className="absolute bottom-4 left-1/2 transform -translate-x-1/2 z-10 flex gap-2">
        {/* Drawing Tools */}
        {config.showDrawingTools && (
          <div className="bg-white border border-mw-neutral-200 rounded-md p-1 flex gap-1 shadow-md">
            {config.enableSelect && (
              <button
                onClick={() => setDrawingMode("simple_select")}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  drawMode === "simple_select"
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.select")}
              >
                <MapPin className="h-4 w-4" />
              </button>
            )}
            {config.enablePolygon && (
              <button
                onClick={() => setDrawingMode("draw_polygon")}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  drawMode === "draw_polygon"
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.drawPolygon")}
              >
                <Square className="h-4 w-4" />
              </button>
            )}
            {config.enableCircle && (
              <button
                onClick={() => setDrawingMode("draw_circle")}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  drawMode === "draw_circle"
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.drawCircle")}
              >
                <Circle className="h-4 w-4" />
              </button>
            )}
            {config.enableLine && (
              <button
                onClick={() => setDrawingMode("draw_line_string")}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  drawMode === "draw_line_string"
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.drawLine")}
              >
                <Route className="h-4 w-4" />
              </button>
            )}
            {config.enableDelete && (
              <button
                id="map-delete-all"
                onClick={() => {
                  // Delete all drawn shapes (polygons, lines, circles) from MapboxDraw
                  if (mapRef.current) {
                    const map = mapRef.current;
                    const source = map.getSource(
                      "custom-polygons",
                    ) as mapboxgl.GeoJSONSource;
                    source.setData({
                      type: "FeatureCollection",
                      features: [],
                    });
                  }

                  // Clear currentCircleId since all circles are deleted
                  setCurrentCircleId(null);
                  deleteAllShapes?.();
                  deleteAllCircleLayers();
                }}
                className="h-8 w-8 p-0 rounded flex items-center justify-center bg-white text-mw-neutral-700 hover:bg-mw-neutral-50 transition-colors"
                title={t("map.controls.deleteSelected")}
              >
                <Trash2 className="h-4 w-4" />
              </button>
            )}
          </div>
        )}

        {/* View Tools */}
        {config.showViewTools && (
          <div className="bg-white border border-mw-neutral-200 rounded-md p-1 flex gap-1 shadow-md">
            {config.enableMountainView && (
              <button
                onClick={toggleMountainView}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  mountainView
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.toggleMountainView")}
              >
                <Mountain className="h-4 w-4" />
              </button>
            )}
            {config.enable3D && (
              <button
                onClick={toggle3D}
                className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                  is3D
                    ? "bg-mw-primary-500 text-white"
                    : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                }`}
                title={t("map.controls.toggle3DBuildings")}
              >
                <Layers3 className="h-4 w-4" />
              </button>
            )}
          </div>
        )}

        {/* Map Styles */}
        {config.showMapStyles && (
          <div className="bg-white border border-mw-neutral-200 rounded-md p-1 flex gap-1 shadow-md">
            {mapStyles
              .filter((style) =>
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                config.enabledStyles?.includes(style.id as any),
              )
              .map((style) => {
                const IconComponent = style.icon;
                return (
                  <button
                    key={style.id}
                    onClick={() => changeMapStyle(style.style)}
                    className={`h-8 w-8 p-0 rounded flex items-center justify-center transition-colors ${
                      currentMapStyle === style.style
                        ? "bg-mw-primary-500 text-white"
                        : "bg-white text-mw-neutral-700 hover:bg-mw-neutral-50"
                    }`}
                    title={t(style.nameKey)}
                  >
                    <IconComponent className="h-4 w-4" />
                  </button>
                );
              })}
          </div>
        )}
      </div>
    </div>
  );
};

export default MapBoxWrapper;
