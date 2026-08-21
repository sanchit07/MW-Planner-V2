package com.mw.planner.dto;

import com.mw.planner.domain.CampaignProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignProposalStatusResponseDTO {

  @Schema(
      description = "generated id of the proposal generation request",
      example = "propgen_123456")
  private String id;

  @Schema(
      description = "The ID of the campaign for which the proposal is to be generated",
      example = "camp_987654")
  private String campaignId;

  @Schema(description = "Current status of the proposal generation", example = "PLANNED")
  private CampaignProposalStatus.Status status;

  @Schema(
      description = "The ID of the media owner associated with the proposal",
      example = "mediaOwner_654321")
  private String mediaOwnerId;

  @Schema(
      description = "List of inventory IDs included in the proposal",
      example = "[\"inv_111\",\"inv_222\",\"inv_333\"]")
  private List<String> inventoryIds;

  public static List<CampaignProposalStatusResponseDTO> mapToDtoList(
      List<CampaignProposalStatus> proposalGenerations) {
    return proposalGenerations.stream()
        .map(
            proposalGeneration ->
                CampaignProposalStatusResponseDTO.builder()
                    .id(proposalGeneration.getId())
                    .campaignId(proposalGeneration.getCampaignId())
                    .status(proposalGeneration.getStatus())
                    .mediaOwnerId(proposalGeneration.getMediaOwnerId())
                    .inventoryIds(proposalGeneration.getInventoryIds())
                    .build())
        .toList();
  }

  public static CampaignProposalStatusResponseDTO mapToDto(
      CampaignProposalStatus proposalGeneration) {
    return CampaignProposalStatusResponseDTO.builder()
        .id(proposalGeneration.getId())
        .campaignId(proposalGeneration.getCampaignId())
        .status(proposalGeneration.getStatus())
        .mediaOwnerId(proposalGeneration.getMediaOwnerId())
        .inventoryIds(proposalGeneration.getInventoryIds())
        .build();
  }
}
