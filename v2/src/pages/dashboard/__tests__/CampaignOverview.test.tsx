import { useGetCampaignOverviewByStatusQuery } from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignOverview from "../CampaignOverview";

const mockCalculateDateRangeForPeriod = vi.fn();

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
  useGetCampaignOverviewByStatusQuery: vi.fn(),
}));

vi.mock("@utils/dashboard.utils", () => ({
  calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
    mockCalculateDateRangeForPeriod(period, range),
}));

vi.mock("@components/common/BarChart", () => ({
  default: ({
    labels,
    datasets,
  }: {
    labels: string[];
    datasets: { label: string; data: number[] }[];
  }) => (
    <div data-testid="campaign-overview-barchart">
      <span data-testid="chart-labels">{labels.join(",")}</span>
      <span data-testid="chart-data">{datasets[0]?.data.join(",")}</span>
    </div>
  ),
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

describe("CampaignOverview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-31",
    });
    vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
      data: {
        data: {
          plannedCampaigns: 5,
          reviewingCampaigns: 3,
          negotiatingCampaigns: 2,
          approvedCampaigns: 4,
          activeCampaigns: 10,
          completedCampaigns: 8,
        },
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
  });

  describe("Rendering", () => {
    it("renders card title Campaign Overview", () => {
      render(<CampaignOverview />);
      expect(
        document.getElementById("campaign-overview-heading"),
      ).toBeInTheDocument();
    });

    it("does not render CampaignTypeDropdown", () => {
      render(<CampaignOverview />);
      expect(
        screen.queryByTestId("campaign-type-dropdown"),
      ).not.toBeInTheDocument();
    });
  });

  describe("Loading state", () => {
    it("shows loading message when isLoading is true", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
      render(<CampaignOverview />);
      expect(
        screen.getByText("campaignOverview.loadingChartData"),
      ).toBeInTheDocument();
    });

    it("renders BarChart when not loading", () => {
      render(<CampaignOverview />);
      expect(
        screen.getByTestId("campaign-overview-barchart"),
      ).toBeInTheDocument();
    });
  });

  describe("refreshing indicator", () => {
    it("shows a spinner during a background refetch instead of the full loading swap", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: {
          data: {
            plannedCampaigns: 5,
            reviewingCampaigns: 3,
            negotiatingCampaigns: 2,
            approvedCampaigns: 4,
            activeCampaigns: 10,
            completedCampaigns: 8,
          },
        },
        isLoading: false,
        isFetching: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);

      render(<CampaignOverview />);

      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(
        screen.getByTestId("campaign-overview-barchart"),
      ).toBeInTheDocument();
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<CampaignOverview />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("Chart data", () => {
    it("renders chart with API data when overviewData is present", () => {
      render(<CampaignOverview />);
      expect(screen.getByTestId("chart-labels")).toHaveTextContent(
        /planned|reviewing|negotiating|approve|active|completed/i,
      );
      expect(screen.getByTestId("chart-data")).toHaveTextContent("5"); // plannedCampaigns
    });

    it("renders chart with zero data when overviewData is missing", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignOverviewByStatusQuery>);
      render(<CampaignOverview />);
      expect(
        screen.getByTestId("campaign-overview-barchart"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("chart-data")).toHaveTextContent("0");
    });

    it("uses fallback zero for missing status fields", () => {
      vi.mocked(useGetCampaignOverviewByStatusQuery).mockReturnValue({
        data: {
          data: {
            plannedCampaigns: 1,
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
      render(<CampaignOverview />);
      expect(screen.getByTestId("chart-data")).toHaveTextContent("1");
    });
  });

  describe("Date range", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      render(
        <CampaignOverview
          selectedPeriod="last-7-days"
          dateRange={{ from: new Date(), to: new Date() }}
        />,
      );
      expect(mockCalculateDateRangeForPeriod).toHaveBeenCalledWith(
        "last-7-days",
        expect.any(Object),
      );
    });

    it("skips query when startDate or endDate is missing", () => {
      mockCalculateDateRangeForPeriod.mockReturnValue({
        startDate: null,
        endDate: null,
      });
      render(<CampaignOverview />);
      expect(useGetCampaignOverviewByStatusQuery).toHaveBeenCalledWith(
        expect.any(Object),
        { skip: true },
      );
    });
  });
});
