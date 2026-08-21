import { useState, useCallback, useMemo, useEffect } from "react";

import {
  TableState,
  SelectionConfig,
  ExpansionConfig,
  SortingConfig,
  PersistenceConfig,
  ColumnSort,
} from "./types";
import storage from "../../../utils/storage";

/**
 * Custom hooks for table functionality
 * Following Single Responsibility Principle
 */

/**
 * Hook for managing table selection state
 */
export function useTableSelection(
  config?: SelectionConfig,
  initialSelected?: Set<string>,
) {
  const [selectedItems, setSelectedItems] = useState<Set<string>>(
    initialSelected ?? config?.selectedItems ?? new Set(),
  );

  // Sync with external selectedItems changes
  useEffect(() => {
    if (config?.selectedItems !== undefined) {
      const externalSet = config.selectedItems;
      setSelectedItems((currentSet) => {
        const externalSize = externalSet.size;
        const currentSize = currentSet.size;

        // Check if sets are different
        if (currentSize !== externalSize) {
          return new Set(externalSet);
        } else if (externalSize > 0) {
          // If sizes match but not empty, check if contents differ
          const hasDifference =
            Array.from(currentSet).some((id) => !externalSet.has(id)) ||
            Array.from(externalSet).some((id) => !currentSet.has(id));
          return hasDifference ? new Set(externalSet) : currentSet;
        }
        // Both are empty, return current to avoid unnecessary update
        return currentSet;
      });
    }
  }, [config?.selectedItems]);

  const toggleSelection = useCallback(
    (itemId: string) => {
      setSelectedItems((prev) => {
        const newSet = new Set(prev);
        if (newSet.has(itemId)) {
          newSet.delete(itemId);
        } else {
          newSet.add(itemId);
        }
        config?.onSelectionChange?.(newSet);
        return newSet;
      });
    },
    [config],
  );

  const selectAll = useCallback(
    (itemIds: string[]) => {
      const newSet = new Set(itemIds);
      setSelectedItems(newSet);
      config?.onSelectionChange?.(newSet);
    },
    [config],
  );

  const clearSelection = useCallback(() => {
    const newSet = new Set<string>();
    setSelectedItems(newSet);
    config?.onSelectionChange?.(newSet);
  }, [config]);

  const isSelected = useCallback(
    (itemId: string) => selectedItems.has(itemId),
    [selectedItems],
  );

  return {
    selectedItems,
    toggleSelection,
    selectAll,
    clearSelection,
    isSelected,
  };
}

/**
 * Hook for managing table expansion state
 */
export function useTableExpansion(
  config?: ExpansionConfig,
  initialExpanded?: Set<string>,
) {
  const [expandedItems, setExpandedItems] = useState<Set<string>>(
    initialExpanded ?? config?.expandedItems ?? new Set(),
  );

  // Follow the consumer's set when it is provided, so expansion can be driven
  // from outside - e.g. an accordion that allows only one open row. Without
  // this the prop is read once and the table's own state wins forever.
  const controlledExpanded = config?.expandedItems;
  useEffect(() => {
    if (!controlledExpanded) return;
    setExpandedItems((prev) => {
      // Only replace on a real difference, otherwise every render loops.
      const sameSize = prev.size === controlledExpanded.size;
      if (sameSize && [...controlledExpanded].every((id) => prev.has(id))) {
        return prev;
      }
      return new Set(controlledExpanded);
    });
  }, [controlledExpanded]);

  const toggleExpansion = useCallback(
    (itemId: string) => {
      setExpandedItems((prev) => {
        const newSet = new Set(prev);
        if (newSet.has(itemId)) {
          newSet.delete(itemId);
        } else {
          newSet.add(itemId);
        }
        config?.onExpansionChange?.(newSet);
        return newSet;
      });
    },
    [config],
  );

  const isExpanded = useCallback(
    (itemId: string) => expandedItems.has(itemId),
    [expandedItems],
  );

  return {
    expandedItems,
    toggleExpansion,
    isExpanded,
  };
}

/**
 * Hook for column visibility
 */
export function useColumnVisibility(
  columns: Array<{
    key: string;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    visible?: boolean | ((context: any) => boolean);
  }>,
  context: Record<string, unknown>,
  hiddenColumns: string[] = [],
) {
  const visibleColumns = useMemo(() => {
    return columns.filter((col) => {
      // First check if column is in hiddenColumns
      if (hiddenColumns.includes(col.key)) return false;

      // Then check visible property
      if (col.visible === undefined) return true;
      if (typeof col.visible === "boolean") return col.visible;
      if (typeof col.visible === "function") return col.visible(context);
      return true;
    });
  }, [columns, context, hiddenColumns]);

  return { visibleColumns };
}

/**
 * Hook for managing sorting state
 */
export function useTableSorting(
  config?: SortingConfig,
  persistence?: PersistenceConfig,
) {
  // Load initial sort state from persistence
  const loadSortState = useCallback((): ColumnSort[] => {
    if (persistence?.enabled && persistence.storageKey) {
      try {
        const stored = storage.getItem(persistence.storageKey);
        if (stored) {
          const parsed = JSON.parse(stored) as TableState;
          return parsed.sortState || config?.defaultSort || [];
        }
      } catch (error) {
        console.error("Error loading sort state from storage:", error);
      }
    }
    return config?.defaultSort ?? [];
  }, [config, persistence]);

  const [internalSortState, setInternalSortState] =
    useState<ColumnSort[]>(loadSortState);

  // Use controlled or internal sort state
  const sortState = config?.sortState ?? internalSortState;
  const setSortState = config?.onSortChange ?? setInternalSortState;

  // Save to persistence
  useEffect(() => {
    if (persistence?.enabled && persistence.storageKey) {
      try {
        const currentState = storage.getItem(persistence.storageKey);
        const parsed = currentState
          ? (JSON.parse(currentState) as TableState)
          : {};
        storage.setItem(
          persistence.storageKey,
          JSON.stringify({ ...parsed, sortState }),
        );
      } catch (error) {
        console.error("Error saving sort state to storage:", error);
      }
    }
  }, [sortState, persistence]);

  return {
    sortState,
    setSortState,
  };
}

/**
 * Hook for managing persistence
 */
export function useTablePersistence(
  config?: PersistenceConfig,
  sortState?: ColumnSort[],
  hiddenColumns?: string[],
) {
  useEffect(() => {
    if (config?.enabled && config.storageKey) {
      try {
        const currentState = storage.getItem(config.storageKey);
        const parsed = currentState
          ? (JSON.parse(currentState) as TableState)
          : {};
        storage.setItem(
          config.storageKey,
          JSON.stringify({
            ...parsed,
            sortState: sortState ?? [],
            hiddenColumns: hiddenColumns ?? [],
          }),
        );
      } catch (error) {
        console.error("Error saving table state to storage:", error);
      }
    }
  }, [config, sortState, hiddenColumns]);
}
