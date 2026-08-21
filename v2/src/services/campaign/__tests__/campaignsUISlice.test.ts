import { describe, it, expect } from "vitest";

import {
  campaignsUISlice,
  setViewType,
  setSortState,
  setSearchQuery,
  setFilters,
  setPaginationPage,
  setPaginationPageSize,
  setVirtualScrollOffset,
  setVirtualScrollLimit,
  toggleItemSelection,
  setSelectedItems,
  clearSelection,
  setFilterModalOpen,
  setColumnCustomizationOpen,
  resetFilters,
  selectCampaignsUI,
  selectViewType,
  selectSortState,
  selectSearchQuery,
  selectFilters,
  selectPagination,
  selectVirtualScroll,
  selectSelectedItems,
  selectIsFilterModalOpen,
  selectIsColumnCustomizationOpen,
  CampaignsUIState,
} from "../campaignsUISlice";

const reducer = campaignsUISlice.reducer;

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 10;

/** Build a fresh default state without touching localStorage */
const defaultState: CampaignsUIState = {
  viewType: "list",
  sortState: [],
  searchQuery: "",
  filters: {
    status: [],
    userName: [],
    period: null,
    campaignGoal: [],
  },
  pagination: {
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE,
  },
  virtualScroll: {
    offset: 0,
    limit: DEFAULT_PAGE_SIZE,
  },
  selectedItems: [],
  isFilterModalOpen: false,
  isColumnCustomizationOpen: false,
};

/** Returns a clean copy so each test starts from the same baseline */
const fresh = (): CampaignsUIState => ({ ...defaultState });

describe("campaignsUISlice — initial state", () => {
  it("has viewType list by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.viewType).toBe("list");
  });

  it("has empty sortState by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.sortState).toEqual([]);
  });

  it("has empty searchQuery by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.searchQuery).toBe("");
  });

  it("has empty filters by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.filters).toEqual({
      status: [],
      userName: [],
      period: null,
      campaignGoal: [],
    });
  });

  it("has default pagination values", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
    expect(state.pagination.pageSize).toBe(DEFAULT_PAGE_SIZE);
  });

  it("has default virtualScroll values", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.virtualScroll.offset).toBe(0);
    expect(state.virtualScroll.limit).toBe(DEFAULT_PAGE_SIZE);
  });

  it("has empty selectedItems by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.selectedItems).toEqual([]);
  });

  it("has modal flags set to false by default", () => {
    const state = reducer(undefined, { type: "@@INIT" });
    expect(state.isFilterModalOpen).toBe(false);
    expect(state.isColumnCustomizationOpen).toBe(false);
  });
});

describe("setViewType", () => {
  it("switches viewType from list to grid", () => {
    const state = reducer(fresh(), setViewType("grid"));
    expect(state.viewType).toBe("grid");
  });

  it("switches viewType from grid to list", () => {
    const start: CampaignsUIState = { ...fresh(), viewType: "grid" };
    const state = reducer(start, setViewType("list"));
    expect(state.viewType).toBe("list");
  });

  it("does not alter other state when viewType is unchanged", () => {
    const state = reducer(fresh(), setViewType("list"));
    expect(state.searchQuery).toBe("");
    expect(state.selectedItems).toEqual([]);
  });

  it("converts virtual scroll offset to page number when switching to list", () => {
    // offset=20, limit=10 → page = floor(20/10) + 1 = 3
    const start: CampaignsUIState = {
      ...fresh(),
      viewType: "grid",
      virtualScroll: { offset: 20, limit: 10 },
    };
    const state = reducer(start, setViewType("list"));
    expect(state.pagination.page).toBe(3);
  });

  it("converts page number to virtual scroll offset when switching to grid", () => {
    // page=3, pageSize=10 → offset = (3-1)*10 = 20
    const start: CampaignsUIState = {
      ...fresh(),
      viewType: "list",
      pagination: { page: 3, pageSize: 10 },
    };
    const state = reducer(start, setViewType("grid"));
    expect(state.virtualScroll.offset).toBe(20);
  });

  it("synchronizes pageSize and limit when they differ (list→grid)", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      viewType: "list",
      pagination: { page: 1, pageSize: 20 },
      virtualScroll: { offset: 0, limit: 10 },
    };
    const state = reducer(start, setViewType("grid"));
    expect(state.virtualScroll.limit).toBe(20);
  });

  it("synchronizes limit and pageSize when they differ (grid→list)", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      viewType: "grid",
      virtualScroll: { offset: 0, limit: 20 },
      pagination: { page: 1, pageSize: 10 },
    };
    const state = reducer(start, setViewType("list"));
    expect(state.pagination.pageSize).toBe(20);
  });

  it("clamps page to DEFAULT_PAGE when offset is 0", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      viewType: "grid",
      virtualScroll: { offset: 0, limit: 10 },
    };
    const state = reducer(start, setViewType("list"));
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });
});

describe("setSortState", () => {
  it("sets a new sort state", () => {
    const sort = [{ key: "name", direction: "asc" as const }];
    const state = reducer(fresh(), setSortState(sort));
    expect(state.sortState).toEqual(sort);
  });

  it("resets pagination page to DEFAULT_PAGE on sort change", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      pagination: { page: 5, pageSize: 10 },
    };
    const state = reducer(
      start,
      setSortState([{ key: "status", direction: "desc" as const }]),
    );
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });

  it("resets virtualScroll offset to 0 on sort change", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      virtualScroll: { offset: 50, limit: 10 },
    };
    const state = reducer(start, setSortState([]));
    expect(state.virtualScroll.offset).toBe(0);
  });

  it("clears sort when given an empty array", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      sortState: [{ key: "name", direction: "asc" as const }],
    };
    const state = reducer(start, setSortState([]));
    expect(state.sortState).toEqual([]);
  });
});

describe("setSearchQuery", () => {
  it("stores the search query", () => {
    const state = reducer(fresh(), setSearchQuery("my campaign"));
    expect(state.searchQuery).toBe("my campaign");
  });

  it("resets pagination page to DEFAULT_PAGE", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      pagination: { page: 3, pageSize: 10 },
    };
    const state = reducer(start, setSearchQuery("test"));
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });

  it("resets virtualScroll offset to 0", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      virtualScroll: { offset: 30, limit: 10 },
    };
    const state = reducer(start, setSearchQuery("test"));
    expect(state.virtualScroll.offset).toBe(0);
  });

  it("stores an empty string when cleared", () => {
    const start: CampaignsUIState = { ...fresh(), searchQuery: "old query" };
    const state = reducer(start, setSearchQuery(""));
    expect(state.searchQuery).toBe("");
  });
});

describe("setFilters", () => {
  it("stores filter values", () => {
    const filters = {
      status: ["DRAFT", "PLANNED"],
      userName: ["alice"],
      period: null,
      campaignGoal: [],
    };
    const state = reducer(fresh(), setFilters(filters));
    expect(state.filters).toEqual(filters);
  });

  it("resets pagination page to DEFAULT_PAGE on filter change", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      pagination: { page: 4, pageSize: 10 },
    };
    const state = reducer(
      start,
      setFilters({ status: [], userName: [], period: null, campaignGoal: [] }),
    );
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });

  it("resets virtualScroll offset to 0 on filter change", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      virtualScroll: { offset: 40, limit: 10 },
    };
    const state = reducer(
      start,
      setFilters({ status: [], userName: [], period: null, campaignGoal: [] }),
    );
    expect(state.virtualScroll.offset).toBe(0);
  });

  it("stores a period filter with date objects", () => {
    const from = new Date("2026-01-01");
    const to = new Date("2026-06-30");
    const filters = {
      status: [],
      userName: [],
      period: { from, to },
      campaignGoal: [],
    };
    const state = reducer(fresh(), setFilters(filters));
    expect(state.filters.period?.from).toEqual(from);
    expect(state.filters.period?.to).toEqual(to);
  });
});

describe("setPaginationPage", () => {
  it("sets the pagination page", () => {
    const state = reducer(fresh(), setPaginationPage(3));
    expect(state.pagination.page).toBe(3);
  });

  it("does not alter pageSize", () => {
    const state = reducer(fresh(), setPaginationPage(2));
    expect(state.pagination.pageSize).toBe(DEFAULT_PAGE_SIZE);
  });
});

describe("setPaginationPageSize", () => {
  it("sets the page size", () => {
    const state = reducer(fresh(), setPaginationPageSize(25));
    expect(state.pagination.pageSize).toBe(25);
  });

  it("resets pagination page to DEFAULT_PAGE when page size changes", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      pagination: { page: 5, pageSize: 10 },
    };
    const state = reducer(start, setPaginationPageSize(25));
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });
});

describe("setVirtualScrollOffset", () => {
  it("sets the virtual scroll offset", () => {
    const state = reducer(fresh(), setVirtualScrollOffset(50));
    expect(state.virtualScroll.offset).toBe(50);
  });

  it("does not alter the limit", () => {
    const state = reducer(fresh(), setVirtualScrollOffset(100));
    expect(state.virtualScroll.limit).toBe(DEFAULT_PAGE_SIZE);
  });
});

describe("setVirtualScrollLimit", () => {
  it("sets the virtual scroll limit", () => {
    const state = reducer(fresh(), setVirtualScrollLimit(20));
    expect(state.virtualScroll.limit).toBe(20);
  });

  it("does not alter the offset", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      virtualScroll: { offset: 40, limit: 10 },
    };
    const state = reducer(start, setVirtualScrollLimit(20));
    expect(state.virtualScroll.offset).toBe(40);
  });
});

describe("toggleItemSelection", () => {
  it("adds an item that is not yet selected", () => {
    const state = reducer(fresh(), toggleItemSelection("camp-1"));
    expect(state.selectedItems).toContain("camp-1");
  });

  it("removes an item that is already selected", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      selectedItems: ["camp-1", "camp-2"],
    };
    const state = reducer(start, toggleItemSelection("camp-1"));
    expect(state.selectedItems).not.toContain("camp-1");
    expect(state.selectedItems).toContain("camp-2");
  });

  it("does not duplicate an item when toggled back off then on", () => {
    let state = reducer(fresh(), toggleItemSelection("camp-1"));
    state = reducer(state, toggleItemSelection("camp-1")); // remove
    state = reducer(state, toggleItemSelection("camp-1")); // add again
    expect(state.selectedItems.filter((id) => id === "camp-1")).toHaveLength(1);
  });
});

describe("setSelectedItems", () => {
  it("replaces selectedItems with the provided array", () => {
    const start: CampaignsUIState = { ...fresh(), selectedItems: ["camp-1"] };
    const state = reducer(start, setSelectedItems(["camp-2", "camp-3"]));
    expect(state.selectedItems).toEqual(["camp-2", "camp-3"]);
  });

  it("clears selectedItems when given an empty array", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      selectedItems: ["camp-1", "camp-2"],
    };
    const state = reducer(start, setSelectedItems([]));
    expect(state.selectedItems).toEqual([]);
  });
});

describe("clearSelection", () => {
  it("empties selectedItems", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      selectedItems: ["camp-1", "camp-2", "camp-3"],
    };
    const state = reducer(start, clearSelection());
    expect(state.selectedItems).toEqual([]);
  });

  it("is a no-op when selectedItems is already empty", () => {
    const state = reducer(fresh(), clearSelection());
    expect(state.selectedItems).toEqual([]);
  });
});

describe("setFilterModalOpen", () => {
  it("sets isFilterModalOpen to true", () => {
    const state = reducer(fresh(), setFilterModalOpen(true));
    expect(state.isFilterModalOpen).toBe(true);
  });

  it("sets isFilterModalOpen to false", () => {
    const start: CampaignsUIState = { ...fresh(), isFilterModalOpen: true };
    const state = reducer(start, setFilterModalOpen(false));
    expect(state.isFilterModalOpen).toBe(false);
  });

  it("does not affect isColumnCustomizationOpen", () => {
    const state = reducer(fresh(), setFilterModalOpen(true));
    expect(state.isColumnCustomizationOpen).toBe(false);
  });
});

describe("setColumnCustomizationOpen", () => {
  it("sets isColumnCustomizationOpen to true", () => {
    const state = reducer(fresh(), setColumnCustomizationOpen(true));
    expect(state.isColumnCustomizationOpen).toBe(true);
  });

  it("sets isColumnCustomizationOpen to false", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      isColumnCustomizationOpen: true,
    };
    const state = reducer(start, setColumnCustomizationOpen(false));
    expect(state.isColumnCustomizationOpen).toBe(false);
  });

  it("does not affect isFilterModalOpen", () => {
    const state = reducer(fresh(), setColumnCustomizationOpen(true));
    expect(state.isFilterModalOpen).toBe(false);
  });
});

describe("resetFilters", () => {
  it("clears all filter fields", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      filters: {
        status: ["DRAFT"],
        userName: ["alice"],
        period: null,
        campaignGoal: ["CPM"],
      },
    };
    const state = reducer(start, resetFilters());
    expect(state.filters).toEqual({
      status: [],
      userName: [],
      period: null,
      campaignGoal: [],
    });
  });

  it("clears the search query", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      searchQuery: "active campaigns",
    };
    const state = reducer(start, resetFilters());
    expect(state.searchQuery).toBe("");
  });

  it("clears the sort state", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      sortState: [{ key: "name", direction: "desc" as const }],
    };
    const state = reducer(start, resetFilters());
    expect(state.sortState).toEqual([]);
  });

  it("resets pagination page to DEFAULT_PAGE", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      pagination: { page: 7, pageSize: 10 },
    };
    const state = reducer(start, resetFilters());
    expect(state.pagination.page).toBe(DEFAULT_PAGE);
  });

  it("resets virtualScroll offset to 0", () => {
    const start: CampaignsUIState = {
      ...fresh(),
      virtualScroll: { offset: 60, limit: 10 },
    };
    const state = reducer(start, resetFilters());
    expect(state.virtualScroll.offset).toBe(0);
  });

  it("does not alter selectedItems", () => {
    const start: CampaignsUIState = { ...fresh(), selectedItems: ["camp-1"] };
    const state = reducer(start, resetFilters());
    expect(state.selectedItems).toEqual(["camp-1"]);
  });
});

describe("selectors", () => {
  const storeState = { campaignsUI: fresh() };

  it("selectCampaignsUI returns the full slice state", () => {
    expect(selectCampaignsUI(storeState)).toEqual(storeState.campaignsUI);
  });

  it("selectViewType returns viewType", () => {
    expect(selectViewType(storeState)).toBe("list");
  });

  it("selectSortState returns sortState", () => {
    expect(selectSortState(storeState)).toEqual([]);
  });

  it("selectSearchQuery returns searchQuery", () => {
    expect(selectSearchQuery(storeState)).toBe("");
  });

  it("selectFilters returns filters", () => {
    expect(selectFilters(storeState)).toEqual({
      status: [],
      userName: [],
      period: null,
      campaignGoal: [],
    });
  });

  it("selectPagination returns pagination", () => {
    expect(selectPagination(storeState)).toEqual({
      page: DEFAULT_PAGE,
      pageSize: DEFAULT_PAGE_SIZE,
    });
  });

  it("selectVirtualScroll returns virtualScroll", () => {
    expect(selectVirtualScroll(storeState)).toEqual({
      offset: 0,
      limit: DEFAULT_PAGE_SIZE,
    });
  });

  it("selectSelectedItems returns selectedItems", () => {
    expect(selectSelectedItems(storeState)).toEqual([]);
  });

  it("selectIsFilterModalOpen returns isFilterModalOpen", () => {
    expect(selectIsFilterModalOpen(storeState)).toBe(false);
  });

  it("selectIsColumnCustomizationOpen returns isColumnCustomizationOpen", () => {
    expect(selectIsColumnCustomizationOpen(storeState)).toBe(false);
  });

  it("selectors reflect updated state after an action", () => {
    const stateAfterAction = {
      campaignsUI: reducer(fresh(), setViewType("grid")),
    };
    expect(selectViewType(stateAfterAction)).toBe("grid");
  });
});
