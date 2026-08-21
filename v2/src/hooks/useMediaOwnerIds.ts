import { useMemo } from "react";
import { useSelector } from "react-redux";

import type { RootState } from "../store";

/**
 * Media-owner ids for the logged-in user: the current company plus its child
 * companies, but ONLY when the user's company is a MEDIA_OWNER. Returns an empty
 * array otherwise.
 *
 * Used to scope inventory endpoints (recommendation, forecast, selected-inventory)
 * to a media owner's own inventory. Ids come from the profile already in Redux —
 * no extra API call.
 *
 * `RootState` is imported type-only so this hook does not pull the store module
 * at runtime (keeps it usable in units that partially mock the API slice).
 */
export function useMediaOwnerIds(): string[] {
  const currentCompany = useSelector(
    (state: RootState) => state.profile?.profile?.current_company,
  );

  return useMemo(() => {
    if (
      currentCompany?.company_type?.code !== "MEDIA_OWNER" ||
      !currentCompany?.id
    ) {
      return [];
    }
    const childIds =
      currentCompany.childCompanies?.items?.map((c) => c.id) ?? [];
    return Array.from(new Set([currentCompany.id, ...childIds]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentCompany?.id, currentCompany?.childCompanies?.items]);
}
