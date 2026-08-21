import { Switch } from "@components/ui/Switch";
import { Tooltip } from "@components/ui/Tooltip";
import { useGetMobilityHeatmapQuery } from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import clsx from "clsx";
import type { Map as MapboxMap } from "mapbox-gl";
import { useEffect, useMemo } from "react";
import type { MobilityTimeBucket } from "src/types/campaign.types";

/**
 * Audience-mobility heatmap for planning maps (geo-fencing + inventory views).
 *
 * `useMobilityHeatmapLayer` owns the Mapbox source/layer lifecycle: it fetches
 * server-aggregated footfall points for the plan's country, renders them as a
 * `heatmap` layer beneath symbol layers, re-attaches after map style switches,
 * and removes everything when toggled off. `MobilityHeatmapControl` is the
 * matching overlay UI (toggle, time-of-day filter, legend, empty state).
 */

/**
 * Country display name → countries-master slug (same slugification the
 * geo-fencing country lookup uses), e.g. "Sri Lanka" → "sri-lanka".
 */
export const toCountrySlug = (name?: string | null): string | undefined =>
  name ? name.toLowerCase().replace(/\s+/g, "-") : undefined;

const SOURCE_ID = "audience-mobility-heatmap";
const LAYER_ID = "audience-mobility-heatmap-layer";

export const MOBILITY_TIME_BUCKETS: MobilityTimeBucket[] = [
  "ALL",
  "MORNING",
  "AFTERNOON",
  "EVENING",
  "NIGHT",
];

// Cold (transparent blue) → hot (red) ramp shared by the layer and the legend.
const HEAT_RAMP = [
  { stop: 0, color: "rgba(33,102,172,0)" },
  { stop: 0.2, color: "rgb(103,169,207)" },
  { stop: 0.4, color: "rgb(209,229,240)" },
  { stop: 0.6, color: "rgb(253,219,199)" },
  { stop: 0.8, color: "rgb(239,138,98)" },
  { stop: 1, color: "rgb(178,24,43)" },
];

const LEGEND_GRADIENT = `linear-gradient(to right, ${HEAT_RAMP.slice(1)
  .map((r) => r.color)
  .join(", ")})`;

interface UseMobilityHeatmapLayerArgs {
  map: MapboxMap | null;
  /** Country slug from the countries master (e.g. "malaysia"). */
  countrySlug?: string;
  enabled: boolean;
  timeBucket: MobilityTimeBucket;
}

export const useMobilityHeatmapLayer = ({
  map,
  countrySlug,
  enabled,
  timeBucket,
}: UseMobilityHeatmapLayerArgs) => {
  const { data, isFetching, isError } = useGetMobilityHeatmapQuery(
    { countryId: countrySlug ?? "", timeBucket },
    { skip: !enabled || !countrySlug },
  );

  const points = useMemo(
    () => (enabled ? (data?.data?.points ?? []) : []),
    [data, enabled],
  );

  useEffect(() => {
    if (!map) return;

    const removeLayer = () => {
      try {
        if (map.getLayer(LAYER_ID)) map.removeLayer(LAYER_ID);
        if (map.getSource(SOURCE_ID)) map.removeSource(SOURCE_ID);
      } catch {
        // Map might be mid style-switch or already destroyed — nothing to clean.
      }
    };

    if (!enabled || points.length === 0) {
      removeLayer();
      return;
    }

    const geojson: GeoJSON.FeatureCollection = {
      type: "FeatureCollection",
      features: points.map((p) => ({
        type: "Feature",
        geometry: { type: "Point", coordinates: [p.lng, p.lat] },
        properties: { weight: p.weight },
      })),
    };

    const addLayer = () => {
      try {
        const existing = map.getSource(SOURCE_ID) as
          | mapboxgl.GeoJSONSource
          | undefined;
        if (existing) {
          existing.setData(geojson);
        } else {
          map.addSource(SOURCE_ID, { type: "geojson", data: geojson });
        }
        if (!map.getLayer(LAYER_ID)) {
          // Keep the heatmap under labels/pins: insert before the first
          // symbol layer if one exists.
          const firstSymbolLayer = map
            .getStyle()
            ?.layers?.find((l) => l.type === "symbol")?.id;
          map.addLayer(
            {
              id: LAYER_ID,
              type: "heatmap",
              source: SOURCE_ID,
              paint: {
                "heatmap-weight": ["get", "weight"],
                "heatmap-intensity": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  4,
                  0.6,
                  9,
                  1.2,
                  14,
                  2,
                ],
                "heatmap-radius": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  4,
                  8,
                  9,
                  22,
                  14,
                  40,
                ],
                "heatmap-color": [
                  "interpolate",
                  ["linear"],
                  ["heatmap-density"],
                  ...HEAT_RAMP.flatMap((r) => [r.stop, r.color]),
                ],
                "heatmap-opacity": [
                  "interpolate",
                  ["linear"],
                  ["zoom"],
                  4,
                  0.8,
                  15,
                  0.55,
                ],
              },
            },
            firstSymbolLayer,
          );
        }
      } catch (error) {
        console.error("Failed to add mobility heatmap layer:", error);
      }
    };

    if (map.isStyleLoaded()) {
      addLayer();
    } else {
      map.once("style.load", addLayer);
    }

    // Style switches (streets → satellite, …) drop all custom layers; re-add.
    const handleStyleLoad = () => addLayer();
    map.on("style.load", handleStyleLoad);

    return () => {
      map.off("style.load", handleStyleLoad);
      removeLayer();
    };
  }, [map, enabled, points]);

  return {
    isLoading: isFetching,
    isError,
    pointCount: points.length,
    isEmpty: enabled && !isFetching && !isError && points.length === 0,
  };
};

interface MobilityHeatmapControlProps {
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
  timeBucket: MobilityTimeBucket;
  onTimeBucketChange: (bucket: MobilityTimeBucket) => void;
  isLoading: boolean;
  isError: boolean;
  isEmpty: boolean;
  className?: string;
}

/**
 * Overlay card for the mobility heatmap: on/off switch, time-of-day filter,
 * intensity legend, and a clear empty state when the area has no data.
 */
export const MobilityHeatmapControl: React.FC<MobilityHeatmapControlProps> = ({
  enabled,
  onToggle,
  timeBucket,
  onTimeBucketChange,
  isLoading,
  isError,
  isEmpty,
  className,
}) => {
  const { t } = useTranslate("campaigns");

  return (
    <div
      className={clsx(
        "absolute top-2 left-2 z-10 rounded-md bg-white/95 shadow-md border border-mw-grey-200 p-2 w-56",
        className,
      )}
      data-testid="mobility-heatmap-control"
    >
      <div className="flex items-center justify-between gap-2">
        <Tooltip content={t("mobilityHeatmap.tooltip")}>
          <span className="text-xs font-medium text-mw-grey-900">
            {t("mobilityHeatmap.title")}
          </span>
        </Tooltip>
        <Switch
          id="mobility-heatmap-toggle"
          size="sm"
          checked={enabled}
          onChange={onToggle}
        />
      </div>

      {enabled && (
        <div className="mt-2 space-y-2">
          {/* Time-of-day filter */}
          <div
            className="flex flex-wrap gap-1"
            role="group"
            aria-label={t("mobilityHeatmap.timeOfDay")}
          >
            {MOBILITY_TIME_BUCKETS.map((bucket) => (
              <button
                key={bucket}
                type="button"
                onClick={() => onTimeBucketChange(bucket)}
                className={clsx(
                  "px-1.5 py-0.5 rounded text-[10px] font-medium border transition-colors",
                  timeBucket === bucket
                    ? "bg-mw-primary-600 text-white border-mw-primary-600"
                    : "bg-white text-mw-grey-600 border-mw-grey-200 hover:border-mw-primary-400",
                )}
              >
                {t(`mobilityHeatmap.buckets.${bucket.toLowerCase()}`)}
              </button>
            ))}
          </div>

          {/* Legend */}
          <div>
            <div
              className="h-1.5 rounded-full"
              style={{ background: LEGEND_GRADIENT }}
            />
            <div className="flex justify-between text-[10px] text-mw-grey-500 mt-0.5">
              <span>{t("mobilityHeatmap.low")}</span>
              <span>{t("mobilityHeatmap.high")}</span>
            </div>
          </div>

          {/* States */}
          {isLoading && (
            <p className="text-[10px] text-mw-grey-500">
              {t("mobilityHeatmap.loading")}
            </p>
          )}
          {isError && (
            <p className="text-[10px] text-red-600">
              {t("mobilityHeatmap.error")}
            </p>
          )}
          {isEmpty && (
            <p
              className="text-[10px] text-mw-grey-500"
              data-testid="mobility-heatmap-empty"
            >
              {t("mobilityHeatmap.noData")}
            </p>
          )}
        </div>
      )}
    </div>
  );
};
