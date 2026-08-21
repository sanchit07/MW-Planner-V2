import { createSlice, PayloadAction } from "@reduxjs/toolkit";

import { InventoryFilters } from "../../types/inventory.types";

// Step state interface
export interface StepState {
  id: number;
  title: string;
  subtitle?: string;
  isCompleted: boolean;
  isAccessible: boolean;
  isCurrent: boolean;
  isOptional?: boolean;
  dependencies?: number[];
}

// Stepper state interface
export interface StepperState {
  steps: StepState[];
  currentStepId: number;
  totalSteps: number;
  progress: number;
  isLoading: boolean;
  isInitialized: boolean;
  // Edit mode state
  isEditMode: boolean;
  editCampaignId: string | null;
  // Inventory filter state
  inventoryFilters: InventoryFilters;
}

// Initial state
const initialState: StepperState = {
  steps: [],
  currentStepId: 1,
  totalSteps: 0,
  progress: 0,
  isLoading: false,
  isInitialized: false,
  // Edit mode initial state
  isEditMode: false,
  editCampaignId: null,
  // Inventory filter initial state
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
    cinemaGenres: [],
    cinemaRatings: [],
  },
};

// Stepper slice
const stepperSlice = createSlice({
  name: "stepper",
  initialState,
  reducers: {
    // Initialize stepper with steps configuration
    initializeStepper: (
      state,
      action: PayloadAction<{
        steps: Array<{
          id: number;
          title: string;
          subtitle?: string;
          isOptional?: boolean;
          dependencies?: number[];
        }>;
        initialStepId?: number;
      }>,
    ) => {
      const { steps, initialStepId = 1 } = action.payload;

      state.steps = steps.map((step) => ({
        id: step.id,
        title: step.title,
        subtitle: step.subtitle,
        isCompleted: false,
        isAccessible: step.id === initialStepId || step.id === 1,
        isCurrent: step.id === initialStepId,
        isOptional: step.isOptional || false,
        dependencies: step.dependencies,
      }));

      state.currentStepId = initialStepId;
      state.totalSteps = steps.length;
      state.progress = 0;
      state.isInitialized = true;
    },

    // Navigate to a specific step
    goToStep: (state, action: PayloadAction<number>) => {
      const targetStepId = action.payload;
      const targetStep = state.steps.find((step) => step.id === targetStepId);

      if (targetStep && targetStep.isAccessible) {
        // Update current step
        state.steps.forEach((step) => {
          step.isCurrent = step.id === targetStepId;
        });

        state.currentStepId = targetStepId;
      }
    },

    // Go to next step
    goToNextStep: (state) => {
      const currentIndex = state.steps.findIndex(
        (step) => step.id === state.currentStepId,
      );
      const nextStep = state.steps[currentIndex + 1];

      if (nextStep) {
        // Mark current step as completed
        const currentStep = state.steps[currentIndex];
        if (currentStep) {
          currentStep.isCompleted = true;
          currentStep.isCurrent = false;
        }

        // Move to next step
        nextStep.isCurrent = true;
        nextStep.isAccessible = true;
        state.currentStepId = nextStep.id;

        // Update progress
        const completedSteps = state.steps.filter(
          (step) => step.isCompleted,
        ).length;
        state.progress = Math.round((completedSteps / state.totalSteps) * 100);
      }
    },

    // Go to previous step
    goToPreviousStep: (state) => {
      const currentIndex = state.steps.findIndex(
        (step) => step.id === state.currentStepId,
      );
      const previousStep = state.steps[currentIndex - 1];

      if (previousStep) {
        // Update current step
        state.steps.forEach((step) => {
          step.isCurrent = step.id === previousStep.id;
        });

        state.currentStepId = previousStep.id;
      }
    },

    // Mark step as completed
    markStepCompleted: (state, action: PayloadAction<number>) => {
      const stepId = action.payload;
      const step = state.steps.find((s) => s.id === stepId);

      if (step) {
        step.isCompleted = true;

        // Make next step accessible if it exists
        const currentIndex = state.steps.findIndex((s) => s.id === stepId);
        const nextStep = state.steps[currentIndex + 1];
        if (nextStep) {
          nextStep.isAccessible = true;
        }

        // Update progress
        const completedSteps = state.steps.filter((s) => s.isCompleted).length;
        state.progress = Math.round((completedSteps / state.totalSteps) * 100);
      }
    },

    // Set loading state
    setStepperLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },

    // Update step accessibility based on dependencies
    updateStepAccessibility: (state) => {
      state.steps.forEach((step) => {
        // If step has no dependencies or all dependencies are completed, make it accessible
        const dependencies = step.id === 1 ? [] : step.dependencies || []; // Simple dependency: previous step
        const allDependenciesCompleted = dependencies.every((depId) => {
          const depStep = state.steps.find((s) => s.id === depId);
          return depStep?.isCompleted || false;
        });

        if (step.id === 1 || allDependenciesCompleted) {
          step.isAccessible = true;
        }
      });
    },

    // Complete stepper (mark all as completed)
    completeStepper: (state) => {
      state.steps.forEach((step) => {
        step.isCompleted = true;
      });
      state.progress = 100;
    },

    // Reset stepper state
    resetStepper: (state) => {
      state.steps = [];
      state.currentStepId = 1;
      state.totalSteps = 0;
      state.progress = 0;
      state.isLoading = false;
      state.isInitialized = false;
      state.isEditMode = false;
      state.editCampaignId = null;
      state.inventoryFilters = {
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
        cinemaGenres: [],
        cinemaRatings: [],
      };
    },

    // Clear stepper data (keep step structure)
    clearStepperFormData: (state) => {
      state.steps.forEach((step) => {
        step.isCompleted = false;
        step.isAccessible = step.id === 1;
        step.isCurrent = step.id === 1;
      });
      state.currentStepId = 1;
      state.progress = 0;
      // Reset edit mode
      state.isEditMode = false;
      state.editCampaignId = null;
    },

    // Set edit mode
    setEditMode: (
      state,
      action: PayloadAction<{ isEditMode: boolean; campaignId?: string }>,
    ) => {
      const { isEditMode, campaignId } = action.payload;
      state.isEditMode = isEditMode;
      state.editCampaignId = campaignId || null;
    },

    // Set inventory filters
    setInventoryFilters: (state, action: PayloadAction<InventoryFilters>) => {
      state.inventoryFilters = action.payload;
    },

    // Clear inventory filters
    clearInventoryFilters: (state) => {
      state.inventoryFilters = {
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
        cinemaGenres: [],
        cinemaRatings: [],
      };
    },
  },
});

// Export actions
export const {
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
} = stepperSlice.actions;

// Export reducer
export default stepperSlice.reducer;
