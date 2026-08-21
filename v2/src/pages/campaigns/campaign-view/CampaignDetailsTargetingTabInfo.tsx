import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader } from "@components/ui/card";
import {
  ConfigurationsMetaData,
  DemographicMetaData,
  setConfigurationMetaData,
  useConfigurationMetadataQuery,
} from "@services/configuration-metadata/configurationMetadataSlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import { MapPin, MapPinHouse } from "lucide-react";
import React, { useEffect, useMemo, useState } from "react";
import { useAppDispatch, useAppSelector } from "src/store";

import { toCountrySlug } from "../../../components/map/MobilityHeatmap";
import { ViewCampaign } from "../../../types/campaign.types";
import InventoryMapViewDrawer from "../inventory/InventoryMapViewDrawer";

interface CampaignDetailsTargetingTabInfoProps {
  tabData?: ViewCampaign["targeting"];
  campaignCurrency?: string;
  campaignId?: string;
  /** Campaign target-country display name (drives the mobility heatmap). */
  campaignCountry?: string;
  isMapViewVisible?: boolean;
  forMediaPlan?: boolean;
}

const CampaignDetailsTargetingTabInfo: React.FC<
  CampaignDetailsTargetingTabInfoProps
> = ({
  tabData,
  campaignCurrency,
  campaignId,
  campaignCountry,
  isMapViewVisible = false,
  forMediaPlan = false,
}) => {
  const { t: tCampaigns } = useTranslate("campaigns");
  const language = useTolgee(["language"]).getLanguage();
  const [isMapViewDrawerOpen, setIsMapViewDrawerOpen] = useState(false);
  const dispatch = useAppDispatch();

  // Get demographic metadata from Redux state
  const demographicMetaDataState = useAppSelector(
    (state) => state.configurationMetadata.demographics,
  );

  // Check if we have data in state
  const hasStateData = useMemo(() => {
    return Object.values(demographicMetaDataState).some(
      (arr) => Array.isArray(arr) && arr.length > 0,
    );
  }, [demographicMetaDataState]);

  // Always fetch with current language so displayed names reflect the active locale.
  const { data: demographicsMetadata } = useConfigurationMetadataQuery({
    language,
  });

  // Transform API data to TreeNode format
  const transformDemographicsData = useMemo(() => {
    // First priority: RTK Query data (language-aware via Accept-Language header)
    if (
      demographicsMetadata &&
      "success" in demographicsMetadata &&
      demographicsMetadata.success
    ) {
      const apiData = (demographicsMetadata as { data: ConfigurationsMetaData })
        .data.demographics;

      if (!apiData) {
        return {
          age: [],
          gender: [],
          income: [],
          interests: [],
          venues: [],
          behavior: [],
        };
      }

      // Check if apiData is an array (flat structure) or object (grouped structure)
      if (Array.isArray(apiData)) {
        // Group data by demoType if it's an array
        const groupedData = apiData.reduce(
          (acc, item) => {
            const demoType = item.demoType || "unknown";
            if (!acc[demoType]) {
              acc[demoType] = [];
            }
            acc[demoType].push(item);
            return acc;
          },
          {} as Record<string, DemographicMetaData[]>,
        );

        return {
          age: groupedData["age"] || [],
          gender: groupedData["gender"] || [],
          income: groupedData["income"] || [],
          interests: groupedData["interests"] || [],
          venues: groupedData["venues"] || [],
          behavior: groupedData["behavior"] || [],
        };
      } else {
        // Data is already grouped as an object
        return {
          age: apiData.age || [],
          gender: apiData.gender || [],
          income: apiData.income || [],
          interests: apiData.interests || [],
          venues: apiData.venues || [],
          behavior: apiData.behavior || [],
        };
      }
    }

    // Fallback: Redux state (may be stale language, used while new language data loads)
    if (hasStateData) {
      return {
        age: demographicMetaDataState.age || [],
        gender: demographicMetaDataState.gender || [],
        income: demographicMetaDataState.income || [],
        interests: demographicMetaDataState.interests || [],
        venues: demographicMetaDataState.venues || [],
        behavior: demographicMetaDataState.behavior || [],
      };
    }

    return {
      age: [],
      gender: [],
      income: [],
      interests: [],
      venues: [],
      behavior: [],
    };
  }, [hasStateData, demographicMetaDataState, demographicsMetadata]);

  const audienceData = useMemo(() => {
    if (tabData?.audienceDemographics) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const audeinceData: any = {};
      if (
        tabData.audienceDemographics.ageGroups &&
        tabData.audienceDemographics.ageGroups.length
      ) {
        const ageGroups = tabData.audienceDemographics.ageGroups;
        ageGroups.forEach((ageGroup) => {
          const ageData = transformDemographicsData.age.find(
            (age: DemographicMetaData) => age.demoKey === ageGroup,
          );
          if (ageData?.name) {
            audeinceData.ageGroups = audeinceData.ageGroups || [];
            audeinceData.ageGroups.push(ageData.name);
          }
        });
      }
      if (
        tabData.audienceDemographics.incomeLevel &&
        tabData.audienceDemographics.incomeLevel.length
      ) {
        const incomeLevel = tabData.audienceDemographics.incomeLevel;
        incomeLevel.forEach((incomeGroup) => {
          const incomeData = transformDemographicsData.income.find(
            (income: DemographicMetaData) => income.demoKey === incomeGroup,
          );
          if (incomeData?.name) {
            audeinceData.incomeLevel = audeinceData.incomeLevel || [];
            audeinceData.incomeLevel.push(incomeData.name);
          }
        });
      }
      if (
        tabData.audienceDemographics.interests &&
        tabData.audienceDemographics.interests.length
      ) {
        const interests = tabData.audienceDemographics.interests;
        interests.forEach((interestKey) => {
          const interestData = transformDemographicsData.interests.find(
            (interest: DemographicMetaData) => interest.demoKey === interestKey,
          );
          if (interestData?.name) {
            audeinceData.interests = audeinceData.interests || [];
            audeinceData.interests.push(interestData.name);
          }
        });
      }
      if (
        tabData.audienceDemographics.lifestyle &&
        tabData.audienceDemographics.lifestyle.length
      ) {
        const lifestyle = tabData.audienceDemographics.lifestyle;
        lifestyle.forEach((behaviorKey) => {
          const behaviorData = transformDemographicsData.behavior.find(
            (behavior: DemographicMetaData) => behavior.demoKey === behaviorKey,
          );
          if (behaviorData?.name) {
            audeinceData.lifestyle = audeinceData.lifestyle || [];
            audeinceData.lifestyle.push(behaviorData.name);
          }
        });
      }
      return audeinceData;
    }
    return {
      age: [],
      interests: [],
      behavior: [],
      incomes: [],
    };
  }, [tabData, transformDemographicsData]);

  // Update Redux state when API data is received
  useEffect(() => {
    if (
      demographicsMetadata &&
      "success" in demographicsMetadata &&
      demographicsMetadata.success
    ) {
      dispatch(setConfigurationMetaData(demographicsMetadata));
    }
  }, [demographicsMetadata, dispatch]);

  return (
    <div className="p-2">
      <Card className="p-2 px-4">
        <CardHeader className="pb-4 border-b border-container-border">
          <span className="text-mw-neutral-800 text-m font-semibold">
            {tCampaigns("viewCampaign.targetingTab.audienceTitle")}
          </span>
        </CardHeader>
        <CardContent className="p-0!">
          <div
            className={`grid pt-4 pb-2 gap-4 ${forMediaPlan ? "" : "grid-cols-2"}`}
          >
            <div className="flex flex-col gap-1">
              <span className="text-mw-neutral-500 text-sm">
                {tCampaigns("viewCampaign.targetingTab.ageGroups")}
              </span>
              <div className="inline-flex gap-1.5 flex-wrap">
                {audienceData?.ageGroups ? (
                  audienceData?.ageGroups?.map((ageGroup: string) => (
                    <Badge
                      key={ageGroup}
                      className="outline-mw-neutral-100! text-black!"
                    >
                      {ageGroup}
                    </Badge>
                  ))
                ) : (
                  <span className="text-mw-neutral-500 text-sm">--</span>
                )}
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-mw-neutral-500 text-sm">
                {tCampaigns("viewCampaign.targetingTab.incomeLevel")}
              </span>
              <div className="inline-flex gap-1.5 flex-wrap">
                {audienceData?.incomeLevel ? (
                  audienceData?.incomeLevel?.map((income: string) => (
                    <Badge
                      key={income}
                      className="outline-mw-neutral-100! text-black!"
                    >
                      {income}
                    </Badge>
                  ))
                ) : (
                  <span className="text-mw-neutral-500 text-sm">--</span>
                )}
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-mw-neutral-500 text-sm">
                {tCampaigns("viewCampaign.targetingTab.interests")}
              </span>
              <div className="inline-flex gap-1.5 flex-wrap">
                {audienceData?.interests ? (
                  audienceData?.interests?.map((interest: string) => (
                    <Badge
                      key={interest}
                      className="outline-mw-neutral-100! text-black!"
                    >
                      {interest}
                    </Badge>
                  ))
                ) : (
                  <span className="text-mw-neutral-500 text-sm">--</span>
                )}
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-mw-neutral-500 text-sm">
                {tCampaigns("viewCampaign.targetingTab.lifestyle")}
              </span>
              <div className="inline-flex gap-1.5 flex-wrap">
                {audienceData?.lifestyle ? (
                  audienceData?.lifestyle?.map((ls: string) => (
                    <Badge
                      key={ls}
                      className="outline-mw-neutral-100! text-black!"
                    >
                      {ls}
                    </Badge>
                  ))
                ) : (
                  <span className="text-mw-neutral-500 text-sm">--</span>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
      {!forMediaPlan && (
        <Card className="p-4 px-4 mt-4">
          <CardHeader className="pb-4 border-b border-container-border">
            <div className="flex justify-between items-center">
              <span className="text-mw-neutral-800 text-m font-semibold">
                {tCampaigns("viewCampaign.targetingTab.geographyTitle")}
              </span>
              {isMapViewVisible && (
                <Button
                  variant="outline"
                  className="text-mw-primary-500 outline-mw-primary-500 flex gap-1"
                  onClick={() => setIsMapViewDrawerOpen(true)}
                >
                  <MapPin size={14} />
                  {tCampaigns("viewCampaign.targetingTab.mapView")}
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent className="p-0!">
            <div className="flex gap-4">
              <Card className="p-4 mt-4 flex-1">
                <CardHeader className="pb-4 border-b border-container-border">
                  <span className="text-mw-neutral-800 text-m font-medium inline-flex gap-2 items-center">
                    <MapPin size={20} />
                    {tCampaigns("viewCampaign.targetingTab.byCityTitle")}
                  </span>
                </CardHeader>
                <CardContent className="p-0! pt-5! max-h-96 flex flex-col gap-4 overflow-y-auto scrollbar-thin">
                  {tabData?.geographicTargeting?.cities ? (
                    tabData?.geographicTargeting?.cities?.map((city, index) => (
                      <Card
                        key={`${city.name ?? "city"}-${index}`}
                        className="p-4"
                      >
                        <CardHeader className="pb-4 border-b-1 border-container-border">
                          <div className="text-mw-neutral-800 text-m font-medium inline-flex gap-1.5 items-center justify-between">
                            <span>{city.name || "--"}</span>
                            {/* If 50 and above then show success*/}
                            <Badge
                              variant={
                                Number(city.allocatedBudget) >= 50
                                  ? "success"
                                  : Number(city.allocatedBudget) >= 25
                                    ? "secondary"
                                    : "default"
                              }
                              size="sm"
                              className={
                                Number(city.allocatedBudget) >= 50
                                  ? "bg-mw-success-100! text-mw-success-500! font-semibold"
                                  : Number(city.allocatedBudget) >= 25
                                    ? ""
                                    : "bg-mw-warning-100! text-mw-warning-500! font-semibold"
                              }
                            >
                              {Number(city.allocatedBudget).toFixed(2)}%
                              {tCampaigns("viewCampaign.targetingTab.ofTotal")}
                            </Badge>
                          </div>
                        </CardHeader>
                        <CardContent className="p-0! pt-4! grid gap-2">
                          <div className="flex">
                            <span className="flex-1 text-xs text-mw-neutral-700">
                              {tCampaigns("budget_goal.goal_types.impressions")}
                            </span>
                            <span className="text-xs text-black font-semibold">
                              {formatNumber(Number(city.impressions))}
                            </span>
                          </div>
                          <div className="flex">
                            <span className="flex-1 text-xs text-mw-neutral-700">
                              {tCampaigns("viewCampaign.targetingTab.adPlays")}
                            </span>
                            <span className="text-xs text-black font-semibold">
                              {formatNumber(Number(city.adPlays))}
                            </span>
                          </div>
                        </CardContent>
                      </Card>
                    ))
                  ) : (
                    <span className="text-mw-neutral-500 text-sm">--</span>
                  )}
                </CardContent>
              </Card>
              <Card className="p-4 mt-4 flex-1">
                <CardHeader className="pb-4 border-b border-container-border">
                  <span className="text-mw-neutral-800 text-m font-medium inline-flex gap-2">
                    <MapPinHouse size={20} />
                    {tCampaigns("viewCampaign.targetingTab.byVenueTypeTitle")}
                  </span>
                </CardHeader>
                <CardContent className="p-0! pt-5! max-h-96 flex flex-col gap-4 overflow-y-auto scrollbar-thin">
                  {tabData?.geographicTargeting?.venueTypes ? (
                    tabData?.geographicTargeting?.venueTypes?.map(
                      (venueType, index) => (
                        <Card
                          key={`${venueType.name ?? "venue"}-${index}`}
                          className="p-4"
                        >
                          <CardHeader className="pb-4 border-b border-container-border">
                            <div className="text-mw-neutral-800 text-m font-medium inline-flex gap-2 items-center justify-between">
                              <span>{venueType.name || "--"}</span>
                              {/* If 50 and above then show success*/}
                              <Badge
                                variant={
                                  Number(venueType.allocatedBudget) >= 50
                                    ? "success"
                                    : Number(venueType.allocatedBudget) >= 25
                                      ? "secondary"
                                      : "default"
                                }
                                size="sm"
                                className={
                                  Number(venueType.allocatedBudget) >= 50
                                    ? "bg-mw-success-100! text-mw-success-500! font-semibold"
                                    : Number(venueType.allocatedBudget) >= 25
                                      ? ""
                                      : "bg-mw-warning-100! text-mw-warning-500! font-semibold"
                                }
                              >
                                {Number(venueType.allocatedBudget).toFixed(2)}%
                                {tCampaigns(
                                  "viewCampaign.targetingTab.ofTotal",
                                )}
                              </Badge>
                            </div>
                          </CardHeader>
                          <CardContent className="p-0! pt-4! grid gap-2">
                            <div className="flex">
                              <span className="flex-1 text-xs text-mw-neutral-700">
                                {tCampaigns(
                                  "budget_goal.goal_types.impressions",
                                )}
                              </span>
                              <span className="text-xs text-black font-semibold">
                                {formatNumber(Number(venueType.impressions))}
                              </span>
                            </div>
                            <div className="flex">
                              <span className="flex-1 text-xs text-mw-neutral-700">
                                {tCampaigns(
                                  "viewCampaign.targetingTab.adPlays",
                                )}
                              </span>
                              <span className="text-xs text-black font-semibold">
                                {formatNumber(Number(venueType.adPlays))}
                              </span>
                            </div>
                          </CardContent>
                        </Card>
                      ),
                    )
                  ) : (
                    <span className="text-mw-neutral-500 text-sm">--</span>
                  )}
                </CardContent>
              </Card>
            </div>
          </CardContent>
        </Card>
      )}
      {/* Map View Drawer */}
      {campaignId && isMapViewVisible && (
        <InventoryMapViewDrawer
          isOpen={isMapViewDrawerOpen}
          onClose={() => setIsMapViewDrawerOpen(false)}
          campaignCurrency={campaignCurrency}
          campaignId={campaignId as string}
          showDeleteInventoryButton={false}
          mobilityCountrySlug={toCountrySlug(campaignCountry)}
        />
      )}
    </div>
  );
};

export default CampaignDetailsTargetingTabInfo;
