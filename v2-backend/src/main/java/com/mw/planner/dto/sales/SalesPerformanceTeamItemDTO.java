package com.mw.planner.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesPerformanceTeamItemDTO {
  private String userId;
  private String name;
  private String region;
  private long countCampaigns;
  private Double revenue;
  private Double conversion;
  private Double share;
}
