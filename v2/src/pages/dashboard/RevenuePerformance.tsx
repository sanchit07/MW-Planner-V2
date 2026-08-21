import BarChart from "@components/common/BarChart";
// import DoughnutChart from "@components/common/DoughnutChart";
import LineChart from "@components/common/LineChart";
import CampaignTypeDropdown from "@components/dashboard/CampaignTypeDropdown";
// import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Loading } from "@components/ui/Spinner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import {
  useGetPerformanceSummaryCostQuery,
  useGetCampaignOverviewByStatusQuery,
  type CampaignOverviewByStatus,
} from "@services/dashboard/dashboardSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import {
  bucketDayWiseChartData,
  calculateDateRangeForPeriod,
  formatSummaryCurrency,
  formatChartTooltipDate,
  formatChartTooltipValue,
  formatChartYAxisValue,
  getChartCurrentDateIndex,
  type DateRange,
  type PeriodOption,
} from "@utils/dashboard.utils";
import { HandCoins, Handshake, Target } from "lucide-react";
import React, { useState, useMemo, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { CampaignType } from "src/types/campaign";

import { type WidgetVisibility } from "./CustomizeLayoutDrawer";
import SummaryCard from "./SummaryCard";
import ClientsTab from "./tabs/ClientsTab";
import RegionalTab from "./tabs/RegionalTab";
import TeamTab from "./tabs/TeamTab";

/** Map bucketed cost data to Revenue vs Cost chart (revenue, cost). Uses revenue key if present, else cost for both. */
function bucketedToRevenueCostChartData(bucketed: {
  labels: string[];
  [key: string]: string[] | number[] | undefined;
}): {
  labels: string[];
  revenueData: number[];
  costData: number[];
} {
  const len = bucketed.labels.length;
  const revenueData = Array.isArray(bucketed.revenue)
    ? bucketed.revenue
    : Array.isArray(bucketed.cost)
      ? bucketed.cost
      : Array(len).fill(0);
  const costData = Array.isArray(bucketed.cost)
    ? bucketed.cost
    : Array(len).fill(0);
  return {
    labels: bucketed.labels,
    revenueData,
    costData,
  };
}

interface RevenuePerformanceProps {
  widgetVisibility: WidgetVisibility;
  selectedPeriod?: PeriodOption;
  dateRange?: DateRange;
}

const RevenuePerformance: React.FC<RevenuePerformanceProps> = ({
  widgetVisibility,
  selectedPeriod = "last-30-days",
  dateRange,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const { t: tCommon } = useTranslate(["common"]);
  const [selectedCampaignType, setSelectedCampaignType] =
    useState<CampaignType>("all-campaign");

  const navigate = useNavigate();

  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );

  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  const { data: costResponse, isFetching: isCostFetching } =
    useGetPerformanceSummaryCostQuery(
      { startDate, endDate, companyId },
      { skip: !startDate || !endDate },
    );

  const dayWiseCostData = costResponse?.data?.dateWiseSchedulePerDateRate;

  // Fetch campaign overview data for sales pipeline funnel
  const {
    data: overviewData,
    isLoading: isOverviewLoading,
    isFetching: isOverviewFetching,
  } = useGetCampaignOverviewByStatusQuery(
    { startDate, endDate, companyId },
    {
      skip: !startDate || !endDate,
    },
  );

  const revenueCostData = useMemo(() => {
    const source =
      dayWiseCostData && Object.keys(dayWiseCostData).length > 0
        ? dayWiseCostData
        : {};
    const bucketed = bucketDayWiseChartData({
      dayWiseData: source,
      period: selectedPeriod,
      startDate,
      endDate,
      t: tCommon,
    });
    return bucketedToRevenueCostChartData(bucketed);
  }, [dayWiseCostData, selectedPeriod, startDate, endDate, tCommon]);

  const currentDateIndex = useMemo(
    () =>
      getChartCurrentDateIndex(selectedPeriod, revenueCostData.labels.length),
    [selectedPeriod, revenueCostData.labels.length],
  );

  const formatTooltipValue = useCallback(
    (value: number, label: string) => formatChartTooltipValue(value, label, 0),
    [],
  );
  const formatTooltipDate = useCallback(formatChartTooltipDate, []);
  const formatYAxisValue = useCallback(
    (value: number) => formatChartYAxisValue(value, 0),
    [],
  );

  // Revenue Distribution data
  // const revenueDistributionData = useMemo(() => {
  //   return [
  //     {
  //       label: "Classic",
  //       value: 85,
  //       color: "--color-mw-primary-500", // Dark blue
  //       status: "High Demand",
  //       percentage: 85,
  //     },
  //     {
  //       label: "Digital",
  //       value: 75,
  //       color: "--color-mw-primary-400", // Medium blue
  //       status: "Medium",
  //       percentage: 75,
  //     },
  //     {
  //       label: "Transit",
  //       value: 55,
  //       color: "--color-mw-secondary-200", // Light blue
  //       status: "Available",
  //       percentage: 55,
  //     },
  //     {
  //       label: "Retail",
  //       value: 55,
  //       color: "--color-mw-secondary-100", // Very light blue
  //       status: "Available",
  //       percentage: 55,
  //     },
  //   ];
  // }, []);
  // const getColorValue = useCallback((color: string): string => {
  //   if (color.startsWith("--")) {
  //     if (typeof window !== "undefined") {
  //       const value = getComputedStyle(document.documentElement)
  //         .getPropertyValue(color)
  //         .trim();
  //       if (value) return value;
  //     }
  //     // Fallback colors if CSS variable not found
  //     const fallbackColors: Record<string, string> = {
  //       "--color-mw-primary-500": "#103860",
  //       "--color-mw-primary-400": "#2176cc",
  //       "--color-mw-secondary-200": "#94cfef",
  //       "--color-mw-secondary-100": "#cee9f8",
  //     };
  //     return fallbackColors[color] || "#2176cc";
  //   }
  //   return color;
  // }, []);

  // Transform API data to chart format for sales pipeline funnel
  const salesPipelineChartData = useMemo(() => {
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

  // Calculate dynamic y-axis max and step for sales pipeline funnel
  const salesPipelineYAxisMax = useMemo(() => {
    if (salesPipelineChartData.data.length === 0) return 10;
    const maxValue = Math.max(...salesPipelineChartData.data);
    return maxValue > 0 ? Math.ceil(maxValue * 1.1) : 10;
  }, [salesPipelineChartData.data]);

  const salesPipelineYAxisStep = useMemo(() => {
    if (salesPipelineYAxisMax <= 0) return 1;
    return Math.max(1, Math.ceil(salesPipelineYAxisMax / 10));
  }, [salesPipelineYAxisMax]);

  // Get status badge variant
  // const getStatusBadgeVariant = useCallback((status: string) => {
  //   const statusLower = status.toLowerCase();
  //   if (statusLower.includes("high") || statusLower.includes("demand")) {
  //     return "bg-mw-error-50! text-mw-error-500!" as const;
  //   }
  //   if (statusLower.includes("medium")) {
  //     return "bg-mw-warning-50! text-mw-warning-500!" as const;
  //   }
  //   return "bg-mw-success-50! text-mw-success-500!" as const;
  // }, []);

  const summary = costResponse?.data;
  const totalRevenue = summary?.totalRevenue ?? 0;
  const lastPeriodTotalRevenue = summary?.lastPeriodTotalRevenue ?? 0;
  const averageRevenuePerUnit = summary?.averageRevenuePerUnit ?? 0;
  const lastPeriodAverageRevenuePerUnit =
    summary?.lastPeriodAverageRevenuePerUnit ?? 0;
  const conversionRate = summary?.conversionRate ?? 0;
  const lastPeriodConversionRate = summary?.lastPeriodConversionRate ?? 0;

  const revenueTrend =
    lastPeriodTotalRevenue !== 0
      ? ((totalRevenue - lastPeriodTotalRevenue) / lastPeriodTotalRevenue) * 100
      : totalRevenue !== 0
        ? 100
        : 0;
  const avgPerUnitTrend =
    lastPeriodAverageRevenuePerUnit !== 0
      ? ((averageRevenuePerUnit - lastPeriodAverageRevenuePerUnit) /
          lastPeriodAverageRevenuePerUnit) *
        100
      : averageRevenuePerUnit !== 0
        ? 100
        : 0;
  const conversionTrend =
    lastPeriodConversionRate !== 0
      ? ((conversionRate - lastPeriodConversionRate) /
          lastPeriodConversionRate) *
        100
      : conversionRate !== 0
        ? 100
        : 0;

  return (
    <Card className="p-4">
      <CardHeader className="pb-4 border-b border-mw-neutral-100">
        <div className="flex items-center justify-between">
          <CardTitle
            className="text-base font-medium leading-5"
            id="revenue-performance-heading"
          >
            {tDashboard("revenuePerformance.title")}
          </CardTitle>
          <div className="flex items-center gap-2">
            <CampaignTypeDropdown
              value={selectedCampaignType}
              onChange={setSelectedCampaignType}
            />
          </div>
        </div>
      </CardHeader>
      <CardContent className="pt-4 px-0">
        {widgetVisibility["sales-overview"] !== false && (
          <div className="relative grid grid-cols-1 md:grid-cols-3 gap-4">
            {isCostFetching && (
              <Loading
                overlay
                size="md"
                variant="primary"
                text={tDashboard("loading")}
              />
            )}
            <SummaryCard
              icon={<HandCoins className="w-7 h-7 text-mw-brown-500" />}
              iconBgColor="bg-mw-brown-50"
              title={tDashboard("revenuePerformance.revenueGenerated")}
              value={formatSummaryCurrency(totalRevenue, summary?.currencyCode)}
              subtitle={tDashboard("revenuePerformance.vsLastPeriod", {
                amount: formatSummaryCurrency(
                  lastPeriodTotalRevenue,
                  summary?.currencyCode,
                ),
              })}
              trend={
                lastPeriodTotalRevenue === 0 && totalRevenue === 0
                  ? undefined
                  : {
                      value:
                        lastPeriodTotalRevenue !== 0
                          ? Math.abs(Math.round(revenueTrend * 10) / 10)
                          : 100,
                      isPositive:
                        lastPeriodTotalRevenue !== 0 ? revenueTrend >= 0 : true,
                    }
              }
            />
            <SummaryCard
              icon={<Target className="w-7 h-7 text-mw-pacific-blue-500" />}
              iconBgColor="bg-mw-pacific-blue-50"
              title={tDashboard("revenuePerformance.averagePerUnit")}
              value={formatSummaryCurrency(
                averageRevenuePerUnit,
                summary?.currencyCode,
              )}
              subtitle={tDashboard("revenuePerformance.vsLastPeriod", {
                amount: formatSummaryCurrency(
                  lastPeriodAverageRevenuePerUnit,
                  summary?.currencyCode,
                ),
              })}
              trend={
                lastPeriodAverageRevenuePerUnit === 0 &&
                averageRevenuePerUnit === 0
                  ? undefined
                  : {
                      value:
                        lastPeriodAverageRevenuePerUnit !== 0
                          ? Math.abs(Math.round(avgPerUnitTrend * 10) / 10)
                          : 100,
                      isPositive:
                        lastPeriodAverageRevenuePerUnit !== 0
                          ? avgPerUnitTrend >= 0
                          : true,
                    }
              }
            />
            <SummaryCard
              icon={<Handshake className="w-7 h-7 text-mw-success-500" />}
              iconBgColor="bg-mw-success-50"
              title={tDashboard("revenuePerformance.conversionRate")}
              value={`${conversionRate.toFixed(2)}%`}
              subtitle={tDashboard("revenuePerformance.vsLastPeriod", {
                amount: `${lastPeriodConversionRate.toFixed(2)}%`,
              })}
              trend={
                lastPeriodConversionRate === 0 && conversionRate === 0
                  ? undefined
                  : {
                      value:
                        lastPeriodConversionRate !== 0
                          ? Math.abs(Math.round(conversionTrend * 10) / 10)
                          : 100,
                      isPositive:
                        lastPeriodConversionRate !== 0
                          ? conversionTrend >= 0
                          : true,
                    }
              }
            />
          </div>
        )}
        {widgetVisibility["sales-performance-summary"] !== false && (
          <Card className="mt-6 p-4">
            <CardHeader className="pb-4 border-b border-mw-neutral-100">
              <CardTitle
                className="text-base font-medium leading-5"
                id="sales-performance-summary-heading"
              >
                {tDashboard("revenuePerformance.salesPerformanceSummary")}
              </CardTitle>
            </CardHeader>
            <CardContent className="px-0 pt-4 pb-0">
              <Tabs defaultValue="overview" id="sales-performance-tabs">
                <TabsList className="grid w-full grid-cols-4 mb-4">
                  <TabsTrigger value="overview">
                    {tDashboard("revenuePerformance.tabs.overview")}
                  </TabsTrigger>
                  <TabsTrigger value="regional">
                    {tDashboard("revenuePerformance.tabs.regional")}
                  </TabsTrigger>
                  <TabsTrigger value="clients">
                    {tDashboard("revenuePerformance.tabs.clients")}
                  </TabsTrigger>
                  <TabsTrigger value="team">
                    {tDashboard("revenuePerformance.tabs.team")}
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="overview" className="mt-4 mb-4">
                  <LineChart
                    title={tDashboard("revenuePerformance.revenueCostAnalysis")}
                    labels={revenueCostData.labels}
                    showLegend={true}
                    datasets={[
                      {
                        label: tDashboard("revenuePerformance.revenue"),
                        data: revenueCostData.revenueData,
                      },
                      {
                        label: tDashboard("revenuePerformance.cost"),
                        data: revenueCostData.costData,
                      },
                    ]}
                    currentDateIndex={currentDateIndex}
                    height={400}
                    formatYAxisValue={formatYAxisValue}
                    formatTooltipValue={formatTooltipValue}
                    formatTooltipDate={formatTooltipDate}
                    initialVisibleItems={
                      selectedPeriod === "last-30-days" ? 22 : undefined
                    }
                    spacingPerItem={
                      selectedPeriod === "last-30-days" ? 70 : undefined
                    }
                  />
                </TabsContent>
                <TabsContent value="regional" className="mt-4">
                  <RegionalTab
                    selectedPeriod={selectedPeriod}
                    dateRange={dateRange}
                    currencyCode={summary?.currencyCode}
                  />
                </TabsContent>
                <TabsContent value="clients" className="mt-4">
                  <ClientsTab
                    selectedPeriod={selectedPeriod}
                    dateRange={dateRange}
                    currencyCode={summary?.currencyCode}
                  />
                </TabsContent>
                <TabsContent value="team" className="mt-4">
                  <TeamTab
                    selectedPeriod={selectedPeriod}
                    dateRange={dateRange}
                    currencyCode={summary?.currencyCode}
                  />
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>
        )}
        {(widgetVisibility["sales-pipeline-funnel"] !== false ||
          widgetVisibility["revenue-distribution"] !== false) && (
          <div className="flex gap-4 mt-6 w-full">
            {widgetVisibility["sales-pipeline-funnel"] !== false && (
              <Card className="p-4 flex-1">
                <CardHeader className="pb-4 border-b border-mw-neutral-100">
                  <div className="flex items-center justify-between">
                    <CardTitle
                      className="text-base font-medium leading-5"
                      id="sales-funnel-heading"
                    >
                      {tDashboard("revenuePerformance.salesPipelineFunnel")}
                    </CardTitle>
                    <div className="flex items-center gap-2">
                      <Button
                        className="outline-mw-primary-500 text-mw-primary-500"
                        variant="outline"
                        size="sm"
                        onClick={() => navigate(`/campaigns`)}
                      >
                        {tDashboard("revenuePerformance.viewCampaign")}
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="relative pt-4">
                  {isOverviewFetching && !isOverviewLoading && (
                    <Loading
                      overlay
                      size="md"
                      variant="primary"
                      text={tDashboard("loading")}
                    />
                  )}
                  {isOverviewLoading ? (
                    <div className="flex items-center justify-center h-[300px]">
                      <div className="text-mw-neutral-500">
                        {tDashboard("revenuePerformance.loadingChartData")}
                      </div>
                    </div>
                  ) : (
                    <BarChart
                      labels={salesPipelineChartData.labels}
                      datasets={[
                        {
                          label: tDashboard("revenuePerformance.salesPipeline"),
                          data: salesPipelineChartData.data,
                        },
                      ]}
                      height={300}
                      yAxisMin={0}
                      yAxisMax={salesPipelineYAxisMax}
                      yAxisStep={salesPipelineYAxisStep}
                    />
                  )}
                </CardContent>
              </Card>
            )}
            {/* {widgetVisibility["revenue-distribution"] !== false && (
              <Card className="p-4 flex-[0.3]">
                <CardHeader className="pb-4 border-b border-mw-neutral-100">
                  <CardTitle className="text-base font-medium leading-5">
                    Revenue Distribution By Type
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4">
                  <div className="flex flex-col items-center gap-6">
                    <DoughnutChart
                      data={revenueDistributionData}
                      height={280}
                      width={250}
                      emphasizeLargest={true}
                      showDoughnutLabels={false}
                      showLegend={false}
                    />
                    <div className="w-full space-y-3">
                      {revenueDistributionData.map((item, index) => {
                        const colorValue = getColorValue(item.color);
                        return (
                          <div
                            key={index}
                            className="flex items-center justify-between gap-3 w-full"
                          >
                            <div className="flex items-center gap-2 flex-1 min-w-0">
                              <div
                                className="w-4 h-4 rounded-sm shrink-0"
                                style={{ backgroundColor: colorValue }}
                              />
                              <span className="text-sm text-mw-neutral-700 font-normal truncate">
                                {item.label}
                              </span>
                              <Badge
                                className={`${getStatusBadgeVariant(item.status)} shrink-0`}
                                size="sm"
                                variant="default"
                              >
                                {item.status}
                              </Badge>
                            </div>
                            <span className="text-sm text-mw-neutral-700 font-medium shrink-0">
                              {item.percentage}%
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </CardContent>
              </Card>
            )} */}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default React.memo(RevenuePerformance);
