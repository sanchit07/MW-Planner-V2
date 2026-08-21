import { configureStore } from "@reduxjs/toolkit";
import campaignSlice from "@services/campaign/campaignSlice";
import stepperSlice, {
  type StepperState,
} from "@services/stepper/stepperSlice";
import userSlice, { type UserProfile } from "@services/user/userSlice";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import ManualSelectionPage from "../ManualSelectionPage";

// The mobility heatmap hook uses an RTK Query hook (campaignApi), which isn't
// part of this test store; stub the module.
vi.mock("@components/map/MobilityHeatmap", () => ({
  toCountrySlug: (name?: string) =>
    name ? name.toLowerCase().replace(/\s+/g, "-") : undefined,
  useMobilityHeatmapLayer: () => ({
    isLoading: false,
    isError: false,
    pointCount: 0,
    isEmpty: false,
  }),
  MobilityHeatmapControl: () => null,
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

const { showSuccess, showError } = vi.hoisted(() => ({
  showSuccess: vi.fn(),
  showError: vi.fn(),
}));
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError, showSuccess }),
}));

type ListPanelCapturedProps = {
  inventoryFilters?: {
    inventoryClassification?: string[];
    venueTypes?: string[];
  };
};
const listPanelProps = vi.hoisted(() => ({
  last: null as ListPanelCapturedProps | null,
}));
vi.mock("@pages/campaigns/inventory/InventoryListPanel", () => ({
  __esModule: true,
  default: React.forwardRef((props: ListPanelCapturedProps, _ref) => {
    listPanelProps.last = props;
    return React.createElement("div", { "data-testid": "list-panel" });
  }),
}));

vi.mock("@pages/campaigns/inventory/InventoryDetailsDrawer", () => ({
  default: () => null,
}));

type FilterDrawerCapturedProps = {
  mediaOwnerStaticOptions?: unknown;
  media_channels?: string[];
};
const filterDrawerProps = vi.hoisted(() => ({
  last: null as FilterDrawerCapturedProps | null,
}));
vi.mock("@pages/campaigns/inventory/InventoryFilterDrawer", () => ({
  default: (props: FilterDrawerCapturedProps) => {
    filterDrawerProps.last = props;
    return null;
  },
}));

vi.mock("@pages/campaigns/inventory/InventoryCsvUploadDrawer", () => ({
  default: () => null,
}));

vi.mock("@components/ui/Mapbox", () => ({
  __esModule: true,
  default: () => React.createElement("div", { "data-testid": "map" }),
}));

const bulkSelectInventoryByReferenceIds = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () =>
      Promise.resolve({ success: true, data: "2 inventories selected" }),
  }),
);

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useSelectInventoryMutation: () => [vi.fn(), {}],
    useBulkSelectInventoryMutation: () => [vi.fn(), {}],
    useBulkSelectInventoryByReferenceIdsMutation: () => [
      bulkSelectInventoryByReferenceIds,
      {},
    ],
    useGetVenuesQuery: () => ({ data: [] }),
    useBulkSelectByIdsMutation: () => [
      vi.fn(() => ({ unwrap: () => Promise.resolve({ success: true }) })),
      {},
    ],
    useLazyGetSelectedInventoryQuery: () => [
      vi.fn(() => ({
        unwrap: () =>
          Promise.resolve({
            success: true,
            data: { content: [], totalPages: 1, number: 0, totalElements: 0 },
          }),
      })),
      {},
    ],
  };
});

const initialStepper: StepperState = {
  steps: [],
  currentStepId: 4,
  totalSteps: 0,
  progress: 0,
  isLoading: false,
  isInitialized: false,
  isEditMode: false,
  editCampaignId: null,
  inventoryFilters: {
    searchbyquery: "",
    mediaOwners: [],
    sizes: [],
    venueTypes: [],
    bookingMode: [],
    latitude: "",
    longitude: "",
    environments: [],
    inventoryClassification: [],
    programmaticSupport: "ALL",
    dealTypes: [],
  },
};

// `ManualSelectionPage` kicks off an async `loadSelectedInventory()` fetch-loop
// on open (see the Step-4 open effect in ManualSelectionPage.tsx). Its state
// updates (setSelectionMap/setIsLoadingSelection) land a tick after render, so
// every render with `isOpen: true` must flush that pending promise chain
// inside `act(...)` before the test proceeds - otherwise React warns that an
// update wasn't wrapped in act().
async function renderPage(
  isOpen: boolean,
  profile?: Record<string, unknown>,
  mediaChannels?: string[],
  targeting?: Record<string, unknown>,
  presetVenueTypes?: string[],
  filtersOverride?: Record<string, unknown>,
) {
  const store = configureStore({
    reducer: {
      campaign: campaignSlice,
      stepper: stepperSlice,
      profile: userSlice,
    },
    preloadedState: {
      profile: { profile: (profile ?? null) as UserProfile | null },
      campaign: {
        currentCampaignName: "",
        campaignId: "c1",
        isCreating: false,
        createError: null,
        isEditMode: false,
        forecastData: null,
        recommendationRun: null,
        campaignData: {
          id: "c1",
          name: "",
          status: "",
          countryId: "US",
          currency: "USD",
          startDate: "",
          endDate: "",
          clientType: "",
          createdAt: "",
          updatedAt: "",
          inventoryCount: 0,
          ...(mediaChannels ? { mediaChannels } : {}),
          ...(targeting ? { targeting: targeting as never } : {}),
        },
      },
      stepper:
        presetVenueTypes || filtersOverride
          ? {
              ...initialStepper,
              inventoryFilters: {
                ...initialStepper.inventoryFilters,
                ...(presetVenueTypes ? { venueTypes: presetVenueTypes } : {}),
                ...(filtersOverride ?? {}),
              },
            }
          : initialStepper,
    },
  });
  const result = render(
    <Provider store={store}>
      <ManualSelectionPage isOpen={isOpen} onClose={vi.fn()} />
    </Provider>,
  );
  // Flush the `loadSelectedInventory()` promise chain (and any resulting
  // state updates) started by the Step-4 open effect.
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  return result;
}

describe("ManualSelectionPage", () => {
  it("renders nothing when closed", async () => {
    await renderPage(false);
    expect(
      screen.queryByText("inventories.manual.title"),
    ).not.toBeInTheDocument();
  });

  it("renders header, search and the inventory list when open", async () => {
    await renderPage(true);
    expect(screen.getByText("inventories.manual.title")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("inventoryMapView.searchPlaceholder"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("list-panel")).toBeInTheDocument();
  });

  it("shows the footer Save Selection and Cancel buttons", async () => {
    await renderPage(true);
    expect(
      await screen.findByRole("button", {
        name: "inventories.manual.footer.saveSelection",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "inventories.manual.footer.cancel",
      }),
    ).toBeInTheDocument();
  });

  describe("media-owner filter list", () => {
    it("passes owned + child companies as static options for a media owner", async () => {
      await renderPage(true, {
        current_company: {
          id: "owner-1",
          name: "Owner Co",
          company_type: { code: "MEDIA_OWNER" },
          childCompanies: {
            items: [
              { id: "child-1", name: "Child One" },
              { id: "child-2", name: "Child Two" },
            ],
          },
        },
      });

      expect(filterDrawerProps.last?.mediaOwnerStaticOptions).toEqual([
        { id: "owner-1", label: "Owner Co", value: "owner-1" },
        { id: "child-1", label: "Child One", value: "child-1" },
        { id: "child-2", label: "Child Two", value: "child-2" },
      ]);
    });

    it("leaves static options undefined for a non-media-owner (API flow)", async () => {
      await renderPage(true, {
        current_company: {
          id: "agency-1",
          name: "Agency Co",
          company_type: { code: "AGENCY" },
        },
      });

      expect(filterDrawerProps.last?.mediaOwnerStaticOptions).toBeUndefined();
    });
  });

  describe("channel-locked classification", () => {
    it("forces Digital classification into the list panel filters when only Digital OOH channel is selected", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH"]);
      expect(
        listPanelProps.last?.inventoryFilters?.inventoryClassification,
      ).toEqual(["Digital"]);
    });

    it("forces Classic classification when only Classic OOH channel is selected", async () => {
      await renderPage(true, undefined, ["CLASSIC_OOH"]);
      expect(
        listPanelProps.last?.inventoryFilters?.inventoryClassification,
      ).toEqual(["Classic"]);
    });

    it("constrains classification to the union when two channels are selected", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH", "CLASSIC_OOH"]);
      expect(
        listPanelProps.last?.inventoryFilters?.inventoryClassification,
      ).toEqual(["Digital", "Classic"]);
    });

    it("does not force a classification when all channels are selected", async () => {
      await renderPage(true, undefined, [
        "DIGITAL_OOH",
        "CLASSIC_OOH",
        "CINEMA",
      ]);
      expect(
        listPanelProps.last?.inventoryFilters?.inventoryClassification,
      ).toEqual([]);
    });

    it("passes the campaign media channels to the filter drawer", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH"]);
      expect(filterDrawerProps.last?.media_channels).toEqual(["DIGITAL_OOH"]);
    });
  });

  describe("venue type seeding from targeting", () => {
    it("seeds the filter Venue Types with the union of targeting digital + classic venue types", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH", "CLASSIC_OOH"], {
        venueTypes: { digitalOoh: ["MALL"], classicOoh: ["GYM"] },
      });
      expect(listPanelProps.last?.inventoryFilters?.venueTypes).toEqual([
        "MALL",
        "GYM",
      ]);
    });

    it("defaults classification to Digital when both channels are selected but only digital venue types are set", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH", "CLASSIC_OOH"], {
        venueTypes: { digitalOoh: ["MALL"], classicOoh: [] },
      });
      expect(
        listPanelProps.last?.inventoryFilters?.inventoryClassification,
      ).toEqual(["Digital"]);
    });

    it("does not seed venue types when targeting has none", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH", "CLASSIC_OOH"]);
      expect(listPanelProps.last?.inventoryFilters?.venueTypes).toEqual([]);
    });

    it("overwrites existing filter venue types with the targeting selection on step entry", async () => {
      await renderPage(
        true,
        undefined,
        ["DIGITAL_OOH", "CLASSIC_OOH"],
        { venueTypes: { digitalOoh: ["MALL"], classicOoh: [] } },
        ["STADIUM", "AIRPORT"],
      );
      expect(listPanelProps.last?.inventoryFilters?.venueTypes).toEqual([
        "MALL",
      ]);
    });

    it("clears filter venue types on entry when targeting has none", async () => {
      await renderPage(true, undefined, ["DIGITAL_OOH"], undefined, [
        "STADIUM",
      ]);
      expect(listPanelProps.last?.inventoryFilters?.venueTypes).toEqual([]);
    });
  });

  describe("active filter count badge", () => {
    it("counts every active filter category, including venue types", async () => {
      // Venue types are seeded from targeting on entry, so supply them there.
      await renderPage(
        true,
        undefined,
        undefined,
        { venueTypes: { digitalOoh: ["MALL"], classicOoh: [] } },
        undefined,
        {
          mediaOwners: ["mo-1"],
          inventoryClassification: ["Digital"],
          bookingMode: ["spot"],
          sizes: ["L"],
          environments: ["Outdoor"],
          programmaticSupport: "YES",
          dealTypes: ["guaranteed"],
          latitude: "1.23",
          longitude: "4.56",
        },
      );
      // 9 preset categories + venue types seeded from targeting = 10.
      expect(screen.getByText("10")).toBeInTheDocument();
    });

    it("does not render the badge when no filters are active", async () => {
      // Scoped to the badge's own class: the footer (mounted alongside the
      // list/map body) legitimately renders its own "0" stats (e.g. zero
      // estimated impressions), so a page-wide text query is too broad.
      const { container } = await renderPage(true);
      expect(
        container.querySelector(".rounded-sm.bg-mw-primary-500"),
      ).toBeNull();
    });
  });

  describe("paste reference IDs", () => {
    beforeEach(() => {
      showSuccess.mockClear();
      showError.mockClear();
      bulkSelectInventoryByReferenceIds.mockClear();
      bulkSelectInventoryByReferenceIds.mockReturnValue({
        unwrap: () =>
          Promise.resolve({ success: true, data: "2 inventories selected" }),
      });
    });

    it("bulk-selects pasted reference IDs and shows the backend's success message", async () => {
      const user = userEvent.setup();
      renderPage(true);

      await user.click(
        screen.getByLabelText("inventories.manual.pasteIds.title"),
      );
      await user.type(
        screen.getByPlaceholderText("inventories.manual.pasteIds.placeholder"),
        "REF-1, REF-2",
      );
      await user.click(
        screen.getByRole("button", {
          name: "inventories.manual.pasteIds.submit",
        }),
      );

      await waitFor(() => {
        expect(bulkSelectInventoryByReferenceIds).toHaveBeenCalledWith({
          campaignId: "c1",
          referenceIds: ["REF-1", "REF-2"],
          operationType: "SELECT",
        });
      });
      expect(showSuccess).toHaveBeenCalledWith("2 inventories selected");
    });

    it("shows an error message when the bulk-select call fails", async () => {
      bulkSelectInventoryByReferenceIds.mockReturnValue({
        unwrap: () => Promise.reject(new Error("Network error")),
      });
      const user = userEvent.setup();
      renderPage(true);

      await user.click(
        screen.getByLabelText("inventories.manual.pasteIds.title"),
      );
      await user.type(
        screen.getByPlaceholderText("inventories.manual.pasteIds.placeholder"),
        "REF-1",
      );
      await user.click(
        screen.getByRole("button", {
          name: "inventories.manual.pasteIds.submit",
        }),
      );

      await waitFor(() => {
        expect(showError).toHaveBeenCalledWith(
          "inventories.manual.selectFailed",
        );
      });
    });
  });
});
