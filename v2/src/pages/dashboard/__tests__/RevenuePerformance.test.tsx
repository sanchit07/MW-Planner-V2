import {
  useGetPerformanceSummaryCostQuery,
  useGetCampaignOverviewByStatusQuery,
} from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import RevenuePerformance from "../RevenuePerformance";

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

const mockNavigate = vi.fn();

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

vi.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock("@services/dashboard/dashboardSlice", () => ({
  useGetPerformanceSummaryCostQuery: vi.fn(),
  useGetCampaignOverviewByStatusQuery: vi.fn(),
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
  default: ({ title, value }: { title: string; value: string | number }) => (
    <div data-testid={`summary-${title.replace(/\s/g, "-").toLowerCase()}`}>
      <span>{title}</span>
      <span>{value}</span>
    </div>
  ),
}));

vi.mock("@components/common/LineChart", () => ({
  default: ({ title }: { title: string }) => (
    <div data-testid="revenue-line-chart">{title}</div>
  ),
}));

vi.mock("@components/common/BarChart", () => ({
  default: () => <div data-testid="revenue-barchart">BarChart</div>,
}));

vi.mock("@components/dashboard/CampaignTypeDropdown", () => ({
  default: () => <div data-testid="campaign-type-dropdown">Dropdown</div>,
}));

vi.mock("../tabs/RegionalTab", () => ({
  default: () => <div data-testid="regional-tab">RegionalTab</div>,
}));

vi.mock("../tabs/ClientsTab", () => ({
  default: () => <div data-testid="clients-tab">ClientsTab</div>,
}));

vi.mock("../tabs/TeamTab", () => ({
  default: () => <div data-testid="team-tab">TeamTab</div>,
}));

vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
  }) => (
    <button type="button" onClick={onClick}>
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Tabs", () => ({
  Tabs: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsList: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  TabsTrigger: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
  }) => (
    <button type="button" data-value={value}>
      {children}
    </button>
  ),
  TabsContent: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
  }) => <div data-tab={value}>{children}</div>,
}));

describe("RevenuePerformance", () => {
  const defaultWidgetVisibility = {
    "sales-overview": true,
    "sales-performance-summary": true,
    "sales-pipeline-funnel": true,
    "revenue-distribution": false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-31",
    });
    mockBucketDayWiseChartData.mockReturnValue({
      labels: ["1 Jan", "2 Jan"],
      revenue: [100, 200],
      cost: [50, 80],
    });
    vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
      data: {
        data: {
          dateWiseSchedulePerDateRate: {},
          totalRevenue: 1000,
          lastPeriodTotalRevenue: 800,
          averageRevenuePerUnit: 50,
          lastPeriodAverageRevenuePerUnit: 40,
          conversionRate: 5.5,
          lastPeriodConversionRate: 4,
          currencyCode: "MYR",
        },
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
    vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
      data: {
        data: {
          plannedCampaigns: 2,
          reviewingCampaigns: 1,
          negotiatingCampaigns: 0,
          approvedCampaigns: 1,
          activeCampaigns: 5,
          completedCampaigns: 3,
        },
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
  });

  describe("Rendering", () => {
    it("renders card title Revenue Performance", () => {
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        document.getElementById("revenue-performance-heading"),
      ).toBeInTheDocument();
    });

    it("renders sales overview SummaryCards when sales-overview is not false", () => {
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.revenuegenerated"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.averageperunit"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.conversionrate"),
      ).toBeInTheDocument();
    });

    it("hides sales overview section when sales-overview is false", () => {
      render(
        <RevenuePerformance
          widgetVisibility={{
            ...defaultWidgetVisibility,
            "sales-overview": false,
          }}
        />,
      );
      expect(
        screen.queryByTestId("summary-revenueperformance.revenuegenerated"),
      ).not.toBeInTheDocument();
    });

    it("renders Sales Performance Summary and tabs when sales-performance-summary is not false", () => {
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        document.getElementById("sales-performance-summary-heading"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("revenue-line-chart")).toBeInTheDocument();
      expect(screen.getByTestId("regional-tab")).toBeInTheDocument();
      expect(screen.getByTestId("clients-tab")).toBeInTheDocument();
      expect(screen.getByTestId("team-tab")).toBeInTheDocument();
    });

    it("hides Sales Performance Summary when sales-performance-summary is false", () => {
      render(
        <RevenuePerformance
          widgetVisibility={{
            ...defaultWidgetVisibility,
            "sales-performance-summary": false,
          }}
        />,
      );
      expect(
        screen.queryByRole("heading", { name: /sales performance summary/i }),
      ).not.toBeInTheDocument();
    });

    it("renders Sales Pipeline funnel when sales-pipeline-funnel is not false", () => {
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        document.getElementById("sales-funnel-heading"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("revenue-barchart")).toBeInTheDocument();
    });

    it("hides Sales Pipeline funnel when sales-pipeline-funnel is false", () => {
      render(
        <RevenuePerformance
          widgetVisibility={{
            ...defaultWidgetVisibility,
            "sales-pipeline-funnel": false,
          }}
        />,
      );
      expect(
        screen.queryByRole("heading", { name: /sales pipeline funnel/i }),
      ).not.toBeInTheDocument();
    });
  });

  describe("Pipeline loading state", () => {
    it("shows loading chart data when overview is loading", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByText("revenuePerformance.loadingChartData"),
      ).toBeInTheDocument();
    });
  });

  describe("refreshing indicator", () => {
    it("shows a spinner next to the title while the cost summary is refetching", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            dateWiseSchedulePerDateRate: {},
            totalRevenue: 1000,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isFetching: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);

      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);

      expect(screen.getAllByRole("status").length).toBeGreaterThan(0);
    });

    it("shows a spinner next to the funnel title during a background refetch, not the full loading swap", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: {
          data: {
            plannedCampaigns: 2,
            reviewingCampaigns: 1,
            negotiatingCampaigns: 0,
            approvedCampaigns: 1,
            activeCampaigns: 5,
            completedCampaigns: 3,
          },
        },
        isLoading: false,
        isFetching: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);

      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);

      expect(
        screen.queryByText("revenuePerformance.loadingChartData"),
      ).not.toBeInTheDocument();
      expect(screen.getAllByRole("status").length).toBeGreaterThan(0);
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("View Campaign navigation", () => {
    it("navigates to /campaigns when View Campaign is clicked", async () => {
      const user = userEvent.setup();
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      const viewBtn = screen.getByRole("button", {
        name: "revenuePerformance.viewCampaign",
      });
      await user.click(viewBtn);
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns");
    });
  });

  describe("Pipeline data branches", () => {
    it("renders pipeline chart with zero data when overviewData is missing", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(screen.getByTestId("revenue-barchart")).toBeInTheDocument();
    });

    it("renders pipeline section when revenue-distribution is true and sales-pipeline-funnel is false", () => {
      render(
        <RevenuePerformance
          widgetVisibility={{
            ...defaultWidgetVisibility,
            "sales-pipeline-funnel": false,
            "revenue-distribution": true,
          }}
        />,
      );
      expect(
        document.getElementById("revenue-performance-heading"),
      ).toBeInTheDocument();
      expect(document.querySelector(".flex.gap-4.mt-6")).toBeInTheDocument();
    });

    it("uses last-30-days for LineChart initialVisibleItems when selectedPeriod is last-30-days", () => {
      render(
        <RevenuePerformance
          widgetVisibility={defaultWidgetVisibility}
          selectedPeriod="last-30-days"
        />,
      );
      expect(screen.getByTestId("revenue-line-chart")).toBeInTheDocument();
    });
  });

  describe("Summary trend branches", () => {
    it("renders trend undefined when lastPeriod and current are both zero (revenue)", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 0,
            lastPeriodTotalRevenue: 0,
            averageRevenuePerUnit: 0,
            lastPeriodAverageRevenuePerUnit: 0,
            conversionRate: 0,
            lastPeriodConversionRate: 0,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.revenuegenerated"),
      ).toBeInTheDocument();
    });

    it("renders negative trend (isPositive false) when current is less than last period", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 500,
            lastPeriodTotalRevenue: 800,
            averageRevenuePerUnit: 30,
            lastPeriodAverageRevenuePerUnit: 50,
            conversionRate: 3,
            lastPeriodConversionRate: 5,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.revenuegenerated"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.averageperunit"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.conversionrate"),
      ).toBeInTheDocument();
    });

    it("renders trend value 100 when lastPeriod is zero but current is not", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 100,
            lastPeriodTotalRevenue: 0,
            averageRevenuePerUnit: 0,
            lastPeriodAverageRevenuePerUnit: 0,
            conversionRate: 0,
            lastPeriodConversionRate: 0,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.revenuegenerated"),
      ).toBeInTheDocument();
    });

    it("renders avgPerUnit trend value 100 when lastPeriodAverageRevenuePerUnit is 0", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 0,
            lastPeriodTotalRevenue: 0,
            averageRevenuePerUnit: 60,
            lastPeriodAverageRevenuePerUnit: 0,
            conversionRate: 0,
            lastPeriodConversionRate: 0,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.averageperunit"),
      ).toBeInTheDocument();
    });

    it("renders conversion trend value 100 when lastPeriodConversionRate is 0", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 0,
            lastPeriodTotalRevenue: 0,
            averageRevenuePerUnit: 0,
            lastPeriodAverageRevenuePerUnit: 0,
            conversionRate: 8,
            lastPeriodConversionRate: 0,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.conversionrate"),
      ).toBeInTheDocument();
    });

    it("renders positive trend (isPositive true) when current exceeds last period", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            totalRevenue: 1200,
            lastPeriodTotalRevenue: 800,
            averageRevenuePerUnit: 60,
            lastPeriodAverageRevenuePerUnit: 40,
            conversionRate: 6,
            lastPeriodConversionRate: 4,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(
        screen.getByTestId("summary-revenueperformance.revenuegenerated"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.averageperunit"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("summary-revenueperformance.conversionrate"),
      ).toBeInTheDocument();
    });
  });

  describe("revenueCostData and bucketed branches", () => {
    it("uses dayWiseCostData when API returns non-empty dateWiseSchedulePerDateRate", () => {
      vi.mocked(useGetPerformanceSummaryCostQuery).mockReturnValue({
        data: {
          data: {
            dateWiseSchedulePerDateRate: {
              "2026-01-01": { revenue: 100, cost: 50 },
              "2026-01-02": { revenue: 200, cost: 80 },
            },
            totalRevenue: 300,
            lastPeriodTotalRevenue: 0,
            averageRevenuePerUnit: 0,
            lastPeriodAverageRevenuePerUnit: 0,
            conversionRate: 0,
            lastPeriodConversionRate: 0,
            currencyCode: "MYR",
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryCostQuery>);
      mockBucketDayWiseChartData.mockReturnValue({
        labels: ["1 Jan", "2 Jan"],
        revenue: [100, 200],
        cost: [50, 80],
      });
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalledWith(
        expect.objectContaining({
          dayWiseData: expect.any(Object),
        }),
      );
    });

    it("salesPipelineYAxisMax and step with all-zero pipeline data", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: {
          data: {
            plannedCampaigns: 0,
            reviewingCampaigns: 0,
            negotiatingCampaigns: 0,
            approvedCampaigns: 0,
            activeCampaigns: 0,
            completedCampaigns: 0,
          },
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(screen.getByTestId("revenue-barchart")).toBeInTheDocument();
    });

    it("bucketedToRevenueCostChartData uses revenue key when present", () => {
      mockBucketDayWiseChartData.mockReturnValue({
        labels: ["1 Jan", "2 Jan"],
        revenue: [100, 200],
        cost: [50, 80],
      });
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalled();
      expect(screen.getByTestId("revenue-line-chart")).toBeInTheDocument();
    });

    it("bucketedToRevenueCostChartData uses cost fallback for revenueData when revenue absent", () => {
      mockBucketDayWiseChartData.mockReturnValue({
        labels: ["1 Jan"],
        cost: [100],
      });
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(screen.getByTestId("revenue-line-chart")).toBeInTheDocument();
    });

    it("bucketedToRevenueCostChartData uses zero array for costData when cost absent", () => {
      mockBucketDayWiseChartData.mockReturnValue({
        labels: ["1 Jan", "2 Jan"],
        revenue: [10, 20],
      });
      render(<RevenuePerformance widgetVisibility={defaultWidgetVisibility} />);
      expect(screen.getByTestId("revenue-line-chart")).toBeInTheDocument();
    });
  });
});
