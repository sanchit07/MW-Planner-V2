/**
 * Local, offline stand-in identity source — Planner's own login page and
 * user "database" for when the Admin Console (account-sphere.replit.app)
 * isn't reachable or configured (no ADMIN_CONSOLE_TOKEN).
 *
 * Temporary measure: exports the exact same three functions as
 * ./admin-console-client (getIdentitySnapshot/getUserById/getOrgById) so
 * v2-mock-iam.ts needs no other changes — swap the import there to switch
 * back to the real Admin Console later.
 */
import type { AcOrg, AcMembership, AcUser } from "./admin-console-client.js";

const ALL_PLANNER_AUTHORITIES = [
  "planner:config:read",
  "planner:config:update",
  "planner:creatives:approve",
  "planner:creatives:create",
  "planner:creatives:delete",
  "planner:creatives:read",
  "planner:creatives:update",
  "planner:plans:create",
  "planner:plans:delete",
  "planner:plans:read",
  "planner:plans:update",
  "planner:reservations:read",
  "planner:reservations:update",
  "planner:statements:create",
  "planner:statements:read",
  "planner:statements:update",
];

const orgs: Record<string, AcOrg> = {
  "org-agency-1": { id: "org-agency-1", name: "Acme Media Agency", type: "agency" },
  "org-media-owner-1": { id: "org-media-owner-1", name: "Sunset Outdoor Media", type: "media_owner" },
  "org-advertiser-1": { id: "org-advertiser-1", name: "Northwind Foods", type: "advertiser" },
};

function membership(orgId: string, roleName: string, isPrimary: boolean): AcMembership {
  const org = orgs[orgId];
  return {
    id: `mem-${orgId}`,
    orgId,
    roleId: `role-${roleName.toLowerCase()}`,
    isPrimary,
    isActive: true,
    org,
    roleName,
    permissionNames: [],
    authorities: ALL_PLANNER_AUTHORITIES,
  };
}

const users: AcUser[] = [
  {
    id: "local-agency-user",
    email: "agency.user@example.com",
    firstName: "Ava",
    lastName: "Agency",
    designation: "Media Planner",
    isActive: true,
    memberships: [membership("org-agency-1", "Planner Admin", true)],
  },
  {
    id: "local-media-owner-user",
    email: "media.owner@example.com",
    firstName: "Milo",
    lastName: "Owner",
    designation: "Inventory Manager",
    isActive: true,
    memberships: [membership("org-media-owner-1", "Planner Admin", true)],
  },
  {
    id: "local-advertiser-user",
    email: "advertiser.user@example.com",
    firstName: "Nora",
    lastName: "North",
    designation: "Brand Manager",
    isActive: true,
    memberships: [membership("org-advertiser-1", "Planner Admin", true)],
  },
];

const orgsById = new Map<string, AcOrg>(Object.values(orgs).map((o) => [o.id, o]));

export async function getIdentitySnapshot(): Promise<{
  users: AcUser[];
  orgsById: Map<string, AcOrg>;
  fetchedAt: number;
}> {
  return { users, orgsById, fetchedAt: Date.now() };
}

export async function getUserById(userId: string): Promise<AcUser | undefined> {
  return users.find((u) => u.id === userId);
}

export async function getOrgById(orgId: string): Promise<AcOrg | undefined> {
  return orgsById.get(orgId);
}
