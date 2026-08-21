/* eslint-disable @typescript-eslint/no-explicit-any */
import { formatCurrency } from "./campaign.utils";
import { ExcelExporter, type WorkbookConfig } from "./excelExport.utils";
import { initializeThemeColors } from "./themeColors";
import type {
  AnalyticsExcelData,
  ClassicOperationScheduleRow,
  DigitalOperationScheduleRow,
  DOOHPanelRow,
  GeographyTargetingRow,
  MobileOperationScheduleRow,
} from "../pages/campaigns/media-plan/analyticsTypes";
import type { PresentationTheme } from "../pages/campaigns/media-plan/types";
import { buildDOOHCalendarWeeks } from "../pages/campaigns/media-plan/utils";

interface ExcelGenerationOptions {
  data: AnalyticsExcelData;
  fileName?: string;
  campaignName?: string;
  theme?: PresentationTheme;
}

interface ThemeColors {
  primary: string;
  secondary: string;
  lightGray: string;
}

/**
 * Generates Excel workbook from analytics data using ExcelExporter.
 * Sheet layout matches "Planner - Proposal Template Excel V1.0.xlsx" exactly
 * (Plan, Inventory Details, Costing, Operation Details, DOOH Schedules,
 * Geography Targeting). A Cinema sheet is added when the plan has cinema
 * line items — gated on the same condition as the on-screen Cinema tab.
 */
export const generateMediaPlanExcel = async (
  options: ExcelGenerationOptions,
): Promise<void> => {
  const { data, fileName, campaignName, theme } = options;

  const themeColors: ThemeColors = initializeThemeColors(undefined, theme);
  const finalFileName =
    fileName ||
    `${campaignName || "MediaPlan"}_Analytics_${new Date().toISOString().split("T")[0]}.xlsx`;

  const workbookConfig: WorkbookConfig = {
    filename: finalFileName,
    theme,
    sheets: [],
    globalStyle: {
      border: {
        top: { style: "thin", color: "FF000000" },
        left: { style: "thin", color: "FF000000" },
        bottom: { style: "thin", color: "FF000000" },
        right: { style: "thin", color: "FF000000" },
      },
    },
  };

  if (data.campaignDetails) {
    workbookConfig.sheets.push(buildPlanSheet(data));
  }

  if (data.inventoryDetails && data.inventoryDetails.length > 0) {
    workbookConfig.sheets.push(buildInventoryDetailsSheet(data));
  }

  if (data.costingInventoryRows && data.costingInventoryRows.length > 0) {
    workbookConfig.sheets.push(buildCostingSheet(data));
  }

  if (data.operationDetails) {
    workbookConfig.sheets.push(buildOperationDetailsSheet(data, themeColors));
  }

  if (data.doohPanels && data.doohPanels.length > 0) {
    workbookConfig.sheets.push(buildDOOHSchedulesSheet(data, themeColors));
  }

  if (data.geographyTargeting && data.geographyTargeting.length > 0) {
    workbookConfig.sheets.push(buildGeographyTargetingSheet(data, themeColors));
  }

  // Cinema sheet — gated on EXACTLY the same condition as the on-screen
  // Cinema analytics tab and the PPT Cinema slide (parity contract).
  if (data.cinemaInventory && data.cinemaInventory.length > 0) {
    workbookConfig.sheets.push(buildCinemaSheet(data));
  }

  const exporter = new ExcelExporter(workbookConfig);
  await exporter.export();
};

/**
 * Build "Plan" sheet — Plan Details / Buyer Details / Estimated Performance
 * Metrics / Targeting Applied cards, then City Insights and Delivery
 * Breakdown tables. Matches the template 1:1 (no State/Inventory
 * Planning/Mapping tables — those were removed from the UI Plan tab too).
 */
function buildPlanSheet(data: AnalyticsExcelData) {
  const sheetData: Array<Record<string, any>> = [];
  let currentRow = 1;

  const cd = data.campaignDetails;
  const epm = data.estimatedPerformanceMetrics;
  const ta = data.targetingApplied;
  const currency = cd?.currency || "";

  if (cd) {
    const cards: Array<{
      title: string;
      data: Array<[string, string | number]>;
    }> = [
      {
        title: "Plan Details",
        data: [
          ["Plan Name", cd.campaignName],
          ["Plan ID", cd.planNumber || cd.campaignId],
          ["Status", cd.status || ""],
          ["Start", cd.startDate],
          ["End", cd.endDate],
          ["Duration", cd.durationLabel || ""],
          ["Currency", cd.currency],
          ["Channels", cd.channelsLabel || ""],
          [`Budget (${currency})`, formatCurrency(cd.budget || 0, currency)],
          ["Goal", cd.goalLabel || cd.goal],
        ],
      },
      {
        title: "Buyer Details",
        data: [
          ["Brand", cd.brand || ""],
          ["Brand Category", cd.brandCategory || ""],
          ["Client Type", cd.clientTypeLabel || ""],
          ["Agency", cd.agency || ""],
          ["Planned By", cd.createdBy || ""],
          ["Company", cd.company || ""],
        ],
      },
    ];

    if (epm) {
      cards.push({
        title: "Estimated Performance Metrics",
        data: [
          ["Total Impressions", epm.totalImpressions.toLocaleString()],
          ["Estimated Reach", epm.estimatedReach.toLocaleString()],
          ["Avg Frequency", epm.avgFrequency.toFixed(2)],
          // Ad plays is a digital-slot concept — omit for classic-only plans.
          ...(epm.hasDigitalInventory
            ? ([["Est. Ad Plays", epm.estAdPlays.toLocaleString()]] as Array<
                [string, string | number]
              >)
            : []),
          [
            `${epm.avgCpmLabel} (${currency})`,
            formatCurrency(epm.avgCpm, currency),
          ],
          [`eCPM (${currency})`, formatCurrency(epm.ecpm, currency)],
          ["Share of Voice (SOV) %", `${epm.sov.toFixed(1)}%`],
          ["Share of Time (SOT) %", `${epm.sot.toFixed(1)}%`],
          [`Total Cost (${currency})`, formatCurrency(epm.totalCost, currency)],
          ["Inventories", epm.inventories.toLocaleString()],
          ["Cities", epm.cities.toLocaleString()],
          ["Channels", epm.channels.toLocaleString()],
        ],
      });
    }

    sheetData.push({
      type: "cards",
      startRow: currentRow,
      startColumn: 1,
      cardSpacing: 1,
      cardWidth: 2,
      cards,
    });

    const maxRows = Math.max(...cards.map((card) => card.data.length));
    currentRow += maxRows + 2;

    if (epm?.footnote) {
      sheetData.push({
        type: "row",
        row: currentRow,
        startColumn: 1,
        data: [{ value: epm.footnote, style: { fontSize: 9 } }],
      });
      currentRow += 2;
    } else {
      currentRow += 1;
    }
  }

  // Targeting Applied — its own row, own (wide, wrapped) value column, since
  // fields like Venue Environments can be a 100+ item comma list; sharing a
  // column with the other cards above would either truncate it or spill
  // into their cells.
  if (ta) {
    const targetingCard = {
      title: "Targeting Applied",
      data: [
        ["Demographics", ta.demographics || ""],
        ["Income", ta.income || ""],
        ["Interests", ta.interests || ""],
        ["Venue Environments", ta.venueEnvironments || ""],
        ["Behaviour", ta.behaviour || ""],
      ] as Array<[string, string]>,
      valueStyle: {
        wrapText: true,
        alignment: { horizontal: "left" as const, vertical: "top" as const },
      },
    };

    sheetData.push({
      type: "cards",
      startRow: currentRow,
      startColumn: 1,
      cardWidth: 6,
      cards: [targetingCard],
    });

    currentRow += targetingCard.data.length + 2;
  }

  // City Insights table
  if (data.cityPlanning && data.cityPlanning.length > 0) {
    sheetData.push({
      type: "table",
      startRow: currentRow,
      startColumn: "auto",
      title: "City Insights",
      headers: [
        { header: "City Name", key: "stateName" },
        { header: "Population", key: "population" },
        { header: "Inventories", key: "inventories" },
        { header: "Impressions", key: "oohImpressions" },
        { header: "Reach", key: "reach" },
        { header: "Frequency", key: "frequency" },
        { header: `CPM (${currency})`, key: "cpm" },
      ],
      data: data.cityPlanning.map((row) => ({
        ...row,
        cpm: Number(row.cpm.toFixed(2)),
      })),
    });
    currentRow += data.cityPlanning.length + 4;
  }

  // Delivery Breakdown (Weekly/Monthly, matches computeExpectedDelivery) table
  if (data.deliveryBreakdown && data.deliveryBreakdown.length > 0) {
    const granularityLabel =
      data.deliveryGranularity === "weekly" ? "Weekly" : "Monthly";
    sheetData.push({
      type: "table",
      startRow: currentRow,
      startColumn: "auto",
      title: `Delivery Breakdown (${granularityLabel})`,
      headers: [
        { header: "Period", key: "period" },
        { header: "Impressions", key: "impressions" },
        { header: "Reach", key: "reach" },
      ],
      data: data.deliveryBreakdown,
    });
  }

  return {
    name: "Plan",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/** "digital"/"digital network" -> "Digital"; everything else -> "Classic" —
 * only two real media channels exist in the product (see MediaChannel enum). */
const channelLabelForInventoryType = (type: string): string =>
  type.includes("digital") ? "Digital" : "Classic";

/** SchedulePattern (@utils/schedule.utils) -> display label. The Excel
 * export has no i18n (every other sheet label here is a hardcoded English
 * literal too), so this mirrors DOOHSchedulesTab.tsx's English copy. */
const PATTERN_LABELS: Record<string, string> = {
  default: "Default",
  commuter: "Commuter Pattern",
  business: "Business Hours",
  nightlife: "Nightlife",
  weekend: "Weekend Focus",
  "24/7": "24/7 Default",
  custom: "Custom",
  "--": "--",
};
const patternLabel = (pattern: string): string =>
  PATTERN_LABELS[pattern] || PATTERN_LABELS.custom;

/**
 * Build "Inventory Details" sheet — flat table, one row per inventory.
 * Matches the template's 9 columns exactly.
 */
function buildInventoryDetailsSheet(data: AnalyticsExcelData) {
  const rows = data.inventoryDetails || [];
  const currency = data.campaignDetails?.currency || "";

  const sheetData: Array<Record<string, any>> = [
    {
      type: "table",
      startRow: 1,
      startColumn: "auto",
      title: `Inventory Details (${rows.length})`,
      headers: [
        { header: "Inventory", key: "inventory" },
        { header: "Channel", key: "channel" },
        { header: "Format", key: "format" },
        { header: "City", key: "city" },
        { header: "Media Owner", key: "mediaOwner" },
        { header: "Impressions", key: "impressions" },
        { header: "Plays/day", key: "playsPerDay" },
        { header: `CPM (${currency})`, key: "cpm" },
      ],
      data: rows.map((row) => ({
        inventory: row.billboardName || "",
        channel: channelLabelForInventoryType(row.type),
        format: row.format || "",
        city: row.city || "",
        mediaOwner: row.mediaOwner || "",
        impressions: row.impressions || 0,
        playsPerDay: row.type.includes("classic") ? "-" : row.playsPerDay || 0,
        cpm: row.type.includes("classic") ? "-" : row.cpm || 0,
      })),
    },
  ];

  return {
    name: "Inventory Details",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/**
 * Build "Costing" sheet — flat per-inventory table with a Totals row.
 * Matches the template's 9 columns exactly.
 */
function buildCostingSheet(data: AnalyticsExcelData) {
  const rows = data.costingInventoryRows || [];
  const currency = data.campaignDetails?.currency || "";

  const currencyCell = (amount: number | null | undefined) => ({
    value: amount ?? 0,
    style: {
      format: "custom" as const,
      customFormat: `[$${currency}] #,##0.00`,
    },
  });

  const sheetData: Array<Record<string, any>> = [
    {
      type: "table",
      startRow: 1,
      startColumn: "auto",
      title: `Costing (${currency})`,
      headers: [
        { header: "Inventory", key: "name" },
        { header: "City", key: "city" },
        { header: "Base CPM", key: "baseCpm" },
        { header: "Impressions", key: "impressions" },
        { header: "Media Cost", key: "mediaCost" },
        { header: "Fee Share", key: "feeShare" },
        { header: "Total", key: "total" },
      ],
      data: rows.map((row) => ({
        name: row.name,
        city: row.city,
        baseCpm: currencyCell(row.baseCpm),
        impressions: row.impressions,
        mediaCost: currencyCell(row.mediaCost),
        feeShare: currencyCell(row.feeShare),
        total: currencyCell(row.total),
      })),
      showTotalRow: true,
      totalRowConfig: {
        label: "Totals",
        columns: [4, 5, 6],
        labelColumn: 0,
        labelColspan: 4,
      },
      tableStyle: {
        totalRowStyle: {
          format: "custom",
          customFormat: `[$${currency}] #,##0.00`,
        },
      },
    },
  ];

  return {
    name: "Costing",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/**
 * Build "Cinema" sheet — one flat table of cinema line items. Cinema is
 * bought by operator/hall/showtime-window with genre/rating constraints;
 * films are only an indicative preview, never a buy unit (note row below the
 * title). Same columns as the on-screen Cinema tab (parity contract).
 */
function buildCinemaSheet(data: AnalyticsExcelData) {
  const rows = data.cinemaInventory || [];
  const currency = data.campaignDetails?.currency || "";

  const sheetData: Array<Record<string, any>> = [
    {
      type: "title",
      row: 1,
      startColumn: 1,
      endColumn: 10,
      value: "Films are indicative only — the buy is operator/hall/showtime.",
      style: { fontSize: 10 },
    },
    {
      type: "table",
      startRow: 3,
      startColumn: "auto",
      title: `Cinema (${rows.length})`,
      headers: [
        { header: "Inventory", key: "inventory" },
        { header: "Operator", key: "operator" },
        { header: "Cinema", key: "cinema" },
        { header: "Hall", key: "hall" },
        { header: "Showtime Windows", key: "showtimeWindows" },
        { header: "Genres", key: "genres" },
        { header: "Ratings", key: "ratings" },
        { header: "Impressions", key: "impressions" },
        { header: `CPM (${currency})`, key: "cpm" },
        { header: `Media Cost (${currency})`, key: "mediaCost" },
      ],
      data: rows.map((row) => ({
        inventory: row.name || "",
        operator: row.operator || "",
        cinema: row.cinemaName || "",
        hall: row.hall || "",
        showtimeWindows: row.showtimeWindows || "",
        genres: row.genres || "",
        ratings: row.ratings || "",
        impressions: row.impressions || 0,
        cpm: row.cpm || 0,
        mediaCost: row.mediaCost || 0,
      })),
    },
  ];

  return {
    name: "Cinema",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/**
 * Build "Operation Details" sheet — one flat table per channel (Classic /
 * Digital / Mobile), one row per inventory-schedule. No per-inventory group
 * headers (that grouping was a UI-only convenience, not in the template).
 */
function buildOperationDetailsSheet(
  data: AnalyticsExcelData,
  themeColors: ThemeColors,
) {
  const sheetData: Array<Record<string, any>> = [];
  let currentRow = 1;

  const sectionTitle = (value: string, endColumn: number) => {
    sheetData.push({
      type: "title",
      row: currentRow,
      startColumn: 1,
      endColumn,
      value,
      style: {
        backgroundColor: themeColors.secondary,
        fontColor: "FFFFFFFF",
        fontWeight: "bold",
        fontSize: 12,
      },
    });
    currentRow += 2;
  };

  const classic = data.operationDetails?.classic || [];
  if (classic.length > 0) {
    sectionTitle("Classic", 7);
    sheetData.push({
      type: "table",
      startRow: currentRow,
      startColumn: "auto",
      headers: [
        { header: "Inventory", key: "inventoryName" },
        { header: "Reference ID", key: "referenceId" },
        { header: "Format", key: "format" },
        { header: "City", key: "city" },
        { header: "Start Date", key: "startDate" },
        { header: "End Date", key: "endDate" },
        { header: "Operation Days", key: "operationDays" },
      ],
      data: classic.map((row: ClassicOperationScheduleRow) => ({
        inventoryName: row.inventoryName,
        referenceId: row.referenceId,
        format: row.format,
        city: row.city,
        startDate: row.startDate,
        endDate: row.endDate,
        operationDays: row.operationDays,
      })),
    });
    currentRow += classic.length + 4;
  }

  const digital = data.operationDetails?.digital || [];
  if (digital.length > 0) {
    sectionTitle("Digital", 12);
    sheetData.push({
      type: "table",
      startRow: currentRow,
      startColumn: "auto",
      headers: [
        { header: "Inventory", key: "inventoryName" },
        { header: "Reference ID", key: "referenceId" },
        { header: "Format", key: "format" },
        { header: "City", key: "city" },
        { header: "Start", key: "startDate" },
        { header: "End", key: "endDate" },
        { header: "Op Days", key: "operationDays" },
        { header: "Op Hours", key: "operationHours" },
        { header: "Start Time", key: "startTime" },
        { header: "End Time", key: "endTime" },
        { header: "Total Spots", key: "totalSpots" },
      ],
      data: digital.map((row: DigitalOperationScheduleRow) => ({
        inventoryName: row.inventoryName,
        referenceId: row.referenceId,
        format: row.format,
        city: row.city,
        startDate: row.startDate,
        endDate: row.endDate,
        operationDays: row.operationDays,
        operationHours: row.operationHours,
        startTime: row.startTime,
        endTime: row.endTime,
        totalSpots: row.totalSpots,
      })),
    });
    currentRow += digital.length + 4;
  }

  const mobile = data.operationDetails?.mobile || [];
  if (mobile.length > 0) {
    sectionTitle("Mobile", 10);
    sheetData.push({
      type: "table",
      startRow: currentRow,
      startColumn: "auto",
      headers: [
        { header: "Inventory", key: "inventoryName" },
        { header: "Reference ID", key: "referenceId" },
        { header: "Format", key: "format" },
        { header: "City", key: "city" },
        { header: "Start Date", key: "startDate" },
        { header: "End Date", key: "endDate" },
        { header: "Operation Days", key: "operationDays" },
        { header: "Operation Hours", key: "operationHours" },
        { header: "Start Time", key: "startTime" },
        { header: "End Time", key: "endTime" },
      ],
      data: mobile.map((row: MobileOperationScheduleRow) => ({
        inventoryName: row.inventoryName,
        referenceId: row.referenceId,
        format: row.format,
        city: row.city,
        startDate: row.startDate,
        endDate: row.endDate,
        operationDays: row.operationDays,
        operationHours: row.operationHours,
        startTime: row.startTime,
        endTime: row.endTime,
      })),
    });
  }

  return {
    name: "Operation Details",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/**
 * Build "DOOH Schedules" sheet — real per-calendar-date columns (via
 * buildDOOHCalendarWeeks, flattened) with "X" marks for active days, then
 * the per-inventory cadence table. Digital panels only — classic/transit
 * inventories have no hourly booking-matrix data to build a real cadence
 * from, so (unlike the demo template) they're not included here.
 */
function buildDOOHSchedulesSheet(
  data: AnalyticsExcelData,
  themeColors: ThemeColors,
) {
  const sheetData: Array<Record<string, any>> = [];
  let currentRow = 1;
  const panels = data.doohPanels || [];

  sheetData.push({
    type: "title",
    row: currentRow,
    startColumn: 1,
    endColumn: 7,
    value: "DOOH Schedule Calendar",
    style: {
      fontWeight: "bold",
      fontSize: 16,
      backgroundColor: themeColors.secondary,
      fontColor: "FFFFFFFF",
    },
  });
  currentRow++;

  sheetData.push({
    type: "row",
    row: currentRow,
    startColumn: 1,
    data: [
      {
        value: `${panels.length} digital panels · week-by-week activity (Sun → Sat). Shaded cells = the panel runs that day.`,
        style: { fontSize: 10 },
      },
    ],
  });
  currentRow += 2;

  // Calendar span = the real union of every segment's bookingMatrix dates
  // (panel.startDate/endDate are already display-formatted, not raw ISO —
  // activeDates are the raw "YYYY-MM-DD" keys buildDOOHCalendarWeeks needs).
  const allActiveDates = panels
    .flatMap((panel) =>
      panel.segments.flatMap((segment) => segment.activeDates),
    )
    .sort();
  const minStart = allActiveDates[0];
  const maxEnd = allActiveDates[allActiveDates.length - 1];
  const calendarDays = buildDOOHCalendarWeeks(minStart, maxEnd).flatMap(
    (week) => week.days,
  );

  const calendarHeaders = [
    { header: "Schedule", key: "schedule" },
    { header: "Inventory Name", key: "inventoryName" },
    { header: "Reference ID", key: "referenceId" },
    { header: "Start", key: "start" },
    { header: "End", key: "end" },
    { header: "Days", key: "days" },
    { header: "Op Hours", key: "opHours" },
    ...calendarDays.map((day) => ({
      header: day.date.slice(5),
      key: day.date,
    })),
  ];

  const calendarRows: Array<Record<string, any>> = [];
  panels.forEach((panel: DOOHPanelRow, panelIndex: number) => {
    const blankDayCells: Record<string, string> = {};
    calendarDays.forEach((day) => {
      blankDayCells[day.date] = "";
    });

    calendarRows.push({
      schedule: `${panelIndex + 1}.0`,
      inventoryName: panel.inventoryName,
      referenceId: panel.referenceId,
      start: panel.startDate,
      end: panel.endDate,
      days: panel.days,
      opHours: panel.opHoursLabel,
      ...blankDayCells,
    });

    panel.segments.forEach((segment, segIndex) => {
      const activeDateSet = new Set(segment.activeDates);
      const dayCells: Record<string, string> = {};
      calendarDays.forEach((day) => {
        dayCells[day.date] = activeDateSet.has(day.date) ? "X" : "";
      });

      calendarRows.push({
        schedule: `${panelIndex + 1}.${segIndex + 1}`,
        inventoryName: segment.segmentName || "Full flight",
        referenceId: "",
        start: segment.startDate,
        end: segment.endDate,
        days: segment.days,
        opHours: segment.opHoursLabel,
        ...dayCells,
      });
    });
  });

  sheetData.push({
    type: "table",
    startRow: currentRow,
    startColumn: "auto",
    headers: calendarHeaders,
    data: calendarRows,
  });
  currentRow += calendarRows.length + 3;

  // Per-inventory cadence table
  const customCount = panels.filter((p) => p.pattern !== "24/7").length;
  sheetData.push({
    type: "title",
    row: currentRow,
    startColumn: 1,
    endColumn: 8,
    value: "Per-inventory cadence",
    style: {
      backgroundColor: themeColors.secondary,
      fontColor: "FFFFFFFF",
      fontWeight: "bold",
      fontSize: 12,
    },
  });
  currentRow++;
  sheetData.push({
    type: "row",
    row: currentRow,
    startColumn: 1,
    data: [
      {
        value: `${panels.length} panels · ${customCount} with custom day-parting · ${panels.length - customCount} on 24/7 default`,
        style: { fontSize: 10 },
      },
    ],
  });
  currentRow += 2;

  sheetData.push({
    type: "table",
    startRow: currentRow,
    startColumn: "auto",
    headers: [
      { header: "Inventory", key: "inventory" },
      { header: "Channel", key: "channel" },
      { header: "Pattern", key: "pattern" },
      { header: "Spots/Loop", key: "spotsPerLoop" },
      { header: "Spots/Hour", key: "spotsPerHour" },
      { header: "Active Hrs/Day", key: "activeHoursPerDay" },
      { header: "Days/Week", key: "daysPerWeek" },
      { header: "SOV %", key: "sov" },
    ],
    data: panels.map((panel) => ({
      inventory: `${panel.inventoryName} (${panel.format} · ${panel.city})`,
      channel: panel.channel,
      pattern: patternLabel(panel.pattern),
      spotsPerLoop: Math.round(panel.spotsPerLoop * 10) / 10,
      spotsPerHour: Math.round(panel.spotsPerHour * 10) / 10,
      activeHoursPerDay: Math.round(panel.activeHoursPerDay * 10) / 10,
      daysPerWeek: panel.daysPerWeek,
      sov: `${panel.sov.toFixed(0)}%`,
    })),
  });

  return {
    name: "DOOH Schedules",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}

/**
 * Build "Geography Targeting" sheet — Country › Region › City hierarchy,
 * with matched POI/geofence rows nested under their city. No Audience Score
 * column — that field doesn't exist anywhere in the real data model.
 */
function buildGeographyTargetingSheet(
  data: AnalyticsExcelData,
  themeColors: ThemeColors,
) {
  const sheetData: Array<Record<string, any>> = [];
  let currentRow = 1;
  const currency = data.campaignDetails?.currency || "";

  sheetData.push({
    type: "title",
    row: currentRow,
    startColumn: 1,
    endColumn: 6,
    value: "Targeted Geography",
    style: {
      fontWeight: "bold",
      fontSize: 16,
      backgroundColor: themeColors.secondary,
      fontColor: "FFFFFFFF",
    },
  });
  currentRow++;

  sheetData.push({
    type: "row",
    row: currentRow,
    startColumn: 1,
    data: [
      {
        value:
          "Country › Region › City hierarchy with per-area performance. Coordinates map to their single nearest inventory.",
        style: { fontSize: 10 },
      },
    ],
  });
  currentRow += 2;

  const geographyRows = data.geographyTargeting || [];
  const poiRowsByCityId = new Map<
    string,
    NonNullable<AnalyticsExcelData["geographyTargetingPoiRows"]>
  >();
  (data.geographyTargetingPoiRows || []).forEach((poi) => {
    const list = poiRowsByCityId.get(poi.parentCityId) || [];
    list.push(poi);
    poiRowsByCityId.set(poi.parentCityId, list);
  });

  const indent = (depth: number) => "    ".repeat(depth);

  const tableRows: Array<Record<string, any>> = [];
  geographyRows.forEach((row: GeographyTargetingRow) => {
    tableRows.push({
      geography: `${indent(row.depth)}${row.name}`,
      inventories: row.inventories,
      impressions: row.impressions,
      reach: row.reach,
      frequency: row.frequency.toFixed(2),
      ecpm: row.ecpm.toFixed(2),
    });

    if (row.level === "city") {
      (poiRowsByCityId.get(row.id) || []).forEach((poi) => {
        tableRows.push({
          geography: `${indent(row.depth + 1)}${poi.name}`,
          inventories: poi.inventories,
          impressions: poi.impressions,
          reach: poi.reach,
          frequency: poi.frequency.toFixed(2),
          ecpm: poi.ecpm.toFixed(2),
        });
      });
    }
  });

  sheetData.push({
    type: "table",
    startRow: currentRow,
    startColumn: "auto",
    headers: [
      { header: "Geography", key: "geography", width: 60 },
      { header: "Inventories", key: "inventories" },
      { header: "Impressions", key: "impressions" },
      { header: "Reach", key: "reach" },
      { header: "Frequency", key: "frequency" },
      { header: `eCPM (${currency})`, key: "ecpm" },
    ],
    data: tableRows,
  });

  return {
    name: "Geography Targeting",
    pageSetup: { orientation: "landscape" as const, fitToPage: true },
    data: sheetData,
  };
}
