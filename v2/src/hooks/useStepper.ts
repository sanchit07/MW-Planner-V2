import { useCallback } from "react";
import { useDispatch, useSelector } from "react-redux";

import {
  selectStepperState,
  selectCurrentStep,
  selectCurrentStepId,
  selectSteps,
  selectIsFirstStep,
  selectIsLastStep,
  selectCanGoNext,
  selectCanGoPrevious,
  selectStepperComponentData,
  selectStepperProgress,
  selectIsStepperLoading,
  selectIsStepperInitialized,
  selectIsEditMode,
  selectEditCampaignId,
} from "../services/stepper/stepperSelectors";
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
} from "../services/stepper/stepperSlice";
import { AppDispatch } from "../store";

// Hook for stepper management
export const useStepper = () => {
  const dispatch = useDispatch<AppDispatch>();

  // Selectors
  const stepperState = useSelector(selectStepperState);
  const currentStep = useSelector(selectCurrentStep);
  const currentStepId = useSelector(selectCurrentStepId);
  const steps = useSelector(selectSteps);
  const isFirstStep = useSelector(selectIsFirstStep);
  const isLastStep = useSelector(selectIsLastStep);
  const canGoNext = useSelector(selectCanGoNext);
  const canGoPrevious = useSelector(selectCanGoPrevious);
  const stepperComponentData = useSelector(selectStepperComponentData);
  const progress = useSelector(selectStepperProgress);
  const isLoading = useSelector(selectIsStepperLoading);
  const isInitialized = useSelector(selectIsStepperInitialized);
  const isEditMode = useSelector(selectIsEditMode);
  const editCampaignId = useSelector(selectEditCampaignId);

  // Actions
  const initialize = useCallback(
    (config: {
      steps: Array<{
        id: number;
        title: string;
        subtitle?: string;
        isOptional?: boolean;
        dependencies?: number[];
      }>;
      initialStepId?: number;
    }) => {
      dispatch(initializeStepper(config));
    },
    [dispatch],
  );

  const navigateToStep = useCallback(
    (stepId: number) => {
      dispatch(goToStep(stepId));
    },
    [dispatch],
  );

  const nextStep = useCallback(() => {
    dispatch(goToNextStep());
  }, [dispatch]);

  const previousStep = useCallback(() => {
    dispatch(goToPreviousStep());
  }, [dispatch]);

  const markCompleted = useCallback(
    (stepId: number) => {
      dispatch(markStepCompleted(stepId));
    },
    [dispatch],
  );

  const setLoading = useCallback(
    (loading: boolean) => {
      dispatch(setStepperLoading(loading));
    },
    [dispatch],
  );

  const updateAccessibility = useCallback(() => {
    dispatch(updateStepAccessibility());
  }, [dispatch]);

  const complete = useCallback(() => {
    dispatch(completeStepper());
  }, [dispatch]);

  const reset = useCallback(() => {
    dispatch(resetStepper());
  }, [dispatch]);

  const clearFormData = useCallback(() => {
    dispatch(clearStepperFormData());
  }, [dispatch]);

  const setStepperEditMode = useCallback(
    (isEditMode: boolean, campaignId?: string) => {
      dispatch(setEditMode({ isEditMode, campaignId }));
    },
    [dispatch],
  );

  return {
    // State
    stepperState,
    currentStep,
    currentStepId,
    steps,
    isFirstStep,
    isLastStep,
    canGoNext,
    canGoPrevious,
    stepperComponentData,
    progress,
    isLoading,
    isInitialized,
    isEditMode,
    editCampaignId,

    // Actions
    initialize,
    navigateToStep,
    nextStep,
    previousStep,
    markCompleted,
    setLoading,
    updateAccessibility,
    complete,
    reset,
    clearFormData,
    setStepperEditMode,
  };
};
