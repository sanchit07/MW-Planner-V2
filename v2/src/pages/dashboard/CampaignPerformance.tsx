// import { Progress } from "@components/ui/Progressbar";
import { AgGridTable } from "@components/ui/AgGridTable";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { CAMPAIGN_STATUS_OPTIONS } from "@constants/campaign.constants";
// import { TablePagination } from "@components/ui/TablePagination";
import {
  useGetCampaignPerformanceQuery,
  type CampaignPerformanceItem,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { formatNumber } from "@utils/budget.utils";
import { formatCurrency } from "@utils/campaign.utils";
import {
  calculateDateRangeForPeriod,
  type PeriodOption,
  type DateRange,
} from "@utils/dashboard.utils";
import { formatDisplayDate } from "@utils/dateUtils";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import { Eye, TrendingUp } from "lucide-react";
import React, { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";

interface CampaignPerformanceProps {
  selectedPeriod?: PeriodOption;
  dateRange?: DateRange;
}

interface CampaignData {
  id: string;
  campaignName: string;
  startDate: string;
  endDate: string;
  impression: number;
  reach: number;
  sov: number;
  budget: number;
  budgetSpent: number;
  status: string;
  currency: string;
}

const CampaignPerformance: React.FC<CampaignPerformanceProps> = ({
  selectedPeriod = "last-7-days",
  dateRange,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const navigate = useNavigate();
  const [selectedStatus, setSelectedStatus] = useState<string>("all");
  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  // Calculate date range for API
  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );

  // Use all status options from constants
  const availableStatusOptions = CAMPAIGN_STATUS_OPTIONS;

  // Map selectedStatus to API status filter - pass uppercase value to backend
  const apiStatusFilter = useMemo(() => {
    if (selectedStatus === "all") return undefined;
    // Find the status option and pass its value (uppercase) to API
    const statusOption = CAMPAIGN_STATUS_OPTIONS.find(
      (opt) =>
        opt.id === selectedStatus || opt.value === selectedStatus.toUpperCase(),
    );
    return statusOption?.value;
  }, [selectedStatus]);

  // Fetch campaign performance data from API
  // Backend will return top 5 records based on the filter
  const {
    data: performanceData,
    isLoading,
    isFetching,
  } = useGetCampaignPerformanceQuery(
    {
      startDate,
      endDate,
      ...(apiStatusFilter && { status: apiStatusFilter }),
      companyId,
    },
    {
      skip: !startDate || !endDate,
    },
  );

  // Transform API data to component format
  // Backend returns top 5, so no client-side filtering needed
  const displayData: CampaignData[] = useMemo(() => {
    if (!performanceData?.data) return [];
    return performanceData.data.map((item: CampaignPerformanceItem) => ({
      id: item.id,
      campaignName: item.name,
      startDate: item.startDate,
      endDate: item.endDate,
      impression: item.estimatedImpression || 0,
      reach: item.estimatedReach || 0,
      sov: item.sov || 0,
      budget: item.budget || 0,
      budgetSpent: item.totalCost || 0,
      status: item.status,
      currency: item.currency || "MYR",
    }));
  }, [performanceData]);

  const columns: ColDef<CampaignData>[] = useMemo(
    () => [
      {
        colId: "campaignName",
        headerName: tDashboard("campaignPerformance.columns.campaign"),
        field: "campaignName",
        sortable: false,
      },
      {
        colId: "startDate",
        headerName: tDashboard("campaignPerformance.columns.flightDates"),
        field: "startDate",
        sortable: false,
        cellRenderer: (params: ICellRendererParams<CampaignData>) => (
          <div className="space-y-1">
            <p>
              {formatDisplayDate(params.data!.startDate, tCommon)} -{" "}
              {formatDisplayDate(params.data!.endDate, tCommon)}
            </p>
          </div>
        ),
      },
      {
        colId: "impression",
        headerName: tDashboard("campaignPerformance.columns.impression"),
        field: "impression",
        sortable: true,
        comparator: (valueA, valueB) => Number(valueA) - Number(valueB),
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return (
            <div className="flex items-center gap-2">
              <span>{formatNumber(Number(params.value), true)}</span>
              <TrendingUp className="w-3 h-3 text-mw-success-500" />
            </div>
          );
        },
      },
      {
        colId: "reach",
        headerName: tDashboard("campaignPerformance.columns.reach"),
        field: "reach",
        sortable: true,
        comparator: (valueA, valueB) => Number(valueA) - Number(valueB),
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return `${params.value ? formatNumber(Number(params.value), true) : "N/A"}`;
        },
      },
      {
        colId: "sov",
        headerName: tDashboard("tabs.clients.columns.sov"),
        field: "sov",
        sortable: true,
        comparator: (valueA, valueB) => Number(valueA) - Number(valueB),
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return (
            <span className="flex items-center gap-2">
              {params.value.toFixed(2)}%
            </span>
          );
        },
      },
      {
        colId: "budget",
        headerName: tDashboard("campaignPerformance.columns.budget"),
        field: "budget",
        sortable: true,
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return `${formatCurrency(Number(params.value), params.data!.currency || "MYR")}`;
        },
      },
      {
        colId: "budgetSpent",
        headerName: tDashboard("campaignPerformance.columns.totalCost"),
        field: "budgetSpent",
        sortable: true,
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return `${formatCurrency(Number(params.value), params.data!.currency || "MYR")}`;
        },
      },
      {
        colId: "status",
        headerName: tDashboard("campaignPerformance.columns.status"),
        field: "status",
        sortable: false,
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          // Find the status option to get the display label
          const statusOption = CAMPAIGN_STATUS_OPTIONS.find(
            (opt) => opt.value === params.data!.status.toUpperCase(),
          );
          const displayStatus = statusOption?.label || params.data!.status;
          const statusForBadge = displayStatus.toLowerCase();
          return (
            <>
              <StatusBadge status={statusForBadge}>
                {tCampaigns(
                  `campaignsList.status.${displayStatus.toUpperCase()}`,
                ) ||
                  displayStatus.charAt(0).toUpperCase() +
                    displayStatus.slice(1)}
              </StatusBadge>
            </>
          );
        },
      },
      {
        colId: "actions",
        headerName: tDashboard("campaignPerformance.columns.action"),
        sortable: false,
        cellRenderer: (params: ICellRendererParams<CampaignData>) => {
          return (
            <div
              id={`campaign-actions-${params.data!.id}`}
              className="flex justify-center items-center"
            >
              <Button
                variant="ghost"
                size="iconMd"
                onClick={() => navigate(`/campaigns/view/${params.data!.id}`)}
              >
                <Eye className="size-4" />{" "}
              </Button>
            </div>
          );
        },
      },
    ],
    [tDashboard, tCampaigns, tCommon, navigate],
  );

  return (
    <Card className="p-4">
      <CardHeader className="pb-4 border-b border-mw-neutral-100">
        <div className="flex items-center justify-between">
          <CardTitle
            className="text-base font-medium leading-5"
            id="campaigns-performance-heading"
          >
            {tDashboard("campaignPerformance.title")}
          </CardTitle>
          <div className="flex items-center gap-2">
            <span className="text-sm text-mw-neutral-700">
              {tDashboard("campaignPerformance.statusLabel")}
            </span>
            <Dropdown value={selectedStatus} onChange={setSelectedStatus}>
              <DropdownTrigger className="min-w-[140px] justify-between">
                {selectedStatus === "all"
                  ? tDashboard("campaignPerformance.allStatuses")
                  : tCampaigns(
                      `campaignsList.status.${selectedStatus.toUpperCase()}`,
                    ) || tDashboard("campaignPerformance.selectStatus")}
              </DropdownTrigger>
              <DropdownContent className="overflow-y-auto scrollbar-thin">
                <DropdownItem value="all">
                  {tDashboard("campaignPerformance.allStatuses")}
                </DropdownItem>
                {availableStatusOptions.map((option) => (
                  <DropdownItem key={option.id} value={option.id}>
                    {tCampaigns(`campaignsList.status.${option.value}`)}
                  </DropdownItem>
                ))}
              </DropdownContent>
            </Dropdown>
          </div>
        </div>
      </CardHeader>
      <CardContent className="relative mt-2 flex-1 overflow-hidden p-0!">
        {isFetching && !isLoading && (
          <Loading
            overlay
            size="md"
            variant="primary"
            text={tDashboard("loading")}
          />
        )}
        {isLoading ? (
          <div className="flex items-center justify-center">
            <div className="text-mw-neutral-500">
              {tDashboard("campaignPerformance.loadingData")}
            </div>
          </div>
        ) : (
          <AgGridTable
            rowData={displayData}
            columnDefs={columns}
            getRowId={(row) => row.id}
            height="100%"
            emptyMessage={tDashboard("campaignPerformance.noCampaigns")}
            pagination={false}
            domLayout="autoHeight"
          />
        )}
      </CardContent>
    </Card>
  );
};

export default React.memo(CampaignPerformance);
