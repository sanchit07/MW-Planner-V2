import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
  Legend,
  type ChartOptions,
  type TooltipItem,
} from "chart.js";
import ChartDataLabels from "chartjs-plugin-datalabels";
import React, { useMemo, useCallback } from "react";
import { Bar } from "react-chartjs-2";

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
  Legend,
  ChartDataLabels,
);

export interface HorizontalBarDataset {
  label: string;
  data: number[];
  backgroundColor?: string | string[];
  borderColor?: string | string[];
  borderWidth?: number;
  borderRadius?: number;
  borderSkipped?:
    | boolean
    | "start"
    | "end"
    | "left"
    | "right"
    | "bottom"
    | "top";
  barThickness?: number;
  maxBarThickness?: number;
  stack?: string; // Stack group identifier for stacked charts
}

export interface HorizontalBarChartProps {
  /** Chart title */
  title?: string;
  /** Labels for the Y-axis (category labels) */
  labels: string[];
  /** Dataset configurations */
  datasets: HorizontalBarDataset[];
  /** Chart height in pixels */
  height?: number;
  /** Chart width - can be number (px) or string (e.g., "100%") */
  width?: number | string;
  /** X-axis label */
  xAxisLabel?: string;
  /** X-axis minimum value */
  xAxisMin?: number;
  /** X-axis maximum value */
  xAxisMax?: number;
  /** X-axis step size */
  xAxisStep?: number;
  /** Format X-axis tick values */
  formatXAxisValue?: (value: number) => string;
  /** Format tooltip values */
  formatTooltipValue?: (
    value: number,
    label: string,
    datasetLabel: string,
  ) => string;
  /** Enable stacked bars */
  stacked?: boolean;
  /** Show legend */
  showLegend?: boolean;
  /** Legend position */
  legendPosition?: "top" | "bottom" | "left" | "right";
  /** Show grid lines on X-axis */
  showXAxisGrid?: boolean;
  /** Show grid lines on Y-axis */
  showYAxisGrid?: boolean;
  /** Show X-axis tick labels */
  showXAxisLabels?: boolean;
  /** Show Y-axis tick labels */
  showYAxisLabels?: boolean;
  /** Show tooltip on hover */
  showTooltip?: boolean;
  /** Show data labels inside bars */
  showDataLabels?: boolean;
  /** Format data labels inside bars */
  formatDataLabel?: (value: number, datasetLabel: string) => string;
  /** Data label font color */
  dataLabelColor?: string;
  /** Data label font size */
  dataLabelFontSize?: number;
  /** Additional CSS class */
  className?: string;
  /** Bar thickness (applies to all datasets if not set per dataset) */
  barThickness?: number;
  /** Maximum bar thickness */
  maxBarThickness?: number;
  /** Category percentage - width of each category */
  categoryPercentage?: number;
  /** Bar percentage - width of each bar within category */
  barPercentage?: number;
  /** Custom tooltip title formatter */
  formatTooltipTitle?: (label: string) => string;
  /** On bar click callback */
  onBarClick?: (
    datasetIndex: number,
    dataIndex: number,
    value: number,
    label: string,
  ) => void;
  /** Font family for all chart text (default: "Poppins") */
  fontFamily?: string;
}

const HorizontalBarChart: React.FC<HorizontalBarChartProps> = ({
  title,
  labels,
  datasets,
  height = 300,
  width,
  xAxisLabel,
  xAxisMin,
  xAxisMax,
  xAxisStep,
  formatXAxisValue,
  formatTooltipValue,
  stacked = false,
  showLegend = true,
  legendPosition = "bottom",
  showXAxisGrid = true,
  showYAxisGrid = false,
  showXAxisLabels = true,
  showYAxisLabels = true,
  showTooltip = true,
  showDataLabels = false,
  formatDataLabel,
  dataLabelColor = "#ffffff",
  dataLabelFontSize = 11,
  className,
  barThickness,
  maxBarThickness,
  categoryPercentage = 0.8,
  barPercentage = 0.9,
  formatTooltipTitle,
  onBarClick,
  fontFamily = "Poppins",
}) => {
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

  // Default colors for datasets
  const defaultColors = useMemo(
    () => [
      getColorValue("--color-mw-primary-500") || "#2176cc",
      getColorValue("--color-mw-success-500") || "#2d7d32",
      getColorValue("--color-mw-warning-500") || "#ed6c02",
      getColorValue("--color-mw-error-500") || "#d32f2f",
      getColorValue("--color-mw-secondary-500") || "#9c27b0",
      "#00bcd4",
      "#ff9800",
      "#795548",
    ],
    [getColorValue],
  );

  // Prepare chart data
  const chartData = useMemo(() => {
    return {
      labels,
      datasets: datasets.map((dataset, index) => ({
        label: dataset.label,
        data: dataset.data,
        backgroundColor:
          dataset.backgroundColor ||
          defaultColors[index % defaultColors.length],
        borderColor:
          dataset.borderColor ||
          dataset.backgroundColor ||
          defaultColors[index % defaultColors.length],
        borderWidth: dataset.borderWidth ?? 0,
        borderRadius: dataset.borderRadius ?? 4,
        borderSkipped: dataset.borderSkipped ?? false,
        barThickness: dataset.barThickness ?? barThickness,
        maxBarThickness: dataset.maxBarThickness ?? maxBarThickness,
        ...(stacked && { stack: dataset.stack || "stack1" }),
      })),
    };
  }, [labels, datasets, defaultColors, barThickness, maxBarThickness, stacked]);

  // Chart options
  const chartOptions = useMemo<ChartOptions<"bar">>(() => {
    return {
      indexAxis: "y" as const, // This makes it horizontal
      responsive: true,
      maintainAspectRatio: false,
      onClick: (_event, elements) => {
        if (elements.length > 0 && onBarClick) {
          const element = elements[0];
          const datasetIndex = element.datasetIndex;
          const dataIndex = element.index;
          const value = datasets[datasetIndex].data[dataIndex];
          const label = labels[dataIndex];
          onBarClick(datasetIndex, dataIndex, value, label);
        }
      },
      plugins: {
        legend: {
          display: showLegend && datasets.length > 1,
          position: legendPosition,
          labels: {
            usePointStyle: true,
            pointStyle: "rect",
            boxWidth: 12,
            boxHeight: 12,
            padding: 15,
            font: {
              family: fontFamily,
              size: 12,
            },
            color: "#505050",
          },
        },
        tooltip: {
          enabled: showTooltip,
          mode: stacked ? "index" : "nearest",
          intersect: !stacked,
          callbacks: {
            title: (tooltipItems) => {
              const label = tooltipItems[0]?.label || "";
              return formatTooltipTitle ? formatTooltipTitle(label) : label;
            },
            label: (context: TooltipItem<"bar">) => {
              const value = context.parsed.x ?? 0;
              const label = context.label || "";
              const datasetLabel = context.dataset.label || "";
              return formatTooltipValue
                ? formatTooltipValue(value, label, datasetLabel)
                : `${datasetLabel}: ${value}`;
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
          color: dataLabelColor,
          font: {
            family: fontFamily,
            size: dataLabelFontSize,
            weight: "bold" as const,
          },
          anchor: "center" as const,
          align: "center" as const,
          formatter: (value: number, context) => {
            const datasetLabel = context.dataset.label || "";
            if (formatDataLabel) {
              return formatDataLabel(value, datasetLabel);
            }
            return `${value}% ${datasetLabel}`;
          },
          // Hide label if bar is too small
          display: (context) => {
            if (!showDataLabels) return false;
            const value = context.dataset.data[context.dataIndex] as number;
            // Hide if value is less than 5% (bar would be too small)
            return value >= 5;
          },
        },
      },
      scales: {
        x: {
          stacked,
          display: showXAxisLabels || showXAxisGrid,
          beginAtZero: true,
          min: xAxisMin,
          max: xAxisMax,
          ticks: {
            display: showXAxisLabels,
            stepSize: xAxisStep,
            font: {
              family: fontFamily,
              size: 12,
            },
            color: "#505050",
            callback: function (value) {
              if (formatXAxisValue) {
                return formatXAxisValue(Number(value));
              }
              return value;
            },
          },
          grid: {
            display: showXAxisGrid,
            color: "#E2E8F0",
          },
          ...(xAxisLabel && {
            title: {
              display: true,
              text: xAxisLabel,
              color: "#505050",
              font: {
                family: fontFamily,
                size: 12,
              },
            },
          }),
        },
        y: {
          stacked,
          display: showYAxisLabels || showYAxisGrid,
          grid: {
            display: showYAxisGrid,
            color: "#E2E8F0",
          },
          ticks: {
            display: showYAxisLabels,
            font: {
              family: fontFamily,
              size: 12,
            },
            color: "#505050",
          },
        },
      },
      datasets: {
        bar: {
          categoryPercentage,
          barPercentage,
        },
      },
    };
  }, [
    datasets,
    labels,
    stacked,
    showLegend,
    legendPosition,
    showTooltip,
    showDataLabels,
    formatDataLabel,
    dataLabelColor,
    dataLabelFontSize,
    formatXAxisValue,
    formatTooltipValue,
    formatTooltipTitle,
    xAxisLabel,
    xAxisMin,
    xAxisMax,
    xAxisStep,
    showXAxisGrid,
    showYAxisGrid,
    showXAxisLabels,
    showYAxisLabels,
    categoryPercentage,
    barPercentage,
    onBarClick,
    fontFamily,
  ]);

  const containerStyle: React.CSSProperties = {
    height: `${height}px`,
    ...(width && { width: typeof width === "number" ? `${width}px` : width }),
  };

  return (
    <div className={className} style={containerStyle}>
      {title && (
        <h3 className="text-base font-medium leading-5 mb-4 text-mw-black">
          {title}
        </h3>
      )}
      <Bar data={chartData} options={chartOptions} />
    </div>
  );
};

export default HorizontalBarChart;
