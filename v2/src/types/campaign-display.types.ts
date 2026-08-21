import { Goals } from "./campaign.types";

/**
 * Campaign status values
 */
export type CampaignStatus = "active" | "paused" | "draft" | "completed";

/**
 * Display view types
 */
export type ViewType = "list" | "grid";

/**
 * Campaign display model - transformed from API response
 * Used in both CampaignCard and CampaignsPage
 */
export interface CampaignDisplay {
  id: string;
  planNumber?: string;
  campaignName: string;
  userName: string;
  brand: string;
  status: string;
  statusColor: CampaignStatus;
  dataMode?: "live" | "demo";
  daysLeft: string;
  budget: string;
  totalCost: string;
  rawBudget?: number;
  rawTotalCost?: number;
  startDate: string;
  endDate: string;
  impressions: number;
  reach: number;
  category?: string;
  sov: number;
  sot?: number;
  plannedSot: number;
  totalSot: number;
  inventory: number;
  goals: Goals & {
    typeName: string;
  };
  companyName: string;
  currentCompanyId?: string;
  currentCompanyName?: string;
}
