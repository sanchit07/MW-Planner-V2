import { useTranslate } from "@tolgee/react";
import {
  AllCommunityModule,
  ModuleRegistry,
  type ApplyColumnStateParams,
  type GridApi,
  type GridReadyEvent,
  type PaginationChangedEvent,
  type SelectionChangedEvent,
  type SortChangedEvent,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";
import React, { useCallback, useEffect, useMemo, useRef } from "react";

import type { AgGridTableProps, AgGridSortState } from "./types";

import "../../../styles/ag-grid-overrides.css";

ModuleRegistry.registerModules([AllCommunityModule]);

const AG_GRID_THEME_CLASS = "ag-theme-quartz";
const DEFAULT_PAGE_SIZE_SELECTOR = [10, 25, 50, 100];

const SERVER_SIDE_COMPARATOR = () => 0;

function escapeOverlayText(text: string): string {
  return text
    .split("&")
    .join("&amp;")
    .split("<")
    .join("&lt;")
    .split(">")
    .join("&gt;")
    .split('"')
    .join("&quot;")
    .split("'")
    .join("&#39;");
}

function sortStateToColumnState(
  sortState: AgGridSortState[],
): ApplyColumnStateParams["state"] {
  return sortState.map((s, idx) => ({
    colId: s.key,
    sort: s.direction,
    sortIndex: idx,
  }));
}

function getRowSelectionOption<T>(
  rowSelection: AgGridTableProps<T>["rowSelection"],
  serverSidePagination: boolean,
):
  | undefined
  | "single"
  | "multiple"
  | {
      mode: "multiRow" | "singleRow";
      isRowSelectable: (params: { data: T }) => boolean;
    } {
  if (rowSelection === false) return undefined;
  if (serverSidePagination) {
    return {
      mode: rowSelection === "multiple" ? "multiRow" : "singleRow",
      isRowSelectable: (params: { data: T }) =>
        !(params.data as { __isPlaceholder?: boolean })?.__isPlaceholder,
    };
  }
  return rowSelection;
}

function AgGridTableInner<T = unknown>(props: AgGridTableProps<T>) {
  const {
    rowData,
    columnDefs,
    getRowId,
    loading = false,
    emptyMessage = "No data",
    className = "",
    height,
    rowSelection = false,
    selectedRowIds = [],
    onSelectionChange,
    sortState = [],
    onSortChange,
    hiddenColumns = [],
    defaultColDef,
    domLayout = "normal",
    serverSidePagination = false,
    serverSideConfig,
    cacheBlockSize,
    footerData,
    pagination = true,
  } = props;

  const { t: tCommon } = useTranslate(["common"]);

  const agGridLocaleText = useMemo(
    () => ({
      pageSizeSelectorLabel: tCommon("agGrid.pageSizeSelectorLabel"),
      page: tCommon("agGrid.page"),
      to: tCommon("agGrid.to"),
      of: tCommon("agGrid.of"),
      noRowsToShow: tCommon("agGrid.noRowsToShow"),
      loadingOoo: tCommon("agGrid.loadingOoo"),
    }),
    [tCommon],
  );

  const gridRef = useRef<AgGridReact<T>>(null);
  const apiRef = useRef<GridApi<T> | null>(null);
  const isSortFromExternalRef = useRef(false);
  const isPaginationFromExternalRef = useRef(false);

  const onGridReady = useCallback(
    (event: GridReadyEvent<T>) => {
      apiRef.current = event.api;
      if (sortState.length > 0) {
        isSortFromExternalRef.current = true;
        event.api.applyColumnState({
          state: sortStateToColumnState(sortState),
        });
      }
      if (serverSidePagination && serverSideConfig) {
        isPaginationFromExternalRef.current = true;
        event.api.paginationGoToPage(serverSideConfig.currentPage - 1);
        event.api.setGridOption(
          "paginationPageSize",
          serverSideConfig.pageSize,
        );
      }
    },
    [sortState, serverSidePagination, serverSideConfig],
  );

  useEffect(
    () => {
      if (!serverSidePagination || !serverSideConfig || !apiRef.current) return;
      const api = apiRef.current;
      const currentGridPage = api.paginationGetCurrentPage();
      const currentGridSize = api.paginationGetPageSize();
      const targetPage = serverSideConfig.currentPage - 1;
      if (
        currentGridPage !== targetPage ||
        currentGridSize !== serverSideConfig.pageSize
      ) {
        isPaginationFromExternalRef.current = true;
        api.paginationGoToPage(targetPage);
        api.setGridOption("paginationPageSize", serverSideConfig.pageSize);
      }
    },
    // Intentionally depend only on currentPage/pageSize to avoid syncing on callback identity changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [
      serverSidePagination,
      serverSideConfig?.currentPage,
      serverSideConfig?.pageSize,
    ],
  );

  const onPaginationChanged = useCallback(
    (event: PaginationChangedEvent<T>) => {
      if (!serverSidePagination || !serverSideConfig || !event.api) return;
      if (event.newData) return;
      if (isPaginationFromExternalRef.current) {
        isPaginationFromExternalRef.current = false;
        return;
      }
      if (event.newPage) {
        const page = event.api.paginationGetCurrentPage() + 1;
        if (page !== serverSideConfig.currentPage) {
          serverSideConfig.onPageChange(page);
        }
      }
      if (event.newPageSize) {
        const pageSize = event.api.paginationGetPageSize();
        if (pageSize !== serverSideConfig.pageSize) {
          serverSideConfig.onPageSizeChange(pageSize);
        }
      }
    },
    [serverSidePagination, serverSideConfig],
  );

  const onSortChanged = useCallback(
    (event: SortChangedEvent<T>) => {
      if (isSortFromExternalRef.current) {
        isSortFromExternalRef.current = false;
        return;
      }
      if (!event.api || !onSortChange) return;
      const api = event.api;
      setTimeout(() => {
        const columnState = api.getColumnState();
        const sortModel = columnState
          .filter((col) => col.sort != null)
          .map((col) => ({
            key: col.colId ?? "",
            direction: (col.sort === "asc" || col.sort === "desc"
              ? col.sort
              : "asc") as "asc" | "desc",
          }));
        onSortChange(sortModel);
      }, 0);
    },
    [onSortChange],
  );

  const onSelectionChanged = useCallback(
    (event: SelectionChangedEvent<T>) => {
      if (!onSelectionChange || !event.api) return;
      const nodes = event.api.getSelectedRows();
      const ids = nodes
        .map((row) => (row ? getRowId(row) : ""))
        .filter(Boolean);
      onSelectionChange(ids);
    },
    [onSelectionChange, getRowId],
  );

  const columnDefsWithVisibility = useMemo(() => {
    const hiddenSet = new Set(hiddenColumns);
    return columnDefs.map((col) => ({
      ...col,
      hide: col.colId ? hiddenSet.has(String(col.colId)) : false,
      sortable: col.sortable !== false,
      comparator: col.comparator ?? SERVER_SIDE_COMPARATOR,
    }));
  }, [columnDefs, hiddenColumns]);

  useEffect(() => {
    if (!apiRef.current || rowSelection === false) return;
    apiRef.current.forEachNode((node) => {
      const id = node.data ? getRowId(node.data) : "";
      node.setSelected(selectedRowIds.includes(id));
    });
  }, [rowData, selectedRowIds, rowSelection, getRowId]);

  const containerStyle = useMemo(() => {
    if (height == null) return undefined;
    const h = typeof height === "number" ? `${height}px` : String(height);
    return { height: h, width: "100%" };
  }, [height]);

  return (
    <div
      className={`${AG_GRID_THEME_CLASS} ag-grid-table-wrapper ${className}`}
      style={containerStyle ?? { height: "100%", width: "100%" }}
    >
      <AgGridReact<T>
        ref={gridRef}
        rowData={rowData}
        columnDefs={columnDefsWithVisibility}
        getRowId={(params) => getRowId(params.data)}
        loading={loading}
        overlayNoRowsTemplate={`<span class="ag-overlay-no-rows-center">${escapeOverlayText(emptyMessage)}</span>`}
        rowSelection={getRowSelectionOption(rowSelection, serverSidePagination)}
        popupParent={typeof document !== "undefined" ? document.body : null}
        onGridReady={onGridReady}
        onSortChanged={onSortChanged}
        onPaginationChanged={onPaginationChanged}
        onSelectionChanged={
          rowSelection !== false ? onSelectionChanged : undefined
        }
        defaultColDef={{
          sortable: false,
          resizable: true,
          ...defaultColDef,
        }}
        domLayout={domLayout}
        rowHeight={60}
        suppressRowTransform={true}
        headerHeight={44}
        localeText={agGridLocaleText}
        pagination={pagination}
        paginationPageSize={serverSideConfig?.pageSize ?? 10}
        paginationPageSizeSelector={
          serverSideConfig?.pageSizeSelector ?? DEFAULT_PAGE_SIZE_SELECTOR
        }
        cacheBlockSize={cacheBlockSize}
        pinnedBottomRowData={footerData?.map((row, i) =>
          typeof row === "object" && row !== null && !("id" in row)
            ? { ...row, id: `pinned-bottom-${i}` }
            : row,
        )}
      />
    </div>
  );
}

export const AgGridTable = AgGridTableInner as <T = unknown>(
  props: AgGridTableProps<T>,
) => React.JSX.Element;
