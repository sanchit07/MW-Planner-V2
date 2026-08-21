import { Button } from "@components/ui/Button";
import CalendarView, {
  CalendarViewMode,
  ColorPaletteInfo,
  RowData,
  CalendarEvent,
  GridCellData,
  HourlyEvent,
} from "@components/ui/CalendarView";
import { Progress } from "@components/ui/Progressbar";
import { AvailabilitySyncWarning } from "@components/common/AvailabilitySyncWarning";
import { useLazyGetInventoryAvailabilityQuery } from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { getStartOfWeek, toISODateString } from "@utils/dateUtils";
import {
  buildAvailabilityIndex,
  getSpotDataFromIndex,
  getBookingDetailsFromIndex,
  calculateSpotsForDateTime,
  getStatusFromSpotData,
  getBookedPercentage,
  getBookingDetailsForDateTime,
  SpotData,
  SpotStatus,
  type AvailabilityIndex,
  type AvailabilityViewMode,
} from "@utils/inventoryavailability.utils";
import { getStatusConfig } from "@utils/inventoryAvailabilityUI.utils";
import {
  parseAvailabilityResponse,
  parseAvailabilitySync,
} from "@utils/inventoryAvailabilityUI.utils";
import { useMemo, useState, useCallback, useEffect } from "react";
import { InventoryItem } from "src/types/inventory.types";
import {
  AvailabilitySyncInfo,
  InventoryAvailabilityData,
} from "src/types/price-management.types";

import { SpotTooltipContent } from "./SpotTooltipContent";

export type { SpotData, SpotStatus };

interface InventoryAvailabilityInterface {
  inventoryData: InventoryItem | null;
  campaignStartDate: string | Date | null | undefined;
  campaignEndDate: string | Date | null | undefined;
}

const InventoryAvailabilityCalendarView: React.FC<
  InventoryAvailabilityInterface
> = ({ inventoryData, campaignStartDate, campaignEndDate }) => {
  const { t: tPrice } = useTranslate(["price"]);
  // Calendar view state
  const [calendarViewMode, setCalendarViewMode] =
    useState<CalendarViewMode>("monthly");
  const [calendarDate, setCalendarDate] = useState(new Date());
  const [getInventoryAvailability] = useLazyGetInventoryAvailabilityQuery();

  const [currentInventoryData, setCurrentInventoryData] =
    useState<InventoryAvailabilityData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorInventoryAvailability, setErrorInventoryAvailability] = useState<
    string | null
  >(null);
  const [syncInfo, setSyncInfo] = useState<AvailabilitySyncInfo | null>(null);

  // Calculate minDate and maxDate from campaign data (used for API call and calendar bounds)
  const { minDate, maxDate } = useMemo(() => {
    if (!campaignStartDate || !campaignEndDate) {
      return { minDate: undefined, maxDate: undefined };
    }

    const startDate = new Date(campaignStartDate);
    const endDate = new Date(campaignEndDate);

    // minDate = campaign start date minus 3 months
    // Handle year boundaries correctly
    const min = new Date(startDate);
    const minYear = min.getFullYear();
    const minMonth = min.getMonth();
    // Calculate new month and year
    let newMinMonth = minMonth - 3;
    let newMinYear = minYear;
    if (newMinMonth < 0) {
      newMinMonth += 12;
      newMinYear -= 1;
    }
    min.setFullYear(newMinYear, newMinMonth, min.getDate());
    min.setHours(0, 0, 0, 0);

    // maxDate = campaign end date plus 3 months
    // Handle year boundaries correctly
    const max = new Date(endDate);
    const maxYear = max.getFullYear();
    const maxMonth = max.getMonth();
    // Calculate new month and year
    let newMaxMonth = maxMonth + 3;
    let newMaxYear = maxYear;
    if (newMaxMonth > 11) {
      newMaxMonth -= 12;
      newMaxYear += 1;
    }
    max.setFullYear(newMaxYear, newMaxMonth, max.getDate());
    max.setHours(23, 59, 59, 999);
    return { minDate: min, maxDate: max };
  }, [campaignStartDate, campaignEndDate]);

  // API date range from visible calendar view: refetch when month/week/day changes
  const { apiStartDate, apiEndDate } = useMemo(() => {
    const base = new Date(calendarDate);
    if (calendarViewMode === "monthly") {
      const start = new Date(base.getFullYear(), base.getMonth(), 1);
      start.setHours(0, 0, 0, 0);
      const end = new Date(base.getFullYear(), base.getMonth() + 1, 0);
      end.setHours(23, 59, 59, 999);
      return { apiStartDate: start, apiEndDate: end };
    }
    if (calendarViewMode === "weekly") {
      const start = getStartOfWeek(base, 1);
      start.setHours(0, 0, 0, 0);
      const end = new Date(start);
      end.setDate(end.getDate() + 6);
      end.setHours(23, 59, 59, 999);
      return { apiStartDate: start, apiEndDate: end };
    }
    const start = new Date(base);
    start.setHours(0, 0, 0, 0);
    const end = new Date(base);
    end.setHours(23, 59, 59, 999);
    return { apiStartDate: start, apiEndDate: end };
  }, [calendarDate, calendarViewMode]);

  const fetchInventoryData = useCallback(
    async (inventoryData: InventoryItem) => {
      if (!inventoryData.detail?.externalId) {
        setCurrentInventoryData(null);
        setErrorInventoryAvailability(null);
        return;
      }

      if (!apiStartDate || !apiEndDate) {
        setCurrentInventoryData(null);
        setErrorInventoryAvailability(null);
        return;
      }

      setIsLoading(true);
      setErrorInventoryAvailability(null);
      try {
        const inventoryId = inventoryData.detail.externalId;
        const availabilityResponse = await getInventoryAvailability({
          data: {
            inventoryIds: [inventoryId],
            startTime: `${toISODateString(apiStartDate)}T00:00:00`,
            endTime: `${toISODateString(apiEndDate)}T23:59:59`,
          },
        }).unwrap();

        const inventories = parseAvailabilityResponse(availabilityResponse);
        setSyncInfo(parseAvailabilitySync(availabilityResponse));

        if (inventories?.[inventoryId]) {
          setCurrentInventoryData(inventories[inventoryId]);
          setErrorInventoryAvailability(null);
        } else {
          setCurrentInventoryData(null);
          setErrorInventoryAvailability(
            tPrice("errors.failed_to_fetch_availability"),
          );
        }
      } catch (error) {
        console.error("Failed to fetch inventory availability:", error);
        setErrorInventoryAvailability(
          tPrice("errors.failed_to_fetch_availability_retry"),
        );
        setCurrentInventoryData(null);
      } finally {
        setIsLoading(false);
      }
    },
    [apiStartDate, apiEndDate, getInventoryAvailability],
  );

  useEffect(() => {
    if (inventoryData) {
      fetchInventoryData(inventoryData);
    } else {
      setCurrentInventoryData(null);
      setErrorInventoryAvailability(null);
    }
  }, [fetchInventoryData, inventoryData]);

  // Get clientPerLoop from inventoryData or default
  const clientPerLoop = inventoryData?.operations?.clientPerLoop || 10; // Default to 10 if not provided

  // Pre-build availability index when data loads; calendar date changes only need fast lookups
  const availabilityIndex = useMemo((): AvailabilityIndex | null => {
    if (!currentInventoryData || !inventoryData) return null;
    return buildAvailabilityIndex(currentInventoryData, inventoryData);
  }, [currentInventoryData, inventoryData]);

  // Check if inventory is classic type (no spot-based calculations)
  const isClassicInventory = useMemo(() => {
    return inventoryData?.detail?.inventoryType === "Classic";
  }, [inventoryData?.detail?.inventoryType]);

  const colorInfo = useMemo<ColorPaletteInfo[]>(
    () => [
      { color: "white", label: tPrice("calendar.legend.available") },
      { color: "mw-primary-50", label: tPrice("calendar.legend.reserved") },
      { color: "mw-error-50", label: tPrice("calendar.legend.booked") },
      { color: "mw-error-200", label: tPrice("calendar.legend.fully_booked") },
      { color: "mw-neutral-100", label: tPrice("calendar.legend.blocked") },
    ],
    [tPrice],
  );

  const isDailyView = calendarViewMode === "daily";

  // Generate monthly events from API data
  const generateMonthlyEvents = useCallback(
    (currentDate: Date): CalendarEvent[] => {
      if (!currentInventoryData || calendarViewMode !== "monthly") return [];

      const events: CalendarEvent[] = [];
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth();
      const daysInMonth = new Date(year, month + 1, 0).getDate();

      const viewMode: AvailabilityViewMode = "monthly";
      for (let day = 1; day <= daysInMonth; day++) {
        const date = new Date(year, month, day);
        const spotData =
          availabilityIndex != null
            ? getSpotDataFromIndex(date, undefined, viewMode, availabilityIndex)
            : calculateSpotsForDateTime(
                date,
                undefined,
                currentInventoryData,
                clientPerLoop,
                inventoryData,
                undefined,
                isClassicInventory,
              );

        const status = getStatusFromSpotData(
          spotData,
          calendarViewMode === "monthly",
        );
        const bookedPercentage = getBookedPercentage(spotData);
        // For classic inventory, don't show progress bar
        const config = getStatusConfig(
          status,
          bookedPercentage,
          !isClassicInventory,
          tPrice,
        );
        const bookedPercentageRounded = Math.round(bookedPercentage);

        // Create custom content for monthly view
        // For classic inventory, show simpler content without spot counts
        const customContent = isClassicInventory ? (
          <div className="w-full h-full flex items-center justify-center">
            <div className={`text-xs font-semibold ${config.textColor}`}>
              {config.label}
            </div>
          </div>
        ) : (
          <div className="space-y-2">
            {/* Status label */}
            <div className={`text-xs font-semibold ${config.textColor}`}>
              {status === "available"
                ? tPrice("availability.spotsAvailable")
                : config.label}
            </div>
            {/* Progress bar - only show if not fully booked */}
            {config.showProgress && (
              <div className="flex items-center gap-1.5">
                <Progress
                  value={bookedPercentageRounded}
                  variant={config.progressColor}
                  className="flex-1"
                  showPercentage={true}
                />
              </div>
            )}
          </div>
        );

        events.push({
          date,
          customContent,
          tooltipContent: (
            <SpotTooltipContent
              spotData={spotData}
              date={date}
              isClassic={isClassicInventory}
              campaignStartDate={campaignStartDate}
              campaignEndDate={campaignEndDate}
              isDailyView={isDailyView}
            />
          ),
          backgroundColor: config.bgColor,
        });
      }

      return events;
    },
    [
      calendarViewMode,
      currentInventoryData,
      availabilityIndex,
      clientPerLoop,
      isClassicInventory,
      inventoryData,
      campaignStartDate,
      campaignEndDate,
      isDailyView,
    ],
  );

  const calendarEvents = useMemo(
    () => generateMonthlyEvents(calendarDate),
    [calendarDate, generateMonthlyEvents],
  );

  const calendarRows: RowData[] = useMemo(() => {
    if (!currentInventoryData) {
      // Fallback rows if no inventory data
      if (calendarViewMode === "daily") {
        return Array.from({ length: 9 }, (_, i) => ({
          id: `spot-${String(i + 1).padStart(2, "0")}`,
          label: `Spot ${String(i + 1).padStart(2, "0")}`,
        }));
      }
      return Array.from({ length: 9 }, (_, i) => ({
        id: `slot-${String(i + 1).padStart(2, "0")}`,
        label: `Slot ${String(i + 1).padStart(2, "0")}`,
      }));
    }

    // For classic inventory, show only one row for daily/hourly views
    if (
      isClassicInventory &&
      (calendarViewMode === "daily" || calendarViewMode === "weekly")
    ) {
      return [
        {
          id: "spot-01",
          label: inventoryData?.detail?.name || "Spot 01",
        },
      ];
    }

    // For daily view, rows = spots per loop
    if (calendarViewMode === "daily") {
      return Array.from({ length: clientPerLoop }, (_, i) => ({
        id: `spot-${String(i + 1).padStart(2, "0")}`,
        label: `Spot ${String(i + 1).padStart(2, "0")}`,
      }));
    }

    // For weekly view, rows = spots per loop
    return Array.from({ length: clientPerLoop }, (_, i) => ({
      id: `slot-${String(i + 1).padStart(2, "0")}`,
      label: `Slot ${String(i + 1).padStart(2, "0")}`,
    }));
  }, [
    calendarViewMode,
    clientPerLoop,
    currentInventoryData,
    isClassicInventory,
    inventoryData?.detail?.name,
  ]);

  // Generate grid data for weekly/daily views
  const generateGridData = useCallback(
    (
      rows: RowData[],
      startDate: Date,
      isDaily: boolean = false,
    ): GridCellData[] => {
      if (!currentInventoryData) return [];

      const gridData: GridCellData[] = [];

      rows.forEach((row) => {
        const spotPosition = parseInt(row.id.split("-")[1], 10); // 0-indexed

        const viewMode: AvailabilityViewMode = isDaily ? "daily" : "weekly";
        if (isDaily) {
          // For daily view (hourly), generate data for 24 hours
          // No progress bar, only status
          for (let hour = 0; hour < 24; hour++) {
            const spotData =
              availabilityIndex != null
                ? getSpotDataFromIndex(
                    startDate,
                    hour,
                    viewMode,
                    availabilityIndex,
                    isClassicInventory ? undefined : spotPosition,
                  )
                : calculateSpotsForDateTime(
                    startDate,
                    hour,
                    currentInventoryData,
                    clientPerLoop,
                    inventoryData,
                    isClassicInventory ? undefined : spotPosition,
                    isClassicInventory,
                  );

            const status = getStatusFromSpotData(spotData);
            const bookedPercentage = getBookedPercentage(spotData);
            const config = getStatusConfig(
              status,
              bookedPercentage,
              false,
              tPrice,
            );
            const bookingDetails =
              availabilityIndex != null
                ? getBookingDetailsFromIndex(startDate, hour, availabilityIndex)
                : getBookingDetailsForDateTime(
                    startDate,
                    hour,
                    currentInventoryData,
                    currentInventoryData.bookings,
                  );

            // Custom content for daily/hourly view - only status, no progress
            const customContent = (
              <div className="w-full h-full flex items-center justify-center">
                <div className={`text-xs font-semibold ${config.textColor}`}>
                  {config.label}
                </div>
              </div>
            );

            gridData.push({
              rowId: row.id,
              hour,
              customContent,
              tooltipContent: (
                <SpotTooltipContent
                  spotData={spotData}
                  date={startDate}
                  hour={hour}
                  bookingDetails={bookingDetails}
                  spotLabel={row.label}
                  isClassic={isClassicInventory}
                  campaignStartDate={campaignStartDate}
                  campaignEndDate={campaignEndDate}
                  isDailyView={true}
                />
              ),
              backgroundColor: config.bgColor,
            });
          }
        } else {
          // For weekly view, generate data for 7 days
          for (let i = 0; i < 7; i++) {
            const date = new Date(startDate);
            date.setDate(startDate.getDate() + i);

            const spotData =
              availabilityIndex != null
                ? getSpotDataFromIndex(
                    date,
                    undefined,
                    viewMode,
                    availabilityIndex,
                    isClassicInventory ? undefined : spotPosition,
                  )
                : calculateSpotsForDateTime(
                    date,
                    undefined,
                    currentInventoryData,
                    clientPerLoop,
                    inventoryData,
                    isClassicInventory ? undefined : spotPosition,
                    isClassicInventory,
                  );

            const status = getStatusFromSpotData(spotData);
            const bookedPercentage = getBookedPercentage(spotData);
            const config = getStatusConfig(
              status,
              bookedPercentage,
              false,
              tPrice,
            );

            // Custom content for weekly view - only status, no progress
            const customContent = (
              <div className="w-full h-full flex items-center justify-center">
                <div className={`text-xs font-semibold ${config.textColor}`}>
                  {config.label}
                </div>
              </div>
            );

            gridData.push({
              rowId: row.id,
              date,
              customContent,
              tooltipContent: (
                <SpotTooltipContent
                  spotData={spotData}
                  date={date}
                  isClassic={isClassicInventory}
                  campaignStartDate={campaignStartDate}
                  campaignEndDate={campaignEndDate}
                  isDailyView={false}
                />
              ),
              backgroundColor: config.bgColor,
            });
          }
        }
      });

      return gridData;
    },
    [
      currentInventoryData,
      availabilityIndex,
      clientPerLoop,
      isClassicInventory,
      inventoryData,
      campaignStartDate,
      campaignEndDate,
    ],
  );

  const calendarGridData = useMemo(() => {
    if (!currentInventoryData) return [];
    // For daily view, use calendarDate directly (not start of week)
    if (calendarViewMode === "daily") {
      return generateGridData(calendarRows, calendarDate, true);
    }
    // For weekly view, use start of week
    const startOfWeek = new Date(calendarDate);
    const day = startOfWeek.getDay();
    const diff = (day - 1 + 7) % 7; // Monday is 1
    startOfWeek.setDate(startOfWeek.getDate() - diff);
    return generateGridData(calendarRows, startOfWeek, false);
  }, [
    calendarDate,
    calendarViewMode,
    calendarRows,
    generateGridData,
    currentInventoryData,
  ]);

  // Generate hourly events for daily view (when not using detail column)
  const hourlyEvents = useMemo((): HourlyEvent[] => {
    if (calendarViewMode !== "daily" || !currentInventoryData) return [];

    const viewMode: AvailabilityViewMode = "daily";
    const events: HourlyEvent[] = [];
    for (let hour = 0; hour < 24; hour++) {
      const spotData =
        availabilityIndex != null
          ? getSpotDataFromIndex(
              calendarDate,
              hour,
              viewMode,
              availabilityIndex,
              undefined,
            )
          : calculateSpotsForDateTime(
              calendarDate,
              hour,
              currentInventoryData,
              clientPerLoop,
              inventoryData,
              undefined,
              isClassicInventory,
            );

      const status = getStatusFromSpotData(spotData);
      const bookedPercentage = getBookedPercentage(spotData);
      const config = getStatusConfig(status, bookedPercentage, false, tPrice);
      const bookingDetails =
        availabilityIndex != null
          ? getBookingDetailsFromIndex(calendarDate, hour, availabilityIndex)
          : getBookingDetailsForDateTime(
              calendarDate,
              hour,
              currentInventoryData,
              currentInventoryData.bookings,
            );

      // Only status, no progress bar
      const customContent = (
        <div className="w-full h-full flex items-center justify-center">
          <div className={`text-xs font-semibold ${config.textColor}`}>
            {config.label}
          </div>
        </div>
      );

      events.push({
        hour,
        customContent,
        tooltipContent: (
          <SpotTooltipContent
            spotData={spotData}
            date={calendarDate}
            hour={hour}
            bookingDetails={bookingDetails}
            spotLabel="Spot 01"
            isClassic={isClassicInventory}
            campaignStartDate={campaignStartDate}
            campaignEndDate={campaignEndDate}
            isDailyView={true}
          />
        ),
        backgroundColor: config.bgColor,
      });
    }
    return events;
  }, [
    calendarViewMode,
    calendarDate,
    currentInventoryData,
    availabilityIndex,
    clientPerLoop,
    isClassicInventory,
    inventoryData,
    campaignStartDate,
    campaignEndDate,
  ]);

  // Show error state with try again button
  if (errorInventoryAvailability && !currentInventoryData) {
    return (
      <div
        className="flex flex-col items-center justify-center p-8 space-y-4"
        role="alert"
        aria-live="polite"
      >
        <div className="text-mw-error-500 text-sm font-medium">
          {errorInventoryAvailability}
        </div>
        <Button
          onClick={() => inventoryData && fetchInventoryData(inventoryData)}
          disabled={isLoading || !inventoryData}
          variant="outline"
        >
          {isLoading
            ? tPrice("availability.loading")
            : tPrice("availability.tryAgain")}
        </Button>
      </div>
    );
  }

  const lastSyncedLabel = syncInfo?.lastSyncedAt
    ? tPrice("availability.lastSynced", {
        time: new Date(syncInfo.lastSyncedAt).toLocaleString(),
      })
    : tPrice("availability.notSyncedYet");

  return (
    <div className="h-full flex flex-col w-full min-w-0">
      {syncInfo?.status === "FAILED" && (
        <div
          className="mb-2 p-2 rounded-md bg-mw-orange-warning-50 text-mw-orange-warning-600 text-xs"
          role="alert"
        >
          {syncInfo.error
            ? tPrice("availability.syncFailed", { error: syncInfo.error })
            : tPrice("availability.syncFailedGeneric")}
        </div>
      )}
      <div className="mb-1 flex items-center justify-end gap-3">
        <AvailabilitySyncWarning syncInfo={syncInfo} />
        <span
          className="text-xs text-mw-neutral-500 text-right"
          data-testid="text-availability-last-synced"
        >
          {lastSyncedLabel}
        </span>
      </div>
      <CalendarView
        viewMode={calendarViewMode}
        onViewModeChange={setCalendarViewMode}
        selectedDate={calendarDate}
        onDateChange={setCalendarDate}
        events={calendarEvents}
        hourlyEvents={hourlyEvents}
        showDetailColumn={
          calendarViewMode === "weekly" || calendarViewMode === "daily"
        }
        detailColumnConfig={{
          header: "",
          width: "80px",
        }}
        rows={calendarRows}
        gridData={calendarGridData}
        colorPaletteInfo={colorInfo}
        highlightSelectedDate={false}
        highlightCurrentDate={false}
        minDate={minDate}
        maxDate={maxDate}
        calendarTitle={tPrice("availability.availabilityTimeline")}
        className={`flex flex-col flex-1 min-w-0 overflow-hidden`}
      />
    </div>
  );
};

export default InventoryAvailabilityCalendarView;
