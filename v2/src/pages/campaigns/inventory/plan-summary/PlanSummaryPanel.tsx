import { Button } from "@components/ui/Button";
import { Card } from "@components/ui/card";
import { Progress } from "@components/ui/Progressbar";
import { useTranslate } from "@tolgee/react";
import { LineChart } from "lucide-react";
import React from "react";

import ForecastTiles from "./ForecastTiles";
import GoalBudgetBanner from "./GoalBudgetBanner";
import ReachBuildChart from "./ReachBuildChart";
import { getProgressMessageKey } from "./recommendationProgress";
import type { ReachCurveStatus } from "./useReachCurve";
import type { RecommendationForecastStatus } from "./useRecommendationForecast";
import type { CampaignForecastData } from "../../../../types/inventory.types";

interface PlanSummaryPanelProps {
  status: RecommendationForecastStatus;
  progress: number;
  forecastData: CampaignForecastData;
  campaignCurrency: string | undefined;
  /** Campaign's total budget — used to compute the Remaining tile. */
  budget?: number | null;
  goals?: { goalType?: string; targetValue?: number };
  /** Reach Build chart data (Phase 2). */
  reachCurve: {
    status: ReachCurveStatus;
    data: number[];
    labels: string[];
    inventoryCount: number;
  };
  /** Re-runs recommendation generation after a failure. */
  onRetry: () => void;
  /** Navigates to the Budget & Goal step (Step 2). */
  onAdjustBudget: () => void;
  onLowerGoal: () => void;
  /** Navigates to the Optimization step (Step 5). */
  onOptimizeToGoal: () => void;
}

/**
 * Left-side panel on the inventory step — the Plan Summary.
 *
 * Renders by recommendation status:
 *  - generating: progress bar + step message
 *  - failed:     error message + Retry
 *  - completed:  forecast tiles (Phase 1) + Reach Build chart (Phase 2),
 *                or the empty state when nothing is selected
 */
const PlanSummaryPanel: React.FC<PlanSummaryPanelProps> = ({
  status,
  progress,
  forecastData,
  campaignCurrency,
  budget,
  goals,
  reachCurve,
  onRetry,
  onAdjustBudget,
  onLowerGoal,
  onOptimizeToGoal,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const hasInventories = (forecastData?.totalInventories ?? 0) > 0;

  return (
    <Card className="overflow-hidden shadow-inset-bottom flex flex-col h-full">
      <div className="flex-1 overflow-auto p-4">
        {status === "generating" && (
          <div className="h-full flex flex-col items-center justify-center gap-4 py-16">
            <div className="w-full max-w-[640px] flex flex-col items-center gap-2">
              <Progress
                value={progress}
                max={100}
                variant="primary"
                progressTrackVariant="default"
                showLabel={false}
              />
              <p className="text-center text-mw-neutral-500 text-xs leading-4">
                {tCampaigns(getProgressMessageKey(progress))}
              </p>
            </div>
          </div>
        )}

        {status === "failed" && (
          <div className="h-full flex flex-col items-center justify-center text-center gap-3 py-16">
            <p className="text-sm text-mw-neutral-500">
              {tCampaigns("inventories.smartSuggestion.errorMessage")}
            </p>
            <Button
              variant="primary"
              className="bg-mw-purple-warning-500! text-white!"
              onClick={onRetry}
            >
              {tCampaigns("inventories.smartSuggestion.retry")}
            </Button>
          </div>
        )}

        {status === "completed" &&
          (hasInventories ? (
            <div className="space-y-6">
              <div className="space-y-3">
                <div className="space-y-0">
                  <h2 className="text-base font-semibold text-mw-grey-800 leading-tight">
                    {tCampaigns("inventories.planSummary.heading")}
                  </h2>
                  <p className="text-sm text-mw-neutral-500 leading-tight">
                    {tCampaigns("inventories.planSummary.breakdown").replace(
                      "%COUNT%",
                      String(forecastData?.totalInventories ?? 0),
                    )}
                  </p>
                </div>
                <GoalBudgetBanner
                  forecastData={forecastData}
                  goals={goals}
                  onAdjustBudget={onAdjustBudget}
                  onLowerGoal={onLowerGoal}
                  onOptimizeToGoal={onOptimizeToGoal}
                />
                <ForecastTiles
                  forecastData={forecastData}
                  campaignCurrency={campaignCurrency}
                  budget={budget}
                />
              </div>
              {reachCurve.status === "loading" && (
                <div className="h-[260px] rounded-lg bg-mw-neutral-50 animate-pulse" />
              )}
              {reachCurve.status === "ready" && reachCurve.data.length > 0 && (
                <ReachBuildChart
                  data={reachCurve.data}
                  labels={reachCurve.labels}
                  inventoryCount={reachCurve.inventoryCount}
                />
              )}
              {reachCurve.status !== "loading" &&
                reachCurve.data.length === 0 && (
                  <div className="h-[260px] rounded-lg bg-mw-neutral-50 flex flex-col items-center justify-center text-center gap-2">
                    <LineChart className="w-8 h-8 text-mw-neutral-300" />
                    <p className="text-sm text-mw-neutral-500">
                      {tCampaigns("inventories.planSummary.noReachData")}
                    </p>
                  </div>
                )}
            </div>
          ) : (
            <div className="h-full flex flex-col items-center justify-center text-center gap-2 py-16">
              <p className="text-sm font-medium text-mw-grey-800">
                {tCampaigns("inventories.planSummary.empty")}
              </p>
              <p className="text-sm text-mw-neutral-500">
                {tCampaigns("inventories.planSummary.emptyHint")}
              </p>
            </div>
          ))}
      </div>
    </Card>
  );
};

export default PlanSummaryPanel;
