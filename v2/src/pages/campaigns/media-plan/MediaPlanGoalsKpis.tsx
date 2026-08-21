import { Card, CardContent } from "@components/ui/card";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { formatCompactNumber } from "@utils/dashboard.utils";
import { Target, TrendingUp } from "lucide-react";
import React from "react";
import { CampaignForecastData } from "src/types/inventory.types";

import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo } from "./types";
import { computeExpectedDelivery } from "./utils";
import { getThemePrimaryBackgroundStyle } from "./utils";

interface MediaPlanGoalsKpisProps {
  goalType?: string;
  targetValue?: number;
  performanceMetrics?: CampaignForecastData | null;
  headerInfo?: MediaPlanHeaderInfo;
  theme?: PresentationTheme;
}

const PEAK_COLOR = "#06b6d4"; // cyan — highlights the peak bin
const OVER_TARGET_COLOR = "#22c55e"; // green — forecast exceeding target

const goalKeyFor = (goalType?: string): string => {
  switch ((goalType || "").toUpperCase()) {
    case "IMPRESSIONS":
      return "impressions";
    case "ADPLAYS":
      return "adplays";
    case "SOV":
      return "sov";
    default:
      return "reach";
  }
};

const MediaPlanGoalsKpisComponent: React.FC<MediaPlanGoalsKpisProps> = ({
  goalType,
  targetValue = 0,
  performanceMetrics,
  headerInfo,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);

  const goalKey = goalKeyFor(goalType);
  const isSov = goalKey === "sov";

  const forecastValue =
    goalKey === "impressions"
      ? performanceMetrics?.estimatedImpression || 0
      : goalKey === "adplays"
        ? performanceMetrics?.estimatedAdPlays || 0
        : goalKey === "sov"
          ? performanceMetrics?.sov || 0
          : performanceMetrics?.estimatedReach || 0;

  const pct =
    targetValue > 0 ? Math.round((forecastValue / targetValue) * 100) : 0;
  // Bar track is fixed-width, so overshoot is shown by rescaling both
  // segments to whichever is larger — pct itself stays uncapped for display.
  const barScale = Math.max(pct, 100);
  const bluePct = (Math.min(pct, 100) / barScale) * 100;
  const greenPct = pct > 100 ? ((pct - 100) / barScale) * 100 : 0;

  const fmt = (v: number) =>
    isSov ? `${Math.round(v)}%` : Math.round(v).toLocaleString();

  const totalImpressions = performanceMetrics?.estimatedImpression || 0;
  const totalReach = performanceMetrics?.estimatedReach || 0;
  const delivery = computeExpectedDelivery(
    headerInfo?.startDate,
    headerInfo?.endDate,
    totalImpressions,
    totalReach,
  );
  const maxBin = Math.max(...delivery.bins.map((b) => b.value), 1);
  const granularityLabel = t(
    `media_plan.goals_kpis.granularity_${delivery.granularity}`,
  );

  const targetNoun = t(`media_plan.goals_kpis.${goalKey}.target_noun`);
  const forecastNoun = t(`media_plan.goals_kpis.${goalKey}.forecast_noun`);
  const goalLabel = t(`media_plan.goals_kpis.${goalKey}.label`);

  return (
    <Card id="media-plan-goals-kpis-card" className="mt-4 overflow-hidden p-0">
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-goals-kpis-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-goals-kpis-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.goals_kpis.title")}
        </h2>
        <p
          id="media-plan-goals-kpis-subtitle"
          className="text-sm text-white/80"
        >
          {t("media_plan.goals_kpis.subtitle", {
            pct,
            peak: delivery.peakLabel,
          })}
        </p>
      </div>

      <CardContent id="media-plan-goals-kpis-content" className="mt-4 p-6">
        {/* Target + Forecast cards */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div
            id="media-plan-goals-kpis-target"
            className="rounded-lg border border-container-border bg-mw-neutral-50 p-4"
          >
            <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
              <Target className="h-4 w-4" />
              {t("media_plan.goals_kpis.target")}
            </div>
            <p className="mt-2 text-2xl font-bold text-mw-neutral-900">
              {fmt(targetValue)}{" "}
              <span className="text-base font-semibold">{targetNoun}</span>
            </p>
            <p className="mt-1 text-xs text-mw-neutral-400">
              {t("media_plan.goals_kpis.goal_type", { type: goalLabel })}
            </p>
          </div>

          <div
            id="media-plan-goals-kpis-forecast"
            className="rounded-lg border border-mw-primary-100 bg-mw-primary-50 p-4"
          >
            <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
              <TrendingUp className="h-4 w-4" />
              {t("media_plan.goals_kpis.planned_forecast")}
            </div>
            <p className="mt-2 text-2xl font-bold text-mw-neutral-900">
              {fmt(forecastValue)}{" "}
              <span className="text-base font-semibold">{forecastNoun}</span>
            </p>
            <p className="mt-1 text-xs text-mw-neutral-400">
              {t("media_plan.goals_kpis.on_track")}
            </p>
          </div>
        </div>

        {/* Forecast vs Target progress */}
        <div id="media-plan-goals-kpis-progress" className="mt-6">
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="font-medium text-mw-neutral-700">
              {t("media_plan.goals_kpis.forecast_vs_target")}
            </span>
            <span className="font-semibold text-mw-neutral-900">{pct}%</span>
          </div>
          <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-mw-neutral-100">
            <div
              className="h-full"
              style={{
                width: `${bluePct}%`,
                ...getThemePrimaryBackgroundStyle(theme),
              }}
            />
            {greenPct > 0 && (
              <div
                className="h-full"
                style={{
                  width: `${greenPct}%`,
                  backgroundColor: OVER_TARGET_COLOR,
                }}
              />
            )}
          </div>
        </div>

        {/* Expected delivery bars */}
        <p className="mt-6 text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
          {t("media_plan.goals_kpis.expected_delivery", {
            granularity: granularityLabel,
          })}
        </p>
        <div
          id="media-plan-goals-kpis-delivery"
          className="mt-4 flex h-56 items-stretch gap-3"
        >
          {delivery.bins.map((bin) => {
            const isPeak = bin.value === delivery.peakValue;
            const tip = (
              <div className="space-y-0.5 text-xs">
                <p className="font-semibold">{bin.label}</p>
                <p>
                  {t("media_plan.goals_kpis.tip_impressions")}:{" "}
                  {bin.value.toLocaleString()}
                </p>
                <p>
                  {t("media_plan.goals_kpis.tip_reach")}:{" "}
                  {bin.reach.toLocaleString()}
                </p>
              </div>
            );
            return (
              <div
                key={bin.label}
                className="flex flex-1 flex-col items-center gap-2"
              >
                <div className="flex w-full flex-1 items-end">
                  <Tooltip
                    content={tip}
                    triggerClassName="flex h-full w-full items-end"
                  >
                    <div
                      className="w-full rounded-t"
                      style={{
                        height: `${Math.max((bin.value / maxBin) * 100, 2)}%`,
                        ...(isPeak
                          ? { backgroundColor: PEAK_COLOR }
                          : getThemePrimaryBackgroundStyle(theme)),
                      }}
                    />
                  </Tooltip>
                </div>
                <span className="text-xs text-mw-neutral-500">{bin.label}</span>
              </div>
            );
          })}
        </div>

        {/* Summary boxes */}
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-lg border border-container-border px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
              {t("media_plan.goals_kpis.granularity")}
            </p>
            <p className="text-lg font-semibold text-mw-neutral-900">
              {granularityLabel}
            </p>
          </div>
          <div className="rounded-lg border border-container-border px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
              {t("media_plan.goals_kpis.peak_bin")}
            </p>
            <p className="text-lg font-semibold text-mw-neutral-900">
              {delivery.peakLabel || "—"}
            </p>
          </div>
          <div className="rounded-lg border border-container-border px-4 py-3">
            <p className="text-xs uppercase tracking-wider text-mw-neutral-500">
              {t("media_plan.goals_kpis.peak_impressions")}
            </p>
            <p className="text-lg font-semibold text-mw-neutral-900">
              {formatCompactNumber(delivery.peakValue)}
            </p>
          </div>
        </div>

        <p
          id="media-plan-goals-kpis-note"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.goals_kpis.note", {
            forecast: fmt(forecastValue),
            forecastNoun,
            target: fmt(targetValue),
            targetNoun,
            pct,
            granularity: granularityLabel.toLowerCase(),
            peak: delivery.peakLabel,
          })}
        </p>
      </CardContent>
    </Card>
  );
};

export default MediaPlanGoalsKpisComponent;
