import {
  MobilityHeatmapControl,
  toCountrySlug,
  useMobilityHeatmapLayer,
} from "@components/map/MobilityHeatmap";
import { Button } from "@components/ui/Button";
import { Checkbox } from "@components/ui/Checkbox";
import { Chip } from "@components/ui/Chip";
import { Input } from "@components/ui/Input";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import Modal from "@components/ui/Modal";
import { Tooltip } from "@components/ui/Tooltip";
import {
  MAX_POI_COUNT,
  DUPLICATE_DETECTION_THRESHOLD,
  DEFAULT_IMPORTED_LOCATION_RADIUS,
} from "@constants/campaign.constants";
import { useAnnounce } from "@hooks/useAnnounce";
import { TargetingFormData } from "@schemas/campaigns/targeting.schema";
import { useLazyGetCountryByNameQuery } from "@services/campaign/campaignSlice";
import {
  setSelectedLocation,
  setLocationIncluded,
  setGeometryIncluded,
  removeLocation,
  removeGeometry,
  setLocations,
  setGeometries,
  resetMapMarkerLocationsState,
  setAllItemsIncluded,
  addLocation,
  updateLocation,
  addGeometry,
  updateLocationRadius,
  updateLocationPOIMetadata as updateLocationPOIMetadataToState,
  deleteAllShapes as deleteAllShapesFromState,
  CombinedItem,
  updateGeometry,
} from "@services/map-marker-lists/mapMarkerLocationsSlice";
import { useTranslate } from "@tolgee/react";
import {
  getLocalNameTypeLabel,
  getPlaceTypeInfo,
} from "@utils/place-type-utils";
import clsx from "clsx";
import { FileDown, MapPin, Plus, Search, Trash2 } from "lucide-react";
import React, { useState, useEffect, useMemo, useRef } from "react";
import { Control, UseFormSetValue } from "react-hook-form";
import { useAppDispatch, useAppSelector } from "src/store";
import {
  MapGeometry,
  MapMarkerLocation,
  MobilityTimeBucket,
} from "src/types/campaign.types";

import type { Map as MapboxMap } from "mapbox-gl";

import GeoFencingLocationPopup from "./GeoFencingLocationPopup";
import GeoFencingPOIPopup from "./GeoFencingPOIPopup";
import {
  LocationCsvUploadDrawer,
  LocationData,
} from "./LocationCsvUploadDrawer";

// Country-level framing when the country record has no usable zoom
// (e.g. UAE is stored with zoom: 0, which previously hung "Loading map...")
const DEFAULT_COUNTRY_MAP_ZOOM = 5;

// Module-level: must keep a stable identity across renders (SI 48)
const GEO_FENCING_MAP_CONFIG: MapControlsConfig = {
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
    enabled: true,
    showResults: true,
    searchTypes: ["place", "poi", "address"],
    limit: 5,
    // POI Filter Configuration
    showPOIFilter: true,
  },
};

interface GeoFencingFormProps {
  control: Control<TargetingFormData>;
  onFieldChange: (value: {
    geofencing: Record<string, unknown>;
  }) => Promise<void>;
  geofencingFormData: TargetingFormData["geofencing"];
  setValue: UseFormSetValue<TargetingFormData>;
}

const GeoFencingForm: React.FC<GeoFencingFormProps> = ({
  onFieldChange,
  geofencingFormData,
  setValue,
}) => {
  // Initialize translation with campaigns namespace
  const { t } = useTranslate("campaigns");
  const { t: tCommon } = useTranslate("common");

  const dispatch = useAppDispatch();
  const markerLocations = useAppSelector((state) => state.mapMarkerLocations);

  const [searchQuery, setSearchQuery] = useState("");
  const [isInitialized, setIsInitialized] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [itemToDelete, setItemToDelete] = useState<CombinedItem | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const lastSyncedDataRef = React.useRef<string>("");

  // Counters for incremental IDs (not saved to payload, regenerated on load)
  const geometryCounterRef = useRef(0);
  const locationCounterRef = useRef(0);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const campaignState = useAppSelector((state: any) => state.campaign);
  const campaignData = campaignState.campaignData;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [availablePOIs, setAvailablePOIs] = useState<any[]>([]);
  // Use lazy query to trigger API call manually
  const [
    getCountryByName,
    {
      data: countryDataResponse,
      isLoading: isCountryLoading,
      isError: isCountryError,
    },
  ] = useLazyGetCountryByNameQuery();

  // Track the last fetched countryId to detect changes
  const lastFetchedCountryIdRef = useRef<string | null>(null);

  // Fetch country data once campaignData is loaded and countryId is available
  useEffect(() => {
    if (campaignData && !isCountryLoading) {
      lastFetchedCountryIdRef.current = campaignData.countryId;
      // API stores country ids as slugs (e.g. "sri-lanka"), so spaces must
      // become hyphens or multi-word countries 404
      getCountryByName(
        campaignData.countryId.toLowerCase().replace(/\s+/g, "-"),
      );
    }
  }, [campaignData, getCountryByName, isCountryLoading]);

  // Reset fetch tracking when campaignData is cleared
  useEffect(() => {
    if (!campaignData) {
      lastFetchedCountryIdRef.current = null;
    }
  }, [campaignData]);

  // Extract map center and zoom from API response.
  // Memoized: an unstable array identity re-runs the map's init/cleanup
  // effects on every keystroke of the sidebar search, tearing down markers/
  // popups and stealing focus from the input (SI 48).
  const countryData = countryDataResponse?.data;
  const mapDefaultCenter: [number, number] | undefined = useMemo(
    () =>
      countryData ? [countryData.longitude, countryData.latitude] : undefined,
    [countryData],
  );
  const mapDefaultZoom: number | undefined = countryData
    ? countryData.zoom || DEFAULT_COUNTRY_MAP_ZOOM
    : undefined;

  // Use refs to store callbacks to avoid them being dependencies
  const setValueRef = useRef(setValue);
  const onFieldChangeRef = useRef(onFieldChange);
  const { showWarning } = useAnnounce();
  const [addPOI, setAddPOI] = useState(false);

  // Audience mobility heatmap (toggleable layer over the geo-fencing map)
  const [mapInstance, setMapInstance] = useState<MapboxMap | null>(null);
  const [heatmapEnabled, setHeatmapEnabled] = useState(false);
  const [heatmapBucket, setHeatmapBucket] = useState<MobilityTimeBucket>("ALL");
  // Country slug matches the countries master (same slugification as the
  // getCountryByName lookup above).
  const countrySlug = toCountrySlug(campaignData?.countryId);
  const heatmapState = useMobilityHeatmapLayer({
    map: mapInstance,
    countrySlug,
    enabled: heatmapEnabled,
    timeBucket: heatmapBucket,
  });

  // Update refs when callbacks change
  useEffect(() => {
    setValueRef.current = setValue;
  }, [setValue]);

  useEffect(() => {
    onFieldChangeRef.current = onFieldChange;
  }, [onFieldChange]);

  // Load saved geofencing data into mapMarkerLocations state on mount
  useEffect(() => {
    if (!isInitialized && geofencingFormData) {
      const { geometries = [], locations = [] } = geofencingFormData;

      // Only load if there's saved data and map state is empty
      if (
        (geometries.length > 0 || locations.length > 0) &&
        markerLocations.geometries.length === 0 &&
        markerLocations.locations.length === 0
      ) {
        // Load geometries (polygons, lines) with proper structure
        // Geometries are always shapes (isShape = true)
        // Use incremental IDs (not saved to payload, regenerated on load)
        geometryCounterRef.current = 0;
        locationCounterRef.current = 0;
        const mappedGeometries = geometries.map((geo) => {
          geometryCounterRef.current += 1;
          return {
            id: `geometry-${geometryCounterRef.current}`,
            type: geo.type,
            coordinates: geo.coordinates,
            included: geo.included,
            name: geo.name || geo.type + " " + geometryCounterRef.current,
            isShape: true, // Geometries are always shapes
            poi: geo.poi || [],
            metadata: geo.metadata || {},
          };
        });

        // Load locations (circles, search results) with proper structure
        // Circles are shapes (have radius), search results are not
        // Use incremental IDs (not saved to payload, regenerated on load)
        const mappedLocations = locations.map((loc) => {
          locationCounterRef.current += 1;
          return {
            id: `location-${locationCounterRef.current}`,
            lat: loc.lat,
            lng: loc.lng,
            radius: loc.radius,
            address: loc.address,
            included: loc.included,
            name: loc.name || `Location ${locationCounterRef.current}`,
            isShape: loc.metadata?.type === "circle", // True if circle (has radius), false for search results
            poi: loc.poi || [],
            metadata: loc.metadata,
          };
        });

        if (mappedGeometries.length > 0) {
          dispatch(setGeometries(mappedGeometries));
        }
        if (mappedLocations.length > 0) {
          dispatch(setLocations(mappedLocations));
        }

        setIsInitialized(true);

        // Set the initial synced data to prevent immediate re-sync
        lastSyncedDataRef.current = JSON.stringify({
          geometries: mappedGeometries.map((g) => ({
            type: g.type,
            coordinates: g.coordinates,
            included: g.included,
            name: g.name,
            poi: g.poi,
            metadata: g.metadata,
          })),
          locations: mappedLocations.map((l) => ({
            lat: l.lat,
            lng: l.lng,
            radius: l.radius,
            address: l.address,
            included: l.included,
            name: l.name,
            poi: l.poi || [],
            metadata: l.metadata,
          })),
        });
      } else if (geometries.length === 0 && locations.length === 0) {
        // If no saved data, mark as initialized to prevent future checks
        setIsInitialized(true);
        // Initialize with empty data
        lastSyncedDataRef.current = JSON.stringify({
          geometries: [],
          locations: [],
        });
      }
    }
  }, [
    geofencingFormData,
    markerLocations.geometries.length,
    markerLocations.locations.length,
    dispatch,
    isInitialized,
  ]);

  // Sync mapMarkerLocations state with form data and trigger autosave
  useEffect(() => {
    // Only sync after initialization to prevent overwriting loaded data
    if (!isInitialized) return;

    // Convert geometries (polygons, lines) for the form
    // Note: isShape is not saved to payload, only used in UI
    const geometries = markerLocations.geometries.map((geo) => ({
      type: geo.type,
      coordinates: geo.coordinates,
      included: geo.included,
      name: geo.name,
      poi: geo.poi,
      metadata: geo.metadata,
    }));

    // Convert locations (circles, search results) for the form
    // Note: isShape is not saved to payload, only used in UI
    const locations = markerLocations.locations.map((loc) => ({
      lat: loc.lat,
      lng: loc.lng,
      radius: loc.radius,
      address: loc.address,
      included: loc.included,
      name: loc.name,
      poi: loc.poi,
      metadata: loc.metadata,
    }));

    const geofencingData = {
      geometries,
      locations,
    };

    // Create a stable string representation to compare
    const currentDataString = JSON.stringify(geofencingData);

    // Only update if data has actually changed
    if (currentDataString !== lastSyncedDataRef.current) {
      lastSyncedDataRef.current = currentDataString;

      // Update form control value so stepper can detect the change
      // Use shouldValidate: false to avoid triggering validation on programmatic changes
      // Use shouldDirty: true to mark the field as changed for the stepper
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setValueRef.current("geofencing", geofencingData as any, {
        shouldValidate: false,
        shouldDirty: true,
      });

      // Trigger autosave with updated geofencing data
      onFieldChangeRef.current({
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        geofencing: geofencingData as any,
      });
    }
    if (markerLocations.selectedLocation) {
      let location: CombinedItem =
        markerLocations.selectedLocation as CombinedItem;
      if (location.itemType === "location") {
        location =
          (markerLocations.locations.find(
            (loc) => loc.id === location.id,
          ) as CombinedItem) || location;
      } else {
        location =
          (markerLocations.geometries.find(
            (geo) => geo.id === location.id,
          ) as CombinedItem) || location;
      }
      if (location.poi && location.poi.length) {
        const poiPlaces = [];
        for (const poi of location.poi) {
          const poiData = location.metadata?.[poi]
            ? JSON.parse(location.metadata[poi])
            : {};
          if (poiData?.places) {
            poiPlaces.push(...poiData.places);
          }
          // Add logic to process each poi
        }
        setAvailablePOIs(poiPlaces);
      }
      // } else {
      //   setAvailablePOIs([]);
    }
  }, [
    markerLocations.locations,
    markerLocations.geometries,
    isInitialized,
    markerLocations.selectedLocation,
  ]);

  // Map configuration
  // Location-pin popup surfaces identity props only. Campaign-level budget
  // totals were removed (they were misleading — shown as per-location values
  // when they were really campaign-wide).
  const parentLocation =
    markerLocations.selectedLocation as MapMarkerLocation | null;
  const parentLocalNameType = parentLocation?.metadata?.localNameType;
  const parentStateLabel =
    parentLocalNameType === "State" || parentLocalNameType === "Region"
      ? parentLocation?.metadata?.localName
      : undefined;
  const popupExtraProps: Record<string, unknown> = useMemo(
    () => ({
      currency: campaignData?.currency,
      stateLabel: parentStateLabel,
    }),
    [campaignData?.currency, parentStateLabel],
  );

  // Stable identity (see SI 48 note above): derived map props are memoized so
  // typing in the sidebar search doesn't churn the map's effects.
  const combinedItems: CombinedItem[] = useMemo(
    () => [
      ...markerLocations.locations.map((loc) => ({
        ...loc,
        itemType: "location" as const,
      })),
      ...markerLocations.geometries.map((geo) => ({
        ...geo,
        itemType: "geometry" as const,
      })),
    ],
    [markerLocations.locations, markerLocations.geometries],
  );

  const handleCancelDelete = () => {
    setItemToDelete(null);
    setIsDeleteModalOpen(false);
  };

  // Handle delete confirmation
  const handleDeleteClick = (item: CombinedItem) => {
    setItemToDelete(item);
    setIsDeleteModalOpen(true);
  };

  // Helper function to get display address for an item
  const getItemAddress = (item: CombinedItem): string => {
    if (
      item.itemType === "location" &&
      (!item.metadata || item.metadata.type !== "circle")
    ) {
      return item.address;
    }
    // For geometries, show coordinates of the first point
    return "";
  };

  // Filter combined items based on search query
  const filteredItems = combinedItems.filter((item) => {
    const searchLower = searchQuery.toLowerCase();
    const name = item.name;
    const address = getItemAddress(item);
    return (
      name?.toLowerCase().includes(searchLower) ||
      address.toLowerCase().includes(searchLower)
    );
  });

  // Toggle all items (both geometries and locations)
  const handleToggleAll = (checked: boolean) => {
    dispatch(setAllItemsIncluded(checked));
  };

  // Toggle individual item
  const handleToggleItem = (item: CombinedItem, checked: boolean) => {
    if (item.itemType === "location") {
      dispatch(setLocationIncluded({ id: item.id, included: checked }));
    } else {
      dispatch(setGeometryIncluded({ id: item.id, included: checked }));
    }
  };

  // Helper to calculate geometry center
  const calculateGeometryCenter = (
    coordinates: number[][],
  ): { lat: number; lng: number } => {
    const sumCoords = coordinates.reduce(
      (acc, coord) => ({ lng: acc.lng + coord[0], lat: acc.lat + coord[1] }),
      { lng: 0, lat: 0 },
    );
    return {
      lng: sumCoords.lng / coordinates.length,
      lat: sumCoords.lat / coordinates.length,
    };
  };

  // Handle item click in list
  const handleItemClick = (item: CombinedItem) => {
    setAvailablePOIs(getSelectedLocationPOIPlaces(item));
    if (
      !markerLocations.selectedLocation ||
      markerLocations.selectedLocation.id !== item.id
    ) {
      if (item.itemType === "location") {
        // Navigate to any location (search results or circles)
        dispatch(setSelectedLocation(item as MapMarkerLocation));
      } else if (item.itemType === "geometry") {
        // For geometries (polygons/lines), calculate center and create a temporary location for navigation
        const geometry = item as MapGeometry;
        const center = calculateGeometryCenter(
          geometry.coordinates as number[][],
        );

        // Create a temporary location object for navigation
        const tempLocation: MapMarkerLocation = {
          id: geometry.id,
          lat: center.lat,
          lng: center.lng,
          name: item.name,
          address: getItemAddress(item),
          included: geometry.included,
          isShape: true,
          poi: geometry.poi || [],
          metadata: geometry.metadata,
        };

        // Select geometry and navigate to it
        dispatch(setSelectedLocation(tempLocation));
      }
    }
  };

  const onCircleRadiusUpdate = (id: string, radius: number) => {
    dispatch(updateLocationRadius({ id, radius }));
  };

  // Handle delete item
  const handleDeleteItem = () => {
    if (itemToDelete) {
      if (
        markerLocations.selectedLocation &&
        markerLocations.selectedLocation.id === itemToDelete.id
      ) {
        setAvailablePOIs([]);
      }
      if (itemToDelete.itemType === "location") {
        dispatch(removeLocation(itemToDelete.id));
      } else {
        dispatch(removeGeometry(itemToDelete.id));
      }
      dispatch(setSelectedLocation(null));
    }
  };

  // Handle import locations from CSV/Excel
  const handleImportLocations = (locations: LocationData[]) => {
    // Helper function to check if a location is duplicate
    const isDuplicateLocation = (loc: LocationData) => {
      return markerLocations.locations.some((currentLoc) => {
        const latDiff = Math.abs(loc.lat - currentLoc.lat);
        const lngDiff = Math.abs(loc.lng - currentLoc.lng);
        // Consider duplicate if within threshold (approximately 10 meters)
        return (
          latDiff < DUPLICATE_DETECTION_THRESHOLD &&
          lngDiff < DUPLICATE_DETECTION_THRESHOLD
        );
      });
    };

    // Separate duplicates from valid locations
    const duplicateLocations: LocationData[] = [];
    const validLocations: LocationData[] = [];

    locations.forEach((loc) => {
      if (isDuplicateLocation(loc)) {
        duplicateLocations.push(loc);
      } else {
        validLocations.push(loc);
      }
    });

    // Show warning once with duplicate count if there are any duplicates
    if (duplicateLocations.length > 0) {
      showWarning(
        `${duplicateLocations.length} location${duplicateLocations.length > 1 ? "s" : ""} already exist${duplicateLocations.length > 1 ? "" : "s"}, skipping duplicate${duplicateLocations.length > 1 ? "s" : ""}`,
      );
    }

    // Add each valid location to Redux
    validLocations.forEach((loc) => {
      // Generate incremental ID for imported location
      locationCounterRef.current += 1;
      const locationId = `location-${locationCounterRef.current}`;

      // Use address if available, otherwise use lat/lng coordinates
      const address = loc.address
        ? loc.address
        : `${loc.lat.toFixed(6)}, ${loc.lng.toFixed(6)}${loc.metaData ? ` (${loc.metaData.type})` : ""}`;

      const newLocation: MapMarkerLocation = {
        id: locationId,
        lat: loc.lat,
        lng: loc.lng,
        name: loc.name || locationId,
        address: address,
        metadata: loc.metaData || {},
        radius: DEFAULT_IMPORTED_LOCATION_RADIUS,
        included: loc.included,
        isShape: false, // Imported locations are not shapes
        poi: [],
      };

      dispatch(addLocation(newLocation));
    });

    // Close the modal
    setIsImportModalOpen(false);
  };

  const handleRemovePoi = (item: CombinedItem, poi: string) => {
    const pois = item.poi || [];
    if (pois.length) {
      const newPois = pois.filter((p) => p !== poi); // Create a new array
      const newMetadata = { ...(item.metadata || {}) };
      delete newMetadata[poi];
      if (item.itemType === "location") {
        dispatch(
          updateLocation({
            ...(item as MapMarkerLocation),
            poi: newPois,
            metadata: newMetadata,
          }),
        );
      } else {
        dispatch(
          updateGeometry({
            ...(item as MapGeometry),
            poi: newPois,
            metadata: newMetadata,
          }),
        );
      }
    }
  };

  const getChipDisplayName = (item: CombinedItem, poi: string) => {
    const metadata = item.metadata;
    if (metadata) {
      const poiMetadata = metadata[poi];
      if (poiMetadata) {
        const poiData = JSON.parse(poiMetadata);
        return poiData.displayName;
      }
    }
    return ""; // Fallback to the original POI string if no displayName found
  };

  const getSelectedLocationPOIPlaces = (item: CombinedItem) => {
    const metadata = item.metadata;
    const pois = item.poi;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let poiPlaces: any[] = [];
    if (metadata && pois && pois.length) {
      pois.forEach((poi) => {
        const poiMetadata = metadata[poi];
        if (poiMetadata) {
          const poiData = JSON.parse(poiMetadata);
          if (poiData.places && poiData.places.length) {
            poiPlaces = [...poiPlaces, ...poiData.places];
          }
        }
      });
    }
    return poiPlaces;
  };

  // Check if all items are enabled
  const allEnabled =
    combinedItems.length > 0 &&
    combinedItems.every((item) => item.included !== false);

  // Auto-navigate to first item when data loads or when component becomes visible
  useEffect(() => {
    // Only trigger when initialized and we have items
    if (!isInitialized || combinedItems.length === 0) return;
    // If no location is selected, select and navigate to the first available item
    if (!markerLocations.selectedLocation) {
      setAvailablePOIs([]);
      const firstItem = combinedItems[0];

      if (firstItem) {
        // Use a small delay to ensure map is fully loaded
        const timer = setTimeout(() => {
          // Inline the navigation logic to avoid dependency on handleItemClick
          setAvailablePOIs(getSelectedLocationPOIPlaces(firstItem));
          if (firstItem.itemType === "location") {
            dispatch(setSelectedLocation(firstItem as MapMarkerLocation));
          } else if (firstItem.itemType === "geometry") {
            const geometry = firstItem as MapGeometry;
            const center = calculateGeometryCenter(
              geometry.coordinates as number[][],
            );
            const tempLocation: MapMarkerLocation = {
              id: geometry.id,
              lat: center.lat,
              lng: center.lng,
              name: firstItem.name,
              address: getItemAddress(firstItem),
              included: geometry.included,
              isShape: true,
              poi: geometry.poi || [],
            };
            dispatch(setSelectedLocation(tempLocation));
          }
        }, 150);

        return () => clearTimeout(timer);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    isInitialized,
    combinedItems.length,
    markerLocations.selectedLocation,
    dispatch,
  ]);

  // Clear map marker locations when component unmounts (navigating away from geofencing tab)
  useEffect(() => {
    return () => {
      dispatch(resetMapMarkerLocationsState());
      // Reset initialization flag so data can be loaded again when returning to this tab
      setIsInitialized(false);
    };
  }, [dispatch]);

  const onLocationSelected = (location: MapMarkerLocation) => {
    // Handle location selection from the map
    dispatch(addLocation(location));
  };

  const onShapeDrawn = (geometry: MapGeometry) => {
    // Handle location selection from the map
    // Helper function to check if two coordinate arrays are similar
    const areCoordsSimmilar = (coords1: number[][], coords2: number[][]) => {
      if (coords1.length !== coords2.length) return false;
      return coords1.every((coord1, index) => {
        const coord2 = coords2[index];
        const lngDiff = Math.abs(coord1[0] - coord2[0]);
        const latDiff = Math.abs(coord1[1] - coord2[1]);
        // Consider similar if within threshold (approximately 10 meters)
        return (
          lngDiff < DUPLICATE_DETECTION_THRESHOLD &&
          latDiff < DUPLICATE_DETECTION_THRESHOLD
        );
      });
    };

    // Check for duplicate geometry by coordinates
    if (markerLocations?.geometries) {
      const isDuplicate = markerLocations.geometries.some((geom) => {
        if (geom.type !== geometry.type) return false;
        return areCoordsSimmilar(geometry.coordinates, geom.coordinates);
      });

      if (!isDuplicate) {
        dispatch(addGeometry(geometry));
      } else {
        showWarning(tCommon("map.duplicateShape"));
      }
    }
  };

  // Show loading state while waiting for campaignData or fetching country data
  if (
    !campaignData ||
    isCountryLoading ||
    !mapDefaultCenter ||
    !mapDefaultZoom
  ) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <div className="text-mw-neutral-500">
          {t("targeting.geofencing.loading_map") || "Loading map..."}
        </div>
      </div>
    );
  }

  // Show error state if country data fetch failed
  if (isCountryError) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <div className="text-red-500">
          {t("targeting.geofencing.map_error") ||
            "Failed to load map. Please try again."}
        </div>
      </div>
    );
  }

  const updateLocationPOIMetadata = (
    id: string,
    metadata: Record<string, string>,
    poi?: Array<string>,
  ) => {
    dispatch(
      updateLocationPOIMetadataToState({
        id,
        metadata: {
          ...metadata,
        },
        poi: poi?.length ? poi : [],
      }),
    );
    setAddPOI(false);
  };

  const deleteAllShapes = () => {
    dispatch(deleteAllShapesFromState());
  };

  return (
    <div className="h-full w-full flex gap-2 p-2">
      {/* Map Section */}
      <div className="flex-1 h-full relative">
        <MobilityHeatmapControl
          enabled={heatmapEnabled}
          onToggle={setHeatmapEnabled}
          timeBucket={heatmapBucket}
          onTimeBucketChange={setHeatmapBucket}
          isLoading={heatmapState.isLoading}
          isError={heatmapState.isError}
          isEmpty={heatmapState.isEmpty}
          className="top-16 right-2 left-auto"
        />
        <MapBoxWrapper
          onMapReady={setMapInstance}
          defaultCenter={mapDefaultCenter}
          defaultZoom={mapDefaultZoom}
          controlsConfig={GEO_FENCING_MAP_CONFIG}
          highlightInListOnClick={true}
          PopupComponent={GeoFencingLocationPopup}
          POIPopupComponent={GeoFencingPOIPopup}
          popupExtraProps={popupExtraProps}
          addPOI={addPOI}
          availablePOIs={availablePOIs}
          onLocationSelected={onLocationSelected}
          onCircleRadiusUpdate={onCircleRadiusUpdate}
          checkForDuplicate={true}
          onShapeDrawn={onShapeDrawn}
          locationsList={combinedItems}
          selectedItemId={markerLocations.selectedLocation?.id}
          updateLocationPOIMetadata={updateLocationPOIMetadata}
          deleteAllShapes={deleteAllShapes}
          selectedCountry={countryData?.iso}
        />
      </div>

      {/* Right Sidebar - Selected Locations */}
      <div className="w-80 bg-white border border-container-border overflow-hidden flex flex-col rounded-xs h-full relative">
        {/* Header */}
        <div className="p-2">
          <div className="text-m font-medium text-mw-black">
            {t("targeting.geofencing.selected_locations")}
          </div>
          <div className="text-xs text-mw-neutral-500 mt-1">
            {t("targeting.geofencing.add_locations_hint")}
          </div>
        </div>

        {/* Search Bar */}
        <div className="p-2 flex flex-col">
          <div className="relative">
            <Search className="absolute right-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-mw-neutral-400 z-10" />
            <Input
              id="search-saved-locations"
              type="text"
              placeholder={t("targeting.geofencing.search_placeholder")}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pr-9"
              autoFocus={searchQuery ? true : false}
            />
          </div>
        </div>
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="p-2 flex items-center justify-end">
            <Checkbox
              id="enable-all-checkbox"
              checked={allEnabled}
              onChange={(e) => handleToggleAll(e.target.checked)}
              label={t("targeting.geofencing.enable_all")}
              className="cursor-pointer"
            />
          </div>

          {/* Item Count and Enable All */}
          {filteredItems.length === 0 ? (
            <div className="p-8 text-center text-mw-neutral-500">
              <p className="text-sm">
                {t("targeting.geofencing.no_locations")}
              </p>
            </div>
          ) : (
            <div className="overflow-y-auto flex-1 scrollbar-thin shadow-sm">
              {filteredItems.map((item, index) => {
                const isSelected =
                  markerLocations.selectedLocation?.id === item.id;
                const isEnabled = item.included !== false;
                const itemName = item.name;
                // Cap the displayed name at 22 chars; full name stays in the tooltip.
                const itemNameDisplay =
                  itemName && itemName.length > 22
                    ? `${itemName.slice(0, 22)}…`
                    : itemName;
                const itemAddress = getItemAddress(item);

                return (
                  <div
                    key={`${item.itemType}-${item.id}`}
                    onClick={() => {
                      handleItemClick(item);
                      setAddPOI(false);
                    }}
                    className={`border border-mw-neutral-100 rounded-xs m-2 p-2 transition-all cursor-pointer shadow-sm ${
                      isSelected
                        ? "bg-mw-primary-50"
                        : "bg-white hover:bg-mw-neutral-30"
                    }`}
                  >
                    {/* Top row: checkbox + photo + name + type badge + delete */}
                    <div className="flex items-start gap-2">
                      <Checkbox
                        id={"saved-location-checkbox-" + (index + 1)}
                        checked={isEnabled}
                        onChange={(e) => {
                          e.stopPropagation();
                          handleToggleItem(item, e.target.checked);
                          setAddPOI(false);
                        }}
                        onClick={(e) => e.stopPropagation()}
                        className="cursor-pointer shrink-0 mt-0.5"
                      />

                      {/* Photo / Icon thumbnail */}
                      {(() => {
                        const rawType = item.metadata?.type ?? "";
                        const cc =
                          item.metadata?.countryCode || countryData?.iso;
                        const typeInfo = getPlaceTypeInfo(rawType, cc);
                        const TypeIcon = typeInfo.Icon;
                        const photoUrl = item.metadata?.photoUrl;
                        const isCircle = rawType === "circle";

                        return (
                          /* Icon is always the base layer; photo overlays on top.
                             If the URL is missing or fails, the icon remains visible. */
                          <div
                            className={`shrink-0 w-9 h-9 rounded-md overflow-hidden relative flex items-center justify-center ${typeInfo.bgClass}`}
                          >
                            {/* Base icon — always present */}
                            {isCircle ? (
                              <MapPin
                                className={`h-4 w-4 ${typeInfo.colorClass}`}
                              />
                            ) : (
                              <TypeIcon
                                className={`h-4 w-4 ${typeInfo.colorClass}`}
                              />
                            )}
                            {/* Photo overlay — hides itself on error */}
                            {!isCircle && photoUrl && (
                              <img
                                src={photoUrl}
                                alt={itemName}
                                className="absolute inset-0 w-full h-full object-cover"
                                onError={(e) => {
                                  (
                                    e.currentTarget as HTMLImageElement
                                  ).style.display = "none";
                                }}
                              />
                            )}
                          </div>
                        );
                      })()}

                      {/* Name + type badge */}
                      <div className="flex-1 min-w-0">
                        <Tooltip content={itemName}>
                          <div className="font-medium text-sm text-mw-neutral-900 truncate leading-tight">
                            {itemNameDisplay}
                          </div>
                        </Tooltip>
                        {item.itemType === "location" &&
                          item.metadata?.type !== "circle" &&
                          (item.metadata?.type || item.metadata?.typeLabel) &&
                          (() => {
                            const rawType = item.metadata?.type ?? "";
                            const cc2 =
                              item.metadata?.countryCode || countryData?.iso;
                            const typeInfo = getPlaceTypeInfo(
                              rawType || null,
                              cc2,
                            );
                            const TypeIcon = typeInfo.Icon;
                            const label =
                              item.metadata?.typeLabel ?? typeInfo.label;
                            return (
                              <span
                                className={`inline-flex items-center gap-0.5 text-xs font-medium mt-0.5 ${typeInfo.colorClass}`}
                              >
                                <TypeIcon className="h-3 w-3" />
                                {label}
                              </span>
                            );
                          })()}
                      </div>

                      {/* Delete Button */}
                      <Button
                        id={"delete-location-button-" + (index + 1)}
                        size="iconMd"
                        variant="ghost"
                        onClick={(e) => {
                          e.stopPropagation();
                          setAddPOI(false);
                          handleDeleteClick(item);
                        }}
                        className="p-0! h-4 w-4 text-red-500 hover:text-red-700 rounded transition-colors cursor-pointer shrink-0"
                        title={t("targeting.geofencing.delete_location")}
                      >
                        <Trash2 className="h-4 w-4 text-mw-error-500" />
                      </Button>
                    </div>

                    {/* Local region name with type prefix (e.g. "City · Kuala Lumpur") */}
                    {item.metadata?.localName &&
                      item.metadata.localName !== itemAddress &&
                      (() => {
                        const localNameType = item.metadata?.localNameType;
                        const fallbackPrefix = getLocalNameTypeLabel(
                          localNameType,
                          item.metadata?.countryCode || countryData?.iso,
                        );
                        const prefix = localNameType
                          ? tCommon(`map.localNameType.${localNameType}`) ||
                            fallbackPrefix
                          : fallbackPrefix;
                        return (
                          <div className="flex items-center gap-1 text-xs font-medium mt-1 ml-11 min-w-0">
                            {prefix && (
                              <span className="shrink-0 text-mw-neutral-400 font-normal">
                                {prefix} ·
                              </span>
                            )}
                            <span className="text-mw-primary-600 truncate">
                              {item.metadata.localName}
                            </span>
                          </div>
                        );
                      })()}

                    {/* Address */}
                    {itemAddress && (
                      <div className="text-xs text-secondary mt-0.5 ml-11 line-clamp-2">
                        {itemAddress}
                      </div>
                    )}

                    {/* POI Chips */}
                    {item.poi && item.poi.length > 0 && (
                      <div className="pois-wrapper mt-1.5 ml-11 flex flex-wrap gap-1.5">
                        {item.poi.map((poi, poiIndex) => {
                          const poiInfo = getPlaceTypeInfo(poi);
                          const PoiIcon = poiInfo.Icon;
                          return (
                            <Chip
                              key={"poi-" + poiIndex}
                              size="sm"
                              variant="outline"
                              className="rounded-xs"
                              closeClassNames="text-mw-neutral-300"
                              onRemove={() => {
                                handleRemovePoi(item, poi);
                              }}
                            >
                              <PoiIcon
                                className={`h-3 w-3 ${poiInfo.colorClass}`}
                              />
                              {getChipDisplayName(item, poi)}
                            </Chip>
                          );
                        })}
                      </div>
                    )}
                    <div className="flex items-center justify-start border-t-2 pt-2 border-container-border">
                      <div
                        className={clsx(
                          "flex-1 text-xs",
                          item.poi && item.poi.length >= MAX_POI_COUNT
                            ? "text-mw-error-300"
                            : "text-secondary",
                        )}
                      >
                        {item.poi && item.poi.length >= MAX_POI_COUNT
                          ? t("targeting.geofencing.poi.reachLimit")
                          : t("targeting.geofencing.poi.hint")}
                      </div>
                      <Button
                        variant="outline"
                        id={"add-poi-button-" + (index + 1)}
                        size="sm"
                        onClick={(e) => {
                          e.stopPropagation();
                          e.preventDefault();
                          handleItemClick(item);
                          setAddPOI(true);
                        }}
                        disabled={item.poi && item.poi.length >= MAX_POI_COUNT}
                        className="outline-mw-primary-500 text-mw-primary-500 cursor-pointer"
                      >
                        <Plus className="h-4 w-4 text-mw-primary-500" />
                        {t("targeting.geofencing.poi.buttonName")}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Import Locations Button */}
        <div className="p-2">
          <Button
            id="import-locations-button"
            variant="outline"
            onClick={() => setIsImportModalOpen(true)}
            className="w-full inline-flex gap-2 outline-mw-primary-500 text-mw-primary-500"
          >
            <FileDown className="h-4 w-4 text-mw-primary-500" />
            {t("targeting.geofencing.import_locations")}
          </Button>
        </div>
      </div>

      <LocationCsvUploadDrawer
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        countryName={campaignData?.countryId || ""}
        onUseFile={handleImportLocations}
      />
      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={handleCancelDelete}
        title={t("targeting.geofencing.removeLocation")}
        primaryButtonText={t("targeting.geofencing.yesRemove")}
        secondaryButtonText={t("targeting.geofencing.cancelRemove")}
        onPrimaryAction={handleDeleteItem}
        onSecondaryAction={handleCancelDelete}
        primaryButtonVariant="danger"
        size="sm"
      >
        <p>
          {t("targeting.geofencing.askRemove")} -{" "}
          <strong>"{itemToDelete?.name || "this item"}"</strong>?
        </p>
      </Modal>
    </div>
  );
};

export default GeoFencingForm;
