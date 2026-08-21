import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "@components/ui/Dropdown";
import { Loading } from "@components/ui/Spinner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import {
  useLazySplitCostCampaignQuery,
  useLazyViewCampaignQuery,
} from "@services/campaign/campaignSlice";
import { useLazyGetPriceSummaryQuery } from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import { formatCurrencyWithLocale } from "@utils/currency";
import { formatDisplayDate } from "@utils/dateUtils";
import { ChevronDown } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import CampaignDetailsTargetingTabInfo from "./CampaignDetailsTargetingTabInfo";
import CampaignGoalView from "./CampaignGoalView";
import CommentsTab from "./CommentsTab";
import CostBreakdown from "./CostBreakdown";
import InventoryOverview from "./InventoryOverview";
import PerformanceMetrics from "./PerformanceMetrics";
import ScheduleOverview from "./ScheduleOverview";
import { CostSplitByCampaignData } from "../../../types/campaign.types";

interface CampaignDetailsProps {
  campaignId: string;
}

const CampaignDetails = ({ campaignId }: CampaignDetailsProps) => {
  const { t: tCampaigns } = useTranslate("campaigns");
  const { t: tCommon } = useTranslate(["common"]);
  const language = useTolgee(["language"]).getLanguage();
  const [splitBy, setSplitBy] = useState("MEDIA_OWNER");
  const [activeTab, setActiveTab] = useState(
    tCampaigns("viewCampaign.tabTitles.goal"),
  );

  const [
    getViewData,
    {
      data: campaignData,
      isLoading: isViewDataLoading,
      isError: isViewDataError,
    },
  ] = useLazyViewCampaignQuery();

  const [
    getSplitCostData,
    {
      data: costSplitData,
      isLoading: isSplitCostLoading,
      isError: isSplitCostError,
    },
  ] = useLazySplitCostCampaignQuery();

  // Separate queries for CITY and VENUE_TYPE cost splits
  const [getSplitCostByCity, { data: costSplitByCityData }] =
    useLazySplitCostCampaignQuery();

  const [getSplitCostByVenueType, { data: costSplitByVenueTypeData }] =
    useLazySplitCostCampaignQuery();

  const [fetchPriceSummary, { data: priceSummaryResponse }] =
    useLazyGetPriceSummaryQuery();

  useEffect(() => {
    if (campaignId) {
      getViewData(campaignId);
      getSplitCostData({
        campaignId: campaignId,
        splitBy: splitBy,
        language,
      });
      // Make calls for CITY and VENUE_TYPE cost splits
      getSplitCostByCity({
        campaignId: campaignId,
        splitBy: "CITY",
        language,
      });
      getSplitCostByVenueType({
        campaignId: campaignId,
        splitBy: "VENUE_TYPE",
        language,
      });
      // Fetch price summary
      fetchPriceSummary({ campaignId });
    }
  }, [
    campaignId,
    language,
    getViewData,
    getSplitCostData,
    getSplitCostByCity,
    getSplitCostByVenueType,
    fetchPriceSummary,
  ]);

  const handleSplitByChange = (newSplitBy: string) => {
    setSplitBy(newSplitBy);
    if (campaignId) {
      getSplitCostData({
        campaignId: campaignId,
        splitBy: newSplitBy,
        language,
      });
    }
  };

  // Transform cost split data into targeting data structure
  const targetingDataWithCostSplit = useMemo(() => {
    const baseTargeting = campaignData?.data?.targeting || {};

    // Transform CITY cost split data
    const cities =
      costSplitByCityData?.data?.map((item) => ({
        name: item.name || "--",
        impressions: item.impressions || 0,
        adPlays: item.totalInventories || 0, // Using totalInventories as adPlays approximation
        allocatedBudget: item.totalAmountInPercentage || 0,
      })) || [];

    // Transform VENUE_TYPE cost split data
    const venueTypes =
      costSplitByVenueTypeData?.data?.map((item) => ({
        name: item.name || "--",
        impressions: item.impressions || 0,
        adPlays: item.totalInventories || 0, // Using totalInventories as adPlays approximation
        allocatedBudget: item.totalAmountInPercentage || 0,
      })) || [];

    return {
      ...baseTargeting,
      geographicTargeting: {
        ...baseTargeting.geographicTargeting,
        cities:
          cities.length > 0
            ? cities
            : baseTargeting.geographicTargeting?.cities || [],
        venueTypes:
          venueTypes.length > 0
            ? venueTypes
            : baseTargeting.geographicTargeting?.venueTypes || [],
      },
    };
  }, [
    campaignData?.data?.targeting,
    costSplitByCityData?.data,
    costSplitByVenueTypeData?.data,
  ]);

  // Total for the Cost Split header chip — computed from the cost-split cards
  // because campaignData.costBreakdown.totalCost is not always populated,
  // which left the chip showing "Total : -" (DEF-PLN-012).
  const costSplitTotal = useMemo(() => {
    if (!costSplitData?.data?.length) return undefined;
    return costSplitData.data.reduce(
      (sum, item: CostSplitByCampaignData) => sum + (item.totalAmount || 0),
      0,
    );
  }, [costSplitData?.data]);

  if (isViewDataLoading) {
    return <Loading overlay={true} text="" variant="primary" />;
  }

  if (isViewDataError || !campaignData) {
    return <div>{tCampaigns("viewCampaign.errorLoadingData")}</div>;
  }

  return (
    <div className="space-y-4">
      {/* Top Row - Campaign Details and Key Stakeholders */}
      <Card className="p-4 border-none space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Campaign Details Card */}
          <Card>
            <CardHeader className="p-4">
              <CardTitle className="text-base font-medium border-b border-container-border pb-4 leading-5">
                {tCampaigns("create_campaign.steps.campaign_details")}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-4">
                {/* Country Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("agency_form.country")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {campaignData.data?.campaignDetail.country || "--"}
                    </p>
                  </div>
                </Card>

                {/* Budget Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("viewCampaign.budget")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {formatCurrencyWithLocale(
                        campaignData.data?.campaignDetail.budget,
                        campaignData.data?.currency,
                      ) || "--"}
                    </p>
                  </div>
                </Card>

                {/* Campaign Start Date Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("viewCampaign.campaignStartDate")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {formatDisplayDate(
                        campaignData.data?.campaignDetail.startDate ?? "--",
                        tCommon,
                      )}
                    </p>
                  </div>
                </Card>

                {/* Campaign End Date Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("viewCampaign.campaignEndDate")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {formatDisplayDate(
                        campaignData.data?.campaignDetail.endDate ?? "--",
                        tCommon,
                      )}
                    </p>
                  </div>
                </Card>
              </div>
            </CardContent>
          </Card>

          {/* Campaign Key Stakeholders Card */}
          <Card>
            <CardHeader className="p-4">
              <CardTitle className="text-base font-medium border-b border-container-border pb-4 leading-5">
                {tCampaigns("viewCampaign.campaignKeyStakeholders")}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-4">
                {/* Agency Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("create_campaign.form.agency")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {campaignData.data?.keyStakeholderDetail?.agency || "--"}
                    </p>
                  </div>
                </Card>

                {/* Planner Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("viewCampaign.planner")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {campaignData.data?.keyStakeholderDetail?.planner || "--"}
                    </p>
                  </div>
                </Card>

                {/* Brand Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("create_campaign.form.brand")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {campaignData.data?.keyStakeholderDetail?.brand || "--"}
                    </p>
                  </div>
                </Card>

                {/* Brand Category Sub-card */}
                <Card className="p-4">
                  <div className="space-y-1">
                    <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                      {tCampaigns("viewCampaign.brandCategory")}
                    </p>
                    <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                      {campaignData.data?.keyStakeholderDetail?.brandCategory ||
                        "--"}
                    </p>
                  </div>
                </Card>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Cost Split Card */}
        <Card>
          <CardHeader className="p-4">
            <div className="flex items-center justify-between border-b border-container-border pb-4">
              <CardTitle className="text-base font-medium leading-5 flex items-center gap-2">
                {tCampaigns("viewCampaign.costSplit")}
                <div className="px-2 py-1 bg-mw-primary-100 text-mw-primary-500 text-xs font-medium leading-4 rounded border border-mw-primary-500">
                  {tCampaigns("viewCampaign.totalLabel")} :&nbsp;
                  {formatCurrencyWithLocale(
                    campaignData.data?.costBreakdown?.totalCost ??
                      costSplitTotal,
                    campaignData.data?.currency,
                  )}
                </div>
              </CardTitle>

              {/* Split by Dropdown */}
              <div className="flex items-center gap-2">
                <span className="text-sm font-normal text-mw-neutral-800 dark:text-mw-neutral-800 leading-4">
                  {tCampaigns("viewCampaign.splitByLabel")}:
                </span>
                <Dropdown>
                  <DropdownTrigger asChild>
                    <Button
                      variant="outline"
                      size="lg"
                      className="flex items-start gap-2"
                    >
                      <span className="text-sm">
                        {tCampaigns(`viewCampaign.${splitBy}`)}
                      </span>
                      <ChevronDown className="w-4 h-4" />
                    </Button>
                  </DropdownTrigger>
                  <DropdownContent align="left">
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("MEDIA_OWNER");
                      }}
                      value="MEDIA_OWNER"
                    >
                      {tCampaigns(`viewCampaign.MEDIA_OWNER`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("SIZE");
                      }}
                      value="SIZE"
                    >
                      {tCampaigns(`viewCampaign.SIZE`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("INVENTORY_TYPE");
                      }}
                      value="INVENTORY_TYPE"
                    >
                      {tCampaigns(`viewCampaign.INVENTORY_TYPE`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("COUNTRY");
                      }}
                      value="COUNTRY"
                    >
                      {tCampaigns(`viewCampaign.COUNTRY`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("STATE");
                      }}
                      value="STATE"
                    >
                      {tCampaigns(`viewCampaign.STATE`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("CITY");
                      }}
                      value="CITY"
                    >
                      {tCampaigns(`viewCampaign.CITY`)}
                    </DropdownItem>
                    <DropdownItem
                      onClick={() => {
                        handleSplitByChange("VENUE_TYPE");
                      }}
                      value="VENUE_TYPE"
                    >
                      {tCampaigns(`viewCampaign.VENUE_TYPE`)}
                    </DropdownItem>
                  </DropdownContent>
                </Dropdown>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {isSplitCostLoading && <Loading overlay={true} />}
            {isSplitCostError && (
              <div>{tCampaigns("campaignDetails.errorLoadingSplitCost")}</div>
            )}
            {costSplitData?.data && (
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                {costSplitData?.data?.map(
                  (item: CostSplitByCampaignData, index: number) => (
                    <Card key={index} className="p-4">
                      <div className="space-y-2">
                        <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4 capitalize">
                          {item.name}
                        </p>
                        <p className="text-lg font-semibold text-mw-primary-500 dark:text-mw-primary-400 leading-6">
                          {formatCurrencyWithLocale(
                            item.totalAmount,
                            campaignData.data?.currency,
                          ) || "--"}
                        </p>
                        <p className="text-sm font-normal text-mw-neutral-500 dark:text-mw-neutral-400 leading-4">
                          {item.totalAmountInPercentage.toFixed(2)}%{" "}
                          {tCampaigns("viewCampaign.ofTotalCost")}
                        </p>
                      </div>
                    </Card>
                  ),
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </Card>

      {/* Bottom Tabs */}
      <Card className="p-4 border-none">
        <Tabs
          value={activeTab}
          onValueChange={setActiveTab}
          className="h-full bg-white"
        >
          <TabsList className="grid w-full grid-cols-7 mb-4">
            <TabsTrigger value={tCampaigns("viewCampaign.tabTitles.goal")}>
              {tCampaigns("viewCampaign.tabTitles.goal")}
            </TabsTrigger>
            <TabsTrigger value={tCampaigns("targeting.title")}>
              {tCampaigns("targeting.title")}
            </TabsTrigger>
            <TabsTrigger value={tCampaigns("viewCampaign.tabTitles.inventory")}>
              {tCampaigns("viewCampaign.tabTitles.inventory")}
            </TabsTrigger>
            <TabsTrigger
              value={tCampaigns("optimization.schedulingTargeting.tabtitle")}
            >
              {tCampaigns("optimization.schedulingTargeting.tabtitle")}
            </TabsTrigger>
            <TabsTrigger
              value={tCampaigns("viewCampaign.tabTitles.performance")}
            >
              {tCampaigns("viewCampaign.tabTitles.performance")}
            </TabsTrigger>
            <TabsTrigger
              value={tCampaigns("viewCampaign.tabTitles.costBreakdown")}
            >
              {tCampaigns("viewCampaign.tabTitles.costBreakdown")}
            </TabsTrigger>
            <TabsTrigger value={tCampaigns("viewCampaign.tabTitles.comments")}>
              {tCampaigns("viewCampaign.tabTitles.comments")}
            </TabsTrigger>
          </TabsList>
          {/* Goal Tab */}
          <TabsContent
            value={tCampaigns("viewCampaign.tabTitles.goal")}
            className="p-0!"
          >
            <CampaignGoalView
              campaignId={campaignData.data?.id}
              goals={campaignData.data?.goals}
            />
          </TabsContent>
          {/* Targeting Tab */}
          <TabsContent value={tCampaigns("targeting.title")}>
            <CampaignDetailsTargetingTabInfo
              tabData={targetingDataWithCostSplit}
              campaignCurrency={campaignData.data?.currency}
              campaignId={campaignData.data?.id}
              campaignCountry={campaignData.data?.campaignDetail.country}
              isMapViewVisible={true}
            />
          </TabsContent>
          {/* Inventory Tab */}
          <TabsContent
            value={tCampaigns("viewCampaign.tabTitles.inventory")}
            className="p-0!"
          >
            <InventoryOverview
              inventoryOverViewData={campaignData.data?.inventoryOverview}
              forecastData={campaignData.data?.performance}
              campaignCurrency={campaignData.data?.currency}
              campaignId={campaignData.data?.id}
              campaignStartDate={campaignData.data?.campaignDetail.startDate}
              campaignEndDate={campaignData.data?.campaignDetail.endDate}
              goalType={campaignData.data?.goals?.goalType}
              isNegotiated={campaignData.data?.isNegotiated}
            />
          </TabsContent>
          {/* Schedule Tab */}
          <TabsContent
            value={tCampaigns("optimization.schedulingTargeting.tabtitle")}
            className="p-0!"
          >
            <ScheduleOverview
              forecastData={campaignData.data?.performance}
              campaignCurrency={campaignData.data?.currency}
              campaignId={campaignData.data?.id}
              campaignStartDate={campaignData.data?.campaignDetail?.startDate}
              campaignEndDate={campaignData.data?.campaignDetail?.endDate}
            />
          </TabsContent>
          {/* Performance Tab */}
          <TabsContent
            value={tCampaigns("viewCampaign.tabTitles.performance")}
            className="p-0!"
          >
            <PerformanceMetrics
              forecastData={campaignData.data?.performance}
              campaignCurrency={campaignData.data?.currency}
            />
          </TabsContent>
          {/* Cost Breakdown Tab */}
          <TabsContent
            value={tCampaigns("viewCampaign.tabTitles.costBreakdown")}
            className="p-0!"
          >
            <CostBreakdown
              costBreakDownData={priceSummaryResponse?.data}
              currency={campaignData.data?.currency}
              campaignId={campaignData.data?.id}
              onRefresh={() => {
                // Refetch campaign data to get updated cost breakdown
                if (campaignId) {
                  getViewData(campaignId);
                  // Refetch price summary
                  if (fetchPriceSummary) {
                    fetchPriceSummary({ campaignId });
                  }
                }
              }}
            />
          </TabsContent>
          {/* Comments Tab */}
          <TabsContent
            value={tCampaigns("viewCampaign.tabTitles.comments")}
            className="p-0!"
          >
            <CommentsTab
              campaignId={campaignData.data?.id}
              onAddComment={(comment, mentions) => {
                console.log("New comment:", comment, "Mentions:", mentions);
                // TODO: Implement API call to save comment
              }}
            />
          </TabsContent>
        </Tabs>
      </Card>
    </div>
  );
};

export default CampaignDetails;
