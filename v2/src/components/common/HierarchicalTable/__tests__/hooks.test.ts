import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import storage from "../../../../utils/storage";
import {
  useColumnVisibility,
  useTableExpansion,
  useTablePersistence,
  useTableSelection,
  useTableSorting,
} from "../hooks";

// Mock storage so tests don't rely on jsdom localStorage
vi.mock("../../../../utils/storage", () => ({
  default: {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    removeAll: vi.fn(),
  },
}));

const mockStorage = storage as {
  getItem: ReturnType<typeof vi.fn>;
  setItem: ReturnType<typeof vi.fn>;
  removeItem: ReturnType<typeof vi.fn>;
  removeAll: ReturnType<typeof vi.fn>;
};

beforeEach(() => {
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// useTableSelection
// ---------------------------------------------------------------------------
describe("useTableSelection", () => {
  it("initialises with an empty selection when no config is provided", () => {
    const { result } = renderHook(() => useTableSelection());

    expect(result.current.selectedItems.size).toBe(0);
  });

  it("initialises with provided initialSelected set", () => {
    const initial = new Set(["a", "b"]);
    const { result } = renderHook(() => useTableSelection(undefined, initial));

    expect(result.current.selectedItems).toEqual(initial);
  });

  it("toggleSelection adds an item that is not selected", () => {
    const { result } = renderHook(() => useTableSelection());

    act(() => {
      result.current.toggleSelection("item-1");
    });

    expect(result.current.selectedItems.has("item-1")).toBe(true);
  });

  it("toggleSelection removes an item that is already selected", () => {
    const initial = new Set(["item-1"]);
    const { result } = renderHook(() => useTableSelection(undefined, initial));

    act(() => {
      result.current.toggleSelection("item-1");
    });

    expect(result.current.selectedItems.has("item-1")).toBe(false);
  });

  it("selectAll replaces the selection with all provided IDs", () => {
    const { result } = renderHook(() => useTableSelection());

    act(() => {
      result.current.selectAll(["a", "b", "c"]);
    });

    expect(result.current.selectedItems).toEqual(new Set(["a", "b", "c"]));
  });

  it("clearSelection empties the set", () => {
    const initial = new Set(["x", "y"]);
    const { result } = renderHook(() => useTableSelection(undefined, initial));

    act(() => {
      result.current.clearSelection();
    });

    expect(result.current.selectedItems.size).toBe(0);
  });

  it("isSelected returns true for a selected item and false otherwise", () => {
    const initial = new Set(["item-1"]);
    const { result } = renderHook(() => useTableSelection(undefined, initial));

    expect(result.current.isSelected("item-1")).toBe(true);
    expect(result.current.isSelected("item-2")).toBe(false);
  });

  it("calls onSelectionChange callback when selection changes", () => {
    const onSelectionChange = vi.fn();
    const config = {
      enabled: true,
      getItemId: (row: { id: string }) => row.id,
      onSelectionChange,
    };

    const { result } = renderHook(() => useTableSelection(config));

    act(() => {
      result.current.toggleSelection("item-1");
    });

    expect(onSelectionChange).toHaveBeenCalledOnce();
    expect(onSelectionChange).toHaveBeenCalledWith(new Set(["item-1"]));
  });
});

// ---------------------------------------------------------------------------
// useTableExpansion
// ---------------------------------------------------------------------------
describe("useTableExpansion", () => {
  it("initialises with an empty expansion set when no config is provided", () => {
    const { result } = renderHook(() => useTableExpansion());

    expect(result.current.expandedItems.size).toBe(0);
  });

  it("initialises with provided initialExpanded set", () => {
    const initial = new Set(["row-1"]);
    const { result } = renderHook(() => useTableExpansion(undefined, initial));

    expect(result.current.expandedItems).toEqual(initial);
  });

  it("toggleExpansion expands a collapsed row", () => {
    const { result } = renderHook(() => useTableExpansion());

    act(() => {
      result.current.toggleExpansion("row-1");
    });

    expect(result.current.expandedItems.has("row-1")).toBe(true);
  });

  it("toggleExpansion collapses an expanded row", () => {
    const initial = new Set(["row-1"]);
    const { result } = renderHook(() => useTableExpansion(undefined, initial));

    act(() => {
      result.current.toggleExpansion("row-1");
    });

    expect(result.current.expandedItems.has("row-1")).toBe(false);
  });

  it("isExpanded returns correct boolean for expanded and collapsed rows", () => {
    const initial = new Set(["row-1"]);
    const { result } = renderHook(() => useTableExpansion(undefined, initial));

    expect(result.current.isExpanded("row-1")).toBe(true);
    expect(result.current.isExpanded("row-2")).toBe(false);
  });

  it("calls onExpansionChange callback when expansion changes", () => {
    const onExpansionChange = vi.fn();
    const config = {
      enabled: true,
      getItemId: (row: { id: string }) => row.id,
      onExpansionChange,
    };

    const { result } = renderHook(() => useTableExpansion(config));

    act(() => {
      result.current.toggleExpansion("row-1");
    });

    expect(onExpansionChange).toHaveBeenCalledOnce();
  });
});

// ---------------------------------------------------------------------------
// useColumnVisibility
// ---------------------------------------------------------------------------
describe("useColumnVisibility", () => {
  const baseColumns = [
    { key: "name", header: "Name" },
    { key: "status", header: "Status" },
    { key: "budget", header: "Budget" },
  ];

  it("returns all columns when no hidden columns are provided", () => {
    const { result } = renderHook(() => useColumnVisibility(baseColumns, {}));

    expect(result.current.visibleColumns).toHaveLength(3);
  });

  it("excludes columns listed in hiddenColumns", () => {
    const { result } = renderHook(() =>
      useColumnVisibility(baseColumns, {}, ["status"]),
    );

    const keys = result.current.visibleColumns.map((c) => c.key);
    expect(keys).not.toContain("status");
    expect(keys).toContain("name");
    expect(keys).toContain("budget");
  });

  it("excludes columns with visible: false", () => {
    const columns = [
      { key: "name", header: "Name", visible: true },
      { key: "secret", header: "Secret", visible: false },
    ];

    const { result } = renderHook(() => useColumnVisibility(columns, {}));

    const keys = result.current.visibleColumns.map((c) => c.key);
    expect(keys).not.toContain("secret");
    expect(keys).toContain("name");
  });

  it("includes columns when visible is a function returning true", () => {
    const columns = [
      { key: "name", header: "Name" },
      { key: "admin", header: "Admin", visible: (_ctx: unknown) => true },
    ];

    const { result } = renderHook(() => useColumnVisibility(columns, {}));

    const keys = result.current.visibleColumns.map((c) => c.key);
    expect(keys).toContain("admin");
  });

  it("excludes columns when visible is a function returning false", () => {
    const columns = [
      { key: "name", header: "Name" },
      { key: "admin", header: "Admin", visible: (_ctx: unknown) => false },
    ];

    const { result } = renderHook(() => useColumnVisibility(columns, {}));

    const keys = result.current.visibleColumns.map((c) => c.key);
    expect(keys).not.toContain("admin");
  });

  it("hiddenColumns takes precedence over visible: true", () => {
    const columns = [{ key: "name", header: "Name", visible: true }];

    const { result } = renderHook(() =>
      useColumnVisibility(columns, {}, ["name"]),
    );

    expect(result.current.visibleColumns).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// useTableSorting
// ---------------------------------------------------------------------------
describe("useTableSorting", () => {
  it("returns empty sort state when no config or persistence is provided", () => {
    const { result } = renderHook(() => useTableSorting());

    expect(result.current.sortState).toEqual([]);
  });

  it("returns defaultSort from config when no storage value exists", () => {
    mockStorage.getItem.mockReturnValue(null);

    const config = {
      defaultSort: [{ key: "name", direction: "asc" as const }],
    };
    const persistence = { enabled: true, storageKey: "test-table" };

    const { result } = renderHook(() => useTableSorting(config, persistence));

    expect(result.current.sortState).toEqual([
      { key: "name", direction: "asc" },
    ]);
  });

  it("loads sortState from storage when persistence is enabled", () => {
    const stored = JSON.stringify({
      sortState: [{ key: "status", direction: "desc" }],
    });
    mockStorage.getItem.mockReturnValue(stored);

    const persistence = { enabled: true, storageKey: "test-table" };

    const { result } = renderHook(() =>
      useTableSorting(undefined, persistence),
    );

    expect(result.current.sortState).toEqual([
      { key: "status", direction: "desc" },
    ]);
  });

  it("saves updated sort state to storage", () => {
    mockStorage.getItem.mockReturnValue(null);

    const persistence = { enabled: true, storageKey: "test-table" };

    const { result } = renderHook(() =>
      useTableSorting(undefined, persistence),
    );

    act(() => {
      result.current.setSortState([{ key: "budget", direction: "asc" }]);
    });

    expect(mockStorage.setItem).toHaveBeenCalled();
    const [key, value] = mockStorage.setItem.mock.calls[
      mockStorage.setItem.mock.calls.length - 1
    ] as [string, string];
    expect(key).toBe("test-table");
    const parsed = JSON.parse(value) as { sortState: unknown };
    expect(parsed.sortState).toEqual([{ key: "budget", direction: "asc" }]);
  });

  it("uses controlled sortState from config when provided", () => {
    const controlledSort = [{ key: "name", direction: "desc" as const }];
    const config = { sortState: controlledSort };

    const { result } = renderHook(() => useTableSorting(config));

    expect(result.current.sortState).toEqual(controlledSort);
  });

  it("does not access storage when persistence is disabled", () => {
    const persistence = { enabled: false, storageKey: "test-table" };

    renderHook(() => useTableSorting(undefined, persistence));

    expect(mockStorage.getItem).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// useTablePersistence
// ---------------------------------------------------------------------------
describe("useTablePersistence", () => {
  it("saves sortState and hiddenColumns to storage when enabled", () => {
    mockStorage.getItem.mockReturnValue(null);

    const config = { enabled: true, storageKey: "persist-table" };
    const sortState = [{ key: "name", direction: "asc" as const }];
    const hiddenColumns = ["budget"];

    renderHook(() => useTablePersistence(config, sortState, hiddenColumns));

    expect(mockStorage.setItem).toHaveBeenCalled();
    const [key, value] = mockStorage.setItem.mock.calls[0] as [string, string];
    expect(key).toBe("persist-table");

    const parsed = JSON.parse(value) as {
      sortState: unknown;
      hiddenColumns: unknown;
    };
    expect(parsed.sortState).toEqual(sortState);
    expect(parsed.hiddenColumns).toEqual(hiddenColumns);
  });

  it("merges with existing storage data instead of replacing it", () => {
    const existing = JSON.stringify({ otherProp: "preserved" });
    mockStorage.getItem.mockReturnValue(existing);

    const config = { enabled: true, storageKey: "persist-table" };

    renderHook(() => useTablePersistence(config, [], []));

    const [, value] = mockStorage.setItem.mock.calls[0] as [string, string];
    const parsed = JSON.parse(value) as { otherProp: unknown };
    expect(parsed.otherProp).toBe("preserved");
  });

  it("does not call storage when persistence is disabled", () => {
    const config = { enabled: false, storageKey: "persist-table" };

    renderHook(() => useTablePersistence(config, [], []));

    expect(mockStorage.setItem).not.toHaveBeenCalled();
  });

  it("does nothing when no config is provided", () => {
    renderHook(() => useTablePersistence(undefined, [], []));

    expect(mockStorage.setItem).not.toHaveBeenCalled();
  });
});
