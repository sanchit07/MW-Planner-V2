import { Button } from "@components/ui/Button";
import { Card, CardContent } from "@components/ui/card";
import MapBoxWrapper, { MapControlsConfig } from "@components/ui/Mapbox";
import { useTranslate } from "@tolgee/react";
import { SquareArrowOutUpRight } from "lucide-react";
import type { Map as MapboxMap } from "mapbox-gl";
import React from "react";
import { POIPlaceData } from "src/types/campaign.types";
import { MapInventoryItem } from "src/types/inventory.types";

import type { MediaPlanMapView } from "./mapView.utils";
import { PresentationTheme } from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";
import GeoFencingPOIPopup from "../geofencing/GeoFencingPOIPopup";
import InventoryMapPopup from "../inventory/InventoryMapPopup";

interface AudienceMapSummary {
  sitesPinned: number;
  marketCount: number;
  totalInventory: number;
  densestMarket: string;
}

interface MediaPlanAudienceMapProps {
  mapView: MediaPlanMapView;
  mapConfig: MapControlsConfig;
  locations: MapInventoryItem[];
  availablePOIs?: POIPlaceData[];
  summary: AudienceMapSummary;
  onMapReady: (map: MapboxMap) => void;
  onInteractiveClick: () => void;
  isInteractiveLoading?: boolean;
  theme?: PresentationTheme;
}

const MediaPlanAudienceMapComponent: React.FC<MediaPlanAudienceMapProps> = ({
  mapView,
  mapConfig,
  locations,
  availablePOIs,
  summary,
  onMapReady,
  onInteractiveClick,
  isInteractiveLoading = false,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);

  const boxes = [
    {
      key: "sites",
      label: t("media_plan.audience_map.sites_pinned"),
      value: summary.sitesPinned.toLocaleString(),
    },
    {
      key: "inventory",
      label: t("media_plan.audience_map.total_inventory"),
      value: summary.totalInventory.toLocaleString(),
    },
    {
      key: "densest",
      label: t("media_plan.audience_map.densest_market"),
      value:
        summary.densestMarket || t("media_plan.audience_map.not_available"),
    },
  ];

  return (
    <Card
      id="media-plan-audience-map-card"
      className="mt-4 overflow-hidden p-0"
    >
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-audience-map-header"
        className="flex items-start justify-between gap-4 px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <div>
          <h2
            id="media-plan-audience-map-title"
            className="text-2xl font-bold leading-8"
          >
            {t("media_plan.audience_map.title")}
          </h2>
          <p
            id="media-plan-audience-map-subtitle"
            className="text-sm text-white/80"
          >
            {t("media_plan.audience_map.subtitle", {
              sites: summary.sitesPinned,
              markets: summary.marketCount,
              market: summary.densestMarket,
            })}
          </p>
        </div>
        <Button
          id="media-plan-audience-map-button"
          onClick={onInteractiveClick}
          size="sm"
          disabled={isInteractiveLoading}
          className="shrink-0 bg-white !text-mw-neutral-900 hover:bg-white/90"
        >
          <SquareArrowOutUpRight className="mr-2 h-4 w-4" />
          {isInteractiveLoading
            ? t("media_plan.map_view.loading")
            : t("media_plan.map_view.click_here")}
        </Button>
      </div>

      {/* Map (full-bleed). Neutralise the selected-marker blue glow here so
          pins read as plain markers on the presentation map. */}
      <style>
        {`#media-plan-audience-map-canvas .marker-wrapper > div { filter: drop-shadow(0 2px 3px rgba(0,0,0,0.35)) !important; }`}
      </style>
      <div id="media-plan-audience-map-canvas" className="h-[600px] w-full">
        <MapBoxWrapper
          defaultCenter={mapView.center}
          defaultZoom={mapView.zoom}
          defaultMapStyleUrl="mapbox://styles/mapbox/light-v11"
          controlsConfig={mapConfig}
          locationsList={locations}
          availablePOIs={availablePOIs}
          PopupComponent={InventoryMapPopup}
          POIPopupComponent={GeoFencingPOIPopup}
          onMapReady={onMapReady}
        />
      </div>

      <CardContent id="media-plan-audience-map-content" className="mt-6 p-6">
        <div
          id="media-plan-audience-map-summary"
          className="grid grid-cols-1 gap-4 sm:grid-cols-3"
        >
          {boxes.map((box) => (
            <div
              key={box.key}
              className="rounded-lg border border-container-border bg-transparent px-4 py-3 text-center"
            >
              <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
                {box.label}
              </p>
              <p className="text-lg font-semibold text-mw-neutral-900">
                {box.value}
              </p>
            </div>
          ))}
        </div>

        <p
          id="media-plan-audience-map-note"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.audience_map.note")}
        </p>
      </CardContent>
    </Card>
  );
};

export default MediaPlanAudienceMapComponent;
