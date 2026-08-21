/**
 * Date utility functions for handling timezone-safe date conversion
 * This ensures dates are stored and retrieved correctly regardless of user's timezone
 */

/**
 * Converts a Date object to a timezone-safe ISO date string (YYYY-MM-DD)
 * This prevents the "one day before" issue by ensuring the date represents
 * the user's local date, not UTC
 */
export const toISODateString = (date: Date | null): string => {
  if (!date || !(date instanceof Date)) {
    throw new Error("Invalid date provided");
  }

  // Get the local date components to avoid timezone conversion issues
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

/**
 * Converts a timezone-safe ISO date string to a Date object
 * This ensures the date represents the user's local date
 */
export const fromISODateString = (dateString: string): Date => {
  if (!dateString || typeof dateString !== "string") {
    throw new Error("Invalid date string provided");
  }

  // Parse the date string and create a local date
  const [year, month, day] = dateString.split("-").map(Number);

  if (isNaN(year) || isNaN(month) || isNaN(day)) {
    throw new Error("Invalid date string format");
  }

  // Create a new date in local timezone
  return new Date(year, month - 1, day);
};

/**
 * Converts a Date object to a timezone-safe ISO date string for API submission
 * This is specifically for API calls where we want to send the user's selected date
 */
export const toAPIDateString = (date: Date | null): string => {
  if (!date) {
    throw new Error("Invalid date provided");
  }
  return toISODateString(date);
};

/**
 * Converts an API date string to a Date object for display
 * This handles dates coming from the server
 */
export const fromAPIDateString = (dateString: string): Date => {
  return fromISODateString(dateString);
};

/**
 * Creates a timezone-safe date from year, month, and day
 * This ensures the date represents the user's local date
 */
export const createLocalDate = (
  year: number,
  month: number,
  day: number,
): Date => {
  return new Date(year, month - 1, day);
};

/**
 * Gets the current date in local timezone
 * This ensures we're working with the user's local date
 */
export const getCurrentLocalDate = (): Date => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
};

/**
 * Adds days to a date in local timezone
 * This ensures date arithmetic works correctly
 */
export const addDays = (date: Date, days: number): Date => {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
};

/**
 * Subtracts days from a date in local timezone
 * This ensures date arithmetic works correctly
 */
export const subtractDays = (date: Date, days: number): Date => {
  const result = new Date(date);
  result.setDate(result.getDate() - days);
  return result;
};

/**
 * Checks if two dates are the same day in local timezone
 * This ensures date comparison works correctly
 */
export const isSameDay = (
  date1: Date | null,
  date2: Date | null | undefined,
): boolean => {
  if (!date1 || !date2) {
    return false;
  }
  return (
    date1.getFullYear() === date2.getFullYear() &&
    date1.getMonth() === date2.getMonth() &&
    date1.getDate() === date2.getDate()
  );
};

/**
 * Checks if a date is between two other dates (inclusive)
 * This ensures date range validation works correctly
 */
export const isDateBetween = (
  date: Date,
  startDate: Date | null,
  endDate: Date | null,
): boolean => {
  if (!startDate || !endDate) {
    return false;
  }
  return date >= startDate && date <= endDate;
};

/**
 * Formats a date for display in a specific format
 * This ensures consistent date display across the application
 */
export const formatDate = (
  date: Date | null,
  format: string = "MM/dd/yyyy",
): string => {
  if (!date || !(date instanceof Date)) {
    return "";
  }

  const month = String(date.getMonth() + 1).padStart(2, "0");
  const monthName = date.toLocaleDateString("en-US", { month: "short" });
  const day = String(date.getDate()).padStart(2, "0");
  const year = date.getFullYear();

  return format
    .replace("MMM", monthName)
    .replace("MM", month)
    .replace("dd", day)
    .replace("yyyy", String(year));
};
/**
 * Converts a date range to API format
 * This ensures date ranges are sent to the API correctly
 */
export const toAPIDateRange = (dateRange: {
  from: Date;
  to: Date;
}): { startDate: string; endDate: string } => {
  return {
    startDate: toAPIDateString(dateRange.from),
    endDate: toAPIDateString(dateRange.to),
  };
};

/**
 * Converts an API date range to local date range
 * This ensures date ranges from the API are displayed correctly
 */
export const fromAPIDateRange = (
  startDate: string,
  endDate: string,
): { from: Date; to: Date } => {
  return {
    from: fromAPIDateString(startDate),
    to: fromAPIDateString(endDate),
  };
};

/**
 * Validates that a date string is in the correct format
 * This ensures data integrity
 */
export const isValidDateString = (dateString: string): boolean => {
  if (!dateString || typeof dateString !== "string") {
    return false;
  }

  try {
    const date = fromISODateString(dateString);
    return !isNaN(date.getTime());
  } catch {
    return false;
  }
};

/**
 * Gets the start of the day for a given date
 * This ensures we're working with the beginning of the day
 */
export const getStartOfDay = (date: Date | null): Date => {
  if (!date) {
    throw new Error("Invalid date provided");
  }
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
};

/**
 * Gets the end of the day for a given date
 * This ensures we're working with the end of the day
 */
export const getEndOfDay = (date: Date | null): Date => {
  if (!date) {
    throw new Error("Invalid date provided");
  }
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    23,
    59,
    59,
    999,
  );
};

/**
 * Converts a date to a timezone-safe timestamp
 * This ensures timestamps are consistent
 */
export const toTimestamp = (date: Date): number => {
  return date.getTime();
};

/**
 * Converts a timestamp to a timezone-safe date
 * This ensures timestamps are converted correctly
 */
export const fromTimestamp = (timestamp: number): Date => {
  return new Date(timestamp);
};

// compute days left until campaign start
export const computeDaysLeft = (startDateStr?: string | null): string => {
  if (!startDateStr) return "--";
  const start = new Date(startDateStr);
  if (isNaN(start.getTime())) return "--";

  // normalize to local start-of-day to compare by date, not time
  const today = getStartOfDay(new Date());
  const normalizedStart = getStartOfDay(start);

  const msPerDay = 24 * 60 * 60 * 1000;
  const diffDays = Math.ceil(
    (normalizedStart.getTime() - today.getTime()) / msPerDay,
  );

  return diffDays > 0 ? `${diffDays} days left` : "--";
};

/**
 * Formats date for display (e.g., "Jan 15, 2024").
 * Pass a `t` function from useTranslate(["common"]) to get locale-aware output.
 */
export const formatDisplayDate = (
  dateString: string,
  t?: (key: string, params?: Record<string, string | number>) => string,
): string => {
  const date = new Date(dateString);
  if (!dateString || isNaN(date.getTime())) return "--";
  if (t) {
    const month = t(`calendar.monthNamesShort.${date.getMonth()}`);
    const day = date.getDate().toString().padStart(2, "0");
    const year = date.getFullYear();
    return t("calendar.formattedShortDate", { month, day, year });
  }
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric",
  });
};

/**
 * Formats a date range for display (e.g., "Jul 25 - Jul 31, 2022").
 * When both dates fall in the same year and the formatted start date ends with
 * that year, the redundant year is dropped from the start date. Locales that put
 * the year first (e.g. Japanese) keep both dates in full.
 */
export const formatDisplayDateRange = (
  startDateString: string,
  endDateString: string,
  t?: (key: string, params?: Record<string, string | number>) => string,
): string => {
  const start = formatDisplayDate(startDateString, t);
  const end = formatDisplayDate(endDateString, t);
  if (start === "--" || end === "--") return "--";

  const startYear = new Date(startDateString).getFullYear();
  const endYear = new Date(endDateString).getFullYear();
  if (startYear !== endYear) return `${start} - ${end}`;

  const withoutTrailingYear = start.replace(
    new RegExp(`[,\\s]*${startYear}\\s*$`),
    "",
  );
  return `${withoutTrailingYear} - ${end}`;
};

export const findDurationInDays = (
  startDateStr: string,
  endDateStr: string,
): number => {
  const start = new Date(startDateStr);
  const end = new Date(endDateStr);
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0;

  // normalize to local start-of-day to compare by date, not time
  const normalizedStart = getStartOfDay(start);
  const normalizedEnd = getStartOfDay(end);

  const msPerDay = 24 * 60 * 60 * 1000;
  const diffDays =
    Math.ceil(
      (normalizedEnd.getTime() - normalizedStart.getTime()) / msPerDay,
    ) + 1; // +1 to include both start and end dates

  return diffDays > 0 ? diffDays : 0;
};

/**
 * Gets the start of the week for a given date.
 * @param date - The date to get the start of week for
 * @param weekStartsOn - Day of week (0 = Sunday, 1 = Monday, etc.)
 */
export function getStartOfWeek(date: Date, weekStartsOn: number): Date {
  const result = new Date(date);
  const day = result.getDay();
  const diff = (day - weekStartsOn + 7) % 7;
  result.setDate(result.getDate() - diff);
  return result;
}

/**
 * Gets the number of days in a month (1-31).
 */
export function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate();
}

/**
 * Formats date as "January 2025" (month long, year numeric).
 */
export function formatMonthYear(date: Date): string {
  return date.toLocaleDateString("en-US", { month: "long", year: "numeric" });
}

/**
 * Returns ISO-like week of year (1-based) for a date, given weekStartsOn (0=Sun, 1=Mon).
 */
export function getWeekOfYear(date: Date, weekStartsOn: number): number {
  const startOfWeek = getStartOfWeek(date, weekStartsOn);
  const startOfYear = new Date(startOfWeek.getFullYear(), 0, 1);
  const startOfFirstWeek = getStartOfWeek(startOfYear, weekStartsOn);
  const msPerWeek = 7 * 24 * 60 * 60 * 1000;
  const diff = startOfWeek.getTime() - startOfFirstWeek.getTime();
  return Math.floor(diff / msPerWeek) + 1;
}

/**
 * Formats week range for display, e.g. "Week 1 (Jan 6 2025 - Jan 12 2025)".
 * weekNumber is 1-based for display.
 */
export function formatWeekRange(date: Date, weekStartsOn: number): string {
  const startOfWeek = getStartOfWeek(date, weekStartsOn);
  const endOfWeek = new Date(startOfWeek);
  endOfWeek.setDate(endOfWeek.getDate() + 6);

  const weekNumber = Math.ceil(startOfWeek.getDate() / 7);
  const startStr = `${startOfWeek.toLocaleDateString("en-US", { month: "short" })} ${startOfWeek.getDate()} ${startOfWeek.getFullYear()}`;
  const endStr = `${endOfWeek.toLocaleDateString("en-US", { month: "short" })} ${endOfWeek.getDate()} ${endOfWeek.getFullYear()}`;
  return `Week ${weekNumber} (${startStr} - ${endStr})`;
}

export function getFirstDayOfMonth(year: number, month: number): number {
  return new Date(year, month, 1).getDay();
}
