import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Label } from "@components/ui/Label";
import MultiSelect, { TreeNode } from "@components/ui/MultiSelect";
import { TargetingFormData } from "@schemas/campaigns/targeting.schema";
import {
  useConfigurationMetadataQuery,
  DemographicMetaData,
  ConfigurationsMetaData,
  setConfigurationMetaData,
} from "@services/configuration-metadata/configurationMetadataSlice";
import { useTranslate, useTolgee } from "@tolgee/react";
import { Target } from "lucide-react";
import { useMemo, useCallback, useEffect } from "react";
import { Control, Controller } from "react-hook-form";

import { useAppSelector, useAppDispatch } from "../../store";

interface DemographicComponentProps {
  control: Control<TargetingFormData>;
  onFieldChange: (value: {
    demographics: Record<string, unknown>;
  }) => Promise<void>;
  demographicFormData: TargetingFormData["demographics"];
}

const DemographicComponent = ({
  control,
  onFieldChange,
  demographicFormData,
}: DemographicComponentProps) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const language = useTolgee(["language"]).getLanguage();
  const dispatch = useAppDispatch();

  // Get demographic metadata from Redux state
  const demographicMetaDataState = useAppSelector(
    (state) => state.configurationMetadata.demographics,
  );

  // Check if we have data in state
  const hasStateData = useMemo(() => {
    return Object.values(demographicMetaDataState).some(
      (arr) => Array.isArray(arr) && arr.length > 0,
    );
  }, [demographicMetaDataState]);

  // Always fetch config with current language so names reflect the active locale.
  // RTK Query caches per-language, so switching back to a previous language is instant.
  const { data: demographicsMetadata, isLoading: isDemographicsLoading } =
    useConfigurationMetadataQuery({ language });

  // Update Redux state when API data is received
  useEffect(() => {
    if (
      demographicsMetadata &&
      "success" in demographicsMetadata &&
      demographicsMetadata.success
    ) {
      dispatch(setConfigurationMetaData(demographicsMetadata));
    }
  }, [demographicsMetadata, dispatch]);

  // Transform to TreeNode format (for age, gender, income).
  // Income passes includeDescription=false: its descriptions carry numeric
  // range values that must not be shown — text only (SI 46).
  const transformToTreeNodes = useCallback(
    (
      items: DemographicMetaData[],
      includeDescription: boolean = true,
    ): TreeNode[] => {
      return items.map((item: DemographicMetaData) => ({
        label: item.name,
        value: item.demoKey,
        id: item.demoKey,
        disabled: false,
        ...(item.children &&
          item.children.length && {
            children: transformToTreeNodes(item.children, includeDescription),
          }),
        ...(includeDescription &&
          item.description && {
            description: item.description,
          }),
      }));
    },
    [],
  );

  // Transform to TreeNode format for interests/behavior.
  // label comes from item.name (API-translated via Accept-Language header).
  // value/id stay as item.stringValue || item.demoKey so saved API payloads are unchanged.
  const transformToTreeNodesWithStringValue = useCallback(
    (items: DemographicMetaData[], isChildren: boolean = false): TreeNode[] => {
      return items.map((item: DemographicMetaData) => ({
        label: item.name,
        value: item.stringValue || item.demoKey,
        id: item.stringValue || item.demoKey,
        disabled: false,
        ...(item.children &&
          item.children.length && {
            children: transformToTreeNodesWithStringValue(item.children, true),
          }),
        ...(item.description &&
          !isChildren && {
            description: item.description,
          }),
      }));
    },
    [],
  );

  // Transform API data to TreeNode format
  const transformDemographicsData = useMemo(() => {
    // First priority: RTK Query data (language-aware — names come from the API's Accept-Language response)
    if (
      demographicsMetadata &&
      "success" in demographicsMetadata &&
      demographicsMetadata.success
    ) {
      const apiData = (demographicsMetadata as { data: ConfigurationsMetaData })
        .data.demographics;

      if (!apiData) {
        return {
          age: [],
          gender: [],
          income: [],
          interests: [],
          behavior: [],
        };
      }

      // Check if apiData is an array (flat structure) or object (grouped structure)
      if (Array.isArray(apiData)) {
        // Group data by demoType if it's an array
        const groupedData = apiData.reduce(
          (acc, item) => {
            const demoType = item.demoType || "unknown";
            if (!acc[demoType]) {
              acc[demoType] = [];
            }
            acc[demoType].push(item);
            return acc;
          },
          {} as Record<string, DemographicMetaData[]>,
        );

        return {
          age: transformToTreeNodes(groupedData["age"] || []),
          gender: transformToTreeNodes(groupedData["gender"] || []),
          income: transformToTreeNodes(groupedData["income"] || [], false),
          interests: transformToTreeNodesWithStringValue(
            groupedData["interests"] || [],
          ),
          behavior: transformToTreeNodesWithStringValue(
            groupedData["behavior"] || [],
          ),
        };
      } else {
        // Data is already grouped as an object
        return {
          age: transformToTreeNodes(apiData.age || []),
          gender: transformToTreeNodes(apiData.gender || []),
          income: transformToTreeNodes(apiData.income || [], false),
          interests: transformToTreeNodesWithStringValue(
            apiData.interests || [],
          ),
          behavior: transformToTreeNodesWithStringValue(apiData.behavior || []),
        };
      }
    }

    // Fallback: Redux state (may be from a previous language, used while new language data loads)
    if (hasStateData) {
      return {
        age: transformToTreeNodes(demographicMetaDataState.age || []),
        gender: transformToTreeNodes(demographicMetaDataState.gender || []),
        income: transformToTreeNodes(
          demographicMetaDataState.income || [],
          false,
        ),
        interests: transformToTreeNodesWithStringValue(
          demographicMetaDataState.interests || [],
        ),
        behavior: transformToTreeNodesWithStringValue(
          demographicMetaDataState.behavior || [],
        ),
      };
    }

    return {
      age: [],
      gender: [],
      income: [],
      interests: [],
      behavior: [],
    };
  }, [
    hasStateData,
    demographicMetaDataState,
    demographicsMetadata,
    transformToTreeNodes,
    transformToTreeNodesWithStringValue,
  ]);

  // Memoized options to prevent unnecessary re-renders
  const ageOptions = useMemo(() => {
    return transformDemographicsData.age.length > 0
      ? transformDemographicsData.age.map((item) => ({
          ...item,
          label: item.label,
        }))
      : transformToTreeNodes([
          {
            demoKey: "18_24",
            name: "18-24",
          },
          {
            demoKey: "25_34",
            name: "25–34",
            description: "",
          },
          {
            demoKey: "35_44",
            name: "35–44",
            description: "",
          },
          {
            demoKey: "45_54",
            name: "45–54",
            description: "",
          },
          {
            demoKey: "55_64",
            name: "55–64",
            description: "",
          },
          {
            demoKey: "65+",
            name: "65+",
            description: "",
          },
        ]);
  }, [transformDemographicsData.age, transformToTreeNodes]);

  const genderOptions = useMemo(() => {
    // "other" is intentionally excluded from both API and fallback sources.
    return transformDemographicsData.gender.length > 0
      ? transformDemographicsData.gender.filter(
          (node) => node.value?.toLowerCase() !== "other",
        )
      : transformToTreeNodes([
          {
            demoKey: "male",
            name: tCampaigns("targeting.fallback.gender.male"),
            description: "",
          },
          {
            demoKey: "female",
            name: tCampaigns("targeting.fallback.gender.female"),
            description: "",
          },
        ]);
  }, [transformDemographicsData.gender, transformToTreeNodes, tCampaigns]);

  const incomeOptions = useMemo(() => {
    return transformDemographicsData.income.length > 0
      ? transformDemographicsData.income
      : transformToTreeNodes([
          {
            demoKey: "low",
            name: tCampaigns("targeting.fallback.income.low"),
            description: "<30,000",
          },
          {
            demoKey: "lower_middle",
            name: tCampaigns("targeting.fallback.income.lower_middle"),
            description: "30,000–50,000",
          },
          {
            demoKey: "middle",
            name: tCampaigns("targeting.fallback.income.middle"),
            description: "50,000–100,000",
          },
          {
            demoKey: "upper_middle",
            name: tCampaigns("targeting.fallback.income.upper_middle"),
            description: "100,000–150,000",
          },
          {
            demoKey: "high",
            name: tCampaigns("targeting.fallback.income.high"),
            description: ">150,000",
          },
        ]);
  }, [transformDemographicsData.income, transformToTreeNodes, tCampaigns]);

  const interestOptions = useMemo(() => {
    return transformDemographicsData.interests.length > 0
      ? transformDemographicsData.interests
      : transformToTreeNodes([
          {
            demoKey: "Sports & Fitness",
            name: tCampaigns("targeting.fallback.interests.sports_fitness"),
            description: "",
          },
        ]);
  }, [transformDemographicsData.interests, transformToTreeNodes, tCampaigns]);

  const behaviorOptions = useMemo(() => {
    return transformDemographicsData.behavior.length > 0
      ? transformDemographicsData.behavior
      : transformToTreeNodes([
          {
            demoKey: "commuters",
            name: tCampaigns("targeting.fallback.behavior.commuters"),
            description: "People traveling to or from work during peak hours",
          },
          {
            demoKey: "Shoppers",
            name: tCampaigns("targeting.fallback.behavior.shoppers"),
            description: "Active shoppers in retail environments",
          },
        ]);
  }, [transformDemographicsData.behavior, transformToTreeNodes, tCampaigns]);

  // Show loading state only if we don't have state data and API is loading
  if (!hasStateData && isDemographicsLoading) {
    return (
      <Card className="border-0">
        <CardContent className="p-6">
          <div className="flex items-center justify-center h-32">
            <div className="text-center">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-mw-primary-600 mx-auto mb-2"></div>
              <p className="text-sm text-mw-neutral-600">
                {tCampaigns("targeting.demography_selection.loading")}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="flex p-2 pt-4 gap-6">
      <Card className="rounded-xs flex-1">
        <CardHeader className="p-4">
          <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
            <div className="w-10 h-10 bg-mw-cyan-50 rounded-lg flex items-center justify-center">
              <Target className="w-5 h-5 text-mw-teal-600" />
            </div>
            <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
              <div className="self-stretch inline-flex justify-start items-start gap-2">
                <CardTitle className="text-sm font-medium leading-none">
                  {tCampaigns("targeting.demography_selection.title")}
                </CardTitle>
              </div>
              <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                {tCampaigns("targeting.demography_selection.description")}
              </p>
            </div>
          </div>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-3">
          {/* Age Group */}
          <div className="mb-2">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("targeting.age.title")}
            </Label>
            <Controller
              name="demographics.age"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={ageOptions}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      demographics: { ...demographicFormData, age: value },
                    });
                  }}
                  placeholder={tCampaigns("targeting.age.placeholder")}
                  maxVisibleChips={2}
                />
              )}
            />
          </div>

          {/* Gender */}
          <div className="mb-2">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("targeting.gender.title")}
            </Label>
            <Controller
              name="demographics.gender"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={genderOptions}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      demographics: { ...demographicFormData, gender: value },
                    });
                  }}
                  placeholder={tCampaigns("targeting.gender.placeholder")}
                  maxVisibleChips={2}
                />
              )}
            />
          </div>

          {/* Income */}
          <div className="mb-2">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("targeting.incomeGroup.title")}
            </Label>
            <Controller
              name="demographics.income"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={incomeOptions}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      demographics: { ...demographicFormData, income: value },
                    });
                  }}
                  placeholder={tCampaigns("targeting.incomeGroup.placeholder")}
                  maxVisibleChips={2}
                />
              )}
            />
          </div>

          {/* Interest & Activities */}
          <div className="mb-2">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("targeting.interestActivity.title")}
            </Label>
            <Controller
              name="demographics.interests"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={interestOptions}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      demographics: {
                        ...demographicFormData,
                        interests: value,
                      },
                    });
                  }}
                  placeholder={tCampaigns(
                    "targeting.interestActivity.placeholder",
                  )}
                  maxVisibleChips={3}
                />
              )}
            />
          </div>

          {/* Behavior Type */}
          <div className="mb-2">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("targeting.audienceBehavior.title")}
            </Label>
            <Controller
              name="demographics.behavior"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={behaviorOptions}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      demographics: { ...demographicFormData, behavior: value },
                    });
                  }}
                  placeholder={tCampaigns(
                    "targeting.audienceBehavior.placeholder",
                  )}
                  maxVisibleChips={3}
                />
              )}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default DemographicComponent;
