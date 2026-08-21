package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for Company Lookup API response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyLookupResponseDTO {
  private String id;
  private String name;

  @JsonProperty("domain")
  private String domain;

  @JsonProperty("seat_id")
  private Integer seatId;

  @JsonProperty("external_id")
  private String externalId;

  @JsonProperty("company_type")
  private String companyType;

  @JsonProperty("is_active")
  private String isActive;

  @JsonProperty("notification_email")
  private String notificationEmail;

  /** Whether the company has access to MW Influence (execution platform for digital OOH). */
  @JsonProperty("influence_access")
  private Boolean influenceAccess;

  @JsonProperty("company_country")
  private String companyCountry;

  @JsonProperty("currency_code")
  private String currencyCode;

  @JsonProperty("country_code")
  private String countryCode;

  @JsonProperty("timezone")
  private String timezone;

  @JsonProperty("logo_url")
  private String logoUrl;

  /** Pagination Meta */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Meta {
    private Integer total;
    private Integer limit;
    private Integer offset;
  }

  /** Wrapper for list response */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyLookupListResponse {
    private List<CompanyLookupResponseDTO> data;
    private Boolean success;
    private String message;
    private Meta meta;
  }
}
