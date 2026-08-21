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
@Document(collection = "campaign_approval_history")
public class CampaignApprovalHistory extends BaseEntity<String> {
  private String comment;
  private String campaignApprovedWorkflowStatusId;
  private Status status;

  public enum Status {
    APPROVED,
    REJECTED,
    IN_NEGOTIATION
  }
}
