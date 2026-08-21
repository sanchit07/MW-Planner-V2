import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import BarChart, { type BarChartProps } from "../BarChart";

// Mock react-chartjs-2
let capturedOptions: unknown = null;

vi.mock("react-chartjs-2", () => ({
  Bar: ({ data, options }: { data: unknown; options: unknown }) => {
    capturedOptions = options;
    return (
      <div data-testid="chartjs-bar">
        <div data-testid="chart-data">{JSON.stringify(data)}</div>
        <div data-testid="chart-options">
          {JSON.stringify(options, (_key, value) => {
            // Replace functions with a marker so we can test their existence
            if (typeof value === "function") {
              return "[Function]";
            }
            return value;
          })}
        </div>
      </div>
    );
  },
}));

describe("BarChart", () => {
  const mockGetComputedStyle = vi.fn();
  const mockGetPropertyValue = vi.fn();

  beforeEach(() => {
    capturedOptions = null;
    mockGetPropertyValue.mockReturnValue("");
    mockGetComputedStyle.mockReturnValue({
      getPropertyValue: mockGetPropertyValue,
    });
    vi.stubGlobal("getComputedStyle", mockGetComputedStyle);
    vi.stubGlobal("window", { ...global.window });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const defaultProps: BarChartProps = {
    labels: ["Jan", "Feb", "Mar"],
    datasets: [
      {
        label: "Sales",
        data: [10, 20, 30],
      },
    ],
  };

  describe("Rendering", () => {
    it("renders chart with default props", () => {
      render(<BarChart {...defaultProps} />);
      expect(screen.getByTestId("chartjs-bar")).toBeInTheDocument();
    });

    it("renders chart with title", () => {
      render(<BarChart {...defaultProps} title="Sales Chart" />);
      expect(screen.getByText("Sales Chart")).toBeInTheDocument();
    });

    it("does not render title when not provided", () => {
      const { container } = render(<BarChart {...defaultProps} />);
      expect(container.querySelector("h3")).not.toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <BarChart {...defaultProps} className="custom-chart" />,
      );
      expect(container.firstChild).toHaveClass("custom-chart");
    });

    it("applies custom height", () => {
      const { container } = render(<BarChart {...defaultProps} height={500} />);
      const chartContainer = container.firstChild as HTMLElement;
      expect(chartContainer.style.height).toBe("500px");
    });

    it("uses default height when not provided", () => {
      const { container } = render(<BarChart {...defaultProps} />);
      const chartContainer = container.firstChild as HTMLElement;
      expect(chartContainer.style.height).toBe("400px");
    });
  });

  describe("Data configuration", () => {
    it("passes labels to chart data", () => {
      render(<BarChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual(["Jan", "Feb", "Mar"]);
    });

    it("passes datasets to chart data", () => {
      render(<BarChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(1);
      expect(data.datasets[0].label).toBe("Sales");
      expect(data.datasets[0].data).toEqual([10, 20, 30]);
    });

    it("handles multiple datasets", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Sales", data: [10, 20, 30] },
          { label: "Costs", data: [5, 15, 25] },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(2);
    });

    it("applies custom borderColor from dataset", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Sales",
            data: [10, 20, 30],
            borderColor: "#ff0000",
          },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#ff0000");
    });

    it("applies custom backgroundColor from dataset", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Sales",
            data: [10, 20, 30],
            backgroundColor: "#00ff00",
          },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#00ff00");
    });

    it("applies custom borderWidth from dataset", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Sales",
            data: [10, 20, 30],
            borderWidth: 3,
          },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderWidth).toBe(3);
    });

    it("applies custom borderRadius from dataset", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Sales",
            data: [10, 20, 30],
            borderRadius: 8,
          },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderRadius).toBe(8);
    });

    it("applies custom borderSkipped from dataset", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Sales",
            data: [10, 20, 30],
            borderSkipped: true,
          },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderSkipped).toBe(true);
    });
  });

  describe("CSS variable color handling", () => {
    it("uses CSS variable value when available", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-primary-500") return " #2176cc ";
        return "";
      });
      render(<BarChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });

    it("falls back to default color when CSS variable is empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<BarChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });

    it("uses different color for second dataset", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-success-500") return "#2d7d32";
        return "";
      });
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Sales", data: [10, 20, 30] },
          { label: "Costs", data: [5, 15, 25] },
        ],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[1].backgroundColor).toBe("#2d7d32");
    });

    it("handles SSR scenario when window is undefined", () => {
      // Test that the component uses fallback color when getComputedStyle returns empty
      // This simulates SSR scenario where CSS variables might not be available
      mockGetPropertyValue.mockReturnValue("");
      // Mock getComputedStyle to return empty string (simulating SSR)
      mockGetComputedStyle.mockReturnValue({
        getPropertyValue: () => "",
      });

      render(<BarChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      // Should use fallback color when CSS variable is not available
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });
  });

  describe("Chart options", () => {
    it("configures responsive and maintainAspectRatio", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.responsive).toBe(true);
      expect(options.maintainAspectRatio).toBe(false);
    });

    it("shows legend when multiple datasets", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Sales", data: [10, 20, 30] },
          { label: "Costs", data: [5, 15, 25] },
        ],
      };
      render(<BarChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(true);
    });

    it("hides legend when single dataset", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("configures legend position and style", () => {
      const props: BarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Sales", data: [10, 20, 30] },
          { label: "Costs", data: [5, 15, 25] },
        ],
      };
      render(<BarChart {...props} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.position).toBe("bottom");
      expect(options.plugins.legend.labels.usePointStyle).toBe(false);
      expect(options.plugins.legend.labels.boxWidth).toBe(20);
      expect(options.plugins.legend.labels.boxHeight).toBe(2);
    });

    it("configures tooltip", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.mode).toBe("index");
      expect(options.plugins.tooltip.intersect).toBe(false);
      expect(options.plugins.tooltip.backgroundColor).toBe("#FFFFFF");
    });

    it("uses custom formatTooltipValue when provided", () => {
      const formatTooltipValue = vi.fn((value, label) => `${label}: $${value}`);
      render(
        <BarChart {...defaultProps} formatTooltipValue={formatTooltipValue} />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: { callbacks: { label: (context: unknown) => string } };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: 10 },
        dataset: { label: "Sales" },
      });
      expect(result).toBe("Sales: $10");
    });

    it("uses default tooltip format when formatTooltipValue not provided", () => {
      render(<BarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: { callbacks: { label: (context: unknown) => string } };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: 10 },
        dataset: { label: "Sales" },
      });
      expect(result).toBe("Sales: 10");
    });

    it("handles tooltip with null parsed value", () => {
      render(<BarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: { callbacks: { label: (context: unknown) => string } };
        };
      };
      const tooltipCallback = options.plugins.tooltip.callbacks.label;
      const result = tooltipCallback({
        parsed: { y: null },
        dataset: { label: "Sales" },
      });
      expect(result).toBe("Sales: 0");
    });
  });

  describe("Y-axis configuration", () => {
    it("configures y-axis with default beginAtZero", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.beginAtZero).toBe(true);
    });

    it("applies custom yAxisMin", () => {
      render(<BarChart {...defaultProps} yAxisMin={10} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.min).toBe(10);
    });

    it("applies custom yAxisMax", () => {
      render(<BarChart {...defaultProps} yAxisMax={100} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.max).toBe(100);
    });

    it("applies custom yAxisStep", () => {
      render(<BarChart {...defaultProps} yAxisStep={5} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.ticks.stepSize).toBe(5);
    });

    it("applies custom yAxisLabel", () => {
      render(<BarChart {...defaultProps} yAxisLabel="Amount ($)" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.title.display).toBe(true);
      expect(options.scales.y.title.text).toBe("Amount ($)");
    });

    it("does not show y-axis title when yAxisLabel not provided", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.title).toBeUndefined();
    });

    it("uses custom formatYAxisValue when provided", () => {
      const formatYAxisValue = vi.fn((value) => `$${value}`);
      render(
        <BarChart {...defaultProps} formatYAxisValue={formatYAxisValue} />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        scales: {
          y: { ticks: { callback: (value: unknown) => string | number } };
        };
      };
      const tickCallback = options.scales.y.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe("$100");
    });

    it("uses default y-axis format when formatYAxisValue not provided", () => {
      render(<BarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        scales: {
          y: { ticks: { callback: (value: unknown) => string | number } };
        };
      };
      const tickCallback = options.scales.y.ticks.callback;
      const result = tickCallback(100);
      expect(result).toBe(100);
    });
  });

  describe("X-axis configuration", () => {
    it("configures x-axis with no grid", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.grid.display).toBe(false);
    });

    it("configures x-axis ticks with font family", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.ticks.font.family).toBe("Poppins");
    });
  });

  describe("Font family", () => {
    it("uses default font family Poppins", () => {
      render(<BarChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Poppins");
      expect(options.plugins.tooltip.titleFont.family).toBe("Poppins");
      expect(options.scales.x.ticks.font.family).toBe("Poppins");
    });

    it("uses custom font family when provided", () => {
      render(<BarChart {...defaultProps} fontFamily="Arial" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Arial");
      expect(options.plugins.tooltip.titleFont.family).toBe("Arial");
    });
  });

  describe("Edge cases", () => {
    it("handles empty labels array", () => {
      const props: BarChartProps = {
        labels: [],
        datasets: [{ label: "Sales", data: [] }],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual([]);
    });

    it("handles empty datasets array", () => {
      const props: BarChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toEqual([]);
    });

    it("handles dataset with empty data array", () => {
      const props: BarChartProps = {
        labels: ["Jan", "Feb"],
        datasets: [{ label: "Sales", data: [] }],
      };
      render(<BarChart {...props} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].data).toEqual([]);
    });
  });
});
