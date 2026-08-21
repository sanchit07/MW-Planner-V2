import { render, screen, within, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import HorizontalBarChart, {
  type HorizontalBarChartProps,
} from "../HorizontalBarChart";

let capturedOptions: unknown = null;

vi.mock("react-chartjs-2", () => ({
  Bar: ({
    data,
    options,
  }: {
    data: unknown;
    options: unknown;
    onClick?: (event: unknown, elements: unknown[]) => void;
  }) => {
    capturedOptions = options;
    const handleClick = (event: unknown, elements: unknown[]) => {
      if (options && typeof options === "object" && "onClick" in options) {
        const onClickHandler = options.onClick as (
          event: unknown,
          elements: unknown[],
        ) => void;
        onClickHandler(event, elements);
      }
    };

    return (
      <div data-testid="chartjs-bar">
        <div data-testid="chart-data">{JSON.stringify(data)}</div>
        <div data-testid="chart-options">
          {JSON.stringify(options, (_key, value) =>
            typeof value === "function" ? "[Function]" : value,
          )}
        </div>
        <button
          type="button"
          data-testid="mock-bar-click"
          onClick={() => handleClick({}, [{ datasetIndex: 0, index: 0 }])}
        >
          Click Bar
        </button>
      </div>
    );
  },
}));

describe("HorizontalBarChart", () => {
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
    cleanup();
    vi.restoreAllMocks();
  });

  const defaultProps: HorizontalBarChartProps = {
    labels: ["Category A", "Category B", "Category C"],
    datasets: [
      {
        label: "Value",
        data: [10, 20, 30],
      },
    ],
  };

  describe("Rendering", () => {
    it("renders chart with default props", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      expect(within(container).getByTestId("chartjs-bar")).toBeInTheDocument();
    });

    it("renders chart with title", () => {
      render(<HorizontalBarChart {...defaultProps} title="Chart Title" />);
      expect(screen.getByText("Chart Title")).toBeInTheDocument();
    });

    it("does not render title when not provided", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      expect(container.querySelector("h3")).not.toBeInTheDocument();
    });

    it("applies custom className", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} className="custom-chart" />,
      );
      expect(container.firstChild).toHaveClass("custom-chart");
    });

    it("applies custom height", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} height={500} />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.style.height).toBe("500px");
    });

    it("uses default height when not provided", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.style.height).toBe("300px");
    });

    it("applies custom width as number", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} width={400} />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.style.width).toBe("400px");
    });

    it("applies custom width as string", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} width="50%" />,
      );
      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper.style.width).toBe("50%");
    });
  });

  describe("Data configuration", () => {
    it("passes labels to chart data", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual(["Category A", "Category B", "Category C"]);
    });

    it("passes datasets to chart data", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(1);
      expect(data.datasets[0].label).toBe("Value");
      expect(data.datasets[0].data).toEqual([10, 20, 30]);
    });

    it("handles multiple datasets", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Value 1", data: [10, 20, 30] },
          { label: "Value 2", data: [5, 15, 25] },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(2);
    });

    it("applies custom backgroundColor from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            backgroundColor: "#ff0000",
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#ff0000");
    });

    it("applies custom borderColor from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            borderColor: "#00ff00",
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#00ff00");
    });

    it("uses backgroundColor as borderColor fallback", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            backgroundColor: "#ff0000",
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderColor).toBe("#ff0000");
    });

    it("applies custom borderWidth from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            borderWidth: 3,
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderWidth).toBe(3);
    });

    it("applies custom borderRadius from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            borderRadius: 8,
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderRadius).toBe(8);
    });

    it("applies custom borderSkipped from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            borderSkipped: "start",
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].borderSkipped).toBe("start");
    });

    it("applies custom barThickness from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            barThickness: 20,
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].barThickness).toBe(20);
    });

    it("applies custom maxBarThickness from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            maxBarThickness: 30,
          },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].maxBarThickness).toBe(30);
    });

    it("applies global barThickness when dataset barThickness not provided", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} barThickness={25} />,
      );
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].barThickness).toBe(25);
    });

    it("applies global maxBarThickness when dataset maxBarThickness not provided", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} maxBarThickness={35} />,
      );
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].maxBarThickness).toBe(35);
    });
  });

  describe("Stacked bars", () => {
    it("enables stacking when stacked is true", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} stacked={true} />,
      );
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].stack).toBe("stack1");
    });

    it("uses custom stack identifier from dataset", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          {
            label: "Value",
            data: [10, 20, 30],
            stack: "custom-stack",
          },
        ],
      };
      const { container } = render(
        <HorizontalBarChart {...props} stacked={true} />,
      );
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].stack).toBe("custom-stack");
    });

    it("does not add stack when stacked is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} stacked={false} />,
      );
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].stack).toBeUndefined();
    });

    it("configures scales for stacked mode", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} stacked={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.stacked).toBe(true);
      expect(options.scales.y.stacked).toBe(true);
    });
  });

  describe("CSS variable color handling", () => {
    it("uses CSS variable value when available", () => {
      mockGetPropertyValue.mockImplementation((name: string) => {
        if (name === "--color-mw-primary-500") return " #2176cc ";
        return "";
      });
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });

    it("falls back to default color when CSS variable is empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });

    it("cycles through default colors for multiple datasets", () => {
      mockGetPropertyValue.mockReturnValue("");
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Value 1", data: [10, 20, 30] },
          { label: "Value 2", data: [5, 15, 25] },
          { label: "Value 3", data: [1, 2, 3] },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toHaveLength(3);
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
      expect(data.datasets[1].backgroundColor).toBe("#2176cc");
      expect(data.datasets[2].backgroundColor).toBe("#2176cc");
    });

    it("handles SSR scenario when getComputedStyle returns empty", () => {
      mockGetPropertyValue.mockReturnValue("");
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].backgroundColor).toBe("#2176cc");
    });
  });

  describe("Chart options", () => {
    it("configures indexAxis as y for horizontal bars", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.indexAxis).toBe("y");
    });

    it("configures responsive and maintainAspectRatio", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.responsive).toBe(true);
      expect(options.maintainAspectRatio).toBe(false);
    });

    it("shows legend when showLegend is true and multiple datasets", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Value 1", data: [10, 20, 30] },
          { label: "Value 2", data: [5, 15, 25] },
        ],
      };
      const { container } = render(
        <HorizontalBarChart {...props} showLegend={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(true);
    });

    it("hides legend when showLegend is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showLegend={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("hides legend when single dataset", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showLegend={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.display).toBe(false);
    });

    it("configures legend position", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} legendPosition="top" />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.position).toBe("top");
    });

    it("configures legend style", () => {
      const props: HorizontalBarChartProps = {
        ...defaultProps,
        datasets: [
          { label: "Value 1", data: [10, 20, 30] },
          { label: "Value 2", data: [5, 15, 25] },
        ],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.usePointStyle).toBe(true);
      expect(options.plugins.legend.labels.pointStyle).toBe("rect");
      expect(options.plugins.legend.labels.boxWidth).toBe(12);
      expect(options.plugins.legend.labels.boxHeight).toBe(12);
    });
  });

  describe("Tooltip", () => {
    it("shows tooltip by default", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(true);
    });

    it("hides tooltip when showTooltip is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showTooltip={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.enabled).toBe(false);
    });

    it("uses index mode for stacked tooltips", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} stacked={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.mode).toBe("index");
      expect(options.plugins.tooltip.intersect).toBe(false);
    });

    it("uses nearest mode for non-stacked tooltips", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} stacked={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.tooltip.mode).toBe("nearest");
      expect(options.plugins.tooltip.intersect).toBe(true);
    });

    it("uses custom formatTooltipValue when provided", () => {
      const formatTooltipValue = vi.fn(
        (value, label, datasetLabel) => `${datasetLabel}: $${value} (${label})`,
      );
      render(
        <HorizontalBarChart
          {...defaultProps}
          formatTooltipValue={formatTooltipValue}
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (ctx: {
                parsed: { x: number };
                label: string;
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        parsed: { x: 10 },
        label: "Category A",
        dataset: { label: "Value" },
      });
      expect(result).toBe("Value: $10 (Category A)");
    });

    it("uses custom formatTooltipTitle when provided", () => {
      const formatTooltipTitle = vi.fn((label) => `Title: ${label}`);
      render(
        <HorizontalBarChart
          {...defaultProps}
          formatTooltipTitle={formatTooltipTitle}
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              title: (items: { label: string }[]) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.title([
        { label: "Category A" },
      ]);
      expect(result).toBe("Title: Category A");
    });

    it("uses default tooltip format when formatters not provided", () => {
      render(<HorizontalBarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (ctx: {
                parsed: { x: number };
                label: string;
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        parsed: { x: 10 },
        label: "Category A",
        dataset: { label: "Value" },
      });
      expect(result).toBe("Value: 10");
    });

    it("handles tooltip with null parsed value", () => {
      render(<HorizontalBarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          tooltip: {
            callbacks: {
              label: (ctx: {
                parsed: { x: number | null };
                label: string;
                dataset: { label: string };
              }) => string;
            };
          };
        };
      };
      const result = options.plugins.tooltip.callbacks.label({
        parsed: { x: null },
        label: "Category A",
        dataset: { label: "Value" },
      });
      expect(result).toBe("Value: 0");
    });
  });

  describe("Data labels", () => {
    it("shows data labels when showDataLabels is true", () => {
      render(<HorizontalBarChart {...defaultProps} showDataLabels={true} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: {
            display: (ctx: {
              dataset: { data: number[] };
              dataIndex: number;
            }) => boolean;
          };
        };
      };
      expect(
        options.plugins.datalabels.display({
          dataset: { data: [10] },
          dataIndex: 0,
        }),
      ).toBe(true);
    });

    it("hides data labels when showDataLabels is false", () => {
      render(<HorizontalBarChart {...defaultProps} showDataLabels={false} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: {
            display: (ctx: {
              dataset: { data: number[] };
              dataIndex: number;
            }) => boolean;
          };
        };
      };
      expect(
        options.plugins.datalabels.display({
          dataset: { data: [10] },
          dataIndex: 0,
        }),
      ).toBe(false);
    });

    it("hides data labels for values less than 5", () => {
      render(<HorizontalBarChart {...defaultProps} showDataLabels={true} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: {
            display: (ctx: {
              dataset: { data: number[] };
              dataIndex: number;
            }) => boolean;
          };
        };
      };
      const displayFn = options.plugins.datalabels.display;
      expect(displayFn({ dataset: { data: [4] }, dataIndex: 0 })).toBe(false);
      expect(displayFn({ dataset: { data: [5] }, dataIndex: 0 })).toBe(true);
    });

    it("uses custom formatDataLabel when provided", () => {
      const formatDataLabel = vi.fn((value, _datasetLabel) => `${value}%`);
      render(
        <HorizontalBarChart
          {...defaultProps}
          showDataLabels={true}
          formatDataLabel={formatDataLabel}
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: {
            formatter: (
              value: number,
              ctx: { dataset: { label: string } },
            ) => string;
          };
        };
      };
      const result = options.plugins.datalabels.formatter(50, {
        dataset: { label: "Value" },
      });
      expect(result).toBe("50%");
    });

    it("uses default data label format when formatter not provided", () => {
      render(<HorizontalBarChart {...defaultProps} showDataLabels={true} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        plugins: {
          datalabels: {
            formatter: (
              value: number,
              ctx: { dataset: { label: string } },
            ) => string;
          };
        };
      };
      const result = options.plugins.datalabels.formatter(50, {
        dataset: { label: "Value" },
      });
      expect(result).toBe("50% Value");
    });

    it("applies custom dataLabelColor", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showDataLabels={true}
          dataLabelColor="#ff0000"
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.color).toBe("#ff0000");
    });

    it("applies custom dataLabelFontSize", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showDataLabels={true}
          dataLabelFontSize={14}
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.datalabels.font.size).toBe(14);
    });
  });

  describe("X-axis configuration", () => {
    it("configures x-axis with default beginAtZero", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.beginAtZero).toBe(true);
    });

    it("applies custom xAxisMin", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} xAxisMin={10} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.min).toBe(10);
    });

    it("applies custom xAxisMax", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} xAxisMax={100} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.max).toBe(100);
    });

    it("applies custom xAxisStep", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} xAxisStep={5} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.ticks.stepSize).toBe(5);
    });

    it("applies custom xAxisLabel", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} xAxisLabel="Amount" />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.title.display).toBe(true);
      expect(options.scales.x.title.text).toBe("Amount");
    });

    it("shows x-axis when showXAxisLabels or showXAxisGrid is true", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showXAxisLabels={true}
          showXAxisGrid={false}
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.display).toBe(true);
    });

    it("hides x-axis when both showXAxisLabels and showXAxisGrid are false", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showXAxisLabels={false}
          showXAxisGrid={false}
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.display).toBe(false);
    });

    it("shows x-axis grid when showXAxisGrid is true", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showXAxisGrid={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.grid.display).toBe(true);
    });

    it("hides x-axis grid when showXAxisGrid is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showXAxisGrid={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.grid.display).toBe(false);
    });

    it("shows x-axis ticks when showXAxisLabels is true", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showXAxisLabels={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.ticks.display).toBe(true);
    });

    it("hides x-axis ticks when showXAxisLabels is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showXAxisLabels={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.x.ticks.display).toBe(false);
    });

    it("uses custom formatXAxisValue when provided", () => {
      const formatXAxisValue = vi.fn((value) => `$${value}`);
      render(
        <HorizontalBarChart
          {...defaultProps}
          formatXAxisValue={formatXAxisValue}
        />,
      );
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        scales: { x: { ticks: { callback: (v: number) => string | number } } };
      };
      const result = options.scales.x.ticks.callback(100);
      expect(result).toBe("$100");
    });

    it("uses default x-axis format when formatXAxisValue not provided", () => {
      render(<HorizontalBarChart {...defaultProps} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        scales: { x: { ticks: { callback: (v: number) => number } } };
      };
      const result = options.scales.x.ticks.callback(100);
      expect(result).toBe(100);
    });
  });

  describe("Y-axis configuration", () => {
    it("shows y-axis when showYAxisLabels or showYAxisGrid is true", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showYAxisLabels={true}
          showYAxisGrid={false}
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.display).toBe(true);
    });

    it("hides y-axis when both showYAxisLabels and showYAxisGrid are false", () => {
      const { container } = render(
        <HorizontalBarChart
          {...defaultProps}
          showYAxisLabels={false}
          showYAxisGrid={false}
        />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.display).toBe(false);
    });

    it("shows y-axis grid when showYAxisGrid is true", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showYAxisGrid={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.grid.display).toBe(true);
    });

    it("hides y-axis grid when showYAxisGrid is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showYAxisGrid={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.grid.display).toBe(false);
    });

    it("shows y-axis ticks when showYAxisLabels is true", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showYAxisLabels={true} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.ticks.display).toBe(true);
    });

    it("hides y-axis ticks when showYAxisLabels is false", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} showYAxisLabels={false} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.scales.y.ticks.display).toBe(false);
    });
  });

  describe("Bar click handler", () => {
    it("calls onBarClick when bar is clicked", async () => {
      const user = userEvent.setup();
      const onBarClick = vi.fn();
      const { container } = render(
        <HorizontalBarChart {...defaultProps} onBarClick={onBarClick} />,
      );
      const clickButton = within(container).getByTestId("mock-bar-click");
      await user.click(clickButton);
      expect(onBarClick).toHaveBeenCalledWith(0, 0, 10, "Category A");
    });

    it("does not call onBarClick when not provided", async () => {
      const user = userEvent.setup();
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const clickButton = within(container).getByTestId("mock-bar-click");
      await user.click(clickButton);
      // Should not throw error
      expect(clickButton).toBeInTheDocument();
    });

    it("handles click with no elements", () => {
      const onBarClick = vi.fn();
      render(<HorizontalBarChart {...defaultProps} onBarClick={onBarClick} />);
      expect(capturedOptions).toBeTruthy();
      const options = capturedOptions as {
        onClick: (event: unknown, elements: unknown[]) => void;
      };
      options.onClick({}, []);
      expect(onBarClick).not.toHaveBeenCalled();
    });
  });

  describe("Category and bar percentage", () => {
    it("applies custom categoryPercentage", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} categoryPercentage={0.9} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.datasets.bar.categoryPercentage).toBe(0.9);
    });

    it("applies custom barPercentage", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} barPercentage={0.8} />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.datasets.bar.barPercentage).toBe(0.8);
    });

    it("uses default categoryPercentage when not provided", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.datasets.bar.categoryPercentage).toBe(0.8);
    });

    it("uses default barPercentage when not provided", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.datasets.bar.barPercentage).toBe(0.9);
    });
  });

  describe("Font family", () => {
    it("uses default font family Poppins", () => {
      const { container } = render(<HorizontalBarChart {...defaultProps} />);
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Poppins");
      expect(options.plugins.tooltip.titleFont.family).toBe("Poppins");
    });

    it("uses custom font family when provided", () => {
      const { container } = render(
        <HorizontalBarChart {...defaultProps} fontFamily="Arial" />,
      );
      const optionsElement = within(container).getByTestId("chart-options");
      const options = JSON.parse(optionsElement.textContent || "{}");
      expect(options.plugins.legend.labels.font.family).toBe("Arial");
      expect(options.plugins.tooltip.titleFont.family).toBe("Arial");
    });
  });

  describe("Edge cases", () => {
    it("handles empty labels array", () => {
      const props: HorizontalBarChartProps = {
        labels: [],
        datasets: [{ label: "Value", data: [] }],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.labels).toEqual([]);
    });

    it("handles empty datasets array", () => {
      const props: HorizontalBarChartProps = {
        labels: ["A", "B"],
        datasets: [],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets).toEqual([]);
    });

    it("handles dataset with empty data array", () => {
      const props: HorizontalBarChartProps = {
        labels: ["A", "B"],
        datasets: [{ label: "Value", data: [] }],
      };
      const { container } = render(<HorizontalBarChart {...props} />);
      const dataElement = within(container).getByTestId("chart-data");
      const data = JSON.parse(dataElement.textContent || "{}");
      expect(data.datasets[0].data).toEqual([]);
    });
  });
});
