import { Button } from "@components/ui/Button";
import { Card, CardContent } from "@components/ui/card";
import { useTranslate } from "@tolgee/react";
import { Eye, ListChecks, Sparkles } from "lucide-react";
import React from "react";

interface ManualModePanelProps {
  /** Opens the full-page manual selection screen. */
  onSelectInventory: () => void;
  /** Opens the read-only "View inventories" screen. */
  onView: () => void;
  viewDisabled?: boolean;
  /** Switch back to the AI recommendation flow (keeps the current selection). */
  onUseRecommendations: () => void;
}

/**
 * Step 4 action row shown when the plan skipped recommendations
 * (skipRecommendation=true): manual selection is the primary surface, with an
 * opt-in button to switch back to the AI recommendation flow at any time.
 */
const ManualModePanel: React.FC<ManualModePanelProps> = ({
  onSelectInventory,
  onView,
  viewDisabled = false,
  onUseRecommendations,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  return (
    <Card>
      <CardContent className="p-4 pt-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="w-10 h-10 bg-mw-neutral-50 rounded-lg flex items-center justify-center shrink-0">
            <ListChecks className="w-5 h-5 text-mw-primary-500" />
          </div>
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium text-mw-grey-800 leading-none">
              {tCampaigns("inventories.manualMode.title")}
            </p>
            <p className="text-xs font-normal text-mw-neutral-500 leading-tight">
              {tCampaigns("inventories.manualMode.description")}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            className="gap-2 text-mw-purple-warning-500"
            onClick={onUseRecommendations}
          >
            <Sparkles className="w-4 h-4" />
            {tCampaigns("inventories.manualMode.useRecommendations")}
          </Button>
          <Button
            variant="outline"
            className="gap-2"
            onClick={onView}
            disabled={viewDisabled}
          >
            <Eye className="w-4 h-4" />
            {tCampaigns("inventories.planSummary.view")}
          </Button>
          <Button variant="primary" onClick={onSelectInventory}>
            {tCampaigns("inventories.manualMode.selectInventory")}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};

export default ManualModePanel;
