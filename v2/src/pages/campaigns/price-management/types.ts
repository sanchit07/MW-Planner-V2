/**
 * An inline proposed-price edit that has not been persisted yet. Held in
 * memory (keyed by row) until the user saves from the Pricing Summary
 * drawer - editing a cell only stages a change, it never calls the API by
 * itself.
 */
export interface PendingPriceEdit {
  newPrice: number;
  /** Last-saved price this edit replaces, so the summary can show the delta. */
  originalPrice: number;
  /** Composite id the price endpoint is keyed on. */
  campaignInventoryScheduleId: string;
  /** Schedule id, present when the edit is on a schedule (child) row. */
  scheduleId?: string;
  isInventoryRow: boolean;
  /** Inventory this edit belongs to - the row id for an inventory edit, the
   * parent id for a schedule edit. Used to drop schedule deltas when the
   * parent inventory is edited too. */
  inventoryId: string;
  /** Inventory or schedule name, for display in the summary drawer. */
  label: string;
}

/** Pending edits keyed by row key (`inventoryId` or `parentId:scheduleId`). */
export type PendingPriceEdits = Record<string, PendingPriceEdit>;

/**
 * Net change the staged edits make to the campaign's proposed total.
 *
 * An inventory's proposed price is already the sum of its schedules, so editing
 * an inventory and one of its own schedules would count twice. When both exist
 * for the same inventory, only the inventory-level edit contributes.
 */
export const getPendingPriceDelta = (edits: PendingPriceEdits): number => {
  const values = Object.values(edits);
  const editedInventoryIds = new Set(
    values
      .filter((edit) => edit.isInventoryRow)
      .map((edit) => edit.inventoryId),
  );

  return values
    .filter(
      (edit) =>
        edit.isInventoryRow || !editedInventoryIds.has(edit.inventoryId),
    )
    .reduce((total, edit) => total + (edit.newPrice - edit.originalPrice), 0);
};

/**
 * Net change the staged *schedule* edits make to one inventory's proposed
 * price. An inventory's price is the sum of its schedules, so editing a
 * schedule has to move the parent row too.
 */
export const getPendingScheduleDelta = (
  edits: PendingPriceEdits,
  inventoryId: string,
): number =>
  Object.values(edits)
    .filter((edit) => !edit.isInventoryRow && edit.inventoryId === inventoryId)
    .reduce((total, edit) => total + (edit.newPrice - edit.originalPrice), 0);

/** A schedule taking part in a pro-rata split. */
export interface DistributableSchedule {
  id: string;
  currentPrice: number;
}

/** One schedule's resulting price after a split. */
export interface DistributedSchedule {
  id: string;
  newPrice: number;
}

const CENTS = 100;
const roundToCents = (value: number): number =>
  Math.round(value * CENTS) / CENTS;

/**
 * Split a new inventory price across its schedules by each schedule's share of
 * the current total.
 *
 * Pro-rata rather than an equal split, because each schedule is reduced by a
 * fraction of *itself* - so no schedule can be driven negative when the new
 * total is >= 0, and no leftover has to be redistributed. An equal split would
 * push small schedules below zero and need an iterative waterfall to fix.
 *
 * Rounding is applied per schedule, then the largest schedule absorbs the
 * remainder so the parts always add up to `newTotal` exactly.
 */
export const distributeProRata = (
  newTotal: number,
  schedules: DistributableSchedule[],
): DistributedSchedule[] => {
  if (schedules.length === 0) return [];

  const target = roundToCents(Math.max(newTotal, 0));
  const currentTotal = schedules.reduce(
    (sum, schedule) => sum + Math.max(schedule.currentPrice, 0),
    0,
  );

  // Nothing to take a share of - fall back to an equal split.
  const distributed: DistributedSchedule[] =
    currentTotal <= 0
      ? schedules.map((schedule) => ({
          id: schedule.id,
          newPrice: roundToCents(target / schedules.length),
        }))
      : schedules.map((schedule) => ({
          id: schedule.id,
          newPrice: roundToCents(
            (Math.max(schedule.currentPrice, 0) / currentTotal) * target,
          ),
        }));

  // Rounding can leave the parts a cent or two off the target. Put the
  // difference on the largest schedule, where it is least visible.
  const distributedTotal = distributed.reduce(
    (sum, schedule) => sum + schedule.newPrice,
    0,
  );
  const remainder = roundToCents(target - distributedTotal);

  if (remainder !== 0) {
    let largestIndex = 0;
    distributed.forEach((schedule, index) => {
      if (schedule.newPrice > distributed[largestIndex].newPrice) {
        largestIndex = index;
      }
    });
    distributed[largestIndex] = {
      ...distributed[largestIndex],
      newPrice: roundToCents(distributed[largestIndex].newPrice + remainder),
    };
  }

  return distributed;
};
