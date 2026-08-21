import {
  handleSortChange as handleSortChangeUtil,
  sortData,
} from "@components/common/HierarchicalTable/sorting";
import { clsx } from "clsx";
import {
  ArrowDownNarrowWide,
  ArrowUpNarrowWide,
  ArrowUpDownIcon,
} from "lucide-react";
import React, { useState, useMemo, useEffect, useCallback } from "react";

import { Checkbox } from "./Checkbox";
import { Skeleton } from "./Skeleton";
import storage from "../../utils/storage";

// Sort direction type
export type SortDirection = "asc" | "desc" | null;

// Sort state for a single column
export interface ColumnSort {
  key: string;
  direction: "asc" | "desc";
}

// Custom sort function type
export type CustomSortFn<T> = (a: T, b: T, direction: "asc" | "desc") => number;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface TableColumn<T = Record<string, any>> {
  key: keyof T | string;
  header: string;
  render?: (value: T[keyof T], row: T, index?: number) => React.ReactNode;
  sortable?: boolean;
  sortKey?: string; // The actual key to use for sorting (defaults to column key)
  customSort?: CustomSortFn<T>; // Custom sort function for this column
  width?: string;
  align?: "left" | "center" | "right";
  frozen?: boolean; // Whether column should be frozen
  frozenPosition?: "left" | "right"; // Position for frozen column (default: "left")
  isAction?: boolean; // Whether this is an action column
  className?: string; // Additional CSS classes
}

// Group column header interface
export interface TableGroupHeader {
  text: string;
  colspan: number; // Number of columns this group spans
  backgroundColor?: string; // Background color for the group header
  align?: "left" | "center" | "right"; // Text alignment (default: "left")
  className?: string; // Additional CSS classes
}

export interface TableState {
  sortState: ColumnSort[];
  hiddenColumns: string[];
  viewType?: "list" | "grid"; // View type (list or grid)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface TableProps<T = Record<string, any>> {
  columns: TableColumn<T>[];
  data: T[];
  onRowClick?: (row: T) => void;
  className?: string;
  emptyMessage?: string;
  emptyRenderer?: () => React.ReactNode; // Custom UI renderer for empty state
  loading?: boolean;
  skeletonRowsCount?: number; // Number of skeleton rows to show when loading (default: 5)
  striped?: boolean;
  hoverable?: boolean;
  // Selection props
  selectable?: boolean;
  selectedRows?: (string | number)[];
  onRowSelect?: (rowId: string | number) => void;
  onSelectAll?: (selected: boolean) => void;
  // Sorting props
  sortMode?: "client" | "remote"; // Sort mode: client-side or remote (default: "client")
  multiSort?: boolean; // Allow multiple column sorting (default: false)
  defaultSort?: ColumnSort[]; // Default sort configuration
  sortState?: ColumnSort[]; // Controlled sort state (for remote sorting)
  onSortChange?: (sort: ColumnSort[]) => void; // Callback when sort changes (for remote sorting)
  // Persistence props
  persistStateKey?: boolean; // If true, table state (sorting, column visibility) will be saved to localStorage
  hiddenColumns?: string[]; // Controlled hidden columns state (for external control)
  onColumnVisibilityChange?: (hiddenColumns: string[]) => void; // Callback when column visibility changes
  // Group header props
  groupHeaders?: TableGroupHeader[]; // Array of group headers to display above the column headers
  // Total row props
  totalRow?: T; // Total row data to display at the bottom
  totalRowColspans?: Record<string, number>; // Optional column spans for total row cells (key: column key, value: colspan)
  totalRowClassName?: string; // Optional custom className for total row
  totalRowCellClassNames?: Record<string, string>; // Optional per-cell className for total row cells (key: column key, value: any Tailwind class or className string)
  totalRowCellAlignments?: Record<string, "left" | "center" | "right">; // Optional per-cell alignment for total row cells (key: column key, value: alignment)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface TableHeaderProps<T = Record<string, any>> {
  columns: TableColumn<T>[];
  selectable?: boolean;
  selectedRows?: (string | number)[];
  totalRows?: number;
  onSelectAll?: (selected: boolean) => void;
  sortState: ColumnSort[];
  onSortChange: (columnKey: string) => void;
  multiSort?: boolean;
  hiddenColumns?: string[];
  groupHeaders?: TableGroupHeader[];
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface TableRowProps<T = Record<string, any>> {
  row: T;
  columns: TableColumn<T>[];
  onClick?: (row: T) => void;
  className?: string;
  selectable?: boolean;
  selected?: boolean;
  onSelect?: (rowId: string | number) => void;
  rowId?: string | number;
  index?: number;
  hiddenColumns?: string[];
}

export interface TableCellProps {
  children: React.ReactNode;
  align?: "left" | "center" | "right";
  width?: string;
  className?: string;
  frozen?: boolean;
  frozenPosition?: "left" | "right";
  id?: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface TableSkeletonRowProps<T = Record<string, any>> {
  columns: TableColumn<T>[];
  selectable?: boolean;
  striped?: boolean;
  index?: number;
}

// Table Cell Component
export const TableCell: React.FC<TableCellProps> = ({
  children,
  align = "left",
  width,
  className,
  frozen = false,
  frozenPosition = "left",
  id,
}) => {
  const alignClasses = {
    left: "text-left",
    center: "text-center",
    right: "text-right",
  };

  const frozenClasses = frozen
    ? frozenPosition === "left"
      ? "sticky left-0 z-10 bg-white dark:bg-mw-neutral-900"
      : "sticky right-0 z-10 bg-white dark:bg-mw-neutral-900"
    : "";

  return (
    <td
      id={id}
      className={clsx(
        "h-19 p-2 whitespace-nowrap text-sm",
        alignClasses[align],
        frozenClasses,
        className,
      )}
      style={{ width }}
    >
      {children}
    </td>
  );
};

// Table Skeleton Row Component
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const TableSkeletonRow = <T extends Record<string, any>>({
  columns,
  selectable = false,
  striped = false,
  index = 0,
}: TableSkeletonRowProps<T>) => {
  return (
    <tr
      className={clsx(
        "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700",
        striped && index % 2 === 1 && "bg-mw-neutral-50 dark:bg-mw-neutral-800",
      )}
    >
      {selectable && (
        <td className="p-2 whitespace-nowrap w-12 sticky left-0 z-20 bg-white dark:bg-mw-neutral-900">
          <div className="flex items-center rounded-lg">
            <Skeleton className="h-4 w-4 rounded" />
          </div>
        </td>
      )}
      {columns.map((column) => {
        // Generate different skeleton widths for variety
        const skeletonWidths = ["w-16", "w-20", "w-24", "w-32", "w-40"];
        const randomWidth = skeletonWidths[index % skeletonWidths.length];
        return (
          <TableCell
            key={column.key as string}
            align={column.align}
            width={column.width}
            frozen={column.frozen}
            frozenPosition={column.frozenPosition}
            className="text-sm font-medium text-og-black dark:text-white"
          >
            <Skeleton className={`h-4 ${randomWidth}`} />
          </TableCell>
        );
      })}
    </tr>
  );
};

// Table Group Header Component
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const TableGroupHeaderRow = <T extends Record<string, any>>({
  groupHeaders,
  columns,
  selectable = false,
  hiddenColumns = [],
  selectedRows = [],
  totalRows = 0,
  onSelectAll,
}: {
  groupHeaders: TableGroupHeader[];
  columns: TableColumn<T>[];
  selectable?: boolean;
  hiddenColumns?: string[];
  selectedRows?: (string | number)[];
  totalRows?: number;
  onSelectAll?: (selected: boolean) => void;
}) => {
  const isAllSelected = selectedRows.length === totalRows && totalRows > 0;
  const isPartiallySelected =
    selectedRows.length > 0 && selectedRows.length < totalRows;
  // Calculate visible columns count
  const visibleColumns = columns.filter(
    (col) => !hiddenColumns.includes(col.key as string),
  );

  // Calculate the actual colspan for each group based on visible columns
  const calculateGroupColspans = useMemo(() => {
    let currentIndex = 0;
    const groupColspans: number[] = [];

    for (const group of groupHeaders) {
      // Calculate how many visible columns this group should span
      let actualColspan = 0;
      let remainingColspan = group.colspan;

      for (
        let i = currentIndex;
        i < visibleColumns.length && remainingColspan > 0;
        i++
      ) {
        actualColspan++;
        remainingColspan--;
      }

      groupColspans.push(actualColspan);
      currentIndex += actualColspan;
    }

    return groupColspans;
  }, [groupHeaders, visibleColumns]);

  return (
    <tr className="bg-mw-primary-100">
      {selectable && (
        <th
          rowSpan={2}
          id="table-group-header-select-all"
          className="p-2 h-12 w-4 sticky left-0 z-20 border-b border-mw-primary-100"
        >
          <div className="flex items-center">
            <Checkbox
              id="table-select-all-checkbox"
              checked={isAllSelected}
              onChange={(e) => onSelectAll?.(e.target.checked)}
              isIndeterminate={isPartiallySelected}
            />
          </div>
        </th>
      )}
      {groupHeaders.map((group, index) => {
        const actualColspan = calculateGroupColspans[index];
        if (actualColspan === 0) return null;

        const alignClasses = {
          left: "text-left",
          center: "text-center",
          right: "text-right",
        };

        const align = group.align || "left";
        const bgColor = group.backgroundColor || "bg-mw-primary-100";

        return (
          <th
            key={`group-header-${index}`}
            colSpan={actualColspan}
            className={clsx(
              "p-4 h-12 text-xs font-medium text-og-black border-b border-mw-primary-100",
              alignClasses[align],
              bgColor,
              group.className,
            )}
            style={{
              backgroundColor: group.backgroundColor,
            }}
          >
            {group.text}
          </th>
        );
      })}
    </tr>
  );
};

// Table Header Component
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const TableHeader = <T extends Record<string, any>>({
  columns,
  selectable = false,
  selectedRows = [],
  totalRows = 0,
  onSelectAll,
  sortState,
  onSortChange,
  multiSort = false,
  hiddenColumns = [],
  groupHeaders,
}: TableHeaderProps<T>) => {
  const isAllSelected = selectedRows.length === totalRows && totalRows > 0;
  const isPartiallySelected =
    selectedRows.length > 0 && selectedRows.length < totalRows;

  // Helper function to get sort info for a column
  const getSortInfo = (columnKey: string) => {
    const sortIndex = sortState.findIndex((s) => s.key === columnKey);
    if (sortIndex === -1) return { direction: null, order: null };
    return {
      direction: sortState[sortIndex].direction,
      order: multiSort && sortState.length > 1 ? sortIndex + 1 : null,
    };
  };

  // Render sort icon based on state
  const renderSortIcon = (columnKey: string, sortable?: boolean) => {
    if (!sortable) return null;

    const { direction, order } = getSortInfo(columnKey);

    return (
      <div className="inline-flex items-center gap-1 ml-1">
        {direction === null ? (
          <ArrowUpDownIcon className="h-3.5 w-3.5 text-black" />
        ) : direction === "asc" ? (
          <ArrowUpNarrowWide className="h-3.5 w-3.5 text-black" />
        ) : (
          <ArrowDownNarrowWide className="h-3.5 w-3.5 text-black" />
        )}
        {order !== null && (
          <span className="text-xs font-semibold text-mw-primary-600 min-w-[12px]">
            {order}
          </span>
        )}
      </div>
    );
  };

  const hasGroupHeaders = groupHeaders && groupHeaders.length > 0;

  return (
    <thead className="bg-grey-50 border-b border-mw-primary-100 rounded-tl-lg rounded-tr-lg">
      {hasGroupHeaders && (
        <TableGroupHeaderRow<T>
          groupHeaders={groupHeaders}
          columns={columns}
          selectable={selectable}
          hiddenColumns={hiddenColumns}
          selectedRows={selectedRows}
          totalRows={totalRows}
          onSelectAll={onSelectAll}
        />
      )}
      <tr>
        {selectable && !hasGroupHeaders && (
          <th
            id="table-header-select-all"
            className="p-2 w-4 h-12 sticky left-0 z-20 bg-grey-50"
          >
            <div className="flex items-center">
              <Checkbox
                id="table-select-all-checkbox"
                checked={isAllSelected}
                onChange={(e) => onSelectAll?.(e.target.checked)}
                isIndeterminate={isPartiallySelected}
              />
            </div>
          </th>
        )}
        {columns
          .filter((column) => !hiddenColumns.includes(column.key as string))
          .map((column) => {
            const sortKey = column.sortKey || (column.key as string);
            const { direction } = getSortInfo(sortKey);
            const frozenClasses = column.frozen
              ? column.frozenPosition === "left"
                ? "sticky left-0 z-10 bg-grey-50"
                : "sticky right-0 z-10 bg-grey-50"
              : "";

            return (
              <th
                id={`table-header-${column.key as string}`}
                key={column.key as string}
                className={clsx(
                  "h-12 p-2 text-left text-og-black text-xs font-medium leading-none",
                  column.sortable &&
                    "cursor-pointer select-none hover:bg-mw-neutral-100 dark:hover:bg-mw-neutral-700 transition-colors",
                  column.align === "center" && "text-center",
                  column.align === "right" && "text-right",
                  direction && "bg-mw-neutral-50 dark:bg-mw-neutral-800",
                  frozenClasses,
                )}
                style={{ width: column.width }}
                onClick={() => column.sortable && onSortChange(sortKey)}
              >
                <div
                  className={clsx(
                    "flex items-center gap-1",
                    column.align === "center" && "justify-center",
                    column.align === "right" && "justify-end",
                  )}
                >
                  <span>{column.header}</span>
                  {renderSortIcon(sortKey, column.sortable)}
                </div>
              </th>
            );
          })}
      </tr>
    </thead>
  );
};

// Table Row Component
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const TableRow = <T extends Record<string, any>>({
  row,
  columns,
  onClick,
  className,
  selectable = false,
  selected = false,
  onSelect,
  rowId,
  index,
  hiddenColumns = [],
}: TableRowProps<T>) => {
  const rowIdAttr = rowId ? `table-row-${rowId}` : `table-row-${index}`;
  return (
    <tr
      id={rowIdAttr}
      className={clsx(
        "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700 hover:bg-mw-primary-50",
        onClick && "cursor-pointer hover:bg-mw-primary-50",
        selected && "bg-mw-primary-50 hover:bg-mw-primary-50",
        className,
      )}
      onClick={() => !selectable && onClick?.(row)}
    >
      {selectable && (
        <td className="h-19 p-2 whitespace-nowrap w-12 sticky left-0 z-20 bg-white dark:bg-mw-neutral-900">
          <div className="flex items-center rounded-lg">
            <Checkbox
              id={`table-row-checkbox-${rowId}`}
              checked={selected}
              onChange={(e) => {
                e.stopPropagation();
                onSelect?.(rowId!);
              }}
            />
          </div>
        </td>
      )}
      {columns
        .filter((column) => !hiddenColumns?.includes(column.key as string))
        .map((column) => {
          const value = row[column.key as keyof T];
          const content = column.render
            ? column.render(value as T[keyof T], row, index)
            : value;

          return (
            <TableCell
              id={`${rowIdAttr}-cell-${column.key as string}`}
              key={column.key as string}
              align={column.align}
              width={column.width}
              frozen={column.frozen}
              frozenPosition={column.frozenPosition}
              className={clsx(
                "text-sm font-medium text-og-black dark:text-white",
                column.className,
              )}
            >
              {content as React.ReactNode}
            </TableCell>
          );
        })}
    </tr>
  );
};

// Main Table Component
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const Table = <T extends Record<string, any>>({
  columns,
  data,
  onRowClick,
  emptyMessage = "No data available",
  emptyRenderer,
  loading = false,
  skeletonRowsCount = 5,
  striped = false,
  hoverable = true,
  selectable = false,
  selectedRows = [],
  onRowSelect,
  onSelectAll,
  sortMode = "client",
  multiSort = false,
  defaultSort = [],
  sortState: controlledSortState,
  onSortChange: onSortChangeCallback,
  persistStateKey = false,
  hiddenColumns: controlledHiddenColumns,
  onColumnVisibilityChange,
  groupHeaders,
  totalRow,
  totalRowColspans,
  totalRowClassName,
  totalRowCellClassNames,
  totalRowCellAlignments,
}: TableProps<T>) => {
  // Generate localStorage key - using a default key when persistStateKey is true
  // This can be made more specific per table instance if needed
  const storageKey = persistStateKey ? "table_persisted_state" : null;

  // Load state from localStorage if persistStateKey is true
  const loadTableState = useCallback((): TableState => {
    if (!persistStateKey || !storageKey) {
      return {
        sortState: defaultSort || [],
        hiddenColumns: [],
      };
    }

    try {
      const stored = storage.getItem(storageKey);
      if (stored) {
        const parsed = JSON.parse(stored) as TableState;
        return {
          sortState: parsed.sortState || defaultSort || [],
          hiddenColumns: parsed.hiddenColumns || [],
        };
      }
    } catch (error) {
      console.error("Error loading table state from storage:", error);
    }

    return {
      sortState: defaultSort || [],
      hiddenColumns: [],
    };
  }, [persistStateKey, storageKey, defaultSort]);

  // Save state to localStorage
  const saveTableState = useCallback(
    (state: TableState) => {
      if (!persistStateKey || !storageKey) return;

      try {
        storage.setItem(storageKey, JSON.stringify(state));
      } catch (error) {
        console.error("Error saving table state to storage:", error);
      }
    },
    [persistStateKey, storageKey],
  );

  // Initialize state from localStorage
  const initialState = useMemo(() => loadTableState(), [loadTableState]);

  // Internal sort state (used for client-side sorting)
  const [internalSortState, setInternalSortState] = useState<ColumnSort[]>(
    initialState.sortState,
  );

  // Column visibility state
  const [internalHiddenColumns, setInternalHiddenColumns] = useState<string[]>(
    () => {
      return controlledHiddenColumns ?? initialState.hiddenColumns;
    },
  );

  // Use controlled or internal sort state
  const sortState = controlledSortState ?? internalSortState;
  const setSortState = onSortChangeCallback
    ? onSortChangeCallback
    : setInternalSortState;

  // Use controlled or internal hidden columns state
  const hiddenColumns = controlledHiddenColumns ?? internalHiddenColumns;
  const setHiddenColumns = onColumnVisibilityChange
    ? onColumnVisibilityChange
    : setInternalHiddenColumns;

  useEffect(() => {
    if (controlledHiddenColumns !== undefined) {
      const controlledStr = JSON.stringify(controlledHiddenColumns);
      const internalStr = JSON.stringify(internalHiddenColumns);
      if (controlledStr !== internalStr) {
        setInternalHiddenColumns(controlledHiddenColumns);
      }
    }
  }, [controlledHiddenColumns]);

  // Save state to localStorage whenever it changes
  // Note: viewType is managed by the parent component, so we don't save it here
  useEffect(() => {
    if (persistStateKey) {
      saveTableState({
        sortState: sortState,
        hiddenColumns: hiddenColumns,
      });
    }
  }, [sortState, hiddenColumns, persistStateKey, saveTableState]);

  // Handle column visibility toggle
  // This function can be called programmatically to toggle column visibility
  // Example: You can create a column visibility menu that calls this
  const handleToggleColumnVisibility = useCallback(
    (columnKey: string) => {
      const newHiddenColumns = hiddenColumns.includes(columnKey)
        ? hiddenColumns.filter((key) => key !== columnKey)
        : [...hiddenColumns, columnKey];

      setHiddenColumns(newHiddenColumns);
    },
    [hiddenColumns, setHiddenColumns],
  );

  // Expose toggle function via window for external access (optional)
  // Users can also use onColumnVisibilityChange callback to manage visibility
  useEffect(() => {
    if (persistStateKey && storageKey && typeof window !== "undefined") {
      (window as unknown as Record<string, unknown>)[
        `toggleTableColumn_${storageKey}`
      ] = handleToggleColumnVisibility;
    }
    return () => {
      if (persistStateKey && storageKey && typeof window !== "undefined") {
        delete (window as unknown as Record<string, unknown>)[
          `toggleTableColumn_${storageKey}`
        ];
      }
    };
  }, [persistStateKey, storageKey, handleToggleColumnVisibility]);

  // Handle column sort click
  const handleSortChange = useCallback(
    (columnKey: string) => {
      const newSortState = handleSortChangeUtil(
        columnKey,
        sortState,
        multiSort,
      );
      setSortState(newSortState);
    },
    [sortState, multiSort, setSortState],
  );

  // Filter visible columns
  const visibleColumns = useMemo(() => {
    return columns.filter((col) => !hiddenColumns.includes(col.key as string));
  }, [columns, hiddenColumns]);

  // Apply client-side sorting
  const sortedData = useMemo(() => {
    // Ensure data rows have id as string for compatibility with BaseRowData
    const dataWithStringId = data.map((row) => ({
      ...row,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      id: String((row as Record<string, any>).id),
    })) as Array<T & { id: string }>;
    return sortData(
      dataWithStringId,
      sortState,
      columns.map((col) => ({
        key: col.key as string,
        sortKey: col.sortKey,
        customSort: col.customSort,
      })),
      sortMode,
    ) as T[];
  }, [data, sortState, sortMode, columns]);

  const tableId = "table-container";
  return (
    <div id={tableId}>
      <table
        id={`${tableId}-table`}
        className="min-w-full divide-y divide-mw-neutral-200 dark:divide-mw-neutral-700"
      >
        <TableHeader<T>
          columns={columns}
          selectable={selectable}
          selectedRows={selectedRows}
          totalRows={loading ? skeletonRowsCount : sortedData.length}
          onSelectAll={onSelectAll}
          sortState={sortState}
          onSortChange={handleSortChange}
          multiSort={multiSort}
          hiddenColumns={hiddenColumns}
          groupHeaders={groupHeaders}
        />
        <tbody className="bg-white dark:bg-mw-neutral-900 divide-y divide-mw-neutral-200 dark:divide-mw-neutral-700">
          {loading ? (
            // Show skeleton rows when loading
            Array.from({ length: skeletonRowsCount }).map((_, index) => (
              <TableSkeletonRow<T>
                key={`skeleton-${index}`}
                columns={columns}
                selectable={selectable}
                striped={striped}
                index={index}
              />
            ))
          ) : sortedData.length === 0 ? (
            // Show empty state when no data
            <tr>
              <td
                colSpan={visibleColumns.length + (selectable ? 1 : 0)}
                className="p-12 text-center"
              >
                {emptyRenderer ? (
                  emptyRenderer()
                ) : (
                  <p className="text-mw-neutral-500 dark:text-mw-neutral-400">
                    {emptyMessage}
                  </p>
                )}
              </td>
            </tr>
          ) : (
            // Show actual data rows
            <>
              {sortedData.map((row, index) => (
                <TableRow<T>
                  key={
                    ((row as Record<string, unknown>).id as React.Key) || index
                  }
                  row={row}
                  columns={columns}
                  onClick={onRowClick}
                  selectable={selectable}
                  selected={
                    selectedRows.includes(
                      (row as Record<string, unknown>).id as string | number,
                    ) || false
                  }
                  onSelect={onRowSelect}
                  rowId={(row as Record<string, unknown>).id as string | number}
                  index={index}
                  hiddenColumns={hiddenColumns}
                  className={clsx(
                    striped &&
                      index % 2 === 1 &&
                      "bg-mw-neutral-50 dark:bg-mw-neutral-800",
                    hoverable &&
                      "hover:bg-mw-primary-50 dark:hover:bg-mw-primary-900/20",
                  )}
                />
              ))}
              {/* Total Row */}
              {totalRow && !loading && sortedData.length > 0 && (
                <tr
                  className={clsx(
                    "font-semibold",
                    totalRowClassName,
                    hoverable &&
                      "hover:bg-mw-primary-50 dark:hover:bg-mw-primary-900/20",
                  )}
                >
                  {selectable && (
                    <td className="h-19 p-2 whitespace-nowrap w-12 sticky left-0 z-20 bg-mw-primary-50 dark:bg-mw-primary-900/20" />
                  )}
                  {(() => {
                    const cells: React.ReactNode[] = [];
                    let skipCount = 0;

                    visibleColumns.forEach((column) => {
                      // Skip columns that are part of a previous colspan
                      if (skipCount > 0) {
                        skipCount--;
                        return;
                      }

                      const colspan =
                        totalRowColspans?.[column.key as string] || 1;
                      skipCount = colspan - 1; // Mark next columns to skip

                      const value = totalRow[column.key as keyof T];
                      const content = column.render
                        ? column.render(value as T[keyof T], totalRow, -1)
                        : value;

                      const alignClasses = {
                        left: "text-left",
                        center: "text-center",
                        right: "text-right",
                      };

                      // Get cell-specific className if provided
                      const cellClassName =
                        totalRowCellClassNames?.[column.key as string];

                      // Get cell-specific alignment if provided, otherwise use column alignment
                      const cellAlignment =
                        totalRowCellAlignments?.[column.key as string] ||
                        column.align ||
                        "left";

                      cells.push(
                        <td
                          key={column.key as string}
                          colSpan={colspan}
                          className={clsx(
                            "h-19 p-2 whitespace-nowrap text-xs font-bold text-og-black",
                            alignClasses[cellAlignment],
                            column.className,
                            cellClassName, // Apply cell-specific className
                          )}
                          style={{ width: column.width }}
                        >
                          {content as React.ReactNode}
                        </td>,
                      );
                    });

                    return cells;
                  })()}
                </tr>
              )}
            </>
          )}
        </tbody>
      </table>
    </div>
  );
};

export default Table;
