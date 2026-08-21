import PageHeader from "@components/PageHeader";
import { Card } from "@components/ui/card";
import { MapControlsConfig } from "@components/ui/Mapbox";
import { StatusBadge } from "@components/ui/StatusBadge";
import { useAnnounce } from "@hooks/useAnnounce";
import { useGetPublicCampaignQuery } from "@services/public-access/publicAccessSlice";
import { useTranslate } from "@tolgee/react";
import { generateMapImage } from "@utils/chartImageGenerator";
import { generateMediaPlanExcel } from "@utils/mediaPlanExcelGenerator";
import { generateMediaPlanPPT } from "@utils/mediaPlanPPTGenerator";
import type { Map } from "mapbox-gl";
import React, { useMemo, useState, useRef } from "react";
import { useParams, useSearchParams } from "react-router-dom";

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
import { usePublicMediaPlanData } from "./usePublicMediaPlanData";
import { computeExpectedDelivery } from "./utils";
import { MediaPlanResponse } from "../../../types/campaign.types";
import { MapInventoryItem } from "../../../types/inventory.types";
import { applyMapLights } from "../inventory/mapConfig";

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

/**
 * Read-only, unauthenticated counterpart of ViewMediaPlanPage. Data comes
 * from the /public-access/* endpoints via the token in the URL — no Bearer
 * auth, no campaign actions/edit affordances, no Layout chrome.
 */
const PublicMediaPlanPage: React.FC = () => {
  const { campaignId } = useParams<{ campaignId: string }>();
  const [searchParams] = useSearchParams();
  const publicToken = searchParams.get("token") || undefined;
  const { t } = useTranslate(["campaigns"]);
  const [viewType, setViewType] = useState<ViewType>("presentation");
  const [selectedTheme, setSelectedTheme] = useState("default");
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const { showSuccess, showError } = useAnnounce();
  const mapInstanceRef = useRef<Map | null>(null);

  const mediaPlanData = usePublicMediaPlanData(publicToken);

  // Public twin of GET /campaigns/{id} — targeting.demographics + goals.
  const { data: publicCampaign } = useGetPublicCampaignQuery(
    { publicToken: publicToken || "" },
    { skip: !publicToken },
  );
  const publicTargeting = publicCampaign?.data?.targeting;
  const publicGoals = publicCampaign?.data?.goals;

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

  const mappedInventoryList: MapInventoryItem[] = useMemo(() => {
    return mediaPlanData?.selectedInventory?.locations || [];
  }, [mediaPlanData?.selectedInventory?.locations]);

  const mapView = useMemo(
    () =>
      computeMediaPlanMapView(
        mediaPlanData?.selectedInventory?.locations || [],
      ),
    [mediaPlanData?.selectedInventory?.locations],
  );

  const audienceMapSummary = useMemo(() => {
    const cities = mediaPlanData?.costSplitByCity || [];
    const densest = cities.reduce<(typeof cities)[number] | null>(
      (top, c) =>
        !top || (c.impressions || 0) > (top.impressions || 0) ? c : top,
      null,
    );
    return {
      sitesPinned: countPlottedSites(mappedInventoryList),
      marketCount: cities.length,
      totalInventory: mediaPlanData?.forecastData?.totalInventories || 0,
      densestMarket: densest?.name || "",
    };
  }, [
    mediaPlanData?.costSplitByCity,
    mediaPlanData?.forecastData?.totalInventories,
    mappedInventoryList,
  ]);

  const handleMapViewClick = () => {
    if (!campaignId || !publicToken) return;
    const url = `/public/inventory-map/view/${campaignId}?token=${publicToken}`;
    window.open(url, "_blank");
  };

  const handleShare = () => {
    setIsShareModalOpen(true);
  };

  const getShareUrl = () => window.location.href;

  const handleDownload = async () => {
    if (isDownloading || !mediaPlanData || mediaPlanData.isAnyApiLoading) {
      return;
    }

    setIsDownloading(true);
    try {
      if (viewType === "presentation") {
        try {
          let mapImage: string | undefined;
          const mapImageLink = campaignId
            ? `${window.location.origin}/public/inventory-map/view/${campaignId}?token=${publicToken}`
            : undefined;

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
          }

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
            campaignId,
            targeting: publicTargeting,
            goalType: publicGoals?.goalType,
            targetValue: publicGoals?.targetValue,
            costSplitByCity: mediaPlanData.costSplitByCity,
            selectedInventoryLocations:
              mediaPlanData.selectedInventory.locations,
            delivery: computeExpectedDelivery(
              mediaPlanData.headerInfo?.startDate,
              mediaPlanData.headerInfo?.endDate,
              mediaPlanData.performanceMetrics?.estimatedImpression || 0,
              mediaPlanData.performanceMetrics?.estimatedReach || 0,
            ),
          });
          showSuccess(t("media_plan.errors.download_success"));
        } catch (error) {
          console.error("Failed to generate PPT:", error);
          showError(t("media_plan.errors.download_failed"));
        }
      } else {
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

  if (!publicToken) {
    return (
      <div className="h-screen w-screen flex items-center justify-center">
        <div>{t("media_plan.errors.publicTokenFailed")}</div>
      </div>
    );
  }

  if (mediaPlanData?.isLoading || !mediaPlanData) {
    return (
      <div className="h-screen w-screen flex items-center justify-center">
        <div>{t("media_plan.errors.loading")}</div>
      </div>
    );
  }

  if (mediaPlanData?.isError) {
    return (
      <div className="h-screen w-screen flex items-center justify-center">
        <div>{t("media_plan.errors.error_loading")}</div>
      </div>
    );
  }

  return (
    <div
      id="public-media-plan-page"
      className="h-screen w-screen flex flex-col"
    >
      <PageHeader
        title={mediaPlanData.headerInfo?.name || t("mediaPlan.defaultTitle")}
        description={
          <div
            id="media-plan-header-info"
            className="inline-flex items-center gap-2"
          >
            <p id="media-plan-campaign-id">
              {t("ID")}: {mediaPlanData.headerInfo?.id || "N/A"}
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
                  goalType={publicGoals?.goalType}
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
                  goalType={publicGoals?.goalType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-targeting-card-section" className="mx-32">
                <MediaPlanTargeting targeting={publicTargeting} theme={theme} />
              </div>

              <div id="media-plan-audience-trends-section" className="mx-32">
                <MediaPlanAudienceTrends
                  campaignId={campaignId}
                  headerInfo={mediaPlanData.headerInfo}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  audienceDemographics={
                    mediaPlanData.mediaPlan.audienceDemographics
                  }
                  targetingDemographics={publicTargeting?.demographics}
                  selectedInventory={mediaPlanData.selectedInventory}
                  theme={theme}
                />
              </div>

              <div id="media-plan-geographic-plan-section" className="mx-32">
                <MediaPlanGeographicPlan
                  costSplitData={mediaPlanData.costSplitByCity}
                  headerInfo={mediaPlanData.headerInfo}
                  performanceMetrics={mediaPlanData.performanceMetrics}
                  goalType={publicGoals?.goalType}
                  theme={theme}
                />
              </div>

              <div id="media-plan-audience-map-section" className="mx-32">
                <MediaPlanAudienceMap
                  mapView={mapView}
                  mapConfig={mapConfig}
                  locations={mappedInventoryList}
                  summary={audienceMapSummary}
                  onInteractiveClick={handleMapViewClick}
                  onMapReady={(map) => {
                    mapInstanceRef.current = map;
                    map.setProjection({ name: "mercator" });
                    applyMapLights(map);
                    fitMapToInventory(map, mappedInventoryList);
                    addInventoryRadiusLayer(map, mappedInventoryList);
                  }}
                  theme={theme}
                />
              </div>

              <div id="media-plan-goals-kpis-section" className="mx-32">
                <MediaPlanGoalsKpis
                  goalType={publicGoals?.goalType}
                  targetValue={publicGoals?.targetValue}
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
                  goalType={publicGoals?.goalType}
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
                  goalType={publicGoals?.goalType}
                  targetValue={publicGoals?.targetValue}
                  targeting={publicTargeting}
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
        shareUrl={getShareUrl()}
      />
    </div>
  );
};

export default PublicMediaPlanPage;
