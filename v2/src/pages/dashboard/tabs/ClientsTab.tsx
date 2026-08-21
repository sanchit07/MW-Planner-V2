import { AgGridTable } from "@components/ui/AgGridTable";
import type { AgGridSortState } from "@components/ui/AgGridTable/types";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import {
  useGetSalesPerformanceSummaryQuery,
  type SalesPerformanceSummaryItem,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatCurrency } from "@utils/campaign.utils";
import {
  calculateDateRangeForPeriod,
  type DateRange,
  type PeriodOption,
} from "@utils/dashboard.utils";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import React, { useState, useMemo, useCallback } from "react";

interface ClientsTabProps {
  selectedPeriod: PeriodOption;
  dateRange?: DateRange;
  currencyCode?: string;
}

const ClientsTab: React.FC<ClientsTabProps> = ({
  selectedPeriod,
  dateRange,
  currencyCode,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const [showBy, setShowBy] = useState<"advertiser" | "agency">("advertiser");
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
      id: `${showBy}-${index}-${item.name || item.agency || ""}`,
      topCampaignsString: item.topCampaigns
        ?.map(
          (campaign) =>
            `${campaign.campaignName}: ${formatCurrency(campaign.cost, currencyCode)}`,
        )
        .join("\n"),
    }));
  }, [data, showBy, currencyCode]);

  const totalItems = data?.data?.totalElements ?? 0;

  const totalRevenue = useMemo(() => {
    if (!data?.data?.content) return 0;
    return data.data.content.reduce(
      (sum, item) => sum + (item.revenue ?? 0),
      0,
    );
  }, [data]);

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

  const columns: ColDef<SalesPerformanceSummaryItem & { id: string }>[] =
    useMemo(
      () => [
        {
          colId: "name",
          headerName:
            showBy === "advertiser"
              ? tDashboard("tabs.clients.columns.advertiser")
              : tDashboard("tabs.clients.columns.agency"),
          field: "name",
          sortable: true,
        } as ColDef<SalesPerformanceSummaryItem & { id: string }>,
        {
          colId: "revenue",
          headerName: tDashboard("tabs.clients.columns.revenue"),
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
          colId: "countCampaigns",
          headerName: tDashboard("tabs.clients.columns.plans"),
          field: "countCampaigns",
          sortable: true,
          cellStyle: { textAlign: "right" },
        },
        {
          colId: "share",
          headerName: tDashboard("tabs.clients.columns.share"),
          field: "share",
          sortable: true,
          cellStyle: { textAlign: "right" },
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
          colId: "adPlays",
          headerName: tDashboard("tabs.clients.columns.adPlays"),
          field: "adPlays",
          sortable: true,
          cellStyle: { textAlign: "right" },
        },
        {
          colId: "sov",
          headerName: tDashboard("tabs.clients.columns.sov"),
          field: "sov",
          sortable: true,
          cellStyle: { textAlign: "right" },
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
          colId: "impressions",
          headerName: tDashboard("tabs.clients.columns.impressions"),
          field: "impressions",
          sortable: true,
          cellStyle: { textAlign: "right" },
        },
        {
          colId: "topCampaigns",
          headerName: tDashboard("tabs.clients.columns.topCampaigns"),
          field: "topCampaignsString",
          sortable: false,
          wrapText: false,
          minWidth: 350,
          autoHeight: true,
          cellRenderer: (
            params: ICellRendererParams<
              SalesPerformanceSummaryItem & {
                id: string;
                topCampaignsString?: string;
              }
            >,
          ) => {
            const value = params.value as string | undefined;
            if (!value) {
              return <span className="text-sm text-mw-black">-</span>;
            }
            return (
              <div className="text-sm text-mw-black whitespace-pre-line text-left">
                {value}
              </div>
            );
          },
        },
      ],
      [showBy, currencyCode, tDashboard],
    );

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between mb-4">
        <div className="text-sm font-medium text-mw-black">
          {tDashboard("tabs.clients.totalRevenue")}:{" "}
          {formatCurrency(totalRevenue, currencyCode)}
        </div>
        <Dropdown
          value={showBy}
          onChange={(value) => {
            setShowBy(value as "advertiser" | "agency");
            setCurrentPage(1);
            setSortState([]);
          }}
        >
          <DropdownTrigger className="min-w-[140px] justify-between">
            {showBy === "advertiser"
              ? tDashboard("tabs.clients.columns.advertiser")
              : tDashboard("tabs.clients.columns.agency")}
          </DropdownTrigger>
          <DropdownContent>
            <DropdownItem value="advertiser">
              {tDashboard("tabs.clients.columns.advertiser")}
            </DropdownItem>
            <DropdownItem value="agency">
              {tDashboard("tabs.clients.columns.agency")}
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

export default ClientsTab;
