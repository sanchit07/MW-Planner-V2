package com.mw.planner.dto;

import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Batch-loaded custom fees for a campaign: company-level and campaign-level fees keyed by
 * companyId, with hidden/visible split per company. Built once per campaign and passed through
 * price calculations to avoid repeated repository calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFeesContext {

  /** Company-level custom fees (campaignId null) by company ID. */
  @Builder.Default
  private Map<String, CompanyCustomFees> companyFeesByCompanyId = Collections.emptyMap();

  /** Campaign-level custom fees for this campaign by company ID. */
  @Builder.Default
  private Map<String, CompanyCustomFees> campaignFeesByCompanyId = Collections.emptyMap();
}
