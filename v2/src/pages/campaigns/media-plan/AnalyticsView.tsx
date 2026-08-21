import { Tabs, TabsList, TabsTrigger, TabsContent } from "@components/ui/Tabs";
import { useTranslate } from "@tolgee/react";
import React, { useEffect, useMemo, useState } from "react";

import CampaignPlanTab from "./AnalyticsComponents/CampaignPlanTab";
import CinemaTab from "./AnalyticsComponents/CinemaTab";
import CostingTab from "./AnalyticsComponents/CostingTab";
import DOOHSchedulesTab from "./AnalyticsComponents/DOOHSchedulesTab";
import GeographyTargetingTab from "./AnalyticsComponents/GeographyTargetingTab";
import InventoryDetailsTab from "./AnalyticsComponents/InventoryDetailsTab";
import OperationDetailsTab from "./AnalyticsComponents/OperationDetailsTab";
import { transformMediaPlanData } from "./transformMediaPlanData";
import { UnifiedMediaPlanData } from "./useMediaPlanData";

interface AnalyticsViewProps {
  mediaPlanData?: UnifiedMediaPlanData;
}

const AnalyticsView: React.FC<AnalyticsViewProps> = ({ mediaPlanData }) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [activeTab, setActiveTab] = useState("campaign-plan");

  // Transform media plan data to analytics data format
  const analyticsData = useMemo(() => {
    return transformMediaPlanData(mediaPlanData, tCommon, tCampaigns);
  }, [mediaPlanData, tCommon, tCampaigns]);

  // DOOH Schedules only applies to digital (hourly-slot) inventory — disable
  // the tab for classic-only campaigns.
  const isClassicOnlyCampaign =
    analyticsData.estimatedPerformanceMetrics?.hasDigitalInventory === false;

  // Cinema tab is rendered only when the plan has cinema line items — the
  // exact same gating condition used by the Excel sheet and PPT slide.
  const hasCinemaInventory = (analyticsData.cinemaInventory?.length ?? 0) > 0;

  useEffect(() => {
    if (isClassicOnlyCampaign && activeTab === "dooh-schedules") {
      setActiveTab("campaign-plan");
    }
    if (!hasCinemaInventory && activeTab === "cinema") {
      setActiveTab("campaign-plan");
    }
  }, [isClassicOnlyCampaign, hasCinemaInventory, activeTab]);

  return (
    <div id="media-plan-analytics-view-container" className="space-y-4 mx-32">
      <Tabs
        id="media-plan-analytics-tabs"
        value={activeTab}
        onValueChange={setActiveTab}
        className="w-full"
      >
        <TabsList
          id="media-plan-analytics-tabs-list"
          className={`grid w-full ${hasCinemaInventory ? "grid-cols-7" : "grid-cols-6"} mb-4`}
        >
          <TabsTrigger value="campaign-plan">
            {tCampaigns("mediaPlanAnalytics.tabs.campaignPlan")}
          </TabsTrigger>
          <TabsTrigger value="inventory-details">
            {tCampaigns("mediaPlanAnalytics.tabs.inventoryDetails")}
          </TabsTrigger>
          <TabsTrigger value="costing">
            {tCampaigns("mediaPlanAnalytics.tabs.costing")}
          </TabsTrigger>
          <TabsTrigger value="operation-details">
            {tCampaigns("mediaPlanAnalytics.tabs.operationDetails")}
          </TabsTrigger>
          <TabsTrigger value="dooh-schedules" disabled={isClassicOnlyCampaign}>
            {tCampaigns("mediaPlanAnalytics.tabs.doohSchedules")}
          </TabsTrigger>
          <TabsTrigger value="geography-targeting">
            {tCampaigns("mediaPlanAnalytics.tabs.geographyTargeting")}
          </TabsTrigger>
          {hasCinemaInventory && (
            <TabsTrigger value="cinema">
              {tCampaigns("mediaPlanAnalytics.tabs.cinema")}
            </TabsTrigger>
          )}
        </TabsList>

        <TabsContent
          id="media-plan-analytics-content-campaign-plan"
          value="campaign-plan"
        >
          <CampaignPlanTab
            analyticsData={analyticsData}
            performanceMetrics={mediaPlanData?.performanceMetrics}
            geographySummary={mediaPlanData?.geographySummary}
            channelCount={mediaPlanData?.channelCount}
            goalType={mediaPlanData?.headerInfo?.goalType}
            headerInfo={mediaPlanData?.headerInfo}
            mediaChannels={mediaPlanData?.mediaChannels}
            clientType={mediaPlanData?.clientType}
            planNumber={mediaPlanData?.planNumber}
          />
        </TabsContent>

        <TabsContent
          id="media-plan-analytics-content-inventory-details"
          value="inventory-details"
        >
          <InventoryDetailsTab
            analyticsData={analyticsData}
            goalType={mediaPlanData?.headerInfo?.goalType}
          />
        </TabsContent>

        <TabsContent id="media-plan-analytics-content-costing" value="costing">
          <CostingTab analyticsData={analyticsData} />
        </TabsContent>

        <TabsContent
          id="media-plan-analytics-content-operation-details"
          value="operation-details"
        >
          <OperationDetailsTab analyticsData={analyticsData} />
        </TabsContent>

        <TabsContent
          id="media-plan-analytics-content-dooh-schedules"
          value="dooh-schedules"
        >
          <DOOHSchedulesTab
            analyticsData={analyticsData}
            flightStartDate={mediaPlanData?.headerInfo?.startDate}
            flightEndDate={mediaPlanData?.headerInfo?.endDate}
          />
        </TabsContent>

        <TabsContent
          id="media-plan-analytics-content-geography-targeting"
          value="geography-targeting"
        >
          <GeographyTargetingTab analyticsData={analyticsData} />
        </TabsContent>

        {hasCinemaInventory && (
          <TabsContent id="media-plan-analytics-content-cinema" value="cinema">
            <CinemaTab analyticsData={analyticsData} />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
};

export default AnalyticsView;
