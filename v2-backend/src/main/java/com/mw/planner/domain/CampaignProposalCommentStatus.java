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
@Document(collection = "campaign_proposal_comments")
public class CampaignProposalCommentStatus extends BaseEntity<String> {

  private String proposalId;
  private CampaignProposalStatus.Status status; // default status
  private String comments;
  private String parentCommentId;
}
