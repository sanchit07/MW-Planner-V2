package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User context from IAM userinfo API response. This class represents the complete user information
 * from IAM including permissions and memberships.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "IAM user context information from userinfo API")
public class IamUserContext implements Serializable {

  @Schema(description = "Unique user identifier", example = "a7134345-98cd-4af2-91f5-9e13e2ccbc1c")
  private String id;

  @Schema(description = "User ID (same as id)", example = "a7134345-98cd-4af2-91f5-9e13e2ccbc1c")
  private String userId;

  @Schema(description = "Subject identifier", example = "planner-admin")
  private String sub;

  @Schema(description = "Username", example = "planner-admin")
  private String username;

  @Schema(description = "User email address", example = "planner-admin@ooh-auth.local")
  private String email;

  @Schema(description = "User's first name", example = "Planner")
  private String firstName;

  @Schema(description = "User's last name", example = "Admin")
  private String lastName;

  @Schema(description = "Email verified status")
  private Boolean emailVerified;

  @Schema(description = "Phone verified status")
  private Boolean phoneVerified;

  @Schema(description = "Is global admin")
  private Boolean isGlobalAdmin;

  @Schema(description = "Has system role")
  private Boolean hasSystemRole;

  @Schema(description = "System permissions")
  private List<String> systemPermissions;

  @Schema(description = "User's locale preference", example = "en_US")
  private Locale locale;

  @Schema(description = "User permissions")
  private List<String> permissions;

  @Schema(description = "User memberships")
  private List<UserInfoResponse.Membership> memberships;

  @Schema(
      description = "Active company ID (from first active membership)",
      example = "00c3298d-a45b-47ef-831e-403563090adc")
  private String companyId;

  @Schema(description = "Current company is supplier-side (e.g. Media Owner)")
  private Boolean isSupplierSide;

  @Schema(description = "Child companies under the current active company")
  private List<UserInfoResponse.ChildCompany> childCompanies;

  /**
   * Creates an IamUserContext from UserInfoResponse data
   *
   * @param userInfoData The UserInfoData from IAM API response
   * @return IamUserContext instance
   */
  public static IamUserContext fromUserInfoData(UserInfoResponse.UserInfoData userInfoData) {
    if (userInfoData == null) {
      return null;
    }

    // Get current company ID as active company
    String activeCompanyId = null;
    if (userInfoData.getCurrentCompany() != null) {
      activeCompanyId = userInfoData.getCurrentCompany().getId();
    }

    Boolean isSupplierSide = null;
    List<UserInfoResponse.ChildCompany> childCompanies = null;
    if (userInfoData.getCurrentCompany() != null) {
      UserInfoResponse.CurrentCompany currentCompany = userInfoData.getCurrentCompany();
      if (currentCompany.getCompanyType() != null) {
        isSupplierSide = currentCompany.getCompanyType().getIsSupplierSide();
      }
      if (currentCompany.getChildCompanies() != null) {
        childCompanies = currentCompany.getChildCompanies().getItems();
      }
    }

    return IamUserContext.builder()
        .id(userInfoData.getId())
        .userId(userInfoData.getUserId())
        .sub(userInfoData.getSub())
        .username(userInfoData.getUsername())
        .email(userInfoData.getEmail())
        .firstName(userInfoData.getFirstName())
        .lastName(userInfoData.getLastName())
        .emailVerified(userInfoData.getEmailVerified())
        .phoneVerified(userInfoData.getPhoneVerified())
        .isGlobalAdmin(userInfoData.getIsGlobalAdmin())
        .hasSystemRole(userInfoData.getHasSystemRole())
        .systemPermissions(userInfoData.getSystemPermissions())
        .permissions(userInfoData.getPermissions())
        .memberships(userInfoData.getMemberships())
        .companyId(activeCompanyId)
        .isSupplierSide(isSupplierSide)
        .childCompanies(childCompanies)
        .build();
  }
}
