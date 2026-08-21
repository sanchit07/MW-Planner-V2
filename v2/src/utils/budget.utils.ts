import { GOAL_TYPE_CONFIG } from "@constants/budget.constants";

import {
  CountryData,
  BudgetFormData,
  GoalType,
  ValidationRule,
} from "../types/budget.types";
import { CountryMarketDetails } from "../types/campaign.types";

/**
 * Formats large numbers with appropriate suffixes (K, M, B)
 * If rounding is true, it rounds to the nearest whole number with suffix; otherwise, it formats with two decimal places.
 */
export const formatNumber = (
  num: number,
  rounding: boolean = false,
): string => {
  const format = (value: number, suffix: string) =>
    rounding ? Math.round(value) + suffix : value.toFixed(2) + suffix;

  if (num === 0) return "0";
  if (num >= 1_000_000_000) return format(num / 1_000_000_000, "B");
  if (num >= 1_000_000) return format(num / 1_000_000, "M");
  if (num >= 1_000) return format(num / 1_000, "K");

  return rounding ? Math.round(num).toString() : num.toLocaleString();
};

/**
 * Formats a numeric value for display inside a text input, inserting thousand
 * separators in the integer part while leaving the decimal part untouched.
 *
 * Accepts either a number or the raw string the user is typing, so a trailing
 * decimal point (e.g. "1000.") is preserved to allow continued typing.
 * Returns "" for empty / null / undefined input.
 */
export const formatNumberInput = (
  value: number | string | null | undefined,
): string => {
  if (value === null || value === undefined || value === "") return "";

  const raw = typeof value === "number" ? value.toString() : value;
  // Strip any existing grouping commas before regrouping.
  const cleaned = raw.replace(/,/g, "");

  const [intPart, ...decParts] = cleaned.split(".");
  const groupedInt = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");

  // Preserve a trailing "." (mid-typing) and any decimal digits.
  return cleaned.includes(".")
    ? `${groupedInt}.${decParts.join(".")}`
    : groupedInt;
};

/**
 * Parses a display string (possibly containing thousand separators) back into a
 * number. Returns undefined for empty or non-numeric input.
 */
export const parseNumberInput = (value: string): number | undefined => {
  if (value === "") return undefined;
  const cleaned = value.replace(/,/g, "");
  const num = parseFloat(cleaned);
  return isNaN(num) ? undefined : num;
};

/**
 * Transforms countries API response to expected format
 */
export const transformCountriesData = (
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  countriesResponse: any,
): CountryData[] => {
  if (!countriesResponse?.data) return [];

  return countriesResponse.data
    .map((country: CountryMarketDetails) => ({
      id: country.id,
      population: country.population,
      impressions: country.impressions,
      countryId: country.countryId,
      countryName: country.countryName,
      inventoryCount: country.inventoryCount,
      inventoryCountByClassification: country.inventoryCountByClassification,
    }))
    .sort((a: CountryData, b: CountryData) =>
      a.countryName.localeCompare(b.countryName),
    );
};

/**
 * Creates goal types configuration
 */
export const createGoalTypes = (
  tCampaigns: (key: string) => string,
): GoalType[] => {
  return Object.entries(GOAL_TYPE_CONFIG).map(([key, config]) => {
    const k = key.toLowerCase();
    return {
      value: key,
      label: tCampaigns(`budget_goal.goal_types.${k}`),
      description: tCampaigns(`budget_goal.goal_types.${k}_desc`),
      targetLabel:
        tCampaigns(`budget_goal.goal_types.${k}_target_label`) || config.label,
      targetPlaceholder:
        tCampaigns(`budget_goal.goal_types.${k}_placeholder`) ||
        config.placeholder,
      unit: tCampaigns(`budget_goal.goal_types.${k}_unit`) || config.unit,
      max: config.max,
      min: config.min,
    };
  });
};

/**
 * Creates validation rules for form validation
 */
export const createValidationRules = (
  data: BudgetFormData,
): ValidationRule[] => {
  return [
    { condition: !data.country, message: "Country is required" },
    { condition: !data.currency, message: "Currency is required" },
    {
      condition: !data.budget || data.budget <= 0,
      message: "Budget must be greater than 0",
    },
    {
      condition:
        data.goalType && (!data.targetValue || data.targetValue <= 0)
          ? true
          : false,
      message: "Target value is required when goal type is selected",
    },
    {
      condition:
        (data.goalType?.toLowerCase() === "other" ||
          data.goalType === "OTHER") &&
        (!data.targetName || !data.targetName.trim()),
      message: "Target name is required when goal type is 'other'",
    },
  ];
};

/**
 * Normalizes a goalType string from either the API display format (e.g. "Ad Plays")
 * or the constant key format (e.g. "ADPLAYS") into a consistent uppercase key.
 *
 * Handles mismatches between what the backend stores vs. what the view-campaign
 * endpoint returns (e.g. "Ad Plays" instead of "ADPLAYS").
 */
export function normalizeGoalType(goalType?: string): string | undefined {
  if (!goalType) return undefined;
  // Strip spaces, %, and normalize to uppercase for comparison
  const normalized = goalType.toUpperCase().replace(/[\s%]+/g, "");
  if (normalized === "ADPLAYS" || normalized === "ADPLAY") return "ADPLAYS";
  if (normalized === "SOV" || normalized === "SHAREOFVOICE") return "SOV";
  if (normalized === "IMPRESSIONS" || normalized === "NUMBEROFIMPRESSIONS")
    return "IMPRESSIONS";
  if (normalized === "REACH" || normalized === "UNIQUEUSERS") return "REACH";
  return goalType.toUpperCase().replace(/\s+/g, "");
}
