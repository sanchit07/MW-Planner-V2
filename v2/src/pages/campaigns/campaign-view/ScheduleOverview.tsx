import { InventoryDetailCard } from "@components/common/InventoryDetailCard";
import { SelectedInventoryListContainer } from "@components/common/SelectedInventoryListContainer";
import { EmptyValueDisplay } from "@constants/inventory.constants";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import { Clock } from "lucide-react";
import React from "react";
import { ViewCampaign } from "src/types/campaign.types";

interface ScheduleOverviewProps {
  campaignId?: string;
  campaignCurrency?: string;
  forecastData?: ViewCampaign["performance"];
  campaignStartDate?: string | Date | null | undefined;
  campaignEndDate?: string | Date | null | undefined;
}

const ScheduleOverview: React.FC<ScheduleOverviewProps> = ({
  campaignId,
  campaignCurrency,
  campaignStartDate,
  campaignEndDate,
}) => {
  const { t: tCampaigns } = useTranslate("campaigns");

  const headerIcon = (
    <div className="w-10 h-10 bg-mw-purple-warning-50 rounded-xs flex items-center justify-center">
      <Clock className="w-5 h-5 text-mw-deep-purple-500 bg-mw-deep-purple-50" />
    </div>
  );

  return (
    <div className="inventory-list pt-4 h-full">
      <SelectedInventoryListContainer
        campaignId={campaignId}
        enabled={!!campaignId}
        containerClassName="p-4 flex flex-col h-full"
        headerClassName="pb-4 border-b border-container-border"
        headerIcon={headerIcon}
        headerTitle={tCampaigns("viewCampaign.scheduleTab.title")}
        headerSubtitle={tCampaigns("viewCampaign.scheduleTab.subTitle")}
        contentClassName="p-0! pt-5! flex flex-col gap-4 flex-1 max-h-96"
        renderItem={(item) => (
          <InventoryDetailCard
            key={item.detail.id}
            item={item}
            campaignCurrency={campaignCurrency}
            formatCurrency={formatCurrency}
            tCampaigns={tCampaigns}
            emptyValueDisplay={EmptyValueDisplay.DASH}
            fromSchedule={true}
            showSmartSuggestionScore={false}
            campaignStartDate={campaignStartDate}
            campaignEndDate={campaignEndDate}
          />
        )}
      />
    </div>
  );
};

export default ScheduleOverview;
