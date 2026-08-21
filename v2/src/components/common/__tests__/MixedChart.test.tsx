import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import MixedChart, {
  type MixedChartDataset,
  type MixedChartProps,
} from "../MixedChart";

let capturedOptions: unknown = null;

vi.mock("react-chartjs-2", () => ({
  Chart: ({
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
      <div data-testid="chartjs-mixed">
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

describe("MixedChart", () => {
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

  const defaultProps: MixedChartProps = {
    labels: ["Jan", "Feb", "Mar"],
    datasets: [
      {
        label: "Bars",
        data: [10, 20, 30],
        type: "bar",
      },
      {
        label: "Line",
        data: [5, 15, 25],
        type: "line",
      },
    ],
  };

  describe("Rendering", () => {
    it("renders chart with default props", () => {
      render(<MixedChart {...defaultProps} />);
      expect(screen.getByTestId("chartjs-mixed")).toBeInTheDocument();
    });

    it("renders chart with title", () => {
      render(<MixedChart {...defaultProps} title="Mixed Chart" />);
      expect(screen.getByText("Mixed Chart")).toBeInTheDocument();
    });

    it("does not render title when not provided", () => {
      const { container } = render(<MixedChart {...defaultProps} />);
      expect(container.querySelector("h3")).not.toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <MixedChart {...defaultProps} className="custom-chart" />,
      );
      expect(container.firstChild).toHaveClass("custom-chart");
    });

    it("applies custom height", () => {
      const { container } = render(
        <MixedChart {...defaultProps} height={500} />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper).toBeTruthy();
      expect(wrapper.style.height).toBe("500px");
    });

    it("uses default height when not provided", () => {
      const { container } = render(<MixedChart {...defaultProps} />);
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper).toBeTruthy();
      expect(wrapper.style.height).toBe("400px");
    });
  });

  describe("Data configuration", () => {
    it("passes labels to chart data", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual(["Jan", "Feb", "Mar"]);
    });

    it("passes datasets to chart data", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(2);
      expect(data.datasets[0].type).toBe("bar");
      expect(data.datasets[1].type).toBe("line");
    });

    it("configures bar dataset correctly", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const barDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "bar",
      );
      expect(barDataset).toBeDefined();
      expect(barDataset.data).toEqual([10, 20, 30]);
      expect(barDataset.order).toBe(1);
    });

    it("configures line dataset correctly", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const lineDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "line",
      );
      expect(lineDataset).toBeDefined();
      expect(lineDataset.data).toEqual([5, 15, 25]);
      expect(lineDataset.order).toBe(0);
    });

    it("uses custom order from dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            order: 5,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].order).toBe(5);
    });

    it("uses custom yAxisID from dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].yAxisID).toBe("y1");
    });

    it("uses default yAxisID when not provided", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].yAxisID).toBe("y");
    });
  });

  describe("Bar dataset styling", () => {
    it("applies custom borderColor to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            borderColor: "#ff0000",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#ff0000");
    });

    it("applies custom backgroundColor to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            backgroundColor: "#00ff00",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#00ff00");
    });

    it("applies custom borderWidth to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            borderWidth: 3,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderWidth).toBe(3);
    });

    it("applies custom borderRadius to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            borderRadius: 8,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderRadius).toBe(8);
    });

    it("applies custom maxBarThickness to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            maxBarThickness: 50,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].maxBarThickness).toBe(50);
    });

    it("applies custom barPercentage to bar dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            barPercentage: 0.7,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].barPercentage).toBe(0.7);
    });
  });

  describe("Line dataset styling", () => {
    it("applies custom borderColor to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            borderColor: "#ff0000",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#ff0000");
    });

    it("applies custom backgroundColor to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            backgroundColor: "#00ff00",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#00ff00");
    });

    it("applies custom borderWidth to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            borderWidth: 3,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderWidth).toBe(3);
    });

    it("applies custom pointRadius to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            pointRadius: 6,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].pointRadius).toBe(6);
    });

    it("applies custom pointHoverRadius to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            pointHoverRadius: 8,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].pointHoverRadius).toBe(8);
    });

    it("applies custom tension to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            tension: 0.5,
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].tension).toBe(0.5);
    });

    it("applies custom pointStyle to line dataset", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            pointStyle: "circle",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].pointStyle).toBe("circle");
    });

    it("sets fill to false for line dataset", () => {
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const lineDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "line",
      );
      expect(lineDataset.fill).toBe(false);
    });
  });

  describe("CSS variable color handling", () => {
    it("uses CSS variable value when available", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-primary-500") return " #2176cc ";
        return "";
      });
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const barDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "bar",
      );
      expect(barDataset.backgroundColor).toBe("#2176cc");
    });

    it("uses direct color value when not a CSS variable", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars",
            data: [10, 20, 30],
            type: "bar",
            backgroundColor: "#ff0000",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#ff0000");
    });

    it("falls back to default color when CSS variable is empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const barDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "bar",
      );
      expect(barDataset.backgroundColor).toBe("#2176cc");
    });

    it("uses different color for second bar dataset", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-secondary-400") return "#87CAED";
        return "";
      });
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Bars 1",
            data: [10, 20, 30],
            type: "bar",
          },
          {
            label: "Bars 2",
            data: [5, 15, 25],
            type: "bar",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const barDatasets = data.datasets.filter(
        (d: MixedChartDataset) => d.type === "bar",
      );
      expect(barDatasets[1].backgroundColor).toBe("#87CAED");
    });

    it("handles SSR scenario when getComputedStyle returns empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<MixedChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      const barDataset = data.datasets.find(
        (d: MixedChartDataset) => d.type === "bar",
      );
      expect(barDataset.backgroundColor).toBe("#2176cc");
    });
  });

  describe("Dual y-axes", () => {
    it("configures y1 axis when dataset uses yAxisID y1", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
      };
      render(<MixedChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y1).toBeDefined();
      expect(options.scales.y1.position).toBe("right");
    });

    it("does not configure y1 axis when no dataset uses yAxisID y1", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y1).toBeUndefined();
    });

    it("configures y1 axis with custom label", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
        y1AxisLabel: "Right Axis",
      };
      render(<MixedChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y1.title.text).toBe("Right Axis");
    });

    it("configures y1 axis with custom min/max", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
        y1AxisMin: 0,
        y1AxisMax: 100,
      };
      render(<MixedChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y1.min).toBe(0);
      expect(options.scales.y1.max).toBe(100);
    });

    it("configures y1 axis grid drawOnChartArea", () => {
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
        y1AxisGridDrawOnChartArea: true,
      };
      render(<MixedChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y1.grid.drawOnChartArea).toBe(true);
    });
  });

  describe("Legend configuration", () => {
    it("shows legend by default", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(true);
    });

    it("hides legend when showLegend is false", () => {
      render(<MixedChart {...defaultProps} showLegend={false} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("hides legend when legend.show is false", () => {
      render(<MixedChart {...defaultProps} legend={{ show: false }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("uses showLegend over legend.show when both provided", () => {
      render(
        <MixedChart
          {...defaultProps}
          showLegend={true}
          legend={{ show: false }}
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(true);
    });

    it("configures legend position", () => {
      render(<MixedChart {...defaultProps} legend={{ position: "top" }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.position).toBe("top");
    });

    it("uses legendPosition over legend.position when both provided", () => {
      render(
        <MixedChart
          {...defaultProps}
          legendPosition="left"
          legend={{ position: "top" }}
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.position).toBe("left");
    });

    it("configures legend align", () => {
      render(<MixedChart {...defaultProps} legend={{ align: "start" }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.align).toBe("start");
    });

    it("configures legend fullWidth", () => {
      render(<MixedChart {...defaultProps} legend={{ fullWidth: true }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.fullWidth).toBe(true);
    });
  });

  describe("Tooltip configuration", () => {
    it("shows tooltip by default", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(true);
    });

    it("hides tooltip when tooltip.show is false", () => {
      render(<MixedChart {...defaultProps} tooltip={{ show: false }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(false);
    });

    it("configures tooltip mode", () => {
      render(<MixedChart {...defaultProps} tooltip={{ mode: "nearest" }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.mode).toBe("nearest");
    });

    it("uses custom formatTooltipValue when provided", () => {
      const formatTooltipValue = vi.fn((value, label) => `${label}: $${value}`);
      render(
        <MixedChart
          {...defaultProps}
          formatTooltipValue={formatTooltipValue}
        />,
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
        dataset: { label: "Bars" },
      });
      expect(result).toBe("Bars: $100");
    });

    it("uses tooltip.formatValue over formatTooltipValue when both provided", () => {
      const formatTooltipValue = vi.fn((value, label) => `${label}: $${value}`);
      const formatValue = vi.fn((value, label) => `${label}: €${value}`);
      render(
        <MixedChart
          {...defaultProps}
          formatTooltipValue={formatTooltipValue}
          tooltip={{ formatValue }}
        />,
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
        dataset: { label: "Bars" },
      });
      expect(result).toBe("Bars: €100");
    });

    it("uses custom formatTooltipTitle when provided", () => {
      const formatTitle = vi.fn(
        (items: { label: string }[]) => `Title: ${items[0]?.label}`,
      );
      render(<MixedChart {...defaultProps} tooltip={{ formatTitle }} />);
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: { title: (items: { label: string }[]) => string };
          };
        };
      };
      const tooltipTitleCallback = options.plugins.tooltip.callbacks.title;
      const result = tooltipTitleCallback([{ label: "Jan" }]);
      expect(result).toBe("Title: Jan");
    });
  });

  describe("Y-axis configuration", () => {
    it("configures y-axis with default beginAtZero", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.beginAtZero).toBe(true);
    });

    it("configures y-axis beginAtZero from prop", () => {
      render(<MixedChart {...defaultProps} yAxisBeginAtZero={false} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.beginAtZero).toBe(false);
    });

    it("applies custom yAxisMin", () => {
      render(<MixedChart {...defaultProps} yAxisMin={10} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.min).toBe(10);
    });

    it("applies custom yAxisMax", () => {
      render(<MixedChart {...defaultProps} yAxisMax={100} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.max).toBe(100);
    });

    it("applies custom yAxisLabel", () => {
      render(<MixedChart {...defaultProps} yAxisLabel="Amount" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.title.text).toBe("Amount");
    });

    it("uses custom formatYAxisValue when provided", () => {
      const formatYAxisValue = vi.fn((value) => `$${value}`);
      render(
        <MixedChart {...defaultProps} formatYAxisValue={formatYAxisValue} />,
      );
      const options = capturedOptions as {
        scales: { y: { ticks: { callback: (value: number) => string } } };
      };
      const tickCallback = options.scales.y.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe("$100");
    });

    it("uses custom formatY1AxisValue when provided", () => {
      const formatY1AxisValue = vi.fn((value) => `€${value}`);
      const props: MixedChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Line",
            data: [5, 15, 25],
            type: "line",
            yAxisID: "y1",
          },
        ],
        formatY1AxisValue,
      };
      render(<MixedChart {...props} />);
      const options = capturedOptions as {
        scales: { y1: { ticks: { callback: (value: number) => string } } };
      };
      const tickCallback = options.scales.y1.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe("€100");
    });
  });

  describe("X-axis configuration", () => {
    it("configures x-axis display", () => {
      render(<MixedChart {...defaultProps} xAxis={{ display: false }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.display).toBe(false);
    });

    it("configures x-axis grid display", () => {
      render(<MixedChart {...defaultProps} xAxis={{ gridDisplay: true }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.grid.display).toBe(true);
    });
  });

  describe("Data labels", () => {
    it("shows data labels when dataLabels.show is true", () => {
      render(<MixedChart {...defaultProps} dataLabels={{ show: true }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(true);
    });

    it("hides data labels when dataLabels.show is false", () => {
      render(<MixedChart {...defaultProps} dataLabels={{ show: false }} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("hides data labels by default", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("uses custom dataLabels formatter when provided", () => {
      const formatter = vi.fn((value) => `$${value}`);
      render(
        <MixedChart {...defaultProps} dataLabels={{ show: true, formatter }} />,
      );
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: number) => string } };
      };
      const dataLabelFormatter = options.plugins.datalabels.formatter;
      const result = dataLabelFormatter(100);
      expect(result).toBe("$100");
    });

    it("filters data labels by displayFor", () => {
      render(
        <MixedChart
          {...defaultProps}
          dataLabels={{ show: true, displayFor: "bar" }}
        />,
      );
      const options = capturedOptions as {
        plugins: {
          datalabels: { filter: (ctx: { datasetIndex: number }) => boolean };
        };
      };
      const filter = options.plugins.datalabels.filter;
      expect(filter({ datasetIndex: 0 })).toBe(true);
      expect(filter({ datasetIndex: 1 })).toBe(false);
    });
  });

  describe("Interaction mode", () => {
    it("configures interaction mode", () => {
      render(<MixedChart {...defaultProps} interactionMode="nearest" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.interaction.mode).toBe("nearest");
    });

    it("configures interaction intersect", () => {
      render(<MixedChart {...defaultProps} interactionIntersect={true} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.interaction.intersect).toBe(true);
    });
  });

  describe("Custom chart options", () => {
    it("merges customChartOptions with default options", () => {
      const customOptions = {
        plugins: {
          legend: {
            display: false,
          },
        },
      };
      render(
        <MixedChart {...defaultProps} customChartOptions={customOptions} />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("deep merges nested customChartOptions", () => {
      const customOptions = {
        plugins: {
          tooltip: {
            backgroundColor: "#000000",
          },
        },
      };
      render(
        <MixedChart {...defaultProps} customChartOptions={customOptions} />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.backgroundColor).toBe("#000000");
      expect(options.plugins.tooltip.enabled).toBe(true); // Should still be true from defaults
    });
  });

  describe("Font family", () => {
    it("uses default font family Poppins", () => {
      render(<MixedChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Poppins");
      expect(options.plugins.tooltip.titleFont.family).toBe("Poppins");
    });

    it("uses custom font family when provided", () => {
      render(<MixedChart {...defaultProps} fontFamily="Arial" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Arial");
      expect(options.plugins.tooltip.titleFont.family).toBe("Arial");
    });
  });

  describe("Edge cases", () => {
    it("handles empty labels array", () => {
      const props: MixedChartProps = {
        labels: [],
        datasets: [{ label: "Bars", data: [], type: "bar" }],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual([]);
    });

    it("handles empty datasets array", () => {
      const props: MixedChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toEqual([]);
    });

    it("handles dataset with empty data array", () => {
      const props: MixedChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [{ label: "Bars", data: [], type: "bar" }],
      };
      render(<MixedChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].data).toEqual([]);
    });
  });
});
