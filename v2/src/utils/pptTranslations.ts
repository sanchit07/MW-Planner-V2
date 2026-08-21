import { getItem } from "./storage";

type TranslationData = {
  media_plan: {
    title_slide: {
      total_budget: string;
      total_cost: string;
      impressions: string;
      campaign_period: string;
      prepared_by: string;
      plan_dates: string;
      planned_by: string;
      days: string;
      moving_walls: string;
      moving_walls_internal: string;
    };
    performance_metrics: {
      title: string;
      subtitle: string;
      est_impressions: string;
      campaign_duration: string;
      est_reach: string;
      unique_viewers: string;
      avg_frequency: string;
      avg_per_person: string;
      avg_ecpm: string;
      effective_rate: string;
      avg_cpm: string;
      media_owner_rate: string;
      est_ad_plays: string;
      total_displays: string;
      sov: string;
      share_of_voice: string;
      sot: string;
      daily_hours_used: string;
    };
    targeting_strategy: {
      title: string;
      subtitle: string;
      age_groups: string;
      income_level: string;
      interests: string;
      lifestyle: string;
    };
    geographic_targeting: {
      title: string;
      subtitle_cities: string;
      subtitle_venue_types: string;
      cities: string;
      venue_type: string;
      ad_plays: string;
      budget_allocation_percent: string;
      of_total_cost: string;
      page: string;
      of: string;
    };
    schedule: {
      title: string;
      subtitle: string;
      daypart_performance: string;
      high_traffic: string;
      medium_traffic: string;
      low_traffic: string;
      chart_unavailable: string;
      chart_not_provided: string;
    };
    cost_breakdown: {
      title: string;
      subtitle: string;
      cost_split: string;
      fee_structure: string;
      media_cost: string;
      platform_fee: string;
      agency_commission: string;
      total_investment: string;
    };
    selected_inventory: {
      title: string;
      subtitle: string;
      total_inventories: string;
      format_types: string;
      cities: string;
      est_impressions: string;
      inventory_name: string;
      type: string;
      city: string;
      schedule_dates: string;
      schedule_hours: string;
      impression: string;
      cost: string;
      total_media_cost: string;
      schedule_title: string;
      schedule_subtitle: string;
    };
    map_view: {
      title: string;
      subtitle: string;
      map_load_error: string;
      click_to_view: string;
    };
    common_labels: {
      na: string;
      moving_walls: string;
    };
  };
};

let cachedTranslations: TranslationData | null = null;
let cachedLanguage: string | null = null;

/**
 * Loads translations for the current language
 */
export const loadTranslations = async (
  language: string = "en",
): Promise<TranslationData> => {
  // Return cached translations if language hasn't changed
  if (cachedTranslations && cachedLanguage === language) {
    return cachedTranslations;
  }

  try {
    const translations = await import(
      `../assets/i18n/campaigns/${language}.json`
    );
    cachedTranslations = translations.default as TranslationData;
    cachedLanguage = language;
    return cachedTranslations;
  } catch (error) {
    console.error(
      `Failed to load translations for language ${language}:`,
      error,
    );
    // Fallback to English
    if (language !== "en") {
      return loadTranslations("en");
    }
    throw error;
  }
};

/**
 * Gets the current language from storage
 */
export const getCurrentLanguage = (): string => {
  const storedLanguage = getItem("mw-planner-language");
  const availableLanguages = ["en", "ja"];
  return storedLanguage && availableLanguages.includes(storedLanguage)
    ? storedLanguage
    : "en";
};

/**
 * Gets translations for PPT generation
 * This should be called before generating the PPT
 */
export const getPPTTranslations = async (): Promise<
  TranslationData["media_plan"]
> => {
  const language = getCurrentLanguage();
  const translations = await loadTranslations(language);
  return translations.media_plan;
};
