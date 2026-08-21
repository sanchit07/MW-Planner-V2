export interface CampaignActionPermissionsByStatus {
  view: boolean;
  edit: boolean;
  viewMediaPlan: boolean;
  duplicate: boolean;
  delete: boolean;
  assignCreative: boolean;
  reserveInventories: boolean;
  campaignApproval: boolean;
  priceManagement: boolean;
  archive: boolean;
  share: boolean;
  download: boolean;
}

export function getCampaignActionPermissionsByStatus(
  status: string,
): CampaignActionPermissionsByStatus {
  const s = status.toLowerCase();
  const isDraft = s === "draft" || s === "planned";
  return {
    view: s !== "draft",
    viewMediaPlan: s !== "draft",
    edit: isDraft || s === "rejected" || s === "negotiating",
    assignCreative: s !== "draft" && s !== "reviewing" && s !== "rejected",
    campaignApproval: s === "planned" || s === "reviewing",
    // PL3-I12: Reserve Inventories is not implemented (no handler/route/API is
    // wired anywhere), so clicking it does nothing. Disabled until the feature
    // is built. Re-enable by restoring `s === "planned" || s === "rejected"`.
    reserveInventories: false,
    priceManagement: s === "planned" || s === "rejected" || s === "negotiating",
    archive: s !== "completed",
    duplicate: true,
    share: s !== "draft",
    download: s !== "draft",
    delete: isDraft,
  };
}

export type ConfirmActionType = "DUPLICATE" | "ARCHIVE" | "DELETE";

export interface CampaignConfirmModalConfig {
  title: string;
  message: string;
  primaryButtonText: string;
  primaryButtonVariant: "default" | "danger";
}

export function getCampaignConfirmModalConfig(
  action: ConfirmActionType | null,
): CampaignConfirmModalConfig | null {
  if (!action) return null;
  switch (action) {
    case "DUPLICATE":
      return {
        title: "Duplicate Campaign",
        message: "Are you sure you want to duplicate this campaign?",
        primaryButtonText: "Yes, Duplicate",
        primaryButtonVariant: "default",
      };
    case "ARCHIVE":
      return {
        title: "Archive Campaign",
        message: "Are you sure you want to archive this campaign?",
        primaryButtonText: "Yes, Archive",
        primaryButtonVariant: "default",
      };
    case "DELETE":
      return {
        title: "Delete Campaign",
        message:
          "Are you sure you want to delete this campaign? This action cannot be undone.",
        primaryButtonText: "Yes, Delete",
        primaryButtonVariant: "danger",
      };
    default:
      return null;
  }
}

export function shouldHideNavItem(
  keyword: string,
  hideList: string[],
): boolean {
  if (!hideList?.length) return false;
  return hideList.some(
    (hideKeyword) => hideKeyword.toLowerCase() === keyword.toLowerCase(),
  );
}

export function getConfirmActionErrorVerb(action: ConfirmActionType): string {
  switch (action) {
    case "DELETE":
      return "delete";
    case "DUPLICATE":
      return "duplicate";
    case "ARCHIVE":
      return "archive";
    default:
      return "complete";
  }
}
