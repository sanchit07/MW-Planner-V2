import { describe, it, expect } from "vitest";

import {
  getCampaignActionPermissionsByStatus,
  getCampaignConfirmModalConfig,
  getConfirmActionErrorVerb,
  shouldHideNavItem,
} from "../campaignActions.utils";

describe("campaignActions.utils", () => {
  describe("getCampaignActionPermissionsByStatus", () => {
    it("returns draft permissions when status is draft", () => {
      const perms = getCampaignActionPermissionsByStatus("draft");
      expect(perms.view).toBe(false);
      expect(perms.edit).toBe(true);
      expect(perms.delete).toBe(true);
      expect(perms.viewMediaPlan).toBe(false);
    });

    it("returns draft permissions when status is planned", () => {
      const perms = getCampaignActionPermissionsByStatus("planned");
      expect(perms.campaignApproval).toBe(true);
    });

    it("returns non-draft view/edit when status is reviewing", () => {
      const perms = getCampaignActionPermissionsByStatus("reviewing");
      expect(perms.view).toBe(true);
      expect(perms.edit).toBe(false);
      expect(perms.campaignApproval).toBe(true);
    });

    it("returns rejected permissions", () => {
      const perms = getCampaignActionPermissionsByStatus("rejected");
      expect(perms.edit).toBe(true);
      expect(perms.priceManagement).toBe(true);
    });

    it("disables reserveInventories for all statuses (PL3-I12: undeveloped feature)", () => {
      // Reserve Inventories has no implementation — no handler is ever wired,
      // and there is no route/drawer/API. Clicking it does nothing. Per the
      // bug report, the feature must stay disabled until it is built.
      for (const status of [
        "draft",
        "planned",
        "reviewing",
        "rejected",
        "negotiating",
        "completed",
      ]) {
        expect(
          getCampaignActionPermissionsByStatus(status).reserveInventories,
        ).toBe(false);
      }
    });

    it("returns completed permissions", () => {
      const perms = getCampaignActionPermissionsByStatus("completed");
      expect(perms.archive).toBe(false);
      expect(perms.view).toBe(true);
    });

    it("is case-insensitive", () => {
      const perms = getCampaignActionPermissionsByStatus("DRAFT");
      expect(perms.view).toBe(false);
      expect(perms.delete).toBe(true);
    });
  });

  describe("getCampaignConfirmModalConfig", () => {
    it("returns null for null action", () => {
      expect(getCampaignConfirmModalConfig(null)).toBeNull();
    });

    it("returns duplicate config for DUPLICATE", () => {
      const config = getCampaignConfirmModalConfig("DUPLICATE");
      expect(config).not.toBeNull();
      expect(config?.title).toBe("Duplicate Campaign");
      expect(config?.primaryButtonVariant).toBe("default");
    });

    it("returns archive config for ARCHIVE", () => {
      const config = getCampaignConfirmModalConfig("ARCHIVE");
      expect(config?.title).toBe("Archive Campaign");
      expect(config?.primaryButtonVariant).toBe("default");
    });

    it("returns delete config with danger variant for DELETE", () => {
      const config = getCampaignConfirmModalConfig("DELETE");
      expect(config?.title).toBe("Delete Campaign");
      expect(config?.primaryButtonVariant).toBe("danger");
    });
  });

  describe("shouldHideNavItem", () => {
    it("returns false when hideList is empty", () => {
      expect(shouldHideNavItem("view", [])).toBe(false);
    });

    it("returns true when keyword matches hideList item", () => {
      expect(shouldHideNavItem("view", ["view"])).toBe(true);
    });

    it("is case-insensitive", () => {
      expect(shouldHideNavItem("View", ["view"])).toBe(true);
      expect(shouldHideNavItem("view", ["View"])).toBe(true);
    });

    it("returns false when keyword is not in hideList", () => {
      expect(shouldHideNavItem("edit", ["view", "delete"])).toBe(false);
    });
  });

  describe("getConfirmActionErrorVerb", () => {
    it("returns delete for DELETE", () => {
      expect(getConfirmActionErrorVerb("DELETE")).toBe("delete");
    });

    it("returns duplicate for DUPLICATE", () => {
      expect(getConfirmActionErrorVerb("DUPLICATE")).toBe("duplicate");
    });

    it("returns archive for ARCHIVE", () => {
      expect(getConfirmActionErrorVerb("ARCHIVE")).toBe("archive");
    });
  });
});
