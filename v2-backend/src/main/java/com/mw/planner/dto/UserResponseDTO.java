package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

  private String id;

  @JsonProperty("userId")
  private String userId;

  private String sub;

  private String username;

  private String externalId;

  private String email;

  @JsonProperty("firstName")
  private String firstName;

  @JsonProperty("lastName")
  private String lastName;

  @JsonProperty("emailVerified")
  private Boolean emailVerified;

  @JsonProperty("phoneVerified")
  private Boolean phoneVerified;

  @JsonProperty("isGlobalAdmin")
  private Boolean isGlobalAdmin;

  @JsonProperty("hasSystemRole")
  private Boolean hasSystemRole;

  private List<String> permissions;

  private List<MembershipDTO> memberships;

  @JsonProperty("currentCompany")
  private CurrentCompanyDTO currentCompany;

  // Legacy fields (kept for backward compatibility)
  @JsonProperty("companyPermissions")
  private Map<String, java.util.List<String>> companyPermissions;

  @JsonProperty("activeCompanyId")
  private String activeCompanyId;

  private List<CompanyDTO> companies;

  @JsonProperty("avatarUrl")
  private String avatarUrl;

  private String phone;

  @JsonProperty("jobTitle")
  private String jobTitle;

  private String department;

  private String location;

  private String bio;

  private String locale;

  @JsonProperty("timeZone")
  private String timeZone;

  private String countryId;

  private boolean activated;

  @JsonProperty("createdAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createdAt;

  @JsonProperty("updatedAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime updatedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyDTO {
    private String id;
    private String name;
    private String businessType;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MembershipDTO {
    @JsonProperty("companyId")
    private String companyId;

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("roleId")
    private String roleId;

    @JsonProperty("roleName")
    private String roleName;

    @JsonProperty("isActive")
    private Boolean isActive;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CurrentCompanyDTO {
    private String id;
    private String name;

    @JsonProperty("companyType")
    private CompanyTypeDTO companyType;

    @JsonProperty("roleId")
    private String roleId;

    @JsonProperty("roleName")
    private String roleName;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompanyTypeDTO {
    private String id;
    private String name;
    private String code;

    @JsonProperty("isSupplierSide")
    private Boolean isSupplierSide;

    @JsonProperty("isDemandSide")
    private Boolean isDemandSide;

    @JsonProperty("isParentCompanyType")
    private Boolean isParentCompanyType;
  }
}
