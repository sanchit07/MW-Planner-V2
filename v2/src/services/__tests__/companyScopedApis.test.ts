import { describe, expect, it } from "vitest";

import { campaignApi } from "@services/campaign/campaignSlice";
import { resetCompanyScopedApiState } from "@services/companyScopedApis";
import { store } from "@store";

describe("resetCompanyScopedApiState", () => {
  it("drops cached company-scoped query data so a company switch cannot serve the previous company's responses", async () => {
    // Seed the cache as if a buyer had loaded a campaign before switching.
    await store.dispatch(
      campaignApi.util.upsertQueryData(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        "getCampaign" as any,
        "campaign-1" as any,
        { id: "campaign-1", name: "Buyer view" } as any,
      ),
    );
    const before = campaignApi.endpoints.getCampaign.select(
      "campaign-1" as never,
    )(store.getState() as never);
    expect(before.data).toBeDefined();

    resetCompanyScopedApiState(store.dispatch);

    const after = campaignApi.endpoints.getCampaign.select(
      "campaign-1" as never,
    )(store.getState() as never);
    expect(after.data).toBeUndefined();
    expect(after.isUninitialized).toBe(true);
  });
});
