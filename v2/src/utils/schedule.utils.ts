/**
 * Schedule utility functions
 */
export type dayString =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

// Constants for weekday mapping
export const MAPPING_WEEKDAYS_NAMES: dayString[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

// Constants
export const DAYS_OF_WEEK = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** Monday-based weekday index (0 = Monday, 6 = Sunday). Reduces duplication and cognitive complexity. */
export const getMondayBasedDay = (date: Date): number => {
  const d = date.getDay();
  return d === 0 ? 6 : d - 1;
};
export const DURATION_OPTIONS = ["15 Sec"];

// Schedule pattern types
export type SchedulePattern =
  | "default"
  | "commuter"
  | "business"
  | "nightlife"
  | "weekend"
  | "24/7"
  | "custom";

export const SCHEDULE_PATTERNS = [
  { value: "default", label: "Default" },
  { value: "commuter", label: "Commuter Pattern" },
  { value: "business", label: "Business Hours" },
  { value: "nightlife", label: "Nightlife" },
  { value: "weekend", label: "Weekend Focus" },
  { value: "24/7", label: "24/7" },
  { value: "custom", label: "Custom" },
] as const;

// Day name conversion utilities
export const convertDayName = (day: string): string => {
  const dayMap: Record<string, string> = {
    MONDAY: "Mon",
    TUESDAY: "Tue",
    WEDNESDAY: "Wed",
    THURSDAY: "Thu",
    FRIDAY: "Fri",
    SATURDAY: "Sat",
    SUNDAY: "Sun",
  };
  return dayMap[day] || day;
};

export const convertDayNameBack = (day: string): string => {
  const dayMap: Record<string, string> = {
    Mon: "MONDAY",
    Tue: "TUESDAY",
    Wed: "WEDNESDAY",
    Thu: "THURSDAY",
    Fri: "FRIDAY",
    Sat: "SATURDAY",
    Sun: "SUNDAY",
  };
  return dayMap[day] || day;
};

// Date formatting utilities for booking matrix
export const formatDateToYYYYMMDD = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

export const parseDateFromBookingMatrix = (dateStr: string): Date => {
  const [year, month, day] = dateStr.split("-").map(Number);
  return new Date(year, month - 1, day);
};

// Operation hours type
export type OperationHours = Record<
  dayString,
  Array<{ start: string; end: string }>
>;

/**
 * Extracts the earliest start time and latest end time from operation hours
 * @param operationHours - Object with day names as keys and arrays of time ranges
 * @returns Object with startTime and endTime in "HH:mm:ss" format, or null if no data
 */
export const extractOperationTimes = (
  operationHours: OperationHours | null | undefined,
): { startTime: string; endTime: string } | null => {
  if (!operationHours) {
    return null;
  }

  let earliestStart: string | null = null;
  let latestEnd: string | null = null;

  // Iterate through all days
  Object.values(operationHours).forEach((timeRanges) => {
    if (!Array.isArray(timeRanges)) return;

    // Iterate through all time ranges for this day
    timeRanges.forEach((range) => {
      if (range.start && range.end) {
        // Compare start times to find earliest
        if (!earliestStart || range.start < earliestStart) {
          earliestStart = range.start;
        }

        // Compare end times to find latest
        if (!latestEnd || range.end > latestEnd) {
          latestEnd = range.end;
        }
      }
    });
  });

  if (!earliestStart || !latestEnd) {
    return null;
  }

  return {
    startTime: earliestStart,
    endTime: latestEnd,
  };
};

/**
 * Adds hours for newly added days to the selected hours set
 * @param scheduleDate - Date range with from and to dates
 * @param newlyAddedDays - Array of day names (e.g., ["Mon", "Tue"]) that were newly added
 * @param currentSelectedHours - Current set of selected hours (format: "YYYY-MM-DD-hour")
 * @returns New Set with hours added for newly added days
 */
export const addHoursForNewlyAddedDays = (
  scheduleDate: {
    from: Date | null | undefined;
    to: Date | null | undefined;
  },
  newlyAddedDays: string[],
  currentSelectedHours: Set<string>,
): Set<string> => {
  if (!scheduleDate.from || !scheduleDate.to || newlyAddedDays.length === 0) {
    return currentSelectedHours;
  }

  const updated = new Set(currentSelectedHours);
  const current = new Date(scheduleDate.from);
  current.setHours(0, 0, 0, 0);
  const end = new Date(scheduleDate.to);
  end.setHours(23, 59, 59, 999);
  const newlyAddedDaysSet = new Set(newlyAddedDays);

  while (current <= end) {
    const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
    if (newlyAddedDaysSet.has(dayName)) {
      const dateStr = formatDateToYYYYMMDD(current);
      for (let hour = 0; hour < 24; hour++) {
        updated.add(`${dateStr}-${hour}`);
      }
    }
    current.setDate(current.getDate() + 1);
  }

  return updated;
};

// Helper function to generate hour range array
const generateHourRange = (start: number, end: number): number[] => {
  return Array.from({ length: end - start + 1 }, (_, i) => start + i);
};

// Helper to generate schedule based on configuration
const generateSchedule = (
  scheduleDate: { from: Date; to: Date },
  allowedDays: string[],
  hourGenerator: (day: string) => number[],
): { days: string[]; hours: Set<string> } => {
  const hoursSet = new Set<string>();
  const DaysSet = new Set(allowedDays);

  const currentDate = new Date(scheduleDate.from);
  currentDate.setHours(0, 0, 0, 0);
  const endDate = new Date(scheduleDate.to);
  endDate.setHours(23, 59, 59, 999);

  while (currentDate <= endDate) {
    const dayName = DAYS_OF_WEEK[getMondayBasedDay(currentDate)];

    if (DaysSet.has(dayName)) {
      const hours = hourGenerator(dayName);
      if (hours.length > 0) {
        const dateStr = formatDateToYYYYMMDD(currentDate);
        hours.forEach((hour) => {
          hoursSet.add(`${dateStr}-${hour}`);
        });
      }
    }
    currentDate.setDate(currentDate.getDate() + 1);
  }

  return { days: allowedDays, hours: hoursSet };
};

// Apply schedule pattern to selected days and hours
export const applySchedulePattern = (
  pattern: SchedulePattern,
  scheduleDate: { from: Date | null | undefined; to: Date | null | undefined },
  availableDays: string[],
): { days: string[]; hours: Set<string> } => {
  if (!scheduleDate.from || !scheduleDate.to) {
    return { days: [], hours: new Set<string>() };
  }

  // Ensure dates are defined for the helper
  const dateRange = { from: scheduleDate.from, to: scheduleDate.to };

  switch (pattern) {
    case "commuter": {
      const days = ["Mon", "Tue", "Wed", "Thu", "Fri"].filter((day) =>
        availableDays.includes(day),
      );
      return generateSchedule(dateRange, days, () => [6, 7, 8, 17, 18, 19]);
    }

    case "business": {
      const days = ["Mon", "Tue", "Wed", "Thu", "Fri"].filter((day) =>
        availableDays.includes(day),
      );
      const businessHours = generateHourRange(9, 17);
      return generateSchedule(dateRange, days, () => businessHours);
    }

    case "nightlife": {
      const days = [...availableDays];
      const nightlifeHours = [0, 1, ...generateHourRange(18, 23)];
      return generateSchedule(dateRange, days, () => nightlifeHours);
    }

    case "weekend": {
      const days = ["Sat", "Sun"].filter((day) => availableDays.includes(day));
      const weekendHours = generateHourRange(10, 23);
      return generateSchedule(dateRange, days, () => weekendHours);
    }

    case "24/7": {
      const days = [...availableDays];
      const allHours = generateHourRange(0, 23);
      return generateSchedule(dateRange, days, () => allHours);
    }

    case "custom":
    default:
      return { days: [], hours: new Set<string>() };
  }
};

const sameDayList = (a: string[], b: string[]): boolean => {
  if (a.length !== b.length) return false;
  const setB = new Set(b);
  return a.every((day) => setB.has(day));
};

const sameHourSet = (a: Set<string>, b: Set<string>): boolean => {
  if (a.size !== b.size) return false;
  for (const value of a) {
    if (!b.has(value)) return false;
  }
  return true;
};

/**
 * Detects which generated pattern (commuter, business, nightlife, weekend,
 * 24/7) a schedule's days + hours exactly match, for the given date range and
 * available days. Returns "custom" when none match. "default" is handled
 * separately (it is a stored snapshot, not a generated shape).
 */
export const detectSchedulePattern = (
  scheduleDate: { from: Date | null | undefined; to: Date | null | undefined },
  selectedDays: string[],
  selectedHours: Set<string>,
  availableDays: string[],
): SchedulePattern => {
  if (!scheduleDate.from || !scheduleDate.to || selectedHours.size === 0) {
    return "custom";
  }

  const candidates: SchedulePattern[] = [
    "commuter",
    "business",
    "nightlife",
    "weekend",
    "24/7",
  ];

  for (const pattern of candidates) {
    const { days, hours } = applySchedulePattern(
      pattern,
      scheduleDate,
      availableDays,
    );
    if (sameDayList(days, selectedDays) && sameHourSet(hours, selectedHours)) {
      return pattern;
    }
  }

  return "custom";
};

export const formatSize = (
  size: string | null | undefined,
): { name: string; colorClass: string } => {
  if (!size) return { name: "", colorClass: "" };

  const sizeMap: Record<string, { name: string; colorClass: string }> = {
    XS: {
      name: "Extra Small",
      colorClass: "outline-mw-pacific-blue-600! text-mw-pacific-blue-600!",
    },
    S: {
      name: "Small",
      colorClass: "outline-mw-deep-purple-600! text-mw-deep-purple-600!",
    },
    M: {
      name: "Medium",
      colorClass: "outline-mw-purple-warning-500! text-mw-purple-warning-500!",
    },
    L: {
      name: "Large",
      colorClass: "outline-mw-brown-warning-500! text-mw-brown-warning-500!",
    },
    XL: {
      name: "Extra Large",
      colorClass: "outline-mw-orange-warning-600! text-mw-orange-warning-600!",
    },
  };

  return {
    name: sizeMap[size.toUpperCase()]?.name || size,
    colorClass: sizeMap[size.toUpperCase()]?.colorClass || "",
  };
};
// Helper to format hour in 12-hour format with AM/PM
export const formatHourTo12Hour = (hour: number): string => {
  const period = hour >= 12 ? "PM" : "AM";
  const hour12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
  return `${String(hour12).padStart(2, "0")}:00 ${period}`;
};

export const formatScheduleHours = (
  bookingMatrix: Record<string, number[]> | null | undefined,
): string[] => {
  if (!bookingMatrix || Object.keys(bookingMatrix).length === 0) {
    return [];
  }

  // Collect all unique hours from all dates
  const allHoursSet = new Set<number>();
  Object.values(bookingMatrix).forEach((hours) => {
    if (Array.isArray(hours)) {
      hours.forEach((hour) => {
        if (typeof hour === "number" && hour >= 0 && hour <= 23) {
          allHoursSet.add(hour);
        }
      });
    }
  });

  if (allHoursSet.size === 0) {
    return [];
  }

  // Convert to sorted array
  const sortedHours = Array.from(allHoursSet).sort((a, b) => a - b);

  // Group consecutive hours into ranges
  const ranges: string[] = [];
  let rangeStart: number | null = null;
  let rangeEnd: number | null = null;

  for (let i = 0; i < sortedHours.length; i++) {
    const currentHour = sortedHours[i];
    const nextHour = sortedHours[i + 1];

    if (rangeStart === null) {
      rangeStart = currentHour;
      rangeEnd = currentHour;
    }

    // Check if next hour is consecutive
    if (nextHour !== undefined && nextHour === currentHour + 1) {
      rangeEnd = nextHour;
    } else {
      // End of consecutive range
      if (rangeStart !== null && rangeEnd !== null) {
        if (rangeStart === rangeEnd) {
          // Single hour range
          ranges.push(formatHourTo12Hour(rangeStart));
        } else {
          // Multi-hour range
          ranges.push(
            `${formatHourTo12Hour(rangeStart)} to ${formatHourTo12Hour(rangeEnd)}`,
          );
        }
      }
      rangeStart = null;
      rangeEnd = null;
    }
  }

  return ranges;
};

/**
 * Calculate min start time and max end time from booking matrix
 * @param bookingMatrix - Record with date strings as keys and arrays of hour numbers as values
 * @returns String in format "HH:MM AM/PM - HH:MM AM/PM" or "--" if no hours found
 */
export const getScheduleTimeSlot = (
  bookingMatrix: Record<string, number[]> | null | undefined,
): string => {
  if (!bookingMatrix || Object.keys(bookingMatrix).length === 0) {
    return "--";
  }

  // Collect all hours from all dates
  const allHours: number[] = [];
  Object.values(bookingMatrix).forEach((hours) => {
    if (Array.isArray(hours)) {
      hours.forEach((hour) => {
        if (typeof hour === "number" && hour >= 0 && hour <= 23) {
          allHours.push(hour);
        }
      });
    }
  });

  if (allHours.length === 0) {
    return "--";
  }

  // Find min and max hours
  const minHour = Math.min(...allHours);
  const maxHour = Math.max(...allHours);

  // Format in 12-hour format
  const startTime = formatHourTo12Hour(minHour);
  const endTime = formatHourTo12Hour(maxHour);

  return `${startTime} - ${endTime}`;
};

/**
 * Gets operation times for a specific day from inventory operations
 * @param operations - Inventory operations object
 * @param dayName - Day name in backend format (MONDAY, TUESDAY, etc.)
 * @returns Object with startTime and endTime in "HH:mm:ss" format, or null if no data
 */
export const getDayOperationTimes = (
  operations:
    | {
        operatingTimes?: OperationHours;
        startTime?: string;
        endTime?: string;
      }
    | null
    | undefined,
  dayName: dayString,
): { startTime: string; endTime: string } | null => {
  if (!operations) return null;

  const { operatingTimes, startTime, endTime } = operations;

  // If operatingTimes exists, get the times for the specific day
  if (operatingTimes?.[dayName]) {
    const dayTimeRanges = operatingTimes[dayName];
    if (dayTimeRanges && dayTimeRanges.length > 0) {
      // Find the earliest start and latest end for this day
      let earliestStart: string | null = null;
      let latestEnd: string | null = null;

      dayTimeRanges.forEach((range) => {
        if (range.start && range.end) {
          if (!earliestStart || range.start < earliestStart) {
            earliestStart = range.start;
          }
          if (!latestEnd || range.end > latestEnd) {
            latestEnd = range.end;
            if (latestEnd === "00:00:00") {
              latestEnd = "23:59:59";
            }
          }
        }
      });

      if (earliestStart && latestEnd) {
        return { startTime: earliestStart, endTime: latestEnd };
      }
    }
  }

  // Fall back to direct startTime/endTime if available
  if (startTime && endTime) {
    return { startTime, endTime };
  }

  return null;
};

/**
 * Checks if selected hours are outside operation hours for a single inventory
 * @param operations - Inventory operations object
 * @param selectedHours - Set of selected hours in format "YYYY-MM-DD-hour"
 * @returns true if any hour is outside operation time or on a non-operation day
 */
export const hasHoursOutsideOperationTime = (
  operations:
    | {
        operatingTimes?: OperationHours;
        startTime?: string;
        endTime?: string;
      }
    | null
    | undefined,
  selectedHours: Set<string>,
): boolean => {
  if (!operations || selectedHours.size === 0) return false;

  for (const hourKey of selectedHours) {
    // Parse hour key: "YYYY-MM-DD-hour"
    const dayHour = hourKey.split("-");
    const hour = Number(dayHour[dayHour.length - 1]);
    const day = new Date(`${dayHour[0]}-${dayHour[1]}-${dayHour[2]}`);
    const dayName =
      MAPPING_WEEKDAYS_NAMES[day.getDay() === 0 ? 6 : day.getDay() - 1];

    // Check if day is in operation days
    const operationDays = Object.keys(operations.operatingTimes || {});
    if (!operationDays.includes(dayName)) {
      return true; // Day is not in operation days
    }

    // Get day-wise operation times
    const dayOperationTimes = getDayOperationTimes(operations, dayName);
    if (!dayOperationTimes) {
      // If no operation times found, mark as error
      return true;
    }

    // Extract hour from time strings (format: "HH:mm:ss" or "HH:mm")
    const startHour = Number(dayOperationTimes.startTime.split(":")[0]);
    const endHour = Number(dayOperationTimes.endTime.split(":")[0]);

    // Check if hour is within operation time range
    if (!(hour >= startHour && hour <= endHour)) {
      return true; // Hour is outside operation time
    }
  }

  return false;
};

/**
 * Groups selected hours (format "YYYY-MM-DD-hour") by date and counts hours per date.
 * @param selectedHours - Set of selected hours in format "YYYY-MM-DD-hour"
 * @returns Record of date string to number of selected hours on that date
 */
export const getHoursPerDayFromSelectedHours = (
  selectedHours: Set<string>,
): Record<string, number> => {
  const hoursPerDay: Record<string, number> = {};

  for (const hourKey of selectedHours) {
    // Parse hour key: "YYYY-MM-DD-hour"
    const dayHour = hourKey.split("-");
    const dateStr = dayHour.slice(0, 3).join("-");
    hoursPerDay[dateStr] = (hoursPerDay[dateStr] || 0) + 1;
  }

  return hoursPerDay;
};

/**
 * Checks whether any scheduled day has fewer selected hours than the selling
 * term's minimum required hours per day. Days with no selected hours are ignored.
 * @param selectedHours - Set of selected hours in format "YYYY-MM-DD-hour"
 * @param minHours - Minimum required hours per scheduled day
 * @returns true if any day with at least one selected hour has fewer than minHours
 */
export const hasAnyDayBelowMinHours = (
  selectedHours: Set<string>,
  minHours: number | null | undefined,
): boolean => {
  if (!minHours || minHours <= 0 || selectedHours.size === 0) return false;

  const hoursPerDay = getHoursPerDayFromSelectedHours(selectedHours);

  return Object.values(hoursPerDay).some((count) => count < minHours);
};

/**
 * Counts the number of unique scheduled days across multiple schedules for the
 * same inventory, by taking the union of each schedule's bookingMatrix dates.
 * @param schedules - Array of schedules belonging to the same inventory
 * @returns Number of unique dates that have at least one scheduled hour
 */
export const countUniqueScheduledDays = (
  schedules: Array<
    { bookingMatrix: Record<string, number[]> } | null | undefined
  >,
): number => {
  const uniqueDays = new Set<string>();

  for (const schedule of schedules) {
    if (!schedule?.bookingMatrix) continue;
    Object.entries(schedule.bookingMatrix).forEach(([date, hours]) => {
      if (Array.isArray(hours) && hours.length > 0) {
        uniqueDays.add(date);
      }
    });
  }

  return uniqueDays.size;
};

/**
 * Counts unique scheduled days for Classic inventories, which have no hourly
 * booking grid — bookingMatrix stays empty for them, so this derives days
 * instead from each schedule's scheduleDays (weekly recurrence, e.g.
 * "MONDAY") applied across its startDate/endDate range. An empty/missing
 * scheduleDays is treated as "no day restriction" (every day in range counts).
 * @param schedules - Array of schedules belonging to the same inventory
 * @returns Number of unique dates covered by the schedules
 */
export const countUniqueScheduledDaysFromRange = (
  schedules: Array<
    | {
        scheduleDays?: string[] | null;
        startDate?: string | null;
        endDate?: string | null;
      }
    | null
    | undefined
  >,
): number => {
  const uniqueDays = new Set<string>();

  for (const schedule of schedules) {
    if (!schedule?.startDate || !schedule.endDate) continue;

    const allowedDays = schedule.scheduleDays?.length
      ? new Set(schedule.scheduleDays.map(convertDayName))
      : null;

    const current = new Date(schedule.startDate);
    current.setHours(0, 0, 0, 0);
    const end = new Date(schedule.endDate);
    end.setHours(23, 59, 59, 999);

    while (current <= end) {
      const dayName = DAYS_OF_WEEK[getMondayBasedDay(current)];
      if (!allowedDays || allowedDays.has(dayName)) {
        uniqueDays.add(formatDateToYYYYMMDD(current));
      }
      current.setDate(current.getDate() + 1);
    }
  }

  return uniqueDays.size;
};
