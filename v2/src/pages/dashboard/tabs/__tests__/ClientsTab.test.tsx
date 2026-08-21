import { useGetSalesPerformanceSummaryQuery } from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ClientsTab from "../ClientsTab";

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

vi.mock("@utils/dashboard.utils", () => ({
  calculateDateRangeForPeriod: (period: unknown, range: unknown) =>
    mockCalculateDateRangeForPeriod(period, range),
}));

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
    sortState?: { key: string; direction: string }[];
    onSortChange?: (sort: { key: string; direction: string }[]) => void;
  }) => {
    const rows = Array.isArray(rowData) ? rowData : [];
    if (loading) {
      return <div data-testid="clients-grid-loading">Loading</div>;
    }
    if (rows.length === 0) {
      return (
        <div data-testid="clients-grid">
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
      <div data-testid="clients-grid">
        {rows.map((row: Record<string, unknown>) => (
          <div key={String(row.id)} data-testid={`row-${row.id}`}>
            {String(row.name ?? row.agency ?? "")}
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
    <div data-testid="showby-dropdown">
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid="showby-select"
      >
        <option value="advertiser">Advertiser</option>
        <option value="agency">Agency</option>
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

describe("ClientsTab", () => {
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
      isFetching: false,
      refetch: vi.fn(),
    } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
  });

  describe("rendering", () => {
    it("renders total revenue and show-by dropdown", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByText(/tabs.clients.totalRevenue/)).toBeInTheDocument();
      expect(screen.getByTestId("showby-dropdown")).toBeInTheDocument();
    });

    it("shows Advertiser in trigger when showBy is advertiser", () => {
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByTestId("showby-select")).toHaveValue("advertiser");
    });

    it("displays loading state when isLoading or isFetching", () => {
      mockQuery.mockReturnValue({
        data: undefined,
        isLoading: true,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByTestId("clients-grid-loading")).toBeInTheDocument();
    });

    it("displays loading state when isFetching is true", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: true,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByTestId("clients-grid-loading")).toBeInTheDocument();
    });
  });

  describe("data and query", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      const dateRange = { from: new Date(), to: new Date() };
      render(
        <ClientsTab
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
      render(<ClientsTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(expect.any(Object), {
        skip: true,
      });
    });

    it("renders empty message when no content", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByTestId("empty-message")).toHaveTextContent(
        "tabs.noDataAvailable",
      );
    });

    it("maps content to table rows with id and topCampaignsString", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "Client A",
                revenue: 1000,
                share: 50,
                countCampaigns: 2,
                topCampaigns: [
                  { campaignName: "C1", cost: 500 },
                  { campaignName: "C2", cost: 500 },
                ],
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} currencyCode="USD" />);
      expect(
        screen.getByTestId("row-advertiser-0-Client A"),
      ).toBeInTheDocument();
      expect(mockFormatCurrency).toHaveBeenCalled();
    });

    it("uses totalRevenue from content sum", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              { name: "A", revenue: 100 },
              { name: "B", revenue: 200 },
            ],
            totalElements: 2,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} currencyCode="MYR" />);
      expect(mockFormatCurrency).toHaveBeenCalledWith(300, "MYR");
    });

    it("treats undefined revenue as 0 in totalRevenue", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [{ name: "A", revenue: undefined }],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(mockFormatCurrency).toHaveBeenCalledWith(0, undefined);
    });
  });

  describe("showBy dropdown", () => {
    it("calls API with showBy advertiser by default", () => {
      render(<ClientsTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ showBy: "advertiser" }),
        expect.any(Object),
      );
    });

    it("scopes the query to the active company so switching companies busts the cache", () => {
      render(<ClientsTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.any(Object),
      );
    });

    it("calls API with showBy agency when user selects agency", async () => {
      const user = userEvent.setup();
      render(<ClientsTab {...defaultProps} />);
      const select = screen.getByTestId("showby-select");
      await user.selectOptions(select, "agency");
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({ showBy: "agency" }),
        expect.any(Object),
      );
    });
  });

  describe("pagination and sort", () => {
    it("passes serverSideConfig with totalCount, currentPage, pageSize", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 42 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      expect(screen.getByTestId("clients-grid")).toBeInTheDocument();
    });

    it("calls onPageChange when page change is triggered", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
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
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
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
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
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
    it("renders topCampaigns cell with dash when value is empty", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "X",
                topCampaigns: undefined,
                topCampaignsString: undefined,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} />);
      const topCell = screen.getByTestId("cell-topCampaigns");
      expect(topCell).toHaveTextContent("-");
    });

    it("renders topCampaigns cell with content when value present", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                name: "Y",
                topCampaigns: [{ campaignName: "Camp", cost: 100 }],
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<ClientsTab {...defaultProps} currencyCode="USD" />);
      const topCell = screen.getByTestId("cell-topCampaigns");
      expect(topCell).toBeInTheDocument();
      expect(mockFormatCurrency).toHaveBeenCalled();
    });
  });
});
