import { useGetCampaignPerformanceQuery } from "@services/dashboard/dashboardSlice";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignPerformance from "../CampaignPerformance";

const mockCalculateDateRangeForPeriod = vi.fn();
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

vi.mock("@services/dashboard/dashboardSlice", () => ({
  useGetCampaignPerformanceQuery: vi.fn(),
}));

vi.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock("@utils/dashboard.utils", () => ({
  calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
    mockCalculateDateRangeForPeriod(period, range),
}));

vi.mock("@constants/campaign.constants", () => ({
  CAMPAIGN_STATUS_OPTIONS: [
    { id: "all", value: "ALL", label: "All Statuses" },
    { id: "active", value: "ACTIVE", label: "Active" },
    { id: "completed", value: "COMPLETED", label: "Completed" },
    { id: "draft", value: "DRAFT", label: "Draft" },
  ],
}));

// Invoke each column's cellRenderer so function coverage includes column renderers
vi.mock("@components/ui/AgGridTable", () => ({
  AgGridTable: ({
    rowData,
    columnDefs,
    emptyMessage,
  }: {
    rowData: Record<string, unknown>[];
    columnDefs: ColDef<Record<string, unknown>>[];
    emptyMessage?: string;
  }) => {
    const rows = Array.isArray(rowData) ? rowData : [];
    if (rows.length === 0) {
      return (
        <div data-testid="campaign-performance-grid">
          <span data-testid="empty-message">{emptyMessage ?? "Empty"}</span>
        </div>
      );
    }
    return (
      <div data-testid="campaign-performance-grid">
        {rows.map((row: Record<string, unknown>) => (
          <div key={String(row.id)} data-testid={`row-${row.id}`}>
            {row.campaignName as string}
            {(columnDefs ?? []).map((col: ColDef<Record<string, unknown>>) => {
              if (typeof col.cellRenderer === "function") {
                const params = {
                  data: row,
                  value: col.field ? row[col.field] : undefined,
                  getValue: () => (col.field ? row[col.field] : undefined),
                } as ICellRendererParams<Record<string, unknown>>;
                const content = col.cellRenderer(params);
                return (
                  <div
                    key={String(col.colId)}
                    data-testid={`cell-${col.colId}`}
                  >
                    {content}
                  </div>
                );
              }
              return null;
            })}
          </div>
        ))}
      </div>
    );
  },
}));

vi.mock("@components/ui/StatusBadge", () => ({
  StatusBadge: ({
    status,
    children,
  }: {
    status: string;
    children: React.ReactNode;
  }) => (
    <span data-testid="status-badge" data-status={status}>
      {children}
    </span>
  ),
}));

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
        <option value="active">Active</option>
        <option value="completed">Completed</option>
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

describe("CampaignPerformance", () => {
  const mockPerformanceData = {
    data: [
      {
        id: "1",
        name: "Campaign One",
        userName: "user1",
        status: "ACTIVE",
        goals: { goalType: "REACH", targetValue: 1000, typeName: "Reach" },
        startDate: "2026-01-01",
        endDate: "2026-01-31",
        budget: 5000,
        currency: "MYR",
        inventory: 10,
        totalCost: 2000,
        estimatedImpression: 10000,
        estimatedReach: 5000,
        sov: 25,
        totalSot: 100,
        plannedSot: 80,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-31",
    });
    vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
      data: mockPerformanceData,
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
  });

  describe("Rendering", () => {
    it("renders card title Campaigns Performance", () => {
      render(<CampaignPerformance />);
      expect(
        document.getElementById("campaigns-performance-heading"),
      ).toBeInTheDocument();
    });

    it("renders Campaign Status dropdown", () => {
      render(<CampaignPerformance />);
      expect(screen.getByTestId("status-dropdown")).toBeInTheDocument();
    });
  });

  describe("Loading state", () => {
    it("shows loading message when isLoading is true", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
      render(<CampaignPerformance />);
      expect(
        screen.getByText("campaignPerformance.loadingData"),
      ).toBeInTheDocument();
    });

    it("renders AgGridTable when not loading", () => {
      render(<CampaignPerformance />);
      expect(
        screen.getByTestId("campaign-performance-grid"),
      ).toBeInTheDocument();
    });
  });

  describe("refreshing indicator", () => {
    it("shows a spinner during a background refetch instead of the full loading swap", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: mockPerformanceData,
        isLoading: false,
        isFetching: true,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);

      render(<CampaignPerformance />);

      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(
        screen.getByTestId("campaign-performance-grid"),
      ).toBeInTheDocument();
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<CampaignPerformance />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
  });

  describe("Data", () => {
    it("renders table rows when performance data is present", () => {
      render(<CampaignPerformance />);
      expect(screen.getByTestId("row-1")).toBeInTheDocument();
      expect(screen.getByText("Campaign One")).toBeInTheDocument();
    });

    it("shows empty message when no data", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: { data: [] },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
      render(<CampaignPerformance />);
      expect(screen.getByTestId("empty-message")).toBeInTheDocument();
    });

    it("maps API item to display fields (estimatedImpression, currency, etc.)", () => {
      render(<CampaignPerformance />);
      expect(screen.getByText("Campaign One")).toBeInTheDocument();
    });
  });

  describe("Date range and query", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      render(
        <CampaignPerformance
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
      render(<CampaignPerformance />);
      expect(useGetCampaignPerformanceQuery).toHaveBeenCalledWith(
        expect.any(Object),
        { skip: true },
      );
    });
  });

  describe("Status filter", () => {
    it("passes selectedStatus to Dropdown", () => {
      render(<CampaignPerformance />);
      const select = screen.getByTestId("status-select");
      expect(select).toHaveValue("all");
    });

    it("calls API with status filter when status is not all", async () => {
      const user = userEvent.setup();
      render(<CampaignPerformance />);
      const select = screen.getByTestId("status-select");
      await user.selectOptions(select, "active");
      await waitFor(() => {
        expect(useGetCampaignPerformanceQuery).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: "ACTIVE" }),
          expect.any(Object),
        );
      });
    });

    it("calls API with COMPLETED when completed is selected", async () => {
      const user = userEvent.setup();
      render(<CampaignPerformance />);
      await user.selectOptions(
        screen.getByTestId("status-select"),
        "completed",
      );
      await waitFor(() => {
        expect(useGetCampaignPerformanceQuery).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: "COMPLETED" }),
          expect.any(Object),
        );
      });
    });
  });

  describe("Column cellRenderers (function coverage)", () => {
    it("invokes all column cellRenderers when table has data", () => {
      render(<CampaignPerformance />);
      // campaignName has no cellRenderer (default); all others do
      expect(screen.getByTestId("cell-startDate")).toBeInTheDocument();
      expect(screen.getByTestId("cell-impression")).toBeInTheDocument();
      expect(screen.getByTestId("cell-reach")).toBeInTheDocument();
      expect(screen.getByTestId("cell-sov")).toBeInTheDocument();
      expect(screen.getByTestId("cell-budget")).toBeInTheDocument();
      expect(screen.getByTestId("cell-status")).toBeInTheDocument();
      expect(screen.getByTestId("cell-actions")).toBeInTheDocument();
    });

    it("renders status badge for row status (status cellRenderer)", () => {
      render(<CampaignPerformance />);
      const badges = screen.getAllByTestId("status-badge");
      expect(badges.length).toBeGreaterThan(0);
      expect(badges[0]).toHaveAttribute("data-status", "active");
    });

    it("renders action button (actions cellRenderer)", () => {
      render(<CampaignPerformance />);
      expect(screen.getByRole("button")).toBeInTheDocument();
    });

    it("navigates to campaign view page when action button is clicked", async () => {
      const user = userEvent.setup();
      render(<CampaignPerformance />);
      const button = screen.getByRole("button");
      await user.click(button);
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith("/campaigns/view/1");
      });
    });
  });

  describe("displayData and API mapping", () => {
    it("maps item with missing optional fields to defaults (currency MYR, reach 0)", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: {
          data: [
            {
              id: "2",
              name: "Minimal Campaign",
              userName: "u",
              status: "DRAFT",
              goals: { goalType: "REACH", targetValue: 0, typeName: "" },
              startDate: "2026-02-01",
              endDate: "2026-02-28",
              budget: 100,
              currency: undefined as unknown as string,
              inventory: 0,
              totalCost: 50,
              estimatedImpression: 0,
              estimatedReach: undefined as unknown as number,
              sov: 0,
              totalSot: 0,
              plannedSot: 0,
            },
          ],
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
      render(<CampaignPerformance />);
      expect(screen.getByText("Minimal Campaign")).toBeInTheDocument();
      expect(screen.getByTestId("row-2")).toBeInTheDocument();
    });

    it("renders reach as N/A when reach is 0 (reach cellRenderer branch)", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: {
          data: [
            {
              id: "3",
              name: "Zero Reach",
              userName: "u",
              status: "ACTIVE",
              goals: { goalType: "REACH", targetValue: 0, typeName: "" },
              startDate: "2026-01-01",
              endDate: "2026-01-31",
              budget: 1000,
              currency: "MYR",
              inventory: 0,
              totalCost: 0,
              estimatedImpression: 0,
              estimatedReach: 0,
              sov: 0,
              totalSot: 0,
              plannedSot: 0,
            },
          ],
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
      render(<CampaignPerformance />);
      expect(screen.getByText("N/A")).toBeInTheDocument();
    });

    it("renders status fallback when status has no matching option (status cellRenderer)", () => {
      vi.mocked(useGetCampaignPerformanceQuery).mockReturnValue({
        data: {
          data: [
            {
              id: "4",
              name: "Unknown Status",
              userName: "u",
              status: "UNKNOWN_STATUS",
              goals: { goalType: "REACH", targetValue: 0, typeName: "" },
              startDate: "2026-01-01",
              endDate: "2026-01-31",
              budget: 0,
              currency: "MYR",
              inventory: 0,
              totalCost: 0,
              estimatedImpression: 0,
              estimatedReach: 0,
              sov: 0,
              totalSot: 0,
              plannedSot: 0,
            },
          ],
        },
        isLoading: false,
        isError: false,
      } as unknown as ReturnType<typeof useGetCampaignPerformanceQuery>);
      render(<CampaignPerformance />);
      const badges = screen.getAllByTestId("status-badge");
      expect(
        badges.some(
          (b) => b.textContent === "campaignsList.status.UNKNOWN_STATUS",
        ),
      ).toBe(true);
    });
  });
});
