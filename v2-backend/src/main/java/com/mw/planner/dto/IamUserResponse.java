package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Wrapper DTO for IAM getUserById API response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IamUserResponse {
  private Boolean success;
  private String message;
  private UserData data;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserData {
    private String id;

    @JsonProperty("user_id")
    private String userId;

    private String sub;
    private String email;
    private String username;
    private String externalId;

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
    private List<Membership> memberships;

    @JsonProperty("current_company")
    private CurrentCompany currentCompany;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Membership {
    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("role_id")
    private String roleId;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("is_active")
    private Boolean isActive;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CurrentCompany {
    private String id;
    private String name;

    @JsonProperty("company_type")
    private CompanyType companyType;

    @JsonProperty("role_id")
    private String roleId;

    @JsonProperty("role_name")
    private String roleName;
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
