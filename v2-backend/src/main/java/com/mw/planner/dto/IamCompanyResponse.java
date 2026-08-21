package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Wrapper DTO for IAM Company API response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamCompanyResponse {
  private Boolean success;
  private String message;
  private CompanyData data;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyData {
    private Company company;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Company {
    private String id;

    @JsonProperty("seat_id")
    private Integer seatId;

    @JsonProperty("external_id")
    private String externalId;

    private String name;

    private String phone;

    private String email;

    private String externalUserId;

    private String domain;
    private String status;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("company_type")
    private CompanyType companyType;

    @JsonProperty("max_users")
    private Integer maxUsers;

    private List<Subscription> subscriptions;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyType {
    private String id;
    private String name;
    private String code;

    @JsonProperty("is_supplier_side")
    private Boolean isSupplierSide;

    @JsonProperty("is_demand_side")
    private Boolean isDemandSide;

    @JsonProperty("is_parent_company_type")
    private Boolean isParentCompanyType;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Subscription {
    private String id;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("deleted_at")
    private String deletedAt;

    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("product_id")
    private String productId;

    private Product product;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("start_date")
    private Long startDate;

    @JsonProperty("max_licenses")
    private Integer maxLicenses;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Product {
    private String id;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("deleted_at")
    private String deletedAt;

    private String name;
    private String description;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("product_code")
    private String productCode;

    @JsonProperty("product_type")
    private String productType;
  }
}
