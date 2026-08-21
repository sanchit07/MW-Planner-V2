import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useTranslate } from "@tolgee/react";
import { POIPlaceData } from "src/types/campaign.types";

import InventoryMapView from "./InventoryMapView";

interface InventoryMapViewDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignCurrency?: string;
  campaignId: string;
  onItemSelection?: (id: string, selected: boolean) => void;
  showDeleteInventoryButton?: boolean;
  availablePOIs?: POIPlaceData[];
  /** Country slug (countries master) enabling the audience mobility heatmap. */
  mobilityCountrySlug?: string;
}

export const InventoryMapViewDrawer: React.FC<InventoryMapViewDrawerProps> = ({
  isOpen,
  onClose,
  campaignCurrency = "",
  campaignId,
  onItemSelection,
  showDeleteInventoryButton = true,
  availablePOIs,
  mobilityCountrySlug,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={onClose}
      title={tCampaigns("inventoryMapView.title")}
      size="custom"
      customWidth="87vw"
    >
      <InventoryMapView
        campaignId={campaignId}
        campaignCurrency={campaignCurrency}
        onItemSelection={onItemSelection}
        showDeleteInventoryButton={showDeleteInventoryButton}
        availablePOIs={availablePOIs}
        mobilityCountrySlug={mobilityCountrySlug}
        enabled={isOpen}
        className="h-[calc(100vh-100px)]"
      />
    </ModalDrawer>
  );
};

export default InventoryMapViewDrawer;
