import { ColumnSort, CustomSortFn, BaseRowData } from "./types";

/**
 * Sorting utilities
 * Following Single Responsibility Principle
 */

/**
 * Default sort function for primitive values
 */
export function defaultSortFn(
  a: unknown,
  b: unknown,
  direction: "asc" | "desc",
): number {
  // Handle null/undefined
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;

  // Handle numbers
  if (typeof a === "number" && typeof b === "number") {
    return direction === "asc" ? a - b : b - a;
  }

  // Handle dates
  if (a instanceof Date && b instanceof Date) {
    return direction === "asc"
      ? a.getTime() - b.getTime()
      : b.getTime() - a.getTime();
  }

  // Handle strings (case-insensitive)
  const aStr = String(a).toLowerCase();
  const bStr = String(b).toLowerCase();

  if (direction === "asc") {
    return aStr.localeCompare(bStr);
  } else {
    return bStr.localeCompare(aStr);
  }
}

/**
 * Sort data based on sort state
 */
export function sortData<T extends BaseRowData>(
  data: T[],
  sortState: ColumnSort[],
  columns: Array<{
    key: string;
    sortKey?: string;
    customSort?: CustomSortFn<T>;
  }>,
  sortMode: "client" | "remote" = "client",
): T[] {
  if (sortMode === "remote" || sortState.length === 0) {
    return data;
  }

  // Clone data for sorting
  const dataToSort = [...data];

  // Apply sorts in order (for multi-sort)
  return dataToSort.sort((a, b) => {
    for (const sort of sortState) {
      const column = columns.find(
        (col) => (col.sortKey || col.key) === sort.key,
      );

      let result = 0;

      if (column?.customSort) {
        // Use custom sort function
        result = column.customSort(a, b, sort.direction);
      } else {
        // Use default sort
        const aValue = a[sort.key as keyof T];
        const bValue = b[sort.key as keyof T];
        result = defaultSortFn(aValue, bValue, sort.direction);
      }

      // If values are not equal, return the result
      if (result !== 0) return result;

      // Otherwise, continue to next sort criteria
    }

    return 0; // All sort criteria equal
  });
}

/**
 * Handle sort change
 */
export function handleSortChange(
  columnKey: string,
  currentSortState: ColumnSort[],
  multiSort: boolean,
): ColumnSort[] {
  const existingSortIndex = currentSortState.findIndex(
    (s) => s.key === columnKey,
  );

  let newSortState: ColumnSort[];

  if (existingSortIndex === -1) {
    // Column not sorted yet - add ascending sort
    if (multiSort) {
      newSortState = [
        ...currentSortState,
        { key: columnKey, direction: "asc" },
      ];
    } else {
      newSortState = [{ key: columnKey, direction: "asc" }];
    }
  } else {
    const currentDirection = currentSortState[existingSortIndex].direction;

    if (currentDirection === "asc") {
      // Change to descending
      newSortState = [...currentSortState];
      newSortState[existingSortIndex] = {
        key: columnKey,
        direction: "desc",
      };
    } else {
      // Clear this sort (remove from array)
      newSortState = currentSortState.filter(
        (_, index) => index !== existingSortIndex,
      );
    }
  }

  return newSortState;
}
