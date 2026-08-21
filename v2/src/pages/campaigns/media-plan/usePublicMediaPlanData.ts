import {
  PublicInventoryItem,
  useGetPublicAllSelectedInventoryQuery,
  useGetPublicCostSplitByQuery,
  useGetPublicForecastQuery,
  useGetPublicMediaPlanQuery,
  useGetPublicPriceSummaryQuery,
  useLazyGetPublicInventoriesQuery,
} from "@services/public-access/publicAccessSlice";
import { useTranslate } from "@tolgee/react";
import { formatDisplayDate } from "@utils/dateUtils";
import { formatScheduleHours } from "@utils/schedule.utils";
import { useEffect, useState, useCallback } from "react";

import { SelectedInventory, SelectedInventorySummary } from "./types";
import { UnifiedMediaPlanData } from "./useMediaPlanData";
import { dayString, InventoryItem } from "../../../types/inventory.types";

/**
 * Maps the reduced public-access inventory payload onto the InventoryItem
 * shape the media plan sections already render. The public endpoint doesn't
 * carry per-schedule financial fields (impressions/sov/pricing/etc.) — those
 * default to 0 rather than being estimated or omitted.
 */
const mapPublicInventoryItemToInventoryItem = (
  item: PublicInventoryItem,
): InventoryItem => {
  const operationDays = Object.keys(
    item.operations.operatingTimes || {},
  ) as dayString[];

  return {
    detail: {
      id: item.detail.id,
      name: item.detail.name,
      externalId: item.detail.externalId,
      referenceId: item.detail.referenceId,
      mediaOwnerId: item.detail.mediaOwnerId,
      mediaOwnerName: item.detail.mediaOwnerName,
      inventoryType: item.detail.inventoryType,
      category: "",
      venueType: item.detail.venueType ?? [],
      thumbnail: item.detail.thumbnail,
      images: [],
      format: item.detail.format,
      environment: item.detail.environment,
      size: "",
      operationMode: "",
      execution: item.detail.execution,
      screens: item.detail.screens,
      sov: item.detail.sov,
      isSelected: item.detail.isSelected,
      isCompliant: item.detail.isCompliant,
      bookingMode: item.detail.bookingMode,
      panels: item.detail.panels,
    },
    location: {
      location: item.location.location,
      poi: { types: [], nearbyPOIs: [], categories: [] },
      demographics: item.location.demographics,
    },
    performance: {
      cpmRate: item.performance.cpmRate,
      estimatedCost: item.performance.estimatedCost,
      perDayCost: item.performance.perDayCost,
      perDayAdPlays: item.performance.perDayAdPlays,
      totalAdPlays: item.performance.totalAdPlays,
      plannedSot: parseFloat(item.performance.plannedSot) || 0,
      totalSot: item.performance.totalSot,
      sov: parseFloat(item.performance.sov) || 0,
    },
    operations: {
      operationDays,
      operatingTimes: item.operations.operatingTimes as Record<
        dayString,
        Array<{ start: string; end: string }>
      >,
      maintenanceWindow: item.operations.maintenanceWindow,
      loopSize: item.operations.loopSize,
      slotDuration: item.operations.slotDuration,
      clientPerLoop: item.operations.clientPerLoop,
      cycleTime: item.operations.cycleTime,
    },
    schedules: item.schedules.map((schedule) => ({
      id: schedule.id,
      name: schedule.name,
      startDate: schedule.startDate,
      endDate: schedule.endDate,
      scheduleDays: schedule.scheduleDays as dayString[],
      bookingMatrix: schedule.bookingMatrix,
      duration: schedule.duration,
      spotsPerHour: schedule.spotsPerHour,
      spotsPerLoop: schedule.spotsPerLoop,
      impressions: 0,
      adPlays: 0,
      sov: 0,
      sot: 0,
      plannedSot: 0,
      order: 0,
      pricing: 0,
    })),
  };
};

/**
 * Public-token equivalent of useMediaPlanData — same UnifiedMediaPlanData
 * shape so the existing media plan sections can render either source
 * unchanged. Every query is skipped when there's no token yet.
 */
export const usePublicMediaPlanData = (
  publicToken: string | undefined,
): UnifiedMediaPlanData | null => {
  const { t: tCommon } = useTranslate(["common"]);
  const [unifiedData, setUnifiedData] = useState<UnifiedMediaPlanData | null>(
    null,
  );

  const {
    data: mediaPlanData,
    isLoading: isMediaPlanLoading,
    isError: isMediaPlanError,
    refetch: refetchMediaPlan,
  } = useGetPublicMediaPlanQuery(
    { publicToken: publicToken || "" },
    { skip: !publicToken },
  );

  const {
    data: costSplitByInventoryType,
    isLoading: isCostSplitInventoryTypeLoading,
  } = useGetPublicCostSplitByQuery(
    { publicToken: publicToken || "", splitBy: "CHANNEL" },
    { skip: !publicToken },
  );
  const { data: costSplitByStateData, isLoading: isCostSplitStateLoading } =
    useGetPublicCostSplitByQuery(
      { publicToken: publicToken || "", splitBy: "STATE" },
      { skip: !publicToken },
    );
  const { data: costSplitByCity, isLoading: isCostSplitCityLoading } =
    useGetPublicCostSplitByQuery(
      { publicToken: publicToken || "", splitBy: "CITY" },
      { skip: !publicToken },
    );
  const { data: costSplitByVenueType, isLoading: isCostSplitVenueTypeLoading } =
    useGetPublicCostSplitByQuery(
      { publicToken: publicToken || "", splitBy: "VENUE_TYPE" },
      { skip: !publicToken },
    );

  const { data: forecastData, isLoading: isForecastLoading } =
    useGetPublicForecastQuery(
      { publicToken: publicToken || "" },
      { skip: !publicToken },
    );
  const { data: priceSummaryResponse, isLoading: isPriceSummaryLoading } =
    useGetPublicPriceSummaryQuery(
      { publicToken: publicToken || "" },
      { skip: !publicToken },
    );

  // Per-item performance overlay (spotRate / estimatedCost / estimatedReach)
  // keyed by referenceId — the rich inventory list omits these fields.
  const { data: allSelectedResponse } = useGetPublicAllSelectedInventoryQuery(
    { publicToken: publicToken || "" },
    { skip: !publicToken },
  );

  const [fetchPublicInventories] = useLazyGetPublicInventoriesQuery();
  const [selectedItems, setSelectedItems] = useState<InventoryItem[]>([]);
  const [isSelectedInventoryLoading, setIsSelectedInventoryLoading] =
    useState(false);

  useEffect(() => {
    if (!publicToken || !mediaPlanData?.data) return;
    setIsSelectedInventoryLoading(true);
    fetchPublicInventories({
      publicToken,
      page: 0,
      size: 1000,
      sortBy: "name",
      sortDir: "asc",
    })
      .unwrap()
      .then((result) => {
        const items = (result.data?.content || []).map(
          mapPublicInventoryItemToInventoryItem,
        );
        setSelectedItems(items);
      })
      .catch(() => {
        setSelectedItems([]);
      })
      .finally(() => {
        setIsSelectedInventoryLoading(false);
      });
  }, [publicToken, mediaPlanData?.data, fetchPublicInventories]);

  const refetch = useCallback(() => {
    refetchMediaPlan();
  }, [refetchMediaPlan]);

  useEffect(() => {
    if (!mediaPlanData?.data) {
      setUnifiedData(null);
      return;
    }

    const mediaPlan = mediaPlanData.data;

    const headerInfo: UnifiedMediaPlanData["headerInfo"] = {
      ...mediaPlan.headerInfo,
      ...(forecastData?.data?.totalCost !== undefined && {
        totalCost: forecastData.data.totalCost,
        impressions: forecastData.data.estimatedImpression,
      }),
    };

    const performanceMetrics =
      forecastData?.data || mediaPlan.performanceMetrics || null;

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

    // Overlay /all performance (spotRate, cost, reach…) keyed by referenceId.
    const perfByRef = new Map(
      (allSelectedResponse?.data || []).map((p) => [
        p.referenceId,
        p.performance,
      ]),
    );

    const selectedInventoryItems = selectedItems.map((rawItem) => {
      const overlay = perfByRef.get(rawItem.detail?.referenceId || "");
      const item = overlay
        ? {
            ...rawItem,
            performance: {
              ...rawItem.performance,
              ...(overlay.spotRate != null && { spotRate: overlay.spotRate }),
              ...(overlay.cpmRate != null && { cpmRate: overlay.cpmRate }),
              ...(overlay.estimatedCost != null && {
                estimatedCost: overlay.estimatedCost,
              }),
              ...(overlay.estimatedReach != null && {
                estimatedReach: overlay.estimatedReach,
              }),
              ...(overlay.estimatedImpression != null && {
                estimatedImpression: overlay.estimatedImpression,
              }),
              ...(overlay.perDayAdPlays != null && {
                perDayAdPlays: overlay.perDayAdPlays,
              }),
              ...(overlay.sov != null && { sov: overlay.sov }),
            },
          }
        : rawItem;
      const schedules = item.schedules || [];
      const scheduleDates: Array<{
        startDate: string;
        endDate: string;
        totalHours: number;
      }> = [];
      const scheduleHours: string[][] = [];

      schedules.forEach((schedule) => {
        let eachScheduleHour = 1;
        if (schedule.bookingMatrix) {
          const hoursForSchedule = formatScheduleHours(schedule.bookingMatrix);
          scheduleHours.push(hoursForSchedule);
          eachScheduleHour = hoursForSchedule.length;
        } else {
          eachScheduleHour = 1;
          scheduleHours.push([]);
        }

        scheduleDates.push({
          startDate: formatDisplayDate(schedule.startDate ?? "--", tCommon),
          endDate: formatDisplayDate(schedule.endDate ?? "--", tCommon),
          totalHours: eachScheduleHour,
        });
      });

      return {
        ...item,
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

    // POI data is not available on the public (tokenised) endpoint, so
    // poiCount stays 0 here; city/country/channel counts are derivable.
    const countryCount = new Set(
      selectedInventoryItems
        .map((item) => item.location?.location?.country)
        .filter((country): country is string => Boolean(country)),
    ).size;

    const geographySummary = {
      cityCount: summaryStatistics.totalCities ?? 0,
      countryCount,
      poiCount: 0,
    };

    const channelCount = costSplitByInventoryType?.data?.length || 0;

    const unified: UnifiedMediaPlanData = {
      mediaPlan,
      costSplitByInventoryType: costSplitByInventoryType?.data || [],
      costSplitByState: costSplitByStateData?.data || [],
      costSplitByCity: costSplitByCity?.data || [],
      costSplitByVenueType: costSplitByVenueType?.data || [],
      forecastData: forecastData?.data || null,
      selectedInventory,
      inventoriesMapping: [],
      headerInfo,
      performanceMetrics,
      geographicTargeting,
      geographySummary,
      channelCount,
      mediaChannels: [],
      // clientType/planNumber/targeting/agency/brand/campaignSchedulePrices
      // all come from the authenticated campaign-detail/price-management
      // APIs, neither of which the public (tokenised) endpoint has access to.
      clientType: "",
      planNumber: "",
      targeting: null,
      agency: null,
      brand: null,
      campaignSchedulePrices: [],
      priceSummary: priceSummaryResponse?.data || null,
      isLoading: isMediaPlanLoading,
      isSelectedInventoryLoading,
      isAnyApiLoading:
        isMediaPlanLoading ||
        isCostSplitInventoryTypeLoading ||
        isCostSplitStateLoading ||
        isCostSplitCityLoading ||
        isCostSplitVenueTypeLoading ||
        isForecastLoading ||
        isPriceSummaryLoading ||
        isSelectedInventoryLoading,
      isError: isMediaPlanError,
      refetch,
    };

    setUnifiedData(unified);
  }, [
    mediaPlanData?.data,
    costSplitByStateData?.data,
    costSplitByInventoryType?.data,
    costSplitByCity?.data,
    costSplitByVenueType?.data,
    forecastData?.data,
    selectedItems,
    allSelectedResponse?.data,
    priceSummaryResponse?.data,
    isMediaPlanLoading,
    isCostSplitInventoryTypeLoading,
    isCostSplitStateLoading,
    isCostSplitCityLoading,
    isCostSplitVenueTypeLoading,
    isForecastLoading,
    isPriceSummaryLoading,
    isSelectedInventoryLoading,
    isMediaPlanError,
    refetch,
    tCommon,
  ]);

  return unifiedData;
};
