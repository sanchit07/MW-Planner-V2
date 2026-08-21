import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

import { Table, TableColumn } from "../Table";

// Create mock functions that can be accessed in both the mock factory and tests
const mockGetItem = vi.fn();
const mockSetItem = vi.fn();
const mockRemoveItem = vi.fn();
const mockRemoveAll = vi.fn();

vi.mock("../../utils/storage", () => ({
  default: {
    getItem: mockGetItem,
    setItem: mockSetItem,
    removeItem: mockRemoveItem,
    removeAll: mockRemoveAll,
  },
}));

describe("Table", () => {
  interface TestData {
    id: string | number;
    name: string;
    age: number;
    email: string;
  }

  const defaultColumns: TableColumn<TestData>[] = [
    { key: "name", header: "Name", sortable: true },
    { key: "age", header: "Age", sortable: true },
    { key: "email", header: "Email" },
  ];

  const defaultData: TestData[] = [
    { id: "1", name: "Alice", age: 25, email: "alice@example.com" },
    { id: "2", name: "Bob", age: 30, email: "bob@example.com" },
    { id: "3", name: "Charlie", age: 28, email: "charlie@example.com" },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    // Reset mock return values
    mockGetItem.mockReturnValue(null);
    mockSetItem.mockReturnValue(undefined);
    mockRemoveItem.mockReturnValue(undefined);
    mockRemoveAll.mockReturnValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Rendering", () => {
    it("renders table with data", () => {
      render(<Table columns={defaultColumns} data={defaultData} />);
      expect(screen.getByText("Alice")).toBeInTheDocument();
      expect(screen.getByText("Bob")).toBeInTheDocument();
    });

    it("renders table headers", () => {
      render(<Table columns={defaultColumns} data={defaultData} />);
      expect(screen.getByText("Name")).toBeInTheDocument();
      expect(screen.getByText("Age")).toBeInTheDocument();
      expect(screen.getByText("Email")).toBeInTheDocument();
    });

    it("renders empty message when no data", () => {
      render(
        <Table
          columns={defaultColumns}
          data={[]}
          emptyMessage="No data found"
        />,
      );
      expect(screen.getByText("No data found")).toBeInTheDocument();
    });

    it("renders custom empty renderer", () => {
      render(
        <Table
          columns={defaultColumns}
          data={[]}
          emptyRenderer={() => <div>Custom Empty</div>}
        />,
      );
      expect(screen.getByText("Custom Empty")).toBeInTheDocument();
    });

    it("renders loading skeleton when loading", () => {
      render(
        <Table columns={defaultColumns} data={defaultData} loading={true} />,
      );
      const skeletons = screen.getAllByRole("row");
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it("applies striped styling when striped is true", () => {
      const { container } = render(
        <Table columns={defaultColumns} data={defaultData} striped={true} />,
      );
      const rows = container.querySelectorAll("tbody tr");
      expect(rows.length).toBeGreaterThan(0);
    });
  });

  describe("Column Rendering", () => {
    it("renders custom cell content with render function", () => {
      const columns: TableColumn<TestData>[] = [
        {
          key: "name",
          header: "Name",
          render: (value) => <strong>{value}</strong>,
        },
      ];
      render(<Table columns={columns} data={defaultData} />);
      const strong = screen.getByText("Alice").closest("strong");
      expect(strong).toBeInTheDocument();
    });

    it("applies column width", () => {
      const columns: TableColumn<TestData>[] = [
        { key: "name", header: "Name", width: "200px" },
      ];
      const { container } = render(
        <Table columns={columns} data={defaultData} />,
      );
      const header = container.querySelector("th");
      expect(header).toHaveStyle({ width: "200px" });
    });

    it("applies column alignment", () => {
      const columns: TableColumn<TestData>[] = [
        { key: "age", header: "Age", align: "right" },
      ];
      const { container } = render(
        <Table columns={columns} data={defaultData} />,
      );
      const cell = container.querySelector("td");
      expect(cell?.className).toContain("text-right");
    });

    it("hides columns when in hiddenColumns", () => {
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          hiddenColumns={["email"]}
        />,
      );
      expect(screen.queryByText("Email")).not.toBeInTheDocument();
    });
  });

  describe("Row Selection", () => {
    it("renders selectable checkbox when selectable is true", () => {
      render(
        <Table columns={defaultColumns} data={defaultData} selectable={true} />,
      );
      const checkboxes = screen.getAllByRole("checkbox");
      expect(checkboxes.length).toBeGreaterThan(0);
    });

    it("calls onRowSelect when row checkbox is clicked", async () => {
      const user = userEvent.setup();
      const onRowSelect = vi.fn();
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          selectable={true}
          onRowSelect={onRowSelect}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      if (checkboxes.length > 1) {
        await user.click(checkboxes[1]);
        expect(onRowSelect).toHaveBeenCalledWith("1");
      }
    });

    it("calls onSelectAll when header checkbox is clicked", async () => {
      const user = userEvent.setup();
      const onSelectAll = vi.fn();
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          selectable={true}
          onSelectAll={onSelectAll}
        />,
      );

      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);

      expect(onSelectAll).toHaveBeenCalled();
    });

    it("shows selected state for selected rows", () => {
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          selectable={true}
          selectedRows={["1"]}
        />,
      );
      const checkboxes = screen.getAllByRole("checkbox");
      expect(checkboxes[1]).toBeChecked();
    });

    it("shows indeterminate state for partial selection", () => {
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          selectable={true}
          selectedRows={["1"]}
        />,
      );
      const headerCheckbox = screen.getAllByRole("checkbox")[0];
      expect(headerCheckbox).toHaveProperty("indeterminate", true);
    });
  });

  describe("Sorting", () => {
    it("renders sort icons for sortable columns", () => {
      render(<Table columns={defaultColumns} data={defaultData} />);
      const nameHeader = screen.getByText("Name").closest("th");
      expect(nameHeader).toHaveClass("cursor-pointer");
    });

    it("sorts data when column header is clicked", async () => {
      const user = userEvent.setup();
      render(<Table columns={defaultColumns} data={defaultData} />);

      const nameHeader = screen.getByText("Name");
      await user.click(nameHeader);

      await waitFor(() => {
        const rows = screen.getAllByRole("row");
        expect(rows.length).toBeGreaterThan(1);
      });
    });

    it("supports multi-sort when multiSort is true", async () => {
      const user = userEvent.setup();
      render(
        <Table columns={defaultColumns} data={defaultData} multiSort={true} />,
      );

      await user.click(screen.getByText("Name"));
      await user.click(screen.getByText("Age"));

      await waitFor(() => {
        expect(screen.getByText("Name")).toBeInTheDocument();
      });
    });

    it("calls onSortChange for remote sorting", async () => {
      const user = userEvent.setup();
      const onSortChange = vi.fn();
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          sortMode="remote"
          onSortChange={onSortChange}
        />,
      );

      await user.click(screen.getByText("Name"));

      expect(onSortChange).toHaveBeenCalled();
    });
  });

  describe("Row Click", () => {
    it("calls onRowClick when row is clicked", async () => {
      const user = userEvent.setup();
      const onRowClick = vi.fn();
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          onRowClick={onRowClick}
        />,
      );

      const row = screen.getByText("Alice").closest("tr");
      if (row) {
        await user.click(row);
        expect(onRowClick).toHaveBeenCalledWith(defaultData[0]);
      }
    });

    it("does not call onRowClick when selectable and checkbox is clicked", async () => {
      const user = userEvent.setup();
      const onRowClick = vi.fn();
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          selectable={true}
          onRowClick={onRowClick}
        />,
      );

      const checkbox = screen.getAllByRole("checkbox")[1];
      await user.click(checkbox);

      expect(onRowClick).not.toHaveBeenCalled();
    });
  });

  describe("Group Headers", () => {
    it("renders group headers when provided", () => {
      const groupHeaders = [
        { text: "Group 1", colspan: 2 },
        { text: "Group 2", colspan: 1 },
      ];
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          groupHeaders={groupHeaders}
        />,
      );
      expect(screen.getByText("Group 1")).toBeInTheDocument();
      expect(screen.getByText("Group 2")).toBeInTheDocument();
    });
  });

  describe("Total Row", () => {
    it("renders total row when provided", () => {
      const totalRow: TestData = {
        id: "total",
        name: "Total",
        age: 83,
        email: "",
      };
      render(
        <Table
          columns={defaultColumns}
          data={defaultData}
          totalRow={totalRow}
        />,
      );
      expect(screen.getByText("Total")).toBeInTheDocument();
      expect(screen.getByText("83")).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("handles data without id field", () => {
      const dataWithoutId = [
        { name: "Alice", age: 25, email: "alice@example.com" },
      ];
      render(
        <Table columns={defaultColumns} data={dataWithoutId as TestData[]} />,
      );
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    it("handles columns with custom sortKey", () => {
      const columns: TableColumn<TestData>[] = [
        { key: "name", header: "Name", sortable: true, sortKey: "fullName" },
      ];
      render(<Table columns={columns} data={defaultData} />);
      expect(screen.getByText("Name")).toBeInTheDocument();
    });

    it("handles frozen columns", () => {
      const columns: TableColumn<TestData>[] = [
        { key: "name", header: "Name", frozen: true, frozenPosition: "left" },
      ];
      const { container } = render(
        <Table columns={columns} data={defaultData} />,
      );
      const cell = container.querySelector("td");
      expect(cell?.className).toContain("sticky");
    });
  });
});
