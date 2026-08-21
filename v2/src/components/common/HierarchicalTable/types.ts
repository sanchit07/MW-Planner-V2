import React from "react";

/**
 * Base types for hierarchical table component
 */

export interface BaseRowData {
  id: string;
  [key: string]: unknown;
}

export interface ParentRowData extends BaseRowData {
  children?: ChildRowData[];
}

export interface ChildRowData extends BaseRowData {
  parentId: string;
}

/**
 * Sort direction type
 */
export type SortDirection = "asc" | "desc" | null;

/**
 * Sort state for a single column
 */
export interface ColumnSort {
  key: string;
  direction: "asc" | "desc";
}

/**
 * Custom sort function type
 */
export type CustomSortFn<T> = (a: T, b: T, direction: "asc" | "desc") => number;

/**
 * Group column header interface
 */
export interface TableGroupHeader {
  text: string;
  colspan: number;
  backgroundColor?: string;
  align?: "left" | "center" | "right";
  className?: string;
}

/**
 * Base column configuration
 */
export interface HierarchicalTableColumn<T = BaseRowData> {
  key: string;
  header: string;
  headerTooltip?: string;
  align?: "left" | "center" | "right";
  width?: string;
  className?: string;
  visible?: boolean | ((context: TableContext) => boolean);
  // Sorting
  sortable?: boolean;
  sortKey?: string; // The actual key to use for sorting (defaults to column key)
  customSort?: CustomSortFn<T>; // Custom sort function for this column
  // Frozen columns
  frozen?: boolean;
  frozenPosition?: "left" | "right";
  // Exclude from column visibility drawer (e.g. serial number)
  hideFromColumnVisibility?: boolean;
  // Render functions
  render?: (
    value: unknown,
    row: T,
    context: CellRenderContext,
  ) => React.ReactNode;
  renderParent?: (
    value: unknown,
    row: T,
    context: CellRenderContext,
  ) => React.ReactNode;
  renderChild?: (
    value: unknown,
    row: T,
    context: CellRenderContext,
  ) => React.ReactNode;
  // Aggregation for parent rows
  aggregate?: (children: T[], parent: T) => unknown;
}

/**
 * Column configuration for parent rows
 * Extends base column with parent-specific features
 */
export type ParentTableColumn<T extends ParentRowData = ParentRowData> =
  HierarchicalTableColumn<T>;

/**
 * Column configuration for child rows
 * Extends base column with child-specific features
 */
export type ChildTableColumn = HierarchicalTableColumn<ChildRowData>;

/**
 * Selection configuration
 */
export interface SelectionConfig {
  enabled: boolean;
  mode?: "single" | "multiple" | "hierarchical";
  selectedItems?: Set<string>;
  onSelectionChange?: (selected: Set<string>) => void;
  getItemId: (row: BaseRowData) => string;
  // For hierarchical selection
  selectParentOnAllChildren?: boolean; // If true, selecting all children selects the parent
  selectChildrenOnParent?: boolean; // If true, selecting a parent automatically selects all its children (default: true)
  // Column position (0-based index, -1 or undefined means first position)
  columnPosition?: number;
}

/**
 * Expansion configuration
 */
export interface ExpansionConfig {
  enabled: boolean;
  expandedItems?: Set<string>;
  onExpansionChange?: (expanded: Set<string>) => void;
  getItemId: (row: BaseRowData) => string;
  defaultExpanded?: boolean;
  // Column position (0-based index, -1 or undefined means after selection column or first if no selection)
  columnPosition?: number;
  // Custom render function for children (instead of table rows)
  // If provided, children will be rendered using this function instead of as table rows
  renderChildren?: (
    children: ChildRowData[],
    parentRow: BaseRowData,
    context: TableContext,
  ) => React.ReactNode;
  // Wrapper element/component for children (e.g., <div>, <tbody>, custom component)
  // If renderChildren is provided, this wraps the result of renderChildren
  // If renderChildren is not provided, this wraps the table rows
  childrenWrapper?:
    | React.ComponentType<{ children: React.ReactNode; parentRow: BaseRowData }>
    | React.ElementType;
  // Props to pass to childrenWrapper
  childrenWrapperProps?: Record<string, unknown>;
  // Whether to automatically add column headers when children are rendered as table rows
  // Default: true (automatically detects and adds headers)
  // Set to false to hide children table headers
  showChildrenHeaders?: boolean;
}

/**
 * Context passed to render functions
 */
export interface TableContext {
  selectedItems: Set<string>;
  expandedItems: Set<string>;
  [key: string]: unknown;
}

export interface CellRenderContext extends TableContext {
  rowIndex: number;
  isParent: boolean;
  isExpanded: boolean;
  isSelected: boolean;
  parentRow?: BaseRowData;
  children?: BaseRowData[];
}

/**
 * Sorting configuration
 */
export interface SortingConfig {
  mode?: "client" | "remote";
  multiSort?: boolean;
  defaultSort?: ColumnSort[];
  sortState?: ColumnSort[];
  onSortChange?: (sort: ColumnSort[]) => void;
}

/**
 * Persistence configuration
 */
export interface PersistenceConfig {
  enabled: boolean;
  storageKey?: string;
}

/**
 * Column visibility drawer configuration
 */
export interface ColumnVisibilityDrawerConfig {
  enabled?: boolean; // Whether to show the column visibility drawer icon
  columnPosition?: number; // Column index where the icon should appear (default: last column)
  storageKey?: string; // Optional separate storage key for column visibility (defaults to persistence.storageKey)
  // Hide the built-in trigger icon rendered in the last header cell.
  // Use with isOpen/onOpenChange to drive the drawer from an external control.
  hideHeaderTrigger?: boolean;
  // Controlled open state. When undefined, the table manages the state internally.
  isOpen?: boolean;
  onOpenChange?: (isOpen: boolean) => void;
}

/**
 * Main component props
 */
export interface HierarchicalTableProps<
  T extends ParentRowData = ParentRowData,
> {
  data: T[];
  // Columns for parent rows
  columns: ParentTableColumn<T>[];
  // Optional separate columns for children (if different from parent)
  // If not provided, parent columns will be used for children
  childrenColumns?: ChildTableColumn[];
  // Selection
  selection?: SelectionConfig;
  // Expansion
  expansion?: ExpansionConfig;
  // Sorting
  sorting?: SortingConfig;
  // Persistence
  persistence?: PersistenceConfig;
  // Group headers
  groupHeaders?: TableGroupHeader[];
  // Total row
  totalRow?: T;
  totalRowColspans?: Record<string, number>;
  totalRowClassName?: string;
  totalRowCellClassNames?: Record<string, string>;
  totalRowCellAlignments?: Record<string, "left" | "center" | "right">;
  // Loading & Empty states
  loading?: boolean;
  skeletonRowsCount?: number;
  emptyMessage?: string;
  emptyRenderer?: () => React.ReactNode;
  // Styling
  className?: string;
  parentRowClassName?: string | ((row: T) => string);
  childRowClassName?: string | ((row: ChildRowData) => string);
  striped?: boolean;
  hoverable?: boolean;
  // Row spacing. "compact" (default) keeps the historical p-2 / text-xs header
  // spacing; "comfortable" uses roomier p-4 cells with a taller text-sm header.
  density?: "compact" | "comfortable";
  // Hidden columns (controlled)
  hiddenColumns?: string[];
  onColumnVisibilityChange?: (hiddenColumns: string[]) => void;
  // Column visibility drawer
  columnVisibilityDrawer?: ColumnVisibilityDrawerConfig;
  // Callbacks
  onRowClick?: (row: T | ChildRowData, isParent: boolean) => void;
  // Additional context
  context?: Record<string, unknown>;
}

/**
 * Internal state
 */
export interface TableState {
  selectedItems: Set<string>;
  expandedItems: Set<string>;
  sortState: ColumnSort[];
  hiddenColumns: string[];
}
