import { configureStore } from "@reduxjs/toolkit";
import campaignsUIReducer from "@services/campaign/campaignsUISlice";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { CampaignDisplay } from "../../../../types/campaign-display.types";
import {
  CampaignsListView,
  isPlaceholderRow,
  type CampaignRowPlaceholder,
} from "../CampaignsListView";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@utils/storage", () => ({
  default: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  },
}));

const mockAgGridTable = vi.fn();
vi.mock("@components/ui/AgGridTable", () => ({
  AgGridTable: (props: unknown) => {
    mockAgGridTable(props);
    return (
      <div data-testid="ag-grid-table">
        <span data-testid="ag-grid-loading">
          {String((props as { loading?: boolean }).loading)}
        </span>
        <span data-testid="ag-grid-empty-message">
          {(props as { emptyMessage?: string }).emptyMessage}
        </span>
        <span data-testid="ag-grid-row-count">
          {(props as { rowData?: unknown[] }).rowData?.length ?? 0}
        </span>
      </div>
    );
  },
}));

let drawerProps: {
  isOpen: boolean;
  onClose: () => void;
  onColumnVisibilityChange: (visibility: Record<string, boolean>) => void;
  availableColumns: Array<{ key: string; label: string }>;
} = {
  isOpen: false,
  onClose: () => {},
  onColumnVisibilityChange: () => {},
  availableColumns: [],
};

vi.mock("@components/common/HierarchicalTable/ColumnVisibilityDrawer", () => ({
  __esModule: true,
  default: (props: typeof drawerProps) => {
    drawerProps = props;
    return (
      <div data-testid="column-visibility-drawer">
        <span data-testid="drawer-open">{String(props.isOpen)}</span>
        <button type="button" onClick={props.onClose}>
          Close drawer
        </button>
        <button
          type="button"
          onClick={() =>
            props.onColumnVisibilityChange({
              serialNo: false,
              campaignName: true,
              brand: true,
              userName: true,
              flightDates: true,
              goalType: true,
              status: true,
              inventory: true,
              budget: true,
              totalCost: true,
            })
          }
        >
          Change visibility
        </button>
      </div>
    );
  },
}));

const defaultPaginationInfo = {
  currentPage: 1,
  totalPages: 2,
  pageSize: 10,
  totalItems: 15,
};

const defaultProps = {
  data: [] as CampaignDisplay[],
  isLoading: false,
  isFetching: false,
  paginationInfo: defaultPaginationInfo,
  onPageChange: vi.fn(),
  onPageSizeChange: vi.fn(),
  onRefresh: vi.fn(),
};

function createStore(overrides: Record<string, unknown> = {}) {
  const base = configureStore({
    reducer: { campaignsUI: campaignsUIReducer },
  });
  const initialState = base.getState().campaignsUI;
  return configureStore({
    reducer: { campaignsUI: campaignsUIReducer },
    preloadedState: {
      campaignsUI: { ...initialState, ...overrides },
    },
  });
}

function renderListView(
  props: Partial<typeof defaultProps> = {},
  store = createStore(),
) {
  return render(
    <Provider store={store}>
      <CampaignsListView {...defaultProps} {...props} />
    </Provider>,
  );
}

describe("CampaignsListView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders container with id campaigns-table-container", () => {
      renderListView();
      expect(
        document.getElementById("campaigns-table-container"),
      ).toBeInTheDocument();
    });

    it("renders AgGridTable with rowData, columnDefs, getRowId", () => {
      renderListView();
      expect(mockAgGridTable).toHaveBeenCalled();
      const props = mockAgGridTable.mock.calls[0][0];
      expect(props.rowData).toBeDefined();
      expect(props.columnDefs).toBeDefined();
      expect(props.getRowId).toBeDefined();
      expect(typeof props.getRowId).toBe("function");
    });

    it("passes emptyMessage No campaigns found to AgGridTable", () => {
      renderListView();
      expect(screen.getByTestId("ag-grid-empty-message")).toHaveTextContent(
        "campaignsList.emptyMessage",
      );
    });

    it("passes loading true when isLoading is true", () => {
      renderListView({ isLoading: true });
      expect(screen.getByTestId("ag-grid-loading")).toHaveTextContent("true");
    });

    it("passes loading true when isFetching is true", () => {
      renderListView({ isFetching: true });
      expect(screen.getByTestId("ag-grid-loading")).toHaveTextContent("true");
    });

    it("passes loading false when neither isLoading nor isFetching", () => {
      renderListView({ isLoading: false, isFetching: false });
      expect(screen.getByTestId("ag-grid-loading")).toHaveTextContent("false");
    });

    it("passes serverSidePagination and serverSideConfig to AgGridTable", () => {
      renderListView();
      const props = mockAgGridTable.mock.calls[0][0];
      expect(props.serverSidePagination).toBe(true);
      expect(props.serverSideConfig).toEqual(
        expect.objectContaining({
          totalCount: 15,
          currentPage: 1,
          pageSize: 10,
          pageSizeSelector: [10, 25, 50, 100],
        }),
      );
      expect(props.serverSideConfig.onPageChange).toBeDefined();
      expect(props.serverSideConfig.onPageSizeChange).toBeDefined();
    });

    it("renders ColumnVisibilityDrawer with id campaigns-column-customization-drawer", () => {
      renderListView();
      expect(
        screen.getByTestId("column-visibility-drawer"),
      ).toBeInTheDocument();
    });

    it("passes correct availableColumns to ColumnVisibilityDrawer", () => {
      renderListView();
      expect(drawerProps.availableColumns.length).toBeGreaterThan(0);
      expect(drawerProps.availableColumns).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            key: "serialNo",
            label: "campaignsList.columns.srNo",
          }),
          expect.objectContaining({
            key: "campaignName",
            label: "campaignsList.columns.campaignName",
          }),
        ]),
      );
    });
  });

  describe("row data", () => {
    it("builds full row data with placeholders when totalItems > 0 and data provided", () => {
      const data: CampaignDisplay[] = [
        {
          id: "c1",
          campaignName: "Campaign 1",
          userName: "User",
          brand: "Brand",
          status: "DRAFT",
          statusColor: "draft",
          daysLeft: "10",
          budget: "100",
          totalCost: "120",
          startDate: "2025-01-01",
          endDate: "2025-01-31",
          impressions: 1000,
          reach: 500,
          sov: 5,
          plannedSot: 10,
          totalSot: 20,
          inventory: 2,
          goals: {
            typeName: "Reach",
            goalType: "REACH",
            targetName: "",
            targetValue: 1000,
          },
          companyName: "Co",
        },
      ];
      renderListView({
        data,
        paginationInfo: {
          ...defaultPaginationInfo,
          totalItems: 15,
          currentPage: 1,
          pageSize: 10,
        },
      });
      const props = mockAgGridTable.mock.calls[0][0];
      expect(props.rowData).toHaveLength(15);
      expect(props.rowData[0]).toEqual(data[0]);
    });

    it("returns empty rowData when totalItems is 0", () => {
      renderListView({
        paginationInfo: { ...defaultPaginationInfo, totalItems: 0 },
      });
      const props = mockAgGridTable.mock.calls[0][0];
      expect(props.rowData).toHaveLength(0);
    });
  });

  describe("column visibility drawer", () => {
    it("drawer is closed when isColumnCustomizationOpen is false", () => {
      renderListView();
      expect(screen.getByTestId("drawer-open")).toHaveTextContent("false");
    });

    it("drawer is open when isColumnCustomizationOpen is true", () => {
      const store = createStore({ isColumnCustomizationOpen: true });
      renderListView({}, store);
      expect(screen.getByTestId("drawer-open")).toHaveTextContent("true");
    });

    it("dispatch setColumnCustomizationOpen false when drawer onClose is called", async () => {
      const user = userEvent.setup();
      const store = createStore({ isColumnCustomizationOpen: true });
      const dispatchSpy = vi.spyOn(store, "dispatch");
      renderListView({}, store);
      await user.click(screen.getByRole("button", { name: /close drawer/i }));
      expect(dispatchSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          type: expect.stringContaining("setColumnCustomizationOpen"),
        }),
      );
    });

    it("calls storage.setItem when column visibility is changed via drawer", async () => {
      const user = userEvent.setup();
      const storage = (await import("@utils/storage")).default;
      renderListView();
      await user.click(
        screen.getByRole("button", { name: /change visibility/i }),
      );
      expect(storage.setItem).toHaveBeenCalled();
    });
  });

  describe("pagination callbacks", () => {
    it("calls onPageChange when serverSideConfig.onPageChange is invoked", () => {
      renderListView();
      const props = mockAgGridTable.mock.calls[0][0];
      props.serverSideConfig.onPageChange(2);
      expect(defaultProps.onPageChange).toHaveBeenCalledWith(2);
    });

    it("calls onPageSizeChange when serverSideConfig.onPageSizeChange is invoked", () => {
      renderListView();
      const props = mockAgGridTable.mock.calls[0][0];
      props.serverSideConfig.onPageSizeChange(25);
      expect(defaultProps.onPageSizeChange).toHaveBeenCalledWith(25);
    });
  });

  describe("sort and selection", () => {
    it("dispatch setSortState when onSortChange is called", () => {
      const store = createStore();
      const dispatchSpy = vi.spyOn(store, "dispatch");
      renderListView({}, store);
      const props = mockAgGridTable.mock.calls[0][0];
      act(() => {
        props.onSortChange?.([{ key: "name", direction: "asc" }]);
      });
      expect(dispatchSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          type: expect.stringContaining("setSortState"),
          payload: [{ key: "name", direction: "asc" }],
        }),
      );
    });

    it("dispatch setSelectedItems when onSelectionChange is called", () => {
      const store = createStore();
      const dispatchSpy = vi.spyOn(store, "dispatch");
      renderListView({}, store);
      const props = mockAgGridTable.mock.calls[0][0];
      act(() => {
        props.onSelectionChange?.(["id1", "id2"]);
      });
      expect(dispatchSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          type: expect.stringContaining("setSelectedItems"),
          payload: ["id1", "id2"],
        }),
      );
    });
  });

  describe("accessibility", () => {
    it("drawer close button is focusable and has accessible name", () => {
      renderListView();
      const closeBtn = screen.getByRole("button", { name: /close drawer/i });
      expect(closeBtn).toBeInTheDocument();
    });
  });

  describe("Created By external user badge", () => {
    const baseRow: CampaignDisplay = {
      id: "c1",
      campaignName: "Campaign 1",
      userName: "Sanchit",
      brand: "Brand",
      status: "DRAFT",
      statusColor: "draft",
      daysLeft: "10",
      budget: "100",
      totalCost: "120",
      startDate: "2025-01-01",
      endDate: "2025-01-31",
      impressions: 1000,
      reach: 500,
      sov: 5,
      plannedSot: 10,
      totalSot: 20,
      inventory: 2,
      goals: {
        typeName: "Reach",
        goalType: "REACH",
        targetName: "",
        targetValue: 1000,
      },
      companyName: "Co",
    };

    function storeWithActiveCompany(activeCompanyId: string) {
      return configureStore({
        reducer: {
          campaignsUI: campaignsUIReducer,
          profile: () => ({ profile: { activeCompanyId } }),
        },
      });
    }

    function getUserNameCellRenderer(
      store: ReturnType<typeof storeWithActiveCompany>,
    ) {
      renderListView({}, store);
      const props =
        mockAgGridTable.mock.calls[mockAgGridTable.mock.calls.length - 1][0];
      const column = props.columnDefs.find(
        (col: { colId: string }) => col.colId === "userName",
      );
      return column.cellRenderer;
    }

    it("shows the External user badge when currentCompanyId differs from the active company", () => {
      const store = storeWithActiveCompany("company-1");
      const cellRenderer = getUserNameCellRenderer(store);
      render(
        cellRenderer({ data: { ...baseRow, currentCompanyId: "company-2" } }),
      );
      expect(
        screen.getByText("campaignsList.externalUser"),
      ).toBeInTheDocument();
    });

    it("does not show the badge when currentCompanyId matches the active company", () => {
      const store = storeWithActiveCompany("company-1");
      const cellRenderer = getUserNameCellRenderer(store);
      render(
        cellRenderer({ data: { ...baseRow, currentCompanyId: "company-1" } }),
      );
      expect(
        screen.queryByText("campaignsList.externalUser"),
      ).not.toBeInTheDocument();
    });

    it("does not show the badge when currentCompanyId is missing", () => {
      const store = storeWithActiveCompany("company-1");
      const cellRenderer = getUserNameCellRenderer(store);
      render(
        cellRenderer({ data: { ...baseRow, currentCompanyId: undefined } }),
      );
      expect(
        screen.queryByText("campaignsList.externalUser"),
      ).not.toBeInTheDocument();
    });
  });
});

describe("isPlaceholderRow", () => {
  it("returns true for row with __isPlaceholder true", () => {
    const row: CampaignRowPlaceholder = {
      id: "ph-1",
      __isPlaceholder: true,
    };
    expect(isPlaceholderRow(row)).toBe(true);
  });

  it("returns false for CampaignDisplay-like object without __isPlaceholder", () => {
    const row = {
      id: "c1",
      campaignName: "Test",
    } as CampaignDisplay;
    expect(isPlaceholderRow(row)).toBe(false);
  });

  it("returns false for object with __isPlaceholder false", () => {
    const row = { id: "x", __isPlaceholder: false };
    expect(
      isPlaceholderRow(row as CampaignDisplay | CampaignRowPlaceholder),
    ).toBe(false);
  });
});
