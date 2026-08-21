import { configureStore } from "@reduxjs/toolkit";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import { CampaignForecastData } from "../../../types/inventory.types";
import CampaignWrapper from "../CampaignWrapper";

// ─── Hoisted mocks ────────────────────────────────────────────────────────────

const { mockShowError } = vi.hoisted(() => ({
  mockShowError: vi.fn(),
}));

const mockUpdateCampaign = vi.hoisted(() => vi.fn());

// Stable mock: must NOT be recreated on every render. clearFormData sits in a
// useEffect dep array; a new vi.fn() each render fires cleanup → dispatches
// clearInventoryFilters → new state.campaign reference → re-render → loop.
const mockClearFormData = vi.fn();

// ─── Module mocks ─────────────────────────────────────────────────────────────

vi.mock("@tolgee/react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tolgee/react")>();
  return {
    ...actual,
    useTranslate: () => ({ t: (key: string) => key }),
    T: ({ keyName }: { keyName: string }) => <span>{keyName}</span>,
  };
});

vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({ showError: mockShowError, showSuccess: vi.fn() }),
}));

vi.mock("@utils/storage", () => ({
  default: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    removeAll: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
const mockUseParams = vi.fn(() => ({}));
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => mockUseParams(),
  };
});

const mockNextStep = vi.fn();
const mockPreviousStep = vi.fn();
const mockNavigateToStep = vi.fn();
const mockMarkCompleted = vi.fn();
const mockInitialize = vi.fn();
const mockSetLoading = vi.fn();
const mockUpdateAccessibility = vi.fn();
const mockSetStepperEditMode = vi.fn();

const mockStepperConfig = {
  isLastStep: false,
  isFirstStep: true,
  currentStepId: 1,
};

vi.mock("../../../hooks/useStepper", () => ({
  useStepper: () => ({
    initialize: mockInitialize,
    stepperComponentData: {
      steps: [
        { id: 1, isCompleted: false, isAccessible: true, isCurrent: true },
        { id: 2, isCompleted: false, isAccessible: false, isCurrent: false },
      ],
    },
    currentStepId: mockStepperConfig.currentStepId,
    isFirstStep: mockStepperConfig.isFirstStep,
    isLastStep: mockStepperConfig.isLastStep,
    nextStep: mockNextStep,
    previousStep: mockPreviousStep,
    navigateToStep: mockNavigateToStep,
    markCompleted: mockMarkCompleted,
    setLoading: mockSetLoading,
    clearFormData: mockClearFormData,
    isInitialized: false,
    setStepperEditMode: mockSetStepperEditMode,
    updateAccessibility: mockUpdateAccessibility,
  }),
}));

vi.mock("../../../hooks/useAutosave", () => ({
  useAutosave: () => ({ isAutosaving: false }),
}));

vi.mock("../BudgetAndGoalPage", () => ({
  default: React.forwardRef(() => <div data-testid="budget-step">Budget</div>),
}));
vi.mock("../CreateCampaignForm", () => ({
  default: React.forwardRef(() => (
    <div data-testid="create-campaign-step">Create Campaign</div>
  )),
}));
vi.mock("../TargetingForm", () => ({
  default: React.forwardRef(() => (
    <div data-testid="targeting-step">Targeting</div>
  )),
}));
vi.mock("../inventory/InventoryPageForm", () => ({
  default: React.forwardRef(() => (
    <div data-testid="inventory-step">Inventory</div>
  )),
}));
const mockOptimizationValidateStep = vi.hoisted(() =>
  vi.fn().mockResolvedValue({ isValid: true, errors: [] }),
);

vi.mock("../optimization/Optimization", () => ({
  default: React.forwardRef((_props: unknown, ref: React.Ref<unknown>) => {
    React.useImperativeHandle(ref, () => ({
      submitForm: async () => true,
      getFormData: () => ({}),
      isValid: () => true,
      validateStep: mockOptimizationValidateStep,
      resetForm: () => {},
    }));
    return <div data-testid="optimization-step">Optimization</div>;
  }),
}));

vi.mock("../../../services/campaign/campaignSlice", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../../../services/campaign/campaignSlice")
    >();
  return {
    ...actual,
    useLazyGetCampaignQuery: () => [
      vi.fn().mockResolvedValue({
        unwrap: () => Promise.resolve({ success: true, data: {} }),
      }),
    ],
    useUpdateCampaignMutation: () => [
      mockUpdateCampaign.mockReturnValue({
        unwrap: () => Promise.resolve({ success: true }),
      }),
    ],
  };
});

// ─── Store helpers ────────────────────────────────────────────────────────────

// Singleton initial states so plain reducers return the SAME object reference
// when an unrelated action is dispatched. Without this, every Redux action
// (e.g. dispatch from a cleanup effect) triggers a re-render because
// `() => ({})` always produces a new reference.
const _initCampaignState = { campaignData: null, campaignId: null };
const _initStepperState = {};
const _initProfileState = { profile: null };

// Base store: no active user — profile is null so company-switch guard is a no-op
const store = configureStore({
  reducer: {
    campaign: (state = _initCampaignState) => state,
    stepper: (state = _initStepperState) => state,
    profile: (state = _initProfileState) => state,
  },
});

const mockForecastData: CampaignForecastData = {
  totalInventories: 5,
  estimatedImpression: 10000,
  estimatedReach: 8000,
  estimatedFrequency: 1.5,
  estimatedAdPlays: 500,
  sov: 20,
  avgCpm: 15.5,
  avgECpm: 12.3,
  totalCost: 5000,
  plannedSot: 40,
  totalSot: 100,
  warnings: [],
};

const mockCampaignData = {
  id: "camp-1",
  name: "Test Campaign",
  status: "DRAFT",
  countryId: "SG",
  startDate: "2026-05-01",
  endDate: "2026-05-31",
  clientType: "AGENCY",
  createdAt: "2026-04-14T00:00:00Z",
  updatedAt: "2026-04-14T00:00:00Z",
};

function createFinalizeStore(forecastData: CampaignForecastData | null) {
  const initCampaign = {
    campaignData: mockCampaignData,
    campaignId: "camp-1",
    forecastData,
  };
  const initStepper = {};
  const initProfile = { profile: null };
  return configureStore({
    reducer: {
      campaign: (state = initCampaign) => state,
      stepper: (state = initStepper) => state,
      profile: (state = initProfile) => state,
    },
  });
}

// ─── Render helpers ───────────────────────────────────────────────────────────

function renderCampaignWrapper(initialRoute = "/campaigns/create") {
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialRoute]}>
        <Routes>
          <Route path="/campaigns/create" element={<CampaignWrapper />} />
          <Route
            path="/campaigns/edit/:campaignId"
            element={<CampaignWrapper />}
          />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

function renderWithFinalizeStore(forecastData: CampaignForecastData | null) {
  const finalizeStore = createFinalizeStore(forecastData);
  return render(
    <Provider store={finalizeStore}>
      <MemoryRouter initialEntries={["/campaigns/create"]}>
        <Routes>
          <Route path="/campaigns/create" element={<CampaignWrapper />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

function renderWithStatus(status: string) {
  const statusStore = configureStore({
    reducer: {
      campaign: () => ({
        campaignData: { ...mockCampaignData, status },
        campaignId: "camp-1",
        forecastData: null,
      }),
      stepper: () => ({}),
      profile: (state = { profile: null }) => state,
    },
  });
  return render(
    <Provider store={statusStore}>
      <MemoryRouter initialEntries={["/campaigns/create"]}>
        <Routes>
          <Route path="/campaigns/create" element={<CampaignWrapper />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe("CampaignWrapper", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders campaign wrapper container", () => {
      const { container } = renderCampaignWrapper();
      expect(container.querySelector("#campaign-wrapper")).toBeInTheDocument();
    });

    it("renders stepper container", () => {
      const { container } = renderCampaignWrapper();
      expect(
        container.querySelector("#campaign-stepper-container"),
      ).toBeInTheDocument();
    });

    it("renders step content area", () => {
      const { container } = renderCampaignWrapper();
      expect(
        container.querySelector("#campaign-step-content"),
      ).toBeInTheDocument();
    });

    it("renders create campaign step when current step is 1", () => {
      renderCampaignWrapper();
      expect(screen.getByTestId("create-campaign-step")).toBeInTheDocument();
    });
  });

  describe("navigation", () => {
    it("renders Next Step button when not on last step", () => {
      renderCampaignWrapper();
      expect(
        screen.getByRole("button", { name: /campaignWrapper\.nextStep/i }),
      ).toBeInTheDocument();
    });
  });

  describe("step 4 Next button gating", () => {
    function renderOnInventoryStep(totalInventories: number | null) {
      const inventoryStore = configureStore({
        reducer: {
          campaign: () => ({
            campaignData: mockCampaignData,
            campaignId: "camp-1",
            forecastData:
              totalInventories === null ? null : { totalInventories },
          }),
          stepper: () => ({}),
          profile: (state = { profile: null }) => state,
        },
      });
      return render(
        <Provider store={inventoryStore}>
          <MemoryRouter initialEntries={["/campaigns/create"]}>
            <Routes>
              <Route path="/campaigns/create" element={<CampaignWrapper />} />
            </Routes>
          </MemoryRouter>
        </Provider>,
      );
    }

    beforeEach(() => {
      mockStepperConfig.currentStepId = 4;
    });

    afterEach(() => {
      mockStepperConfig.currentStepId = 1;
    });

    it("disables Next when no inventory is selected", () => {
      renderOnInventoryStep(0);
      expect(
        screen.getByRole("button", { name: /campaignWrapper\.nextStep/i }),
      ).toBeDisabled();
    });

    it("disables Next when forecast data is absent", () => {
      renderOnInventoryStep(null);
      expect(
        screen.getByRole("button", { name: /campaignWrapper\.nextStep/i }),
      ).toBeDisabled();
    });

    it("enables Next when at least one inventory is selected", () => {
      renderOnInventoryStep(3);
      expect(
        screen.getByRole("button", { name: /campaignWrapper\.nextStep/i }),
      ).toBeEnabled();
    });
  });

  describe("handleFinaliseCampaign — performance payload", () => {
    beforeEach(() => {
      mockStepperConfig.isLastStep = true;
      mockStepperConfig.isFirstStep = false;
      mockUpdateCampaign.mockReturnValue({
        unwrap: () => Promise.resolve({ success: true }),
      });
      mockOptimizationValidateStep.mockResolvedValue({
        isValid: true,
        errors: [],
      });
    });

    afterEach(() => {
      mockStepperConfig.isLastStep = false;
      mockStepperConfig.isFirstStep = true;
    });

    it("includes performance object in PUT payload when forecastData exists", async () => {
      renderWithFinalizeStore(mockForecastData);

      const finaliseBtn = await screen.findByRole("button", {
        name: /buttons.finaliseCampaign/i,
      });
      fireEvent.click(finaliseBtn);

      await waitFor(() => {
        expect(mockUpdateCampaign).toHaveBeenCalled();
      });

      const payload = mockUpdateCampaign.mock.calls[0][0];
      expect(payload.performance).toEqual({
        totalInventories: mockForecastData.totalInventories,
        estimatedImpression: mockForecastData.estimatedImpression,
        estimatedReach: mockForecastData.estimatedReach,
        totalCost: mockForecastData.totalCost,
        estimatedFrequency: mockForecastData.estimatedFrequency,
        estimatedAdPlays: mockForecastData.estimatedAdPlays,
        avgCpm: mockForecastData.avgCpm,
        avgECpm: mockForecastData.avgECpm,
        sov: mockForecastData.sov,
        plannedSot: mockForecastData.plannedSot,
        totalSot: mockForecastData.totalSot,
      });
    });

    it("omits performance from PUT payload when forecastData is null", async () => {
      renderWithFinalizeStore(null);

      const finaliseBtn = await screen.findByRole("button", {
        name: /buttons.finaliseCampaign/i,
      });
      fireEvent.click(finaliseBtn);

      await waitFor(() => {
        expect(mockUpdateCampaign).toHaveBeenCalled();
      });

      const payload = mockUpdateCampaign.mock.calls[0][0];
      expect(payload.performance).toBeUndefined();
    });

    it("always sets status to PLANNED in PUT payload", async () => {
      renderWithFinalizeStore(mockForecastData);

      const finaliseBtn = await screen.findByRole("button", {
        name: /buttons.finaliseCampaign/i,
      });
      fireEvent.click(finaliseBtn);

      await waitFor(() => {
        expect(mockUpdateCampaign).toHaveBeenCalled();
      });

      const payload = mockUpdateCampaign.mock.calls[0][0];
      expect(payload.status).toBe("PLANNED");
    });
  });

  describe("handleFinaliseCampaign — selling term minDays validation", () => {
    beforeEach(() => {
      mockStepperConfig.isLastStep = true;
      mockStepperConfig.isFirstStep = false;
      mockStepperConfig.currentStepId = 5; // Optimization step — the only one whose ref exposes validateStep in these tests
      mockUpdateCampaign.mockReturnValue({
        unwrap: () => Promise.resolve({ success: true }),
      });
    });

    afterEach(() => {
      mockStepperConfig.isLastStep = false;
      mockStepperConfig.isFirstStep = true;
      mockStepperConfig.currentStepId = 1;
      mockOptimizationValidateStep.mockResolvedValue({
        isValid: true,
        errors: [],
      });
    });

    it("calls validateStep and proceeds to update the campaign when it passes", async () => {
      mockOptimizationValidateStep.mockResolvedValue({
        isValid: true,
        errors: [],
      });
      renderWithFinalizeStore(mockForecastData);

      const finaliseBtn = await screen.findByRole("button", {
        name: /buttons.finaliseCampaign/i,
      });
      fireEvent.click(finaliseBtn);

      await waitFor(() => {
        expect(mockOptimizationValidateStep).toHaveBeenCalled();
      });
      await waitFor(() => {
        expect(mockUpdateCampaign).toHaveBeenCalled();
      });
    });

    it("blocks finalise (does not update the campaign) when validateStep reports a minDays violation", async () => {
      mockOptimizationValidateStep.mockResolvedValue({
        isValid: false,
        errors: ["Some inventory needs more scheduled days"],
      });
      renderWithFinalizeStore(mockForecastData);

      const finaliseBtn = await screen.findByRole("button", {
        name: /buttons.finaliseCampaign/i,
      });
      fireEvent.click(finaliseBtn);

      await waitFor(() => {
        expect(mockOptimizationValidateStep).toHaveBeenCalled();
      });
      expect(mockUpdateCampaign).not.toHaveBeenCalled();
    });
  });

  describe("final-step action buttons visibility", () => {
    beforeEach(() => {
      mockStepperConfig.isLastStep = true;
      mockStepperConfig.isFirstStep = false;
    });

    afterEach(() => {
      mockStepperConfig.isLastStep = false;
      mockStepperConfig.isFirstStep = true;
    });

    it("shows both Save as Draft and Finalise for a DRAFT campaign", async () => {
      renderWithStatus("DRAFT");
      expect(
        await screen.findByRole("button", { name: /buttons.saveAsDraft/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /buttons.finaliseCampaign/i }),
      ).toBeInTheDocument();
    });

    it("shows Finalise but hides Save as Draft for a PLANNED (edited) campaign", async () => {
      renderWithStatus("PLANNED");
      expect(
        await screen.findByRole("button", {
          name: /buttons.finaliseCampaign/i,
        }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /buttons.saveAsDraft/i }),
      ).not.toBeInTheDocument();
    });
  });
});
