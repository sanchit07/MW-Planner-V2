/**
 * Standalone mock IAM/OAuth2 authorization server for the V2 stack, backed
 * live by the Admin Console.
 *
 * This used to be mounted inside the legacy V1 Express server at `/v2iam`.
 * It has been extracted into its own service (`v2-iam-companion/`) so V2
 * (v2/, v2-backend/, v2-recommendation/) has zero runtime dependency on V1.
 * Behavior is unchanged from the original: the V2 Spring Boot backend
 * validates Bearer JWTs against `<iam.service-url>/.well-known/jwks.json`
 * and requires `iss` to equal `iam.service-url` exactly. This router keeps
 * issuing local RS256 tokens (the Admin Console has no OAuth yet), but all
 * identity DATA — users, memberships, companies, roles, permissions — comes
 * live from the Admin Console (account-sphere.replit.app) via
 * ./admin-console-client.
 *
 * Flow: /api/v1/oauth/authorize renders a user picker of real Admin Console
 * users; the chosen user id travels in the auth code; issued tokens carry
 * that user's primary company and per-company permission authorities
 * derived from their Admin Console role in each org. Tenant switching and
 * permission enforcement in the backend/frontend then work off real data.
 */
import { Router, type Request, type Response } from "express";
import crypto from "crypto";
import {
  getIdentitySnapshot,
  getUserById,
  getOrgById,
  type AcUser,
  type AcMembership,
  type AcOrg,
} from "./admin-console-client.js";

// Must exactly match `mw-planner.iam.service-url` in the v2-backend config
// profile that's active wherever this service runs (SecurityConfiguration's
// jwtDecoder validates `iss` equality and fetches JWKS from
// `${V2_IAM_BASE}/.well-known/jwks.json`). Defaults to the path-relative form
// v2-gateway exposes (proxying /v2iam/* to this service's real port 10001) —
// see mw-planner.iam.service-url in application-replit.yaml.
export const V2_IAM_BASE = process.env.V2_IAM_BASE ?? "http://127.0.0.1:5000/v2iam";

const PRODUCT_ID = "cc24af2e-9e69-44ea-9440-838790cd8c0a"; // planner product id from backend config

// ---- signing key (persisted: the Java backend caches the JWKS by kid, so a
// fresh key per process would invalidate every new token until the cache
// expires; reusing one key across restarts also keeps sessions alive) ----
import fs from "fs";
import path from "path";
const KEY_PATH = path.join(process.cwd(), ".data", "v2-iam-key.pem");
let privateKey: crypto.KeyObject;
if (fs.existsSync(KEY_PATH)) {
  privateKey = crypto.createPrivateKey(fs.readFileSync(KEY_PATH));
} else {
  const pair = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
  fs.mkdirSync(path.dirname(KEY_PATH), { recursive: true });
  fs.writeFileSync(KEY_PATH, pair.privateKey.export({ type: "pkcs8", format: "pem" }), { mode: 0o600 });
  privateKey = pair.privateKey;
}
const publicKey = crypto.createPublicKey(privateKey);
const KID = "v2-local-key";
export const publicJwk = { ...publicKey.export({ format: "jwk" }), kid: KID, use: "sig", alg: "RS256" };

function b64url(input: Buffer | string): string {
  return Buffer.from(input).toString("base64url");
}

export function signJwt(claims: Record<string, unknown>, expiresInSec: number): string {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT", kid: KID };
  const payload = { iss: V2_IAM_BASE, iat: now, nbf: now, exp: now + expiresInSec, ...claims };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const signature = crypto.sign("RSA-SHA256", Buffer.from(signingInput), privateKey);
  return `${signingInput}.${signature.toString("base64url")}`;
}

/** Verify one of our own JWTs and return its payload, or null. */
export function verifyJwt(token: string): Record<string, any> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const ok = crypto.verify(
    "RSA-SHA256",
    Buffer.from(`${parts[0]}.${parts[1]}`),
    publicKey,
    Buffer.from(parts[2], "base64url"),
  );
  if (!ok) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString());
    if (payload.exp && payload.exp * 1000 < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

function bearerUser(req: Request): Promise<AcUser | undefined> {
  const auth = String(req.headers.authorization ?? "");
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const payload = token ? verifyJwt(token) : null;
  if (!payload?.sub) return Promise.resolve(undefined);
  return getUserById(String(payload.sub));
}

// ---- Admin Console org -> IAM company shapes ----

const SUPPLIER_TYPES = new Set(["media_owner", "vendor"]);

function companyTypeFor(org: AcOrg) {
  const code = org.type.toUpperCase();
  return {
    id: `type-${org.type}`,
    name: org.type
      .split("_")
      .map((w) => w[0].toUpperCase() + w.slice(1))
      .join(" "),
    code,
    is_supplier_side: SUPPLIER_TYPES.has(org.type),
    is_demand_side: !SUPPLIER_TYPES.has(org.type),
    is_parent_company_type: false,
  };
}

const subscriptions = [
  { subscription_id: "55555555-5555-5555-5555-555555555555", product_id: PRODUCT_ID, product_name: "Planner", is_active: true },
];

function membershipDto(m: AcMembership, seat: number) {
  return {
    company_id: m.orgId,
    company_seat_id: seat,
    company_name: m.org.name,
    role_id: m.roleId,
    role_name: m.roleName ?? "Member",
    is_active: m.isActive,
    company_type: companyTypeFor(m.org),
    subscriptions,
  };
}

function primaryMembership(user: AcUser): AcMembership | undefined {
  return user.memberships.find((m) => m.isPrimary) ?? user.memberships[0];
}

function userInfoDto(user: AcUser) {
  const primary = primaryMembership(user);
  return {
    id: user.id,
    user_id: user.id,
    sub: user.id,
    email: user.email,
    username: user.email,
    first_name: user.firstName,
    last_name: user.lastName,
    email_verified: true,
    phone_verified: true,
    is_global_admin: false,
    has_system_role: false,
    system_permissions: [],
    // Backend UserInfoResponse declares this as List<String>; the per-company
    // map travels in the JWT claim instead. Surface the primary org's
    // authorities here (frontend also reads this for gating).
    permissions: primary?.authorities ?? [],
    // Client-side extra: per-company authority map for the FE permission hook.
    company_permissions: permissionsClaim(user),
    memberships: user.memberships.map((m, i) => membershipDto(m, i + 1)),
    current_company: primary
      ? {
          id: primary.orgId,
          seat_id: 1,
          name: primary.org.name,
          company_type: companyTypeFor(primary.org),
          role_id: primary.roleId,
          role_name: primary.roleName ?? "Member",
          child_companies: { items: [], total_count: 0, has_more: false },
        }
      : undefined,
    primary_company_id: primary?.orgId,
  };
}

/** Per-company permission authorities in backend format. */
function permissionsClaim(user: AcUser): Record<string, string[]> {
  return Object.fromEntries(user.memberships.map((m) => [m.orgId, m.authorities]));
}

// Lookup DTO in the backend expects company_type and is_active as strings.
function companyRecordDto(org: AcOrg) {
  return {
    id: org.id,
    name: org.name,
    domain: null,
    seat_id: Number(org.seatId) || 1,
    external_id: org.seatId ?? org.id,
    company_type: org.type.toUpperCase(),
    is_active: "true",
    notification_email: null,
    company_country: "Malaysia",
    currency_code: "MYR",
    country_code: "MY",
    timezone: "Asia/Kuala_Lumpur",
    logo_url: null,
  };
}

function issueTokens(user: AcUser, state?: string) {
  const primary = primaryMembership(user);
  const accessToken = signJwt(
    {
      sub: user.id,
      subscriptions: [PRODUCT_ID],
      is_global_admin: false,
      has_system_role: false,
      system_permissions: [],
      primary_company_id: primary?.orgId,
      permissions: permissionsClaim(user),
      email: user.email,
      username: user.email,
    },
    12 * 3600,
  );
  return {
    access_token: accessToken,
    refresh_token: signJwt({ sub: user.id, token_use: "refresh" }, 30 * 24 * 3600),
    expires_in: 12 * 3600,
    token_type: "Bearer",
    scope: "read,write",
    ...(state ? { state } : {}),
  };
}

// ---- one-time authorization codes: random, short-lived, single-use.
// Prevents minting tokens for arbitrary users from a guessable code.
const AUTH_CODE_TTL_MS = 60_000;
const authCodes = new Map<
  string,
  { userId: string; redirectUri: string; codeChallenge?: string; exp: number }
>();

export function issueAuthCode(
  userId: string,
  redirectUri: string,
  codeChallenge?: string,
): string {
  // Opportunistic cleanup of expired codes.
  const now = Date.now();
  for (const [k, v] of authCodes) if (v.exp < now) authCodes.delete(k);
  const code = crypto.randomBytes(24).toString("base64url");
  authCodes.set(code, { userId, redirectUri, codeChallenge, exp: now + AUTH_CODE_TTL_MS });
  return code;
}

/**
 * Consumes a one-time auth code and verifies PKCE: the caller's code_verifier must hash (S256) to
 * the code_challenge captured at /authorize, exactly like a real IAM would enforce — without
 * this, anyone who intercepts an auth code within its short TTL could redeem it themselves.
 */
export function consumeAuthCode(code: string, codeVerifier?: string): string | undefined {
  const entry = authCodes.get(code);
  if (!entry) return undefined;
  authCodes.delete(code); // single-use
  if (entry.exp < Date.now()) return undefined;
  if (entry.codeChallenge) {
    if (!codeVerifier) return undefined;
    const computed = crypto.createHash("sha256").update(codeVerifier).digest("base64url");
    if (computed !== entry.codeChallenge) return undefined;
  }
  return entry.userId;
}

const esc = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

function userPickerHtml(
  users: AcUser[],
  redirectUri: string,
  state: string,
  codeChallenge?: string,
): string {
  const rows = users
    .map((u) => {
      const primary = primaryMembership(u);
      const others = u.memberships.filter((m) => m !== primary);
      const badge = (m: AcMembership) => `${esc(m.org.name)} · ${esc(m.roleName ?? "Member")}`;
      // code_challenge must be forwarded from the initial /authorize request so it's still
      // present on the follow-up request the picker link triggers (with user_id set).
      const qs = new URLSearchParams({
        redirect_uri: redirectUri,
        state,
        user_id: u.id,
        ...(codeChallenge ? { code_challenge: codeChallenge } : {}),
      });
      return `<a class="row" href="?${qs.toString()}">
        <div class="name">${esc(u.firstName)} ${esc(u.lastName)} <span class="email">${esc(u.email)}</span></div>
        <div class="orgs">${primary ? `<b>${badge(primary)}</b>` : "<i>no membership</i>"}${others.length ? " — switches into: " + others.map(badge).join(", ") : ""}</div>
      </a>`;
    })
    .join("\n");
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Sign in — Admin Console users</title>
  <style>
    body{font-family:system-ui,sans-serif;background:#f6f7f9;margin:0;padding:40px}
    .card{max-width:720px;margin:0 auto;background:#fff;border-radius:12px;box-shadow:0 2px 10px rgba(0,0,0,.08);padding:28px}
    h1{font-size:20px;margin:0 0 4px} p{color:#667;margin:0 0 20px;font-size:14px}
    .row{display:block;padding:12px 14px;border:1px solid #e3e6ea;border-radius:8px;margin-bottom:10px;text-decoration:none;color:#222}
    .row:hover{border-color:#2563eb;background:#f0f6ff}
    .name{font-weight:600} .email{color:#889;font-weight:400;font-size:13px;margin-left:6px}
    .orgs{font-size:13px;color:#556;margin-top:3px}
  </style></head><body><div class="card">
  <h1>Sign in to Planner</h1>
  <p>Pick an Admin Console user (live from account-sphere). Memberships and permissions come from the Admin Console.</p>
  ${rows}
  </div></body></html>`;
}

export function createV2MockIam(): Router {
  const router = Router();
  const ok = (data: unknown) => ({ success: true, message: "OK", data });

  router.get("/.well-known/jwks.json", (_req, res) => res.json({ keys: [publicJwk] }));

  // OAuth authorize: shows a picker of real Admin Console users; picking one
  // bounces back to the app with a code that encodes the chosen user id.
  router.get("/api/v1/oauth/authorize", async (req: Request, res: Response) => {
    const redirectUri = String(req.query.redirect_uri ?? "");
    const state = String(req.query.state ?? "");
    if (!redirectUri) return res.status(400).json({ error: "missing redirect_uri" });
    // Guard against open redirect: only allow same-host or loopback targets.
    try {
      // Fixed allowlist only — do NOT trust the request's Host header, which
      // an attacker can forge to smuggle the code redirect to another origin.
      // 127.0.0.1:5000/localhost:5000 is v2-gateway, the single public entry
      // point (dev and Reserved-VM deployment alike); 4700 is the Vite dev
      // server directly, for local testing outside the gateway.
      const target = new URL(redirectUri, "http://127.0.0.1:5000");
      const allowedHosts = new Set(
        [
          "127.0.0.1:5000",
          "localhost:5000",
          "127.0.0.1:4700",
          "localhost:4700",
          process.env.REPLIT_DEV_DOMAIN,
          // Production deployment domains (comma-separated in REPLIT_DOMAINS).
          ...(process.env.REPLIT_DOMAINS ?? "").split(",").map((d) => d.trim()),
        ].filter(Boolean),
      );
      if (!allowedHosts.has(target.host)) {
        return res.status(400).json({ error: "redirect_uri host not allowed" });
      }
    } catch {
      return res.status(400).json({ error: "invalid redirect_uri" });
    }

    const userId = String(req.query.user_id ?? "");
    if (userId) {
      const codeChallenge = req.query.code_challenge ? String(req.query.code_challenge) : undefined;
      const code = issueAuthCode(userId, redirectUri, codeChallenge);
      const sep = redirectUri.includes("?") ? "&" : "?";
      return res.redirect(
        `${redirectUri}${sep}code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`,
      );
    }
    try {
      const snap = await getIdentitySnapshot();
      const codeChallenge = req.query.code_challenge ? String(req.query.code_challenge) : undefined;
      res.type("html").send(userPickerHtml(snap.users, redirectUri, state, codeChallenge));
    } catch (e) {
      res
        .status(502)
        .type("html")
        .send(
          `<h2>Admin Console unreachable</h2><p>${esc((e as Error).message)}</p><p>Check the ADMIN_CONSOLE_TOKEN secret and that account-sphere.replit.app is up, then reload.</p>`,
        );
    }
  });

  router.post("/api/v1/oauth/token", async (req: Request, res: Response) => {
    try {
      const body = req.body ?? {};
      let userId: string | undefined;
      if (typeof body.refresh_token === "string" && body.refresh_token) {
        const payload = verifyJwt(body.refresh_token);
        if (payload?.token_use === "refresh" && payload.sub) userId = String(payload.sub);
        if (!userId) return res.status(401).json({ error: "invalid refresh_token" });
      } else {
        userId = consumeAuthCode(
          String(body.code ?? ""),
          typeof body.code_verifier === "string" ? body.code_verifier : undefined,
        );
      }
      if (!userId)
        return res
          .status(400)
          .json({ error: "invalid/expired/already-used code, or PKCE code_verifier mismatch" });
      const user = await getUserById(userId);
      if (!user) return res.status(401).json({ error: "unknown user (Admin Console)" });
      res.json(issueTokens(user, typeof body.state === "string" ? body.state : undefined));
    } catch (e) {
      res.status(502).json({ error: `Admin Console unreachable: ${(e as Error).message}` });
    }
  });

  router.get("/api/v1/oauth/clients/:clientId/info", (_req, res) =>
    res.json(ok({ client_id: "local", client_name: "MW Planner (Replit V2)", logo_url: null })),
  );

  router.post("/api/v1/auth/logout", (_req, res) => res.json(ok({ logged_out: true })));

  router.get("/userinfo", async (req, res) => {
    try {
      const user = await bearerUser(req);
      if (!user) return res.status(401).json({ success: false, message: "invalid token" });
      res.json(ok(userInfoDto(user)));
    } catch (e) {
      res.status(502).json({ success: false, message: (e as Error).message });
    }
  });

  router.get("/api/v1/users/me/companies", async (req, res) => {
    try {
      const user = await bearerUser(req);
      if (!user) return res.status(401).json({ success: false, message: "invalid token" });
      res.json(ok(user.memberships.map((m, i) => membershipDto(m, i + 1))));
    } catch (e) {
      res.status(502).json({ success: false, message: (e as Error).message });
    }
  });

  router.get("/api/v1/users/:userId", async (req, res) => {
    try {
      const user = await getUserById(req.params.userId);
      if (!user) return res.status(404).json({ success: false, message: "user not found" });
      res.json(ok(userInfoDto(user)));
    } catch (e) {
      res.status(502).json({ success: false, message: (e as Error).message });
    }
  });

  router.get("/api/v1/companies/lookup", async (req: Request, res: Response) => {
    try {
      const snap = await getIdentitySnapshot();
      const all = [...snap.orgsById.values()].map(companyRecordDto);
      const idFilter = String(req.query.company_id ?? req.query.id ?? req.query.ids ?? "");
      const records = idFilter ? all.filter((c) => idFilter.split(",").includes(c.id)) : all;
      res.json({ ...ok(records), meta: { total: records.length, limit: 50, offset: 0 } });
    } catch (e) {
      res.status(502).json({ success: false, message: (e as Error).message });
    }
  });

  router.get("/api/v1/companies/:companyId/children", (_req, res) =>
    res.json(ok({ items: [], total_count: 0, has_more: false })),
  );

  // companyById uses a different wrapper than lookup: data.company with
  // company_type as an object and is_active as a boolean.
  router.get("/api/v1/companies/:companyId", async (req: Request, res: Response) => {
    try {
      const org = await getOrgById(req.params.companyId);
      if (!org) return res.status(404).json({ success: false, message: "company not found" });
      const record = companyRecordDto(org);
      res.json(
        ok({
          company: {
            ...record,
            is_active: true,
            company_type: companyTypeFor(org),
            status: "ACTIVE",
            email: record.notification_email,
          },
        }),
      );
    } catch (e) {
      res.status(502).json({ success: false, message: (e as Error).message });
    }
  });

  // Market access: the wizard's Target Country list comes from here. All 4
  // countries with imported inventory (transit + network, classic + digital).
  router.get("/api/v1/companies/:companyId/market-access", (req: Request, res: Response) => {
    const markets = [
      { country_id: "MY", country_name: "Malaysia", country_code: "MY" },
      { country_id: "IN", country_name: "India", country_code: "IN" },
      { country_id: "JP", country_name: "Japan", country_code: "JP" },
      { country_id: "SG", country_name: "Singapore", country_code: "SG" },
    ].map((m) => ({
      id: `market-${m.country_code.toLowerCase()}`,
      company_id: req.params.companyId,
      is_active: true,
      ...m,
    }));
    // Frontend reads `markets` at the top level (CompanyMarketAccessResponse);
    // keep the ok() envelope too for any consumer that unwraps `data`.
    const payload = { company_id: req.params.companyId, markets, total_count: markets.length };
    res.json({ ...ok(payload), ...payload });
  });

  // Anything else: succeed with empty data so optional lookups degrade quietly.
  router.use((req, res) => {
    console.log(`[v2-iam-companion] unhandled ${req.method} ${req.originalUrl}`);
    res.json(ok(null));
  });

  return router;
}
