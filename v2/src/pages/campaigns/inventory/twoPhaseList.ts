import type { InventoryItem } from "../../../types/inventory.types";

/**
 * Two-phase manual-edit inventory list: page through the campaign's
 * selected inventories first (from /selected-inventory), then continue with the
 * browse list (/filter). Selected therefore always render on top.
 */
export type ListPhase = "selected" | "filter";

/** Mark every item as selected (used for /selected-inventory phase items). */
export function markAllSelected(items: InventoryItem[]): InventoryItem[] {
  return items.map((item) => ({
    ...item,
    detail: { ...item.detail, isSelected: true },
  }));
}

/**
 * Drop items whose `referenceId` is in `refIds` (the already-shown selected
 * set) so the /filter phase never repeats a selected inventory. Returns the
 * same array reference when there is nothing to exclude.
 */
export function excludeByReferenceId(
  items: InventoryItem[],
  refIds: Set<string>,
): InventoryItem[] {
  if (refIds.size === 0) return items;
  return items.filter((item) => !refIds.has(item.detail.referenceId));
}
