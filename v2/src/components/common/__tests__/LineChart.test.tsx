import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import LineChart, { type LineChartProps } from "../LineChart";

let capturedOptions: unknown = null;

vi.mock("react-chartjs-2", () => ({
  Line: ({
    data,
    options,
    plugins,
  }: {
    data: unknown;
    options: unknown;
    plugins: unknown[];
  }) => {
    capturedOptions = options;
    return (
      <div data-testid="chartjs-line">
        <div data-testid="chart-data">{JSON.stringify(data)}</div>
        <div data-testid="chart-options">
          {JSON.stringify(options, (_key, value) =>
            typeof value === "function" ? "[Function]" : value,
          )}
        </div>
        <div data-testid="chart-plugins">{plugins.length}</div>
      </div>
    );
  },
}));

type GridColorFn = (ctx: { index: number }) => string;
type GridBorderDashFn = (ctx: { index: number }) => number[];

describe("LineChart", () => {
  const mockGetComputedStyle = vi.fn();
  const mockGetPropertyValue = vi.fn();

  beforeEach(() => {
    capturedOptions = null;
    mockGetPropertyValue.mockReturnValue("");
    mockGetComputedStyle.mockReturnValue({
      getPropertyValue: mockGetPropertyValue,
    });
    vi.stubGlobal("getComputedStyle", mockGetComputedStyle);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const defaultProps: LineChartProps = {
    labels: ["Jan", "Feb", "Mar"],
    datasets: [
      {
        label: "Revenue",
        data: [100, 200, 300],
      },
    ],
  };

  describe("Rendering", () => {
    it("renders chart with default props", () => {
      render(<LineChart {...defaultProps} />);
      expect(screen.getByTestId("chartjs-line")).toBeInTheDocument();
    });

    it("renders chart with title", () => {
      render(<LineChart {...defaultProps} title="Revenue Chart" />);
      expect(screen.getByText("Revenue Chart")).toBeInTheDocument();
    });

    it("does not render title when not provided", () => {
      const { container } = render(<LineChart {...defaultProps} />);
      expect(container.querySelector("h3")).not.toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <LineChart {...defaultProps} className="custom-chart" />,
      );
      expect(container.firstChild).toHaveClass("custom-chart");
    });

    it("applies custom height", () => {
      render(<LineChart {...defaultProps} height={500} />);
      const chartWrapper = screen.getByTestId("chartjs-line")
        .parentElement as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.height).toBe("500px");
    });

    it("uses default height when not provided", () => {
      render(<LineChart {...defaultProps} />);
      const chartWrapper = screen.getByTestId("chartjs-line")
        .parentElement as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.height).toBe("400px");
    });
  });

  describe("Data configuration", () => {
    it("passes labels to chart data", () => {
      render(<LineChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual(["Jan", "Feb", "Mar"]);
    });

    it("passes datasets to chart data", () => {
      render(<LineChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(1);
      expect(data.datasets[0].label).toBe("Revenue");
      expect(data.datasets[0].data).toEqual([100, 200, 300]);
    });

    it("handles multiple datasets", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Revenue", data: [100, 200, 300] },
          { label: "Costs", data: [50, 150, 250] },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(2);
    });

    it("applies custom borderColor from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            borderColor: "#ff0000",
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#ff0000");
    });

    it("applies custom backgroundColor from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            backgroundColor: "#00ff00",
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#00ff00");
    });

    it("applies custom pointRadius from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            pointRadius: 6,
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].pointRadius).toBe(6);
    });

    it("applies custom pointHoverRadius from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            pointHoverRadius: 8,
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].pointHoverRadius).toBe(8);
    });

    it("applies custom borderWidth from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            borderWidth: 3,
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderWidth).toBe(3);
    });

    it("applies custom tension from dataset", () => {
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Revenue",
            data: [100, 200, 300],
            tension: 0.5,
          },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].tension).toBe(0.5);
    });
  });

  describe("CSS variable color handling", () => {
    it("uses CSS variable value when available", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-error-500") return " #c52828 ";
        return "";
      });
      render(<LineChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#c52828");
    });

    it("falls back to default color when CSS variable is empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<LineChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#2176cc");
    });

    it("uses different color for second dataset", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-success-500") return "#2d7d32";
        return "";
      });
      const props: LineChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Revenue", data: [100, 200, 300] },
          { label: "Costs", data: [50, 150, 250] },
        ],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[1].borderColor).toBe("#2d7d32");
    });

    it("handles SSR scenario when getComputedStyle returns empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<LineChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#2176cc");
    });
  });

  describe("Current date vertical line", () => {
    it("includes vertical line plugin when currentDateIndex is provided", () => {
      render(<LineChart {...defaultProps} currentDateIndex={1} />);
      expect(screen.getByTestId("chart-plugins")).toHaveTextContent("1");
    });

    it("does not include vertical line plugin when currentDateIndex is undefined", () => {
      render(<LineChart {...defaultProps} />);
      expect(screen.getByTestId("chart-plugins")).toHaveTextContent("1");
    });

    it("does not include vertical line plugin when currentDateIndex is null", () => {
      render(
        <LineChart
          {...defaultProps}
          currentDateIndex={null as unknown as number | undefined}
        />,
      );
      expect(screen.getByTestId("chart-plugins")).toHaveTextContent("1");
    });
  });

  describe("Chart options", () => {
    it("configures responsive and maintainAspectRatio", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.responsive).toBe(true);
      expect(options.maintainAspectRatio).toBe(false);
    });

    it("shows legend when showLegend is true", () => {
      render(<LineChart {...defaultProps} showLegend={true} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(true);
    });

    it("hides legend when showLegend is false", () => {
      render(<LineChart {...defaultProps} showLegend={false} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("hides legend by default", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("configures legend position and style", () => {
      render(<LineChart {...defaultProps} showLegend={true} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.position).toBe("bottom");
      expect(options.plugins.legend.labels.usePointStyle).toBe(false);
      expect(options.plugins.legend.labels.boxWidth).toBe(20);
      expect(options.plugins.legend.labels.boxHeight).toBe(2);
    });

    it("configures tooltip", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.mode).toBe("index");
      expect(options.plugins.tooltip.intersect).toBe(false);
      expect(options.plugins.tooltip.backgroundColor).toBe("#FFFFFF");
    });

    it("uses custom formatTooltipValue when provided", () => {
      const formatTooltipValue = vi.fn((value, label) => `${label}: $${value}`);
      render(
        <LineChart {...defaultProps} formatTooltipValue={formatTooltipValue} />,
      );
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (c: {
                parsed: { y: number };
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: 100 },
        dataset: { label: "Revenue" },
      });
      expect(result).toBe("Revenue: $100");
    });

    it("uses custom formatTooltipDate when provided", () => {
      const formatTooltipDate = vi.fn((date) => `Date: ${date}`);
      render(
        <LineChart {...defaultProps} formatTooltipDate={formatTooltipDate} />,
      );
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: { title: (items: { label: string }[]) => string };
          };
        };
      };
      const tooltipTitleCallback = options.plugins.tooltip.callbacks.title;
      const result = tooltipTitleCallback([{ label: "Jan" }]);
      expect(result).toBe("Date: Jan");
    });

    it("uses default tooltip format when formatters not provided", () => {
      render(<LineChart {...defaultProps} />);
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (c: {
                parsed: { y: number };
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: 100 },
        dataset: { label: "Revenue" },
      });
      expect(result).toBe("Revenue: 100");
    });

    it("handles tooltip with empty items array", () => {
      render(<LineChart {...defaultProps} />);
      const options = capturedOptions as {
        plugins: {
          tooltip: { callbacks: { title: (items: unknown[]) => string } };
        };
      };
      const tooltipTitleCallback = options.plugins.tooltip.callbacks.title;
      const result = tooltipTitleCallback([]);
      expect(result).toBe("");
    });

    it("handles tooltip with null parsed value", () => {
      render(<LineChart {...defaultProps} />);
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (c: {
                parsed: { y: number | null };
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: null },
        dataset: { label: "Revenue" },
      });
      expect(result).toBe("Revenue: 0");
    });
  });

  describe("Data labels", () => {
    it("shows data labels when dataLabels.show is true", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: true }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(true);
    });

    it("hides data labels when dataLabels.show is false", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: false }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("hides data labels by default", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("uses custom dataLabels formatter when provided", () => {
      const formatter = vi.fn((value) => `$${value}`);
      render(
        <LineChart {...defaultProps} dataLabels={{ show: true, formatter }} />,
      );
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: number) => string } };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(100);
      expect(result).toBe("$100");
    });

    it("formats integer values without decimals", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: true }} />);
      const options = capturedOptions as {
        plugins: {
          datalabels: { formatter: (value: number) => string | number };
        };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(100);
      expect(result).toBe(100);
    });

    it("formats decimal values with 2 decimal places", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: true }} />);
      const options = capturedOptions as {
        plugins: {
          datalabels: { formatter: (value: number) => string | number };
        };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(100.123);
      expect(result).toBe("100.12");
    });

    it("handles NaN values in dataLabels formatter", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: true }} />);
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: number) => string } };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(NaN);
      expect(result).toBe("");
    });

    it("handles non-number values in dataLabels formatter", () => {
      render(<LineChart {...defaultProps} dataLabels={{ show: true }} />);
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: unknown) => string } };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter("not a number");
      expect(result).toBe("");
    });

    it("handles null formatter result", () => {
      const formatter = vi.fn(() => null);
      render(
        <LineChart {...defaultProps} dataLabels={{ show: true, formatter }} />,
      );
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: number) => string } };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(100);
      expect(result).toBe("");
    });
  });

  describe("Y-axis configuration", () => {
    it("configures y-axis with default beginAtZero", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.beginAtZero).toBe(true);
    });

    it("applies custom yAxisLabel", () => {
      render(<LineChart {...defaultProps} yAxisLabel="Amount ($)" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.title.display).toBe(true);
      expect(options.scales.y.title.text).toBe("Amount ($)");
    });

    it("does not show y-axis title when yAxisLabel not provided", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.title).toBeUndefined();
    });

    it("uses custom formatYAxisValue when provided", () => {
      const formatYAxisValue = vi.fn((value) => `$${value}`);
      render(
        <LineChart {...defaultProps} formatYAxisValue={formatYAxisValue} />,
      );
      const options = capturedOptions as {
        scales: { y: { ticks: { callback: (value: number) => string } } };
      };
      const tickCallback = options.scales.y.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe("$100");
    });

    it("uses default y-axis format when formatYAxisValue not provided", () => {
      render(<LineChart {...defaultProps} />);
      const options = capturedOptions as {
        scales: { y: { ticks: { callback: (value: number) => number } } };
      };
      const tickCallback = options.scales.y.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe(100);
    });

    it("configures y-axis with no grid lines", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.grid.display).toBe(false);
    });
  });

  describe("X-axis configuration", () => {
    it("configures x-axis grid with custom color function", () => {
      render(<LineChart {...defaultProps} currentDateIndex={1} />);
      const options = capturedOptions as {
        scales: { x: { grid: { color: GridColorFn } } };
      };
      const gridColor = options.scales.x.grid.color;
      expect(gridColor({ index: 0 })).toBe("#E2E8F0");
      expect(gridColor({ index: 1 })).toBe("transparent");
      expect(gridColor({ index: 2 })).toBe("#E2E8F0");
    });

    it("configures x-axis grid with custom borderDash function", () => {
      render(<LineChart {...defaultProps} currentDateIndex={1} />);
      const options = capturedOptions as {
        scales: { x: { grid: { borderDash: GridBorderDashFn } } };
      };
      const borderDash = options.scales.x.grid.borderDash;
      expect(borderDash({ index: 0 })).toEqual([5, 5]);
      expect(borderDash({ index: 1 })).toEqual([]);
      expect(borderDash({ index: 2 })).toEqual([5, 5]);
    });

    it("handles x-axis grid when currentDateIndex is undefined", () => {
      render(<LineChart {...defaultProps} />);
      const options = capturedOptions as {
        scales: { x: { grid: { color: GridColorFn } } };
      };
      const gridColor = options.scales.x.grid.color;
      expect(gridColor({ index: 1 })).toBe("#E2E8F0");
    });

    it("handles x-axis grid when currentDateIndex is null", () => {
      render(
        <LineChart
          {...defaultProps}
          currentDateIndex={null as unknown as number | undefined}
        />,
      );
      const options = capturedOptions as {
        scales: { x: { grid: { color: GridColorFn } } };
      };
      const gridColor = options.scales.x.grid.color;
      expect(gridColor({ index: 1 })).toBe("#E2E8F0");
    });
  });

  describe("Horizontal scrolling", () => {
    it("enables horizontal scroll when labels.length >= 30", () => {
      const props: LineChartProps = {
        ...defaultProps,
        labels: Array(30).fill("Label"),
      };
      const { container } = render(<LineChart {...props} />);
      const scrollContainer = container.querySelector(
        '[class*="overflow-x-auto"]',
      );
      expect(scrollContainer).toBeInTheDocument();
    });

    it("enables horizontal scroll when initialVisibleItems is provided", () => {
      const { container } = render(
        <LineChart {...defaultProps} initialVisibleItems={10} />,
      );
      const scrollContainer = container.querySelector(
        '[class*="overflow-x-auto"]',
      );
      expect(scrollContainer).toBeInTheDocument();
    });

    it("does not enable horizontal scroll when labels.length < 30 and initialVisibleItems not provided", () => {
      const { container } = render(<LineChart {...defaultProps} />);
      const scrollContainer = container.querySelector(
        '[class*="overflow-x-auto"]',
      );
      expect(scrollContainer).not.toBeInTheDocument();
    });

    it("calculates spacing correctly for many labels", () => {
      const props: LineChartProps = {
        ...defaultProps,
        labels: Array(30).fill("Label"),
      };
      render(<LineChart {...props} />);
      const chartWrapper = screen.getByTestId("chartjs-line")
        .parentElement as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.minWidth).toBe("2100px");
    });

    it("uses custom spacingPerItem when provided", () => {
      render(
        <LineChart
          {...defaultProps}
          spacingPerItem={100}
          initialVisibleItems={5}
        />,
      );
      const chartWrapper = screen.getByTestId("chartjs-line")
        .parentElement as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.minWidth).toBe("300px");
    });

    it("calculates containerMaxWidth from initialVisibleItems", () => {
      const { container } = render(
        <LineChart {...defaultProps} initialVisibleItems={5} />,
      );
      const scrollContainer = container.querySelector(
        '[class*="overflow-x-auto"]',
      );
      expect(scrollContainer).toBeInTheDocument();
    });
  });

  describe("Font family", () => {
    it("uses default font family Poppins", () => {
      render(<LineChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Poppins");
      expect(options.plugins.tooltip.titleFont.family).toBe("Poppins");
    });

    it("uses custom font family when provided", () => {
      render(<LineChart {...defaultProps} fontFamily="Arial" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Arial");
      expect(options.plugins.tooltip.titleFont.family).toBe("Arial");
    });
  });

  describe("Edge cases", () => {
    it("handles empty labels array", () => {
      const props: LineChartProps = {
        labels: [],
        datasets: [{ label: "Revenue", data: [] }],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual([]);
    });

    it("handles empty datasets array", () => {
      const props: LineChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toEqual([]);
    });

    it("handles dataset with empty data array", () => {
      const props: LineChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [{ label: "Revenue", data: [] }],
      };
      render(<LineChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].data).toEqual([]);
    });
  });
});
