package com.mw.planner.constants;

/** Display strings used for campaign approval workflow actions. */
public enum CampaignApprovalAction {
  APPROVED("Approved"),
  REJECTED("Rejected"),
  REQUESTED_CHANGES("Requested Changes");

  private final String label;

  CampaignApprovalAction(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
