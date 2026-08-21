import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { formatCompactNumber } from "@utils/dashboard.utils";
import {
  Clock,
  DollarSign,
  Gauge,
  Globe,
  Layers,
  MapPin,
  Megaphone,
  PlayCircle,
  Repeat,
  Tag,
  Users,
} from "lucide-react";
import React from "react";
import { CostSplitByCampaignData } from "src/types/campaign.types";
import { CampaignForecastData } from "src/types/inventory.types";

import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo } from "./types";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface GeographySummary {
  cityCount: number;
  countryCount: number;
  poiCount: number;
}

interface MediaPlanPerformanceMetricsProps {
  performanceMetrics?: CampaignForecastData | null;
  headerInfo?: MediaPlanHeaderInfo;
  geographySummary?: GeographySummary;
  channelCount?: number;
  /** Campaign goal — SOV/ADPLAYS relabel Avg CPM → Avg CPS (value unchanged). */
  goalType?: string;
  /** Inventory cost split — summed for the Total Cost card, same as Inventory Mix. */
  costSplitData?: CostSplitByCampaignData[];
  theme?: PresentationTheme;
}

const MS_PER_DAY = 1000 * 60 * 60 * 24;

const MediaPlanPerformanceMetricsComponent: React.FC<
  MediaPlanPerformanceMetricsProps
> = ({
  performanceMetrics,
  headerInfo,
  geographySummary,
  channelCount = 0,
  goalType,
  costSplitData = [],
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);

  const currency = headerInfo?.currency;
  // SOV / AD_PLAYS goals are priced per spot → relabel the CPM card as CPS
  // (same backend value, just the label per the manual-edit convention).
  const normalizedGoal = normalizeGoalType(goalType);
  const isCPSGoal = normalizedGoal === "SOV" || normalizedGoal === "ADPLAYS";
  const cpmKey = isCPSGoal ? "avg_cps" : "avg_cpm";
  const geo = geographySummary ?? {
    cityCount: 0,
    countryCount: 0,
    poiCount: 0,
  };

  // Duration in whole days (inclusive), for the footer note.
  const durationDays =
    headerInfo?.startDate && headerInfo?.endDate
      ? Math.floor(
          (new Date(headerInfo.endDate).getTime() -
            new Date(headerInfo.startDate).getTime()) /
            MS_PER_DAY,
        ) + 1
      : 0;

  // Share of time as a percentage of the plan's total available air-time.
  const sotPercent =
    performanceMetrics?.plannedSot && performanceMetrics?.totalSot
      ? (performanceMetrics.plannedSot / performanceMetrics.totalSot) * 100
      : 0;

  const inventoryCount = performanceMetrics?.totalInventories || 0;
  // Total cost = sum across the inventory cost split, same as Inventory Mix.
  const totalCost = costSplitData.reduce(
    (sum, r) => sum + (r.totalAmount || 0),
    0,
  );

  // Ad plays only apply to digital (slot-based) inventory — classic OOH is
  // priced/booked daily with no play count. Hide the card for classic-only
  // campaigns; show it if the split is empty (not yet loaded) or mixed.
  const hasDigitalInventory =
    costSplitData.length === 0 ||
    costSplitData.some((r) => r.name?.toLowerCase().includes("digital"));

  const metricCards = [
    {
      key: "geography",
      icon: Globe,
      label: t("media_plan.performance_metrics.geography"),
      value: geo.cityCount.toLocaleString(),
      unit: t("media_plan.performance_metrics.geography_unit"),
      subtitle: t("media_plan.performance_metrics.geography_sub", {
        countries: geo.countryCount,
        pois: geo.poiCount,
      }),
    },
    {
      key: "inventories",
      icon: MapPin,
      label: t("media_plan.performance_metrics.inventories"),
      value: inventoryCount.toLocaleString(),
      unit: t("media_plan.performance_metrics.inventories_unit"),
      subtitle: t("media_plan.performance_metrics.inventories_sub"),
    },
    {
      key: "channels",
      icon: Layers,
      label: t("media_plan.performance_metrics.channels"),
      value: channelCount.toLocaleString(),
      unit: t("media_plan.performance_metrics.channels_unit"),
      subtitle: t("media_plan.performance_metrics.channels_sub"),
    },
    {
      key: "total-impressions",
      icon: Gauge,
      label: t("media_plan.performance_metrics.total_impressions"),
      value: formatCompactNumber(performanceMetrics?.estimatedImpression || 0),
      unit: t("media_plan.performance_metrics.total_impressions_unit"),
      subtitle: t("media_plan.performance_metrics.total_impressions_sub"),
    },
    {
      key: "estimated-reach",
      icon: Users,
      label: t("media_plan.performance_metrics.estimated_reach"),
      value: formatCompactNumber(performanceMetrics?.estimatedReach || 0),
      unit: t("media_plan.performance_metrics.estimated_reach_unit"),
      subtitle: t("media_plan.performance_metrics.estimated_reach_sub"),
    },
    {
      key: "avg-frequency",
      icon: Repeat,
      label: t("media_plan.performance_metrics.avg_frequency"),
      value: `${(performanceMetrics?.estimatedFrequency || 0).toFixed(1)}×`,
      unit: t("media_plan.performance_metrics.avg_frequency_unit"),
      subtitle: t("media_plan.performance_metrics.avg_frequency_sub"),
    },
    {
      key: "avg-cpm",
      icon: Tag,
      label: t(`media_plan.performance_metrics.${cpmKey}`),
      value: formatCurrency(performanceMetrics?.avgCpm || 0, currency),
      unit: t(`media_plan.performance_metrics.${cpmKey}_unit`),
      subtitle: t(`media_plan.performance_metrics.${cpmKey}_sub`),
    },
    {
      key: "ecpm",
      icon: Tag,
      label: t("media_plan.performance_metrics.ecpm"),
      value: formatCurrency(performanceMetrics?.avgECpm || 0, currency),
      unit: t("media_plan.performance_metrics.ecpm_unit"),
      subtitle: t("media_plan.performance_metrics.ecpm_sub"),
    },
    {
      key: "sov",
      icon: Megaphone,
      label: t("media_plan.performance_metrics.sov"),
      value: `${(performanceMetrics?.sov || 0).toFixed(2)}%`,
      unit: t("media_plan.performance_metrics.sov_unit"),
      subtitle: t("media_plan.performance_metrics.sov_sub"),
    },
    {
      key: "sot",
      icon: Clock,
      label: t("media_plan.performance_metrics.sot"),
      value: `${sotPercent.toFixed(0)}%`,
      unit: t("media_plan.performance_metrics.sot_unit"),
      subtitle: t("media_plan.performance_metrics.sot_sub"),
    },
    {
      key: "total-cost",
      icon: DollarSign,
      label: t("media_plan.performance_metrics.total_cost"),
      value: formatCurrency(totalCost, currency),
      unit: t("media_plan.performance_metrics.total_cost_unit"),
      subtitle: t("media_plan.performance_metrics.total_cost_sub"),
    },
    ...(hasDigitalInventory
      ? [
          {
            key: "ad-plays",
            icon: PlayCircle,
            label: t("media_plan.performance_metrics.ad_plays"),
            value: formatCompactNumber(
              performanceMetrics?.estimatedAdPlays || 0,
            ),
            unit: t("media_plan.performance_metrics.ad_plays_unit"),
            subtitle: t("media_plan.performance_metrics.ad_plays_sub"),
          },
        ]
      : []),
  ];

  return (
    <Card
      id="media-plan-performance-metrics-card"
      className="mt-4 overflow-hidden p-0"
    >
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-performance-metrics-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-performance-metrics-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.performance_metrics.title")}
        </h2>
        <p
          id="media-plan-performance-metrics-subtitle"
          className="text-sm text-white/80"
        >
          {t("media_plan.performance_metrics.subtitle")}
        </p>
      </div>

      <CardContent
        id="media-plan-performance-metrics-content"
        className="mt-4 p-6"
      >
        <div
          id="media-plan-performance-metrics-grid"
          className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
        >
          {metricCards.map((card) => {
            const IconComponent = card.icon;
            return (
              <div
                key={card.key}
                id={`media-plan-performance-metric-${card.key}`}
                className="flex min-h-[160px] flex-col gap-4 rounded-lg border border-container-border bg-[rgba(37,99,235,0.03)] p-5"
              >
                <div className="flex items-center gap-2 text-xs font-medium tracking-wider text-mw-neutral-500">
                  <IconComponent className="h-4 w-4" />
                  {card.label}
                </div>
                <div className="flex items-baseline gap-1.5">
                  <span
                    id={`media-plan-performance-metric-${card.key}-value`}
                    className="text-2xl font-bold leading-8 text-mw-neutral-900"
                  >
                    {card.value}
                  </span>
                  {card.unit && (
                    <span className="text-sm text-mw-neutral-500">
                      {card.unit}
                    </span>
                  )}
                </div>
                <div className="mt-auto text-xs text-mw-neutral-400">
                  {card.subtitle}
                </div>
              </div>
            );
          })}
        </div>

        <p
          id="media-plan-performance-metrics-note-primary"
          className="mt-6 text-xs leading-5 text-mw-neutral-400"
        >
          {t("media_plan.performance_metrics.note_pricing")}
        </p>
        <p
          id="media-plan-performance-metrics-note-secondary"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.performance_metrics.note_derivation", {
            count: inventoryCount,
            days: durationDays,
          })}
        </p>
      </CardContent>
    </Card>
  );
};

export default MediaPlanPerformanceMetricsComponent;
