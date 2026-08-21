import type { CSSProperties } from "react";

import { PresentationTheme } from "./types";

const DEFAULT_PRIMARY_CSS_VAR = "var(--color-mw-primary-500)";

export function getThemePrimaryColorStyle(
  theme?: PresentationTheme | null,
): CSSProperties {
  return {
    color: theme ? `var(${theme.colors.primary})` : DEFAULT_PRIMARY_CSS_VAR,
  };
}

export function getThemePrimaryBackgroundStyle(
  theme?: PresentationTheme | null,
): CSSProperties {
  return {
    backgroundColor: theme
      ? `var(${theme.colors.primary})`
      : DEFAULT_PRIMARY_CSS_VAR,
  };
}

/** Weekday keys (backend) paired with their short display label. */
export const WEEKDAYS: Array<{ key: string; short: string }> = [
  { key: "MONDAY", short: "Mon" },
  { key: "TUESDAY", short: "Tue" },
  { key: "WEDNESDAY", short: "Wed" },
  { key: "THURSDAY", short: "Thu" },
  { key: "FRIDAY", short: "Fri" },
  { key: "SATURDAY", short: "Sat" },
  { key: "SUNDAY", short: "Sun" },
];

export interface AudienceActivityDay {
  day: string; // short weekday label (Mon…Sun)
  hours: number; // distinct active hours that weekday (0–24)
  sharePct: number; // share of the week's total active hours
  isPeak: boolean; // true only for the single peak day (never on ties)
}

export interface AudienceActivity {
  bars: AudienceActivityDay[];
  /** The single busiest weekday, or null when days tie (incl. flat 24/7). */
  peakDay: string | null;
  /** At least one inventory runs a non-24/7 schedule. */
  hasCustomSchedule: boolean;
  /** Any bookable-hour data exists across the plan's schedules. */
  hasScheduleData: boolean;
}

/** Weekday index (Mon=0 … Sun=6) from a "YYYY-MM-DD" date string, or null. */
const weekdayIndex = (dateStr: string): number | null => {
  const [y, m, d] = dateStr.split("-").map(Number);
  if (!y || !m || !d) return null;
  const date = new Date(y, m - 1, d);
  if (isNaN(date.getTime())) return null;
  // JS getDay(): 0=Sun … 6=Sat → shift so Mon=0 … Sun=6.
  return (date.getDay() + 6) % 7;
};

interface ScheduleBearing {
  schedules?: Array<{ bookingMatrix?: Record<string, number[]> | null }>;
}

/**
 * Audience Activity by Day (PRD §10.5.2). The selected inventories' daypart
 * booking-matrices are OR-unioned into a single 7-day × hour grid: a weekday's
 * value is the count of DISTINCT hours active across all inventories and dates
 * (overlaps count once, capped at 24), expressed as a share of the week's total
 * active hours. With no schedule data the week is a flat default 24/7 (each day
 * ~14.3%). The peak day is highlighted only when one day strictly wins; every
 * tie (including flat 24/7) yields peakDay = null.
 */
export function computeAudienceActivityByDay(
  locations: ScheduleBearing[] = [],
): AudienceActivity {
  const grid: Array<Set<number>> = WEEKDAYS.map(() => new Set<number>());

  locations.forEach((item) => {
    (item.schedules || []).forEach((schedule) => {
      const matrix = schedule.bookingMatrix;
      if (!matrix) return;
      Object.entries(matrix).forEach(([dateStr, hours]) => {
        const wd = weekdayIndex(dateStr);
        if (wd === null || !Array.isArray(hours)) return;
        hours.forEach((h) => {
          if (typeof h === "number" && h >= 0 && h <= 23) grid[wd].add(h);
        });
      });
    });
  });

  const perDay = grid.map((set) => set.size);
  const total = perDay.reduce((sum, n) => sum + n, 0);

  // No schedule data → default 24/7 assumption → flat, no peak.
  if (total === 0) {
    return {
      bars: WEEKDAYS.map(({ short }) => ({
        day: short,
        hours: 24,
        sharePct: 100 / WEEKDAYS.length,
        isPeak: false,
      })),
      peakDay: null,
      hasCustomSchedule: false,
      hasScheduleData: false,
    };
  }

  const max = Math.max(...perDay);
  const daysAtMax = perDay.filter((n) => n === max).length;
  const peakIndex = daysAtMax === 1 ? perDay.indexOf(max) : -1;
  const hasCustomSchedule = perDay.some((n) => n !== 24);

  return {
    bars: WEEKDAYS.map(({ short }, i) => ({
      day: short,
      hours: perDay[i],
      sharePct: (perDay[i] / total) * 100,
      isPeak: i === peakIndex,
    })),
    peakDay: peakIndex >= 0 ? WEEKDAYS[peakIndex].short : null,
    hasCustomSchedule,
    hasScheduleData: true,
  };
}

export interface DOOHRollupCellCount {
  hour: number;
  count: number;
}

export interface DOOHRollupRowCounts {
  day: string;
  cells: DOOHRollupCellCount[];
}

export interface DOOHRollupGrid {
  rows: DOOHRollupRowCounts[];
  maxCount: number;
  totalSchedules: number;
}

/**
 * Plan-level DOOH rollup heatmap: counts, per (weekday, hour), how many
 * DISTINCT schedules across every digital inventory are active — a schedule
 * spanning many weeks only counts once per weekday+hour cell, not once per
 * date. `totalSchedules` counts every schedule segment encountered
 * (regardless of whether it has bookingMatrix data).
 */
export function computeDOOHRollupHeatmap(
  locations: ScheduleBearing[] = [],
): DOOHRollupGrid {
  const grid: number[][] = WEEKDAYS.map(() => new Array(24).fill(0));
  let totalSchedules = 0;

  locations.forEach((item) => {
    (item.schedules || []).forEach((schedule) => {
      totalSchedules += 1;
      const matrix = schedule.bookingMatrix;
      if (!matrix) return;

      // Dedup within this one schedule first, so a schedule active every
      // Monday for 8 weeks still only contributes 1 to the Mon/hour cell.
      const touched = new Set<string>();
      Object.entries(matrix).forEach(([dateStr, hours]) => {
        const wd = weekdayIndex(dateStr);
        if (wd === null || !Array.isArray(hours)) return;
        hours.forEach((h) => {
          if (typeof h === "number" && h >= 0 && h <= 23) {
            touched.add(`${wd}-${h}`);
          }
        });
      });
      touched.forEach((key) => {
        const [wd, h] = key.split("-").map(Number);
        grid[wd][h] += 1;
      });
    });
  });

  const maxCount = Math.max(1, ...grid.flat());

  return {
    rows: WEEKDAYS.map(({ short }, i) => ({
      day: short,
      cells: grid[i].map((count, hour) => ({ hour, count })),
    })),
    maxCount,
    totalSchedules,
  };
}

export interface DeliveryBin {
  label: string;
  value: number;
  reach: number;
}

export interface ExpectedDelivery {
  granularity: "weekly" | "monthly";
  bins: DeliveryBin[];
  peakLabel: string;
  peakValue: number;
}

/** Wave amplitude for the impressions series; reach uses half this amplitude. */
const DELIVERY_WAVE_AMPLITUDE = 0.15;

/**
 * Equal split across bins, modulated by a sine wave for visual variation,
 * rescaled so the bins still sum to `total` (matching Math.round drift aside).
 */
const distributeWithWave = (
  binCount: number,
  total: number,
  amplitude: number,
): number[] => {
  const weights = Array.from(
    { length: binCount },
    (_, i) => 1 + Math.sin(i * 0.6) * amplitude,
  );
  const weightSum = weights.reduce((sum, w) => sum + w, 0);
  return weights.map((w) =>
    weightSum > 0 ? Math.round((total * w) / weightSum) : 0,
  );
};

const DAY_MS = 86_400_000;
// Flights up to 8 weeks bin weekly; longer flights bin monthly.
const WEEKLY_MAX_DAYS = 56;

/** Parse a "YYYY-MM-DD" string to a local-midnight Date (no TZ drift). */
const parseLocalDate = (value?: string): Date | null => {
  if (!value) return null;
  const [y, m, d] = value.split("-").map(Number);
  if (!y || !m || !d) return null;
  const date = new Date(y, m - 1, d);
  return isNaN(date.getTime()) ? null : date;
};

const wholeDays = (from: Date, to: Date): number =>
  Math.round((to.getTime() - from.getTime()) / DAY_MS) + 1;

/**
 * Expected-delivery bins for the Goals & KPIs chart. No per-period
 * impression/reach data exists on the backend, so each series is an equal
 * split across bins modulated by a sine wave (visual variation only), then
 * rescaled so bins still sum to their series total. Reach uses half the
 * impressions wave's amplitude. Granularity is weekly for flights ≤ 8 weeks,
 * monthly otherwise; peak bin is the highest-impression bin.
 */
export function computeExpectedDelivery(
  startDate?: string,
  endDate?: string,
  totalImpressions = 0,
  totalReach = 0,
): ExpectedDelivery {
  const start = parseLocalDate(startDate);
  const end = parseLocalDate(endDate);
  if (!start || !end || end < start) {
    return { granularity: "monthly", bins: [], peakLabel: "", peakValue: 0 };
  }

  const durationDays = wholeDays(start, end);
  const weekly = durationDays <= WEEKLY_MAX_DAYS;

  const labels: string[] = [];
  if (weekly) {
    let cursor = new Date(start);
    while (cursor <= end) {
      labels.push(
        cursor.toLocaleDateString("en-US", {
          month: "short",
          day: "numeric",
        }),
      );
      cursor = new Date(cursor.getTime() + 7 * DAY_MS);
    }
  } else {
    let cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    while (cursor <= end) {
      labels.push(
        cursor.toLocaleDateString("en-US", {
          month: "short",
          year: "numeric",
        }),
      );
      cursor = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1);
    }
  }

  const impressionValues = distributeWithWave(
    labels.length,
    totalImpressions,
    DELIVERY_WAVE_AMPLITUDE,
  );
  const reachValues = distributeWithWave(
    labels.length,
    totalReach,
    DELIVERY_WAVE_AMPLITUDE / 2,
  );
  const bins: DeliveryBin[] = labels.map((label, i) => ({
    label,
    value: impressionValues[i],
    reach: reachValues[i],
  }));

  const peak = bins.reduce<DeliveryBin | null>(
    (mx, b) => (!mx || b.value > mx.value ? b : mx),
    null,
  );

  return {
    granularity: weekly ? "weekly" : "monthly",
    bins,
    peakLabel: peak?.label || "",
    peakValue: peak?.value || 0,
  };
}

// ─── DOOH weekly Gantt calendar ─────────────────────────────────────────────

export interface DOOHCalendarDay {
  /** "YYYY-MM-DD", for matching against a schedule segment's activeDates. */
  date: string;
  /** Single-letter weekday label, S M T W T F S (Sun-based). */
  dayLetter: string;
}

export interface DOOHCalendarWeek {
  label: string;
  days: DOOHCalendarDay[];
}

const toDateKey = (date: Date): string =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

const SUN_BASED_DAY_LETTERS = ["S", "M", "T", "W", "T", "F", "S"];

/**
 * Real Sun–Sat calendar weeks spanning the campaign flight, padded out to
 * full weeks at both ends (e.g. a Wed-start flight's first week still shows
 * Sun/Mon/Tue as empty columns). Unlike computeExpectedDelivery's arbitrary
 * 7-day-from-start bins, these are actual calendar weeks so day cells can be
 * matched directly against a schedule's bookingMatrix date keys.
 */
export function buildDOOHCalendarWeeks(
  startDate?: string,
  endDate?: string,
): DOOHCalendarWeek[] {
  const start = parseLocalDate(startDate);
  const end = parseLocalDate(endDate);
  if (!start || !end || end < start) return [];

  const gridStart = new Date(start);
  gridStart.setDate(gridStart.getDate() - gridStart.getDay());
  const gridEnd = new Date(end);
  gridEnd.setDate(gridEnd.getDate() + (6 - gridEnd.getDay()));

  const weeks: DOOHCalendarWeek[] = [];
  const cursor = new Date(gridStart);
  let weekIndex = 1;
  while (cursor <= gridEnd) {
    const days: DOOHCalendarDay[] = [];
    for (let i = 0; i < 7; i++) {
      days.push({
        date: toDateKey(cursor),
        dayLetter: SUN_BASED_DAY_LETTERS[i],
      });
      cursor.setDate(cursor.getDate() + 1);
    }
    weeks.push({ label: `Wk ${weekIndex}`, days });
    weekIndex++;
  }
  return weeks;
}

// ─── Goal roadmap & plan reasons (PRD §10.5.7) ──────────────────────────────

export type RoadmapUnit = "day" | "week" | "month" | "quarter";
export type RoadmapPhase = "ramp" | "mid_flight" | "closeout";

export interface GoalMilestone {
  phase: RoadmapPhase;
  unit: RoadmapUnit;
  /** 1 | 2 | 3 — the third's ordinal (label reads "{Unit} {ordinal}"). */
  ordinal: number;
  /** Calendar span of this third, e.g. "Apr 1 – Apr 30". */
  dateRange: string;
  /** Achievement % — may exceed 100 when the plan over-delivers vs target. */
  pct: number;
}

// Pre-computed S-curve pacing t²(3−2t) at t = 1/3, 2/3, 1.
const PACING = [0.26, 0.74, 1.0];
const ROADMAP_PHASES: RoadmapPhase[] = ["ramp", "mid_flight", "closeout"];

/** Label unit chosen once for the whole flight, from total duration. */
const pickRoadmapUnit = (durationDays: number): RoadmapUnit => {
  if (durationDays <= 14) return "day";
  if (durationDays <= 90) return "week";
  if (durationDays <= 365) return "month";
  return "quarter";
};

const fmtDay = (d: Date): string =>
  d.toLocaleDateString("en-US", { month: "short", day: "numeric" });

/**
 * Always-three-milestone goal roadmap (Ramp → Mid-flight → Closeout). The
 * flight is split into three equal thirds by calendar days; each third is
 * labelled "{Unit} {ordinal}" (unit picked once from total duration) with its
 * calendar date range. Achievement % follows a fixed S-curve pacing
 * (0.26 / 0.74 / 1.00) scaled by forecast ÷ target; when no goal target is set,
 * target defaults to forecast (closeout lands at 100%). Card height is constant
 * — every campaign renders exactly three rows. PRD §10.5.7.
 */
export function computeGoalRoadmap(
  startDate?: string,
  endDate?: string,
  forecast = 0,
  target = 0,
): GoalMilestone[] {
  const start = parseLocalDate(startDate);
  const end = parseLocalDate(endDate);
  if (!start || !end || end < start) return [];

  const durationDays = wholeDays(start, end);
  const unit = pickRoadmapUnit(durationDays);

  // Day index (1-based) → local Date, clamped to the flight end.
  const dayDate = (n: number): Date => {
    const d = new Date(start);
    d.setDate(start.getDate() + Math.min(Math.max(n, 1), durationDays) - 1);
    return d;
  };

  // Three equal thirds by calendar days.
  const e1 = Math.max(1, Math.floor(durationDays / 3));
  const e2 = Math.max(e1 + 1, Math.floor((2 * durationDays) / 3));
  const bounds: Array<[number, number]> = [
    [1, e1],
    [e1 + 1, e2],
    [e2 + 1, durationDays],
  ];

  const ratio = target > 0 ? forecast / target : 1;

  return ROADMAP_PHASES.map((phase, i) => ({
    phase,
    unit,
    ordinal: i + 1,
    dateRange: `${fmtDay(dayDate(bounds[i][0]))} – ${fmtDay(dayDate(bounds[i][1]))}`,
    pct: Math.round(PACING[i] * ratio * 100),
  }));
}

/** Forecast value for the campaign's goal metric (mirrors Goals & KPIs). */
export function resolveGoalForecast(
  goalType: string | undefined,
  perf?: {
    estimatedImpression?: number;
    estimatedAdPlays?: number;
    sov?: number;
    estimatedReach?: number;
  } | null,
): number {
  switch ((goalType || "").toUpperCase()) {
    case "IMPRESSIONS":
      return perf?.estimatedImpression || 0;
    case "ADPLAYS":
      return perf?.estimatedAdPlays || 0;
    case "SOV":
      return perf?.sov || 0;
    default:
      return perf?.estimatedReach || 0;
  }
}

export interface PlanReason {
  /** i18n subkey under `media_plan.why_plan`. */
  key: string;
  params: Record<string, string | number>;
}

/**
 * The three "Why This Plan Works" reasons in fixed priority order:
 * geography → economics → audience. Reason 3 falls back demographics → venue
 * types → generic benchmark, so the card always shows three. Shared by the
 * on-screen card and the PPTX export so the two always match. PRD §10.5.7.
 */
export function buildPlanReasons(input: {
  topCityName: string;
  inventories: number;
  impressions: number;
  cpm: number;
  demographicSegments: string[];
  venueTypes: string[];
  compact: (n: number) => string;
  currency: (n: number) => string;
}): PlanReason[] {
  const reasons: PlanReason[] = [
    {
      key: "reason_geography",
      params: {
        inventories: input.inventories,
        market: input.topCityName,
        impressions: input.compact(input.impressions),
      },
    },
    {
      key: "reason_scale",
      params: {
        impressions: input.compact(input.impressions),
        inventories: input.inventories,
        cpm: input.currency(input.cpm),
      },
    },
  ];
  if (input.demographicSegments.length >= 1) {
    const [a, b] = input.demographicSegments;
    reasons.push({
      key: "reason_audience",
      params: { segments: b ? `${a} + ${b}` : a },
    });
  } else if (input.venueTypes.length >= 1) {
    const [a, b] = input.venueTypes;
    reasons.push({
      key: "reason_audience_venue",
      params: { venues: b ? `${a} + ${b}` : a },
    });
  } else {
    reasons.push({ key: "reason_audience_generic", params: {} });
  }
  return reasons.slice(0, 3);
}

/** Humanise a demographic/venue code: "18_24" → "18–24", "young_adult" →
 * "Young Adult". Age ranges keep their numeric form with an en-dash. */
export function humaniseSegment(code: string): string {
  if (/^\d+[_-]\d+$/.test(code)) return code.replace(/[_-]/, "–");
  return code
    .split(/[-_]/)
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase())
    .join(" ");
}

// ─── Targeting Concentration Index (PRD §10.5.x) ────────────────────────────

export type ConcentrationKey =
  | "age_gender"
  | "income_group"
  | "behavior"
  | "interest";

export interface ConcentrationRow {
  key: ConcentrationKey; // i18n label key under audience_trends.*
  index: number; // comparative index in the 1.4×–3.6× range
}

/** Category value-arrays feeding the four concentration rows. */
export interface ConcentrationInput {
  ageGender?: string[];
  income?: string[];
  behavior?: string[];
  interest?: string[];
}

export const CONCENTRATION_INDEX_MAX = 3.6;

/** Deterministic string hash (h = h·31 + charCode). Same input → same number. */
const hashSeed = (seed: string): number => {
  let h = 0;
  for (let i = 0; i < seed.length; i++) {
    h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return h;
};

/** Map a hash into the 1.4×–3.6× band: (hash mod 220) ÷ 100 + 1.4, 1 decimal. */
const concentrationIndexFor = (seed: string): number =>
  Math.round(((hashSeed(seed) % 220) / 100 + 1.4) * 10) / 10;

/**
 * Targeting Concentration Index — a planning heuristic, not measured research.
 * Four fixed rows (Age & Gender / Income Group / Behavior / Interest). For each
 * axis the targeted segment tokens are joined with "|" and hashed into the
 * 1.4×–3.6× reach-lift band; an untargeted axis falls back to a campaign-specific
 * seed ("campaign-{id}-{axis}") so its number still varies per campaign. Callers
 * suppress the whole block when the plan has no audience targeting at all.
 */
export function computeConcentrationIndex(
  categories: ConcentrationInput = {},
  campaignId = "",
): ConcentrationRow[] {
  const rows: Array<{
    key: ConcentrationKey;
    axis: string;
    vals: string[];
  }> = [
    { key: "age_gender", axis: "age_gender", vals: categories.ageGender || [] },
    { key: "income_group", axis: "income", vals: categories.income || [] },
    { key: "behavior", axis: "behavior", vals: categories.behavior || [] },
    { key: "interest", axis: "interest", vals: categories.interest || [] },
  ];
  return rows.map((r) => {
    const seed = r.vals.length
      ? r.vals.join("|")
      : `campaign-${campaignId}-${r.axis}`;
    return { key: r.key, index: concentrationIndexFor(seed) };
  });
}

export type TrafficLevel = "high" | "medium" | "low";

export function getTrafficLevelFromPercentage(value: number): TrafficLevel {
  if (value >= 50) return "high";
  if (value >= 25) return "medium";
  return "low";
}

const CSS_VAR_FALLBACKS: Record<string, string> = {
  "--color-mw-success-500": "#2d7d32",
  "--color-mw-warning-500": "#f9a825",
  "--color-mw-error-500": "#c52828",
  "--color-mw-primary-500": "#2176cc",
  "--color-mw-primary-300": "#6898c9",
};

export function getCssVariableValue(cssVar: string): string {
  if (typeof window !== "undefined" && typeof document !== "undefined") {
    const value = getComputedStyle(document.documentElement)
      .getPropertyValue(cssVar)
      .trim();
    if (value) return value;
  }
  return CSS_VAR_FALLBACKS[cssVar] ?? "#2176cc";
}
