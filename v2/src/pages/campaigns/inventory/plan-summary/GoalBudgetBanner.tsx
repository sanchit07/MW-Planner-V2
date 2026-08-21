import Alert from "@components/ui/Alert";
import { Button } from "@components/ui/Button";
import { useTranslate } from "@tolgee/react";
import { normalizeGoalType } from "@utils/budget.utils";
import { useEffect, useMemo, useState } from "react";

import type { CampaignForecastData } from "../../../../types/inventory.types";

interface GoalBudgetBannerProps {
  forecastData: CampaignForecastData;
  goals?: { goalType?: string; targetValue?: number };
  /** Navigates to the Budget & Goal step (Step 2). */
  onAdjustBudget: () => void;
  onLowerGoal: () => void;
  /** Navigates to the Optimization step (Step 5). */
  onOptimizeToGoal: () => void;
}

const GOAL_FORECAST_FIELD: Record<
  string,
  keyof CampaignForecastData | undefined
> = {
  IMPRESSIONS: "estimatedImpression",
  REACH: "estimatedReach",
  SOV: "sov",
  ADPLAYS: "estimatedAdPlays",
};

// Thresholds for "well above"/"falls short of" the goal — the QA doc didn't
// specify exact numbers, so these are reasonable defaults: >20% over is
// clearly excess, <95% is a meaningful shortfall worth flagging.
const OVER_ACHIEVED_RATIO = 1.2;
const SHORTFALL_RATIO = 0.95;

type BannerKind = "over" | "shortfall" | null;

/** Banner above the forecast tiles comparing projected delivery to the campaign's goal. */
const GoalBudgetBanner: React.FC<GoalBudgetBannerProps> = ({
  forecastData,
  goals,
  onAdjustBudget,
  onLowerGoal,
  onOptimizeToGoal,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [dismissed, setDismissed] = useState(false);

  const goalKey = normalizeGoalType(goals?.goalType);
  const targetValue = goals?.targetValue ?? 0;
  const forecastField = goalKey ? GOAL_FORECAST_FIELD[goalKey] : undefined;
  const achievedValue = forecastField
    ? Number(forecastData?.[forecastField] ?? 0)
    : 0;

  const bannerKind: BannerKind = useMemo(() => {
    if (!forecastField || targetValue <= 0) return null;
    if (achievedValue >= targetValue * OVER_ACHIEVED_RATIO) return "over";
    if (achievedValue < targetValue * SHORTFALL_RATIO) return "shortfall";
    return null;
  }, [forecastField, targetValue, achievedValue]);

  // Re-show the banner if the underlying condition changes (e.g. the user
  // adjusts budget/goal and comes back to a different state).
  useEffect(() => {
    setDismissed(false);
  }, [bannerKind]);

  if (!bannerKind || dismissed) return null;

  const goalLabel = goalKey
    ? tCampaigns(`campaignsList.goalTypes.${goalKey}`)
    : "";

  return (
    <Alert variant={bannerKind === "over" ? "success" : "warning"}>
      <div className="space-y-2">
        <p>
          {bannerKind === "over"
            ? tCampaigns("inventories.planSummary.goalOverAchieved", {
                achieved: achievedValue.toLocaleString(),
                target: targetValue.toLocaleString(),
                goalLabel,
              })
            : tCampaigns("inventories.planSummary.budgetShortfall", {
                percent: Math.round((achievedValue / targetValue) * 100),
                target: targetValue.toLocaleString(),
                goalLabel,
              })}
        </p>
        <div className="flex items-center gap-2">
          {bannerKind === "over" ? (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setDismissed(true)}
              >
                {tCampaigns("inventories.planSummary.keepPlan")}
              </Button>
              <Button variant="outline" size="sm" onClick={onOptimizeToGoal}>
                {tCampaigns("inventories.planSummary.optimizeToGoal")}
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={onAdjustBudget}>
                {tCampaigns("inventories.planSummary.adjustBudget")}
              </Button>
              <Button variant="outline" size="sm" onClick={onLowerGoal}>
                {tCampaigns("inventories.planSummary.lowerGoal")}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setDismissed(true)}
              >
                {tCampaigns("inventories.planSummary.continueAnyway")}
              </Button>
            </>
          )}
        </div>
      </div>
    </Alert>
  );
};

export default GoalBudgetBanner;
