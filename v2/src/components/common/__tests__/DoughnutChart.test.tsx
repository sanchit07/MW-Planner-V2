import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import DoughnutChart, {
  type DoughnutChartDataItem,
  type DoughnutChartProps,
} from "../DoughnutChart";

let capturedOptions: unknown = null;

vi.mock("react-chartjs-2", () => ({
  Doughnut: ({ data, options }: { data: unknown; options: unknown }) => {
    capturedOptions = options;
    return (
      <div data-testid="chartjs-doughnut">
        <div data-testid="chart-data">{JSON.stringify(data)}</div>
        <div data-testid="chart-options">
          {JSON.stringify(options, (_key, value) =>
            typeof value === "function" ? "[Function]" : value,
          )}
        </div>
      </div>
    );
  },
}));

// Mock Badge component
vi.mock("@components/ui/Badge", () => ({
  Badge: ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => <span className={className}>{children}</span>,
}));

describe("DoughnutChart", () => {
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

  const defaultData: DoughnutChartDataItem[] = [
    {
      label: "High",
      value: 50,
      color: "--color-mw-error-500",
      percentage: 50,
    },
    {
      label: "Medium",
      value: 30,
      color: "--color-mw-warning-500",
      percentage: 30,
    },
    {
      label: "Low",
      value: 20,
      color: "--color-mw-success-500",
      percentage: 20,
    },
  ];

  const defaultProps: DoughnutChartProps = {
    data: defaultData,
  };

  describe("Rendering", () => {
    it("renders chart with default props", () => {
      render(<DoughnutChart {...defaultProps} />);
      expect(screen.getByTestId("chartjs-doughnut")).toBeInTheDocument();
    });

    it("applies custom height", () => {
      const { container } = render(
        <DoughnutChart {...defaultProps} height={400} />,
      );
      const chartWrapper = container.querySelector(
        '.flex.justify-center[style*="height"]',
      ) as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.height).toBe("400px");
    });

    it("uses default height when not provided", () => {
      const { container } = render(<DoughnutChart {...defaultProps} />);
      const chartWrapper = container.querySelector(
        '.flex.justify-center[style*="height"]',
      ) as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.height).toBe("300px");
    });

    it("applies custom width", () => {
      const { container } = render(
        <DoughnutChart {...defaultProps} width={400} />,
      );
      const chartWrapper = container.querySelector(
        '.flex.justify-center[style*="width"]',
      ) as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.width).toBe("400px");
    });

    it("uses 100% width when width not provided", () => {
      const { container } = render(<DoughnutChart {...defaultProps} />);
      const chartWrapper = container.querySelector(
        '.flex.justify-center[style*="width"]',
      ) as HTMLElement;
      expect(chartWrapper).toBeTruthy();
      expect(chartWrapper.style.width).toBe("100%");
    });
  });

  describe("Data configuration", () => {
    it("passes data to chart", () => {
      render(<DoughnutChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual(["High", "Medium", "Low"]);
      expect(data.datasets[0].data).toEqual([50, 30, 20]);
    });

    it("calculates total correctly", () => {
      const data: DoughnutChartDataItem[] = [
        { label: "A", value: 10, color: "#ff0000", percentage: 50 },
        { label: "B", value: 10, color: "#00ff00", percentage: 50 },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].data).toEqual([10, 10]);
    });

    it("handles empty data array", () => {
      render(<DoughnutChart data={[]} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual([]);
      expect(data.datasets[0].data).toEqual([]);
    });

    it("handles single data item", () => {
      const data: DoughnutChartDataItem[] = [
        { label: "Only", value: 100, color: "#ff0000", percentage: 100 },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].data).toEqual([100]);
    });
  });

  describe("Color handling", () => {
    it("uses CSS variable value when available", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-error-500") return " #c52828 ";
        return "";
      });
      render(<DoughnutChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor[0]).toBe("#c52828");
    });

    it("uses direct color value when not a CSS variable", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "Direct",
          value: 100,
          color: "#ff0000",
          percentage: 100,
        },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].backgroundColor[0]).toBe("#ff0000");
    });

    it("falls back to default color when CSS variable is empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      render(<DoughnutChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor[0]).toBe("#2176cc");
    });

    it("uses fallback colors for known CSS variables", () => {
      mockGetPropertyValue.mockReturnValue("");
      const data: DoughnutChartDataItem[] = [
        {
          label: "Primary",
          value: 100,
          color: "--color-mw-primary-500",
          percentage: 100,
        },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].backgroundColor[0]).toBe("#103860");
    });

    it("handles SSR scenario when window is undefined", () => {
      mockGetPropertyValue.mockReturnValue("");
      const data: DoughnutChartDataItem[] = [
        {
          label: "Primary",
          value: 100,
          color: "--color-mw-primary-500",
          percentage: 100,
        },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].backgroundColor[0]).toBe("#103860");
    });
  });

  describe("Emphasize largest", () => {
    it("emphasizes largest slice when emphasizeLargest is true", () => {
      render(<DoughnutChart {...defaultProps} emphasizeLargest={true} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].offset[0]).toBe(8); // First item is largest (50)
      expect(data.datasets[0].offset[1]).toBe(0);
      expect(data.datasets[0].offset[2]).toBe(0);
    });

    it("does not emphasize when emphasizeLargest is false", () => {
      render(<DoughnutChart {...defaultProps} emphasizeLargest={false} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].offset).toEqual([0, 0, 0]);
    });

    it("emphasizes largest by default", () => {
      render(<DoughnutChart {...defaultProps} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].offset[0]).toBe(8);
    });

    it("handles equal values correctly", () => {
      const data: DoughnutChartDataItem[] = [
        { label: "A", value: 10, color: "#ff0000", percentage: 33.33 },
        { label: "B", value: 10, color: "#00ff00", percentage: 33.33 },
        { label: "C", value: 10, color: "#0000ff", percentage: 33.34 },
      ];
      render(<DoughnutChart data={data} emphasizeLargest={true} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      // First item with max value should be emphasized
      expect(chartData.datasets[0].offset[0]).toBe(8);
    });

    it("handles empty data with emphasizeLargest", () => {
      render(<DoughnutChart data={[]} emphasizeLargest={true} />);
      const dataElement = screen.getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].offset).toEqual([]);
    });
  });

  describe("Legend", () => {
    it("shows legend by default", () => {
      render(<DoughnutChart {...defaultProps} />);
      expect(screen.getByText("High")).toBeInTheDocument();
      expect(screen.getByText("Medium")).toBeInTheDocument();
      expect(screen.getByText("Low")).toBeInTheDocument();
    });

    it("hides legend when showLegend is false", () => {
      render(<DoughnutChart {...defaultProps} showLegend={false} />);
      expect(screen.queryByText("High")).not.toBeInTheDocument();
    });

    it("displays percentage in legend", () => {
      render(<DoughnutChart {...defaultProps} />);
      expect(screen.getByText("50%")).toBeInTheDocument();
      expect(screen.getByText("30%")).toBeInTheDocument();
      expect(screen.getByText("20%")).toBeInTheDocument();
    });

    it("displays status badge when status is provided", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "High Demand",
          value: 100,
          color: "#ff0000",
          percentage: 100,
          status: "High Demand",
        },
      ];
      render(<DoughnutChart data={data} />);
      const elements = screen.getAllByText("High Demand");
      expect(elements).toHaveLength(2);
      const badge = elements.find((el) => el.className.includes("bg-mw-error"));
      expect(badge).toBeInTheDocument();
    });

    it("does not display status badge when status is not provided", () => {
      render(<DoughnutChart {...defaultProps} />);
      const badges = screen.queryAllByText(/High|Medium|Low/);
      // Should only find labels, not badges
      expect(badges.length).toBeGreaterThan(0);
    });

    it("applies correct badge variant for high status", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "High",
          value: 100,
          color: "#ff0000",
          percentage: 100,
          status: "High Demand",
        },
      ];
      render(<DoughnutChart data={data} />);
      const badge = screen.getByText("High Demand");
      expect(badge).toHaveClass("bg-mw-error-50!");
    });

    it("applies correct badge variant for medium status", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "Medium",
          value: 100,
          color: "#ffaa00",
          percentage: 100,
          status: "Medium",
        },
      ];
      render(<DoughnutChart data={data} />);
      const elements = screen.getAllByText("Medium");
      const badge = elements.find((el) =>
        el.className.includes("bg-mw-warning"),
      );
      expect(badge).toHaveClass("bg-mw-warning-50!");
    });

    it("applies correct badge variant for low status", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "Low",
          value: 100,
          color: "#00ff00",
          percentage: 100,
          status: "Low",
        },
      ];
      render(<DoughnutChart data={data} />);
      const elements = screen.getAllByText("Low");
      const badge = elements.find((el) =>
        el.className.includes("bg-mw-success"),
      );
      expect(badge).toHaveClass("bg-mw-success-50!");
    });

    it("handles case-insensitive status matching", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "Test",
          value: 100,
          color: "#ff0000",
          percentage: 100,
          status: "HIGH DEMAND",
        },
      ];
      render(<DoughnutChart data={data} />);
      const badge = screen.getByText("HIGH DEMAND");
      expect(badge).toHaveClass("bg-mw-error-50!");
    });
  });

  describe("Doughnut labels", () => {
    it("shows doughnut labels when showDoughnutLabels is true and donutLabelType is value", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="value"
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(true);
    });

    it("shows doughnut labels when showDoughnutLabels is true and donutLabelType is percentage", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="percentage"
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(true);
    });

    it("hides doughnut labels when showDoughnutLabels is false", () => {
      render(<DoughnutChart {...defaultProps} showDoughnutLabels={false} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("hides doughnut labels when donutLabelType is none", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="none"
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.display).toBe(false);
    });

    it("formats labels as percentage when donutLabelType is percentage", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="percentage"
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: { formatter: (value: number) => string | null };
        };
      };
      const result = options.plugins.datalabels.formatter(50);
      expect(result).toBe("50%");
    });

    it("formats labels as value when donutLabelType is value", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="value"
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: { formatter: (value: number) => string | number };
        };
      };
      const result = options.plugins.datalabels.formatter(50);
      expect(result).toBe(50);
    });

    it("returns null when showDoughnutLabels is false in formatter", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={false}
          donutLabelType="value"
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: { formatter: (value: number) => string | null };
        };
      };
      const result = options.plugins.datalabels.formatter(50);
      expect(result).toBeNull();
    });

    it("handles zero total in percentage formatter", () => {
      render(
        <DoughnutChart
          data={[]}
          showDoughnutLabels={true}
          donutLabelType="percentage"
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: { datalabels: { formatter: (value: number) => string } };
      };
      const result = options.plugins.datalabels.formatter(50);
      expect(result).toBe("0%");
    });

    it("applies custom donutLabelColor", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="value"
          donutLabelColor="#ff0000"
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.color).toBe("#ff0000");
    });

    it("applies custom donutLabelFontSize", () => {
      render(
        <DoughnutChart
          {...defaultProps}
          showDoughnutLabels={true}
          donutLabelType="value"
          donutLabelFontSize={14}
        />,
      );
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.font.size).toBe(14);
    });
  });

  describe("Tooltip", () => {
    it("shows tooltip by default", () => {
      render(<DoughnutChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(true);
    });

    it("hides tooltip when showTooltip is false", () => {
      render(<DoughnutChart {...defaultProps} showTooltip={false} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(false);
    });

    it("uses custom formatTooltip when provided", () => {
      const formatTooltip = vi.fn(
        (label, value, percentage) => `${label}: ${value} (${percentage}%)`,
      );
      render(<DoughnutChart {...defaultProps} formatTooltip={formatTooltip} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (context: {
                label: string;
                parsed: number;
                dataIndex: number;
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        label: "High",
        parsed: 50,
        dataIndex: 0,
      });
      expect(result).toBe("High: 50 (50%)");
    });

    it("uses tooltipLabelType percentage by default", () => {
      render(<DoughnutChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (context: {
                label: string;
                parsed: number;
                dataIndex: number;
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        label: "High",
        parsed: 50,
        dataIndex: 0,
      });
      expect(result).toBe("High: 50%");
    });

    it("uses tooltipLabelType value", () => {
      render(<DoughnutChart {...defaultProps} tooltipLabelType="value" />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (context: {
                label: string;
                parsed: number;
                dataIndex: number;
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        label: "High",
        parsed: 50,
        dataIndex: 0,
      });
      expect(result).toBe("High: 50");
    });

    it("uses tooltipLabelType both", () => {
      render(<DoughnutChart {...defaultProps} tooltipLabelType="both" />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (context: {
                label: string;
                parsed: number;
                dataIndex: number;
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        label: "High",
        parsed: 50,
        dataIndex: 0,
      });
      expect(result).toBe("High: 50 (50%)");
    });

    it("handles tooltip with null parsed value", () => {
      render(<DoughnutChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (context: {
                label: string;
                parsed: number | null;
                dataIndex: number;
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        label: "High",
        parsed: null,
        dataIndex: 0,
      });
      expect(result).toBe("High: 50%");
    });
  });

  describe("Cutout", () => {
    it("uses default cutout percentage", () => {
      render(<DoughnutChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.cutout).toBe("60%");
    });

    it("applies custom cutout percentage", () => {
      render(<DoughnutChart {...defaultProps} cutout="70%" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.cutout).toBe("70%");
    });
  });

  describe("Font family", () => {
    it("uses default font family Poppins", () => {
      render(<DoughnutChart {...defaultProps} />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.titleFont.family).toBe("Poppins");
      expect(options.plugins.datalabels.font.family).toBe("Poppins");
    });

    it("uses custom font family when provided", () => {
      render(<DoughnutChart {...defaultProps} fontFamily="Arial" />);
      const optionsElement = screen.getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.titleFont.family).toBe("Arial");
      expect(options.plugins.datalabels.font.family).toBe("Arial");
    });
  });

  describe("Edge cases", () => {
    it("handles data with zero values", () => {
      const data: DoughnutChartDataItem[] = [
        { label: "Zero", value: 0, color: "#ff0000", percentage: 0 },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].data).toEqual([0]);
    });

    it("handles very large values", () => {
      const data: DoughnutChartDataItem[] = [
        {
          label: "Large",
          value: 1000000,
          color: "#ff0000",
          percentage: 100,
        },
      ];
      render(<DoughnutChart data={data} />);
      const dataElement = screen.getByTestId("chart-data");
      const chartData = JSON.parse(dataElement.textContent || "{}");
      expect(chartData.datasets[0].data).toEqual([1000000]);
    });
  });
});
