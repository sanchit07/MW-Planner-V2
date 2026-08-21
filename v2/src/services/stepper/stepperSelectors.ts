import { createSelector } from "@reduxjs/toolkit";

import { StepState } from "./stepperSlice";
import { RootState } from "../../store";

// Basic selectors
export const selectStepperState = (state: RootState) => state.stepper;

export const selectSteps = (state: RootState) => state.stepper.steps;

export const selectCurrentStepId = (state: RootState) =>
  state.stepper.currentStepId;

export const selectTotalSteps = (state: RootState) => state.stepper.totalSteps;

export const selectProgress = (state: RootState) => state.stepper.progress;

export const selectIsStepperLoading = (state: RootState) =>
  state.stepper.isLoading;

export const selectIsStepperInitialized = (state: RootState) =>
  state.stepper.isInitialized;

// Edit mode selectors
export const selectIsEditMode = (state: RootState) => state.stepper.isEditMode;

export const selectEditCampaignId = (state: RootState) =>
  state.stepper.editCampaignId;

// Inventory filter selectors
export const selectInventoryFilters = (state: RootState) =>
  state.stepper.inventoryFilters;

// Derived selectors
export const selectCurrentStep = createSelector(
  [selectSteps, selectCurrentStepId],
  (steps, currentStepId): StepState | undefined => {
    return steps.find((step) => step.id === currentStepId);
  },
);

export const selectStepById = (stepId: number) =>
  createSelector([selectSteps], (steps): StepState | undefined => {
    return steps.find((step) => step.id === stepId);
  });

export const selectCompletedSteps = createSelector(
  [selectSteps],
  (steps): StepState[] => {
    return steps.filter((step) => step.isCompleted);
  },
);

export const selectAccessibleSteps = createSelector(
  [selectSteps],
  (steps): StepState[] => {
    return steps.filter((step) => step.isAccessible);
  },
);

export const selectIsFirstStep = createSelector(
  [selectSteps, selectCurrentStepId],
  (steps, currentStepId): boolean => {
    return steps.length > 0 && steps[0].id === currentStepId;
  },
);

export const selectIsLastStep = createSelector(
  [selectSteps, selectCurrentStepId],
  (steps, currentStepId): boolean => {
    return steps.length > 0 && steps[steps.length - 1].id === currentStepId;
  },
);

export const selectCanGoNext = createSelector(
  [selectSteps, selectCurrentStep],
  (steps, currentStep): boolean => {
    const currentIndex = steps.findIndex((step) => step.id === currentStep?.id);
    return currentIndex < steps.length - 1;
  },
);

export const selectCanGoPrevious = createSelector(
  [selectSteps, selectCurrentStep],
  (steps, currentStep): boolean => {
    const currentIndex = steps.findIndex((step) => step.id === currentStep?.id);
    return currentIndex > 0;
  },
);

export const selectStepperProgress = createSelector(
  [selectSteps, selectCompletedSteps, selectProgress],
  (steps, completedSteps, progress) => {
    return {
      current: completedSteps.length,
      total: steps.length,
      percentage: progress,
    };
  },
);

// Stepper component data selector
export const selectStepperComponentData = createSelector(
  [selectSteps, selectCurrentStepId, selectProgress, selectIsStepperLoading],
  (steps, currentStepId, progress, isLoading) => {
    return {
      steps: steps.map((step) => ({
        id: step.id,
        title: step.title,
        subtitle: step.subtitle,
        isCompleted: step.isCompleted,
        isCurrent: step.isCurrent,
        isAccessible: step.isAccessible,
        isOptional: step.isOptional,
      })),
      currentStepId,
      progress,
      isLoading,
    };
  },
);
