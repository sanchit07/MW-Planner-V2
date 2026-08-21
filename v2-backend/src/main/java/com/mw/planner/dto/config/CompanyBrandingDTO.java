package com.mw.planner.dto.config;

import com.mw.planner.domain.CompanyBranding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBrandingDTO {

  private String companyId;
  private boolean whiteLabel;
  private String logoUrl;

  public static CompanyBrandingDTO from(CompanyBranding d) {
    return CompanyBrandingDTO.builder()
        .companyId(d.getCompanyId())
        .whiteLabel(d.isWhiteLabel())
        .logoUrl(d.getLogoUrl())
        .build();
  }

  public static CompanyBrandingDTO defaults(String companyId) {
    return CompanyBrandingDTO.builder()
        .companyId(companyId)
        .whiteLabel(false)
        .logoUrl(null)
        .build();
  }
}
