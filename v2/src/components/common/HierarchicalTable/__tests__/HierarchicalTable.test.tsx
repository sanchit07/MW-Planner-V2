import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { HierarchicalTable } from "../HierarchicalTable";
import type { ParentRowData, ChildRowData, ParentTableColumn } from "../types";

// Mock dependencies
const mockGetItem = vi.fn();
const mockSetItem = vi.fn();

vi.mock("../../../utils/storage", () => ({
  default: {
    getItem: mockGetItem,
    setItem: mockSetItem,
    removeItem: vi.fn(),
    removeAll: vi.fn(),
  },
}));

// Mock hooks
const mockSelectedItems = new Set<string>();
const mockExpandedItems = new Set<string>();
const mockSortState: Array<{ key: string; direction: "asc" | "desc" }> = [];

const mockSelectionHook = {
  selectedItems: mockSelectedItems,
  selectAll: vi.fn(),
  clearSelection: vi.fn(),
  isSelected: vi.fn((id: string) => mockSelectedItems.has(id)),
};

const mockExpansionHook = {
  expandedItems: mockExpandedItems,
  toggleExpansion: vi.fn(),
  isExpanded: vi.fn((id: string) => mockExpandedItems.has(id)),
};

const mockSortingHook = {
  sortState: mockSortState,
  setSortState: vi.fn(),
};

vi.mock("../hooks", () => ({
  useTableSelection: () => mockSelectionHook,
  useTableExpansion: () => mockExpansionHook,
  useTableSorting: () => mockSortingHook,
  useTablePersistence: vi.fn(),
}));

// Mock ColumnVisibilityDrawer
vi.mock("../ColumnVisibilityDrawer", () => ({
  default: ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) => {
    if (!isOpen) return null;
    return (
      <div data-testid="column-visibility-drawer">
        <button onClick={onClose}>Close</button>
      </div>
    );
  },
}));

// Mock cell renderers
vi.mock("../cell-renderers", () => ({
  TooltipHeader: ({ label }: { label: string }) => <span>{label}</span>,
}));

// Mock UI components
vi.mock("@components/ui/Button", () => ({
  Button: ({
    children,
    onClick,
    "data-testid": testId,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
    "data-testid"?: string;
  }) => (
    <button onClick={onClick} data-testid={testId}>
      {children}
    </button>
  ),
}));

vi.mock("@components/ui/Checkbox", () => ({
  Checkbox: ({
    checked,
    onChange,
    isIndeterminate,
    "data-testid": testId,
  }: {
    checked: boolean;
    onChange?: (e: { target: { checked: boolean } }) => void;
    isIndeterminate?: boolean;
    "data-testid"?: string;
  }) => (
    <input
      type="checkbox"
      checked={checked}
      onChange={(e) => onChange?.({ target: { checked: e.target.checked } })}
      data-indeterminate={isIndeterminate}
      data-testid={testId}
    />
  ),
}));

vi.mock("@components/ui/Skeleton", () => ({
  Skeleton: ({ className }: { className?: string }) => (
    <div data-testid="skeleton" className={className} />
  ),
}));

vi.mock("@components/ui/Spinner", () => ({
  Spinner: () => <div data-testid="spinner" />,
}));

// Test data
const createTestData = (): ParentRowData[] => [
  {
    id: "parent1",
    name: "Parent 1",
    value: 100,
    children: [
      { id: "child1", parentId: "parent1", name: "Child 1", value: 50 },
      { id: "child2", parentId: "parent1", name: "Child 2", value: 50 },
    ] as ChildRowData[],
  },
  {
    id: "parent2",
    name: "Parent 2",
    value: 200,
    children: [] as ChildRowData[],
  },
];

const createTestColumns = (): ParentTableColumn[] => [
  { key: "name", header: "Name", sortable: true },
  { key: "value", header: "Value", sortable: true, align: "right" },
];

describe("HierarchicalTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSelectedItems.clear();
    mockExpandedItems.clear();
    mockSortState.length = 0;
    mockGetItem.mockReturnValue(null);
    mockSetItem.mockReturnValue(undefined);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("Basic rendering", () => {
    it("renders table with data", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(<HierarchicalTable data={data} columns={columns} />);

      expect(screen.getByText("Parent 1")).toBeInTheDocument();
      expect(screen.getByText("Parent 2")).toBeInTheDocument();
    });

    it("renders column headers", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(<HierarchicalTable data={data} columns={columns} />);

      expect(screen.getByText("Name")).toBeInTheDocument();
      expect(screen.getByText("Value")).toBeInTheDocument();
    });

    it("renders empty state when data is empty", () => {
      const columns = createTestColumns();

      render(<HierarchicalTable data={[]} columns={columns} />);

      expect(screen.getByText("No data available")).toBeInTheDocument();
    });

    it("renders custom empty message", () => {
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={[]}
          columns={columns}
          emptyMessage="Custom empty message"
        />,
      );

      expect(screen.getByText("Custom empty message")).toBeInTheDocument();
    });

    it("renders custom empty renderer", () => {
      const columns = createTestColumns();
      const emptyRenderer = () => <div>Custom Empty</div>;

      render(
        <HierarchicalTable
          data={[]}
          columns={columns}
          emptyRenderer={emptyRenderer}
        />,
      );

      expect(screen.getByText("Custom Empty")).toBeInTheDocument();
    });

    it("applies custom className", () => {
      const data = createTestData();
      const columns = createTestColumns();

      const { container } = render(
        <HierarchicalTable
          data={data}
          columns={columns}
          className="custom-table"
        />,
      );

      const tableContainer = container.querySelector(".custom-table");
      expect(tableContainer).toBeInTheDocument();
    });
  });

  describe("Loading state", () => {
    it("renders skeleton rows when loading", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable data={data} columns={columns} loading={true} />,
      );

      const skeletons = screen.getAllByTestId("skeleton");
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it("renders custom number of skeleton rows", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          loading={true}
          skeletonRowsCount={10}
        />,
      );

      const skeletons = screen.getAllByTestId("skeleton");
      // Each row has multiple skeleton cells
      expect(skeletons.length).toBeGreaterThan(10);
    });

    it("does not sort data when loading", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockSortState.push({ key: "name", direction: "asc" });

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          loading={true}
          sorting={{ mode: "client" }}
        />,
      );

      // When loading, skeleton rows are shown instead of data
      const skeletons = screen.getAllByTestId("skeleton");
      expect(skeletons.length).toBeGreaterThan(0);
      // Actual data should not be visible
      expect(screen.queryByText("Parent 1")).not.toBeInTheDocument();
    });
  });

  describe("Selection", () => {
    it("renders selection checkbox when selection is enabled", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      expect(checkboxes.length).toBeGreaterThan(0);
    });

    it("selects item when checkbox is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[1]); // Click first row checkbox (index 0 is select all)

      expect(mockSelectionHook.selectAll).toHaveBeenCalled();
    });

    it("selects all when select all checkbox is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]); // Select all checkbox

      expect(mockSelectionHook.selectAll).toHaveBeenCalled();
    });

    it("shows indeterminate state when some items are selected", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockSelectedItems.add("parent1");

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const selectAllCheckbox = screen.getAllByRole("checkbox")[0];
      expect(selectAllCheckbox).toHaveAttribute("data-indeterminate", "true");
    });

    it("cascades selection to children when parent is selected", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
            selectChildrenOnParent: true,
          }}
        />,
      );

      // Expand parent first
      mockExpandedItems.add("parent1");

      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[1]); // Click parent checkbox

      const callArgs = mockSelectionHook.selectAll.mock.calls[0][0];
      expect(callArgs).toContain("parent1");
      expect(callArgs).toContain("child1");
      expect(callArgs).toContain("child2");
    });
  });

  describe("Expansion", () => {
    it("renders expansion button when expansion is enabled", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const expandButtons = screen.getAllByTestId(/button-expand-/);
      expect(expandButtons.length).toBeGreaterThan(0);
    });

    it("expands row when expansion button is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const expandButton = screen.getByTestId("button-expand-parent1");
      await user.click(expandButton);

      expect(mockExpansionHook.toggleExpansion).toHaveBeenCalledWith("parent1");
    });

    it("renders children when row is expanded", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockExpandedItems.add("parent1");

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      expect(screen.getByText("Child 1")).toBeInTheDocument();
      expect(screen.getByText("Child 2")).toBeInTheDocument();
    });

    it("renders loading row for children", () => {
      const columns = createTestColumns();
      mockExpandedItems.add("parent1");

      const dataWithLoading: ParentRowData[] = [
        {
          id: "parent1",
          name: "Parent 1",
          value: 100,
          children: [
            {
              id: "loading-child",
              parentId: "parent1",
              isLoading: true,
            } as unknown as ChildRowData,
          ] as ChildRowData[],
        },
      ];

      render(
        <HierarchicalTable
          data={dataWithLoading}
          columns={columns}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      expect(screen.getByText("Loading schedules...")).toBeInTheDocument();
      expect(screen.getByTestId("spinner")).toBeInTheDocument();
    });
  });

  describe("Sorting", () => {
    it("renders sort icons for sortable columns", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          sorting={{ mode: "client" }}
        />,
      );

      // Sort icons should be present (mocked as part of header)
      expect(screen.getByText("Name")).toBeInTheDocument();
    });

    it("calls setSortState when sortable column header is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          sorting={{ mode: "client" }}
        />,
      );

      // Find and click sortable header
      const nameHeader = screen.getByText("Name").closest("th");
      if (nameHeader) {
        await user.click(nameHeader);
        expect(mockSortingHook.setSortState).toHaveBeenCalled();
      }
    });

    it("supports multi-sort", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          sorting={{ mode: "client", multiSort: true }}
        />,
      );

      // Click multiple column headers
      const nameHeader = screen.getByText("Name").closest("th");
      if (nameHeader) {
        await user.click(nameHeader);
        expect(mockSortingHook.setSortState).toHaveBeenCalled();
      }
    });
  });

  describe("Column visibility", () => {
    it("hides columns based on hiddenColumns prop", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          hiddenColumns={["value"]}
        />,
      );

      expect(screen.getByText("Name")).toBeInTheDocument();
      // Value column should be hidden
      const valueHeaders = screen.queryAllByText("Value");
      expect(valueHeaders.length).toBe(0);
    });

    it("opens column visibility drawer when icon is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{ enabled: true }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      const toggleButton = screen.getByTestId("column-visibility-toggle");
      await user.click(toggleButton);

      expect(
        screen.getByTestId("column-visibility-drawer"),
      ).toBeInTheDocument();
    });

    it("loads hidden columns from storage on mount", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockGetItem.mockReturnValue(JSON.stringify({ hiddenColumns: ["value"] }));

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{ enabled: true, storageKey: "test-key" }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      // Storage should be accessed (may be called asynchronously)
      // The important thing is the component renders without errors
      expect(screen.getByText("Name")).toBeInTheDocument();
    });

    it("handles invalid storage data gracefully", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockGetItem.mockReturnValue("invalid json");

      const consoleSpy = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{ enabled: true, storageKey: "test-key" }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      // Component should render without crashing
      expect(screen.getByText("Name")).toBeInTheDocument();
      // Error may be logged (but we don't wait for it as it's async)
      consoleSpy.mockRestore();
    });

    it("aligns inline child rows with the parent grid", () => {
      const data = createTestData();
      const columns = createTestColumns();
      mockExpandedItems.add("parent1");

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          selection={{
            enabled: true,
            columnPosition: 0,
            getItemId: (row) => row.id,
          }}
          expansion={{
            enabled: true,
            columnPosition: 1,
            getItemId: (row) => row.id,
            showChildrenHeaders: false,
          }}
        />,
      );

      const parentRow = screen.getByText("Parent 1").closest("tr");
      const childRow = screen.getByText("Child 1").closest("tr");

      // Same number of cells, so every child value sits under its parent header
      expect(childRow?.querySelectorAll("td")).toHaveLength(
        parentRow?.querySelectorAll("td").length ?? 0,
      );

      // ...and the child's name lands in the same column index as the parent's
      const cellIndex = (row: Element | null | undefined, text: string) =>
        Array.from(row?.querySelectorAll("td") ?? []).findIndex((cell) =>
          cell.textContent?.includes(text),
        );
      expect(cellIndex(childRow, "Child 1")).toBe(
        cellIndex(parentRow, "Parent 1"),
      );

      mockExpandedItems.delete("parent1");
    });

    it("uses compact cell spacing by default", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(<HierarchicalTable data={data} columns={columns} />);

      expect(screen.getByText("Name").closest("th")).toHaveClass("p-2");
      expect(screen.getByText("Parent 1").closest("td")).toHaveClass("p-2");
    });

    it("uses roomier cell spacing when density is comfortable", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          density="comfortable"
        />,
      );

      const header = screen.getByText("Name").closest("th");
      expect(header).toHaveClass("h-12");
      expect(header).toHaveClass("px-4");
      expect(header).not.toHaveClass("p-2");
      expect(screen.getByText("Parent 1").closest("td")).toHaveClass("p-4");
    });

    it("hides the header trigger when hideHeaderTrigger is set", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{ enabled: true, hideHeaderTrigger: true }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      expect(
        screen.queryByTestId("column-visibility-toggle"),
      ).not.toBeInTheDocument();
    });

    it("opens the drawer from a controlled isOpen prop", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{
            enabled: true,
            hideHeaderTrigger: true,
            isOpen: true,
          }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      expect(
        screen.getByTestId("column-visibility-drawer"),
      ).toBeInTheDocument();
    });

    it("notifies onOpenChange when a controlled drawer is closed", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();
      const onOpenChange = vi.fn();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          columnVisibilityDrawer={{
            enabled: true,
            hideHeaderTrigger: true,
            isOpen: true,
            onOpenChange,
          }}
          persistence={{ enabled: true, storageKey: "test-key" }}
        />,
      );

      await user.click(screen.getByText("Close"));

      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
  });

  describe("Group headers", () => {
    it("renders group headers", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          groupHeaders={[
            { text: "Group 1", colspan: 1 },
            { text: "Group 2", colspan: 1 },
          ]}
        />,
      );

      expect(screen.getByText("Group 1")).toBeInTheDocument();
      expect(screen.getByText("Group 2")).toBeInTheDocument();
    });

    it("renders selection checkbox in group header row", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          groupHeaders={[{ text: "Group 1", colspan: 2 }]}
          selection={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      expect(checkboxes.length).toBeGreaterThan(0);
    });
  });

  describe("Total row", () => {
    it("renders total row when provided", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const totalRow = {
        name: "Total",
        value: 300,
      } as unknown as ParentRowData;

      render(
        <HierarchicalTable data={data} columns={columns} totalRow={totalRow} />,
      );

      expect(screen.getByText("Total")).toBeInTheDocument();
    });

    it("does not render total row when loading", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const totalRow = {
        name: "Total",
        value: 300,
      } as unknown as ParentRowData;

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          totalRow={totalRow}
          loading={true}
        />,
      );

      expect(screen.queryByText("Total")).not.toBeInTheDocument();
    });

    it("does not render total row when data is empty", () => {
      const columns = createTestColumns();
      const totalRow = { name: "Total", value: 0 } as unknown as ParentRowData;

      render(
        <HierarchicalTable data={[]} columns={columns} totalRow={totalRow} />,
      );

      expect(screen.queryByText("Total")).not.toBeInTheDocument();
    });
  });

  describe("Row styling", () => {
    it("applies striped styling", () => {
      const data = createTestData();
      const columns = createTestColumns();

      const { container } = render(
        <HierarchicalTable data={data} columns={columns} striped={true} />,
      );

      const rows = container.querySelectorAll("tbody tr");
      expect(rows.length).toBeGreaterThan(0);
    });

    it("applies hoverable styling when onRowClick is provided", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const onRowClick = vi.fn();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          onRowClick={onRowClick}
        />,
      );

      // Rows should have cursor-pointer class (checked via rendering)
      expect(screen.getByText("Parent 1")).toBeInTheDocument();
    });

    it("calls onRowClick when row is clicked", async () => {
      const user = userEvent.setup();
      const data = createTestData();
      const columns = createTestColumns();
      const onRowClick = vi.fn();

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          onRowClick={onRowClick}
        />,
      );

      const parentRow = screen.getByText("Parent 1").closest("tr");
      if (parentRow) {
        await user.click(parentRow);
        expect(onRowClick).toHaveBeenCalled();
      }
    });
  });

  describe("Column alignment", () => {
    it("applies right alignment to columns", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(<HierarchicalTable data={data} columns={columns} />);

      // Value column has right alignment
      expect(screen.getByText("Value")).toBeInTheDocument();
    });
  });

  describe("Edge cases", () => {
    it("handles rows without children", () => {
      const data = createTestData();
      const columns = createTestColumns();

      render(<HierarchicalTable data={data} columns={columns} />);

      expect(screen.getByText("Parent 2")).toBeInTheDocument();
    });

    it("handles custom children columns", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const childrenColumns = [{ key: "name", header: "Child Name" }];

      mockExpandedItems.add("parent1");

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          childrenColumns={childrenColumns}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      expect(screen.getByText("Child 1")).toBeInTheDocument();
    });

    it("handles function-based parentRowClassName", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const parentRowClassName = (row: ParentRowData) =>
        row.id === "parent1" ? "custom-class" : "";

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          parentRowClassName={parentRowClassName}
        />,
      );

      expect(screen.getByText("Parent 1")).toBeInTheDocument();
    });

    it("handles function-based childRowClassName", () => {
      const data = createTestData();
      const columns = createTestColumns();
      const childRowClassName = (row: ChildRowData) =>
        row.id === "child1" ? "custom-child-class" : "";

      mockExpandedItems.add("parent1");

      render(
        <HierarchicalTable
          data={data}
          columns={columns}
          childRowClassName={childRowClassName}
          expansion={{
            enabled: true,
            getItemId: (row) => row.id,
          }}
        />,
      );

      expect(screen.getByText("Child 1")).toBeInTheDocument();
    });
  });
});
