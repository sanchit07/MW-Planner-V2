package com.mw.planner.controller;

import com.mw.planner.dto.*;
import com.mw.planner.exception.user.UserNotFoundException;
import com.mw.planner.service.SecurityContextService;
import com.mw.planner.service.iam.IamUserServiceApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User related operations")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserController {

  private final SecurityContextService securityContextService;
  private final IamUserServiceApiClient iamUserService;

  @GetMapping("/userinfo")
  @Operation(
      summary = "Get user info from IAM",
      description =
          "Returns user information from IAM service including user details, memberships, and permissions. This is a proxy endpoint that forwards the request to IAM userinfo API.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User info retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "User not authenticated or invalid token",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<UserInfoResponseDto> getUserInfo() {
    try {
      String token = securityContextService.getBearerToken();
      UserInfoResponse userInfoResponse = iamUserService.getUserInfo(token);

      if (userInfoResponse == null || userInfoResponse.getData() == null) {
        throw new UserNotFoundException("User info not found");
      }

      return ApiResponse.success(toUserInfoResponseDto(userInfoResponse.getData()));
    } catch (com.mw.planner.exception.auth.AuthenticationException e) {
      throw new UserNotFoundException("Unable to get user info", e);
    }
  }

  private static UserInfoResponseDto toUserInfoResponseDto(UserInfoResponse.UserInfoData data) {
    if (data == null) {
      return null;
    }

    UserInfoResponseDto.UserInfoResponseDtoBuilder builder =
        UserInfoResponseDto.builder()
            .id(data.getId())
            .userId(data.getUserId())
            .sub(data.getSub())
            .email(data.getEmail())
            .username(data.getUsername())
            .firstName(data.getFirstName())
            .lastName(data.getLastName())
            .emailVerified(data.getEmailVerified())
            .phoneVerified(data.getPhoneVerified())
            .isGlobalAdmin(data.getIsGlobalAdmin())
            .hasSystemRole(data.getHasSystemRole())
            .permissions(data.getPermissions())
            .companyPermissions(data.getCompanyPermissions());

    if (data.getMemberships() != null) {
      builder.memberships(
          data.getMemberships().stream()
              .map(
                  m -> {
                    UserInfoResponseDto.Membership.MembershipBuilder membershipBuilder =
                        UserInfoResponseDto.Membership.builder()
                            .companyId(m.getCompanyId())
                            .companySeatId(m.getCompanySeatId())
                            .companyName(m.getCompanyName())
                            .roleId(m.getRoleId())
                            .roleName(m.getRoleName())
                            .isActive(m.getIsActive());

                    if (m.getCompanyType() != null) {
                      UserInfoResponse.CompanyType ct = m.getCompanyType();
                      membershipBuilder.companyType(
                          UserInfoResponseDto.CompanyType.builder()
                              .id(ct.getId())
                              .name(ct.getName())
                              .code(ct.getCode())
                              .isSupplierSide(ct.getIsSupplierSide())
                              .isDemandSide(ct.getIsDemandSide())
                              .isParentCompanyType(ct.getIsParentCompanyType())
                              .build());
                    }

                    return membershipBuilder.build();
                  })
              .toList());
    }

    if (data.getCurrentCompany() != null) {
      UserInfoResponse.CurrentCompany currentCompany = data.getCurrentCompany();
      UserInfoResponseDto.CurrentCompany.CurrentCompanyBuilder currentCompanyBuilder =
          UserInfoResponseDto.CurrentCompany.builder()
              .id(currentCompany.getId())
              .seatId(currentCompany.getSeatId())
              .name(currentCompany.getName())
              .roleId(currentCompany.getRoleId())
              .roleName(currentCompany.getRoleName());

      if (currentCompany.getCompanyType() != null) {
        UserInfoResponse.CompanyType companyType = currentCompany.getCompanyType();
        currentCompanyBuilder.companyType(
            UserInfoResponseDto.CompanyType.builder()
                .id(companyType.getId())
                .name(companyType.getName())
                .code(companyType.getCode())
                .isSupplierSide(companyType.getIsSupplierSide())
                .isDemandSide(companyType.getIsDemandSide())
                .isParentCompanyType(companyType.getIsParentCompanyType())
                .build());
      }

      if (currentCompany.getChildCompanies() != null) {
        UserInfoResponse.ChildCompanies cc = currentCompany.getChildCompanies();
        List<UserInfoResponseDto.ChildCompany> items =
            cc.getItems() != null
                ? cc.getItems().stream()
                    .map(
                        child ->
                            UserInfoResponseDto.ChildCompany.builder()
                                .id(child.getId())
                                .name(child.getName())
                                .companyType(
                                    child.getCompanyType() != null
                                        ? UserInfoResponseDto.CompanyType.builder()
                                            .id(child.getCompanyType().getId())
                                            .name(child.getCompanyType().getName())
                                            .code(child.getCompanyType().getCode())
                                            .isSupplierSide(
                                                child.getCompanyType().getIsSupplierSide())
                                            .isDemandSide(child.getCompanyType().getIsDemandSide())
                                            .isParentCompanyType(
                                                child.getCompanyType().getIsParentCompanyType())
                                            .build()
                                        : null)
                                .grantedScopes(child.getGrantedScopes())
                                .scopes(child.getScopes())
                                .build())
                    .toList()
                : null;
        currentCompanyBuilder.childCompanies(
            UserInfoResponseDto.ChildCompanies.builder()
                .items(items)
                .totalCount(cc.getTotalCount())
                .hasMore(cc.getHasMore())
                .build());
      }

      builder.currentCompany(currentCompanyBuilder.build());
    }

    return builder.build();
  }
}
