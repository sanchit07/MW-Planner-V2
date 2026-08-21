import { describe, it, expect } from "vitest";

import type { CampaignCreateResponse } from "../../../../types/campaign.types";
import { buildCampaignTargetingFilters } from "../inventoryFilters.utils";

function campaign(
  partial: Partial<CampaignCreateResponse>,
): CampaignCreateResponse {
  return partial as CampaignCreateResponse;
}

describe("buildCampaignTargetingFilters", () => {
  it("returns {} when campaignData is null", () => {
    expect(buildCampaignTargetingFilters(null)).toEqual({});
  });

  it("maps country to countries[]", () => {
    const f = buildCampaignTargetingFilters(campaign({ countryId: "Japan" }));
    expect(f.countries).toEqual(["Japan"]);
  });

  it("returns empty countries when no countryId", () => {
    const f = buildCampaignTargetingFilters(campaign({}));
    expect(f.countries).toEqual([]);
  });

  it("maps demographics and geofencing from targeting", () => {
    const f = buildCampaignTargetingFilters(
      campaign({
        countryId: "Japan",
        targeting: {
          demographics: {
            age: ["18-24"],
            gender: ["male"],
            venues: ["Mall"],
          },
          geofencing: {
            geometries: [{ type: "Polygon", coordinates: [], included: true }],
            locations: [{ lat: 1, lng: 2, radius: 3, address: "a" }],
          },
        },
      } as unknown as Partial<CampaignCreateResponse>),
    );
    expect(f.demographics).toEqual({ age: ["18-24"], gender: ["male"] });
    // Demographic venues are intentionally NOT sent to /filter as venueTypes.
    expect(f.venueTypes).toBeUndefined();
    expect(f.geofencing?.geometries).toHaveLength(1);
    expect(f.geofencing?.locations).toHaveLength(1);
  });

  it("omits demographics when targeting fields are empty", () => {
    const f = buildCampaignTargetingFilters(
      campaign({
        countryId: "Japan",
        targeting: {
          demographics: { age: [], gender: [], venues: [] },
        },
      } as unknown as Partial<CampaignCreateResponse>),
    );
    expect(f.demographics).toBeUndefined();
    expect(f.venueTypes).toBeUndefined();
  });

  it("maps inventoryCluster straight through", () => {
    const f = buildCampaignTargetingFilters(
      campaign({
        targeting: {
          inventoryCluster: ["DIGITAL", "DIGITAL_NETWORK", "CLASSIC_TRANSIT"],
        },
      } as unknown as Partial<CampaignCreateResponse>),
    );
    expect(f.inventoryCluster).toEqual([
      "DIGITAL",
      "DIGITAL_NETWORK",
      "CLASSIC_TRANSIT",
    ]);
  });

  it("omits inventoryCluster when no inventory clusters are selected", () => {
    const f = buildCampaignTargetingFilters(
      campaign({
        targeting: {
          inventoryCluster: [],
        },
      } as unknown as Partial<CampaignCreateResponse>),
    );
    expect(f.inventoryCluster).toBeUndefined();
  });
});
