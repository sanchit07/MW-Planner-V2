import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { ListChecks, Sparkles } from "lucide-react";
import React from "react";

interface RecommendationChoiceCardProps {
  /** Start the AI recommendation flow (default path). */
  onUseRecommendations: () => void;
  /** Skip recommendations and go straight to manual inventory selection. */
  onPickManually: () => void;
}

/**
 * Choice gate shown when a plan reaches Step 4 (Inventories) for the first
 * time: use the AI recommendation, or skip it and pick inventory manually.
 * The choice is persisted on the plan (skipRecommendation) so revisits honour
 * it; users who skip can still opt back in later from the manual-mode panel.
 */
const RecommendationChoiceCard: React.FC<RecommendationChoiceCardProps> = ({
  onUseRecommendations,
  onPickManually,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  return (
    <Card className="flex-1 overflow-hidden shadow-inset-bottom">
      <CardContent className="h-full flex flex-col items-center justify-center gap-6 p-8">
        <div className="flex flex-col items-center gap-1 text-center">
          <h2 className="text-base font-semibold text-mw-grey-800">
            {tCampaigns("inventories.recommendationChoice.title")}
          </h2>
          <p className="text-sm text-mw-neutral-500 max-w-[560px]">
            {tCampaigns("inventories.recommendationChoice.description")}
          </p>
        </div>

        <div className="flex flex-wrap items-stretch justify-center gap-4">
          <button
            type="button"
            data-testid="choice-use-recommendations"
            onClick={onUseRecommendations}
            className="w-[280px] flex flex-col items-start gap-2 rounded-lg border border-mw-purple-warning-500 bg-gradient-to-b from-mw-purple-warning-100 to-white p-4 text-left hover:shadow-[0px_0px_12px_0px_rgba(138,56,245,0.32)] transition-shadow"
          >
            <div className="w-10 h-10 bg-mw-purple-warning-50 rounded-lg flex items-center justify-center">
              <Sparkles className="w-5 h-5 text-mw-purple-warning-500" />
            </div>
            <span className="text-sm font-medium text-mw-grey-800">
              {tCampaigns("inventories.recommendationChoice.useAiTitle")}
            </span>
            <span className="text-xs text-mw-neutral-500">
              {tCampaigns("inventories.recommendationChoice.useAiDescription")}
            </span>
          </button>

          <button
            type="button"
            data-testid="choice-pick-manually"
            onClick={onPickManually}
            className="w-[280px] flex flex-col items-start gap-2 rounded-lg border border-mw-neutral-200 bg-white p-4 text-left hover:border-mw-primary-500 hover:shadow-sm transition-all"
          >
            <div className="w-10 h-10 bg-mw-neutral-50 rounded-lg flex items-center justify-center">
              <ListChecks className="w-5 h-5 text-mw-primary-500" />
            </div>
            <span className="text-sm font-medium text-mw-grey-800">
              {tCampaigns("inventories.recommendationChoice.manualTitle")}
            </span>
            <span className="text-xs text-mw-neutral-500">
              {tCampaigns("inventories.recommendationChoice.manualDescription")}
            </span>
          </button>
        </div>
      </CardContent>
    </Card>
  );
};

export default RecommendationChoiceCard;
