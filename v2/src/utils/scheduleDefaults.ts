/**
 * Persistence + mapping helpers for the "Restore Default" feature of the
 * schedule edit drawer.
 *
 * There is ONE default per inventory: the first saved schedule (order 1). Its
 * values are captured once and persisted in localStorage so they survive save +
 * drawer close + page reload. Clicking "Restore Default" from any schedule's
 * edit drawer repopulates the form with this single default schedule.
 *
 * Scoped per (campaignId, inventoryId).
 */
import { InventorySchedule } from "src/types/inventory.types";

import {
  convertDayName,
  detectSchedulePattern,
  formatDateToYYYYMMDD,
  parseDateFromBookingMatrix,
  SchedulePattern,
} from "./schedule.utils";

const KEY_PREFIX = "schedule_default";

const buildKey = (campaignId: string, inventoryId: string): string =>
  `${KEY_PREFIX}_${campaignId}_${inventoryId}`;

/**
 * Reads the persisted default schedule for an inventory, or null if none.
 */
export const getDefaultSchedule = (
  campaignId?: string,
  inventoryId?: string,
): InventorySchedule | null => {
  if (!campaignId || !inventoryId) return null;
  try {
    const raw = localStorage.getItem(buildKey(campaignId, inventoryId));
    return raw ? (JSON.parse(raw) as InventorySchedule) : null;
  } catch {
    return null;
  }
};

/**
 * Captures the default schedule the FIRST time only. Subsequent calls for the
 * same inventory are no-ops, so the original first-saved values are never
 * overwritten by later edits.
 */
export const captureDefaultSchedule = (
  campaignId: string | undefined,
  inventoryId: string | undefined,
  schedule: InventorySchedule,
): void => {
  if (!campaignId || !inventoryId) return;
  const key = buildKey(campaignId, inventoryId);
  try {
    if (localStorage.getItem(key) != null) return; // capture once only
    localStorage.setItem(key, JSON.stringify(schedule));
  } catch {
    // ignore storage errors (quota / disabled storage)
  }
};

/**
 * Removes the persisted default schedule for an inventory (e.g. after the
 * default schedule is deleted).
 */
export const clearDefaultSchedule = (
  campaignId?: string,
  inventoryId?: string,
): void => {
  if (!campaignId || !inventoryId) return;
  try {
    localStorage.removeItem(buildKey(campaignId, inventoryId));
  } catch {
    // ignore storage errors
  }
};

const sameDays = (a: string[], b: string[]): boolean => {
  if (a.length !== b.length) return false;
  const sa = [...a].sort((x, y) => x.localeCompare(y));
  const sb = [...b].sort((x, y) => x.localeCompare(y));
  return sa.every((v, i) => v === sb[i]);
};

const sameBookingMatrix = (
  a: Record<string, number[]>,
  b: Record<string, number[]>,
): boolean => {
  const aKeys = Object.keys(a);
  if (aKeys.length !== Object.keys(b).length) return false;
  return aKeys.every((key) => {
    if (!b[key]) return false;
    const av = [...a[key]].sort((x, y) => x - y);
    const bv = [...b[key]].sort((x, y) => x - y);
    return av.length === bv.length && av.every((v, i) => v === bv[i]);
  });
};

/**
 * Compares the schedule-defining fields (dates, days, hours, duration, spots)
 * of two schedules. Used to decide whether an opened schedule still matches the
 * default snapshot (→ show "Default") or has been customised (→ "Custom").
 * Ignores ids, names, order, pricing and computed metrics (impressions, sov…).
 */
export const schedulesMatch = (
  a: InventorySchedule | null | undefined,
  b: InventorySchedule | null | undefined,
): boolean => {
  if (!a || !b) return false;
  return (
    a.startDate === b.startDate &&
    a.endDate === b.endDate &&
    (a.duration ?? null) === (b.duration ?? null) &&
    (a.spotsPerLoop ?? null) === (b.spotsPerLoop ?? null) &&
    (a.spotsPerHour ?? null) === (b.spotsPerHour ?? null) &&
    sameDays(a.scheduleDays ?? [], b.scheduleDays ?? []) &&
    sameBookingMatrix(a.bookingMatrix ?? {}, b.bookingMatrix ?? {})
  );
};

export interface ScheduleFormState {
  scheduleDate: { from: Date | null; to: Date | null };
  selectedDays: string[];
  duration: string;
  spots: { perLoop: number; perHour: number };
  selectedHours: Set<string>;
}

/**
 * Maps an InventorySchedule into the drawer's form state shape. Mirrors the
 * fallbacks used by the drawer's init effect so restored values match what was
 * first displayed.
 */
export const mapScheduleToFormState = (
  schedule: InventorySchedule,
  fallbackSpotsPerHour: number,
): ScheduleFormState => {
  const selectedHours = new Set<string>();
  if (schedule.bookingMatrix) {
    Object.entries(schedule.bookingMatrix).forEach(([dateStr, hours]) => {
      const dateKey = formatDateToYYYYMMDD(parseDateFromBookingMatrix(dateStr));
      hours.forEach((hour) => selectedHours.add(`${dateKey}-${hour}`));
    });
  }

  return {
    scheduleDate: {
      from: schedule.startDate ? new Date(schedule.startDate) : null,
      to: schedule.endDate ? new Date(schedule.endDate) : null,
    },
    selectedDays: schedule.scheduleDays
      ? schedule.scheduleDays.map(convertDayName)
      : [],
    duration: schedule.duration ? `${schedule.duration} Sec` : "15 Sec",
    spots: {
      perLoop: schedule.spotsPerLoop ?? 1,
      perHour: schedule.spotsPerHour ?? fallbackSpotsPerHour,
    },
    selectedHours,
  };
};

/**
 * Resolves which pattern dropdown value represents a saved schedule when its
 * edit drawer opens: "default" when it still matches the inventory default
 * snapshot, otherwise the matching generated pattern, otherwise "custom".
 */
export const resolveSchedulePattern = (
  schedule: InventorySchedule,
  defaultSnapshot: InventorySchedule | null | undefined,
  availableDays: string[],
  fallbackSpotsPerHour: number,
): SchedulePattern => {
  if (schedulesMatch(schedule, defaultSnapshot)) return "default";
  const form = mapScheduleToFormState(schedule, fallbackSpotsPerHour);
  return detectSchedulePattern(
    form.scheduleDate,
    form.selectedDays,
    form.selectedHours,
    availableDays,
  );
};
