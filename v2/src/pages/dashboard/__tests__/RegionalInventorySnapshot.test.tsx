import {
  useGetSalesPerformanceSummaryQuery,
  type SalesPerformanceSummaryItem,
} from "@services/dashboard/dashboardSlice";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import RegionalInventorySnapshot, {
  getUtilizationCategory,
  getData,
  getOptions,
  applyChartStyles,
} from "../RegionalInventorySnapshot";

const mockCountryContent: SalesPerformanceSummaryItem[] = [
  {
    country: "India",
    inventories: 485,
    utilization: 85,
    conversion: 2.5,
    countCampaigns: 23,
    cost: 100000,
    revenue: 3000000,
  },
  {
    country: "Singapore",
    inventories: 250,
    utilization: 65,
    conversion: 1.8,
    countCampaigns: 12,
    cost: 50000,
    revenue: 1500000,
  },
  {
    country: "United Kingdom",
    inventories: 320,
    utilization: 72,
    conversion: 2.1,
    countCampaigns: 18,
    cost: 80000,
    revenue: 2200000,
  },
  {
    country: "United States",
    inventories: 380,
    utilization: 65,
    conversion: 1.9,
    countCampaigns: 20,
    cost: 90000,
    revenue: 2800000,
  },
  {
    country: "Canada",
    inventories: 380,
    utilization: 80,
    conversion: 2.2,
    countCampaigns: 20,
    cost: 85000,
    revenue: 2800000,
  },
];

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("react-google-charts", () => ({
  Chart: ({
    data,
    options,
  }: {
    data: unknown[];
    options: Record<string, unknown>;
  }) => (
    <div data-testid="geo-chart">
      <span data-testid="chart-data-rows">
        {Array.isArray(data) ? data.length : 0}
      </span>
      <span data-testid="chart-height">{String(options?.height ?? "")}</span>
    </div>
  ),
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

describe("RegionalInventorySnapshot", () => {
  const defaultProps = {
    selectedPeriod: "last-7-days" as const,
  };

  beforeEach(() => {
    vi.mocked(useGetSalesPerformanceSummaryQuery).mockReturnValue({
      data: {
        data: {
          content: mockCountryContent,
        },
      },
      isLoading: false,
      isFetching: false,
      isError: false,
      isSuccess: true,
      refetch: vi.fn(),
    } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);
  });
  describe("getUtilizationCategory", () => {
    it("returns 3 (High) when utilization >= 80", () => {
      expect(getUtilizationCategory(80)).toBe(3);
      expect(getUtilizationCategory(85)).toBe(3);
      expect(getUtilizationCategory(100)).toBe(3);
    });

    it("returns 2 (Medium) when utilization >= 60 and < 80", () => {
      expect(getUtilizationCategory(60)).toBe(2);
      expect(getUtilizationCategory(65)).toBe(2);
      expect(getUtilizationCategory(79)).toBe(2);
    });

    it("returns 1 (Low) when utilization < 60", () => {
      expect(getUtilizationCategory(0)).toBe(1);
      expect(getUtilizationCategory(59)).toBe(1);
      expect(getUtilizationCategory(30)).toBe(1);
    });
  });

  describe("getData", () => {
    it("returns header row and data rows with Country, Utilization Category, and tooltip", () => {
      const data = getData(mockCountryContent);

      expect(Array.isArray(data)).toBe(true);
      expect(data[0]).toEqual([
        "Country",
        "Utilization Category",
        { role: "tooltip", type: "string", p: { html: true } },
      ]);

      const dataRows = data.slice(1) as [string, number, string][];
      expect(dataRows.length).toBe(5);

      expect(dataRows[0][0]).toBe("India");
      expect(dataRows[0][1]).toBe(3);
      expect(typeof dataRows[0][2]).toBe("string");
      expect(dataRows[0][2]).toContain("485");
      expect(dataRows[0][2]).toContain("Inventories");

      expect(dataRows[1][0]).toBe("Singapore");
      expect(dataRows[1][1]).toBe(2);

      expect(dataRows[4][0]).toBe("Canada");
      expect(dataRows[4][1]).toBe(3);
    });

    it("maps utilization to category in data rows", () => {
      const data = getData(mockCountryContent);
      const rows = data.slice(1) as [string, number, string][];

      const indiaRow = rows.find((r) => r[0] === "India");
      expect(indiaRow?.[1]).toBe(3);

      const singaporeRow = rows.find((r) => r[0] === "Singapore");
      expect(singaporeRow?.[1]).toBe(2);

      const ukRow = rows.find((r) => r[0] === "United Kingdom");
      expect(ukRow?.[1]).toBe(2);
    });

    it("returns only header row when content is undefined", () => {
      const data = getData(undefined);
      expect(data.length).toBe(1);
      expect(data[0]).toEqual([
        "Country",
        "Utilization Category",
        { role: "tooltip", type: "string", p: { html: true } },
      ]);
    });

    it("returns only header row when content is empty array", () => {
      const data = getData([]);
      expect(data.length).toBe(1);
    });

    it("filters out items without country", () => {
      const content = [
        ...mockCountryContent.slice(0, 1),
        { ...mockCountryContent[1], country: "" },
        { ...mockCountryContent[2], country: undefined },
      ] as SalesPerformanceSummaryItem[];
      const data = getData(content);
      const dataRows = data.slice(1);
      expect(dataRows.length).toBe(1);
      expect((dataRows[0] as [string, number, string])[0]).toBe("India");
    });
  });

  describe("getOptions", () => {
    it("returns options with height, colorAxis, and tooltip config", () => {
      const options = getOptions();

      expect(options.height).toBe(600);
      expect(options.keepAspectRatio).toBe(false);
      expect(options.colorAxis).toEqual({
        values: [1, 2, 3],
        colors: ["#72A876", "#FBC56D", "#C52828"],
        minValue: 1,
        maxValue: 3,
      });
      expect(options.displayMode).toBe("regions");
      expect(options.resolution).toBe("countries");
      expect(options.legend).toBe("none");
      expect(options.tooltip).toEqual({ isHtml: true });
      expect(options.backgroundColor).toBe("#ffff");
      expect(options.datalessRegionColor).toBe("white");
      expect(options.defaultColor).toBe("#E5E7EB");
      expect(options.borderColor).toBe("#000000");
    });
  });

  describe("applyChartStyles", () => {
    it("hides line and polyline elements in color scale", () => {
      const container = document.createElement("div");
      container.innerHTML = `
        <svg>
          <line data-testid="scale-line"/>
          <polyline data-testid="scale-polyline"/>
        </svg>
      `;

      applyChartStyles(container);

      const line = container.querySelector("line");
      const polyline = container.querySelector("polyline");
      expect(line?.style.display).toBe("none");
      expect(polyline?.style.display).toBe("none");
    });

    it("does not hide rect or polygon (only line/polyline get display none)", () => {
      const container = document.createElement("div");
      container.innerHTML = `
        <svg>
          <rect fill="url(#grad)"/>
          <polygon points="0,0 1,0 1,1"/>
        </svg>
      `;

      applyChartStyles(container);

      const rect = container.querySelector("rect");
      const polygon = container.querySelector("polygon");
      expect(rect?.style.display).not.toBe("none");
      expect(polygon?.style.display).not.toBe("none");
    });

    it("hides line when element has fill containing url", () => {
      const container = document.createElement("div");
      container.innerHTML = `<svg><line fill="url(#x)"/></svg>`;

      applyChartStyles(container);

      expect(container.querySelector("line")?.style.display).toBe("none");
    });

    it("applies stroke styles to path not in legend or scale", () => {
      const container = document.createElement("div");
      container.innerHTML = `<svg><path d="M0 0"/></svg>`;

      applyChartStyles(container);

      const path = container.querySelector("path");
      expect(path?.style.stroke).toBe("#717171");
      expect(path?.style.strokeWidth).toBe("0.62px");
      expect(path?.style.strokeLinejoin).toBe("round");
    });

    it("does not apply stroke to path inside legend", () => {
      const container = document.createElement("div");
      container.innerHTML = `
        <div class="chart-legend">
          <svg><path d="M0 0"/></path></svg>
        </div>
      `;

      applyChartStyles(container);

      const path = container.querySelector("path");
      expect(path?.style.stroke).toBe("");
    });

    it("does not apply stroke to path inside scale", () => {
      const container = document.createElement("div");
      container.innerHTML = `
        <div class="color-scale">
          <svg><path d="M0 0"/></svg>
        </div>
      `;

      applyChartStyles(container);

      const path = container.querySelector("path");
      expect(path?.style.stroke).toBe("");
    });

    it("hides line elements in color axis lines selector", () => {
      const container = document.createElement("div");
      const line = document.createElementNS(
        "http://www.w3.org/2000/svg",
        "line",
      );
      line.setAttribute("stroke-width", "1");
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.appendChild(line);
      container.appendChild(svg);

      applyChartStyles(container);

      expect(line.style.display).toBe("none");
    });

    it("does not hide g elements (only line elements in that selector)", () => {
      const container = document.createElement("div");
      const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
      g.setAttribute("aria-label", "color axis");
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.appendChild(g);
      container.appendChild(svg);

      applyChartStyles(container);

      expect(g.style.display).not.toBe("none");
    });

    it("handles empty container without throwing", () => {
      const container = document.createElement("div");

      expect(() => applyChartStyles(container)).not.toThrow();
    });
  });

  describe("component rendering", () => {
    it("renders card with title Regional Inventory Snapshot", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(screen.getByText("regionalInventory.title")).toBeInTheDocument();
    });

    it("renders legend with High, Medium, and Low labels", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(
        screen.getByText("regionalInventory.highLabel"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("regionalInventory.mediumLabel"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("regionalInventory.lowLabel"),
      ).toBeInTheDocument();
    });

    it("renders chart container with test id", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(
        screen.getByTestId("regional-chart-container"),
      ).toBeInTheDocument();
    });

    it("scopes the query to the active company so switching companies busts the cache", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(useGetSalesPerformanceSummaryQuery).toHaveBeenCalledWith(
        expect.objectContaining({ companyId: "company-1" }),
        expect.any(Object),
      );
    });

    it("renders GeoChart with API data and options", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      const chart = screen.getByTestId("geo-chart");
      expect(chart).toBeInTheDocument();

      const dataRowsEl = screen.getByTestId("chart-data-rows");
      expect(dataRowsEl.textContent).toBe("6");

      const heightEl = screen.getByTestId("chart-height");
      expect(heightEl.textContent).toBe("600");
    });

    it("shows loading state when isLoading is true", () => {
      vi.mocked(useGetSalesPerformanceSummaryQuery).mockReturnValue({
        data: undefined,
        isLoading: true,
        isFetching: true,
        isError: false,
        isSuccess: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);

      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(screen.getByTestId("regional-chart-loading")).toBeInTheDocument();
      expect(screen.getByText("regionalInventory.loading")).toBeInTheDocument();
      expect(screen.queryByTestId("geo-chart")).not.toBeInTheDocument();
    });

    it("shows a spinner next to the title during a background refetch, not the full loading swap", () => {
      vi.mocked(useGetSalesPerformanceSummaryQuery).mockReturnValue({
        data: { data: { content: mockCountryContent } },
        isLoading: false,
        isFetching: true,
        isError: false,
        isSuccess: true,
        refetch: vi.fn(),
      } as ReturnType<typeof useGetSalesPerformanceSummaryQuery>);

      render(<RegionalInventorySnapshot {...defaultProps} />);

      expect(screen.getByRole("status")).toBeInTheDocument();
      expect(
        screen.queryByTestId("regional-chart-loading"),
      ).not.toBeInTheDocument();
      expect(screen.getByTestId("geo-chart")).toBeInTheDocument();
    });

    it("does not show a spinner when no fetch is in flight", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    it("cleans up timeouts and observer on unmount", () => {
      const clearTimeoutSpy = vi.spyOn(global, "clearTimeout");
      const disconnectSpy = vi.fn();
      const OriginalObserver = global.MutationObserver;

      (
        global as unknown as { MutationObserver: typeof MutationObserver }
      ).MutationObserver = vi.fn().mockImplementation(() => ({
        observe: vi.fn(),
        disconnect: disconnectSpy,
        takeRecords: vi.fn(),
      })) as unknown as typeof MutationObserver;

      const { unmount } = render(
        <RegionalInventorySnapshot {...defaultProps} />,
      );
      unmount();

      expect(clearTimeoutSpy).toHaveBeenCalled();
      expect(disconnectSpy).toHaveBeenCalled();

      clearTimeoutSpy.mockRestore();
      (
        global as unknown as { MutationObserver: typeof MutationObserver }
      ).MutationObserver = OriginalObserver;
    });
  });

  describe("accessibility", () => {
    it("has accessible legend color labels", () => {
      render(<RegionalInventorySnapshot {...defaultProps} />);

      const highLabel = screen.getByText("regionalInventory.highLabel");
      const mediumLabel = screen.getByText("regionalInventory.mediumLabel");
      const lowLabel = screen.getByText("regionalInventory.lowLabel");

      expect(highLabel).toBeVisible();
      expect(mediumLabel).toBeVisible();
      expect(lowLabel).toBeVisible();
    });
  });
});
