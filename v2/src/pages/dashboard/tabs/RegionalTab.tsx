import { AgGridTable } from "@components/ui/AgGridTable";
import type { AgGridSortState } from "@components/ui/AgGridTable/types";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Progress } from "@components/ui/Progressbar";
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
import React, { useState, useMemo, useCallback } from "react";

interface RegionalTabProps {
  selectedPeriod: PeriodOption;
  dateRange?: DateRange;
  currencyCode?: string;
}

const RegionalTab: React.FC<RegionalTabProps> = ({
  selectedPeriod,
  dateRange,
  currencyCode,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const [showBy, setShowBy] = useState<"country" | "city">("country");
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
      showBy,
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
      id: `${showBy}-${index}-${item.country || item.city || ""}`,
    }));
  }, [data, showBy]);

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

  const columns: ColDef<SalesPerformanceSummaryItem & { id: string }>[] =
    useMemo(() => {
      const baseColumns: ColDef<
        SalesPerformanceSummaryItem & { id: string }
      >[] = [];

      if (showBy === "city") {
        baseColumns.push({
          colId: "city",
          headerName: tDashboard("tabs.regional.columns.city"),
          field: "city" as keyof (SalesPerformanceSummaryItem & { id: string }),
          sortable: true,
        });
      }

      baseColumns.push({
        colId: "country",
        headerName: tDashboard("tabs.regional.columns.country"),
        field: "country" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
      });

      baseColumns.push({
        colId: "inventories",
        headerName: tDashboard("tabs.regional.columns.inventories"),
        field: "inventories" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
      });

      baseColumns.push({
        colId: "utilization",
        headerName: tDashboard("tabs.regional.columns.utilization"),
        field: "utilization" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
        minWidth: 200,
        flex: 1,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => {
          const percentage = Number(params.value);
          return (
            <div className="flex items-center gap-2 w-full">
              <Progress
                value={percentage}
                showLabel
                label={`${percentage.toFixed(2)}%`}
                className="w-full min-w-[180px]"
                variant="primary"
                size="md"
                showPercentage={false}
                showInfo={false}
              />
            </div>
          );
        },
      });

      baseColumns.push({
        colId: "conversion",
        headerName: tDashboard("tabs.regional.columns.conversion"),
        field: "conversion" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => {
          const percentage = Number(params.value);
          return (
            <span
              className={`text-sm font-medium ${getPercentageColorClass(percentage)}`}
            >
              {percentage.toFixed(2)}%
            </span>
          );
        },
      });

      baseColumns.push({
        colId: "countCampaigns",
        headerName: tDashboard("tabs.regional.columns.plans"),
        field: "countCampaigns" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => (
          <div className="flex items-center gap-1">
            <span className="text-sm text-mw-black">{params.value}</span>
          </div>
        ),
      });

      baseColumns.push({
        colId: "cost",
        headerName: tDashboard("tabs.regional.columns.cost"),
        field: "cost" as keyof (SalesPerformanceSummaryItem & { id: string }),
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
      });

      baseColumns.push({
        colId: "revenue",
        headerName: tDashboard("tabs.regional.columns.revenue"),
        field: "revenue" as keyof (SalesPerformanceSummaryItem & {
          id: string;
        }),
        sortable: true,
        cellRenderer: (
          params: ICellRendererParams<
            SalesPerformanceSummaryItem & { id: string }
          >,
        ) => (
          <div className="flex items-center gap-1">
            <span className="text-sm text-mw-black">
              {formatCurrency(params.value ?? 0, currencyCode)}
            </span>
          </div>
        ),
      });

      return baseColumns;
    }, [showBy, currencyCode, tDashboard]);

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div className="text-sm font-medium text-mw-black">
          {totalItems}{" "}
          {showBy === "country"
            ? tDashboard("tabs.regional.countries")
            : tDashboard("tabs.regional.cities")}
        </div>
        <Dropdown
          value={showBy}
          onChange={(value) => {
            setShowBy(value as "country" | "city");
            setCurrentPage(1);
            setSortState([]);
          }}
        >
          <DropdownTrigger className="min-w-[140px] justify-between">
            {showBy === "country"
              ? tDashboard("tabs.regional.columns.country")
              : tDashboard("tabs.regional.columns.city")}
          </DropdownTrigger>
          <DropdownContent>
            <DropdownItem value="country">
              {tDashboard("tabs.regional.columns.country")}
            </DropdownItem>
            <DropdownItem value="city">
              {tDashboard("tabs.regional.columns.city")}
            </DropdownItem>
          </DropdownContent>
        </Dropdown>
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

export default RegionalTab;
