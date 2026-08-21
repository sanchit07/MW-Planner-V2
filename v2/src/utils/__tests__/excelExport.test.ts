import { describe, it, expect, vi, beforeEach } from "vitest";

import { ExcelExporter, sampleWorkbookConfig } from "../excelExport.utils";

// Minimal WorkbookConfig type alias for test ergonomics
type WbConfig = typeof sampleWorkbookConfig;

// Mock browser APIs that jsdom doesn't implement
const createObjectURLMock = vi.fn(() => "blob:mock-url");
const revokeObjectURLMock = vi.fn();

Object.defineProperty(globalThis, "URL", {
  value: {
    createObjectURL: createObjectURLMock,
    revokeObjectURL: revokeObjectURLMock,
  },
  writable: true,
});

// Prevent actual anchor click from doing anything
const clickMock = vi.fn();
const origCreateElement = document.createElement.bind(document);
vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
  const el = origCreateElement(tag);
  if (tag === "a") {
    Object.defineProperty(el, "click", { value: clickMock, writable: true });
  }
  return el;
});

// ── Minimal configs ──────────────────────────────────────────────────────────

const minimalConfig: WbConfig = {
  filename: "test.xlsx",
  sheets: [
    {
      name: "Sheet1",
      data: [
        {
          type: "row",
          startColumn: 1,
          row: 1,
          data: ["hello"],
        },
      ],
    },
  ],
};

const tableConfig: WbConfig = {
  filename: "table.xlsx",
  sheets: [
    {
      name: "TableSheet",
      data: [
        {
          type: "table",
          startRow: 1,
          startColumn: 1,
          headers: [
            { header: "Name", key: "name", width: 20 },
            { header: "Value", key: "value", width: 15 },
          ],
          data: [
            {
              name: "Alice",
              value: { value: 100, style: { format: "number" } },
            },
            { name: "Bob", value: 200 },
          ],
        },
      ],
    },
  ],
};

const tableWithExtrasConfig: WbConfig = {
  filename: "table_extras.xlsx",
  sheets: [
    {
      name: "ExtrasSheet",
      data: [
        {
          type: "table",
          startRow: 1,
          startColumn: "auto",
          title: "My Table Title",
          groupHeaders: [{ text: "Group A", colspan: 2, align: "center" }],
          headers: [
            { header: "Col1", key: "col1", width: 15 },
            { header: "Col2", key: "col2", width: 15 },
          ],
          data: [
            { col1: "r1c1", col2: { value: "r1c2", rowspan: 1, colspan: 1 } },
          ],
          showTotalRow: true,
          totalRowConfig: {
            label: "Total",
            columns: [1],
            labelColumn: 0,
          },
        },
      ],
    },
  ],
};

const rowConfig: WbConfig = {
  filename: "row.xlsx",
  sheets: [
    {
      name: "RowSheet",
      data: [
        {
          type: "row",
          startColumn: 1,
          row: 1,
          data: [
            "Label",
            { value: 42, rowspan: 1, colspan: 2, style: { format: "number" } },
            null,
          ],
        },
        {
          type: "row",
          startColumn: "auto",
          row: 2,
          data: ["Another"],
        },
      ],
    },
  ],
};

const columnConfig: WbConfig = {
  filename: "column.xlsx",
  sheets: [
    {
      name: "ColSheet",
      data: [
        {
          type: "column",
          column: 1,
          startRow: 1,
          data: ["Header", { value: 10, rowspan: 2, colspan: 1 }, "Footer"],
        },
        {
          type: "column",
          column: "auto",
          startRow: 1,
          data: ["A", "B"],
        },
      ],
    },
  ],
};

const cellConfig: WbConfig = {
  filename: "cell.xlsx",
  sheets: [
    {
      name: "CellSheet",
      data: [
        {
          type: "cell",
          cellAddress: "B2",
          value: "Hello",
          rowspan: 1,
          colspan: 2,
          style: { fontWeight: "bold" },
        },
      ],
    },
  ],
};

const titleConfig: WbConfig = {
  filename: "title.xlsx",
  sheets: [
    {
      name: "TitleSheet",
      data: [
        {
          type: "title",
          row: 1,
          startColumn: 1,
          endColumn: 5,
          value: "My Title",
          style: { fontSize: 18, fontWeight: "bold" },
        },
        {
          type: "title",
          row: 2,
          startColumn: 1,
          endColumn: "auto",
          value: "Auto Title",
        },
      ],
    },
  ],
};

const cardsConfig: WbConfig = {
  filename: "cards.xlsx",
  sheets: [
    {
      name: "CardsSheet",
      data: [
        {
          type: "cards",
          startRow: 1,
          startColumn: 1,
          cardSpacing: 1,
          cardWidth: 2,
          cards: [
            {
              title: "Card 1",
              data: [
                ["Label A", "Value A"],
                ["Label B", 42],
              ],
            },
            {
              title: "Card 2",
              data: [["Label C", null]],
            },
          ],
        },
      ],
    },
  ],
};

const styledConfig: WbConfig = {
  filename: "styled.xlsx",
  globalStyle: { fontFamily: "Arial", fontSize: 11 },
  defaultColors: {
    primary: "FF2176CC",
    secondary: "FF4A84BF",
    lightGray: "FFF2F2F2",
  },
  sheets: [
    {
      name: "StyledSheet",
      defaultStyle: { fontSize: 10 },
      data: [
        {
          type: "table",
          startRow: 1,
          startColumn: 1,
          headers: [
            { header: "Col1", key: "col1", rowspan: 2 },
            { header: "Col2", key: "col2", colspan: 2 },
          ],
          data: [
            {
              col1: { value: 1, style: { format: "currency" } },
              col2: { value: 0.5, style: { format: "percentage" } },
            },
          ],
          tableStyle: {
            headerStyle: { backgroundColor: "FF4472C4", fontColor: "FFFFFFFF" },
            rowStyle: { backgroundColor: "FFFFFFFF" },
            alternateRowStyle: { backgroundColor: "FFF2F2F2" },
            totalRowStyle: { fontWeight: "bold" },
            titleStyle: { fontSize: 14 },
            groupHeaderStyle: { fontSize: 12 },
          },
        },
      ],
    },
  ],
};

const applyCellStyleConfig: WbConfig = {
  filename: "styles.xlsx",
  sheets: [
    {
      name: "Styles",
      data: [
        {
          type: "row",
          startColumn: 1,
          row: 1,
          data: [
            {
              value: "styled",
              style: {
                backgroundColor: "FFFF0000",
                fontColor: "FF000000",
                fontSize: 14,
                fontWeight: "bold",
                fontFamily: "Calibri",
                alignment: { horizontal: "center", vertical: "middle" },
                border: {
                  top: { style: "thin", color: "FF000000" },
                  left: { style: "medium" },
                  bottom: { style: "thick", color: "FFFF0000" },
                  right: { style: "thin" },
                },
                format: "date",
              },
            },
            { value: 99, style: { format: "currency" } },
            { value: 0.42, style: { format: "percentage" } },
            { value: "custom", style: { format: "custom", customFormat: "@" } },
          ],
        },
      ],
    },
  ],
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ExcelExporter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("constructor + getThemeColor", () => {
    it("initializes with default colors when none provided", () => {
      const exporter = new ExcelExporter(minimalConfig);
      expect(exporter.getThemeColor("primary")).toBe("FF4472C4");
      expect(exporter.getThemeColor("secondary")).toBe("FF2E75B6");
      expect(exporter.getThemeColor("lightGray")).toBe("FFF2F2F2");
    });

    it("uses provided defaultColors", () => {
      const exporter = new ExcelExporter({
        ...minimalConfig,
        defaultColors: { primary: "FF112233", secondary: "FF445566" },
      });
      expect(exporter.getThemeColor("primary")).toBe("FF112233");
      expect(exporter.getThemeColor("secondary")).toBe("FF445566");
    });

    it("derives colors from theme when defaultColors absent", () => {
      const exporter = new ExcelExporter({
        ...minimalConfig,
        theme: {
          colors: {
            primary: "--color-mw-primary-500",
            secondary: "--color-mw-primary-400",
          },
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any,
      });
      expect(exporter.getThemeColor("primary")).toBeDefined();
      expect(exporter.getThemeColor("lightGray")).toBe("FFF2F2F2");
    });
  });

  describe("export() – creates download link and revokes URL", () => {
    it("calls createObjectURL and revokeObjectURL", async () => {
      const exporter = new ExcelExporter(minimalConfig);
      await exporter.export();
      expect(createObjectURLMock).toHaveBeenCalledTimes(1);
      expect(revokeObjectURLMock).toHaveBeenCalledWith("blob:mock-url");
      expect(clickMock).toHaveBeenCalledTimes(1);
    });

    it("uses sampleWorkbookConfig without errors", async () => {
      const exporter = new ExcelExporter(sampleWorkbookConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with table data type", () => {
    it("exports table config without errors", async () => {
      const exporter = new ExcelExporter(tableConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });

    it("exports table with title, groupHeaders, and totalRow", async () => {
      const exporter = new ExcelExporter(tableWithExtrasConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });

    it("exports table with nested/multi-level headers", async () => {
      const exporter = new ExcelExporter(styledConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with row data type", () => {
    it("exports row config without errors", async () => {
      const exporter = new ExcelExporter(rowConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with column data type", () => {
    it("exports column config without errors", async () => {
      const exporter = new ExcelExporter(columnConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with cell data type", () => {
    it("exports specific cell config without errors", async () => {
      const exporter = new ExcelExporter(cellConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with title data type", () => {
    it("exports title config without errors (numeric and auto endColumn)", async () => {
      const exporter = new ExcelExporter(titleConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("createSheet with cards data type", () => {
    it("exports card layout without errors", async () => {
      const exporter = new ExcelExporter(cardsConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("applyCellStyle coverage", () => {
    it("exports config with many border/format styles", async () => {
      const exporter = new ExcelExporter(applyCellStyleConfig);
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });

  describe("globalStyle merging", () => {
    it("applies globalStyle to all sheets", async () => {
      const exporter = new ExcelExporter({
        filename: "global.xlsx",
        globalStyle: { fontFamily: "Helvetica", fontSize: 12 },
        sheets: [
          {
            name: "S1",
            data: [{ type: "row", row: 1, startColumn: 1, data: ["x"] }],
          },
        ],
      });
      await expect(exporter.export()).resolves.toBeUndefined();
    });
  });
});
