import { useGetSalesPerformanceSummaryQuery } from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ColDef, ICellRendererParams } from "ag-grid-community";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import RegionalTab from "../RegionalTab";

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
    sortState?: { key: string; direction: string }[];
    onSortChange?: (sort: { key: string; direction: string }[]) => void;
  }) => {
    const rows = Array.isArray(rowData) ? rowData : [];
    if (loading) {
      return <div data-testid="regional-grid-loading">Loading</div>;
    }
    if (rows.length === 0) {
      return (
        <div data-testid="regional-grid">
          <span data-testid="empty-message">{emptyMessage ?? "Empty"}</span>
          {serverSideConfig && (
            <button
              type="button"
              data-testid="page-size-change"
              onClick={() => serverSideConfig.onPageSizeChange(25)}
            >
              Size 25
            </button>
          )}
          {onSortChange && (
            <button
              type="button"
              data-testid="sort-change"
              onClick={() =>
                onSortChange([{ key: "utilization", direction: "asc" }])
              }
            >
              Sort
            </button>
          )}
        </div>
      );
    }
    return (
      <div data-testid="regional-grid">
        {rows.map((row: Record<string, unknown>) => (
          <div key={String(row.id)} data-testid={`row-${row.id}`}>
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
          <button
            type="button"
            data-testid="page-size-change"
            onClick={() => serverSideConfig.onPageSizeChange(25)}
          >
            Size 25
          </button>
        )}
        {onSortChange && (
          <button
            type="button"
            data-testid="sort-change"
            onClick={() =>
              onSortChange([{ key: "utilization", direction: "asc" }])
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
        <option value="country">Country</option>
        <option value="city">City</option>
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

vi.mock("@components/ui/Progressbar", () => ({
  Progress: ({ value, label }: { value: number; label: string }) => (
    <div data-testid="progress" data-value={value}>
      {label}
    </div>
  ),
}));

describe("RegionalTab", () => {
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
    it("renders count and show-by dropdown", () => {
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      expect(screen.getByText("0 tabs.regional.countries")).toBeInTheDocument();
      expect(screen.getByTestId("showby-dropdown")).toBeInTheDocument();
    });

    it("shows Countries when showBy is country", () => {
      render(<RegionalTab {...defaultProps} />);
      expect(screen.getByText(/tabs.regional.countries/)).toBeInTheDocument();
    });

    it("shows Cities when showBy is city", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      await user.selectOptions(screen.getByTestId("showby-select"), "city");
      expect(screen.getByText("0 tabs.regional.cities")).toBeInTheDocument();
    });

    it("displays loading state when isLoading or isFetching", () => {
      mockQuery.mockReturnValue({
        data: undefined,
        isLoading: true,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      expect(screen.getByTestId("regional-grid-loading")).toBeInTheDocument();
    });
  });

  describe("data and query", () => {
    it("calls calculateDateRangeForPeriod with selectedPeriod and dateRange", () => {
      const dateRange = { from: new Date(), to: new Date() };
      render(
        <RegionalTab
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
      render(<RegionalTab {...defaultProps} />);
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
      render(<RegionalTab {...defaultProps} />);
      expect(screen.getByTestId("empty-message")).toHaveTextContent(
        "tabs.noDataAvailable",
      );
    });

    it("maps content to table rows with id from country or city", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [{ country: "MY", city: "Kuala Lumpur", utilization: 50 }],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      expect(screen.getByTestId("row-country-0-MY")).toBeInTheDocument();
    });
  });

  describe("showBy dropdown", () => {
    it("calls API with showBy country by default", () => {
      render(<RegionalTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ showBy: "country" }),
        expect.any(Object),
      );
    });

    it("scopes the query to the active company so switching companies busts the cache", () => {
      render(<RegionalTab {...defaultProps} />);
      expect(mockQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.any(Object),
      );
    });

    it("calls API with showBy city when user selects city", async () => {
      const user = userEvent.setup();
      render(<RegionalTab {...defaultProps} />);
      await user.selectOptions(screen.getByTestId("showby-select"), "city");
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({ showBy: "city" }),
        expect.any(Object),
      );
    });
  });

  describe("columns", () => {
    it("includes city column when showBy is city", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [{ city: "KL", country: "MY", utilization: 60 }],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      await user.selectOptions(screen.getByTestId("showby-select"), "city");
      expect(screen.getByTestId("row-city-0-MY")).toBeInTheDocument();
    });

    it("renders conversion cell with error style when percentage > 80", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                country: "MY",
                conversion: 85,
                utilization: 50,
                countCampaigns: 1,
                cost: 100,
                revenue: 200,
                inventories: 10,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      const conversionCell = screen.getByTestId("cell-conversion");
      expect(conversionCell).toHaveTextContent("85.00%");
      expect(
        conversionCell.querySelector(".text-mw-error-500"),
      ).toBeInTheDocument();
    });

    it("renders conversion cell with warning style when percentage 60-80", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                country: "MY",
                conversion: 70,
                utilization: 50,
                countCampaigns: 1,
                cost: 100,
                revenue: 200,
                inventories: 10,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      const conversionCell = screen.getByTestId("cell-conversion");
      expect(conversionCell).toHaveTextContent("70.00%");
      expect(
        conversionCell.querySelector(".text-mw-warning-500"),
      ).toBeInTheDocument();
    });

    it("renders conversion cell with success style when percentage < 60", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                country: "MY",
                conversion: 50,
                utilization: 40,
                countCampaigns: 1,
                cost: 100,
                revenue: 200,
                inventories: 10,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
      const conversionCell = screen.getByTestId("cell-conversion");
      expect(conversionCell).toHaveTextContent("50.00%");
      expect(
        conversionCell.querySelector(".text-mw-success-500"),
      ).toBeInTheDocument();
    });

    it("uses currencyCode in cost and revenue cells", () => {
      mockQuery.mockReturnValue({
        data: {
          data: {
            content: [
              {
                country: "MY",
                cost: 100,
                revenue: 200,
                utilization: 50,
                countCampaigns: 1,
                inventories: 10,
              },
            ],
            totalElements: 1,
          },
        },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} currencyCode="USD" />);
      expect(mockFormatCurrency).toHaveBeenCalledWith(100, "USD");
      expect(mockFormatCurrency).toHaveBeenCalledWith(200, "USD");
    });
  });

  describe("pagination and sort", () => {
    it("calls onPageSizeChange and resets to page 1", async () => {
      const user = userEvent.setup();
      mockQuery.mockReturnValue({
        data: { data: { content: [], totalElements: 0 } },
        isLoading: false,
        isFetching: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
      render(<RegionalTab {...defaultProps} />);
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
      render(<RegionalTab {...defaultProps} />);
      await user.click(screen.getByTestId("sort-change"));
      expect(mockQuery).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sortBy: "utilization",
          sortDir: "asc",
        }),
        expect.any(Object),
      );
    });
  });
});
