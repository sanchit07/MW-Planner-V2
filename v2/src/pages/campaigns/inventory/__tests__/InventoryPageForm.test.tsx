import { configureStore } from "@reduxjs/toolkit";
import { agencyApi } from "@services/agency/agencySlice";
import campaignSlice from "@services/campaign/campaignSlice";
import { inventoryApi } from "@services/inventory/inventorySlice";
import stepperSlice, {
  type StepperState,
} from "@services/stepper/stepperSlice";
import userSlice from "@services/user/userSlice";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import InventoryPageForm from "../InventoryPageForm";

// ---------------------------------------------------------------------------
// Child full-page screens are mocked so we can observe the open/close props
// InventoryPageForm passes to them (that is the behaviour under test here).
// ---------------------------------------------------------------------------
const viewProps = vi.hoisted(() => ({ isOpen: false }));
const manualProps = vi.hoisted(() => ({ isOpen: false }));

vi.mock("../view/ViewInventoriesPage", () => ({
  default: ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) => {
    viewProps.isOpen = isOpen;
    return isOpen ? (
      <button data-testid="view-close" onClick={onClose}>
        view-open
      </button>
    ) : null;
  },
}));

vi.mock("../manual-selection/ManualSelectionPage", () => ({
  default: ({
    isOpen,
    onClose,
    onManualEditsChange,
  }: {
    isOpen: boolean;
    onClose: () => void;
    onManualEditsChange?: (hasEdits: boolean) => void;
  }) => {
    manualProps.isOpen = isOpen;
    return (
      <>
        <button
          data-testid="manual-simulate-edit"
          onClick={() => onManualEditsChange?.(true)}
        >
          simulate-edit
        </button>
        {isOpen && (
          <button data-testid="manual-close" onClick={onClose}>
            manual-open
          </button>
        )}
      </>
    );
  },
}));

// Mutable forecast payload so individual tests can control the selected count.
const forecastState = vi.hoisted(() => ({
  totalInventories: 0,
  estimatedImpression: 0,
}));

// Stable trigger so the lazy-query mock keeps the same identity across renders.
const forecastTrigger = vi.hoisted(() =>
  vi.fn().mockImplementation(() => ({
    unwrap: () =>
      Promise.resolve({
        success: true,
        data: {
          totalInventories: forecastState.totalInventories,
          estimatedImpression: forecastState.estimatedImpression,
          estimatedReach: 0,
          estimatedFrequency: 0,
          estimatedAdPlays: 0,
          sov: 0,
          avgCpm: 0,
          avgECpm: 0,
          totalCost: 0,
          plannedSot: 0,
          totalSot: 0,
          warnings: [],
        },
      }),
  })),
);

const showError = vi.hoisted(() => vi.fn());

// Autosave mutation — used to persist the skipRecommendation choice.
const autosaveTrigger = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () => Promise.resolve({ success: true }),
  }),
);

vi.mock("@services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/campaign/campaignSlice")>();
  return {
    ...actual,
    useAutosaveCampaignMutation: () => [autosaveTrigger, {}],
  };
});

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError, showSuccess: vi.fn() }),
}));

// Generate trigger. Default: immediately reports a COMPLETED run so the panel
// reaches the "completed" (ready) state. Tests can override to keep it pending
// (a never-resolving promise) to hold the panel in "generating".
const generateTrigger = vi.hoisted(() => vi.fn());

// Bulk-select mutation (used by the Restore flow's deselect-all step).
const bulkSelectTrigger = vi.hoisted(() =>
  vi.fn().mockReturnValue({
    unwrap: () => Promise.resolve({ success: true }),
  }),
);

vi.mock("@services/inventory/inventorySlice", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@services/inventory/inventorySlice")>();
  return {
    ...actual,
    useLazyGetCampaignForecastQuery: () => [forecastTrigger, {}],
    useLazyGenerateInventoryRecommendationQuery: () => [generateTrigger, {}],
    useBulkSelectInventoryMutation: () => [bulkSelectTrigger, {}],
    // Reach curve: return no selected inventories so the chart never renders
    // (chart.js can't paint in jsdom); the curve stays idle.
    useLazyGetAllSelectedInventoryQuery: () => [
      vi.fn().mockReturnValue({
        unwrap: () => Promise.resolve({ success: true, data: [] }),
      }),
      {},
    ],
    useLazyGetReachSaturationCurveQuery: () => [
      vi.fn().mockReturnValue({
        unwrap: () => Promise.resolve({ success: true, data: [] }),
      }),
      {},
    ],
  };
});

// Mirrors the real wizard's step config (CampaignWrapper.tsx) closely enough
// for stepper-navigation assertions: step 5 depends on [1, 2, 4].
function makeStepperState(overrides: Partial<StepperState> = {}): StepperState {
  return {
    steps: [
      {
        id: 1,
        title: "Details",
        isCompleted: true,
        isAccessible: true,
        isCurrent: false,
        dependencies: [],
      },
      {
        id: 2,
        title: "Budget & Goals",
        isCompleted: true,
        isAccessible: true,
        isCurrent: false,
        dependencies: [1],
      },
      {
        id: 3,
        title: "Targeting",
        isCompleted: false,
        isAccessible: true,
        isCurrent: false,
        isOptional: true,
        dependencies: [1, 2],
      },
      {
        id: 4,
        title: "Inventories",
        isCompleted: false,
        isAccessible: true,
        isCurrent: true,
        dependencies: [1, 2],
      },
      {
        id: 5,
        title: "Optimization",
        isCompleted: false,
        isAccessible: false,
        isCurrent: false,
        isOptional: true,
        dependencies: [1, 2, 4],
      },
    ],
    currentStepId: 4,
    totalSteps: 5,
    progress: 40,
    isLoading: false,
    isInitialized: true,
    isEditMode: false,
    editCampaignId: null,
    inventoryFilters: {
      mediaOwners: [],
      venueTypes: [],
      bookingMode: [],
      sizes: [],
      latitude: "",
      longitude: "",
      searchbyquery: "",
      environments: [],
      inventoryClassification: [],
      programmaticSupport: "ALL",
      dealTypes: [],
    },
    ...overrides,
  };
}

function createTestStore(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  campaignDataOverrides: Record<string, any> = {},
) {
  return configureStore({
    reducer: {
      campaign: campaignSlice,
      profile: userSlice,
      stepper: stepperSlice,
      [inventoryApi.reducerPath]: inventoryApi.reducer,
      [agencyApi.reducerPath]: agencyApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(
        inventoryApi.middleware,
        agencyApi.middleware,
      ),
    preloadedState: {
      campaign: {
        currentCampaignName: "",
        campaignId: "campaign-1",
        isCreating: false,
        createError: null,
        isEditMode: false,
        forecastData: null,
        recommendationRun: null,
        campaignData: {
          id: "campaign-1",
          name: "",
          status: "",
          countryId: "US",
          currency: "USD",
          startDate: "",
          endDate: "",
          clientType: "",
          createdAt: "",
          updatedAt: "",
          // Default: the campaign already has inventory, so Step 4 opens in
          // the AI recommendation flow (as before the opt-out choice existed).
          // Choice-gate tests override this back to 0.
          inventoryCount: 1,
          ...campaignDataOverrides,
        },
      },
      stepper: makeStepperState(),
    },
  });
}

function renderForm(
  props: Partial<React.ComponentProps<typeof InventoryPageForm>> = {},
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  campaignDataOverrides: Record<string, any> = {},
) {
  const store = createTestStore(campaignDataOverrides);
  const ref = React.createRef<React.ComponentRef<typeof InventoryPageForm>>();
  const result = render(
    <Provider store={store}>
      <MemoryRouter>
        <InventoryPageForm ref={ref} {...props} />
      </MemoryRouter>
    </Provider>,
  );
  return { ...result, ref, store };
}

/** Waits for the recommendation to settle into the "completed" (ready) state. */
async function waitForReady() {
  await screen.findByText("inventories.aiRecommendation.title");
  await waitFor(() => {
    expect(
      (
        screen
          .getByText("inventories.planSummary.view")
          .closest("button") as HTMLButtonElement | null
      )?.disabled,
    ).toBe(false);
  });
}

describe("InventoryPageForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    forecastState.totalInventories = 0;
    forecastState.estimatedImpression = 0;
    viewProps.isOpen = false;
    manualProps.isOpen = false;
    // Default: recommendation completes immediately.
    generateTrigger.mockResolvedValue({
      isError: false,
      data: {
        data: {
          status: "COMPLETED",
          completionPercentage: 100,
          runId: "run-1",
          campaignId: "campaign-1",
        },
      },
    });
    bulkSelectTrigger.mockReturnValue({
      unwrap: () => Promise.resolve({ success: true }),
    });
  });

  describe("rendering", () => {
    it("renders the AI Smart Recommendation panel (title, description, actions)", async () => {
      renderForm();
      expect(
        await screen.findByText("inventories.aiRecommendation.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.aiRecommendation.description"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.aiRecommendation.editManually"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.planSummary.view"),
      ).toBeInTheDocument();
    });

    it("shows the Plan Summary empty state when no inventories are selected", async () => {
      renderForm();
      expect(
        await screen.findByText("inventories.planSummary.empty"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("inventories.planSummary.emptyHint"),
      ).toBeInTheDocument();
    });

    it("shows the Plan Summary heading + forecast breakdown when inventories are selected", async () => {
      forecastState.totalInventories = 3;
      renderForm();
      expect(
        await screen.findByText("inventories.planSummary.heading"),
      ).toBeInTheDocument();
      // Empty state must be gone once the count is > 0.
      await waitFor(() => {
        expect(
          screen.queryByText("inventories.planSummary.empty"),
        ).not.toBeInTheDocument();
      });
    });
  });

  describe("View / Edit Manually screens", () => {
    it("opens the View screen when the View button is clicked", async () => {
      const user = userEvent.setup();
      renderForm();
      await waitForReady();

      expect(viewProps.isOpen).toBe(false);
      await user.click(screen.getByText("inventories.planSummary.view"));
      expect(viewProps.isOpen).toBe(true);
      expect(screen.getByTestId("view-close")).toBeInTheDocument();
    });

    it("opens the Manual Selection screen when Edit Manually is clicked", async () => {
      const user = userEvent.setup();
      renderForm();
      await waitForReady();

      expect(manualProps.isOpen).toBe(false);
      await user.click(
        screen.getByText("inventories.aiRecommendation.editManually"),
      );
      expect(manualProps.isOpen).toBe(true);
      expect(screen.getByTestId("manual-close")).toBeInTheDocument();
    });

    it("notifies the wizard (onInventorySelectionChange) when Manual Selection closes", async () => {
      const user = userEvent.setup();
      const onInventorySelectionChange = vi.fn();
      renderForm({ onInventorySelectionChange });
      await waitForReady();

      await user.click(
        screen.getByText("inventories.aiRecommendation.editManually"),
      );
      await user.click(screen.getByTestId("manual-close"));

      expect(onInventorySelectionChange).toHaveBeenCalledTimes(1);
      await waitFor(() => {
        expect(manualProps.isOpen).toBe(false);
      });
    });
  });

  describe("action buttons disabled while generating", () => {
    beforeEach(() => {
      // Keep the recommendation permanently "generating": never resolve.
      generateTrigger.mockReturnValue(new Promise<never>(() => {}));
    });

    it("disables View, Edit Manually and Restore until the run completes", async () => {
      const user = userEvent.setup();
      renderForm();
      // Restore only renders once the user has made a manual edit.
      await user.click(screen.getByTestId("manual-simulate-edit"));
      // Progress bar shows while generating; buttons stay disabled.
      const view = await screen.findByText("inventories.planSummary.view");
      expect(view.closest("button")).toBeDisabled();
      expect(
        screen
          .getByText("inventories.aiRecommendation.editManually")
          .closest("button"),
      ).toBeDisabled();
      expect(
        screen.getByLabelText("inventories.aiRecommendation.restoreAi"),
      ).toBeDisabled();
    });
  });

  describe("Restore AI recommendation", () => {
    it("deselects all then regenerates when the restore is confirmed", async () => {
      const user = userEvent.setup();
      renderForm();
      await waitForReady();
      await user.click(screen.getByTestId("manual-simulate-edit"));

      await user.click(
        screen.getByLabelText("inventories.aiRecommendation.restoreAi"),
      );
      // Confirmation modal → confirm.
      await user.click(
        await screen.findByText("inventories.aiRecommendation.restoreConfirm"),
      );

      await waitFor(() => {
        expect(bulkSelectTrigger).toHaveBeenCalledWith(
          expect.objectContaining({
            campaignId: "campaign-1",
            operationType: "DESELECT",
          }),
        );
      });
    });

    it("sends forceRegenerate=true only on the restore /generate call", async () => {
      const user = userEvent.setup();
      renderForm();
      await waitForReady();
      await user.click(screen.getByTestId("manual-simulate-edit"));

      // The initial mount run must NOT force a regenerate.
      expect(generateTrigger).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({
          campaignId: "campaign-1",
          forceRegenerate: false,
        }),
      );

      await user.click(
        screen.getByLabelText("inventories.aiRecommendation.restoreAi"),
      );
      await user.click(
        await screen.findByText("inventories.aiRecommendation.restoreConfirm"),
      );

      // The restore-triggered run forces a regenerate.
      await waitFor(() => {
        expect(generateTrigger).toHaveBeenLastCalledWith(
          expect.objectContaining({
            campaignId: "campaign-1",
            forceRegenerate: true,
          }),
        );
      });
    });

    it("failed-state Retry never sends forceRegenerate=true", async () => {
      const user = userEvent.setup();
      generateTrigger.mockResolvedValue({ isError: true });
      renderForm();

      const retryButton = await screen.findByText(
        "inventories.smartSuggestion.retry",
      );
      await user.click(retryButton);

      await waitFor(() => {
        expect(generateTrigger).toHaveBeenLastCalledWith(
          expect.objectContaining({
            campaignId: "campaign-1",
            forceRegenerate: false,
          }),
        );
      });
      expect(generateTrigger).not.toHaveBeenCalledWith(
        expect.objectContaining({ forceRegenerate: true }),
      );
    });
  });

  describe("recommendation cache key", () => {
    it("does not reuse a cached run whose signature omits the current campaignId", async () => {
      // Regression: two campaigns sharing the same budget/dates/targeting used to
      // collide because the cache signature excluded campaignId. A stale run cached
      // under the pre-fix (campaignId-less) signature must NOT be reused — generate
      // must run for this campaign.
      const stalePreFixSignature = JSON.stringify({
        budget: null,
        startDate: "",
        endDate: "",
        goalType: null,
        targeting: null,
        mediaOwnerIds: [],
      });
      const store = configureStore({
        reducer: {
          campaign: campaignSlice,
          profile: userSlice,
          [inventoryApi.reducerPath]: inventoryApi.reducer,
          [agencyApi.reducerPath]: agencyApi.reducer,
        },
        middleware: (getDefaultMiddleware) =>
          getDefaultMiddleware().concat(
            inventoryApi.middleware,
            agencyApi.middleware,
          ),
        preloadedState: {
          campaign: {
            currentCampaignName: "",
            campaignId: "campaign-1",
            isCreating: false,
            createError: null,
            isEditMode: false,
            forecastData: null,
            recommendationRun: {
              runId: "run-from-other-campaign",
              signature: stalePreFixSignature,
              mediaChannels: [],
            },
            campaignData: {
              id: "campaign-1",
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
            },
          },
        },
      });
      render(
        <Provider store={store}>
          <MemoryRouter>
            <InventoryPageForm />
          </MemoryRouter>
        </Provider>,
      );

      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalledWith(
          expect.objectContaining({ campaignId: "campaign-1" }),
        );
      });
    });

    it("does not reuse a cached run when only the budgetAllocation changed", async () => {
      // budgetAllocation is part of the recommendation signature, so editing the
      // budget split must invalidate the cached run and re-run /generate.
      // This cached signature matches the campaign on every other input but
      // predates the allocation edit (it omits budgetAllocation).
      const preAllocationSignature = JSON.stringify({
        campaignId: "campaign-1",
        budget: null,
        startDate: "",
        endDate: "",
        goalType: null,
        targeting: null,
        mediaChannels: null,
        mediaOwnerIds: [],
      });
      const store = configureStore({
        reducer: {
          campaign: campaignSlice,
          profile: userSlice,
          [inventoryApi.reducerPath]: inventoryApi.reducer,
          [agencyApi.reducerPath]: agencyApi.reducer,
        },
        middleware: (getDefaultMiddleware) =>
          getDefaultMiddleware().concat(
            inventoryApi.middleware,
            agencyApi.middleware,
          ),
        preloadedState: {
          campaign: {
            currentCampaignName: "",
            campaignId: "campaign-1",
            isCreating: false,
            createError: null,
            isEditMode: false,
            forecastData: null,
            recommendationRun: {
              runId: "run-before-allocation-edit",
              signature: preAllocationSignature,
              mediaChannels: [],
            },
            campaignData: {
              id: "campaign-1",
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
              budgetAllocation: {
                digital: 100,
                classic: 0,
                transit: 0,
                retail: 0,
              },
            },
          },
        },
      });
      render(
        <Provider store={store}>
          <MemoryRouter>
            <InventoryPageForm />
          </MemoryRouter>
        </Provider>,
      );

      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalledWith(
          expect.objectContaining({ campaignId: "campaign-1" }),
        );
      });
    });
  });

  describe("ref", () => {
    it("exposes submitForm that returns true", async () => {
      const { ref } = renderForm();
      await waitForReady();
      const result = await ref.current!.submitForm();
      expect(result).toBe(true);
    });

    it("validateStep is invalid and surfaces an error when no inventory is selected", async () => {
      const { ref } = renderForm();
      await waitForReady();
      const result = await ref.current!.validateStep();
      expect(result.isValid).toBe(false);
      expect(result.errors).toEqual([
        "Please select at least one inventory item",
      ]);
      expect(showError).toHaveBeenCalledWith(
        "Please select at least one inventory item to proceed.",
      );
    });

    it("validateStep is valid when at least one inventory is selected", async () => {
      forecastState.totalInventories = 3;
      const { ref } = renderForm();
      await waitFor(() => {
        expect(
          screen.queryByText("inventories.planSummary.empty"),
        ).not.toBeInTheDocument();
      });
      const result = await ref.current!.validateStep();
      expect(result.isValid).toBe(true);
      expect(showError).not.toHaveBeenCalled();
    });

    it("exposes resetForm", async () => {
      const { ref } = renderForm();
      await waitForReady();
      act(() => {
        ref.current!.resetForm();
      });
      expect(ref.current).not.toBeNull();
    });
  });

  describe("recommendation opt-out (choice gate)", () => {
    it("shows the choice card on first arrival and makes no recommendation call", async () => {
      renderForm({}, { inventoryCount: 0 });
      expect(
        await screen.findByText("inventories.recommendationChoice.title"),
      ).toBeInTheDocument();
      expect(generateTrigger).not.toHaveBeenCalled();
      // Neither flow panel is rendered yet.
      expect(
        screen.queryByText("inventories.aiRecommendation.title"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("inventories.manualMode.title"),
      ).not.toBeInTheDocument();
    });

    it("validateStep is invalid while the choice is still pending", async () => {
      const { ref } = renderForm({}, { inventoryCount: 0 });
      await screen.findByText("inventories.recommendationChoice.title");
      const result = await ref.current!.validateStep();
      expect(result.isValid).toBe(false);
    });

    it("skips straight to manual selection, persists the choice, never calls /generate", async () => {
      const user = userEvent.setup();
      renderForm({}, { inventoryCount: 0 });
      await screen.findByText("inventories.recommendationChoice.title");

      await user.click(screen.getByTestId("choice-pick-manually"));

      // Manual selection page opens immediately as the primary surface.
      expect(manualProps.isOpen).toBe(true);
      // Choice persisted on the plan.
      expect(autosaveTrigger).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { skipRecommendation: true },
      });
      // Manual-mode panel replaces the AI panel; no recommendation call made.
      expect(
        await screen.findByText("inventories.manualMode.title"),
      ).toBeInTheDocument();
      expect(generateTrigger).not.toHaveBeenCalled();
    });

    it("choosing recommendations starts the AI flow and persists the choice", async () => {
      const user = userEvent.setup();
      renderForm({}, { inventoryCount: 0 });
      await screen.findByText("inventories.recommendationChoice.title");

      await user.click(screen.getByTestId("choice-use-recommendations"));

      expect(autosaveTrigger).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { skipRecommendation: false },
      });
      await screen.findByText("inventories.aiRecommendation.title");
      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalledWith(
          expect.objectContaining({ campaignId: "campaign-1" }),
        );
      });
    });

    it("a plan saved with skipRecommendation=true reopens directly in manual mode", async () => {
      renderForm({}, { inventoryCount: 0, skipRecommendation: true });
      expect(
        await screen.findByText("inventories.manualMode.title"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("inventories.recommendationChoice.title"),
      ).not.toBeInTheDocument();
      expect(generateTrigger).not.toHaveBeenCalled();
      // Forecast for the current manual selection still loads.
      await waitFor(() => {
        expect(forecastTrigger).toHaveBeenCalled();
      });
    });

    it("manual mode can opt back in to recommendations (no selection: flag flips first)", async () => {
      const user = userEvent.setup();
      renderForm({}, { inventoryCount: 0, skipRecommendation: true });
      await screen.findByText("inventories.manualMode.title");

      await user.click(
        screen.getByText("inventories.manualMode.useRecommendations"),
      );

      await screen.findByText("inventories.aiRecommendation.title");
      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalledWith(
          expect.objectContaining({ campaignId: "campaign-1" }),
        );
      });
      expect(autosaveTrigger).toHaveBeenCalledWith({
        id: "campaign-1",
        data: { skipRecommendation: false },
      });
      // With nothing selected, the flag flip happens before generation.
      expect(autosaveTrigger.mock.invocationCallOrder[0]).toBeLessThan(
        generateTrigger.mock.invocationCallOrder[0],
      );
    });

    it("waits for a slow skip=true save before generating, so the backend never sees skip=false mid-flight", async () => {
      // Simulate a slow PATCH: "pick manually" starts skipRecommendation=true
      // but it hasn't reached the backend when the user opts back in with a
      // selection. /generate must not start until that PATCH resolves.
      let resolveSave!: () => void;
      const savePromise = new Promise<{ success: boolean }>((resolve) => {
        resolveSave = () => resolve({ success: true });
      });
      autosaveTrigger.mockReturnValueOnce({ unwrap: () => savePromise });

      forecastState.totalInventories = 2;
      const user = userEvent.setup();
      renderForm({}, { inventoryCount: 0 });
      await screen.findByText("inventories.recommendationChoice.title");

      await user.click(screen.getByTestId("choice-pick-manually"));
      await screen.findByText("inventories.manualMode.title");
      // Forecast (selection count) loads in manual mode.
      await screen.findByText("inventories.planSummary.heading");

      await user.click(
        screen.getByText("inventories.manualMode.useRecommendations"),
      );

      // The skip=true save is still in flight → no generate yet.
      expect(generateTrigger).not.toHaveBeenCalled();

      resolveSave();
      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalledWith(
          expect.objectContaining({ campaignId: "campaign-1" }),
        );
      });
    });

    it("keeps mode across a Step-4 remount: the persisted choice updates Redux campaignData", async () => {
      const user = userEvent.setup();
      const { store, unmount } = renderForm({}, { inventoryCount: 0 });
      await screen.findByText("inventories.recommendationChoice.title");

      await user.click(screen.getByTestId("choice-pick-manually"));
      await waitFor(() => {
        expect(store.getState().campaign.campaignData?.skipRecommendation).toBe(
          true,
        );
      });

      // Simulate navigating away and back to Step 4 (remount with same store).
      unmount();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <InventoryPageForm />
          </MemoryRouter>
        </Provider>,
      );

      // Revisit honours the persisted choice — manual mode, no choice card.
      expect(
        await screen.findByText("inventories.manualMode.title"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("inventories.recommendationChoice.title"),
      ).not.toBeInTheDocument();
      expect(generateTrigger).not.toHaveBeenCalled();
    });

    it("keeps recommendation mode across a remount after opting back in", async () => {
      const user = userEvent.setup();
      const { store, unmount } = renderForm(
        {},
        { inventoryCount: 0, skipRecommendation: true },
      );
      await screen.findByText("inventories.manualMode.title");

      await user.click(
        screen.getByText("inventories.manualMode.useRecommendations"),
      );
      await waitFor(() => {
        expect(store.getState().campaign.campaignData?.skipRecommendation).toBe(
          false,
        );
      });

      unmount();
      render(
        <Provider store={store}>
          <MemoryRouter>
            <InventoryPageForm />
          </MemoryRouter>
        </Provider>,
      );

      // Remount re-derives recommendation mode, not manual.
      expect(
        await screen.findByText("inventories.aiRecommendation.title"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("inventories.manualMode.title"),
      ).not.toBeInTheDocument();
    });

    it("manual mode with a selection keeps it: flag flips only after the run completes", async () => {
      forecastState.totalInventories = 2;
      const user = userEvent.setup();
      renderForm({}, { inventoryCount: 2, skipRecommendation: true });
      await screen.findByText("inventories.manualMode.title");
      // Wait for the forecast (selection count) to load.
      await screen.findByText("inventories.planSummary.heading");

      await user.click(
        screen.getByText("inventories.manualMode.useRecommendations"),
      );

      await waitFor(() => {
        expect(generateTrigger).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(autosaveTrigger).toHaveBeenCalledWith({
          id: "campaign-1",
          data: { skipRecommendation: false },
        });
      });
      // Generation started before the flag flipped off, so the backend
      // preserved the manual selection while recording the run.
      expect(generateTrigger.mock.invocationCallOrder[0]).toBeLessThan(
        autosaveTrigger.mock.invocationCallOrder[0],
      );
    });
  });

  describe("Goal/Budget banner — Optimize to Goal", () => {
    it("advances to the Optimization step (5), marking step 4 completed so it's actually accessible", async () => {
      forecastState.totalInventories = 3;
      // 120 >= 100 * 1.2 → GoalBudgetBanner's "over" (over-achieved) state.
      forecastState.estimatedImpression = 120;
      const user = userEvent.setup();
      const { store } = renderForm(
        {},
        { goals: { goalType: "IMPRESSIONS", targetValue: 100 } },
      );
      await waitForReady();

      const optimizeButton = await screen.findByText(
        "inventories.planSummary.optimizeToGoal",
      );
      await user.click(optimizeButton);

      const stepperState = store.getState().stepper;
      expect(stepperState.currentStepId).toBe(5);
      expect(stepperState.steps.find((s) => s.id === 4)?.isCompleted).toBe(
        true,
      );
      expect(stepperState.steps.find((s) => s.id === 5)?.isCurrent).toBe(true);
    });
  });
});
