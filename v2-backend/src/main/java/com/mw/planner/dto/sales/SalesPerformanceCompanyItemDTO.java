package com.mw.planner.dto.sales;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reusable DTO for showBy=advertiser and showBy=agency.
 *
 * <p>- For advertiser: companyId = advertiser companyId, name = advertiser name
 *
 * <p>- For agency: companyId = agencyId, name = agency name
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesPerformanceCompanyItemDTO {
  private String companyId;
  private String name;
  private Double revenue;
  private long countCampaigns;
  private Double share;
  private Long adPlays;
  private Double sov;
  private Long impressions;
  private List<TopCampaignCostDTO> topCampaigns;
}
