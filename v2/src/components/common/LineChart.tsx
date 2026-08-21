import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  type ChartOptions,
  type TooltipItem,
  type Plugin,
} from "chart.js";
import ChartDataLabels from "chartjs-plugin-datalabels";
import React, { useMemo, useCallback } from "react";
import { Line } from "react-chartjs-2";

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  ChartDataLabels,
);

export interface LineChartDataset {
  label: string;
  data: number[];
  borderColor?: string;
  backgroundColor?: string;
  pointRadius?: number;
  pointHoverRadius?: number;
  borderWidth?: number;
  tension?: number;
}

export interface LineChartProps {
  title?: string;
  labels: string[];
  datasets: LineChartDataset[];
  currentDateIndex?: number;
  height?: number;
  yAxisLabel?: string;
  formatYAxisValue?: (value: number) => string;
  formatTooltipValue?: (value: number, label: string) => string;
  formatTooltipDate?: (date: string) => string;
  className?: string;
  initialVisibleItems?: number;
  spacingPerItem?: number;
  /** Font family for all chart text (default: "Poppins") */
  fontFamily?: string;
  /** Whether to show the legend (default: false) */
  showLegend?: boolean;
  /** Data labels: show values on points. Decimals are shown with 2 decimal places. */
  dataLabels?: {
    show?: boolean;
    /** Optional formatter; default shows decimals fixed to 2 places */
    formatter?: (value: number) => string | number | null;
  };
}

// Custom plugin factory to draw vertical line for current date
const createVerticalLinePlugin = (
  currentDateIndex?: number,
): Plugin<"line"> => {
  return {
    id: "verticalLine",
    afterDatasetsDraw: (chart) => {
      if (currentDateIndex === undefined || currentDateIndex === null) {
        return;
      }

      const {
        ctx,
        chartArea: { left, right, top, bottom },
        scales: { x },
      } = chart;

      if (!x) {
        return;
      }

      const xValue = x.getPixelForValue(currentDateIndex);

      // Only draw if the line is within the chart area
      if (xValue >= left && xValue <= right) {
        ctx.save();
        // Ensure solid line - reset any dash pattern
        ctx.setLineDash([]);
        ctx.strokeStyle = "#000000"; // Solid black color
        ctx.lineWidth = 2; // Thicker to ensure it's visible and solid
        ctx.lineCap = "butt";
        ctx.beginPath();
        ctx.moveTo(xValue, top);
        ctx.lineTo(xValue, bottom);
        ctx.stroke();
        ctx.restore();
      }
    },
  };
};

const LineChart: React.FC<LineChartProps> = ({
  title,
  labels,
  datasets,
  currentDateIndex,
  height = 400,
  yAxisLabel,
  formatYAxisValue,
  formatTooltipValue,
  formatTooltipDate,
  className,
  initialVisibleItems,
  spacingPerItem,
  fontFamily = "Poppins",
  showLegend = false,
  dataLabels: dataLabelsConfig,
}) => {
  const dataLabelsShow = dataLabelsConfig?.show ?? false;
  const dataLabelsFormatter = useCallback(
    (value: number): string | number => {
      if (dataLabelsConfig?.formatter) {
        const result = dataLabelsConfig.formatter(value);
        return result ?? "";
      }
      if (typeof value !== "number" || Number.isNaN(value)) return "";
      return Number.isInteger(value) ? value : Number(value).toFixed(2);
    },
    [dataLabelsConfig?.formatter],
  );
  // Get color value from CSS variable
  const getColorValue = useCallback((cssVar: string): string => {
    if (typeof window !== "undefined") {
      return (
        getComputedStyle(document.documentElement)
          .getPropertyValue(cssVar)
          .trim() || "#2176cc"
      );
    }
    return "#2176cc";
  }, []);

  // Prepare chart data
  const chartData = useMemo(() => {
    return {
      labels,
      datasets: datasets.map((dataset, index) => ({
        label: dataset.label,
        data: dataset.data,
        borderColor:
          dataset.borderColor ||
          (index === 0
            ? getColorValue("--color-mw-error-500") || "#c52828" // Red for Revenue
            : getColorValue("--color-mw-success-500") || "#2d7d32"), // Green for Cost
        backgroundColor:
          dataset.backgroundColor ||
          (index === 0
            ? getColorValue("--color-mw-error-500") || "#c52828"
            : getColorValue("--color-mw-success-500") || "#2d7d32"),
        pointRadius: dataset.pointRadius ?? 4,
        pointHoverRadius: dataset.pointHoverRadius ?? 6,
        borderWidth: dataset.borderWidth ?? 2,
        tension: dataset.tension ?? 0.1,
      })),
    };
  }, [labels, datasets, getColorValue]);

  // Chart options
  const chartOptions = useMemo<ChartOptions<"line">>(() => {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: showLegend,
          position: "bottom",
          labels: {
            usePointStyle: false,
            boxWidth: 20, // Width of the line indicator
            boxHeight: 2, // Height/thickness of the line indicator
            padding: 15,
            font: {
              family: fontFamily,
              size: 12,
            },
            color: "#505050",
          },
        },
        tooltip: {
          mode: "index",
          intersect: false,
          callbacks: {
            title: (items: TooltipItem<"line">[]) => {
              if (items.length === 0) return "";
              const label = items[0].label;
              return formatTooltipDate ? formatTooltipDate(label) : label;
            },
            label: (context: TooltipItem<"line">) => {
              const value = context.parsed.y ?? 0;
              const label = context.dataset.label || "";
              return formatTooltipValue
                ? formatTooltipValue(value, label)
                : `${label}: ${value}`;
            },
          },
          backgroundColor: "#FFFFFF",
          titleColor: "#505050",
          bodyColor: "#505050",
          borderColor: "#CCCCCC",
          borderWidth: 1,
          padding: 12,
          titleFont: {
            family: fontFamily,
          },
          bodyFont: {
            family: fontFamily,
          },
        },
        datalabels: {
          display: dataLabelsShow,
          color: "#505050",
          font: {
            family: fontFamily,
            size: 11,
            weight: "normal",
          },
          anchor: "end",
          align: "top",
          offset: 4,
          formatter: dataLabelsFormatter,
        },
      },
      scales: {
        x: {
          grid: {
            display: true,
            color: (context: { index: number }) => {
              // Skip drawing grid line at current date index - return transparent
              if (
                currentDateIndex !== undefined &&
                currentDateIndex !== null &&
                context.index === currentDateIndex
              ) {
                return "transparent";
              }
              return "#E2E8F0";
            },
            borderDash: (context: { index: number }) => {
              // Skip dash pattern at current date index
              if (
                currentDateIndex !== undefined &&
                currentDateIndex !== null &&
                context.index === currentDateIndex
              ) {
                return [];
              }
              return [5, 5]; // Dotted vertical grid lines for dates
            },
            drawOnChartArea: true,
            drawTicks: false,
          },
          ticks: {
            font: {
              family: fontFamily,
              size: 12,
            },
            color: "#505050",
          },
        },
        y: {
          beginAtZero: true,
          ticks: {
            font: {
              size: 12,
            },
            color: "#505050",
            callback: function (value) {
              if (formatYAxisValue) {
                return formatYAxisValue(Number(value));
              }
              return value;
            },
          },
          grid: {
            display: false, // Remove horizontal grid lines
          },
          ...(yAxisLabel && {
            title: {
              display: true,
              text: yAxisLabel,
              color: "#505050",
              font: {
                family: fontFamily,
                size: 12,
              },
            },
          }),
        },
      },
    };
  }, [
    currentDateIndex,
    formatYAxisValue,
    formatTooltipValue,
    formatTooltipDate,
    yAxisLabel,
    fontFamily,
    showLegend,
    dataLabelsShow,
    dataLabelsFormatter,
  ]);

  // Determine spacing and scrolling behavior
  const spacing = spacingPerItem ?? (labels.length >= 30 ? 70 : 40);
  const needsHorizontalScroll =
    labels.length >= 30 || initialVisibleItems !== undefined;
  const chartMinWidth = needsHorizontalScroll
    ? `${labels.length * spacing}px`
    : "100%";
  const containerMaxWidth =
    initialVisibleItems !== undefined
      ? `${initialVisibleItems * spacing}px`
      : undefined;

  return (
    <div className={className}>
      {title && (
        <h3 className="text-base font-medium leading-5 mb-4 text-mw-black">
          {title}
        </h3>
      )}
      <div
        className={
          needsHorizontalScroll
            ? "overflow-x-auto scrollbar-thin scrollbar-thumb-mw-neutral-300 scrollbar-track-transparent"
            : ""
        }
        style={
          needsHorizontalScroll
            ? { height: `${height}px`, maxWidth: containerMaxWidth }
            : {}
        }
      >
        <div style={{ height: `${height}px`, minWidth: chartMinWidth }}>
          <Line
            data={chartData}
            options={chartOptions}
            plugins={[createVerticalLinePlugin(currentDateIndex)]}
          />
        </div>
      </div>
    </div>
  );
};

export default LineChart;
