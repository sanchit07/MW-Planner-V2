import { ColumnSort } from "@components/ui/Table";
import { DEFAULT_PAGE, DEFAULT_PAGE_SIZE } from "@constants/campaign.constants";
import { FilterValues } from "@pages/campaigns/common/CampaignFilterModal";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import storage from "@utils/storage";

import { ViewType } from "../../types/campaign-display.types";

const CAMPAIGNS_UI_STORAGE_KEY = "campaigns_ui_state";

export interface CampaignsUIState {
  // View type
  viewType: ViewType;
  // Sorting
  sortState: ColumnSort[];
  // Search
  searchQuery: string;
  // Filters
  filters: FilterValues;
  // Pagination (for list view)
  pagination: {
    page: number;
    pageSize: number;
  };
  // Virtual scroll (for grid view)
  virtualScroll: {
    offset: number;
    limit: number;
  };
  // Selection
  selectedItems: string[];
  // UI flags
  isFilterModalOpen: boolean;
  isColumnCustomizationOpen: boolean;
}

const getInitialState = (): CampaignsUIState => {
  try {
    const stored = storage.getItem(CAMPAIGNS_UI_STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      // Validate and restore state
      return {
        viewType:
          parsed.viewType === "list" || parsed.viewType === "grid"
            ? parsed.viewType
            : "list",
        sortState: Array.isArray(parsed.sortState) ? parsed.sortState : [],
        searchQuery:
          typeof parsed.searchQuery === "string" ? parsed.searchQuery : "",
        filters: parsed.filters
          ? {
              status: Array.isArray(parsed.filters.status)
                ? parsed.filters.status
                : [],
              userName: Array.isArray(parsed.filters.userName)
                ? parsed.filters.userName
                : [],
              period: parsed.filters.period
                ? {
                    from: parsed.filters.period.from
                      ? new Date(parsed.filters.period.from)
                      : null,
                    to: parsed.filters.period.to
                      ? new Date(parsed.filters.period.to)
                      : null,
                  }
                : null,
              campaignGoal: Array.isArray(parsed.filters.campaignGoal)
                ? parsed.filters.campaignGoal
                : [],
            }
          : {
              status: [],
              userName: [],
              period: null,
              campaignGoal: [],
            },
        pagination: parsed.pagination
          ? {
              page:
                typeof parsed.pagination.page === "number" &&
                parsed.pagination.page > 0
                  ? parsed.pagination.page
                  : DEFAULT_PAGE,
              pageSize:
                typeof parsed.pagination.pageSize === "number" &&
                parsed.pagination.pageSize > 0
                  ? parsed.pagination.pageSize
                  : DEFAULT_PAGE_SIZE,
            }
          : {
              page: DEFAULT_PAGE,
              pageSize: DEFAULT_PAGE_SIZE,
            },
        virtualScroll: parsed.virtualScroll
          ? {
              offset:
                typeof parsed.virtualScroll.offset === "number" &&
                parsed.virtualScroll.offset >= 0
                  ? parsed.virtualScroll.offset
                  : 0,
              limit:
                typeof parsed.virtualScroll.limit === "number" &&
                parsed.virtualScroll.limit > 0
                  ? parsed.virtualScroll.limit
                  : DEFAULT_PAGE_SIZE,
            }
          : {
              offset: 0,
              limit: DEFAULT_PAGE_SIZE,
            },
        selectedItems: Array.isArray(parsed.selectedItems)
          ? parsed.selectedItems
          : [],
        isFilterModalOpen: false,
        isColumnCustomizationOpen: false,
      };
    }
  } catch (error) {
    console.error("Error loading campaigns UI state from storage:", error);
  }

  // Default state
  return {
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
};

const initialState: CampaignsUIState = getInitialState();

// Helper to persist state to localStorage
const persistState = (state: CampaignsUIState) => {
  try {
    storage.setItem(CAMPAIGNS_UI_STORAGE_KEY, JSON.stringify(state));
  } catch (error) {
    console.error("Error persisting campaigns UI state to storage:", error);
  }
};

export const campaignsUISlice = createSlice({
  name: "campaignsUI",
  initialState,
  reducers: {
    setViewType: (state, action: PayloadAction<ViewType>) => {
      const previousViewType = state.viewType;
      state.viewType = action.payload;

      // Preserve pagination state when switching views
      if (previousViewType !== action.payload) {
        if (action.payload === "list") {
          // Switching to list view: convert virtual scroll offset to page number
          const currentPage =
            Math.floor(state.virtualScroll.offset / state.virtualScroll.limit) +
            1;
          state.pagination.page = Math.max(DEFAULT_PAGE, currentPage);
          if (state.pagination.pageSize !== state.virtualScroll.limit) {
            state.pagination.pageSize = state.virtualScroll.limit;
          }
        } else {
          // Switching to grid view: convert page number to virtual scroll offset
          const currentOffset =
            (state.pagination.page - 1) * state.pagination.pageSize;
          state.virtualScroll.offset = Math.max(0, currentOffset);
          if (state.virtualScroll.limit !== state.pagination.pageSize) {
            state.virtualScroll.limit = state.pagination.pageSize;
          }
        }
      }
      persistState(state);
    },
    setSortState: (state, action: PayloadAction<ColumnSort[]>) => {
      state.sortState = action.payload;
      // Reset pagination on sort change
      state.pagination.page = DEFAULT_PAGE;
      state.virtualScroll.offset = 0;
      persistState(state);
    },
    setSearchQuery: (state, action: PayloadAction<string>) => {
      state.searchQuery = action.payload;
      // Reset pagination on search change
      state.pagination.page = DEFAULT_PAGE;
      state.virtualScroll.offset = 0;
      persistState(state);
    },
    setFilters: (state, action: PayloadAction<FilterValues>) => {
      state.filters = action.payload;
      // Reset pagination on filter change
      state.pagination.page = DEFAULT_PAGE;
      state.virtualScroll.offset = 0;
      persistState(state);
    },
    setPaginationPage: (state, action: PayloadAction<number>) => {
      state.pagination.page = action.payload;
      persistState(state);
    },
    setPaginationPageSize: (state, action: PayloadAction<number>) => {
      state.pagination.pageSize = action.payload;
      state.pagination.page = DEFAULT_PAGE; // Reset to first page
      persistState(state);
    },
    setVirtualScrollOffset: (state, action: PayloadAction<number>) => {
      state.virtualScroll.offset = action.payload;
      persistState(state);
    },
    setVirtualScrollLimit: (state, action: PayloadAction<number>) => {
      state.virtualScroll.limit = action.payload;
      persistState(state);
    },
    toggleItemSelection: (state, action: PayloadAction<string>) => {
      const index = state.selectedItems.indexOf(action.payload);
      if (index >= 0) {
        state.selectedItems.splice(index, 1);
      } else {
        state.selectedItems.push(action.payload);
      }
      persistState(state);
    },
    setSelectedItems: (state, action: PayloadAction<string[]>) => {
      state.selectedItems = action.payload;
      persistState(state);
    },
    clearSelection: (state) => {
      state.selectedItems = [];
      persistState(state);
    },
    setFilterModalOpen: (state, action: PayloadAction<boolean>) => {
      state.isFilterModalOpen = action.payload;
    },
    setColumnCustomizationOpen: (state, action: PayloadAction<boolean>) => {
      state.isColumnCustomizationOpen = action.payload;
    },
    resetFilters: (state) => {
      state.filters = {
        status: [],
        userName: [],
        period: null,
        campaignGoal: [],
      };
      state.searchQuery = "";
      state.sortState = [];
      state.pagination.page = DEFAULT_PAGE;
      state.virtualScroll.offset = 0;
      persistState(state);
    },
  },
});

export const {
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
} = campaignsUISlice.actions;

// Selectors
export const selectCampaignsUI = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI;
export const selectViewType = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.viewType;
export const selectSortState = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.sortState;
export const selectSearchQuery = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.searchQuery;
export const selectFilters = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.filters;
export const selectPagination = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.pagination;
export const selectVirtualScroll = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.virtualScroll;
export const selectSelectedItems = (state: { campaignsUI: CampaignsUIState }) =>
  state.campaignsUI.selectedItems;
export const selectIsFilterModalOpen = (state: {
  campaignsUI: CampaignsUIState;
}) => state.campaignsUI.isFilterModalOpen;
export const selectIsColumnCustomizationOpen = (state: {
  campaignsUI: CampaignsUIState;
}) => state.campaignsUI.isColumnCustomizationOpen;

export default campaignsUISlice.reducer;
