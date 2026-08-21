import { useGeneratePublicTokenMutation } from "@services/public-access/publicAccessSlice";
import { generateMediaPlanPPT } from "@utils/mediaPlanPPTGenerator";
import React, { useEffect, useRef } from "react";

import { MediaPlanResponse } from "../../../types/campaign.types";
import { THEMES_COLORS } from "../media-plan/constants";
import { useMediaPlanData } from "../media-plan/useMediaPlanData";

interface CampaignPPTDownloaderProps {
  campaignId: string;
  onComplete: () => void;
  onError: () => void;
}

/**
 * Headless component that fetches all media plan data and generates a PPT download.
 * Renders null — purely a side-effect driver.
 * Mount it when a download is triggered; it calls onComplete/onError and can be unmounted.
 */
export const CampaignPPTDownloader: React.FC<CampaignPPTDownloaderProps> = ({
  campaignId,
  onComplete,
  onError,
}) => {
  const mediaPlanData = useMediaPlanData(campaignId);
  const [generatePublicToken] = useGeneratePublicTokenMutation();
  const hasTriggeredRef = useRef(false);

  useEffect(() => {
    // Wait until data is fully initialized (null = hook not yet ready, isLoading =
    // primary query in-flight, isAnyApiLoading = a subsidiary query — cost splits,
    // forecast, price summary, selected inventory — still in-flight). A subsidiary
    // query that FAILS still lets isAnyApiLoading settle to false — we only wait for
    // things to finish, not to succeed, so the PPT generates with whatever partial
    // data is available rather than skipping fields silently or blocking forever.
    if (
      !mediaPlanData ||
      mediaPlanData.isLoading ||
      mediaPlanData.isAnyApiLoading ||
      mediaPlanData.isError
    ) {
      return;
    }

    // Guard: useMediaPlanData rebuilds unifiedData multiple times as subsidiary queries resolve.
    // We only want to generate the PPT once — on the first valid data state.
    if (hasTriggeredRef.current) return;
    hasTriggeredRef.current = true;

    const generate = async () => {
      try {
        // Generate public token for the map link embedded in the PPT (graceful fallback on failure)
        let mapImageLink: string | undefined;
        try {
          const tokenResponse = await generatePublicToken(campaignId).unwrap();
          if (tokenResponse.success && tokenResponse.data?.publicToken) {
            mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}?token=${tokenResponse.data.publicToken}`;
          } else {
            mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}`;
          }
        } catch {
          mapImageLink = `${window.location.origin}/public/inventory-map/view/${campaignId}`;
        }

        // Transform selectedInventory to match the MediaPlanResponse structure expected by the PPT generator.
        // This is the same transformation used in ViewMediaPlanPage.handleDownload.
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

        // Build the media plan data object for the PPT generator.
        // Same construction as ViewMediaPlanPage.handleDownload.
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

        const theme = THEMES_COLORS[0];
        const fileName = `${mediaPlanData.headerInfo?.name || "MediaPlan"}_${theme.name.replace(/\s+/g, "")}.pptx`;

        await generateMediaPlanPPT({
          mediaPlan: mediaPlanForPPT,
          costSplitData: mediaPlanData.costSplitByInventoryType,
          theme,
          fileName,
          mapImage: undefined, // No Mapbox instance available outside the media plan page
          mapImageLink,
          scheduleChartImage: undefined, // No DOM elements to capture from here
          selectedInventoryChartImage: undefined,
        });

        onComplete();
      } catch (error) {
        console.error("CampaignPPTDownloader: PPT generation failed", error);
        onError();
      }
    };

    generate();
  }, [mediaPlanData, campaignId, generatePublicToken, onComplete, onError]);

  return null;
};
