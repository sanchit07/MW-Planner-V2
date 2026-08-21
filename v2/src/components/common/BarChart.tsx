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
import React, { useMemo, useCallback } from "react";
import { Bar } from "react-chartjs-2";

// Register Chart.js components
ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend);

export interface BarChartDataset {
  label: string;
  data: number[];
  borderColor?: string;
  backgroundColor?: string;
  borderWidth?: number;
  borderRadius?: number;
  borderSkipped?: boolean;
}

export interface BarChartProps {
  title?: string;
  labels: string[];
  datasets: BarChartDataset[];
  height?: number;
  yAxisLabel?: string;
  yAxisMin?: number;
  yAxisMax?: number;
  yAxisStep?: number;
  formatYAxisValue?: (value: number) => string;
  formatTooltipValue?: (value: number, label: string) => string;
  className?: string;
  /** Font family for all chart text (default: "Poppins") */
  fontFamily?: string;
}

const BarChart: React.FC<BarChartProps> = ({
  title,
  labels,
  datasets,
  height = 400,
  yAxisLabel,
  yAxisMin = 0,
  yAxisMax,
  yAxisStep,
  formatYAxisValue,
  formatTooltipValue,
  className,
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
            ? getColorValue("--color-mw-primary-500") || "#2176cc"
            : getColorValue("--color-mw-success-500") || "#2d7d32"),
        backgroundColor:
          dataset.backgroundColor ||
          (index === 0
            ? getColorValue("--color-mw-primary-500") || "#2176cc"
            : getColorValue("--color-mw-success-500") || "#2d7d32"),
        borderWidth: dataset.borderWidth ?? 1,
        borderRadius: dataset.borderRadius ?? 4,
        borderSkipped: dataset.borderSkipped ?? false,
      })),
    };
  }, [labels, datasets, getColorValue]);

  // Chart options
  const chartOptions = useMemo<ChartOptions<"bar">>(() => {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        datalabels: {
          display: false,
        },
        legend: {
          display: datasets.length > 1,
          position: "bottom",
          labels: {
            usePointStyle: false,
            boxWidth: 20,
            boxHeight: 2,
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
            label: (context: TooltipItem<"bar">) => {
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
      },
      scales: {
        x: {
          grid: {
            display: false,
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
          min: yAxisMin,
          max: yAxisMax,
          ticks: {
            stepSize: yAxisStep,
            font: {
              family: fontFamily,
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
            display: true,
            color: "#E2E8F0",
            borderDash: [5, 5],
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
    datasets.length,
    formatYAxisValue,
    formatTooltipValue,
    yAxisLabel,
    yAxisMin,
    yAxisMax,
    yAxisStep,
    fontFamily,
  ]);

  return (
    <div className={className} style={{ height: `${height}px` }}>
      {title && (
        <h3 className="text-base font-medium leading-5 mb-4 text-mw-black">
          {title}
        </h3>
      )}
      <Bar data={chartData} options={chartOptions} />
    </div>
  );
};

export default BarChart;
