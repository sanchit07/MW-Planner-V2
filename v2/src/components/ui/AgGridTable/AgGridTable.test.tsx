import { render } from "@testing-library/react";
import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { AgGridTable } from "./AgGridTable";
import type { AgGridTableProps, AgGridSortState } from "./types";

type GridApiMock = {
  applyColumnState: ReturnType<typeof vi.fn>;
  paginationGoToPage: ReturnType<typeof vi.fn>;
  setGridOption: ReturnType<typeof vi.fn>;
  paginationGetCurrentPage: ReturnType<typeof vi.fn>;
  paginationGetPageSize: ReturnType<typeof vi.fn>;
  getColumnState: ReturnType<typeof vi.fn>;
  getSelectedRows: ReturnType<typeof vi.fn>;
  forEachNode: ReturnType<typeof vi.fn>;
};

let lastGridProps: Record<string, unknown> = {};
let mockApi: GridApiMock;

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("ag-grid-community", () => ({
  AllCommunityModule: {},
  ModuleRegistry: { registerModules: vi.fn() },
}));

vi.mock("ag-grid-react", () => {
  let apiInstance: GridApiMock | null = null;
  return {
    AgGridReact: React.forwardRef<
      GridApiMock,
      Record<string, unknown> & {
        onGridReady?: (event: { api: GridApiMock }) => void;
        onSortChanged?: (event: { api: GridApiMock }) => void;
        onPaginationChanged?: (event: {
          api: GridApiMock;
          newData?: boolean;
          newPage?: boolean;
          newPageSize?: boolean;
        }) => void;
        onSelectionChanged?: (event: { api: GridApiMock }) => void;
      }
    >(function MockAgGridReact(props, ref) {
      lastGridProps = { ...props };
      if (!apiInstance) {
        apiInstance = {
          applyColumnState: vi.fn(),
          paginationGoToPage: vi.fn(),
          setGridOption: vi.fn(),
          paginationGetCurrentPage: vi.fn(),
          paginationGetPageSize: vi.fn(),
          getColumnState: vi.fn(),
          getSelectedRows: vi.fn(),
          forEachNode: vi.fn(),
        };
      }
      mockApi = apiInstance;
      if (typeof ref === "object" && ref !== null) {
        (ref as React.MutableRefObject<GridApiMock | null>).current = mockApi;
      }

      React.useEffect(() => {
        const onReady = props.onGridReady as
          | ((e: { api: GridApiMock }) => void)
          | undefined;
        onReady?.({ api: mockApi });
      }, []);
      return <div data-testid="ag-grid-react" />;
    }),
  };
});

vi.mock("../../../styles/ag-grid-overrides.css", () => ({}));

const defaultProps: AgGridTableProps<{ id: string; name: string }> = {
  rowData: [{ id: "1", name: "Row 1" }],
  columnDefs: [
    { colId: "id", field: "id" },
    { colId: "name", field: "name" },
  ],
  getRowId: (row) => row.id,
};

describe("AgGridTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lastGridProps = {};
  });

  describe("rendering", () => {
    it("renders wrapper with theme class and default style when height not provided", () => {
      render(<AgGridTable {...defaultProps} />);
      const wrapper = document.querySelector(
        ".ag-theme-quartz.ag-grid-table-wrapper",
      );
      expect(wrapper).toBeInTheDocument();
      expect(wrapper).toHaveStyle({
        width: "100%",
      });
    });

    it("renders with custom className", () => {
      render(<AgGridTable {...defaultProps} className="custom-class" />);
      const wrapper = document.querySelector(
        ".ag-theme-quartz.ag-grid-table-wrapper.custom-class",
      );
      expect(wrapper).toBeInTheDocument();
    });

    it("applies container height as number in px", () => {
      render(<AgGridTable {...defaultProps} height={400} />);
      const wrapper = document.querySelector(".ag-grid-table-wrapper");
      expect(wrapper).toHaveStyle({ height: "400px", width: "100%" });
    });

    it("applies container height as string", () => {
      render(<AgGridTable {...defaultProps} height="50vh" />);
      const wrapper = document.querySelector(".ag-grid-table-wrapper");
      expect(wrapper).toHaveStyle({ height: "50vh", width: "100%" });
    });

    it("passes rowData and columnDefs to grid", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.rowData).toEqual(defaultProps.rowData);
      expect(lastGridProps.columnDefs).toBeDefined();
      expect((lastGridProps.columnDefs as unknown[]).length).toBe(2);
    });

    it("passes getRowId to grid via getRowId callback", () => {
      const getRowId = vi.fn((row: { id: string }) => row.id);
      render(<AgGridTable {...defaultProps} getRowId={getRowId} />);
      const getRowIdFn = lastGridProps.getRowId as (params: {
        data: { id: string; name?: string };
      }) => string;
      expect(getRowIdFn({ data: { id: "x", name: "y" } })).toBe("x");
      expect(getRowId).toHaveBeenCalledWith(
        expect.objectContaining({ id: "x" }),
      );
    });

    it("passes loading true to grid", () => {
      render(<AgGridTable {...defaultProps} loading={true} />);
      expect(lastGridProps.loading).toBe(true);
    });

    it("uses default emptyMessage when not provided", () => {
      render(<AgGridTable {...defaultProps} />);
      const template = lastGridProps.overlayNoRowsTemplate as string;
      expect(template).toContain("No data");
    });

    it("passes custom emptyMessage to overlay template", () => {
      render(<AgGridTable {...defaultProps} emptyMessage="No rows" />);
      const template = lastGridProps.overlayNoRowsTemplate as string;
      expect(template).toContain("No rows");
    });

    it("escapes emptyMessage in overlay template to prevent XSS", () => {
      render(
        <AgGridTable
          {...defaultProps}
          emptyMessage='<script>alert("x")</script>'
        />,
      );
      const template = lastGridProps.overlayNoRowsTemplate as string;
      expect(template).toContain(
        "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;",
      );
      expect(template).not.toContain("<script>");
    });

    it("passes domLayout to grid", () => {
      render(<AgGridTable {...defaultProps} domLayout="autoHeight" />);
      expect(lastGridProps.domLayout).toBe("autoHeight");
    });

    it("passes defaultColDef with sortable false and resizable true", () => {
      render(
        <AgGridTable
          {...defaultProps}
          defaultColDef={{ sortable: true, resizable: false }}
        />,
      );
      const def = lastGridProps.defaultColDef as Record<string, unknown>;
      expect(def.sortable).toBe(true);
      expect(def.resizable).toBe(false);
    });

    it("passes cacheBlockSize when provided", () => {
      render(<AgGridTable {...defaultProps} cacheBlockSize={100} />);
      expect(lastGridProps.cacheBlockSize).toBe(100);
    });

    it("uses paginationPageSize from serverSideConfig when provided", () => {
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 50,
            currentPage: 1,
            pageSize: 25,
            onPageChange: vi.fn(),
            onPageSizeChange: vi.fn(),
          }}
        />,
      );
      expect(lastGridProps.paginationPageSize).toBe(25);
    });

    it("uses default paginationPageSize 10 when serverSideConfig not provided", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.paginationPageSize).toBe(10);
    });

    it("uses serverSideConfig pageSizeSelector when provided", () => {
      render(
        <AgGridTable
          {...defaultProps}
          serverSideConfig={{
            totalCount: 0,
            currentPage: 1,
            pageSize: 10,
            onPageChange: vi.fn(),
            onPageSizeChange: vi.fn(),
            pageSizeSelector: [5, 20],
          }}
        />,
      );
      expect(lastGridProps.paginationPageSizeSelector).toEqual([5, 20]);
    });

    it("uses default pageSizeSelector when not in serverSideConfig", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.paginationPageSizeSelector).toEqual([
        10, 25, 50, 100,
      ]);
    });

    it("sets popupParent to document.body when document is defined", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.popupParent).toBe(document.body);
    });
  });

  describe("sort state", () => {
    it("applies sortState on grid ready via applyColumnState", () => {
      const sortState: AgGridSortState[] = [
        { key: "name", direction: "desc" },
        { key: "id", direction: "asc" },
      ];
      render(<AgGridTable {...defaultProps} sortState={sortState} />);
      expect(mockApi.applyColumnState).toHaveBeenCalledWith({
        state: [
          { colId: "name", sort: "desc", sortIndex: 0 },
          { colId: "id", sort: "asc", sortIndex: 1 },
        ],
      });
    });

    it("does not call applyColumnState when sortState is empty on ready", () => {
      render(<AgGridTable {...defaultProps} sortState={[]} />);
      expect(mockApi.applyColumnState).not.toHaveBeenCalled();
    });

    it("applies sortState only on grid ready (no sync from parent when sortState changes)", () => {
      const sortState: AgGridSortState[] = [{ key: "id", direction: "asc" }];
      const { rerender } = render(
        <AgGridTable {...defaultProps} sortState={[]} />,
      );
      mockApi.applyColumnState.mockClear();
      rerender(<AgGridTable {...defaultProps} sortState={sortState} />);
      expect(mockApi.applyColumnState).not.toHaveBeenCalled();
    });
  });

  describe("server-side pagination", () => {
    it("calls paginationGoToPage and setGridOption on grid ready when serverSideConfig provided", () => {
      const onPageChange = vi.fn();
      const onPageSizeChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 3,
            pageSize: 25,
            onPageChange,
            onPageSizeChange,
          }}
        />,
      );
      expect(mockApi.paginationGoToPage).toHaveBeenCalledWith(2);
      expect(mockApi.setGridOption).toHaveBeenCalledWith(
        "paginationPageSize",
        25,
      );
    });

    it("onPaginationChanged calls onPageChange when newPage and page differs", () => {
      const onPageChange = vi.fn();
      const onPageSizeChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 1,
            pageSize: 10,
            onPageChange,
            onPageSizeChange,
          }}
        />,
      );
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
        newData?: boolean;
        newPage?: boolean;
        newPageSize?: boolean;
      }) => void;
      onPaginationChanged({ api: mockApi, newPage: false });
      mockApi.paginationGetCurrentPage.mockReturnValue(1);
      onPaginationChanged({
        api: mockApi,
        newPage: true,
        newPageSize: false,
      });
      expect(onPageChange).toHaveBeenCalledWith(2);
    });

    it("onPaginationChanged calls onPageSizeChange when newPageSize and size differs", () => {
      const onPageChange = vi.fn();
      const onPageSizeChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 1,
            pageSize: 10,
            onPageChange,
            onPageSizeChange,
          }}
        />,
      );
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
        newData?: boolean;
        newPage?: boolean;
        newPageSize?: boolean;
      }) => void;
      onPaginationChanged({ api: mockApi, newPage: false });
      mockApi.paginationGetPageSize.mockReturnValue(50);
      onPaginationChanged({
        api: mockApi,
        newPage: false,
        newPageSize: true,
      });
      expect(onPageSizeChange).toHaveBeenCalledWith(50);
    });

    it("onPaginationChanged does not call onPageChange when page equals currentPage", () => {
      const onPageChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 2,
            pageSize: 10,
            onPageChange,
            onPageSizeChange: vi.fn(),
          }}
        />,
      );
      mockApi.paginationGetCurrentPage.mockReturnValue(1);
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
        newPage?: boolean;
      }) => void;
      onPaginationChanged({ api: mockApi, newPage: true });
      expect(onPageChange).not.toHaveBeenCalled();
    });

    it("onPaginationChanged does not call onPageSizeChange when size equals serverSideConfig pageSize", () => {
      const onPageSizeChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 1,
            pageSize: 10,
            onPageChange: vi.fn(),
            onPageSizeChange,
          }}
        />,
      );
      mockApi.paginationGetPageSize.mockReturnValue(10);
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
        newPageSize?: boolean;
      }) => void;
      onPaginationChanged({ api: mockApi, newPageSize: true });
      expect(onPageSizeChange).not.toHaveBeenCalled();
    });

    it("onPaginationChanged returns early when event.newData", () => {
      const onPageChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 1,
            pageSize: 10,
            onPageChange,
            onPageSizeChange: vi.fn(),
          }}
        />,
      );
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
        newData?: boolean;
      }) => void;
      onPaginationChanged({ api: mockApi, newData: true });
      expect(onPageChange).not.toHaveBeenCalled();
    });

    it("onPaginationChanged returns early when serverSidePagination is false", () => {
      const onPageChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          serverSideConfig={{
            totalCount: 100,
            currentPage: 1,
            pageSize: 10,
            onPageChange,
            onPageSizeChange: vi.fn(),
          }}
        />,
      );
      const onPaginationChanged = lastGridProps.onPaginationChanged as (event: {
        api: GridApiMock;
      }) => void;
      if (onPaginationChanged) onPaginationChanged({ api: mockApi });
      expect(onPageChange).not.toHaveBeenCalled();
    });
  });

  describe("onSortChanged", () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });
    afterEach(() => {
      vi.useRealTimers();
    });
    it("calls onSortChange with sort model from column state after timeout", () => {
      const onSortChange = vi.fn();
      render(<AgGridTable {...defaultProps} onSortChange={onSortChange} />);
      mockApi.getColumnState.mockReturnValue([
        { colId: "name", sort: "desc" },
        { colId: "id", sort: "asc" },
      ]);
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).toHaveBeenCalledWith([
        { key: "name", direction: "desc" },
        { key: "id", direction: "asc" },
      ]);
    });

    it("maps non-asc/desc sort value to asc", () => {
      const onSortChange = vi.fn();
      render(<AgGridTable {...defaultProps} onSortChange={onSortChange} />);
      mockApi.getColumnState.mockReturnValue([
        { colId: "x", sort: "other" as "asc" | "desc" },
        { colId: "y", sort: "asc" },
      ]);
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).toHaveBeenCalled();
      const model = onSortChange.mock.calls[0][0] as AgGridSortState[];
      const xEntry = model.find((m) => m.key === "x");
      expect(xEntry?.direction).toBe("asc");
    });

    it("does not call onSortChange when onSortChange prop is not provided", () => {
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      render(<AgGridTable {...defaultProps} />);
      onSortChanged?.({ api: mockApi });
      vi.runAllTimers();
      expect(mockApi.getColumnState).not.toHaveBeenCalled();
    });

    it("does not call onSortChange when sortChanged is from initial apply (isSortFromExternalRef)", () => {
      const onSortChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          sortState={[{ key: "name", direction: "desc" }]}
          onSortChange={onSortChange}
        />,
      );
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).not.toHaveBeenCalled();
    });

    it("filters columns with null sort from sort model", () => {
      const onSortChange = vi.fn();
      render(<AgGridTable {...defaultProps} onSortChange={onSortChange} />);
      mockApi.getColumnState.mockReturnValue([
        { colId: "a", sort: "asc" },
        { colId: "b", sort: null },
      ]);
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).toHaveBeenCalledWith([
        { key: "a", direction: "asc" },
      ]);
    });

    it("uses colId empty string when colId is null/undefined", () => {
      const onSortChange = vi.fn();
      render(<AgGridTable {...defaultProps} onSortChange={onSortChange} />);
      mockApi.getColumnState.mockReturnValue([
        { colId: undefined, sort: "desc" },
      ]);
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).toHaveBeenCalledWith([
        { key: "", direction: "desc" },
      ]);
    });

    it("calls onSortChange with empty array when all columns have null sort (default)", () => {
      const onSortChange = vi.fn();
      render(<AgGridTable {...defaultProps} onSortChange={onSortChange} />);
      mockApi.getColumnState.mockReturnValue([
        { colId: "a", sort: null },
        { colId: "b", sort: null },
      ]);
      const onSortChanged = lastGridProps.onSortChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSortChanged({ api: mockApi });
      vi.runAllTimers();
      expect(onSortChange).toHaveBeenCalledWith([]);
    });
  });

  describe("selection", () => {
    it("calls onSelectionChange with ids from getSelectedRows and getRowId", () => {
      const onSelectionChange = vi.fn();
      const getRowId = (row: { id: string }) => row.id;
      render(
        <AgGridTable
          {...defaultProps}
          rowSelection="multiple"
          onSelectionChange={onSelectionChange}
          getRowId={getRowId}
        />,
      );
      const row1 = { id: "r1", name: "R1" };
      const row2 = { id: "r2", name: "R2" };
      mockApi.getSelectedRows.mockReturnValue([row1, row2]);
      const onSelectionChanged = lastGridProps.onSelectionChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSelectionChanged({ api: mockApi });
      expect(onSelectionChange).toHaveBeenCalledWith(["r1", "r2"]);
    });

    it("filters out empty ids when row is null", () => {
      const onSelectionChange = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          rowSelection="multiple"
          onSelectionChange={onSelectionChange}
        />,
      );
      mockApi.getSelectedRows.mockReturnValue([
        { id: "a", name: "A" },
        null,
        undefined,
      ]);
      const onSelectionChanged = lastGridProps.onSelectionChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSelectionChanged({ api: mockApi });
      expect(onSelectionChange).toHaveBeenCalledWith(["a"]);
    });

    it("does not pass onSelectionChanged when rowSelection is false", () => {
      render(<AgGridTable {...defaultProps} rowSelection={false} />);
      expect(lastGridProps.onSelectionChanged).toBeUndefined();
    });

    it("onSelectionChanged returns early when onSelectionChange is not provided", () => {
      render(<AgGridTable {...defaultProps} rowSelection="multiple" />);
      const onSelectionChanged = lastGridProps.onSelectionChanged as (event: {
        api: GridApiMock;
      }) => void;
      onSelectionChanged({ api: mockApi });
      expect(mockApi.getSelectedRows).not.toHaveBeenCalled();
    });

    it("passes rowSelection undefined when rowSelection is false", () => {
      render(<AgGridTable {...defaultProps} rowSelection={false} />);
      expect(lastGridProps.rowSelection).toBeUndefined();
    });

    it("passes rowSelection single when not server-side", () => {
      render(<AgGridTable {...defaultProps} rowSelection="single" />);
      expect(lastGridProps.rowSelection).toBe("single");
    });

    it("passes rowSelection multiple when not server-side", () => {
      render(<AgGridTable {...defaultProps} rowSelection="multiple" />);
      expect(lastGridProps.rowSelection).toBe("multiple");
    });

    it("passes mode singleRow and isRowSelectable when server-side and rowSelection single", () => {
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 0,
            currentPage: 1,
            pageSize: 10,
            onPageChange: vi.fn(),
            onPageSizeChange: vi.fn(),
          }}
          rowSelection="single"
        />,
      );
      const opt = lastGridProps.rowSelection as {
        mode: string;
        isRowSelectable: (params: { data: unknown }) => boolean;
      };
      expect(opt.mode).toBe("singleRow");
      expect(opt.isRowSelectable({ data: { id: "1", name: "X" } })).toBe(true);
      expect(
        opt.isRowSelectable({
          data: { id: "2", name: "Y", __isPlaceholder: true },
        }),
      ).toBe(false);
    });

    it("passes mode multiRow when server-side and rowSelection multiple", () => {
      render(
        <AgGridTable
          {...defaultProps}
          serverSidePagination={true}
          serverSideConfig={{
            totalCount: 0,
            currentPage: 1,
            pageSize: 10,
            onPageChange: vi.fn(),
            onPageSizeChange: vi.fn(),
          }}
          rowSelection="multiple"
        />,
      );
      const opt = lastGridProps.rowSelection as { mode: string };
      expect(opt.mode).toBe("multiRow");
    });

    it("syncs selectedRowIds via forEachNode setSelected when api is ready", () => {
      const setSelected = vi.fn();
      render(
        <AgGridTable
          {...defaultProps}
          rowData={[
            { id: "1", name: "A" },
            { id: "2", name: "B" },
          ]}
          rowSelection="multiple"
          selectedRowIds={["1"]}
        />,
      );
      expect(mockApi.forEachNode).toHaveBeenCalled();
      const forEachNodeCb = mockApi.forEachNode.mock.calls[0][0];
      const node1 = { data: { id: "1", name: "A" }, setSelected };
      const node2 = { data: { id: "2", name: "B" }, setSelected: vi.fn() };
      forEachNodeCb(node1);
      forEachNodeCb(node2);
      expect(setSelected).toHaveBeenCalledWith(true);
      expect(node2.setSelected).toHaveBeenCalledWith(false);
    });
  });

  describe("columnDefsWithVisibility", () => {
    it("hides columns whose colId is in hiddenColumns", () => {
      type RowWithExtra = { id: string; name: string; extra?: string };
      render(
        <AgGridTable<RowWithExtra>
          {...(defaultProps as AgGridTableProps<RowWithExtra>)}
          columnDefs={[
            { colId: "id", field: "id" },
            { colId: "name", field: "name" },
            { colId: "extra", field: "extra" },
          ]}
          hiddenColumns={["name"]}
        />,
      );
      const defs = lastGridProps.columnDefs as Array<{
        colId?: string;
        hide?: boolean;
      }>;
      const nameCol = defs.find((c) => c.colId === "name");
      expect(nameCol?.hide).toBe(true);
      const idCol = defs.find((c) => c.colId === "id");
      expect(idCol?.hide).toBe(false);
    });

    it("sets sortable true when not explicitly false", () => {
      render(
        <AgGridTable
          {...defaultProps}
          columnDefs={[
            { colId: "id", field: "id" },
            { colId: "name", field: "name", sortable: false },
          ]}
        />,
      );
      const defs = lastGridProps.columnDefs as Array<{ sortable?: boolean }>;
      expect(defs[0].sortable).toBe(true);
      expect(defs[1].sortable).toBe(false);
    });

    it("uses custom comparator when provided", () => {
      const comparator = vi.fn(() => 1);
      render(
        <AgGridTable
          {...defaultProps}
          columnDefs={[{ colId: "id", field: "id", comparator }]}
        />,
      );
      const defs = lastGridProps.columnDefs as Array<{
        comparator?: (a: unknown, b: unknown) => number;
      }>;
      expect(defs[0].comparator).toBe(comparator);
    });

    it("sets hide false when colId is missing", () => {
      render(
        <AgGridTable
          {...defaultProps}
          columnDefs={
            [{ field: "noColId" }] as unknown as AgGridTableProps<{
              id: string;
              name: string;
            }>["columnDefs"]
          }
          hiddenColumns={["noColId"]}
        />,
      );
      const defs = lastGridProps.columnDefs as Array<{ hide?: boolean }>;
      expect(defs[0].hide).toBe(false);
    });
  });

  describe("default props", () => {
    it("uses default loading false", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.loading).toBe(false);
    });

    it("uses autoHeight when height not provided so grid sizes to rows", () => {
      render(<AgGridTable {...defaultProps} />);
      expect(lastGridProps.domLayout).toBe("normal");
    });
  });
});
