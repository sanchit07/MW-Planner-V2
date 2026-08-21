import { fromISODateString, toAPIDateString } from "./dateUtils";

// ---------------------------------------------------------------------------
// Date range & period (from dashboardDateUtils)
// ---------------------------------------------------------------------------

export type PeriodOption =
  | "last-7-days"
  | "last-30-days"
  | "last-month"
  | "quarterly"
  | "yearly"
  | "date-range";

export interface DateRange {
  from: Date | null | undefined;
  to: Date | null | undefined;
}

/** Format number as currency for summary cards (e.g. MYR 31,514,581.39). */
export function formatSummaryCurrency(value: number, currency = "MYR"): string {
  if (typeof value !== "number" || Number.isNaN(value)) return `${currency} 0`;
  return `${currency} ${value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

/**
 * Returns Tailwind text color class for a percentage value (e.g. conversion/utilization).
 * - < 60: success (green)
 * - 60–80: warning (amber)
 * - > 80: error (red)
 */
export function getPercentageColorClass(value: number): string {
  const n = Number(value);
  if (n < 60) return "text-mw-success-500";
  if (n <= 80) return "text-mw-warning-500";
  return "text-mw-error-500";
}

/** Format number for chart axis/tooltip (e.g. 1.2M, 50K). */
export function formatCompactNumber(value: number, decimals = 0): string {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(decimals)}K`;
  }
  return value.toString();
}

/** Format chart tooltip value: "Label: 1.2M" */
export function formatChartTooltipValue(
  value: number,
  label: string,
  decimals = 0,
): string {
  return `${label}: ${formatCompactNumber(value, decimals)}`;
}

/** Format chart tooltip date with current year: "10 Feb 2026" */
export function formatChartTooltipDate(dateLabel: string): string {
  return `${dateLabel} ${new Date().getFullYear()}`;
}

/** Format chart Y-axis value using compact number */
export function formatChartYAxisValue(value: number, decimals = 0): string {
  return formatCompactNumber(value, decimals);
}

/** Index to highlight as "current" in chart (last bucket for most periods; for yearly, last or 11). */
export function getChartCurrentDateIndex(
  period: PeriodOption,
  labelsLength: number,
): number {
  if (labelsLength === 0) return 0;
  if (period === "yearly") {
    return Math.min(11, labelsLength - 1);
  }
  return labelsLength - 1;
}

/** Label for period dropdown display. */
export function getPeriodLabel(period: PeriodOption): string {
  switch (period) {
    case "last-7-days":
      return "filters.last7Days";
    case "last-30-days":
      return "filters.last30Days";
    case "last-month":
      return "filters.lastMonth";
    case "quarterly":
      return "filters.quarterly";
    case "yearly":
      return "filters.yearly";
    case "date-range":
      return "filters.dateRange";
    default:
      return "Select period";
  }
}

/**
 * Calculates startDate and endDate based on period option
 * @param period - The selected period option
 * @param dateRange - The date range (used when period is "date-range")
 * @returns Object with startDate and endDate in API format (YYYY-MM-DD)
 */
export const calculateDateRangeForPeriod = (
  period: PeriodOption,
  dateRange?: DateRange,
): { startDate: string; endDate: string } => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  let startDate: Date;
  let endDate: Date = new Date(today);

  switch (period) {
    case "last-7-days":
      startDate = new Date(today);
      startDate.setDate(today.getDate() - 6); // Include today, so 6 days back
      break;

    case "last-30-days":
      startDate = new Date(today);
      startDate.setDate(today.getDate() - 29); // Include today, so 29 days back
      break;

    case "last-month": {
      // First day of previous month
      startDate = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      // Last day of previous month
      endDate = new Date(today.getFullYear(), today.getMonth(), 0);
      break;
    }

    case "quarterly": {
      // Get current quarter
      const currentQuarter = Math.floor(today.getMonth() / 3);
      const currentYear = today.getFullYear();
      // First day of current quarter
      startDate = new Date(currentYear, currentQuarter * 3, 1);
      // End date is today (current quarter up to today)
      endDate = new Date(today);
      break;
    }

    case "yearly": {
      // First day of current year
      startDate = new Date(today.getFullYear(), 0, 1);
      // End date is today (current year up to today)
      endDate = new Date(today);
      break;
    }

    case "date-range":
      if (dateRange?.from) {
        // A single clicked date (no `to` yet, e.g. picker closed after only
        // one click) is treated as a single-day range rather than falling
        // back to last-7-days, which silently ignored the user's selection.
        startDate = new Date(dateRange.from);
        endDate = new Date(dateRange.to ?? dateRange.from);
        startDate.setHours(0, 0, 0, 0);
        endDate.setHours(23, 59, 59, 999);
      } else {
        // Fallback to last 7 days if date range is not provided
        startDate = new Date(today);
        startDate.setDate(today.getDate() - 6);
      }
      break;
  }

  return {
    startDate: toAPIDateString(startDate),
    endDate: toAPIDateString(endDate),
  };
};

// ---------------------------------------------------------------------------
// Chart data bucketing (from dashboardChartDataUtils)
// ---------------------------------------------------------------------------

const MAX_LEGENDS = 12;
const DAYS_THRESHOLD_FOR_QUARTERLY = 88;

/** Day-wise API response: date string (YYYY-MM-DD) -> metric key -> number */
export type DayWiseChartData = Record<string, Record<string, number>>;

/** Chart-ready data: labels + one number array per metric */
export type BucketedChartData = {
  labels: string[];
  [metricKey: string]: string[] | number[];
};

/**
 * Get all date strings (YYYY-MM-DD) between start and end inclusive.
 */
function getDatesInRange(startDateStr: string, endDateStr: string): string[] {
  const start = fromISODateString(startDateStr);
  const end = fromISODateString(endDateStr);
  const dates: string[] = [];
  const current = new Date(start);
  current.setHours(0, 0, 0, 0);
  const endTime = end.getTime();
  while (current.getTime() <= endTime) {
    dates.push(toAPIDateString(current));
    current.setDate(current.getDate() + 1);
  }
  return dates;
}

/**
 * Sum numeric values from multiple day objects into one object (same keys).
 */
function sumMetricObjects(
  objects: Array<Record<string, number>>,
): Record<string, number> {
  const result: Record<string, number> = {};
  for (const obj of objects) {
    for (const [key, value] of Object.entries(obj)) {
      if (typeof value === "number" && !Number.isNaN(value)) {
        result[key] = (result[key] ?? 0) + value;
      }
    }
  }
  return result;
}

/**
 * Get unique metric keys from day-wise data (e.g. impressions, reach).
 */
function getMetricKeys(dayWiseData: DayWiseChartData): string[] {
  const keys = new Set<string>();
  for (const day of Object.values(dayWiseData)) {
    for (const k of Object.keys(day)) {
      if (typeof (day as Record<string, unknown>)[k] === "number") keys.add(k);
    }
  }
  return Array.from(keys);
}

/**
 * Build bucketed chart data from buckets: array of { label, dayDates } and day-wise data.
 */
function buildChartDataFromBuckets(
  buckets: Array<{ label: string; dateKeys: string[] }>,
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
): BucketedChartData {
  const labels = buckets.map((b) => b.label);
  const result: BucketedChartData = { labels };

  for (const key of metricKeys) {
    result[key] = buckets.map((bucket) => {
      const objects = bucket.dateKeys
        .filter((dk) => dayWiseData[dk])
        .map((dk) => dayWiseData[dk] as unknown as Record<string, number>);
      const summed = sumMetricObjects(objects);
      return summed[key] ?? 0;
    });
  }
  return result;
}

export type TFunc = (
  key: string,
  params?: Record<string, string | number>,
) => string;

/**
 * Shared "Next N days" date-range preset list (7/14/30/45/60/90 days from today),
 * used by both the campaign wizard's Step 1 date picker and the dashboard's
 * "Date range" picker so the two don't drift into separate definitions.
 */
export function createNDaysPresets(
  t: TFunc,
): Array<{ label: string; range: DateRange }> {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return [7, 14, 30, 45, 60, 90].map((n) => {
    const to = new Date(today);
    to.setDate(today.getDate() + n - 1);
    return {
      label: t("calendar.presets.nextNDays", { n }),
      range: { from: new Date(today), to },
    };
  });
}

/**
 * Format a date for day bucket label (e.g. "10 Feb" / "7月3日").
 */
function formatDayLabel(dateStr: string, t?: TFunc): string {
  const d = fromISODateString(dateStr);
  const day = d.getDate();
  if (t) {
    const month = t(`calendar.monthNamesShort.${d.getMonth()}`);
    return t("calendar.chartDayLabel", { day, month });
  }
  const month = d.toLocaleDateString("en-US", { month: "short" });
  return `${day} ${month}`;
}

/**
 * Format date range for bucket label (e.g. "10 Feb - 11 Feb").
 */
function formatDayRangeLabel(
  startStr: string,
  endStr: string,
  t?: TFunc,
): string {
  return `${formatDayLabel(startStr, t)} - ${formatDayLabel(endStr, t)}`;
}

function bucketByDays(
  dateKeys: string[],
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
  daysPerBucket: number,
  t?: TFunc,
): BucketedChartData {
  const buckets: Array<{ label: string; dateKeys: string[] }> = [];
  for (let i = 0; i < dateKeys.length; i += daysPerBucket) {
    const chunk = dateKeys.slice(i, i + daysPerBucket);
    const startStr = chunk[0];
    const endStr = chunk[chunk.length - 1];
    const label =
      chunk.length === 1
        ? formatDayLabel(startStr, t)
        : formatDayRangeLabel(startStr, endStr, t);
    buckets.push({ label, dateKeys: chunk });
  }
  return buildChartDataFromBuckets(buckets, dayWiseData, metricKeys);
}

const QUARTER_MONTH_RANGES: Record<number, string> = {
  1: "Jan - Mar",
  2: "Apr - Jun",
  3: "Jul - Sep",
  4: "Oct - Dec",
};

function getQuarter(dateStr: string): { year: number; quarter: number } {
  const d = fromISODateString(dateStr);
  const year = d.getFullYear();
  const quarter = Math.floor(d.getMonth() / 3) + 1;
  return { year, quarter };
}

/** Format quarter as "Jan - Mar 2026" / "2026年1月〜3月". */
function formatQuarterLabel(year: number, quarter: number, t?: TFunc): string {
  const range = t
    ? t(`calendar.chartQuarterRanges.${quarter}`)
    : (QUARTER_MONTH_RANGES[quarter] ?? `Q${quarter}`);
  return t
    ? t("calendar.chartRangeWithYear", { range, year })
    : `${range} ${year}`;
}

const HALF_YEAR_MONTH_RANGES: Record<number, string> = {
  1: "Jan - Jun",
  2: "Jul - Dec",
};

function getHalfYear(dateStr: string): { year: number; half: number } {
  const d = fromISODateString(dateStr);
  const year = d.getFullYear();
  const half = d.getMonth() < 6 ? 1 : 2;
  return { year, half };
}

/** Format half-year as "Jan - Jun 2026" / "2026年1月〜6月". */
function formatHalfYearLabel(year: number, half: number, t?: TFunc): string {
  const range = t
    ? t(`calendar.chartHalfYearRanges.${half}`)
    : (HALF_YEAR_MONTH_RANGES[half] ?? `H${half}`);
  return t
    ? t("calendar.chartRangeWithYear", { range, year })
    : `${range} ${year}`;
}

function getYear(dateStr: string): number {
  return fromISODateString(dateStr).getFullYear();
}

/**
 * Group date keys by quarter; labels show month range with year (e.g. "Jan - Mar 2026").
 */
function bucketByQuarters(
  dateKeys: string[],
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
  t?: TFunc,
): BucketedChartData {
  const groupByQuarter = new Map<string, string[]>();
  const labelByKey = new Map<string, string>();
  for (const d of dateKeys) {
    const { year, quarter } = getQuarter(d);
    const key = `${year}-Q${quarter}`;
    if (!groupByQuarter.has(key)) {
      groupByQuarter.set(key, []);
      labelByKey.set(key, formatQuarterLabel(year, quarter, t));
    }
    groupByQuarter.get(key)!.push(d);
  }
  const sortedKeys = Array.from(groupByQuarter.keys()).sort((a, b) =>
    a.localeCompare(b),
  );
  const buckets = sortedKeys.map((k) => ({
    label: labelByKey.get(k) ?? k,
    dateKeys: groupByQuarter.get(k)!,
  }));
  return buildChartDataFromBuckets(buckets, dayWiseData, metricKeys);
}

/**
 * Group date keys by half-year; labels show month range with year (e.g. "Jan - Jun 2026").
 */
function bucketByHalfYears(
  dateKeys: string[],
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
  t?: TFunc,
): BucketedChartData {
  const groupByHalf = new Map<string, string[]>();
  const labelByKey = new Map<string, string>();
  for (const d of dateKeys) {
    const { year, half } = getHalfYear(d);
    const key = `${year} H${half}`;
    if (!groupByHalf.has(key)) {
      groupByHalf.set(key, []);
      labelByKey.set(key, formatHalfYearLabel(year, half, t));
    }
    groupByHalf.get(key)!.push(d);
  }
  const sortedKeys = Array.from(groupByHalf.keys()).sort((a, b) =>
    a.localeCompare(b),
  );
  const buckets = sortedKeys.map((k) => ({
    label: labelByKey.get(k) ?? k,
    dateKeys: groupByHalf.get(k)!,
  }));
  return buildChartDataFromBuckets(buckets, dayWiseData, metricKeys);
}

/**
 * Group date keys by year.
 */
function bucketByYears(
  dateKeys: string[],
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
): BucketedChartData {
  const groupByYear = new Map<string, string[]>();
  for (const d of dateKeys) {
    const year = getYear(d);
    const key = String(year);
    if (!groupByYear.has(key)) groupByYear.set(key, []);
    groupByYear.get(key)!.push(d);
  }
  const sortedKeys = Array.from(groupByYear.keys()).sort((a, b) =>
    a.localeCompare(b),
  );
  const buckets = sortedKeys.map((k) => ({
    label: k,
    dateKeys: groupByYear.get(k)!,
  }));
  return buildChartDataFromBuckets(buckets, dayWiseData, metricKeys);
}

/**
 * When period is "date-range" and days > 88: choose quarterly, half-yearly, or yearly
 * so that the number of buckets does not exceed MAX_LEGENDS.
 */
function bucketByDateRangeWithQuarterlyFallback(
  startDateStr: string,
  endDateStr: string,
  dayWiseData: DayWiseChartData,
  metricKeys: string[],
  t?: TFunc,
): BucketedChartData {
  const dateKeys = getDatesInRange(startDateStr, endDateStr);
  if (dateKeys.length === 0) {
    return {
      labels: [],
      ...Object.fromEntries(metricKeys.map((k) => [k, []])),
    };
  }

  const byQuarter = bucketByQuarters(dateKeys, dayWiseData, metricKeys, t);
  if (byQuarter.labels.length <= MAX_LEGENDS) return byQuarter;

  const byHalf = bucketByHalfYears(dateKeys, dayWiseData, metricKeys, t);
  if (byHalf.labels.length <= MAX_LEGENDS) return byHalf;

  return bucketByYears(dateKeys, dayWiseData, metricKeys);
}

export interface BucketDayWiseChartDataOptions {
  /** Day-wise API data: { "2026-02-20": { impressions: 980, reach: 500 }, ... } */
  dayWiseData: DayWiseChartData;
  /** Selected period from dashboard filter */
  period: PeriodOption;
  /** Start date (YYYY-MM-DD). Required for date-range; for presets can be derived. */
  startDate: string;
  /** End date (YYYY-MM-DD). Required for date-range; for presets can be derived. */
  endDate: string;
  /** Optional Tolgee translate function for locale-aware axis labels. */
  t?: TFunc;
}

/**
 * Clubs day-wise chart data into at most 12 x-axis legends for any period.
 * - quarterly: bucket by quarter (Jan-Mar, Apr-Jun, etc.); if > 12 then half-year, then year.
 * - yearly: bucket by year.
 * - Otherwise (date-range, last-7-days, last-30-days, last-month): by days ≤ 88, else quarter/half/year.
 *
 * @returns Chart-ready data: { labels, impressions?, reach?, ... } with same metric keys as input.
 */
export function bucketDayWiseChartData(
  options: BucketDayWiseChartDataOptions,
): BucketedChartData {
  const { dayWiseData, period, startDate, endDate, t } = options;
  const metricKeys = getMetricKeys(dayWiseData);
  if (metricKeys.length === 0) {
    return {
      labels: [],
      ...Object.fromEntries(metricKeys.map((k) => [k, []])),
    };
  }

  const dateKeys = getDatesInRange(startDate, endDate);
  const days = dateKeys.length;

  if (days === 0) {
    return {
      labels: [],
      ...Object.fromEntries(metricKeys.map((k) => [k, []])),
    };
  }

  if (period === "quarterly") {
    const byQuarter = bucketByQuarters(dateKeys, dayWiseData, metricKeys, t);
    if (byQuarter.labels.length <= MAX_LEGENDS) return byQuarter;
    const byHalf = bucketByHalfYears(dateKeys, dayWiseData, metricKeys, t);
    if (byHalf.labels.length <= MAX_LEGENDS) return byHalf;
    return bucketByYears(dateKeys, dayWiseData, metricKeys);
  }

  if (period === "yearly") {
    return bucketByYears(dateKeys, dayWiseData, metricKeys);
  }

  if (days <= DAYS_THRESHOLD_FOR_QUARTERLY) {
    const daysPerBucket = Math.max(1, Math.ceil(days / MAX_LEGENDS));
    return bucketByDays(dateKeys, dayWiseData, metricKeys, daysPerBucket, t);
  }

  return bucketByDateRangeWithQuarterlyFallback(
    startDate,
    endDate,
    dayWiseData,
    metricKeys,
    t,
  );
}
