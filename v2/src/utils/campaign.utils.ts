import { formatCurrencyWithLocale } from "./currency";
import { findDurationInDays } from "./dateUtils";
import { toPascalCase } from "./stringManipulation.utils";
import {
  FALLBACK_VALUES,
  DEFAULT_CURRENCY,
} from "../constants/campaign.constants";
import { CampaignDisplay } from "../types/campaign-display.types";
import { CampaignListItem } from "../types/campaign.types";

/**
 * Formats currency amount with currency code
 */
export const formatCurrency = (
  amount: number | null | undefined,
  currency?: string,
  decimals: number = 2,
): string => {
  if (amount === null || amount === undefined) {
    return FALLBACK_VALUES.BUDGET;
  }
  return formatCurrencyWithLocale(
    amount,
    currency || DEFAULT_CURRENCY,
    decimals,
  );
};

/**
 * Normalizes campaign id to string (handles string | number from UI)
 */
export function normalizeCampaignId(id: string | number): string {
  return typeof id === "number" ? id.toString() : id;
}

/**
 * Normalizes campaign status to lowercase
 */
export const normalizeStatus = (
  status: string,
): CampaignDisplay["statusColor"] => {
  const normalized = status.toLowerCase();
  return normalized === "approved"
    ? "active"
    : normalized === "planned"
      ? "paused"
      : "draft";
};

/**
 * Transforms API campaign data to display format
 * Used in CampaignsPage for displaying campaigns in both list and grid views
 */
export const transformCampaignData = (
  apiCampaign: CampaignListItem,
): CampaignDisplay => ({
  id: apiCampaign.id,
  planNumber: apiCampaign.planNumber,
  campaignName: apiCampaign.name,
  userName:
    apiCampaign.firstName && apiCampaign.lastName
      ? `${apiCampaign.firstName} ${apiCampaign.lastName}`
      : apiCampaign.userName || FALLBACK_VALUES.USER_NAME,
  companyName: apiCampaign.companyName || FALLBACK_VALUES.USER_NAME,
  currentCompanyId: apiCampaign.currentCompanyId,
  currentCompanyName: apiCampaign.currentCompanyName,
  brand: apiCampaign.brand?.name || FALLBACK_VALUES.BRAND,
  category: apiCampaign.categoryName || FALLBACK_VALUES.CATEGORY,
  goals: {
    goalType: apiCampaign.goals?.typeName || FALLBACK_VALUES.GOAL_TYPE,
    targetName: apiCampaign.goals?.targetName || FALLBACK_VALUES.GOAL_TYPE,
    targetValue: apiCampaign.goals?.targetValue || 0,
    typeName: "",
  },
  status: toPascalCase(apiCampaign.status),
  budget: formatCurrency(apiCampaign.budget, apiCampaign.currency),
  totalCost: formatCurrency(apiCampaign.totalCost, apiCampaign.currency),
  rawBudget: apiCampaign.budget || 0,
  rawTotalCost: apiCampaign.totalCost || 0,
  startDate: apiCampaign.startDate,
  endDate: apiCampaign.endDate,
  daysLeft: (() => {
    const d = findDurationInDays(apiCampaign.startDate, apiCampaign.endDate);
    return d > 0 ? String(d) : FALLBACK_VALUES.DAYS_LEFT;
  })(),
  impressions: apiCampaign.estimatedImpression,
  reach: apiCampaign.estimatedReach,
  sov: apiCampaign.sov,
  sot: apiCampaign.sot,
  plannedSot: apiCampaign.plannedSot,
  totalSot: apiCampaign.totalSot,
  inventory: apiCampaign.inventory,
  statusColor: normalizeStatus(apiCampaign.status),
  dataMode: apiCampaign.dataMode,
});
