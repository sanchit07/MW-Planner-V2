// Verifies the fix for: creating/finalizing/autosaving a campaign left the
// Dashboard's RTK Query cache (a separate API slice) stale until its default
// 60s keepUnusedDataFor window expired. campaignSlice.ts now dispatches
// dashboardApi.util.invalidateTags(...) from each mutation's onQueryStarted,
// so an actively-subscribed dashboard query refetches immediately instead of
// waiting out the cache window.
import { configureStore } from "@reduxjs/toolkit";
import { describe, expect, it, vi, beforeEach } from "vitest";

type MockQueryResult =
  | { data: { success: boolean; data: unknown } }
  | { error: { status: number; data: unknown } };

const queryFnSpy = vi.fn(
  (): Promise<MockQueryResult> =>
    Promise.resolve({ data: { success: true, data: {} } }),
);

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => queryFnSpy(),
}));

import { dashboardApi } from "../../dashboard/dashboardSlice";
import { campaignApi } from "../campaignSlice";

function makeStore() {
  return configureStore({
    reducer: {
      [campaignApi.reducerPath]: campaignApi.reducer,
      [dashboardApi.reducerPath]: dashboardApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(
        campaignApi.middleware,
        dashboardApi.middleware,
      ),
  });
}

const dashboardParams = {
  startDate: "2026-01-01",
  endDate: "2026-01-31",
  companyId: "company-1",
};

// Wait past the microtask queue for the invalidation-triggered refetch
// (a brand-new async request cycle) to actually call the base query.
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("campaignApi mutations — dashboard cache invalidation", () => {
  let store: ReturnType<typeof makeStore>;

  beforeEach(() => {
    queryFnSpy.mockClear();
    store = makeStore();
  });

  it("createCampaign triggers a dashboard refetch for an active subscriber", async () => {
    const subscription = store.dispatch(
      dashboardApi.endpoints.getCampaignOverviewByStatus.initiate(
        dashboardParams,
      ),
    );
    await subscription;
    expect(queryFnSpy).toHaveBeenCalledTimes(1);

    await store.dispatch(
      campaignApi.endpoints.createCampaign.initiate({
        name: "Test Campaign",
        countryId: "SG",
        startDate: "2026-01-01",
        endDate: "2026-01-31",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any),
    );
    await flush();

    // 1 initial dashboard fetch + 1 mutation call + 1 invalidation-triggered
    // dashboard refetch.
    expect(queryFnSpy).toHaveBeenCalledTimes(3);
    subscription.unsubscribe();
  });

  it("updateCampaign triggers a dashboard refetch for an active subscriber", async () => {
    const subscription = store.dispatch(
      dashboardApi.endpoints.getCampaignPerformance.initiate({
        ...dashboardParams,
      }),
    );
    await subscription;
    expect(queryFnSpy).toHaveBeenCalledTimes(1);

    await store.dispatch(
      campaignApi.endpoints.updateCampaign.initiate({
        id: "camp-1",
        name: "Test Campaign",
        status: "PLANNED",
        countryId: "SG",
        startDate: "2026-01-01",
        endDate: "2026-01-31",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any),
    );
    await flush();

    // 1 initial dashboard fetch + 1 mutation call + 1 invalidation-triggered
    // dashboard refetch.
    expect(queryFnSpy).toHaveBeenCalledTimes(3);
    subscription.unsubscribe();
  });

  it("autosaveCampaign triggers a dashboard refetch for an active subscriber", async () => {
    const subscription = store.dispatch(
      dashboardApi.endpoints.getPerformanceSummaryCost.initiate({
        ...dashboardParams,
      }),
    );
    await subscription;
    expect(queryFnSpy).toHaveBeenCalledTimes(1);

    await store.dispatch(
      campaignApi.endpoints.autosaveCampaign.initiate({
        id: "camp-1",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        data: { name: "Updated Name" } as any,
      }),
    );
    await flush();

    // 1 initial dashboard fetch + 1 mutation call + 1 invalidation-triggered
    // dashboard refetch.
    expect(queryFnSpy).toHaveBeenCalledTimes(3);
    subscription.unsubscribe();
  });

  it("does not invalidate the dashboard cache when the mutation fails", async () => {
    const subscription = store.dispatch(
      dashboardApi.endpoints.getCampaignOverviewByStatus.initiate(
        dashboardParams,
      ),
    );
    await subscription;
    expect(queryFnSpy).toHaveBeenCalledTimes(1);

    // Base query contract: a failed request resolves with an `error` field
    // (never rejects) — that's what makes onQueryStarted's `queryFulfilled`
    // reject, taking the catch branch instead of invalidating. Queued here
    // (not before the fetch above) so it applies to the mutation call.
    queryFnSpy.mockImplementationOnce(() =>
      Promise.resolve({ error: { status: 500, data: "fail" } }),
    );

    await store.dispatch(
      campaignApi.endpoints.createCampaign.initiate({
        name: "Test Campaign",
        countryId: "SG",
        startDate: "2026-01-01",
        endDate: "2026-01-31",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any),
    );
    await flush();

    // 1 initial dashboard fetch + 1 failed mutation call — no invalidation
    // refetch since the mutation didn't succeed.
    expect(queryFnSpy).toHaveBeenCalledTimes(2);
    subscription.unsubscribe();
  });
});
