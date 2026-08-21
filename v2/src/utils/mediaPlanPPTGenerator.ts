import PptxGenJS from "pptxgenjs";

import { normalizeGoalType } from "./budget.utils";
import { formatCurrency } from "./campaign.utils";
import { formatDisplayDate } from "./dateUtils";
import { getPPTTranslations } from "./pptTranslations";
import { getCssVariableValue, hexToRgbString } from "./themeColors";
import backLogo from "../assets/images/media-plan-bg.jpg";
import {
  buildPlanReasons,
  computeAudienceActivityByDay,
  computeConcentrationIndex,
  CONCENTRATION_INDEX_MAX,
  computeGoalRoadmap,
  humaniseSegment,
  resolveGoalForecast,
} from "../pages/campaigns/media-plan/utils";
import {
  CostSplitByCampaignData,
  MediaPlanResponse,
  Targeting,
} from "../types/campaign.types";
import { InventoryItem } from "../types/inventory.types";

interface Theme {
  id: string;
  name: string;
  colors: { primary: string; secondary: string; accent: string };
}

interface DeliveryData {
  granularity: string;
  bins: Array<{ label: string; value: number }>;
  peakLabel: string;
  peakValue: number;
}

/** Which of the redesigned media-plan sections to include as slides. */
export interface SlideVisibilityConfig {
  titleSlide?: boolean;
  performanceMetrics?: boolean;
  inventoryMix?: boolean;
  targeting?: boolean;
  audienceTrends?: boolean;
  geographicPlan?: boolean;
  audienceMap?: boolean;
  goalsKpis?: boolean;
  inventorySnapshots?: boolean;
  whyPlan?: boolean;
}

interface PPTGenerationOptions {
  mediaPlan: MediaPlanResponse;
  costSplitData?: CostSplitByCampaignData[];
  theme: Theme;
  fileName?: string;
  mapImage?: string;
  mapImageLink?: string;
  // Extra data for the redesigned slides
  geographySummary?: {
    cityCount: number;
    countryCount: number;
    poiCount: number;
  };
  channelCount?: number;
  targeting?: Targeting;
  /** Campaign id — seeds the untargeted-axis fallback in the concentration index. */
  campaignId?: string;
  /** Campaign goal, for the "Why This Plan Works" milestone roadmap. */
  goalType?: string;
  targetValue?: number;
  costSplitByCity?: CostSplitByCampaignData[];
  selectedInventoryLocations?: InventoryItem[];
  delivery?: DeliveryData;
  /** Cumulative reach-build curve for the Audience Trends slide. */
  reachCurve?: { data: number[]; labels: string[] };
  // Retained for backwards compatibility; unused (charts rebuilt natively).
  scheduleChartImage?: string;
  selectedInventoryChartImage?: string;
  slideVisibility?: SlideVisibilityConfig;
}

const DEFAULT_SLIDE_VISIBILITY: SlideVisibilityConfig = {
  titleSlide: true,
  performanceMetrics: true,
  inventoryMix: true,
  targeting: true,
  audienceTrends: true,
  geographicPlan: true,
  audienceMap: true,
  goalsKpis: true,
  inventorySnapshots: true,
  whyPlan: true,
};

// LAYOUT_WIDE = 13.33in x 7.5in
const SLIDE_W = 13.33;
const MARGIN = 0.5;
const CONTENT_W = SLIDE_W - MARGIN * 2;
const MS_PER_DAY = 1000 * 60 * 60 * 24;

const WHITE = "FFFFFF";
const INK = "1A1A2E";
const MUTED = "8A94A6";
const CARD_FILL = "F5F7FA";
const CARD_LINE = "E2E8F0";
const OVER_TARGET_COLOR = "22C55E"; // green — forecast exceeding target
const FONT = "Calibri";

const durationDays = (start?: string, end?: string): number => {
  if (!start || !end) return 0;
  const d =
    Math.floor(
      (new Date(end).getTime() - new Date(start).getTime()) / MS_PER_DAY,
    ) + 1;
  return d > 0 ? d : 0;
};

const compact = (n: number): string => {
  if (!n) return "0";
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K`;
  return `${Math.round(n)}`;
};

const titleCaseCode = (code: string): string =>
  code
    .split(/[-_]/)
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");

/** Media-owner avatar initials (first letters of first two words). */
const ownerInitials = (name: string): string =>
  name
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("") || "—";

/** Fetch an image URL and return a base64 data URI so it can be embedded in
 * the PPTX (pptxgenjs cannot reliably reference remote URLs offline). Resolves
 * to undefined on any failure (network / CORS / decode) — caller falls back. */
const fetchImageDataUri = async (
  url?: string | null,
): Promise<string | undefined> => {
  if (!url) return undefined;
  try {
    const res = await fetch(url);
    if (!res.ok) return undefined;
    const blob = await res.blob();
    return await new Promise<string | undefined>((resolve) => {
      const reader = new FileReader();
      reader.onloadend = () =>
        resolve(typeof reader.result === "string" ? reader.result : undefined);
      reader.onerror = () => resolve(undefined);
      reader.readAsDataURL(blob);
    });
  } catch {
    return undefined;
  }
};

export const generateMediaPlanPPT = async (
  options: PPTGenerationOptions,
): Promise<void> => {
  const {
    mediaPlan,
    theme,
    fileName,
    costSplitData = [],
    geographySummary = { cityCount: 0, countryCount: 0, poiCount: 0 },
    channelCount = 0,
    targeting,
    campaignId = "",
    goalType,
    targetValue = 0,
    costSplitByCity = [],
    selectedInventoryLocations = [],
    delivery,
    reachCurve,
    mapImage,
    mapImageLink,
    slideVisibility = DEFAULT_SLIDE_VISIBILITY,
  } = options;

  const translations = await getPPTTranslations();
  // Dot-path translation lookup (covers the redesigned sections without
  // widening the typed translation interface for every new key).
  const T = (path: string): string => {
    let o: unknown = translations;
    for (const k of path.split(".")) o = (o as Record<string, unknown>)?.[k];
    return typeof o === "string" ? o : "";
  };
  const fill = (
    path: string,
    params: Record<string, string | number>,
  ): string =>
    Object.entries(params).reduce(
      (s, [k, v]) => s.replace(`{${k}}`, String(v)),
      T(path),
    );

  const { headerInfo, brandDetails, performanceMetrics, audienceDemographics } =
    mediaPlan;
  const perf = performanceMetrics;
  const currency = headerInfo?.currency;
  const money = (n?: number) => formatCurrency(n || 0, currency);
  // SOV / AD_PLAYS goals are priced per spot → relabel aggregate CPM as CPS.
  const goalNorm = normalizeGoalType(goalType);
  const isCPSGoal = goalNorm === "SOV" || goalNorm === "ADPLAYS";

  const pptx = new PptxGenJS();
  pptx.layout = "LAYOUT_WIDE";
  const primary = hexToRgbString(getCssVariableValue(theme.colors.primary));

  // ── Shared helpers ───────────────────────────────────────────────────────
  const banner = (
    s: PptxGenJS.Slide,
    title: string,
    subtitle: string,
  ): void => {
    s.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 0,
      w: "100%",
      h: 1.0,
      fill: { color: primary },
      line: { type: "none" },
    });
    s.addText(
      [
        {
          text: title,
          options: { fontSize: 22, bold: true, color: WHITE, breakLine: true },
        },
        { text: subtitle, options: { fontSize: 11, color: "E6ECF5" } },
      ],
      {
        x: MARGIN,
        y: 0.12,
        w: CONTENT_W,
        h: 0.8,
        align: "left",
        valign: "middle",
        fontFace: FONT,
      },
    );
  };

  const statCard = (
    s: PptxGenJS.Slide,
    x: number,
    y: number,
    w: number,
    h: number,
    label: string,
    value: string,
    unit = "",
    sub = "",
  ): void => {
    s.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w,
      h,
      fill: { color: CARD_FILL },
      line: { color: CARD_LINE, width: 1 },
      rectRadius: 0.06,
    });
    s.addText(label.toUpperCase(), {
      x: x + 0.12,
      y: y + 0.1,
      w: w - 0.24,
      h: 0.25,
      align: "left",
      fontSize: 8,
      color: MUTED,
      fontFace: FONT,
    });
    s.addText(
      [
        { text: value, options: { fontSize: 15, bold: true, color: INK } },
        ...(unit
          ? [{ text: ` ${unit}`, options: { fontSize: 9, color: MUTED } }]
          : []),
      ],
      {
        x: x + 0.12,
        y: y + 0.32,
        w: w - 0.24,
        h: 0.35,
        align: "left",
        valign: "middle",
        fontFace: FONT,
      },
    );
    if (sub) {
      s.addText(sub, {
        x: x + 0.12,
        y: y + h - 0.35,
        w: w - 0.24,
        h: 0.3,
        align: "left",
        fontSize: 8,
        color: MUTED,
        fontFace: FONT,
      });
    }
  };

  // Horizontal progress bar (track + fill + % label).
  const progressBar = (
    s: PptxGenJS.Slide,
    x: number,
    y: number,
    w: number,
    pct: number,
    label?: string,
  ): void => {
    if (label) {
      s.addText(label, {
        x,
        y: y - 0.02,
        w: 1.4,
        h: 0.25,
        fontSize: 10,
        color: INK,
        fontFace: FONT,
      });
    }
    const barX = label ? x + 1.5 : x;
    const barW = label ? w - 2 : w;
    s.addShape(pptx.ShapeType.roundRect, {
      x: barX,
      y: y + 0.03,
      w: barW,
      h: 0.16,
      fill: { color: "E7EBF0" },
      line: { type: "none" },
      rectRadius: 0.08,
    });
    if (pct > 0) {
      // Track is fixed-width, so overshoot is shown by rescaling both
      // segments to whichever is larger — pct itself stays uncapped for display.
      const barScale = Math.max(pct, 100);
      const blueW = (barW * Math.min(pct, 100)) / barScale;
      s.addShape(pptx.ShapeType.roundRect, {
        x: barX,
        y: y + 0.03,
        w: Math.max(blueW, 0.05),
        h: 0.16,
        fill: { color: primary },
        line: { type: "none" },
        rectRadius: 0.08,
      });
      if (pct > 100) {
        const greenW = (barW * (pct - 100)) / barScale;
        s.addShape(pptx.ShapeType.roundRect, {
          x: barX + blueW,
          y: y + 0.03,
          w: Math.max(greenW, 0.05),
          h: 0.16,
          fill: { color: OVER_TARGET_COLOR },
          line: { type: "none" },
          rectRadius: 0.08,
        });
      }
    }
    if (label) {
      s.addText(`${pct}%`, {
        x: x + w - 0.5,
        y: y - 0.02,
        w: 0.5,
        h: 0.25,
        align: "right",
        fontSize: 10,
        bold: true,
        color: INK,
        fontFace: FONT,
      });
    }
  };

  const note = (s: PptxGenJS.Slide, text: string): void => {
    s.addText(text, {
      x: MARGIN,
      y: 7.02,
      w: CONTENT_W,
      h: 0.4,
      align: "left",
      valign: "middle",
      fontSize: 8,
      italic: true,
      color: MUTED,
      fontFace: FONT,
    });
  };

  // ── Slide 1: Title / hero ────────────────────────────────────────────────
  if (slideVisibility.titleSlide !== false) {
    const s = pptx.addSlide();
    s.addImage({ path: backLogo, x: 0, y: 0, w: "100%", h: "100%" });
    s.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 0,
      w: "100%",
      h: "100%",
      fill: { type: "solid", color: "000000", transparency: 45 },
      line: { type: "none" },
    });
    s.addShape(pptx.ShapeType.roundRect, {
      x: 0.5,
      y: 0.5,
      w: 0.55,
      h: 0.55,
      fill: { color: WHITE },
      line: { type: "none" },
      rectRadius: 0.08,
    });
    s.addText("MP", {
      x: 0.5,
      y: 0.5,
      w: 0.55,
      h: 0.55,
      align: "center",
      valign: "middle",
      fontSize: 14,
      bold: true,
      color: primary,
      fontFace: FONT,
    });
    s.addText(
      [
        {
          text: T("title_slide.moving_walls_internal").toUpperCase(),
          options: { fontSize: 13, bold: true, color: WHITE, breakLine: true },
        },
        {
          text: T("title_slide.moving_walls"),
          options: { fontSize: 9, color: "D8DEE9" },
        },
      ],
      {
        x: 1.15,
        y: 0.5,
        w: 5,
        h: 0.55,
        align: "left",
        valign: "middle",
        fontFace: FONT,
      },
    );
    if (brandDetails?.name) {
      s.addText(brandDetails.name, {
        x: SLIDE_W - 4.15,
        y: 0.5,
        w: 3,
        h: 0.55,
        align: "right",
        valign: "middle",
        fontSize: 13,
        bold: true,
        color: WHITE,
        fontFace: FONT,
      });
      s.addShape(pptx.ShapeType.roundRect, {
        x: SLIDE_W - 1.05,
        y: 0.5,
        w: 0.55,
        h: 0.55,
        fill: { color: WHITE },
        line: { type: "none" },
        rectRadius: 0.08,
      });
      s.addText(brandDetails.name[0]?.toUpperCase() || "B", {
        x: SLIDE_W - 1.05,
        y: 0.5,
        w: 0.55,
        h: 0.55,
        align: "center",
        valign: "middle",
        fontSize: 14,
        bold: true,
        color: primary,
        fontFace: FONT,
      });
    }
    s.addText(headerInfo?.name || "", {
      x: 0.5,
      y: 2.7,
      w: 9,
      h: 1.6,
      align: "left",
      valign: "middle",
      fontSize: 40,
      bold: true,
      color: WHITE,
      fontFace: FONT,
    });
    const hasDates = Boolean(headerInfo?.startDate && headerInfo?.endDate);
    const days =
      headerInfo?.duration ??
      durationDays(headerInfo?.startDate, headerInfo?.endDate);
    s.addText(
      [
        {
          text: T("title_slide.plan_dates").toUpperCase(),
          options: { fontSize: 9, color: "C7CED9", breakLine: true },
        },
        ...(hasDates
          ? [
              {
                text: `${formatDisplayDate(headerInfo!.startDate!)} – ${formatDisplayDate(headerInfo!.endDate!)}`,
                options: {
                  fontSize: 15,
                  bold: true,
                  color: WHITE,
                  breakLine: true,
                },
              },
              {
                text: T("title_slide.days").replace("{count}", String(days)),
                options: { fontSize: 10, color: "C7CED9" },
              },
            ]
          : []),
      ],
      {
        x: 0.5,
        y: 6.1,
        w: 6,
        h: 1,
        align: "left",
        valign: "top",
        fontFace: FONT,
      },
    );
    const statusText = headerInfo?.status
      ? headerInfo.status.charAt(0).toUpperCase() +
        headerInfo.status.slice(1).toLowerCase()
      : "";
    s.addText(
      [
        {
          text: T("title_slide.planned_by").toUpperCase(),
          options: { fontSize: 9, color: "C7CED9", breakLine: true },
        },
        {
          text: headerInfo?.preparedBy || "",
          options: { fontSize: 15, bold: true, color: WHITE, breakLine: true },
        },
        {
          text: T("title_slide.moving_walls_internal"),
          options: { fontSize: 10, color: "C7CED9" },
        },
      ],
      {
        x: SLIDE_W - 5.5,
        y: 6.0,
        w: 5,
        h: 1,
        align: "right",
        valign: "top",
        fontFace: FONT,
      },
    );
    if (statusText) {
      s.addShape(pptx.ShapeType.roundRect, {
        x: SLIDE_W - 2.0,
        y: 6.95,
        w: 1.5,
        h: 0.32,
        fill: { color: WHITE },
        line: { type: "none" },
        rectRadius: 0.16,
      });
      s.addText(statusText, {
        x: SLIDE_W - 2.0,
        y: 6.95,
        w: 1.5,
        h: 0.32,
        align: "center",
        valign: "middle",
        fontSize: 9,
        bold: true,
        color: primary,
        fontFace: FONT,
      });
    }
    s.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 0,
      w: "100%",
      h: "100%",
      fill: { type: "none" },
      line: { color: primary, width: 2 },
    });
  }

  // ── Slide 2: Performance Metrics (12 cards) ──────────────────────────────
  if (slideVisibility.performanceMetrics !== false) {
    const s = pptx.addSlide();
    banner(
      s,
      T("performance_metrics.title"),
      T("performance_metrics.subtitle"),
    );
    const sotPct =
      perf?.plannedSot && perf?.totalSot
        ? Math.round((perf.plannedSot / perf.totalSot) * 100)
        : 0;
    // Ad plays only apply to digital (slot-based) inventory — omit the card
    // for classic-only campaigns.
    const hasDigitalInventory =
      costSplitData.length === 0 ||
      costSplitData.some((r) => r.name?.toLowerCase().includes("digital"));
    const cards: Array<[string, string, string, string]> = [
      [
        T("performance_metrics.geography"),
        String(geographySummary.cityCount),
        T("performance_metrics.geography_unit"),
        fill("performance_metrics.geography_sub", {
          countries: geographySummary.countryCount,
          pois: geographySummary.poiCount,
        }),
      ],
      [
        T("performance_metrics.inventories"),
        String(perf?.totalInventories || 0),
        T("performance_metrics.inventories_unit"),
        T("performance_metrics.inventories_sub"),
      ],
      [
        T("performance_metrics.channels"),
        String(channelCount),
        T("performance_metrics.channels_unit"),
        T("performance_metrics.channels_sub"),
      ],
      [
        T("performance_metrics.total_impressions"),
        compact(perf?.estimatedImpression || 0),
        T("performance_metrics.total_impressions_unit"),
        T("performance_metrics.total_impressions_sub"),
      ],
      [
        T("performance_metrics.estimated_reach"),
        compact(perf?.estimatedReach || 0),
        T("performance_metrics.estimated_reach_unit"),
        T("performance_metrics.estimated_reach_sub"),
      ],
      [
        T("performance_metrics.avg_frequency"),
        `${(perf?.estimatedFrequency || 0).toFixed(1)}×`,
        T("performance_metrics.avg_frequency_unit"),
        T("performance_metrics.avg_frequency_sub"),
      ],
      [
        T(`performance_metrics.${isCPSGoal ? "avg_cps" : "avg_cpm"}`),
        money(perf?.avgCpm),
        T(`performance_metrics.${isCPSGoal ? "avg_cps" : "avg_cpm"}_unit`),
        T(`performance_metrics.${isCPSGoal ? "avg_cps" : "avg_cpm"}_sub`),
      ],
      [
        T("performance_metrics.ecpm"),
        money(perf?.avgECpm),
        T("performance_metrics.ecpm_unit"),
        T("performance_metrics.ecpm_sub"),
      ],
      [
        T("performance_metrics.sov"),
        `${(perf?.sov || 0).toFixed(2)}%`,
        T("performance_metrics.sov_unit"),
        T("performance_metrics.sov_sub"),
      ],
      [
        T("performance_metrics.sot"),
        `${sotPct}%`,
        T("performance_metrics.sot_unit"),
        T("performance_metrics.sot_sub"),
      ],
      [
        T("performance_metrics.total_cost"),
        money(costSplitData.reduce((sum, r) => sum + (r.totalAmount || 0), 0)),
        T("performance_metrics.total_cost_unit"),
        T("performance_metrics.total_cost_sub"),
      ],
      ...(hasDigitalInventory
        ? ([
            [
              T("performance_metrics.ad_plays"),
              compact(perf?.estimatedAdPlays || 0),
              T("performance_metrics.ad_plays_unit"),
              T("performance_metrics.ad_plays_sub"),
            ],
          ] as Array<[string, string, string, string]>)
        : []),
    ];
    const cols = 4;
    const gap = 0.2;
    const cw = (CONTENT_W - gap * (cols - 1)) / cols;
    const ch = 1.55;
    cards.forEach(([label, value, unit, sub], i) => {
      const x = MARGIN + (i % cols) * (cw + gap);
      const y = 1.35 + Math.floor(i / cols) * (ch + gap);
      statCard(s, x, y, cw, ch, label, value, unit, sub);
    });
    s.addText(T("performance_metrics.note_pricing"), {
      x: MARGIN,
      y: 6.55,
      w: CONTENT_W,
      h: 0.45,
      align: "left",
      valign: "middle",
      fontSize: 8,
      color: MUTED,
      fontFace: FONT,
    });
    note(
      s,
      T("performance_metrics.note_derivation")
        .replace("{count}", String(perf?.totalInventories || 0))
        .replace(
          "{days}",
          String(
            headerInfo?.duration ??
              durationDays(headerInfo?.startDate, headerInfo?.endDate),
          ),
        ),
    );
  }

  // ── Slide 3: Inventory Mix (table) ───────────────────────────────────────
  if (slideVisibility.inventoryMix !== false) {
    const s = pptx.addSlide();
    banner(
      s,
      T("inventory_mix.title"),
      fill("inventory_mix.subtitle", {
        inventories: costSplitData.reduce(
          (a, r) => a + (r.totalInventories || 0),
          0,
        ),
        channels: costSplitData.length,
      }),
    );
    const head: PptxGenJS.TableRow = [
      "col_media_channel",
      "col_inventories",
      "col_impressions",
      "col_cost",
      isCPSGoal ? "col_cps" : "col_cpm",
      "col_share",
    ].map((k) => ({
      text: T(`inventory_mix.${k}`),
      options: {
        bold: true,
        color: MUTED,
        fontSize: 9,
        fill: { color: CARD_FILL },
      },
    }));
    const isClassicChannel = (name?: string) =>
      name?.trim().toLowerCase().includes("classic") ?? false;
    const rows: PptxGenJS.TableRow[] = costSplitData.map((r) => [
      { text: r.name || "", options: { fontSize: 10, color: INK } },
      {
        text: String(r.totalInventories || 0),
        options: { fontSize: 10, align: "right" as const },
      },
      {
        text: compact(r.impressions || 0),
        options: { fontSize: 10, align: "right" as const },
      },
      {
        text: money(r.totalAmount),
        options: { fontSize: 10, align: "right" as const },
      },
      {
        text: isClassicChannel(r.name) ? "-" : money(perf?.avgCpm ?? 0),
        options: { fontSize: 10, align: "right" as const },
      },
      {
        text: `${Math.round(r.totalAmountInPercentage || 0)}%`,
        options: {
          fontSize: 10,
          bold: true,
          align: "right" as const,
          color: primary,
        },
      },
    ]);
    const totalImp = costSplitData.reduce(
      (a, r) => a + (r.impressions || 0),
      0,
    );
    const totalCost = costSplitData.reduce(
      (a, r) => a + (r.totalAmount || 0),
      0,
    );
    const totalInv = costSplitData.reduce(
      (a, r) => a + (r.totalInventories || 0),
      0,
    );
    const blended = perf?.avgCpm ?? 0;
    rows.push([
      {
        text: T("inventory_mix.total"),
        options: { fontSize: 10, bold: true, color: INK },
      },
      {
        text: String(totalInv),
        options: { fontSize: 10, bold: true, align: "right" as const },
      },
      {
        text: compact(totalImp),
        options: { fontSize: 10, bold: true, align: "right" as const },
      },
      {
        text: money(totalCost),
        options: { fontSize: 10, bold: true, align: "right" as const },
      },
      {
        text: money(blended),
        options: { fontSize: 10, bold: true, align: "right" as const },
      },
      {
        text: "100%",
        options: {
          fontSize: 10,
          bold: true,
          align: "right" as const,
          color: primary,
        },
      },
    ]);
    s.addTable([head, ...rows], {
      x: MARGIN,
      y: 1.35,
      w: CONTENT_W,
      colW: [4.5, 1.4, 1.9, 2, 1.6, 0.93],
      border: { type: "solid", color: CARD_LINE, pt: 1 },
      valign: "middle",
      fontFace: FONT,
      rowH: 0.62,
    });
    const boxY = 1.35 + (rows.length + 1) * 0.64 + 0.45;
    const bw = (CONTENT_W - 0.4) / 3;
    const boxH = 1.2;
    statCard(
      s,
      MARGIN,
      boxY,
      bw,
      boxH,
      T("inventory_mix.summary_media_channels"),
      String(costSplitData.length),
    );
    statCard(
      s,
      MARGIN + bw + 0.2,
      boxY,
      bw,
      boxH,
      T("inventory_mix.summary_total_inventories"),
      String(totalInv),
    );
    statCard(
      s,
      MARGIN + (bw + 0.2) * 2,
      boxY,
      bw,
      boxH,
      T("inventory_mix.summary_total_cost"),
      money(totalCost),
    );
    const lead = costSplitData.reduce<CostSplitByCampaignData | null>(
      (m, r) => (!m || (r.totalAmount || 0) > (m.totalAmount || 0) ? r : m),
      null,
    );
    if (lead)
      note(
        s,
        fill("inventory_mix.note", {
          channel: lead.name,
          share: Math.round(lead.totalAmountInPercentage || 0),
          inventories: lead.totalInventories || 0,
        }),
      );
  }

  // ── Slide 4: Targeting (4 columns) ───────────────────────────────────────
  if (slideVisibility.targeting !== false) {
    const s = pptx.addSlide();
    banner(s, T("targeting_card.title"), T("targeting_card.subtitle"));
    const demo = targeting?.demographics;
    const venue = Array.from(
      new Set(
        [
          ...(targeting?.venueTypes?.digitalOoh || []),
          ...(targeting?.venueTypes?.classicOoh || []),
        ].map((c) => c.split("-")[0]),
      ),
    ).map(titleCaseCode);
    const na = T("targeting_card.not_selected");
    const chips = (arr?: string[]) =>
      arr && arr.length ? arr.join("   ·   ") : na;
    const columns: Array<[string, string, string]> = [
      [
        T("targeting_card.demographics"),
        T("targeting_card.demographics_sub"),
        `${T("targeting_card.age")}: ${chips((demo?.age || []).map((a) => a.replace(/_/g, "-")))}\n\n${T("targeting_card.gender")}: ${chips((demo?.gender || []).map(titleCaseCode))}\n\n${T("targeting_card.income")}: ${chips((demo?.income || []).map(titleCaseCode))}`,
      ],
      [
        T("targeting_card.venue_types"),
        T("targeting_card.venue_types_sub"),
        chips(venue),
      ],
      [
        T("targeting_card.behaviour"),
        T("targeting_card.behaviour_sub"),
        chips(demo?.behavior),
      ],
      [
        T("targeting_card.interests"),
        T("targeting_card.interests_sub"),
        chips(demo?.interests),
      ],
    ];
    const cw = (CONTENT_W - 0.6) / 4;
    columns.forEach(([label, sub, body], i) => {
      const x = MARGIN + i * (cw + 0.2);
      s.addShape(pptx.ShapeType.roundRect, {
        x,
        y: 1.35,
        w: cw,
        h: 5.55,
        fill: { color: WHITE },
        line: { color: CARD_LINE, width: 1 },
        rectRadius: 0.06,
      });
      s.addText(
        [
          {
            text: label.toUpperCase(),
            options: {
              fontSize: 10,
              bold: true,
              color: MUTED,
              breakLine: true,
            },
          },
          { text: sub, options: { fontSize: 8, color: MUTED } },
        ],
        {
          x: x + 0.15,
          y: 1.5,
          w: cw - 0.3,
          h: 0.6,
          align: "left",
          fontFace: FONT,
        },
      );
      s.addText(body, {
        x: x + 0.15,
        y: 2.2,
        w: cw - 0.3,
        h: 4.55,
        align: "left",
        valign: "top",
        fontSize: 10,
        color: INK,
        fontFace: FONT,
      });
    });
  }

  // ── Slide 5: Audience Trends ─────────────────────────────────────────────
  if (slideVisibility.audienceTrends !== false) {
    const s = pptx.addSlide();
    banner(s, T("audience_trends.title"), T("audience_trends.subtitle"));
    s.addText(T("audience_trends.cumulative_reach").toUpperCase(), {
      x: MARGIN,
      y: 1.3,
      w: CONTENT_W,
      h: 0.3,
      fontSize: 9,
      color: MUTED,
      fontFace: FONT,
    });
    s.addText(
      fill("audience_trends.reach_headline", {
        reach: compact(perf?.estimatedReach || 0),
      }),
      {
        x: MARGIN,
        y: 1.6,
        w: CONTENT_W,
        h: 0.32,
        fontSize: 14,
        bold: true,
        color: INK,
        fontFace: FONT,
      },
    );

    // Cumulative reach-build line chart (native pptx chart).
    const CHART_Y = 1.98;
    const CHART_H = 2.05;
    if (reachCurve && reachCurve.data.length > 0) {
      // Thin the x-axis labels so long flights (30+ days) stay legible.
      const n = reachCurve.labels.length;
      const step = Math.max(1, Math.ceil(n / 8));
      const cats = reachCurve.labels.map((l, i) =>
        i % step === 0 || i === n - 1 ? l : "",
      );
      s.addChart(
        pptx.ChartType.line,
        [
          {
            name: T("audience_trends.cumulative_reach"),
            labels: cats,
            values: reachCurve.data,
          },
        ],
        {
          x: MARGIN,
          y: CHART_Y,
          w: CONTENT_W,
          h: CHART_H,
          chartColors: [primary],
          lineSize: 2,
          lineSmooth: true,
          showLegend: false,
          showTitle: false,
          showValue: false,
          catAxisLabelFontSize: 7,
          catAxisLabelColor: MUTED,
          valAxisLabelFontSize: 7,
          valAxisLabelColor: MUTED,
          valAxisMinVal: 0,
          valGridLine: { style: "dash", color: CARD_LINE },
          catGridLine: { style: "none" },
        },
      );
    } else {
      s.addText(T("audience_trends.reach_unavailable"), {
        x: MARGIN,
        y: CHART_Y,
        w: CONTENT_W,
        h: CHART_H,
        align: "center",
        valign: "middle",
        fontSize: 10,
        color: MUTED,
        fontFace: FONT,
      });
    }

    // Audience Activity by Day — union of the daypart grids (PRD §10.5.2),
    // suppressed unless there's audience targeting or a custom schedule.
    const LOWER_Y = CHART_Y + CHART_H + 0.25; // 4.28
    const activity = computeAudienceActivityByDay(selectedInventoryLocations);
    const hasAudienceTargeting = Boolean(
      audienceDemographics?.ageGroups?.length ||
        audienceDemographics?.incomeLevel?.length ||
        audienceDemographics?.interests?.length ||
        audienceDemographics?.lifestyle?.length,
    );
    const showActivity = hasAudienceTargeting || activity.hasCustomSchedule;
    if (showActivity) {
      s.addText(T("audience_trends.activity_by_day").toUpperCase(), {
        x: MARGIN,
        y: LOWER_Y,
        w: 6,
        h: 0.3,
        fontSize: 9,
        color: MUTED,
        fontFace: FONT,
      });
      const barW = 0.55;
      const barTop = LOWER_Y + 0.4;
      const barH = 1.75;
      const maxShare = Math.max(...activity.bars.map((b) => b.sharePct), 1);
      activity.bars.forEach((b, i) => {
        const x = MARGIN + i * 0.8;
        const h = Math.max((barH * b.sharePct) / maxShare, 0.05);
        s.addShape(pptx.ShapeType.rect, {
          x,
          y: barTop + (barH - h),
          w: barW,
          h,
          fill: { color: b.isPeak ? "06b6d4" : primary },
          line: { type: "none" },
        });
        s.addText(b.day, {
          x,
          y: barTop + barH + 0.05,
          w: barW,
          h: 0.25,
          align: "center",
          fontSize: 8,
          color: MUTED,
          fontFace: FONT,
        });
      });
    }
    // Targeting Concentration Index (right) — 4 fixed axes, deterministic
    // reach-lift index (1.4×–3.6×). Same util/values as the on-screen card;
    // shown only when the plan has any audience targeting.
    const demo = targeting?.demographics;
    const concentrationInput = demo
      ? {
          ageGender: [...(demo.age || []), ...(demo.gender || [])],
          income: demo.income,
          behavior: demo.behavior,
          interest: demo.interests,
        }
      : {
          ageGender: audienceDemographics?.ageGroups,
          income: audienceDemographics?.incomeLevel,
          behavior: audienceDemographics?.lifestyle,
          interest: audienceDemographics?.interests,
        };
    const hasConcentration = Object.values(concentrationInput).some(
      (v) => (v || []).length > 0,
    );
    const concentration = computeConcentrationIndex(
      concentrationInput,
      campaignId,
    );
    if (hasConcentration) {
      s.addText(T("audience_trends.concentration_index").toUpperCase(), {
        x: 7,
        y: LOWER_Y,
        w: 5.8,
        h: 0.25,
        fontSize: 9,
        color: MUTED,
        fontFace: FONT,
      });
      s.addText(T("audience_trends.concentration_subtitle"), {
        x: 7,
        y: LOWER_Y + 0.26,
        w: 5.8,
        h: 0.3,
        italic: true,
        fontSize: 7,
        color: MUTED,
        fontFace: FONT,
      });
      concentration.forEach((row, i) => {
        const y = LOWER_Y + 0.66 + i * 0.42;
        s.addText(T(`audience_trends.${row.key}`), {
          x: 7,
          y,
          w: 1.8,
          h: 0.3,
          fontSize: 10,
          color: INK,
          fontFace: FONT,
        });
        s.addShape(pptx.ShapeType.roundRect, {
          x: 8.9,
          y: y + 0.05,
          w: 3,
          h: 0.16,
          fill: { color: "E7EBF0" },
          line: { type: "none" },
          rectRadius: 0.08,
        });
        s.addShape(pptx.ShapeType.roundRect, {
          x: 8.9,
          y: y + 0.05,
          w: Math.max((3 * row.index) / CONCENTRATION_INDEX_MAX, 0.05),
          h: 0.16,
          fill: { color: primary },
          line: { type: "none" },
          rectRadius: 0.08,
        });
        s.addText(`${row.index.toFixed(1)}×`, {
          x: 11.9,
          y,
          w: 0.7,
          h: 0.3,
          align: "right",
          fontSize: 10,
          bold: true,
          color: INK,
          fontFace: FONT,
        });
      });
    }
    note(s, T("audience_trends.note"));
  }

  // ── Slide 6: Geographic Plan (table, 8 city rows per slide) ──────────────
  if (slideVisibility.geographicPlan !== false) {
    // Sorted desc by impressions — same order as the on-screen Geographic
    // Plan table (MediaPlanGeographicPlan.tsx).
    const cities = [...costSplitByCity].sort(
      (a, b) => (b.impressions || 0) - (a.impressions || 0),
    );
    const totalInv = cities.reduce((a, r) => a + (r.totalInventories || 0), 0);
    const totalCost = cities.reduce((a, r) => a + (r.totalAmount || 0), 0);
    const totalImp = cities.reduce((a, r) => a + (r.impressions || 0), 0);
    const avgCpm = perf?.avgCpm ?? 0;
    const top = cities.reduce<CostSplitByCampaignData | null>(
      (m, r) => (!m || (r.impressions || 0) > (m.impressions || 0) ? r : m),
      null,
    );
    const countries = new Set(cities.map((r) => r.country).filter(Boolean))
      .size;
    const share =
      top && totalImp > 0
        ? Math.round(((top.impressions || 0) / totalImp) * 100)
        : 0;

    const head = [
      "col_city",
      "col_country",
      "col_inventories",
      "col_impressions",
      "col_reach",
      "col_cost",
      isCPSGoal ? "col_cps" : "col_cpm",
    ].map((k) => ({
      text: T(`geographic_plan.${k}`),
      options: {
        bold: true,
        color: MUTED,
        fontSize: 9,
        fill: { color: CARD_FILL },
      },
    }));

    // Paginate city rows into fixed-size slides (same as Inventory Snapshots).
    // The summary/footer renders once — on the last table slide if it has
    // room (< 6 rows), otherwise it spills into its own trailing slide.
    const ROWS_PER_SLIDE = 8;
    const pages: CostSplitByCampaignData[][] = [];
    for (let i = 0; i < cities.length; i += ROWS_PER_SLIDE) {
      pages.push(cities.slice(i, i + ROWS_PER_SLIDE));
    }
    if (pages.length === 0) pages.push([]);
    const lastPageRowCount = pages[pages.length - 1].length;
    const summaryOnOwnSlide = lastPageRowCount >= 6;

    const drawSummary = (s: PptxGenJS.Slide, y: number): void => {
      const bw = (CONTENT_W - 0.6) / 4;
      const boxH = 1.2;
      statCard(
        s,
        MARGIN,
        y,
        bw,
        boxH,
        T("geographic_plan.summary_cities"),
        String(cities.length),
      );
      statCard(
        s,
        MARGIN + bw + 0.2,
        y,
        bw,
        boxH,
        T("geographic_plan.summary_inventories"),
        String(totalInv),
      );
      statCard(
        s,
        MARGIN + (bw + 0.2) * 2,
        y,
        bw,
        boxH,
        T("geographic_plan.summary_total_cost"),
        money(totalCost),
      );
      statCard(
        s,
        MARGIN + (bw + 0.2) * 3,
        y,
        bw,
        boxH,
        T(
          `geographic_plan.${isCPSGoal ? "summary_avg_cps" : "summary_avg_cpm"}`,
        ),
        money(avgCpm),
      );
      if (top)
        note(
          s,
          fill("geographic_plan.note", {
            cities: cities.length,
            countries,
            topMarket: top.name,
            share,
          }),
        );
    };

    pages.forEach((pageRows, pi) => {
      const s = pptx.addSlide();
      const start = pi * ROWS_PER_SLIDE + 1;
      const end = pi * ROWS_PER_SLIDE + pageRows.length;
      const isLastTableSlide = pi === pages.length - 1;
      banner(
        s,
        T("geographic_plan.title"),
        pages.length > 1
          ? fill("geographic_plan.page_range", {
              start,
              end,
              total: cities.length,
            })
          : fill("geographic_plan.subtitle", {
              inventories: totalInv,
              cities: cities.length,
            }),
      );
      const rows = pageRows.map((r) => [
        { text: r.name || "", options: { fontSize: 10, color: INK } },
        { text: r.country || "—", options: { fontSize: 10 } },
        {
          text: String(r.totalInventories || 0),
          options: { fontSize: 10, align: "right" as const },
        },
        {
          text: compact(r.impressions || 0),
          options: { fontSize: 10, align: "right" as const },
        },
        {
          text: compact(r.reach || 0),
          options: { fontSize: 10, align: "right" as const },
        },
        {
          text: money(r.totalAmount),
          options: { fontSize: 10, align: "right" as const },
        },
        {
          text: money(r.avgCpm),
          options: { fontSize: 10, align: "right" as const },
        },
      ]);
      s.addTable([head, ...rows], {
        x: MARGIN,
        y: 1.35,
        w: CONTENT_W,
        colW: [2.3, 1.8, 1.1, 1.7, 1.7, 1.9, 1.83],
        border: { type: "solid", color: CARD_LINE, pt: 1 },
        valign: "middle",
        fontFace: FONT,
        rowH: 0.62,
      });
      if (isLastTableSlide && !summaryOnOwnSlide) {
        const boxY = 1.35 + (rows.length + 1) * 0.64 + 0.45;
        drawSummary(s, boxY);
      }
    });

    if (summaryOnOwnSlide) {
      const s = pptx.addSlide();
      banner(
        s,
        T("geographic_plan.title"),
        fill("geographic_plan.subtitle", {
          inventories: totalInv,
          cities: cities.length,
        }),
      );
      drawSummary(s, 1.35);
    }
  }

  // ── Slide 7: Audience Map ────────────────────────────────────────────────
  if (slideVisibility.audienceMap !== false) {
    const s = pptx.addSlide();
    const densest = costSplitByCity.reduce<CostSplitByCampaignData | null>(
      (m, r) => (!m || (r.impressions || 0) > (m.impressions || 0) ? r : m),
      null,
    );
    banner(
      s,
      T("audience_map.title"),
      fill("audience_map.subtitle", {
        sites: selectedInventoryLocations.length,
        markets: costSplitByCity.length,
        market: densest?.name || "",
      }),
    );
    if (mapImage) {
      const img = {
        data: mapImage.startsWith("data:")
          ? mapImage
          : `data:image/jpeg;base64,${mapImage}`,
        x: MARGIN,
        y: 1.35,
        w: CONTENT_W,
        h: 4.2,
      } as PptxGenJS.ImageProps;
      if (mapImageLink) img.hyperlink = { url: mapImageLink };
      s.addImage(img);
    } else {
      s.addShape(pptx.ShapeType.roundRect, {
        x: MARGIN,
        y: 1.35,
        w: CONTENT_W,
        h: 4.2,
        fill: { color: CARD_FILL },
        line: { color: CARD_LINE, width: 1 },
        rectRadius: 0.06,
      });
    }
    const bw = (CONTENT_W - 0.4) / 3;
    const by = 5.75;
    statCard(
      s,
      MARGIN,
      by,
      bw,
      0.9,
      T("audience_map.sites_pinned"),
      String(selectedInventoryLocations.length),
    );
    statCard(
      s,
      MARGIN + bw + 0.2,
      by,
      bw,
      0.9,
      T("audience_map.total_inventory"),
      String(perf?.totalInventories || 0),
    );
    statCard(
      s,
      MARGIN + (bw + 0.2) * 2,
      by,
      bw,
      0.9,
      T("audience_map.densest_market"),
      densest?.name || "—",
    );
    note(s, T("audience_map.note"));
  }

  // ── Slide 8: Goals & KPIs ────────────────────────────────────────────────
  if (slideVisibility.goalsKpis !== false) {
    const s = pptx.addSlide();
    const gt = (headerInfo?.goalType || "").toUpperCase();
    const goalKey =
      gt === "IMPRESSIONS"
        ? "impressions"
        : gt === "ADPLAYS"
          ? "adplays"
          : gt === "SOV"
            ? "sov"
            : "reach";
    const target = headerInfo?.targetValue || 0;
    const forecast =
      goalKey === "impressions"
        ? perf?.estimatedImpression || 0
        : goalKey === "adplays"
          ? perf?.estimatedAdPlays || 0
          : goalKey === "sov"
            ? perf?.sov || 0
            : perf?.estimatedReach || 0;
    const pct = target > 0 ? Math.round((forecast / target) * 100) : 0;
    const del = delivery || {
      granularity: "weekly",
      bins: [],
      peakLabel: "",
      peakValue: 0,
    };
    const granLabel = T(`goals_kpis.granularity_${del.granularity}`);
    banner(
      s,
      T("goals_kpis.title"),
      fill("goals_kpis.subtitle", { pct, peak: del.peakLabel }),
    );
    const fmtGoal = (v: number) =>
      goalKey === "sov" ? `${Math.round(v)}%` : Math.round(v).toLocaleString();
    const halfW = (CONTENT_W - 0.2) / 2;
    statCard(
      s,
      MARGIN,
      1.35,
      halfW,
      1.0,
      T("goals_kpis.target"),
      fmtGoal(target),
      T(`goals_kpis.${goalKey}.target_noun`),
      fill("goals_kpis.goal_type", { type: T(`goals_kpis.${goalKey}.label`) }),
    );
    statCard(
      s,
      MARGIN + halfW + 0.2,
      1.35,
      halfW,
      1.0,
      T("goals_kpis.planned_forecast"),
      fmtGoal(forecast),
      T(`goals_kpis.${goalKey}.forecast_noun`),
      T("goals_kpis.on_track"),
    );
    s.addText(T("goals_kpis.forecast_vs_target"), {
      x: MARGIN,
      y: 2.6,
      w: 3,
      h: 0.3,
      fontSize: 11,
      bold: true,
      color: INK,
      fontFace: FONT,
    });
    progressBar(s, MARGIN, 2.95, CONTENT_W, pct);
    s.addText(`${pct}%`, {
      x: SLIDE_W - 1.3,
      y: 2.6,
      w: 0.8,
      h: 0.3,
      align: "right",
      fontSize: 11,
      bold: true,
      color: INK,
      fontFace: FONT,
    });
    // Delivery bars
    s.addText(
      fill("goals_kpis.expected_delivery", {
        granularity: granLabel,
      }).toUpperCase(),
      {
        x: MARGIN,
        y: 3.5,
        w: CONTENT_W,
        h: 0.3,
        fontSize: 9,
        color: MUTED,
        fontFace: FONT,
      },
    );
    const maxBin = Math.max(...del.bins.map((b) => b.value), 1);
    const n = del.bins.length || 1;
    const slotW = CONTENT_W / n;
    del.bins.forEach((b, i) => {
      const h = Math.max((1.9 * b.value) / maxBin, 0.05);
      const isPeak = b.value === del.peakValue;
      const x = MARGIN + i * slotW + slotW * 0.15;
      s.addShape(pptx.ShapeType.rect, {
        x,
        y: 3.9 + (1.9 - h),
        w: slotW * 0.7,
        h,
        fill: { color: isPeak ? "06B6D4" : primary },
        line: { type: "none" },
      });
      s.addText(b.label, {
        x: MARGIN + i * slotW,
        y: 5.85,
        w: slotW,
        h: 0.25,
        align: "center",
        fontSize: 8,
        color: MUTED,
        fontFace: FONT,
      });
    });
    const bw = (CONTENT_W - 0.4) / 3;
    statCard(s, MARGIN, 6.2, bw, 0.75, T("goals_kpis.granularity"), granLabel);
    statCard(
      s,
      MARGIN + bw + 0.2,
      6.2,
      bw,
      0.75,
      T("goals_kpis.peak_bin"),
      del.peakLabel || "—",
    );
    statCard(
      s,
      MARGIN + (bw + 0.2) * 2,
      6.2,
      bw,
      0.75,
      T("goals_kpis.peak_impressions"),
      compact(del.peakValue),
    );
  }

  // ── Slide 9: Inventory Snapshots (card grid, 6 per slide, 3×2) ──────────────
  if (slideVisibility.inventorySnapshots !== false) {
    const impOf = (it: InventoryItem) => {
      const p = it.performance as
        | { estimatedImpression?: number; estimatedImpressions?: number }
        | undefined;
      return p?.estimatedImpression ?? p?.estimatedImpressions ?? 0;
    };
    const items = [...selectedInventoryLocations].sort(
      (a, b) => impOf(b) - impOf(a),
    );
    // Snapshots use the per-inventory CPS (spotRate); label from the shared
    // isCPSGoal (SOV / AD_PLAYS) computed above.

    // Pre-fetch each inventory's lead image → data URI (parallel, best-effort).
    const imageOf = new Map<InventoryItem, string>();
    await Promise.all(
      items.map(async (it) => {
        const uri = await fetchImageDataUri(
          it.detail?.images?.[0] || it.detail?.thumbnail,
        );
        if (uri) imageOf.set(it, uri);
      }),
    );

    // Card geometry — 3 columns × 2 rows filling the content band.
    const PER_SLIDE = 6;
    const gap = 0.25;
    const cw = (CONTENT_W - gap * 2) / 3;
    const ch = 2.55;
    const vgap = 0.3;
    const gridTop = 1.35;
    const imgH = 0.8;

    // One card = image strip + badge + title + meta + 5 metrics + owner.
    const drawCard = (
      s: PptxGenJS.Slide,
      it: InventoryItem,
      x: number,
      y: number,
    ) => {
      const rawType = (it.detail?.inventoryType || "").toLowerCase();
      const type = rawType.includes("classic")
        ? "Classic"
        : rawType.includes("cinema")
          ? "Cinema"
          : rawType.includes("retail")
            ? "Retail"
            : "Digital";
      const impressionValue = it.performance?.estimatedImpression || 0;
      const format = it.detail?.format || "";
      const city = it.location?.location?.city || "";
      const pad = 0.15;
      s.addShape(pptx.ShapeType.roundRect, {
        x,
        y,
        w: cw,
        h: ch,
        fill: { color: WHITE },
        line: { color: CARD_LINE, width: 1 },
        rectRadius: 0.06,
      });
      // Image strip (or neutral placeholder when unavailable)
      const dataUri = imageOf.get(it);
      if (dataUri) {
        s.addImage({
          data: dataUri,
          x,
          y,
          w: cw,
          h: imgH,
          sizing: { type: "cover", w: cw, h: imgH },
        });
      } else {
        s.addShape(pptx.ShapeType.rect, {
          x,
          y,
          w: cw,
          h: imgH,
          fill: { color: CARD_FILL },
          line: { type: "none" },
        });
      }
      // Channel badge (overlaid on the image, top-left)
      s.addText(type.toUpperCase(), {
        x: x + pad,
        y: y + 0.12,
        w: 1.5,
        h: 0.28,
        align: "center",
        valign: "middle",
        fontSize: 8,
        bold: true,
        color: WHITE,
        fill: { color: primary },
        fontFace: FONT,
      });
      // Title + meta
      s.addText(it.detail?.name || "", {
        x: x + pad,
        y: y + imgH + 0.08,
        w: cw - pad * 2,
        h: 0.28,
        fontSize: 11,
        bold: true,
        color: INK,
        fontFace: FONT,
      });
      s.addText(
        [[type, format].filter(Boolean).join(" "), city]
          .filter(Boolean)
          .join(" · "),
        {
          x: x + pad,
          y: y + imgH + 0.34,
          w: cw - pad * 2,
          h: 0.2,
          fontSize: 8,
          color: MUTED,
          fontFace: FONT,
        },
      );
      // Metric grid (3 cols × 2 rows)
      const sov = it.performance?.sov;
      const spotRate = it.performance?.spotRate;
      const priceMetric: [string, string] = isCPSGoal
        ? [
            T("inventory_snapshots.cps"),
            spotRate != null ? money(spotRate) : "—",
          ]
        : [T("inventory_snapshots.cpm"), money(it.performance?.cpmRate)];
      const metrics: Array<[string, string]> = [
        [T("inventory_snapshots.impressions"), compact(impressionValue)],
        [
          T("inventory_snapshots.plays_day"),
          rawType.includes("classic")
            ? "-"
            : compact(it.performance?.perDayAdPlays || 0),
        ],
        priceMetric,
        [T("inventory_snapshots.cost"), money(it.performance?.estimatedCost)],
        [
          T("inventory_snapshots.sov"),
          sov != null ? `${sov.toFixed(1)}%` : "—",
        ],
      ];
      const mW = (cw - pad * 2) / 3;
      const metricsTop = y + imgH + 0.56;
      metrics.forEach(([label, value], i) => {
        const mx = x + pad + (i % 3) * mW;
        const my = metricsTop + Math.floor(i / 3) * 0.46;
        s.addText(label, {
          x: mx,
          y: my,
          w: mW,
          h: 0.16,
          fontSize: 6,
          color: MUTED,
          fontFace: FONT,
        });
        s.addText(value, {
          x: mx,
          y: my + 0.16,
          w: mW,
          h: 0.24,
          fontSize: 10,
          bold: true,
          color: INK,
          fontFace: FONT,
        });
      });
      // Owner footer
      if (it.detail?.mediaOwnerName) {
        const fy = y + ch - 0.29;
        s.addShape(pptx.ShapeType.line, {
          x: x + pad,
          y: fy - 0.06,
          w: cw - pad * 2,
          h: 0,
          line: { color: CARD_LINE, width: 1 },
        });
        s.addText(ownerInitials(it.detail.mediaOwnerName), {
          x: x + pad,
          y: fy,
          w: 0.32,
          h: 0.24,
          align: "center",
          valign: "middle",
          margin: 0,
          fontSize: 7,
          bold: true,
          color: primary,
          fill: { color: CARD_FILL },
          fontFace: FONT,
        });
        s.addText(it.detail.mediaOwnerName, {
          x: x + pad + 0.37,
          y: fy,
          w: cw - pad * 2 - 0.37,
          h: 0.24,
          valign: "middle",
          fontSize: 8,
          color: MUTED,
          fontFace: FONT,
        });
      }
    };

    const chunks: InventoryItem[][] = [];
    for (let i = 0; i < items.length; i += PER_SLIDE) {
      chunks.push(items.slice(i, i + PER_SLIDE));
    }
    // Always emit at least one slide (empty plan → banner only).
    if (chunks.length === 0) chunks.push([]);

    chunks.forEach((chunk, ci) => {
      const s = pptx.addSlide();
      const start = ci * PER_SLIDE + 1;
      const end = ci * PER_SLIDE + chunk.length;
      banner(
        s,
        T("inventory_snapshots.title"),
        fill("inventory_snapshots.subtitle", {
          start,
          end,
          total: items.length,
        }),
      );
      chunk.forEach((it, i) => {
        const col = i % 3;
        const row = Math.floor(i / 3);
        const x = MARGIN + col * (cw + gap);
        const y = gridTop + row * (ch + vgap);
        drawCard(s, it, x, y);
      });
      note(
        s,
        fill("inventory_snapshots.note", {
          total: items.length,
          shown: chunk.length,
        }),
      );
    });
  }

  // ── Slide 10: Why This Plan Works ────────────────────────────────────────
  // ── Cinema slide ─────────────────────────────────────────────────────────
  // Gated on EXACTLY the same condition as the on-screen Cinema analytics tab
  // and the Excel Cinema sheet (parity contract): the plan has cinema line
  // items. Cinema is bought by operator/hall/showtime-window with genre/rating
  // constraints — films are only an indicative preview, never a buy unit.
  const cinemaLocations = selectedInventoryLocations.filter((it) =>
    (it.detail?.inventoryType || "").toLowerCase().includes("cinema"),
  );
  if (cinemaLocations.length > 0) {
    const s = pptx.addSlide();
    banner(s, T("cinema.title"), T("cinema.subtitle"));

    // Column layout across the content band. `headerKey` is the cinema.*
    // translation key for each column's header label.
    const cols = [
      { headerKey: "inventory", w: 3.1 },
      { headerKey: "operator", w: 2.0 },
      { headerKey: "cinema", w: 2.2 },
      { headerKey: "hall", w: 1.3 },
      { headerKey: "showtime", w: 2.4 },
      { headerKey: "genre_rating", w: 1.9 },
    ];
    const headerY = 1.35;
    const rowH = 0.42;
    // Cap rows to what fits above the footer note (overflow drops silently in
    // pptxgenjs) — add a "+N more" line for the remainder.
    const MAX_ROWS = 11;
    const bodyTop = headerY + 0.5;

    // Header row
    let cx = MARGIN;
    cols.forEach((c) => {
      s.addText(T(`cinema.${c.headerKey}`), {
        x: cx + 0.08,
        y: headerY,
        w: c.w - 0.16,
        h: 0.4,
        fontSize: 9,
        bold: true,
        color: MUTED,
        fontFace: FONT,
        valign: "middle",
      });
      cx += c.w;
    });

    const visible = cinemaLocations.slice(0, MAX_ROWS);
    const hidden = cinemaLocations.length - visible.length;

    visible.forEach((it, i) => {
      const cf = it.detail?.cinemaFields || {};
      const rowY = bodyTop + i * rowH;
      if (i % 2 === 1) {
        s.addShape(pptx.ShapeType.roundRect, {
          x: MARGIN,
          y: rowY,
          w: CONTENT_W,
          h: rowH,
          fill: { color: CARD_FILL },
          line: { type: "none" },
          rectRadius: 0.02,
        });
      }
      const showtime = (cf.showtimeWindows || [])
        .map((w) => w.label)
        .filter(Boolean)
        .join(", ");
      const values = [
        it.detail?.name || "—",
        cf.operator || "—",
        cf.cinemaName || "—",
        cf.hallName || (cf.hallNumber != null ? String(cf.hallNumber) : "—"),
        showtime || "—",
        [(cf.genres || []).join(", "), (cf.ratings || []).join(", ")]
          .filter(Boolean)
          .join(" · ") || "—",
      ];
      let vx = MARGIN;
      cols.forEach((c, ci) => {
        s.addText(values[ci], {
          x: vx + 0.08,
          y: rowY,
          w: c.w - 0.16,
          h: rowH,
          fontSize: 9,
          color: INK,
          fontFace: FONT,
          valign: "middle",
          fit: "shrink",
        });
        vx += c.w;
      });
    });

    if (hidden > 0) {
      s.addText(fill("cinema.more", { count: hidden }), {
        x: MARGIN,
        y: bodyTop + visible.length * rowH + 0.05,
        w: CONTENT_W,
        h: 0.3,
        fontSize: 9,
        italic: true,
        color: MUTED,
        fontFace: FONT,
      });
    }

    note(s, T("cinema.note"));
  }

  if (slideVisibility.whyPlan !== false) {
    const s = pptx.addSlide();
    banner(s, T("why_plan.title"), T("why_plan.subtitle"));
    const topCity = costSplitByCity.reduce<CostSplitByCampaignData | null>(
      (m, r) => (!m || (r.impressions || 0) > (m.impressions || 0) ? r : m),
      null,
    );
    // Reasons — same builder as the on-screen card, so the two always match.
    const demographicSegments = [
      ...(targeting?.demographics?.age || []),
      ...(targeting?.demographics?.gender || []),
      ...(targeting?.demographics?.income || []),
      ...(targeting?.demographics?.behavior || []),
      ...(targeting?.demographics?.interests || []),
    ]
      .map(humaniseSegment)
      .slice(0, 2);
    const venueTypes = Array.from(
      new Set(
        [
          ...(targeting?.venueTypes?.digitalOoh || []),
          ...(targeting?.venueTypes?.classicOoh || []),
        ].map((c) => c.split("-")[0]),
      ),
    )
      .map(humaniseSegment)
      .slice(0, 2);
    const reasons = buildPlanReasons({
      topCityName: topCity?.name || T("why_plan.fallback_market"),
      inventories: perf?.totalInventories || 0,
      impressions: perf?.estimatedImpression || 0,
      cpm: perf?.avgCpm || 0,
      demographicSegments,
      venueTypes,
      compact,
      currency: (n) => money(n),
    }).map((r) => fill(`why_plan.${r.key}`, r.params));
    const cw = (CONTENT_W - 0.4) / 3;
    reasons.forEach((r, i) => {
      const x = MARGIN + i * (cw + 0.2);
      s.addShape(pptx.ShapeType.roundRect, {
        x,
        y: 1.35,
        w: cw,
        h: 1.9,
        fill: { color: WHITE },
        line: { color: CARD_LINE, width: 1 },
        rectRadius: 0.06,
      });
      s.addShape(pptx.ShapeType.ellipse, {
        x: x + 0.15,
        y: 1.5,
        w: 0.35,
        h: 0.35,
        fill: { color: primary },
        line: { type: "none" },
      });
      s.addText(String(i + 1), {
        x: x + 0.15,
        y: 1.5,
        w: 0.35,
        h: 0.35,
        align: "center",
        valign: "middle",
        fontSize: 11,
        bold: true,
        color: WHITE,
        fontFace: FONT,
      });
      s.addText(fill("why_plan.reason", { number: i + 1 }).toUpperCase(), {
        x: x + 0.6,
        y: 1.5,
        w: cw - 0.75,
        h: 0.3,
        fontSize: 9,
        color: MUTED,
        fontFace: FONT,
      });
      s.addText(r, {
        x: x + 0.15,
        y: 2.0,
        w: cw - 0.3,
        h: 1.15,
        fontSize: 10,
        color: INK,
        fontFace: FONT,
        valign: "top",
      });
    });

    // Inventory in the plan — names + city, matching the on-screen chip list.
    s.addText(T("why_plan.inventory_in_plan").toUpperCase(), {
      x: MARGIN,
      y: 3.35,
      w: CONTENT_W,
      h: 0.25,
      fontSize: 9,
      color: MUTED,
      fontFace: FONT,
    });
    const MAX_VISIBLE_INVENTORIES = 8;
    const MAX_INVENTORY_LABEL_LENGTH = 30;
    const visibleInventoryItems = selectedInventoryLocations.slice(
      0,
      MAX_VISIBLE_INVENTORIES,
    );
    const hiddenInventoryCount = Math.max(
      selectedInventoryLocations.length - MAX_VISIBLE_INVENTORIES,
      0,
    );
    const formatInventoryLabel = (
      it: (typeof selectedInventoryLocations)[number],
    ) => {
      const name = it.detail?.name || "";
      const city = it.location?.location?.city || "";
      const label = city ? `${name} — ${city}` : name;
      return label.length > MAX_INVENTORY_LABEL_LENGTH
        ? `${label.slice(0, MAX_INVENTORY_LABEL_LENGTH - 3)}...`
        : label;
    };
    const invLine = [
      ...visibleInventoryItems.map(formatInventoryLabel),
      ...(hiddenInventoryCount > 0 ? [`+${hiddenInventoryCount}`] : []),
    ].join("    ·    ");
    s.addText(invLine || T("inventory_snapshots.empty"), {
      x: MARGIN,
      y: 3.6,
      w: CONTENT_W,
      h: 0.4,
      fontSize: 9,
      color: INK,
      fontFace: FONT,
      valign: "top",
    });

    // Milestone roadmap — always three (Ramp / Mid-flight / Closeout) with the
    // S-curve achievement %, matching the on-screen card. PRD §10.5.7.
    const forecast = resolveGoalForecast(goalType, perf);
    const roadmap = computeGoalRoadmap(
      headerInfo?.startDate,
      headerInfo?.endDate,
      forecast,
      targetValue,
    );
    s.addText(T("why_plan.expected_goal_achievement").toUpperCase(), {
      x: MARGIN,
      y: 4.2,
      w: CONTENT_W - 3,
      h: 0.3,
      fontSize: 9,
      color: MUTED,
      fontFace: FONT,
    });
    if (roadmap.length) {
      s.addText(T(`why_plan.track_note_${roadmap[0].unit}`), {
        x: MARGIN + CONTENT_W - 4,
        y: 4.2,
        w: 4,
        h: 0.3,
        align: "right",
        italic: true,
        fontSize: 8,
        color: MUTED,
        fontFace: FONT,
      });
    }
    roadmap.forEach((m, i) => {
      const rowY = 4.62 + i * 0.8;
      const unitName = T(`why_plan.unit_${m.unit}`);
      // Label line: "{Unit} {ordinal}  {date range} · {Phase}"  +  {pct}% right
      s.addText(
        [
          {
            text: `${unitName} ${m.ordinal}`,
            options: { fontSize: 11, bold: true, color: INK },
          },
          {
            text: `  ${m.dateRange} · ${T(`why_plan.phase_${m.phase}`)}`,
            options: { fontSize: 9, color: MUTED },
          },
        ],
        { x: MARGIN, y: rowY, w: CONTENT_W - 1, h: 0.28, fontFace: FONT },
      );
      s.addText(`${m.pct}%`, {
        x: MARGIN + CONTENT_W - 1,
        y: rowY,
        w: 1,
        h: 0.28,
        align: "right",
        fontSize: 11,
        bold: true,
        color: INK,
        fontFace: FONT,
      });
      // Track + fill (fill capped at 100%, label shows the true pct).
      const barY = rowY + 0.34;
      s.addShape(pptx.ShapeType.roundRect, {
        x: MARGIN,
        y: barY,
        w: CONTENT_W,
        h: 0.14,
        fill: { color: "E7EBF0" },
        line: { type: "none" },
        rectRadius: 0.07,
      });
      s.addShape(pptx.ShapeType.roundRect, {
        x: MARGIN,
        y: barY,
        w: Math.max((CONTENT_W * Math.min(m.pct, 100)) / 100, 0.05),
        h: 0.14,
        fill: { color: primary },
        line: { type: "none" },
        rectRadius: 0.07,
      });
    });
    note(s, T("why_plan.note"));
  }

  void MUTED;
  await pptx.writeFile({
    fileName: fileName || `${headerInfo?.name || "media-plan"}.pptx`,
  });
};
