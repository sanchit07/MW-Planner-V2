package com.mw.planner.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCampaignCostDTO {
  private String campaignId;
  private String campaignName;
  private Double cost;
  private Double revenue;
}
