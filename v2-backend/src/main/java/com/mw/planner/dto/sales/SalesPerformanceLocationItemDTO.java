package com.mw.planner.dto.sales;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reusable DTO for location-based summaries.
 *
 * <p>- For showBy=COUNTRY: set city=null, country=country.
 *
 * <p>- For showBy=CITY: set city=city, country=country.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesPerformanceLocationItemDTO {
  private String country;
  private String city;
  private long inventories;
  private Double utilization;
  private Double conversion;
  private long countCampaigns;
  private Double cost;
  private Double revenue;

  /**
   * Count of inventories by classification (e.g. CLASSIC_NETWORK, Digital, Transit). Present when
   * showBy=country.
   */
  private Map<String, Long> classification;
}
