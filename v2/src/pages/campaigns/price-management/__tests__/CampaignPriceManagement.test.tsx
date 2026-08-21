import { store } from "@store";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CampaignPriceManagement, {
  formatScheduleName,
} from "../CampaignPriceManagement";

const mockNavigate = vi.fn();
const { storageGet, storageSet, translationsLoading } = vi.hoisted(() => ({
  storageGet: vi.fn(),
  storageSet: vi.fn(),
  translationsLoading: { current: false },
}));

vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return {
    ...actual,
    useParams: () => ({ campaignId: "campaign-1" }),
    useNavigate: () => mockNavigate,
  };
});

const mockDispatch = vi.fn();
vi.mock("react-redux", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-redux")>();
  return {
    ...actual,
    useDispatch: () => mockDispatch,
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({
    t: (key: string, opts?: { defaultValue?: string }) =>
      opts?.defaultValue ?? key,
    isLoading: translationsLoading.current,
  }),
}));

const mockShowSuccess = vi.fn();
const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showSuccess: mockShowSuccess,
    showError: mockShowError,
  }),
}));

const mockFetchCampaignSchedulePrices = vi.fn();
const mockGetCampaign = vi.fn();
const mockAcceptAllPrices = vi.fn();
let mockCampaignData: unknown = undefined;
let mockPriceData: unknown = undefined;
let mockIsFetching = false;

vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/campaign/campaignSlice")>();
  return {
    ...actual,
    useLazyGetCampaignQuery: () => [
      mockGetCampaign,
      { data: mockCampaignData },
    ],
  };
});

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetCampaignSchedulePricesQuery: () => [
      mockFetchCampaignSchedulePrices,
      { isLoading: false, isFetching: mockIsFetching, data: mockPriceData },
    ],
    useAcceptAllPricesMutation: () => [
      mockAcceptAllPrices,
      { isLoading: false },
    ],
  };
});

vi.mock("@utils/storage", () => ({
  default: {
    getItem: (key: string) => storageGet(key),
    setItem: (key: string, value: string) => storageSet(key, value),
  },
}));

const FAKE_PROPOSED_PRICE_ROW = {
  id: "inv-1",
  proposedRate: 2200,
  campaignInventoryScheduleId: "cis-1",
};

vi.mock("@components/common/HierarchicalTable", () => ({
  HierarchicalTable: ({
    data,
    selection,
    skeletonRowsCount,
    loading,
    className,
    columns,
  }: {
    data: unknown[];
    selection?: { onSelectionChange: (s: Set<string>) => void };
    skeletonRowsCount?: number;
    loading?: boolean;
    className?: string;
    columns?: Array<{
      key: string;
      render?: (
        value: unknown,
        row: unknown,
        context: unknown,
      ) => React.ReactNode;
    }>;
  }) => (
    <div
      data-testid="hierarchical-table"
      data-loading={String(Boolean(loading))}
      className={className}
    >
      <span data-testid="table-row-count">{data?.length ?? 0}</span>
      <span data-testid="table-skeleton-rows-count">{skeletonRowsCount}</span>
      {/* Renders the real (unmocked) proposed-price cell for one fabricated
          row, so tests can drive an actual inline edit through the page. */}
      {columns
        ?.find((column) => column.key === "proposedRate")
        ?.render?.(
          FAKE_PROPOSED_PRICE_ROW.proposedRate,
          FAKE_PROPOSED_PRICE_ROW,
          {},
        )}
      {selection?.onSelectionChange && (
        <button
          type="button"
          data-testid="select-first-row"
          onClick={() => selection.onSelectionChange(new Set(["inv-1"]))}
        >
          Select first
        </button>
      )}
    </div>
  ),
}));

vi.mock("@components/PageHeader", () => ({
  default: ({
    title,
    description,
    leftAction,
  }: {
    title: string;
    description?: React.ReactNode;
    leftAction: React.ReactNode;
  }) => (
    <div data-testid="page-header">
      <span data-testid="page-title">{title}</span>
      <div data-testid="page-description">{description}</div>
      <div data-testid="left-action">{leftAction}</div>
    </div>
  ),
}));

vi.mock("../ApplyDiscountDrawer", () => ({
  ApplyDiscountDrawer: ({
    isOpen,
    onClose,
  }: {
    isOpen: boolean;
    onClose: () => void;
  }) =>
    isOpen ? (
      <div data-testid="apply-discount-drawer">
        <button type="button" onClick={onClose}>
          Close discount
        </button>
      </div>
    ) : null,
}));

vi.mock("../ApplyBonusDrawer", () => ({
  ApplyBonusDrawer: ({
    isOpen,
    onClose,
  }: {
    isOpen: boolean;
    onClose: () => void;
  }) =>
    isOpen ? (
      <div data-testid="apply-bonus-drawer">
        <button type="button" onClick={onClose}>
          Close bonus
        </button>
      </div>
    ) : null,
}));

vi.mock("../PriceManagementFiltersDrawer", () => ({
  PriceManagementFiltersDrawer: ({
    isOpen,
    onClose,
    onApplyFilters,
  }: {
    isOpen: boolean;
    onClose: () => void;
    onApplyFilters: (f: unknown) => void;
  }) =>
    isOpen ? (
      <div data-testid="filters-drawer">
        <button type="button" onClick={onClose}>
          Close filters
        </button>
        <button
          type="button"
          onClick={() =>
            onApplyFilters({
              cities: ["kl"],
              inventoryTypes: [],
              mediaOwners: [],
              minPricing: "",
              maxPricing: "",
            })
          }
        >
          Apply filters
        </button>
      </div>
    ) : null,
  PriceManagementFilters: {},
}));

vi.mock("../PricingSummaryDrawer", () => ({
  PricingSummaryDrawer: ({
    isOpen,
    onClose,
    pendingPriceEdits,
  }: {
    isOpen: boolean;
    onClose: () => void;
    pendingPriceEdits?: Record<string, unknown>;
  }) =>
    isOpen ? (
      <div data-testid="pricing-summary-drawer">
        <span data-testid="pending-price-edits-count">
          {Object.keys(pendingPriceEdits ?? {}).length}
        </span>
        <button type="button" onClick={onClose}>
          Close summary
        </button>
      </div>
    ) : null,
}));

vi.mock("../components/PriceHistoryDrawer", () => ({
  PriceHistoryDrawer: ({
    isOpen,
    campaignId,
  }: {
    isOpen: boolean;
    campaignId?: string;
  }) =>
    isOpen ? (
      <div data-testid="price-history-drawer" data-campaign-id={campaignId} />
    ) : null,
}));

vi.mock("../MapAvailabilityView", () => ({
  default: () => <div data-testid="map-availability-view">Map View</div>,
}));

vi.mock("../../components/CampaignActionsDropdownContent", () => ({
  CampaignActionsDropdownContent: () => (
    <div data-testid="campaign-actions-dropdown">Actions</div>
  ),
}));

function renderWithProviders(
  initialRoute = "/campaigns/price-management/campaign-1",
) {
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialRoute]} initialIndex={0}>
        <CampaignPriceManagement />
      </MemoryRouter>
    </Provider>,
  );
}

describe("CampaignPriceManagement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCampaignData = undefined;
    mockPriceData = undefined;
    mockIsFetching = false;
    translationsLoading.current = false;
    storageGet.mockReturnValue(null);
    mockGetCampaign.mockReturnValue(undefined);
    mockFetchCampaignSchedulePrices.mockReturnValue(undefined);
    mockAcceptAllPrices.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: { message: "Prices accepted" },
        }),
    });
  });

  describe("rendering", () => {
    it("renders page with campaign-price-management-page id", () => {
      renderWithProviders();
      expect(
        document.getElementById("campaign-price-management-page"),
      ).toBeInTheDocument();
    });

    it("shows a loading state instead of raw keys while translations are loading", () => {
      translationsLoading.current = true;
      renderWithProviders();

      // The page shell renders, but none of its labelled content does -
      // no raw i18n key strings should ever hit the screen
      expect(
        document.getElementById("campaign-price-management-page"),
      ).toBeInTheDocument();
      expect(screen.queryByTestId("page-header")).not.toBeInTheDocument();
      expect(
        screen.queryByText("table.columns.inventory_name"),
      ).not.toBeInTheDocument();
    });

    it("renders the full page once translations finish loading", () => {
      translationsLoading.current = false;
      renderWithProviders();

      expect(screen.getByTestId("page-header")).toBeInTheDocument();
    });

    it("renders page header", () => {
      renderWithProviders();
      expect(screen.getByTestId("page-header")).toBeInTheDocument();
    });

    it("renders campaigns toolbar with search and view toggle", () => {
      renderWithProviders();
      expect(document.getElementById("campaigns-toolbar")).toBeInTheDocument();
      expect(
        document.getElementById("campaigns-search-input"),
      ).toBeInTheDocument();
      expect(
        document.getElementById("campaigns-view-toggle"),
      ).toBeInTheDocument();
    });

    it("renders filter and summary buttons", () => {
      renderWithProviders();
      expect(
        screen.getByRole("button", { name: /actions\.filter/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /actions\.summary/i }),
      ).toBeInTheDocument();
    });

    it("renders hierarchical table when view is grid", () => {
      renderWithProviders();
      expect(screen.getByTestId("hierarchical-table")).toBeInTheDocument();
    });

    it("shows the plan number when campaign data has loaded", () => {
      mockCampaignData = { data: { id: "campaign-1", planNumber: "PLN-0001" } };
      renderWithProviders();
      expect(screen.getByTestId("page-description").textContent).toContain(
        "PLN-0001",
      );
    });

    it("does not render the ID label when planNumber is missing", () => {
      mockCampaignData = { data: { id: "campaign-1" } };
      renderWithProviders();
      expect(screen.getByTestId("page-description").textContent?.trim()).toBe(
        "",
      );
    });

    it("sizes the loading skeleton to the current page size, not a fixed count", () => {
      renderWithProviders();
      // Default page size is 10 - the skeleton should show 10 rows while
      // loading, not the component's own default of 5, so there's no layout
      // jump when the real (up to 10) rows arrive.
      expect(screen.getByTestId("table-skeleton-rows-count")).toHaveTextContent(
        "10",
      );
    });
  });

  describe("data loading", () => {
    it("fetches campaign and price data on mount when campaignId is in params", async () => {
      renderWithProviders();
      await waitFor(() => {
        expect(mockGetCampaign).toHaveBeenCalledWith("campaign-1");
      });
      await waitFor(() => {
        expect(mockFetchCampaignSchedulePrices).toHaveBeenCalledWith(
          expect.objectContaining({
            campaignId: "campaign-1",
            params: expect.objectContaining({
              page: 0,
              size: 10,
            }),
          }),
        );
      });
    });
  });

  describe("refetch loading behaviour", () => {
    it("shows the full skeleton on first load, before any rows exist", () => {
      mockPriceData = undefined;
      mockIsFetching = true;
      renderWithProviders();

      expect(screen.getByTestId("hierarchical-table")).toHaveAttribute(
        "data-loading",
        "true",
      );
      expect(
        screen.queryByRole("status", { name: /loading/i }),
      ).not.toBeInTheDocument();
    });

    it("dims existing rows with an overlay instead of blanking to a skeleton on refetch", () => {
      mockPriceData = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: 0,
        },
      };
      mockIsFetching = true;
      renderWithProviders();

      const table = screen.getByTestId("hierarchical-table");
      // Existing data means this is a refresh, not a first load - the table
      // itself must not switch into its own skeleton state...
      expect(table).toHaveAttribute("data-loading", "false");
      expect(table.className).toContain("opacity-50");
      expect(table.className).toContain("pointer-events-none");
      // ...and a small overlay spinner communicates the refresh instead
      expect(screen.getByRole("status")).toBeInTheDocument();
    });

    it("shows neither the skeleton nor the overlay once a refetch settles", () => {
      mockPriceData = {
        data: {
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: 0,
        },
      };
      mockIsFetching = false;
      renderWithProviders();

      const table = screen.getByTestId("hierarchical-table");
      expect(table).toHaveAttribute("data-loading", "false");
      expect(table.className).not.toContain("opacity-50");
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    it("disables the toolbar while a fetch is in flight", () => {
      mockIsFetching = true;
      renderWithProviders();

      expect(document.getElementById("campaigns-search-input")).toBeDisabled();
      expect(document.getElementById("campaigns-filter-btn")).toBeDisabled();
    });

    it("keeps the toolbar enabled once the fetch settles", () => {
      mockIsFetching = false;
      renderWithProviders();

      expect(document.getElementById("campaigns-search-input")).toBeEnabled();
      expect(document.getElementById("campaigns-filter-btn")).toBeEnabled();
    });
  });

  describe("navigation", () => {
    it("navigates to /campaigns when back button is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const leftActionWrapper = screen.getByTestId("left-action");
      const backButton =
        leftActionWrapper.firstElementChild ?? leftActionWrapper;
      await user.click(backButton as HTMLElement);
      expect(mockNavigate).toHaveBeenCalledWith("/campaigns");
    });
  });

  const clickBackButton = async (user: ReturnType<typeof userEvent.setup>) => {
    const leftActionWrapper = screen.getByTestId("left-action");
    const backButton = leftActionWrapper.firstElementChild ?? leftActionWrapper;
    await user.click(backButton as HTMLElement);
  };

  describe("info carousel", () => {
    it("shows info content when no items selected", () => {
      renderWithProviders();
      expect(
        screen.getByText("info.select_multiple_inventories.title"),
      ).toBeInTheDocument();
    });

    it("shows carousel index and prev/next controls", () => {
      renderWithProviders();
      expect(screen.getByText("1/4")).toBeInTheDocument();
    });

    it("cycles to next info when next chevron is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      expect(screen.getByText("1/4")).toBeInTheDocument();

      await user.click(screen.getByTestId("button-carousel-next"));

      await waitFor(() => {
        expect(screen.getByText("2/4")).toBeInTheDocument();
      });
    });

    it("cycles to the previous info when the prev chevron is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await user.click(screen.getByTestId("button-carousel-next"));
      await waitFor(() => {
        expect(screen.getByText("2/4")).toBeInTheDocument();
      });

      await user.click(screen.getByTestId("button-carousel-prev"));
      await waitFor(() => {
        expect(screen.getByText("1/4")).toBeInTheDocument();
      });
    });

    it("jumps straight to a tip when its dot is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await user.click(screen.getByTestId("button-carousel-dot-3"));

      await waitFor(() => {
        expect(screen.getByText("3/4")).toBeInTheDocument();
      });
    });
  });

  describe("search", () => {
    it("updates search input value when user types", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const searchInput = document.getElementById("campaigns-search-input");
      expect(searchInput).toBeInTheDocument();
      await user.type(searchInput as HTMLInputElement, "test");
      expect((searchInput as HTMLInputElement).value).toBe("test");
    });

    it("does not call the API while typing", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      mockFetchCampaignSchedulePrices.mockClear();

      const searchInput = document.getElementById("campaigns-search-input");
      await user.type(searchInput as HTMLInputElement, "test");

      expect(mockFetchCampaignSchedulePrices).not.toHaveBeenCalled();
    });

    it("calls the API with the search term when Enter is pressed", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      const searchInput = document.getElementById("campaigns-search-input");
      await user.type(searchInput as HTMLInputElement, "billboard");
      mockFetchCampaignSchedulePrices.mockClear();
      await user.keyboard("{Enter}");

      expect(mockFetchCampaignSchedulePrices).toHaveBeenCalledWith(
        expect.objectContaining({
          params: expect.objectContaining({
            page: 0,
            filters: expect.objectContaining({ name: "billboard" }),
          }),
        }),
      );
    });
  });

  describe("view toggle", () => {
    it("switches to map view when map button is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const mapBtn = screen.getByRole("button", {
        name: /actions\.map/i,
      });
      await user.click(mapBtn);
      expect(screen.getByTestId("map-availability-view")).toBeInTheDocument();
    });

    it("switches to calendar view when calendar button is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const calendarBtn = screen.getByRole("button", {
        name: /actions\.calendar/i,
      });
      await user.click(calendarBtn);
      expect(screen.getByTestId("map-availability-view")).toBeInTheDocument();
    });

    it("shows table when grid view button is clicked after map", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(screen.getByRole("button", { name: /actions\.map/i }));
      await user.click(screen.getByRole("button", { name: /actions\.view/i }));
      expect(screen.getByTestId("hierarchical-table")).toBeInTheDocument();
    });
  });

  describe("drawers", () => {
    it("opens filters drawer when filter button is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(
        screen.getByRole("button", { name: /actions\.filter/i }),
      );
      expect(screen.getByTestId("filters-drawer")).toBeInTheDocument();
    });

    it("closes filters drawer when close is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(
        screen.getByRole("button", { name: /actions\.filter/i }),
      );
      await user.click(screen.getByRole("button", { name: /Close filters/i }));
      expect(screen.queryByTestId("filters-drawer")).not.toBeInTheDocument();
    });

    it("opens the price history drawer from the toolbar", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      expect(
        screen.queryByTestId("price-history-drawer"),
      ).not.toBeInTheDocument();

      await user.click(screen.getByTestId("button-price-history"));

      const drawer = screen.getByTestId("price-history-drawer");
      expect(drawer).toBeInTheDocument();
      expect(drawer).toHaveAttribute("data-campaign-id", "campaign-1");
    });

    it("opens pricing summary drawer when summary button is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(
        screen.getByRole("button", { name: /actions\.summary/i }),
      );
      expect(screen.getByTestId("pricing-summary-drawer")).toBeInTheDocument();
    });

    it("opens discount drawer when apply discount is clicked with selection", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const selectBtn = screen.getByTestId("select-first-row");
      await user.click(selectBtn);
      await user.click(
        screen.getByRole("button", { name: /actions\.apply_discount/i }),
      );
      expect(screen.getByTestId("apply-discount-drawer")).toBeInTheDocument();
    });

    it("opens bonus drawer when apply bonus is clicked with selection", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const selectBtn = screen.getByTestId("select-first-row");
      await user.click(selectBtn);
      await user.click(
        screen.getByRole("button", { name: /actions\.apply_bonus/i }),
      );
      expect(screen.getByTestId("apply-bonus-drawer")).toBeInTheDocument();
    });
  });

  describe("selection actions", () => {
    it("shows action bar with clear all when items are selected", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const selectBtn = screen.getByTestId("select-first-row");
      await user.click(selectBtn);
      expect(
        screen.getByRole("button", { name: /actions\.clear_all/i }),
      ).toBeInTheDocument();
    });

    it("switches the banner from info blue to purple once something is selected", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      const banner = () =>
        document
          .getElementById("campaign-price-management-page")
          ?.querySelector('[class*="bg-mw-info-50"], [class*="bg-mw-purple"]');

      expect(banner()?.className).toContain("bg-mw-info-50");

      await user.click(screen.getByTestId("select-first-row"));

      expect(banner()?.className).toContain("bg-mw-purple-warning-50");
      expect(banner()?.className).toContain("border-mw-purple-warning-200");
    });

    it("renders the selection actions with the reference test ids", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(screen.getByTestId("select-first-row"));

      expect(
        screen.getByTestId("button-clear-selection-action"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("button-apply-discount-action"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("button-apply-bonus-action"),
      ).toBeInTheDocument();
      // The Accept flow was removed - prices now save from the Summary drawer
      expect(
        screen.queryByTestId("button-accept-all-action"),
      ).not.toBeInTheDocument();
    });

    it("clears selection when clear all is clicked", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      const selectBtn = screen.getByTestId("select-first-row");
      await user.click(selectBtn);
      await user.click(
        screen.getByRole("button", { name: /actions\.clear_all/i }),
      );
      expect(
        screen.getByText("info.select_multiple_inventories.title"),
      ).toBeInTheDocument();
    });
  });

  describe("pending price edits", () => {
    const stageAnEdit = async (user: ReturnType<typeof userEvent.setup>) => {
      await user.click(screen.getByRole("button", { name: /2,200/ }));
      const priceInput = screen.getByRole("textbox", {
        name: "table.columns.proposed_price",
      });
      await user.clear(priceInput);
      await user.type(priceInput, "1800{Enter}");
    };

    it("blocks apply discount and apply bonus while an edit is staged", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(screen.getByTestId("select-first-row"));

      await stageAnEdit(user);

      expect(
        screen.getByRole("button", { name: /actions\.apply_discount/i }),
      ).toBeDisabled();
      expect(
        screen.getByRole("button", { name: /actions\.apply_bonus/i }),
      ).toBeDisabled();
    });

    it("keeps clear all enabled while an edit is staged", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(screen.getByTestId("select-first-row"));

      await stageAnEdit(user);

      expect(
        screen.getByRole("button", { name: /actions\.clear_all/i }),
      ).toBeEnabled();
    });

    it("passes staged edits through to the summary drawer", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await stageAnEdit(user);

      await user.click(
        screen.getByRole("button", { name: /actions\.summary/i }),
      );

      expect(screen.getByTestId("pending-price-edits-count")).toHaveTextContent(
        "1",
      );
    });

    it("shows a leave-confirmation instead of navigating when back is clicked with staged edits", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await stageAnEdit(user);
      await clickBackButton(user);

      expect(
        screen.getByText("drawers.pricing_summary.discard_changes_title"),
      ).toBeInTheDocument();
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it("discards the staged edits and navigates away once leaving is confirmed", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await stageAnEdit(user);
      await clickBackButton(user);

      const confirmModal = document.getElementById(
        "modal-drawers.pricing_summary.discard_changes_title",
      );
      await user.click(
        within(confirmModal as HTMLElement).getByRole("button", {
          name: /drawers\.pricing_summary\.discard_changes_confirm/i,
        }),
      );

      expect(mockNavigate).toHaveBeenCalledWith("/campaigns");
    });

    it("keeps the staged edits and stays on the page when leaving is cancelled", async () => {
      const user = userEvent.setup();
      renderWithProviders();

      await stageAnEdit(user);
      await clickBackButton(user);

      const confirmModal = document.getElementById(
        "modal-drawers.pricing_summary.discard_changes_title",
      );
      await user.click(
        within(confirmModal as HTMLElement).getByRole("button", {
          name: /buttons\.cancel/i,
        }),
      );

      expect(mockNavigate).not.toHaveBeenCalled();
      expect(
        screen.queryByText("drawers.pricing_summary.discard_changes_title"),
      ).not.toBeInTheDocument();

      await user.click(
        screen.getByRole("button", { name: /actions\.summary/i }),
      );
      expect(screen.getByTestId("pending-price-edits-count")).toHaveTextContent(
        "1",
      );
    });
  });

  describe("filters", () => {
    it("applies filters when apply is clicked in filters drawer", async () => {
      const user = userEvent.setup();
      renderWithProviders();
      await user.click(
        screen.getByRole("button", { name: /actions\.filter/i }),
      );
      await user.click(screen.getByRole("button", { name: /Apply filters/i }));
      await waitFor(() => {
        expect(mockFetchCampaignSchedulePrices).toHaveBeenCalled();
      });
    });
  });

  describe("formatScheduleName", () => {
    it("rewrites the default API name to the #n form", () => {
      expect(formatScheduleName("Schedule 1", 0)).toBe("Schedule #1");
      expect(formatScheduleName("schedule 12", 3)).toBe("Schedule #12");
    });

    it("keeps a name that is already in the #n form", () => {
      expect(formatScheduleName("Schedule #4", 0)).toBe("Schedule #4");
    });

    it("leaves a user-provided name untouched", () => {
      expect(formatScheduleName("Morning drive time", 0)).toBe(
        "Morning drive time",
      );
    });

    it("falls back to the row index when there is no name", () => {
      expect(formatScheduleName(undefined, 2)).toBe("Schedule #3");
      expect(formatScheduleName("", 0)).toBe("Schedule #1");
    });
  });

  describe("storage", () => {
    it("loads hidden columns from storage when available", () => {
      storageGet.mockImplementation((key: string) => {
        if (key === "campaign-price-management-columns") {
          return JSON.stringify({ hiddenColumns: ["mediaOwner", "discount"] });
        }
        return null;
      });
      renderWithProviders();
      expect(storageGet).toHaveBeenCalledWith(
        "campaign-price-management-columns",
      );
    });
  });
});
