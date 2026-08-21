import ColumnVisibilityDrawer, {
  type ColumnVisibility,
} from "@components/common/HierarchicalTable/ColumnVisibilityDrawer";
import { AgGridTable } from "@components/ui/AgGridTable";
import type { AgGridSortState } from "@components/ui/AgGridTable";
import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { StatusBadge } from "@components/ui/StatusBadge";
import type { ColumnSort } from "@components/ui/Table";
import { Tooltip } from "@components/ui/Tooltip";
import {
  selectCampaignsUI,
  setColumnCustomizationOpen,
  setSelectedItems,
  setSortState,
} from "@services/campaign/campaignsUISlice";
import { RootState } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatNumber, normalizeGoalType } from "@utils/budget.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import storage from "@utils/storage";
import type { ColDef, ValueGetterParams } from "ag-grid-community";
import { MoreHorizontal } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { CampaignActionsDropdownContent } from "./CampaignActionsDropdownContent";
import type { CampaignDisplay } from "../../../types/campaign-display.types";
import { allCampaignColumn } from "../common/CampaignFilterModal";

const TABLE_STATE_STORAGE_KEY = "table_persisted_state";

const DEFAULT_COLUMN_VISIBILITY: ColumnVisibility = {
  serialNo: false,
  campaignName: true,
  brand: false,
  userName: true,
  flightDates: true,
  goalType: false,
  status: true,
  inventory: false,
  budget: false,
  totalCost: true,
};

const AVAILABLE_COLUMN_KEYS = [
  { key: "serialNo", labelKey: "campaignsList.columns.srNo" },
  { key: "campaignName", labelKey: "campaignsList.columns.campaignName" },
  { key: "brand", labelKey: "campaignsList.columns.brand" },
  { key: "userName", labelKey: "campaignsList.columns.plannedBy" },
  { key: "flightDates", labelKey: "campaignsList.columns.flightDates" },
  { key: "goalType", labelKey: "campaignsList.columns.goalType" },
  { key: "status", labelKey: "campaignsList.columns.status" },
  { key: "inventory", labelKey: "campaignsList.columns.inventories" },
  { key: "budget", labelKey: "campaignsList.columns.budget" },
  { key: "totalCost", labelKey: "campaignsList.columns.totalCost" },
] as const;

const COLUMN_KEY_TO_COL_ID: Record<string, string> = {
  serialNo: "serialNo",
  campaignName: "name",
  brand: "brand",
  userName: "userName",
  flightDates: "startDate",
  goalType: "goalType",
  status: "status",
  inventory: "inventory",
  budget: "budget",
  totalCost: "totalCost",
};

export interface CampaignRowPlaceholder {
  id: string;
  __isPlaceholder: true;
}

export function isPlaceholderRow(
  row: CampaignDisplay | CampaignRowPlaceholder,
): row is CampaignRowPlaceholder {
  return (row as CampaignRowPlaceholder).__isPlaceholder === true;
}

export interface CampaignsListViewProps {
  data: CampaignDisplay[];
  isLoading: boolean;
  isFetching: boolean;
  paginationInfo: {
    currentPage: number;
    totalPages: number;
    pageSize: number;
    totalItems: number;
  };
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onColumnVisibilityChange?: (hiddenColumns: string[]) => void;
  columnVisibility?: Record<string, boolean>;
  hiddenColumns?: string[];
  onRefresh?: () => void;
}

interface AgGridHeaderParams {
  displayName?: string;
}

function CampaignTableHeaderWithDrawer(params: AgGridHeaderParams) {
  return (
    <div className="custom-header-container inline-flex">
      <span className="header-label">{params.displayName}</span>
    </div>
  );
}

function loadColumnVisibilityFromStorage(storageKey: string): ColumnVisibility {
  try {
    const stored = storage.getItem(storageKey);
    if (stored) {
      const parsed = JSON.parse(stored);
      if (parsed.hiddenColumns && Array.isArray(parsed.hiddenColumns)) {
        const visibility = { ...DEFAULT_COLUMN_VISIBILITY };
        parsed.hiddenColumns.forEach((key: string) => {
          if (key in visibility) {
            visibility[key] = false;
          }
        });
        return visibility;
      }
    }
  } catch (error) {
    console.error("Error loading column visibility from storage:", error);
  }
  return { ...DEFAULT_COLUMN_VISIBILITY };
}

function useTableColumnVisibility(storageKey: string, viewType: string) {
  const loadColumnVisibility = useCallback(() => {
    return loadColumnVisibilityFromStorage(storageKey);
  }, [storageKey]);

  const [columnVisibility, setColumnVisibility] =
    useState<ColumnVisibility>(loadColumnVisibility);

  useEffect(() => {
    setColumnVisibility(loadColumnVisibility());
  }, [viewType, loadColumnVisibility]);

  const hiddenColumns = useMemo(() => {
    const hidden: string[] = [];
    allCampaignColumn.forEach((column) => {
      if (columnVisibility[column] === false) {
        hidden.push(column);
      }
    });
    return hidden;
  }, [columnVisibility]);

  const hiddenColumnIds = useMemo(() => {
    return hiddenColumns
      .map((key) => COLUMN_KEY_TO_COL_ID[key])
      .filter(Boolean);
  }, [hiddenColumns]);

  const handleColumnVisibilityChange = useCallback(
    (newVisibility: ColumnVisibility) => {
      setColumnVisibility(newVisibility);
      try {
        const stored = storage.getItem(storageKey);
        const currentState = stored
          ? JSON.parse(stored)
          : { sortState: [], hiddenColumns: [], viewType };

        const hiddenColumnsArray: string[] = [];
        allCampaignColumn.forEach((column) => {
          if (newVisibility[column] === false) {
            hiddenColumnsArray.push(column);
          }
        });

        storage.setItem(
          storageKey,
          JSON.stringify({
            ...currentState,
            hiddenColumns: hiddenColumnsArray,
            viewType,
          }),
        );
      } catch (error) {
        console.error("Error updating column visibility:", error);
      }
    },
    [storageKey, viewType],
  );

  return {
    columnVisibility,
    hiddenColumns,
    hiddenColumnIds,
    handleColumnVisibilityChange,
  };
}

function useCampaignListRowData(
  data: CampaignDisplay[],
  paginationInfo: CampaignsListViewProps["paginationInfo"],
): (CampaignDisplay | CampaignRowPlaceholder)[] {
  const { totalItems, currentPage, pageSize } = paginationInfo;
  return useMemo(() => {
    if (totalItems === 0) return [];
    const start = (currentPage - 1) * pageSize;
    const rows: (CampaignDisplay | CampaignRowPlaceholder)[] = Array.from(
      { length: totalItems },
      (_, i) => ({
        id: `campaign-placeholder-${i}`,
        __isPlaceholder: true as const,
      }),
    );
    data.forEach((row, i) => {
      rows[start + i] = row;
    });
    return rows;
  }, [data, totalItems, currentPage, pageSize]);
}

function buildCampaignListColumnDefs(
  onRefresh: CampaignsListViewProps["onRefresh"],
  tCampaigns: (key: string, params?: Record<string, string | number>) => string,
  tCommon: (key: string, params?: Record<string, string | number>) => string,
  activeCompanyId: string,
): ColDef<CampaignDisplay | CampaignRowPlaceholder>[] {
  const serialNoValueGetter = (
    params: ValueGetterParams<CampaignDisplay | CampaignRowPlaceholder>,
  ) => {
    if (!params.data || isPlaceholderRow(params.data)) return "—";
    const serialNo = (params.node?.rowIndex ?? 0) + 1;
    return serialNo.toString().padStart(2, "0");
  };

  return [
    {
      colId: "serialNo",
      headerName: tCampaigns("campaignsList.columns.srNo"),
      sortable: false,
      width: 64,
      minWidth: 64,
      valueGetter: serialNoValueGetter,
    },
    {
      colId: "name",
      headerName: tCampaigns("campaignsList.columns.campaignName"),
      sortable: true,
      field: "campaignName",
      minWidth: 250,
      flex: 1,
      cellRenderer: (params: {
        value: string;
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        return (
          <div className="space-y-1 min-w-0">
            <div className="flex items-center gap-2 min-w-0">
              <Tooltip content={params.value ?? ""}>
                <p className="truncate">{params.value}</p>
              </Tooltip>
              {(params.data as { dataMode?: string }).dataMode === "demo" && (
                <span className="shrink-0 rounded bg-amber-100 border border-amber-300 text-amber-800 text-[10px] font-semibold px-1.5 py-0.5 leading-none">
                  DEMO
                </span>
              )}
            </div>
            {params.data.planNumber && (
              <p className="text-secondary text-xs">
                {tCampaigns("viewCampaign.ID")}: {params.data.planNumber}
              </p>
            )}
          </div>
        );
      },
    },
    {
      colId: "brand",
      headerName: tCampaigns("campaignsList.columns.brand"),
      sortable: true,
      field: "brand",
      minWidth: 160,
      width: 180,
      cellRenderer: (params: {
        value: string;
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        return (
          <div className="space-y-1 min-w-0">
            <p className="break-words">{params.value}</p>
            <p className="text-secondary text-xs">
              {params.data.category || tCampaigns("campaignsList.notAvailable")}
            </p>
          </div>
        );
      },
    },
    {
      colId: "userName",
      headerName: tCampaigns("campaignsList.columns.plannedBy"),
      sortable: true,
      field: "userName",
      minWidth: 160,
      width: 260,
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        const isExternal = Boolean(
          params.data.currentCompanyId &&
            activeCompanyId &&
            params.data.currentCompanyId !== activeCompanyId,
        );
        return (
          <div className="space-y-1 min-w-0">
            <div className="flex items-center gap-1.5 min-w-0">
              <p className="truncate">{params.data.userName}</p>
              {isExternal && (
                <Tooltip
                  content={tCampaigns("campaignsList.externalUserTooltip")}
                  triggerClassName="shrink-0"
                >
                  <Badge
                    variant="warning"
                    size="sm"
                    className="whitespace-nowrap"
                  >
                    {tCampaigns("campaignsList.externalUser")}
                  </Badge>
                </Tooltip>
              )}
            </div>
            <p className="text-secondary text-xs">
              {params.data.currentCompanyName}
            </p>
          </div>
        );
      },
    },
    {
      colId: "startDate",
      headerName: tCampaigns("campaignsList.columns.flightDates"),
      sortable: true,
      minWidth: 200,
      flex: 1,
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        return (
          <div className="space-y-1 min-w-0">
            <p>
              {formatDisplayDate(params.data.startDate, tCommon)} -{" "}
              {formatDisplayDate(params.data.endDate, tCommon)}
            </p>
            <p className="text-mw-neutral-500 text-xs">
              {params.data.daysLeft && params.data.daysLeft !== "--"
                ? tCampaigns("campaignsList.durationDays", {
                    n: params.data.daysLeft,
                  })
                : params.data.daysLeft}
            </p>
          </div>
        );
      },
    },
    {
      colId: "goalType",
      headerName: tCampaigns("campaignsList.columns.goalType"),
      sortable: false,
      minWidth: 140,
      width: 160,
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) =>
        !params.data || isPlaceholderRow(params.data) ? (
          "—"
        ) : (
          <p className="min-w-0 break-words">
            {(() => {
              const raw = params.data.goals?.goalType;
              const targetValue = params.data.goals?.targetValue;
              const key = normalizeGoalType(raw);
              const KNOWN = ["IMPRESSIONS", "REACH", "SOV", "ADPLAYS"];
              const label =
                key && KNOWN.includes(key)
                  ? tCampaigns(`campaignsList.goalTypes.${key}`)
                  : raw;
              if (!label) return "—";
              return targetValue
                ? `${formatNumber(targetValue)} ${label}`
                : label;
            })()}
          </p>
        ),
    },
    {
      colId: "status",
      headerName: tCampaigns("campaignsList.columns.status"),
      sortable: true,
      width: 120,
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        return (
          <div className="min-w-0 self-stretch inline-flex items-center">
            <StatusBadge status={params.data.status.toLocaleLowerCase()}>
              {tCampaigns(
                `campaignsList.status.${params.data.status?.toUpperCase()}`,
              ) ||
                params.data.status.charAt(0).toUpperCase() +
                  params.data.status.slice(1)}
            </StatusBadge>
          </div>
        );
      },
    },
    {
      colId: "inventory",
      headerName: tCampaigns("campaignsList.columns.inventories"),
      sortable: true,
      minWidth: 100,
      width: 120,
      cellStyle: { textAlign: "center" },
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) =>
        !params.data || isPlaceholderRow(params.data) ? (
          "—"
        ) : (
          <p>
            {params.data.inventory} {tCampaigns("campaignsList.inventoryUnits")}
          </p>
        ),
    },
    {
      colId: "budget",
      headerName: tCampaigns("campaignsList.columns.budget"),
      sortable: true,
      minWidth: 120,
      width: 140,
      cellStyle: { textAlign: "right" },
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) =>
        !params.data || isPlaceholderRow(params.data) ? (
          "—"
        ) : (
          <p>{params.data.budget}</p>
        ),
    },
    {
      colId: "totalCost",
      headerName: tCampaigns("campaignsList.columns.totalCost"),
      sortable: true,
      minWidth: 120,
      width: 140,
      cellStyle: { textAlign: "right" },
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) =>
        !params.data || isPlaceholderRow(params.data) ? (
          "—"
        ) : (
          <p>{params.data.totalCost}</p>
        ),
    },
    {
      colId: "actions",
      headerName: tCampaigns("campaignsList.columns.actions"),
      sortable: false,
      minWidth: 100,
      width: 100,
      suppressHeaderMenuButton: true,
      headerComponent: CampaignTableHeaderWithDrawer,
      cellRenderer: (params: {
        data?: CampaignDisplay | CampaignRowPlaceholder;
      }) => {
        if (!params.data || isPlaceholderRow(params.data)) return "—";
        const row = params.data as CampaignDisplay;
        return (
          <div
            id={`campaign-actions-${row.id}`}
            className="flex justify-center items-center"
          >
            <Dropdown name={`campaign-actions-${row.id}`}>
              <DropdownTrigger asChild>
                <Button
                  id={`campaign-actions-menu-btn-${row.id}`}
                  variant="ghost"
                  size="iconMd"
                  className="focus:bg-mw-neutral-100"
                >
                  <MoreHorizontal className="size-6 text-black" />
                </Button>
              </DropdownTrigger>
              <CampaignActionsDropdownContent
                campaignId={row.id}
                campaignData={{
                  status: row.status,
                  createdByUsername: row.userName,
                }}
                handlers={{ onRefresh }}
              />
            </Dropdown>
          </div>
        );
      },
    },
  ];
}

export function CampaignsListView({
  data,
  isLoading,
  isFetching,
  paginationInfo,
  onPageChange,
  onPageSizeChange,
  onRefresh,
}: CampaignsListViewProps) {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const dispatch = useDispatch();
  const uiState = useSelector((state: RootState) => selectCampaignsUI(state));
  const profile = useSelector((state: RootState) => state.profile?.profile);
  const activeCompanyId =
    profile?.activeCompanyId || profile?.current_company?.id || "";

  const { columnVisibility, hiddenColumnIds, handleColumnVisibilityChange } =
    useTableColumnVisibility(TABLE_STATE_STORAGE_KEY, uiState.viewType);

  const handleSortChange = useCallback(
    (sort: AgGridSortState[]) => {
      const mapped: ColumnSort[] = sort.map((s) => ({
        key: s.key,
        direction: s.direction,
      }));
      dispatch(setSortState(mapped));
    },
    [dispatch],
  );

  const handleSelectionChange = useCallback(
    (selectedIds: string[]) => {
      dispatch(setSelectedItems(selectedIds));
    },
    [dispatch],
  );

  const sortStateForGrid: AgGridSortState[] = useMemo(
    () =>
      uiState.sortState.map((s) => ({
        key: s.key,
        direction: s.direction,
      })),
    [uiState.sortState],
  );

  const fullRowData = useCampaignListRowData(data, paginationInfo);
  const columnDefs = useMemo(
    () =>
      buildCampaignListColumnDefs(
        onRefresh,
        tCampaigns,
        tCommon,
        activeCompanyId,
      ),
    [onRefresh, tCampaigns, tCommon, activeCompanyId],
  );

  return (
    <div
      id="campaigns-table-container"
      className="h-full overflow-hidden flex flex-col"
    >
      <div className="flex-1 min-h-0">
        <AgGridTable<CampaignDisplay | CampaignRowPlaceholder>
          rowData={fullRowData}
          columnDefs={columnDefs}
          getRowId={(row) => row.id}
          loading={isLoading || isFetching}
          emptyMessage={tCampaigns("campaignsList.emptyMessage")}
          height="100%"
          rowSelection="multiple"
          selectedRowIds={uiState.selectedItems}
          onSelectionChange={handleSelectionChange}
          sortState={sortStateForGrid}
          onSortChange={handleSortChange}
          hiddenColumns={hiddenColumnIds}
          serverSidePagination
          cacheBlockSize={10}
          serverSideConfig={{
            totalCount: paginationInfo.totalItems,
            currentPage: paginationInfo.currentPage,
            pageSize: paginationInfo.pageSize,
            onPageChange,
            onPageSizeChange,
            pageSizeSelector: [10, 25, 50, 100],
          }}
        />
      </div>

      <ColumnVisibilityDrawer
        id="campaigns-column-customization-drawer"
        isOpen={uiState.isColumnCustomizationOpen}
        onClose={() => dispatch(setColumnCustomizationOpen(false))}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={handleColumnVisibilityChange}
        availableColumns={AVAILABLE_COLUMN_KEYS.map(({ key, labelKey }) => ({
          key,
          label: tCampaigns(labelKey),
        }))}
      />
    </div>
  );
}
