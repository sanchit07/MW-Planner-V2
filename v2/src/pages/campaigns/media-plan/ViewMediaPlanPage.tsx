import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Card } from "@components/ui/card";
import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { MapControlsConfig } from "@components/ui/Mapbox";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useAnnounce } from "@hooks/useAnnounce";
import { useGetCampaignQuery } from "@services/campaign/campaignSlice";
import {
  useGeneratePublicTokenMutation,
  useLazyGetPublicInventoriesQuery,
} from "@services/public-access/publicAccessSlice";
import { useTranslate } from "@tolgee/react";
import { generateMapImage } from "@utils/chartImageGenerator";
import { extractGeofencingPOIs } from "@utils/geofencing-pois";
import { generateMediaPlanExcel } from "@utils/mediaPlanExcelGenerator";
import { generateMediaPlanPPT } from "@utils/mediaPlanPPTGenerator";
import { ArrowLeft, MoreHorizontal } from "lucide-react";
import type { Map } from "mapbox-gl";
import React, { useEffect, useMemo, useState, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";

import AnalyticsView from "./AnalyticsView";
import { THEMES_COLORS } from "./constants";
import {
  addInventoryRadiusLayer,
  computeMediaPlanMapView,
  countPlottedSites,
  fitMapToInventory,
} from "./mapView.utils";
import MediaPlanAudienceMap from "./MediaPlanAudienceMap";
import MediaPlanAudienceTrends from "./MediaPlanAudienceTrends";
import MediaPlanGeographicPlan from "./MediaPlanGeographicPlan";
import MediaPlanGoalsKpis from "./MediaPlanGoalsKpis";
import MediaPlanHeader from "./MediaPlanHeader";
import MediaPlanInventoryMix from "./MediaPlanInventoryMix";
import MediaPlanInventorySnapshots from "./MediaPlanInventorySnapshots";
import MediaPlanPerformanceMetrics from "./MediaPlanPerformanceMetrics";
import MediaPlanTargeting from "./MediaPlanTargeting";
import MediaPlanTitleSlide from "./MediaPlanTitleSlide";
import MediaPlanWhyThisPlanWorks from "./MediaPlanWhyThisPlanWorks";
import ShareModalDrawer from "./ShareModalDrawer";
import { transformMediaPlanData } from "./transformMediaPlanData";
import { ViewType } from "./types";
import { useMediaPlanData } from "./useMediaPlanData";
import { computeExpectedDelivery } from "./utils";
import { MediaPlanResponse } from "../../../types/campaign.types";
import { MapInventoryItem } from "../../../types/inventory.types";
import { CampaignActionsDropdownContent } from "../components/CampaignActionsDropdownContent";
import { applyMapLights } from "../inventory/mapConfig";
import { useReachCurve } from "../inventory/plan-summary/useReachCurve";

const ViewMediaPlanPage: React.FC = () => {
  const navigate = useNavigate();
  const { campaignId } = useParams<{ campaignId: string }>();
  const { t } = useTranslate(["campaigns"]);
  const [viewType, setViewType] = useState<ViewType>("presentation");
  const [selectedTheme, setSelectedTheme] = useState("default");
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);
  const [shareUrl, setShareUrl] = useState("");
  const [isDownloading, setIsDownloading] = useState(false);
  const [recentlyShared, setRecentlyShared] = useState<
    Array<{ email: string; initials?: string; avatar?: string }>
  >([]);
  const { showSuccess, showError } = useAnnounce();
  const mapInstanceRef = useRef<Map | null>(null);

  // Public access API hooks
  const [generatePublicToken] = useGeneratePublicTokenMutation();
  const [getPublicInventories] = useLazyGetPublicInventoriesQuery();
  const [isLoadingMapView, setIsLoadingMapView] = useState(false);

  // Fetch all media plan data using unified hook
  const mediaPlanData = useMediaPlanData(campaignId);

  const availableThemes = THEMES_COLORS;
  const theme =
    availableThemes.find((t) => t.id === selectedTheme) || availableThemes[0];

  const handleViewTypeChange = (newViewType: ViewType) => {
    setViewType(newViewType);
    const newAvailableThemes = THEMES_COLORS;
    if (!newAvailableThemes.find((t) => t.id === selectedTheme)) {
      setSelectedTheme(newAvailableThemes[0].id);
    }
  };

  const handleBack = () => {
    navigate("/campaigns");
  };

  const backButton = (
    <div onClick={handleBack}>
      <ArrowLeft className="w-5 h-5" cursor="pointer" />
    </div>
  );

  // Map configuration
  const mapConfig: MapControlsConfig = {
    showDrawingTools: false,
    enableSelect: false,
    enablePolygon: false,
    enableCircle: false,
    enableLine: false,
    enableDelete: false,
    showViewTools: true,
    enableMountainView: false,
    enable3D: true,
    showMapStyles: false,
    enabledStyles: [],
    search: {
      enabled: false,
      showResults: false,
      searchTypes: [],
      limit: 5,
      showPOIFilter: false,
    },
  };

  // Map selectedInventory items to MapInventoryItem format for map view
  const mappedInventoryList: MapInventoryItem[] = useMemo(() => {
    return mediaPlanData?.selectedInventory?.locations || [];
  }, [mediaPlanData?.selectedInventory?.locations]);

  // POI places for the audience map — parsed from the campaign's geofencing
  // metadata (same GET /campaigns/{id} response, RTK-cached / deduped).
  const { data: campaignDetail } = useGetCampaignQuery(campaignId || "", {
    skip: !campaignId,
  });
  const availablePOIs = useMemo(
    () => extractGeofencingPOIs(campaignDetail?.data),
    [campaignDetail?.data],
  );

  // Cumulative reach-build curve — same source as the Audience Trends section,
  // lifted here so it can be embedded in the downloaded PPT.
  const { overallReach: reachCurveData, labels: reachCurveLabels } =
    useReachCurve(
      campaignId || "",
      Boolean(campaignId),
      mediaPlanData?.headerInfo?.startDate,
      mediaPlanData?.headerInfo?.endDate,
    );

  // Center on the inventory centroid; neutral world view when the plan has
  // no parseable coordinates (never a wrong country — SI 41)
  const mapView = useMemo(
    () =>
      computeMediaPlanMapView(
        mediaPlanData?.selectedInventory?.locations || [],
      ),
    [mediaPlanData?.selectedInventory?.locations],
  );

  // Summary counts for the Audience Map section. Densest market = the city
  // with the most impressions (from the CITY cost-split).
  const audienceMapSummary = useMemo(() => {
    const cities = mediaPlanData?.costSplitByCity || [];
    const densest = cities.reduce<(typeof cities)[number] | null>(
      (top, c) =>
        !top || (c.impressions || 0) > (top.impressions || 0) ? c : top,
      null,
    );
    return {
      // Sites pinned on the map = inventory pins + POI pins.
      sitesPinned:
        countPlottedSites(mappedInventoryList) + availablePOIs.length,
      marketCount: cities.length,
      totalInventory: mediaPlanData?.forecastData?.totalInventories || 0,
      densestMarket: densest?.name || "",
    };
  }, [
    mediaPlanData?.costSplitByCity,
    mediaPlanData?.forecastData?.totalInventories,
    mappedInventoryList,
    availablePOIs,
  ]);

  // Re-apply the audience heatmap when inventory data changes — the map can
  // mount before selected inventory has loaded, so onMapReady alone may run
  // with no points. addInventoryRadiusLayer updates the existing source when
  // present, or adds it once the map + data are both ready.
  useEffect(() => {
    if (mapInstanceRef.current) {
      fitMapToInventory(
        mapInstanceRef.current,
        mappedInventoryList,
        availablePOIs,
      );
      addInventoryRadiusLayer(mapInstanceRef.current, mappedInventoryList);
    }
  }, [mappedInventoryList, availablePOIs]);

  const handleDownload = async () => {
    if (isDownloading || !mediaPlanData || mediaPlanData.isAnyApiLoading) {
      return;
    }

    setIsDownloading(true);
    try {
      if (viewType === "presentation") {
        try {
          let mapImage: string | undefined;
          let mapImageLink: string | undefined;

          // Capture map image
          if (mapInstanceRef.current) {
            mapImage = await generateMapImage({
              mapInstance: mapInstanceRef.current,
              waitTime: 1000,
              backgroundColor: null,
              scale: 1,
              imageFormat: "image/jpeg",
              imageQuality: 0.8,
              errorMessage: "Failed to capture map image",
            });

            // Generate public token for offline PPT access
            // This ensures the map link in the downloaded PPT will work
            if (campaignId) {
              try {
                const tokenResponse =
                  await generatePublicToken(campaignId).unwrap();
                if (tokenResponse.success && tokenResponse.data?.publicToken) {
                  const publicToken = tokenResponse.data.publicToken;
                  mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}?token=${publicToken}`;
                } else {
                  // Fallback: URL without token (user will need to authenticate)
                  mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}`;
                  console.warn(
                    "Failed to generate public token for PPT map link",
                  );
                }
              } catch (tokenError) {
                // Fallback: URL without token if token generation fails
                mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}`;
                console.error(
                  "Error generating public token for PPT:",
                  tokenError,
                );
              }
            } else {
              mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}`;
            }
          }

          // Transform selectedInventory to match MediaPlanResponse structure
          const transformedSelectedInventory = mediaPlanData.selectedInventory
            ? {
                summaryStatistics: {
                  totalAssets:
                    mediaPlanData.selectedInventory.summaryStatistics
                      ?.totalAssets || 0,
                  formatTypes: Array.from(
                    new Set(
                      mediaPlanData.selectedInventory.locations
                        ?.map((item) => item.detail?.format)
                        .filter((format) => format) || [],
                    ),
                  ),
                  totalFormatTypes:
                    mediaPlanData.selectedInventory.summaryStatistics
                      ?.totalFormatTypes || 0,
                  totalCities:
                    mediaPlanData.selectedInventory.summaryStatistics
                      ?.totalCities || 0,
                },
                locations:
                  mediaPlanData.selectedInventory.locations?.map((item) => ({
                    name: item.detail?.name || "",
                    country: item.location?.location?.country || "",
                    state: item.location?.location?.state || "",
                    type: item.detail?.inventoryType || "",
                    city: item.location?.location?.city || "",
                    impressions:
                      item.performance?.estimatedImpressions ??
                      item.performance?.estimatedImpression ??
                      0,
                    cost: item.performance?.estimatedCost || 0,
                    lat:
                      item.location?.location?.locationCoordinates
                        ?.coordinates?.[0]?.latitude || 0,
                    lng:
                      item.location?.location?.locationCoordinates
                        ?.coordinates?.[0]?.longitude || 0,
                    mediaOwnerName: item.detail?.mediaOwnerName || "",
                    scheduleDates: item.scheduleDates || [],
                    scheduleHours: item.scheduleHours || [],
                  })) || [],
              }
            : undefined;

          // Build media plan with all unified data for PPT
          // Ensure performanceMetrics is always present (fallback to mediaPlan's if null)
          const mediaPlanForPPT: MediaPlanResponse = {
            ...mediaPlanData.mediaPlan,
            headerInfo:
              mediaPlanData.headerInfo as MediaPlanResponse["headerInfo"],
            performanceMetrics:
              mediaPlanData.performanceMetrics ||
              mediaPlanData.mediaPlan.performanceMetrics,
            ...(mediaPlanData.geographicTargeting && {
              geographicTargeting: mediaPlanData.geographicTargeting,
            }),
            ...(transformedSelectedInventory && {
              selectedInventory: transformedSelectedInventory,
            }),
          };

          const fileName = `${mediaPlanData.headerInfo?.name || "MediaPlan"}_${theme.name.replace(/\s+/g, "")}.pptx`;
          await generateMediaPlanPPT({
            mediaPlan: mediaPlanForPPT,
            costSplitData: mediaPlanData.costSplitByInventoryType,
            theme,
            fileName,
            mapImage,
            mapImageLink,
            geographySummary: mediaPlanData.geographySummary,
            channelCount: mediaPlanData.channelCount,
            targeting: campaignDetail?.data?.targeting,
            campaignId,
            goalType: campaignDetail?.data?.goals?.goalType,
            targetValue: campaignDetail?.data?.goals?.targetValue,
            costSplitByCity: mediaPlanData.costSplitByCity,
            selectedInventoryLocations:
              mediaPlanData.selectedInventory.locations,
            delivery: computeExpectedDelivery(
              mediaPlanData.headerInfo?.startDate,
              mediaPlanData.headerInfo?.endDate,
              mediaPlanData.performanceMetrics?.estimatedImpression || 0,
            ),
            reachCurve:
              reachCurveData.length > 0
                ? { data: reachCurveData, labels: reachCurveLabels }
                : undefined,
          });
          showSuccess(t("media_plan.errors.download_success"));
        } catch (error) {
          console.error("Failed to generate PPT:", error);
          showError(t("media_plan.errors.download_failed"));
        }
      } else {
        // Analytics view - generate Excel using transformed data
        try {
          const analyticsData = transformMediaPlanData(
            mediaPlanData,
            undefined,
            t,
          );
          const fileName = `${mediaPlanData.headerInfo?.name || "MediaPlan"}_Analytics.xlsx`;
          await generateMediaPlanExcel({
            data: analyticsData,
            fileName,
            campaignName: mediaPlanData.headerInfo?.name,
            theme,
          });
          showSuccess(t("media_plan.errors.excelSuccess"));
        } catch (error) {
          console.error("Failed to generate Excel:", error);
          showError(t("media_plan.errors.excelFailed"));
        }
      }
    } catch (error) {
      console.error("Download error:", error);
    } finally {
      setIsDownloading(false);
    }
  };

  const handleShare = async () => {
    const id = mediaPlanData?.headerInfo?.id || campaignId;
    const fallbackUrl = `${window.location.origin}/campaigns/media-plan/${id}`;

    if (!id) {
      setShareUrl(fallbackUrl);
      setIsShareModalOpen(true);
      return;
    }

    try {
      const tokenResponse = await generatePublicToken(id).unwrap();
      const publicToken = tokenResponse.data?.publicToken;
      if (!publicToken) {
        throw new Error("No publicToken in generate-token response");
      }
      setShareUrl(
        `${window.location.origin}/public/media-plan/view/${id}?token=${publicToken}`,
      );
    } catch (error) {
      console.error("Error generating public share link:", error);
      showError(t("media_plan.errors.publicTokenFailed"));
      setShareUrl(fallbackUrl);
    }
    setIsShareModalOpen(true);
  };

  const handleInternalShare = (emails: string[]) => {
    const newContacts = emails
      .filter(
        (email) => !recentlyShared.some((contact) => contact.email === email),
      )
      .map((email) => ({ email }));
    setRecentlyShared([...recentlyShared, ...newContacts]);
  };

  const handleRemoveRecentContact = (email: string) => {
    setRecentlyShared(
      recentlyShared.filter((contact) => contact.email !== email),
    );
  };

  const handleMapViewClick = async () => {
    if (!campaignId) {
      showError(t("media_plan.errors.campaignIdMissing"));
      return;
    }

    if (isLoadingMapView) {
      return; // Prevent multiple clicks
    }

    setIsLoadingMapView(true);
    try {
      // Step 1: Generate public token
      const tokenResponse = await generatePublicToken(campaignId).unwrap();

      if (!tokenResponse.success || !tokenResponse.data?.publicToken) {
        showError(t("media_plan.errors.publicTokenFailed"));
        return;
      }

      const publicToken = tokenResponse.data.publicToken;

      // Step 2: Fetch public inventories with pagination
      const inventoriesResponse = await getPublicInventories({
        publicToken,
        page: 0,
        size: 10,
        sortBy: "name",
        sortDir: "asc",
      }).unwrap();

      if (!inventoriesResponse.success) {
        showError(t("media_plan.errors.fetchInventoriesFailed"));
        return;
      }

      // Open the public map view with the token
      const url = `/public/inventory-map/view/${campaignId}?token=${publicToken}`;
      window.open(url, "_blank");
    } catch (error) {
      console.error("Error in handleMapViewClick:", error);
      showError(t("media_plan.errors.mapViewFailed"));
    } finally {
      setIsLoadingMapView(false);
    }
  };

  if (mediaPlanData?.isLoading || !mediaPlanData) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <div>{t("media_plan.errors.loading")}</div>
      </div>
    );
  }

  if (mediaPlanData?.isError) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <div>{t("media_plan.errors.error_loading")}</div>
      </div>
    );
  }

  return (
    <div id="campaigns-page" className="h-full w-full flex flex-col">
      <PageHeader
        title={mediaPlanData.headerInfo?.name || t("mediaPlan.defaultTitle")}
        description={
          <div
            id="media-plan-header-info"
            className="inline-flex items-center gap-2"
          >
            <p id="media-plan-campaign-id">
              {t("ID")}: {mediaPlanData.planNumber || "N/A"}
            </p>
            <StatusBadge
              status={
                mediaPlanData.headerInfo?.status?.toLowerCase() || "draft"
              }
            >
              {mediaPlanData.headerInfo?.status
                ? t(
                    `campaignsList.status.${mediaPlanData.headerInfo.status}`,
                  ) || mediaPlanData.headerInfo.status
                : "N/A"}
            </StatusBadge>
          </div>
        }
        actions={
          <>
            <Dropdown>
              <DropdownTrigger asChild>
                <Button
                  variant="outline"
                  size="iconMd"
                  className="outline-mw-primary-500 text-mw-primary-500"
                >
                  <MoreHorizontal className="h-4 w-4" />
                </Button>
              </DropdownTrigger>
              <CampaignActionsDropdownContent
                campaignId={campaignId || ""}
                campaignData={{
                  status: mediaPlanData.headerInfo?.status || "",
                }}
                handlers={{
                  onRefresh: mediaPlanData.refetch,
                }}
                hideNavigation={["viewMediaPlan"]}
              />
            </Dropdown>
          </>
        }
        leftAction={backButton}
      />
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        <Card className="flex flex-col">
          <MediaPlanHeader
            viewType={viewType}
            selectedTheme={selectedTheme}
            onViewTypeChange={handleViewTypeChange}
            onThemeChange={setSelectedTheme}
            onDownload={handleDownload}
            onShare={handleShare}
            isDownloading={isDownloading}
            isDataLoading={mediaPlanData?.isAnyApiLoading}
          />

          {viewType === "presentation" ? (
            <div id="media-plan-presentation-view">
              <div id="media-plan-title-slide-section" className="mx-32">
                <MediaPlanTitleSlide
                  headerInfo={mediaPlanData.headerInfo}
                  brandDetails={mediaPlanData.mediaPlan.brandDetails}
                  theme={theme}
                />
              </div>

              <div
                id="media-plan-performance-metrics-section"
                className="mx-32"
              >
                <MediaPlanPerformanceMetrics
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  headerInfo={mediaPlanData.headerInfo}
                  geographySummary={mediaPlanData.geographySummary}
                  channelCount={mediaPlanData.channelCount}
                  goalType={campaignDetail?.data?.goals?.goalType}
                  costSplitData={mediaPlanData.costSplitByInventoryType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-inventory-mix-section" className="mx-32">
                <MediaPlanInventoryMix
                  costSplitData={mediaPlanData.costSplitByInventoryType}
                  mediaChannels={mediaPlanData.mediaChannels}
                  headerInfo={mediaPlanData.headerInfo}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  goalType={campaignDetail?.data?.goals?.goalType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-targeting-card-section" className="mx-32">
                <MediaPlanTargeting
                  targeting={campaignDetail?.data?.targeting}
                  theme={theme}
                />
              </div>

              <div id="media-plan-audience-trends-section" className="mx-32">
                <MediaPlanAudienceTrends
                  campaignId={campaignId}
                  headerInfo={mediaPlanData.headerInfo}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  audienceDemographics={
                    mediaPlanData.mediaPlan.audienceDemographics
                  }
                  targetingDemographics={
                    campaignDetail?.data?.targeting?.demographics
                  }
                  selectedInventory={mediaPlanData.selectedInventory}
                  theme={theme}
                />
              </div>

              <div id="media-plan-geographic-plan-section" className="mx-32">
                <MediaPlanGeographicPlan
                  costSplitData={mediaPlanData.costSplitByCity}
                  headerInfo={mediaPlanData.headerInfo}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  goalType={campaignDetail?.data?.goals?.goalType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-audience-map-section" className="mx-32">
                <MediaPlanAudienceMap
                  mapView={mapView}
                  mapConfig={mapConfig}
                  locations={mappedInventoryList}
                  availablePOIs={availablePOIs}
                  summary={audienceMapSummary}
                  onInteractiveClick={handleMapViewClick}
                  isInteractiveLoading={isLoadingMapView}
                  onMapReady={(map) => {
                    mapInstanceRef.current = map;
                    // Flat presentation map (light basemap set at creation).
                    map.setProjection({ name: "mercator" });
                    applyMapLights(map);
                    fitMapToInventory(map, mappedInventoryList, availablePOIs);
                    addInventoryRadiusLayer(map, mappedInventoryList);
                  }}
                  theme={theme}
                />
              </div>

              <div id="media-plan-goals-kpis-section" className="mx-32">
                <MediaPlanGoalsKpis
                  goalType={campaignDetail?.data?.goals?.goalType}
                  targetValue={campaignDetail?.data?.goals?.targetValue}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  headerInfo={mediaPlanData.headerInfo}
                  theme={theme}
                />
              </div>

              <div
                id="media-plan-inventory-snapshots-section"
                className="mx-32"
              >
                <MediaPlanInventorySnapshots
                  selectedInventory={mediaPlanData.selectedInventory}
                  headerInfo={mediaPlanData.headerInfo}
                  goalType={campaignDetail?.data?.goals?.goalType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-why-plan-section" className="mx-32">
                <MediaPlanWhyThisPlanWorks
                  forecastData={mediaPlanData.forecastData}
                  costSplitByCity={mediaPlanData.costSplitByCity}
                  channelCount={mediaPlanData.channelCount}
                  selectedInventory={mediaPlanData.selectedInventory}
                  headerInfo={mediaPlanData.headerInfo}
                  goalType={campaignDetail?.data?.goals?.goalType}
                  targetValue={campaignDetail?.data?.goals?.targetValue}
                  targeting={campaignDetail?.data?.targeting}
                  theme={theme}
                />
              </div>
            </div>
          ) : (
            <div id="media-plan-analytics-view">
              <AnalyticsView mediaPlanData={mediaPlanData} />
            </div>
          )}
        </Card>
      </div>
      <ShareModalDrawer
        isOpen={isShareModalOpen}
        onClose={() => setIsShareModalOpen(false)}
        shareUrl={shareUrl}
        recentlyShared={recentlyShared}
        onInternalShare={handleInternalShare}
        onRemoveRecentContact={handleRemoveRecentContact}
      />
    </div>
  );
};

export default ViewMediaPlanPage;
