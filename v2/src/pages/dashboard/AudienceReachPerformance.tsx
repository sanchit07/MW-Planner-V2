import MixedChart from "@components/common/MixedChart";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Loading } from "@components/ui/Spinner";
import { CAMPAIGN_STATUS_OPTIONS } from "@constants/campaign.constants";
import { useGetPerformanceSummaryReachQuery } from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import {
  bucketDayWiseChartData,
  calculateDateRangeForPeriod,
  formatCompactNumber,
  type DateRange,
  type DayWiseChartData,
  type PeriodOption,
} from "@utils/dashboard.utils";
import { clsx } from "clsx";
import { Info } from "lucide-react";
import React, { useMemo, useState } from "react";

// Types
export interface AudienceReachData {
  labels: string[];
  reach: number[];
  impressions: number[];
}

export interface AudienceReachPerformanceProps {
  /** Day-wise API data: { "2026-02-20": { impressions: 980, reach: 500 }, ... }. When provided, data is bucketed by period/date range. */
  dayWiseData?: DayWiseChartData;
  /** Selected period (required when using dayWiseData) */
  selectedPeriod?: PeriodOption;
  /** Date range (used when selectedPeriod is "date-range") */
  dateRange?: DateRange;
  /** Additional CSS class */
  className?: string;
}

// Default sample data matching the design (December data)
const defaultData: DayWiseChartData = {
  "2026-03-01": { reach: 240000, impressions: 250000 },
  "2026-03-02": { reach: 280000, impressions: 290000 },
  "2026-03-03": { reach: 150000, impressions: 170000 },
  "2026-03-04": { reach: 30000, impressions: 50000 },
  "2026-03-05": { reach: 79000, impressions: 69000 },
  "2026-03-06": { reach: 45000, impressions: 45000 },
  "2026-03-07": { reach: 120000, impressions: 120000 },
  "2026-03-08": { reach: 80000, impressions: 80000 },
  "2026-03-09": { reach: 60000, impressions: 60000 },
  "2026-03-10": { reach: 55000, impressions: 55000 },
  "2026-03-11": { reach: 52000, impressions: 52000 },
  "2026-03-12": { reach: 58000, impressions: 58000 },
  "2026-03-13": { reach: 90000, impressions: 90000 },
  "2026-03-14": { reach: 100000, impressions: 100000 },
  "2026-03-15": { reach: 75000, impressions: 75000 },
  "2026-03-16": { reach: 95000, impressions: 95000 },
  "2026-03-17": { reach: 40000, impressions: 40000 },
  "2026-03-18": { reach: 110000, impressions: 110000 },
  "2026-03-19": { reach: 140000, impressions: 140000 },
  "2026-03-20": { reach: 160000, impressions: 160000 },
  "2026-03-21": { reach: 180000, impressions: 180000 },
  "2026-03-22": { reach: 150000, impressions: 150000 },
};
// Chart colors
const CHART_COLORS = {
  reach: "#d86f6f", // Red line
  impressions: "#6898c9", // Light blue bars
};

/** Convert bucketed result to AudienceReachData (reach/impressions arrays, 0 for missing). */
function bucketedToAudienceReachData(bucketed: {
  labels: string[];
  [key: string]: string[] | number[] | undefined;
}): AudienceReachData {
  const len = bucketed.labels.length;
  return {
    labels: bucketed.labels,
    reach: Array.isArray(bucketed.reach) ? bucketed.reach : Array(len).fill(0),
    impressions: Array.isArray(bucketed.impressions)
      ? bucketed.impressions
      : Array(len).fill(0),
  };
}

const AudienceReachPerformance: React.FC<AudienceReachPerformanceProps> = ({
  selectedPeriod = "last-7-days",
  dateRange,
  className,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [selectedStatus, setSelectedStatus] = useState<string>("all");

  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );

  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  // Map selectedStatus to API status filter - pass uppercase value to backend
  const apiStatusFilter = useMemo(() => {
    if (selectedStatus === "all") return undefined;
    const statusOption = CAMPAIGN_STATUS_OPTIONS.find(
      (opt) =>
        opt.id === selectedStatus || opt.value === selectedStatus.toUpperCase(),
    );
    return statusOption?.value;
  }, [selectedStatus]);

  const { data: reachResponse, isFetching } =
    useGetPerformanceSummaryReachQuery(
      {
        startDate,
        endDate,
        companyId,
        ...(apiStatusFilter && { statuses: apiStatusFilter }),
      },
      { skip: !startDate || !endDate },
    );

  const dayWiseData = reachResponse?.data?.dateWiseSchedulePerDateRate;

  const data = useMemo((): AudienceReachData => {
    const source =
      dayWiseData && Object.keys(dayWiseData).length > 0
        ? dayWiseData
        : defaultData;
    const bucketed = bucketDayWiseChartData({
      dayWiseData: source,
      period: selectedPeriod,
      startDate,
      endDate,
      t: tCommon,
    });
    return bucketedToAudienceReachData(bucketed);
  }, [dayWiseData, selectedPeriod, startDate, endDate, tCommon]);

  // Prepare chart datasets
  const chartDatasets = useMemo(() => {
    return [
      {
        label: tDashboard("audienceReach.impressions"),
        data: data.impressions,
        type: "bar" as const,
        backgroundColor: CHART_COLORS.impressions,
        borderColor: CHART_COLORS.impressions,
        borderRadius: 4,
        maxBarThickness: 120,
        barPercentage: 0.6,
        yAxisID: "y",
        order: 2,
        legendConfig: {
          usePointStyle: false,
          pointStyle: "rect" as const,
        },
        tooltipConfig: {
          usePointStyle: true,
          pointStyle: "rect" as const,
        },
      },
      {
        label: tDashboard("audienceReach.reach"),
        data: data.reach,
        type: "line" as const,
        borderColor: CHART_COLORS.reach,
        backgroundColor: CHART_COLORS.reach,
        borderWidth: 2,
        pointRadius: 4,
        pointHoverRadius: 6,
        tension: 0.1,
        pointStyle: "line" as const,
        yAxisID: "y",
        order: 1,
        // Line dataset: line point style in tooltip
        legendConfig: {
          usePointStyle: false,
          pointStyle: "line" as const,
        },
        tooltipConfig: {
          usePointStyle: true,
          pointStyle: "line" as const,
        },
      },
    ];
  }, [data, tDashboard]);

  return (
    <Card className={clsx("p-4", className)}>
      {/* Header */}
      <CardHeader className="pb-4 border-b border-container-border">
        <div className="flex items-center justify-between">
          <CardTitle
            id="audience-reach-title"
            className="text-base font-medium text-mw-neutral-800"
          >
            {tDashboard("audienceReach.title")}
          </CardTitle>
          <div className="flex items-center gap-2">
            <span className="text-sm text-mw-neutral-700">
              {tDashboard("audienceReach.statusLabel")}
            </span>
            <Dropdown value={selectedStatus} onChange={setSelectedStatus}>
              <DropdownTrigger className="min-w-[140px] justify-between">
                {selectedStatus === "all"
                  ? tDashboard("audienceReach.allStatuses")
                  : tCampaigns(
                      `campaignsList.status.${selectedStatus.toUpperCase()}`,
                    )}
              </DropdownTrigger>
              <DropdownContent className="overflow-y-auto scrollbar-thin">
                <DropdownItem value="all">
                  {tDashboard("audienceReach.allStatuses")}
                </DropdownItem>
                {CAMPAIGN_STATUS_OPTIONS.map((option) => (
                  <DropdownItem key={option.id} value={option.id}>
                    {tCampaigns(`campaignsList.status.${option.value}`)}
                  </DropdownItem>
                ))}
              </DropdownContent>
            </Dropdown>
          </div>
        </div>
        <p className="text-xs text-mw-neutral-500 mt-1 flex items-center gap-1">
          <Info className="size-3.5 shrink-0" />
          {tDashboard("audienceReach.estimatesNote")}
        </p>
      </CardHeader>

      <CardContent className="relative pt-4">
        {isFetching && (
          <Loading
            overlay
            size="md"
            variant="primary"
            text={tDashboard("loading")}
          />
        )}
        <MixedChart
          labels={data.labels}
          datasets={chartDatasets}
          height={350}
          showLegend={true}
          legend={{
            usePointStyle: true,
          }}
          legendPosition="bottom"
          formatYAxisValue={formatCompactNumber}
          formatTooltipValue={(value, label) =>
            `${label}: ${formatCompactNumber(value)}`
          }
          dataLabels={{ show: false }}
        />
      </CardContent>
    </Card>
  );
};

export default React.memo(AudienceReachPerformance);
