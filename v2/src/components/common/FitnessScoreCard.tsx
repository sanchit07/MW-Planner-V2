import { useTranslate } from "@tolgee/react";
import React from "react";

import type { InventoryRecommendationItem } from "../../types/inventory.types";

type ComponentScores = InventoryRecommendationItem["componentScores"];

interface FitnessScoreCardProps {
  scores: ComponentScores;
}

/**
 * Hover popup content for the smart-recommendation score — the "Fitness score
 * card" breakdown of component scores. Reused from the old recommendation list,
 * including its fixed per-row colours.
 */
const FitnessScoreCard: React.FC<FitnessScoreCardProps> = ({ scores }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  // Fixed colours per row + same order as the old design.
  const rows: { label: string; value: number | undefined; color: string }[] = [
    {
      label: tCampaigns("inventories.fitness.budgetRelevance"),
      value: scores?.budgetFit,
      color: "text-mw-success-500",
    },
    {
      // No component-score field for goal match — kept as a literal (as in old design).
      label: tCampaigns("inventories.fitness.campaignGoalMatch"),
      value: 60,
      color: "text-mw-warning-500",
    },
    {
      label: tCampaigns("inventories.fitness.geographyRelevance"),
      value: scores?.geoFit,
      color: "text-mw-error-500",
    },
    {
      label: tCampaigns("inventories.fitness.audienceRelevance"),
      value: scores?.audienceFit,
      color: "text-mw-success-500",
    },
    {
      label: tCampaigns("inventories.fitness.brandRelevance"),
      value: scores?.brandFit,
      color: "text-mw-error-500",
    },
    {
      label: tCampaigns("inventories.fitness.inventoryQuality"),
      value: scores?.qualityFit,
      color: "text-mw-warning-500",
    },
    {
      label: tCampaigns("inventories.fitness.timeAlignment"),
      value: scores?.timeFit,
      color: "text-mw-error-500",
    },
    {
      label: tCampaigns("inventories.fitness.measureConfidence"),
      value: scores?.measureFit,
      color: "text-mw-success-500",
    },
    {
      label: tCampaigns("inventories.fitness.availabilityWindow"),
      value: scores?.availability,
      color: "text-mw-warning-500",
    },
  ];

  return (
    <div className="p-2 flex flex-col gap-2 min-w-[220px]">
      <div className="text-black text-xs font-medium leading-4">
        {tCampaigns("inventories.fitness.title")}
      </div>
      <div className="flex flex-col gap-2">
        {rows.map((row) => (
          <div key={row.label} className="inline-flex items-start gap-1">
            <div className="flex-1 text-mw-secondary text-xs leading-4">
              {row.label}
            </div>
            <div className={`text-xs leading-4 ${row.color}`}>
              {(row.value ?? 0).toFixed(2)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default FitnessScoreCard;
