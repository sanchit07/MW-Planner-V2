import {
  MobilityHeatmapControl,
  useMobilityHeatmapLayer,
} from "@components/map/MobilityHeatmap";
import MapBoxWrapper from "@components/ui/Mapbox";
import { getLatitude, getLongitude } from "@utils/inventory.utils";
import type { Map as MapboxMap } from "mapbox-gl";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type {
  MobilityTimeBucket,
  POIPlaceData,
} from "src/types/campaign.types";

import InventoryMapPopup from "./InventoryMapPopup";
import { applyMapLights, readOnlyMapConfig } from "./mapConfig";
import type { InventoryItem } from "../../../types/inventory.types";
import GeoFencingPOIPopup from "../geofencing/GeoFencingPOIPopup";

interface InventoryMapPanelProps {
  items: InventoryItem[];
  /** Highlighted inventory id — the map flies to it. */
  selectedItemId?: string;
  availablePOIs?: POIPlaceData[];
  /** Fired when a pin is clicked (id matches selectedItemId). */
  onMarkerClick?: (id: string) => void;
  /** When set, the map flies to these coords (read-only jump-to dropdowns). */
  flyTo?: { lng: number; lat: number } | null;
  /**
   * Country slug (countries master) enabling the audience mobility heatmap
   * toggle. Omit to hide the heatmap control entirely.
   */
  mobilityCountrySlug?: string;
}

/**
 * Mapbox panel for an inventory list: pins for every item, geofencing POIs,
 * fit-to-bounds on load / pin change, fly-to + popup on selected item, lights,
 * and the 3D toggle. Shared by the View and Manual-edit screens.
 */
const InventoryMapPanel: React.FC<InventoryMapPanelProps> = ({
  items,
  selectedItemId,
  availablePOIs,
  onMarkerClick,
  flyTo,
  mobilityCountrySlug,
}) => {
  const mapRef = useRef<MapboxMap | null>(null);
  // State copy of the map instance so the heatmap hook re-runs once ready.
  const [mapInstance, setMapInstance] = useState<MapboxMap | null>(null);
  const [heatmapEnabled, setHeatmapEnabled] = useState(false);
  const [heatmapBucket, setHeatmapBucket] = useState<MobilityTimeBucket>("ALL");
  const heatmapState = useMobilityHeatmapLayer({
    map: mapInstance,
    countrySlug: mobilityCountrySlug,
    enabled: heatmapEnabled,
    timeBucket: heatmapBucket,
  });

  const fitToPins = useCallback((map: MapboxMap, list: InventoryItem[]) => {
    const coords = list
      .map(
        (i) =>
          [
            getLongitude(i.location.location),
            getLatitude(i.location.location),
          ] as [number | undefined, number | undefined],
      )
      .filter(
        (c): c is [number, number] =>
          typeof c[0] === "number" && typeof c[1] === "number",
      );
    if (coords.length === 0) return;

    let minLng = coords[0][0];
    let maxLng = coords[0][0];
    let minLat = coords[0][1];
    let maxLat = coords[0][1];
    coords.forEach(([lng, lat]) => {
      minLng = Math.min(minLng, lng);
      maxLng = Math.max(maxLng, lng);
      minLat = Math.min(minLat, lat);
      maxLat = Math.max(maxLat, lat);
    });

    try {
      map.fitBounds(
        [
          [minLng, minLat],
          [maxLng, maxLat],
        ],
        { padding: 60, maxZoom: 15, duration: 500 },
      );
    } catch (error) {
      console.error("Failed to fit map to pins:", error);
    }
  }, []);

  const handleMapReady = useCallback(
    (map: MapboxMap) => {
      mapRef.current = map;
      setMapInstance(map);
      applyMapLights(map);
      fitToPins(map, items);
    },
    [fitToPins, items],
  );

  // Re-fit whenever the pins change (initial load + pagination).
  useEffect(() => {
    if (mapRef.current) fitToPins(mapRef.current, items);
  }, [items, fitToPins]);

  // Fly to a coord chosen from the read-only Locations / POIs dropdowns.
  useEffect(() => {
    if (mapRef.current && flyTo) {
      mapRef.current.flyTo({
        center: [flyTo.lng, flyTo.lat],
        zoom: 15,
        duration: 1200,
      });
    }
  }, [flyTo]);

  const mapCenter = useMemo((): [number, number] => {
    if (items.length === 0) return [0, 0];
    const avgLng =
      items.reduce((s, i) => s + (getLongitude(i.location.location) || 0), 0) /
      items.length;
    const avgLat =
      items.reduce((s, i) => s + (getLatitude(i.location.location) || 0), 0) /
      items.length;
    return [avgLng, avgLat];
  }, [items]);

  if (items.length === 0) return null;

  return (
    <div className="relative h-full w-full">
      {mobilityCountrySlug && (
        <MobilityHeatmapControl
          enabled={heatmapEnabled}
          onToggle={setHeatmapEnabled}
          timeBucket={heatmapBucket}
          onTimeBucketChange={setHeatmapBucket}
          isLoading={heatmapState.isLoading}
          isError={heatmapState.isError}
          isEmpty={heatmapState.isEmpty}
        />
      )}
      <MapBoxWrapper
        defaultCenter={mapCenter}
        defaultZoom={12}
        controlsConfig={readOnlyMapConfig}
        locationsList={items}
        selectedItemId={selectedItemId}
        PopupComponent={InventoryMapPopup}
        POIPopupComponent={GeoFencingPOIPopup}
        availablePOIs={availablePOIs}
        onMapReady={handleMapReady}
        onMarkerClick={onMarkerClick}
      />
    </div>
  );
};

export default InventoryMapPanel;
