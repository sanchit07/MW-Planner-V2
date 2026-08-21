import Alert from "@components/ui/Alert";
import { Checkbox } from "@components/ui/Checkbox";
import { Label } from "@components/ui/Label";
import MultiSelect, { TreeNode } from "@components/ui/MultiSelect";
import { Switch } from "@components/ui/Switch";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@components/ui/Tabs";
import { Tooltip } from "@components/ui/Tooltip";
import {
  INVENTORY_CLUSTERS_BY_CHANNEL,
  INVENTORY_CLUSTERS_COMING_SOON,
} from "@constants/inventory.constants";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAutosave } from "@hooks/useAutosave";
import DemographicComponent from "@pages/campaigns/DemographicComponent";
import targetingSchema, {
  TargetingFormData,
} from "@schemas/campaigns/targeting.schema";
import { setCampaignData } from "@services/campaign/campaignSlice";
import {
  useGetVenuesQuery,
  VenueItem,
} from "@services/inventory/inventorySlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import clsx from "clsx";
import { Building2, Layers, MapPin, Monitor, Sparkles } from "lucide-react";
import {
  useState,
  useEffect,
  useMemo,
  useCallback,
  forwardRef,
  useImperativeHandle,
  useRef,
} from "react";
import { Controller, useForm, type Resolver } from "react-hook-form";

import GeoFencingForm from "./geofencing/GeoFencingForm";
import { useAppSelector, useAppDispatch } from "../../store";
import SignalsForm from "../signals/SignalsForm";

export interface TargetingFormRef {
  submitForm: () => Promise<boolean>;
  getFormData: () => TargetingFormData | null;
  isValid: () => boolean;
  validateStep: () => Promise<{ isValid: boolean; errors: string[] }>;
  resetForm: () => void;
}

interface TargetingFormProps {
  onSubmit?: (formData: TargetingFormData) => void;
  initialData?: Partial<TargetingFormData>;
  onValidationChange?: (isValid: boolean) => void;
  stepId?: number; // Add stepId for Redux integration
}

const TargetingForm = forwardRef<TargetingFormRef, TargetingFormProps>(
  (
    { onSubmit, initialData: propInitialData, onValidationChange, stepId = 3 },
    ref,
  ) => {
    const [activeTab, setActiveTab] = useState("demographics");
    const { t: tCampaigns } = useTranslate(["campaigns"]);
    const language = useTolgee(["language"]).getLanguage();

    // Venues API for Inventory Types tab
    const { data: venuesData = [] } = useGetVenuesQuery({ language });

    const venueTreeNodes = useMemo((): TreeNode[] => {
      const transform = (items: VenueItem[]): TreeNode[] =>
        items.map((item) => ({
          label: item.name,
          value: item.stringValue,
          id: String(item.enumerationId),
          disabled: false,
          ...(item.definition ? { description: item.definition } : {}),
          ...(item.children?.length
            ? { children: transform(item.children) }
            : {}),
        }));
      return transform(venuesData);
    }, [venuesData]);
    const dispatch = useAppDispatch();

    // Campaign state from Redux
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const campaignState = useAppSelector((state: any) => state.campaign);

    const selectedChannels: string[] = useMemo(() => {
      const mc = campaignState.campaignData?.mediaChannels;
      return Array.isArray(mc) && mc.length > 0 ? mc : ["DIGITAL_OOH"];
    }, [campaignState.campaignData?.mediaChannels]);

    // DSP is set on Step 1 — when active, programmatic targeting is forced on
    // and locked here rather than left as an independent, contradictory choice.
    const isDspActive = campaignState.campaignData?.dsp === "ACTIVATE";

    // Programmatic buying only applies to digital inventory — without the
    // Digital OOH channel selected in Step 1, there's nothing programmatic
    // to target, so the toggle is locked off.
    const hasDigitalChannel = selectedChannels.includes("DIGITAL_OOH");

    // Use ref to track if we've initialized to prevent initial update loops
    const isInitializedRef = useRef(false);

    // Default form data
    const defaultData: TargetingFormData = useMemo(
      () => ({
        demographics: {
          age: [],
          gender: [],
          income: [],
          interests: [],
          behavior: [],
        },
        geofencing: {
          geometries: [],
          locations: [],
        },
        signals: [],
        venueTypes: { digitalOoh: [], classicOoh: [] },
        inventoryCluster: [
          ...INVENTORY_CLUSTERS_BY_CHANNEL.digitalOoh,
          ...INVENTORY_CLUSTERS_BY_CHANNEL.classicOoh,
        ].filter((value) => !INVENTORY_CLUSTERS_COMING_SOON.includes(value)),
        programmaticOnly: false,
      }),
      [],
    );

    // Sanitize initial data
    const sanitizedInitialData = useMemo(() => {
      if (!propInitialData) return {};
      return { ...propInitialData };
    }, [propInitialData]);

    // Transform campaign data to targeting form format
    const transformCampaignDataToTargetingForm = useCallback(
      (campaignData: unknown): Partial<TargetingFormData> => {
        if (!campaignData) return {};

        // Type guard for campaign data
        const data = campaignData as {
          targeting?: {
            demographics?: {
              age?: string[];
              gender?: string[];
              income?: string[];
              interests?: string[];
              behavior?: string[];
            };
            geofencing?: {
              geometries?: Array<{
                type: "Polygon" | "LineString" | "Point";
                coordinates: number[][];
                included: boolean;
              }>;
              locations?: Array<{
                lat: number;
                lng: number;
                radius?: number;
                address: string;
                included: boolean;
                name?: string;
              }>;
            };
            signals?: {
              deviceTypes?: string[];
              behaviors?: string[];
              purchaseHistory?: string[];
              engagementLevel?: string[];
            };
          };
        };

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const result: any = data.targeting || {};
        return result as Partial<TargetingFormData>;
      },
      [],
    );

    const {
      handleSubmit,
      formState: { errors, isValid: formIsValid },
      watch,
      control,
      getValues,
      setValue,
    } = useForm<TargetingFormData>({
      resolver: zodResolver(
        targetingSchema,
      ) as unknown as Resolver<TargetingFormData>,
      defaultValues: {
        ...defaultData,
        ...sanitizedInitialData,
      },
      mode: "onChange", // Enable real-time validation
    });

    // Initialize form with campaign data if available (only once per stepId)
    useEffect(() => {
      if (!isInitializedRef.current && campaignState.campaignData) {
        const campaignData = campaignState.campaignData;
        const targetingFormData =
          transformCampaignDataToTargetingForm(campaignData);

        // Set form values from campaign data
        Object.entries(targetingFormData).forEach(([key, value]) => {
          if (value !== undefined && value !== null) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            setValue(key as keyof TargetingFormData, value as any);
          }
        });

        isInitializedRef.current = true;
      }
    }, [
      stepId,
      setValue,
      campaignState.campaignData,
      transformCampaignDataToTargetingForm,
    ]);

    // Initialize autosave hook with error handling
    const { autosave } = useAutosave({
      debounceMs: 0, // Wait 1.5 seconds after user stops typing
      onSuccess: (response) => {
        dispatch(setCampaignData(response.data));
      },
      onError: (error) => {
        console.error("Autosave failed:", error);
        // You could show a toast notification here
      },
    });

    const handleTargetingFieldChange = useCallback(
      async (partial: Partial<TargetingFormData>) => {
        const currentTargeting = campaignState.campaignData?.targeting ?? {};
        const targetValue = { ...currentTargeting, ...partial };
        try {
          await autosave("targeting", targetValue);
        } catch (error) {
          console.error("Failed to autosave targeting:", error);
        }
      },
      [autosave, campaignState.campaignData?.targeting],
    );

    // Notify parent of validation changes
    useEffect(() => {
      if (onValidationChange) {
        onValidationChange(formIsValid);
      }
    }, [formIsValid, onValidationChange]);

    // Reconcile venue types when the selected media channels change. Clears
    // (and persists) the venue types for any channel that is no longer
    // selected, so stale, disabled selections don't linger after switching
    // channels in edit mode.
    useEffect(() => {
      if (!isInitializedRef.current) return;
      const currentVenueTypes = getValues("venueTypes") ?? {
        digitalOoh: [],
        classicOoh: [],
      };
      const next = { ...currentVenueTypes };
      let changed = false;
      if (
        !selectedChannels.includes("CLASSIC_OOH") &&
        next.classicOoh?.length
      ) {
        next.classicOoh = [];
        changed = true;
      }
      if (
        !selectedChannels.includes("DIGITAL_OOH") &&
        next.digitalOoh?.length
      ) {
        next.digitalOoh = [];
        changed = true;
      }
      if (changed) {
        setValue("venueTypes", next);
        handleTargetingFieldChange({
          venueTypes: next,
        } as Partial<TargetingFormData>);
      }
    }, [selectedChannels, getValues, setValue, handleTargetingFieldChange]);

    // Same reconciliation as venue types, but for the Inventory Types
    // cluster checkboxes: drop a channel's cluster values from the flat
    // list when that channel is no longer selected in Step 1.
    useEffect(() => {
      if (!isInitializedRef.current) return;
      const current = getValues("inventoryCluster") ?? [];
      let next = current;
      if (!selectedChannels.includes("CLASSIC_OOH")) {
        next = next.filter(
          (v) => !INVENTORY_CLUSTERS_BY_CHANNEL.classicOoh.includes(v),
        );
      }
      if (!selectedChannels.includes("DIGITAL_OOH")) {
        next = next.filter(
          (v) => !INVENTORY_CLUSTERS_BY_CHANNEL.digitalOoh.includes(v),
        );
      }
      if (next.length !== current.length) {
        setValue("inventoryCluster", next);
        handleTargetingFieldChange({
          inventoryCluster: next,
        } as Partial<TargetingFormData>);
      }
    }, [selectedChannels, getValues, setValue, handleTargetingFieldChange]);

    // Toggle a single inventory-cluster checkbox and persist the result.
    const handleClusterToggle = useCallback(
      (value: string, checked: boolean) => {
        const current = getValues("inventoryCluster") ?? [];
        const next = checked
          ? [...current, value]
          : current.filter((v) => v !== value);
        setValue("inventoryCluster", next);
        handleTargetingFieldChange({
          inventoryCluster: next,
        } as Partial<TargetingFormData>);
      },
      [getValues, setValue, handleTargetingFieldChange],
    );

    // Force programmatic-only ON and locked while a DSP is active; once the
    // wizard's Step 1 DSP is set back to None, the toggle is user-controlled
    // again (existing value is left as-is, not reset).
    useEffect(() => {
      if (!isInitializedRef.current) return;
      if (isDspActive && !getValues("programmaticOnly")) {
        setValue("programmaticOnly", true);
        handleTargetingFieldChange({
          programmaticOnly: true,
        } as Partial<TargetingFormData>);
      }
    }, [isDspActive, getValues, setValue, handleTargetingFieldChange]);

    // Force programmatic-only OFF and locked when the Digital OOH channel
    // isn't selected — there's no digital inventory to target programmatically.
    useEffect(() => {
      if (!isInitializedRef.current) return;
      if (!hasDigitalChannel && getValues("programmaticOnly")) {
        setValue("programmaticOnly", false);
        handleTargetingFieldChange({
          programmaticOnly: false,
        } as Partial<TargetingFormData>);
      }
    }, [hasDigitalChannel, getValues, setValue, handleTargetingFieldChange]);

    // Form data is now managed by autosave and campaign store
    // No need to update stepper form data

    // Form submission handler (simplified since autosave handles persistence)
    const onFormSubmit = useCallback(
      (data: TargetingFormData) => {
        console.log("Targeting form submitted with data:", data);
        // No API call needed here - autosave handles persistence
        // Stepper will handle the navigation and final submission
        if (onSubmit) {
          onSubmit(data);
        }
      },
      [onSubmit],
    );

    // Expose form methods to parent component
    useImperativeHandle(
      ref,
      () => ({
        submitForm: async () => {
          // Tab navigation: Demographics → Geofencing → Inventory Types → proceed
          if (activeTab === "demographics") {
            setActiveTab("geofencing");
            return false;
          }
          if (activeTab === "geofencing") {
            setActiveTab("inventoryTypes");
            return false;
          }
          return new Promise((resolve) => {
            handleSubmit(
              // Success callback - called when validation passes
              (data) => {
                // const typedData = data as unknown as TargetingFormData;
                onFormSubmit(data);
                resolve(true);
              },
              // Error callback - called when validation fails
              (errors) => {
                console.warn("Targeting form validation failed:", errors);
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
          const errors: string[] = [];

          return {
            isValid: errors.length === 0,
            errors,
          };
        },
        resetForm: () => {
          setValue("demographics", defaultData.demographics);
          setValue("geofencing", defaultData.geofencing);
          setValue("signals", []);
          setValue("venueTypes", { digitalOoh: [], classicOoh: [] });
          setValue("inventoryCluster", defaultData.inventoryCluster);
          setValue("programmaticOnly", false);
          setActiveTab("demographics");
        },
        getNextLabel: () => {
          if (activeTab === "demographics")
            return tCampaigns("create_campaign.buttons.next_geofencing");
          if (activeTab === "geofencing")
            return tCampaigns("create_campaign.buttons.next_inventory_type");
          return tCampaigns("create_campaign.buttons.next_step");
        },
      }),
      [
        activeTab,
        handleSubmit,
        onFormSubmit,
        getValues,
        errors,
        formIsValid,
        setValue,
        defaultData,
        tCampaigns,
      ],
    );

    return (
      <Tabs
        id="targeting-form-tabs"
        value={activeTab}
        onValueChange={setActiveTab}
        className={clsx(
          "bg-white",
          activeTab === "geofencing" ? "h-full flex flex-col" : "min-h-full",
        )}
      >
        <TabsList
          id="targeting-form-tabs-list"
          className="grid w-full grid-cols-4"
        >
          <TabsTrigger value="demographics">
            {tCampaigns("targeting.tabTitles.demographics")}
          </TabsTrigger>
          <TabsTrigger value="geofencing">
            {tCampaigns("targeting.tabTitles.geoFencing")}
          </TabsTrigger>
          <TabsTrigger value="inventoryTypes">
            {tCampaigns("targeting.tabTitles.inventoryTypes")}
          </TabsTrigger>
          <Tooltip
            content={tCampaigns("targeting.comingSoon")}
            position="bottom"
            triggerClassName="flex-1 cursor-not-allowed"
          >
            <TabsTrigger value="signals" disabled className="w-full">
              {tCampaigns("targeting.tabTitles.signals")}
            </TabsTrigger>
          </Tooltip>
        </TabsList>

        {/* Demographics Tab */}
        <div
          id="targeting-form-tab-content-wrapper"
          className={clsx(
            "targeting-tab-content",
            activeTab === "geofencing" ? "overflow-hidden h-full" : "",
          )}
        >
          <TabsContent
            id="targeting-form-demographics-content"
            value="demographics"
            className="h-full"
          >
            <DemographicComponent
              control={control}
              onFieldChange={(value: {
                demographics: Record<string, unknown>;
              }) =>
                handleTargetingFieldChange(value as Partial<TargetingFormData>)
              }
              demographicFormData={getValues("demographics")}
            />
          </TabsContent>

          {/* Geo-fencing Tab */}
          <TabsContent
            id="targeting-form-geofencing-content"
            value="geofencing"
            className="h-full flex-1 overflow-hidden"
          >
            <div id="targeting-form-geofencing-wrapper" className="h-full">
              <GeoFencingForm
                control={control}
                onFieldChange={handleTargetingFieldChange}
                geofencingFormData={watch().geofencing}
                setValue={setValue}
              />
            </div>
          </TabsContent>

          {/* Inventory Types Tab */}
          <TabsContent
            id="targeting-form-inventory-types-content"
            value="inventoryTypes"
            className="h-full"
          >
            <div className="p-4 space-y-4">
              {/* Programmatic Buying */}
              <div className="border border-mw-neutral-100 rounded-lg p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-4 text-mw-primary-500" />
                  <span className="text-sm font-semibold">
                    1. {tCampaigns("targeting.programmaticBuying.title")}
                  </span>
                </div>
                <p className="text-sm text-mw-neutral-500">
                  {tCampaigns("targeting.programmaticBuying.description")}
                </p>
                <div className="border border-mw-neutral-100 rounded-lg p-4 flex items-center justify-between gap-4">
                  <div>
                    <p className="text-sm font-medium">
                      {tCampaigns("targeting.programmaticBuying.toggleLabel")}
                    </p>
                    <p className="text-xs text-mw-neutral-500 mt-1">
                      {tCampaigns(
                        "targeting.programmaticBuying.toggleDescription",
                      )}
                    </p>
                  </div>
                  <Controller
                    name="programmaticOnly"
                    control={control}
                    render={({ field }) => (
                      <Switch
                        id="targeting-form-programmatic-only-switch"
                        checked={field.value}
                        disabled={isDspActive || !hasDigitalChannel}
                        onChange={(checked) => {
                          field.onChange(checked);
                          handleTargetingFieldChange({
                            programmaticOnly: checked,
                          } as Partial<TargetingFormData>);
                        }}
                      />
                    )}
                  />
                </div>
                {isDspActive && hasDigitalChannel && (
                  <p className="text-xs text-mw-neutral-500">
                    {tCampaigns("targeting.programmaticBuying.lockedNote")}
                  </p>
                )}
                {!hasDigitalChannel && (
                  <Alert variant="warning">
                    {tCampaigns(
                      "targeting.programmaticBuying.noDigitalChannelNote",
                    )}
                  </Alert>
                )}
              </div>

              {/* Inventory Types */}
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <Layers className="size-4 text-mw-primary-500" />
                  <span className="text-sm font-semibold">
                    2. {tCampaigns("targeting.inventoryClusters.title")}
                  </span>
                </div>
                <p className="text-sm text-mw-neutral-500">
                  {tCampaigns("targeting.inventoryClusters.description")}
                </p>
                <Controller
                  name="inventoryCluster"
                  control={control}
                  render={({ field }) => {
                    const clusters = field.value ?? [];
                    return (
                      <div className="space-y-3">
                        {selectedChannels.includes("DIGITAL_OOH") && (
                          <div className="border border-mw-neutral-100 rounded-lg p-4 space-y-3 bg-mw-primary-50">
                            <div className="flex items-center gap-2">
                              <Monitor className="size-4 text-mw-primary-500" />
                              <span className="text-sm font-semibold">
                                {tCampaigns(
                                  "targeting.inventoryClusters.digitalOoh.title",
                                )}
                              </span>
                            </div>
                            <div className="grid grid-cols-3 gap-3">
                              {INVENTORY_CLUSTERS_BY_CHANNEL.digitalOoh.map(
                                (value) => {
                                  const isComingSoon =
                                    INVENTORY_CLUSTERS_COMING_SOON.includes(
                                      value,
                                    );
                                  const checkbox = (
                                    <Checkbox
                                      id={`targeting-form-inventory-cluster-${value}`}
                                      label={tCampaigns(
                                        `targeting.inventoryClusters.options.${value}`,
                                      )}
                                      checked={clusters.includes(value)}
                                      disabled={isComingSoon}
                                      onChange={(e) =>
                                        handleClusterToggle(
                                          value,
                                          e.target.checked,
                                        )
                                      }
                                    />
                                  );
                                  return (
                                    <div
                                      key={value}
                                      className="border border-mw-neutral-100 rounded-lg p-3 bg-white"
                                    >
                                      {isComingSoon ? (
                                        <Tooltip
                                          content={tCampaigns(
                                            "targeting.comingSoon",
                                          )}
                                          position="top"
                                          triggerClassName="cursor-not-allowed"
                                        >
                                          {checkbox}
                                        </Tooltip>
                                      ) : (
                                        checkbox
                                      )}
                                    </div>
                                  );
                                },
                              )}
                            </div>
                          </div>
                        )}

                        {selectedChannels.includes("CLASSIC_OOH") && (
                          <div className="border border-mw-neutral-100 rounded-lg p-4 space-y-3 bg-mw-orange-warning-50">
                            <div className="flex items-center gap-2">
                              <Building2 className="size-4 text-mw-orange-warning-500" />
                              <span className="text-sm font-semibold">
                                {tCampaigns(
                                  "targeting.inventoryClusters.classicOoh.title",
                                )}
                              </span>
                            </div>
                            <div className="grid grid-cols-3 gap-3">
                              {INVENTORY_CLUSTERS_BY_CHANNEL.classicOoh.map(
                                (value) => {
                                  const isComingSoon =
                                    INVENTORY_CLUSTERS_COMING_SOON.includes(
                                      value,
                                    );
                                  const checkbox = (
                                    <Checkbox
                                      id={`targeting-form-inventory-cluster-${value}`}
                                      label={tCampaigns(
                                        `targeting.inventoryClusters.options.${value}`,
                                      )}
                                      checked={clusters.includes(value)}
                                      disabled={isComingSoon}
                                      onChange={(e) =>
                                        handleClusterToggle(
                                          value,
                                          e.target.checked,
                                        )
                                      }
                                    />
                                  );
                                  return (
                                    <div
                                      key={value}
                                      className="border border-mw-neutral-100 rounded-lg p-3 bg-white"
                                    >
                                      {isComingSoon ? (
                                        <Tooltip
                                          content={tCampaigns(
                                            "targeting.comingSoon",
                                          )}
                                          position="top"
                                          triggerClassName="cursor-not-allowed"
                                        >
                                          {checkbox}
                                        </Tooltip>
                                      ) : (
                                        checkbox
                                      )}
                                    </div>
                                  );
                                },
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  }}
                />
              </div>

              <div className="border border-mw-neutral-100 rounded-lg p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <MapPin className="size-4 text-mw-primary-500" />
                  <span className="text-sm font-semibold">
                    3. {tCampaigns("targeting.inventoryTypes.sectionTitle")}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-6">
                  {/* Digital OOH */}
                  <div>
                    <Label className="text-sm font-medium text-neutral-700 mb-2 block">
                      {tCampaigns("targeting.inventoryTypes.digitalOoh.title")}
                      <span className="text-xs text-mw-neutral-400 font-normal ml-1">
                        {tCampaigns("create_campaign.form.optional_label")}
                      </span>
                    </Label>
                    <Controller
                      name="venueTypes.digitalOoh"
                      control={control}
                      render={({ field }) => (
                        <MultiSelect
                          options={venueTreeNodes}
                          value={field.value}
                          onChange={(value) => field.onChange(value)}
                          onBlur={(value) => {
                            handleTargetingFieldChange({
                              venueTypes: {
                                ...getValues("venueTypes"),
                                digitalOoh: value,
                              },
                            } as Partial<TargetingFormData>);
                          }}
                          placeholder={tCampaigns(
                            "targeting.inventoryTypes.digitalOoh.placeholder",
                          )}
                          maxVisibleChips={3}
                          responsiveChips
                          disabled={!selectedChannels.includes("DIGITAL_OOH")}
                        />
                      )}
                    />
                  </div>

                  {/* Classic OOH */}
                  <div>
                    <Label className="text-sm font-medium text-neutral-700 mb-2 block">
                      {tCampaigns("targeting.inventoryTypes.classicOoh.title")}
                      <span className="text-xs text-mw-neutral-400 font-normal ml-1">
                        {tCampaigns("create_campaign.form.optional_label")}
                      </span>
                    </Label>
                    <Controller
                      name="venueTypes.classicOoh"
                      control={control}
                      render={({ field }) => (
                        <MultiSelect
                          options={venueTreeNodes}
                          value={field.value}
                          onChange={(value) => field.onChange(value)}
                          onBlur={(value) => {
                            handleTargetingFieldChange({
                              venueTypes: {
                                ...getValues("venueTypes"),
                                classicOoh: value,
                              },
                            } as Partial<TargetingFormData>);
                          }}
                          placeholder={tCampaigns(
                            "targeting.inventoryTypes.classicOoh.placeholder",
                          )}
                          maxVisibleChips={3}
                          responsiveChips
                          disabled={!selectedChannels.includes("CLASSIC_OOH")}
                        />
                      )}
                    />
                  </div>
                </div>
              </div>
            </div>
          </TabsContent>

          {/* Signals Tab */}
          <TabsContent
            id="targeting-form-signals-content"
            value="signals"
            className="h-full"
          >
            <div id="targeting-form-signals-wrapper" className="h-full">
              <SignalsForm
                control={control}
                onFieldChange={handleTargetingFieldChange}
              />
            </div>
          </TabsContent>
        </div>
      </Tabs>
    );
  },
);

TargetingForm.displayName = "TargetingForm";

export default TargetingForm;
