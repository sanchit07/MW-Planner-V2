import type { AppDispatch } from "@store";

import { accountApi, accountUserApi } from "@services/account/accountApi";
import { agencyApi } from "@services/agency/agencySlice";
import { brandApi } from "@services/brand/brandSlice";
import { campaignDetailsApi } from "@services/campaign/campaignDetailsSlice";
import { campaignApi } from "@services/campaign/campaignSlice";
import { dashboardApi } from "@services/dashboard/dashboardSlice";
import {
  inventoryApi,
  inventoryManagementApi,
  reachFrequencyApi,
} from "@services/inventory/inventorySlice";

/**
 * RTK Query APIs whose cached responses depend on the acting company
 * (`X-Company-Id`). Cache keys for many of these endpoints (campaign detail,
 * media plan, execution plan/status, selected inventory, prices, …) are keyed
 * by campaign/entity ID only, so after a company switch a stale response for
 * the PREVIOUS company would otherwise be served from cache without ever
 * hitting the backend's acting-company guards.
 */
export const COMPANY_SCOPED_APIS = [
  campaignApi,
  campaignDetailsApi,
  inventoryApi,
  inventoryManagementApi,
  reachFrequencyApi,
  dashboardApi,
  brandApi,
  agencyApi,
  accountApi,
  accountUserApi,
] as const;

/**
 * Atomically drop every company-scoped RTK Query cache. Must be dispatched
 * whenever the active company changes; subscribed components refetch under
 * the new `X-Company-Id`.
 */
export function resetCompanyScopedApiState(dispatch: AppDispatch): void {
  for (const api of COMPANY_SCOPED_APIS) {
    dispatch(api.util.resetApiState());
  }
}
