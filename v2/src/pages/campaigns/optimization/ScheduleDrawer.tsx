import { InventoryCard } from "@components/common/InventoryCard";
import { ScheduleGrid } from "@components/common/ScheduleGrid";
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
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Radio } from "@components/ui/Radio";
import { Tooltip } from "@components/ui/Tooltip";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useAddInventorySchedulesMutation,
  useUpdateInventorySchedulesMutation,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import { fromAPIDateString, getEndOfDay } from "@utils/dateUtils";
import {
  addHoursForNewlyAddedDays,
  applySchedulePattern,
  convertDayName,
  convertDayNameBack,
  DAYS_OF_WEEK,
  DURATION_OPTIONS,
  formatDateToYYYYMMDD,
  getMondayBasedDay,
  hasAnyDayBelowMinHours,
  hasHoursOutsideOperationTime,
  parseDateFromBookingMatrix,
  SchedulePattern,
  SCHEDULE_PATTERNS,
  dayString,
} from "@utils/schedule.utils";
import {
  captureDefaultSchedule,
  getDefaultSchedule,
  mapScheduleToFormState,
  resolveSchedulePattern,
} from "@utils/scheduleDefaults";
import { Info, Minus, Plus, Search } from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  startTransition,
} from "react";
import { InventoryClassification } from "src/constants/inventory.constants";
import { InventorySchedulePayload } from "src/types/inventory.types";

import { ScheduleDrawerProps } from "./types/schedule.types";

export const ScheduleDrawer: React.FC<ScheduleDrawerProps> = ({
  isOpen,
  onClose,
  selectedInventory,
  inventorySchedules,
  scheduleId,
  campaignStartDate,
  campaignEndDate,
  campaignId,
  onScheduleSaved,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { showWarning, showError, showSuccess } = useAnnounce();
  const [updateSchedules, { isLoading: isUpdating }] =
    useUpdateInventorySchedulesMutation();
  const [addSchedules, { isLoading: isAdding }] =
    useAddInventorySchedulesMutation();
  const isSaving = isUpdating || isAdding;
  const [scheduleDate, setScheduleDate] = useState<{
    from: Date | null | undefined;
    to: Date | null | undefined;
  }>({
    from: null,
    to: null,
  });
  const [selectedDays, setSelectedDays] = useState<string[]>([]);
  const [duration, setDuration] = useState<string>("15 Sec");
  const [spots, setSpots] = useState<{ perLoop: number; perHour: number }>({
    perLoop: 1,
    perHour: 60,
  });
  const spotsPerLoop = spots.perLoop;
  const spotsPerHour = spots.perHour;
  const [selectedHours, setSelectedHours] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [clearPreviousSchedule, setClearPreviousSchedule] =
    useState<boolean>(false);
  const [scheduleGridMode, setScheduleGridMode] = useState<
    "select" | "deselect" | "custom"
  >("custom");
  const [selectedPattern, setSelectedPattern] =
    useState<SchedulePattern>("custom");
  // Whether a persisted "default" snapshot exists for the opened schedule.
  const [hasDefault, setHasDefault] = useState<boolean>(false);
  const isApplyingPatternRef = useRef(false); // Prevent pattern detection when applying pattern
  const hasManualInteractionRef = useRef(false); // Track if user has manually interacted with cells
  const initialLoopSize = selectedInventory?.operations?.loopSize || 60;
  const initialSpotsPerHour = 3600 / initialLoopSize;

  // Get available days from inventory operations
  const availableDays = useMemo(() => {
    if (!selectedInventory?.operations?.operationDays) {
      return DAYS_OF_WEEK;
    }
    return selectedInventory.operations.operationDays.map(convertDayName);
  }, [selectedInventory]);

  // Initialize form from existing schedule data - only when edit button is clicked
  useEffect(() => {
    if (!isOpen) {
      // Reset when drawer closes
      setSelectedPattern("custom");
      setSelectedDays([]);
      setClearPreviousSchedule(false);
      setSpots({ perLoop: 1, perHour: initialSpotsPerHour });
      setHasDefault(false);
      return;
    }

    // Capture the single default schedule for this inventory (the first saved
    // schedule, order 1) once, and expose it via "Restore Default" from any
    // schedule's edit drawer. Captured once so later edits never overwrite the
    // original first-saved values.
    const inventoryId = selectedInventory?.detail.id;
    const defaultSchedule = inventorySchedules.find((s) => s.order === 1);
    if (inventoryId && defaultSchedule) {
      captureDefaultSchedule(campaignId, inventoryId, defaultSchedule);
      setHasDefault(true);
    } else {
      setHasDefault(false);
    }

    if (scheduleId && inventorySchedules.length > 0) {
      // Edit mode - load existing schedule data
      const schedule = inventorySchedules.find((s) => s.id === scheduleId);
      if (schedule) {
        // Reflect the schedule's shape in the pattern dropdown on open:
        // default snapshot match → known generated pattern → custom.
        setSelectedPattern(
          resolveSchedulePattern(
            schedule,
            getDefaultSchedule(campaignId, inventoryId),
            availableDays,
            initialSpotsPerHour,
          ),
        );

        // Set schedule dates
        if (schedule.startDate && schedule.endDate) {
          setScheduleDate({
            from: new Date(schedule.startDate),
            to: new Date(schedule.endDate),
          });
        }

        // Set selected days (convert from "MONDAY" to "Mon")
        if (schedule.scheduleDays) {
          const convertedDays = schedule.scheduleDays.map(convertDayName);
          setSelectedDays(convertedDays);
        }

        // Set duration (convert from seconds to "15 Sec" format)
        if (schedule.duration) {
          setDuration(`${schedule.duration} Sec`);
        }

        // Set spots per loop and spots per hour
        if (
          schedule.spotsPerLoop !== undefined ||
          schedule.spotsPerHour !== undefined
        ) {
          setSpots({
            perLoop: schedule.spotsPerLoop ?? 1,
            perHour: schedule.spotsPerHour ?? initialSpotsPerHour,
          });
        }

        // Convert bookingMatrix to selectedHours Set
        if (schedule.bookingMatrix) {
          const hoursSet = new Set<string>();
          Object.entries(schedule.bookingMatrix).forEach(([dateStr, hours]) => {
            const date = parseDateFromBookingMatrix(dateStr);
            hours.forEach((hour) => {
              const dateKey = formatDateToYYYYMMDD(date);
              hoursSet.add(`${dateKey}-${hour}`);
            });
          });
          setSelectedHours(hoursSet);
        }
      }
    } else {
      // New schedule - reset to blank/defaults
      // Set default dates based on campaign dates if available
      let defaultFrom: Date;
      let defaultTo: Date;

      if (campaignStartDate && campaignEndDate) {
        defaultFrom = new Date(campaignStartDate);
        defaultTo = new Date(campaignEndDate);
      } else {
        const today = new Date();
        defaultFrom = today;
        defaultTo = new Date(today);
        defaultTo.setDate(today.getDate() + 14);
      }

      setScheduleDate({
        from: defaultFrom,
        to: defaultTo,
      });
      setSelectedDays([]);
      setDuration("15 Sec");
      setSpots({ perLoop: 1, perHour: initialSpotsPerHour });
      setSelectedHours(new Set());
      setSelectedPattern("custom");
      hasManualInteractionRef.current = false; // Reset on new schedule
    }
  }, [
    isOpen,
    inventorySchedules,
    scheduleId,
    campaignId,
    campaignStartDate,
    campaignEndDate,
    selectedInventory,
    availableDays,
    initialSpotsPerHour,
  ]);

  const inventoryType = selectedInventory?.detail.inventoryType || "";

  // Initialize all hours as selected when dates are set (only for new schedules, not edit mode)
  // Skip this if a pattern is being applied or if pattern is not custom
  useEffect(() => {
    if (
      !scheduleId &&
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
          const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
          if (selectedDays.includes(dayName)) {
            for (let hour = 0; hour < 24; hour++) {
              const dateStr = formatDateToYYYYMMDD(current);
              newSelectedHours.add(`${dateStr}-${hour}`);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        setSelectedHours((prev) =>
          prev.size !== newSelectedHours.size ||
          [...prev].some((k) => !newSelectedHours.has(k))
            ? newSelectedHours
            : prev,
        );
      } else if (!selectedDays.length) {
        setSelectedHours((prev) => (prev.size === 0 ? prev : new Set()));
      } else if (
        hasManualInteractionRef.current &&
        selectedDays.length &&
        selectedHours.size
      ) {
        // Start with a copy of current selectedHours
        const newSelectedHours = new Set(selectedHours);
        while (current <= end) {
          const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
          const dateStr = formatDateToYYYYMMDD(current);

          if (!selectedDays.includes(dayName)) {
            // Remove all hours for days not in selectedDays
            for (let hour = 0; hour < 24; hour++) {
              newSelectedHours.delete(`${dateStr}-${hour}`);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        setSelectedHours((prev) =>
          prev.size !== newSelectedHours.size ||
          [...prev].some((k) => !newSelectedHours.has(k))
            ? newSelectedHours
            : prev,
        );
      }
    }
  }, [
    scheduleDate.from,
    scheduleDate.to,
    selectedDays,
    scheduleId,
    selectedPattern,
    selectedHours,
  ]);

  const handleDayToggle = (day: string) => {
    if (!availableDays.includes(day)) {
      return;
    }
    hasManualInteractionRef.current = true;
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    const isDeselecting = selectedDays.includes(day);
    setSelectedDays((prev) =>
      isDeselecting
        ? prev.filter((d) => d !== day)
        : [...prev, day].sort(
            (a, b) => DAYS_OF_WEEK.indexOf(a) - DAYS_OF_WEEK.indexOf(b),
          ),
    );
    if (isDeselecting && scheduleDate.from && scheduleDate.to) {
      setSelectedHours((prev) => {
        const newSet = new Set(prev);
        const current = new Date(scheduleDate.from!);
        current.setHours(0, 0, 0, 0);
        const end = new Date(scheduleDate.to!);
        end.setHours(23, 59, 59, 999);
        while (current <= end) {
          if (DAYS_OF_WEEK[getMondayBasedDay(current)] === day) {
            const dateStr = formatDateToYYYYMMDD(current);
            for (let hour = 0; hour < 24; hour++) {
              newSet.delete(`${dateStr}-${hour}`);
            }
          }
          current.setDate(current.getDate() + 1);
        }
        return newSet;
      });
    }
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

  const handleCellClick = useCallback(
    (date: Date, hour: number) => {
      // Mark that user has manually interacted
      hasManualInteractionRef.current = true;

      // If manually changing hours, switch to custom pattern
      if (!isApplyingPatternRef.current) {
        setSelectedPattern("custom");
      }
      const dateStr = formatDateToYYYYMMDD(date);
      const hourKey = `${dateStr}-${hour}`;
      setSelectedHours((prev) => {
        const newSet = new Set(prev);
        const isDeselecting = newSet.has(hourKey);
        if (isDeselecting) {
          newSet.delete(hourKey);
          // If "Select All" is active and user deselects, switch to custom
          if (scheduleGridMode === "select") {
            setScheduleGridMode("custom");
          }
        } else {
          newSet.add(hourKey);
          // If "Deselect All" is active and user selects, switch to custom
          if (scheduleGridMode === "deselect") {
            setScheduleGridMode("custom");
          }
        }
        return newSet;
      });
    },
    [scheduleGridMode],
  );

  const handleRowClick = useCallback(
    (date: Date) => {
      // Mark that user has manually interacted
      hasManualInteractionRef.current = true;

      // If manually changing hours, switch to custom pattern
      if (!isApplyingPatternRef.current) {
        setSelectedPattern("custom");
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
        // Update radio mode based on action
        if (
          (allHoursSelected && scheduleGridMode === "select") ||
          (!allHoursSelected && scheduleGridMode === "deselect")
        ) {
          setScheduleGridMode("custom");
        }
        return newSet;
      });
    },
    [selectedHours, scheduleGridMode],
  );

  const handleColumnClick = useCallback(
    (hour: number) => {
      if (!scheduleDate.from || !scheduleDate.to) return;

      // Mark that user has manually interacted
      hasManualInteractionRef.current = true;

      // If manually changing hours, switch to custom pattern
      if (!isApplyingPatternRef.current) {
        setSelectedPattern("custom");
      }

      // Check if all selected days have this hour selected
      const current = new Date(scheduleDate.from);
      current.setHours(0, 0, 0, 0);
      const end = new Date(scheduleDate.to);
      end.setHours(23, 59, 59, 999);

      let allSelected = true;
      while (current <= end) {
        const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
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
          const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
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
        // Update radio mode based on action
        if (allSelected && scheduleGridMode === "select") {
          setScheduleGridMode("custom");
        } else if (!allSelected && scheduleGridMode === "deselect") {
          setScheduleGridMode("custom");
        }
        return newSet;
      });
    },
    [
      scheduleDate.from,
      scheduleDate.to,
      selectedDays,
      selectedHours,
      scheduleGridMode,
    ],
  );

  const handleSelectAllGrid = () => {
    if (!scheduleDate.from || !scheduleDate.to) return;
    // If manually changing hours, switch to custom pattern
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    const newSelectedHours = new Set<string>();
    const current = new Date(scheduleDate.from);
    current.setHours(0, 0, 0, 0);
    const end = new Date(scheduleDate.to);
    end.setHours(23, 59, 59, 999);

    while (current <= end) {
      const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
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
  };

  const handleDeselectAllGrid = () => {
    // Mark that user has manually interacted
    hasManualInteractionRef.current = true;

    // If manually changing hours, switch to custom pattern
    if (!isApplyingPatternRef.current) {
      setSelectedPattern("custom");
    }
    setSelectedHours(new Set());
    setScheduleGridMode("deselect");
  };

  // Handle pattern selection
  // Restore the currently-open drawer to the inventory's default schedule
  // (the first saved schedule, order 1).
  const handleRestoreDefault = () => {
    const inventoryId = selectedInventory?.detail.id;
    const defaultSchedule = getDefaultSchedule(campaignId, inventoryId);
    if (!defaultSchedule) return;
    const form = mapScheduleToFormState(defaultSchedule, initialSpotsPerHour);
    setScheduleDate(form.scheduleDate);
    setSelectedDays(form.selectedDays);
    setDuration(form.duration);
    setSpots(form.spots);
    setSelectedHours(form.selectedHours);
    // Restored hours are explicit; avoid auto-fill/pattern interference.
    hasManualInteractionRef.current = true;
    isApplyingPatternRef.current = false;
  };

  const handlePatternChange = (pattern: SchedulePattern) => {
    setSelectedPattern(pattern);
    if (pattern === "custom") {
      // Custom - don't change anything, let user select manually
      isApplyingPatternRef.current = false;
      // Don't reset manual interaction flag - let user continue their manual selection
      return;
    }

    // Default - load the inventory's default schedule (order 1) values
    if (pattern === "default") {
      handleRestoreDefault();
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
      availableDays,
    );

    // Clear previous selection and set new one
    setSelectedDays(days);
    setSelectedHours(new Set(hours)); // Create new Set to ensure proper update

    // Reset flag after a longer delay to ensure useEffect doesn't override
    setTimeout(() => {
      isApplyingPatternRef.current = false;
    }, 500);
  };

  // Check for overlapping schedules - returns true if overlap found
  const checkOverlappingSchedules = useCallback(() => {
    if (!scheduleDate.from || !scheduleDate.to) {
      return false;
    }

    // For Digital type, require days and hours to be selected
    if (inventoryType === InventoryClassification.DIGITAL) {
      if (selectedDays.length === 0 || selectedHours.size === 0) {
        return false;
      }
    }

    // Check overlap with existing schedules (excluding current schedule if editing)
    const otherSchedules = inventorySchedules.filter(
      (schedule) => !scheduleId || schedule.id !== scheduleId,
    );

    for (const schedule of otherSchedules) {
      const scheduleStart = new Date(schedule.startDate);
      const scheduleEnd = new Date(schedule.endDate);
      const currentStart = new Date(scheduleDate.from!);
      const currentEnd = new Date(scheduleDate.to!);

      // Check if date ranges overlap
      if (currentStart <= scheduleEnd && currentEnd >= scheduleStart) {
        // For Classic type: only check date overlap
        if (inventoryType === InventoryClassification.CLASSIC) {
          return true; // Date overlap found for Classic
        }

        // For Digital type: check days and hours overlap
        if (inventoryType === InventoryClassification.DIGITAL) {
          // Convert selected days to backend format
          const scheduleDays = selectedDays.map(convertDayNameBack);

          // Check if days overlap
          if (schedule.scheduleDays.some((day) => scheduleDays.includes(day))) {
            // Build current booking matrix from selectedHours
            // hourKey format: "YYYY-MM-DD-hour" (already in yyyy-mm-dd format)
            const currentBookingMatrix: Record<string, number[]> = {};
            selectedHours.forEach((hourKey) => {
              const lastHyphenIndex = hourKey.lastIndexOf("-");
              const dateStr = hourKey.substring(0, lastHyphenIndex);
              const hourStr = hourKey.substring(lastHyphenIndex + 1);
              const hour = parseInt(hourStr, 10);

              if (!currentBookingMatrix[dateStr]) {
                currentBookingMatrix[dateStr] = [];
              }
              if (!currentBookingMatrix[dateStr].includes(hour)) {
                currentBookingMatrix[dateStr].push(hour);
              }
            });

            // Check for overlapping hours (same date and same hour)
            for (const [dateStr, hours] of Object.entries(
              currentBookingMatrix,
            )) {
              if (schedule.bookingMatrix[dateStr]) {
                const overlappingHours = hours.filter((hour) =>
                  schedule.bookingMatrix[dateStr].includes(hour),
                );
                if (overlappingHours.length > 0) {
                  return true; // Overlap found for Digital
                }
              }
            }
          }
        }
      }
    }

    return false;
  }, [
    scheduleDate,
    selectedDays,
    selectedHours,
    inventorySchedules,
    scheduleId,
    inventoryType,
  ]);

  const clientPerLoop = selectedInventory?.operations?.clientPerLoop ?? 1;
  const maxSpotsPerLoop = clientPerLoop;
  const maxSpotsPerHour = clientPerLoop * initialSpotsPerHour;

  const handleIncreaseSpotsPerLoop = useCallback(() => {
    startTransition(() => {
      setSpots((prev) => {
        if (prev.perLoop >= maxSpotsPerLoop) return prev;
        return {
          perLoop: prev.perLoop + 1,
          perHour: prev.perHour + initialSpotsPerHour,
        };
      });
    });
  }, [initialSpotsPerHour, maxSpotsPerLoop]);

  const handleDecreaseSpotsPerLoop = useCallback(() => {
    startTransition(() => {
      setSpots((prev) => {
        const newPerLoop = Math.max(1, prev.perLoop - 1);
        const newPerHour = Math.max(1, prev.perHour - initialSpotsPerHour);
        return { perLoop: newPerLoop, perHour: newPerHour };
      });
    });
  }, [initialSpotsPerHour]);

  const handleIncreaseSpotsPerHour = useCallback(() => {
    startTransition(() => {
      setSpots((prev) => {
        if (prev.perHour >= maxSpotsPerHour) return prev;
        const newPerHour = Math.min(
          maxSpotsPerHour,
          prev.perHour + initialSpotsPerHour,
        );
        const newPerLoop = Math.min(
          clientPerLoop,
          Math.floor(newPerHour / initialSpotsPerHour),
        );
        return { perLoop: newPerLoop, perHour: newPerHour };
      });
    });
  }, [initialSpotsPerHour, maxSpotsPerHour, clientPerLoop]);

  const handleDecreaseSpotsPerHour = useCallback(() => {
    startTransition(() => {
      setSpots((prev) => {
        const newPerHour = Math.max(1, prev.perHour - initialSpotsPerHour);
        const newPerLoop = Math.max(
          1,
          Math.floor(newPerHour / initialSpotsPerHour),
        );
        return { perLoop: newPerLoop, perHour: newPerHour };
      });
    });
  }, [initialSpotsPerHour]);

  // Validation helpers
  const validateScheduleDates = (): boolean => {
    if (inventoryType === InventoryClassification.DIGITAL) {
      if (!scheduleDate.from || !scheduleDate.to || selectedDays.length === 0) {
        showError(tCampaigns("scheduleDrawer.errors.fillRequiredFields"));
        return false;
      }
    }

    if (inventoryType === InventoryClassification.CLASSIC) {
      if (!scheduleDate.from || !scheduleDate.to) {
        showError(tCampaigns("scheduleDrawer.errors.selectScheduleDates"));
        return false;
      }
    }

    if (!scheduleDate.from || !scheduleDate.to) {
      showError(tCampaigns("scheduleDrawer.errors.selectScheduleDates"));
      return false;
    }

    if (
      selectedHours.size === 0 &&
      inventoryType === InventoryClassification.DIGITAL
    ) {
      showError(tCampaigns("scheduleDrawer.errors.selectAtLeastOneHour"));
      return false;
    }

    return true;
  };

  const validateCampaignDateRange = (): boolean => {
    if (!campaignStartDate || !campaignEndDate) {
      return true; // No campaign dates to validate against
    }

    const campaignStart = new Date(campaignStartDate);
    const campaignEnd = new Date(campaignEndDate);
    const scheduleStart = new Date(scheduleDate.from!);
    const scheduleEnd = new Date(scheduleDate.to!);

    // Reset time to compare dates only
    campaignStart.setHours(0, 0, 0, 0);
    campaignEnd.setHours(23, 59, 59, 999);
    scheduleStart.setHours(0, 0, 0, 0);
    scheduleEnd.setHours(23, 59, 59, 999);

    if (scheduleStart < campaignStart || scheduleEnd > campaignEnd) {
      showWarning(tCampaigns("scheduleDrawer.datesOutsideCampaignRange"));
      return false;
    }

    return true;
  };

  const calculateValidatedSpots = (): {
    spotsPerLoop: number;
    spotsPerHour: number;
  } => {
    const clientPerLoop = selectedInventory?.operations?.clientPerLoop;

    let finalSpotsPerLoop = spotsPerLoop;
    if (clientPerLoop && spotsPerLoop > clientPerLoop) {
      showWarning(
        tCampaigns("scheduleDrawer.spotsLoopCapped", { max: clientPerLoop }),
      );
      finalSpotsPerLoop = clientPerLoop;
    }

    let finalSpotsPerHour = spotsPerHour;
    if (clientPerLoop && initialSpotsPerHour) {
      const maxSpotsPerHour = clientPerLoop * initialSpotsPerHour;
      if (spotsPerHour > maxSpotsPerHour) {
        showWarning(
          tCampaigns("scheduleDrawer.spotsHourCapped", {
            max: maxSpotsPerHour,
          }),
        );
        finalSpotsPerHour = maxSpotsPerHour;
      }
    }

    return { spotsPerLoop: finalSpotsPerLoop, spotsPerHour: finalSpotsPerHour };
  };

  const validateAndFilterSelectedHours = (): Set<string> => {
    if (!scheduleDate.from || !scheduleDate.to) {
      return selectedHours;
    }

    const startDate = new Date(scheduleDate.from);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(scheduleDate.to);
    endDate.setHours(23, 59, 59, 999);

    const filteredHours = new Set<string>();

    selectedHours.forEach((hourKey) => {
      const lastHyphenIndex = hourKey.lastIndexOf("-");
      const dateStr = hourKey.substring(0, lastHyphenIndex);
      const [year, month, day] = dateStr.split("-").map(Number);
      const date = new Date(year, month - 1, day);
      date.setHours(0, 0, 0, 0);

      // Check if date is within the schedule date range
      if (date >= startDate && date <= endDate) {
        filteredHours.add(hourKey);
      }
    });

    return filteredHours;
  };

  const buildBookingMatrix = useCallback(
    (hoursToUse?: Set<string>): Record<string, number[]> => {
      const bookingMatrix: Record<string, number[]> = {};
      const hours = hoursToUse || selectedHours;

      hours.forEach((hourKey) => {
        const lastHyphenIndex = hourKey.lastIndexOf("-");
        const dateStr = hourKey.substring(0, lastHyphenIndex);
        const hourStr = hourKey.substring(lastHyphenIndex + 1);

        const [year, month, day] = dateStr.split("-").map(Number);
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

      // Sort hours for each date
      Object.keys(bookingMatrix).forEach((date) => {
        bookingMatrix[date].sort((a, b) => a - b);
      });

      return bookingMatrix;
    },
    [selectedHours],
  );

  const buildSchedulePayload = (
    order: number,
    name: string,
    pricing: number,
    hoursToUse?: Set<string>,
  ): InventorySchedulePayload => {
    const scheduleDays = selectedDays.map(convertDayNameBack);
    const durationInSeconds = parseInt(duration.replace(" Sec", ""), 10);
    const { spotsPerLoop, spotsPerHour } = calculateValidatedSpots();
    const bookingMatrix = buildBookingMatrix(hoursToUse);

    return {
      name,
      startDate: formatDateToYYYYMMDD(scheduleDate.from!),
      endDate: formatDateToYYYYMMDD(scheduleDate.to!),
      scheduleDays: scheduleDays as Array<dayString>,
      bookingMatrix,
      duration: durationInSeconds,
      spotsPerLoop,
      spotsPerHour,
      order,
      pricing,
      inventoryId: selectedInventory?.detail.id,
    };
  };

  const handleSave = async () => {
    // Validate inputs
    if (!validateScheduleDates() || !validateCampaignDateRange()) {
      return;
    }

    // Validate and filter selected hours to ensure they are within the date range.
    // Classic inventories have no hourly grid (date range only), so this only
    // applies to Digital — see validateScheduleDates()'s matching gated check.
    const filteredHours = validateAndFilterSelectedHours();

    if (
      inventoryType === InventoryClassification.DIGITAL &&
      filteredHours.size === 0
    ) {
      showError(tCampaigns("scheduleDrawer.selectAtLeastOneHour"));
      return;
    }
    // Update state with filtered hours (discard entries outside date range)
    if (filteredHours.size !== selectedHours.size) {
      setSelectedHours(filteredHours);
    }

    try {
      const existingSchedule = scheduleId
        ? inventorySchedules.find((s) => s.id === scheduleId)
        : null;

      let result;

      if (scheduleId && existingSchedule) {
        // Editing existing schedule
        const payload = buildSchedulePayload(
          existingSchedule.order,
          existingSchedule.name ||
            tCampaigns("scheduleDrawer.scheduleNameDefault", {
              order: existingSchedule.order,
            }),
          existingSchedule.pricing || 0,
          filteredHours,
        );

        result = await updateSchedules({
          campaignId,
          data: payload,
          scheduleId,
        }).unwrap();
      } else {
        // Creating new schedule
        const existingSchedules = inventorySchedules || [];
        const lastSchedule = existingSchedules.length
          ? existingSchedules[existingSchedules.length - 1]
          : null;
        const nextOrder = lastSchedule ? (lastSchedule.order || 1) + 1 : 1;

        const payload = buildSchedulePayload(
          nextOrder,
          tCampaigns("scheduleDrawer.scheduleNameDefault", {
            order: nextOrder,
          }),
          0,
          filteredHours,
        );

        result = await addSchedules({
          campaignId,
          data: payload,
        }).unwrap();
      }

      // Handle response
      if (result.success && typeof result.data === "string") {
        showSuccess(
          result.data || tCampaigns("scheduleDrawer.scheduleSavedSuccessfully"),
        );

        const hasOverlap = checkOverlappingSchedules();
        // Check for hours outside operation time for Digital inventory
        if (inventoryType === InventoryClassification.DIGITAL) {
          const hasError = hasHoursOutsideOperationTime(
            selectedInventory?.operations,
            selectedHours,
          );
          if (hasError) {
            showWarning(tCampaigns("scheduleDrawer.hoursOutsideOperationTime"));
          }
        }
        if (hasOverlap && !clearPreviousSchedule) {
          const warningMessage =
            inventoryType === InventoryClassification.CLASSIC
              ? tCampaigns("scheduleDrawer.overlappingSchedulesClassic")
              : tCampaigns("scheduleDrawer.overlappingSchedulesDigital");
          showWarning(warningMessage);
        }

        onScheduleSaved?.();
        onClose();
      } else {
        showError(tCampaigns("scheduleDrawer.errors.failedToSave"));
      }
    } catch (error) {
      console.error("Error saving schedule:", error);
      showError(tCampaigns("scheduleDrawer.errors.failedToSaveRetry"));
    }
  };

  const filteredInventory = useMemo(() => {
    if (!selectedInventory) return null;
    if (!searchQuery) return selectedInventory;
    const query = searchQuery.toLowerCase();
    const displayName = selectedInventory.detail.name?.toLowerCase() || "";
    return displayName.includes(query) ? selectedInventory : null;
  }, [selectedInventory, searchQuery]);

  const isDigital = inventoryType === InventoryClassification.DIGITAL;
  const isClassic = inventoryType === InventoryClassification.CLASSIC;

  const minHours = selectedInventory?.detail?.sellingTerm?.minHours;
  const isMinHoursViolated = useMemo(
    () => isDigital && hasAnyDayBelowMinHours(selectedHours, minHours),
    [isDigital, selectedHours, minHours],
  );

  // Calculate schedule title dynamically
  const scheduleTitle = useMemo(() => {
    if (scheduleId) {
      const schedule = inventorySchedules.find((s) => s.id === scheduleId);
      if (schedule) {
        const order = schedule.order || 1;
        const name = tCampaigns("scheduleOptimization.scheduleName", { order });
        return order === 1
          ? `${name} ${tCampaigns("scheduleOptimization.scheduleDefault")}`
          : name;
      }
    }
    const lastSchedule = inventorySchedules.length
      ? inventorySchedules[inventorySchedules.length - 1]
      : null;
    const nextOrder = lastSchedule ? (lastSchedule.order || 1) + 1 : 1;
    return tCampaigns("scheduleOptimization.scheduleName", {
      order: nextOrder,
    });
  }, [scheduleId, inventorySchedules, tCampaigns]);

  if (!selectedInventory) {
    return (
      <ModalDrawer
        isOpen={isOpen}
        onClose={onClose}
        title={tCampaigns("scheduleDrawer.title")}
        size="custom"
        customWidth="87vw"
      >
        <div className="flex items-center justify-center h-64">
          <p className="text-sm text-mw-neutral-500">
            {tCampaigns("scheduleDrawer.noInventorySelected")}
          </p>
        </div>
      </ModalDrawer>
    );
  }

  const footer = (
    <div className="flex flex-col gap-2 w-full">
      {isMinHoursViolated && (
        <p
          id="schedule-drawer-min-hours-error"
          className="text-xs text-mw-error-500"
        >
          {tCampaigns("scheduleDrawer.minHoursViolation", { minHours })}
        </p>
      )}
      <div className="flex items-center justify-between w-full">
        <Checkbox
          id="schedule-drawer-clear-previous-schedule"
          checked={clearPreviousSchedule}
          onChange={(e) => setClearPreviousSchedule(e.target.checked)}
          label={tCampaigns("scheduleDrawer.clearPreviousSchedule")}
        />
        <div className="flex gap-2">
          <Button
            id="schedule-drawer-cancel-btn"
            variant="outline"
            onClick={onClose}
            disabled={isSaving}
          >
            {tCommon("buttons.cancel")}
          </Button>
          <Button
            id="schedule-drawer-save-btn"
            variant="primary"
            onClick={handleSave}
            disabled={isSaving || isMinHoursViolated}
          >
            {isSaving
              ? tCampaigns("scheduleDrawer.saving")
              : tCampaigns("scheduleDrawer.saveChanges")}
          </Button>
        </div>
      </div>
    </div>
  );

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={onClose}
      title={
        scheduleId
          ? `${tCommon("buttons.edit")} ${scheduleTitle}`
          : tCampaigns("scheduleDrawer.newTitle", { title: scheduleTitle })
      }
      size="custom"
      customWidth="87vw"
      footer={footer}
      showBackButton={false}
    >
      <div className="flex gap-6 h-full">
        {/* Left Card - Selected Inventories (Bigger) */}
        <div className="flex-1">
          <Card className="p-4 h-full">
            <CardHeader className="pb-3 border-b border-mw-neutral-100">
              <CardTitle className="text-sm font-medium text-mw-neutral-700">
                {tCampaigns("scheduleDrawer.selectedInventories")}
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0! pt-4!">
              <div className="relative mb-4">
                <Input
                  id="schedule-drawer-search-input"
                  placeholder={tCampaigns("scheduleDrawer.searchPlaceholder")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10"
                />
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-mw-neutral-400 pointer-events-none" />
              </div>
              {filteredInventory && (
                <InventoryCard
                  item={filteredInventory}
                  className="p-2 bg-mw-primary-50!"
                />
              )}
            </CardContent>
          </Card>
        </div>
        {/* Right Card - Schedule Configuration */}
        <div className="w-[70%]">
          <Card className="p-4">
            <CardHeader className="pb-3 border-b border-mw-neutral-100">
              <CardTitle className="text-sm font-medium text-mw-neutral-700">
                {scheduleTitle}
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0! pt-4!">
              {isDigital ? (
                // DIGITAL UI
                <div className="space-y-6">
                  {/* Schedule Date and Select Days - Side by Side */}
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <DateRangePicker
                        id="schedule-drawer-date-picker"
                        value={scheduleDate}
                        label={tCampaigns("scheduleDrawer.scheduleDate")}
                        onChange={(range) => {
                          setScheduleDate(range);
                          // If a generated pattern is active, reapply it with
                          // new dates. "default" carries its own dates, so skip.
                          if (
                            selectedPattern !== "custom" &&
                            selectedPattern !== "default"
                          ) {
                            handlePatternChange(selectedPattern);
                          }
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
                          "scheduleDrawer.selectDateRange",
                        )}
                        format="dd MMM yyyy"
                        clearable={false}
                        numberOfMonths={2}
                        minDate={
                          campaignStartDate
                            ? fromAPIDateString(campaignStartDate)
                            : undefined
                        }
                        maxDate={
                          campaignEndDate
                            ? getEndOfDay(fromAPIDateString(campaignEndDate))
                            : undefined
                        }
                      />
                    </div>
                    <div className="space-y-2 -mt-1">
                      <div className="flex items-center justify-between gap-3">
                        <Label>
                          {tCampaigns("scheduleDrawer.selectDaysLabel")}{" "}
                          <Tooltip
                            content={tCampaigns(
                              "scheduleDrawerSelectAllDaysTooltip",
                            )}
                          >
                            <Info className="mt-1 w-4 h-4 text-mw-neutral-300" />
                          </Tooltip>
                        </Label>
                        <Checkbox
                          id="schedule-drawer-select-all-days"
                          checked={
                            selectedDays.length === availableDays.length &&
                            availableDays.length > 0
                          }
                          isIndeterminate={
                            selectedDays.length > 0 &&
                            selectedDays.length < availableDays.length
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
                          label={tCampaigns("scheduleDrawer.selectAllDays")}
                        />
                      </div>
                      <div className="flex gap-2 flex-wrap">
                        {DAYS_OF_WEEK.map((day) => {
                          const isAvailable = availableDays.includes(day);
                          return (
                            <button
                              key={day}
                              id={`schedule-drawer-day-${day.toLowerCase()}`}
                              type="button"
                              onClick={() => handleDayToggle(day)}
                              disabled={!isAvailable}
                              className={`
                            p-2 rounded-md text-sm font-medium transition-colors
                            ${
                              selectedDays.includes(day)
                                ? "bg-mw-primary-500 text-white"
                                : isAvailable
                                  ? "bg-mw-neutral-50 text-black hover:bg-mw-neutral-100"
                                  : "bg-mw-neutral-50 text-mw-neutral-300 cursor-not-allowed opacity-50"
                            }
                          `}
                            >
                              {tCommon(
                                `calendar.weekDaysShort.${DAYS_OF_WEEK.indexOf(day)}`,
                              )}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                    <div>
                      <Label className="mb-2">
                        {tCampaigns("scheduleDrawer.duration")}
                      </Label>
                      <Dropdown name="schedule-drawer-duration">
                        <DropdownTrigger className="w-full justify-between ">
                          {duration}
                        </DropdownTrigger>
                        <DropdownContent align="left" className="w-full">
                          {DURATION_OPTIONS.map((option) => (
                            <DropdownItem
                              key={option}
                              value={option}
                              onClick={() => setDuration(option)}
                            >
                              {option}
                            </DropdownItem>
                          ))}
                        </DropdownContent>
                      </Dropdown>
                    </div>
                    <div className="flex items-center gap-8">
                      <div>
                        <Label className="mb-2">
                          {tCampaigns("scheduleDrawer.spotsLoop")}
                        </Label>
                        <div className="flex items-center gap-8">
                          <Button
                            id="schedule-drawer-spots-per-loop-decrease"
                            size="sm"
                            variant="outline"
                            onClick={handleDecreaseSpotsPerLoop}
                            disabled={spotsPerLoop <= 1}
                            className="outline outline-mw-primary-500 p-2!"
                          >
                            <Minus className="w-6 h-6 text-mw-primary-500" />
                          </Button>
                          <span id="schedule-drawer-spots-per-loop-value">
                            {spotsPerLoop}
                          </span>
                          <Button
                            id="schedule-drawer-spots-per-loop-increase"
                            size="sm"
                            variant="outline"
                            onClick={handleIncreaseSpotsPerLoop}
                            disabled={
                              !selectedInventory?.operations?.clientPerLoop ||
                              spotsPerLoop >=
                                (selectedInventory.operations.clientPerLoop ||
                                  1)
                            }
                            className="outline outline-mw-primary-500 p-2!"
                          >
                            <Plus className="w-6 h-6 text-mw-primary-500" />
                          </Button>
                        </div>
                      </div>
                      <div>
                        <Label className="mb-2">
                          {tCampaigns("scheduleDrawer.spotsHour")}
                        </Label>
                        <div className="flex items-center gap-8">
                          <Button
                            id="schedule-drawer-spots-per-hour-decrease"
                            size="sm"
                            variant="outline"
                            onClick={handleDecreaseSpotsPerHour}
                            disabled={
                              !selectedInventory?.operations?.loopSize ||
                              spotsPerHour <= initialSpotsPerHour
                            }
                            className="outline outline-mw-primary-500 p-2!"
                          >
                            <Minus className="w-6 h-6 text-mw-primary-500" />
                          </Button>
                          <span
                            id="schedule-drawer-spots-per-hour-value"
                            className="text-sm font-medium"
                          >
                            {spotsPerHour}
                          </span>

                          <Button
                            id="schedule-drawer-spots-per-hour-increase"
                            variant="outline"
                            size="sm"
                            onClick={handleIncreaseSpotsPerHour}
                            disabled={
                              spotsPerHour >=
                              (selectedInventory?.operations?.clientPerLoop ||
                                1) *
                                initialSpotsPerHour
                            }
                            className="outline outline-mw-primary-500 p-2!"
                          >
                            <Plus className="w-6 h-6 text-mw-primary-500" />
                          </Button>
                        </div>
                      </div>
                    </div>
                  </div>
                  {/* Schedule Grids */}
                  <div className="space-y-2">
                    <Label>{tCampaigns("scheduleDrawer.scheduleGrids")}</Label>
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex-1 flex items-center gap-3">
                        <Radio
                          id="schedule-drawer-select-all-grid"
                          name="grid-mode"
                          checked={scheduleGridMode === "select"}
                          onChange={() => handleSelectAllGrid()}
                          label={tCampaigns("scheduleDrawer.selectAll")}
                          className="mt-1!"
                          disabled={selectedDays.length === 0}
                        />
                        <Radio
                          id="schedule-drawer-deselect-all-grid"
                          name="grid-mode"
                          checked={scheduleGridMode === "deselect"}
                          onChange={() => handleDeselectAllGrid()}
                          label={tCampaigns("scheduleDrawer.deselectAll")}
                          className="mt-1!"
                          disabled={selectedDays.length === 0}
                        />
                      </div>
                      <div className="flex-1 flex items-center justify-end">
                        <Dropdown name="schedule-drawer-pattern">
                          <DropdownTrigger className="w-full justify-between">
                            {tCampaigns(
                              `scheduleDrawer.schedulePatterns.${selectedPattern}`,
                            ) || tCampaigns("scheduleDrawer.customPattern")}
                          </DropdownTrigger>
                          <DropdownContent align="right">
                            {SCHEDULE_PATTERNS.filter(
                              // "Default" only when a default schedule exists
                              (pattern) =>
                                pattern.value !== "default" || hasDefault,
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
                    {scheduleDate.from && scheduleDate.to && (
                      <ScheduleGrid
                        startDate={scheduleDate.from}
                        endDate={scheduleDate.to}
                        selectedDays={selectedDays}
                        selectedHours={selectedHours}
                        onCellClick={handleCellClick}
                        onRowClick={handleRowClick}
                        onColumnClick={handleColumnClick}
                        className="max-h-[400px] overflow-y-auto"
                      />
                    )}
                  </div>
                </div>
              ) : isClassic ? (
                // CLASSIC UI - Only date range
                <div>
                  <Label className="mb-2">
                    {tCampaigns("scheduleDrawer.scheduleDate")}
                  </Label>
                  <DateRangePicker
                    id="schedule-drawer-classic-date-picker"
                    value={scheduleDate}
                    onChange={(range) => {
                      setScheduleDate(range);
                      // If a generated pattern is active, reapply it with new
                      // dates. "default" carries its own dates, so skip.
                      if (
                        selectedPattern !== "custom" &&
                        selectedPattern !== "default"
                      ) {
                        handlePatternChange(selectedPattern);
                      }
                    }}
                    onBlur={() => {
                      // A single click only sets `from` — closing the picker
                      // without a second click used to leave `to` null,
                      // blocking save. Treat it as a single-day selection.
                      if (scheduleDate.from && !scheduleDate.to) {
                        setScheduleDate({
                          from: scheduleDate.from,
                          to: scheduleDate.from,
                        });
                      }
                    }}
                    placeholder={tCampaigns("scheduleDrawer.selectDateRange")}
                    format="dd MMM yyyy"
                    clearable={false}
                    numberOfMonths={2}
                    minDate={
                      campaignStartDate
                        ? fromAPIDateString(campaignStartDate)
                        : undefined
                    }
                    maxDate={
                      campaignEndDate
                        ? getEndOfDay(fromAPIDateString(campaignEndDate))
                        : undefined
                    }
                  />
                </div>
              ) : (
                // Default/Unknown type
                <div className="text-sm text-mw-neutral-500">
                  {tCampaigns("scheduleDrawer.invalidInventoryType")}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </ModalDrawer>
  );
};
