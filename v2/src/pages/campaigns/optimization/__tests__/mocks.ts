import { configureStore } from "@reduxjs/toolkit";
import campaignSlice, {
  type CampaignState,
} from "@services/campaign/campaignSlice";

/**
 * Shared mocks for optimization tests (store, campaign state, currency).
 * For @tolgee/react and @hooks/useAnnounce, use inline vi.mock in each test
 * file to avoid Vitest hoisting issues (e.g. useTranslate: () => ({ t: (k) => k })).
 */
export const DEFAULT_CAMPAIGN_ID = "campaign-1";
export const DEFAULT_CURRENCY = "USD";

export const defaultBudgetAllocation = {
  digital: 25,
  transit: 25,
  classic: 25,
  retail: 25,
};

export const defaultCampaignData = {
  id: DEFAULT_CAMPAIGN_ID,
  budgetAllocation: defaultBudgetAllocation,
  currency: DEFAULT_CURRENCY,
  startDate: "2025-01-01",
  endDate: "2025-12-31",
};

export const defaultCampaignState = {
  campaignId: DEFAULT_CAMPAIGN_ID,
  campaignData: defaultCampaignData,
};

export type CreateOptimizationStoreOptions = {
  campaignId?: string;
  campaignData?: Record<string, unknown>;
};

export function createOptimizationStore(
  options: CreateOptimizationStoreOptions = {},
) {
  const { campaignId = DEFAULT_CAMPAIGN_ID, campaignData = {} } = options;

  const campaignPreload: CampaignState = {
    currentCampaignName: "",
    campaignId,
    isCreating: false,
    createError: null,
    isEditMode: false,
    forecastData: null,
    recommendationRun: null,
    campaignData: {
      ...defaultCampaignData,
      ...campaignData,
    } as CampaignState["campaignData"],
  };

  return configureStore({
    reducer: {
      campaign: campaignSlice,
    },
    preloadedState: {
      campaign: campaignPreload,
    },
  });
}
