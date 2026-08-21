package com.mw.planner.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_proposal_status")
public class CampaignProposalStatus extends BaseEntity<String> {
  private String campaignId;
  private Status status = Status.PENDING; // default status
  private String mediaOwnerId;
  private List<String> inventoryIds;

  public enum Status {
    PENDING,
    APPROVED,
    NEGOTIATING,
    REJECTED
  }
}
