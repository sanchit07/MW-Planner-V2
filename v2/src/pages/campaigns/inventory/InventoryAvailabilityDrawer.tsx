import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useSelectedInventoryList } from "@hooks/useSelectedInventoryList";
import { useTranslate } from "@tolgee/react";
import { useEffect } from "react";
import { CampaignCreateResponse } from "src/types/campaign.types";

import SelectedInventoryAvailability from "./SelectedInventoryAvailability";

interface InventoryAvailabilityDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId: string;
  campaignData: CampaignCreateResponse | null;
}

const InventoryAvailabilityDrawer: React.FC<
  InventoryAvailabilityDrawerProps
> = ({ isOpen, onClose, campaignId, campaignData }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  // Use the reusable hook for loading all selected inventories at once
  const { selectedItems, isLoading, reset, loadSelectedInventory } =
    useSelectedInventoryList({
      campaignId,
      enabled: (campaignId && isOpen) || !!campaignData,
      pageSize: -1, // Load all at once
      sortBy: "name",
      sortDir: "asc",
    });

  // Reset and refetch when drawer opens
  useEffect(() => {
    if (isOpen && campaignId) {
      // Reset the hook state and force refetch when drawer opens
      reset();
      // Manually trigger load after reset to ensure refetch happens
      loadSelectedInventory(0, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, campaignId]);

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={onClose}
      title={tCampaigns("inventoryAvailability.title")}
      size="custom"
      customWidth="85vw"
    >
      <div className="h-[calc(100vh-120px)] overflow-auto">
        {isLoading && (
          <div className="flex items-center justify-center h-full">
            <p className="text-sm text-mw-neutral-600">
              {tCampaigns("inventoryAvailability.loadingInventories")}
            </p>
          </div>
        )}
        {selectedItems.length === 0 && !isLoading && (
          <div className="flex flex-col items-center justify-center h-full">
            <p className="text-lg font-medium text-mw-neutral-600">
              {tCampaigns("inventoryAvailability.noSelectedInventories")}
            </p>
            <p className="text-sm text-mw-neutral-500 mt-2">
              {tCampaigns("inventoryAvailability.pleaseSelectInventories")}
            </p>
          </div>
        )}
        {selectedItems.length > 0 && !isLoading && (
          <SelectedInventoryAvailability
            inventories={selectedItems}
            campaignData={campaignData}
          />
        )}
      </div>
    </ModalDrawer>
  );
};

export default InventoryAvailabilityDrawer;
