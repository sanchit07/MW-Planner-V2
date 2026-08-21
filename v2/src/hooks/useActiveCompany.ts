import { UserCompanyType } from "@services/user/userSlice";
import { useAppSelector } from "@store";

export interface ActiveCompanyInfo {
  /** ID of the currently active company. */
  companyId: string;
  /** Display name of the currently active company. */
  companyName: string;
  companyType: UserCompanyType | undefined;
  isAgency: boolean;
  isMediaOwner: boolean;
}

/**
 * Resolves the active company's type by matching `activeCompanyId` against
 * (in order): `current_company`, `memberships`, then `current_company.childCompanies`.
 *
 * Use this instead of reading `profile.current_company.company_type` directly —
 * the latter never updates when the user switches tenants.
 */
export function useActiveCompany(): ActiveCompanyInfo {
  const user = useAppSelector((state) => state.profile.profile);
  const activeId = user?.activeCompanyId || user?.current_company?.id || "";

  // 1. Login / current company
  if (activeId && activeId === user?.current_company?.id) {
    const ct = user.current_company.company_type;
    return build(user.current_company.id, user.current_company.name, ct);
  }

  // 2. Other memberships (company_type now included in UserMemberships)
  const membership = user?.memberships?.find((m) => m.company_id === activeId);
  if (membership) {
    return build(
      membership.company_id,
      membership.company_name,
      membership.company_type,
    );
  }

  // 3. Child companies of the login company
  const child = user?.current_company?.childCompanies?.items?.find(
    (c) => c.id === activeId,
  );
  if (child) {
    return build(
      child.id,
      child.name,
      child.companyType as unknown as UserCompanyType,
    );
  }

  return build(activeId, "", undefined);
}

function build(
  companyId: string,
  companyName: string,
  companyType: UserCompanyType | undefined,
): ActiveCompanyInfo {
  return {
    companyId,
    companyName,
    companyType,
    isAgency: companyType?.code === "AGENCY",
    isMediaOwner: companyType?.is_supplier_side === true,
  };
}
