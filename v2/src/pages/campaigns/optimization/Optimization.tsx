import { Card, CardContent } from "@components/ui/card";
import { Loading } from "@components/ui/Spinner";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAnnounce } from "@hooks/useAnnounce";
import optimizationSchema, {
  OptimizationFormData,
} from "@schemas/campaigns/optimzation.schema";
import {
  setCampaignData,
  setForecastData as setForecastDataRedux,
  useAutosaveCampaignMutation,
} from "@services/campaign/campaignSlice";
import {
  useLazyGetCampaignForecastQuery,
  useLazyGetSelectedInventoryQuery,
  useLazyGetSelectedInventorySchedulesQuery,
} from "@services/inventory/inventorySlice";
import { useTranslate } from "@tolgee/react";
import {
  countUniqueScheduledDays,
  countUniqueScheduledDaysFromRange,
} from "@utils/schedule.utils";
import {
  useState,
  useEffect,
  useMemo,
  useCallback,
  forwardRef,
  useImperativeHandle,
  useRef,
} from "react";
import { useForm } from "react-hook-form";
import { InventoryClassification } from "src/constants/inventory.constants";

import ScheduleOptimizationComponent from "./ScheduleOptimization";
import { useAppSelector, useAppDispatch } from "../../../store";
import { CampaignForecastData } from "../../../types/inventory.types";
import CampaignForecast from "../common/CampaignForecast";

export interface OptimizationFormRef {
  submitForm: () => Promise<boolean>;
  getFormData: () => OptimizationFormData | null;
  isValid: () => boolean;
  validateStep: () => Promise<{ isValid: boolean; errors: string[] }>;
  resetForm: () => void;
}

interface OptimizationFormProps {
  onSubmit: (formData: OptimizationFormData) => void;
  initialData: Partial<OptimizationFormData>;
  onValidationChange?: (isValid: boolean) => void;
  stepId?: number; // Add stepId for Redux integration
}

const OptimizationForm = forwardRef<OptimizationFormRef, OptimizationFormProps>(
  ({ onValidationChange, stepId = 5 }, ref) => {
    const { t: tCampaigns } = useTranslate(["campaigns"]);
    const dispatch = useAppDispatch();
    const { showError } = useAnnounce();

    // Campaign state from Redux
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const campaignState = useAppSelector((state: any) => state.campaign);

    // Campaign Id
    const campaignId =
      campaignState.campaignId || campaignState.campaignData?.id;
    const campaignCurrency = campaignState.campaignData?.currency || "";

    // Use ref to track if we've initialized to prevent initial update loops
    const isInitializedRef = useRef(false);

    const [forecastData, setForecastData] = useState<CampaignForecastData>({
      totalInventories: 0,
      estimatedImpression: 0,
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
    });
    const hasInitialLoadRef = useRef(false);
    const [fetchCampaignForecast] = useLazyGetCampaignForecastQuery();
    const [fetchSelectedInventory] = useLazyGetSelectedInventoryQuery();
    const [fetchSelectedInventorySchedules] =
      useLazyGetSelectedInventorySchedulesQuery();

    // Refs for performance optimization and cleanup
    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortControllerRef = useRef<AbortController | null>(null);
    const pendingRequestsRef = useRef<Set<string>>(new Set());
    // RTK Query mutation hook
    const [autosaveCampaignMutation, { isLoading }] =
      useAutosaveCampaignMutation();

    // Default form data
    const defaultData: OptimizationFormData = useMemo(() => {
      return {
        budgetAllocation: {
          digital: 25,
          transit: 25,
          classic: 25,
          retail: 25,
        },
        scheduleTargeting: {
          weekdayDistribution: {
            MONDAY: 14.28571428571429,
            TUESDAY: 14.28571428571429,
            WEDNESDAY: 14.28571428571429,
            THURSDAY: 14.28571428571429,
            FRIDAY: 14.28571428571429,
            SATURDAY: 14.28571428571429,
            SUNDAY: 14.28571428571429,
          },
          daypartDistribution: {
            "06-10": 20,
            "10-14": 20,
            "14-18": 20,
            "18-22": 20,
            "22-06": 20,
          },
        },
      };
    }, []);

    const {
      formState: { errors, isValid: formIsValid },
      getValues,
      setValue,
      trigger,
    } = useForm<OptimizationFormData>({
      resolver: zodResolver(optimizationSchema),
      mode: "onChange", // Enable real-time validation
    });

    const getErrorMessage = useCallback(
      (saveFormKey: keyof OptimizationFormData) => {
        if (!errors[saveFormKey]) return;
        if (saveFormKey === "scheduleTargeting") {
          return (
            errors[saveFormKey]?.weekdayDistribution?.message ||
            errors[saveFormKey]?.daypartDistribution?.message
          );
        }
        return errors[saveFormKey]?.message;
      },
      [errors],
    );

    // Handle autosave on field changes
    const handleBudgetAllocationFieldChange = useCallback(
      async (value: Record<string, unknown>) => {
        try {
          const budgetAllocationValue = {
            ...campaignState.campaignData.budgetAllocation,
            ...value,
          };
          setValue("budgetAllocation", budgetAllocationValue);
          const isValid = await trigger("budgetAllocation");
          if (!isValid) {
            const message =
              getErrorMessage("budgetAllocation") ||
              "optimization.budgetAllocation.validationFailed";
            showError(tCampaigns(message));
          }
        } catch (error) {
          console.error(`Failed to autosave budget allocation:`, error);
        }
      },
      [
        trigger,
        showError,
        tCampaigns,
        campaignState,
        setValue,
        getErrorMessage,
      ],
    );

    const callAutoSaveApi = useCallback(
      async (
        fieldName: keyof OptimizationFormData,
        value: Record<string, unknown>,
      ) => {
        try {
          // Validate required data
          if (!campaignId) {
            console.warn("Autosave: Campaign ID not available");
            return;
          }

          if (Object.keys(value).length === 0) {
            console.log(
              `Autosave: Skipping empty value for field ${fieldName}`,
            );
            return;
          }

          // Create unique request key for tracking
          const requestKey = `${fieldName}`;

          // Clear existing debounce timer
          if (debounceTimerRef.current) {
            clearTimeout(debounceTimerRef.current);
          }

          // Set up debounced autosave
          debounceTimerRef.current = setTimeout(async () => {
            try {
              // Check if request is already pending for this field
              const existingRequest = Array.from(
                pendingRequestsRef.current,
              ).find((key) => key.startsWith(fieldName));

              if (existingRequest) {
                abortControllerRef.current?.abort();
              }

              // Add to pending requests
              pendingRequestsRef.current.add(requestKey);

              // Create abort controller for this request
              abortControllerRef.current = new AbortController();

              console.log(
                `Autosave: Saving ${fieldName} = ${value} for campaign ${campaignId}`,
              );

              // Prepare autosave data
              const autosaveData = {
                [fieldName]: value,
                // lastModified: new Date().toISOString(),
                // step: "budget_and_goal", // Identify which step this autosave is from
              };

              // Make the autosave API call
              const response = await autosaveCampaignMutation({
                id: campaignId,
                data: autosaveData,
              }).unwrap();

              console.log(
                `Autosave: Successfully saved ${fieldName}`,
                response,
              );
              if (response.data) {
                dispatch(setCampaignData(response.data));
                loadForecastData();
              }
            } catch (error: unknown) {
              // Handle different types of errors
              if (error && typeof error === "object" && "name" in error) {
                if (error.name === "AbortError") {
                  console.log(`Autosave: Request aborted for ${fieldName}`);
                  return;
                }
              }

              console.error(`Autosave: Failed to save ${fieldName}`, error);

              // You might want to implement retry logic here
              // For now, we'll just log the error
            } finally {
              // Cleanup: Remove from pending requests
              pendingRequestsRef.current.delete(requestKey);

              // Reset abort controller if it's the current one
              if (abortControllerRef.current) {
                abortControllerRef.current = null;
              }

              // Clear debounce timer
              debounceTimerRef.current = null;
            }
          }, 500);
          // await autosave(fieldName, value);
        } catch (error) {
          console.error(`Failed to autosave ${fieldName}:`, error);
        }
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps -- loadForecastData is intentionally excluded: it is defined after this callback and referenced only inside a setTimeout, so it is always initialized by the time it runs
      [autosaveCampaignMutation, campaignId, dispatch],
    );

    // Handle autosave for geofencing field changes
    const handleBudgetSchedulingFieldMouseUp = useCallback(
      async (saveFormKey: keyof OptimizationFormData) => {
        const scheduleSavedData = getValues(saveFormKey);
        try {
          const isValid = await trigger(saveFormKey);
          if (isValid) {
            // await autosave(saveFormKey, scheduleSavedData);
            callAutoSaveApi(saveFormKey, scheduleSavedData);
          } else {
            let message = getErrorMessage(saveFormKey);
            message =
              message ||
              errors[saveFormKey]?.message ||
              `optimization.${saveFormKey}.validationFailed`;
            showError(tCampaigns(message));
          }
        } catch (error) {
          console.error(`Failed to autosave optimization:`, error);
        }
      },
      [
        trigger,
        showError,
        tCampaigns,
        getValues,
        errors,
        callAutoSaveApi,
        getErrorMessage,
      ],
    );

    // Transform campaign data to optimization form format
    const transformCampaignDataToOptimizationForm = useCallback(
      (campaignData: unknown): Partial<OptimizationFormData> => {
        if (!campaignData) return {};

        // Type guard for campaign data
        const data = campaignData as {
          budgetAllocation: {
            digital: number;
            transit: number;
            classic: number;
            retail: number;
          };
          scheduleTargeting: {
            weekdayDistribution: {
              MONDAY: number;
              TUESDAY: number;
              WEDNESDAY: number;
              THURSDAY: number;
              FRIDAY: number;
              SATURDAY: number;
              SUNDAY: number;
            };
            daypartDistribution: {
              "06-10": number;
              "10-14": number;
              "14-18": number;
              "18-22": number;
              "22-06": number;
            };
          };
        };

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const result: any = {
          ...defaultData,
          ...data,
        };
        if (
          !data.budgetAllocation ||
          Object.entries(data.budgetAllocation).length === 0
        ) {
          setValue("budgetAllocation", defaultData.budgetAllocation);
          handleBudgetAllocationFieldChange(defaultData.budgetAllocation);
          handleBudgetSchedulingFieldMouseUp("budgetAllocation");
        }
        return result as Partial<OptimizationFormData>;
      },
      [
        defaultData,
        setValue,
        handleBudgetAllocationFieldChange,
        handleBudgetSchedulingFieldMouseUp,
      ],
    );

    // Initialize form with campaign data if available (only once per stepId)
    useEffect(() => {
      if (!isInitializedRef.current && campaignState.campaignData) {
        const campaignData = campaignState.campaignData;
        const optimizationFormData =
          transformCampaignDataToOptimizationForm(campaignData);

        console.log(
          "Initializing optimization form with campaign data:",
          optimizationFormData,
        );

        // Set form values from campaign data
        Object.entries(optimizationFormData).forEach(([key, value]) => {
          if (value !== undefined && value !== null) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            setValue(key as keyof OptimizationFormData, value as any);
          }
        });

        isInitializedRef.current = true;
      }
    }, [
      stepId,
      setValue,
      campaignState.campaignData,
      transformCampaignDataToOptimizationForm,
    ]);

    // Notify parent of validation changes
    useEffect(() => {
      if (onValidationChange) {
        onValidationChange(formIsValid);
      }
    }, [formIsValid, onValidationChange]);

    // Expose form methods to parent component
    useImperativeHandle(
      ref,
      () => ({
        submitForm: async () => {
          return true;
        },
        getFormData: () => {
          const data = getValues();
          const isFormValid = Object.keys(errors).length === 0;
          return isFormValid ? data : null;
        },
        isValid: () => {
          return formIsValid && Object.keys(errors).length === 0;
        },
        validateStep: async () => {
          if (!campaignId) {
            return { isValid: true, errors: [] };
          }

          try {
            const inventoryResult = await fetchSelectedInventory({
              campaignId,
              params: { page: 0, size: -1, sortBy: "name", sortDir: "ASC" },
            }).unwrap();

            const inventories = inventoryResult.success
              ? inventoryResult.data?.content || []
              : [];

            if (inventories.length === 0) {
              return { isValid: true, errors: [] };
            }

            const inventoryIds = inventories.map(
              (inventory) => inventory.detail.id,
            );
            const scheduleResult = await fetchSelectedInventorySchedules({
              campaignId,
              inventories: inventoryIds,
            }).unwrap();

            const schedulesByInventoryId = new Map(
              (scheduleResult.data || []).map((entry) => [
                entry.inventoryId,
                entry.schedules,
              ]),
            );

            const violations: string[] = [];
            inventories.forEach((inventory) => {
              const minDays = inventory.detail.sellingTerm?.minDays;
              if (!minDays || minDays <= 0) return;

              const schedules =
                schedulesByInventoryId.get(inventory.detail.id) || [];
              // Classic inventories have no hourly booking grid, so
              // bookingMatrix stays empty — derive days from scheduleDays +
              // date range instead (see countUniqueScheduledDaysFromRange).
              const actualDays =
                inventory.detail.inventoryType ===
                InventoryClassification.CLASSIC
                  ? countUniqueScheduledDaysFromRange(schedules)
                  : countUniqueScheduledDays(schedules);

              if (actualDays < minDays) {
                violations.push(
                  tCampaigns("campaignWrapper.errors.minDaysViolationDetail", {
                    name: inventory.detail.name,
                    minDays,
                    actualDays,
                  }),
                );
              }
            });

            if (violations.length > 0) {
              showError(tCampaigns("campaignWrapper.errors.minDaysViolation"));
              return { isValid: false, errors: violations };
            }

            return { isValid: true, errors: [] };
          } catch (error) {
            console.error("Error validating minimum scheduled days:", error);
            return { isValid: true, errors: [] };
          }
        },
        resetForm: () => {
          setValue("budgetAllocation", defaultData.budgetAllocation);
          setValue("scheduleTargeting", defaultData.scheduleTargeting);
        },
      }),
      [
        getValues,
        setValue,
        errors,
        formIsValid,
        defaultData,
        campaignId,
        fetchSelectedInventory,
        fetchSelectedInventorySchedules,
        tCampaigns,
        showError,
      ],
    );

    // Initial load
    useEffect(() => {
      if (campaignId && !hasInitialLoadRef.current) {
        hasInitialLoadRef.current = true;
        loadForecastData();
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [campaignId]);

    // Fetch forecast data
    const loadForecastData = useCallback(
      async (forceRegenerate?: boolean) => {
        if (!campaignId) return;

        try {
          const result = await fetchCampaignForecast({
            campaignId,
            forceRegenerate,
          }).unwrap();
          if (result.success && result.data) {
            setForecastData({
              ...result.data,
              plannedSot: result.data.plannedSot ?? 0,
            });
            dispatch(setForecastDataRedux(result.data));
            await autosaveCampaignMutation({
              id: campaignId,
              data: {
                performance: {
                  totalInventories: result.data.totalInventories,
                  estimatedImpression: result.data.estimatedImpression,
                  estimatedReach: result.data.estimatedReach,
                  totalCost: result.data.totalCost,
                  estimatedFrequency: result.data.estimatedFrequency,
                  estimatedAdPlays: result.data.estimatedAdPlays,
                  avgCpm: result.data.avgCpm,
                  avgECpm: result.data.avgECpm,
                  sov: result.data.sov,
                  plannedSot: result.data.plannedSot,
                  totalSot: result.data.totalSot,
                },
              },
            }).unwrap();
          }
        } catch (error) {
          console.error("Error loading forecast data:", error);
        }
      },
      [campaignId, fetchCampaignForecast, dispatch, autosaveCampaignMutation],
    );

    return (
      <>
        {isLoading && <Loading overlay={true} text="" variant="primary" />}
        <div className="flex gap-4">
          <div className="flex-1 h-full">
            <Card className="h-full">
              <CardContent className="pt-4 h-full">
                <ScheduleOptimizationComponent
                  campaignId={campaignId}
                  campaignState={campaignState}
                  loadForeCastData={loadForecastData}
                />
              </CardContent>
            </Card>
          </div>
          <div className="w-80 space-y-4 right-2">
            <CampaignForecast
              campaignCurrency={campaignCurrency}
              forecastDataValue={forecastData}
              goalType={campaignState.campaignData?.goals?.goalType}
            />
          </div>
        </div>
      </>
    );
  },
);

OptimizationForm.displayName = "OptimizationForm";

export default OptimizationForm;
