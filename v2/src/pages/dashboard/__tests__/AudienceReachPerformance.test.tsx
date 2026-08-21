import { useGetPerformanceSummaryReachQuery } from "@services/dashboard/dashboardSlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import AudienceReachPerformance from "../AudienceReachPerformance";

vi.mock("@components/ui/Dropdown", () => ({
  Dropdown: ({
    value,
    onChange,
    children,
  }: {
    value: string;
    onChange: (v: string) => void;
    children: React.ReactNode;
  }) => (
    <div data-testid="status-dropdown">
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid="status-select"
      >
        <option value="all">All Statuses</option>
        <option value="planned">Planned</option>
        <option value="active">Active</option>
      </select>
      {children}
    </div>
  ),
  DropdownTrigger: ({ children }: { children: React.ReactNode }) => (
    <span>{children}</span>
  ),
  DropdownContent: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  DropdownItem: () => null,
}));

const mockBucketDayWiseChartData = vi.fn();
const mockCalculateDateRangeForPeriod = vi.fn();
const mockFormatCompactNumber = vi.fn((v: number) => String(v));

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
  useGetPerformanceSummaryReachQuery: vi.fn(),
}));

vi.mock("@utils/dashboard.utils", () => ({
  bucketDayWiseChartData: (args: unknown) => mockBucketDayWiseChartData(args),
  calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
    mockCalculateDateRangeForPeriod(period, range),
  formatCompactNumber: (v: number) => mockFormatCompactNumber(v),
}));

vi.mock("@components/common/MixedChart", () => ({
  default: ({
    labels,
    datasets,
  }: {
    labels: string[];
    datasets: { label: string; data: number[] }[];
  }) => (
    <div data-testid="audience-mixed-chart">
      <span data-testid="chart-labels">{labels.join(",")}</span>
      {datasets.map((d) => (
        <span key={d.label} data-testid={`dataset-${d.label}`}>
          {d.data?.join(",") ?? ""}
        </span>
      ))}
    </div>
  ),
}));

describe("AudienceReachPerformance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-07",
    });
    mockBucketDayWiseChartData.mockReturnValue({
      labels: ["1 Jan", "2 Jan", "3 Jan"],
      reach: [100, 200, 150],
      impressions: [120, 220, 180],
    });
    vi.mocked(useGetPerformanceSummaryReachQuery).mockReturnValue({
      data: {
        data: {
          dateWiseSchedulePerDateRate: {
            "2026-01-01": { reach: 100, impressions: 120 },
            "2026-01-02": { reach: 200, impressions: 220 },
            "2026-01-03": { reach: 150, impressions: 180 },
          },
        },
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useGetPerformanceSummaryReachQuery>);
  });
  function renderInventoriesPage() {
    return render(
      <MemoryRouter>
        <AudienceReachPerformance />
      </MemoryRouter>,
    );
  }

  describe("Rendering", () => {
    it("renders card with audience reach title from translation", () => {
      renderInventoriesPage();
      expect(
        document.getElementById("audience-reach-title"),
      ).toBeInTheDocument();
    });

    it("renders MixedChart", () => {
      render(<AudienceReachPerformance />);
      expect(screen.getByTestId("audience-mixed-chart")).toBeInTheDocument();
    });

    it("applies custom className when provided", () => {
      const { container } = render(
        <AudienceReachPerformance className="custom-class" />,
      );
      const card = container.querySelector(".custom-class");
      expect(card).toBeInTheDocument();
    });
  });

  describe("refreshing indicator", () => {
    it("shows a spinner while a background refetch is in flight, without hiding stale data", () => {
      vi.mocked(useGetPerformanceSummaryReachQuery).mockReturnValue({
        data: {
          data: {
            dateWiseSchedulePerDateRate: {
              "2026-01-01": { reach: 100, impressions: 120 },
            },
          },
        },
        isLoading: false,
        isFetching: true,
        isError: false,
        refetch: vi.fn(),
      } as unknown as ReturnType<typeof useGetPerformanceSummaryReachQuery>);

      render(<AudienceReachPerformance />);

      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(screen.getByTestId("audience-mixed-chart")).toBeInTheDocument();
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<AudienceReachPerformance />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("API and data", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      render(
        <AudienceReachPerformance
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
      render(<AudienceReachPerformance />);
      expect(useGetPerformanceSummaryReachQuery).toHaveBeenCalledWith(
        expect.any(Object),
        { skip: true },
      );
    });

    it("uses API reach data when dateWiseSchedulePerDateRate is present", () => {
      render(<AudienceReachPerformance />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalled();
      expect(screen.getByTestId("audience-mixed-chart")).toBeInTheDocument();
    });

    it("uses defaultData when API returns no dayWiseData", () => {
      vi.mocked(useGetPerformanceSummaryReachQuery).mockReturnValue({
        data: { data: {} },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetPerformanceSummaryReachQuery>);
      render(<AudienceReachPerformance />);
      expect(mockBucketDayWiseChartData).toHaveBeenCalledWith(
        expect.objectContaining({
          dayWiseData: expect.any(Object),
        }),
      );
      expect(
        Object.keys(
          (
            mockBucketDayWiseChartData.mock.calls[0][0] as {
              dayWiseData: Record<string, unknown>;
            }
          ).dayWiseData,
        ).length,
      ).toBeGreaterThan(0);
    });
  });

  describe("Status filter", () => {
    it("does not send statuses when All Statuses is selected", () => {
      renderInventoriesPage();
      expect(useGetPerformanceSummaryReachQuery).toHaveBeenLastCalledWith(
        expect.not.objectContaining({ statuses: expect.anything() }),
        expect.any(Object),
      );
    });

    it("sends statuses to the API when a status is selected", async () => {
      const user = userEvent.setup();
      renderInventoriesPage();
      await user.selectOptions(screen.getByTestId("status-select"), "planned");
      await waitFor(() => {
        expect(useGetPerformanceSummaryReachQuery).toHaveBeenLastCalledWith(
          expect.objectContaining({ statuses: "PLANNED" }),
          expect.any(Object),
        );
      });
    });

    it("sends ACTIVE when active is selected", async () => {
      const user = userEvent.setup();
      renderInventoriesPage();
      await user.selectOptions(screen.getByTestId("status-select"), "active");
      await waitFor(() => {
        expect(useGetPerformanceSummaryReachQuery).toHaveBeenLastCalledWith(
          expect.objectContaining({ statuses: "ACTIVE" }),
          expect.any(Object),
        );
      });
    });
  });
});
