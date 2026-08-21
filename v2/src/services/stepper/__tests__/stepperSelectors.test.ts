import { describe, it, expect } from "vitest";

import {
  selectStepperState,
  selectSteps,
  selectCurrentStepId,
  selectTotalSteps,
  selectProgress,
  selectIsStepperLoading,
  selectIsStepperInitialized,
  selectIsEditMode,
  selectEditCampaignId,
  selectInventoryFilters,
  selectCurrentStep,
  selectStepById,
  selectCompletedSteps,
  selectAccessibleSteps,
  selectIsFirstStep,
  selectIsLastStep,
  selectCanGoNext,
  selectCanGoPrevious,
  selectStepperProgress,
  selectStepperComponentData,
} from "../stepperSelectors";
import { StepperState, StepState } from "../stepperSlice";

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
};

const makeStep = (overrides: Partial<StepState> = {}): StepState => ({
  id: 1,
  title: "Step 1",
  isCompleted: false,
  isAccessible: true,
  isCurrent: true,
  isOptional: false,
  ...overrides,
});

const makeState = (overrides: Partial<StepperState> = {}) =>
  ({
    stepper: {
      steps: [],
      currentStepId: 1,
      totalSteps: 0,
      progress: 0,
      isLoading: false,
      isInitialized: false,
      isEditMode: false,
      editCampaignId: null,
      inventoryFilters: defaultFilters,
      ...overrides,
    } as StepperState,
    // RootState has other keys that selectors don't access
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  }) as any;

// ── Basic selectors ──────────────────────────────────────────────────────────

describe("basic stepper selectors", () => {
  it("selectStepperState returns the stepper slice", () => {
    const state = makeState({ isLoading: true });
    expect(selectStepperState(state).isLoading).toBe(true);
  });

  it("selectSteps returns steps array", () => {
    const steps = [makeStep()];
    const state = makeState({ steps });
    expect(selectSteps(state)).toHaveLength(1);
  });

  it("selectCurrentStepId returns currentStepId", () => {
    expect(selectCurrentStepId(makeState({ currentStepId: 3 }))).toBe(3);
  });

  it("selectTotalSteps returns totalSteps", () => {
    expect(selectTotalSteps(makeState({ totalSteps: 5 }))).toBe(5);
  });

  it("selectProgress returns progress", () => {
    expect(selectProgress(makeState({ progress: 50 }))).toBe(50);
  });

  it("selectIsStepperLoading returns isLoading", () => {
    expect(selectIsStepperLoading(makeState({ isLoading: true }))).toBe(true);
  });

  it("selectIsStepperInitialized returns isInitialized", () => {
    expect(selectIsStepperInitialized(makeState({ isInitialized: true }))).toBe(
      true,
    );
  });

  it("selectIsEditMode returns isEditMode", () => {
    expect(selectIsEditMode(makeState({ isEditMode: true }))).toBe(true);
  });

  it("selectEditCampaignId returns editCampaignId", () => {
    expect(selectEditCampaignId(makeState({ editCampaignId: "c-999" }))).toBe(
      "c-999",
    );
  });

  it("selectInventoryFilters returns inventoryFilters", () => {
    const filters = { ...defaultFilters, mediaOwners: ["mo-1"] };
    expect(
      selectInventoryFilters(makeState({ inventoryFilters: filters })),
    ).toEqual(filters);
  });
});

// ── Derived selectors ────────────────────────────────────────────────────────

describe("derived stepper selectors", () => {
  describe("selectCurrentStep", () => {
    it("returns the step matching currentStepId", () => {
      const steps = [
        makeStep({ id: 1 }),
        makeStep({ id: 2, isCurrent: false }),
      ];
      const state = makeState({ steps, currentStepId: 1 });
      expect(selectCurrentStep(state)?.id).toBe(1);
    });

    it("returns undefined when no matching step", () => {
      const state = makeState({ steps: [], currentStepId: 5 });
      expect(selectCurrentStep(state)).toBeUndefined();
    });
  });

  describe("selectStepById", () => {
    it("returns the matching step", () => {
      const steps = [makeStep({ id: 1 }), makeStep({ id: 2, title: "Two" })];
      const state = makeState({ steps });
      expect(selectStepById(2)(state)?.title).toBe("Two");
    });

    it("returns undefined when id not found", () => {
      const state = makeState({ steps: [makeStep({ id: 1 })] });
      expect(selectStepById(99)(state)).toBeUndefined();
    });
  });

  describe("selectCompletedSteps", () => {
    it("returns only completed steps", () => {
      const steps = [
        makeStep({ id: 1, isCompleted: true }),
        makeStep({ id: 2, isCompleted: false }),
      ];
      const state = makeState({ steps });
      expect(selectCompletedSteps(state)).toHaveLength(1);
      expect(selectCompletedSteps(state)[0].id).toBe(1);
    });

    it("returns empty array when none completed", () => {
      const state = makeState({ steps: [makeStep()] });
      expect(selectCompletedSteps(state)).toHaveLength(0);
    });
  });

  describe("selectAccessibleSteps", () => {
    it("returns only accessible steps", () => {
      const steps = [
        makeStep({ id: 1, isAccessible: true }),
        makeStep({ id: 2, isAccessible: false }),
      ];
      const state = makeState({ steps });
      expect(selectAccessibleSteps(state)).toHaveLength(1);
    });
  });

  describe("selectIsFirstStep", () => {
    it("returns true when on first step", () => {
      const steps = [makeStep({ id: 1 }), makeStep({ id: 2 })];
      expect(selectIsFirstStep(makeState({ steps, currentStepId: 1 }))).toBe(
        true,
      );
    });

    it("returns false when not on first step", () => {
      const steps = [makeStep({ id: 1 }), makeStep({ id: 2 })];
      expect(selectIsFirstStep(makeState({ steps, currentStepId: 2 }))).toBe(
        false,
      );
    });

    it("returns false with empty steps", () => {
      expect(
        selectIsFirstStep(makeState({ steps: [], currentStepId: 1 })),
      ).toBe(false);
    });
  });

  describe("selectIsLastStep", () => {
    it("returns true when on last step", () => {
      const steps = [makeStep({ id: 1 }), makeStep({ id: 2 })];
      expect(selectIsLastStep(makeState({ steps, currentStepId: 2 }))).toBe(
        true,
      );
    });

    it("returns false when not on last step", () => {
      const steps = [makeStep({ id: 1 }), makeStep({ id: 2 })];
      expect(selectIsLastStep(makeState({ steps, currentStepId: 1 }))).toBe(
        false,
      );
    });

    it("returns false with empty steps", () => {
      expect(selectIsLastStep(makeState({ steps: [] }))).toBe(false);
    });
  });

  describe("selectCanGoNext", () => {
    it("returns true when there is a next step", () => {
      const steps = [
        makeStep({ id: 1 }),
        makeStep({ id: 2, isCurrent: false }),
      ];
      const state = makeState({ steps, currentStepId: 1 });
      expect(selectCanGoNext(state)).toBe(true);
    });

    it("returns false on last step", () => {
      const steps = [makeStep({ id: 1 })];
      const state = makeState({ steps, currentStepId: 1 });
      expect(selectCanGoNext(state)).toBe(false);
    });

    it("returns false with no current step", () => {
      const state = makeState({ steps: [], currentStepId: 5 });
      expect(selectCanGoNext(state)).toBe(false);
    });
  });

  describe("selectCanGoPrevious", () => {
    it("returns true when there is a previous step", () => {
      const steps = [
        makeStep({ id: 1, isCurrent: false }),
        makeStep({ id: 2 }),
      ];
      const state = makeState({ steps, currentStepId: 2 });
      expect(selectCanGoPrevious(state)).toBe(true);
    });

    it("returns false on first step", () => {
      const steps = [makeStep({ id: 1 })];
      const state = makeState({ steps, currentStepId: 1 });
      expect(selectCanGoPrevious(state)).toBe(false);
    });
  });

  describe("selectStepperProgress", () => {
    it("returns current, total and percentage", () => {
      const steps = [
        makeStep({ id: 1, isCompleted: true }),
        makeStep({ id: 2, isCompleted: false }),
      ];
      const result = selectStepperProgress(makeState({ steps, progress: 50 }));
      expect(result.current).toBe(1);
      expect(result.total).toBe(2);
      expect(result.percentage).toBe(50);
    });
  });

  describe("selectStepperComponentData", () => {
    it("returns mapped step data for UI consumption", () => {
      const steps = [makeStep({ id: 1, subtitle: "sub" })];
      const result = selectStepperComponentData(
        makeState({ steps, currentStepId: 1, progress: 0, isLoading: false }),
      );
      expect(result.currentStepId).toBe(1);
      expect(result.steps[0].id).toBe(1);
      expect(result.steps[0].subtitle).toBe("sub");
      expect(result.isLoading).toBe(false);
    });
  });
});
