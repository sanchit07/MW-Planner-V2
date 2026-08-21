package com.mw.planner.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {

  /** Company ID */
  private String id;

  /** Seat id */
  private Integer seatId;

  /** External identifier from IAM (previously mapped as companyId) */
  private String externalId;

  private String name;

  private String phone;

  private String email;

  private String externalUserId;

  /** Company status from IAM */
  private String status;

  /** Active flag from IAM (is_active) */
  private boolean activated;

  /** Company type info from IAM */
  private CompanyType companyType;

  /** Max users from IAM */
  private Integer maxUsers;

  /** Subscriptions from IAM */
  private List<Subscription> subscriptions;

  /** Timestamps from IAM */
  private String createdAt;

  private String updatedAt;

  /**
   * Internal role classification used by Planner (derived from IAM companyType.code). Kept for
   * backwards compatibility with business logic and message localization.
   */
  private BusinessType businessType;

  public enum BusinessType {
    MEDIA_OWNER,
    MEDIA_OPERATOR,
    MEDIA_BUYER,
    MEDIA_AGENCY,
    ALL
  }

  /**
   * Legacy enum kept for backwards compatibility with existing DTOs. Not used for persistence. (IAM
   * company payload does not provide this field.)
   */
  public enum AccountType {
    INTERNAL,
    EXTERNAL,
    PARTNER
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyType {
    private String id;
    private String name;
    private String code;
    private Boolean isSupplierSide;
    private Boolean isDemandSide;
    private Boolean isParentCompanyType;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Subscription {
    private String id;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;
    private String companyId;
    private String productId;
    private Product product;
    private Boolean isActive;
    private Long startDate;
    private Integer maxLicenses;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Product {
    private String id;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;
    private String name;
    private String description;
    private Boolean isActive;
    private String productCode;
    private String productType;
  }
}
