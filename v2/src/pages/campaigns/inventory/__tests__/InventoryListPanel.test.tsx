import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import InventoryListPanel, {
  type InventoryListPanelRef,
} from "../InventoryListPanel";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => (key: string) => key,
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

vi.mock("@hooks/useMediaOwnerIds", () => ({
  useMediaOwnerIds: () => [],
}));

const mockFetchInventoryList = vi.fn();
const mockFetchSelectedInventory = vi.fn(
  (): { unwrap: () => Promise<unknown> } => ({
    unwrap: () =>
      Promise.resolve({ success: true, data: { content: [], last: true } }),
  }),
);
vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetInventoryListQuery: () => [
    mockFetchInventoryList,
    { isLoading: false },
  ],
  useLazyGetSelectedInventoryQuery: () => [
    mockFetchSelectedInventory,
    { isLoading: false },
  ],
  useGetVenuesQuery: () => ({ data: venuesHoisted.data }),
}));

const venuesHoisted = vi.hoisted(() => ({
  data: [] as Array<{
    enumerationId: number;
    stringValue: string;
    children?: unknown[];
  }>,
}));

vi.mock("@hooks/useInfiniteScroll", () => ({
  useInfiniteScroll: () => ({ current: null }),
}));

vi.mock("../InventorySkeleton", () => ({
  default: ({ count }: { count: number }) => (
    <div data-testid="inventory-skeleton">Skeleton {count}</div>
  ),
}));

vi.mock("@components/common/InventoryDetailCard", () => ({
  InventoryDetailCard: ({
    item,
    onViewDetails,
    onCheckboxChange,
    goalType,
  }: {
    item: { detail: { id: string; externalId: string; isSelected: boolean } };
    onViewDetails: () => void;
    onCheckboxChange?: (checked: boolean) => void;
    goalType?: string;
  }) => (
    <div data-testid="inventory-detail-card">
      <span>{item.detail.id}</span>
      {goalType && <span data-testid="goal-type">{goalType}</span>}
      <button type="button" onClick={onViewDetails}>
        View Details
      </button>
      {onCheckboxChange && (
        <input
          type="checkbox"
          checked={item.detail.isSelected}
          onChange={(e) => onCheckboxChange(e.target.checked)}
          data-testid={`checkbox-${item.detail.id}`}
        />
      )}
    </div>
  ),
}));

const defaultFilters = {
  searchbyquery: "",
  mediaOwners: [],
  sizes: [],
  venueTypes: [],
  bookingMode: [],
  latitude: "",
  longitude: "",
  environments: [],
  inventoryClassification: [],
  programmaticSupport: "ALL" as const,
  dealTypes: [],
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mockInventoryItem: any = {
  detail: {
    id: "inv-1",
    externalId: "ext-1",
    name: "Inventory 1",
    isSelected: false,
    isCompliant: true,
    referenceId: "ref-1",
    mediaOwnerId: "owner-1",
    mediaOwnerName: "Owner Name",
    inventoryType: "digital",
    width: 800,
    height: 600,
    orientation: "landscape",
    format: "display",
    illumination: "day",
    drivingDirection: "north",
    category: "billboard",
    venueType: "outdoor",
    thumbnail: "https://example.com/thumbnail.jpg",
    images: [],
    description: "Test inventory",
    location: "Test location",
    market: "Test market",
    rate: 1000,
    currency: "USD",
    environment: "indoor",
    size: "large",
    operationMode: "automatic",
    execution: "standard",
    visibility: "high",
  },
  location: {},
  performance: {},
  operations: {},
  schedules: [],
} as unknown as typeof mockInventoryItem;

const defaultProps = {
  campaignId: "campaign-1",
  campaignData: null,
  inventoryFilters: defaultFilters,
  filtersLoadedFromStorage: true,
  isUploadDrawerOpen: false,
  groupBy: "all",
  setGroupBy: vi.fn(),
  getSelectAllState: () => ({ checked: false, indeterminate: false }),
  handleSelectAll: vi.fn(),
  isSelecting: false,
  totalElements: 0,
  forecastData: null,
  tCampaigns: (key: string) => key,
  inventoryItems: [] as (typeof mockInventoryItem)[],
  handleViewDetails: vi.fn(),
  campaignCurrency: "USD",
  formatCurrency: (value: number) => `$${value}`,
  handleItemSelection: vi.fn(),
  sovValues: {} as Record<string, number>,
  onInventoryLoaded: vi.fn(),
};

describe("InventoryListPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    venuesHoisted.data = [];
    mockFetchInventoryList.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          success: true,
          data: {
            content: [],
            totalElements: 0,
            last: true,
          },
        }),
    });
  });

  describe("rendering", () => {
    it("renders select all checkbox and hides the non-functional group by control", () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          ref={React.createRef()}
        />,
      );
      expect(
        screen.getByText("inventoryPageForm.selectAll"),
      ).toBeInTheDocument();
      // Group By is commented out (PL3-I2) until real grouping is built.
      expect(
        screen.queryByText("inventories.group_by"),
      ).not.toBeInTheDocument();
    });

    // The count badge is gated on the first /filter load completing, so these
    // await the load (filtersLoadedFromStorage=true) before asserting the count.
    it("renders badge with forecast and total count", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          totalElements={10}
          forecastData={{ totalInventories: 3 } as never}
          ref={React.createRef()}
        />,
      );
      expect(await screen.findByText("3/10")).toBeInTheDocument();
    });

    it("renders badge as totalElements/totalElements when all inventories are selected (SELECT ALL)", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          totalElements={10}
          forecastData={{ totalInventories: 10 } as never}
          ref={React.createRef()}
        />,
      );
      expect(await screen.findByText("10/10")).toBeInTheDocument();
    });

    it("renders badge as 0/totalElements when no inventories are selected (DESELECT ALL)", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          totalElements={10}
          forecastData={{ totalInventories: 0 } as never}
          ref={React.createRef()}
        />,
      );
      expect(await screen.findByText("0/10")).toBeInTheDocument();
    });

    it("renders badge as 0/0 when there are no inventories at all", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          totalElements={0}
          forecastData={null}
          ref={React.createRef()}
        />,
      );
      expect(await screen.findByText("0/0")).toBeInTheDocument();
    });

    it("shows a loading indicator in the count badge until the first load completes", () => {
      // filtersLoadedFromStorage=false → initial load never fires, so the count
      // stays gated and the badge must not show the stale 0/0.
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          totalElements={0}
          forecastData={null}
          ref={React.createRef()}
        />,
      );
      expect(screen.queryByText("0/0")).not.toBeInTheDocument();
    });

    it("renders inventory items when provided", () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          inventoryItems={[mockInventoryItem as never]}
          ref={React.createRef()}
        />,
      );
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
      expect(screen.getByText("inv-1")).toBeInTheDocument();
    });

    it("passes goalType from campaignData.goals.goalType to InventoryDetailCard", () => {
      const campaignData = {
        goals: { goalType: "SOV" },
      } as never;
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          campaignData={campaignData}
          inventoryItems={[mockInventoryItem as never]}
          ref={React.createRef()}
        />,
      );
      expect(screen.getByTestId("goal-type")).toHaveTextContent("SOV");
    });

    it("does not render goalType when campaignData has no goals", () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          campaignData={null}
          inventoryItems={[mockInventoryItem as never]}
          ref={React.createRef()}
        />,
      );
      expect(screen.queryByTestId("goal-type")).not.toBeInTheDocument();
    });

    it("renders empty state when no items and not loading", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          ref={React.createRef()}
        />,
      );
      await waitFor(
        () => {
          expect(
            screen.getByText("inventories.list.empty"),
          ).toBeInTheDocument();
        },
        { timeout: 2000 },
      );
      expect(
        screen.getByText("inventories.list.emptyHint"),
      ).toBeInTheDocument();
    });

    it("renders error state when fetch fails", async () => {
      mockFetchInventoryList.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          ref={React.createRef()}
        />,
      );
      await waitFor(
        () => {
          expect(
            screen.getByText("inventories.list.loadError"),
          ).toBeInTheDocument();
        },
        { timeout: 2000 },
      );
    });
  });

  describe("interactions", () => {
    it("calls handleViewDetails when View Details is clicked", async () => {
      const user = userEvent.setup();
      const handleViewDetails = vi.fn();
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          inventoryItems={[mockInventoryItem as never]}
          handleViewDetails={handleViewDetails}
          ref={React.createRef()}
        />,
      );
      await user.click(screen.getByRole("button", { name: "View Details" }));
      expect(handleViewDetails).toHaveBeenCalledWith("ext-1");
    });

    it("calls handleItemSelection when checkbox is changed", async () => {
      const user = userEvent.setup();
      const handleItemSelection = vi.fn();
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          inventoryItems={[mockInventoryItem as never]}
          handleItemSelection={handleItemSelection}
          ref={React.createRef()}
        />,
      );
      const checkbox = screen.getByTestId("checkbox-inv-1");
      await user.click(checkbox);
      expect(handleItemSelection).toHaveBeenCalledWith("inv-1", true);
    });

    it("calls handleSelectAll when select all checkbox is toggled", async () => {
      const user = userEvent.setup();
      const handleSelectAll = vi.fn();
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          totalElements={5}
          handleSelectAll={handleSelectAll}
          getSelectAllState={() => ({ checked: false, indeterminate: false })}
          ref={React.createRef()}
        />,
      );
      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);
      expect(handleSelectAll).toHaveBeenCalledWith(true);
    });

    it("disables select all when totalElements is 0", () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          totalElements={0}
          isSelecting={false}
          ref={React.createRef()}
        />,
      );
      const checkbox = screen.getByRole("checkbox");
      expect(checkbox).toBeDisabled();
    });
  });

  describe("ref.reload", () => {
    it("exposes reload and calls fetch with search override", async () => {
      const ref = React.createRef<InventoryListPanelRef>();
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={false}
          ref={ref}
        />,
      );
      expect(ref.current).not.toBeNull();
      mockFetchInventoryList.mockClear();
      await act(async () => {
        await ref.current!.reload("search-term");
      });
      expect(mockFetchInventoryList).toHaveBeenCalledWith(
        expect.objectContaining({
          campaignId: "campaign-1",
          params: expect.objectContaining({ name: "search-term" }),
        }),
      );
    });
  });

  describe("campaign date filters", () => {
    it("includes startDate and endDate in fetch params when campaignData provides them", async () => {
      const campaignData = {
        startDate: "2025-01-01",
        endDate: "2025-03-31",
      } as never;

      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          campaignData={campaignData}
          ref={React.createRef()}
        />,
      );

      await waitFor(
        () => {
          expect(mockFetchInventoryList).toHaveBeenCalledWith(
            expect.objectContaining({
              params: expect.objectContaining({
                startDate: "2025-01-01",
                endDate: "2025-03-31",
              }),
            }),
          );
        },
        { timeout: 2000 },
      );
    });

    it("omits startDate and endDate when campaignData has no dates", async () => {
      const campaignData = {
        countryId: "MY",
      } as never;

      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          campaignData={campaignData}
          ref={React.createRef()}
        />,
      );

      await waitFor(
        () => {
          expect(mockFetchInventoryList).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );

      const calledParams = mockFetchInventoryList.mock.calls[0][0].params;
      expect(calledParams).not.toHaveProperty("startDate");
      expect(calledParams).not.toHaveProperty("endDate");
    });

    it("omits startDate and endDate when campaignData is null", async () => {
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          campaignData={null}
          ref={React.createRef()}
        />,
      );

      await waitFor(
        () => {
          expect(mockFetchInventoryList).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );

      const calledParams = mockFetchInventoryList.mock.calls[0][0].params;
      expect(calledParams).not.toHaveProperty("startDate");
      expect(calledParams).not.toHaveProperty("endDate");
    });
  });

  describe("inventory cluster filter", () => {
    it("includes inventoryCluster from targeting.inventoryCluster", async () => {
      const campaignData = {
        targeting: {
          inventoryCluster: ["DIGITAL", "DIGITAL_NETWORK", "CLASSIC_TRANSIT"],
        },
      } as never;

      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          campaignData={campaignData}
          ref={React.createRef()}
        />,
      );

      await waitFor(
        () => {
          expect(mockFetchInventoryList).toHaveBeenCalledWith(
            expect.objectContaining({
              params: expect.objectContaining({
                inventoryCluster: [
                  "DIGITAL",
                  "DIGITAL_NETWORK",
                  "CLASSIC_TRANSIT",
                ],
              }),
            }),
          );
        },
        { timeout: 2000 },
      );
    });

    it("omits inventoryCluster when no inventory clusters are selected", async () => {
      const campaignData = {
        targeting: {
          inventoryCluster: [],
        },
      } as never;

      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          campaignData={campaignData}
          ref={React.createRef()}
        />,
      );

      await waitFor(
        () => {
          expect(mockFetchInventoryList).toHaveBeenCalled();
        },
        { timeout: 2000 },
      );

      const calledParams = mockFetchInventoryList.mock.calls[0][0].params;
      expect(calledParams).not.toHaveProperty("inventoryCluster");
    });
  });

  describe("onInventoryLoaded", () => {
    it("calls onInventoryLoaded when fetch succeeds", async () => {
      const onInventoryLoaded = vi.fn();
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          onInventoryLoaded={onInventoryLoaded}
          ref={React.createRef()}
        />,
      );
      await waitFor(
        () => {
          expect(onInventoryLoaded).toHaveBeenCalledWith(
            expect.objectContaining({
              content: [],
              totalElements: 0,
              last: true,
              append: false,
            }),
          );
        },
        { timeout: 2000 },
      );
    });
  });

  describe("venue type id filter", () => {
    it("sends venueTypeIdFilter (ids bucketed by classification) to /filter", async () => {
      venuesHoisted.data = [
        { enumerationId: 1, stringValue: "MALL" },
        { enumerationId: 3, stringValue: "AIRPORT" },
      ];
      render(
        <InventoryListPanel
          {...defaultProps}
          filtersLoadedFromStorage={true}
          inventoryFilters={{
            ...defaultFilters,
            venueTypes: ["MALL", "AIRPORT"],
            inventoryClassification: ["Digital"],
          }}
          ref={React.createRef()}
        />,
      );
      await waitFor(() =>
        expect(mockFetchInventoryList).toHaveBeenCalledWith(
          expect.objectContaining({
            params: expect.objectContaining({
              venueTypeIdFilter: { digitalOoh: ["1", "3"], classicOoh: [] },
            }),
          }),
        ),
      );
    });
  });

  describe("partial results on API failure", () => {
    const withRef = (detail: Record<string, unknown>) =>
      ({ detail }) as unknown as typeof mockInventoryItem;

    it("renders already-loaded items together with the error banner when /filter fails after /selected-inventory succeeds", async () => {
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [withRef({ id: "s1", referenceId: "ref-1" })],
              totalElements: 1,
              last: true,
            },
          }),
      });
      mockFetchInventoryList.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          inventoryItems={[mockInventoryItem]}
          ref={React.createRef()}
        />,
      );

      await waitFor(() =>
        expect(
          screen.getByText("inventories.list.loadError"),
        ).toBeInTheDocument(),
      );
      // Selected inventories stay visible despite the /filter failure.
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
    });

    it("keeps the error banner when /selected-inventory fails and /filter succeeds", async () => {
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      mockFetchInventoryList.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [withRef({ id: "f1", referenceId: "ref-2" })],
              totalElements: 1,
              last: true,
            },
          }),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          inventoryItems={[mockInventoryItem]}
          ref={React.createRef()}
        />,
      );

      // The chained /filter phase runs and succeeds...
      await waitFor(() => expect(mockFetchInventoryList).toHaveBeenCalled());
      // ...but the selected-phase error is not silently erased.
      await waitFor(() =>
        expect(
          screen.getByText("inventories.list.loadError"),
        ).toBeInTheDocument(),
      );
      // Browse results still render.
      expect(screen.getByTestId("inventory-detail-card")).toBeInTheDocument();
    });

    it("shows only the error banner (no empty state) when both APIs fail", async () => {
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      mockFetchInventoryList.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          inventoryItems={[]}
          ref={React.createRef()}
        />,
      );

      await waitFor(() =>
        expect(
          screen.getByText("inventories.list.loadError"),
        ).toBeInTheDocument(),
      );
      expect(
        screen.queryByText("inventories.list.empty"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId("inventory-detail-card"),
      ).not.toBeInTheDocument();
    });

    it("hides the empty state while /filter is still loading after /selected-inventory returned nothing", async () => {
      // /selected-inventory → empty (module default). /filter → never resolves,
      // so the second phase stays in flight (isLoadingMore).
      mockFetchInventoryList.mockReturnValue({
        unwrap: () => new Promise(() => {}),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          inventoryItems={[]}
          ref={React.createRef()}
        />,
      );

      // Once the /filter phase has started, the empty message must NOT show
      // even though the merged list is still empty — /filter is loading.
      await waitFor(() => expect(mockFetchInventoryList).toHaveBeenCalled());
      expect(
        screen.queryByText("inventories.list.empty"),
      ).not.toBeInTheDocument();
      // The load-more skeleton is what's shown instead.
      expect(
        screen.getAllByTestId("inventory-skeleton").length,
      ).toBeGreaterThan(0);
    });

    it("retries loading when the banner retry button is clicked and clears the error on success", async () => {
      const user = userEvent.setup();
      mockFetchInventoryList.mockReturnValueOnce({
        unwrap: () => Promise.reject(new Error("Network error")),
      });

      render(<InventoryListPanel {...defaultProps} ref={React.createRef()} />);

      await waitFor(() =>
        expect(
          screen.getByText("inventories.list.loadError"),
        ).toBeInTheDocument(),
      );

      // Default beforeEach mock (success) serves the retry.
      await user.click(
        screen.getByRole("button", { name: /inventories\.list\.retry/i }),
      );

      await waitFor(() =>
        expect(mockFetchInventoryList).toHaveBeenCalledTimes(2),
      );
      await waitFor(() =>
        expect(
          screen.queryByText("inventories.list.loadError"),
        ).not.toBeInTheDocument(),
      );
    });
  });

  describe("two-phase (selectedFirst)", () => {
    const withRef = (detail: Record<string, unknown>) =>
      ({ detail }) as unknown as typeof mockInventoryItem;

    it("loads /selected-inventory first (marked selected), then /filter deduped", async () => {
      const onInventoryLoaded = vi.fn();
      // Selected phase: one selected item (ref-1), single page.
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [withRef({ id: "s1", referenceId: "ref-1" })],
              totalElements: 1,
              last: true,
            },
          }),
      });
      // Filter phase returns ref-1 (dup) + ref-2; ref-1 must be excluded.
      mockFetchInventoryList.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [
                withRef({ id: "f1", referenceId: "ref-1" }),
                withRef({ id: "f2", referenceId: "ref-2" }),
              ],
              totalElements: 2,
              last: true,
            },
          }),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          filtersLoadedFromStorage={true}
          onInventoryLoaded={onInventoryLoaded}
          ref={React.createRef()}
        />,
      );

      // Selected phase fetched with page 0 / size 1000.
      await waitFor(() =>
        expect(mockFetchSelectedInventory).toHaveBeenCalledWith(
          expect.objectContaining({
            campaignId: "campaign-1",
            params: expect.objectContaining({ page: 0, size: 1000 }),
          }),
        ),
      );

      // First onInventoryLoaded = selected item, marked selected, not last.
      await waitFor(() =>
        expect(onInventoryLoaded).toHaveBeenCalledWith(
          expect.objectContaining({
            content: [
              expect.objectContaining({
                detail: expect.objectContaining({
                  referenceId: "ref-1",
                  isSelected: true,
                }),
              }),
            ],
            append: false,
            last: false,
          }),
        ),
      );

      // Then the /filter page, with ref-1 deduped out (only ref-2 remains).
      await waitFor(() =>
        expect(onInventoryLoaded).toHaveBeenCalledWith(
          expect.objectContaining({
            content: [
              expect.objectContaining({
                detail: expect.objectContaining({ referenceId: "ref-2" }),
              }),
            ],
            append: true,
            last: true,
          }),
        ),
      );
    });

    it("skips a fully-deduped /filter page instead of stalling", async () => {
      const onInventoryLoaded = vi.fn();
      // Selected: ref-1 (single page).
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [withRef({ id: "s1", referenceId: "ref-1" })],
              totalElements: 1,
              last: true,
            },
          }),
      });
      // /filter page 0 = only ref-1 (fully deduped → 0 visible, not last);
      // page 1 = ref-2 (last). The loader must advance to page 1 on its own.
      mockFetchInventoryList.mockImplementation(
        ({ params }: { params: { page: number } }) => ({
          unwrap: () =>
            Promise.resolve(
              params.page === 0
                ? {
                    success: true,
                    data: {
                      content: [withRef({ id: "f1", referenceId: "ref-1" })],
                      totalElements: 2,
                      last: false,
                    },
                  }
                : {
                    success: true,
                    data: {
                      content: [withRef({ id: "f2", referenceId: "ref-2" })],
                      totalElements: 2,
                      last: true,
                    },
                  },
            ),
        }),
      );

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          filtersLoadedFromStorage={true}
          onInventoryLoaded={onInventoryLoaded}
          ref={React.createRef()}
        />,
      );

      // Both /filter pages get requested without any scroll event.
      await waitFor(() =>
        expect(mockFetchInventoryList).toHaveBeenCalledWith(
          expect.objectContaining({
            params: expect.objectContaining({ page: 1 }),
          }),
        ),
      );
      // ref-2 (page 1) reaches the list; the fully-deduped page 0 did not stall.
      await waitFor(() =>
        expect(onInventoryLoaded).toHaveBeenCalledWith(
          expect.objectContaining({
            content: [
              expect.objectContaining({
                detail: expect.objectContaining({ referenceId: "ref-2" }),
              }),
            ],
            append: true,
            last: true,
          }),
        ),
      );
    });

    it("client-side filters the /selected-inventory response (hides non-matching)", async () => {
      const onInventoryLoaded = vi.fn();
      // Selected phase: one indoor + one outdoor item, single page.
      mockFetchSelectedInventory.mockReturnValue({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [
                withRef({
                  id: "s-indoor",
                  referenceId: "ref-in",
                  environment: "indoor",
                }),
                withRef({
                  id: "s-outdoor",
                  referenceId: "ref-out",
                  environment: "outdoor",
                }),
              ],
              totalElements: 2,
              last: true,
            },
          }),
      });

      render(
        <InventoryListPanel
          {...defaultProps}
          selectedFirst={true}
          filtersLoadedFromStorage={true}
          // "Indoor" (drawer case) must match response "indoor".
          inventoryFilters={{ ...defaultFilters, environments: ["Indoor"] }}
          onInventoryLoaded={onInventoryLoaded}
          ref={React.createRef()}
        />,
      );

      // Only the indoor item survives the client-side filter (marked selected).
      await waitFor(() =>
        expect(onInventoryLoaded).toHaveBeenCalledWith(
          expect.objectContaining({
            content: [
              expect.objectContaining({
                detail: expect.objectContaining({
                  referenceId: "ref-in",
                  isSelected: true,
                }),
              }),
            ],
            append: false,
            last: false,
          }),
        ),
      );

      // The hidden (outdoor) item is never emitted in any selected-phase payload.
      const emittedRefs = onInventoryLoaded.mock.calls
        .map((c) => c[0])
        .filter((p) => p.append === false)
        .flatMap((p) =>
          p.content.map(
            (i: { detail: { referenceId: string } }) => i.detail.referenceId,
          ),
        );
      expect(emittedRefs).not.toContain("ref-out");
    });
  });
});
