import { useAnnounce } from "@hooks/useAnnounce";
import { useUpdateCampaignMutation } from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import {
  ArrowLeft,
  ChevronLeft,
  Megaphone,
  DollarSign,
  ChevronRight,
} from "lucide-react";
import React, {
  useState,
  useEffect,
  useMemo,
  useRef,
  useCallback,
} from "react";
import { useNavigate, useParams } from "react-router-dom";

import BudgetAndGoalForm from "./BudgetAndGoalPage";
import CreateCampaignForm from "./CreateCampaignForm";
import InventoryPageForm from "./inventory/InventoryPageForm";
import OptimizationForm from "./optimization/Optimization";
import TargetingForm from "./TargetingForm";
import PageHeader from "../../components/PageHeader";
import { Button } from "../../components/ui/Button";
import { Spinner } from "../../components/ui/Spinner";
import Stepper from "../../components/ui/Stepper";
import { useAutosave } from "../../hooks/useAutosave";
import { useStepper } from "../../hooks/useStepper";
import {
  useLazyGetCampaignQuery,
  setCampaignData,
  setIsEditMode,
  setCampaignId,
} from "../../services/campaign/campaignSlice";
import { clearInventoryFilters } from "../../services/stepper/stepperSlice";
import { useAppSelector, useAppDispatch } from "../../store";
import {
  StepConfig,
  StepFormRef,
  CampaignCreateResponse,
} from "../../types/campaign.types";
import storage from "../../utils/storage";

// Import step components

// LocalStorage key for storing stepper step in edit mode
const STEPPER_STEP_STORAGE_KEY = "campaign_stepper_step";
const INVENTORY_FILTERS_STORAGE_KEY = "inventory_filters";

interface CampaignWrapperProps {
  initialStep?: number;
}

const CampaignWrapper: React.FC<CampaignWrapperProps> = ({
  initialStep = 1,
}) => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  const { campaignId: campaignIdFromUrl } = useParams<{ campaignId: string }>();
  const { showError, showSuccess } = useAnnounce();
  const [updateCampaign] = useUpdateCampaignMutation();

  const [isLoading, setIsLoading] = useState(false);

  // Autosave hook with loading state
  const { isAutosaving } = useAutosave();

  // Ref to access current step form methods
  const currentStepFormRef = useRef<StepFormRef>(null);

  // Get campaign ID from URL if in edit mode
  const isEditModeFromUrl = !!campaignIdFromUrl;

  // API hook for fetching campaign data
  const [getCampaign] = useLazyGetCampaignQuery();

  // Initialize Redux stepper - single source of truth
  const {
    initialize,
    stepperComponentData,
    currentStepId,
    isFirstStep,
    isLastStep,
    nextStep,
    previousStep,
    navigateToStep,
    markCompleted,
    setLoading,
    clearFormData,
    isInitialized,
    setStepperEditMode,
    updateAccessibility,
  } = useStepper();

  // Campaign state from Redux
  const campaignState = useAppSelector((state) => state.campaign);

  // Detect company switch and exit campaign creation to avoid mismatched company context
  const user = useAppSelector((state) => state.profile.profile);
  const activeCompanyId = user?.activeCompanyId || "";
  const initialCompanyIdRef = useRef<string | null>(null);
  useEffect(() => {
    if (!activeCompanyId) return;
    if (initialCompanyIdRef.current === null) {
      initialCompanyIdRef.current = activeCompanyId;
      return;
    }
    if (activeCompanyId !== initialCompanyIdRef.current) {
      showError(tCampaigns("campaignWrapper.errors.companySwitched"));
      navigate("/campaigns");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCompanyId]);

  // Define all steps with enhanced configuration
  const steps: StepConfig[] = useMemo(
    () => [
      {
        id: 1,
        title: tCampaigns("create_campaign.title"),
        subtitle: tCampaigns("create_campaign.subtitle"),
        component: CreateCampaignForm,
        validation: {
          required: true,
        },
        metadata: {
          description: "Basic campaign information",
          icon: Megaphone,
        },
      },
      {
        id: 2,
        title: tCampaigns("budget_goal.title"),
        subtitle: tCampaigns("budget_goal.subtitle"),
        component: BudgetAndGoalForm,
        validation: {
          required: true,
        },
        dependencies: [1],
        metadata: {
          description: tCampaigns("budget_goal.metadata.description"),
          icon: DollarSign,
        },
      },
      {
        id: 3,
        title: tCampaigns("targeting.title"),
        subtitle: tCampaigns("targeting.description"),
        component: TargetingForm,
        canSkip: true,
        validation: {
          required: false,
        },
        dependencies: [1, 2], // Must complete step 1 first
      },
      {
        id: 4,
        title: tCampaigns("inventories.title"),
        subtitle: tCampaigns("inventories.description"),
        component: InventoryPageForm,
        validation: {
          required: false,
        },
        dependencies: [1, 2],
        metadata: {
          description: "Inventory selection",
          icon: DollarSign,
        },
      },
      {
        id: 5,
        title: tCampaigns("optimization.title"),
        subtitle: tCampaigns("optimization.subtitle"),
        component: OptimizationForm,
        validation: {
          required: false,
        },
        canSkip: true,
        dependencies: [1, 2, 4],
        metadata: {
          description: "Inventory selection",
          icon: DollarSign,
        },
      },
    ],
    [tCampaigns],
  );

  // Function to determine which steps are completed based on campaign data
  const determineCompletedSteps = useCallback(
    (campaignData: CampaignCreateResponse) => {
      const completedSteps: number[] = [];

      // Step 1: Campaign Details - Check basic campaign info
      if (
        campaignData.name &&
        campaignData.startDate &&
        campaignData.endDate &&
        campaignData.clientType
      ) {
        completedSteps.push(1);
      }
      // Step 2: Budget & Goals - Check budget and goals
      if (campaignData.countryId && campaignData.currency) {
        completedSteps.push(2);
      }

      // Step 3: Targeting - Check if targeting data exists
      if (campaignData.targeting) {
        completedSteps.push(3);
      }

      // Step 4: Inventory - Check if inventories are selected
      if (campaignData?.inventoryCount && campaignData.inventoryCount > 0) {
        completedSteps.push(3);
        completedSteps.push(4);
      }

      return completedSteps;
    },
    [],
  );

  // Function to reload campaign data (can be called from child components)
  const reloadCampaignData = useCallback(async () => {
    if (!campaignIdFromUrl && !campaignState.campaignId) {
      return;
    }

    const campaignIdToLoad = campaignIdFromUrl || campaignState.campaignId;
    if (!campaignIdToLoad) return;

    try {
      const result = await getCampaign(campaignIdToLoad).unwrap();

      if (result.success && result.data) {
        // Set campaign data in Redux
        dispatch(setCampaignData(result.data));
      }
    } catch (error) {
      console.error("Error reloading campaign data:", error);
    }
  }, [campaignIdFromUrl, campaignState.campaignId, getCampaign, dispatch]);

  // Load campaign data if in edit mode (from URL)
  useEffect(() => {
    const loadCampaignData = async () => {
      if (isEditModeFromUrl && campaignIdFromUrl && isInitialized) {
        dispatch(setIsEditMode(true));

        try {
          const result = await getCampaign(campaignIdFromUrl).unwrap();

          if (result.success && result.data) {
            // Set campaign data in Redux
            dispatch(setCampaignData(result.data));
            dispatch(setCampaignId(campaignIdFromUrl));

            // Set stepper to edit mode
            setStepperEditMode(true, campaignIdFromUrl);

            // Determine which steps are completed based on campaign data
            const completedSteps = determineCompletedSteps(result.data);

            // Mark completed steps in the stepper
            completedSteps.forEach((stepId: number) => {
              markCompleted(stepId);
            });

            updateAccessibility();

            // Navigate to stored step if available and accessible
            const storedStep = loadStepFromStorage();
            if (storedStep && storedStep !== currentStepId) {
              const targetStep = stepperComponentData.steps.find(
                (s: { id: number }) => s.id === storedStep,
              );
              if (targetStep?.isAccessible) {
                setTimeout(() => {
                  navigateToStep(storedStep);
                }, 100);
              }
            }
          }
        } catch (error) {
          console.error("Error loading campaign:", error);
          showError(tCampaigns("campaignWrapper.errors.failedToLoadData"));
        }
      }
    };

    loadCampaignData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEditModeFromUrl, campaignIdFromUrl, isInitialized]);

  // Effective campaign id — from the URL (edit mode) or Redux (create flow,
  // once the campaign has been created). Used to persist/restore the current
  // step so leaving the wizard (e.g. opening the full-page View) and coming
  // back restores the same step in BOTH edit and create flows.
  const effectiveCampaignId =
    campaignIdFromUrl ||
    campaignState.campaignId ||
    campaignState.campaignData?.id ||
    "";

  // Load the persisted step for the current campaign.
  const loadStepFromStorage = useCallback((): number => {
    if (effectiveCampaignId) {
      try {
        const stored = storage.getItem(STEPPER_STEP_STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          // Only use stored step if it matches the current campaign ID
          if (parsed.campaignId === effectiveCampaignId && parsed.step) {
            return parsed.step;
          }
        }
      } catch (error) {
        console.error("Error loading stepper step from storage:", error);
      }
    }
    return initialStep;
  }, [effectiveCampaignId, initialStep]);

  // Persist the current step for the current campaign.
  const saveStepToStorage = useCallback(
    (step: number) => {
      if (effectiveCampaignId) {
        try {
          storage.setItem(
            STEPPER_STEP_STORAGE_KEY,
            JSON.stringify({
              campaignId: effectiveCampaignId,
              step: step,
            }),
          );
        } catch (error) {
          console.error("Error saving stepper step to storage:", error);
        }
      }
    },
    [effectiveCampaignId],
  );

  // Clear step from localStorage
  const clearStepFromStorage = useCallback(() => {
    try {
      storage.removeItem(STEPPER_STEP_STORAGE_KEY);
    } catch (error) {
      console.error("Error clearing stepper step from storage:", error);
    }
  }, []);

  // Initialize stepper on component mount
  useEffect(() => {
    if (!isInitialized) {
      const stepToLoad = loadStepFromStorage();
      initialize({
        steps: steps.map((step) => ({
          id: step.id,
          title: step.title,
          subtitle: step.subtitle,
          isOptional: step.canSkip,
          dependencies: step.dependencies,
        })),
        initialStepId: stepToLoad,
      });
    }
  }, [initialize, isInitialized, loadStepFromStorage, steps]);

  // Use Redux state for current step
  const currentStep = currentStepId;
  const currentStepConfig = steps.find((step) => step.id === currentStep);

  // The "Next" button label comes from the step form's getNextLabel(). Reading it
  // off the ref during render showed the *previous* step's label, because the ref
  // only reattaches after commit and React 18 batches the step change with the
  // isLoading reset into one render (e.g. "Next: Demographics" while already on
  // Demographics). We instead capture the label in a callback ref and store it in
  // state. Some step forms recreate their imperative handle every render (unstable
  // deps), so this callback can fire on every render — we therefore only setState
  // when the label VALUE actually changes, which both prevents an infinite update
  // loop and makes sub-step label changes (Targeting tabs) reactive. (DEF-PLN-006)
  const [nextLabel, setNextLabel] = useState<string | undefined>(undefined);
  const setStepFormRef = useCallback((instance: StepFormRef | null) => {
    currentStepFormRef.current = instance;
    const label = instance?.getNextLabel?.();
    setNextLabel((prev) => (prev === label ? prev : label));
  }, []);

  // Step 4 (Inventories): block advancing until at least one inventory is
  // selected. Count comes from the forecast data the inventory step dispatches
  // to Redux (mirrors InventoryPageForm's own validateStep check).
  const selectedInventoryCount =
    campaignState.forecastData?.totalInventories ?? 0;
  const isInventoryStepEmpty =
    currentStep === 4 && selectedInventoryCount === 0;

  // Clear storage when campaign ID changes (user navigates to edit different campaign)
  useEffect(() => {
    if (isEditModeFromUrl && campaignIdFromUrl) {
      // Check if stored step is for a different campaign
      try {
        const stored = storage.getItem(STEPPER_STEP_STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          if (parsed.campaignId && parsed.campaignId !== campaignIdFromUrl) {
            // Clear storage for the old campaign
            clearStepFromStorage();
          }
        }
      } catch (error) {
        console.error("Error clearing stepper step from storage:", error);
        // Ignore errors
      }
    }
  }, [campaignIdFromUrl, isEditModeFromUrl, clearStepFromStorage]);

  // Create stepper steps that combine fresh translated content with Redux state
  const stepperSteps = useMemo(() => {
    return steps.map((step) => {
      // Find corresponding Redux step data
      const reduxStep = stepperComponentData.steps.find(
        (s) => s.id === step.id,
      );

      return {
        id: step.id,
        title: step.title, // Use fresh translated title
        subtitle: step.subtitle, // Use fresh translated subtitle
        isCompleted: reduxStep?.isCompleted || false,
        isAccessible: reduxStep?.isAccessible || false,
        isCurrent: reduxStep?.isCurrent || false,
        isOptional: step.canSkip || false,
        dependencies: step.dependencies || [],
      };
    });
  }, [steps, stepperComponentData.steps]);

  // Clear Redux state when component unmounts
  // Note: We don't clear localStorage on unmount to preserve step on refresh
  useEffect(() => {
    return () => {
      clearFormData();
      // Clear inventory filters from Redux state and local storage on unmount
      dispatch(clearInventoryFilters());
      dispatch(setIsEditMode(false));
      dispatch(setCampaignId(null));
      storage.removeItem(INVENTORY_FILTERS_STORAGE_KEY);
      // Don't clear localStorage here - it will be cleared when navigating away
    };
  }, [clearFormData, dispatch]);

  // Handle browser events (refresh, close, navigation)
  // Note: We don't clear localStorage on beforeunload/popstate to preserve step on refresh
  useEffect(() => {
    const handlePopState = () => {
      // Only clear if navigating away from the campaign page
      const currentPath = window.location.pathname;
      if (
        !currentPath.includes("/campaigns/create") &&
        !currentPath.includes("/campaigns/edit")
      ) {
        clearFormData();
        clearStepFromStorage();
      }
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      window.removeEventListener("popstate", handlePopState);
    };
  }, [clearFormData, clearStepFromStorage]);

  const handleNext = async () => {
    setIsLoading(true);
    setLoading(true);

    try {
      // Get current step form reference
      const currentFormRef = currentStepFormRef.current;

      if (!currentFormRef) {
        console.error("Current step form ref not available");
        return;
      }

      // First, validate the current step
      const validationResult = await currentFormRef.validateStep();
      if (!validationResult.isValid) {
        // if (validationResult.errors && validationResult.errors.length > 0) {
        //   showError(validationResult?.errors.join(", "));
        // }
        // The form should already show inline validation errors
        return;
      }

      // If validation passes, submit the form (this will trigger API calls)
      const submitResult = await currentFormRef.submitForm();
      if (!submitResult) {
        console.error("Form submission failed");
        // The form should show any submission errors
        return;
      }

      console.log("Form submitted successfully for step", currentStep);

      // Mark step as completed and move to next
      markCompleted(currentStep);
      updateAccessibility();

      // Check if this is the last step
      if (isLastStep) {
        console.log("Campaign creation completed!");
        clearFormData();
        clearStepFromStorage();
        navigate("/campaigns");
      } else {
        nextStep();
        // Save the next step to localStorage in edit mode
        const currentIndex = steps.findIndex((s) => s.id === currentStep);
        const nextStepId = steps[currentIndex + 1]?.id;
        if (nextStepId && isEditModeFromUrl && campaignIdFromUrl) {
          saveStepToStorage(nextStepId);
        }
      }
    } catch (error) {
      console.error("Error processing step:", error);
    } finally {
      setIsLoading(false);
      setLoading(false);
    }
  };

  const handlePrevious = () => {
    // If we have a campaign ID, enable edit mode for any previous step navigation
    if (campaignState.campaignId) {
      setStepperEditMode(true, campaignState.campaignId);
    }
    // Reload full campaign data (including brandName/agencyName) in background
    reloadCampaignData();
    previousStep();
    // Save the previous step to localStorage in edit mode
    const currentIndex = steps.findIndex((s) => s.id === currentStep);
    const previousStepId = steps[currentIndex - 1]?.id;
    if (previousStepId && isEditModeFromUrl && campaignIdFromUrl) {
      saveStepToStorage(previousStepId);
    }
  };

  const handleBack = () => {
    clearFormData();
    clearStepFromStorage();
    navigate("/campaigns");
  };

  const handleFinaliseCampaign = async () => {
    setIsLoading(true);
    setLoading(true);
    try {
      // Validate the current (last) step before finalising — e.g. blocks
      // finalise when a selected inventory hasn't met its selling term's
      // minimum scheduled days requirement.
      const currentFormRef = currentStepFormRef.current;
      if (currentFormRef) {
        const validationResult = await currentFormRef.validateStep();
        if (!validationResult.isValid) {
          return;
        }
      }

      // Ensure campaign ID exists before proceeding
      if (!campaignState.campaignData?.id) {
        showError(tCampaigns("campaignWrapper.errors.campaignIdRequired"));
        setIsLoading(false);
        return;
      }
      const forecastData = campaignState.forecastData;
      const performance = forecastData
        ? {
            totalInventories: forecastData.totalInventories,
            estimatedImpression: forecastData.estimatedImpression,
            estimatedReach: forecastData.estimatedReach,
            totalCost: forecastData.totalCost,
            estimatedFrequency: forecastData.estimatedFrequency,
            estimatedAdPlays: forecastData.estimatedAdPlays,
            avgCpm: forecastData.avgCpm,
            avgECpm: forecastData.avgECpm,
            sov: forecastData.sov,
            plannedSot: forecastData.plannedSot,
            totalSot: forecastData.totalSot,
          }
        : undefined;
      const updatedData: CampaignCreateResponse = {
        ...campaignState.campaignData,
        status: "PLANNED",
        ...(performance && { performance }),
      };
      if (updatedData) {
        const result = await updateCampaign(updatedData).unwrap();
        if (result.success) {
          showSuccess(tCampaigns("campaignWrapper.success.statusUpdated"));
          clearFormData();
          clearStepFromStorage();
          navigate("/campaigns");
        } else {
          showError(tCampaigns("campaignWrapper.errors.failedToFinalise"));
        }
      } else {
        showError(tCampaigns("campaignWrapper.errors.campaignIdRequired"));
        setIsLoading(false);
        setLoading(false);
        return;
      }

      // Call the submit for review API
    } finally {
      setIsLoading(false);
      setLoading(false);
    }
  };

  const handleStepClick = (stepId: number) => {
    const step = stepperComponentData.steps.find(
      (s: { id: number }) => s.id === stepId,
    );
    if (step?.isAccessible) {
      navigateToStep(stepId);
      // Save the clicked step to localStorage in edit mode
      if (isEditModeFromUrl && campaignIdFromUrl) {
        saveStepToStorage(stepId);
      }
      if (campaignState.campaignData) {
        const completedSteps = determineCompletedSteps(
          campaignState.campaignData,
        );

        // Mark completed steps in the stepper
        completedSteps.forEach((stepId: number) => {
          markCompleted(stepId);
        });
        updateAccessibility();
      }
    }
  };

  if (!currentStepConfig) {
    return <div>{tCampaigns("campaignWrapper.invalidStep")}</div>;
  }

  const CurrentStepComponent = currentStepConfig.component;

  const backButton = (
    <div onClick={handleBack}>
      <ArrowLeft className="w-5 h-5" cursor="pointer" />
    </div>
  );

  // A finalized (PLANNED) campaign being edited keeps only the Finalise action —
  // Save as Draft is hidden so it can't be reverted to draft.
  const isPlanned = campaignState.campaignData?.status === "PLANNED";

  const rightActionButtons = (
    <div id="campaign-final-actions" className="flex gap-3">
      {!isPlanned && (
        <Button
          id="campaign-save-draft-btn"
          variant="outline"
          onClick={handleNext}
          disabled={isLoading}
          className="flex items-center gap-2 outline-mw-primary-500 text-mw-primary-500"
        >
          {tCampaigns("buttons.saveAsDraft")}
        </Button>
      )}
      <Button
        id="campaign-finalise-btn"
        variant="primary"
        onClick={handleFinaliseCampaign}
        disabled={isLoading}
        className="flex items-center gap-2"
      >
        {tCampaigns("buttons.finaliseCampaign")}
      </Button>
    </div>
  );

  return (
    <div id="campaign-wrapper" className="h-full flex flex-col">
      {/* Header with Stepper */}
      <div id="campaign-wrapper-header" className="shrink-0">
        <PageHeader
          title={`${isEditModeFromUrl ? tCampaigns("edit_new_campaign") : tCampaigns("create_new_campaign")}`}
          description={tCampaigns("create_campaign.subtitle")}
          leftAction={backButton}
          actions={isLastStep && rightActionButtons}
        />

        {/* Stepper Component */}
        <div id="campaign-stepper-container" className="px-6 pt-4">
          <Stepper
            id="campaign-stepper"
            steps={stepperSteps}
            onStepClick={handleStepClick}
            variant="compact"
          />
        </div>
      </div>

      {/* Main Content*/}
      <div
        id="campaign-step-content"
        className="campaign-step-content-wrapper mb-2 flex-1 overflow-y-auto scrollbar-thin"
      >
        <div
          id={`campaign-step-${currentStep}-content`}
          className="mx-auto px-6 py-4 h-full"
        >
          <CurrentStepComponent
            ref={setStepFormRef}
            key={currentStep}
            stepId={currentStep}
            onInventorySelectionChange={
              currentStep === 4 ? reloadCampaignData : undefined
            }
          />
        </div>
      </div>

      {/* Footer - Navigation Controls - Fixed at bottom */}
      <div
        id="campaign-wrapper-footer"
        className="shrink-0 bg-white border-t border-container-border"
      >
        <div className="p-4">
          <div className="flex items-center justify-between w-full">
            <div className="flex gap-3 items-center">
              {!isFirstStep && (
                <Button
                  id="campaign-previous-btn"
                  variant="outline"
                  onClick={handlePrevious}
                  disabled={isLoading}
                  className="flex items-center gap-2 outline-mw-primary-500 text-mw-primary-500"
                >
                  <ChevronLeft className="w-4 h-4" />
                  {tCampaigns("campaignWrapper.previousStep")}
                </Button>
              )}
              {/* Autosave loader indicator */}
              {isAutosaving && (
                <div
                  id="autosave-loader"
                  className="flex items-center gap-2 text-sm text-mw-neutral-600 dark:text-mw-neutral-400"
                >
                  <Spinner size="sm" variant="primary" />
                  <span>{tCampaigns("campaignWrapper.saving")}</span>
                </div>
              )}
            </div>

            {!isLastStep && (
              <div className="flex gap-3">
                <Button
                  id="campaign-next-btn"
                  variant={isLastStep ? "primary" : "outline"}
                  onClick={handleNext}
                  disabled={isLoading || isInventoryStepEmpty}
                  className="flex items-center gap-2 outline-mw-primary-500 text-mw-primary-500"
                >
                  {isLoading
                    ? tCampaigns("campaignWrapper.processing")
                    : isLastStep
                      ? tCommon("complete")
                      : (nextLabel ?? tCampaigns("campaignWrapper.nextStep"))}
                  <ChevronRight className="w-4 h-4" />
                </Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CampaignWrapper;
