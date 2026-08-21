export type UserRole = "admin" | "advertiser" | "agency" | "internal";

export const ROUTE_PERMISSIONS: Record<string, UserRole[]> = {
  "/dashboard": ["admin", "advertiser", "agency", "internal"],
  "/campaigns": ["admin", "advertiser", "agency"],
  "/campaigns/new": ["admin", "advertiser", "agency"],
  "/creatives": ["admin", "advertiser", "agency"],
  "/statements": ["admin", "advertiser", "agency"],
  "/measures": ["admin", "advertiser", "agency", "internal"],
  "/inventories": ["admin", "internal"],
  "/proposal-theme": ["admin", "internal"],
  "/settings": ["admin", "internal"],
  "/signals": ["admin", "internal"],
  "/pois": ["admin", "internal"],
  "/tags": ["admin", "internal"],
  "/profile": ["admin", "advertiser", "agency", "internal"],
  "/campaigns/create": ["admin", "advertiser", "agency"],
};

export const hasRouteAccess = (
  pathname: string,
  userRole: UserRole,
): boolean => {
  const allowedRoles = ROUTE_PERMISSIONS[pathname];
  return allowedRoles ? allowedRoles.includes(userRole) : false;
};
