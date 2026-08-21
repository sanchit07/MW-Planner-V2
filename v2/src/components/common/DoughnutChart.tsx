import { Badge } from "@components/ui/Badge";
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  type ChartOptions,
  type TooltipItem,
} from "chart.js";
import ChartDataLabels from "chartjs-plugin-datalabels";
import React, { useMemo, useCallback } from "react";
import { Doughnut } from "react-chartjs-2";

// Register Chart.js components
ChartJS.register(ArcElement, Tooltip, Legend, ChartDataLabels);

export interface DoughnutChartDataItem {
  label: string;
  value: number;
  color: string;
  status?: string;
  percentage: number;
}

export type DoughnutLabelType = "none" | "value" | "percentage";
export type TooltipLabelType = "value" | "percentage" | "both";

export interface DoughnutChartProps {
  data: DoughnutChartDataItem[];
  height?: number;
  width?: number;
  emphasizeLargest?: boolean;
  /** Whether to show the legend section below the chart (default: true) */
  showLegend?: boolean;
  /** Whether to show labels inside donut segments (default: false) */
  showDoughnutLabels?: boolean;
  /** Type of label to show inside segments: 'none', 'value', or 'percentage' (default: 'none') */
  donutLabelType?: DoughnutLabelType;
  /** Color for donut segment labels (default: '#ffffff') */
  donutLabelColor?: string;
  /** Font size for donut segment labels (default: 11) */
  donutLabelFontSize?: number;
  /** Cutout percentage for the donut hole (default: '60%') */
  cutout?: string;
  /** Whether to show tooltip on hover (default: true) */
  showTooltip?: boolean;
  /** Type of label to show in tooltip: 'value', 'percentage', or 'both' (default: 'percentage') */
  tooltipLabelType?: TooltipLabelType;
  /** Custom tooltip formatter function */
  formatTooltip?: (label: string, value: number, percentage: number) => string;
  /** Font family for all chart text (default: "Poppins") */
  fontFamily?: string;
}

const DoughnutChart: React.FC<DoughnutChartProps> = ({
  data,
  height = 300,
  width,
  emphasizeLargest = true,
  showLegend = true,
  showDoughnutLabels = false,
  donutLabelType = "none",
  donutLabelColor = "#ffffff",
  donutLabelFontSize = 11,
  cutout = "60%",
  showTooltip = true,
  tooltipLabelType = "percentage",
  formatTooltip,
  fontFamily = "Poppins",
}) => {
  // Calculate total for percentage calculations
  const total = useMemo(() => {
    return data.reduce((sum, item) => sum + item.value, 0);
  }, [data]);

  // Find the largest value index
  const largestIndex = useMemo(() => {
    if (!emphasizeLargest || data.length === 0) return -1;
    let maxIndex = 0;
    let maxValue = data[0].value;
    data.forEach((item, index) => {
      if (item.value > maxValue) {
        maxValue = item.value;
        maxIndex = index;
      }
    });
    return maxIndex;
  }, [data, emphasizeLargest]);

  // Get color value from CSS variable or use directly
  const getColorValue = useCallback((color: string): string => {
    if (color.startsWith("--")) {
      if (typeof window !== "undefined") {
        const value = getComputedStyle(document.documentElement)
          .getPropertyValue(color)
          .trim();
        if (value) return value;
      }
      // Fallback colors if CSS variable not found
      const fallbackColors: Record<string, string> = {
        "--color-mw-primary-500": "#103860",
        "--color-mw-primary-400": "#2176cc",
        "--color-mw-secondary-200": "#94cfef",
        "--color-mw-secondary-100": "#cee9f8",
      };
      return fallbackColors[color] || "#2176cc";
    }
    return color;
  }, []);

  // Chart.js data configuration
  const chartJsData = useMemo(() => {
    const colors = data.map((item) => getColorValue(item.color));
    const offsets = data.map((_, index) =>
      emphasizeLargest && index === largestIndex ? 8 : 0,
    );

    return {
      labels: data.map((item) => item.label),
      datasets: [
        {
          data: data.map((item) => item.value),
          backgroundColor: colors,
          borderColor: "#ffffff",
          borderWidth: 2,
          borderRadius: 4, // Rounded edges
          spacing: 4, // Gap between segments
          offset: offsets, // Pull out the largest slice
        },
      ],
    };
  }, [data, largestIndex, emphasizeLargest, getColorValue]);

  // Generate tooltip label based on type
  const getTooltipLabel = useCallback(
    (label: string, value: number, percentage: number): string => {
      if (formatTooltip) {
        return formatTooltip(label, value, percentage);
      }
      switch (tooltipLabelType) {
        case "value":
          return `${label}: ${value}`;
        case "percentage":
          return `${label}: ${percentage}%`;
        case "both":
          return `${label}: ${value} (${percentage}%)`;
        default:
          return `${label}: ${percentage}%`;
      }
    },
    [tooltipLabelType, formatTooltip],
  );

  // Chart.js options configuration
  const chartOptions = useMemo<ChartOptions<"doughnut">>(() => {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false, // Hide default legend, we'll use custom one
        },
        tooltip: {
          enabled: showTooltip,
          callbacks: {
            label: (context: TooltipItem<"doughnut">) => {
              const label = context.label || "";
              const item = data[context.dataIndex];
              const value = context.parsed || 0;
              return getTooltipLabel(label, value, item.percentage);
            },
          },
          backgroundColor: "#FFFFFF",
          titleColor: "#505050",
          bodyColor: "#505050",
          borderColor: "#CCCCCC",
          borderWidth: 1,
          padding: 12,
          displayColors: true,
          boxPadding: 6,
          titleFont: {
            family: fontFamily,
          },
          bodyFont: {
            family: fontFamily,
          },
        },
        datalabels: {
          display: showDoughnutLabels && donutLabelType !== "none",
          color: donutLabelColor,
          font: {
            family: fontFamily,
            weight: "bold",
            size: donutLabelFontSize,
          },
          formatter: (value: number) => {
            if (!showDoughnutLabels || donutLabelType === "none") {
              return null;
            }
            if (donutLabelType === "percentage") {
              const percentage =
                total > 0 ? Math.round((value / total) * 100) : 0;
              return `${percentage}%`;
            }
            return value;
          },
        },
      },
      cutout: cutout,
    };
  }, [
    data,
    showDoughnutLabels,
    donutLabelType,
    donutLabelColor,
    donutLabelFontSize,
    cutout,
    total,
    showTooltip,
    getTooltipLabel,
    fontFamily,
  ]);

  // Get status badge variant
  const getStatusBadgeVariant = useCallback((status: string) => {
    const statusLower = status.toLowerCase();
    if (statusLower.includes("high") || statusLower.includes("demand")) {
      return "bg-mw-error-50! text-mw-error-500!" as const;
    }
    if (statusLower.includes("medium")) {
      return "bg-mw-warning-50! text-mw-warning-500!" as const;
    }
    return "bg-mw-success-50! text-mw-success-500!" as const;
  }, []);

  return (
    <div className="flex flex-col items-center gap-6">
      <div
        className="flex justify-center"
        style={{
          height: `${height}px`,
          width: width ? `${width}px` : "100%",
        }}
      >
        <Doughnut data={chartJsData} options={chartOptions} />
      </div>
      {showLegend && (
        <div className="w-full space-y-3">
          {data.map((item, index) => {
            const colorValue = getColorValue(item.color);
            return (
              <div
                key={index}
                className="flex items-center justify-between gap-3 w-full"
              >
                <div className="flex items-center gap-2 flex-1 min-w-0">
                  <div
                    className="w-4 h-4 rounded-sm shrink-0"
                    style={{ backgroundColor: colorValue }}
                  />
                  <span className="text-sm text-mw-neutral-700 font-normal truncate">
                    {item.label}
                  </span>
                  {item.status && (
                    <Badge
                      className={`${getStatusBadgeVariant(item.status)} shrink-0`}
                      size="sm"
                      variant="default"
                    >
                      {item.status}
                    </Badge>
                  )}
                </div>
                <span className="text-sm text-mw-neutral-700 font-medium shrink-0">
                  {item.percentage}%
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default DoughnutChart;
