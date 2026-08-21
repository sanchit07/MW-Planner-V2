import type { CampaignCreateResponse } from "src/types/campaign.types";

import type {
  GeofencingLocation,
  InventoryFilterRequest,
} from "../../../types/inventory.types";

/**
 * Builds the campaign-scoped portion of an inventory filter request
 * (country + targeting). Shared by the manual-edit list and the
 * Restore-AI-recommendation deselect-all call so both target the same set.
 */
export function buildCampaignTargetingFilters(
  campaignData: CampaignCreateResponse | null,
): InventoryFilterRequest {
  const filters: InventoryFilterRequest = {};
  if (!campaignData) return filters;

  filters.countries = campaignData.countryId ? [campaignData.countryId] : [];

  const targeting = campaignData.targeting;
  if (targeting) {
    if (targeting.demographics?.age?.length) {
      filters.demographics ??= {};
      filters.demographics.age = targeting.demographics.age;
    }
    if (targeting.demographics?.gender?.length) {
      filters.demographics ??= {};
      filters.demographics.gender = targeting.demographics.gender;
    }
    if (targeting.geofencing) {
      filters.geofencing = {
        geometries: targeting.geofencing.geometries?.map((geo) => ({
          type: geo.type as "Polygon" | "Circle" | "LineString",
          coordinates: geo.coordinates,
          included: geo.included,
        })),
        locations: targeting.geofencing.locations as GeofencingLocation[],
      };
    }
    if (targeting.inventoryCluster?.length) {
      filters.inventoryCluster = targeting.inventoryCluster;
    }
  }

  return filters;
}
