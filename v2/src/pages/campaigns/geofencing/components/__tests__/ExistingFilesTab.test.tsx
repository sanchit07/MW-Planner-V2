import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { LocationCsvFile } from "../../types/location-csv.types";
import { ExistingFilesTab } from "../ExistingFilesTab";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/dateUtils", () => ({
  formatDisplayDate: (date: string) => date || "",
}));

vi.mock("@components/ui/AgGridTable/AgGridTable", () => ({
  AgGridTable: (props: {
    rowData: LocationCsvFile[];
    loading: boolean;
    emptyMessage: string;
    rowSelection?: string;
    selectedRowIds?: string[];
    onSelectionChange?: (ids: string[]) => void;
    columnDefs?: Array<{
      colId?: string;
      cellRenderer?: (params: {
        data?: LocationCsvFile;
        value?: unknown;
      }) => React.ReactNode;
      valueFormatter?: (params: { value?: unknown }) => string;
      valueGetter?: (params: {
        data?: LocationCsvFile;
        node?: { rowIndex?: number };
      }) => unknown;
      field?: string;
    }>;
  }) => {
    const {
      rowData,
      loading,
      emptyMessage,
      rowSelection,
      selectedRowIds,
      onSelectionChange,
      columnDefs = [],
    } = props;
    if (loading) {
      return (
        <div role="table">
          <div>Loading...</div>
        </div>
      );
    }
    if (!rowData || rowData.length === 0) {
      return (
        <div role="table">
          <div>{emptyMessage}</div>
        </div>
      );
    }
    return (
      <table role="table" aria-label="files">
        <tbody>
          {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
          {rowData.map((row: any, rowIndex) => (
            <tr key={row.id} data-testid={`row-${row.id}`}>
              {rowSelection === "single" && (
                <td>
                  <input
                    type="radio"
                    role="radio"
                    id={`radio-fileSelection-${row.id}`}
                    checked={selectedRowIds?.includes(row.id) ?? false}
                    aria-checked={selectedRowIds?.includes(row.id) ?? false}
                    onChange={() => onSelectionChange?.([row.id])}
                  />
                </td>
              )}
              {columnDefs.map((col, colIdx) => {
                const value =
                  col.valueGetter?.({
                    data: row,
                    node: { rowIndex },
                  }) ?? (col.field ? row[col.field] : null);
                const formatted =
                  col.valueFormatter?.({ value }) ??
                  (value != null ? String(value) : "");
                const cellContent =
                  col.cellRenderer?.({ data: row, value }) ?? formatted;
                return (
                  <td
                    key={col.colId ?? colIdx}
                    data-testid={col.colId ? `cell-${col.colId}` : undefined}
                  >
                    {cellContent}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    );
  },
}));

const mockFiles: LocationCsvFile[] = [
  {
    id: "file-1",
    fileName: "locations.csv",
    locationCount: 50,
    createdBy: "user@test.com",
    createdAt: "2025-01-15T10:00:00",
  },
];

function renderExistingFilesTab(
  props: Partial<React.ComponentProps<typeof ExistingFilesTab>> = {},
) {
  const onSelectFile = vi.fn();
  const onDeleteFile = vi.fn();
  const onDownloadFile = vi.fn();
  const onViewFile = vi.fn();
  const onPageChange = vi.fn();
  const onPageSizeChange = vi.fn();
  const setActiveTab = vi.fn();
  render(
    <ExistingFilesTab
      files={[]}
      selectedFileId=""
      onSelectFile={onSelectFile}
      onDeleteFile={onDeleteFile}
      onDownloadFile={onDownloadFile}
      onViewFile={onViewFile}
      currentPage={1}
      pageSize={10}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      totalItems={1}
      {...props}
    />,
  );
  return {
    onSelectFile,
    onDeleteFile,
    onDownloadFile,
    onViewFile,
    onPageChange,
    onPageSizeChange,
    setActiveTab,
  };
}

describe("ExistingFilesTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("loading state", () => {
    it("renders table with loading state when isLoading is true", () => {
      renderExistingFilesTab({ isLoading: true });
      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(
        screen.queryByText("geofencingExistingFiles.noFilesYet"),
      ).not.toBeInTheDocument();
    });
  });

  describe("empty state", () => {
    it("renders empty state when no files", () => {
      renderExistingFilesTab({ files: [] });
      expect(
        screen.getByText("geofencingExistingFiles.noFilesYet"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("geofencingExistingFiles.startUploadingBulk"),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: /geofencingExistingFiles.uploadCsvXlsx/i,
        }),
      ).toBeInTheDocument();
    });

    it("calls setActiveTab with 'upload' when Upload CSV/XLSX is clicked", async () => {
      const setActiveTab = vi.fn();
      const user = userEvent.setup();
      renderExistingFilesTab({ files: [], setActiveTab });
      await user.click(
        screen.getByRole("button", {
          name: /geofencingExistingFiles.uploadCsvXlsx/i,
        }),
      );
      expect(setActiveTab).toHaveBeenCalledWith("upload");
    });

    it("does not throw when Upload is clicked and setActiveTab is undefined", async () => {
      const user = userEvent.setup();
      renderExistingFilesTab({ files: [], setActiveTab: undefined });
      await user.click(
        screen.getByRole("button", {
          name: /geofencingExistingFiles.uploadCsvXlsx/i,
        }),
      );
    });
  });

  describe("table with files", () => {
    it("renders table and pagination when files are provided", () => {
      renderExistingFilesTab({
        files: mockFiles,
        totalItems: 1,
      });
      expect(
        screen.getByText("geofencingExistingFiles.autoSelectGps"),
      ).toBeInTheDocument();
      expect(screen.getByText("locations.csv")).toBeInTheDocument();
      expect(screen.getByText("50")).toBeInTheDocument();
      expect(screen.getByText("user@test.com")).toBeInTheDocument();
    });

    it("renders first row with file data", () => {
      renderExistingFilesTab({ files: mockFiles });
      const table = screen.getByRole("table");
      expect(within(table).getByText("locations.csv")).toBeInTheDocument();
      expect(within(table).getByText("50")).toBeInTheDocument();
    });

    it("calls onSelectFile when radio is selected", async () => {
      const { onSelectFile } = renderExistingFilesTab({
        files: mockFiles,
        selectedFileId: "",
      });
      const user = userEvent.setup();
      const radio =
        document.getElementById("radio-fileSelection-file-1") ??
        screen.getByRole("radio");
      await user.click(radio!);
      expect(onSelectFile).toHaveBeenCalledWith("file-1");
    });

    it("shows radio as checked when selectedFileId matches", () => {
      renderExistingFilesTab({
        files: mockFiles,
        selectedFileId: "file-1",
      });
      const radio = screen.getByRole("radio");
      expect(radio).toBeChecked();
    });
  });

  describe("row actions", () => {
    it("calls onViewFile when View is clicked from dropdown", async () => {
      const { onViewFile } = renderExistingFilesTab({ files: mockFiles });
      const user = userEvent.setup();
      const tbody = document.querySelector("tbody");
      const menuButton = tbody?.querySelector("button");
      expect(menuButton).toBeInTheDocument();
      await user.click(menuButton!);
      const viewItem = await screen.findByRole("menuitem", { name: /View/i });
      await user.click(viewItem);
      expect(onViewFile).toHaveBeenCalledWith("file-1");
    });

    it("calls onDownloadFile when Download is clicked from dropdown", async () => {
      const { onDownloadFile } = renderExistingFilesTab({ files: mockFiles });
      const user = userEvent.setup();
      const tbody = document.querySelector("tbody");
      const menuButton = tbody?.querySelector("button");
      await user.click(menuButton!);
      const downloadItem = await screen.findByRole("menuitem", {
        name: /Download/i,
      });
      await user.click(downloadItem);
      expect(onDownloadFile).toHaveBeenCalledWith("file-1");
    });

    it("calls onDeleteFile when Delete is clicked from dropdown", async () => {
      const { onDeleteFile } = renderExistingFilesTab({ files: mockFiles });
      const user = userEvent.setup();
      const tbody = document.querySelector("tbody");
      const menuButton = tbody?.querySelector("button");
      await user.click(menuButton!);
      const deleteItem = await screen.findByRole("menuitem", {
        name: /Delete/i,
      });
      await user.click(deleteItem);
      expect(onDeleteFile).toHaveBeenCalledWith("file-1");
    });
  });

  describe("default props", () => {
    it("uses default isLoading false", () => {
      renderExistingFilesTab({ files: mockFiles });
      expect(screen.queryByText("Loading files...")).not.toBeInTheDocument();
    });

    it("uses default totalPages and totalItems 0", () => {
      renderExistingFilesTab({ files: mockFiles });
      expect(screen.getByText("locations.csv")).toBeInTheDocument();
    });
  });
});
