import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export interface GeneralSettings {
  dateFormat?: string;
  timeFormat?: string;
  currencyDisplay?: "CODE" | "SYMBOL";
  decimalPlaces?: number;
  fiscalYearStartMonth?: number;
  helpBubblesEnabled?: boolean;
  tourEnabled?: boolean;
}

export interface TerminologySettings {
  customTerms?: Record<string, string>;
}

export interface TargetingSettings {
  ageGroupRanges?: string[];
  incomeBrackets?: string[];
  geographyLevels?: string[];
  radiusUnit?: string;
  defaultRadius?: number;
}

export interface NumberFormatSettings {
  thousandsSeparator?: string;
  decimalSeparator?: string;
  compactNotation?: boolean;
}

export interface DashboardSettings {
  visibleWidgetKeys?: string[];
  defaultView?: string;
}

export interface CampaignToggleSettings {
  setupFeaturesEnabled?: boolean;
  targetingFeaturesEnabled?: boolean;
  advancedFeaturesEnabled?: boolean;
}

export interface InventoryToggleSettings {
  visibleColumns?: string[];
  visibleFilters?: string[];
}

export interface PoiSettings {
  maxPoiPerCampaign?: number;
  radiusOptions?: number[];
  visibilityScope?: "COMPANY" | "USER";
}

export interface ScheduleSettings {
  frequencyCap?: number | null;
  shareOfVoiceDefault?: number;
  spotDurationSeconds?: number;
}

export interface ReportsSettings {
  defaultColumns?: string[];
  defaultExportFormat?: string;
}

export interface FiltersSettings {
  pinnedFilterKeys?: string[];
}

export interface ApprovalsSettings {
  mediaOwnerAutoApproveHours?: number;
  reminderBeforeHours?: number;
}

export interface BonusWorkflowSettings {
  enabled?: boolean;
  allowedBonusTypes?: string[];
}

export interface PlannerConfiguration {
  companyId: string;
  general?: GeneralSettings;
  terminology?: TerminologySettings;
  targeting?: TargetingSettings;
  numberFormats?: NumberFormatSettings;
  dashboard?: DashboardSettings;
  campaign?: CampaignToggleSettings;
  inventory?: InventoryToggleSettings;
  poi?: PoiSettings;
  schedule?: ScheduleSettings;
  reports?: ReportsSettings;
  filters?: FiltersSettings;
  approvals?: ApprovalsSettings;
  bonusWorkflow?: BonusWorkflowSettings;
}

export const plannerConfigurationApi = createApi({
  reducerPath: "plannerConfigurationApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["PlannerConfiguration"],
  endpoints: (builder) => ({
    getPlannerConfiguration: builder.query<
      SuccessResponse<PlannerConfiguration> | CustomErrorResponse,
      { companyId: string }
    >({
      query: ({ companyId }) => ({
        url: `/config/settings/${companyId}`,
        method: "GET",
      }),
      providesTags: ["PlannerConfiguration"],
    }),
    updatePlannerConfiguration: builder.mutation<
      SuccessResponse<PlannerConfiguration> | CustomErrorResponse,
      { companyId: string; update: Partial<PlannerConfiguration> }
    >({
      query: ({ companyId, update }) => ({
        url: `/config/settings/${companyId}`,
        method: "PUT",
        data: update,
      }),
      invalidatesTags: ["PlannerConfiguration"],
    }),
  }),
});

export const {
  useGetPlannerConfigurationQuery,
  useUpdatePlannerConfigurationMutation,
} = plannerConfigurationApi;
