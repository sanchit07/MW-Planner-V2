import { useActiveCompany } from "@hooks/useActiveCompany";
import { useAnnounce } from "@hooks/useAnnounce";
import { useGetChildCompaniesQuery } from "@services/agency/agencySlice";
import {
  setCampaignData,
  useAutosaveCampaignMutation,
} from "@services/campaign/campaignSlice";
import {
  goToStep,
  markStepCompleted,
  updateStepAccessibility,
} from "@services/stepper/stepperSlice";
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { CampaignCreateResponse } from "src/types/campaign.types";

import ManualSelectionPage from "./manual-selection/ManualSelectionPage";
import AiSmartRecommendationPanel from "./plan-summary/AiSmartRecommendationPanel";
import ManualModePanel from "./plan-summary/ManualModePanel";
import PlanSummaryPanel from "./plan-summary/PlanSummaryPanel";
import RecommendationChoiceCard from "./plan-summary/RecommendationChoiceCard";
import { useReachCurve } from "./plan-summary/useReachCurve";
import { useRecommendationForecast } from "./plan-summary/useRecommendationForecast";
import { useRestoreRecommendation } from "./plan-summary/useRestoreRecommendation";
import ViewInventoriesPage from "./view/ViewInventoriesPage";
import { type RootState, useAppSelector, useAppDispatch } from "../../../store";

interface InventoryPageFormProps {
  stepId?: number;
  initialData?: Record<string, unknown>;
  /** Called after the selection changes so the wizard can reload campaign data. */
  onInventorySelectionChange?: () => void;
}

export interface InventoryPageFormRef {
  submitForm: () => Promise<boolean>;
  validateStep: () => Promise<{ isValid: boolean; errors?: string[] }>;
  resetForm: () => void;
}

/**
 * Step 4 (Inventories) orchestrator.
 *
 * Renders the redesigned stacked layout:
 *  - top:    AiSmartRecommendationPanel (full-width action row — Edit / Restore)
 *  - bottom: PlanSummaryPanel (forecast tiles + Reach Build chart — phases 1 & 2)
 *
 * Data flow (phase 1): generate recommendation → poll until COMPLETED →
 * fetch forecast. Encapsulated in useRecommendationForecast.
 *
 * The inventory list, selection and CSV flows now live in the full-page
 * "View" (phase 3) and "Edit Manually" (phase 4) screens.
 */
const InventoryPageForm = forwardRef<
  InventoryPageFormRef,
  InventoryPageFormProps
>(({ onInventorySelectionChange }, ref) => {
  const { showError } = useAnnounce();
  const [isViewOpen, setIsViewOpen] = useState(false);
  const [isManualOpen, setIsManualOpen] = useState(false);
  const [hasManualEdits, setHasManualEdits] = useState(false);

  const campaignState = useAppSelector((state: RootState) => state.campaign);
  const campaignData =
    campaignState?.campaignData as CampaignCreateResponse | null;
  const campaignId =
    campaignState.campaignId || campaignState.campaignData?.id || "";
  const campaignCurrency = campaignData?.currency || "";
  const cachedRun = campaignState?.recommendationRun ?? null;

  const [autosaveCampaign] = useAutosaveCampaignMutation();

  // Recommendation mode for Step 4:
  //  - "choice":         first arrival — the user picks a path (nothing runs).
  //  - "recommendation": default AI flow (generate → poll → forecast).
  //  - "manual":         recommendations skipped — manual selection is primary.
  // Persisted on the plan via skipRecommendation so revisits honour the choice.
  // Plans that already have a run/selection go straight to the AI flow, so
  // existing plans behave exactly as before.
  const [mode, setMode] = useState<"choice" | "recommendation" | "manual">(
    () => {
      if (campaignData?.skipRecommendation === true) return "manual";
      if (cachedRun || (campaignData?.inventoryCount ?? 0) > 0)
        return "recommendation";
      return "choice";
    },
  );

  const dispatch = useAppDispatch();

  const { isMediaOwner, companyId: activeCompanyId } = useActiveCompany();

  const { data: childCompaniesData } = useGetChildCompaniesQuery(
    { id: activeCompanyId },
    { skip: !isMediaOwner || !activeCompanyId },
  );

  // Build the default mediaOwnerIds for media owner users:
  // active company + all child company IDs from the API.
  const mediaOwnerDefaultIds = useMemo(() => {
    if (!isMediaOwner || !activeCompanyId) return [];
    const childIds = (childCompaniesData?.children ?? [])
      .map((c) => c.company?.id)
      .filter((id): id is string => Boolean(id));
    const allIds = Array.from(new Set([activeCompanyId, ...childIds]));
    return allIds;
  }, [isMediaOwner, activeCompanyId, childCompaniesData?.children]);

  // Digest of the inputs that affect the recommendation. When it still matches
  // a cached completed run, generation is skipped and only the forecast loads.
  // campaignId is included so the cached run of one campaign is never reused for
  // a different campaign that happens to share the same targeting/budget/dates.
  const signature = useMemo(
    () =>
      JSON.stringify({
        campaignId,
        budget: campaignData?.budget ?? null,
        budgetAllocation: campaignData?.budgetAllocation ?? null,
        startDate: campaignData?.startDate ?? null,
        endDate: campaignData?.endDate ?? null,
        goalType: campaignData?.goals?.goalType ?? null,
        targeting: campaignData?.targeting ?? null,
        mediaChannels: campaignData?.mediaChannels ?? null,
        mediaOwnerIds: mediaOwnerDefaultIds,
      }),
    [
      campaignId,
      campaignData?.budget,
      campaignData?.budgetAllocation,
      campaignData?.startDate,
      campaignData?.endDate,
      campaignData?.goals?.goalType,
      campaignData?.targeting,
      campaignData?.mediaChannels,
      mediaOwnerDefaultIds,
    ],
  );

  const {
    status,
    progress,
    forecastData,
    retry,
    regenerateFromRestore,
    refetchForecast,
  } = useRecommendationForecast(
    campaignId,
    signature,
    mediaOwnerDefaultIds,
    campaignData?.mediaChannels ?? [],
    // Only the AI flow ever calls /generate; "choice" and "manual" fetch just
    // the forecast for the current selection.
    mode === "recommendation",
  );

  const isReady = status === "completed";

  // Latest campaign data for the persist callback (avoids stale closures).
  const campaignDataRef = useRef(campaignData);
  campaignDataRef.current = campaignData;

  // Persist the user's choice on the plan. On success the Redux campaignData
  // is updated too, so a Step-4 remount (in-app revisit) re-derives the same
  // mode instead of reading a stale skipRecommendation. The latest persist
  // promise is tracked so the opt-in path can serialize behind it.
  const persistPromiseRef = useRef<Promise<unknown>>(Promise.resolve());
  const persistSkipRecommendation = useCallback(
    (skip: boolean) => {
      if (!campaignId) return Promise.resolve();
      const p = autosaveCampaign({
        id: campaignId,
        data: { skipRecommendation: skip },
      })
        .unwrap()
        .then(() => {
          const current = campaignDataRef.current;
          if (current) {
            dispatch(setCampaignData({ ...current, skipRecommendation: skip }));
          }
        })
        .catch((err) => {
          console.warn("Failed to persist recommendation choice", err);
        });
      persistPromiseRef.current = p;
      return p;
    },
    [campaignId, autosaveCampaign, dispatch],
  );

  // Choice card: default AI path.
  const handleChooseRecommendations = useCallback(() => {
    persistSkipRecommendation(false);
    setMode("recommendation");
  }, [persistSkipRecommendation]);

  // Choice card: skip recommendations → manual selection opens immediately as
  // the primary surface. No recommendation call is made on this path.
  const handleChooseManual = useCallback(() => {
    persistSkipRecommendation(true);
    setMode("manual");
    setIsManualOpen(true);
  }, [persistSkipRecommendation]);

  // Manual mode → opt back in to recommendations without losing an existing
  // manual selection: while the plan still has skipRecommendation=true the
  // backend records the run WITHOUT resyncing the selection, so we only flip
  // the flag off after the run completes. With nothing selected yet, flip it
  // first so the completed run auto-selects its recommendation.
  const pendingFlagFlipRef = useRef(false);
  const handleUseRecommendationsFromManual = useCallback(async () => {
    const hasSelection = (forecastData?.totalInventories ?? 0) > 0;
    if (hasSelection) {
      // Serialize behind any in-flight skipRecommendation=true PATCH — if
      // /generate raced ahead of it, the backend would still see false and
      // resync (deleting the manual selection).
      pendingFlagFlipRef.current = true;
      await persistPromiseRef.current;
    } else {
      await persistSkipRecommendation(false);
    }
    setMode("recommendation");
  }, [forecastData?.totalInventories, persistSkipRecommendation]);

  useEffect(() => {
    if (
      pendingFlagFlipRef.current &&
      mode === "recommendation" &&
      status === "completed"
    ) {
      pendingFlagFlipRef.current = false;
      persistSkipRecommendation(false);
    }
  }, [mode, status, persistSkipRecommendation]);

  const reachCurve = useReachCurve(
    campaignId,
    status === "completed" && (forecastData?.totalInventories ?? 0) > 0,
    campaignData?.startDate,
    campaignData?.endDate,
  );

  const handleView = useCallback(() => {
    if (!campaignId) return;
    setIsViewOpen(true);
  }, [campaignId]);

  const handleEditManually = useCallback(() => {
    if (!campaignId) return;
    setIsManualOpen(true);
  }, [campaignId]);

  const { isRestoring, restore: handleRestoreRecommendation } =
    useRestoreRecommendation(campaignId, campaignData, regenerateFromRestore);

  // Restoring undoes manual edits, so the button goes away again until the
  // user makes new ones.
  const handleRestore = useCallback(async () => {
    await handleRestoreRecommendation();
    setHasManualEdits(false);
  }, [handleRestoreRecommendation]);

  // Both actions land on the Budget & Goal step, where budget and goal are edited.
  const handleGoToBudgetGoal = useCallback(() => {
    dispatch(goToStep(2));
  }, [dispatch]);

  // "Optimize to goal" jumps straight to the Optimization step (5), skipping
  // the normal "Next" button on this step. Step 5 depends on Step 4 being
  // marked completed (CampaignWrapper.handleNext does this via
  // markCompleted()+updateAccessibility() before advancing) — without doing
  // the same here, goToStep(5) silently no-ops because Step 5 is never
  // isAccessible yet.
  const handleGoToOptimization = useCallback(() => {
    dispatch(markStepCompleted(4));
    dispatch(updateStepAccessibility());
    dispatch(goToStep(5));
  }, [dispatch]);

  // After editing the selection, refresh the forecast + reach curve.
  const handleManualClose = useCallback(() => {
    setIsManualOpen(false);
    refetchForecast();
    reachCurve.refetch();
    onInventorySelectionChange?.();
  }, [refetchForecast, reachCurve, onInventorySelectionChange]);

  useImperativeHandle(ref, () => ({
    submitForm: async () => true,
    validateStep: async () => {
      if (mode === "choice") {
        showError("Please select at least one inventory item to proceed.");
        return {
          isValid: false,
          errors: ["Please select at least one inventory item"],
        };
      }
      if (forecastData?.totalInventories === 0) {
        showError("Please select at least one inventory item to proceed.");
        return {
          isValid: false,
          errors: ["Please select at least one inventory item"],
        };
      }
      return { isValid: true };
    },
    resetForm: () => {},
  }));

  return (
    <div className="h-full flex flex-col gap-3">
      {mode === "choice" && (
        <RecommendationChoiceCard
          onUseRecommendations={handleChooseRecommendations}
          onPickManually={handleChooseManual}
        />
      )}

      {mode === "recommendation" && (
        <AiSmartRecommendationPanel
          onEditManually={handleEditManually}
          editDisabled={!isReady || isRestoring}
          onView={handleView}
          viewDisabled={!isReady || isRestoring}
          onRestoreRecommendation={hasManualEdits ? handleRestore : undefined}
          restoreDisabled={status === "generating" || isRestoring}
          isRestoring={isRestoring}
        />
      )}

      {mode === "manual" && (
        <ManualModePanel
          onSelectInventory={handleEditManually}
          onView={handleView}
          viewDisabled={!isReady}
          onUseRecommendations={handleUseRecommendationsFromManual}
        />
      )}

      {mode !== "choice" && (
        <PlanSummaryPanel
          status={status}
          progress={progress}
          forecastData={forecastData}
          campaignCurrency={campaignCurrency}
          budget={campaignData?.budget}
          goals={campaignData?.goals}
          reachCurve={{
            status: reachCurve.status,
            data: reachCurve.overallReach,
            labels: reachCurve.labels,
            inventoryCount: reachCurve.inventoryCount,
          }}
          onRetry={retry}
          onAdjustBudget={handleGoToBudgetGoal}
          onLowerGoal={handleGoToBudgetGoal}
          onOptimizeToGoal={handleGoToOptimization}
        />
      )}

      <ViewInventoriesPage
        isOpen={isViewOpen}
        onClose={() => setIsViewOpen(false)}
      />

      <ManualSelectionPage
        isOpen={isManualOpen}
        onClose={handleManualClose}
        onManualEditsChange={setHasManualEdits}
      />
    </div>
  );
});

InventoryPageForm.displayName = "InventoryPageForm";

export default InventoryPageForm;
