import { CountryMarketDetails } from "./campaign.types";

export interface CurrencyInfo {
  code: string;
  symbol: string;
  label: string;
  isoCode?: string;
}

export interface CountryData {
  id: string;
  population: number;
  impressions: number;
  countryId: string;
  countryName: string;
  inventoryCount: number;
  inventoryCountByClassification?: Record<string, number>;
}

export interface GoalTypeConfig {
  label: string;
  placeholder: string;
  unit: string;
  max?: number;
  min?: number;
}

export interface GoalType {
  value: string;
  label: string;
  description: string;
  targetLabel: string;
  targetPlaceholder: string;
  unit: string;
  max?: number;
  min?: number;
}

export interface CampaignBudgetData {
  countryId?: string;
  currency?: string;
  budget?: number;
  goals?: {
    goalType?: string;
    targetName?: string;
    targetValue?: number;
  };
}

export interface BudgetFormData {
  country: string;
  currency: string;
  budget?: number;
  goalType?: string;
  targetName?: string;
  targetValue?: number;
}

export interface CountriesResponse {
  data: CountryData[];
}

export interface FormInitializationParams {
  campaignData: CampaignBudgetData | null;
  countries: CountryMarketDetails[];
  dynamicCurrencyMapping: Record<string, CurrencyInfo>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  setValue: (name: keyof BudgetFormData, value: any) => void;
}

export interface ValidationRule {
  condition: boolean;
  message: string;
}
