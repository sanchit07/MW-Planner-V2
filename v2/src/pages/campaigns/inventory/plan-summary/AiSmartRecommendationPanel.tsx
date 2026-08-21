import { Button } from "@components/ui/Button";
import { Card, CardContent } from "@components/ui/card";
import { Modal } from "@components/ui/Modal";
import { Tooltip } from "@components/ui/Tooltip";
import { useTranslate } from "@tolgee/react";
import { Eye, RotateCcw, Sparkles } from "lucide-react";
import React, { useState } from "react";

interface AiSmartRecommendationPanelProps {
  /** Opens the full-page manual selection screen. */
  onEditManually: () => void;
  /** Disable Edit Manually until the recommendation has finished loading. */
  editDisabled?: boolean;
  /** Opens the read-only "View inventories" screen. */
  onView: () => void;
  /** Disable View until the recommendation has finished loading. */
  viewDisabled?: boolean;
  /** Regenerate the AI recommendation (discards manual changes). */
  onRestoreRecommendation?: () => void;
  /** Disable Restore while a recommendation is already generating. */
  restoreDisabled?: boolean;
  /** Deselect-all (pre-regeneration) in flight — spins the Restore icon. */
  isRestoring?: boolean;
}

/**
 * Full-width AI Smart Recommendation row at the bottom of the inventory step.
 * Shows the title/description on the left and the Edit Manually + Restore
 * action buttons on the right. (Metrics live in the Plan Summary tiles.)
 */
const AiSmartRecommendationPanel: React.FC<AiSmartRecommendationPanelProps> = ({
  onEditManually,
  editDisabled = false,
  onView,
  viewDisabled = false,
  onRestoreRecommendation,
  restoreDisabled = false,
  isRestoring = false,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [isRestoreConfirmOpen, setIsRestoreConfirmOpen] = useState(false);

  const handleConfirmRestore = () => {
    setIsRestoreConfirmOpen(false);
    onRestoreRecommendation?.();
  };

  return (
    <Card className="bg-gradient-to-b from-mw-purple-warning-100 to-white shadow-[0px_0px_12px_0px_rgba(138,56,245,0.32)] border-mw-purple-warning-500">
      <CardContent className="p-4 pt-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <div className="w-10 h-10 bg-mw-purple-warning-50 rounded-lg flex items-center justify-center shrink-0">
            <Sparkles className="w-5 h-5 text-mw-purple-warning-500" />
          </div>
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium text-mw-grey-800 leading-none">
              {tCampaigns("inventories.aiRecommendation.title")}
            </p>
            <p className="text-xs font-normal text-mw-neutral-500 leading-tight">
              {tCampaigns("inventories.aiRecommendation.description")}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {onRestoreRecommendation && (
            <Tooltip
              content={tCampaigns("inventories.aiRecommendation.restoreAi")}
            >
              <Button
                variant="ghost"
                aria-label={tCampaigns(
                  "inventories.aiRecommendation.restoreAi",
                )}
                className="text-mw-purple-warning-500 px-2"
                onClick={() => setIsRestoreConfirmOpen(true)}
                disabled={restoreDisabled}
              >
                <RotateCcw
                  className={`w-4 h-4 ${isRestoring ? "animate-spin" : ""}`}
                />
              </Button>
            </Tooltip>
          )}
          <Button
            variant="outline"
            className="gap-2"
            onClick={onView}
            disabled={viewDisabled}
          >
            <Eye className="w-4 h-4" />
            {tCampaigns("inventories.planSummary.view")}
          </Button>
          <Button
            variant="outline"
            className="border-mw-purple-warning-500 text-mw-purple-warning-500"
            onClick={onEditManually}
            disabled={editDisabled}
          >
            {tCampaigns("inventories.aiRecommendation.editManually")}
          </Button>
        </div>
      </CardContent>

      <Modal
        isOpen={isRestoreConfirmOpen}
        onClose={() => setIsRestoreConfirmOpen(false)}
        title={tCampaigns("inventories.aiRecommendation.restoreAi")}
        primaryButtonText={tCampaigns(
          "inventories.aiRecommendation.restoreConfirm",
        )}
        secondaryButtonText={tCampaigns(
          "inventories.aiRecommendation.restoreCancel",
        )}
        onPrimaryAction={handleConfirmRestore}
        onSecondaryAction={() => setIsRestoreConfirmOpen(false)}
        size="sm"
      >
        <p>
          {tCampaigns("inventories.aiRecommendation.restoreConfirmMessage")}
        </p>
      </Modal>
    </Card>
  );
};

export default AiSmartRecommendationPanel;
