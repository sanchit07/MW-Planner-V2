import { describe, it, expect } from "vitest";

import {
  initializeStepper,
  goToStep,
  goToNextStep,
  goToPreviousStep,
  markStepCompleted,
  setStepperLoading,
  updateStepAccessibility,
  completeStepper,
  resetStepper,
  clearStepperFormData,
  setEditMode,
  setInventoryFilters,
  clearInventoryFilters,
  StepperState,
} from "../stepperSlice";
import stepperReducer from "../stepperSlice";

// ── Helpers ──────────────────────────────────────────────────────────────────

const defaultFilters = {
  mediaOwners: [],
  venueTypes: [],
  bookingMode: [],
  sizes: [],
  latitude: "",
  longitude: "",
  searchbyquery: "",
  environments: [],
  inventoryClassification: [],
  programmaticSupport: "ALL" as const,
  dealTypes: [],
  cinemaGenres: [],
  cinemaRatings: [],
};

const initialState: StepperState = {
  steps: [],
  currentStepId: 1,
  totalSteps: 0,
  progress: 0,
  isLoading: false,
  isInitialized: false,
  isEditMode: false,
  editCampaignId: null,
  inventoryFilters: defaultFilters,
};

const threeSteps = [
  { id: 1, title: "Step 1" },
  { id: 2, title: "Step 2", isOptional: true },
  { id: 3, title: "Step 3", dependencies: [1, 2] },
];

function withSteps(state: StepperState = initialState) {
  return stepperReducer(state, initializeStepper({ steps: threeSteps }));
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("stepperSlice reducers", () => {
  describe("initializeStepper", () => {
    it("populates steps and sets currentStepId to 1 by default", () => {
      const state = withSteps();
      expect(state.steps).toHaveLength(3);
      expect(state.currentStepId).toBe(1);
      expect(state.isInitialized).toBe(true);
      expect(state.totalSteps).toBe(3);
      expect(state.progress).toBe(0);
    });

    it("makes first step accessible and current", () => {
      const state = withSteps();
      expect(state.steps[0].isCurrent).toBe(true);
      expect(state.steps[0].isAccessible).toBe(true);
      expect(state.steps[1].isCurrent).toBe(false);
    });

    it("respects initialStepId option", () => {
      const state = stepperReducer(
        initialState,
        initializeStepper({ steps: threeSteps, initialStepId: 2 }),
      );
      expect(state.currentStepId).toBe(2);
      expect(state.steps[1].isCurrent).toBe(true);
      expect(state.steps[1].isAccessible).toBe(true);
    });

    it("sets isOptional flag", () => {
      const state = withSteps();
      expect(state.steps[1].isOptional).toBe(true);
      expect(state.steps[0].isOptional).toBe(false);
    });

    it("preserves dependencies array", () => {
      const state = withSteps();
      expect(state.steps[2].dependencies).toEqual([1, 2]);
    });
  });

  describe("goToStep", () => {
    it("moves to an accessible step", () => {
      const initialized = withSteps();
      // Step 2 is not yet accessible initially; make it accessible first
      const withAccessible = stepperReducer(initialized, markStepCompleted(1));
      const state = stepperReducer(withAccessible, goToStep(2));
      expect(state.currentStepId).toBe(2);
      expect(state.steps[1].isCurrent).toBe(true);
      expect(state.steps[0].isCurrent).toBe(false);
    });

    it("is a no-op when step is not accessible", () => {
      const state = stepperReducer(withSteps(), goToStep(3));
      // Step 3 has dependencies [1,2] which aren't completed
      expect(state.currentStepId).toBe(1);
    });

    it("is a no-op when step does not exist", () => {
      const state = stepperReducer(withSteps(), goToStep(99));
      expect(state.currentStepId).toBe(1);
    });
  });

  describe("goToNextStep", () => {
    it("advances to next step and marks current as completed", () => {
      const state = stepperReducer(withSteps(), goToNextStep());
      expect(state.currentStepId).toBe(2);
      expect(state.steps[0].isCompleted).toBe(true);
      expect(state.steps[0].isCurrent).toBe(false);
      expect(state.steps[1].isCurrent).toBe(true);
      expect(state.steps[1].isAccessible).toBe(true);
    });

    it("updates progress after advancing", () => {
      const state = stepperReducer(withSteps(), goToNextStep());
      expect(state.progress).toBeGreaterThan(0);
    });

    it("is a no-op when on last step", () => {
      let state = withSteps();
      state = stepperReducer(state, goToNextStep());
      state = stepperReducer(state, goToNextStep());
      const beforeId = state.currentStepId;
      state = stepperReducer(state, goToNextStep());
      expect(state.currentStepId).toBe(beforeId);
    });
  });

  describe("goToPreviousStep", () => {
    it("goes back to the previous step", () => {
      let state = stepperReducer(withSteps(), goToNextStep());
      state = stepperReducer(state, goToPreviousStep());
      expect(state.currentStepId).toBe(1);
      expect(state.steps[0].isCurrent).toBe(true);
    });

    it("is a no-op when on first step", () => {
      const state = stepperReducer(withSteps(), goToPreviousStep());
      expect(state.currentStepId).toBe(1);
    });
  });

  describe("markStepCompleted", () => {
    it("marks a step as completed and makes next accessible", () => {
      const state = stepperReducer(withSteps(), markStepCompleted(1));
      expect(state.steps[0].isCompleted).toBe(true);
      expect(state.steps[1].isAccessible).toBe(true);
    });

    it("updates progress", () => {
      const state = stepperReducer(withSteps(), markStepCompleted(1));
      expect(state.progress).toBeGreaterThan(0);
    });

    it("is a no-op for non-existent step id", () => {
      const state = stepperReducer(withSteps(), markStepCompleted(99));
      expect(state.steps.every((s) => !s.isCompleted)).toBe(true);
    });

    it("does not break when step has no next step", () => {
      let state = withSteps();
      state = stepperReducer(state, markStepCompleted(1));
      state = stepperReducer(state, markStepCompleted(2));
      const before = state.steps[2].isAccessible;
      state = stepperReducer(state, markStepCompleted(3));
      expect(state.steps[2].isCompleted).toBe(true);
      expect(state.steps[2].isAccessible).toBe(before);
    });
  });

  describe("setStepperLoading", () => {
    it("sets loading to true", () => {
      const state = stepperReducer(initialState, setStepperLoading(true));
      expect(state.isLoading).toBe(true);
    });

    it("sets loading to false", () => {
      const dirty = { ...initialState, isLoading: true };
      const state = stepperReducer(dirty, setStepperLoading(false));
      expect(state.isLoading).toBe(false);
    });
  });

  describe("updateStepAccessibility", () => {
    it("makes step accessible when all dependencies are completed", () => {
      let state = withSteps();
      state = stepperReducer(state, markStepCompleted(1));
      state = stepperReducer(state, markStepCompleted(2));
      state = stepperReducer(state, updateStepAccessibility());
      expect(state.steps[2].isAccessible).toBe(true);
    });

    it("step 1 is always accessible", () => {
      const state = stepperReducer(withSteps(), updateStepAccessibility());
      expect(state.steps[0].isAccessible).toBe(true);
    });
  });

  describe("completeStepper", () => {
    it("marks all steps completed and progress = 100", () => {
      const state = stepperReducer(withSteps(), completeStepper());
      expect(state.steps.every((s) => s.isCompleted)).toBe(true);
      expect(state.progress).toBe(100);
    });
  });

  describe("resetStepper", () => {
    it("resets all state to initial values", () => {
      let state = withSteps();
      state = stepperReducer(
        state,
        setEditMode({ isEditMode: true, campaignId: "c1" }),
      );
      state = stepperReducer(state, resetStepper());
      expect(state.steps).toHaveLength(0);
      expect(state.currentStepId).toBe(1);
      expect(state.isInitialized).toBe(false);
      expect(state.isEditMode).toBe(false);
      expect(state.editCampaignId).toBeNull();
      expect(state.inventoryFilters).toEqual(defaultFilters);
    });
  });

  describe("clearStepperFormData", () => {
    it("resets step completion state but keeps structure", () => {
      let state = stepperReducer(withSteps(), goToNextStep());
      state = stepperReducer(state, clearStepperFormData());
      expect(state.steps).toHaveLength(3);
      expect(state.steps[0].isCompleted).toBe(false);
      expect(state.steps[0].isCurrent).toBe(true);
      expect(state.currentStepId).toBe(1);
      expect(state.progress).toBe(0);
      expect(state.isEditMode).toBe(false);
      expect(state.editCampaignId).toBeNull();
    });

    it("only step 1 is accessible after clear", () => {
      let state = stepperReducer(withSteps(), goToNextStep());
      state = stepperReducer(state, clearStepperFormData());
      expect(state.steps[0].isAccessible).toBe(true);
      expect(state.steps[1].isAccessible).toBe(false);
    });
  });

  describe("setEditMode", () => {
    it("sets edit mode with campaign id", () => {
      const state = stepperReducer(
        initialState,
        setEditMode({ isEditMode: true, campaignId: "camp-123" }),
      );
      expect(state.isEditMode).toBe(true);
      expect(state.editCampaignId).toBe("camp-123");
    });

    it("uses null when no campaignId provided", () => {
      const state = stepperReducer(
        initialState,
        setEditMode({ isEditMode: false }),
      );
      expect(state.editCampaignId).toBeNull();
    });
  });

  describe("setInventoryFilters", () => {
    it("replaces inventory filters", () => {
      const filters = {
        ...defaultFilters,
        mediaOwners: ["owner-1"],
        venueTypes: ["MALL"],
      };
      const state = stepperReducer(initialState, setInventoryFilters(filters));
      expect(state.inventoryFilters.mediaOwners).toEqual(["owner-1"]);
      expect(state.inventoryFilters.venueTypes).toEqual(["MALL"]);
    });
  });

  describe("clearInventoryFilters", () => {
    it("resets filters back to defaults", () => {
      const dirty = {
        ...initialState,
        inventoryFilters: { ...defaultFilters, mediaOwners: ["owner-1"] },
      };
      const state = stepperReducer(dirty, clearInventoryFilters());
      expect(state.inventoryFilters).toEqual(defaultFilters);
    });
  });
});
