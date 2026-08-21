/**
 * Hierarchical Table Component
 *
 * A reusable, configuration-driven table component for displaying parent-child relationships
 * Following SOLID principles and DRY methodology
 */

export { HierarchicalTable } from "./HierarchicalTable";
export type {
  HierarchicalTableProps,
  HierarchicalTableColumn,
  ParentTableColumn,
  ChildTableColumn,
  ParentRowData,
  ChildRowData,
  SelectionConfig,
  ExpansionConfig,
  TableContext,
  CellRenderContext,
  ColumnSort,
  SortDirection,
  TableGroupHeader,
  ColumnVisibilityDrawerConfig,
} from "./types";

export { TooltipHeader, SelectionCell, ExpandCell } from "./cell-renderers";

export {
  useTableSelection,
  useTableExpansion,
  useTableSorting,
  useTablePersistence,
} from "./hooks";

export { sortData, handleSortChange, defaultSortFn } from "./sorting";
