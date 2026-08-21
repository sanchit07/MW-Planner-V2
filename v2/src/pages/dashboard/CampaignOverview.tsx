import BarChart from "@components/common/BarChart";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Loading } from "@components/ui/Spinner";
import {
  useGetCampaignOverviewByStatusQuery,
  type CampaignOverviewByStatus,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import {
  calculateDateRangeForPeriod,
  type PeriodOption,
  type DateRange,
} from "@utils/dashboard.utils";
import { Info } from "lucide-react";
import React, { useMemo } from "react";

interface CampaignOverviewProps {
  selectedPeriod?: PeriodOption;
  dateRange?: DateRange;
}

const CampaignOverview: React.FC<CampaignOverviewProps> = ({
  selectedPeriod = "last-7-days",
  dateRange,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  // Calculate date range for API
  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );

  // Fetch campaign overview data from API
  const {
    data: overviewData,
    isLoading,
    isFetching,
  } = useGetCampaignOverviewByStatusQuery(
    { startDate, endDate, companyId },
    {
      skip: !startDate || !endDate,
    },
  );

  // Transform API data to chart format
  const chartData = useMemo(() => {
    const labels = [
      tDashboard("campaignStatus.planned"),
      tDashboard("campaignStatus.reviewing"),
      tDashboard("campaignStatus.negotiating"),
      tDashboard("campaignStatus.approve"),
      tDashboard("campaignStatus.active"),
      tDashboard("campaignStatus.completed"),
    ];

    if (!overviewData?.data) {
      return {
        labels,
        data: [0, 0, 0, 0, 0, 0],
      };
    }

    const data: CampaignOverviewByStatus = overviewData.data;

    return {
      labels,
      data: [
        data.plannedCampaigns || 0,
        data.reviewingCampaigns || 0,
        data.negotiatingCampaigns || 0,
        data.approvedCampaigns || 0,
        data.activeCampaigns || 0,
        data.completedCampaigns || 0,
      ],
    };
  }, [overviewData, tDashboard]);

  return (
    <Card className="p-4">
      <CardHeader className="pb-4 border-b border-mw-neutral-100">
        <div className="flex items-center justify-between">
          <CardTitle
            className="text-base font-medium leading-5"
            id="campaign-overview-heading"
          >
            {tDashboard("campaignOverview.title")}
          </CardTitle>
        </div>
        <p className="text-xs text-mw-neutral-500 mt-1 flex items-center gap-1">
          <Info className="size-3.5 shrink-0" />
          {tDashboard("activationNote")}
        </p>
      </CardHeader>
      <CardContent className="relative pt-4">
        {isFetching && !isLoading && (
          <Loading
            overlay
            size="md"
            variant="primary"
            text={tDashboard("loading")}
          />
        )}
        {isLoading ? (
          <div className="flex items-center justify-center h-[300px]">
            <div className="text-mw-neutral-500">
              {tDashboard("campaignOverview.loadingChartData")}
            </div>
          </div>
        ) : (
          <BarChart
            labels={chartData.labels}
            datasets={[
              {
                label: tDashboard("campaignOverview.campaignsByStatus"),
                data: chartData.data,
              },
            ]}
            height={300}
            yAxisMin={0}
            yAxisMax={
              chartData.data.length > 0 ? Math.max(...chartData.data, 10) : 10
            }
            yAxisStep={
              chartData.data.length > 0
                ? Math.max(1, Math.ceil(Math.max(...chartData.data, 10) / 10))
                : 1
            }
          />
        )}
      </CardContent>
    </Card>
  );
};

export default React.memo(CampaignOverview);
