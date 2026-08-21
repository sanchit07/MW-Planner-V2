/**
 * Client for the Admin Console (account-sphere.replit.app) — the system of
 * record for identity: companies, users, memberships, roles, permissions.
 *
 * Prototype access mechanics (no OAuth on the Admin Console yet):
 *  - Base URL https://account-sphere.replit.app/api
 *  - The deployment is private, so every request carries
 *    `Authorization: Bearer $ADMIN_CONSOLE_TOKEN` (Replit external access token).
 *  - Identity headers (x-user-id / x-acting-org-id) exist as a convention but
 *    the read endpoints used here are not scoped by them today.
 *
 * This module fetches the full identity graph (users + memberships + orgs,
 * roles, role→permission joins, permission catalog), caches it briefly, and
 * computes effective per-org permission authorities in the format the V2
 * Spring backend expects (`module:resource:action`, e.g. `planner:plans:read`).
 */

const BASE = process.env.ADMIN_CONSOLE_BASE_URL ?? "https://account-sphere.replit.app/api";
const TOKEN = () => process.env.ADMIN_CONSOLE_TOKEN ?? "";
const CACHE_TTL_MS = 60_000;
const FETCH_TIMEOUT_MS = 15_000;

export interface AcOrg {
  id: string;
  name: string;
  type: string; // agency | media_owner | mw_internal | partner | reseller | vendor | advertiser ...
  seatId?: string | null;
  isDemo?: boolean;
}

export interface AcMembership {
  id: string;
  orgId: string;
  roleId: string;
  isPrimary: boolean;
  isActive: boolean;
  org: AcOrg;
  roleName?: string;
  /** Backend-authority-format permissions for this org (e.g. planner:plans:read). */
  authorities: string[];
  /** Raw Admin Console permission names (e.g. planner:plans.view). */
  permissionNames: string[];
}

export interface AcUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  designation?: string | null;
  isActive: boolean;
  memberships: AcMembership[];
}

interface Snapshot {
  users: AcUser[];
  orgsById: Map<string, AcOrg>;
  fetchedAt: number;
}

async function acFetch(path: string): Promise<unknown> {
  if (!TOKEN()) throw new Error("ADMIN_CONSOLE_TOKEN is not configured");
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE}${path}`, {
      headers: { Authorization: `Bearer ${TOKEN()}`, Accept: "application/json" },
      signal: controller.signal,
      redirect: "manual", // a 307 to replshield means the token is bad/missing
    });
    if (res.status >= 300 && res.status < 400) {
      throw new Error(`Admin Console rejected the request (redirect ${res.status}) — check ADMIN_CONSOLE_TOKEN`);
    }
    if (!res.ok) throw new Error(`Admin Console ${path} -> HTTP ${res.status}`);
    const ct = res.headers.get("content-type") ?? "";
    if (!ct.includes("application/json")) {
      throw new Error(`Admin Console ${path} returned non-JSON (${ct || "unknown"}) — endpoint missing?`);
    }
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Map an Admin Console permission name (module:resource.action) to the
 * authority strings the Spring backend checks via @PreAuthorize.
 * The backend uses read/update verbs where the Admin Console catalog says
 * view/edit, so those verbs are aliased (both forms are emitted).
 */
export function mapPermissionToAuthorities(name: string): string[] {
  const m = name.match(/^([a-z_]+):([a-z_]+)\.([a-z_]+)$/i);
  if (!m) return [name];
  const [, module, resource, action] = m;
  const actions = new Set([action]);
  if (action === "view") actions.add("read");
  if (action === "edit") actions.add("update");
  if (action === "manage") ["read", "create", "update", "delete"].forEach((a) => actions.add(a));
  return [...actions].map((a) => `${module}:${resource}:${a}`);
}

let snapshot: Snapshot | null = null;
let inflight: Promise<Snapshot> | null = null;

async function buildSnapshot(): Promise<Snapshot> {
  const [usersRaw, rolesRaw, permsRaw] = await Promise.all([
    acFetch("/users") as Promise<any[]>,
    acFetch("/roles") as Promise<any[]>,
    acFetch("/permissions") as Promise<any[]>,
  ]);

  const permById = new Map<string, string>(permsRaw.map((p) => [p.id, p.name]));
  const roleNameById = new Map<string, string>(rolesRaw.map((r) => [r.id, r.name]));

  // Distinct roleIds actually used by memberships — fetch their permission joins.
  const roleIds = new Set<string>();
  for (const u of usersRaw) for (const m of u.memberships ?? []) if (m.roleId) roleIds.add(m.roleId);
  const rolePermNames = new Map<string, string[]>();
  await Promise.all(
    [...roleIds].map(async (rid) => {
      try {
        const joins = (await acFetch(`/roles/${rid}/permissions`)) as any[];
        rolePermNames.set(
          rid,
          joins.map((j) => permById.get(j.permissionId)).filter((n): n is string => !!n),
        );
      } catch (e) {
        console.warn(`[admin-console] failed to load permissions for role ${rid}:`, (e as Error).message);
        rolePermNames.set(rid, []);
      }
    }),
  );

  const orgsById = new Map<string, AcOrg>();
  const users: AcUser[] = usersRaw
    .filter((u) => u.isActive !== false)
    .map((u) => ({
      id: u.id,
      email: u.email,
      firstName: u.firstName ?? "",
      lastName: u.lastName ?? "",
      designation: u.designation,
      isActive: u.isActive !== false,
      memberships: (u.memberships ?? [])
        .filter((m: any) => m.isActive !== false && m.org)
        .map((m: any) => {
          const org: AcOrg = {
            id: m.org.id,
            name: m.org.name,
            type: m.org.type,
            seatId: m.org.seatId,
            isDemo: m.org.isDemo,
          };
          orgsById.set(org.id, org);
          const permissionNames = rolePermNames.get(m.roleId) ?? [];
          return {
            id: m.id,
            orgId: m.orgId,
            roleId: m.roleId,
            isPrimary: !!m.isPrimary,
            isActive: m.isActive !== false,
            org,
            roleName: roleNameById.get(m.roleId) ?? "Member",
            permissionNames,
            authorities: [...new Set(permissionNames.flatMap(mapPermissionToAuthorities))],
          } satisfies AcMembership;
        }),
    }));

  return { users, orgsById, fetchedAt: Date.now() };
}

export async function getIdentitySnapshot(force = false): Promise<Snapshot> {
  if (!force && snapshot && Date.now() - snapshot.fetchedAt < CACHE_TTL_MS) return snapshot;
  if (!inflight) {
    inflight = buildSnapshot()
      .then((s) => (snapshot = s))
      .finally(() => (inflight = null));
  }
  // If a stale snapshot exists, serve it while refreshing in the background.
  if (snapshot) {
    inflight.catch((e) => console.warn("[admin-console] background refresh failed:", (e as Error).message));
    return snapshot;
  }
  return inflight;
}

export async function getUserById(userId: string): Promise<AcUser | undefined> {
  const s = await getIdentitySnapshot();
  return s.users.find((u) => u.id === userId);
}

export async function getOrgById(orgId: string): Promise<AcOrg | undefined> {
  const s = await getIdentitySnapshot();
  return s.orgsById.get(orgId);
}
