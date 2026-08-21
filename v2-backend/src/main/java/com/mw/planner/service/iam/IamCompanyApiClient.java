package com.mw.planner.service.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.config.MwPlannerProperties;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.IamCompanyResponse;
import com.mw.planner.dto.UserInfoResponse;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.service.SecurityContextService;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * IAM Company API client. Handles HTTP communication with IAM Company API endpoint. This component
 * is responsible only for API communication, not caching or business logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamCompanyApiClient {

  private final MwPlannerProperties mwPlannerProperties;
  private final RestTemplate restTemplate;
  private final SecurityContextService securityContextService;

  /**
   * Fetches company information from the IAM Company API endpoint.
   *
   * @param companyId Company ID to fetch
   * @param token Bearer token for authentication
   * @return IamCompanyResponse containing company information
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public IamCompanyResponse getCompanyById(@NotNull String companyId, @NotNull String token) {

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      String companyUrl = mwPlannerProperties.getIam().getFullCompanyByIdUrl();
      log.debug("Calling IAM Company API: {}", companyUrl);

      ResponseEntity<IamCompanyResponse> response =
          restTemplate.exchange(
              companyUrl, HttpMethod.GET, entity, IamCompanyResponse.class, companyId);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Company API returned non-2xx status: {}", response.getStatusCode());
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch company information from IAM API");
      }

      IamCompanyResponse apiResponse = response.getBody();
      if (apiResponse == null) {
        log.error("Company API returned null response");
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response from IAM Company API");
      }

      // Validate success field from API response
      if (apiResponse.getSuccess() == null || !apiResponse.getSuccess()) {
        String errorMessage =
            StringUtils.hasText(apiResponse.getMessage())
                ? apiResponse.getMessage()
                : "Failed to retrieve company information";
        log.error(
            "Company API returned success=false for company ID {}: {}", companyId, errorMessage);
        throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS, errorMessage);
      }

      // Validate data object exists
      if (apiResponse.getData() == null || apiResponse.getData().getCompany() == null) {
        log.error(
            "Company API returned success=true but company data is null for company ID: {}",
            companyId);
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response: company data is null");
      }

      log.debug("Successfully retrieved company information for company ID: {}", companyId);
      return apiResponse;

    } catch (HttpClientErrorException.NotFound ex) {
      log.error("Company not found in IAM API: {}", companyId);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS, "Company not found: " + companyId);
    } catch (HttpClientErrorException ex) {
      String errorMessage = extractErrorMessage(ex);
      log.error(
          "Client error calling IAM Company API: {} - {}", ex.getStatusCode(), errorMessage, ex);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          errorMessage != null
              ? errorMessage
              : "Failed to fetch company information: " + ex.getStatusCode());
    } catch (HttpServerErrorException ex) {
      log.error(
          "Server error calling IAM Company API: {} - {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "IAM Company API server error: " + ex.getStatusCode());
    } catch (RestClientException ex) {
      log.error("Error calling IAM Company API", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to connect to IAM Company API: " + ex.getMessage());
    } catch (AuthenticationException ex) {
      // Re-throw AuthenticationException as-is
      throw ex;
    } catch (Exception e) {
      log.error("Unexpected error calling IAM Company API", e);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected error while fetching company information");
    }
  }

  /**
   * Fetches child companies of the given company from the IAM API.
   *
   * @param companyId Parent company ID
   * @param token Bearer token for authentication
   * @return List of ChildCompany entries; empty list on failure
   */
  public List<UserInfoResponse.ChildCompany> getCompanyChildren(
      @NotNull String companyId, @NotNull String token) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      String url = mwPlannerProperties.getIam().getFullIamCompanyChildrenUrl();
      log.debug("Calling IAM company children API for companyId: {}", companyId);

      ResponseEntity<CompanyChildrenResponse> response =
          restTemplate.exchange(
              url, HttpMethod.GET, entity, CompanyChildrenResponse.class, companyId);

      CompanyChildrenResponse body = response.getBody();
      if (body == null || body.getData() == null || body.getData().getItems() == null) {
        return Collections.emptyList();
      }
      return body.getData().getItems();

    } catch (Exception ex) {
      log.warn("Failed to fetch company children for companyId {}: {}", companyId, ex.getMessage());
      return Collections.emptyList();
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class CompanyChildrenResponse {
    private Boolean success;
    private String message;
    private UserInfoResponse.ChildCompanies data;
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

  /**
   * Fetches company lookup results from the IAM Company API endpoint with optional filters.
   *
   * @param token Bearer token for authentication
   * @param limit Number of items per page (default 50, max 100)
   * @param offset Number of items to skip (default 0)
   * @param search Search by company name
   * @param companyType Filter by company type code (e.g., AGENCY, MEDIA_OWNER)
   * @param country Filter by country code (e.g., US, MY)
   * @param fields Comma-separated additional fields:
   *     domain,seat_id,external_id,country_code,company_type,logo_url,currency_code,timezone
   * @return List of CompanyLookupResponseDTO containing company information
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public CompanyLookupResponseDTO.CompanyLookupListResponse companyLookup(
      @NotNull String token,
      Integer limit,
      Integer offset,
      String search,
      String companyType,
      String country,
      String domain,
      String notificationEmail,
      String currencyCode,
      String timezone,
      String parentCompanyId,
      String companyId,
      String fields) {

    String baseUrl = mwPlannerProperties.getIam().getFullCompanyLookupUrl();
    UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl);

    if (limit != null) {
      uriBuilder.queryParam("limit", limit);
    }
    if (offset != null) {
      uriBuilder.queryParam("offset", offset);
    }
    if (StringUtils.hasText(search)) {
      uriBuilder.queryParam("search", search);
    }
    if (StringUtils.hasText(companyType)) {
      uriBuilder.queryParam("company_type", companyType);
    }
    if (StringUtils.hasText(country)) {
      uriBuilder.queryParam("country", country);
    }

    if (StringUtils.hasText(domain)) {
      uriBuilder.queryParam("domain", domain);
    }

    if (StringUtils.hasText(notificationEmail)) {
      uriBuilder.queryParam("notification_email", notificationEmail);
    }

    if (StringUtils.hasText(currencyCode)) {
      uriBuilder.queryParam("currency_code", currencyCode);
    }

    if (StringUtils.hasText(timezone)) {
      uriBuilder.queryParam("timezone", timezone);
    }

    if (StringUtils.hasText(parentCompanyId)) {
      uriBuilder.queryParam("parent_company_id", parentCompanyId);
    }

    if (StringUtils.hasText(companyId)) {
      uriBuilder.queryParam("company_id", companyId);
    }

    if (StringUtils.hasText(fields)) {
      uriBuilder.queryParam("fields", fields);
    }

    return callCompanyLookupApi(token, companyId, uriBuilder);
  }

  private CompanyLookupResponseDTO.CompanyLookupListResponse callCompanyLookupApi(
      String token, String companyId, UriComponentsBuilder uriBuilder) {
    return callCompanyLookupApi(token, companyId, uriBuilder, true);
  }

  private CompanyLookupResponseDTO.CompanyLookupListResponse callCompanyLookupApi(
      String token,
      String companyId,
      UriComponentsBuilder uriBuilder,
      boolean includeCompanyIdHeader) {

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(token);
      if (StringUtils.hasText(companyId) && includeCompanyIdHeader) {
        headers.set("X-Company-Id", companyId);
      }
      log.info("Headesrs for IAM Company Lookup API call: {}", headers);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      String lookupUrl = uriBuilder.toUriString();
      log.info("Calling IAM Company Lookup API: {}", lookupUrl);

      ResponseEntity<CompanyLookupResponseDTO.CompanyLookupListResponse> response =
          restTemplate.exchange(
              lookupUrl,
              HttpMethod.GET,
              entity,
              CompanyLookupResponseDTO.CompanyLookupListResponse.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Company Lookup API returned non-2xx status: {}", response.getStatusCode());
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch company lookup from IAM API");
      }

      CompanyLookupResponseDTO.CompanyLookupListResponse apiResponse = response.getBody();
      if (apiResponse == null) {
        log.error("Company Lookup API returned null response");
        throw new AuthenticationException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Invalid response from IAM Company Lookup API");
      }

      if (apiResponse.getSuccess() == null || !apiResponse.getSuccess()) {
        String errorMessage =
            StringUtils.hasText(apiResponse.getMessage())
                ? apiResponse.getMessage()
                : "Failed to retrieve company lookup information";
        log.error("Company Lookup API returned success=false: {}", errorMessage);
        throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS, errorMessage);
      }

      return apiResponse;

    } catch (HttpClientErrorException.NotFound ex) {
      log.error("Company not found in IAM API");
      throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS, "Company not found");
    } catch (HttpClientErrorException ex) {
      String errorMessage = extractErrorMessage(ex);
      log.error(
          "Client error calling IAM Company Lookup API: {} - {}",
          ex.getStatusCode(),
          errorMessage,
          ex);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS,
          errorMessage != null
              ? errorMessage
              : "Failed to fetch company lookup: " + ex.getStatusCode());
    } catch (HttpServerErrorException ex) {
      log.error(
          "Server error calling IAM Company Lookup API: {} - {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "IAM Company Lookup API server error: " + ex.getStatusCode());
    } catch (RestClientException ex) {
      log.error("Error calling IAM Company Lookup API", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to connect to IAM Company Lookup API: " + ex.getMessage());
    } catch (Exception ex) {
      log.error("Unexpected error calling IAM Company Lookup API", ex);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected error while fetching company lookup");
    }
  }

  /**
   * Fetches company lookup result by companyId with seat_id and external_id fields from the IAM
   * Company API endpoint.
   *
   * @param companyId Company ID to lookup
   * @return CompanyLookupResponseDTO containing company information with seat_id and external_id
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public CompanyLookupResponseDTO getCompanyLookupWithCompanyId(@NotNull String companyId) {
    return getCompanyLookupWithCompanyId(companyId, true);
  }

  /**
   * Fetches company lookup result by companyId, optionally omitting the {@code X-Company-Id}
   * header.
   *
   * @param companyId Company ID to lookup
   * @param includeCompanyIdHeader when {@code false}, the {@code X-Company-Id} scoping header is
   *     not sent (used for cross-company lookups such as the ADS submission flow); the {@code
   *     company_id} query param still identifies the target
   * @return CompanyLookupResponseDTO containing company information with seat_id and external_id
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public CompanyLookupResponseDTO getCompanyLookupWithCompanyId(
      @NotNull String companyId, boolean includeCompanyIdHeader) {

    String token = securityContextService.getBearerToken();

    String baseUrl = mwPlannerProperties.getIam().getFullCompanyLookupUrl();
    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("company_id", companyId)
            .queryParam(
                "fields",
                "domain,seat_id,external_id,company_type,is_active,notification_email,company_country,currency_code,timezone");

    CompanyLookupResponseDTO.CompanyLookupListResponse apiResponse =
        callCompanyLookupApi(token, companyId, uriBuilder, includeCompanyIdHeader);

    List<CompanyLookupResponseDTO> companies = apiResponse.getData();
    if (companies == null || companies.isEmpty()) {
      log.error("Company Lookup API returned no data for companyId: {}", companyId);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS, "Company not found: " + companyId);
    }

    return companies.getFirst();
  }

  /**
   * Fetches company lookup result by companyId with currency_code from the IAM Company API
   * endpoint.
   *
   * @param companyId Company ID to lookup
   * @return CompanyLookupResponseDTO containing company information with currency_code
   * @throws AuthenticationException if the API call fails or returns an error
   */
  public CompanyLookupResponseDTO getCompanyCurrencyCodeWithCompanyId(@NotNull String companyId) {

    String token = securityContextService.getBearerToken();

    String baseUrl = mwPlannerProperties.getIam().getFullCompanyLookupUrl();
    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("company_id", companyId)
            .queryParam("fields", "currency_code");

    CompanyLookupResponseDTO.CompanyLookupListResponse apiResponse =
        callCompanyLookupApi(token, companyId, uriBuilder);

    List<CompanyLookupResponseDTO> companies = apiResponse.getData();
    if (companies == null || companies.isEmpty()) {
      log.error("Company Lookup API returned no data for companyId: {}", companyId);
      throw new AuthenticationException(
          ErrorCode.INVALID_CREDENTIALS, "Company not found: " + companyId);
    }

    return companies.getFirst();
  }
}
