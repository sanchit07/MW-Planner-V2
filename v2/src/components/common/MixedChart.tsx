import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Tooltip,
  Legend,
  type ChartOptions,
  type TooltipItem,
} from "chart.js";
import ChartDataLabels from "chartjs-plugin-datalabels";
import React, { useMemo, useCallback } from "react";
import { Chart } from "react-chartjs-2";

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Tooltip,
  Legend,
  ChartDataLabels,
);

export interface DatasetLegendConfig {
  /** Whether to use point style for this dataset in legend (default: uses global legend.usePointStyle) */
  usePointStyle?: boolean;
  /** Point style for this dataset in legend (default: uses dataset.pointStyle or global legend.pointStyle) */
  pointStyle?:
    | "line"
    | "circle"
    | "rect"
    | "rectRounded"
    | "rectRot"
    | "cross"
    | "crossRot"
    | "star"
    | "triangle";
  /** Box width for this dataset in legend (default: uses global legend.boxWidth) */
  boxWidth?: number;
  /** Box height for this dataset in legend (default: uses global legend.boxHeight) */
  boxHeight?: number;
  /** Whether to show this dataset in legend (default: true) */
  showInLegend?: boolean;
}

export interface DatasetTooltipConfig {
  /** Whether to use point style for this dataset in tooltip (default: uses global tooltip.usePointStyle) */
  usePointStyle?: boolean;
  /** Point style for this dataset in tooltip (default: uses dataset.pointStyle or global tooltip settings) */
  pointStyle?:
    | "line"
    | "circle"
    | "rect"
    | "rectRounded"
    | "rectRot"
    | "cross"
    | "crossRot"
    | "star"
    | "triangle";
  /** Box width for this dataset in tooltip (default: uses global tooltip.boxWidth) */
  boxWidth?: number;
  /** Box height for this dataset in tooltip (default: uses global tooltip.boxHeight) */
  boxHeight?: number;
  /** Box padding for this dataset in tooltip (default: uses global tooltip.boxPadding) */
  boxPadding?: number;
}

export interface MixedChartDataset {
  label: string;
  data: number[];
  type: "bar" | "line";
  borderColor?: string;
  backgroundColor?: string;
  borderWidth?: number;
  borderRadius?: number;
  pointRadius?: number;
  pointHoverRadius?: number;
  pointStyle?:
    | "circle"
    | "cross"
    | "crossRot"
    | "dash"
    | "line"
    | "rect"
    | "rectRounded"
    | "rectRot"
    | "star"
    | "triangle"
    | "string";
  tension?: number;
  yAxisID?: string;
  order?: number;
  /** Per-dataset legend configuration */
  legendConfig?: DatasetLegendConfig;
  /** Per-dataset tooltip configuration */
  tooltipConfig?: DatasetTooltipConfig;
  /** Bar only: maximum bar width in pixels */
  maxBarThickness?: number;
  /** Bar only: percent (0-1) of category width the bar occupies; use < 1 to keep bar centered */
  barPercentage?: number;
}

export interface LegendConfig {
  /** Whether to show the legend (default: true) */
  show?: boolean;
  /** Position of the legend (default: "bottom") */
  position?: "top" | "bottom" | "left" | "right";
  /** Align of the legend (default: "center") */
  align?: "start" | "center" | "end";
  /** Whether to use full width for legend (default: false) */
  fullWidth?: boolean;
  /** Font size for legend labels (default: 12) */
  fontSize?: number;
  /** Color for legend labels (default: "#505050") */
  color?: string;
  /** Padding between legend items (default: 15) */
  padding?: number;
  /** Whether to use point style (default: true) */
  usePointStyle?: boolean;
  /** Point style for line charts: "line" | "circle" | "rect" | "rectRounded" | "rectRot" | "cross" | "crossRot" | "star" | "triangle" (default: "line") */
  pointStyle?:
    | "line"
    | "circle"
    | "rect"
    | "rectRounded"
    | "rectRot"
    | "cross"
    | "crossRot"
    | "star"
    | "triangle";
  /** Width of the legend box (default: 20) */
  boxWidth?: number;
  /** Height of the legend box (default: 2) */
  boxHeight?: number;
}

export interface TooltipConfig {
  /** Whether to show tooltip (default: true) */
  show?: boolean;
  /** Tooltip interaction mode (default: "index") */
  mode?: "index" | "nearest" | "point" | "x" | "y" | "dataset";
  /** Whether tooltip intersects with element (default: false) */
  intersect?: boolean;
  /** Background color of tooltip (default: "#FFFFFF") */
  backgroundColor?: string;
  /** Title text color (default: "#505050") */
  titleColor?: string;
  /** Body text color (default: "#505050") */
  bodyColor?: string;
  /** Border color (default: "#CCCCCC") */
  borderColor?: string;
  /** Border width (default: 1) */
  borderWidth?: number;
  /** Padding inside tooltip (default: 12) */
  padding?: number;
  /** Whether to display color boxes in tooltip (default: true) */
  displayColors?: boolean;
  /** Whether to use point style in tooltip color boxes (default: true when usePointStyle in legend is true) */
  usePointStyle?: boolean;
  /** Width of color box in tooltip (default: 12) */
  boxWidth?: number;
  /** Height of color box in tooltip (default: 12) */
  boxHeight?: number;
  /** Padding between color box and text in tooltip (default: 6) */
  boxPadding?: number;
  /** Custom formatter for tooltip title */
  formatTitle?: (items: TooltipItem<"bar" | "line">[]) => string;
  /** Custom formatter for tooltip value (already exists as formatTooltipValue) */
  formatValue?: (value: number, label: string, datasetLabel: string) => string;
}

export interface AxisConfig {
  /** Whether to display the axis (default: true) */
  display?: boolean;
  /** Whether to display grid lines (default: true for y, false for x) */
  gridDisplay?: boolean;
  /** Grid line color (default: "#E2E8F0" for y) */
  gridColor?: string;
  /** Ticks font size (default: 12 for y, 11 for x) */
  ticksFontSize?: number;
  /** Ticks color (default: "#505050") */
  ticksColor?: string;
  /** Title color (default: "#505050") */
  titleColor?: string;
  /** Title font size (default: 12) */
  titleFontSize?: number;
}

export interface DataLabelsConfig {
  /** Whether to show data labels (default: false) */
  show?: boolean;
  /** Color of data labels (default: "#505050") */
  color?: string;
  /** Font size of data labels (default: 11) */
  fontSize?: number;
  /** Font weight of data labels (default: "normal") */
  fontWeight?: "normal" | "bold" | number;
  /** Custom formatter for data labels */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  formatter?: (value: number, context: any) => string | number | null;
  /** Anchor position: "center" | "start" | "end" (default: "center") */
  anchor?: "center" | "start" | "end";
  /** Alignment: "center" | "start" | "end" | "right" | "bottom" | "left" | "top" (default: "center") */
  align?: "center" | "start" | "end" | "right" | "bottom" | "left" | "top";
  /** Offset from anchor point (default: 0) */
  offset?: number;
  /** Whether to clamp labels within the chart area (default: false) */
  clamp?: boolean;
  /** Whether to clip labels outside the chart area (default: false) */
  clip?: boolean;
  /** Show labels only for specific dataset types: "bar" | "line" | "both" (default: "both") */
  displayFor?: "bar" | "line" | "both";
}

export interface MixedChartProps {
  title?: string;
  labels: string[];
  datasets: MixedChartDataset[];
  height?: number;
  /** Y-axis label */
  yAxisLabel?: string;
  /** Y1-axis label (right axis) */
  y1AxisLabel?: string;
  /** Y-axis minimum value */
  yAxisMin?: number;
  /** Y-axis maximum value */
  yAxisMax?: number;
  /** Y1-axis minimum value */
  y1AxisMin?: number;
  /** Y1-axis maximum value */
  y1AxisMax?: number;
  /** Format function for Y-axis values */
  formatYAxisValue?: (value: number) => string;
  /** Format function for Y1-axis values */
  formatY1AxisValue?: (value: number) => string;
  /** Format function for tooltip values (deprecated, use tooltip.formatValue) */
  formatTooltipValue?: (value: number, label: string) => string;
  className?: string;
  /** Whether to show legend (deprecated, use legend.show) */
  showLegend?: boolean;
  /** Legend position (deprecated, use legend.position) */
  legendPosition?: "top" | "bottom" | "left" | "right";
  /** Legend configuration */
  legend?: LegendConfig;
  /** Tooltip configuration */
  tooltip?: TooltipConfig;
  /** X-axis configuration */
  xAxis?: AxisConfig;
  /** Y-axis configuration */
  yAxis?: AxisConfig;
  /** Y1-axis configuration (right axis) */
  y1Axis?: AxisConfig;
  /** Chart interaction mode (default: "index") */
  interactionMode?: "index" | "nearest" | "point" | "x" | "y" | "dataset";
  /** Chart interaction intersect (default: false) */
  interactionIntersect?: boolean;
  /** Whether to begin Y-axis at zero (default: true) */
  yAxisBeginAtZero?: boolean;
  /** Whether to begin Y1-axis at zero (default: true) */
  y1AxisBeginAtZero?: boolean;
  /** Whether to draw Y1 grid on chart area (default: false) */
  y1AxisGridDrawOnChartArea?: boolean;
  /** Data labels configuration */
  dataLabels?: DataLabelsConfig;
  /** Custom Chart.js options to override or extend default options */
  customChartOptions?: Partial<ChartOptions<"bar" | "line">>;
  /** Font family for all chart text (default: "Poppins") */
  fontFamily?: string;
}

const MixedChart: React.FC<MixedChartProps> = ({
  title,
  labels,
  datasets,
  height = 400,
  yAxisLabel,
  y1AxisLabel,
  yAxisMin = 0,
  yAxisMax,
  y1AxisMin = 0,
  y1AxisMax,
  formatYAxisValue,
  formatY1AxisValue,
  formatTooltipValue,
  className,
  showLegend,
  legendPosition,
  legend = {},
  tooltip = {},
  xAxis = {},
  yAxis = {},
  y1Axis = {},
  interactionMode = "index",
  interactionIntersect = false,
  yAxisBeginAtZero = true,
  y1AxisBeginAtZero = true,
  y1AxisGridDrawOnChartArea = false,
  dataLabels = {},
  customChartOptions,
  fontFamily = "Poppins",
}) => {
  // Deep merge function for chart options
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const deepMerge = useCallback((target: any, source: any): any => {
    const output = { ...target };
    if (isObject(target) && isObject(source)) {
      Object.keys(source).forEach((key) => {
        if (isObject(source[key])) {
          if (!(key in target)) {
            Object.assign(output, { [key]: source[key] });
          } else {
            output[key] = deepMerge(target[key], source[key]);
          }
        } else {
          Object.assign(output, { [key]: source[key] });
        }
      });
    }
    return output;
  }, []);

  // Helper to check if value is an object
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const isObject = (item: any): boolean => {
    return item && typeof item === "object" && !Array.isArray(item);
  };

  // Get color value from CSS variable
  const getColorValue = useCallback((cssVar: string): string => {
    if (cssVar.startsWith("--") && typeof window !== "undefined") {
      return (
        getComputedStyle(document.documentElement)
          .getPropertyValue(cssVar)
          .trim() || "#2176cc"
      );
    }
    return cssVar;
  }, []);

  // Prepare chart data
  const chartData = useMemo(() => {
    return {
      labels,
      datasets: datasets.map((dataset, index) => {
        const baseConfig = {
          label: dataset.label,
          data: dataset.data,
          type: dataset.type,
          yAxisID: dataset.yAxisID || "y",
          order: dataset.order ?? (dataset.type === "line" ? 0 : 1),
        };

        if (dataset.type === "line") {
          return {
            ...baseConfig,
            borderColor:
              dataset.borderColor ||
              getColorValue("--color-mw-error-500") ||
              "#c52828",
            backgroundColor:
              dataset.backgroundColor ||
              getColorValue("--color-mw-error-500") ||
              "#c52828",
            borderWidth: dataset.borderWidth ?? 2,
            pointRadius: dataset.pointRadius ?? 4,
            pointHoverRadius: dataset.pointHoverRadius ?? 6,
            ...(dataset.pointStyle && { pointStyle: dataset.pointStyle }),
            tension: dataset.tension ?? 0.1,
            fill: false,
          };
        }

        // Bar type
        return {
          ...baseConfig,
          borderColor:
            dataset.borderColor ||
            (index === 0
              ? getColorValue("--color-mw-primary-500") || "#2176cc"
              : getColorValue("--color-mw-secondary-400") || "#87CAED"),
          backgroundColor:
            dataset.backgroundColor ||
            (index === 0
              ? getColorValue("--color-mw-primary-500") || "#2176cc"
              : getColorValue("--color-mw-secondary-400") || "#87CAED"),
          borderWidth: dataset.borderWidth ?? 0,
          borderRadius: dataset.borderRadius ?? 4,
          borderSkipped: false,
          // For bars, pointStyle is used in legend when usePointStyle is true
          ...(dataset.pointStyle && { pointStyle: dataset.pointStyle }),
          ...(dataset.maxBarThickness !== undefined && {
            maxBarThickness: dataset.maxBarThickness,
          }),
          ...(dataset.barPercentage !== undefined && {
            barPercentage: dataset.barPercentage,
          }),
        };
      }),
    };
  }, [labels, datasets, getColorValue]);

  // Check if we need dual y-axes
  const hasDualAxes = datasets.some((d) => d.yAxisID === "y1");

  // Generate custom labels for per-dataset legend configuration
  const generateLabels = useCallback(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (chart: any) => {
      const original = ChartJS.defaults.plugins.legend.labels.generateLabels;
      const defaultLabels = original ? original(chart) : [];

      return (
        defaultLabels
          .map(
            (
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              label: any,
              index: number,
            ) => {
              const dataset = datasets[index];
              if (!dataset) return label;

              const datasetLegendConfig = dataset.legendConfig || {};

              // Check if dataset should be shown in legend
              if (datasetLegendConfig.showInLegend === false) {
                return null;
              }

              // Use dataset legendConfig only - no global fallback
              const datasetUsePointStyle =
                datasetLegendConfig.usePointStyle !== undefined
                  ? datasetLegendConfig.usePointStyle
                  : true; // Default to true if not specified

              // Determine pointStyle for this dataset
              let datasetPointStyle:
                | "line"
                | "circle"
                | "rect"
                | "rectRounded"
                | "rectRot"
                | "cross"
                | "crossRot"
                | "star"
                | "triangle"
                | undefined = datasetLegendConfig.pointStyle;

              // If no pointStyle in legendConfig but usePointStyle is true, use dataset's pointStyle
              if (!datasetPointStyle && datasetUsePointStyle) {
                const validPointStyles = [
                  "line",
                  "circle",
                  "rect",
                  "rectRounded",
                  "rectRot",
                  "cross",
                  "crossRot",
                  "star",
                  "triangle",
                ];
                if (
                  dataset.pointStyle &&
                  validPointStyles.includes(dataset.pointStyle)
                ) {
                  datasetPointStyle = dataset.pointStyle as
                    | "line"
                    | "circle"
                    | "rect"
                    | "rectRounded"
                    | "rectRot"
                    | "cross"
                    | "crossRot"
                    | "star"
                    | "triangle";
                }
              }

              // If usePointStyle is false, don't set pointStyle
              if (!datasetUsePointStyle) {
                datasetPointStyle = undefined;
              }

              // Determine box dimensions for this dataset
              const datasetBoxWidth =
                datasetLegendConfig.boxWidth ??
                (datasetUsePointStyle ? 12 : 20);
              const datasetBoxHeight =
                datasetLegendConfig.boxHeight ??
                (datasetUsePointStyle ? 12 : 2);

              return {
                ...label,
                usePointStyle: datasetUsePointStyle,
                ...(datasetPointStyle && { pointStyle: datasetPointStyle }),
                ...(datasetBoxWidth && { boxWidth: datasetBoxWidth }),
                ...(datasetBoxHeight && { boxHeight: datasetBoxHeight }),
              };
            },
          )
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .filter((label: any) => label !== null)
      );
    },
    [datasets],
  );

  // Calculate tooltip usePointStyle value - default to true
  const tooltipUsePointStyleValue =
    tooltip.usePointStyle !== undefined ? tooltip.usePointStyle : true;

  // Custom plugin for per-dataset tooltip configuration
  const tooltipPlugin = useMemo(() => {
    return {
      id: "perDatasetTooltip",
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      afterTooltipDraw: (chart: any) => {
        // Use requestAnimationFrame to ensure DOM is fully rendered
        requestAnimationFrame(() => {
          const tooltipModel = chart.tooltip;
          if (
            !tooltipModel ||
            !tooltipModel.dataPoints ||
            tooltipModel.dataPoints.length === 0
          ) {
            return;
          }

          // Find the tooltip element - Chart.js creates it in the canvas parent or body
          let tooltipEl = chart.canvas.parentNode?.querySelector(
            ".chartjs-tooltip",
          ) as HTMLElement;
          if (!tooltipEl) {
            tooltipEl = document.querySelector(
              ".chartjs-tooltip",
            ) as HTMLElement;
          }
          if (!tooltipEl) return;

          // Update each tooltip item based on dataset's tooltipConfig
          tooltipModel.dataPoints.forEach(
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            (dataPoint: any, index: number) => {
              const dataset = datasets[dataPoint.datasetIndex];
              if (!dataset) return;

              const datasetTooltipConfig = dataset.tooltipConfig || {};
              const datasetUsePointStyle =
                datasetTooltipConfig.usePointStyle !== undefined
                  ? datasetTooltipConfig.usePointStyle
                  : tooltipUsePointStyleValue;

              // Find the corresponding tooltip item element
              // Chart.js tooltip structure: .chartjs-tooltip > .chartjs-tooltip-body > .chartjs-tooltip-item
              const tooltipBody = tooltipEl.querySelector(
                ".chartjs-tooltip-body",
              ) as HTMLElement;
              const tooltipItems = tooltipBody
                ? tooltipBody.querySelectorAll(".chartjs-tooltip-item")
                : tooltipEl.querySelectorAll(".chartjs-tooltip-item");

              const itemEl = tooltipItems[index] as HTMLElement;
              if (!itemEl) return;

              // Find the color box - Chart.js uses a span with inline styles
              // Try multiple approaches to find the color indicator
              let colorBox = itemEl.querySelector("span") as HTMLElement;
              if (!colorBox || !colorBox.style.backgroundColor) {
                // Look for any element with background color
                const allSpans = itemEl.querySelectorAll("span");
                for (let i = 0; i < allSpans.length; i++) {
                  const span = allSpans[i] as HTMLElement;
                  if (
                    span.style.backgroundColor ||
                    span.style.background ||
                    getComputedStyle(span).backgroundColor !==
                      "rgba(0, 0, 0, 0)"
                  ) {
                    colorBox = span;
                    break;
                  }
                }
              }

              // If still not found, try finding by class or any colored element
              if (!colorBox || !colorBox.style.backgroundColor) {
                const coloredElements = Array.from(
                  itemEl.querySelectorAll("*"),
                ).filter((el) => {
                  const style = window.getComputedStyle(el as Element);
                  return (
                    style.backgroundColor !== "rgba(0, 0, 0, 0)" &&
                    style.backgroundColor !== "transparent"
                  );
                });
                if (coloredElements.length > 0) {
                  colorBox = coloredElements[0] as HTMLElement;
                }
              }

              if (!colorBox) return;

              // Update point style based on tooltipConfig
              if (datasetUsePointStyle && datasetTooltipConfig.pointStyle) {
                const pointStyle = datasetTooltipConfig.pointStyle;
                // Apply point style via CSS
                if (pointStyle === "circle") {
                  colorBox.style.borderRadius = "50%";
                  colorBox.style.width = `${datasetTooltipConfig.boxWidth ?? 12}px`;
                  colorBox.style.height = `${datasetTooltipConfig.boxHeight ?? 12}px`;
                  colorBox.style.display = "inline-block";
                } else if (pointStyle === "line") {
                  colorBox.style.borderRadius = "0";
                  colorBox.style.width = `${datasetTooltipConfig.boxWidth ?? 20}px`;
                  colorBox.style.height = `${datasetTooltipConfig.boxHeight ?? 2}px`;
                  colorBox.style.display = "inline-block";
                } else if (pointStyle === "rect") {
                  colorBox.style.borderRadius = "0";
                  colorBox.style.width = `${datasetTooltipConfig.boxWidth ?? 12}px`;
                  colorBox.style.height = `${datasetTooltipConfig.boxHeight ?? 12}px`;
                  colorBox.style.display = "inline-block";
                } else {
                  // For other point styles, use appropriate dimensions
                  colorBox.style.borderRadius = "0";
                  colorBox.style.width = `${datasetTooltipConfig.boxWidth ?? 12}px`;
                  colorBox.style.height = `${datasetTooltipConfig.boxHeight ?? 12}px`;
                  colorBox.style.display = "inline-block";
                }
              } else if (!datasetUsePointStyle) {
                // Use box style
                colorBox.style.borderRadius = "0";
                colorBox.style.width = `${datasetTooltipConfig.boxWidth ?? 20}px`;
                colorBox.style.height = `${datasetTooltipConfig.boxHeight ?? 2}px`;
                colorBox.style.display = "inline-block";
              }

              // Update box dimensions if specified
              if (datasetTooltipConfig.boxWidth !== undefined) {
                colorBox.style.width = `${datasetTooltipConfig.boxWidth}px`;
              }
              if (datasetTooltipConfig.boxHeight !== undefined) {
                colorBox.style.height = `${datasetTooltipConfig.boxHeight}px`;
              }
            },
          );
        });
      },
    };
  }, [datasets, tooltipUsePointStyleValue]);

  // Chart options
  const chartOptions = useMemo<ChartOptions<"bar" | "line">>(() => {
    // Legend configuration with defaults
    // Support backward compatibility with showLegend and legendPosition
    const legendShow =
      showLegend !== undefined ? showLegend : legend.show !== false;
    const legendPos = legendPosition || legend.position || "bottom";

    const legendConfig = {
      display: legendShow,
      position: legendPos as "top" | "bottom" | "left" | "right",
      align: (legend.align || "center") as "start" | "center" | "end",
      fullWidth: legend.fullWidth || false,
      labels: {
        // Default to usePointStyle true, but each dataset's legendConfig will override
        usePointStyle: true,
        padding: legend.padding ?? 15,
        font: {
          family: fontFamily,
          size: legend.fontSize ?? 12,
        },
        color: legend.color || "#505050",
        // Use custom label generator for per-dataset configuration
        generateLabels: generateLabels,
      },
    };

    // Tooltip configuration with defaults
    // Use point style in tooltip if explicitly set, otherwise default to true
    const tooltipUsePointStyle =
      tooltip.usePointStyle !== undefined ? tooltip.usePointStyle : true;

    const tooltipConfig = {
      enabled: tooltip.show !== false,
      mode: (tooltip.mode || "index") as
        | "index"
        | "nearest"
        | "point"
        | "x"
        | "y"
        | "dataset",
      intersect: tooltip.intersect || false,
      backgroundColor: tooltip.backgroundColor || "#FFFFFF",
      titleColor: tooltip.titleColor || "#505050",
      bodyColor: tooltip.bodyColor || "#505050",
      borderColor: tooltip.borderColor || "#CCCCCC",
      borderWidth: tooltip.borderWidth ?? 1,
      padding: tooltip.padding ?? 12,
      displayColors: tooltip.displayColors !== false,
      // Set usePointStyle to true to enable point styles, but we'll override per-dataset via plugin
      usePointStyle: true,
      boxWidth: tooltip.boxWidth ?? 12,
      boxHeight: tooltip.boxHeight ?? 12,
      boxPadding: tooltip.boxPadding ?? 6,
      titleFont: {
        family: fontFamily,
      },
      bodyFont: {
        family: fontFamily,
      },
      callbacks: {
        title: tooltip.formatTitle,
        label: (context: TooltipItem<"bar" | "line">) => {
          const value = context.parsed.y ?? 0;
          const label = context.dataset.label || "";
          const datasetLabel = context.dataset.label || "";

          // Use new formatValue if provided, otherwise fallback to formatTooltipValue, then default
          if (tooltip.formatValue) {
            return tooltip.formatValue(value, label, datasetLabel);
          }
          if (formatTooltipValue) {
            return formatTooltipValue(value, label);
          }
          return `${label}: ${value}`;
        },
        labelColor: (context: TooltipItem<"bar" | "line">) => {
          const dataset = datasets[context.datasetIndex];
          if (!dataset) {
            return {
              borderColor: context.dataset.borderColor as string,
              backgroundColor: context.dataset.backgroundColor as string,
            };
          }

          const datasetTooltipConfig = dataset.tooltipConfig || {};

          // Determine usePointStyle for this dataset
          const datasetUsePointStyle =
            datasetTooltipConfig.usePointStyle !== undefined
              ? datasetTooltipConfig.usePointStyle
              : tooltipUsePointStyle;

          // Use tooltipConfig pointStyle if provided, otherwise fallback to dataset pointStyle
          let datasetPointStyle = datasetTooltipConfig.pointStyle;
          if (!datasetPointStyle && datasetUsePointStyle) {
            // If usePointStyle is true but no tooltipConfig pointStyle, use dataset's pointStyle
            const validPointStyles = [
              "line",
              "circle",
              "rect",
              "rectRounded",
              "rectRot",
              "cross",
              "crossRot",
              "star",
              "triangle",
            ];
            if (
              dataset.pointStyle &&
              validPointStyles.includes(dataset.pointStyle)
            ) {
              datasetPointStyle = dataset.pointStyle as
                | "line"
                | "circle"
                | "rect"
                | "rectRounded"
                | "rectRot"
                | "cross"
                | "crossRot"
                | "star"
                | "triangle";
            }
          }
          if (!datasetPointStyle && !datasetUsePointStyle) {
            // If usePointStyle is false, use global default (line for tooltip)
            datasetPointStyle = "line" as
              | "line"
              | "circle"
              | "rect"
              | "rectRounded"
              | "rectRot"
              | "cross"
              | "crossRot"
              | "star"
              | "triangle"
              | undefined;
          }

          // Determine box dimensions for this dataset
          const datasetBoxWidth =
            datasetTooltipConfig.boxWidth ??
            (datasetUsePointStyle
              ? 12
              : (tooltip.boxWidth ?? (tooltipUsePointStyle ? 12 : 20)));
          const datasetBoxHeight =
            datasetTooltipConfig.boxHeight ??
            (datasetUsePointStyle
              ? 12
              : (tooltip.boxHeight ?? (tooltipUsePointStyle ? 12 : 2)));
          const datasetBoxPadding =
            datasetTooltipConfig.boxPadding ?? tooltip.boxPadding ?? 6;

          return {
            borderColor: context.dataset.borderColor as string,
            backgroundColor: context.dataset.backgroundColor as string,
            // Chart.js labelColor callback can return pointStyle to override dataset pointStyle
            ...(datasetUsePointStyle &&
              datasetPointStyle && {
                pointStyle: datasetPointStyle,
              }),
            ...(datasetBoxWidth && { boxWidth: datasetBoxWidth }),
            ...(datasetBoxHeight && { boxHeight: datasetBoxHeight }),
            ...(datasetBoxPadding && { boxPadding: datasetBoxPadding }),
          };
        },
      },
    };

    const defaultOptions: ChartOptions<"bar" | "line"> = {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: interactionMode as
          | "index"
          | "nearest"
          | "point"
          | "x"
          | "y"
          | "dataset",
        intersect: interactionIntersect,
      },
      plugins: {
        legend: legendConfig,
        tooltip: tooltipConfig,
        datalabels: {
          display: dataLabels.show || false,
          color: dataLabels.color || "#505050",
          font: {
            family: fontFamily,
            size: dataLabels.fontSize ?? 11,
            weight: dataLabels.fontWeight || "normal",
          },
          anchor: dataLabels.anchor || "center",
          align: dataLabels.align || "center",
          offset: dataLabels.offset ?? 0,
          clamp: dataLabels.clamp || false,
          clip: dataLabels.clip || false,
          formatter: dataLabels.formatter || ((value: number) => value),
          // Show labels only for specified dataset types
          ...(dataLabels.displayFor &&
            dataLabels.displayFor !== "both" && {
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              filter: (context: any) => {
                const datasetType = datasets[context.datasetIndex]?.type;
                return datasetType === dataLabels.displayFor;
              },
            }),
        },
      },
      scales: {
        x: {
          display: xAxis.display !== false,
          grid: {
            display: xAxis.gridDisplay ?? false,
            color: xAxis.gridColor,
          },
          ticks: {
            font: {
              family: fontFamily,
              size: xAxis.ticksFontSize ?? 11,
            },
            color: xAxis.ticksColor || "#505050",
          },
        },
        y: {
          type: "linear",
          display: yAxis.display !== false,
          position: "left",
          beginAtZero: yAxisBeginAtZero,
          min: yAxisMin,
          max: yAxisMax,
          ticks: {
            font: {
              family: fontFamily,
              size: yAxis.ticksFontSize ?? 12,
            },
            color: yAxis.ticksColor || "#505050",
            callback: function (value) {
              if (formatYAxisValue) {
                return formatYAxisValue(Number(value));
              }
              return value;
            },
          },
          grid: {
            display: yAxis.gridDisplay !== false,
            color: yAxis.gridColor || "#E2E8F0",
            drawOnChartArea: true,
          },
          ...(yAxisLabel && {
            title: {
              display: true,
              text: yAxisLabel,
              color: yAxis.titleColor || "#505050",
              font: {
                family: fontFamily,
                size: yAxis.titleFontSize ?? 12,
              },
            },
          }),
        },
        ...(hasDualAxes && {
          y1: {
            type: "linear" as const,
            display: y1Axis.display !== false,
            position: "right" as const,
            beginAtZero: y1AxisBeginAtZero,
            min: y1AxisMin,
            max: y1AxisMax,
            grid: {
              display: y1Axis.gridDisplay !== false,
              color: y1Axis.gridColor,
              drawOnChartArea: y1AxisGridDrawOnChartArea,
            },
            ticks: {
              font: {
                family: fontFamily,
                size: y1Axis.ticksFontSize ?? 12,
              },
              color: y1Axis.ticksColor || "#505050",
              callback: function (value: string | number) {
                if (formatY1AxisValue) {
                  return formatY1AxisValue(Number(value));
                }
                return value;
              },
            },
            ...(y1AxisLabel && {
              title: {
                display: true,
                text: y1AxisLabel,
                color: y1Axis.titleColor || "#505050",
                font: {
                  family: fontFamily,
                  size: y1Axis.titleFontSize ?? 12,
                },
              },
            }),
          },
        }),
      },
    };

    // Merge with custom chart options if provided
    if (customChartOptions) {
      return deepMerge(defaultOptions, customChartOptions);
    }

    return defaultOptions;
  }, [
    showLegend,
    legendPosition,
    legend,
    tooltip,
    xAxis,
    yAxis,
    y1Axis,
    formatYAxisValue,
    formatY1AxisValue,
    formatTooltipValue,
    yAxisLabel,
    y1AxisLabel,
    yAxisMin,
    yAxisMax,
    y1AxisMin,
    y1AxisMax,
    hasDualAxes,
    interactionMode,
    interactionIntersect,
    yAxisBeginAtZero,
    y1AxisBeginAtZero,
    y1AxisGridDrawOnChartArea,
    dataLabels,
    datasets,
    customChartOptions,
    deepMerge,
    fontFamily,
    generateLabels,
  ]);

  return (
    <div className={className} style={{ height: `${height}px` }}>
      {title && (
        <h3 className="text-base font-medium leading-5 mb-4 text-mw-black">
          {title}
        </h3>
      )}
      <Chart
        type="bar"
        data={chartData}
        options={chartOptions}
        plugins={[tooltipPlugin]}
      />
    </div>
  );
};

export default MixedChart;
