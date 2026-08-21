import LineChart from "@components/common/LineChart";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Loading } from "@components/ui/Spinner";
import { useGetPerformanceSummaryCostQuery } from "@services/dashboard/dashboardSlice";
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
import { Wallet, BanknoteArrowDown, Banknote, Info } from "lucide-react";
import React, { useMemo, useCallback } from "react";

import SummaryCard from "./SummaryCard";

/** Map bucketed cost data to chart series (total, spent, remaining). Supports API keys: total/budget, spent/cost, remaining. */
function bucketedToBudgetChartData(bucketed: {
  labels: string[];
  [key: string]: string[] | number[] | undefined;
}): {
  labels: string[];
  totalData: number[];
  spentData: number[];
  remainingData: number[];
} {
  const len = bucketed.labels.length;
  const totalData = Array.isArray(bucketed.total)
    ? bucketed.total
    : Array.isArray(bucketed.budget)
      ? bucketed.budget
      : Array(len).fill(0);
  const spentData = Array.isArray(bucketed.spent)
    ? bucketed.spent
    : Array.isArray(bucketed.cost)
      ? bucketed.cost
      : Array(len).fill(0);
  const remainingData = Array.isArray(bucketed.remaining)
    ? bucketed.remaining
    : Array(len).fill(0);
  return {
    labels: bucketed.labels,
    totalData,
    spentData,
    remainingData,
  };
}

interface BudgetTrackerProps {
  selectedPeriod?: PeriodOption;
  dateRange?: DateRange;
  showOverview?: boolean;
  showPerformanceSummary?: boolean;
}

const BudgetTracker: React.FC<BudgetTrackerProps> = ({
  selectedPeriod = "last-30-days",
  dateRange,
  showOverview = true,
  showPerformanceSummary = true,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);
  const { t: tCommon } = useTranslate(["common"]);

  const { startDate, endDate } = useMemo(
    () => calculateDateRangeForPeriod(selectedPeriod, dateRange),
    [selectedPeriod, dateRange],
  );
  const user = useAppSelector((s) => s.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";

  const { data: costResponse, isFetching } = useGetPerformanceSummaryCostQuery(
    { startDate, endDate, companyId },
    { skip: !startDate || !endDate },
  );

  const dayWiseCostData = costResponse?.data?.dateWiseSchedulePerDateRate;

  const chartData = useMemo(() => {
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
    return bucketedToBudgetChartData(bucketed);
  }, [dayWiseCostData, selectedPeriod, startDate, endDate, tCommon]);

  const formatTooltipValue = useCallback(
    (value: number, label: string) => formatChartTooltipValue(value, label, 0),
    [],
  );
  const formatTooltipDate = useCallback(formatChartTooltipDate, []);
  const formatYAxisValue = useCallback(
    (value: number) => formatChartYAxisValue(value, 0),
    [],
  );

  const currentDateIndex = useMemo(
    () => getChartCurrentDateIndex(selectedPeriod, chartData.labels.length),
    [selectedPeriod, chartData.labels.length],
  );

  const summary = costResponse?.data;
  const totalRevenue = summary?.totalBudget ?? 0;
  const lastPeriodTotalBudget = summary?.lastPeriodTotalBudget ?? 0;
  const lastPeriodTotalCost = summary?.lastPeriodTotalCost ?? 0;
  const totalCost = summary?.totalCost ?? 0;
  const remainingBudget = summary?.remainingBudget ?? 0;
  const budgetTrend =
    lastPeriodTotalBudget !== 0
      ? ((totalRevenue - lastPeriodTotalBudget) / lastPeriodTotalBudget) * 100
      : 0;
  const costTrend =
    lastPeriodTotalCost !== 0
      ? ((totalCost - lastPeriodTotalCost) / lastPeriodTotalCost) * 100
      : 0;

  return (
    <Card className="p-4">
      <CardHeader className="pb-4 border-b border-mw-neutral-100">
        <div className="flex items-center justify-between">
          <CardTitle
            className="text-base font-medium leading-5"
            id="budget-heading"
          >
            {tDashboard("budgetTracker.title")}
          </CardTitle>
        </div>
        {summary?.currencyCode && (
          <p className="text-xs text-mw-neutral-500 mt-1 flex items-center gap-1">
            <Info className="size-3.5 shrink-0" />
            {tDashboard("budgetTracker.currencyNote", {
              currency: summary.currencyCode,
            })}
          </p>
        )}
      </CardHeader>
      <CardContent className="relative pt-4 space-y-4 px-0">
        {isFetching && (
          <Loading
            overlay
            size="md"
            variant="primary"
            text={tDashboard("loading")}
          />
        )}
        {/* Budget Metrics Cards */}
        {showOverview && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <SummaryCard
              icon={<Wallet className="w-7 h-7 text-mw-primary-500" />}
              iconBgColor="bg-mw-primary-50"
              title={tDashboard("budgetTracker.totalBudget")}
              value={formatSummaryCurrency(totalRevenue, summary?.currencyCode)}
              subtitle={tDashboard("budgetTracker.vsLastPeriod", {
                amount: formatSummaryCurrency(
                  lastPeriodTotalBudget,
                  summary?.currencyCode,
                ),
              })}
              trend={
                lastPeriodTotalBudget === 0
                  ? undefined
                  : {
                      value: Math.abs(Math.round(budgetTrend * 10) / 10),
                      isPositive: budgetTrend >= 0,
                    }
              }
              trendLabel={
                lastPeriodTotalBudget === 0 && totalRevenue !== 0
                  ? tDashboard("budgetTracker.newLabel")
                  : undefined
              }
            />
            <SummaryCard
              icon={
                <BanknoteArrowDown className="w-7 h-7 text-mw-secondary-500" />
              }
              iconBgColor="bg-mw-secondary-50"
              title={tDashboard("budgetTracker.budgetSpent")}
              value={formatSummaryCurrency(totalCost, summary?.currencyCode)}
              subtitle={tDashboard("budgetTracker.usedInActiveCampaigns")}
              trend={
                lastPeriodTotalCost === 0
                  ? undefined
                  : {
                      value: Math.abs(Math.round(costTrend * 10) / 10),
                      isPositive: costTrend >= 0,
                    }
              }
              trendLabel={
                lastPeriodTotalCost === 0 && totalCost !== 0
                  ? tDashboard("budgetTracker.newLabel")
                  : undefined
              }
            />
            <SummaryCard
              icon={<Banknote className="w-7 h-7 text-mw-smaragdine-500" />}
              iconBgColor="bg-mw-smaragdine-50"
              title={tDashboard("budgetTracker.remainingBudget")}
              value={formatSummaryCurrency(
                remainingBudget,
                summary?.currencyCode,
              )}
              subtitle={tDashboard("budgetTracker.availableForAllocation")}
            />
          </div>
        )}

        {/* Budget Performance Summary Chart */}
        {showPerformanceSummary && (
          <Card className="p-4">
            <CardHeader className="pb-4 border-b border-mw-neutral-100">
              <CardTitle
                className="text-base font-medium leading-5"
                id="budget-summary-heading"
              >
                {tDashboard("budgetTracker.budgetPerformanceSummary")}
              </CardTitle>
            </CardHeader>

            <CardContent className="pt-4 pb-0 px-0">
              <LineChart
                title=""
                labels={chartData.labels}
                showLegend={true}
                datasets={[
                  {
                    label: tDashboard("budgetTracker.total"),
                    data: chartData.totalData,
                    borderColor: "#2176cc", // Blue
                    backgroundColor: "#2176cc",
                  },
                  {
                    label: tDashboard("budgetTracker.spent"),
                    data: chartData.spentData,
                    borderColor: "#c52828", // Red
                    backgroundColor: "#c52828",
                  },
                  {
                    label: tDashboard("budgetTracker.remaining"),
                    data: chartData.remainingData,
                    borderColor: "#2d7d32", // Green
                    backgroundColor: "#2d7d32",
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
            </CardContent>
          </Card>
        )}
      </CardContent>
    </Card>
  );
};

export default React.memo(BudgetTracker);
