import InventoryThumbnail from "@components/common/InventoryThumbnail";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@components/ui/card";
import { Checkbox } from "@components/ui/Checkbox";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Label } from "@components/ui/Label";
import { Tooltip } from "@components/ui/Tooltip";
import { TOOLTIP_CONTENT } from "@constants/tooltip.constants";
import { useLazyGetInventoryReachFrequencyQuery } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatNumber, normalizeGoalType } from "@utils/budget.utils";
import { findDurationInDays } from "@utils/dateUtils";
import {
  formatInventoryLocation,
  getChipOverflow,
  MAX_VISIBLE_VENUE_TYPES,
  isInventoryItem,
  toInventoryDisplayItem,
} from "@utils/inventory-display.utils";
import { sortDaysStartingFromMonday } from "@utils/inventory.utils";
import { clsx } from "clsx";
import {
  CalendarClock,
  ChevronDown,
  ChevronUp,
  InfoIcon,
  Sparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  InventoryClassification,
  Environment,
  EmptyValueDisplay,
} from "src/constants/inventory.constants";
import {
  InventoryItem,
  InventoryRecommendationItem,
  InventoryReachFrequencyResponse,
  InventorySchedule,
} from "src/types/inventory.types";

/**
 * Where the card sources reach / frequency / impressions:
 * - "api" (default): always call the R&F API on expand
 * - "local": always use the item's own performance values (from /filter)
 * - "local-if-available": use local values when an estimated impression is
 *   present, otherwise fall back to the R&F API
 */
export type PerformanceSource = "api" | "local" | "local-if-available";

export interface InventoryDetailCardProps {
  item: InventoryItem | InventoryRecommendationItem;
  onViewDetails?: (
    inventory: InventoryItem | InventoryRecommendationItem,
  ) => void;
  campaignCurrency?: string;
  formatCurrency: (
    amount: number | null | undefined,
    currency?: string,
  ) => string;
  tCampaigns: (key: string) => string;
  // Checkbox props
  showCheckbox?: boolean;
  checkboxChecked?: boolean;
  checkboxDisabled?: boolean;
  onCheckboxChange?: (checked: boolean) => void;
  // SOV props
  showSovDropdown?: boolean;
  sovValue?: number;
  // Styling
  cardClassName?: string;
  emptyValueDisplay?: EmptyValueDisplay;
  fromSchedule?: boolean;
  fromPriceManagement?: boolean;
  showSmartSuggestionScore: boolean;
  performanceSource?: PerformanceSource;
  campaignStartDate?: string | Date | null;
  campaignEndDate?: string | Date | null;
  goalType?: string;
  isNegotiated?: boolean;
  /**
   * Availability annotation from the recommendation run, for items rendered
   * from /filter//selected-inventory (which carry no recommendationInfo).
   * When set, the amber "Limited availability" badge renders even when
   * showSmartSuggestionScore is off — e.g. browse-list items that were not
   * auto-selected.
   */
  availabilityInfo?: InventoryRecommendationItem["availability"];
  /**
   * The recommendation run excluded this item as effectively sold out /
   * blocked for the plan's dates — render an explicit "Unavailable for your
   * dates" state instead of a plain unflagged row.
   */
  unavailableForDates?: boolean;
}

export const InventoryDetailCard: React.FC<InventoryDetailCardProps> = ({
  item,
  onViewDetails,
  campaignCurrency,
  formatCurrency,
  tCampaigns,
  showCheckbox = false,
  checkboxChecked = false,
  checkboxDisabled = false,
  onCheckboxChange,
  // showSovDropdown = false,
  // sovValue,
  cardClassName,
  emptyValueDisplay = EmptyValueDisplay.DASH,
  fromSchedule = false,
  fromPriceManagement = false,
  showSmartSuggestionScore = false,
  performanceSource = "api",
  campaignStartDate,
  campaignEndDate,
  goalType,
  isNegotiated = false,
  availabilityInfo,
  unavailableForDates = false,
}) => {
  const { t: tCommon } = useTranslate(["common"]);

  const DAY_TO_WEEK_INDEX: Record<string, number> = {
    MONDAY: 0,
    TUESDAY: 1,
    WEDNESDAY: 2,
    THURSDAY: 3,
    FRIDAY: 4,
    SATURDAY: 5,
    SUNDAY: 6,
  };

  const display = useMemo(() => toInventoryDisplayItem(item), [item]);
  const estimatedCost = useMemo(() => {
    const baseCost = display.performance.estimatedCost;
    const firstScheduleDiscount = display.schedules?.[0]?.discount?.value ?? 0;
    if (isNegotiated && firstScheduleDiscount > 0 && baseCost != null) {
      return baseCost - (baseCost * firstScheduleDiscount) / 100;
    }
    return baseCost;
  }, [display.performance.estimatedCost, display.schedules, isNegotiated]);

  const sizeLabel = display.detail.size
    ? tCommon(`inventorySize.${display.detail.size.toLowerCase()}.label`)
    : "";

  // Venue types: show the first few inline, collapse the rest to "+N" with a
  // hover tooltip listing all (same pattern as sizes).
  const venue = getChipOverflow(
    display.detail.venueTypes,
    MAX_VISIBLE_VENUE_TYPES,
  );

  const emptyValue = emptyValueDisplay;
  const normalizedGoalType = normalizeGoalType(goalType);
  const isCPSGoal =
    normalizedGoalType === "SOV" || normalizedGoalType === "ADPLAYS";
  const [isExpanded, setIsExpanded] = useState(false);
  const [reachFrequencyData, setReachFrequencyData] =
    useState<InventoryReachFrequencyResponse | null>(null);
  const [fetchReachFrequency] = useLazyGetInventoryReachFrequencyQuery();

  // Availability annotation shown on the amber badge. Recommendation-sourced
  // items carry it in recommendationInfo (gated on showSmartSuggestionScore,
  // unchanged); browse-list items get it injected via availabilityInfo.
  const effectiveAvailability =
    (showSmartSuggestionScore
      ? display.recommendationInfo?.availability
      : undefined) ?? availabilityInfo;

  const schedules = display.schedules ?? [];
  const [selectedSchedule, setSelectedSchedule] = useState<
    InventorySchedule | undefined
  >(schedules.length ? schedules[0] : undefined);

  const hasFetchedReachFrequencyRef = useRef(false);
  const [isLoadingReachFrequency, setIsLoadingReachFrequency] = useState(false);

  /**
   * Calculate campaign duration in days
   * Uses campaign start/end dates if available, otherwise falls back to slotDuration
   * @returns {number} Duration in days
   */
  const calculateCampaignDuration = useCallback((): number => {
    // If both campaign dates are provided, calculate duration
    if (campaignStartDate && campaignEndDate) {
      try {
        const startStr =
          typeof campaignStartDate === "string"
            ? campaignStartDate
            : new Date(campaignStartDate).toISOString();
        const endStr =
          typeof campaignEndDate === "string"
            ? campaignEndDate
            : new Date(campaignEndDate).toISOString();

        const duration = findDurationInDays(startStr, endStr);
        if (duration > 0) return duration;
      } catch (error) {
        console.error("Error calculating campaign duration:", error);
      }
    }

    // Fallback to slotDuration from operations
    return display.operations?.slotDuration ?? 0;
  }, [campaignStartDate, campaignEndDate, display.operations?.slotDuration]);

  const fetchReachFrequencyData = useCallback(async () => {
    // Use local performance values (from /filter) when forced, or when allowed
    // and an estimated impression is present. Otherwise call the R&F API.
    const localImpressions = display?.performance?.estimatedImpressions;
    const useLocal =
      performanceSource === "local" ||
      (performanceSource === "local-if-available" && localImpressions != null);
    if (useLocal) {
      setReachFrequencyData({
        reach: display?.performance?.estimatedReach,
        frequency: display?.performance?.estimatedFrequency,
        impressions: localImpressions,
        status: "COMPLETED",
      });
      return;
    }

    try {
      setIsLoadingReachFrequency(true);

      const duration = calculateCampaignDuration();

      const slotDuration = display.operations?.slotDuration ?? 1;
      const clientPerLoop = display.operations?.clientPerLoop ?? 1;
      const spotsPerHour = Math.floor(3600 / slotDuration / clientPerLoop);

      const dayparts = (() => {
        const schedules = display.schedules ?? [];
        if (!schedules.length) return undefined;
        const merged: Record<string, Set<number>> = {};
        schedules.forEach((schedule) => {
          Object.entries(schedule.bookingMatrix ?? {}).forEach(
            ([date, hours]) => {
              if (!merged[date]) merged[date] = new Set();
              hours.forEach((h) => merged[date].add(h));
            },
          );
        });
        return Object.entries(merged)
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([scheduledDate, hoursSet]) => ({
            scheduledDate,
            scheduledTime: [...hoursSet]
              .sort((a, b) => a - b)
              .map((h) => String(h).padStart(2, "0")),
          }));
      })();

      const requestData = {
        inventories: [
          {
            referenceId: display.detail.referenceId,
            type: "billboard",
            spotsPerHour: spotsPerHour || 0,
            dayparts,
          },
        ],
        duration,
      };

      const result = await fetchReachFrequency(requestData).unwrap();
      if (result.success && result.data && result.data.length > 0) {
        setReachFrequencyData(result.data[0]);
      }
    } catch (error) {
      console.error("Error loading reach and frequency data:", error);
    } finally {
      setIsLoadingReachFrequency(false);
    }
  }, [
    display.detail.referenceId,
    display.operations?.clientPerLoop,
    display.operations?.slotDuration,
    display.performance,
    display.schedules,
    fetchReachFrequency,
    performanceSource,
    calculateCampaignDuration,
  ]);

  useEffect(() => {
    if (
      !fromSchedule &&
      !fromPriceManagement &&
      isExpanded &&
      !hasFetchedReachFrequencyRef.current
    ) {
      hasFetchedReachFrequencyRef.current = true;
      fetchReachFrequencyData();
    }
  }, [isExpanded, fromSchedule, fromPriceManagement, fetchReachFrequencyData]);

  useEffect(() => {
    if (!isExpanded) {
      hasFetchedReachFrequencyRef.current = false;
    }
  }, [isExpanded]);

  const handleToggle = useCallback(() => {
    setIsExpanded((prev) => !prev);
  }, []);

  const handleViewDetailsClick = useCallback(() => {
    onViewDetails?.(item as InventoryItem | InventoryRecommendationItem);
  }, [onViewDetails, item]);

  // const renderSovDisplay = () => {
  //   if (showSovDropdown) {
  //     return (
  //       <span className="text-xs cursor-default!">
  //         {display.detail.inventoryType === InventoryClassification.CLASSIC
  //           ? EmptyValueDisplay.NOT_APPLICABLE
  //           : `${sovValue?.toFixed(2) || display.detail.sov?.toFixed(2) || 10}%`}
  //       </span>
  //     );
  //   }
  //   return (
  //     <span className="text-xs text-mw-neutral-700">
  //       {display.detail.inventoryType === InventoryClassification.CLASSIC
  //         ? EmptyValueDisplay.NOT_APPLICABLE
  //         : display.detail.sov
  //           ? `${display.detail.sov?.toFixed(2)} %`
  //           : EmptyValueDisplay.NOT_APPLICABLE}
  //     </span>
  //   );
  // };

  // const renderSotDisplay = () => {
  //   if (display.performance.totalSot) {
  //     if (display.detail.inventoryType === InventoryClassification.CLASSIC) {
  //       return (
  //         <span className="text-xs text-mw-neutral-700">
  //           {SOTDisplay.CLASSIC_DEFAULT}
  //         </span>
  //       );
  //     }
  //     return (
  //       <span className="text-xs text-mw-neutral-700">
  //         {display.performance.plannedSot?.toFixed(2) || 0}
  //         h/
  //         {display.performance.totalSot}h
  //       </span>
  //     );
  //   }
  //   return (
  //     <span className="text-xs text-mw-neutral-700">
  //       {display.detail.inventoryType === InventoryClassification.CLASSIC
  //         ? SOTDisplay.CLASSIC_DEFAULT
  //         : `${emptyValue}`}
  //     </span>
  //   );
  // };

  const handleScheduleChange = (schedule: InventorySchedule) => {
    setSelectedSchedule(schedule);
  };
  const minMaxHour = useMemo(() => {
    if (selectedSchedule?.bookingMatrix) {
      let minHour = 24;
      let maxHour = 0;
      for (const day in selectedSchedule.bookingMatrix) {
        const hours = selectedSchedule.bookingMatrix[day];
        if (hours && hours.length > 0) {
          const min = Math.min(...hours);
          const max = Math.max(...hours);
          if (min < minHour) minHour = min;
          if (max > maxHour) maxHour = max;
        }
      }
      return { min: minHour, max: maxHour };
    }
    return { min: 0, max: 23 };
  }, [selectedSchedule]);

  const renderSmartSuggestionScore = ({
    score,
  }: {
    score: InventoryRecommendationItem["componentScores"];
  }) => {
    return (
      <div className="p-2 relative flex-col justify-start items-start gap-2">
        <div className="self-stretch flex flex-col justify-start items-start gap-2">
          <div className="self-stretch flex flex-col justify-start items-start gap-1">
            <div className="self-stretch justify-start text-black text-xs font-medium leading-4">
              {tCampaigns("inventoryDetailCard.fitnessScoreCard")}
            </div>
          </div>
          <div className="self-stretch flex flex-col justify-start items-start gap-2">
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.budgetRelevance")}
              </div>
              <div className="justify-start text-mw-success-500 text-xs font-normal  leading-4">
                {score?.budgetFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.campaignGoalMatch")}
              </div>
              <div className="justify-start text-mw-warning-500 text-xs font-normal  leading-4">
                60
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.geographyRelevance")}
              </div>
              <div className="justify-start text-mw-error-500 text-xs font-normal  leading-4">
                {score?.geoFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.audienceRelevance")}
              </div>
              <div className="justify-start text-mw-success-500 text-xs font-normal  leading-4">
                {score?.audienceFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.brandCategoryRelevance")}
              </div>
              <div className="justify-start text-mw-error-500 text-xs font-normal  leading-4">
                {score?.brandFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.inventoryQuality")}
              </div>
              <div className="justify-start text-mw-warning-500 text-xs font-normal  leading-4">
                {score?.qualityFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.timeAlignment")}
              </div>
              <div className="justify-start text-mw-error-500 text-xs font-normal  leading-4">
                {score?.timeFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.measureConfidence")}
              </div>
              <div className="justify-start text-mw-success-500 text-xs font-normal  leading-4">
                {score?.measureFit?.toFixed(2) ?? 0}
              </div>
            </div>
            <div className="self-stretch inline-flex justify-start items-start gap-1">
              <div className="flex-1 justify-start text-mw-secondary text-xs font-normal  leading-4">
                {tCampaigns("inventoryDetailCard.availabilityWindow")}
              </div>
              <div className="justify-start text-mw-warning-500 text-xs font-normal  leading-4">
                {score?.availability?.toFixed(2) ?? 0}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  };

  return (
    <Card key={display.detail.id} className={cardClassName}>
      <CardHeader className="p-2">
        <div className="flex justify-between items-start border-b border-mw-neutral-100 pb-2">
          <div className="flex flex-1 justify-start items-start gap-2">
            {/* Checkbox */}
            {showCheckbox && (
              <div className="flex items-center">
                <Checkbox
                  checked={checkboxChecked}
                  onChange={(e) => onCheckboxChange?.(e.target.checked)}
                  disabled={checkboxDisabled}
                />
              </div>
            )}

            {/* Image */}
            {!fromPriceManagement && (
              <InventoryThumbnail
                src={display.detail.thumbnail}
                alt={display.detail.referenceId}
                className="text-sm size-14 rounded-sm object-cover shrink-0"
              />
            )}
            {/* Content */}
            <div className="inline-flex flex-col justify-start items-start gap-2">
              <div className="inline-flex justify-start items-center gap-2">
                <h3 className="text-sm font-semibold leading-none">
                  {display.detail.name || ""}
                </h3>
              </div>
              <p className="text-xs mw-secondary leading-none">
                {formatInventoryLocation(display.location.location)}
              </p>
              <div className="inline-flex justify-start items-start gap-1.5">
                {display.detail.inventoryType && (
                  <Badge
                    variant="outline"
                    size="sm"
                    className={`${display.detail.inventoryType === InventoryClassification.DIGITAL ? "outline-mw-secondary-500! text-mw-secondary-500!" : "outline-mw-neutral-500! text-mw-neutral-500!"}`}
                  >
                    {tCommon(
                      `inventoryClassification.${display.detail.inventoryType.toLowerCase()}`,
                    )}
                  </Badge>
                )}
                {display.detail.format && (
                  <Badge
                    className="outline-mw-rose-warning-400! text-mw-rose-warning-400!"
                    variant="outline"
                    size="sm"
                  >
                    {display.detail.format}
                  </Badge>
                )}
                {display.detail.environment && (
                  <Badge
                    variant="outline"
                    size="sm"
                    className={`${display.detail.environment === Environment.OUTDOOR ? "outline-mw-bluish-grey-500! text-mw-bluish-grey-500!" : "outline-mw-amaranth-500! text-mw-amaranth-500!"}`}
                  >
                    {tCommon(
                      `inventoryEnvironment.${display.detail.environment}`,
                    )}
                  </Badge>
                )}
                {sizeLabel && (
                  <Badge
                    variant="outline"
                    size="sm"
                    className="outline-mw-orange-warning-500! text-mw-orange-warning-500!"
                  >
                    {sizeLabel}
                  </Badge>
                )}
                {venue.count > 0 &&
                  (venue.overflowCount > 0 ? (
                    <Tooltip
                      content={
                        <div className="flex flex-col">
                          {venue.labels.map((v) => (
                            <span key={v}>{v}</span>
                          ))}
                        </div>
                      }
                    >
                      <Badge
                        variant="outline"
                        size="sm"
                        className="outline-mw-primary-500! text-mw-primary-500!"
                      >
                        {`${venue.visibleText} +${venue.overflowCount}`}
                      </Badge>
                    </Tooltip>
                  ) : (
                    <Badge
                      variant="outline"
                      size="sm"
                      className="outline-mw-primary-500! text-mw-primary-500!"
                    >
                      {venue.visibleText}
                    </Badge>
                  ))}
              </div>
            </div>
          </div>
          <div className="flex flex-col justify-between space-y-3">
            <div className="inline-flex justify-end items-center">
              <Badge
                variant="default"
                className="bg-mw-neutral-50! text-mw-neutral-500! mr-2"
              >
                {display.detail.referenceId}
              </Badge>
              {/* Action Buttons */}
              {!fromSchedule && !fromPriceManagement && (
                <div className="flex items-center gap-2 justify-end">
                  <Button variant="outline" size="xsm" onClick={handleToggle}>
                    {isExpanded ? (
                      <ChevronUp className="size-4 text-mw-neutral-500" />
                    ) : (
                      <ChevronDown className="size-4 text-mw-neutral-500" />
                    )}
                  </Button>
                </div>
              )}
            </div>
            <div className=" flex justify-end">
              {showSmartSuggestionScore && (
                <Tooltip
                  content={renderSmartSuggestionScore({
                    score: display.recommendationInfo?.componentScores ?? {
                      geoFit: 0,
                      availability: 0,
                      budgetFit: 0,
                      audienceFit: 0,
                      brandFit: 0,
                      qualityFit: 0,
                      timeFit: 0,
                      measureFit: 0,
                    },
                  })}
                >
                  <div className="px-1.5 py-1 bg-mw-purple-warning-100 rounded-[999px] outline outline-1 outline-offset-[-1px] outline-container-border inline-flex justify-end items-center gap-1">
                    <Sparkles className="size-3 text-mw-purple-warning-500" />
                    <div className="text-center justify-start text-mw-purple-warning-500 text-sm font-medium leading-3">
                      {" "}
                      {tCampaigns(
                        "inventoryDetailCard.smartRecommendationScore",
                      )}{" "}
                      -{" "}
                      {display.recommendationInfo?.finalScore?.toFixed(2) ?? 0}
                    </div>
                  </div>
                </Tooltip>
              )}
            </div>
            {unavailableForDates && (
              <div className="flex justify-end">
                <Tooltip
                  content={
                    effectiveAvailability?.summary ||
                    tCampaigns("inventoryDetailCard.unavailableForDates")
                  }
                >
                  <div
                    className="px-1.5 py-1 bg-mw-error-50 rounded-[999px] outline outline-1 outline-offset-[-1px] outline-container-border inline-flex justify-end items-center gap-1"
                    data-testid={`badge-unavailable-${display.detail.id}`}
                  >
                    <CalendarClock className="size-3 text-mw-error-500" />
                    <div className="text-center text-mw-error-500 text-xs font-medium leading-3">
                      {tCampaigns("inventoryDetailCard.unavailableForDates")}
                    </div>
                  </div>
                </Tooltip>
              </div>
            )}
            {!unavailableForDates &&
              effectiveAvailability &&
              !effectiveAvailability.allAvailable &&
              (effectiveAvailability.totalDays ?? 0) > 0 && (
                <div className="flex justify-end">
                  <Tooltip
                    content={
                      effectiveAvailability.summary ||
                      tCampaigns("inventoryDetailCard.limitedAvailability")
                    }
                  >
                    <div
                      className="px-1.5 py-1 bg-mw-warning-50 rounded-[999px] outline outline-1 outline-offset-[-1px] outline-container-border inline-flex justify-end items-center gap-1"
                      data-testid={`badge-limited-availability-${display.detail.id}`}
                    >
                      <CalendarClock className="size-3 text-mw-warning-500" />
                      <div className="text-center text-mw-warning-500 text-xs font-medium leading-3">
                        {tCampaigns("inventoryDetailCard.limitedAvailability")}{" "}
                        ({effectiveAvailability.availableDays}/
                        {effectiveAvailability.totalDays})
                      </div>
                    </div>
                  </Tooltip>
                </div>
              )}
          </div>
        </div>
      </CardHeader>

      {!fromSchedule && !fromPriceManagement && isExpanded && (
        <CardContent className="pt-4">
          <div className="grid grid-cols-5 gap-6">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.impressions")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.impressions)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {isLoadingReachFrequency
                  ? emptyValue
                  : (reachFrequencyData?.impressions?.toLocaleString() ??
                    emptyValue)}
              </p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.reach")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.reach)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {isLoadingReachFrequency
                  ? emptyValue
                  : (reachFrequencyData?.reach?.toLocaleString() ?? emptyValue)}
              </p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.frequency")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.frequency)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {isLoadingReachFrequency
                  ? emptyValue
                  : (reachFrequencyData?.frequency?.toFixed(2) ?? emptyValue)}
              </p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>
                  {isCPSGoal
                    ? tCampaigns("inventories.metrics.cps_rate")
                    : tCampaigns("inventories.metrics.cpm_rate")}
                </Label>
                <Tooltip
                  content={tCampaigns(
                    isCPSGoal
                      ? TOOLTIP_CONTENT.inventory.cpsRate
                      : TOOLTIP_CONTENT.inventory.cpmRate,
                  )}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {isCPSGoal ? (
                  display.performance.spotRate ? (
                    <span>
                      {campaignCurrency}{" "}
                      {display.performance.spotRate.toFixed(2)}
                    </span>
                  ) : (
                    tCampaigns("inventories.metrics.na")
                  )
                ) : display.performance.cpmRate ? (
                  <span>
                    {campaignCurrency} {display.performance.cpmRate.toFixed(2)}
                  </span>
                ) : (
                  tCampaigns("inventories.metrics.na")
                )}
              </p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.est_cost")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.estimatedCost)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {estimatedCost ? (
                  <span>
                    {campaignCurrency} {estimatedCost.toFixed(2)}
                  </span>
                ) : (
                  "-"
                )}
              </p>
            </div>
            {display.detail.inventoryType !==
              InventoryClassification.CLASSIC && (
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <Label>{tCampaigns("inventories.metrics.ad_plays")}</Label>
                  <Tooltip
                    content={tCampaigns(TOOLTIP_CONTENT.inventory.adPlays)}
                    className="whitespace-normal w-44 break-words"
                  >
                    <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                  </Tooltip>
                </div>
                <p className="text-sm font-medium leading-4">
                  {display.performance.totalAdPlays?.toLocaleString() || "-"}
                </p>
              </div>
            )}
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.screens")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.screens)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {display.detail.screens?.toLocaleString() || "-"}
              </p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.poi")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.poi)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">{emptyValue}</p>
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Label>{tCampaigns("inventories.metrics.owner")}</Label>
                <Tooltip
                  content={tCampaigns(TOOLTIP_CONTENT.inventory.owner)}
                  className="whitespace-normal w-44 break-words"
                >
                  <InfoIcon className="size-3.5 relative text-mw-neutral-300 cursor-help" />
                </Tooltip>
              </div>
              <p className="text-sm font-medium leading-4">
                {display.detail.mediaOwnerName}
              </p>
            </div>
          </div>
          {display.detail.cinemaFields && (
            <div className="mt-4 rounded-md border border-mw-neutral-100 p-3">
              <div className="grid grid-cols-3 gap-4">
                {display.detail.cinemaFields.operator && (
                  <div>
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.operator")}
                    </Label>
                    <p className="text-sm font-medium leading-4">
                      {display.detail.cinemaFields.operator}
                    </p>
                  </div>
                )}
                {(display.detail.cinemaFields.cinemaName ||
                  display.detail.cinemaFields.hallName) && (
                  <div>
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.hall")}
                    </Label>
                    <p className="text-sm font-medium leading-4">
                      {[
                        display.detail.cinemaFields.cinemaName,
                        display.detail.cinemaFields.hallName,
                      ]
                        .filter(Boolean)
                        .join(" · ") || emptyValue}
                    </p>
                  </div>
                )}
                {display.detail.cinemaFields.seats != null && (
                  <div>
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.seats")}
                    </Label>
                    <p className="text-sm font-medium leading-4">
                      {display.detail.cinemaFields.seats.toLocaleString()}
                    </p>
                  </div>
                )}
                {!!display.detail.cinemaFields.showtimeWindows?.length && (
                  <div className="col-span-3">
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.showtimes")}
                    </Label>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {display.detail.cinemaFields.showtimeWindows.map((w) => (
                        <Badge
                          key={`${w.label}-${w.start}`}
                          variant="outline"
                          size="sm"
                          className="outline-mw-primary-500! text-mw-primary-500!"
                        >
                          {`${w.label} (${w.start}–${w.end})`}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
                {!!display.detail.cinemaFields.genres?.length && (
                  <div className="col-span-3">
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.genres")}
                    </Label>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {display.detail.cinemaFields.genres.map((g) => (
                        <Badge key={g} variant="outline" size="sm">
                          {g}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
                {!!display.detail.cinemaFields.ratings?.length && (
                  <div className="col-span-3">
                    <Label>
                      {tCampaigns("inventoryDetailCard.cinema.ratings")}
                    </Label>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {display.detail.cinemaFields.ratings.map((r) => (
                        <Badge key={r} variant="outline" size="sm">
                          {r}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
          <div className="self-stretch p-2 h-0 border-b border-mw-neutral-100" />
        </CardContent>
      )}
      {fromSchedule && selectedSchedule && (
        <div className="flex-1 p-2">
          <div className="flex justify-end">
            <Dropdown>
              <DropdownTrigger>{selectedSchedule.name}</DropdownTrigger>
              <DropdownContent align="left">
                {schedules.map((schedule: InventorySchedule) => (
                  <DropdownItem
                    key={schedule.id}
                    onClick={() => handleScheduleChange(schedule)}
                  >
                    <div className="text-sm">{schedule.name}</div>
                  </DropdownItem>
                ))}
              </DropdownContent>
            </Dropdown>
          </div>
          <div className="grid grid-cols-2 gap-4 mt-4">
            <Card className="p-2">
              <CardTitle className="text-sm font-medium text-mw-neutral-800 mb-2 border-b border-container-border pb-2">
                {tCampaigns("viewCampaign.scheduleTab.scheduleConfiguration")}
              </CardTitle>
              <CardContent className="p-0! space-y-4">
                <div className="schedule-days flex gap-2 pt-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(TOOLTIP_CONTENT.schedule.days)}
                  >
                    {tCampaigns("approval.days")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 inline-flex flex-wrap gap-1.5 justify-end leading-4">
                    {sortDaysStartingFromMonday(
                      selectedSchedule?.scheduleDays || [],
                    ).map((day: string) => {
                      return (
                        <Badge
                          key={day}
                          className="text-xs! text-black! font-medium! outline-mw-neutral-100!"
                        >
                          {tCommon(
                            `calendar.weekDaysShort.${DAY_TO_WEEK_INDEX[day.toUpperCase()] ?? ""}`,
                          )}
                        </Badge>
                      );
                    })}
                  </div>
                </div>
                <div className="schedule-hours flex gap-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(TOOLTIP_CONTENT.schedule.hours)}
                  >
                    {tCampaigns("viewCampaign.scheduleTab.hours")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 text-right leading-4">
                    {minMaxHour.min}
                    {":00"} - {minMaxHour.max}
                    {":00"}
                  </div>
                </div>
                <div className="schedule-spots-loop flex gap-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(TOOLTIP_CONTENT.schedule.slotDuration)}
                  >
                    {tCampaigns("inventories.metrics.frequency")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 text-right leading-4">
                    {selectedSchedule?.duration}
                    {"s"}
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card className="p-2">
              <CardTitle className="text-sm font-medium text-mw-neutral-800 mb-2 border-b border-container-border pb-2">
                {tCampaigns("viewCampaign.scheduleTab.expectedPerformance")}
              </CardTitle>
              <CardContent className="p-0! space-y-4">
                <div className="schedule-expected-impressions pt-2 flex gap-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(
                      TOOLTIP_CONTENT.schedule.expectedImpressions,
                    )}
                  >
                    {tCampaigns("viewCampaign.scheduleTab.expectedImpressions")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 text-right leading-4">
                    {isInventoryItem(item) &&
                    item.performance?.estimatedImpression != null
                      ? formatNumber(item.performance.estimatedImpression)
                      : tCampaigns("inventories.metrics.na")}
                  </div>
                </div>
                <div className="schedule-play-hours flex gap-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(TOOLTIP_CONTENT.schedule.adPlays)}
                  >
                    {tCampaigns("viewCampaign.targetingTab.adPlays")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 text-right leading-4">
                    {formatNumber(selectedSchedule?.adPlays) ||
                      tCampaigns("inventories.metrics.na")}
                  </div>
                </div>
                <div className="schedule-play-hours flex gap-2">
                  <Label
                    className="text-xs text-mw-neutral-700 capitalize"
                    info={tCampaigns(TOOLTIP_CONTENT.schedule.sov)}
                  >
                    {tCampaigns("card.sov")}
                  </Label>
                  <div className="text-xs text-black font-medium flex-1 text-right leading-4">
                    {selectedSchedule?.sov.toFixed(2)}%
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      <CardFooter
        className={clsx({
          "p-2! pt-0!": fromPriceManagement,
        })}
      >
        {!fromSchedule && !fromPriceManagement && (
          <div className="flex-1 flex items-center gap-4">
            {/* SOV Label */}
            {/* <div className="flex items-center gap-1">
              <Label>SOV</Label>
              <InfoIcon className="size-3.5 relative text-mw-neutral-300" />
              {!showSovDropdown && ":"}
              {renderSovDisplay()}
            </div> */}

            {/* Vertical Separator */}
            {/* <div className="w-px h-6 bg-mw-neutral-200"></div> */}

            {/* SOT Label with Value */}
            {/* <div className="flex items-center gap-1">
              <Label>SOT</Label>
              <InfoIcon className="size-3.5 relative text-mw-neutral-300" />:
              {display.performance.plannedSot &&
              display.performance.totalSot &&
              display.detail.inventoryType !==
                InventoryClassification.CLASSIC ? (
                <span className="text-xs text-mw-neutral-700">
                  {display.performance.plannedSot.toFixed(2)}h /
                  {display.performance.totalSot}h
                </span>
              ) : display.performance.totalSot ? (
                renderSotDisplay()
              ) : (
                <span className="text-xs text-mw-neutral-700">
                  {SOTDisplay.CLASSIC_DEFAULT}
                </span>
              )}
            </div> */}

            {/* Vertical Separator */}
            {/* <div className="w-px h-6 bg-mw-neutral-200"></div> */}

            {display.performance.totalAdPlays &&
            display.detail.inventoryType !== InventoryClassification.CLASSIC ? (
              <>
                <div className="flex items-center gap-2">
                  <Label>
                    {tCampaigns("inventories.metrics.est")}{" "}
                    {display.performance.perDayAdPlays?.toLocaleString() ?? 0}{" "}
                    {tCampaigns("inventories.metrics.daily_plays")}
                  </Label>
                </div>
                {/* <div className="w-px h-6 bg-mw-neutral-200"></div> */}
              </>
            ) : (
              <Label>{EmptyValueDisplay.NOT_APPLICABLE}</Label>
            )}
          </div>
        )}
        {fromSchedule && <div className="flex-1" />}

        {/* View Details Button */}
        <div
          className={clsx("flex gap-2", {
            "flex-col flex-1 align-center": fromPriceManagement,
            "justify-end items-center": !fromPriceManagement,
          })}
        >
          {/* Estimated Cost */}
          <div
            className={clsx("flex justify-start items-start gap-2", {
              "items-center!": fromPriceManagement,
            })}
          >
            <Label className="text-sm font-medium text-mw-primary-500">
              {tCampaigns("inventoryMapView.estimatedCost")} :
            </Label>
            <Label className="text-sm font-medium text-mw-primary-500">
              {formatCurrency(estimatedCost, campaignCurrency)}
            </Label>
            <Tooltip
              content={tCampaigns(TOOLTIP_CONTENT.inventory.estimatedCost)}
              className="whitespace-normal w-40 break-words text-center"
            >
              <InfoIcon className="size-3.5 relative text-mw-primary-500 cursor-help" />
            </Tooltip>
          </div>
          {!fromSchedule && (
            <Button
              className="rounded-sm outline -outline-offset-1 outline-mw-primary-500 text-mw-primary-500"
              variant="outline"
              size="md"
              onClick={handleViewDetailsClick}
            >
              {tCampaigns("inventories.item.view_details")}
            </Button>
          )}
        </div>
      </CardFooter>
    </Card>
  );
};
