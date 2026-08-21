import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, vi } from "vitest";

import type {
  AnalyticsExcelData,
  DOOHPanelRow,
  DOOHRollupHeatmap,
} from "../../analyticsTypes";
import DOOHSchedulesTab from "../DOOHSchedulesTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
  }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("DOOHSchedulesTab", () => {
  const baseAnalyticsData: AnalyticsExcelData = { doohPanels: [] };

  beforeEach(() => {
    vi.clearAllMocks?.();
  });

  it("renders both sections and shows the empty message when there are no panels", () => {
    render(
      <DOOHSchedulesTab
        analyticsData={baseAnalyticsData}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    expect(
      document.getElementById("media-plan-analytics-dooh-calendar-card"),
    ).toBeInTheDocument();
    expect(
      document.getElementById("media-plan-analytics-dooh-cadence-card"),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("mediaPlanAnalytics.doohSchedules.noData").length,
    ).toBe(2);
  });

  const makePanel = (overrides: Partial<DOOHPanelRow> = {}): DOOHPanelRow => ({
    id: "INV-1",
    inventoryName: "Times Square Billboard",
    referenceId: "INV-1",
    format: "Digital",
    city: "Manhattan",
    channel: "Classic",
    startDate: "Apr 1, 2026",
    endDate: "Apr 30, 2026",
    days: 30,
    opHoursLabel: "mixed",
    segments: [
      {
        id: "seg-1",
        segmentName: "Morning Rush",
        startDate: "Apr 1, 2026",
        endDate: "Apr 15, 2026",
        days: 15,
        opHoursLabel: "06:00–10:00",
        activeDates: ["2026-04-01", "2026-04-02"],
      },
      {
        id: "seg-2",
        segmentName: "Evening Peak",
        startDate: "Apr 16, 2026",
        endDate: "Apr 30, 2026",
        days: 15,
        opHoursLabel: "17:00–20:00",
        activeDates: ["2026-04-16"],
      },
    ],
    spotsPerLoop: 1.5,
    spotsPerHour: 45,
    activeHoursPerDay: 7.3,
    daysPerWeek: 7,
    sov: 30.4,
    pattern: "commuter",
    ...overrides,
  });

  it("renders the calendar with panel + segment rows and shades active dates", () => {
    render(
      <DOOHSchedulesTab
        analyticsData={{ doohPanels: [makePanel()] }}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    expect(
      document.getElementById("media-plan-analytics-dooh-panel-INV-1"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Times Square Billboard").length).toBe(2);
    expect(screen.getByText("INV-1")).toBeInTheDocument();
    expect(screen.getByText("Morning Rush")).toBeInTheDocument();
    expect(screen.getByText("Evening Peak")).toBeInTheDocument();
    expect(screen.getByText("06:00–10:00")).toBeInTheDocument();
    // Panel row shows "Mixed" since segments have differing opHoursLabel
    expect(
      screen.getByText("mediaPlanAnalytics.doohSchedules.calendar.mixed"),
    ).toBeInTheDocument();

    const segRow = document.getElementById(
      "media-plan-analytics-dooh-segment-seg-1",
    );
    const shadedCell = segRow?.querySelector('td[style*="rgb(37, 99, 235)"]');
    expect(shadedCell).toBeInTheDocument();
  });

  it("renders the cadence table with pattern badge and weighted metrics", () => {
    render(
      <DOOHSchedulesTab
        analyticsData={{ doohPanels: [makePanel()] }}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    expect(
      document.getElementById("media-plan-analytics-dooh-cadence-row-INV-1"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("mediaPlanAnalytics.doohSchedules.pattern.commuter"),
    ).toBeInTheDocument();
    expect(screen.getByText("1.5")).toBeInTheDocument();
    expect(screen.getByText("45")).toBeInTheDocument();
    expect(screen.getByText("7.3")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
    expect(screen.getByText("30.4%")).toBeInTheDocument();
    expect(screen.getByText("Digital · Manhattan")).toBeInTheDocument();
  });

  it("counts custom-day-parting vs 24/7-default panels in the cadence subtitle", () => {
    const panels = [
      makePanel({ id: "a", pattern: "commuter" }),
      makePanel({ id: "b", pattern: "24/7" }),
    ];
    render(
      <DOOHSchedulesTab
        analyticsData={{ doohPanels: panels }}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    expect(
      screen.getByText(
        'mediaPlanAnalytics.doohSchedules.cadence.subtitle:{"total":2,"custom":1,"default":1}',
      ),
    ).toBeInTheDocument();
  });

  const makeRollup = (
    overrides: Partial<DOOHRollupHeatmap> = {},
  ): DOOHRollupHeatmap => ({
    rows: [
      {
        day: "Mon",
        cells: Array.from({ length: 24 }, (_, hour) => ({
          hour,
          count: hour === 17 ? 2 : 0,
        })),
      },
    ],
    maxCount: 2,
    totalSchedules: 6,
    totalPatterns: 3,
    ...overrides,
  });

  it("shows the rollup heatmap by default with the schedules/patterns summary", () => {
    render(
      <DOOHSchedulesTab
        analyticsData={{ doohPanels: [], doohRollupHeatmap: makeRollup() }}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    expect(
      document.getElementById("media-plan-analytics-dooh-rollup-card"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'mediaPlanAnalytics.doohSchedules.rollup.summary:{"schedules":6,"patterns":3}',
      ),
    ).toBeInTheDocument();
    expect(screen.getByTitle("Mon 17:00 — 2")).toBeInTheDocument();
  });

  it("animates the rollup heatmap closed (collapses its container) when the toggle is switched off", async () => {
    const user = userEvent.setup();
    render(
      <DOOHSchedulesTab
        analyticsData={{ doohPanels: [], doohRollupHeatmap: makeRollup() }}
        flightStartDate="2026-04-01"
        flightEndDate="2026-04-30"
      />,
    );
    const card = document.getElementById(
      "media-plan-analytics-dooh-rollup-card",
    );
    expect(card).toBeInTheDocument();
    // Collapsible wrapper (CSS grid-rows transition) starts expanded.
    expect(card?.parentElement?.parentElement).toHaveClass("grid-rows-[1fr]");

    await user.click(screen.getByRole("checkbox"));

    // Stays mounted (so the collapse can animate) but the wrapper collapses.
    expect(card).toBeInTheDocument();
    expect(card?.parentElement?.parentElement).toHaveClass("grid-rows-[0fr]");
  });
});
