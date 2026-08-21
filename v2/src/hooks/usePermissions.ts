import { useCallback, useMemo } from "react";

import { useUser } from "./useUser";

/**
 * Permission hook backed by the Admin Console identity data.
 *
 * The IAM userinfo payload carries `company_permissions`: a map of
 * companyId -> authority strings in backend format (e.g. "planner:plans:read",
 * "planner:plans:update"). Permissions follow the ACTIVE company, so switching
 * tenants in the header immediately rescopes what the user can do — matching
 * the backend, which rescopes authorities from the X-Company-Id header.
 *
 * Backward compatibility: if the profile has no `company_permissions` map at
 * all (older stored profiles from before the Admin Console integration),
 * everything is allowed — the backend remains the enforcement authority.
 */
export function usePermissions() {
  const { profile } = useUser();

  const activeCompanyId =
    profile?.activeCompanyId || profile?.current_company?.id;

  const authorities = useMemo(() => {
    const map = profile?.company_permissions;
    if (!map) return null; // unknown -> permissive (legacy profiles)
    return new Set(activeCompanyId ? (map[activeCompanyId] ?? []) : []);
  }, [profile?.company_permissions, activeCompanyId]);

  const can = useCallback(
    (authority: string) => {
      if (!authorities) return true;
      return authorities.has(authority);
    },
    [authorities],
  );

  return {
    can,
    /** True when the profile carries real per-company permission data. */
    hasPermissionData: authorities !== null,
    canCreatePlans: can("planner:plans:create"),
    canEditPlans: can("planner:plans:update"),
    canDeletePlans: can("planner:plans:delete"),
    canGenerateProposals: can("planner:proposals:generate"),
  };
}
