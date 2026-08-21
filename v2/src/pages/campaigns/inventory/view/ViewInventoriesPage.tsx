import { toCountrySlug } from "@components/map/MobilityHeatmap";
import { Tabs, TabsList, TabsTrigger } from "@components/ui/Tabs";
import { useTranslate } from "@tolgee/react";
import { clsx } from "clsx";
import { X } from "lucide-react";
import { useEffect, useState } from "react";
import type { CampaignCreateResponse } from "src/types/campaign.types";

import { type RootState, useAppSelector } from "../../../../store";
import InventoryMapView from "../InventoryMapView";
import { useRecommendationScores } from "./useRecommendationScores";

interface ViewInventoriesPageProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * Full-page popup (overlay) "View Inventories" screen (read-only).
 *
 * Rendered inside the wizard as a fixed overlay so the wizard never unmounts —
 * closing it returns to the exact same step. A single InventoryMapView is
 * mounted; the Map/Availability toggle only swaps its right panel.
 */
const ViewInventoriesPage = ({ isOpen, onClose }: ViewInventoriesPageProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const campaignState = useAppSelector((state: RootState) => state.campaign);
  const campaignData =
    campaignState?.campaignData as CampaignCreateResponse | null;
  const campaignId = campaignState.campaignId || campaignData?.id || "";
  const campaignCurrency = campaignData?.currency || "";
  const runId = campaignState.recommendationRun?.runId;

  const [activeView, setActiveView] = useState<"map" | "availability">("map");

  // Slide-in/out animation: `mounted` keeps it in the DOM through the slide-out;
  // `visible` drives the translate-x transition.
  const [mounted, setMounted] = useState(isOpen);
  const [visible, setVisible] = useState(isOpen);

  useEffect(() => {
    if (isOpen) {
      setMounted(true);
      let raf2 = 0;
      const raf1 = requestAnimationFrame(() => {
        raf2 = requestAnimationFrame(() => setVisible(true));
      });
      return () => {
        cancelAnimationFrame(raf1);
        cancelAnimationFrame(raf2);
      };
    }
    setVisible(false);
    const timer = setTimeout(() => setMounted(false), 300);
    return () => clearTimeout(timer);
  }, [isOpen]);

  // Smart score per inventory (same source the recommendation list used).
  const scoresByReferenceId = useRecommendationScores(
    campaignId,
    runId,
    isOpen && !!runId,
  );

  if (!mounted) return null;

  return (
    <div
      className={clsx(
        "fixed inset-0 z-50 flex flex-col overflow-hidden bg-white transition-transform duration-300 ease-in-out",
        visible ? "translate-x-0" : "translate-x-full",
      )}
    >
      <Tabs
        value={activeView}
        onValueChange={(v) => setActiveView(v as "map" | "availability")}
        className="flex flex-col h-full"
      >
        {/* Header */}
        <div className="relative flex items-center justify-between gap-4 px-4 py-3 bg-white border-b border-mw-neutral-100 shrink-0">
          <h1 className="text-base font-semibold text-mw-grey-800">
            {tCampaigns("inventories.view.title")}
          </h1>
          <div className="absolute left-1/2 -translate-x-1/2">
            <TabsList>
              <TabsTrigger value="map">
                {tCampaigns("inventories.view.tabs.map")}
              </TabsTrigger>
              <TabsTrigger value="availability">
                {tCampaigns("inventories.view.tabs.availability")}
              </TabsTrigger>
            </TabsList>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={tCampaigns("inventories.view.title")}
            className="text-mw-neutral-600 hover:text-mw-grey-900"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content — single instance; only the right panel changes */}
        <div className="flex-1 min-h-0 overflow-hidden p-1">
          <InventoryMapView
            campaignId={campaignId}
            campaignCurrency={campaignCurrency}
            showDeleteInventoryButton={false}
            showViewDetailsButton
            campaignStartDate={campaignData?.startDate}
            campaignEndDate={campaignData?.endDate}
            scoresByReferenceId={scoresByReferenceId}
            rightPanel={activeView}
            mobilityCountrySlug={toCountrySlug(campaignData?.countryId)}
            enabled
            className="h-full"
          />
        </div>
      </Tabs>
    </div>
  );
};

export default ViewInventoriesPage;
