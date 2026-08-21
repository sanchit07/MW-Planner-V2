import { InventoryDetailCard } from "@components/common/InventoryDetailCard";
import { SelectedInventoryListContainer } from "@components/common/SelectedInventoryListContainer";
// PL3-I2: Button, Dropdown* and ChevronDown imports retained for the hidden
// Group By control. Restore together with the commented-out headerActions block.
// import { Button } from "@components/ui/Button";
import { Card, CardContent, CardTitle, CardHeader } from "@components/ui/card";
// import {
//   Dropdown,
//   DropdownTrigger,
//   DropdownContent,
//   DropdownItem,
//   DropdownSeparator,
// } from "@components/ui/Dropdown";
import { EmptyValueDisplay } from "@constants/inventory.constants";
import { useTranslate } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import { Info, Warehouse } from "lucide-react";
import React, { useState } from "react";
import { ViewCampaign } from "src/types/campaign.types";

import { InventoryItem } from "../../../types/inventory.types";
import InventoryDetailsDrawer from "../inventory/InventoryDetailsDrawer";

interface InventoryOverviewProps {
  inventoryOverViewData?: {
    totalInventories?: number;
    totalFormats?: number;
    totalTypes?: number;
    totalCity?: number;
    estImpressions?: number;
    adPlays?: number;
  };
  campaignId?: string;
  campaignCurrency?: string;
  forecastData?: ViewCampaign["performance"];
  campaignStartDate?: string | Date | null | undefined;
  campaignEndDate?: string | Date | null | undefined;
  goalType?: string;
  isNegotiated?: boolean;
}

const InventoryOverview: React.FC<InventoryOverviewProps> = ({
  inventoryOverViewData,
  forecastData,
  campaignId,
  campaignCurrency,
  campaignStartDate,
  campaignEndDate,
  goalType,
  isNegotiated,
}) => {
  const { t: tCampaigns } = useTranslate("campaigns");

  // PL3-I2: groupBy state retained for the hidden Group By control. Restore
  // together with the commented-out headerActions block below.
  // const [groupBy, setGroupBy] = useState("all");
  const [isDetailsDrawerOpen, setIsDetailsDrawerOpen] = useState(false);
  const [externalInventoryId, setExternalInventoryId] = useState<string>("");

  // Handle view details
  const handleViewDetails = (inventory: InventoryItem) => {
    setExternalInventoryId(inventory.detail.externalId);
    setIsDetailsDrawerOpen(true);
  };

  return (
    <>
      <Card>
        <CardHeader className="p-4">
          <div className="flex items-center justify-between border-b border-container-border pb-4">
            <CardTitle className="text-base font-medium leading-5 flex items-center gap-2">
              {tCampaigns("viewCampaign.inventoryTab.title")}
            </CardTitle>
          </div>
        </CardHeader>

        {/* Metrics Cards */}
        <CardContent>
          <div className="bg-mw-info-50 text-mw-info-500 flex items-center p-2 text-sm rounded-xs gap-2">
            <Info size="14" />
            {tCampaigns("viewCampaign.inventoryTab.inventoryInfo")}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4 mt-4">
            {/* Total Inventory */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("viewCampaign.inventoryTab.totalInventory")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {inventoryOverViewData?.totalInventories || "--"}
                </p>
              </div>
            </Card>

            {/* Format */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("viewCampaign.inventoryTab.format")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {inventoryOverViewData?.totalFormats || "--"}
                </p>
              </div>
            </Card>

            {/* Types */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("viewCampaign.inventoryTab.types")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {inventoryOverViewData?.totalTypes || "--"}
                </p>
              </div>
            </Card>

            {/* Cities */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("viewCampaign.inventoryTab.cities")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {inventoryOverViewData?.totalCity || "--"}
                </p>
              </div>
            </Card>

            {/* Est. Impressions */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("inventories.forecast.est_impressions")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {(forecastData &&
                    formatNumber(forecastData?.estimatedImpression)) ||
                    "--"}
                </p>
              </div>
            </Card>

            {/* Ad Plays */}
            <Card className="p-4">
              <div className="space-y-1">
                <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                  {tCampaigns("viewCampaign.inventoryTab.adPlays")}
                </p>
                <p className="text-lg font-semibold text-mw-primary-500 leading-6">
                  {(forecastData &&
                    formatNumber(forecastData?.estimatedAdPlays)) ||
                    "--"}
                </p>
              </div>
            </Card>
          </div>
        </CardContent>
      </Card>
      <div className="inventory-list pt-4">
        <SelectedInventoryListContainer
          campaignId={campaignId}
          enabled={!!campaignId}
          containerClassName="p-4"
          headerClassName="pb-4 border-b border-container-border"
          headerIcon={
            <div className="w-10 h-10 bg-mw-purple-warning-50 rounded-xs flex items-center justify-center">
              <Warehouse className="w-5 h-5 text-mw-purple-warning-500" />
            </div>
          }
          headerTitle={tCampaigns(
            "viewCampaign.inventoryTab.selectedInventory",
          )}
          headerSubtitle={tCampaigns(
            "viewCampaign.inventoryTab.selectedInventoryInfo",
          )}
          // PL3-I2: the `headerActions` Group By dropdown is hidden until real
          // grouping is built. It was an unfinished stub (only "All Inventories"
          // was wired and `groupBy` did not group the list). To restore, re-add
          // the headerActions prop here plus the `groupBy` state and the
          // Button / Dropdown* / ChevronDown imports (see git history).
          contentClassName="p-0! pt-5! flex flex-col gap-4 flex-1 max-h-96"
          renderItem={(item) => (
            <InventoryDetailCard
              key={item.detail.id}
              item={item}
              onViewDetails={() => handleViewDetails(item)}
              campaignCurrency={campaignCurrency}
              formatCurrency={formatCurrency}
              tCampaigns={tCampaigns}
              emptyValueDisplay={EmptyValueDisplay.DASH}
              showSmartSuggestionScore={false}
              campaignStartDate={campaignStartDate}
              campaignEndDate={campaignEndDate}
              goalType={goalType}
              isNegotiated={isNegotiated}
            />
          )}
        />

        {/* Details Drawer */}
        <InventoryDetailsDrawer
          isOpen={isDetailsDrawerOpen}
          onClose={() => setIsDetailsDrawerOpen(false)}
          campaignStartDate={campaignStartDate}
          campaignEndDate={campaignEndDate}
          externalInventoryId={externalInventoryId || ""}
        />
      </div>
    </>
  );
};

export default InventoryOverview;
