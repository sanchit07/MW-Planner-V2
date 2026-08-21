package com.mw.planner.dto;

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
@Schema(description = "Campaign price summary response")
public class CampaignPriceSummaryResponseDTO {

  @Schema(description = "Current Price: MediaCost (including hidden fees) + StandardFees")
  private Double currentPrice;

  @Schema(description = "Proposed Price: (MediaCost - discount) + StandardFees")
  private Double proposedPrice;

  @Schema(description = "Change in Price: CurrentPrice - ProposedPrice")
  private Double changeInPrice;

  @Schema(
      description =
          "Change in percentage: Percentage difference between Current Price and Change in Price")
  private Double changeInPercentage;

  @Schema(description = "MediaCost: basePrice + hidden fees (aggregated)")
  private Double mediaCost;

  @Schema(description = "DiscountedMediaCost: MediaCost - discount (aggregated)")
  private Double discountedMediaCost;

  @Schema(description = "StandardFees: Visible custom fees on top of discounted media cost")
  private Double standardFees;

  @Schema(
      description =
          "List of all visible CustomFee objects for the campaign and logged-in user company")
  private List<CustomFeeResponseDTO> customFees;

  @Schema(description = "Whether all schedules in the campaign are approved")
  private Boolean isAllApproved;
}
