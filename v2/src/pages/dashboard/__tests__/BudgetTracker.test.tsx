import { useGetPerformanceSummaryCostQuery } from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import BudgetTracker from "../BudgetTracker";

const mockBucketDayWiseChartData = vi.fn();
const mockCalculateDateRangeForPeriod = vi.fn();
const mockFormatSummaryCurrency = vi.fn(
  (v: number, c?: string) => `${c ?? "MYR"} ${v}`,
);
const mockFormatChartTooltipDate = vi.fn((l: string) => `${l} 2026`);
const mockFormatChartTooltipValue = vi.fn(
  (v: number, l: string) => `${l}: ${v}`,
);
const mockFormatChartYAxisValue = vi.fn((v: number) => String(v));
const mockGetChartCurrentDateIndex = vi.fn(() => 0);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@store", () => ({
  useAppSelector: vi.fn((selector: (s: unknown) => unknown) =>
    selector({
      profile: {
        profile: {
          activeCompanyId: "company-1",
          current_company: { id: "company-1" },
        },
      },
    }),
  ),
}));

vi.mock("@services/dashboard/dashboardSlice", () => ({
  useGetPerformanceSummaryCostQuery: vi.fn(),
}));

vi.mock("@utils/dashboard.utils", () => ({
  bucketDayWiseChartData: (args: unknown) => mockBucketDayWiseChartData(args),
  calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
    mockCalculateDateRangeForPeriod(period, range),
  formatSummaryCurrency: (v: number, c?: string) =>
    mockFormatSummaryCurrency(v, c),
  formatChartTooltipDate: (l: string) => mockFormatChartTooltipDate(l),
  formatChartTooltipValue: (v: number, l: string) =>
    mockFormatChartTooltipValue(v, l),
  formatChartYAxisValue: (v: number) => mockFormatChartYAxisValue(v),
  getChartCurrentDateIndex: () => mockGetChartCurrentDateIndex(),
}));

vi.mock("../SummaryCard", () => ({
  default: ({
    title,
    value,
    subtitle,
  }: {
    title: string;
    value: string | number;
    subtitle?: string;
  }) => (
    <div data-testid={`summary-${title.replace(/\s/g, "-").toLowerCase()}`}>
      <span>{title}</span>
      <span>{value}</span>
      {subtitle != null && <span>{subtitle}</span>}
    </div>
  ),
}));

vi.mock("@components/common/LineChart", () => ({
  default: ({
    labels,
    datasets,
  }: {
    labels: string[];
    datasets: { label: string; data: number[] }[];
  }) => (
    <div data-testid="budget-line-chart">
      <span data-testid="chart-labels">{labels.join(",")}</span>
      {datasets.map((d) => (
        <span key={d.label} data-testid={`dataset-${d.label}`}>
          {d.data.join(",")}
        </span>
      ))}
    </div>
  ),
}));

describe("BudgetTracker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-31",
    });
    mockBucketDayWiseChartData.mockReturnValue({
      labels: ["1 Jan", "2 Jan"],
      total: [100, 200],
      spent: [50, 80],
      remaining: [50, 120],
    });
    vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
      data: {
        data: {
          dateWiseSchedulePerDateRate: {
            "2026-01-01": { total: 100, cost: 50, remaining: 50 },
            "2026-01-02": { total: 200, cost: 80, remaining: 120 },
          },
          totalBudget: 1000,
          lastPeriodTotalBudget: 800,
          lastPeriodTotalCost: 400,
          totalCost: 500,
          remainingBudget: 500,
          currencyCode: "MYR",
        },
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
  });

  describe("Rendering", () => {
    it("renders card title Budget Tracker", () => {
      render(<BudgetTracker />);
      expect(document.getElementById("budget-heading")).toBeInTheDocument();
    });

    it("renders three SummaryCards: Total Budget, Budget Spent, Remaining Budget", () => {
      render(<BudgetTracker />);
      expect(
        screen.getByTestId("summary-budgettracker.totalbudget"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-budgettracker.budgetspent"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-budgettracker.remainingbudget"),
      ).toBeInTheDocument();
    });

    it("renders Budget Performance Summary chart section", () => {
      render(<BudgetTracker />);
      expect(
        document.getElementById("budget-summary-heading"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("budget-line-chart")).toBeInTheDocument();
    });
  });

  describe("refreshing indicator", () => {
    it("shows a spinner while a background refetch is in flight, without hiding stale data", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            dateWiseSchedulePerDateRate: {},
            totalBudget: 1000,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isFetching: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);

      render(<BudgetTracker />);

      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-budgettracker.totalbudget"),
      ).toBeInTheDocument();
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<BudgetTracker />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("API and data", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      render(
        <BudgetTracker
          selectedPeriod="last-30-days"
          dateRange={{
            from: new Date("2026-01-01"),
            to: new Date("2026-01-31"),
          }}
        />,
      );
      expect(mockCalculateDateRangeForPeriod).toHaveBeenCalledWith(
        "last-30-days",
        { from: expect.any(Date), to: expect.any(Date) },
      );
    });

    it("skips query when startDate or endDate is missing", () => {
      mockCalculateDateRangeForPeriod.mockReturnValue({
        startDate: null,
        endDate: null,
      });
      render(<BudgetTracker />);
      expect(useGetPerformanceSummaryCostQuery).toHaveBeenCalledWith(
        expect.any(Object),
        { skip: true },
      );
    });

    it("uses API cost data for chart when dateWiseSchedulePerDateRate is present", () => {
      render(<BudgetTracker />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalled();
      expect(screen.getByTestId("budget-line-chart")).toBeInTheDocument();
    });

    it("formats summary currency with API currencyCode", () => {
      render(<BudgetTracker />);
      expect(mockFormatSummaryCurrency).toHaveBeenCalledWith(
        expect.any(Number),
        "MYR",
      );
    });
  });

  describe("Chart data branches", () => {
    it("uses empty source when API returns no dayWiseData", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: { data: {} },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<BudgetTracker />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalledWith(
        expect.objectContaining({
          dayWiseData: {},
        }),
      );
    });

    it("uses last-30-days initialVisibleItems and spacing when selectedPeriod is last-30-days", () => {
      mockBucketDayWiseChartData.mockReturnValue({
        labels: Array(22).fill("d"),
        total: Array(22).fill(0),
        spent: Array(22).fill(0),
        remaining: Array(22).fill(0),
      });
      render(<BudgetTracker selectedPeriod="last-30-days" />);
      expect(screen.getByTestId("budget-line-chart")).toBeInTheDocument();
    });
  });
});
