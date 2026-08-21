import { useGetSalesPerformanceSummaryQuery } from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import { describe, it, expect, vi, beforeEach } from "vitest";

import TeamTab from "../TeamTab";

const mockCalculateDateRangeForPeriod = vi.fn();
const mockFormatCurrency = vi.fn(
  (amount: number, code?: string) => `${code ?? "MYR"} ${amount}`,
);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
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
  useGetSalesPerformanceSummaryQuery: vi.fn(),
}));

vi.mock("@utils/dashboard.utils", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@utils/dashboard.utils")>();
  return {
    ...actual,
    calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
      mockCalculateDateRangeForPeriod(period, range),
  };
});

vi.mock("@utils/campaign.utils", () => ({
  formatCurrency: (amount: number, code?: string) =>
    mockFormatCurrency(amount, code),
}));

vi.mock("@components/ui/AgGridTable", () => ({
  AgGridTable: ({
    rowData,
    columnDefs,
    emptyMessage,
    loading,
    serverSideConfig,
    onSortChange,
  }: {
    rowData: Record<string, unknown>[];
    columnDefs: ColDef<Record<string, unknown>>[];
    emptyMessage?: string;
    loading?: boolean;
    serverSideConfig?: {
      totalCount: number;
      currentPage: number;
      pageSize: number;
      onPageChange: (page: number) => void;
      onPageSizeChange: (size: number) => void;
    };
    onSortChange?: (sort: { key: string; direction: string }[]) => void;
  }) => {
    const rows = Array.isArray(rowData) ? rowData : [];
    if (loading) {
      return <div data-testid="team-grid-loading">Loading</div>;
    }
    if (rows.length === 0) {
      return (
        <div data-testid="team-grid">
          <span data-testid="empty-message">{emptyMessage ?? "Empty"}</span>
          {serverSideConfig && (
            <>
              <button
                type="button"
                data-testid="page-change"
                onClick={() => serverSideConfig.onPageChange(2)}
              >
                Page 2
              </button>
              <button
                type="button"
                data-testid="page-size-change"
                onClick={() => serverSideConfig.onPageSizeChange(25)}
              >
                Size 25
              </button>
            </>
          )}
          {onSortChange && (
            <button
              type="button"
              data-testid="sort-change"
              onClick={() =>
                onSortChange([{ key: "revenue", direction: "desc" }])
              }
            >
              Sort
            </button>
          )}
        </div>
      );
    }
    return (
      <div data-testid="team-grid">
        {rows.map((row: Record<string, unknown>) => (
          <div key={String(row.id)} data-testid={`row-${row.id}`}>
            {String(row.name ?? "")}
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
              if (col.field && col.colId) {
                return (
                  <div
                    key={String(col.colId)}
                    data-testid={`cell-${col.colId}`}
                  >
                    {String(row[col.field] ?? "")}
                  </div>
                );
              }
              return null;
            })}
          </div>
        ))}
        {serverSideConfig && (
          <>
            <button
              type="button"
              data-testid="page-change"
              onClick={() => serverSideConfig.onPageChange(2)}
            >
              Page 2
            </button>
            <button
              type="button"
              data-testid="page-size-change"
              onClick={() => serverSideConfig.onPageSizeChange(25)}
            >
              Size 25
            </button>
          </>
        )}
        {onSortChange && (
          <button
            type="button"
            data-testid="sort-change"
            onClick={() =>
              onSortChange([{ key: "revenue", direction: "desc" }])
            }
          >
            Sort
          </button>
        )}
      </div>
    );
  },
}));

describe("TeamTab", () => {
  const mockQuery = vi.mocked(useGetSalesPerformanceSummaryQuery);
  const defaultProps = {
    selectedPeriod: "last-30-days" as const,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockCalculateDateRangeForPeriod.mockReturnValue({
      startDate: "2026-01-01",
      endDate: "2026-01-31",
    });
    mockQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      refetch: vi.fn(),
    } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
  });

  describe("rendering", () => {
    it("renders Sales Team Performance heading", () => {
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByText("tabs.team.title")).toBeInTheDocument();
    });

    it("displays loading state when isLoading", () => {
      mockQuery.mockReturnValue({
        data: undefined,
        isLoading: true,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("team-grid-loading")).toBeInTheDocument();
    });

    it("displays loading state during a background refetch, not just the initial isLoading", () => {
      mockQuery.mockReturnValue({
        data: undefined,
        isLoading: false,
        isFetching: true,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("team-grid-loading")).toBeInTheDocument();
    });
  });

  describe("data and query", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      const dateRange = { from: new Date(), to: new Date() };
      render(
        <TeamTab
          {...defaultProps}
          selectedPeriod="last-7-days"
          dateRange={dateRange}
        />,
      );
      expect(mockCalculateDateRangeForPeriod).toHaveBeenCalledWith(
        "last-7-days",
        dateRange,
      );
    });

    it("skips query when startDate or endDate is missing", () => {
      mockCalculateDateRangeForPeriod.mockReturnValue({
        startDate: "",
        endDate: "2026-01-31",
      });
      render(<TeamTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(expect.any(Object), {
        skip: true,
      });
    });

    it("calls API with showBy team", () => {
      render(<TeamTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ showBy: "team" }),
        expect.any(Object),
      );
    });

    it("scopes the query to the active company so switching companies busts the cache", () => {
      render(<TeamTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.any(Object),
      );
    });

    it("renders empty message when no content", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("empty-message")).toHaveTextContent(
        "tabs.noDataAvailable",
      );
    });

    it("maps content to table rows with id and rank", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "Team A",
                team: "Team A",
                share: 30,
                countCampaigns: 2,
                revenue: 1000,
                conversion: 25,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} currencyCode="USD" />);
      expect(screen.getByTestId("row-team-0-Team A")).toBeInTheDocument();
      expect(mockFormatCurrency).toHaveBeenCalledWith(1000, "USD");
    });

    it("uses empty string for id when team is undefined", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "NoTeam",
                team: undefined,
                share: 0,
                countCampaigns: 0,
                revenue: 0,
                conversion: 0,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("row-team-0-")).toBeInTheDocument();
    });

    it("computes rank from currentPage and pageSize", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "T1",
                team: "T1",
                share: 10,
                countCampaigns: 1,
                revenue: 100,
                conversion: 5,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      const rankCell = screen.getByTestId("cell-rank");
      expect(rankCell).toHaveTextContent("1");
    });
  });

  describe("pagination and sort", () => {
    it("calls onPageChange when page change is triggered", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      await user.click(screen.getByTestId("page-change"));
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1 }),
        expect.any(Object),
      );
    });

    it("calls onPageSizeChange and resets to page 1", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      await user.click(screen.getByTestId("page-size-change"));
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 25, page: 0 }),
        expect.any(Object),
      );
    });

    it("calls onSortChange and resets page", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      await user.click(screen.getByTestId("sort-change"));
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sortBy: "revenue",
          sortDir: "desc",
        }),
        expect.any(Object),
      );
    });
  });

  describe("cell renderers", () => {
    it("renders share and conversion as percentage", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "T1",
                team: "T1",
                share: 33.5,
                countCampaigns: 1,
                revenue: 100,
                conversion: 66.25,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("cell-share")).toHaveTextContent("33.50%");
      expect(screen.getByTestId("cell-conversion")).toHaveTextContent("66.25%");
    });

    it("handles undefined revenue in revenue cell", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "T1",
                team: "T1",
                share: 0,
                countCampaigns: 0,
                revenue: undefined,
                conversion: 0,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(mockFormatCurrency).toHaveBeenCalledWith(0, undefined);
    });

    it("handles undefined share and conversion in cell renderers", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "T2",
                team: "T2",
                share: undefined,
                countCampaigns: 0,
                revenue: 0,
                conversion: undefined,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<TeamTab {...defaultProps} />);
      expect(screen.getByTestId("cell-share")).toHaveTextContent("0.00%");
      expect(screen.getByTestId("cell-conversion")).toHaveTextContent("0.00%");
    });
  });
});
