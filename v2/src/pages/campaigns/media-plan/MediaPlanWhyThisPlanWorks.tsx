import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React from "react";
import { CostSplitByCampaignData, Targeting } from "src/types/campaign.types";
import { CampaignForecastData } from "src/types/inventory.types";

import { SelectedInventory } from "./types";
import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo } from "./types";
import {
  buildPlanReasons,
  computeGoalRoadmap,
  getThemePrimaryBackgroundStyle,
  humaniseSegment,
  resolveGoalForecast,
} from "./utils";

interface MediaPlanWhyThisPlanWorksProps {
  forecastData?: CampaignForecastData | null;
  costSplitByCity?: CostSplitByCampaignData[];
  channelCount?: number;
  selectedInventory?: SelectedInventory;
  headerInfo?: MediaPlanHeaderInfo;
  goalType?: string;
  targetValue?: number;
  targeting?: Targeting;
  theme?: PresentationTheme;
}

/** Ordered demographic segments (max 2) for reason 3 — age first, then gender,
 * income, behaviour, interests. */
const demographicSegmentsFrom = (targeting?: Targeting): string[] => {
  const d = targeting?.demographics;
  if (!d) return [];
  return [
    ...(d.age || []),
    ...(d.gender || []),
    ...(d.income || []),
    ...(d.behavior || []),
    ...(d.interests || []),
  ]
    .map(humaniseSegment)
    .slice(0, 2);
};

/** Distinct venue-type families (max 2) for the reason-3 fallback. */
const venueTypesFrom = (targeting?: Targeting): string[] =>
  Array.from(
    new Set(
      [
        ...(targeting?.venueTypes?.digitalOoh || []),
        ...(targeting?.venueTypes?.classicOoh || []),
      ].map((c) => c.split("-")[0]),
    ),
  )
    .map(humaniseSegment)
    .slice(0, 2);

const MediaPlanWhyThisPlanWorksComponent: React.FC<
  MediaPlanWhyThisPlanWorksProps
> = ({
  forecastData,
  costSplitByCity = [],
  selectedInventory,
  headerInfo,
  goalType,
  targetValue = 0,
  targeting,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);
  const currency = headerInfo?.currency;

  const inventories = forecastData?.totalInventories || 0;
  const impressions = forecastData?.estimatedImpression || 0;
  const cpm = forecastData?.avgCpm || 0;

  const topCity = costSplitByCity.reduce<CostSplitByCampaignData | null>(
    (top, c) =>
      !top || (c.impressions || 0) > (top.impressions || 0) ? c : top,
    null,
  );
  const topCityName = topCity?.name || t("media_plan.why_plan.fallback_market");

  const reasons = buildPlanReasons({
    topCityName,
    inventories,
    impressions,
    cpm,
    demographicSegments: demographicSegmentsFrom(targeting),
    venueTypes: venueTypesFrom(targeting),
    compact: formatCompactNumber,
    currency: (n) => formatCurrency(n, currency),
  });

  // Three-milestone goal roadmap (Ramp / Mid-flight / Closeout) with S-curve
  // achievement %. Forecast = the plan's forecast for the campaign goal metric;
  // target defaults to forecast when no goal is set. PRD §10.5.7.
  const forecast = resolveGoalForecast(goalType, forecastData);
  const roadmap = computeGoalRoadmap(
    headerInfo?.startDate,
    headerInfo?.endDate,
    forecast,
    targetValue,
  );
  const milestones = roadmap.map((m) => ({
    label: `${t(`media_plan.why_plan.unit_${m.unit}`)} ${m.ordinal}`,
    dateRange: m.dateRange,
    phase: t(`media_plan.why_plan.phase_${m.phase}`),
    pct: m.pct,
  }));
  // Granularity note shown at the top-right of the roadmap section.
  const trackNote = roadmap.length
    ? t(`media_plan.why_plan.track_note_${roadmap[0].unit}`)
    : "";

  const inventoryList = selectedInventory?.locations || [];
  const MAX_INVENTORY_LABEL_LENGTH = 30;
  const MAX_VISIBLE_INVENTORIES = 7;
  const visibleInventoryList = inventoryList.slice(0, MAX_VISIBLE_INVENTORIES);
  const hiddenInventoryCount = Math.max(
    inventoryList.length - MAX_VISIBLE_INVENTORIES,
    0,
  );
  const formatInventoryLabel = (
    item: NonNullable<SelectedInventory["locations"]>[number],
  ) => {
    const name = item.detail?.name || "";
    const city = item.location?.location?.city || "";
    const label = city ? `${name} — ${city}` : name;
    return label.length > MAX_INVENTORY_LABEL_LENGTH
      ? `${label.slice(0, MAX_INVENTORY_LABEL_LENGTH - 3)}...`
      : label;
  };

  return (
    <Card id="media-plan-why-plan-card" className="mt-4 overflow-hidden p-0">
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-why-plan-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-why-plan-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.why_plan.title")}
        </h2>
        <p id="media-plan-why-plan-subtitle" className="text-sm text-white/80">
          {t("media_plan.why_plan.subtitle")}
        </p>
      </div>

      <CardContent id="media-plan-why-plan-content" className="mt-4 p-6">
        {/* Reason cards */}
        <div
          id="media-plan-why-plan-reasons"
          className="grid grid-cols-1 gap-4 md:grid-cols-3"
        >
          {reasons.map((reason, index) => (
            <div
              key={reason.key}
              className="rounded-lg border border-container-border p-4"
            >
              <div
                className="mb-2 flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold text-white"
                style={getThemePrimaryBackgroundStyle(theme)}
              >
                {index + 1}
              </div>
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.why_plan.reason", { number: index + 1 })}
              </p>
              <p className="mt-1 text-sm text-mw-neutral-700">
                {t(`media_plan.why_plan.${reason.key}`, reason.params)}
              </p>
            </div>
          ))}
        </div>

        {/* Inventory + milestone roadmap */}
        <p className="mt-6 text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
          {t("media_plan.why_plan.inventory_in_plan")}
        </p>
        {inventoryList.length > 0 && (
          <div
            id="media-plan-why-plan-inventory"
            className="mt-2 flex flex-wrap gap-2"
          >
            {visibleInventoryList.map((item) => (
              <span
                key={item.detail?.referenceId || item.detail?.name}
                title={item.detail?.name || ""}
                className="rounded-full border border-container-border bg-mw-neutral-50 px-2.5 py-0.5 text-xs text-mw-neutral-700"
              >
                {formatInventoryLabel(item)}
              </span>
            ))}
            {hiddenInventoryCount > 0 && (
              <span className="rounded-full border border-container-border bg-mw-neutral-50 px-2.5 py-0.5 text-xs font-medium text-mw-neutral-700">
                +{hiddenInventoryCount}
              </span>
            )}
          </div>
        )}

        <div className="mt-6 flex items-baseline justify-between gap-4">
          <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
            {t("media_plan.why_plan.expected_goal_achievement")}
          </p>
          {trackNote && (
            <p className="text-xs italic text-mw-neutral-400">{trackNote}</p>
          )}
        </div>
        <div
          id="media-plan-why-plan-milestones"
          className="mt-3 flex flex-col gap-4"
        >
          {milestones.map((m) => (
            <div key={m.phase}>
              {/* Label row: {Unit} {n}  {date range} · {Phase} ........ {pct}% */}
              <div className="flex items-baseline justify-between gap-2 text-sm">
                <span className="truncate">
                  <span className="font-semibold text-mw-neutral-900">
                    {m.label}
                  </span>
                  <span className="text-mw-neutral-500">
                    {" "}
                    {m.dateRange} · {m.phase}
                  </span>
                </span>
                <span className="shrink-0 font-semibold text-mw-neutral-900">
                  {m.pct}%
                </span>
              </div>
              <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-mw-neutral-100">
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${Math.min(m.pct, 100)}%`,
                    ...getThemePrimaryBackgroundStyle(theme),
                  }}
                />
              </div>
            </div>
          ))}
        </div>

        <p
          id="media-plan-why-plan-note"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.why_plan.note")}
        </p>
      </CardContent>
    </Card>
  );
};

export default MediaPlanWhyThisPlanWorksComponent;
