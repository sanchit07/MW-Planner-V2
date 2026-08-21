import { useTranslate } from "@tolgee/react";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
  type Chart,
  type ChartOptions,
  type Plugin,
  type ScriptableContext,
  type TooltipItem,
} from "chart.js";
// Type-only side-effect import: augments plugins.datalabels in ChartOptions so
// we can disable it. The plugin is registered globally by the shared LineChart,
// which otherwise prints a value label on every point of this chart.
import "chartjs-plugin-datalabels";
import React, { useMemo } from "react";
import { Line } from "react-chartjs-2";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
);

interface ReachBuildChartProps {
  /** Cumulative overall reach (%) per day. */
  data: number[];
  /** Date labels aligned to `data`. */
  labels: string[];
  /** Number of selected inventories (shown in the subtitle). */
  inventoryCount: number;
  height?: number;
}

/** The Reach Build saturation chart (cumulative reach % over the campaign). */
const ReachBuildChart: React.FC<ReachBuildChartProps> = ({
  data,
  labels,
  inventoryCount,
  height = 260,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const LINE_COLOR = "#2563eb";
  const FONT = "Poppins";

  // Vertical gradient fill (line colour → transparent). Built from the chart
  // canvas, so it tracks the plot area on resize.
  const areaGradient = (ctx: ScriptableContext<"line">) => {
    const { chart } = ctx;
    const { ctx: canvasCtx, chartArea } = chart;
    if (!chartArea) return "rgba(37, 99, 235, 0.12)"; // first paint, no area yet
    const gradient = canvasCtx.createLinearGradient(
      0,
      chartArea.top,
      0,
      chartArea.bottom,
    );
    gradient.addColorStop(0, "rgba(37, 99, 235, 0.28)");
    gradient.addColorStop(1, "rgba(37, 99, 235, 0.02)");
    return gradient;
  };

  const chartData = useMemo(
    () => ({
      labels,
      datasets: [
        {
          label: tCampaigns("inventories.planSummary.reachBuild.title"),
          data,
          borderColor: LINE_COLOR,
          backgroundColor: areaGradient,
          fill: "origin" as const,
          borderWidth: 2.5,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: LINE_COLOR,
          pointHoverBorderColor: "#ffffff",
          pointHoverBorderWidth: 2,
        },
      ],
    }),
    [labels, data, tCampaigns],
  );

  const options = useMemo<ChartOptions<"line">>(
    () => ({
      responsive: true,
      maintainAspectRatio: false,
      layout: { padding: { top: 8, right: 8 } },
      interaction: { mode: "index", intersect: false },
      // Built-in animation off; the left-to-right reveal is handled by the
      // clip plugin below so the line + fill "draw" together cleanly.
      animation: false,
      plugins: {
        legend: { display: false },
        datalabels: { display: false },
        tooltip: {
          mode: "index",
          intersect: false,
          backgroundColor: "#ffffff",
          titleColor: "#1f2937",
          bodyColor: "#1f2937",
          borderColor: "#e5e7eb",
          borderWidth: 1,
          padding: 12,
          cornerRadius: 8,
          displayColors: false,
          titleFont: { family: FONT, size: 12, weight: "bold" },
          bodyFont: { family: FONT, size: 12 },
          callbacks: {
            label: (ctx: TooltipItem<"line">) =>
              `${tCampaigns("inventories.planSummary.tiles.reach")}: ${(
                ctx.parsed.y ?? 0
              ).toFixed(1)}%`,
          },
        },
      },
      scales: {
        x: {
          border: { display: false },
          grid: { display: false },
          ticks: {
            autoSkip: true,
            maxTicksLimit: 8,
            maxRotation: 0,
            color: "#374151",
            font: { family: FONT, size: 12, weight: "bold" },
            padding: 6,
          },
        },
        y: {
          beginAtZero: true,
          max: 100,
          border: { display: false },
          ticks: {
            stepSize: 25,
            color: "#374151",
            font: { family: FONT, size: 12, weight: "bold" },
            padding: 8,
            callback: (value) => `${value}%`,
          },
          grid: { color: "rgba(0, 0, 0, 0.05)" },
        },
      },
    }),
    [tCampaigns],
  );

  // Left-to-right "draw" reveal: clip the plot to a width that grows from 0 to
  // full on mount (and whenever the data changes). Re-created per data set so
  // the animation replays. Self-drives redraws via requestAnimationFrame.
  const revealPlugin = useMemo<Plugin<"line">>(() => {
    let start: number | null = null;
    let saved = false;
    const duration = 900;
    const easeOutQuart = (x: number) => 1 - Math.pow(1 - x, 4);
    return {
      id: "ltrReveal",
      beforeDatasetsDraw(chart: Chart<"line">) {
        const { ctx, chartArea } = chart;
        if (!ctx || !chartArea) return;
        if (start === null) start = performance.now();
        const progress = Math.min(1, (performance.now() - start) / duration);
        const width =
          (chartArea.right - chartArea.left) * easeOutQuart(progress);
        ctx.save();
        saved = true;
        ctx.beginPath();
        ctx.rect(
          chartArea.left,
          chartArea.top,
          width,
          chartArea.bottom - chartArea.top,
        );
        ctx.clip();
        if (progress < 1) {
          requestAnimationFrame(() => {
            // The chart may have been destroyed (unmount / data-driven
            // re-render) before this frame fires; drawing then crashes with
            // a null canvas context.
            if (chart.ctx && chart.canvas && chart.canvas.isConnected) {
              chart.draw();
            }
          });
        }
      },
      afterDatasetsDraw(chart: Chart<"line">) {
        if (saved) {
          chart.ctx.restore();
          saved = false;
        }
      },
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, labels]);

  return (
    <div className="rounded-xl border border-mw-neutral-100 bg-white p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-mw-grey-800">
          {tCampaigns("inventories.planSummary.reachBuild.title")}
        </h3>
        <span className="text-xs font-medium text-mw-neutral-500">
          {tCampaigns("inventories.planSummary.reachBuild.subtitle").replace(
            "%COUNT%",
            String(inventoryCount),
          )}
        </span>
      </div>
      <div style={{ height: `${height}px` }}>
        <Line data={chartData} options={options} plugins={[revealPlugin]} />
      </div>
    </div>
  );
};

export default ReachBuildChart;
