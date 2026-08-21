import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { formatCompactNumber } from "@utils/dashboard.utils";
import React, { useMemo } from "react";
import { CampaignForecastData } from "src/types/inventory.types";

import { PresentationTheme } from "./types";
import { MediaPlanHeaderInfo, SelectedInventory } from "./types";
import {
  computeAudienceActivityByDay,
  computeConcentrationIndex,
  CONCENTRATION_INDEX_MAX,
  getThemePrimaryBackgroundStyle,
} from "./utils";
import ReachBuildChart from "../inventory/plan-summary/ReachBuildChart";
import { useReachCurve } from "../inventory/plan-summary/useReachCurve";

const PEAK_COLOR = "#06b6d4"; // cyan — highlights the single busiest day

interface AudienceDemographics {
  ageGroups?: string[];
  incomeLevel?: string[];
  interests?: string[];
  lifestyle?: string[];
}

/** Campaign targeting demographics (from GET /campaigns/{id}); the only source
 * with gender + behaviour. Absent on the public page. */
interface TargetingDemographics {
  age?: string[];
  gender?: string[];
  income?: string[];
  interests?: string[];
  behavior?: string[];
}

interface MediaPlanAudienceTrendsProps {
  campaignId?: string;
  headerInfo?: MediaPlanHeaderInfo;
  performanceMetrics?: CampaignForecastData | null;
  audienceDemographics?: AudienceDemographics;
  targetingDemographics?: TargetingDemographics;
  selectedInventory?: SelectedInventory;
  theme?: PresentationTheme;
}

const MediaPlanAudienceTrendsComponent: React.FC<
  MediaPlanAudienceTrendsProps
> = ({
  campaignId,
  headerInfo,
  performanceMetrics,
  audienceDemographics,
  targetingDemographics,
  selectedInventory,
  theme,
}) => {
  const { t } = useTranslate(["campaigns"]);

  const estimatedReach = performanceMetrics?.estimatedReach || 0;

  // Per-inventory reach/cost from the plan's selected inventory (already carries
  // the /selected-inventory/all overlay on both authed & public). Passing this
  // to useReachCurve skips its authed /all fetch — required on the public page
  // (no Bearer token); the reach-saturation-curve endpoint itself is token-less.
  const reachInventories = useMemo(
    () =>
      (selectedInventory?.locations || []).map((it) => ({
        referenceId: it.detail?.referenceId || "",
        reach: it.performance?.estimatedReach || 0,
        cpmBudget: it.performance?.estimatedCost || 0,
      })),
    [selectedInventory],
  );

  // Cumulative reach curve — fed from the plan's own inventory (no authed call).
  const { status, overallReach, labels, inventoryCount } = useReachCurve(
    campaignId || "",
    Boolean(campaignId),
    headerInfo?.startDate,
    headerInfo?.endDate,
    reachInventories,
  );

  // Audience Activity by Day — union of the selected inventories' daypart grids
  // rolled up per weekday (PRD §10.5.2).
  const activity = useMemo(
    () => computeAudienceActivityByDay(selectedInventory?.locations || []),
    [selectedInventory],
  );
  const maxActivity = Math.max(...activity.bars.map((d) => d.sharePct), 1);

  // Targeting presence — any demographic dimension selected.
  const hasAudienceTargeting = Boolean(
    audienceDemographics?.ageGroups?.length ||
      audienceDemographics?.incomeLevel?.length ||
      audienceDemographics?.interests?.length ||
      audienceDemographics?.lifestyle?.length,
  );
  // Suppress the activity block unless there's targeting or a custom schedule.
  const showActivity = hasAudienceTargeting || activity.hasCustomSchedule;

  // Targeting Concentration Index — 4 fixed axes, each a deterministic
  // reach-lift index (1.4×–3.6×) hashed from the axis's targeted tokens (or a
  // campaign-specific fallback when an axis is untargeted). Prefer campaign
  // targeting (has gender + behaviour); fall back to the media-plan
  // audienceDemographics on the public page.
  const concentrationInput = useMemo(
    () =>
      targetingDemographics
        ? {
            ageGender: [
              ...(targetingDemographics.age || []),
              ...(targetingDemographics.gender || []),
            ],
            income: targetingDemographics.income,
            behavior: targetingDemographics.behavior,
            interest: targetingDemographics.interests,
          }
        : {
            ageGender: audienceDemographics?.ageGroups,
            income: audienceDemographics?.incomeLevel,
            behavior: audienceDemographics?.lifestyle,
            interest: audienceDemographics?.interests,
          },
    [targetingDemographics, audienceDemographics],
  );
  // Only show the concentration block when the plan has any audience targeting.
  const hasConcentration = Object.values(concentrationInput).some(
    (v) => (v || []).length > 0,
  );
  const concentration = useMemo(
    () => computeConcentrationIndex(concentrationInput, campaignId),
    [concentrationInput, campaignId],
  );

  return (
    <Card
      id="media-plan-audience-trends-card"
      className="mt-4 overflow-hidden p-0"
    >
      {/* Section banner (theme-primary background) */}
      <div
        id="media-plan-audience-trends-header"
        className="px-6 py-5 text-white"
        style={getThemePrimaryBackgroundStyle(theme)}
      >
        <h2
          id="media-plan-audience-trends-title"
          className="text-2xl font-bold leading-8"
        >
          {t("media_plan.audience_trends.title")}
        </h2>
        <p
          id="media-plan-audience-trends-subtitle"
          className="text-sm text-white/80"
        >
          {t("media_plan.audience_trends.subtitle")}
        </p>
      </div>

      <CardContent id="media-plan-audience-trends-content" className="mt-4 p-6">
        {/* Cumulative reach */}
        <div
          id="media-plan-audience-trends-reach"
          className="rounded-lg border border-container-border p-4"
        >
          <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
            {t("media_plan.audience_trends.cumulative_reach")}
          </p>
          <p className="mb-2 text-sm font-semibold text-mw-neutral-900">
            {t("media_plan.audience_trends.reach_headline", {
              reach: formatCompactNumber(estimatedReach),
            })}
          </p>
          {status === "ready" && overallReach.length > 0 ? (
            <ReachBuildChart
              data={overallReach}
              labels={labels}
              inventoryCount={inventoryCount}
              height={220}
            />
          ) : (
            <p
              id="media-plan-audience-trends-reach-empty"
              className="py-10 text-center text-sm text-mw-neutral-400"
            >
              {status === "loading"
                ? t("media_plan.audience_trends.reach_loading")
                : t("media_plan.audience_trends.reach_unavailable")}
            </p>
          )}
        </div>

        <div
          className={`mt-4 grid grid-cols-1 gap-4 ${
            showActivity && hasConcentration ? "lg:grid-cols-2" : ""
          }`}
        >
          {/* Audience activity by day — hidden when neither audience targeting
              nor a custom schedule exists (PRD §10.5.2). */}
          {showActivity && (
            <div
              id="media-plan-audience-trends-activity"
              className="rounded-lg border border-container-border p-4"
            >
              <p className="mb-4 text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.audience_trends.activity_by_day")}
              </p>
              <div className="flex h-40 items-stretch gap-2">
                {activity.bars.map((d) => (
                  <div
                    key={d.day}
                    className="flex flex-1 flex-col items-center gap-2"
                  >
                    <div className="flex w-full flex-1 items-end">
                      <div
                        className="w-full rounded-t"
                        style={{
                          height: `${Math.max((d.sharePct / maxActivity) * 100, 2)}%`,
                          ...(d.isPeak
                            ? { backgroundColor: PEAK_COLOR }
                            : getThemePrimaryBackgroundStyle(theme)),
                        }}
                        title={`${d.hours}h · ${d.sharePct.toFixed(1)}%`}
                      />
                    </div>
                    <span className="text-xs text-mw-neutral-500">{d.day}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Targeting Concentration Index — relative reach-lift heuristic,
              shown only when the plan has any audience targeting. */}
          {hasConcentration && (
            <div
              id="media-plan-audience-trends-concentration"
              className="rounded-lg border border-container-border p-4"
            >
              <p className="text-xs font-medium uppercase tracking-wider text-mw-neutral-500">
                {t("media_plan.audience_trends.concentration_index")}
              </p>
              <p className="mb-4 text-xs italic text-mw-neutral-400">
                {t("media_plan.audience_trends.concentration_subtitle")}
              </p>
              <div className="flex flex-col gap-3">
                {concentration.map((c) => (
                  <div key={c.key} className="flex items-center gap-3 text-sm">
                    <span className="w-28 shrink-0 text-mw-neutral-700">
                      {t(`media_plan.audience_trends.${c.key}`)}
                    </span>
                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-mw-neutral-100">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${(c.index / CONCENTRATION_INDEX_MAX) * 100}%`,
                          ...getThemePrimaryBackgroundStyle(theme),
                        }}
                      />
                    </div>
                    <span className="w-10 shrink-0 text-right font-semibold text-mw-neutral-900">
                      {c.index.toFixed(1)}×
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <p
          id="media-plan-audience-trends-note"
          className="mt-4 border-t border-container-border pt-4 text-xs leading-5"
          style={{ color: "hsl(var(--muted-foreground))" }}
        >
          {t("media_plan.audience_trends.note")}
        </p>
      </CardContent>
    </Card>
  );
};

export default MediaPlanAudienceTrendsComponent;
