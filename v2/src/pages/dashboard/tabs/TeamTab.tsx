import { AgGridTable } from "@components/ui/AgGridTable";
import type { AgGridSortState } from "@components/ui/AgGridTable/types";
import {
  useGetSalesPerformanceSummaryQuery,
  type SalesPerformanceSummaryItem,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import {
  calculateDateRangeForPeriod,
  getPercentageColorClass,
  type DateRange,
  type PeriodOption,
} from "@utils/dashboard.utils";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import React, { useMemo, useState, useCallback } from "react";

interface TeamTabProps {
  selectedPeriod: PeriodOption;
  dateRange?: DateRange;
  currencyCode?: string;
}

const TeamTab: React.FC<TeamTabProps> = ({
  selectedPeriod,
  dateRange,
  currencyCode,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [sortState, setSortState] = useState<AgGridSortState[]>([]);

  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );
  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  const { data, isLoading, isFetching } = useGetSalesPerformanceSummaryQuery(
    {
      startDate,
      endDate,
      showBy: "team",
      page: currentPage - 1,
      size: pageSize,
      sortBy: sortState.length > 0 ? sortState[0].key : undefined,
      sortDir: sortState.length > 0 ? sortState[0].direction : undefined,
      companyId,
    },
    { skip: !startDate || !endDate },
  );

  const tableData = useMemo(() => {
    if (!data?.data?.content) return [];
    return data.data.content.map((item, index) => ({
      ...item,
      id: `team-${index}-${item.team || ""}`,
      rank: index + 1 + (currentPage - 1) * pageSize,
    }));
  }, [data, currentPage, pageSize]);

  const totalItems = data?.data?.totalElements ?? 0;

  const handlePageChange = useCallback((page: number) => {
    setCurrentPage(page);
  }, []);

  const handlePageSizeChange = useCallback((size: number) => {
    setPageSize(size);
    setCurrentPage(1);
  }, []);

  const handleSortChange = useCallback((sort: AgGridSortState[]) => {
    setSortState(sort);
    setCurrentPage(1);
  }, []);

  const formatPercentage = (value: number): string => {
    return `${Number(value).toFixed(2)}%`;
  };

  const columns: ColDef<
    SalesPerformanceSummaryItem & { id: string; rank: number }
  >[] = useMemo(
    () => [
      {
        colId: "rank",
        headerName: tDashboard("tabs.team.columns.rank"),
        field: "rank",
        sortable: true,
        cellStyle: { textAlign: "right" },
      },
      {
        colId: "name",
        headerName: tDashboard("tabs.team.columns.name"),
        field: "name",
        sortable: true,
        flex: 1,
      },
      {
        colId: "region",
        headerName: tDashboard("tabs.team.columns.region"),
        field: "region",
        sortable: true,
      },
      {
        colId: "share",
        headerName: tDashboard("tabs.team.columns.share"),
        field: "share",
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => (
          <span className="text-sm text-mw-black">
            {formatPercentage(Number(params.value ?? 0))}
          </span>
        ),
      },
      {
        colId: "countCampaigns",
        headerName: tDashboard("tabs.team.columns.plans"),
        field: "countCampaigns",
        sortable: true,
      },
      {
        colId: "revenue",
        headerName: tDashboard("tabs.team.columns.revenue"),
        field: "revenue",
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => (
          <span className="text-sm text-mw-black">
            {formatCurrency(params.value ?? 0, currencyCode)}
          </span>
        ),
      },
      {
        colId: "conversion",
        headerName: tDashboard("tabs.team.columns.conversion"),
        field: "conversion",
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => {
          const percentage = Number(params.value ?? 0);
          return (
            <span
              className={`text-sm font-medium ${getPercentageColorClass(percentage)}`}
            >
              {percentage.toFixed(2)}%
            </span>
          );
        },
      },
    ],
    [currencyCode, tDashboard],
  );

  return (
    <div className="flex flex-col">
      <div className="text-sm font-medium text-mw-black mb-4">
        {tDashboard("tabs.team.title")}
      </div>
      <AgGridTable
        rowData={tableData}
        columnDefs={columns}
        getRowId={(row) => row.id}
        height="100%"
        loading={isLoading || isFetching}
        serverSidePagination={true}
        serverSideConfig={{
          totalCount: totalItems,
          currentPage,
          pageSize,
          onPageChange: handlePageChange,
          onPageSizeChange: handlePageSizeChange,
        }}
        sortState={sortState}
        onSortChange={handleSortChange}
        emptyMessage={tDashboard("tabs.noDataAvailable")}
        domLayout="autoHeight"
      />
    </div>
  );
};

export default TeamTab;
