package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for IAM userinfo "data" payload.
 *
 * <p>This matches the IAM userinfo response shape (snake_case fields).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponseDto {
  private String id;

  @JsonProperty("user_id")
  private String userId;

  private String sub;
  private String email;
  private String username;

  @JsonProperty("first_name")
  private String firstName;

  @JsonProperty("last_name")
  private String lastName;

  @JsonProperty("email_verified")
  private Boolean emailVerified;

  @JsonProperty("phone_verified")
  private Boolean phoneVerified;

  @JsonProperty("is_global_admin")
  private Boolean isGlobalAdmin;

  @JsonProperty("has_system_role")
  private Boolean hasSystemRole;

  private List<String> permissions;

  /** Per-company authority map (companyId -> permission strings) from the IAM. */
  @JsonProperty("company_permissions")
  private java.util.Map<String, List<String>> companyPermissions;

  private List<Membership> memberships;

  @JsonProperty("current_company")
  private CurrentCompany currentCompany;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Membership {
    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("company_seat_id")
    private Integer companySeatId;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("role_id")
    private String roleId;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("company_type")
    private CompanyType companyType;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CurrentCompany {
    private String id;

    @JsonProperty("seat_id")
    private Integer seatId;

    private String name;

    @JsonProperty("company_type")
    private CompanyType companyType;

    @JsonProperty("role_id")
    private String roleId;

    @JsonProperty("role_name")
    private String roleName;

    private ChildCompanies childCompanies;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ChildCompanies {
    private List<ChildCompany> items;
    private Integer totalCount;
    private Boolean hasMore;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ChildCompany {
    private String id;
    private String name;
    private CompanyType companyType;
    private List<String> grantedScopes;
    private List<String> scopes;
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
}
