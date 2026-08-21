import { AvailabilitySyncWarning } from "@components/common/AvailabilitySyncWarning";
import { SpotTooltipContent } from "@components/common/SpotTooltipContent";
import { Button } from "@components/ui/Button";
import { Tooltip } from "@components/ui/Tooltip";
import {
  useLazyGetInventoryAvailabilityQuery,
  useTriggerInventoryAvailabilitySyncMutation,
  useLazyGetInventoryAvailabilitySyncStatusQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import {
  getDaysInMonth,
  getStartOfWeek,
  toISODateString,
} from "@utils/dateUtils";
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
} from "@utils/inventoryavailability.utils";
import {
  getStatusConfig,
  parseAvailabilityResponse,
  parseAvailabilitySync,
} from "@utils/inventoryAvailabilityUI.utils";
import { clsx } from "clsx";
import {
  ChevronDown,
  ChevronUp,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useMemo, useState, useCallback, useEffect, useRef } from "react";
import { InventoryClassification } from "src/constants/inventory.constants";
import { CampaignCreateResponse } from "src/types/campaign.types";
import { InventoryItem } from "src/types/inventory.types";
import {
  AvailabilitySyncInfo,
  Booking,
  InventoryAvailabilityData,
} from "src/types/price-management.types";

export type CalendarViewMode = "monthly" | "weekly" | "daily";

interface SelectedInventoryAvailabilityProps {
  inventories: InventoryItem[];
  campaignData: CampaignCreateResponse | null;
  /** Interval between sync-status polls after a manual sync (test override). */
  syncPollIntervalMs?: number;
}

// Bounded polling after a manual sync: 5s interval, up to 5 minutes.
const DEFAULT_SYNC_POLL_INTERVAL_MS = 5000;
const SYNC_POLL_MAX_ATTEMPTS = 60;

const SelectedInventoryAvailability: React.FC<
  SelectedInventoryAvailabilityProps
> = ({
  inventories,
  campaignData,
  syncPollIntervalMs = DEFAULT_SYNC_POLL_INTERVAL_MS,
}) => {
  const { t: tPrice } = useTranslate(["price"]);
  const { t: tCommon } = useTranslate(["common"]);
  const dayNamesShort = useMemo(
    () => [
      tPrice("availability.days.sun"),
      tPrice("availability.days.mon"),
      tPrice("availability.days.tue"),
      tPrice("availability.days.wed"),
      tPrice("availability.days.thu"),
      tPrice("availability.days.fri"),
      tPrice("availability.days.sat"),
    ],
    [tPrice],
  );
  const [calendarViewMode, setCalendarViewMode] =
    useState<CalendarViewMode>("monthly");
  const [calendarDate, setCalendarDate] = useState(new Date());
  const [getInventoryAvailability] = useLazyGetInventoryAvailabilityQuery();
  const [expandedInventories, setExpandedInventories] = useState<string>("");
  const [inventoryAvailabilityData, setInventoryAvailabilityData] = useState<
    Record<string, InventoryAvailabilityData>
  >({});
  const [availabilityError, setAvailabilityError] = useState<string | null>(
    null,
  );
  const [syncInfo, setSyncInfo] = useState<AvailabilitySyncInfo | null>(null);
  // Bumped after a manual sync so the availability effect refetches.
  const [refreshKey, setRefreshKey] = useState(0);
  const [triggerAvailabilitySync, { isLoading: isTriggeringSync }] =
    useTriggerInventoryAvailabilitySyncMutation();
  const [fetchSyncStatus] = useLazyGetInventoryAvailabilitySyncStatusQuery();
  // True from trigger until the sync reaches a terminal state (or polling gives up).
  const [isSyncInProgress, setIsSyncInProgress] = useState(false);
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const handleSyncNow = useCallback(async () => {
    setIsSyncInProgress(true);
    try {
      try {
        await triggerAvailabilitySync().unwrap();
      } catch (error) {
        // 409 = a sync is already running (single-flight) — keep polling it.
        const status = (error as { status?: number })?.status;
        if (status !== 409) {
          throw error;
        }
      }
      // Poll the sync status at a bounded interval until it leaves RUNNING,
      // then refetch availability so the view reflects the completed sync.
      for (let attempt = 0; attempt < SYNC_POLL_MAX_ATTEMPTS; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, syncPollIntervalMs));
        if (!isMountedRef.current) return;
        const statusEnvelope = await fetchSyncStatus().unwrap();
        const statusData = (statusEnvelope as { data?: unknown })?.data ?? statusEnvelope;
        const state = (statusData as { status?: string })?.status;
        if (state && state !== "RUNNING") {
          if (state === "FAILED" && isMountedRef.current) {
            // Show the failure immediately; the refetch below replaces this
            // with the server's authoritative sync info (also FAILED).
            setSyncInfo((prev) => ({
              source: prev?.source ?? "IMS",
              lastSyncedAt: prev?.lastSyncedAt ?? null,
              status: "FAILED",
              error:
                (statusData as { error?: string })?.error ?? prev?.error ?? null,
            }));
          }
          break;
        }
      }
    } catch (error) {
      console.error("IMS availability sync failed:", error);
      if (isMountedRef.current) {
        setAvailabilityError("availability.syncFailedGeneric");
      }
    } finally {
      if (isMountedRef.current) {
        setIsSyncInProgress(false);
        setRefreshKey((k) => k + 1);
      }
    }
  }, [triggerAvailabilitySync, fetchSyncStatus, syncPollIntervalMs]);
  const isSyncing = isTriggeringSync || isSyncInProgress;

  const inventoryIdsKey = useMemo(
    () =>
      inventories
        .map((inv) => inv.detail?.externalId ?? inv.detail?.id ?? "")
        .filter(Boolean)
        .sort()
        .join(","),
    [inventories],
  );

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
    // daily
    const start = new Date(base);
    start.setHours(0, 0, 0, 0);
    const end = new Date(base);
    end.setHours(23, 59, 59, 999);
    return { apiStartDate: start, apiEndDate: end };
  }, [calendarDate, calendarViewMode]);

  useEffect(() => {
    const fetchAllInventoriesAvailability = async () => {
      if (!apiStartDate || !apiEndDate) {
        setInventoryAvailabilityData({});
        setAvailabilityError(null);
        return;
      }

      const externalIds = inventories
        .map((inv) => inv.detail?.externalId)
        .filter((id): id is string => !!id);

      if (externalIds.length === 0) {
        setInventoryAvailabilityData({});
        setAvailabilityError(null);
        return;
      }

      setAvailabilityError(null);
      try {
        const response = await getInventoryAvailability({
          data: {
            inventoryIds: externalIds,
            startTime: `${toISODateString(apiStartDate)}T00:00:00`,
            endTime: `${toISODateString(apiEndDate)}T23:59:59`,
          },
        }).unwrap();

        const inventoriesData = parseAvailabilityResponse(response);
        // Keep the previous sync info (e.g. an optimistic FAILED state) when
        // the response carries none.
        const parsedSync = parseAvailabilitySync(response);
        setSyncInfo((prev) => parsedSync ?? prev);
        const dataMap: Record<string, InventoryAvailabilityData> = {};

        if (inventoriesData) {
          inventories.forEach((inventory) => {
            const externalId = inventory.detail?.externalId;
            const id = inventory.detail?.id;
            if (externalId && id && inventoriesData[externalId]) {
              dataMap[id] = inventoriesData[externalId];
            }
          });
        }

        setInventoryAvailabilityData(dataMap);
      } catch (error) {
        console.error("Failed to fetch inventory availability:", error);
        setAvailabilityError("availability.loadError");
        setInventoryAvailabilityData({});
      }
    };

    if (inventories.length > 0) {
      fetchAllInventoriesAvailability();
    }
  }, [
    inventoryIdsKey,
    inventories,
    getInventoryAvailability,
    apiStartDate,
    apiEndDate,
    refreshKey,
  ]);

  // Toggle inventory expansion
  const toggleInventoryExpansion = useCallback((inventoryId: string) => {
    setExpandedInventories((prev) => (prev === inventoryId ? "" : inventoryId));
  }, []);

  const isDailyView = calendarViewMode === "daily";

  // Generate rows for the calendar
  const calendarRows = useMemo(() => {
    const rows: Array<{
      id: string;
      inventoryId: string;
      label: string;
      isInventoryHeader: boolean;
      spotIndex?: number;
    }> = [];

    inventories.forEach((inventory) => {
      const detail = inventory.detail;
      if (!detail?.id) return;
      const isDigital =
        detail.inventoryType === InventoryClassification.DIGITAL;
      const isClassic =
        detail.inventoryType === InventoryClassification.CLASSIC;
      const isExpanded = expandedInventories === detail.id;
      const clientPerLoop = inventory.operations?.clientPerLoop || 10;

      if (isClassic) {
        rows.push({
          id: `${detail.id}-row`,
          inventoryId: detail.id,
          label: detail.name ?? "",
          isInventoryHeader: false,
        });
      } else if (isDigital) {
        rows.push({
          id: `${detail.id}-header`,
          inventoryId: detail.id,
          label: detail.name ?? "",
          isInventoryHeader: true,
        });

        if (isExpanded) {
          for (let i = 0; i < clientPerLoop; i++) {
            rows.push({
              id: `${detail.id}-spot-${String(i + 1).padStart(2, "0")}`,
              inventoryId: detail.id,
              label: `Spot ${String(i + 1).padStart(2, "0")}`,
              isInventoryHeader: false,
              spotIndex: i,
            });
          }
        }
      }
    });

    return rows;
  }, [inventories, expandedInventories]);

  // Generate calendar dates based on view mode
  const calendarDates = useMemo(() => {
    if (calendarViewMode === "monthly") {
      const year = calendarDate.getFullYear();
      const month = calendarDate.getMonth();
      const daysInMonth = getDaysInMonth(year, month);
      const dates: Date[] = [];
      for (let day = 1; day <= daysInMonth; day++) {
        dates.push(new Date(year, month, day));
      }
      return dates;
    } else if (calendarViewMode === "weekly") {
      const startOfWeek = getStartOfWeek(calendarDate, 1); // Monday
      const dates: Date[] = [];
      for (let i = 0; i < 7; i++) {
        const date = new Date(startOfWeek);
        date.setDate(startOfWeek.getDate() + i);
        dates.push(date);
      }
      return dates;
    } else {
      // Daily view - return single date
      return [calendarDate];
    }
  }, [calendarDate, calendarViewMode]);

  // Generate hours for daily view
  const hours = useMemo(() => {
    if (calendarViewMode === "daily") {
      return Array.from({ length: 24 }, (_, i) => i);
    }
    return [];
  }, [calendarViewMode]);

  // Pre-build availability index per inventory (when data loads). Calendar date changes only need fast lookups.
  const availabilityIndexByInventory = useMemo(() => {
    const map: Record<string, AvailabilityIndex | null> = {};
    inventories.forEach((inv) => {
      const id = inv.detail?.id;
      if (!id) return;
      const data = inventoryAvailabilityData[id];
      if (data) {
        map[id] = buildAvailabilityIndex(data, inv);
      } else {
        map[id] = null;
      }
    });
    return map;
  }, [inventories, inventoryAvailabilityData]);

  // Cell data type (non-null return from getCellData when data exists)
  type CellData = {
    spotData: SpotData | null;
    status: SpotStatus | null;
    config: {
      label: string;
      bgColor: string;
      textColor: string;
      progressColor:
        | "success"
        | "error"
        | "secondary"
        | "warning"
        | "primary"
        | "info"
        | "neutral"
        | "teal"
        | "lightGreen"
        | "orange"
        | undefined;
      showProgress: boolean;
    };
    isClassic: boolean;
    isBlank: boolean;
    isParentRow?: boolean;
    bookingDetails?: Booking[];
    tooltipContent?: React.ReactNode;
  };

  // Pre-compute cell data map: rowId -> cellKey -> CellData | null
  // Prefer index (getSpotDataFromIndex) for all views when available — fast lookups; returns 0 available when outside operating hours.
  // Fall back to legacy (calculateSpotsForDateTime) only when index is null or monthly spot row returns 0 from index.
  const cellDataMap = useMemo(() => {
    const map: Record<string, Record<string, CellData | null>> = {};
    const spotDataCache = new Map<string, SpotData>();

    const getCellKey = (date: Date, hour?: number) =>
      hour !== undefined
        ? `${toISODateString(date)}-${hour}`
        : toISODateString(date);

    const cacheKey = (
      invId: string,
      date: Date,
      hour: number | undefined,
      spotPosition: number | "all",
    ) => `${invId}|${toISODateString(date)}|${hour ?? "d"}|${spotPosition}`;

    const aggregateSpotData = (items: SpotData[]): SpotData => {
      if (items.length === 0)
        return {
          totalSpots: 0,
          availableSpots: 0,
          bookedSpots: 0,
          reservedSpots: 0,
        };
      return items.reduce(
        (acc, s) => ({
          totalSpots: acc.totalSpots + s.totalSpots,
          availableSpots: acc.availableSpots + s.availableSpots,
          bookedSpots: acc.bookedSpots + s.bookedSpots,
          reservedSpots: acc.reservedSpots + s.reservedSpots,
          totalSpotsPerDay:
            (acc.totalSpotsPerDay ?? acc.totalSpots) +
            (s.totalSpotsPerDay ?? s.totalSpots),
          availableSpotsPerDay:
            (acc.availableSpotsPerDay ?? acc.availableSpots) +
            (s.availableSpotsPerDay ?? s.availableSpots),
          bookedSpotsPerDay:
            (acc.bookedSpotsPerDay ?? acc.bookedSpots) +
            (s.bookedSpotsPerDay ?? s.bookedSpots),
          reservedSpotsPerDay:
            (acc.reservedSpotsPerDay ?? acc.reservedSpots) +
            (s.reservedSpotsPerDay ?? s.reservedSpots),
          isBlocked: acc.isBlocked || s.isBlocked,
        }),
        {
          totalSpots: 0,
          availableSpots: 0,
          bookedSpots: 0,
          reservedSpots: 0,
          totalSpotsPerDay: 0,
          availableSpotsPerDay: 0,
          bookedSpotsPerDay: 0,
          reservedSpotsPerDay: 0,
        } as SpotData,
      );
    };

    // Cap booked/reserved to totalSpots and recompute available (safety net for any path).
    const normalizeSpotData = (s: SpotData): SpotData => {
      const total = s.totalSpots;
      if (total <= 0) return s;
      let booked = Math.min(s.bookedSpots, total);
      let reserved = Math.min(s.reservedSpots, total);
      if (booked + reserved > total) {
        const scale = total / (booked + reserved);
        booked = Math.floor(booked * scale);
        reserved = total - booked;
      }
      const available = Math.floor(Math.max(0, total - booked - reserved));
      const out: SpotData = {
        ...s,
        bookedSpots: Math.round(booked),
        reservedSpots: Math.round(reserved),
        availableSpots: available,
      };
      if (s.totalSpotsPerDay != null && s.totalSpotsPerDay > 0) {
        let bd = Math.min(s.bookedSpotsPerDay ?? 0, s.totalSpotsPerDay);
        let rd = Math.min(s.reservedSpotsPerDay ?? 0, s.totalSpotsPerDay);
        if (bd + rd > s.totalSpotsPerDay) {
          const scale = s.totalSpotsPerDay / (bd + rd);
          bd = Math.floor(bd * scale);
          rd = Math.round(s.totalSpotsPerDay - bd);
        }
        out.bookedSpotsPerDay = Math.round(bd);
        out.reservedSpotsPerDay = Math.round(rd);
        out.availableSpotsPerDay = Math.floor(
          Math.max(0, s.totalSpotsPerDay - bd - rd),
        );
      }
      return out;
    };

    const getCachedOrComputeLegacy = (
      invId: string,
      date: Date,
      hour: number | undefined,
      spotPosition: number | "all",
      availabilityData: InventoryAvailabilityData,
      inventory: InventoryItem,
      clientPerLoop: number,
      isClassic: boolean,
    ): SpotData => {
      const key = cacheKey(invId, date, hour, spotPosition);
      const cached = spotDataCache.get(key);
      if (cached != null) return cached;
      const pos = spotPosition === "all" ? undefined : spotPosition;
      const result = calculateSpotsForDateTime(
        date,
        hour,
        availabilityData,
        clientPerLoop,
        inventory,
        pos,
        isClassic,
      );
      spotDataCache.set(key, result);
      return result;
    };

    const computeCellData = (
      row: (typeof calendarRows)[number],
      date: Date,
      hour?: number,
    ): CellData | null => {
      const inventory = inventories.find(
        (inv) => inv.detail?.id === row.inventoryId,
      );
      if (!inventory) return null;

      const isExpanded = expandedInventories === row.inventoryId;
      if (row.isInventoryHeader && isExpanded) {
        return {
          spotData: null,
          status: null,
          config: {
            label: "",
            bgColor: "bg-white",
            textColor: "",
            progressColor: undefined,
            showProgress: false,
          },
          isClassic: false,
          isBlank: true,
        };
      }

      const availabilityData = inventoryAvailabilityData[row.inventoryId];
      if (!availabilityData) return null;

      const clientPerLoop = inventory.operations?.clientPerLoop || 10;
      const spotPosition =
        row.spotIndex !== undefined ? row.spotIndex + 1 : undefined;
      const isClassic =
        inventory.detail.inventoryType === InventoryClassification.CLASSIC;
      const index = availabilityIndexByInventory[row.inventoryId];
      const isParentRow = row.isInventoryHeader === true;

      let spotData: SpotData;

      if (calendarViewMode === "monthly") {
        if (isParentRow) {
          spotData =
            index != null
              ? getSpotDataFromIndex(
                  date,
                  undefined,
                  "monthly",
                  index,
                  undefined,
                )
              : getCachedOrComputeLegacy(
                  row.inventoryId,
                  date,
                  undefined,
                  "all",
                  availabilityData,
                  inventory,
                  clientPerLoop,
                  isClassic,
                );
        } else {
          if (index != null) {
            const fromIndex = getSpotDataFromIndex(
              date,
              undefined,
              "weekly",
              index,
              spotPosition,
            );
            spotData =
              fromIndex.totalSpots === 0
                ? getCachedOrComputeLegacy(
                    row.inventoryId,
                    date,
                    undefined,
                    spotPosition!,
                    availabilityData,
                    inventory,
                    clientPerLoop,
                    isClassic,
                  )
                : fromIndex;
          } else {
            spotData = getCachedOrComputeLegacy(
              row.inventoryId,
              date,
              undefined,
              spotPosition!,
              availabilityData,
              inventory,
              clientPerLoop,
              isClassic,
            );
          }
        }
      } else if (calendarViewMode === "weekly") {
        if (index != null) {
          if (isParentRow) {
            const perSpot: SpotData[] = [];
            for (let pos = 1; pos <= clientPerLoop; pos++) {
              perSpot.push(
                getSpotDataFromIndex(date, undefined, "weekly", index, pos),
              );
            }
            spotData = aggregateSpotData(perSpot);
          } else {
            spotData = getSpotDataFromIndex(
              date,
              undefined,
              "weekly",
              index,
              spotPosition,
            );
          }
        } else {
          if (isParentRow) {
            const perSpot: SpotData[] = [];
            for (let pos = 1; pos <= clientPerLoop; pos++) {
              perSpot.push(
                getCachedOrComputeLegacy(
                  row.inventoryId,
                  date,
                  undefined,
                  pos,
                  availabilityData,
                  inventory,
                  clientPerLoop,
                  isClassic,
                ),
              );
            }
            spotData = aggregateSpotData(perSpot);
          } else {
            spotData = getCachedOrComputeLegacy(
              row.inventoryId,
              date,
              undefined,
              spotPosition!,
              availabilityData,
              inventory,
              clientPerLoop,
              isClassic,
            );
          }
        }
      } else {
        if (index != null) {
          if (isParentRow) {
            const perSpot: SpotData[] = [];
            for (let pos = 1; pos <= clientPerLoop; pos++) {
              perSpot.push(
                getSpotDataFromIndex(date, hour, "daily", index, pos),
              );
            }
            spotData = aggregateSpotData(perSpot);
          } else {
            spotData = getSpotDataFromIndex(
              date,
              hour,
              "daily",
              index,
              spotPosition,
            );
          }
        } else {
          if (isParentRow) {
            const perSpot: SpotData[] = [];
            for (let pos = 1; pos <= clientPerLoop; pos++) {
              perSpot.push(
                getCachedOrComputeLegacy(
                  row.inventoryId,
                  date,
                  hour,
                  pos,
                  availabilityData,
                  inventory,
                  clientPerLoop,
                  isClassic,
                ),
              );
            }
            spotData = aggregateSpotData(perSpot);
          } else {
            spotData = getCachedOrComputeLegacy(
              row.inventoryId,
              date,
              hour,
              spotPosition!,
              availabilityData,
              inventory,
              clientPerLoop,
              isClassic,
            );
          }
        }
      }

      spotData = normalizeSpotData(spotData);

      const isMonthlyView = calendarViewMode === "monthly";
      const status = getStatusFromSpotData(spotData, isMonthlyView);
      const bookedPercentage = getBookedPercentage(spotData);
      const showProgress = isMonthlyView && !isClassic;
      const config = getStatusConfig(
        status,
        bookedPercentage,
        showProgress,
        tPrice,
      );

      const bookingDetails =
        index != null
          ? getBookingDetailsFromIndex(date, hour, index)
          : getBookingDetailsForDateTime(
              date,
              hour,
              availabilityData,
              availabilityData.bookings,
            );

      const tooltipContent = (
        <SpotTooltipContent
          spotData={spotData}
          date={date}
          hour={hour}
          isClassic={isClassic}
          isParentRow={isParentRow ?? false}
          bookingDetails={bookingDetails}
          spotLabel={
            row.spotIndex !== undefined
              ? `Spot ${String(row.spotIndex + 1).padStart(2, "0")}`
              : undefined
          }
          campaignStartDate={campaignData?.startDate}
          campaignEndDate={campaignData?.endDate}
          isDailyView={isDailyView}
        />
      );

      return {
        spotData,
        status,
        config,
        isClassic,
        isBlank: false,
        isParentRow,
        bookingDetails,
        tooltipContent,
      };
    };

    for (const row of calendarRows) {
      map[row.id] = {};
      if (calendarViewMode === "daily") {
        for (const hour of hours) {
          const key = getCellKey(calendarDate, hour);
          map[row.id][key] = computeCellData(row, calendarDate, hour);
        }
      } else {
        for (const date of calendarDates) {
          const key = getCellKey(date);
          map[row.id][key] = computeCellData(row, date);
        }
      }
    }

    return map;
  }, [
    calendarRows,
    calendarDates,
    hours,
    calendarDate,
    calendarViewMode,
    inventories,
    inventoryAvailabilityData,
    availabilityIndexByInventory,
    expandedInventories,
    campaignData?.startDate,
    campaignData?.endDate,
    isDailyView,
  ]);

  const handlePrevious = useCallback(() => {
    const newDate = new Date(calendarDate);
    if (calendarViewMode === "monthly") {
      newDate.setDate(1);
      newDate.setMonth(newDate.getMonth() - 1);
    } else if (calendarViewMode === "weekly") {
      newDate.setDate(newDate.getDate() - 7);
    } else {
      newDate.setDate(newDate.getDate() - 1);
    }
    setCalendarDate(newDate);
  }, [calendarDate, calendarViewMode]);

  const handleNext = useCallback(() => {
    const newDate = new Date(calendarDate);
    if (calendarViewMode === "monthly") {
      newDate.setDate(1);
      newDate.setMonth(newDate.getMonth() + 1);
    } else if (calendarViewMode === "weekly") {
      newDate.setDate(newDate.getDate() + 7);
    } else {
      newDate.setDate(newDate.getDate() + 1);
    }
    setCalendarDate(newDate);
  }, [calendarDate, calendarViewMode]);

  const calendarTitle = useMemo(() => {
    if (calendarViewMode === "monthly") {
      return `${tCommon(`calendar.monthNames.${calendarDate.getMonth()}`)} ${calendarDate.getFullYear()}`;
    }
    if (calendarViewMode === "weekly") {
      const startOfWeek = getStartOfWeek(calendarDate, 1);
      const endOfWeek = new Date(startOfWeek);
      endOfWeek.setDate(endOfWeek.getDate() + 6);
      const weekNumber = Math.ceil(startOfWeek.getDate() / 7);
      const fmt = (d: Date) =>
        `${tCommon(`calendar.monthNamesShort.${d.getMonth()}`)} ${d.getDate()} ${d.getFullYear()}`;
      return `${tCommon("calendar.weekTitle", { n: weekNumber })} (${fmt(startOfWeek)} - ${fmt(endOfWeek)})`;
    }
    return tCommon("calendar.fullDateTitle", {
      weekday: tCommon(`calendar.weekDaysLong.${calendarDate.getDay()}`),
      month: tCommon(`calendar.monthNames.${calendarDate.getMonth()}`),
      day: calendarDate.getDate(),
      year: calendarDate.getFullYear(),
    });
  }, [calendarDate, calendarViewMode, tCommon]);

  const renderCellContent = useCallback(
    (cellData: CellData | null, isMonthly: boolean) => {
      if (!cellData) {
        return (
          <div className="w-full h-full flex items-center justify-center text-xs text-mw-neutral-400">
            -
          </div>
        );
      }

      // For blank cells (expanded header rows), return empty content
      if (cellData.isBlank) {
        return <div className="w-full h-full" />;
      }

      const { config, isClassic } = cellData;

      // For classic inventory, show simple status without "Spots Available" text
      if (isClassic) {
        return (
          <div className="w-full h-full flex items-center justify-center">
            <div className={`text-xs font-semibold ${config.textColor}`}>
              {config.label}
            </div>
          </div>
        );
      }

      if (isMonthly) {
        return (
          <div className="space-y-2">
            <div className={`text-xs font-semibold ${config.textColor}`}>
              {config.label}
            </div>
          </div>
        );
      }

      return (
        <div className="w-full h-full flex items-center justify-center">
          <div className={`text-xs font-semibold ${config.textColor}`}>
            {config.label}
          </div>
        </div>
      );
    },
    [],
  );

  return (
    <div
      className="w-full h-full flex flex-col"
      role="region"
      aria-label={tPrice("availability.ariaRegion")}
    >
      {availabilityError && (
        <div
          className="mb-4 p-3 rounded-lg bg-mw-error-50 text-mw-error-700 text-sm"
          role="alert"
          aria-live="polite"
        >
          {tPrice(availabilityError)}
        </div>
      )}
      {syncInfo?.status === "FAILED" && (
        <div
          className="mb-4 p-3 rounded-lg bg-mw-orange-warning-50 text-mw-orange-warning-600 text-sm"
          role="alert"
          aria-live="polite"
        >
          {syncInfo.error
            ? tPrice("availability.syncFailed", { error: syncInfo.error })
            : tPrice("availability.syncFailedGeneric")}
        </div>
      )}
      <div className="flex items-center justify-between mb-4">
        <div className="" aria-live="polite">
          {inventories.length} {tPrice("availability.selectedInventories")}
        </div>
        <div className="flex items-center gap-4">
          <AvailabilitySyncWarning syncInfo={syncInfo} />
          <span
            className="text-xs text-mw-neutral-500"
            data-testid="text-availability-last-synced"
          >
            {syncInfo?.lastSyncedAt
              ? tPrice("availability.lastSynced", {
                  time: new Date(syncInfo.lastSyncedAt).toLocaleString(),
                })
              : tPrice("availability.notSyncedYet")}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={isSyncing}
            onClick={handleSyncNow}
            data-testid="button-availability-sync-now"
          >
            {isSyncing
              ? tPrice("availability.syncing")
              : tPrice("availability.syncNow")}
          </Button>
          <div
            className="flex items-center bg-mw-neutral-50 rounded-lg p-1"
            role="group"
            aria-label={tPrice("availability.ariaCalendarView")}
          >
            {(["monthly", "weekly", "daily"] as CalendarViewMode[]).map(
              (mode) => (
                <button
                  key={mode}
                  type="button"
                  aria-pressed={calendarViewMode === mode}
                  aria-label={`${mode} view`}
                  onClick={() => setCalendarViewMode(mode)}
                  className={clsx(
                    "px-4 py-1.5 text-xs font-medium rounded-md transition-all cursor-pointer",
                    calendarViewMode === mode
                      ? "bg-mw-primary-500 text-white shadow-sm"
                      : "text-mw-neutral-600 hover:text-mw-neutral-900 hover:bg-mw-neutral-200",
                  )}
                >
                  {mode === "monthly"
                    ? tPrice("availability.viewMonthly")
                    : mode === "weekly"
                      ? tPrice("availability.viewWeekly")
                      : tPrice("availability.viewDaily")}
                </button>
              ),
            )}
          </div>
        </div>
      </div>

      {/* Navigation */}
      <div className="flex items-center justify-between mb-4">
        {/* Legend */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 border border-container-border bg-white" />
            <span className="text-xs text-mw-neutral-600">
              {tPrice("calendar.legend.available")}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 border border-container-border bg-mw-primary-50" />
            <span className="text-xs text-mw-neutral-600">
              {tPrice("calendar.legend.reserved")}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 border border-container-border bg-mw-error-50" />
            <span className="text-xs text-mw-neutral-600">
              {tPrice("calendar.legend.booked")}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 border border-container-border bg-mw-error-200" />
            <span className="text-xs text-mw-neutral-600">
              {tPrice("calendar.legend.fully_booked")}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-4 h-4 border border-container-border bg-mw-neutral-100" />
            <span className="text-xs text-mw-neutral-600">
              {tPrice("calendar.legend.blocked")}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="iconMd"
            onClick={handlePrevious}
            className="p-2 rounded-lg"
            aria-label={tPrice("availability.ariaPreviousPeriod")}
          >
            <ChevronLeft className="w-5 h-5 text-mw-neutral-600" aria-hidden />
          </Button>
          <span className="text-xs font-medium text-black px-2">
            {calendarTitle}
          </span>
          <Button
            variant="outline"
            size="iconMd"
            onClick={handleNext}
            className="p-2 rounded-lg"
            aria-label={tPrice("availability.ariaNextPeriod")}
          >
            <ChevronRight className="w-5 h-5 text-mw-neutral-600" aria-hidden />
          </Button>
        </div>
      </div>

      {/* Calendar Grid */}
      <div className="flex-1 overflow-auto rounded-lg">
        <div className="min-w-full">
          {/* Header Row */}
          <div className="flex sticky top-0 z-10">
            {/* Detail Column */}
            <div className="w-[20%] shrink-0 border border-mw-neutral-200 p-2 bg-mw-neutral-50 rounded-tl-lg">
              <div className="text-sm font-medium text-mw-neutral-600"></div>
            </div>
            {/* Date/Hour Columns */}
            {calendarViewMode === "daily"
              ? hours.map((hour) => (
                  <div
                    key={hour}
                    className="w-[80px] bg-mw-neutral-50 shrink-0 border-b border-r border-t border-mw-neutral-200 p-2 text-center  last:rounded-tr-lg"
                  >
                    <div className="text-m font-medium text-mw-neutral-900">
                      {String(hour).padStart(2, "0")}:00
                    </div>
                  </div>
                ))
              : calendarDates.map((date) => {
                  const dayName = dayNamesShort[date.getDay()];
                  const dayNumber = date.getDate();
                  return (
                    <div
                      key={toISODateString(date)}
                      className={clsx(
                        "bg-mw-neutral-50 shrink-0 border-b border-r border-t border-mw-neutral-200 p-2 text-center last:rounded-tr-lg",
                        calendarViewMode === "weekly" ? "flex-1" : "w-[120px]",
                      )}
                    >
                      <div className="text-m font-medium text-mw-neutral-600">
                        {dayName}
                      </div>
                      <div className="text-m font-medium text-mw-neutral-900">
                        {dayNumber}
                      </div>
                    </div>
                  );
                })}
          </div>

          {/* Data Rows */}
          {calendarRows.map((row) => {
            const inventory = inventories.find(
              (inv) => inv.detail?.id === row.inventoryId,
            );
            const isExpanded = expandedInventories === row.inventoryId;
            const isDigital =
              inventory?.detail.inventoryType ===
              InventoryClassification.DIGITAL;

            return (
              <div key={row.id} className="flex">
                {/* Row Label Cell */}
                <div
                  className={clsx(
                    "w-[20%] shrink-0 border-b border-r border-l border-mw-neutral-200 p-2",
                    row.isInventoryHeader ? "bg-white" : "bg-mw-neutral-50",
                  )}
                >
                  {row.isInventoryHeader && isDigital ? (
                    <button
                      type="button"
                      onClick={() => toggleInventoryExpansion(row.inventoryId)}
                      className="flex items-center justify-between gap-2 w-full text-left"
                      aria-expanded={isExpanded}
                      aria-label={
                        isExpanded
                          ? tPrice("availability.ariaCollapseSpots", {
                              label: row.label,
                            })
                          : tPrice("availability.ariaExpandSpots", {
                              label: row.label,
                            })
                      }
                    >
                      <div className="text-sm font-medium text-mw-neutral-900">
                        {row.label}
                      </div>
                      <div className="border border-mw-primary-500 p-1 rounded-md">
                        {isExpanded ? (
                          <ChevronUp className="w-4 h-4 text-mw-neutral-500" />
                        ) : (
                          <ChevronDown className="w-4 h-4 text-mw-neutral-500" />
                        )}
                      </div>
                    </button>
                  ) : (
                    <div className="text-sm font-medium text-mw-neutral-900">
                      {row.label}
                    </div>
                  )}
                </div>

                {/* Data Cells */}
                {calendarViewMode === "daily"
                  ? hours.map((hour) => {
                      const cellKey = `${toISODateString(calendarDate)}-${hour}`;
                      const cellData = cellDataMap[row.id]?.[cellKey] ?? null;
                      return (
                        <div
                          key={hour}
                          className={`w-[80px] shrink-0 h-full min-h-[48px] border-b border-r border-mw-neutral-200 p-2 ${cellData?.config.bgColor ?? ""}`}
                        >
                          {cellData?.tooltipContent && !cellData?.isBlank ? (
                            <Tooltip
                              content={cellData.tooltipContent}
                              position="bottom"
                              className="whitespace-normal"
                            >
                              <div
                                className={clsx(
                                  "w-full h-full",
                                  cellData?.config.bgColor,
                                )}
                              >
                                {renderCellContent(cellData, false)}
                              </div>
                            </Tooltip>
                          ) : (
                            <div
                              className={clsx(
                                "w-full h-full",
                                cellData?.config.bgColor,
                              )}
                            >
                              {renderCellContent(cellData, false)}
                            </div>
                          )}
                        </div>
                      );
                    })
                  : calendarDates.map((date) => {
                      const cellKey = toISODateString(date);
                      const cellData = cellDataMap[row.id]?.[cellKey] ?? null;
                      return (
                        <div
                          key={cellKey}
                          className={clsx(
                            "shrink-0 border-b border-r border-mw-neutral-200 p-2 h-full min-h-[48px]",
                            calendarViewMode === "weekly"
                              ? "flex-1"
                              : "w-[120px]",
                            cellData?.config.bgColor
                              ? cellData?.config.bgColor
                              : "bg-mw-neutral-50",
                          )}
                        >
                          {cellData?.tooltipContent && !cellData?.isBlank ? (
                            <Tooltip
                              content={cellData.tooltipContent}
                              position="bottom"
                              className="whitespace-normal"
                            >
                              <div
                                className={clsx(
                                  "w-full h-full",
                                  cellData?.config.bgColor,
                                )}
                              >
                                {renderCellContent(
                                  cellData,
                                  calendarViewMode === "monthly",
                                )}
                              </div>
                            </Tooltip>
                          ) : (
                            <div
                              className={clsx(
                                "w-full h-full",
                                cellData?.config.bgColor,
                              )}
                            >
                              {renderCellContent(
                                cellData,
                                calendarViewMode === "monthly",
                              )}
                            </div>
                          )}
                        </div>
                      );
                    })}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default SelectedInventoryAvailability;
