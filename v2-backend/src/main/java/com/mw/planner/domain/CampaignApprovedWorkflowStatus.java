package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_approved_workflow_status")
public class CampaignApprovedWorkflowStatus extends BaseEntity<String> {
  private String campaignId;
  private Status status = Status.PENDING; // default status
  private ApprovalAuthority approvalAuthority;

  public enum Status {
    PENDING,
    IN_PROGRESS,
    CHANGES_REQUESTED,
    COMPLETED,
    REJECTED,
    SKIPPED
  }

  public enum ApprovalAuthority {
    AGENCY,
    INTERNAL,
    MEDIA_OWNER
  }
}
