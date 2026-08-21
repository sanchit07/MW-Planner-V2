import type { ColDef } from "ag-grid-community";

export type AgGridSortDirection = "asc" | "desc";

export interface AgGridSortState {
  key: string;
  direction: AgGridSortDirection;
}

export interface AgGridTableServerSideConfig {
  totalCount: number;
  currentPage: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
  pageSizeSelector?: number[];
}

export interface AgGridTableProps<T = unknown> {
  rowData: T[];
  columnDefs: ColDef<T>[];
  getRowId: (data: T) => string;
  loading?: boolean;
  emptyMessage?: string;
  className?: string;
  height?: string | number;
  rowSelection?: "single" | "multiple" | false;
  selectedRowIds?: string[];
  onSelectionChange?: (selectedIds: string[]) => void;
  sortState?: AgGridSortState[];
  onSortChange?: (sort: AgGridSortState[]) => void;
  hiddenColumns?: string[];
  onColumnVisibilityChange?: (hiddenColumnIds: string[]) => void;
  suppressColumnVisibilityHandling?: boolean;
  defaultColDef?: ColDef<T>;
  domLayout?: "normal" | "autoHeight" | "print";
  /** When true, pagination is disabled in the grid; parent controls page/size and passes one page of rowData. */
  serverSidePagination?: boolean;
  /** Required when serverSidePagination is true. Drives external pagination and total row count. */
  serverSideConfig?: AgGridTableServerSideConfig;
  /** Used for client-side pagination when serverSidePagination is false. Ignored when serverSidePagination is true. */
  totalCount?: number;
  cacheBlockSize?: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  footerData?: any[]; // New prop for footer data
  pagination?: boolean;
}
