import { InventoryCard } from "@components/common/InventoryCard";
import { ScheduleGrid } from "@components/common/ScheduleGrid";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Checkbox } from "@components/ui/Checkbox";
import { DateRangePicker } from "@components/ui/DateRangePicker";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Input } from "@components/ui/Input";
import { Label } from "@components/ui/Label";
import { Modal } from "@components/ui/Modal";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Radio } from "@components/ui/Radio";
import { Loading } from "@components/ui/Spinner";
import { Tooltip } from "@components/ui/Tooltip";
import { useAnnounce } from "@hooks/useAnnounce";
import { useSelectedInventoryList } from "@hooks/useSelectedInventoryList";
import {
  useLazyGetSelectedInventorySchedulesQuery,
  useOptimizeInventorySchedulesMutation,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { formatTime } from "@utils/optimization.utils";
import {
  addHoursForNewlyAddedDays,
  applySchedulePattern,
  convertDayNameBack,
  DAYS_OF_WEEK,
  extractOperationTimes,
  formatDateToYYYYMMDD,
  getDayOperationTimes,
  hasAnyDayBelowMinHours,
  MAPPING_WEEKDAYS_NAMES,
  SCHEDULE_PATTERNS,
  SchedulePattern,
} from "@utils/schedule.utils";
import clsx from "clsx";
import { Info, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { InventoryClassification } from "src/constants/inventory.constants";
import {
  InventoryItem,
  InventorySchedules,
  ScheduleDays,
} from "src/types/inventory.types";

export interface OptimizeManuallyDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  campaignState: any;
}

export const OptimizeManuallyDrawer: React.FC<OptimizeManuallyDrawerProps> = ({
  isOpen,
  onClose,
  campaignId,
  campaignState,
}) => {
  const { showError, showSuccess, showWarning } = useAnnounce();
  const campaignStartDate = campaignState.campaignData?.startDate
    ? new Date(campaignState.campaignData?.startDate)
    : undefined;
  campaignStartDate?.setHours(0, 0, 0, 0);
  const campaignEndDate = campaignState.campaignData?.endDate
    ? new Date(campaignState.campaignData?.endDate)
    : undefined;
  campaignEndDate?.setHours(23, 59, 59, 999);

  const [scheduleDate, setScheduleDate] = useState<{
    from: Date | undefined | null;
    to: Date | undefined | null;
  }>({
    from: campaignStartDate,
    to: campaignEndDate,
  });
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [selectedDays, setSelectedDays] = useState<string[]>([]);
  const [selectedHours, setSelectedHours] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [clearPreviousSchedule, setClearPreviousSchedule] =
    useState<boolean>(false);
  const [scheduleGridMode, setScheduleGridMode] = useState<
    "select" | "deselect" | "custom"
  >("custom");
  const [selectedPattern, setSelectedPattern] =
    useState<SchedulePattern>("custom");

  const [inventoriesSelectedList, setInventoriesSelectedList] = useState<
    InventoryItem[]
  >([]);

  const [inventoriesSelectedForSchedules, setInventoriesSelectedForSchedules] =
    useState<string[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [inventorySchedules, setInventorySchedules] = useState<
    InventorySchedules[]
  >([]);
  const [isSaveModalOpen, setIsSaveModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const isApplyingPatternRef = useRef(false); // Prevent pattern detection when applying pattern
  const hasManualInteractionRef = useRef(false); // Track if user has manually interacted with cells
  const hasDeselectAllInteractionRef = useRef(false); // Track if user has manually interacted with cells

  // API hooks
  const [fetchSelectedInventorySchedules] =
    useLazyGetSelectedInventorySchedulesQuery();
  const [saveOptimizedSchedule] = useOptimizeInventorySchedulesMutation();

  // Use the reusable hook for loading all selected inventories at once.
  // `enabled` is gated on `isOpen` (not campaignData) so the hook resets on
  // close and refetches on every open — otherwise the list is a one-time
  // snapshot and inventories deleted after first mount keep showing.
  const { selectedItems, isLoading: isInventoryLoading } =
    useSelectedInventoryList({
      campaignId,
      enabled: !!campaignId && isOpen,
      pageSize: -1, // Load all at once
      sortBy: "name",
      sortDir: "asc",
    });

  // Sync loading state
  useEffect(() => {
    setIsLoading(isInventoryLoading);
  }, [isInventoryLoading]);

  // Reset state when drawer opens/closes
  useEffect(() => {
    if (isOpen && campaignId) {
      setSearchQuery("");
      setClearPreviousSchedule(false);
      setScheduleGridMode("custom");
      setSelectedPattern("custom");
      setIsSaving(false);
      setIsSaveModalOpen(false);
      isApplyingPatternRef.current = false;
      hasManualInteractionRef.current = false;
      hasDeselectAllInteractionRef.current = false;
    }
  }, [isOpen, campaignId]);

  // Copy the freshly-fetched selected inventories into local state on open.
  // The follow-up schedule/grid setup is invoked through a ref because those
  // handlers are declared further down — calling them directly here is a
  // forward reference (TDZ ReferenceError once the fetch resolves after reopen).
  const hasProcessedInitialLoadRef = useRef(false);
  const runInitialScheduleSetupRef = useRef<(ids: string[]) => void>(() => {});
  useEffect(() => {
    if (
      selectedItems.length > 0 &&
      !hasProcessedInitialLoadRef.current &&
      isOpen
    ) {
      hasProcessedInitialLoadRef.current = true;
      setInventoriesSelectedList([...selectedItems]);
      const newIds = selectedItems.map((inv) => inv.detail.id);
      setInventoriesSelectedForSchedules(newIds);
      runInitialScheduleSetupRef.current(newIds);
    }
    // Reset the ref when drawer closes
    if (!isOpen) {
      hasProcessedInitialLoadRef.current = false;
    }
  }, [selectedItems, isOpen]);

  // Initialize all hours as selected when dates are set
  useEffect(() => {
    if (
      scheduleDate.from &&
      scheduleDate.to &&
      !isApplyingPatternRef.current && // Don't auto-select when pattern is being applied
      selectedPattern === "custom" // Only auto-select for custom pattern
    ) {
      const newSelectedHours = new Set<string>();
      const current = new Date(scheduleDate.from);
      current.setHours(0, 0, 0, 0);
      const end = new Date(scheduleDate.to);
      end.setHours(23, 59, 59, 999);
      if (selectedDays.length && !hasManualInteractionRef.current) {
        while (current <= end) {
          const dayName =
            DAYS_OF_WEEK[current.getDay() === 0 ? 6 : current.getDay() - 1];
          if (selectedDays.includes(dayName)) {
            for (let hour = 0; hour < 24; hour++) {
              const dateStr = formatDateToYYYYMMDD(current);
              newSelectedHours.add(`${dateStr}-${hour}`);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        setSelectedHours(newSelectedHours);
      } else if (!selectedDays.length) {
        setSelectedHours(new Set());
      } else if (
        hasManualInteractionRef.current &&
        selectedDays.length &&
        selectedHours.size
      ) {
        // Start with a copy of current selectedHours
        const newSelectedHours = new Set(selectedHours);
        while (current <= end) {
          const dayName =
            DAYS_OF_WEEK[current.getDay() === 0 ? 6 : current.getDay() - 1];
          const dateStr = formatDateToYYYYMMDD(current);

          if (!selectedDays.includes(dayName)) {
            // Remove all hours for days not in selectedDays
            for (let hour = 0; hour < 24; hour++) {
              newSelectedHours.delete(`${dateStr}-${hour}`);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        setSelectedHours(newSelectedHours);
      }
    }
  }, [
    scheduleDate.from,
    scheduleDate.to,
    selectedDays,
    isApplyingPatternRef,
    selectedPattern,
  ]);

  const handleDayToggle = (day: string) => {
    setScheduleGridMode("custom");
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    // Mark that user has manually interacted
    hasManualInteractionRef.current = true;
    setSelectedDays((prev) =>
      prev.includes(day)
        ? prev.filter((d) => d !== day)
        : [...prev, day].sort(
            (a, b) => DAYS_OF_WEEK.indexOf(a) - DAYS_OF_WEEK.indexOf(b),
          ),
    );
  };

  const handleSelectAllDays = () => {
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    hasManualInteractionRef.current = true;
    setScheduleGridMode("custom");
    const newSelectedDays = [...DAYS_OF_WEEK];

    setSelectedDays(newSelectedDays);

    // Add hours for newly added days (manually since useEffect won't run due to hasManualInteractionRef)
    if (scheduleDate.from && scheduleDate.to && newSelectedDays.length > 0) {
      setSelectedHours(() =>
        addHoursForNewlyAddedDays(scheduleDate, newSelectedDays, new Set()),
      );
    }
  };

  const handleCellClick = useCallback((date: Date, hour: number) => {
    const dateStr = formatDateToYYYYMMDD(date);
    const hourKey = `${dateStr}-${hour}`;
    setScheduleGridMode("custom");
    setSelectedHours((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(hourKey)) {
        newSet.delete(hourKey);
      } else {
        newSet.add(hourKey);
      }
      return newSet;
    });
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    // Mark that user has manually interacted
    hasManualInteractionRef.current = true;
  }, []);

  const handleRowClick = useCallback(
    (date: Date) => {
      setScheduleGridMode("custom");
      const dayName = DAYS_OF_WEEK[date.getDay() === 0 ? 6 : date.getDay() - 1];
      if (selectedDays.indexOf(dayName) === -1) {
        return;
      }
      const dateStr = formatDateToYYYYMMDD(date);
      const allHoursSelected = Array.from({ length: 24 }, (_, i) => i).every(
        (hour) => selectedHours.has(`${dateStr}-${hour}`),
      );

      setSelectedHours((prev) => {
        const newSet = new Set(prev);
        for (let hour = 0; hour < 24; hour++) {
          const hourKey = `${dateStr}-${hour}`;
          if (allHoursSelected) {
            newSet.delete(hourKey);
          } else {
            newSet.add(hourKey);
          }
        }
        return newSet;
      });
      if (!isApplyingPatternRef.current) {
        setSelectedPattern("custom");
      }
      // Mark that user has manually interacted
      hasManualInteractionRef.current = true;
    },
    [selectedHours, selectedDays],
  );

  const handleColumnClick = useCallback(
    (hour: number) => {
      setScheduleGridMode("custom");
      if (!scheduleDate.from || !scheduleDate.to || selectedDays.length === 0)
        return;

      // Check if all selected days have this hour selected
      const current = new Date(scheduleDate.from);
      current.setHours(0, 0, 0, 0);
      const end = new Date(scheduleDate.to);
      end.setHours(23, 59, 59, 999);

      let allSelected = true;
      while (current <= end) {
        const dayName =
          DAYS_OF_WEEK[current.getDay() === 0 ? 6 : current.getDay() - 1];
        if (selectedDays.includes(dayName)) {
          const dateStr = formatDateToYYYYMMDD(current);
          const hourKey = `${dateStr}-${hour}`;
          if (!selectedHours.has(hourKey)) {
            allSelected = false;
            break;
          }
        }
        current.setDate(current.getDate() + 1);
      }

      // Toggle the entire column
      setSelectedHours((prev) => {
        const newSet = new Set(prev);
        const current = new Date(scheduleDate.from!);
        current.setHours(0, 0, 0, 0);
        const end = new Date(scheduleDate.to!);
        end.setHours(23, 59, 59, 999);
        while (current <= end) {
          const dayName =
            DAYS_OF_WEEK[current.getDay() === 0 ? 6 : current.getDay() - 1];
          if (selectedDays.includes(dayName)) {
            const dateStr = formatDateToYYYYMMDD(current);
            const hourKey = `${dateStr}-${hour}`;
            if (allSelected) {
              newSet.delete(hourKey);
            } else {
              newSet.add(hourKey);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        return newSet;
      });
      if (!isApplyingPatternRef.current) {
        setSelectedPattern("custom");
      }
      // Mark that user has manually interacted
      hasManualInteractionRef.current = true;
    },
    [scheduleDate.from, scheduleDate.to, selectedDays, selectedHours],
  );

  const handleSelectAllGrid = () => {
    if (!scheduleDate.from || !scheduleDate.to) return;
    const newSelectedHours = new Set<string>();
    const current = new Date(scheduleDate.from);
    current.setHours(0, 0, 0, 0);
    const end = new Date(scheduleDate.to);
    end.setHours(23, 59, 59, 999);

    while (current <= end) {
      const dayName =
        DAYS_OF_WEEK[current.getDay() === 0 ? 6 : current.getDay() - 1];
      if (selectedDays.includes(dayName)) {
        for (let hour = 0; hour < 24; hour++) {
          const dateStr = formatDateToYYYYMMDD(current);
          newSelectedHours.add(`${dateStr}-${hour}`);
        }
      }
      current.setDate(current.getDate() + 1);
    }
    setSelectedHours(newSelectedHours);
    setScheduleGridMode("select");
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
  };

  const handleDeselectAllGrid = () => {
    setSelectedHours(new Set());
    setScheduleGridMode("deselect");
    hasDeselectAllInteractionRef.current = true;
    // Mark that user has manually interacted
    hasManualInteractionRef.current = true;
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
  };

  // Classic inventories have no hourly grid — only Digital selections need
  // at least one hour picked before saving.
  const selectedForSchedulesItems = useMemo(
    () =>
      inventoriesSelectedList.filter((item) =>
        inventoriesSelectedForSchedules.includes(item.detail.id),
      ),
    [inventoriesSelectedList, inventoriesSelectedForSchedules],
  );
  const hasDigitalSelectedForSchedules = selectedForSchedulesItems.some(
    (item) => item.detail.inventoryType === InventoryClassification.DIGITAL,
  );
  const hasClassicSelectedForSchedules = selectedForSchedulesItems.some(
    (item) => item.detail.inventoryType === InventoryClassification.CLASSIC,
  );
  // Digital-only fields (day-of-week restriction, hourly booking grid,
  // schedule patterns) don't apply when every selected inventory is Classic —
  // Classic schedules only carry a date range.
  const allSelectedAreClassic =
    hasClassicSelectedForSchedules && !hasDigitalSelectedForSchedules;

  const handleScheduleSave = () => {
    if (hasDigitalSelectedForSchedules && selectedHours.size === 0) {
      showError(
        tCampaigns("optimization.optimizeManuallyDrawer.noHoursSelected"),
      );
      return;
    }
    if (inventoriesSelectedForSchedules.length === 0) {
      showError(tCampaigns("optimizeManually.errors.selectInventory"));
      return;
    }
    if (hasMinHoursViolation) {
      showError(
        tCampaigns("optimization.optimizeManuallyDrawer.minHoursViolation"),
      );
      return;
    }
    setIsSaveModalOpen(true);
  };

  const handleSave = async () => {
    setIsSaving(true);
    // Convert schedule dates to yyyy-mm-dd format (matching API format)
    if (!scheduleDate.from || !scheduleDate.to) {
      showError(tCampaigns("optimizeManually.errors.selectScheduleDates"));
      return;
    }
    const scheduleStartDate = formatDateToYYYYMMDD(scheduleDate.from);
    const scheduleEndDate = formatDateToYYYYMMDD(scheduleDate.to);
    // Convert scheduleDays from "Mon" to "MONDAY" format
    const scheduleDays = selectedDays.map(convertDayNameBack);

    // Convert selectedHours Set to bookingMatrix format
    // Format: "YYYY-MM-DD-hour" (e.g., "2025-12-13-7")
    const bookingMatrix: Record<string, number[]> = {};

    // Group hours by date
    selectedHours.forEach((hourKey) => {
      // Split by last hyphen to separate date and hour
      const lastHyphenIndex = hourKey.lastIndexOf("-");
      const dateStr = hourKey.substring(0, lastHyphenIndex); // "YYYY-MM-DD"
      const hourStr = hourKey.substring(lastHyphenIndex + 1); // "hour"

      const [year, month, day] = dateStr.split("-").map(Number);

      // Create date object
      const date = new Date(year, month - 1, day);
      const formattedDate = formatDateToYYYYMMDD(date);
      const hour = parseInt(hourStr, 10);

      if (!bookingMatrix[formattedDate]) {
        bookingMatrix[formattedDate] = [];
      }
      if (!bookingMatrix[formattedDate].includes(hour)) {
        bookingMatrix[formattedDate].push(hour);
      }
    });
    try {
      const scheduleResp = await saveOptimizedSchedule({
        campaignId,
        data: {
          inventoryIds: inventoriesSelectedForSchedules,
          clearSchedules: clearPreviousSchedule,
          schedule: {
            startDate: scheduleStartDate,
            endDate: scheduleEndDate,
            scheduleDays: scheduleDays as ScheduleDays,
            bookingMatrix: bookingMatrix,
          },
        },
      }).unwrap();
      if (scheduleResp.success && scheduleResp.data) {
        showSuccess(scheduleResp.data);
        if (
          Object.keys(overlappingInventoryMap).length &&
          !clearPreviousSchedule
        ) {
          showWarning(
            tCampaigns("optimization.optimizeManuallyDrawer.scheduleWarning"),
          );
        }
        setIsSaving(false);
        onClose();
      } else {
        setIsSaving(false);
        showError(
          scheduleResp.data ||
            tCampaigns("optimization.optimizeManuallyDrawer.errorWhileSaving"),
        );
      }
    } catch (error) {
      setIsSaving(false);
      console.error("Error while Saving:", error);
    }
  };

  const filteredInventories = useMemo(() => {
    let inventoriesToReturn: InventoryItem[] = [];
    if (!searchQuery) {
      inventoriesToReturn = inventoriesSelectedList.filter(
        (inventorySelected) => inventorySelected,
      );
    } else {
      const query = searchQuery.toLowerCase();
      inventoriesToReturn = inventoriesSelectedList.filter(
        (inventorySelected: InventoryItem) =>
          inventorySelected.detail.name?.toLowerCase().includes(query),
      );
    }
    return inventoriesToReturn;
  }, [searchQuery, inventoriesSelectedList]);

  const selectAllInventory = useMemo(() => {
    const diffFilteredToSelected = filteredInventories.filter(
      (item) => !inventoriesSelectedForSchedules.includes(item.detail.id),
    );
    return diffFilteredToSelected.length === 0;
  }, [inventoriesSelectedForSchedules, filteredInventories]);

  const getOverlapMap = useCallback(
    (schedules: InventorySchedules[]) => {
      return schedules.reduce<
        Record<
          string,
          {
            count: number;
            inventoryIds: string[];
          }
        >
      >(
        (acc, inventorySchedule) => {
          inventorySchedule.schedules.forEach((schedule) => {
            Object.entries(schedule.bookingMatrix).forEach(
              ([bookingDate, hours]) => {
                const bookingInDateFormat = new Date(bookingDate);
                const dateKey = formatDateToYYYYMMDD(bookingInDateFormat);
                hours.forEach((hour) => {
                  const dateHourKey = dateKey + `-${hour}`;
                  const countInvoentoryIdMap = acc[dateHourKey] || {};
                  countInvoentoryIdMap.count =
                    (countInvoentoryIdMap.count || 0) + 1;
                  countInvoentoryIdMap.inventoryIds =
                    countInvoentoryIdMap.inventoryIds || [];
                  if (
                    !countInvoentoryIdMap.inventoryIds.includes(
                      inventorySchedule.inventoryId,
                    )
                  ) {
                    countInvoentoryIdMap.inventoryIds.push(
                      inventorySchedule.inventoryId,
                    );
                  }
                  acc[dateHourKey] = countInvoentoryIdMap;
                });
              },
            );
          });
          return acc; // <-- important
        },
        {}, // <-- initial value
      );
    },
    [selectedHours],
  );

  const overlappingMap = useMemo((): Record<
    string,
    {
      count: number;
      inventoryIds: string[];
    }
  > => {
    if (scheduleGridMode === "deselect") {
      return {};
    }
    const schedules = inventorySchedules.filter((inventorySchedule) => {
      return inventoriesSelectedForSchedules.includes(
        inventorySchedule.inventoryId,
      );
    });
    return getOverlapMap(schedules);
  }, [inventorySchedules, inventoriesSelectedForSchedules, getOverlapMap]);

  const overlappingInventoryMap = useMemo((): Record<string, boolean> => {
    const mapOverlappingInventory: Record<string, boolean> = {};
    Object.values(overlappingMap).forEach((overlapMap) => {
      if (overlapMap.count > 1) {
        overlapMap.inventoryIds.forEach((inventoryId) => {
          if (inventoriesSelectedForSchedules.includes(inventoryId)) {
            mapOverlappingInventory[inventoryId] = true;
          }
        });
      }
    });
    return mapOverlappingInventory;
  }, [overlappingMap, inventoriesSelectedForSchedules]);

  const scheduleerrorInventoryMap = useMemo((): Record<string, boolean> => {
    // Classic inventories have no hourly grid (date range only), so hours
    // outside operation time can't apply to them — same gate ScheduleDrawer
    // uses (`inventoryType === InventoryClassification.DIGITAL`) before
    // calling hasHoursOutsideOperationTime. Without it, a Classic item's
    // empty/absent operatingTimes always falls outside whatever hours the
    // shared bulk grid has selected, flagging it as an error it can't fix.
    const inventoryData = filteredInventories.filter(
      (inventory) =>
        inventoriesSelectedForSchedules.includes(inventory.detail.id) &&
        inventory.detail.inventoryType === InventoryClassification.DIGITAL,
    );
    const mapOverlappingInventory: Record<string, boolean> = {};
    inventoryData.forEach((inventory) => {
      selectedHours.forEach((key) => {
        const dayHour = key.split("-");
        const hour = Number(dayHour[dayHour.length - 1]);
        const day = new Date(`${dayHour[0]}-${dayHour[1]}-${dayHour[2]}`);
        const dayName =
          MAPPING_WEEKDAYS_NAMES[day.getDay() === 0 ? 6 : day.getDay() - 1];

        const operationDays = Object.keys(
          inventory.operations?.operatingTimes || {},
        );
        // Check if day is in operation days
        if (!operationDays.includes(dayName)) {
          mapOverlappingInventory[inventory.detail.id] = true;
          return;
        }

        // Get day-wise operation times
        const dayOperationTimes = getDayOperationTimes(
          inventory.operations,
          dayName,
        );
        if (!dayOperationTimes) {
          // If no operation times found, mark as error
          mapOverlappingInventory[inventory.detail.id] = true;
          return;
        }

        // Extract hour from time strings (format: "HH:mm:ss" or "HH:mm")
        const startHour = Number(dayOperationTimes.startTime.split(":")[0]);
        const endHour = Number(dayOperationTimes.endTime.split(":")[0]);

        // Check if hour is within operation time range
        if (!(hour >= startHour && hour <= endHour)) {
          mapOverlappingInventory[inventory.detail.id] = true;
        }
      });
    });
    return mapOverlappingInventory;
  }, [selectedHours, filteredInventories, inventoriesSelectedForSchedules]);

  // Per-row visual map (Digital only) for inventories whose scheduled hours
  // don't meet their selling term's minimum required hours per day.
  const minHoursErrorInventoryMap = useMemo((): Record<string, boolean> => {
    const map: Record<string, boolean> = {};
    filteredInventories.forEach((inventory) => {
      if (
        inventoriesSelectedForSchedules.includes(inventory.detail.id) &&
        inventory.detail.inventoryType === InventoryClassification.DIGITAL &&
        hasAnyDayBelowMinHours(
          selectedHours,
          inventory.detail.sellingTerm?.minHours,
        )
      ) {
        map[inventory.detail.id] = true;
      }
    });
    return map;
  }, [filteredInventories, inventoriesSelectedForSchedules, selectedHours]);

  // Computed against the full selected-for-schedules list (not the
  // search-filtered one) so the Save button stays disabled even if the
  // violating inventory is currently hidden behind a search query.
  const hasMinHoursViolation = useMemo(
    () =>
      selectedForSchedulesItems.some(
        (item) =>
          item.detail.inventoryType === InventoryClassification.DIGITAL &&
          hasAnyDayBelowMinHours(
            selectedHours,
            item.detail.sellingTerm?.minHours,
          ),
      ),
    [selectedForSchedulesItems, selectedHours],
  );

  const scheduleGridColoring = useMemo(() => {
    const min = 1;
    let max = 1;
    if (!hasDeselectAllInteractionRef.current) {
      Object.values(overlappingMap).forEach((overlapMap) => {
        if (overlapMap.count > max) {
          max = overlapMap.count;
        }
      });
    }
    if (min === max) {
      return {};
    } else {
      const colorMap: Record<string, string> = {};
      Object.entries(overlappingMap).forEach(([bookingKey, overlapMap]) => {
        const minPer = parseInt("" + (1 / max) * 100);
        const colorPerSharePer = parseInt("" + (100 - minPer) / 5);
        const per = (overlapMap.count / max) * 100;
        let colorClass = "";
        if (per <= minPer) {
          colorClass = "bg-mw-primary-100!";
        } else if (per > minPer && per <= minPer + colorPerSharePer) {
          colorClass = "bg-mw-primary-200!";
        } else if (
          per > minPer + colorPerSharePer &&
          per <= minPer + colorPerSharePer * 2
        ) {
          colorClass = "bg-mw-primary-300!";
        } else if (
          per < minPer + colorPerSharePer * 2 &&
          per <= minPer + colorPerSharePer * 3
        ) {
          colorClass = "bg-mw-primary-400!";
        } else if (
          per < minPer + colorPerSharePer * 3 &&
          per <= minPer + colorPerSharePer * 4
        ) {
          colorClass = "bg-mw-primary-500!";
        } else {
          colorClass = "bg-mw-primary-600!";
        }
        colorMap[bookingKey] = colorClass;
      });
      return colorMap;
    }
  }, [overlappingMap]);

  if (isLoading) {
    return (
      <ModalDrawer
        isOpen={isOpen}
        onClose={onClose}
        title={tCampaigns("optimization.optimizeManuallyDrawer.title")}
        size="custom"
        customWidth="87vw"
      >
        <Loading></Loading>
      </ModalDrawer>
    );
  }

  const handleInventoryClick = (item: InventoryItem, checked: boolean) => {
    const itemId = item.detail.id;
    setScheduleGridMode("custom");
    setInventoriesSelectedForSchedules((prev) => {
      if (prev.includes(itemId)) {
        return prev.filter((id) => id !== itemId);
      } else {
        return [...prev, itemId];
      }
    });
    if (checked) {
      fecthInventorySchedule(itemId);
    }
  };

  const fecthInventorySchedule = async (inventoryIds: string | string[]) => {
    let inventoriesId = [];
    if (Array.isArray(inventoryIds)) {
      inventoriesId = inventoryIds;
    } else {
      inventoriesId = [inventoryIds];
    }
    const getInventorieScheduleIds = inventoriesId.filter((inventoryId) => {
      return !inventorySchedules.some(
        (inventorySchedule) => inventoryId === inventorySchedule.inventoryId,
      );
    });
    if (getInventorieScheduleIds.length) {
      const scheduleResponse = await fetchSelectedInventorySchedules({
        campaignId,
        inventories: getInventorieScheduleIds,
      }).unwrap();
      if (scheduleResponse.success && scheduleResponse.data?.length) {
        const data = scheduleResponse.data || [];
        setInventorySchedules((prev) => [...prev, ...data]);
      }
    }
  };

  // Wire the on-open setup ref now that its handlers are defined (see the
  // load effect above). Runs each render so the ref always holds the latest.
  runInitialScheduleSetupRef.current = (newIds: string[]) => {
    fecthInventorySchedule(newIds);
    handleSelectAllDays();
    handleSelectAllGrid();
  };

  const handleAllInventoryClick = (selectAll: boolean) => {
    setScheduleGridMode("custom");
    if (selectAll) {
      const allIds = filteredInventories.map((item) => item.detail.id);
      const newIds = [...inventoriesSelectedForSchedules];
      allIds.forEach((itemId) => {
        if (!newIds.includes(itemId)) {
          newIds.push(itemId);
        }
      });
      setInventoriesSelectedForSchedules(newIds);
      fecthInventorySchedule(newIds);
    } else {
      setInventoriesSelectedForSchedules(() => {
        const filteredIds = filteredInventories.map((item) => item.detail.id);
        return inventoriesSelectedForSchedules.filter(
          (id) => !filteredIds.includes(id),
        );
      });
    }
  };

  // Handle pattern selection
  const handlePatternChange = (pattern: SchedulePattern) => {
    setSelectedPattern(pattern);
    setScheduleGridMode("custom");
    if (pattern === "custom") {
      // Custom - don't change anything, let user select manually
      isApplyingPatternRef.current = false;
      // Don't reset manual interaction flag - let user continue their manual selection
      return;
    }

    // When selecting a pattern, reset manual interaction flag
    hasManualInteractionRef.current = false;

    // Check if schedule dates are set
    if (!scheduleDate.from || !scheduleDate.to) {
      showWarning(tCampaigns("scheduleDrawer.selectScheduleDatesFirst"));
      return;
    }

    // Apply the selected pattern
    isApplyingPatternRef.current = true;
    const { days, hours } = applySchedulePattern(
      pattern,
      scheduleDate,
      DAYS_OF_WEEK,
    );

    // Clear previous selection and set new one
    setSelectedDays(days);
    setSelectedHours(new Set(hours)); // Create new Set to ensure proper update

    // Reset flag after a longer delay to ensure useEffect doesn't override
    setTimeout(() => {
      isApplyingPatternRef.current = false;
    }, 500);
  };

  const footer = (
    <div className="flex flex-col gap-2 w-full">
      {hasMinHoursViolation && (
        <p
          id="optimize-manually-drawer-min-hours-error"
          className="text-xs text-mw-error-500"
        >
          {tCampaigns("optimization.optimizeManuallyDrawer.minHoursViolation")}
        </p>
      )}
      <div className="flex items-center justify-between w-full">
        <Checkbox
          id="optimize-manually-drawer-clear-previous-schedule"
          checked={clearPreviousSchedule}
          onChange={(e) => setClearPreviousSchedule(e.target.checked)}
          label={tCampaigns(
            "optimization.optimizeManuallyDrawer.clearPreviousSchedule",
          )}
        />
        <div className="flex gap-2">
          <Button
            id="optimize-manually-drawer-cancel-btn"
            variant="outline"
            onClick={onClose}
          >
            {tCampaigns("optimization.optimizeManuallyDrawer.cancel")}
          </Button>
          <Button
            id="optimize-manually-drawer-save-btn"
            variant="primary"
            onClick={handleScheduleSave}
            disabled={isSaving || hasMinHoursViolation}
          >
            {isSaving
              ? tCampaigns("optimization.optimizeManuallyDrawer.saving")
              : tCampaigns("optimization.optimizeManuallyDrawer.saveChanges")}
          </Button>
        </div>
      </div>
    </div>
  );

  return (
    <>
      <ModalDrawer
        isOpen={isOpen}
        onClose={onClose}
        title={tCampaigns("optimization.optimizeManuallyDrawer.title")}
        size="custom"
        customWidth="87vw"
        footer={footer}
        showBackButton={false}
      >
        <div className="flex gap-6 h-full">
          {/* Left Card - Selected Inventories (Bigger) */}
          <div className="flex-1 overflow-hidden">
            <Card className="h-full flex flex-col">
              <CardHeader className="pb-4">
                <CardTitle className="py-4 mx-4 border-b border-mw-neutral-100 text-sm font-medium text-mw-neutral-700">
                  {tCampaigns(
                    "optimization.optimizeManuallyDrawer.selectedInventories",
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent className="p-0! flex-1 flex flex-col overflow-hidden">
                <div className="relative mx-4">
                  <Input
                    id="optimize-manually-drawer-search-input"
                    placeholder={tCampaigns(
                      "optimization.optimizeManuallyDrawer.searchInventoryPlaceholder",
                    )}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pr-10"
                  />
                  <Search className="absolute right-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-mw-neutral-400 pointer-events-none" />
                </div>
                {filteredInventories.length > 0 && (
                  <>
                    <div className="px-6 py-4 flex">
                      <Checkbox
                        checked={selectAllInventory}
                        isIndeterminate={
                          inventoriesSelectedForSchedules.length > 0 &&
                          inventoriesSelectedForSchedules.length <
                            filteredInventories.length
                        }
                        onChange={(e) =>
                          handleAllInventoryClick(e.target.checked)
                        }
                        label={tCampaigns("inventoryPageForm.selectAll")}
                        id="checkbox-select-all-inventories"
                      ></Checkbox>
                      <Badge
                        className="ml-2 rounded-xl"
                        variant="secondary"
                        size="sm"
                      >
                        {inventoriesSelectedForSchedules.length} /{" "}
                        {filteredInventories.length}
                      </Badge>
                    </div>
                    <div className="flex-1 overflow-hidden">
                      <div className="h-full overflow-y-auto scrollbar-thin space-y-2 p-4 pt-0">
                        {filteredInventories.map(
                          (
                            filteredInventory: InventoryItem,
                            inventoryIndex: number,
                          ) => {
                            const isSelected =
                              inventoriesSelectedForSchedules.includes(
                                filteredInventory.detail.id,
                              );
                            const hasError =
                              scheduleerrorInventoryMap[
                                filteredInventory.detail.id
                              ];
                            const hasOverlap =
                              overlappingInventoryMap[
                                filteredInventory.detail.id
                              ];
                            const hasMinHoursError =
                              minHoursErrorInventoryMap[
                                filteredInventory.detail.id
                              ];

                            // Get operation times for tooltip
                            const operationTimes = filteredInventory.operations
                              ?.operatingTimes
                              ? extractOperationTimes(
                                  filteredInventory.operations.operatingTimes,
                                )
                              : filteredInventory.operations?.startTime &&
                                  filteredInventory.operations?.endTime
                                ? {
                                    startTime:
                                      filteredInventory.operations.startTime,
                                    endTime:
                                      filteredInventory.operations.endTime,
                                  }
                                : null;

                            const alertTooltipContent = hasMinHoursError
                              ? tCampaigns(
                                  "optimization.optimizeManuallyDrawer.minHoursViolationTooltip",
                                  {
                                    minHours:
                                      filteredInventory.detail.sellingTerm
                                        ?.minHours,
                                  },
                                )
                              : hasError
                                ? operationTimes
                                  ? tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleErrorPart1",
                                    ) +
                                    formatTime(operationTimes.startTime) +
                                    tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleErrorTo",
                                    ) +
                                    formatTime(operationTimes.endTime) +
                                    "." +
                                    tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleErrorPart2",
                                    )
                                  : tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleErrorPart1",
                                    ) +
                                    tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleErrorPart2",
                                    )
                                : hasOverlap
                                  ? tCampaigns(
                                      "optimization.optimizeManuallyDrawer.scheduleWarning",
                                    )
                                  : null;

                            return (
                              <InventoryCard
                                key={filteredInventory.detail.id}
                                item={filteredInventory}
                                showCheckbox={true}
                                checkboxId={`checkbox-${inventoryIndex}`}
                                checkboxChecked={isSelected}
                                onCheckboxChange={(checked) =>
                                  handleInventoryClick(
                                    filteredInventory,
                                    checked,
                                  )
                                }
                                headerClassName={clsx(
                                  "p-2 rounded-sm",
                                  isSelected
                                    ? "bg-mw-primary-50 border border-mw-primary-500"
                                    : "",
                                  hasOverlap
                                    ? "bg-mw-warning-50 border border-mw-warning-500"
                                    : "",
                                  hasError || hasMinHoursError
                                    ? "bg-mw-error-50! border border-mw-error-500!"
                                    : "",
                                )}
                                showAlertIcon={
                                  hasError || hasOverlap || hasMinHoursError
                                }
                                alertTooltipContent={
                                  alertTooltipContent ? (
                                    <div className="max-w-[268px] text-wrap">
                                      {alertTooltipContent}
                                    </div>
                                  ) : undefined
                                }
                                alertIconClassName={clsx(
                                  hasError || hasMinHoursError
                                    ? "text-mw-error-500"
                                    : hasOverlap
                                      ? "text-mw-warning-500"
                                      : "",
                                )}
                              />
                            );
                          },
                        )}
                      </div>
                    </div>
                  </>
                )}
                {filteredInventories.length === 0 && (
                  <p className="text-sm text-mw-neutral-400">
                    {tCampaigns(
                      "optimization.configureScheduling.noInventories",
                    )}
                  </p>
                )}
              </CardContent>
            </Card>
          </div>
          {/* Right Card - Schedule Configuration */}
          <div className="w-[70%]">
            <Card className="p-4 h-full flex flex-col">
              <CardHeader className="pb-3 border-b border-mw-neutral-100">
                <CardTitle className="text-sm font-medium text-mw-neutral-700">
                  {tCampaigns(
                    "optimization.optimizeManuallyDrawer.optimizeSchedule",
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent className="p-0! pt-4! flex-1 overflow-hidden flex flex-col">
                <div className="space-y-6 h-full flex flex-col">
                  {/* Schedule Date and Select Days - Side by Side (Select
                  Days is a digital-only concept — Classic-only selections
                  only need the date range) */}
                  <div
                    className={
                      allSelectedAreClassic
                        ? "grid grid-cols-1 gap-4"
                        : "grid grid-cols-2 gap-4"
                    }
                  >
                    <div>
                      <DateRangePicker
                        id="optimize-manually-drawer-date-picker"
                        label={tCampaigns(
                          "optimization.optimizeManuallyDrawer.scheduleDate",
                        )}
                        value={scheduleDate}
                        minDate={campaignStartDate}
                        maxDate={campaignEndDate}
                        onChange={(range) => {
                          console.log("Date Range Selected:", range);
                          setScheduleGridMode("custom");
                          setScheduleDate(range);
                        }}
                        onBlur={() => {
                          // A single click only sets `from` — closing the
                          // picker without a second click used to leave `to`
                          // null, hiding the hourly grid entirely. Treat that
                          // as a single-day selection instead.
                          if (scheduleDate.from && !scheduleDate.to) {
                            setScheduleDate({
                              from: scheduleDate.from,
                              to: scheduleDate.from,
                            });
                          }
                        }}
                        placeholder={tCampaigns(
                          "optimizeManuallySelectDateRange",
                        )}
                        format="dd MMM yyyy"
                        clearable={false}
                        numberOfMonths={2}
                      />
                    </div>
                    {!allSelectedAreClassic && (
                      <div className="space-y-2 -mt-1">
                        <div className="flex items-center justify-between gap-3">
                          <Label>
                            {tCampaigns(
                              "optimization.optimizeManuallyDrawer.dayScheduleTitle",
                            )}{" "}
                            <Tooltip
                              content={tCampaigns(
                                "scheduleDrawerSelectAllDaysTooltip",
                              )}
                            >
                              <Info className="mt-1 w-4 h-4 text-mw-neutral-300" />
                            </Tooltip>
                          </Label>
                          <Checkbox
                            id="optimize-manually-drawer-select-all-days"
                            checked={
                              selectedDays.length === DAYS_OF_WEEK.length
                            }
                            isIndeterminate={
                              selectedDays.length > 0 &&
                              selectedDays.length < DAYS_OF_WEEK.length
                            }
                            onChange={(e) => {
                              if (e.target.checked) {
                                handleSelectAllDays();
                              } else {
                                setSelectedDays([]);
                                setSelectedHours(new Set());
                                setScheduleGridMode("custom");
                              }
                            }}
                            label={tCampaigns(
                              "optimization.optimizeManuallyDrawer.selectAllDays",
                            )}
                          />
                        </div>
                        <div className="flex gap-2 flex-wrap">
                          {DAYS_OF_WEEK.map((day) => (
                            <button
                              key={day}
                              id={`optimize-manually-drawer-day-${day.toLowerCase()}`}
                              type="button"
                              onClick={() => handleDayToggle(day)}
                              className={`
                            p-2 rounded-md text-sm font-medium transition-colors
                            ${
                              selectedDays.includes(day)
                                ? "bg-mw-primary-500 text-white"
                                : "bg-mw-neutral-50 text-black hover:bg-mw-neutral-100"
                            }
                          `}
                            >
                              {day}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                  {/* Schedule Grids — digital-only (hourly booking matrix,
                  patterns); Classic-only selections have nothing to show here */}
                  {!allSelectedAreClassic && (
                    <div className="space-y-2 flex-1 overflow-hidden flex flex-col">
                      <Label>
                        {tCampaigns(
                          "optimization.optimizeManuallyDrawer.scheduleGrids",
                        )}
                      </Label>
                      <div className="flex items-center justify-between gap-4">
                        <div className="flex-1 flex items-center gap-3">
                          <Radio
                            id="optimize-manually-drawer-select-all-grid"
                            name="grid-mode"
                            checked={scheduleGridMode === "select"}
                            onChange={() => handleSelectAllGrid()}
                            label={tCampaigns(
                              "optimization.optimizeManuallyDrawer.selectAll",
                            )}
                            className="mt-1!"
                            disabled={selectedDays.length === 0}
                          />
                          <Radio
                            id="optimize-manually-drawer-deselect-all-grid"
                            name="grid-mode"
                            checked={scheduleGridMode === "deselect"}
                            onChange={() => handleDeselectAllGrid()}
                            label={tCampaigns(
                              "optimization.optimizeManuallyDrawer.deselectAll",
                            )}
                            className="mt-1!"
                            disabled={selectedDays.length === 0}
                          />
                        </div>
                        <div className="flex-1 flex items-center justify-end">
                          <Dropdown name="optimize-manually-drawer-pattern">
                            <DropdownTrigger className="w-full justify-between">
                              {tCampaigns(
                                `scheduleDrawer.schedulePatterns.${selectedPattern}`,
                              ) || tCampaigns("scheduleDrawer.customPattern")}
                            </DropdownTrigger>
                            <DropdownContent align="right">
                              {SCHEDULE_PATTERNS.filter(
                                // "Default" restores a single inventory's default
                                // schedule; not applicable to bulk optimize.
                                (pattern) => pattern.value !== "default",
                              ).map((pattern) => (
                                <DropdownItem
                                  key={pattern.value}
                                  value={pattern.value}
                                  onClick={() =>
                                    handlePatternChange(pattern.value)
                                  }
                                >
                                  {tCampaigns(
                                    `scheduleDrawer.schedulePatterns.${pattern.value}`,
                                  )}
                                </DropdownItem>
                              ))}
                            </DropdownContent>
                          </Dropdown>
                        </div>
                      </div>
                      {hasClassicSelectedForSchedules && (
                        <div className="text-xs text-mw-neutral-500 bg-mw-neutral-50 border border-mw-neutral-100 rounded-lg p-2">
                          {tCampaigns(
                            "optimization.optimizeManuallyDrawer.classicHourlyNotApplicable",
                          )}
                        </div>
                      )}
                      {scheduleDate.from && scheduleDate.to && (
                        <div className="flex-1 overflow-hidden">
                          <ScheduleGrid
                            startDate={scheduleDate.from}
                            endDate={scheduleDate.to}
                            selectedDays={selectedDays}
                            selectedHours={selectedHours}
                            onCellClick={handleCellClick}
                            onRowClick={handleRowClick}
                            onColumnClick={handleColumnClick}
                            className="h-full overflow-y-auto"
                            cellClassName={scheduleGridColoring}
                          />
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </ModalDrawer>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isSaveModalOpen}
        onClose={() => setIsSaveModalOpen(false)}
        title={tCampaigns(
          "optimization.optimizeManuallyDrawer.saveChangesTitle",
        )}
        primaryButtonText={tCampaigns(
          "optimization.optimizeManuallyDrawer.saveChangesPrimaryButtonLabel",
        )}
        onPrimaryAction={handleSave}
        onSecondaryAction={() => setIsSaveModalOpen(false)}
        size="md"
      >
        <p>
          {tCampaigns(
            "optimization.optimizeManuallyDrawer.saveChangesBodeyText",
          ) +
            inventoriesSelectedForSchedules.length +
            " " +
            tCampaigns("create_campaign.steps.inventories")}
        </p>
      </Modal>
    </>
  );
};
