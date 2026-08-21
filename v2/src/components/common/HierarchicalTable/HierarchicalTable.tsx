import { Button } from "@components/ui/Button";
import { Checkbox } from "@components/ui/Checkbox";
import { Skeleton } from "@components/ui/Skeleton";
import { Spinner } from "@components/ui/Spinner";
import { cn } from "@utils/tailwindMerge";
import clsx from "clsx";
import {
  ArrowDownNarrowWide,
  ArrowUpNarrowWide,
  ArrowUpDownIcon,
  ChevronRight,
  Columns3Cog,
} from "lucide-react";
import React, { useMemo, Fragment, useState, useCallback } from "react";

import { TooltipHeader } from "./cell-renderers";
import ColumnVisibilityDrawer from "./ColumnVisibilityDrawer";
import {
  useTableSelection,
  useTableExpansion,
  useTableSorting,
  useTablePersistence,
} from "./hooks";
import { sortData, handleSortChange } from "./sorting";
import type { BaseRowData } from "./types";
import {
  HierarchicalTableProps,
  ParentTableColumn,
  ChildTableColumn,
  ParentRowData,
  ChildRowData,
  CellRenderContext,
  TableContext,
} from "./types";
import storage from "../../../utils/storage";

/**
 * Hierarchical Table Component
 *
 * A comprehensive, reusable table component that supports:
 * - Hierarchical (parent-child) and flat data structures
 * - Sorting (client/remote, multi-sort)
 * - Selection (single, multiple, hierarchical)
 * - Expansion (expand/collapse rows)
 * - Price editing (inline editing)
 * - Loading states (skeleton rows)
 * - Empty states
 * - Group headers
 * - Total rows
 * - Frozen columns
 * - Persistence (localStorage)
 * - Column visibility
 *
 * Following SOLID principles and DRY methodology
 */
export function HierarchicalTable<T extends ParentRowData = ParentRowData>({
  data,
  columns,
  childrenColumns,
  selection,
  expansion,
  sorting,
  persistence,
  groupHeaders,
  totalRow,
  totalRowColspans,
  totalRowClassName,
  totalRowCellClassNames,
  totalRowCellAlignments,
  loading = false,
  skeletonRowsCount = 5,
  emptyMessage = "No data available",
  emptyRenderer,
  className = "",
  parentRowClassName = "",
  childRowClassName = "",
  striped = false,
  hoverable = true,
  density = "compact",
  hiddenColumns: controlledHiddenColumns = [],
  onColumnVisibilityChange,
  columnVisibilityDrawer,
  onRowClick,
  context = {},
}: HierarchicalTableProps<T>) {
  // Row density. "compact" is the historical spacing and stays the default so
  // existing consumers are unaffected; "comfortable" matches the roomier design
  // used by the price management table.
  const isComfortable = density === "comfortable";
  const cellPadding = isComfortable ? "p-4" : "p-2";
  const headerPadding = isComfortable ? "h-12 px-4" : "p-2";
  const headerTextSize = isComfortable ? "text-sm" : "text-xs";

  // Children render as plain rows inside the parent table (no sub-table, no
  // sub-header), so they must line up with the parent columns cell for cell.
  const childrenRenderInline =
    expansion?.showChildrenHeaders === false && !expansion?.childrenWrapper;

  // Column visibility drawer state (controlled by the consumer when isOpen is provided)
  const [internalDrawerOpen, setInternalDrawerOpen] = useState(false);
  const isDrawerOpen = columnVisibilityDrawer?.isOpen ?? internalDrawerOpen;
  const setIsDrawerOpen = useCallback(
    (open: boolean) => {
      if (columnVisibilityDrawer?.isOpen === undefined) {
        setInternalDrawerOpen(open);
      }
      columnVisibilityDrawer?.onOpenChange?.(open);
    },
    [columnVisibilityDrawer],
  );

  // Internal hidden columns state
  const [internalHiddenColumns, setInternalHiddenColumns] = useState<string[]>(
    [],
  );

  // Helper function to get storage key (extracted to avoid duplication)
  // SonarQube: Removed duplicate storage key resolution logic
  const getStorageKey = useCallback((): string | null => {
    if (
      columnVisibilityDrawer?.enabled &&
      persistence?.enabled &&
      (columnVisibilityDrawer.storageKey ?? persistence.storageKey)
    ) {
      return (
        columnVisibilityDrawer.storageKey ?? persistence.storageKey ?? null
      );
    }
    return null;
  }, [
    columnVisibilityDrawer?.enabled,
    columnVisibilityDrawer?.storageKey,
    persistence?.enabled,
    persistence?.storageKey,
  ]);

  // Load initial hidden columns from persistence if enabled
  React.useEffect(() => {
    const storageKey = getStorageKey();
    if (!storageKey) return;

    try {
      const stored = storage.getItem(storageKey);
      if (stored) {
        const parsed = JSON.parse(stored) as { hiddenColumns?: string[] };
        // SonarQube: Validate parsed structure to prevent security issues (S1523)
        if (
          parsed &&
          typeof parsed === "object" &&
          Array.isArray(parsed.hiddenColumns) &&
          parsed.hiddenColumns.length > 0 &&
          parsed.hiddenColumns.every((col) => typeof col === "string")
        ) {
          setInternalHiddenColumns(parsed.hiddenColumns);
          onColumnVisibilityChange?.(parsed.hiddenColumns);
        }
      }
    } catch (error) {
      console.error("Error loading column visibility from storage:", error);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run on mount - getStorageKey is stable

  const hiddenColumns =
    controlledHiddenColumns.length > 0
      ? controlledHiddenColumns
      : internalHiddenColumns;

  // Update internal state when controlled changes
  React.useEffect(() => {
    if (controlledHiddenColumns.length > 0) {
      setInternalHiddenColumns(controlledHiddenColumns);
    }
  }, [controlledHiddenColumns]);

  // Initialize hooks
  const selectionHook = useTableSelection(selection, selection?.selectedItems);
  const expansionHook = useTableExpansion(expansion, expansion?.expandedItems);
  const sortingHook = useTableSorting(sorting, persistence);

  // Persistence
  useTablePersistence(persistence, sortingHook.sortState, hiddenColumns);

  // Handle column visibility change from drawer
  const handleColumnVisibilityChange = useCallback(
    (columnVisibility: Record<string, boolean>) => {
      const newHiddenColumns = Object.entries(columnVisibility)
        .filter(([_, visible]) => !visible)
        .map(([key]) => key);

      setInternalHiddenColumns(newHiddenColumns);
      onColumnVisibilityChange?.(newHiddenColumns);

      // Save to persistence if enabled
      const storageKey = getStorageKey();
      if (storageKey) {
        try {
          const currentState = storage.getItem(storageKey);
          let parsed: Record<string, unknown> = {};
          if (currentState) {
            try {
              const parsedValue = JSON.parse(currentState);
              // Validate parsed structure
              if (parsedValue && typeof parsedValue === "object") {
                parsed = parsedValue as Record<string, unknown>;
              }
            } catch (parseError) {
              console.error("Error parsing storage state:", parseError);
              // Continue with empty object if parse fails
            }
          }
          storage.setItem(
            storageKey,
            JSON.stringify({ ...parsed, hiddenColumns: newHiddenColumns }),
          );
        } catch (error) {
          console.error("Error saving column visibility to storage:", error);
        }
      }
    },
    [getStorageKey, onColumnVisibilityChange],
  );

  // Convert hiddenColumns array to columnVisibility object for drawer
  const columnVisibility = useMemo(() => {
    const visibility: Record<string, boolean> = {};
    columns.forEach((col) => {
      visibility[col.key] = !hiddenColumns.includes(col.key);
    });
    return visibility;
  }, [columns, hiddenColumns]);

  // Available columns for drawer (exclude columns that should not be toggled)
  const availableColumns = useMemo(
    () =>
      columns
        .filter((col) => !col.hideFromColumnVisibility)
        .map((col) => ({
          key: col.key,
          label: col.header,
        })),
    [columns],
  );

  // Build table context
  const tableContext: TableContext = useMemo(
    () => ({
      selectedItems: selectionHook.selectedItems,
      expandedItems: expansionHook.expandedItems,
      sortState: sortingHook.sortState,
      hiddenColumns,
      ...context,
    }),
    [
      selectionHook.selectedItems,
      expansionHook.expandedItems,
      sortingHook.sortState,
      hiddenColumns,
      context,
    ],
  );

  // Get visible columns - filter by visibility and hiddenColumns
  // Type: ParentTableColumn<T>[] - columns for parent rows
  const visibleColumns = useMemo((): ParentTableColumn<T>[] => {
    return columns.filter((col) => {
      // First check if column is in hiddenColumns
      if (hiddenColumns.includes(col.key)) return false;

      // Then check visible property
      if (col.visible === undefined) return true;
      if (typeof col.visible === "boolean") return col.visible;
      if (typeof col.visible === "function") return col.visible(tableContext);
      return true;
    });
  }, [columns, tableContext, hiddenColumns]);

  // Get visible children columns - use childrenColumns if provided, otherwise use columns
  // Type: ChildTableColumn[] - columns for child rows
  const visibleChildrenColumns = useMemo((): ChildTableColumn[] => {
    const colsToUse = childrenColumns ?? columns;
    return colsToUse.filter((col) => {
      // First check if column is in hiddenColumns
      if (hiddenColumns.includes(col.key)) return false;

      // Then check visible property
      if (col.visible === undefined) return true;
      if (typeof col.visible === "boolean") return col.visible;
      if (typeof col.visible === "function") return col.visible(tableContext);
      return true;
    }) as ChildTableColumn[];
  }, [childrenColumns, columns, tableContext, hiddenColumns]);

  // Calculate column positions for selection and expansion
  const getColumnPositions = useMemo(() => {
    const totalColumns = visibleColumns.length;
    const selectionPos = selection?.enabled
      ? selection.columnPosition !== undefined
        ? Math.max(0, Math.min(selection.columnPosition, totalColumns))
        : 0
      : -1;
    const expansionPos = expansion?.enabled
      ? expansion.columnPosition !== undefined
        ? Math.max(
            0,
            Math.min(
              expansion.columnPosition,
              totalColumns + (selection?.enabled ? 1 : 0),
            ),
          )
        : selection?.enabled
          ? 1
          : 0
      : -1;

    return { selectionPos, expansionPos };
  }, [
    visibleColumns.length,
    selection?.enabled,
    selection?.columnPosition,
    expansion?.enabled,
    expansion?.columnPosition,
  ]);

  // Calculate column positions for children (use children columns if provided)
  // For child rows: selection is always first (position 0), no expansion column
  const getChildrenColumnPositions = useMemo(() => {
    // Inline children share the parent grid, so the control columns must sit at
    // the same indices - including the expansion slot, which child rows fill
    // with an empty placeholder cell (they have no nested children to expand).
    if (childrenRenderInline) return getColumnPositions;

    // Sub-table modes render their own header row without an expansion column.
    const selectionPos = selection?.enabled ? 0 : -1;
    const expansionPos = -1;
    return { selectionPos, expansionPos };
  }, [childrenRenderInline, getColumnPositions, selection?.enabled]);

  // Helper function to build cells array with control columns at specified positions
  const buildCellsArray = useCallback(
    (
      dataCells: React.ReactNode[],
      selectionCell: React.ReactNode | null,
      expansionCell: React.ReactNode | null,
      useChildrenColumns = false,
    ): React.ReactNode[] => {
      // Use children column positions if building child rows, otherwise use parent positions
      const positions = useChildrenColumns
        ? getChildrenColumnPositions
        : getColumnPositions;
      const { selectionPos, expansionPos } = positions;
      const result: React.ReactNode[] = [];

      // Create array of all insertions with their positions
      const insertions: Array<{
        pos: number;
        cell: React.ReactNode;
        type: "selection" | "expansion";
      }> = [];
      if (selectionCell && selectionPos >= 0) {
        insertions.push({
          pos: selectionPos,
          cell: selectionCell,
          type: "selection",
        });
      }
      if (expansionCell && expansionPos >= 0) {
        insertions.push({
          pos: expansionPos,
          cell: expansionCell,
          type: "expansion",
        });
      }

      // Sort by position, but if positions are equal, selection comes first
      insertions.sort((a, b) => {
        if (a.pos !== b.pos) return a.pos - b.pos;
        return a.type === "selection" ? -1 : 1;
      });

      // Build result array by inserting control columns at specified positions
      let dataIndex = 0;
      let insertionIndex = 0;

      for (let i = 0; i < dataCells.length + insertions.length; i++) {
        // Check if we need to insert a control column at this position
        if (
          insertionIndex < insertions.length &&
          i === insertions[insertionIndex].pos
        ) {
          result.push(insertions[insertionIndex].cell);
          insertionIndex++;
        } else if (dataIndex < dataCells.length) {
          result.push(dataCells[dataIndex]);
          dataIndex++;
        }
      }

      return result;
    },
    [getColumnPositions, getChildrenColumnPositions],
  );

  // Sort data
  const sortedData = useMemo(() => {
    if (loading) return data;
    return sortData(
      data,
      sortingHook.sortState,
      columns,
      sorting?.mode ?? "client",
    );
  }, [data, sortingHook.sortState, columns, sorting?.mode, loading]);

  // Handle sort change
  const handleSortChangeClick = useCallback(
    (columnKey: string) => {
      const newSortState = handleSortChange(
        columnKey,
        sortingHook.sortState,
        sorting?.multiSort ?? false,
      );
      sortingHook.setSortState(newSortState);
    },
    [sortingHook, sorting?.multiSort],
  );

  // Get sort info for a column
  const getSortInfo = useCallback(
    (columnKey: string) => {
      const sortIndex = sortingHook.sortState.findIndex(
        (s) => s.key === columnKey,
      );
      if (sortIndex === -1) return { direction: null, order: null };
      return {
        direction: sortingHook.sortState[sortIndex].direction,
        order:
          sorting?.multiSort && sortingHook.sortState.length > 1
            ? sortIndex + 1
            : null,
      };
    },
    [sortingHook.sortState, sorting?.multiSort],
  );

  // Render sort icon
  const renderSortIcon = useCallback(
    (columnKey: string, sortable?: boolean) => {
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
    },
    [getSortInfo],
  );

  // Handle select all
  const handleSelectAll = useMemo(() => {
    if (!selection?.enabled) return undefined;

    return (checked: boolean) => {
      if (checked) {
        const allIds = new Set<string>();

        // First, select all children
        sortedData.forEach((item) => {
          const childIds = (item.children || []).map((child) =>
            selection.getItemId(child as BaseRowData),
          );
          childIds.forEach((childId) => allIds.add(childId));
        });

        // Then, select parents only if all their children are selected
        // (respecting selectParentOnAllChildren config)
        sortedData.forEach((item) => {
          const itemId = selection.getItemId(item);
          const children = (item.children ?? []) as ChildRowData[];

          if (children.length > 0) {
            const childIds = children.map((child) =>
              selection.getItemId(child as BaseRowData),
            );
            const allChildrenSelected = childIds.every((childId) =>
              allIds.has(childId),
            );

            // Select parent if all children are selected and config allows it
            if (
              allChildrenSelected &&
              selection?.selectParentOnAllChildren !== false
            ) {
              allIds.add(itemId);
            }
          } else {
            // No children, select parent directly
            allIds.add(itemId);
          }
        });

        selectionHook.selectAll(Array.from(allIds));
      } else {
        selectionHook.clearSelection();
      }
    };
  }, [selection, sortedData, selectionHook]);

  const allSelected = useMemo(() => {
    if (!selection?.enabled) return false;
    const allIds = sortedData.flatMap((item) => {
      const itemId = selection.getItemId(item);
      const childIds = (item.children ?? []).map((child) =>
        selection.getItemId(child as BaseRowData),
      );
      return [itemId, ...childIds];
    });
    return (
      allIds.length > 0 && allIds.every((id) => selectionHook.isSelected(id))
    );
  }, [selection, sortedData, selectionHook]);

  const isPartiallySelected = useMemo(() => {
    if (!selection?.enabled) return false;
    const allIds = sortedData.flatMap((item) => {
      const itemId = selection.getItemId(item);
      const childIds = (item.children ?? []).map((child) =>
        selection.getItemId(child as BaseRowData),
      );
      return [itemId, ...childIds];
    });
    const selectedCount = allIds.filter((id) =>
      selectionHook.isSelected(id),
    ).length;
    return selectedCount > 0 && selectedCount < allIds.length;
  }, [selection, sortedData, selectionHook]);

  // Render cell content
  // Accepts either parent or child column types
  const renderCell = (
    column: ParentTableColumn<T> | ChildTableColumn,
    row: T | ChildRowData,
    isParent: boolean,
    renderContext: CellRenderContext,
  ): React.ReactNode => {
    const value = row[column.key as keyof typeof row];

    // Use specific render function if available
    const renderFn = isParent
      ? (column.renderParent ?? column.render)
      : (column.renderChild ?? column.render);

    if (renderFn) {
      // Type assertion: when rendering child cells (isParent=false), row is ChildRowData
      return renderFn(
        value,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (isParent ? row : (row as ChildRowData)) as any,
        renderContext,
      );
    }

    // Default rendering
    return <span>{String(value ?? "-")}</span>;
  };

  // Render skeleton row
  const renderSkeletonRow = (index: number) => {
    const skeletonWidths = ["w-16", "w-20", "w-24", "w-32", "w-40"];
    const randomWidth = skeletonWidths[index % skeletonWidths.length];

    return (
      <tr
        key={`skeleton-${index}`}
        className={clsx(
          "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700",
          striped &&
            index % 2 === 1 &&
            "bg-mw-neutral-50 dark:bg-mw-neutral-800",
        )}
      >
        {buildCellsArray(
          visibleColumns.map((column) => (
            <td
              key={column.key}
              className={clsx(
                `${cellPadding} whitespace-nowrap text-sm`,
                column.align === "right" && "text-right",
                column.align === "center" && "text-center",
                column.className,
              )}
            >
              <Skeleton className={`h-4 ${randomWidth}`} />
            </td>
          )),
          selection?.enabled ? (
            <td
              key="selection-skeleton"
              className={`${cellPadding} whitespace-nowrap w-12 sticky left-0 z-20 bg-inherit`}
            >
              <div className="flex items-center rounded-lg">
                <Skeleton className="h-4 w-4 rounded" />
              </div>
            </td>
          ) : null,
          expansion?.enabled ? (
            <td key="expansion-skeleton">
              <Skeleton className="h-4 w-4 rounded" />
            </td>
          ) : null,
        )}
      </tr>
    );
  };

  // Render group headers
  const renderGroupHeaders = () => {
    if (!groupHeaders || groupHeaders.length === 0) return null;

    const calculateGroupColspans = () => {
      let currentIndex = 0;
      const groupColspans: number[] = [];

      for (const group of groupHeaders) {
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
    };

    const groupColspans = calculateGroupColspans();

    return (
      <tr className="bg-mw-primary-100">
        {selection?.enabled && (
          <th
            rowSpan={2}
            className={`${headerPadding} w-4 sticky left-0 z-20 border-b border-mw-primary-100 bg-mw-primary-100`}
          >
            <div className="flex items-center">
              <Checkbox
                checked={allSelected}
                onChange={(e) => handleSelectAll?.(e.target.checked)}
                isIndeterminate={isPartiallySelected}
              />
            </div>
          </th>
        )}
        {expansion?.enabled && <th rowSpan={2}></th>}
        {groupHeaders.map((group, index) => {
          const actualColspan = groupColspans[index];
          if (actualColspan === 0) return null;

          const alignClasses = {
            left: "text-left",
            center: "text-center",
            right: "text-right",
          };

          const align = group.align || "left";

          return (
            <th
              key={`group-header-${index}`}
              colSpan={actualColspan}
              className={clsx(
                "p-4 text-xs font-medium text-og-black border-b border-mw-primary-100",
                alignClasses[align],
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

  // Render parent row
  const renderParentRow = (item: T, index: number) => {
    const itemId = selection?.getItemId(item) || item.id;
    const isExpanded = expansionHook.isExpanded(itemId);
    const isSelected = selection?.enabled
      ? selectionHook.isSelected(itemId)
      : false;
    const children = (item.children ?? []) as ChildRowData[];

    // Calculate indeterminate state: some (but not all) children are selected
    let isIndeterminate = false;
    let checkboxChecked = isSelected;
    if (selection?.enabled && children.length > 0) {
      // Always calculate child-based state to ensure parent checkbox reflects children
      const childIds = children.map(
        (child) => selection?.getItemId(child) || child.id,
      );
      const selectedChildCount = childIds.filter((childId) =>
        selectionHook.isSelected(childId),
      ).length;

      if (selectedChildCount > 0 && selectedChildCount < children.length) {
        // Some children selected but not all
        isIndeterminate = true;
        checkboxChecked = false;
      } else if (selectedChildCount === children.length) {
        // All children selected
        checkboxChecked = true;
      } else if (selectedChildCount === 0) {
        // No children selected
        checkboxChecked = false;
      }
    }

    const cellContext: CellRenderContext = {
      ...tableContext,
      rowIndex: index,
      isParent: true,
      isExpanded,
      isSelected,
      children,
    };

    const parentClassName =
      typeof parentRowClassName === "function"
        ? parentRowClassName(item)
        : parentRowClassName;

    return (
      <Fragment key={itemId}>
        <tr
          // cn (tailwind-merge) so parentRowClassName can override the base row
          // background. It is applied before hover/selected so those still win.
          className={cn(
            "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700",
            "font-medium",
            striped &&
              index % 2 === 1 &&
              "bg-mw-neutral-50 dark:bg-mw-neutral-800",
            parentClassName,
            // Row highlight on hover matches ui/Table: it is a readability aid,
            // so it does not depend on the row being clickable.
            hoverable &&
              "hover:bg-mw-primary-50 dark:hover:bg-mw-primary-900/20",
            hoverable && onRowClick && "cursor-pointer",
            isSelected && "bg-mw-primary-50 dark:bg-mw-primary-900/20",
          )}
          onClick={() => onRowClick?.(item, true)}
        >
          {buildCellsArray(
            visibleColumns.map((column) => {
              const cellContextWithEditing: CellRenderContext = {
                ...cellContext,
              };

              const frozenClasses = column.frozen
                ? column.frozenPosition === "left"
                  ? "sticky left-0 z-10 bg-inherit"
                  : "sticky right-0 z-10 bg-inherit"
                : "";

              return (
                <td
                  key={column.key}
                  className={clsx(
                    `${cellPadding} whitespace-nowrap text-sm font-medium text-og-black dark:text-white`,
                    column.align === "right" && "text-right",
                    column.align === "center" && "text-center",
                    frozenClasses,
                    column.className,
                  )}
                  style={{
                    width: column.width,
                    minWidth:
                      column.width && column.width.includes("px")
                        ? column.width
                        : undefined,
                  }}
                >
                  {renderCell(column, item, true, cellContextWithEditing)}
                </td>
              );
            }),
            selection?.enabled ? (
              <td
                key="selection-cell"
                className={`${cellPadding} whitespace-nowrap w-12 sticky left-0 z-20 bg-inherit`}
              >
                <div className="flex items-center rounded-lg">
                  <Checkbox
                    checked={checkboxChecked}
                    isIndeterminate={isIndeterminate}
                    onChange={(e) => {
                      const newSelected = new Set(selectionHook.selectedItems);
                      if (e.target.checked) {
                        newSelected.add(itemId);
                        // Cascade selection: if selectChildrenOnParent is true (default), select all children
                        if (
                          selection?.selectChildrenOnParent !== false &&
                          children.length > 0
                        ) {
                          children.forEach((child) => {
                            const childId =
                              selection?.getItemId(child) || child.id;
                            newSelected.add(childId);
                          });
                        }
                      } else {
                        newSelected.delete(itemId);
                        // When deselecting parent, also deselect all children
                        if (children.length > 0) {
                          children.forEach((child) => {
                            const childId =
                              selection?.getItemId(child) || child.id;
                            newSelected.delete(childId);
                          });
                        }
                      }
                      selectionHook.selectAll(Array.from(newSelected));
                    }}
                    data-testid={`checkbox-${itemId}`}
                  />
                </div>
              </td>
            ) : null,
            expansion?.enabled ? (
              <td key="expansion-cell">
                <Button
                  variant="ghost"
                  size="iconMd"
                  onClick={(e) => {
                    e.stopPropagation();
                    expansionHook.toggleExpansion(itemId);
                  }}
                  data-testid={`button-expand-${itemId}`}
                >
                  {/* One rotating chevron rather than swapping icons, so the
                      trigger animates in step with the panel it opens. */}
                  <ChevronRight
                    className={clsx(
                      "size-5 text-mw-neutral-500 transition-transform duration-300 ease-out motion-reduce:transition-none",
                      isExpanded && "rotate-90",
                    )}
                  />
                </Button>
              </td>
            ) : null,
          )}
        </tr>

        {/* Child rows or custom children rendering */}
        {isExpanded &&
          (() => {
            // Check if custom render function is provided
            if (expansion?.renderChildren) {
              const customChildren = expansion.renderChildren(
                children,
                item,
                tableContext,
              );

              // Wrap with childrenWrapper if provided
              if (expansion.childrenWrapper) {
                const Wrapper = expansion.childrenWrapper;
                const wrapperProps = expansion.childrenWrapperProps ?? {};
                return (
                  <Wrapper {...wrapperProps} parentRow={item}>
                    {customChildren}
                  </Wrapper>
                );
              }

              return customChildren;
            }

            // Default: render as table rows
            const childRows = children.map((child, childIndex) => {
              const childId =
                selection?.getItemId(child as BaseRowData) || child.id;
              const isChildSelected = selection?.enabled
                ? selectionHook.isSelected(childId)
                : false;

              // Check if this is a loading row
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              const isLoadingRow = (child as any).isLoading === true;

              const childCellContext: CellRenderContext = {
                ...tableContext,
                rowIndex: childIndex,
                isParent: false,
                isExpanded: false,
                isSelected: isChildSelected,
                parentRow: item,
              };

              const childClassName =
                typeof childRowClassName === "function"
                  ? childRowClassName(child)
                  : childRowClassName;

              // Render loading row
              if (isLoadingRow) {
                return (
                  <tr
                    key={childId}
                    className={clsx(
                      "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700",
                      childClassName,
                    )}
                  >
                    {buildCellsArray(
                      [
                        <td
                          key="loading-cell"
                          colSpan={visibleChildrenColumns.length}
                          className="p-4 text-center"
                        >
                          <div className="flex items-center justify-center gap-2">
                            <Spinner size="sm" variant="primary" />
                            <span className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
                              Loading schedules...
                            </span>
                          </div>
                        </td>,
                      ],
                      selection?.enabled ? (
                        <td
                          key="loading-selection-cell"
                          className={`${cellPadding} whitespace-nowrap w-12 sticky left-0 z-20 bg-inherit`}
                        >
                          <div className="flex items-center rounded-lg">
                            <Skeleton className="h-4 w-4 rounded" />
                          </div>
                        </td>
                      ) : null,
                      // No expansion column in child rows (no nested children)
                      null,
                      true, // Use children columns for positioning
                    )}
                  </tr>
                );
              }

              return (
                <tr
                  key={childId}
                  className={cn(
                    "bg-white dark:bg-mw-neutral-900 border-b border-container-border dark:border-mw-neutral-700",
                    striped &&
                      (index + childIndex) % 2 === 1 &&
                      "bg-mw-neutral-50 dark:bg-mw-neutral-800",
                    childClassName,
                    // Child rows deliberately have no hover highlight - only
                    // clickable ones react, as before.
                    hoverable &&
                      onRowClick &&
                      "cursor-pointer hover:bg-mw-primary-50 dark:hover:bg-mw-neutral-800",
                    isChildSelected &&
                      "bg-mw-primary-50 dark:bg-mw-primary-900/20",
                  )}
                  onClick={() => onRowClick?.(child, false)}
                >
                  {buildCellsArray(
                    visibleChildrenColumns.map((column) => {
                      const childCellContextWithEditing: CellRenderContext = {
                        ...childCellContext,
                      };

                      const frozenClasses = column.frozen
                        ? column.frozenPosition === "left"
                          ? "sticky left-0 z-10 bg-inherit"
                          : "sticky right-0 z-10 bg-inherit"
                        : "";

                      return (
                        <td
                          key={column.key}
                          className={clsx(
                            `${cellPadding} whitespace-nowrap text-sm font-medium text-og-black dark:text-white`,
                            column.align === "right" && "text-right",
                            column.align === "center" && "text-center",
                            frozenClasses,
                            column.className,
                          )}
                          style={{
                            width: column.width,
                            minWidth:
                              column.width && column.width.includes("px")
                                ? column.width
                                : undefined,
                          }}
                        >
                          {renderCell(
                            column as ChildTableColumn,
                            child,
                            false,
                            childCellContextWithEditing,
                          )}
                        </td>
                      );
                    }),
                    selection?.enabled ? (
                      <td
                        key="child-selection-cell"
                        className={`${cellPadding} whitespace-nowrap w-12 sticky left-0 z-20 bg-inherit`}
                      >
                        <div className="flex items-center rounded-lg">
                          <Checkbox
                            checked={isChildSelected}
                            onChange={(e) => {
                              e.stopPropagation();
                              if (e.target.checked !== isChildSelected) {
                                const newSelected = new Set(
                                  selectionHook.selectedItems,
                                );

                                // Toggle the child
                                if (newSelected.has(childId)) {
                                  newSelected.delete(childId);
                                } else {
                                  newSelected.add(childId);
                                }

                                // Update parent selection based on children state
                                const parentId =
                                  selection?.getItemId(item) || item.id;
                                const allChildIds = children.map(
                                  (c) => selection?.getItemId(c) || c.id,
                                );
                                const allChildrenSelected = allChildIds.every(
                                  (id) => newSelected.has(id),
                                );

                                // If all children are selected, select parent (if enabled)
                                if (
                                  allChildrenSelected &&
                                  selection?.selectParentOnAllChildren !== false
                                ) {
                                  newSelected.add(parentId);
                                } else {
                                  // Not all children selected, deselect parent
                                  newSelected.delete(parentId);
                                }

                                selectionHook.selectAll(
                                  Array.from(newSelected),
                                );
                              }
                            }}
                            data-testid={`checkbox-${childId}`}
                          />
                        </div>
                      </td>
                    ) : null,
                    // Child rows have no nested children, so there is no expand
                    // control. When they render inline in the parent table they
                    // still need a placeholder cell, otherwise every child cell
                    // shifts one column left. In the wrapper/sub-table modes the
                    // children get their own header row, which also omits the
                    // expansion column, so no placeholder is wanted there.
                    childrenRenderInline && expansion?.enabled ? (
                      <td
                        key="child-expansion-placeholder"
                        aria-hidden="true"
                      />
                    ) : null,
                    true, // Use children columns for positioning
                  )}
                </tr>
              );
            });

            // Helper function to render children table headers
            const renderChildrenHeaders = () => {
              if (expansion?.showChildrenHeaders === false) return null;

              return (
                <thead
                  className={`bg-mw-neutral-50 dark:bg-mw-neutral-800 border-b border-mw-neutral-200 dark:border-mw-neutral-700 ${expansion?.showChildrenHeaders ? "" : "invisible"}`}
                >
                  <tr className="h-8">
                    {/* Selection column header - always first in child table */}
                    {selection?.enabled && (
                      <th
                        className={`${headerPadding} w-12 text-left text-og-black ${headerTextSize} font-medium leading-none`}
                      >
                        {/* Empty header for selection */}
                      </th>
                    )}
                    {/* No expansion column header in child table (no nested children) */}
                    {/* Data column headers - use children columns if provided */}
                    {visibleChildrenColumns.map((column) => (
                      <th
                        key={column.key}
                        className={clsx(
                          `${headerPadding} text-left text-og-black ${headerTextSize} font-medium leading-none`,
                          column.align === "center" && "text-center",
                          column.align === "right" && "text-right",
                        )}
                        style={{ width: column.width }}
                      >
                        <div
                          className={clsx(
                            "flex items-center gap-1",
                            column.align === "center" && "justify-center",
                            column.align === "right" && "justify-end",
                          )}
                        >
                          <span>{column.header}</span>
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
              );
            };

            // Wrap table rows with childrenWrapper if provided
            if (expansion?.childrenWrapper) {
              const Wrapper = expansion.childrenWrapper;
              const wrapperProps = expansion.childrenWrapperProps || {};

              // Check if children are table rows and wrapper should get headers
              const shouldAddHeaders =
                expansion?.showChildrenHeaders !== false &&
                childRows.length > 0;

              // If headers should be added, wrap children with table structure
              const childrenWithHeaders = shouldAddHeaders ? (
                <>
                  {renderChildrenHeaders()}
                  <tbody>{childRows}</tbody>
                </>
              ) : (
                childRows
              );

              return (
                <Wrapper {...wrapperProps} parentRow={item}>
                  {childrenWithHeaders}
                </Wrapper>
              );
            }

            return childRows;
          })()}
      </Fragment>
    );
  };

  // Render total row
  const renderTotalRow = () => {
    if (!totalRow || loading || sortedData.length === 0) return null;

    const cells: React.ReactNode[] = [];
    let skipCount = 0;

    visibleColumns.forEach((column) => {
      if (skipCount > 0) {
        skipCount--;
        return;
      }

      const colspan = totalRowColspans?.[column.key] || 1;
      skipCount = colspan - 1;

      const value = totalRow[column.key as keyof T];
      const content = column.render
        ? column.render(value as unknown, totalRow, {
            ...tableContext,
            rowIndex: -1,
            isParent: true,
            isExpanded: false,
            isSelected: false,
          })
        : value;

      const alignClasses = {
        left: "text-left",
        center: "text-center",
        right: "text-right",
      };

      const cellAlignment =
        totalRowCellAlignments?.[column.key] || column.align || "left";
      const cellClassName = totalRowCellClassNames?.[column.key];

      cells.push(
        <td
          key={column.key}
          colSpan={colspan}
          className={clsx(
            `${cellPadding} whitespace-nowrap text-xs font-bold text-og-black`,
            alignClasses[cellAlignment],
            column.className,
            cellClassName,
          )}
          style={{ width: column.width }}
        >
          {content as React.ReactNode}
        </td>,
      );
    });

    return (
      <tr className={clsx("font-semibold", totalRowClassName)}>
        {buildCellsArray(
          cells,
          selection?.enabled ? (
            <td
              key="total-selection-cell"
              className={`${cellPadding} whitespace-nowrap w-12 sticky left-0 z-20 bg-mw-primary-50 dark:bg-mw-primary-900/20`}
            />
          ) : null,
          expansion?.enabled ? (
            <td
              key="total-expansion-cell"
              className="bg-mw-primary-50 dark:bg-mw-primary-900/20"
            />
          ) : null,
        )}
      </tr>
    );
  };

  const tableId = "hierarchical-table-container";

  return (
    <div id={tableId} className={clsx("w-full overflow-x-auto", className)}>
      <table
        id={`${tableId}-table`}
        className="min-w-full divide-y divide-mw-neutral-200 dark:divide-mw-neutral-700 table-auto"
        style={{ tableLayout: "auto" }}
      >
        <thead className="bg-grey-50 border-b border-mw-primary-100">
          {renderGroupHeaders()}
          <tr className="h-8">
            {buildCellsArray(
              visibleColumns.map((column, visibleIndex) => {
                const sortKey = column.sortKey || column.key;
                const { direction } = getSortInfo(sortKey);
                const frozenClasses = column.frozen
                  ? column.frozenPosition === "left"
                    ? "sticky left-0 z-10 bg-grey-50"
                    : "sticky right-0 z-10 bg-grey-50"
                  : "";

                // Show icon on the last visible column
                // This ensures the icon always appears on the rightmost visible column
                const isLastVisibleColumn =
                  visibleIndex === visibleColumns.length - 1;
                const showVisibilityIcon =
                  columnVisibilityDrawer?.enabled &&
                  !columnVisibilityDrawer?.hideHeaderTrigger &&
                  isLastVisibleColumn;

                return (
                  <th
                    id={`table-header-${column.key}`}
                    key={column.key}
                    className={clsx(
                      `${headerPadding} text-left text-og-black ${headerTextSize} font-medium leading-none`,
                      column.sortable &&
                        "cursor-pointer select-none hover:bg-mw-neutral-100 dark:hover:bg-mw-neutral-700 transition-colors",
                      column.align === "center" && "text-center",
                      column.align === "right" && "text-right",
                      direction && "bg-mw-neutral-50 dark:bg-mw-neutral-800",
                      frozenClasses,
                    )}
                    style={{
                      width: column.width,
                      minWidth:
                        column.width && column.width.includes("px")
                          ? column.width
                          : undefined,
                    }}
                    onClick={() =>
                      column.sortable && handleSortChangeClick(sortKey)
                    }
                  >
                    <div
                      className={clsx(
                        "flex items-center gap-1",
                        column.align === "center" && "justify-center",
                        column.align === "right" && "justify-end",
                      )}
                    >
                      <TooltipHeader
                        label={column.header}
                        tooltip={column.headerTooltip}
                        align={column.align}
                      />
                      {renderSortIcon(sortKey, column.sortable)}
                      {showVisibilityIcon && (
                        <Button
                          variant="ghost"
                          size="iconMd"
                          className="ml-1"
                          onClick={(e) => {
                            e.stopPropagation();
                            setIsDrawerOpen(true);
                          }}
                          data-testid="column-visibility-toggle"
                        >
                          <Columns3Cog className="size-4 text-mw-neutral-500" />
                        </Button>
                      )}
                    </div>
                  </th>
                );
              }),
              !groupHeaders && selection?.enabled ? (
                <th
                  key="selection-header"
                  id="table-header-select-all"
                  className={`${headerPadding} w-4 sticky left-0 z-20 bg-grey-50`}
                >
                  <div className="flex items-center">
                    <Checkbox
                      checked={allSelected}
                      onChange={(e) => handleSelectAll?.(e.target.checked)}
                      isIndeterminate={isPartiallySelected}
                    />
                  </div>
                </th>
              ) : null,
              !groupHeaders && expansion?.enabled ? (
                <th key="expansion-header"></th>
              ) : null,
            )}
          </tr>
        </thead>
        <tbody className="bg-white dark:bg-mw-neutral-900 divide-y divide-mw-neutral-200 dark:divide-mw-neutral-700">
          {loading ? (
            Array.from({ length: skeletonRowsCount }).map((_, index) =>
              renderSkeletonRow(index),
            )
          ) : (
            <>
              {sortedData.length === 0 ? (
                <tr>
                  <td
                    colSpan={
                      visibleColumns.length +
                      (selection?.enabled ? 1 : 0) +
                      (expansion?.enabled ? 1 : 0)
                    }
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
                <>
                  {sortedData.map((item, index) =>
                    renderParentRow(item, index),
                  )}
                  {renderTotalRow()}
                </>
              )}
            </>
          )}
        </tbody>
      </table>
      {/* Column Visibility Drawer */}
      {columnVisibilityDrawer?.enabled && (
        <ColumnVisibilityDrawer
          isOpen={isDrawerOpen}
          onClose={() => setIsDrawerOpen(false)}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={handleColumnVisibilityChange}
          availableColumns={availableColumns}
          id="hierarchical-table-column-visibility-drawer"
        />
      )}
    </div>
  );
}

// Export types and utilities
export * from "./types";
export * from "./cell-renderers";
export * from "./hooks";
export * from "./sorting";
