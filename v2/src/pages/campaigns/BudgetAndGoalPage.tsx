import Alert from "@components/ui/Alert";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownScrollableContent,
  DropdownSearch,
  DropdownSeparator,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Input } from "@components/ui/Input";
import { Label } from "@components/ui/Label";
import ModalDrawer from "@components/ui/ModalDrawer";
import { Loading } from "@components/ui/Spinner";
import { zodResolver } from "@hookform/resolvers/zod";
import budgetSchema, {
  createBudgetSchema,
} from "@schemas/campaigns/budget.schema";
import { useTranslate } from "@tolgee/react";
import { Earth, Goal, Info, RotateCcw } from "lucide-react";
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { Chart } from "react-google-charts";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";

import { COUNTRY_CURRENCY_MAP } from "../../constants/budget.constants";
import { useAutosave } from "../../hooks/useAutosave";
import {
  setCampaignData,
  useGetCountryMarketDetailsByIsoQuery,
  useGetCompanyMarketAccessQuery,
} from "../../services/campaign/campaignSlice";
import { useAppSelector, useAppDispatch } from "../../store";
import { CampaignBudgetData } from "../../types/budget.types";
import {
  BudgetAllocation,
  CompanyMarketAccessItem,
  CountryMarketDetails,
} from "../../types/campaign.types";
import {
  formatNumber,
  formatNumberInput,
  parseNumberInput,
  createGoalTypes,
} from "../../utils/budget.utils";
import { FlagIcon } from "../../utils/FlagIcon";
import { toKebabKey } from "../../utils/stringManipulation.utils";

// Budget distribution channel configuration
const CHANNEL_CONFIG: Record<
  string,
  { inventoryKey: string; budgetKey: string; supportsAdPlays: boolean }
> = {
  DIGITAL_OOH: {
    inventoryKey: "Digital",
    budgetKey: "digital",
    supportsAdPlays: true,
  },
  CLASSIC_OOH: {
    inventoryKey: "Classic",
    budgetKey: "classic",
    supportsAdPlays: false,
  },
  CINEMA: {
    inventoryKey: "Cinema",
    budgetKey: "cinema",
    supportsAdPlays: false,
  },
  TRANSIT: {
    inventoryKey: "Transit",
    budgetKey: "transit",
    supportsAdPlays: true,
  },
  RETAIL: {
    inventoryKey: "Retail",
    budgetKey: "retail",
    supportsAdPlays: true,
  },
};

// Custom hook to fetch countries data filtered by company market access
// Testing defaults appended to the Target Country list when the company's
// market access doesn't already include them (see useCountriesData).
const DEFAULT_TEST_COUNTRIES: CompanyMarketAccessItem[] = [
  {
    id: "default-my",
    company_id: "",
    country_id: "MY",
    country_name: "Malaysia",
    country_code: "MY",
    is_active: true,
  },
  {
    id: "default-in",
    company_id: "",
    country_id: "IN",
    country_name: "India",
    country_code: "IN",
    is_active: true,
  },
];

// Custom hook to fetch countries from market-access API (active + deduped by country_code)
const useCountriesData = () => {
  const user = useAppSelector((state) => state.profile.profile);
  const companyId =
    user?.activeCompanyId || user?.memberships?.[0]?.company_id || "";

  const { data: marketAccessResponse, isLoading } =
    useGetCompanyMarketAccessQuery(companyId, { skip: !companyId });

  const countries = useMemo<CompanyMarketAccessItem[]>(() => {
    const seen = new Set<string>();
    const fromApi = (marketAccessResponse?.markets ?? []).filter((m) => {
      if (!m.is_active || seen.has(m.country_code)) return false;
      seen.add(m.country_code);
      return true;
    });
    // Testing defaults: always offer Malaysia and India, even when the
    // company's market access doesn't include them. The market-access API
    // integration stays as-is; these are only appended when missing.
    const withDefaults = [...fromApi];
    for (const fallback of DEFAULT_TEST_COUNTRIES) {
      if (!seen.has(fallback.country_code)) {
        seen.add(fallback.country_code);
        withDefaults.push(fallback);
      }
    }
    return withDefaults.sort((a, b) =>
      a.country_name.localeCompare(b.country_name),
    );
  }, [marketAccessResponse]);

  return { countries, isLoading };
};

type BudgetFormData = z.infer<typeof budgetSchema>;

export interface BudgetFormRef {
  submitForm: () => Promise<boolean>;
  getFormData: () => BudgetFormData | null;
  isValid: () => boolean;
  validateStep: () => Promise<{ isValid: boolean; errors: string[] }>;
  resetForm: () => void;
}

interface BudgetFormProps {
  onSubmit?: (formData: BudgetFormData) => void;
  initialData?: Partial<BudgetFormData>;
  onValidationChange?: (isValid: boolean) => void;
}

const BudgetAndGoalForm = forwardRef<BudgetFormRef, BudgetFormProps>(
  ({ onSubmit, initialData: propInitialData, onValidationChange }, ref) => {
    const { t: tCampaigns } = useTranslate(["campaigns"]);
    const dispatch = useAppDispatch();
    const campaignState = useAppSelector((state) => state.campaign);
    const [selectedCountryData, setSelectedCountryData] =
      useState<CountryMarketDetails | null>(null);
    const isInitializedRef = useRef(false);
    const isUserGoalTypeChangeRef = useRef(false);
    const hasRunGeoRef = useRef(false);
    const distributionChannelsKeyRef = useRef<string>("");

    // Budget distribution state
    const [isDistributionModalOpen, setIsDistributionModalOpen] =
      useState(false);
    const [isManualMode, setIsManualMode] = useState(false);
    const [channelPercentages, setChannelPercentages] = useState<
      Record<string, number>
    >({});
    const [pendingPercentages, setPendingPercentages] = useState<
      Record<string, number>
    >({});

    // Fetch countries data
    const { countries, isLoading: isCountriesLoading } = useCountriesData();

    // ISO code of the currently selected country; drives the market-details fetch
    const [selectedCountryIso, setSelectedCountryIso] = useState<string>("");

    const { data: marketDetailsResponse, isFetching: isMarketDetailsFetching } =
      useGetCountryMarketDetailsByIsoQuery(selectedCountryIso, {
        skip: !selectedCountryIso,
      });

    // Sync market-details response into selectedCountryData for the right panel
    useEffect(() => {
      if (marketDetailsResponse?.data?.length) {
        setSelectedCountryData(marketDetailsResponse.data[0]);
      } else if (marketDetailsResponse) {
        setSelectedCountryData(null);
      }
    }, [marketDetailsResponse]);

    // Form setup with simplified defaults
    const defaultData: BudgetFormData = useMemo(
      () => ({
        country: "",
        currency: "",
        budget: undefined,
        goalType: "",
        targetName: undefined,
        targetValue: undefined,
      }),
      [],
    );

    const schema = useMemo(() => createBudgetSchema(tCampaigns), [tCampaigns]);

    const {
      register,
      handleSubmit,
      formState: { errors, isValid: formIsValid },
      watch,
      control,
      getValues,
      setValue,
      reset,
      trigger,
    } = useForm<BudgetFormData>({
      resolver: zodResolver(schema),
      defaultValues: { ...defaultData, ...propInitialData },
      mode: "onChange",
    });

    // Goal types configuration using utility function
    const goalTypes = useMemo(() => createGoalTypes(tCampaigns), [tCampaigns]);

    // Initialize form with campaign data - simple approach
    useEffect(() => {
      if (
        !isInitializedRef.current &&
        countries.length > 0 &&
        !isCountriesLoading
      ) {
        const campaignData =
          campaignState.campaignData as CampaignBudgetData | null;

        // Set country - use from campaignData or blank
        const country = campaignData?.countryId || "";
        setValue("country", country);

        // Set currency - use from campaignData or blank
        const currency = campaignData?.currency || "";
        setValue("currency", currency);

        // If country exists but no currency, find currency from constant data
        if (country && !currency) {
          const countryKey = toKebabKey(country);
          const currencyInfo = COUNTRY_CURRENCY_MAP[countryKey];
          if (currencyInfo) {
            setValue("currency", currencyInfo.code);
          }
        }

        // Set other fields
        if (
          campaignData?.budget !== undefined &&
          campaignData?.budget !== null
        ) {
          setValue("budget", campaignData.budget);
        }
        if (campaignData?.goals?.goalType) {
          setValue("goalType", campaignData.goals.goalType);
        }
        if (campaignData?.goals?.targetName) {
          setValue("targetName", campaignData.goals.targetName);
        }
        if (campaignData?.goals?.targetValue !== undefined) {
          setValue("targetValue", campaignData.goals.targetValue);
        }

        // Trigger market-details fetch for right panel if country is already set
        if (country) {
          const marketItem = countries.find((c) => c.country_name === country);
          if (marketItem) {
            setSelectedCountryIso(marketItem.country_code);
          }
        }

        isInitializedRef.current = true;
        isUserGoalTypeChangeRef.current = false;
      }
    }, [campaignState.campaignData, countries, isCountriesLoading, setValue]);

    // Initialize autosave hook with error handling
    const { autosave, autosaveBatch } = useAutosave({
      debounceMs: 0,
      onSuccess: (response) => {
        dispatch(setCampaignData(response.data));
      },
      onError: (error) => {
        console.error("Autosave failed:", error);
        // You could show a toast notification here
      },
    });

    // Simplified autosave handler
    const handleFieldBlur = useCallback(
      async (
        fieldName: string,
        value: unknown,
        canSkipValueNullCheck = false,
      ) => {
        try {
          // Trigger validation for the field that was blurred
          const isValid = await trigger(fieldName as keyof BudgetFormData);

          // If validation fails (pattern error, zod schema error, etc.), don't call autosave
          if (!isValid) {
            return;
          }

          // Double-check errors object as a safety measure
          const fieldError = errors[fieldName as keyof typeof errors];
          if (fieldError) {
            return;
          }

          const isGoalField = [
            "goalType",
            "targetValue",
            "targetName",
          ].includes(fieldName);

          if (isGoalField) {
            const currentValues = getValues();
            const goalsData = {
              goalType:
                fieldName === "goalType" ? value : currentValues.goalType,
              targetName:
                fieldName === "targetName" ? value : currentValues.targetName,
              targetValue:
                fieldName === "targetValue" ? value : currentValues.targetValue,
            };

            // For goal fields, also check if there are errors in related goal fields
            const hasGoalErrors =
              errors.goalType || errors.targetValue || errors.targetName;

            if (hasGoalErrors) {
              return;
            }

            if (
              goalsData.goalType &&
              goalsData.targetValue !== undefined &&
              goalsData.targetValue !== null
            ) {
              await autosave("goals", goalsData);
            } else if (!goalsData.goalType) {
              await autosave("goals", {}, canSkipValueNullCheck);
            }
          } else if (fieldName === "country") {
            // Check for country errors
            if (errors.country) {
              return;
            }

            const countryId = value as string;
            // Find currency from constant data
            const countryKey = toKebabKey(countryId);
            const currencyInfo = COUNTRY_CURRENCY_MAP[countryKey];
            const currencyCode = currencyInfo?.code || "";

            // Update form currency value
            if (currencyCode) {
              setValue("currency", currencyCode);
              // Trigger validation for currency after setting it
              const isCurrencyValid = await trigger("currency");
              // If currency validation fails, don't autosave
              if (!isCurrencyValid) {
                return;
              }
            }

            // Send both countryId and currency to API
            await autosaveBatch({
              countryId: countryId,
              currency: currencyCode,
            });
          } else {
            // For other fields (like budget), check if there's an error
            if (errors[fieldName as keyof typeof errors]) {
              return;
            }
            await autosave(fieldName, value, canSkipValueNullCheck);
          }
        } catch (error) {
          console.error(`Failed to autosave ${fieldName}:`, error);
        }
      },
      [autosave, autosaveBatch, getValues, setValue, trigger, errors],
    );

    // Auto-detect country from IP geolocation (only if no country already selected)
    useEffect(() => {
      if (isCountriesLoading || countries.length === 0) return;
      if (!isInitializedRef.current) return;
      if (hasRunGeoRef.current) return;

      const currentCountry = getValues("country");
      if (currentCountry) return;

      hasRunGeoRef.current = true;

      fetch("https://ipapi.co/json/")
        .then((r) => r.json())
        .then((data: { country_name?: string }) => {
          const detectedName = data.country_name;
          if (!detectedName) return;

          const match = countries.find(
            (c) => c.country_name.toLowerCase() === detectedName.toLowerCase(),
          );
          if (match) {
            setValue("country", match.country_name);
            setSelectedCountryIso(match.country_code);
            handleFieldBlur("country", match.country_name);
          }
        })
        .catch(() => {});
    }, [countries, isCountriesLoading, getValues, setValue, handleFieldBlur]);

    // Resolve selected media channels from campaign state
    const selectedChannels = useMemo((): string[] => {
      const mc = campaignState.campaignData as {
        mediaChannels?: string[];
      } | null;
      return Array.isArray(mc?.mediaChannels) && mc!.mediaChannels!.length > 0
        ? mc!.mediaChannels!
        : ["DIGITAL_OOH"];
    }, [campaignState.campaignData]);

    const adPlaysDisabled = selectedChannels.every(
      (ch) => !CHANNEL_CONFIG[ch]?.supportsAdPlays,
    );

    // Market Insight's inventory count should reflect only the channels
    // selected in Step 1, not the country's channel-agnostic total.
    const selectedInventoryCount = useMemo(() => {
      const classification =
        selectedCountryData?.inventoryCountByClassification;
      if (!classification) return selectedCountryData?.inventoryCount ?? 0;
      return selectedChannels.reduce(
        (sum, ch) =>
          sum + (classification[CHANNEL_CONFIG[ch]?.inventoryKey ?? ""] ?? 0),
        0,
      );
    }, [selectedCountryData, selectedChannels]);

    // Auto percentage computation based on inventory count proportions
    const autoPercentages = useMemo((): Record<string, number> => {
      const classification = (selectedCountryData as CountryMarketDetails)
        ?.inventoryCountByClassification;
      const counts = selectedChannels.map((ch) => ({
        ch,
        count: classification?.[CHANNEL_CONFIG[ch]?.inventoryKey ?? ""] ?? 0,
      }));
      const total = counts.reduce((s, c) => s + c.count, 0);
      if (total === 0) {
        let evenAcc = 0;
        return Object.fromEntries(
          selectedChannels.map((ch, i) => {
            const pct =
              i === selectedChannels.length - 1
                ? Math.round((100 - evenAcc) * 10) / 10
                : Math.round((100 / selectedChannels.length) * 10) / 10;
            evenAcc += i === selectedChannels.length - 1 ? 0 : pct;
            return [ch, pct];
          }),
        );
      }
      let acc = 0;
      return Object.fromEntries(
        counts.map(({ ch, count }, i) => {
          const pct =
            i === counts.length - 1
              ? Math.round((100 - acc) * 10) / 10
              : Math.round((count / total) * 1000) / 10;
          acc += i === counts.length - 1 ? 0 : pct;
          return [ch, pct];
        }),
      );
    }, [selectedCountryData, selectedChannels]);

    // Initialize distribution percentages once when autoPercentages become available,
    // and re-initialize whenever the set of selected channels changes.
    useEffect(() => {
      if (selectedChannels.length === 0) return;

      const channelsKey = selectedChannels.slice().sort().join(",");
      const prevKey = distributionChannelsKeyRef.current;
      const channelsChanged = prevKey !== "" && prevKey !== channelsKey;

      // Skip if already initialized for this exact channel set
      if (prevKey !== "" && !channelsChanged) return;

      // Restore from saved allocation only when channels haven't changed
      if (!channelsChanged) {
        const savedAllocation = (
          campaignState.campaignData as {
            budgetAllocation?: BudgetAllocation;
          } | null
        )?.budgetAllocation;

        const allSaved =
          savedAllocation &&
          selectedChannels.every(
            (ch) =>
              CHANNEL_CONFIG[ch] &&
              typeof (savedAllocation as unknown as Record<string, number>)[
                CHANNEL_CONFIG[ch].budgetKey
              ] === "number",
          );

        if (allSaved) {
          const fromSaved = Object.fromEntries(
            selectedChannels.map((ch) => [
              ch,
              (savedAllocation as unknown as Record<string, number>)[
                CHANNEL_CONFIG[ch]?.budgetKey ?? ch
              ],
            ]),
          );
          setChannelPercentages(fromSaved);
          distributionChannelsKeyRef.current = channelsKey;
          return;
        }
      }

      // Wait for country data — needed for proportional distribution
      if (!selectedCountryData) return;

      setChannelPercentages(autoPercentages);
      distributionChannelsKeyRef.current = channelsKey;

      // Persist the computed auto-distribution so the backend has it from load
      const alloc = Object.fromEntries(
        selectedChannels.map((ch) => [
          CHANNEL_CONFIG[ch]?.budgetKey ?? ch,
          autoPercentages[ch] ?? 0,
        ]),
      );
      handleFieldBlur("budgetAllocation", alloc, true);
    }, [
      autoPercentages,
      selectedChannels,
      campaignState.campaignData,
      selectedCountryData,
      handleFieldBlur,
    ]);

    // Budget distribution handlers
    // Modal distribute evenly — updates pendingPercentages only
    const distributeEvenly = useCallback(() => {
      const n = selectedChannels.length;
      const base = Math.floor((100 / n) * 10) / 10;
      const last = Math.round((100 - base * (n - 1)) * 10) / 10;
      const next: Record<string, number> = {};
      selectedChannels.forEach((ch, i) => {
        next[ch] = i === n - 1 ? last : base;
      });
      setPendingPercentages(next);
    }, [selectedChannels]);

    // Modal reset — restores auto percentages to pending
    const resetToAuto = useCallback(() => {
      setPendingPercentages(autoPercentages);
    }, [autoPercentages]);

    const handlePendingPercentageChange = (ch: string, raw: string) => {
      const val = raw === "" ? 0 : parseFloat(raw);
      setPendingPercentages((prev) => ({
        ...prev,
        [ch]: isNaN(val) ? 0 : val,
      }));
    };

    // Open modal — copy current percentages to pending, reset manual mode
    const openDistributionModal = useCallback(() => {
      setPendingPercentages({ ...channelPercentages });
      setIsManualMode(false);
      setIsDistributionModalOpen(true);
    }, [channelPercentages]);

    // Apply — commit pending to actual + autosave
    const applyDistribution = useCallback(() => {
      setChannelPercentages(pendingPercentages);
      const alloc = Object.fromEntries(
        selectedChannels.map((ch) => [
          CHANNEL_CONFIG[ch]?.budgetKey ?? ch,
          pendingPercentages[ch] ?? 0,
        ]),
      );
      handleFieldBlur("budgetAllocation", alloc, true);
      setIsDistributionModalOpen(false);
    }, [pendingPercentages, selectedChannels, handleFieldBlur]);

    // Notify parent of validation changes
    useEffect(() => {
      if (onValidationChange) {
        onValidationChange(formIsValid);
      }
    }, [formIsValid, onValidationChange]);

    // Watch goal type for conditional rendering
    const watchedGoalType = watch("goalType");
    const watchedCountry = watch("country");
    const watchedBudget = watch("budget");
    const watchedTargetValue = watch("targetValue");

    // Clear ADPLAYS goal type when selected channels no longer support ad plays
    useEffect(() => {
      if (adPlaysDisabled && watchedGoalType === "ADPLAYS") {
        setValue("goalType", "");
        handleFieldBlur("goalType", "", true);
      }
    }, [adPlaysDisabled, watchedGoalType, setValue, handleFieldBlur]);

    // Clear target value when goal type changes (only for user changes, not initialization)
    useEffect(() => {
      if (
        watchedGoalType &&
        isInitializedRef.current &&
        isUserGoalTypeChangeRef.current
      ) {
        setValue("targetValue", undefined);
        trigger("targetValue");
        // Reset the user change flag
        isUserGoalTypeChangeRef.current = false;
      } else if (
        !watchedGoalType &&
        isInitializedRef.current &&
        isUserGoalTypeChangeRef.current
      ) {
        isUserGoalTypeChangeRef.current = false;
      }
    }, [watchedGoalType, setValue, trigger]);

    // Form submission handler (simplified since autosave handles persistence)
    const onFormSubmit = useCallback(
      (data: BudgetFormData) => {
        // No API call needed here - autosave handles persistence
        // Stepper will handle the navigation and final submission
        if (onSubmit) {
          onSubmit(data);
        }
      },
      [onSubmit],
    );

    // Simplified helper functions
    const getCurrentCountry = () =>
      countries.find((c) => c.country_name === watchedCountry);

    const getCurrentCurrency = () => {
      if (!watchedCountry) return null;
      const countryKey = toKebabKey(watchedCountry);
      return COUNTRY_CURRENCY_MAP[countryKey] || null;
    };

    const getCurrentGoalType = () =>
      goalTypes.find((gt) => gt.value === watchedGoalType);

    // Simplified insights logic
    const currentInsights = useMemo(() => {
      const goalTypeLower = watchedGoalType?.toLowerCase() || "default";
      const baseKey = `budget_goal.goal_insights.${goalTypeLower}`;

      return {
        title: tCampaigns(`${baseKey}.title`),
        insights: [1, 2, 3].map((i) => tCampaigns(`${baseKey}.insight${i}`)),
      };
    }, [watchedGoalType, tCampaigns]);

    // Expose form methods to parent component
    useImperativeHandle(
      ref,
      () => ({
        submitForm: async () => {
          if (isMarketDetailsFetching) return false;
          return new Promise((resolve) => {
            handleSubmit(
              // Success callback - called when validation passes
              (data) => {
                onFormSubmit(data);
                resolve(true);
              },
              // Error callback - called when validation fails
              (errors) => {
                console.warn("Budget form validation failed:", errors);
                resolve(false);
              },
            )();
          });
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
          return new Promise((resolve) => {
            handleSubmit(
              () => resolve({ isValid: true, errors: [] }),
              (validationErrors) => {
                const errorMessages: string[] = [];
                Object.keys(validationErrors).forEach((key) => {
                  const fieldError =
                    validationErrors[key as keyof typeof validationErrors];
                  if (fieldError?.message) {
                    errorMessages.push(fieldError.message);
                  }
                });
                resolve({ isValid: false, errors: errorMessages });
              },
            )();
          });
        },
        resetForm: () => {
          reset(defaultData);
          isInitializedRef.current = false;
          distributionChannelsKeyRef.current = "";
          hasRunGeoRef.current = false;
        },
        getNextLabel: () =>
          tCampaigns("create_campaign.buttons.next_demographics"),
      }),
      [
        handleSubmit,
        onFormSubmit,
        getValues,
        errors,
        formIsValid,
        reset,
        defaultData,
        tCampaigns,
        isMarketDetailsFetching,
      ],
    );

    return (
      <div id="budget-goal-form" className="flex gap-6">
        {/* Left Side - Form */}
        <div id="budget-goal-form-left" className="flex-1 space-y-4">
          {/* Market Selection Card */}
          <Card id="budget-goal-market-card">
            <div className="p-4">
              <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
                <div className="w-10 h-10 bg-mw-cyan-50 rounded-lg flex items-center justify-center">
                  <Earth className="w-5 h-5 text-mw-teal-600" />
                </div>
                <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
                  <div className="self-stretch inline-flex justify-start items-start gap-2">
                    <CardTitle className="text-sm font-medium leading-none">
                      {tCampaigns("budget_goal.market_selection.title")}
                    </CardTitle>
                  </div>
                  <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                    {tCampaigns("budget_goal.market_selection.description")}
                  </p>
                </div>
              </div>
            </div>
            <CardContent>
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <Label className="mb-2">
                      {tCampaigns(
                        "budget_goal.market_selection.target_country",
                      )}
                    </Label>
                    <Controller
                      name="country"
                      control={control}
                      render={({ field }) => (
                        <Dropdown name="country" searchable={true}>
                          <DropdownTrigger className="w-full justify-between">
                            {getCurrentCountry() ? (
                              <div className="flex items-center gap-2">
                                {(() => {
                                  const key = toKebabKey(
                                    getCurrentCountry()!.country_name,
                                  );
                                  const iso =
                                    COUNTRY_CURRENCY_MAP[key]?.isoCode;
                                  return iso ? <FlagIcon code={iso} /> : null;
                                })()}
                                <span>{getCurrentCountry()?.country_name}</span>
                              </div>
                            ) : (
                              tCampaigns(
                                "budget_goal.market_selection.select_country",
                              )
                            )}
                          </DropdownTrigger>
                          <DropdownContent align="left" className="w-full">
                            <DropdownSearch
                              placeholder={tCampaigns(
                                "searchOptionsPlaceholder",
                              )}
                            />
                            <DropdownScrollableContent maxHeight="150px">
                              {countries.map((country) => {
                                const key = toKebabKey(country.country_name);
                                const iso = COUNTRY_CURRENCY_MAP[key]?.isoCode;
                                return (
                                  <DropdownItem
                                    key={country.country_code}
                                    value={country.country_name}
                                    searchableText={country.country_name}
                                    onClick={() => {
                                      field.onChange(country.country_name);
                                      setSelectedCountryIso(
                                        country.country_code,
                                      );
                                      handleFieldBlur(
                                        "country",
                                        country.country_name,
                                      );
                                    }}
                                  >
                                    <span className="flex items-center gap-2">
                                      {iso && <FlagIcon code={iso} />}
                                      {country.country_name}
                                    </span>
                                  </DropdownItem>
                                );
                              })}
                            </DropdownScrollableContent>
                          </DropdownContent>
                        </Dropdown>
                      )}
                    />
                    {errors.country && (
                      <div
                        id="budget-goal-country-error"
                        className="text-sm text-mw-error-500 mt-1"
                      >
                        {errors.country.message}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Budget & Goals Card */}
          <Card id="budget-goal-budget-card">
            <div className="p-4">
              <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
                <div className="w-10 h-10 bg-mw-info-50 rounded-lg flex items-center justify-center">
                  <Goal className="w-5 h-5 text-mw-info-500" />
                </div>
                <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
                  <div className="self-stretch inline-flex justify-start items-start gap-2">
                    <CardTitle className="text-sm font-medium leading-none">
                      {tCampaigns("budget_goal.budget_goal_setup.title")}
                    </CardTitle>
                  </div>
                  <p className="text-sm font-normal text-mw-neutral-500 leading-none">
                    {tCampaigns("budget_goal.budget_goal_setup.description")}
                  </p>
                </div>
              </div>
            </div>
            <CardContent>
              <div className="space-y-4">
                {/* First Row: Currency and Budget */}
                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <Input
                      id="budget-goal-currency-input"
                      label={tCampaigns(
                        "budget_goal.budget_goal_setup.currency",
                      )}
                      value={
                        getCurrentCurrency()
                          ? `${getCurrentCurrency()?.label}`
                          : tCampaigns("budget_goal.budget_goal_setup.currency")
                      }
                      disabled
                      placeholder={tCampaigns(
                        "budget_goal.budget_goal_setup.currency_placeholder",
                      )}
                    />
                    {errors.currency && (
                      <div
                        id="budget-goal-currency-error"
                        className="text-sm text-mw-error-500 mt-1"
                      >
                        {errors.currency.message}
                      </div>
                    )}
                  </div>
                  <div>
                    <Input
                      id="budget-goal-budget-input"
                      label={tCampaigns(
                        "budget_goal.budget_goal_setup.budget_amount",
                      )}
                      placeholder={tCampaigns(
                        "budget_goal.budget_goal_setup.budget_placeholder",
                      )}
                      type="text"
                      inputMode="decimal"
                      value={formatNumberInput(watchedBudget)}
                      error={errors.budget?.message}
                      onChange={(e) => {
                        // Keep only digits and a single decimal point so the
                        // displayed value stays a valid formatted number.
                        const sanitized = e.target.value.replace(/[^\d.]/g, "");
                        setValue("budget", parseNumberInput(sanitized), {
                          shouldValidate: true,
                        });
                      }}
                      onBlur={(e) => {
                        const sanitized = e.target.value.replace(/[^\d.]/g, "");
                        if (sanitized === "") {
                          // Allow empty budget (it's optional)
                          setValue("budget", undefined);
                          handleFieldBlur("budget", undefined, true);
                        } else {
                          const value = parseNumberInput(sanitized);
                          if (value !== undefined && value >= 0) {
                            handleFieldBlur("budget", value, true);
                          }
                        }
                      }}
                    />
                  </div>
                </div>

                {/* Budget Distribution by Type — banner */}
                {!!watchedCountry && selectedChannels.length > 0 && (
                  <div className="flex items-center justify-between bg-mw-primary-50 border border-mw-primary-100 rounded-lg px-4 py-3">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-mw-neutral-800">
                        {tCampaigns("budget_goal.distribution.title")}
                      </p>
                      <p className="text-xs text-mw-neutral-500 mt-0.5">
                        {tCampaigns(
                          "budget_goal.distribution.subtitle_expanded",
                        )}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={openDistributionModal}
                      className="ml-4 shrink-0 inline-flex items-center gap-1 px-4 py-2 text-xs font-medium bg-mw-primary-500 text-white rounded-lg hover:bg-mw-primary-600 transition-colors duration-150"
                    >
                      {tCampaigns("budget_goal.distribution.edit_btn")}
                      <span>›</span>
                    </button>
                  </div>
                )}

                {/* Budget Distribution Modal */}
                <ModalDrawer
                  isOpen={isDistributionModalOpen}
                  onClose={() => setIsDistributionModalOpen(false)}
                  title={tCampaigns("budget_goal.distribution.title")}
                  showBackButton={false}
                  size="lg"
                  footer={
                    <div className="flex justify-end gap-3">
                      <button
                        type="button"
                        onClick={() => setIsDistributionModalOpen(false)}
                        className="px-4 py-2 text-sm font-medium border border-mw-neutral-200 rounded-lg text-mw-neutral-700 hover:bg-mw-neutral-50 transition-colors"
                      >
                        {tCampaigns("budget_goal.distribution.cancel")}
                      </button>
                      <button
                        type="button"
                        onClick={applyDistribution}
                        className="px-4 py-2 text-sm font-medium bg-mw-primary-500 text-white rounded-lg hover:bg-mw-primary-600 transition-colors"
                      >
                        {tCampaigns("budget_goal.distribution.apply")}
                      </button>
                    </div>
                  }
                >
                  <div className="space-y-4">
                    <p className="text-sm text-mw-neutral-500">
                      {tCampaigns("budget_goal.distribution.subtitle_expanded")}
                    </p>

                    {/* Total / warning row */}
                    {(() => {
                      const totalPct =
                        Math.round(
                          Object.values(pendingPercentages).reduce(
                            (s, v) => s + v,
                            0,
                          ) * 10,
                        ) / 10;
                      const isValid = totalPct === 100;
                      const diff =
                        Math.round(Math.abs(totalPct - 100) * 10) / 10;
                      return (
                        <div
                          className={`flex items-start gap-2 px-3 py-2.5 rounded-lg border text-sm transition-colors duration-300 ${
                            isValid
                              ? "bg-mw-success-50 border-mw-success-200 text-mw-success-700"
                              : totalPct > 100
                                ? "bg-mw-error-50 border-mw-error-200 text-mw-error-700"
                                : "bg-mw-warning-50 border-mw-warning-100 text-mw-warning-700"
                          }`}
                        >
                          <Info className="w-4 h-4 shrink-0 mt-0.5" />
                          <div>
                            <p className="font-semibold">
                              {tCampaigns("budget_goal.distribution.total")}{" "}
                              {totalPct.toFixed(1)}%
                            </p>
                            <p className="text-xs font-normal mt-0.5">
                              {isValid
                                ? tCampaigns(
                                    "budget_goal.distribution.looks_good",
                                  )
                                : totalPct > 100
                                  ? tCampaigns(
                                      "budget_goal.distribution.exceeds_100",
                                      { amount: String(diff) },
                                    )
                                  : tCampaigns(
                                      "budget_goal.distribution.below_100",
                                      { amount: String(diff) },
                                    )}
                            </p>
                          </div>
                        </div>
                      );
                    })()}

                    {/* Action buttons */}
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={distributeEvenly}
                        disabled={selectedChannels.length <= 1}
                        className="px-3 py-1.5 text-xs font-medium border border-mw-primary-400 text-mw-primary-500 rounded-md hover:bg-mw-primary-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                      >
                        {tCampaigns(
                          "budget_goal.distribution.distribute_evenly",
                        )}
                      </button>
                      <button
                        type="button"
                        onClick={resetToAuto}
                        className="px-3 py-1.5 text-xs font-medium border border-mw-neutral-200 text-mw-neutral-700 rounded-md hover:bg-mw-neutral-50 transition-colors flex items-center gap-1 group"
                      >
                        <RotateCcw className="w-3 h-3 group-hover:rotate-[-180deg] transition-transform duration-300" />
                        {tCampaigns("budget_goal.distribution.reset")}
                      </button>
                    </div>

                    {/* Channel rows */}
                    {selectedChannels.map((ch, index) => {
                      const config = CHANNEL_CONFIG[ch];
                      if (!config) return null;
                      const pct = pendingPercentages[ch] ?? 0;
                      const amount = Math.round(
                        (pct / 100) * (watchedBudget ?? 0),
                      );
                      return (
                        <div
                          key={ch}
                          className="relative overflow-hidden rounded-lg border border-mw-neutral-100 hover:border-mw-primary-200 hover:shadow-sm transition-all duration-200"
                          style={{
                            opacity: isDistributionModalOpen ? 1 : 0,
                            transform: isDistributionModalOpen
                              ? "translateY(0)"
                              : "translateY(6px)",
                            transition: `opacity 0.25s ease ${150 + index * 70}ms, transform 0.25s ease ${150 + index * 70}ms, box-shadow 0.2s, border-color 0.2s`,
                          }}
                        >
                          {/* Progress bar background */}
                          <div
                            className="absolute inset-y-0 left-0 bg-mw-primary-50 transition-[width] duration-700 ease-out"
                            style={{ width: `${Math.min(pct, 100)}%` }}
                          />
                          {/* Row content */}
                          <div className="relative flex items-center gap-3 px-3 py-1">
                            <span className="flex-1 text-sm text-mw-neutral-700">
                              {tCampaigns(
                                `create_campaign.media_channels.${ch}.label`,
                              )}
                            </span>
                            <div className="flex items-center border border-mw-neutral-200 rounded-lg overflow-hidden h-9 bg-white/90">
                              <span className="px-2 text-xs text-mw-neutral-400 border-r border-mw-neutral-200 h-full flex items-center bg-mw-neutral-50">
                                %
                              </span>
                              <input
                                type="number"
                                value={pct}
                                onChange={(e) =>
                                  handlePendingPercentageChange(
                                    ch,
                                    e.target.value,
                                  )
                                }
                                disabled={!isManualMode}
                                className={`w-16 h-full px-2 text-sm text-right tabular-nums bg-transparent transition-colors duration-150 ${
                                  isManualMode
                                    ? "focus:outline-none focus:ring-1 focus:ring-mw-primary-500"
                                    : "cursor-default select-none text-mw-neutral-600"
                                }`}
                                step="0.1"
                                min="0"
                                max="100"
                              />
                            </div>
                            <span className="text-sm font-semibold text-mw-primary-500 w-28 text-right tabular-nums">
                              {getCurrentCurrency()?.code ?? ""}{" "}
                              {amount.toLocaleString()}
                            </span>
                          </div>
                        </div>
                      );
                    })}

                    {/* Manual / lock toggle */}
                    {!isManualMode ? (
                      <button
                        type="button"
                        onClick={() => setIsManualMode(true)}
                        className="text-xs text-mw-primary-500 hover:text-mw-primary-700 underline underline-offset-2 transition-colors duration-150"
                      >
                        {tCampaigns("budget_goal.distribution.adjust_manually")}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setIsManualMode(false)}
                        className="text-xs text-mw-neutral-400 hover:text-mw-neutral-600 underline underline-offset-2 transition-colors duration-150"
                      >
                        {tCampaigns(
                          "budget_goal.distribution.lock_distribution",
                        )}
                      </button>
                    )}
                  </div>
                </ModalDrawer>

                {/* Second Row: Goal Type and Target Value */}
                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <Label className="mb-2">
                      {tCampaigns("budget_goal.budget_goal_setup.goal_type")}
                    </Label>
                    <Controller
                      name="goalType"
                      control={control}
                      render={({ field }) => (
                        <Dropdown name="goal-type">
                          <DropdownTrigger
                            className="w-full justify-between"
                            clearable={true}
                            hasValue={!!field.value}
                            onClear={() => {
                              field.onChange("");
                              // Trigger autosave when goal type is selected
                              handleFieldBlur("goalType", "", true);
                              // Trigger validation for targetValue to show inline error if needed
                              setTimeout(() => {
                                trigger("targetValue");
                              }, 0);
                            }}
                          >
                            {getCurrentGoalType()?.label ||
                              tCampaigns(
                                "budget_goal.budget_goal_setup.select_goal_type",
                              )}
                          </DropdownTrigger>
                          <DropdownContent
                            align="left"
                            className="w-full max-h-[200px] overflow-y-auto scrollbar-thin"
                          >
                            {goalTypes.map((goalType) => {
                              const isDisabled =
                                adPlaysDisabled && goalType.value === "ADPLAYS";
                              return (
                                <DropdownItem
                                  key={goalType.value}
                                  value={goalType.value}
                                  disabled={isDisabled}
                                  onClick={() => {
                                    // Mark this as a user change
                                    isUserGoalTypeChangeRef.current = true;
                                    field.onChange(goalType.value);
                                    // Trigger autosave when goal type is selected
                                    handleFieldBlur("goalType", goalType.value);
                                    // Trigger validation for targetValue to show inline error if needed
                                    setTimeout(() => {
                                      trigger("targetValue");
                                    }, 0);
                                  }}
                                >
                                  <div className="flex-1 items-start flex flex-col gap-1">
                                    <p>{goalType.label}</p>
                                    <p className="text-xs text-mw-neutral-500">
                                      {goalType.description}
                                    </p>
                                    <DropdownSeparator />
                                  </div>
                                </DropdownItem>
                              );
                            })}
                          </DropdownContent>
                        </Dropdown>
                      )}
                    />
                    {errors.goalType && (
                      <div
                        id="budget-goal-goal-type-error"
                        className="text-sm text-mw-error-500 mt-1"
                      >
                        {errors.goalType.message}
                      </div>
                    )}
                    {(watchedGoalType === "IMPRESSIONS" ||
                      watchedGoalType === "REACH") && (
                      <p className="text-xs text-mw-neutral-500 mt-1">
                        {tCampaigns(
                          "budget_goal.budget_goal_setup.goal_type_cpm_hint",
                        )}
                      </p>
                    )}
                    {(watchedGoalType === "SOV" ||
                      watchedGoalType === "ADPLAYS") && (
                      <p className="text-xs text-mw-neutral-500 mt-1">
                        {tCampaigns(
                          "budget_goal.budget_goal_setup.goal_type_cps_hint",
                        )}
                      </p>
                    )}
                  </div>
                  {watchedGoalType && (
                    <div id="budget-goal-target-value-section">
                      <Input
                        id="budget-goal-target-value-input"
                        label={
                          getCurrentGoalType()?.targetLabel ||
                          tCampaigns(
                            "budget_goal.budget_goal_setup.target_value_optional",
                          )
                        }
                        placeholder={
                          getCurrentGoalType()?.targetPlaceholder ||
                          tCampaigns(
                            "budget_goal.budget_goal_setup.enter_target_value",
                          )
                        }
                        type="text"
                        inputMode="decimal"
                        value={formatNumberInput(watchedTargetValue)}
                        error={errors.targetValue?.message}
                        required
                        onChange={(e) => {
                          // Keep only digits and a single decimal point; the
                          // display value is regrouped with commas on render.
                          const sanitized = e.target.value.replace(
                            /[^\d.]/g,
                            "",
                          );
                          setValue("targetValue", parseNumberInput(sanitized), {
                            shouldValidate: true,
                          });
                        }}
                        onBlur={(e) => {
                          const sanitized = e.target.value.replace(
                            /[^\d.]/g,
                            "",
                          );
                          const value = parseNumberInput(sanitized);
                          if (value !== undefined && value >= 0) {
                            // Round to 2 decimal places
                            const formattedValue = parseFloat(value.toFixed(2));
                            // For SOV, ensure value doesn't exceed 100
                            if (
                              watchedGoalType === "SOV" &&
                              formattedValue > 100
                            ) {
                              return; // Don't save invalid percentage
                            }
                            setValue("targetValue", formattedValue);
                            handleFieldBlur("targetValue", formattedValue);
                          }
                        }}
                      />
                      {getCurrentGoalType()?.unit && (
                        <div
                          id="budget-goal-target-value-unit"
                          className="text-xs text-mw-neutral-500 mt-1"
                        >
                          {tCampaigns(
                            "budget_goal.budget_goal_setup.unit_label",
                          )}{" "}
                          {getCurrentGoalType()?.unit}
                          {watchedGoalType === "sov" &&
                            ` ${tCampaigns("budget_goal.budget_goal_setup.sov_range")}`}
                        </div>
                      )}
                    </div>
                  )}
                </div>
                {watchedGoalType === "REACH" && (
                  <Alert variant="info">
                    {tCampaigns(
                      "budget_goal.budget_goal_setup.reach_planning_note",
                    )}
                  </Alert>
                )}
                {/* Third Row: Target Name (only visible when "Other" is selected) */}
                {(watchedGoalType === "OTHER" ||
                  watchedGoalType === "other") && (
                  <div
                    id="budget-goal-target-name-section"
                    className="grid grid-cols-2 gap-6"
                  >
                    <div>
                      <Input
                        {...register("targetName")}
                        id="budget-goal-target-name-input"
                        label={tCampaigns(
                          "budget_goal.budget_goal_setup.target_name",
                        )}
                        placeholder={tCampaigns(
                          "budget_goal.budget_goal_setup.enter_target_name",
                        )}
                        type="text"
                        error={errors.targetName?.message}
                        required
                        maxLength={100}
                        onBlur={(e) => {
                          const value = e.target.value.trim();
                          if (value) {
                            handleFieldBlur("targetName", value);
                          }
                        }}
                      />
                    </div>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right Side - Form Insights & Quick Tips */}
        <div className="w-80 space-y-4">
          {/* Market Insights - Only visible when country is selected */}
          {getCurrentCountry() && (
            <Card>
              <CardHeader className="p-4 pt-2 pb-2">
                <div className="inline-flex justify-start items-center gap-2">
                  <Earth className="w-4 h-4 relative overflow-hidden " />
                  <h3 className="font-medium text-sm">
                    {tCampaigns("budget_goal.market_insights.title")}
                  </h3>
                </div>
              </CardHeader>
              <CardContent className="relative space-y-4 w-full">
                {isMarketDetailsFetching && (
                  <Loading overlay variant="primary" size="sm" text="" />
                )}
                <div className="self-stretch p-2 rounded outline outline-1 outline-offset-[-1px] outline-mw-neutral-100 flex flex-col justify-start items-start gap-0.5">
                  <div
                    className="self-stretch h-48 min-h-[192px] w-full"
                    aria-label={tCampaigns("selectedCountryMapAria")}
                  >
                    <Chart
                      chartType="GeoChart"
                      data={[
                        ["Country", "Selected"],
                        [getCurrentCountry()?.country_name ?? "", 1],
                      ]}
                      options={{
                        height: 192,
                        keepAspectRatio: false,
                        colorAxis: {
                          values: [0, 1],
                          colors: ["#E5E7EB", "#2563EB"],
                          minValue: 0,
                          maxValue: 1,
                        },
                        displayMode: "regions",
                        resolution: "countries",
                        backgroundColor: "transparent",
                        datalessRegionColor: "#FFFFFF",
                        borderColor: "#000000",
                        defaultColor: "#E5E7EB",
                        legend: "none",
                        tooltip: { trigger: "none" },
                      }}
                    />
                  </div>
                  <div className="self-stretch justify-start text-mw-neutral-500 text-xs font-normal leading-4">
                    {tCampaigns("budget_goal.market_insights.market")}
                  </div>
                  <div className="self-stretch justify-start text-mw-black text-base font-semibold leading-5">
                    {selectedCountryData?.countryName || watchedCountry} (
                    {getCurrentCurrency()?.code})
                  </div>
                </div>
                <div className="self-stretch grid grid-cols-3 justify-start items-start gap-2">
                  <div className="flex-1 p-2 bg-mw-warning-50 rounded outline outline-1 outline-offset-[-1px] outline-mw-warning-100 inline-flex flex-col justify-start items-start gap-0.5">
                    <div className="self-stretch justify-start text-mw-neutral-500 text-xs font-normal leading-4">
                      {tCampaigns("budget_goal.market_insights.population")}
                    </div>
                    <div className="self-stretch justify-start text-mw-black text-base font-semibold leading-5">
                      {selectedCountryData
                        ? formatNumber(selectedCountryData.population)
                        : "0"}
                    </div>
                  </div>
                  <div className="flex-1 p-2 bg-mw-primary-50 rounded outline outline-1 outline-offset-[-1px] outline-mw-primary-100 inline-flex flex-col justify-start items-start gap-0.5">
                    <div className="self-stretch justify-start text-mw-neutral-500 text-xs font-normal leading-4">
                      {tCampaigns("budget_goal.market_insights.inventories")}
                    </div>
                    <div className="self-stretch justify-start text-mw-black text-base font-semibold leading-5">
                      {selectedCountryData
                        ? formatNumber(selectedInventoryCount)
                        : "0"}
                    </div>
                  </div>
                  <div className="flex-1 p-2 bg-mw-success-50 rounded outline outline-1 outline-offset-[-1px] outline-mw-success-100 inline-flex flex-col justify-start items-start gap-0.5">
                    <div className="self-stretch justify-start text-mw-neutral-500 text-xs font-normal leading-4">
                      {tCampaigns("budget_goal.market_insights.impressions")}
                    </div>
                    <div className="self-stretch justify-start text-mw-black text-base font-semibold leading-5">
                      {selectedCountryData
                        ? formatNumber(selectedCountryData.impressions)
                        : "0"}
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Quick Tips */}
          <Card>
            <div className="p-4">
              <div className="inline-flex justify-start items-center gap-2">
                <Info className="w-4 h-4 relative overflow-hidden" />
                <h3 className="font-medium text-sm">
                  {currentInsights?.title ||
                    tCampaigns("budget_goal.quick_tips.title")}
                </h3>
              </div>
            </div>
            <CardContent>
              <div className="space-y-4 text-xs">
                {currentInsights?.insights.map((tip, index) => (
                  <div
                    key={index}
                    className="inline-flex justify-start items-start gap-2"
                  >
                    <div className="h-5 flex justify-start items-center gap-2.5">
                      <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                    </div>
                    <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                      {tip}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  },
);

BudgetAndGoalForm.displayName = "BudgetAndGoalForm";

export default BudgetAndGoalForm;
