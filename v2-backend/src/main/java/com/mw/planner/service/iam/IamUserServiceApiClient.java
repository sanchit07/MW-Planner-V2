package com.mw.planner.service.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.IamUserResponse;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.dto.UserMeCompaniesResponse;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.exception.user.UserNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Service for all IAM User related API calls. This service handles userinfo API and other
 * IAM-user-related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IamUserServiceApiClient {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;

  /**
   * Fetches user information from the IAM userinfo API endpoint.
   *
   * @param token Bearer token for authentication
   * @return UserInfoResponse containing user information, memberships, and permissions
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public UserInfoResponse getUserInfo(String token) {
    if (!StringUtils.hasText(token)) {
      throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS, "Token is required");
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      // Use IAM service URL for userinfo endpoint
      String userinfoUrl = mwPlannerProperties.getIam().getFullUserinfoUrl();
      log.debug("Calling IAM userinfo API: {}", userinfoUrl);

      ResponseEntity<UserInfoResponse> response =
          restTemplate.exchange(userinfoUrl, HttpMethod.GET, entity, UserInfoResponse.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Userinfo API returned non-2xx status: {}", response.getStatusCode());
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch user information from IAM API");
      }

      UserInfoResponse userInfoResponse = response.getBody();
      if (userInfoResponse == null || userInfoResponse.getData() == null) {
        log.error("Userinfo API returned null or empty response");
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response from IAM userinfo API");
      }

      log.debug(
          "Successfully retrieved user information for user: {}",
          userInfoResponse.getData().getUsername());
      return userInfoResponse;

    } catch (HttpClientErrorException ex) {
      String errorMessage = extractErrorMessage(ex);
      log.error(
          "Client error calling IAM userinfo API: {} - {}", ex.getStatusCode(), errorMessage, ex);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          errorMessage != null
              ? errorMessage
              : "Failed to fetch user information: " + ex.getStatusCode());
    } catch (HttpServerErrorException ex) {
      log.error(
          "Server error calling IAM userinfo API: {} - {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "IAM userinfo API server error: " + ex.getStatusCode());
    } catch (RestClientException ex) {
      log.error("Error calling IAM userinfo API", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to connect to IAM userinfo API: " + ex.getMessage());
    } catch (AuthenticationException ex) {
      // Re-throw AuthenticationException as-is
      throw ex;
    } catch (Exception e) {
      log.error("Unexpected error calling IAM userinfo API", e);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected error while fetching user information");
    }
  }

  /**
   * Fetches user information by user ID from the IAM users API endpoint.
   *
   * @param userId User ID to fetch
   * @param token Bearer token for authentication
   * @return UserResponseDTO containing user information (only the data object from API response)
   * @throws AuthenticationException if the API call fails or returns an error
   * @throws UserNotFoundException if user not found (404) or success=false
   */
  public UserResponseDTO getUserById(@NotNull String userId, @NotNull String token) {

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<IamUserResponse> response =
          restTemplate.exchange(
              mwPlannerProperties.getIam().getFullUserByIdUrl(),
              HttpMethod.GET,
              entity,
              IamUserResponse.class,
              userId);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Users API returned non-2xx status: {}", response.getStatusCode());
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch user information from IAM API");
      }

      IamUserResponse apiResponse = response.getBody();
      if (apiResponse == null) {
        log.error("Users API returned null response");
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response from IAM users API");
      }

      // Validate success field from API response
      if (apiResponse.getSuccess() == null || !apiResponse.getSuccess()) {
        String errorMessage =
            StringUtils.hasText(apiResponse.getMessage())
                ? apiResponse.getMessage()
                : "Failed to retrieve user information";
        log.error("Users API returned success=false for user ID {}: {}", userId, errorMessage);
        throw new UserNotFoundException("User not found: " + errorMessage);
      }

      // Validate data object exists
      if (apiResponse.getData() == null) {
        log.error("Users API returned success=true but data is null for user ID: {}", userId);
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response: data object is null");
      }

      // Map UserByIdData to UserResponseDTO
      UserResponseDTO userResponseDTO = mapToUserResponseDTO(apiResponse.getData());

      log.debug("Successfully retrieved user information for user ID: {}", userId);
      return userResponseDTO;

    } catch (HttpClientErrorException.NotFound ex) {
      log.error("User not found in IAM API: {}", userId);
      throw new UserNotFoundException(userId);
    } catch (UserNotFoundException ex) {
      // Re-throw UserNotFoundException as-is
      throw ex;
    } catch (HttpClientErrorException ex) {
      String errorMessage = extractErrorMessage(ex);
      log.error(
          "Client error calling IAM users API: {} - {}", ex.getStatusCode(), errorMessage, ex);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          errorMessage != null
              ? errorMessage
              : "Failed to fetch user information: " + ex.getStatusCode());
    } catch (HttpServerErrorException ex) {
      log.error(
          "Server error calling IAM users API: {} - {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "IAM users API server error: " + ex.getStatusCode());
    } catch (RestClientException ex) {
      log.error("Error calling IAM users API", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to connect to IAM users API: " + ex.getMessage());
    } catch (AuthenticationException ex) {
      // Re-throw AuthenticationException as-is
      throw ex;
    } catch (Exception e) {
      log.error("Unexpected error calling IAM users API", e);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected error while fetching user information");
    }
  }

  /**
   * Maps UserByIdData from API response to UserResponseDTO.
   *
   * @param data UserByIdData from API response
   * @return UserResponseDTO mapped from the data
   */
  private UserResponseDTO mapToUserResponseDTO(IamUserResponse.UserData data) {
    UserResponseDTO.UserResponseDTOBuilder builder =
        UserResponseDTO.builder()
            .id(data.getId())
            .userId(data.getUserId())
            .sub(data.getSub())
            .username(data.getUsername())
            .externalId(data.getExternalId())
            .email(data.getEmail())
            .firstName(data.getFirstName())
            .lastName(data.getLastName())
            .emailVerified(data.getEmailVerified())
            .phoneVerified(data.getPhoneVerified())
            .isGlobalAdmin(data.getIsGlobalAdmin())
            .hasSystemRole(data.getHasSystemRole())
            .permissions(data.getPermissions());

    // Map current company
    if (data.getCurrentCompany() != null) {
      IamUserResponse.CurrentCompany currentCompany = data.getCurrentCompany();

      // Map to CurrentCompanyDTO
      UserResponseDTO.CurrentCompanyDTO.CurrentCompanyDTOBuilder currentCompanyBuilder =
          UserResponseDTO.CurrentCompanyDTO.builder()
              .id(currentCompany.getId())
              .name(currentCompany.getName())
              .roleId(currentCompany.getRoleId())
              .roleName(currentCompany.getRoleName());

      // Map company type if available
      if (currentCompany.getCompanyType() != null) {
        IamUserResponse.CompanyType companyType = currentCompany.getCompanyType();
        UserResponseDTO.CompanyTypeDTO companyTypeDTO =
            UserResponseDTO.CompanyTypeDTO.builder()
                .id(companyType.getId())
                .name(companyType.getName())
                .code(companyType.getCode())
                .isSupplierSide(companyType.getIsSupplierSide())
                .isDemandSide(companyType.getIsDemandSide())
                .isParentCompanyType(companyType.getIsParentCompanyType())
                .build();
        currentCompanyBuilder.companyType(companyTypeDTO);
      }

      builder.currentCompany(currentCompanyBuilder.build());

      // Also set activeCompanyId for backward compatibility
      builder.activeCompanyId(currentCompany.getId());
    }

    // Map memberships
    if (data.getMemberships() != null && !data.getMemberships().isEmpty()) {
      List<UserResponseDTO.MembershipDTO> memberships =
          data.getMemberships().stream()
              .map(
                  membership ->
                      UserResponseDTO.MembershipDTO.builder()
                          .companyId(membership.getCompanyId())
                          .companyName(membership.getCompanyName())
                          .roleId(membership.getRoleId())
                          .roleName(membership.getRoleName())
                          .isActive(membership.getIsActive())
                          .build())
              .toList();
      builder.memberships(memberships);

      // Also map to legacy companies list for backward compatibility
      List<UserResponseDTO.CompanyDTO> companies =
          data.getMemberships().stream()
              .map(
                  membership ->
                      UserResponseDTO.CompanyDTO.builder()
                          .id(membership.getCompanyId())
                          .name(membership.getCompanyName())
                          .build())
              .toList();
      builder.companies(companies);
    }

    return builder.build();
  }

  /**
   * Fetches all companies the current user is a member of from the IAM /users/me/companies
   * endpoint.
   *
   * @param token Bearer token for authentication
   * @return List of Membership entries; empty list on failure
   */
  public List<UserInfoResponse.Membership> getUserMeCompanies(@NotNull String token) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      String baseUrl = mwPlannerProperties.getIam().getFullUserMeCompaniesUrl();
      List<UserInfoResponse.Membership> all = new java.util.ArrayList<>();
      int limit = 500;
      int offset = 0;

      while (true) {
        String url =
            UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .toUriString();

        log.debug("Calling IAM user-me-companies API: {}", url);

        ResponseEntity<UserMeCompaniesResponse> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, UserMeCompaniesResponse.class);

        UserMeCompaniesResponse body = response.getBody();
        if (body == null || body.getData() == null || body.getData().isEmpty()) {
          break;
        }

        List<UserInfoResponse.Membership> page = body.getData();
        all.addAll(page);

        if (page.size() < limit) {
          break;
        }
        offset += limit;
      }

      return all;

    } catch (Exception ex) {
      log.warn("Failed to fetch user me companies from IAM API: {}", ex.getMessage());
      return Collections.emptyList();
    }
  }

  /** Extracts error message from HttpClientErrorException response body */
  private String extractErrorMessage(HttpClientErrorException ex) {
    try {
      String responseBody = ex.getResponseBodyAsString();
      if (StringUtils.hasText(responseBody)) {
        // Try to parse the error response JSON
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> errorMap = objectMapper.readValue(responseBody, Map.class);

        // Check for error message fields
        String error = (String) errorMap.get("error");
        String errorDescription = (String) errorMap.get("error_description");
        String message = (String) errorMap.get("message");

        if (StringUtils.hasText(errorDescription)) {
          return errorDescription;
        } else if (StringUtils.hasText(message)) {
          return message;
        } else if (StringUtils.hasText(error)) {
          return "OAuth error: " + error;
        }

        return responseBody;
      }
    } catch (Exception e) {
      log.warn("Failed to parse error response body", e);
    }

    return ex.getStatusCode() + " " + ex.getStatusText();
  }
}
