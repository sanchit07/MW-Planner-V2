// Shared types for media plan components

import { InventoryItem } from "../../../types/inventory.types";

export interface PresentationTheme {
  id: string;
  name: string;
  colors: {
    primary: string;
    secondary: string;
    accent: string;
  };
}

export type ViewType = "presentation" | "analytics";

export interface MediaPlanHeaderInfo {
  id?: string;
  name?: string;
  status?: string;
  budget?: number;
  totalCost?: number;
  impressions?: number;
  currency?: string;
  startDate?: string;
  endDate?: string;
  preparedBy?: string;
}

export interface MediaPlanBrandDetails {
  name?: string;
}

export interface MediaPlanSchedule {
  dailySchedule?: Record<string, number>;
}

export interface CostSplitItem {
  name: string;
  totalAmount: number;
  totalAmountInPercentage: number;
}

export interface SelectedInventorySummary {
  totalAssets?: number;
  totalFormatTypes?: number;
  totalCities?: number;
}

export interface SelectedInventory {
  summaryStatistics?: SelectedInventorySummary;
  locations?: InventoryItem[];
}

// export interface MediaPlanData {
//   headerInfo?: MediaPlanHeaderInfo;
//   brandDetails?: MediaPlanBrandDetails;
//   performanceMetrics?: CampaignForecastData | null;
//   audienceDemographics?: {
//     ageGroups?: string[];
//     incomeLevel?: string[];
//     interests?: string[];
//     lifestyle?: string[];
//   };
//   geographicTargeting?: Record<string, Record<string, string | number>[]>;
//   schedules?: MediaPlanSchedule;
//   selectedInventory?: SelectedInventory;
// }
