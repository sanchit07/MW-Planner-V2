import { formatNumber, normalizeGoalType } from "@utils/budget.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import {
  convertDayName,
  detectSchedulePattern,
  extractOperationTimes,
  formatHourTo12Hour,
} from "@utils/schedule.utils";
import { mapScheduleToFormState } from "@utils/scheduleDefaults";
import { InventoryTypeLowercase } from "src/constants/inventory.constants";
import type { CostSplitByCampaignData } from "src/types/campaign.types";
import type {
  CampaignSchedulePriceItem,
  InventoryItem,
  InventoryOperations,
  InventorySchedule,
} from "src/types/inventory.types";
import { findInventoryForPOI } from "src/utils/inventory-match.utils";

type TFunction = (
  key: string,
  params?: Record<string, string | number>,
) => string;

// Venue type may arrive as an array (/filter shape) or a string (media-plan
// API); normalise to a comma-joined display string.
const venueTypeToText = (value: unknown): string =>
  Array.isArray(value)
    ? value.filter(Boolean).join(", ")
    : typeof value === "string"
      ? value
      : "";

import type {
  AnalyticsExcelData,
  CampaignDetails,
  CinemaInventoryRow,
  ClassicInventoryRow,
  ClassicOperationScheduleRow,
  CostingDetails,
  CostingInventoryRow,
  DeliveryBreakdownRow,
  DigitalInventoryRow,
  DigitalOperationScheduleRow,
  DOOHPanelRow,
  DOOHRollupHeatmap,
  DOOHScheduleRow,
  DOOHScheduleSegmentRow,
  DOOHScheduleSummary,
  EstimatedPerformanceMetrics,
  GeographyPoiRow,
  GeographyTargetingRow,
  InventoryDetailsRow,
  InventoryMappingRow,
  InventoryPlanningRow,
  MobileOperationScheduleRow,
  OperationDetailsData,
  StatePlanningRow,
  TargetingApplied,
} from "./analyticsTypes";
import { UnifiedMediaPlanData } from "./useMediaPlanData";
import { computeDOOHRollupHeatmap, computeExpectedDelivery } from "./utils";

/** True when an inventory item is a Cinema line item. Cinema is a real
 * channel: it flows through the generic tables (inventory planning/mapping/
 * details, costing) but stays out of the digital/classic/DOOH-specific tables
 * and gets its own dedicated Cinema table (see transformCinemaInventory). */
const isCinemaInventory = (item: {
  detail?: { inventoryType?: string };
}): boolean =>
  (item.detail?.inventoryType || "").toLowerCase().includes("cinema");

/**
 * Transforms UnifiedMediaPlanData to AnalyticsExcelData
 * Fills all attributes from the data structure, leaving blank if not found
 */
export const transformMediaPlanData = (
  mediaPlanData?: UnifiedMediaPlanData | null,
  t?: TFunction,
  tCampaigns?: TFunction,
): AnalyticsExcelData => {
  if (!mediaPlanData) {
    return {};
  }

  // Cinema is a real channel now — keep every inventory location (including
  // cinema) in the working set. Cinema rows are steered into the generic
  // tables and their own dedicated Cinema table, while the digital/classic/
  // DOOH-specific transforms filter them out by inventoryType.
  const data0: UnifiedMediaPlanData = mediaPlanData;

  const data: AnalyticsExcelData = {};

  // Transform Campaign Details
  data.campaignDetails = transformCampaignDetails(data0, t, tCampaigns);

  // Transform Estimated Performance Metrics (Excel Plan sheet)
  data.estimatedPerformanceMetrics = transformEstimatedPerformanceMetrics(
    data0,
    tCampaigns,
  );

  // Transform Targeting Applied (Excel Plan sheet)
  data.targetingApplied = transformTargetingApplied(data0);

  // Transform Delivery Breakdown (Excel Plan sheet)
  const deliveryBreakdown = transformDeliveryBreakdown(data0);
  data.deliveryBreakdown = deliveryBreakdown.rows;
  data.deliveryGranularity = deliveryBreakdown.granularity;

  // Transform State Planning
  data.statePlanning = transformStatePlanning(data0);

  // Transform City Planning
  data.cityPlanning = transformCityPlanning(data0);

  // Transform Inventory Planning
  data.inventoryPlanning = transformInventoryPlanning(data0);

  // Transform Inventory Mapping
  data.inventoryMapping = transformInventoryMapping(data0);

  // Transform Inventory Details
  data.inventoryDetails = transformInventoryDetails(data0);

  // Transform Costing Details
  data.costingDetails = transformCostingDetails(data0);

  // Transform per-inventory Costing rows
  data.costingInventoryRows = transformCostingInventoryRows(data0);

  // Transform Classic Inventory
  data.classicInventory = transformClassicInventory(data0);

  // Transform Classic Network Inventory
  data.classicNetworkInventory = transformClassicNetworkInventory(data0);

  // Transform Digital Inventory
  data.digitalInventory = transformDigitalInventory(data0);

  // Transform Digital Network Inventory
  data.digitalNetworkInventory = transformDigitalNetworkInventory(data0);

  // Transform Cinema Inventory (operator/hall/showtime — films indicative only)
  data.cinemaInventory = transformCinemaInventory(data0);

  // Transform Operation Details
  data.operationDetails = transformOperationDetails(data0, t);

  // Transform DOOH Schedules
  data.doohSchedules = transformDOOHSchedules(data0, t);

  // Transform DOOH Schedule Summary
  data.doohScheduleSummary = transformDOOHScheduleSummary(data0, t);

  // Transform DOOH Panels (Schedule Calendar + per-inventory cadence table)
  data.doohPanels = transformDOOHPanels(data0, t, tCampaigns);

  // Transform DOOH plan-level rollup heatmap
  data.doohRollupHeatmap = transformDOOHRollupHeatmap(data0, data.doohPanels);

  // Transform Geography Targeting (Country › Region › City)
  data.geographyTargeting = transformGeographyTargeting(data0, tCampaigns);

  // Transform Geography Targeting POI rows (Excel sheet only)
  data.geographyTargetingPoiRows = transformGeographyPoiRows(
    data0,
    data.geographyTargeting,
    tCampaigns,
  );

  return data;
};

/** "DIGITAL_OOH" -> "Digital OOH" — mirrors CampaignPlanTab's formatChannelLabel. */
const formatChannelLabel = (channel: string): string => {
  const [first, ...rest] = channel.split("_");
  if (!first) return channel;
  return [first.charAt(0) + first.slice(1).toLowerCase(), ...rest].join(" ");
};

/** Noun for the Excel Plan sheet's "Goal" row, e.g. "4,200,000 unique reach". */
const GOAL_NOUN_BY_TYPE: Record<string, string> = {
  IMPRESSIONS: "impressions",
  REACH: "unique reach",
  SOV: "SOV",
  ADPLAYS: "ad plays",
};

function transformCampaignDetails(
  data: UnifiedMediaPlanData,
  t?: TFunction,
  tCampaigns?: TFunction,
): CampaignDetails {
  const headerInfo = data.headerInfo || {};
  const mediaPlan = data.mediaPlan || {};
  const brandDetails = mediaPlan.brandDetails || {};

  // Real brand + categories come from the campaign detail's `brand` field
  // (mediaPlan.brandDetails has no categories, only a legacy category string).
  const brandName = data.brand?.name || brandDetails.name || "--";
  const brandCategoryLabel = data.brand?.categories?.length
    ? data.brand.categories.map((category) => category.name).join(", ")
    : brandDetails.category || "";

  const normalizedGoal = normalizeGoalType(headerInfo.goalType);
  const goalNoun = normalizedGoal
    ? GOAL_NOUN_BY_TYPE[normalizedGoal]
    : undefined;
  const channels = data.mediaChannels || [];

  return {
    campaignName: headerInfo.name || "",
    campaignId: headerInfo.id || "",
    planNumber: data.planNumber || "",
    currency: headerInfo.currency || "",
    createdOn: formatDisplayDate(headerInfo.createdAt || "", t) || "",
    startDate: formatDisplayDate(headerInfo.startDate, t) || "",
    endDate: formatDisplayDate(headerInfo.endDate, t) || "",
    goal: headerInfo.goalType
      ? tCampaigns?.(
          `budget_goal.goal_types.${headerInfo.goalType.toLowerCase()}`,
        ) || headerInfo.goalType
      : "",
    kpi: headerInfo.targetValue ? formatNumber(headerInfo.targetValue) : "",
    createdBy: headerInfo.preparedBy || "",
    company: headerInfo.companyDetails?.name || "",
    emailAddress: headerInfo.userEmail || "",
    dsp:
      headerInfo.dsp === "ACTIVATE"
        ? tCampaigns?.("create_campaign.form.dsp_active") || headerInfo.dsp
        : headerInfo.dsp || "--",
    seatId: headerInfo.companyDetails?.seatId || "",
    brand: brandName,
    oohImpressions: headerInfo.impressions
      ? formatNumber(headerInfo.impressions)
      : "",
    uniqueReach: data.performanceMetrics?.estimatedReach
      ? formatNumber(data.performanceMetrics.estimatedReach)
      : "",
    averageFrequency: data.performanceMetrics?.estimatedFrequency
      ? data.performanceMetrics.estimatedFrequency.toFixed(2)
      : "",
    cpm: data.performanceMetrics?.avgCpm
      ? data.performanceMetrics.avgCpm.toFixed(2)
      : "",
    // Excel Plan-sheet-only fields below.
    status: headerInfo.status
      ? tCampaigns?.(`campaignsList.status.${headerInfo.status}`) ||
        headerInfo.status
      : "",
    durationLabel:
      headerInfo.duration !== undefined ? `${headerInfo.duration} days` : "",
    channelsLabel: channels.map(formatChannelLabel).join(", "),
    budget: headerInfo.budget,
    goalLabel:
      headerInfo.targetValue && goalNoun
        ? `${formatNumber(headerInfo.targetValue)} ${goalNoun}`
        : "",
    brandCategory: brandCategoryLabel,
    clientTypeLabel:
      data.clientType === "DIRECT_ADVERTISER"
        ? tCampaigns?.(
            "mediaPlanAnalytics.campaignPlan.buyerDetails.clientTypeDirect",
          ) || data.clientType
        : data.clientType === "AGENCY"
          ? tCampaigns?.(
              "mediaPlanAnalytics.campaignPlan.buyerDetails.clientTypeAgency",
            ) || data.clientType
          : data.clientType || "",
    agency: data.agency?.name || "",
  };
}

/**
 * Estimated Performance Metrics for the Excel Plan sheet — identical
 * fields/formulas to CampaignPlanTab.tsx's inline estimatedPerformanceRows,
 * persisted here so the Excel generator doesn't need the raw hook data.
 */
function transformEstimatedPerformanceMetrics(
  data: UnifiedMediaPlanData,
  tCampaigns?: TFunction,
): EstimatedPerformanceMetrics {
  const pm = data.performanceMetrics;
  const normalizedGoal = normalizeGoalType(data.headerInfo?.goalType);
  const isCPSGoal = normalizedGoal === "SOV" || normalizedGoal === "ADPLAYS";
  const sotPercent =
    pm?.plannedSot && pm?.totalSot ? (pm.plannedSot / pm.totalSot) * 100 : 0;
  // Ad plays only apply to digital (slot-based) inventory — classic OOH is
  // booked/priced daily with no play count.
  const channelSplit = data.costSplitByInventoryType || [];
  const hasDigitalInventory =
    channelSplit.length === 0 ||
    channelSplit.some((r) => r.name?.toLowerCase().includes("digital"));

  return {
    totalImpressions: pm?.estimatedImpression || 0,
    estimatedReach: pm?.estimatedReach || 0,
    avgFrequency: pm?.estimatedFrequency || 0,
    estAdPlays: pm?.estimatedAdPlays || 0,
    hasDigitalInventory,
    avgCpmLabel:
      tCampaigns?.(
        `mediaPlanAnalytics.campaignPlan.estimation.${isCPSGoal ? "avgCps" : "avgCpm"}`,
      ) || (isCPSGoal ? "Avg CPS" : "Avg CPM"),
    avgCpm: pm?.avgCpm || 0,
    ecpm: pm?.avgECpm || 0,
    sov: pm?.sov || 0,
    sot: sotPercent,
    totalCost: pm?.totalCost || 0,
    inventories: pm?.totalInventories || 0,
    cities: data.geographySummary?.cityCount || 0,
    channels: data.channelCount || 0,
    footnote: tCampaigns?.("mediaPlanAnalytics.campaignPlan.estimation.note"),
  };
}

// "18_24" -> "18-24"
const formatAge = (code: string): string => code.replace(/_/g, "-");

// "lower_middle" -> "Lower Middle"
const titleCase = (code: string): string =>
  code
    .split(/[-_]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");

/**
 * Targeting Applied section of the Excel Plan sheet, sourced entirely from
 * the campaign's real targeting config. Cinema-only venue-type entries are
 * filtered out ("we don't have that feature").
 */
function transformTargetingApplied(
  data: UnifiedMediaPlanData,
): TargetingApplied {
  const targeting = data.targeting;
  if (!targeting) return {};

  const demographics = [
    ...(targeting.demographics?.age || []).map(formatAge),
    ...(targeting.demographics?.gender || []).map(titleCase),
  ]
    .filter(Boolean)
    .join(", ");
  const income = (targeting.demographics?.income || [])
    .map(titleCase)
    .join(", ");
  const interests = (targeting.demographics?.interests || []).join(", ");
  const venueEnvironments = Array.from(
    new Set(
      [
        ...(targeting.venueTypes?.digitalOoh || []),
        ...(targeting.venueTypes?.classicOoh || []),
      ].map(titleCase),
    ),
  ).join(", ");
  const behaviour = (targeting.demographics?.behavior || []).join(", ");
  const signals = (targeting.signals || []).join(", ");
  const geography = data.geographySummary?.poiCount
    ? `${data.geographySummary.poiCount} POIs`
    : "";

  return {
    demographics,
    income,
    interests,
    venueEnvironments,
    behaviour,
    signals,
    geography,
  };
}

/**
 * Delivery Breakdown (Monthly) rows for the Excel Plan sheet — reuses
 * computeExpectedDelivery (./utils), the same bins/values already shown on
 * the on-screen Goals & KPIs chart.
 */
function transformDeliveryBreakdown(data: UnifiedMediaPlanData): {
  rows: DeliveryBreakdownRow[];
  granularity: "weekly" | "monthly";
} {
  const delivery = computeExpectedDelivery(
    data.headerInfo?.startDate,
    data.headerInfo?.endDate,
    data.performanceMetrics?.estimatedImpression || 0,
    data.performanceMetrics?.estimatedReach || 0,
  );

  return {
    rows: delivery.bins.map((bin) => ({
      period: bin.label,
      impressions: bin.value,
      reach: bin.reach,
    })),
    granularity: delivery.granularity,
  };
}

function transformStatePlanning(
  data: UnifiedMediaPlanData,
): StatePlanningRow[] {
  if (!data.costSplitByState || data.costSplitByState.length === 0) {
    return [];
  }

  return data.costSplitByState.map((item, index) => ({
    id: String(index + 1),
    stateName: item.name || "",
    population: item.population?.toLocaleString() || "0",
    inventories: item.totalInventories || 0,
    oohImpressions: formatNumber(item.impressions) || "0",
    reach: formatNumber(item.reach) || "0",
    frequency: item.frequency.toFixed(2) || "0",
    cpm: item.avgCpm || 0,
  }));
}

function transformCityPlanning(data: UnifiedMediaPlanData): StatePlanningRow[] {
  if (!data.costSplitByCity || data.costSplitByCity.length === 0) {
    return [];
  }

  return data.costSplitByCity.map((item, index) => ({
    id: String(index + 1),
    stateName: item.name || "",
    population: item.population?.toLocaleString() || "0",
    inventories: item.totalInventories || 0,
    oohImpressions: formatNumber(item.impressions) || "0",
    reach: formatNumber(item.reach) || "0",
    frequency: item.frequency.toFixed(2) || "0",
    cpm: item.avgCpm || 0,
  }));
}

function transformInventoryPlanning(
  data: UnifiedMediaPlanData,
): InventoryPlanningRow[] {
  if (
    !data.selectedInventory?.locations ||
    data.selectedInventory.locations.length === 0
  ) {
    return [];
  }

  return data.selectedInventory.locations.map((item, index) => {
    const detail = item.detail || {};
    const performance = item.performance || {};
    // const location = item.location?.location || {};

    return {
      id: String(index + 1),
      name: detail.name || "",
      billboardName: detail.name || "",
      referenceId: detail.referenceId || "",
      oohImpressions:
        formatNumber(
          performance.estimatedImpressions ??
            performance.estimatedImpression ??
            0,
        ) || "0",
      uniqueReach: formatNumber(performance.estimatedReach ?? 0) || "0",
      frequency: performance.estimatedFrequency || 0,
      ecpm: performance.cpmRate || 0,
    };
  });
}

function transformInventoryMapping(
  data: UnifiedMediaPlanData,
): InventoryMappingRow[] {
  const items = data.inventoriesMapping || [];

  return items.map((item, index) => ({
    id: String(index + 1),
    name: item.name || "",
    latitude: parseFloat(item.latitude) || 0,
    longitude: parseFloat(item.longitude) || 0,
    billboardName: item.billboardName || "",
    referenceId: item.referenceId || "",
    distanceFromCityCenter: item.distanceMeters || 0,
    stateName: item.stateName || "",
    districtName: item.districtName || "",
  }));
}

function transformInventoryDetails(
  data: UnifiedMediaPlanData,
): InventoryDetailsRow[] {
  if (
    !data.selectedInventory?.locations ||
    data.selectedInventory.locations.length === 0
  ) {
    return [];
  }

  return data.selectedInventory.locations.map((item, index) => {
    const detail = item.detail || {};
    const location = item.location?.location || {};
    const coordinates = location.locationCoordinates?.coordinates || [];
    const firstCoord = coordinates[0] || {};
    const operations = item.operations || {};
    const schedule = item.schedules || [];
    const performance = item.performance || {};

    const spotsPerHour = schedule
      .map((sch) => sch.spotsPerHour || 0)
      .reduce((acc, val) => acc + val, 0);

    const totalSpots = schedule
      .map((sch) => sch.spotsPerLoop || 0)
      .reduce((acc, val) => acc + val, 0);

    // Determine inventory type
    const inventoryType = detail.inventoryType?.toLowerCase() || "";
    let type: InventoryDetailsRow["type"] = InventoryTypeLowercase.CLASSIC;
    if (
      inventoryType.includes(InventoryTypeLowercase.DIGITAL) &&
      inventoryType.includes("network")
    ) {
      type = InventoryTypeLowercase.DIGITAL_NETWORK;
    } else if (inventoryType.includes(InventoryTypeLowercase.DIGITAL)) {
      type = InventoryTypeLowercase.DIGITAL;
    } else if (inventoryType.includes(InventoryTypeLowercase.CINEMA)) {
      type = InventoryTypeLowercase.CINEMA;
    } else if (inventoryType.includes("network")) {
      type = InventoryTypeLowercase.CLASSIC_NETWORK;
    } else if (inventoryType.includes(InventoryTypeLowercase.MOBILE)) {
      type = InventoryTypeLowercase.MOBILE;
    }

    // Get all image URLs - prefer images array, fallback to thumbnail
    const imageUrls =
      detail.images && detail.images.length > 0
        ? detail.images
        : detail.thumbnail
          ? [detail.thumbnail]
          : [];
    const assetImages = imageUrls.join(", ");

    const baseRow: InventoryDetailsRow = {
      id: String(index + 1),
      type,
      billboardName: detail.name || "",
      referenceId: detail.referenceId || "",
      mediaOwner: detail.mediaOwnerName || "",
      format: detail.format || "",
      category: detail.category || "",
      exclusions: "",
      assetImages: assetImages,
      city: location.city || "",
      thumbnailUrl: imageUrls[0] || "",
      impressions:
        performance.estimatedImpression ??
        performance.estimatedImpressions ??
        0,
      playsPerDay: performance.perDayAdPlays || 0,
      cpm: performance.cpmRate || 0,
      spotRate: performance.spotRate || 0,
      schedulePattern: classifyDominantSchedulePattern(
        schedule,
        operations.operationDays,
      ),
    };
    const panels = detail.panels?.[0] || {};

    // Add type-specific fields
    if (type === "classic") {
      return {
        ...baseRow,
        size:
          panels.physicalWidth && panels.physicalHeight
            ? `${panels.physicalWidth} x ${panels.physicalHeight}`
            : "",
        latitude: firstCoord.latitude?.toString() || "",
        longitude: firstCoord.longitude?.toString() || "",
        location: location.address || "--",
        venueType: venueTypeToText(detail.venueType) || "--",
        noOfFaces: "",
        boardFacing: "",
        languageSupport: "",
        description: "",
      };
    } else if (type === "classic network") {
      return {
        ...baseRow,
        size:
          panels.physicalWidth && panels.physicalHeight
            ? `${panels.physicalWidth} x ${panels.physicalHeight}`
            : "",
        noOfBillboards: "",
      };
    } else if (type === "digital") {
      return {
        ...baseRow,
        resolution:
          panels.pixelWidth && panels.pixelHeight
            ? `${panels.pixelWidth} x ${panels.pixelHeight}`
            : "",
        creative: "",
        latitude: firstCoord.latitude?.toString() || "",
        longitude: firstCoord.longitude?.toString() || "",
        location: location.address || "",
        venueType: venueTypeToText(detail.venueType) || "",
        noOfScreens: detail.screens?.toString() || "",
        spotDuration: operations.slotDuration?.toString() || "",
        spotsPerHour: spotsPerHour,
        totalSpots: totalSpots,
        languageSupport: "--",
      };
    } else if (type === "digital network") {
      const panels = detail.panels?.[0] || {};
      return {
        ...baseRow,
        resolution:
          panels.pixelWidth && panels.pixelHeight
            ? `${panels.pixelWidth} x ${panels.pixelHeight}`
            : "",
        creative: "--",
        noOfBillboards: "--",
        noOfScreens: detail.screens?.toString() || "",
        spotDuration: operations.slotDuration?.toString() || "",
        spotsPerHour: spotsPerHour,
        totalSpots: totalSpots,
      };
    } else if (type === "cinema") {
      const cinemaFields = detail.cinemaFields || {};
      return {
        ...baseRow,
        // Cinema is bought by operator/hall/showtime; surface the buy
        // environment in the generic Inventory Details view.
        location: cinemaFields.cinemaName || location.address || "--",
        venueType: cinemaFields.hallName || "--",
        description: (cinemaFields.showtimeWindows || [])
          .map((window) => window.label)
          .filter(Boolean)
          .join(", "),
      };
    } else {
      // mobile
      return {
        ...baseRow,
        campaignBudget: "",
        dailyBudget: "",
        bidRate: "",
        advertiserDomain: "",
        frequencyCapping: "",
      };
    }
  });
}

function transformCostingDetails(data: UnifiedMediaPlanData): CostingDetails {
  const priceSummary = data.priceSummary;
  const performanceMetrics = data.performanceMetrics || null;
  const totalCost = performanceMetrics?.totalCost || 0;

  // Use price summary data if available, otherwise fall back to performance metrics
  const discountedMediaCost = priceSummary?.discountedMediaCost || totalCost;
  const proposedPrice = priceSummary?.proposedPrice || totalCost;
  const standardFees = priceSummary?.standardFees || 0;

  // Map PriceSummaryCustomFee to CustomFee format (from campaign.types.ts)
  const customFees =
    priceSummary?.customFees?.map((fee) => ({
      name: fee.name,
      amount: fee.effectiveCustomFee || 0,
      includeInPlan: fee.isIncludeInMediaPlan,
      description: fee.description,
    })) || undefined;

  return {
    subTotal: discountedMediaCost,
    outdoorCommission: 0,
    agencyCommission: 0,
    discount: 0,
    netTotal: discountedMediaCost,
    additionalCost: 0,
    sst: 0,
    platformFee: standardFees,
    grandTotal: proposedPrice,
    // Price summary fields for direct access
    discountedMediaCost,
    proposedPrice,
    customFees,
  };
}

/**
 * Per-inventory row for the flat Costing table. Base/Proposed/Accepted CPM
 * come from the Price Management schedule-price API (a separate query from
 * the rest of media-plan analytics), keyed by inventoryId. An inventory with
 * multiple flight segments has one schedule-price entry per segment; those
 * are combined into a single Base/Proposed/Accepted CPM per inventory,
 * weighted by each schedule's approximate cost share (cpmRate × reach — the
 * schedule-price API has no direct per-schedule cost field to weight by).
 * Fee share has no per-inventory source anywhere in the API, so campaign-level
 * fees (priceSummary.standardFees + included custom fees) are pro-rated
 * across rows by each row's share of total media cost.
 */
function transformCostingInventoryRows(
  data: UnifiedMediaPlanData,
): CostingInventoryRow[] {
  if (
    !data.selectedInventory?.locations ||
    data.selectedInventory.locations.length === 0
  ) {
    return [];
  }

  const schedulesByInventoryId = new Map<string, CampaignSchedulePriceItem[]>();
  (data.campaignSchedulePrices || []).forEach((item) => {
    const list = schedulesByInventoryId.get(item.inventoryId) || [];
    list.push(item);
    schedulesByInventoryId.set(item.inventoryId, list);
  });

  const priceByInventoryId = new Map<
    string,
    { baseCpm: number; proposed: number; accepted: number }
  >();
  schedulesByInventoryId.forEach((items, inventoryId) => {
    const weights = items.map(
      (item) => (item.cpmRate || 0) * (item.reach || 0),
    );
    const weightSum = weights.reduce((sum, w) => sum + w, 0);
    const weightedAvg = (
      getValue: (item: CampaignSchedulePriceItem) => number,
    ) =>
      weightSum > 0
        ? items.reduce((sum, item, i) => sum + getValue(item) * weights[i], 0) /
          weightSum
        : items.reduce((sum, item) => sum + getValue(item), 0) / items.length;

    priceByInventoryId.set(inventoryId, {
      baseCpm: weightedAvg((item) => item.cpmRate || 0),
      proposed: weightedAvg((item) => item.proposedRate || 0),
      accepted: weightedAvg((item) => item.currentRate || 0),
    });
  });

  const rows = data.selectedInventory.locations.map((item, index) => {
    const detail = item.detail || {};
    const location = item.location?.location || {};
    const performance = item.performance || {};
    const pricing = priceByInventoryId.get(detail.id || "");

    return {
      id: String(index + 1),
      name: detail.name || "",
      city: location.city || "",
      baseCpm: pricing?.baseCpm ?? performance.cpmRate ?? 0,
      proposed: pricing?.proposed ?? performance.cpmRate ?? 0,
      accepted: pricing?.accepted ?? performance.cpmRate ?? 0,
      impressions:
        performance.estimatedImpressions ??
        performance.estimatedImpression ??
        0,
      mediaCost: performance.estimatedCost || 0,
      feeShare: 0,
      total: 0,
    };
  });

  const totalFees =
    (data.priceSummary?.standardFees || 0) +
    (data.priceSummary?.customFees || [])
      .filter((fee) => fee.isIncludeInMediaPlan)
      .reduce((sum, fee) => sum + (fee.effectiveCustomFee || 0), 0);
  const totalMediaCost = rows.reduce((sum, row) => sum + row.mediaCost, 0);

  return rows.map((row) => {
    const feeShare =
      totalMediaCost > 0 ? (row.mediaCost / totalMediaCost) * totalFees : 0;
    return {
      ...row,
      feeShare,
      total: row.mediaCost + feeShare,
    };
  });
}

function transformClassicInventory(
  data: UnifiedMediaPlanData,
): ClassicInventoryRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return (
        type.includes(InventoryTypeLowercase.CLASSIC) &&
        !type.includes("network")
      );
    })
    .map((item, index) => {
      const detail = item.detail || {};
      const performance = item.performance || {};

      return {
        id: String(index + 1),
        billboardName: detail.name || "",
        referenceId: detail.referenceId || "",
        mediaOwner: detail.mediaOwnerName || "",
        mediaCost: performance.estimatedCost || 0,
        lighting: 0,
        production: 0,
        otherFees: 0,
        subTotal: performance.estimatedCost || 0,
      };
    });
}

function transformClassicNetworkInventory(
  data: UnifiedMediaPlanData,
): ClassicInventoryRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return (
        type.includes(InventoryTypeLowercase.CLASSIC) &&
        type.includes("network")
      );
    })
    .map((item, index) => {
      const detail = item.detail || {};
      const performance = item.performance || {};

      return {
        id: String(index + 1),
        billboardName: detail.name || "",
        referenceId: detail.referenceId || "",
        mediaOwner: detail.mediaOwnerName || "",
        mediaCost: performance.estimatedCost || 0,
        lighting: 0,
        production: 0,
        otherFees: 0,
        subTotal: performance.estimatedCost || 0,
      };
    });
}

function transformDigitalInventory(
  data: UnifiedMediaPlanData,
): DigitalInventoryRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return (
        type.includes(InventoryTypeLowercase.DIGITAL) &&
        !type.includes("network")
      );
    })
    .map((item, index) => {
      const detail = item.detail || {};
      const performance = item.performance || {};

      return {
        id: String(index + 1),
        billboardName: detail.name || "",
        referenceId: detail.referenceId || "",
        mediaOwner: detail.mediaOwnerName || "",
        contentManagementFee: performance.estimatedCost || 0,
        subTotal: performance.estimatedCost || 0,
      };
    });
}

function transformDigitalNetworkInventory(
  data: UnifiedMediaPlanData,
): DigitalInventoryRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return (
        type.includes(InventoryTypeLowercase.DIGITAL) &&
        type.includes("network")
      );
    })
    .map((item, index) => {
      const detail = item.detail || {};
      const performance = item.performance || {};

      return {
        id: String(index + 1),
        billboardName: detail.name || "",
        referenceId: detail.referenceId || "",
        mediaOwner: detail.mediaOwnerName || "",
        contentManagementFee: performance.estimatedCost || 0,
        subTotal: performance.estimatedCost || 0,
      };
    });
}

/**
 * Cinema line items for the dedicated Cinema analytics table/sheet. The buy
 * unit is the environment (operator → cinema → hall → showtime window +
 * genre/rating constraints); films are only an indicative read-only preview,
 * never a buy unit. Sourced from item.detail.cinemaFields — degrades to blank
 * strings when a field is absent.
 */
function transformCinemaInventory(
  data: UnifiedMediaPlanData,
): CinemaInventoryRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => isCinemaInventory(item))
    .map((item, index) => {
      const detail = item.detail || {};
      const performance = item.performance || {};
      const cinemaFields = detail.cinemaFields || {};

      const showtimeWindows = (cinemaFields.showtimeWindows || [])
        .map((window) => window.label)
        .filter(Boolean)
        .join(", ");

      return {
        id: String(index + 1),
        name: detail.name || "",
        operator: cinemaFields.operator || "",
        cinemaName: cinemaFields.cinemaName || "",
        hall:
          cinemaFields.hallName ||
          (cinemaFields.hallNumber != null
            ? String(cinemaFields.hallNumber)
            : ""),
        showtimeWindows,
        genres: (cinemaFields.genres || []).join(", "),
        ratings: (cinemaFields.ratings || []).join(", "),
        impressions:
          performance.estimatedImpression ??
          performance.estimatedImpressions ??
          0,
        cpm: performance.cpmRate || 0,
        mediaCost: performance.estimatedCost || 0,
      };
    });
}

function transformOperationDetails(
  data: UnifiedMediaPlanData,
  t?: TFunction,
): OperationDetailsData {
  if (!data.selectedInventory?.locations) {
    return {};
  }

  const classic: ClassicOperationScheduleRow[] = [];
  const digital: DigitalOperationScheduleRow[] = [];
  const mobile: MobileOperationScheduleRow[] = [];

  let rowIndex = 0;

  data.selectedInventory.locations.forEach((item) => {
    const detail = item.detail || {};
    const location = item.location?.location || {};
    const operations = item.operations || {};
    const schedules = item.schedules || [];
    const type = detail.inventoryType?.toLowerCase() || "";
    const inventoryName = detail.name || "";
    const referenceId = detail.referenceId || "";
    const format = detail.format || "";
    const city = location.city || "";

    // If no schedules, skip this inventory
    if (!schedules || schedules.length === 0) {
      return;
    }

    // Process each schedule for this inventory
    schedules.forEach((schedule) => {
      const scheduleStartDate = schedule.startDate || "";
      const scheduleEndDate = schedule.endDate || "";

      // Calculate operation days the same way campaign duration is calculated
      // (inclusive of both start and end dates)
      const operationDays = scheduleDayCount(schedule);

      if (
        type.includes(InventoryTypeLowercase.CLASSIC) &&
        !type.includes("network")
      ) {
        classic.push({
          id: String(++rowIndex),
          inventoryName,
          referenceId,
          format,
          city,
          segment: schedule.name || "",
          startDate: formatDisplayDate(scheduleStartDate, t),
          endDate: formatDisplayDate(scheduleEndDate, t),
          operationDays,
        });
      } else if (type.includes(InventoryTypeLowercase.DIGITAL)) {
        // Calculate operation hours from schedule
        const bookingMatrix = schedule.bookingMatrix || {};

        // Calculate total hours from booking matrix (sum of all hours across all dates)
        let operationHours = 0;
        const allHours: number[] = [];
        Object.values(bookingMatrix).forEach((hours) => {
          operationHours += hours.length;
          allHours.push(...hours);
        });

        // Calculate start and end time from booking matrix (min and max hours)
        let startTime = "";
        let endTime = "";
        if (allHours.length > 0) {
          const minHour = Math.min(...allHours);
          const maxHour = Math.max(...allHours);
          // Format as "HH:00" in 24-hour format
          startTime = `${String(minHour).padStart(2, "0")}:00`;
          endTime = `${String(maxHour).padStart(2, "0")}:00`;
        } else {
          // Fallback to operations times if booking matrix is empty
          startTime = operations.startTime || "";
          endTime = operations.endTime || "";
        }

        // Calculate total spots: total hours × spots per hour
        const spotsPerHour = schedule.spotsPerHour || 0;
        const totalSpots = operationHours * spotsPerHour;

        digital.push({
          id: String(++rowIndex),
          inventoryName,
          referenceId,
          format,
          city,
          segment: schedule.name || "",
          startDate: formatDisplayDate(scheduleStartDate, t),
          endDate: formatDisplayDate(scheduleEndDate, t),
          scheduleType: "--",
          operationDays,
          operationHours,
          startTime,
          endTime,
          totalSpots,
        });
      } else if (type.includes("mobile")) {
        // Calculate operation hours for mobile
        const bookingMatrix = schedule.bookingMatrix || {};

        let operationHours = 0;
        const allHours: number[] = [];
        Object.values(bookingMatrix).forEach((hours) => {
          operationHours += hours.length;
          allHours.push(...hours);
        });

        // Calculate start and end time from booking matrix (min and max hours)
        let startTime = "";
        let endTime = "";
        if (allHours.length > 0) {
          const minHour = Math.min(...allHours);
          const maxHour = Math.max(...allHours);
          // Format as "HH:00" in 24-hour format
          startTime = `${String(minHour).padStart(2, "0")}:00`;
          endTime = `${String(maxHour).padStart(2, "0")}:00`;
        } else {
          // Fallback to operations times if booking matrix is empty
          startTime = operations.startTime || "";
          endTime = operations.endTime || "";
        }

        mobile.push({
          id: String(++rowIndex),
          inventoryName,
          referenceId,
          format,
          city,
          segment: schedule.name || "",
          startDate: formatDisplayDate(scheduleStartDate, t),
          endDate: formatDisplayDate(scheduleEndDate, t),
          scheduleType: "--",
          operationDays,
          operationHours,
          startTime,
          endTime,
        });
      }
    });
  });

  return {
    classic: classic.length > 0 ? classic : undefined,
    digital: digital.length > 0 ? digital : undefined,
    mobile: mobile.length > 0 ? mobile : undefined,
  };
}

function transformDOOHSchedules(
  data: UnifiedMediaPlanData,
  t?: TFunction,
): DOOHScheduleRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  const doohSchedules: DOOHScheduleRow[] = [];
  let sno = 0;

  data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return type.includes(InventoryTypeLowercase.DIGITAL);
    })
    .forEach((item) => {
      const detail = item.detail || {};
      const operations = item.operations || {};
      const schedules = item.schedules || [];
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const operatingTimes: any = operations.operatingTimes || {};

      // If no schedules, skip this inventory
      if (!schedules || schedules.length === 0) {
        return;
      }

      // Find minimum and maximum dates from all schedules
      let minDate: Date | null = null;
      let maxDate: Date | null = null;

      schedules.forEach((schedule) => {
        if (schedule.startDate) {
          const start = new Date(schedule.startDate);
          if (!minDate || start < minDate) {
            minDate = start;
          }
        }
        if (schedule.startDate) {
          const end = new Date(schedule.endDate);
          if (!maxDate || end > maxDate) {
            maxDate = end;
          }
        }
      });

      // If we have valid dates, create a schedule row
      if (minDate && maxDate) {
        const startDate = new Date(minDate).toISOString().split("T")[0] || "";
        const endDate = new Date(maxDate).toISOString().split("T")[0] || "";
        const diffTime =
          new Date(maxDate).getTime() - new Date(minDate).getTime();
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
        // Ensure at least 1 day if dates are valid
        const duration = diffDays > 0 ? diffDays : 1;

        // Format operation hours from operatingTimes
        // Find the earliest start time and latest end time across all days
        let earliestStart: string | null = null;
        let latestEnd: string | null = null;

        const dayKeys = [
          "MONDAY",
          "TUESDAY",
          "WEDNESDAY",
          "THURSDAY",
          "FRIDAY",
          "SATURDAY",
          "SUNDAY",
        ];

        dayKeys.forEach((day) => {
          if (operatingTimes[day] && operatingTimes[day].length > 0) {
            operatingTimes[day].forEach(
              (timeRange: { start?: string; end?: string }) => {
                if (timeRange.start) {
                  if (!earliestStart || timeRange.start < earliestStart) {
                    earliestStart = timeRange.start;
                  }
                }
                if (timeRange.end) {
                  if (!latestEnd || timeRange.end > latestEnd) {
                    latestEnd = timeRange.end;
                  }
                }
              },
            );
          }
        });

        // Format operation hours
        let operationHours = "";
        if (earliestStart && latestEnd) {
          // Format time from "HH:MM:SS" to "HH:MM"
          const startTime = (earliestStart as string).slice(0, 5);
          const endTime = (latestEnd as string).slice(0, 5);
          operationHours = `${startTime} - ${endTime}`;
        }

        // Check which days are active based on operatingTimes
        const mon = !!operatingTimes.MONDAY && operatingTimes.MONDAY.length > 0;
        const tue =
          !!operatingTimes.TUESDAY && operatingTimes.TUESDAY.length > 0;
        const wed =
          !!operatingTimes.WEDNESDAY && operatingTimes.WEDNESDAY.length > 0;
        const thu =
          !!operatingTimes.THURSDAY && operatingTimes.THURSDAY.length > 0;
        const fri = !!operatingTimes.FRIDAY && operatingTimes.FRIDAY.length > 0;
        const sat =
          !!operatingTimes.SATURDAY && operatingTimes.SATURDAY.length > 0;
        const sun = !!operatingTimes.SUNDAY && operatingTimes.SUNDAY.length > 0;

        doohSchedules.push({
          id: String(++sno),
          sno,
          billboardName: detail.name || "",
          startDate: formatDisplayDate(startDate, t),
          endDate: formatDisplayDate(endDate, t),
          duration,
          operationHours,
          mon,
          tue,
          wed,
          thu,
          fri,
          sat,
          sun,
        });
      }
    });

  return doohSchedules;
}

function transformDOOHScheduleSummary(
  data: UnifiedMediaPlanData,
  t?: TFunction,
): DOOHScheduleSummary {
  const headerInfo = data.headerInfo || {};
  const performanceMetrics = data.performanceMetrics || null;

  const startDate = headerInfo.startDate || "";
  const endDate = headerInfo.endDate || "";
  const start = new Date(startDate);
  const end = new Date(endDate);
  const duration = Math.ceil(
    (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24),
  );

  return {
    campaignName: headerInfo.name || "",
    duration,
    startDate: formatDisplayDate(startDate, t),
    totalAdPlays: performanceMetrics?.estimatedAdPlays
      ? formatNumber(performanceMetrics.estimatedAdPlays)
      : "",
    totalImpressions: headerInfo.impressions
      ? formatNumber(headerInfo.impressions)
      : "",
  };
}

const FULL_WEEK: Array<InventorySchedule["scheduleDays"][number]> = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const scheduleOpHoursLabel = (schedule: InventorySchedule): string => {
  const allHours: number[] = [];
  Object.values(schedule.bookingMatrix || {}).forEach((hours) => {
    allHours.push(...hours);
  });
  if (allHours.length === 0) return "--";
  const minHour = Math.min(...allHours);
  const maxHour = Math.max(...allHours);
  return `${formatHourTo12Hour(minHour)}–${formatHourTo12Hour((maxHour + 1) % 24)}`;
};

// Formats a "HH:mm" / "HH:mm:ss" time string in the same 12-hour style as
// formatHourTo12Hour, but preserving actual minutes instead of forcing :00.
const formatOperationTime = (time: string): string => {
  const [hoursStr, minutesStr = "00"] = time.split(":");
  const hour = parseInt(hoursStr, 10);
  const period = hour >= 12 ? "PM" : "AM";
  const hour12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
  return `${String(hour12).padStart(2, "0")}:${minutesStr.padStart(2, "0")} ${period}`;
};

// Inventory's own operating hours (item.operations), independent of any
// schedule's bookingMatrix — same source InventoryCard uses for its clock icon.
const inventoryOpHoursLabel = (operations: InventoryOperations): string => {
  const operationTimes = operations.operatingTimes
    ? extractOperationTimes(operations.operatingTimes)
    : operations.startTime && operations.endTime
      ? { startTime: operations.startTime, endTime: operations.endTime }
      : null;
  if (!operationTimes) return "--";
  return `${formatOperationTime(operationTimes.startTime)}–${formatOperationTime(operationTimes.endTime)}`;
};

const scheduleDayCount = (schedule: InventorySchedule): number => {
  if (!schedule.startDate || !schedule.endDate) return schedule.duration || 0;
  const diffDays = Math.ceil(
    (new Date(schedule.endDate).getTime() -
      new Date(schedule.startDate).getTime()) /
      (1000 * 60 * 60 * 24),
  );
  return Math.max(diffDays + 1, 1);
};

/**
 * Classify an inventory's dominant (longest) schedule segment against the
 * existing SCHEDULE_PATTERNS classifier (@utils/schedule.utils). Shared by
 * transformDOOHPanels (digital only) and transformInventoryDetails's Excel
 * "Schedule" column (all inventory types).
 */
const classifyDominantSchedulePattern = (
  schedules: InventorySchedule[],
  operationDays?: string[],
): string => {
  if (schedules.length === 0) return "--";
  const dominantSchedule = schedules.reduce((longest, schedule) =>
    scheduleDayCount(schedule) > scheduleDayCount(longest) ? schedule : longest,
  );
  const availableDays = (operationDays?.length ? operationDays : FULL_WEEK).map(
    convertDayName,
  );
  const form = mapScheduleToFormState(
    dominantSchedule,
    dominantSchedule.spotsPerHour || 1,
  );
  return detectSchedulePattern(
    form.scheduleDate,
    form.selectedDays,
    form.selectedHours,
    availableDays,
  );
};

/**
 * DOOH Schedule Calendar + Per-inventory cadence table data — one row per
 * digital inventory ("panel"), each carrying its own schedule segments
 * (with real bookingMatrix-derived active dates for the weekly Gantt) plus
 * day-weighted-average cadence metrics (spots/loop, spots/hour, active
 * hrs/day) across those segments. Days/Week is the union of scheduleDays
 * across all segments (a plain count of distinct active weekdays, not an
 * average). Pattern reuses the existing SCHEDULE_PATTERNS classifier
 * (@utils/schedule.utils detectSchedulePattern) against the panel's dominant
 * (longest) segment — an exact-match classifier, so organically-drawn
 * schedules will often resolve to "custom".
 */
function transformDOOHPanels(
  data: UnifiedMediaPlanData,
  t?: TFunction,
  tCampaigns?: TFunction,
): DOOHPanelRow[] {
  if (!data.selectedInventory?.locations) {
    return [];
  }

  return data.selectedInventory.locations
    .filter((item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return (
        type.includes(InventoryTypeLowercase.DIGITAL) &&
        (item.schedules || []).length > 0
      );
    })
    .map((item, index) => {
      const detail = item.detail || {};
      const location = item.location?.location || {};
      const operations = item.operations || {};
      const schedules = item.schedules || [];

      const segments: DOOHScheduleSegmentRow[] = schedules.map(
        (schedule, segIndex) => ({
          id: `${detail.referenceId || index}-${schedule.id || segIndex}`,
          segmentName: schedule.name || "",
          startDate: formatDisplayDate(schedule.startDate, t),
          endDate: formatDisplayDate(schedule.endDate, t),
          days: scheduleDayCount(schedule),
          opHoursLabel: scheduleOpHoursLabel(schedule),
          activeDates: Object.entries(schedule.bookingMatrix || {})
            .filter(([, hours]) => Array.isArray(hours) && hours.length > 0)
            .map(([date]) => date),
        }),
      );

      const totalDays = segments.reduce((sum, seg) => sum + seg.days, 0) || 1;
      const weightedAvg = (getValue: (schedule: InventorySchedule) => number) =>
        schedules.reduce(
          (sum, schedule, i) => sum + getValue(schedule) * segments[i].days,
          0,
        ) / totalDays;

      const activeHoursPerDay = weightedAvg((schedule) => {
        const dayHourCounts = Object.values(schedule.bookingMatrix || {});
        return dayHourCounts.length > 0
          ? dayHourCounts.reduce((sum, hours) => sum + hours.length, 0) /
              dayHourCounts.length
          : 0;
      });

      const daysPerWeekUnion = new Set<string>();
      schedules.forEach((schedule) =>
        (schedule.scheduleDays || []).forEach((day) =>
          daysPerWeekUnion.add(day),
        ),
      );

      const opHoursLabel = inventoryOpHoursLabel(operations);

      const rawStarts = schedules.map((s) => s.startDate).filter(Boolean);
      const rawEnds = schedules.map((s) => s.endDate).filter(Boolean);
      const minStart = [...rawStarts].sort()[0] || "";
      const maxEnd = [...rawEnds].sort().slice(-1)[0] || "";
      const days =
        minStart && maxEnd
          ? Math.max(
              Math.ceil(
                (new Date(maxEnd).getTime() - new Date(minStart).getTime()) /
                  (1000 * 60 * 60 * 24),
              ) + 1,
              1,
            )
          : 0;

      // Classify pattern off the dominant (longest) segment — a single panel
      // can only show one Pattern badge.
      const pattern = classifyDominantSchedulePattern(
        schedules,
        operations.operationDays,
      );

      // This transform only ever processes digital inventories (see the
      // filter above), so the channel label is always the Digital one.
      const channel =
        tCampaigns?.("media_plan.inventory_mix.channel_digital_ooh") ||
        "Digital";

      return {
        id: detail.referenceId || String(index + 1),
        inventoryName: detail.name || "",
        referenceId: detail.referenceId || "",
        format: detail.format || "",
        city: location.city || "",
        channel,
        startDate: formatDisplayDate(minStart, t),
        endDate: formatDisplayDate(maxEnd, t),
        days,
        opHoursLabel,
        segments,
        spotsPerLoop: weightedAvg((s) => s.spotsPerLoop || 0),
        spotsPerHour: weightedAvg((s) => s.spotsPerHour || 0),
        activeHoursPerDay,
        daysPerWeek: daysPerWeekUnion.size,
        sov: weightedAvg((s) => s.sov || 0),
        pattern,
      };
    });
}

/**
 * Plan-level rollup heatmap for the DOOH Schedules tab — cross-panel
 * (weekday, hour) activity counts via computeDOOHRollupHeatmap, plus the
 * distinct classified Pattern count across the already-transformed panels.
 */
function transformDOOHRollupHeatmap(
  data: UnifiedMediaPlanData,
  panels: DOOHPanelRow[],
): DOOHRollupHeatmap {
  const digitalLocations = (data.selectedInventory?.locations || []).filter(
    (item) => {
      const type = item.detail?.inventoryType?.toLowerCase() || "";
      return type.includes(InventoryTypeLowercase.DIGITAL);
    },
  );

  const { rows, maxCount, totalSchedules } =
    computeDOOHRollupHeatmap(digitalLocations);
  const totalPatterns = new Set(panels.map((p) => p.pattern)).size;

  return { rows, maxCount, totalSchedules, totalPatterns };
}

interface GeographyAccumulator {
  inventories: number;
  impressions: number;
  reach: number;
  cost: number;
}

const emptyGeographyAccumulator = (): GeographyAccumulator => ({
  inventories: 0,
  impressions: 0,
  reach: 0,
  cost: 0,
});

const addToGeographyAccumulator = (
  acc: GeographyAccumulator,
  item: GeographyAccumulator,
) => {
  acc.inventories += item.inventories;
  acc.impressions += item.impressions;
  acc.reach += item.reach;
  acc.cost += item.cost;
};

const geographyAccumulatorToRow = (
  id: string,
  level: GeographyTargetingRow["level"],
  depth: number,
  name: string,
  acc: GeographyAccumulator,
): GeographyTargetingRow => ({
  id,
  level,
  depth,
  name,
  inventories: acc.inventories,
  impressions: acc.impressions,
  reach: acc.reach,
  frequency: acc.reach > 0 ? acc.impressions / acc.reach : 0,
  ecpm: acc.impressions > 0 ? (acc.cost / acc.impressions) * 1000 : 0,
});

/**
 * Country › Region › City hierarchy. The tree shape (which cities sit under
 * which states/country) is built client-side from each selected inventory
 * item's own location fields, since the cost-split-by-state/city API rows
 * are two independent flat lists with no parent linkage between them.
 *
 * The *numbers* at every level, however, are reconciled to the same
 * cost-split-by endpoints City Insights / State Planning use
 * (`data.costSplitByCity` / `data.costSplitByState`, matched by name) rather
 * than left as a raw sum of each inventory's own `estimatedReach`. Reach is
 * not additive across inventories (overlapping audiences in the same
 * city/state would be double-counted by a plain sum), so summing
 * per-inventory reach client-side systematically over-counts vs. the
 * backend's own reach model — this was the cause of Geography Targeting
 * showing a different reach than City Insights for the same city. Falls
 * back to the client-side sum only if a node has no matching cost-split row
 * (e.g. an inventory missing location data, bucketed under "N/A").
 *
 * The country row keeps its pre-existing override: impressions/reach/cost
 * replaced with the campaign's overall performanceMetrics
 * (estimatedImpression/estimatedReach/totalCost — the /media-plan API
 * figures), since campaigns only ever target one country and that's the
 * authoritative total. Sorted by impressions descending at every level,
 * matching the convention used elsewhere in this tab (City Insights,
 * Costing).
 */
function transformGeographyTargeting(
  data: UnifiedMediaPlanData,
  tCampaigns?: TFunction,
): GeographyTargetingRow[] {
  const locations = data.selectedInventory?.locations || [];
  if (locations.length === 0) return [];

  const notAvailable =
    tCampaigns?.("media_plan.geographic_plan.not_available") || "N/A";

  const normalizeName = (name: string) => name.trim().toLowerCase();
  const cityCostSplitByName = new Map(
    (data.costSplitByCity || []).map((item) => [
      normalizeName(item.name || ""),
      item,
    ]),
  );
  const stateCostSplitByName = new Map(
    (data.costSplitByState || []).map((item) => [
      normalizeName(item.name || ""),
      item,
    ]),
  );

  // Reconciles a client-computed row to the matching backend cost-split row
  // (same authoritative reach/frequency/eCPM City Insights and State
  // Planning already show), falling back to the raw sum if no match exists.
  const reconcileToCostSplit = (
    row: GeographyTargetingRow,
    match: CostSplitByCampaignData | undefined,
  ): GeographyTargetingRow =>
    match
      ? {
          ...row,
          inventories: match.totalInventories ?? row.inventories,
          impressions: match.impressions ?? row.impressions,
          reach: match.reach ?? row.reach,
          frequency: match.frequency ?? row.frequency,
          ecpm: match.avgCpm ?? row.ecpm,
        }
      : row;

  // country -> state -> city -> accumulator
  const tree = new Map<
    string,
    Map<string, Map<string, GeographyAccumulator>>
  >();

  locations.forEach((item) => {
    const performance = item.performance || {};
    const geo = item.location?.location || {};
    const country = geo.country || notAvailable;
    const state = geo.state || notAvailable;
    const city = geo.city || notAvailable;

    const leaf: GeographyAccumulator = {
      inventories: 1,
      impressions:
        performance.estimatedImpressions ??
        performance.estimatedImpression ??
        0,
      reach: performance.estimatedReach || 0,
      cost: performance.estimatedCost || 0,
    };

    if (!tree.has(country)) tree.set(country, new Map());
    const states = tree.get(country)!;
    if (!states.has(state)) states.set(state, new Map());
    const cities = states.get(state)!;
    if (!cities.has(city)) cities.set(city, emptyGeographyAccumulator());
    addToGeographyAccumulator(cities.get(city)!, leaf);
  });

  // Campaigns only ever target a single country, so its overall
  // impressions/reach/cost (from the /media-plan API's performanceMetrics,
  // same figures shown on Estimated Performance Metrics) are the
  // authoritative country-level numbers — more accurate than summing
  // individually-fetched inventory data, which can under-count if any item
  // is missing location data (falls into a separate "N/A" bucket instead of
  // the real country) or use a different reach-weighting methodology than
  // the backend's estimatedFrequency/avgECpm.
  const campaignImpressions = data.performanceMetrics?.estimatedImpression;
  const campaignReach = data.performanceMetrics?.estimatedReach;
  const campaignCost = data.performanceMetrics?.totalCost;

  const rows: GeographyTargetingRow[] = [];
  let rowIndex = 0;

  const sortByImpressionsDesc = <T>(
    entries: Array<[string, T]>,
    getImpressions: (value: T) => number,
  ) => [...entries].sort((a, b) => getImpressions(b[1]) - getImpressions(a[1]));

  const cityTotalImpressions = (cities: Map<string, GeographyAccumulator>) =>
    [...cities.values()].reduce((sum, acc) => sum + acc.impressions, 0);
  const stateTotalImpressions = (
    states: Map<string, Map<string, GeographyAccumulator>>,
  ) =>
    [...states.values()].reduce(
      (sum, cities) => sum + cityTotalImpressions(cities),
      0,
    );

  sortByImpressionsDesc([...tree.entries()], stateTotalImpressions).forEach(
    ([country, states]) => {
      const countryAcc = emptyGeographyAccumulator();
      [...states.values()].forEach((cities) =>
        [...cities.values()].forEach((acc) =>
          addToGeographyAccumulator(countryAcc, acc),
        ),
      );
      if (campaignImpressions !== undefined) {
        countryAcc.impressions = campaignImpressions;
      }
      if (campaignReach !== undefined) {
        countryAcc.reach = campaignReach;
      }
      if (campaignCost !== undefined) {
        countryAcc.cost = campaignCost;
      }
      rows.push(
        geographyAccumulatorToRow(
          String(++rowIndex),
          "country",
          0,
          country,
          countryAcc,
        ),
      );

      sortByImpressionsDesc(
        [...states.entries()],
        cityTotalImpressions,
      ).forEach(([state, cities]) => {
        const stateAcc = emptyGeographyAccumulator();
        [...cities.values()].forEach((acc) =>
          addToGeographyAccumulator(stateAcc, acc),
        );
        rows.push(
          reconcileToCostSplit(
            geographyAccumulatorToRow(
              String(++rowIndex),
              "state",
              1,
              state,
              stateAcc,
            ),
            stateCostSplitByName.get(normalizeName(state)),
          ),
        );

        sortByImpressionsDesc(
          [...cities.entries()],
          (acc: GeographyAccumulator) => acc.impressions,
        ).forEach(([city, acc]) => {
          rows.push(
            reconcileToCostSplit(
              geographyAccumulatorToRow(
                String(++rowIndex),
                "city",
                2,
                city,
                acc,
              ),
              cityCostSplitByName.get(normalizeName(city)),
            ),
          );
        });
      });
    },
  );

  return rows;
}

/** country|state|city -> city row id, walked off the already-built flat
 * (ordered) GeographyTargetingRow list — avoids recomputing the tree. */
function buildCityIdLookup(rows: GeographyTargetingRow[]): Map<string, string> {
  const map = new Map<string, string>();
  let currentCountry = "";
  let currentState = "";
  rows.forEach((row) => {
    if (row.level === "country") currentCountry = row.name;
    else if (row.level === "state") currentState = row.name;
    else if (row.level === "city") {
      map.set(`${currentCountry}|${currentState}|${row.name}`, row.id);
    }
  });
  return map;
}

/**
 * Geofence/POI rows nested under a city for the Excel Geography Targeting
 * sheet only. Built from the campaign's real targeting.geofencing.locations
 * (circle/POI zones with a radius — arbitrary drawn polygons in .geometries
 * are out of scope, since "nearest inventory" needs a single lat/lng and a
 * polygon has no natural single point). Each zone is matched to its nearest
 * inventory via findInventoryForPOI (src/utils/inventory-match.utils.ts),
 * using the zone's own drawn radius as the match threshold. A zone with no
 * inventory within that radius is omitted — there's nothing real to report.
 */
function transformGeographyPoiRows(
  data: UnifiedMediaPlanData,
  geographyRows: GeographyTargetingRow[] = [],
  tCampaigns?: TFunction,
): GeographyPoiRow[] {
  const zones = data.targeting?.geofencing?.locations || [];
  if (zones.length === 0) return [];

  const inventory: InventoryItem[] = data.selectedInventory?.locations || [];
  if (inventory.length === 0) return [];

  const notAvailable =
    tCampaigns?.("media_plan.geographic_plan.not_available") || "N/A";
  const cityIdLookup = buildCityIdLookup(geographyRows);

  const rows: GeographyPoiRow[] = [];

  zones.forEach((zone) => {
    const matched = findInventoryForPOI(
      inventory,
      { locationLat: zone.lat, locationLng: zone.lng },
      zone.radius || undefined,
    );
    if (!matched) return;

    const geo = matched.location?.location || {};
    const cityId = cityIdLookup.get(
      `${geo.country || notAvailable}|${geo.state || notAvailable}|${geo.city || notAvailable}`,
    );
    if (!cityId) return;

    const detail = matched.detail || {};
    const performance = matched.performance || {};
    const channel = (detail.inventoryType || "")
      .toLowerCase()
      .includes(InventoryTypeLowercase.DIGITAL)
      ? tCampaigns?.("media_plan.inventory_mix.channel_digital_ooh") ||
        "Digital"
      : tCampaigns?.("media_plan.inventory_mix.channel_classic_ooh") ||
        "Classic";
    const radiusKm = zone.radius ? (zone.radius / 1000).toFixed(1) : "0";
    const inclusionLabel = zone.included ? "Include" : "Exclude";
    const sov = matched.schedules?.[0]?.sov ?? performance.sov ?? 0;

    const impressions =
      performance.estimatedImpressions ?? performance.estimatedImpression ?? 0;
    const reach = performance.estimatedReach || 0;
    const cost = performance.estimatedCost || 0;

    rows.push({
      parentCityId: cityId,
      name: `${zone.name} · ${radiusKm} km · ${inclusionLabel} — ${detail.name || ""} (${channel}, ${detail.category || ""}, ${detail.mediaOwnerName || ""}, SOV ${sov.toFixed(0)}%)`,
      inventories: 1,
      impressions,
      reach,
      frequency: reach > 0 ? impressions / reach : 0,
      ecpm: impressions > 0 ? (cost / impressions) * 1000 : 0,
    });
  });

  return rows;
}
