import { describe, expect, it } from "vitest";

import { hasRouteAccess } from "../routePermissions";

describe("hasRouteAccess", () => {
  it("should return true for allowed role", () => {
    expect(hasRouteAccess("/dashboard", "admin")).toBe(true);
    expect(hasRouteAccess("/campaigns", "advertiser")).toBe(true);
  });

  it("should return false for disallowed role", () => {
    expect(hasRouteAccess("/inventories", "advertiser")).toBe(false);
  });

  it("should return false for unknown route", () => {
    expect(hasRouteAccess("/unknown", "admin")).toBe(false);
  });
});
