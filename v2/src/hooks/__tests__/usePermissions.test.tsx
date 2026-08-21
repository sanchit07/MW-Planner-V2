import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { usePermissions } from "../usePermissions";
import { useUser } from "../useUser";

vi.mock("../useUser", () => ({
  useUser: vi.fn(),
}));

const mockUseUser = vi.mocked(useUser);

const setProfile = (profile: unknown) =>
  mockUseUser.mockReturnValue({
    profile,
  } as unknown as ReturnType<typeof useUser>);

const FULL = [
  "planner:plans:read",
  "planner:plans:create",
  "planner:plans:update",
  "planner:plans:delete",
  "planner:proposals:generate",
];

describe("usePermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("is permissive when the profile has no company_permissions map (legacy profiles)", () => {
    setProfile({ id: "u1", activeCompanyId: "co-1" });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(false);
    expect(result.current.can("planner:plans:update")).toBe(true);
    expect(result.current.canCreatePlans).toBe(true);
    expect(result.current.canEditPlans).toBe(true);
    expect(result.current.canDeletePlans).toBe(true);
    expect(result.current.canGenerateProposals).toBe(true);
  });

  it("is permissive when the profile itself is missing", () => {
    setProfile(null);
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(false);
    expect(result.current.can("anything:at:all")).toBe(true);
  });

  it("grants exactly the active company's authorities when the map is present", () => {
    setProfile({
      id: "u1",
      activeCompanyId: "co-full",
      company_permissions: {
        "co-full": FULL,
        "co-readonly": ["planner:plans:read"],
      },
    });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(true);
    expect(result.current.canCreatePlans).toBe(true);
    expect(result.current.canEditPlans).toBe(true);
    expect(result.current.canDeletePlans).toBe(true);
    expect(result.current.canGenerateProposals).toBe(true);
  });

  it("is strict for a read-only active company: writes denied, reads allowed", () => {
    setProfile({
      id: "u1",
      activeCompanyId: "co-readonly",
      company_permissions: {
        "co-full": FULL,
        "co-readonly": ["planner:plans:read"],
      },
    });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(true);
    expect(result.current.can("planner:plans:read")).toBe(true);
    expect(result.current.canCreatePlans).toBe(false);
    expect(result.current.canEditPlans).toBe(false);
    expect(result.current.canDeletePlans).toBe(false);
    expect(result.current.canGenerateProposals).toBe(false);
  });

  it("denies everything for an active company absent from the map", () => {
    setProfile({
      id: "u1",
      activeCompanyId: "co-unknown",
      company_permissions: { "co-full": FULL },
    });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(true);
    expect(result.current.can("planner:plans:read")).toBe(false);
    expect(result.current.canCreatePlans).toBe(false);
  });

  it("denies everything when the map exists but no active company can be resolved", () => {
    setProfile({ id: "u1", company_permissions: { "co-full": FULL } });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.hasPermissionData).toBe(true);
    expect(result.current.can("planner:plans:read")).toBe(false);
  });

  it("falls back to current_company.id when activeCompanyId is missing", () => {
    setProfile({
      id: "u1",
      current_company: { id: "co-readonly" },
      company_permissions: {
        "co-full": FULL,
        "co-readonly": ["planner:plans:read"],
      },
    });
    const { result } = renderHook(() => usePermissions());
    expect(result.current.can("planner:plans:read")).toBe(true);
    expect(result.current.canEditPlans).toBe(false);
  });
});
