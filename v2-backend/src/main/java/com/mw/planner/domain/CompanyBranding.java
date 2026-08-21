package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Planner-side tenant branding (white-label + logo). Deliberately separate from IAM/Admin Console's
 * company record — v2-backend has no local Company domain (company data is proxied live via {@code
 * IamCompanyApiClient}), and pushing branding into that external product is out of scope here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "company_branding")
public class CompanyBranding extends BaseEntity<String> {

  @Indexed(unique = true)
  private String companyId;

  @Builder.Default private boolean whiteLabel = false;
  private String logoUrl;
}
