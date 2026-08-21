import { useSelectedInventoryList } from "@hooks/useSelectedInventoryList";
import {
  useGetCampaignQuery,
  useGetMediaPlanQuery,
  useSplitCostCampaignQuery,
} from "@services/campaign/campaignSlice";
import {
  useLazyGetAllSelectedInventoryQuery,
  useLazyGetCampaignForecastQuery,
  useLazyGetCampaignSchedulePricesQuery,
  useLazyGetInventoriesMappingQuery,
  useLazyGetPriceSummaryQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import { formatScheduleHours } from "@utils/schedule.utils";
import { useEffect, useState, useCallback } from "react";
import {
  CampaignAgency,
  CampaignBrand,
  CostSplitByCampaignData,
  MediaPlanResponse,
  Targeting,
} from "src/types/campaign.types";
import {
  CampaignForecastData,
  CampaignSchedulePriceItem,
  InventoryItem,
  InventoryMappingItem,
  PriceSummaryResponse,
} from "src/types/inventory.types";

import { SelectedInventory, SelectedInventorySummary } from "./types";

export interface UnifiedMediaPlanData {
  mediaPlan: MediaPlanResponse;
  costSplitByState: CostSplitByCampaignData[];
  costSplitByInventoryType: CostSplitByCampaignData[];
  costSplitByCity: CostSplitByCampaignData[];
  costSplitByVenueType: CostSplitByCampaignData[];
  forecastData: CampaignForecastData | null;
  selectedInventory: SelectedInventory;
  inventoriesMapping: InventoryMappingItem[];
  headerInfo: MediaPlanResponse["headerInfo"] & {
    totalCost?: number;
    impressions?: number;
  };
  performanceMetrics: CampaignForecastData | null;
  geographicTargeting: MediaPlanResponse["geographicTargeting"] | null;
  /** Top-line geography counts for the performance-metrics section.
   * cityCount = distinct cities (city cost-split); countryCount = distinct
   * countries across selected inventory; poiCount = sum of POI selections
   * across the campaign's geofencing locations. */
  geographySummary: {
    cityCount: number;
    countryCount: number;
    poiCount: number;
  };
  /** Distinct media-channel count (from the CHANNEL cost-split). */
  channelCount: number;
  /** Media channels selected on the campaign (codes, e.g. "DIGITAL_OOH"),
   * derived from budgetAllocation (the API has no mediaChannels field).
   * Used to surface targeted-but-unbooked channels in the Inventory Mix. */
  mediaChannels: string[];
  /** "DIRECT_ADVERTISER" | "AGENCY", from the campaign detail. */
  clientType: string;
  /** Human-readable plan number (e.g. "PL-1024"), from the campaign detail —
   * distinct from headerInfo.id, which is the Mongo document id. */
  planNumber: string;
  /** Full campaign targeting config (demographics/geofencing/signals/venue
   * types), from the campaign detail — same query already fetched for
   * geographySummary.poiCount, just exposed wholesale for Excel export. */
  targeting: Targeting | null;
  /** Agency on the campaign, from the campaign detail. */
  agency: CampaignAgency | null;
  /** Brand + brand categories on the campaign, from the campaign detail
   * (mediaPlan.brandDetails has no categories, only a legacy category string). */
  brand: CampaignBrand | null;
  /** Per-schedule proposed/accepted/rate-card pricing from the Price
   * Management API, keyed by inventoryId — an inventory with multiple
   * flight segments has one entry per schedule. */
  campaignSchedulePrices: CampaignSchedulePriceItem[];
  priceSummary: PriceSummaryResponse | null;
  /** True while the main media plan query is in flight. Gates the initial
   * full-page loading screen — does NOT wait for selected inventory, so the
   * page can render as soon as the plan itself is ready. */
  isLoading: boolean;
  isError: boolean;
  /**
   * True while the selected-inventory list is still loading. It arrives via
   * a separate fetch that only starts once the media plan itself has
   * loaded, so callers that need real coordinates/counts (the map, the
   * selected-inventory sections) should show their own loading state keyed
   * on this rather than blocking the whole page on it.
   */
  isSelectedInventoryLoading: boolean;
  /**
   * True while any of the underlying queries (cost splits, forecast, price
   * summary, selected inventory) is still in flight — regardless of whether
   * it eventually succeeds or fails. A consumer like the PPT/Excel download
   * button should stay disabled until this is false, so it never exports a
   * plan while some of its data hasn't arrived yet.
   */
  isAnyApiLoading: boolean;
  refetch?: () => void;
}

export const useMediaPlanData = (
  campaignId: string | undefined,
): UnifiedMediaPlanData | null => {
  const { t: tCommon } = useTranslate(["common"]);
  const language = useTolgee(["language"]).getLanguage();
  const [unifiedData, setUnifiedData] = useState<UnifiedMediaPlanData | null>(
    null,
  );

  // Main media plan query
  const {
    data: mediaPlanData,
    isLoading: isMediaPlanLoading,
    isError: isMediaPlanError,
    refetch: refetchMediaPlan,
  } = useGetMediaPlanQuery(campaignId || "", {
    skip: !campaignId,
  });

  // Campaign detail — only needed for geofencing POI counts in the
  // performance-metrics section (media-plan response has no POI data).
  const { data: campaignDetail } = useGetCampaignQuery(campaignId || "", {
    skip: !campaignId,
  });

  // Cost split queries
  const {
    data: costSplitByInventoryType,
    isLoading: isCostSplitInventoryTypeLoading,
  } = useSplitCostCampaignQuery(
    { campaignId: campaignId || "", splitBy: "CHANNEL", language },
    { skip: !campaignId },
  );

  const { data: costSplitByStateData, isLoading: isCostSplitStateLoading } =
    useSplitCostCampaignQuery(
      { campaignId: campaignId || "", splitBy: "STATE", language },
      { skip: !campaignId },
    );

  const { data: costSplitByCity, isLoading: isCostSplitCityLoading } =
    useSplitCostCampaignQuery(
      { campaignId: campaignId || "", splitBy: "CITY", language },
      { skip: !campaignId },
    );

  const { data: costSplitByVenueType, isLoading: isCostSplitVenueTypeLoading } =
    useSplitCostCampaignQuery(
      { campaignId: campaignId || "", splitBy: "VENUE_TYPE", language },
      { skip: !campaignId },
    );

  // Lazy queries for dependent data
  const [
    fetchCampaignForecast,
    {
      data: forecastData,
      isLoading: isForecastLoading,
      isUninitialized: isForecastUninitialized,
    },
  ] = useLazyGetCampaignForecastQuery();
  const [
    fetchPriceSummary,
    {
      data: priceSummaryResponse,
      isLoading: isPriceSummaryLoading,
      isUninitialized: isPriceSummaryUninitialized,
    },
  ] = useLazyGetPriceSummaryQuery();

  const [
    fetchInventoriesMapping,
    {
      data: inventoriesMappingResponse,
      isLoading: isInventoriesMappingLoading,
      isUninitialized: isInventoriesMappingUninitialized,
    },
  ] = useLazyGetInventoriesMappingQuery();

  // Per-schedule proposed/accepted pricing for the Costing analytics table —
  // a separate API from the media plan itself (Price Management).
  const [
    fetchCampaignSchedulePrices,
    {
      data: campaignSchedulePricesResponse,
      isLoading: isCampaignSchedulePricesLoading,
      isUninitialized: isCampaignSchedulePricesUninitialized,
    },
  ] = useLazyGetCampaignSchedulePricesQuery();

  // getSelectedInventory (paginated) doesn't reliably carry per-item
  // estimatedCost/estimatedReach — getAllSelectedInventory ("/selected-inventory/all")
  // is the documented source for those (see useReachCurve.ts, which already
  // relies on it for the same reason). Fetched separately and merged into
  // selectedInventoryItems below, keyed by referenceId.
  const [
    fetchAllSelectedInventory,
    {
      data: allSelectedInventoryResponse,
      isLoading: isAllSelectedInventoryLoading,
      isUninitialized: isAllSelectedInventoryUninitialized,
    },
  ] = useLazyGetAllSelectedInventoryQuery();

  // Use the reusable hook for loading all selected inventories at once
  const { selectedItems, isLoading: isSelectedInventoryLoading } =
    useSelectedInventoryList({
      campaignId,
      enabled: !!campaignId && !!mediaPlanData?.data,
      pageSize: -1, // Load all at once
      sortBy: "name",
      sortDir: "asc",
    });

  // Fetch dependent data when media plan is loaded
  useEffect(() => {
    if (campaignId && mediaPlanData?.data) {
      fetchCampaignForecast({ campaignId });
      fetchPriceSummary({ campaignId });
      fetchAllSelectedInventory({ campaignId });
      fetchInventoriesMapping({ campaignId });
      fetchCampaignSchedulePrices({
        campaignId,
        params: { page: 0, size: -1, sortBy: "name", sortDir: "asc" },
      });
    }
  }, [
    campaignId,
    mediaPlanData?.data,
    fetchCampaignForecast,
    fetchPriceSummary,
    fetchAllSelectedInventory,
    fetchInventoriesMapping,
    fetchCampaignSchedulePrices,
  ]);

  // Create refetch function that refetches all queries
  const refetch = useCallback(() => {
    if (campaignId) {
      refetchMediaPlan();
    }
  }, [campaignId, refetchMediaPlan]);

  // Build unified data object when all data is available
  useEffect(() => {
    if (!mediaPlanData?.data) {
      setUnifiedData(null);
      return;
    }

    const mediaPlan = mediaPlanData.data;

    // Build header info with forecast totalCost if available
    const headerInfo: UnifiedMediaPlanData["headerInfo"] = {
      ...mediaPlan.headerInfo,
      ...(forecastData?.data?.totalCost !== undefined && {
        totalCost: forecastData.data.totalCost,
        impressions: forecastData.data.estimatedImpression,
      }),
    };

    // Build performance metrics from forecast or fallback to mediaPlan
    const performanceMetrics =
      forecastData?.data || mediaPlan.performanceMetrics || null;

    // Build geographic targeting from cost split data
    const cities =
      costSplitByCity?.data?.map((item) => ({
        name: item.name,
        impressions: item.impressions || 0,
        adPlays:
          Math.round((item.impressions || 0) * (item.frequency || 1)) || 0,
        allocatedBudget: item.totalAmountInPercentage || 0,
      })) || [];

    const venueTypes =
      costSplitByVenueType?.data?.map((item) => ({
        name: item.name,
        impressions: item.impressions || 0,
        adPlays:
          Math.round((item.impressions || 0) * (item.frequency || 1)) || 0,
        allocatedBudget: item.totalAmountInPercentage || 0,
      })) || [];

    const geographicTargeting =
      cities.length > 0 || venueTypes.length > 0
        ? { cities, venueTypes }
        : mediaPlan.geographicTargeting || null;

    // Build selected inventory summary
    const rawInventoryItems: InventoryItem[] = selectedItems || [];

    // Per-inventory estimatedCost/estimatedReach, keyed by referenceId — the
    // paginated selected-inventory items above don't reliably carry these.
    const performanceByReferenceId = new Map(
      (allSelectedInventoryResponse?.data || []).map((item) => [
        item.referenceId,
        item.performance,
      ]),
    );

    // Enhance inventory items with scheduleDates and scheduleHours
    const selectedInventoryItems: (InventoryItem & {
      scheduleDates?: Array<{
        startDate: string;
        endDate: string;
        totalHours: number;
      }>;
      scheduleHours?: string[][];
    })[] = rawInventoryItems.map((item) => {
      const overlayPerformance = performanceByReferenceId.get(
        item.detail?.referenceId,
      );
      const performance = overlayPerformance
        ? {
            ...item.performance,
            estimatedCost:
              overlayPerformance.estimatedCost ??
              item.performance?.estimatedCost,
            estimatedReach:
              overlayPerformance.estimatedReach ??
              item.performance?.estimatedReach,
          }
        : item.performance;

      const schedules = item.schedules || [];

      // Extract schedule dates and hours for each schedule
      const scheduleDates: Array<{
        startDate: string;
        endDate: string;
        totalHours: number;
      }> = [];
      const scheduleHours: string[][] = [];

      schedules.forEach((schedule) => {
        let eachScheduleHour = 1;
        // Calculate hours for this specific schedule
        if (schedule.bookingMatrix) {
          const hoursForSchedule = formatScheduleHours(schedule.bookingMatrix);
          scheduleHours.push(hoursForSchedule);
          eachScheduleHour = hoursForSchedule.length;
        } else {
          eachScheduleHour = 1;
          scheduleHours.push([]);
        }

        // Extract schedule dates (start and end dates for each schedule)
        scheduleDates.push({
          startDate: formatDisplayDate(schedule.startDate ?? "--", tCommon),
          endDate: formatDisplayDate(schedule.endDate ?? "--", tCommon),
          totalHours: eachScheduleHour,
        });
      });

      return {
        ...item,
        performance,
        scheduleDates: scheduleDates.length > 0 ? scheduleDates : undefined,
        scheduleHours: scheduleHours.length > 0 ? scheduleHours : undefined,
      };
    });

    const summaryStatistics: SelectedInventorySummary = {
      totalAssets:
        forecastData?.data?.totalInventories !== undefined &&
        forecastData.data.totalInventories !== null
          ? forecastData.data.totalInventories
          : 0,
      totalCities:
        costSplitByCity?.data && Array.isArray(costSplitByCity.data)
          ? costSplitByCity.data.length
          : 0,
      totalFormatTypes: selectedInventoryItems.length
        ? new Set(
            selectedInventoryItems
              .map((item) => item.detail?.format)
              .filter((format) => format),
          ).size
        : 0,
    };

    const selectedInventory: SelectedInventory = {
      summaryStatistics,
      locations: selectedInventoryItems,
    };

    // Top-line geography counts for the performance-metrics cards.
    const countryCount = new Set(
      selectedInventoryItems
        .map((item) => item.location?.location?.country)
        .filter((country): country is string => Boolean(country)),
    ).size;

    const poiCount = (
      campaignDetail?.data?.targeting?.geofencing?.locations || []
    ).reduce((sum, location) => sum + (location.poi?.length || 0), 0);

    const geographySummary = {
      cityCount: summaryStatistics.totalCities ?? 0,
      countryCount,
      poiCount,
    };

    const channelCount = costSplitByInventoryType?.data?.length || 0;
    // The campaign-detail API does not return a `mediaChannels` field —
    // derive the codes from budgetAllocation instead (only digital/classic
    // map to real OOH channels; retail/transit are budget-split-only).
    const budgetAllocation = campaignDetail?.data?.budgetAllocation;
    const mediaChannels = [
      ...((budgetAllocation?.digital ?? 0) > 0 ? ["DIGITAL_OOH"] : []),
      ...((budgetAllocation?.classic ?? 0) > 0 ? ["CLASSIC_OOH"] : []),
      ...((budgetAllocation?.cinema ?? 0) > 0 ? ["CINEMA"] : []),
    ];
    const clientType = campaignDetail?.data?.clientType || "";
    const planNumber = campaignDetail?.data?.planNumber || "";
    const targeting = campaignDetail?.data?.targeting || null;
    const agency = campaignDetail?.data?.agency || null;
    const brand = campaignDetail?.data?.brand || null;

    // Create unified data object
    const unified: UnifiedMediaPlanData = {
      mediaPlan,
      costSplitByInventoryType: costSplitByInventoryType?.data || [],
      costSplitByState: costSplitByStateData?.data || [],
      costSplitByCity: costSplitByCity?.data || [],
      costSplitByVenueType: costSplitByVenueType?.data || [],
      forecastData: forecastData?.data || null,
      selectedInventory,
      inventoriesMapping: inventoriesMappingResponse?.data || [],
      headerInfo,
      performanceMetrics,
      geographicTargeting,
      geographySummary,
      channelCount,
      mediaChannels,
      clientType,
      planNumber,
      targeting,
      agency,
      brand,
      campaignSchedulePrices:
        campaignSchedulePricesResponse?.data?.content || [],
      priceSummary: priceSummaryResponse?.data || null,
      isLoading: isMediaPlanLoading,
      isSelectedInventoryLoading:
        isSelectedInventoryLoading ||
        isAllSelectedInventoryLoading ||
        isAllSelectedInventoryUninitialized,
      isAnyApiLoading:
        isMediaPlanLoading ||
        isCostSplitInventoryTypeLoading ||
        isCostSplitStateLoading ||
        isCostSplitCityLoading ||
        isCostSplitVenueTypeLoading ||
        isForecastLoading ||
        isForecastUninitialized ||
        isPriceSummaryLoading ||
        isPriceSummaryUninitialized ||
        isAllSelectedInventoryLoading ||
        isAllSelectedInventoryUninitialized ||
        isSelectedInventoryLoading ||
        isInventoriesMappingLoading ||
        isInventoriesMappingUninitialized ||
        isCampaignSchedulePricesLoading ||
        isCampaignSchedulePricesUninitialized,
      isError: isMediaPlanError,
      refetch,
    };

    setUnifiedData(unified);
  }, [
    mediaPlanData?.data,
    campaignDetail?.data,
    costSplitByStateData?.data,
    costSplitByInventoryType?.data,
    costSplitByCity?.data,
    costSplitByVenueType?.data,
    campaignSchedulePricesResponse?.data,
    isCampaignSchedulePricesLoading,
    isCampaignSchedulePricesUninitialized,
    forecastData?.data,
    selectedItems,
    priceSummaryResponse?.data,
    allSelectedInventoryResponse?.data,
    inventoriesMappingResponse?.data,
    isMediaPlanLoading,
    isCostSplitInventoryTypeLoading,
    isCostSplitStateLoading,
    isCostSplitCityLoading,
    isCostSplitVenueTypeLoading,
    isForecastLoading,
    isForecastUninitialized,
    isPriceSummaryLoading,
    isPriceSummaryUninitialized,
    isAllSelectedInventoryLoading,
    isAllSelectedInventoryUninitialized,
    isSelectedInventoryLoading,
    isInventoriesMappingLoading,
    isInventoriesMappingUninitialized,
    isMediaPlanError,
    refetch,
    tCommon,
  ]);

  return unifiedData;
};
