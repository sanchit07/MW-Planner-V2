import * as Excel from "exceljs";

import { initializeThemeColors } from "./themeColors";
import type { PresentationTheme } from "../pages/campaigns/media-plan/types";

// ==================== INTERFACES ====================

type CellFormat =
  | "text"
  | "number"
  | "currency"
  | "date"
  | "percentage"
  | "custom";

interface CellStyle {
  backgroundColor?: string; // ARGB: "FFFF0000"
  fontColor?: string; // ARGB: "FF000000"
  fontSize?: number;
  fontWeight?: "normal" | "bold";
  fontFamily?: string;
  alignment?: {
    horizontal?: "left" | "center" | "right" | "fill" | "justify";
    vertical?: "top" | "middle" | "bottom";
  };
  wrapText?: boolean;
  border?: {
    top?: { style: "thin" | "medium" | "thick"; color?: string };
    left?: { style: "thin" | "medium" | "thick"; color?: string };
    bottom?: { style: "thin" | "medium" | "thick"; color?: string };
    right?: { style: "thin" | "medium" | "thick"; color?: string };
  };
  format?: CellFormat;
  customFormat?: string;
}

interface CellData {
  value: string | number | Date | boolean | null;
  rowspan?: number;
  colspan?: number;
  style?: CellStyle;
}

interface TableColumnHeader {
  header: string;
  key: string;
  width?: number;
  rowspan?: number;
  colspan?: number;
  style?: CellStyle;
}

interface GroupHeader {
  text: string;
  colspan: number;
  align?: "left" | "center" | "right";
}

interface TableConfig {
  type: "table";
  startColumn?: number | "auto"; // 'auto' = calculate from previous data
  startRow: number;
  leaveColumnsAfter?: number;
  headers: TableColumnHeader[];
  data: Record<string, CellData | string | number | Date | boolean | null>[];
  groupHeaders?: GroupHeader[]; // Multi-level headers
  title?: string; // Optional table title
  showTotalRow?: boolean; // Show total row at the bottom
  totalRowConfig?: {
    // Legacy single label support (for backward compatibility)
    label?: string; // Label for total row (default: "Total")
    columns?: number[]; // Column indices to sum (0-based)
    labelColumn?: number; // Column index for label (default: 0)
    labelColspan?: number; // Colspan for label cell

    // New multiple label/value support
    items?: Array<{
      label: string; // Label text
      labelColumn: number; // Column index where label starts (0-based)
      labelColspan: number; // Number of columns to merge for label
      valueColumn: number; // Column index where calculated value should be placed (0-based)
      valueKey?: string; // Optional: key to calculate from (defaults to header key at valueColumn)
      calculateFrom?: number[]; // Optional: specific column indices to sum (defaults to valueColumn)
    }>;
  };
  tableStyle?: {
    headerStyle?: CellStyle;
    dataStyle?: CellStyle;
    alternateRowStyle?: CellStyle;
    titleStyle?: CellStyle;
    groupHeaderStyle?: CellStyle;
    totalRowStyle?: CellStyle;
  };
}

interface SpecificCellConfig {
  type: "cell";
  cellAddress: string; // "A1", "B5"
  value: string | number | Date | boolean | null;
  rowspan?: number;
  colspan?: number;
  style?: CellStyle;
}

interface RowDataConfig {
  type: "row";
  startColumn: number | "auto"; // 'auto' = next available column
  row: number;
  data: (CellData | string | number | Date | boolean | null)[];
  style?: CellStyle;
}

interface CardLayoutConfig {
  type: "cards";
  startRow: number;
  startColumn?: number | "auto";
  cards: Array<{
    title: string;
    data: Array<[string, string | number | Date | boolean | null]>; // [label, value] pairs
    titleStyle?: CellStyle;
    labelStyle?: CellStyle;
    valueStyle?: CellStyle;
  }>;
  cardSpacing?: number; // Columns between cards (default: 1)
  cardWidth?: number; // Width of each card in columns (default: 2)
}

interface ColumnDataConfig {
  type: "column";
  column: number | "auto"; // 'auto' = next available column
  startRow: number;
  data: (CellData | string | number | Date | boolean | null)[];
  style?: CellStyle;
}

interface TitleConfig {
  type: "title";
  row: number;
  startColumn: number | "auto";
  endColumn: number | "auto"; // Can be relative: 'auto' means span to last used column
  value: string;
  style?: CellStyle;
}

type SheetDataConfig =
  | TableConfig
  | SpecificCellConfig
  | RowDataConfig
  | ColumnDataConfig
  | TitleConfig
  | CardLayoutConfig
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  | any;

interface SheetConfig {
  name: string;
  data: SheetDataConfig[];
  defaultStyle?: CellStyle;
  pageSetup?: {
    paperSize?: number;
    orientation?: "portrait" | "landscape";
    fitToPage?: boolean;
    fitToWidth?: number;
    fitToHeight?: number;
  };
}

export interface WorkbookConfig {
  filename: string;
  sheets: SheetConfig[];
  globalStyle?: CellStyle;
  theme?: PresentationTheme; // Theme for dynamic color binding
  defaultColors?: {
    primary?: string; // ARGB format
    secondary?: string; // ARGB format
    lightGray?: string; // ARGB format
  };
}

// ==================== COLUMN TRACKER ====================

class ColumnTracker {
  // Map: row -> array of occupied column ranges [start, end]
  private occupiedCells: Map<number, Array<[number, number]>> = new Map();

  /**
   * Mark cells as occupied
   */
  markOccupied(
    startRow: number,
    startCol: number,
    rowspan: number,
    colspan: number,
  ): void {
    for (let row = startRow; row < startRow + rowspan; row++) {
      if (!this.occupiedCells.has(row)) {
        this.occupiedCells.set(row, []);
      }
      this.occupiedCells.get(row)!.push([startCol, startCol + colspan - 1]);
    }
  }

  /**
   * Get next available column for a given row
   */
  getNextAvailableColumn(row: number): number {
    if (!this.occupiedCells.has(row)) {
      return 1; // Start from column A
    }

    const ranges = this.occupiedCells.get(row)!;
    if (ranges.length === 0) {
      return 1;
    }

    // Sort ranges by start column
    ranges.sort((a, b) => a[0] - b[0]);

    // Find first gap or return after last range
    let nextCol = 1;
    for (const [start, end] of ranges) {
      if (nextCol < start) {
        return nextCol;
      }
      nextCol = Math.max(nextCol, end + 1);
    }

    return nextCol;
  }

  /**
   * Get the rightmost occupied column in a given row
   */
  getRightmostColumn(row: number): number {
    if (!this.occupiedCells.has(row)) {
      return 0;
    }

    const ranges = this.occupiedCells.get(row)!;
    let rightmost = 0;
    for (const [, end] of ranges) {
      rightmost = Math.max(rightmost, end);
    }
    return rightmost;
  }

  /**
   * Get the rightmost column across all rows
   */
  getGlobalRightmostColumn(): number {
    let rightmost = 0;
    for (const ranges of this.occupiedCells.values()) {
      for (const [, end] of ranges) {
        rightmost = Math.max(rightmost, end);
      }
    }
    return rightmost;
  }
}

// ==================== EXCEL EXPORTER CLASS ====================

export class ExcelExporter {
  private workbook: Excel.Workbook;
  private config: WorkbookConfig;
  private themeColors: {
    primary: string;
    secondary: string;
    lightGray: string;
  };

  constructor(config: WorkbookConfig) {
    this.config = config;
    this.workbook = new Excel.Workbook();
    this.workbook.creator = "Moving Walls Planner";
    this.workbook.created = new Date();
    this.workbook.modified = new Date();

    // Initialize theme colors
    this.themeColors = initializeThemeColors(
      config.defaultColors,
      config.theme,
    );
  }

  /**
   * Get theme color by name
   */
  public getThemeColor(color: "primary" | "secondary" | "lightGray"): string {
    return this.themeColors[color];
  }

  /**
   * Main export method
   */
  async export(): Promise<void> {
    // Process each sheet
    for (const sheetConfig of this.config.sheets) {
      this.createSheet(sheetConfig);
    }

    // Generate and download
    const buffer = await this.workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = this.config.filename;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  /**
   * Create a sheet with all its data configurations
   */
  private createSheet(sheetConfig: SheetConfig): void {
    const worksheet = this.workbook.addWorksheet(sheetConfig.name, {
      pageSetup: sheetConfig.pageSetup,
    });

    // Track column usage for automatic placement
    const columnTracker = new ColumnTracker();

    // Process each data configuration in order
    for (const dataConfig of sheetConfig.data) {
      switch (dataConfig.type) {
        case "table":
          this.renderTable(worksheet, dataConfig, sheetConfig, columnTracker);
          break;
        case "cell":
          this.renderSpecificCell(
            worksheet,
            dataConfig,
            sheetConfig,
            columnTracker,
          );
          break;
        case "row":
          this.renderRow(worksheet, dataConfig, sheetConfig, columnTracker);
          break;
        case "column":
          this.renderColumn(worksheet, dataConfig, sheetConfig, columnTracker);
          break;
        case "title":
          this.renderTitle(worksheet, dataConfig, sheetConfig, columnTracker);
          break;
        case "cards":
          this.renderCardLayout(
            worksheet,
            dataConfig,
            sheetConfig,
            columnTracker,
          );
          break;
      }
    }

    // Auto-size columns
    this.autoSizeColumns(worksheet);
  }

  /**
   * Render a table with headers and data
   */
  private renderTable(
    worksheet: Excel.Worksheet,
    config: TableConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const startRow = config.startRow;
    const startCol =
      config.startColumn === "auto"
        ? tracker.getNextAvailableColumn(startRow)
        : config.startColumn || 1;

    let currentRow = startRow;

    // Render title if provided
    if (config.title) {
      const flatHeaders = this.flattenHeaders(config.headers);
      const titleColspan = flatHeaders.length;
      worksheet.mergeCells(
        currentRow,
        startCol,
        currentRow,
        startCol + titleColspan - 1,
      );
      const titleCell = worksheet.getCell(currentRow, startCol);
      titleCell.value = config.title;

      const titleStyle = this.mergeStyles(
        this.config.globalStyle,
        sheetConfig.defaultStyle,
        config.tableStyle?.titleStyle,
        {
          backgroundColor: this.themeColors.secondary,
          fontColor: "FFFFFFFF",
          fontWeight: "bold",
          fontSize: 12,
          alignment: { horizontal: "left", vertical: "middle" },
        },
      );
      this.applyCellStyle(titleCell, titleStyle);
      tracker.markOccupied(currentRow, startCol, 1, titleColspan);
      currentRow++;
    }

    // Render group headers if provided
    if (config.groupHeaders && config.groupHeaders.length > 0) {
      let colOffset = 0;
      for (const groupHeader of config.groupHeaders) {
        const cellCol = startCol + colOffset;
        const endCol = startCol + colOffset + groupHeader.colspan - 1;

        if (groupHeader.colspan > 1) {
          worksheet.mergeCells(currentRow, cellCol, currentRow, endCol);
        }

        const groupCell = worksheet.getCell(currentRow, cellCol);
        groupCell.value = groupHeader.text;

        const groupStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          config.tableStyle?.groupHeaderStyle,
          config.tableStyle?.headerStyle,
          {
            backgroundColor: this.themeColors.primary,
            fontColor: "FFFFFFFF",
            fontWeight: "bold",
            alignment: {
              horizontal: groupHeader.align || "center",
              vertical: "middle",
            },
          },
        );
        this.applyCellStyle(groupCell, groupStyle);
        tracker.markOccupied(currentRow, cellCol, 1, groupHeader.colspan);
        colOffset += groupHeader.colspan;
      }
      currentRow++;
    }

    // Calculate header structure (for multi-level headers with rowspan/colspan)
    const headerLevels = this.calculateHeaderLevels(config.headers);
    const flatHeaders = this.flattenHeaders(config.headers);

    // Render headers
    let headerRowIndex = 0;
    for (const levelHeaders of headerLevels) {
      let colOffset = 0;
      for (const header of levelHeaders) {
        const cellCol = startCol + colOffset;
        const cellRow = currentRow + headerRowIndex;
        const cell = worksheet.getCell(cellRow, cellCol);

        cell.value = header.header;

        // Apply header styles
        const mergedStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          config.tableStyle?.headerStyle,
          header.style,
          {
            backgroundColor: this.themeColors.primary,
            fontColor: "FFFFFFFF",
            fontWeight: "bold",
            fontSize: 12,
          },
        );
        this.applyCellStyle(cell, mergedStyle);

        // Handle merges
        const rowspan = header.rowspan || 1;
        const colspan = header.colspan || 1;
        if (rowspan > 1 || colspan > 1) {
          worksheet.mergeCells(
            cellRow,
            cellCol,
            cellRow + rowspan - 1,
            cellCol + colspan - 1,
          );
        }

        // Track occupied cells
        tracker.markOccupied(cellRow, cellCol, rowspan, colspan);

        // Set column width
        if (header.width) {
          worksheet.getColumn(cellCol).width = header.width;
        }

        colOffset += colspan;
      }
      headerRowIndex++;
    }

    currentRow += headerLevels.length;

    // Render data rows
    config.data.forEach((rowData, rowIndex) => {
      let colOffset = 0;
      for (const header of flatHeaders) {
        const cellCol = startCol + colOffset;
        const cellRow = currentRow;
        const cell = worksheet.getCell(cellRow, cellCol);

        const cellValue = rowData[header.key];
        let value: string | number | Date | boolean | null;
        let cellStyle: CellStyle | undefined;
        let rowspan = 1;
        let colspan = 1;

        // Extract value and style
        if (this.isCellData(cellValue)) {
          value = cellValue.value;
          cellStyle = cellValue.style;
          rowspan = cellValue.rowspan || 1;
          colspan = cellValue.colspan || 1;
        } else {
          value = cellValue;
        }

        cell.value = value;

        // Apply data styles (with alternate row support)
        const alternateStyle =
          rowIndex % 2 === 1 ? config.tableStyle?.alternateRowStyle : undefined;

        const mergedStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          config.tableStyle?.dataStyle,
          alternateStyle,
          cellStyle,
        );
        this.applyCellStyle(cell, mergedStyle);

        // Handle merges
        if (rowspan > 1 || colspan > 1) {
          worksheet.mergeCells(
            cellRow,
            cellCol,
            cellRow + rowspan - 1,
            cellCol + colspan - 1,
          );
        }

        // Track occupied cells
        tracker.markOccupied(cellRow, cellCol, rowspan, colspan);

        colOffset++;
      }
      currentRow++;
    });

    // Render total row if requested
    if (config.showTotalRow && config.data.length > 0) {
      const totalConfig = config.totalRowConfig || {};

      // Check if using new multiple items format
      if (totalConfig.items && totalConfig.items.length > 0) {
        // Render multiple label/value pairs
        totalConfig.items.forEach((item) => {
          // Only render label if labelColspan > 0 and label is not empty
          if (item.labelColspan > 0 && item.label) {
            // Merge label cells if colspan > 1
            if (item.labelColspan > 1) {
              worksheet.mergeCells(
                currentRow,
                startCol + item.labelColumn,
                currentRow,
                startCol + item.labelColumn + item.labelColspan - 1,
              );
            }

            // Set label
            const labelCell = worksheet.getCell(
              currentRow,
              startCol + item.labelColumn,
            );
            labelCell.value = item.label;

            const labelStyle = this.mergeStyles(
              this.config.globalStyle,
              sheetConfig.defaultStyle,
              config.tableStyle?.dataStyle,
              config.tableStyle?.totalRowStyle,
              {
                backgroundColor: this.themeColors.lightGray,
                fontWeight: "bold",
                alignment: { horizontal: "left", vertical: "middle" },
              },
            );
            this.applyCellStyle(labelCell, labelStyle);
            tracker.markOccupied(
              currentRow,
              startCol + item.labelColumn,
              1,
              item.labelColspan,
            );
          }

          // Calculate and set value
          const valueHeader = flatHeaders[item.valueColumn];
          if (valueHeader) {
            const valueKey = item.valueKey || valueHeader.key;

            const sum = config.data.reduce((acc, rowData) => {
              // If calculateFrom is specified, sum those columns
              if (item.calculateFrom && item.calculateFrom.length > 0) {
                return item.calculateFrom.reduce((sumAcc, colIndex) => {
                  const header = flatHeaders[colIndex];
                  if (!header) return sumAcc;
                  const cellValue = rowData[header.key];
                  const value = this.isCellData(cellValue)
                    ? cellValue.value
                    : cellValue;
                  const numValue =
                    typeof value === "number"
                      ? value
                      : parseFloat(String(value)) || 0;
                  return sumAcc + numValue;
                }, acc);
              } else {
                // Default: sum from the valueKey
                const cellValue = rowData[valueKey];
                const value = this.isCellData(cellValue)
                  ? cellValue.value
                  : cellValue;
                const numValue =
                  typeof value === "number"
                    ? value
                    : parseFloat(String(value)) || 0;
                return acc + numValue;
              }
            }, 0);

            const valueCell = worksheet.getCell(
              currentRow,
              startCol + item.valueColumn,
            );
            valueCell.value = sum;

            const valueStyle = this.mergeStyles(
              this.config.globalStyle,
              sheetConfig.defaultStyle,
              config.tableStyle?.dataStyle,
              config.tableStyle?.totalRowStyle,
              {
                backgroundColor: this.themeColors.lightGray,
                fontWeight: "bold",
                alignment: { horizontal: "right", vertical: "middle" },
              },
            );
            this.applyCellStyle(valueCell, valueStyle);
            tracker.markOccupied(currentRow, startCol + item.valueColumn, 1, 1);
          }
        });
      } else {
        // Legacy single label support (backward compatibility)
        const labelColumn = totalConfig.labelColumn ?? 0;
        const labelColspan =
          totalConfig.labelColspan ??
          flatHeaders.length - (totalConfig.columns?.length || 0);
        const label = totalConfig.label || "Total";

        // Merge label cells
        if (labelColspan > 1) {
          worksheet.mergeCells(
            currentRow,
            startCol + labelColumn,
            currentRow,
            startCol + labelColumn + labelColspan - 1,
          );
        }

        const labelCell = worksheet.getCell(currentRow, startCol + labelColumn);
        labelCell.value = label;

        const totalStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          config.tableStyle?.dataStyle,
          config.tableStyle?.totalRowStyle,
          {
            backgroundColor: this.themeColors.lightGray,
            fontWeight: "bold",
            alignment: { horizontal: "right", vertical: "middle" },
          },
        );
        this.applyCellStyle(labelCell, totalStyle);
        tracker.markOccupied(
          currentRow,
          startCol + labelColumn,
          1,
          labelColspan,
        );

        // Calculate and set totals for specified columns
        if (totalConfig.columns) {
          totalConfig.columns.forEach((colIndex) => {
            const header = flatHeaders[colIndex];
            if (!header) return;

            const sum = config.data.reduce((acc, rowData) => {
              const cellValue = rowData[header.key];
              const value = this.isCellData(cellValue)
                ? cellValue.value
                : cellValue;
              const numValue =
                typeof value === "number"
                  ? value
                  : parseFloat(String(value)) || 0;
              return acc + numValue;
            }, 0);

            const totalCell = worksheet.getCell(
              currentRow,
              startCol + colIndex,
            );
            totalCell.value = sum;

            const totalCellStyle = this.mergeStyles(
              this.config.globalStyle,
              sheetConfig.defaultStyle,
              config.tableStyle?.dataStyle,
              config.tableStyle?.totalRowStyle,
              {
                backgroundColor: this.themeColors.lightGray,
                fontWeight: "bold",
                alignment: { horizontal: "right", vertical: "middle" },
              },
            );
            this.applyCellStyle(totalCell, totalCellStyle);
            tracker.markOccupied(currentRow, startCol + colIndex, 1, 1);
          });
        }
      }

      currentRow++;
    }

    // Leave blank columns after table if specified
    if (config.leaveColumnsAfter) {
      const tableEndCol = startCol + flatHeaders.length - 1;
      for (let row = startRow; row < currentRow; row++) {
        tracker.markOccupied(row, tableEndCol + 1, 1, config.leaveColumnsAfter);
      }
    }
  }

  /**
   * Render a specific cell at given address
   */
  private renderSpecificCell(
    worksheet: Excel.Worksheet,
    config: SpecificCellConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const { row, col } = this.cellAddressToRowCol(config.cellAddress);
    const cell = worksheet.getCell(row, col);

    cell.value = config.value;

    const mergedStyle = this.mergeStyles(
      this.config.globalStyle,
      sheetConfig.defaultStyle,
      config.style,
    );
    this.applyCellStyle(cell, mergedStyle);

    const rowspan = config.rowspan || 1;
    const colspan = config.colspan || 1;

    if (rowspan > 1 || colspan > 1) {
      worksheet.mergeCells(row, col, row + rowspan - 1, col + colspan - 1);
    }

    tracker.markOccupied(row, col, rowspan, colspan);
  }

  /**
   * Render a row of data
   */
  private renderRow(
    worksheet: Excel.Worksheet,
    config: RowDataConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const row = config.row;
    const startCol =
      config.startColumn === "auto"
        ? tracker.getNextAvailableColumn(row)
        : config.startColumn;

    let colOffset = 0;
    for (const cellValue of config.data) {
      const cellCol = startCol + colOffset;
      const cell = worksheet.getCell(row, cellCol);

      let value: string | number | Date | boolean | null;
      let cellStyle: CellStyle | undefined;
      let rowspan = 1;
      let colspan = 1;

      if (this.isCellData(cellValue)) {
        value = cellValue.value;
        cellStyle = cellValue.style;
        rowspan = cellValue.rowspan || 1;
        colspan = cellValue.colspan || 1;
      } else {
        value = cellValue;
      }

      cell.value = value;

      const mergedStyle = this.mergeStyles(
        this.config.globalStyle,
        sheetConfig.defaultStyle,
        config.style,
        cellStyle,
      );
      this.applyCellStyle(cell, mergedStyle);

      if (rowspan > 1 || colspan > 1) {
        worksheet.mergeCells(
          row,
          cellCol,
          row + rowspan - 1,
          cellCol + colspan - 1,
        );
      }

      tracker.markOccupied(row, cellCol, rowspan, colspan);
      colOffset += colspan;
    }
  }

  /**
   * Render a column of data
   */
  private renderColumn(
    worksheet: Excel.Worksheet,
    config: ColumnDataConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const startRow = config.startRow;
    const col =
      config.column === "auto"
        ? tracker.getNextAvailableColumn(startRow)
        : config.column;

    let rowOffset = 0;
    for (const cellValue of config.data) {
      const cellRow = startRow + rowOffset;
      const cell = worksheet.getCell(cellRow, col);

      let value: string | number | Date | boolean | null;
      let cellStyle: CellStyle | undefined;
      let rowspan = 1;
      let colspan = 1;

      if (this.isCellData(cellValue)) {
        value = cellValue.value;
        cellStyle = cellValue.style;
        rowspan = cellValue.rowspan || 1;
        colspan = cellValue.colspan || 1;
      } else {
        value = cellValue;
      }

      cell.value = value;

      const mergedStyle = this.mergeStyles(
        this.config.globalStyle,
        sheetConfig.defaultStyle,
        config.style,
        cellStyle,
      );
      this.applyCellStyle(cell, mergedStyle);

      if (rowspan > 1 || colspan > 1) {
        worksheet.mergeCells(
          cellRow,
          col,
          cellRow + rowspan - 1,
          col + colspan - 1,
        );
      }

      tracker.markOccupied(cellRow, col, rowspan, colspan);
      rowOffset += rowspan;
    }
  }

  /**
   * Render a title (merged cell spanning columns)
   */
  private renderTitle(
    worksheet: Excel.Worksheet,
    config: TitleConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const row = config.row;
    const startCol =
      config.startColumn === "auto"
        ? 1 // Titles default to column A
        : config.startColumn;

    const endCol =
      config.endColumn === "auto"
        ? tracker.getGlobalRightmostColumn() || 1
        : config.endColumn;

    const cell = worksheet.getCell(row, startCol);
    cell.value = config.value;

    const mergedStyle = this.mergeStyles(
      this.config.globalStyle,
      sheetConfig.defaultStyle,
      config.style,
    );
    this.applyCellStyle(cell, mergedStyle);

    if (endCol > startCol) {
      worksheet.mergeCells(row, startCol, row, endCol);
    }

    tracker.markOccupied(row, startCol, 1, endCol - startCol + 1);
  }

  /**
   * Render a card layout (multiple cards side by side)
   */
  private renderCardLayout(
    worksheet: Excel.Worksheet,
    config: CardLayoutConfig,
    sheetConfig: SheetConfig,
    tracker: ColumnTracker,
  ): void {
    const startRow = config.startRow;
    const startCol =
      config.startColumn === "auto"
        ? tracker.getNextAvailableColumn(startRow)
        : config.startColumn || 1;
    const cardSpacing = config.cardSpacing ?? 1;
    const cardWidth = config.cardWidth ?? 2;

    let currentCol = startCol;
    let maxRows = 0;

    // Find max rows needed
    config.cards.forEach((card) => {
      maxRows = Math.max(maxRows, card.data.length);
    });

    // Render each card
    config.cards.forEach((card) => {
      const cardStartCol = currentCol;

      // Card title
      worksheet.mergeCells(
        startRow,
        cardStartCol,
        startRow,
        cardStartCol + cardWidth - 1,
      );
      const titleCell = worksheet.getCell(startRow, cardStartCol);
      titleCell.value = card.title;

      const titleStyle = this.mergeStyles(
        this.config.globalStyle,
        sheetConfig.defaultStyle,
        card.titleStyle,
        {
          backgroundColor: this.themeColors.secondary,
          fontColor: "FFFFFFFF",
          fontWeight: "bold",
          fontSize: 12,
          alignment: { horizontal: "left", vertical: "middle" },
        },
      );
      this.applyCellStyle(titleCell, titleStyle);
      tracker.markOccupied(startRow, cardStartCol, 1, cardWidth);

      // Card data rows
      card.data.forEach(([label, value], rowIndex) => {
        const dataRow = startRow + 1 + rowIndex;

        // Label cell
        const labelCell = worksheet.getCell(dataRow, cardStartCol);
        labelCell.value = label;
        const labelStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          card.labelStyle,
          {
            fontWeight: "bold",
            backgroundColor: this.themeColors.lightGray,
          },
        );
        this.applyCellStyle(labelCell, labelStyle);
        tracker.markOccupied(dataRow, cardStartCol, 1, 1);

        // Value cell — spans the rest of the card's width so long text
        // (e.g. a 100+ item venue-type list) wraps within the card instead
        // of overflowing into the next card's cells.
        const valueColspan = Math.max(cardWidth - 1, 1);
        if (valueColspan > 1) {
          worksheet.mergeCells(
            dataRow,
            cardStartCol + 1,
            dataRow,
            cardStartCol + valueColspan,
          );
        }
        const valueCell = worksheet.getCell(dataRow, cardStartCol + 1);
        valueCell.value = value ?? "";
        const valueStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          card.valueStyle,
        );
        this.applyCellStyle(valueCell, valueStyle);
        tracker.markOccupied(dataRow, cardStartCol + 1, 1, valueColspan);
      });

      // Fill empty rows for alignment if needed
      for (let i = card.data.length; i < maxRows; i++) {
        const dataRow = startRow + 1 + i;
        const labelCell = worksheet.getCell(dataRow, cardStartCol);
        const valueCell = worksheet.getCell(dataRow, cardStartCol + 1);
        const emptyStyle = this.mergeStyles(
          this.config.globalStyle,
          sheetConfig.defaultStyle,
          card.labelStyle,
        );
        this.applyCellStyle(labelCell, emptyStyle);
        this.applyCellStyle(valueCell, emptyStyle);
        tracker.markOccupied(dataRow, cardStartCol, 1, cardWidth);
      }

      // Move to next card position
      currentCol += cardWidth + cardSpacing;
    });
  }

  // ==================== HELPER FUNCTIONS ====================

  /**
   * Apply cell style
   */
  private applyCellStyle(cell: Excel.Cell, style?: CellStyle): void {
    if (!style) return;

    // Font
    cell.font = {
      ...(style.fontSize && { size: style.fontSize }),
      ...(style.fontWeight && { bold: style.fontWeight === "bold" }),
      ...(style.fontFamily && { name: style.fontFamily }),
      ...(style.fontColor && { color: { argb: style.fontColor } }),
    };

    // Alignment
    if (style.alignment || style.wrapText) {
      cell.alignment = {
        horizontal: style.alignment?.horizontal,
        vertical: style.alignment?.vertical,
        wrapText: style.wrapText,
      };
    }

    // Background
    if (style.backgroundColor) {
      cell.fill = {
        type: "pattern",
        pattern: "solid",
        fgColor: { argb: style.backgroundColor },
      };
    }

    // Border
    if (style.border) {
      cell.border = {
        ...(style.border.top && {
          top: {
            style: style.border.top.style,
            color: { argb: style.border.top.color || "FF000000" },
          },
        }),
        ...(style.border.left && {
          left: {
            style: style.border.left.style,
            color: { argb: style.border.left.color || "FF000000" },
          },
        }),
        ...(style.border.bottom && {
          bottom: {
            style: style.border.bottom.style,
            color: { argb: style.border.bottom.color || "FF000000" },
          },
        }),
        ...(style.border.right && {
          right: {
            style: style.border.right.style,
            color: { argb: style.border.right.color || "FF000000" },
          },
        }),
      };
    }

    // Number format
    if (style.format) {
      switch (style.format) {
        case "currency":
          cell.numFmt = "₹#,##0.00";
          break;
        case "percentage":
          cell.numFmt = "0.00%";
          break;
        case "number":
          cell.numFmt = "#,##0";
          break;
        case "date":
          cell.numFmt = "dd/mm/yyyy";
          break;
        case "custom":
          if (style.customFormat) {
            cell.numFmt = style.customFormat;
          }
          break;
      }
    }
  }

  /**
   * Merge multiple styles (later styles override earlier ones)
   */
  private mergeStyles(
    ...styles: (CellStyle | undefined)[]
  ): CellStyle | undefined {
    const merged: CellStyle = {};

    for (const style of styles) {
      if (!style) continue;

      Object.assign(merged, style);

      // Deep merge alignment and border
      if (style.alignment) {
        merged.alignment = { ...merged.alignment, ...style.alignment };
      }
      if (style.border) {
        merged.border = { ...merged.border, ...style.border };
      }
    }

    return Object.keys(merged).length > 0 ? merged : undefined;
  }

  /**
   * Auto-size columns based on content
   */
  private autoSizeColumns(worksheet: Excel.Worksheet): void {
    worksheet.columns.forEach((column) => {
      if (column.width) return; // Skip if width already set

      let maxLength = 10;
      column.eachCell?.({ includeEmpty: true }, (cell) => {
        const cellValue = cell.value?.toString() || "";
        maxLength = Math.max(maxLength, cellValue.length);
      });
      column.width = Math.min(maxLength + 2, 50); // Max 50 characters
    });
  }

  /**
   * Convert cell address like "A1" to row/col numbers
   */
  private cellAddressToRowCol(address: string): { row: number; col: number } {
    const match = address.match(/^([A-Z]+)(\d+)$/);
    if (!match) throw new Error(`Invalid cell address: ${address}`);

    const colStr = match[1];
    const rowStr = match[2];

    let col = 0;
    for (let i = 0; i < colStr.length; i++) {
      col = col * 26 + (colStr.charCodeAt(i) - 64);
    }

    return { row: parseInt(rowStr), col };
  }

  /**
   * Check if value is CellData object
   */
  private isCellData(value: unknown): value is CellData {
    return value !== null && typeof value === "object" && "value" in value;
  }

  /**
   * Calculate header levels for multi-row headers
   */
  private calculateHeaderLevels(
    headers: TableColumnHeader[],
  ): TableColumnHeader[][] {
    // Simple implementation: assumes single level headers
    // Extend this for multi-level header support
    return [headers];
  }

  /**
   * Flatten headers for data mapping
   */
  private flattenHeaders(headers: TableColumnHeader[]): TableColumnHeader[] {
    return headers.filter((h) => !h.colspan || h.colspan <= 1);
  }
}

// ==================== SAMPLE DATA ====================

export const sampleWorkbookConfig: WorkbookConfig = {
  filename: "Sales_Report_2024.xlsx",
  globalStyle: {
    fontFamily: "Arial",
    fontSize: 11,
  },
  sheets: [
    {
      name: "Sales Summary",
      data: [
        {
          type: "title",
          row: 1,
          startColumn: 1,
          endColumn: "auto",
          value: "Q4 2024 Sales Report",
          style: {
            fontSize: 18,
            fontWeight: "bold",
            backgroundColor: "FF2176CC",
            fontColor: "FFFFFFFF",
            alignment: { horizontal: "center", vertical: "middle" },
          },
        },
        {
          type: "table",
          startRow: 3,
          startColumn: "auto",
          headers: [
            { header: "Region", key: "region", width: 20 },
            { header: "Revenue", key: "revenue", width: 15 },
            { header: "Growth %", key: "growth", width: 15 },
          ],
          data: [
            {
              region: "North America",
              revenue: { value: 410000, style: { format: "currency" } },
              growth: { value: 0.15, style: { format: "percentage" } },
            },
            {
              region: "Europe",
              revenue: { value: 315000, style: { format: "currency" } },
              growth: { value: 0.12, style: { format: "percentage" } },
            },
          ],
          tableStyle: {
            headerStyle: {
              fontWeight: "bold",
              backgroundColor: "FF4472C4",
              fontColor: "FFFFFFFF",
            },
          },
        },
      ],
    },
  ],
};
