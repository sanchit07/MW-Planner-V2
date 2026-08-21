package com.mw.planner.enums;

/**
 * Enum representing campaign approval statuses for display purposes. These statuses are
 * user-specific and context-aware based on the user's role (maintainer/agency/internal vs media
 * owner).
 */
public enum CampaignApprovalStatus {
  AWAITING_AGENCY_ACCEPTANCE,
  AWAITING_INTERNAL_REVIEW,
  AWAITING_MEDIA_OWNER_APPROVAL,
  APPROVED,
  REJECTED,
  IN_NEGOTIATION
}
