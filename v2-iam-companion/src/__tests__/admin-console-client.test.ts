import { test, describe, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { mapPermissionToAuthorities, getUserById } from "../admin-console-client";

describe("mapPermissionToAuthorities", () => {
  test("passes through an already-correct verb unchanged", () => {
    assert.deepEqual(mapPermissionToAuthorities("planner:plans.read"), ["planner:plans:read"]);
  });

  test("aliases view -> both view and read", () => {
    const authorities = mapPermissionToAuthorities("planner:plans.view");
    assert.ok(authorities.includes("planner:plans:view"));
    assert.ok(authorities.includes("planner:plans:read"));
    assert.equal(authorities.length, 2);
  });

  test("aliases edit -> both edit and update", () => {
    const authorities = mapPermissionToAuthorities("planner:config.edit");
    assert.ok(authorities.includes("planner:config:edit"));
    assert.ok(authorities.includes("planner:config:update"));
    assert.equal(authorities.length, 2);
  });

  test("aliases manage -> full CRUD plus itself", () => {
    const authorities = new Set(mapPermissionToAuthorities("planner:creatives.manage"));
    for (const verb of ["manage", "read", "create", "update", "delete"]) {
      assert.ok(authorities.has(`planner:creatives:${verb}`), `expected ${verb} authority`);
    }
    assert.equal(authorities.size, 5);
  });

  test("returns the raw name unchanged when it doesn't match module:resource.action", () => {
    assert.deepEqual(mapPermissionToAuthorities("not-a-permission-name"), ["not-a-permission-name"]);
  });
});

describe("getUserById error handling", () => {
  const originalFetch = globalThis.fetch;
  const originalToken = process.env.ADMIN_CONSOLE_TOKEN;

  beforeEach(() => {
    process.env.ADMIN_CONSOLE_TOKEN = "test-token";
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    process.env.ADMIN_CONSOLE_TOKEN = originalToken;
  });

  test("surfaces a descriptive error when the Admin Console redirects (bad/missing token)", async () => {
    globalThis.fetch = (async () =>
      new Response(null, { status: 307 })) as unknown as typeof fetch;
    await assert.rejects(() => getUserById("any-id"), /rejected the request \(redirect 307\)/);
  });

  test("surfaces a descriptive error on a non-JSON response", async () => {
    globalThis.fetch = (async () =>
      new Response("<html>not json</html>", {
        status: 200,
        headers: { "content-type": "text/html" },
      })) as unknown as typeof fetch;
    await assert.rejects(() => getUserById("any-id"), /returned non-JSON/);
  });

  test("surfaces a descriptive error on a non-2xx JSON error response", async () => {
    globalThis.fetch = (async () =>
      new Response("{}", { status: 500, headers: { "content-type": "application/json" } })) as unknown as typeof fetch;
    await assert.rejects(() => getUserById("any-id"), /HTTP 500/);
  });

  test("throws when ADMIN_CONSOLE_TOKEN is not configured", async () => {
    delete process.env.ADMIN_CONSOLE_TOKEN;
    globalThis.fetch = (async () => new Response("{}")) as unknown as typeof fetch;
    await assert.rejects(() => getUserById("any-id"), /ADMIN_CONSOLE_TOKEN is not configured/);
  });
});
