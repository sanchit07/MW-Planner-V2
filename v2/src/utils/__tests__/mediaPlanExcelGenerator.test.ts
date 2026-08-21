/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock ExcelExporter so we don't need browser APIs
vi.mock("../excelExport.utils", () => ({
  ExcelExporter: vi.fn().mockImplementation(() => ({
    export: vi.fn().mockResolvedValue(undefined),
    getThemeColor: vi.fn().mockReturnValue("FF000000"),
  })),
}));

// Mock initializeThemeColors
vi.mock("../themeColors", () => ({
  initializeThemeColors: vi.fn().mockReturnValue({
    primary: "FF4472C4",
    secondary: "FF2E75B6",
    lightGray: "FFF2F2F2",
  }),
  getCssVariableValue: vi.fn().mockReturnValue("#2176cc"),
  hexToRgbString: vi.fn().mockReturnValue("2176CC"),
  hexToArgb: vi.fn().mockReturnValue("FF2176CC"),
}));

// Mock buildDOOHCalendarWeeks so the DOOH sheet test doesn't depend on real
// calendar-date math — only the sheet-inclusion/gating behavior is under test.
vi.mock("../../pages/campaigns/media-plan/utils", () => ({
  buildDOOHCalendarWeeks: vi.fn().mockReturnValue([]),
}));

import { ExcelExporter } from "../excelExport.utils";
import { generateMediaPlanExcel } from "../mediaPlanExcelGenerator";

const MockedExcelExporter = ExcelExporter as unknown as ReturnType<
  typeof vi.fn
>;

// Minimal campaign details object
const campaignDetails = {
  campaignName: "Test Campaign",
  campaignId: "C-001",
  createdOn: "2026-01-01",
  startDate: "2026-02-01",
  endDate: "2026-03-01",
  goal: "Awareness",
  kpi: "Impressions",
  currency: "USD",
};

describe("generateMediaPlanExcel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls ExcelExporter.export() and resolves", async () => {
    await expect(generateMediaPlanExcel({ data: {} })).resolves.toBeUndefined();
    expect(MockedExcelExporter).toHaveBeenCalledTimes(1);
  });

  it("uses provided fileName", async () => {
    await generateMediaPlanExcel({ data: {}, fileName: "my_report.xlsx" });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    expect(constructorArg.filename).toBe("my_report.xlsx");
  });

  it("generates filename from campaignName when fileName not provided", async () => {
    await generateMediaPlanExcel({ data: {}, campaignName: "My Campaign" });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    expect(constructorArg.filename).toContain("My Campaign");
    expect(constructorArg.filename).toContain("Analytics");
    expect(constructorArg.filename).toContain(".xlsx");
  });

  it("adds no sheets when data is empty", async () => {
    await generateMediaPlanExcel({ data: {} });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    expect(constructorArg.sheets).toHaveLength(0);
  });

  it("adds Plan sheet when campaignDetails provided", async () => {
    await generateMediaPlanExcel({ data: { campaignDetails } });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("Plan");
  });

  it("adds Inventory Details sheet when inventoryDetails provided", async () => {
    await generateMediaPlanExcel({
      data: {
        inventoryDetails: [
          {
            id: "inv-1",
            type: "digital",
            billboardName: "Board A",
            referenceId: "REF001",
          } as any,
        ],
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("Inventory Details");
  });

  it("skips Inventory Details sheet when the array is empty", async () => {
    await generateMediaPlanExcel({ data: { inventoryDetails: [] } });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).not.toContain("Inventory Details");
  });

  it("adds Costing sheet when costingInventoryRows provided", async () => {
    await generateMediaPlanExcel({
      data: {
        costingInventoryRows: [
          {
            id: "c1",
            name: "Board A",
            city: "Manhattan",
            baseCpm: 18.3,
            proposed: 18.3,
            accepted: 18.3,
            impressions: 100000,
            mediaCost: 5000,
            feeShare: 0,
            total: 5000,
          },
        ],
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("Costing");
  });

  it("skips Costing sheet when costingInventoryRows is empty", async () => {
    await generateMediaPlanExcel({ data: { costingInventoryRows: [] } });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).not.toContain("Costing");
  });

  it("adds Operation Details sheet when operationDetails provided", async () => {
    await generateMediaPlanExcel({
      data: {
        operationDetails: { classic: [], digital: [], mobile: [] } as any,
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("Operation Details");
  });

  it("adds DOOH Schedules sheet when doohPanels provided", async () => {
    await generateMediaPlanExcel({
      data: {
        doohPanels: [
          {
            id: "d1",
            inventoryName: "DOOH 1",
            referenceId: "INV-1",
            format: "Portrait",
            city: "Downtown",
            channel: "Digital",
            startDate: "Apr 1, 2026",
            endDate: "Jun 30, 2026",
            days: 91,
            opHoursLabel: "00:00–24:00",
            segments: [],
            spotsPerLoop: 1,
            spotsPerHour: 30,
            activeHoursPerDay: 24,
            daysPerWeek: 7,
            sov: 100,
            pattern: "24/7",
          },
        ] as any,
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("DOOH Schedules");
  });

  it("skips DOOH Schedules sheet when doohPanels is empty", async () => {
    await generateMediaPlanExcel({ data: { doohPanels: [] } });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).not.toContain("DOOH Schedules");
  });

  it("adds Geography Targeting sheet when geographyTargeting provided", async () => {
    await generateMediaPlanExcel({
      data: {
        geographyTargeting: [
          {
            id: "1",
            level: "country",
            depth: 0,
            name: "United States",
            inventories: 3,
            impressions: 7_700_000,
            reach: 3_100_000,
            frequency: 2.5,
            ecpm: 58.23,
          },
        ],
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    const sheetNames = constructorArg.sheets.map(
      (s: { name: string }) => s.name,
    );
    expect(sheetNames).toContain("Geography Targeting");
  });

  it("adds all sheets when complete data provided", async () => {
    await generateMediaPlanExcel({
      data: {
        campaignDetails,
        inventoryDetails: [{ id: "i1", type: "digital" } as any],
        costingInventoryRows: [
          {
            id: "c1",
            name: "Board A",
            city: "Manhattan",
            baseCpm: 18.3,
            proposed: 18.3,
            accepted: 18.3,
            impressions: 100000,
            mediaCost: 5000,
            feeShare: 0,
            total: 5000,
          },
        ],
        operationDetails: { classic: [], digital: [], mobile: [] } as any,
        doohPanels: [
          {
            id: "d1",
            inventoryName: "DOOH",
            referenceId: "INV-1",
            format: "Portrait",
            city: "Downtown",
            channel: "Digital",
            startDate: "Apr 1, 2026",
            endDate: "Jun 30, 2026",
            days: 91,
            opHoursLabel: "00:00–24:00",
            segments: [],
            spotsPerLoop: 1,
            spotsPerHour: 30,
            activeHoursPerDay: 24,
            daysPerWeek: 7,
            sov: 100,
            pattern: "24/7",
          },
        ] as any,
        geographyTargeting: [
          {
            id: "1",
            level: "country",
            depth: 0,
            name: "United States",
            inventories: 3,
            impressions: 7_700_000,
            reach: 3_100_000,
            frequency: 2.5,
            ecpm: 58.23,
          },
        ],
      },
    });
    const constructorArg = MockedExcelExporter.mock.calls[0][0];
    expect(constructorArg.sheets.length).toBe(6);
  });
});
