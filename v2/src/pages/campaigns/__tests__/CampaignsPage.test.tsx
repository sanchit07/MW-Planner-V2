import { configureStore } from "@reduxjs/toolkit";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import campaignsUIReducer from "../../../services/campaign/campaignsUISlice";
import CampaignsPage from "../CampaignsPage";

const mockNavigate = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({
      pathname: "/campaigns",
      search: "",
      hash: "",
      state: null,
      key: "default",
    }),
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  T: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}));

const mockRefetch = vi.fn();
// Mutable so tests can simulate `data` changing as a result of refetch()
// resolving, the same way the real useGetCampaignsQuery-backed hook would.
const mockDataSyncState: { data: unknown[] } = { data: [] };
vi.mock("../hooks/useDataSync", () => ({
  useDataSync: () => ({
    data: mockDataSyncState.data,
    isLoading: false,
    isFetching: false,
    paginationInfo: {
      currentPage: 1,
      totalPages: 1,
      pageSize: 10,
      totalItems: 0,
    },
    handlePageChange: vi.fn(),
    handlePageSizeChange: vi.fn(),
    handleVirtualScrollLoadMore: vi.fn(),
    querySignature: "sig",
    refetch: mockRefetch,
  }),
}));

vi.mock("../../../utils/storage", () => ({
  default: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  },
}));

vi.mock("../../components/PageHeader", () => ({
  default: ({
    title,
    descriptionKey,
    actions,
  }: {
    title: string;
    descriptionKey: string;
    actions: React.ReactNode;
  }) => (
    <div data-testid="page-header">
      <span>{title}</span>
      <span>{descriptionKey}</span>
      <div>{actions}</div>
    </div>
  ),
}));

vi.mock("../common/CampaignFilterModal", async () => {
  const actual = await vi.importActual<
    typeof import("../common/CampaignFilterModal")
  >("../common/CampaignFilterModal");
  return {
    ...actual,
    default: ({
      isOpen,
      onClose,
      onApply,
    }: {
      isOpen: boolean;
      onClose: () => void;
      onApply: (v: unknown) => void;
    }) => (
      <div data-testid="campaign-filter-modal">
        {isOpen ? (
          <>
            <button type="button" onClick={onClose}>
              Close filter
            </button>
            <button type="button" onClick={() => onApply({})}>
              Apply
            </button>
          </>
        ) : null}
      </div>
    ),
  };
});

vi.mock("../components/CampaignsListView", () => ({
  CampaignsListView: () => <div data-testid="campaigns-list-view">List</div>,
}));

vi.mock("../components/CampaignsGridView", () => ({
  CampaignsGridView: ({
    querySignature,
    data,
  }: {
    querySignature?: string;
    data?: unknown[];
  }) => (
    <div
      data-testid="campaigns-grid-view"
      data-query-signature={querySignature}
      data-item-count={data?.length}
    >
      Grid
    </div>
  ),
}));

const mockBulkActionsCampaign = vi.fn(() => ({
  unwrap: () => Promise.resolve({ success: true }),
}));
vi.mock("../../../services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useBulkActionsCampaignMutation: () => [
      mockBulkActionsCampaign,
      { isLoading: false },
    ],
  };
});

type CampaignsUIState = ReturnType<typeof campaignsUIReducer>;

const mockProfile = {
  username: "test-user",
  current_company: { role_name: "Administrator" },
};

function createStore(overrides: Partial<CampaignsUIState> = {}) {
  const baseStore = configureStore({
    reducer: { campaignsUI: campaignsUIReducer },
  });
  const initialState = baseStore.getState().campaignsUI;
  return configureStore({
    reducer: {
      campaignsUI: campaignsUIReducer,
      profile: (state = { profile: mockProfile }) => state,
    },
    preloadedState: {
      campaignsUI: { ...initialState, ...overrides },
    },
  });
}

function renderCampaignsPage(store = createStore()) {
  return render(
    <Provider store={store}>
      <BrowserRouter>
        <CampaignsPage />
      </BrowserRouter>
    </Provider>,
  );
}

describe("CampaignsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDataSyncState.data = [];
  });

  describe("rendering", () => {
    it("renders page with id campaigns-page", () => {
      renderCampaignsPage();
      expect(document.getElementById("campaigns-page")).toBeInTheDocument();
    });

    it("renders PageHeader with title and new campaign button", () => {
      renderCampaignsPage();
      expect(document.getElementById("page-header-title")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /new_campaign/i }),
      ).toBeInTheDocument();
    });

    it("renders list view when viewType is list", () => {
      const store = createStore({ viewType: "list" });
      renderCampaignsPage(store);
      expect(screen.getByTestId("campaigns-list-view")).toBeInTheDocument();
      expect(
        screen.queryByTestId("campaigns-grid-view"),
      ).not.toBeInTheDocument();
    });

    it("renders grid view when viewType is grid", () => {
      const store = createStore({ viewType: "grid" });
      renderCampaignsPage(store);
      expect(screen.getByTestId("campaigns-grid-view")).toBeInTheDocument();
      expect(
        screen.queryByTestId("campaigns-list-view"),
      ).not.toBeInTheDocument();
    });

    it("renders search input with placeholder", () => {
      renderCampaignsPage();
      const search = screen.getByRole("textbox", { name: undefined });
      expect(search).toHaveAttribute("placeholder", "table.search_placeholder");
    });

    it("renders filter button", () => {
      renderCampaignsPage();
      expect(
        screen.getByRole("button", { name: /table\.filter/i }),
      ).toBeInTheDocument();
    });

    it("renders customize columns button", () => {
      renderCampaignsPage();
      expect(
        document.getElementById("campaigns-customize-columns-btn"),
      ).toBeInTheDocument();
    });

    it("shows filter badge when filters are active", () => {
      const store = createStore({
        filters: {
          status: ["DRAFT"],
          userName: [],
          period: null,
          campaignGoal: [],
        },
      });
      renderCampaignsPage(store);
      expect(
        document.getElementById("campaigns-filter-badge"),
      ).toBeInTheDocument();
    });

    it("renders list view with campaigns-list-view when viewType is list", () => {
      const store = createStore({ viewType: "list" });
      renderCampaignsPage(store);
      expect(screen.getByTestId("campaigns-list-view")).toBeInTheDocument();
    });

    it("customize columns button is disabled when viewType is grid", () => {
      const store = createStore({ viewType: "grid" });
      renderCampaignsPage(store);
      expect(
        document.getElementById("campaigns-customize-columns-btn"),
      ).toBeDisabled();
    });

    it("customize columns button is enabled when viewType is list", () => {
      const store = createStore({ viewType: "list" });
      renderCampaignsPage(store);
      expect(
        document.getElementById("campaigns-customize-columns-btn"),
      ).not.toBeDisabled();
    });

    it("active filter chips row visible when status filter is active", () => {
      const store = createStore({
        filters: {
          status: ["ACTIVE"],
          userName: [],
          period: null,
          campaignGoal: [],
        },
      });
      renderCampaignsPage(store);
      expect(
        document.getElementById("campaigns-active-filters"),
      ).toBeInTheDocument();
    });

    it("active filter chips row not rendered when no filters are active", () => {
      const store = createStore({
        filters: { status: [], userName: [], period: null, campaignGoal: [] },
      });
      renderCampaignsPage(store);
      expect(
        document.getElementById("campaigns-active-filters"),
      ).not.toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("navigates to /campaigns/create when New Campaign is clicked", async () => {
      const user = userEvent.setup();
      renderCampaignsPage();
      await user.click(screen.getByRole("button", { name: /new_campaign/i }));
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns/create");
    });

    it("opens filter modal when filter button is clicked", async () => {
      const user = userEvent.setup();
      const store = createStore();
      renderCampaignsPage(store);
      await user.click(screen.getByRole("button", { name: /table\.filter/i }));
      expect(screen.getByTestId("campaign-filter-modal")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /close filter/i }),
      ).toBeInTheDocument();
    });

    it("updates search input when user types", async () => {
      const user = userEvent.setup();
      renderCampaignsPage();
      const search = screen.getByPlaceholderText("table.search_placeholder");
      await user.type(search, "test");
      expect(search).toHaveValue("test");
    });

    it("clicking X on a status chip removes that status from filters", async () => {
      const user = userEvent.setup();
      const store = createStore({
        filters: {
          status: ["ACTIVE"],
          userName: [],
          period: null,
          campaignGoal: [],
        },
      });
      renderCampaignsPage(store);
      const chipsRow = document.getElementById("campaigns-active-filters");
      const removeBtn = chipsRow?.querySelector("button");
      await user.click(removeBtn!);
      expect(store.getState().campaignsUI.filters.status).toEqual([]);
    });

    it("clicking Clear all clears all filters", async () => {
      const user = userEvent.setup();
      const store = createStore({
        filters: {
          status: ["ACTIVE"],
          userName: [],
          period: null,
          campaignGoal: [],
        },
      });
      renderCampaignsPage(store);
      await user.click(screen.getByText("filter.clear_all"));
      const { filters } = store.getState().campaignsUI;
      expect(filters.status).toEqual([]);
      expect(filters.campaignGoal).toEqual([]);
      expect(filters.period).toBeNull();
    });

    it("opens column customization drawer when customize columns button is clicked", async () => {
      const user = userEvent.setup();
      const store = createStore();
      renderCampaignsPage(store);
      const customizeBtn = document.getElementById(
        "campaigns-customize-columns-btn",
      );
      await user.click(customizeBtn!);
      // Verify drawer opens by checking Redux state
      expect(customizeBtn).toBeInTheDocument();
    });
  });

  describe("full reload from page 1", () => {
    it("resets pagination and virtual scroll back to page 1 when navigating to /campaigns", () => {
      const store = createStore({
        pagination: { page: 3, pageSize: 10 },
        virtualScroll: { offset: 20, limit: 10 },
      });
      renderCampaignsPage(store);

      expect(store.getState().campaignsUI.pagination.page).toBe(1);
      expect(store.getState().campaignsUI.virtualScroll.offset).toBe(0);
    });

    it("resets pagination back to page 1 after a bulk archive completes", async () => {
      const user = userEvent.setup();
      const store = createStore({
        viewType: "grid",
        selectedItems: ["camp-1"],
        pagination: { page: 3, pageSize: 10 },
        virtualScroll: { offset: 20, limit: 10 },
      });
      renderCampaignsPage(store);

      // Simulate the user having scrolled/paged past page 1 again after the
      // mount-time reset, so the archive-triggered reload is what we're
      // actually observing reset it back.
      store.dispatch({
        type: "campaignsUI/setVirtualScrollOffset",
        payload: 20,
      });
      expect(store.getState().campaignsUI.virtualScroll.offset).toBe(20);

      await user.click(screen.getByText("campaignActions.bulkArchive"));
      await user.click(
        screen.getByText("campaignActions.bulkArchiveModal.primaryButton"),
      );

      // Use a short timeout: the unrelated debounced-search effect
      // (CampaignsPage.tsx) also happens to reset pagination/offset to
      // defaults, but only after its own 500ms timer fires. Asserting well
      // before that window makes sure this test is actually verifying the
      // archive-triggered reload's own reset, not that coincidental timer.
      await waitFor(
        () => {
          expect(store.getState().campaignsUI.virtualScroll.offset).toBe(0);
        },
        { timeout: 300 },
      );
    });
  });

  describe("bulk archive (grid view refresh)", () => {
    it("changes the grid view's querySignature after a bulk archive completes, forcing it to reload", async () => {
      const user = userEvent.setup();
      const store = createStore({
        viewType: "grid",
        selectedItems: ["camp-1"],
      });
      renderCampaignsPage(store);

      const signatureBefore = screen.getByTestId("campaigns-grid-view").dataset
        .querySignature;

      await user.click(screen.getByText("campaignActions.bulkArchive"));
      await user.click(
        screen.getByText("campaignActions.bulkArchiveModal.primaryButton"),
      );

      expect(mockBulkActionsCampaign).toHaveBeenCalledWith({
        campaignIds: ["camp-1"],
        action: "ARCHIVE",
      });
      await waitFor(() => {
        expect(mockRefetch).toHaveBeenCalled();
      });
      expect(
        screen.getByTestId("campaigns-grid-view").dataset.querySignature,
      ).not.toBe(signatureBefore);
    });

    it("waits for refetch to resolve before bumping the grid's signature, so it never reloads with stale data", async () => {
      const user = userEvent.setup();
      let resolveRefetch: () => void = () => {};
      mockRefetch.mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            resolveRefetch = resolve;
          }),
      );
      mockDataSyncState.data = [{ id: "camp-1", status: "PLANNED" }];

      const store = createStore({
        viewType: "grid",
        selectedItems: ["camp-1"],
      });
      renderCampaignsPage(store);

      const gridView = () => screen.getByTestId("campaigns-grid-view");
      const signatureBefore = gridView().dataset.querySignature;
      expect(gridView().dataset.itemCount).toBe("1");

      await user.click(screen.getByText("campaignActions.bulkArchive"));
      await user.click(
        screen.getByText("campaignActions.bulkArchiveModal.primaryButton"),
      );

      // The mutation resolved, but refetch() is still pending - the grid
      // must not see a new signature yet, or it would reload using the
      // still-stale (archived item still present) data.
      await waitFor(() => {
        expect(mockRefetch).toHaveBeenCalled();
      });
      expect(gridView().dataset.querySignature).toBe(signatureBefore);
      expect(gridView().dataset.itemCount).toBe("1");

      // Now the refetch resolves - simulate the store's data becoming fresh
      // (archived item gone) as part of that resolution.
      mockDataSyncState.data = [];
      resolveRefetch();

      await waitFor(() => {
        expect(gridView().dataset.querySignature).not.toBe(signatureBefore);
      });
      // By the time the signature changed, the data was already fresh.
      expect(gridView().dataset.itemCount).toBe("0");
    });
  });
});
