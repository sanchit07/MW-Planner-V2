package com.mw.planner.dto.creative;

import com.mw.planner.domain.CreativeAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeAssignmentDTO {

  private String id;
  private String creativeId;
  private String lineItemId;
  private String campaignId;
  private String mediaOwnerId;
  private String inventoryId;
  private CreativeAssignment.BindingStatus bindingStatus;
  private boolean forcedMatch;
  private String forcedMatchReason;

  public static CreativeAssignmentDTO from(CreativeAssignment a) {
    return CreativeAssignmentDTO.builder()
        .id(a.getId())
        .creativeId(a.getCreativeId())
        .lineItemId(a.getLineItemId())
        .campaignId(a.getCampaignId())
        .mediaOwnerId(a.getMediaOwnerId())
        .inventoryId(a.getInventoryId())
        .bindingStatus(a.getBindingStatus())
        .forcedMatch(a.isForcedMatch())
        .forcedMatchReason(a.getForcedMatchReason())
        .build();
  }
}
