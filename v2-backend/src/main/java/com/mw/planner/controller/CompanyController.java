package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.auth.AuthenticationException;
import com.mw.planner.service.SecurityContextService;
import com.mw.planner.service.iam.IamCompanyApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Company Management", description = "Company related operations")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanyController {

  private final IamCompanyApiClient iamCompanyApiClient;
  private final SecurityContextService securityContextService;

  @GetMapping("/lookup")
  @Operation(
      summary = "Company lookup",
      description =
          "Searches and filters companies with optional parameters: limit, offset, search, company_type, country, and fields")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Company lookup successful",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "User not authenticated",
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
  public ApiResponse<List<CompanyLookupResponseDTO>> companyLookup(
      @Parameter(description = "Number of items per page (default 50, max 100)", example = "50")
          @RequestParam(required = false)
          Integer limit,
      @Parameter(description = "Number of items to skip (default 0)", example = "0")
          @RequestParam(required = false)
          Integer offset,
      @Parameter(description = "Search by company name", example = "MW India")
          @RequestParam(required = false)
          String search,
      @Parameter(
              description = "Filter by company type code (e.g., AGENCY, MEDIA_OWNER)",
              example = "AGENCY")
          @RequestParam(required = false)
          String company_type,
      @Parameter(description = "Filter by country code (e.g., US, MY)", example = "US")
          @RequestParam(required = false)
          String country,
      @Parameter(description = "Filter by domain (exact match)", example = "domain")
          @RequestParam(required = false)
          String domain,
      @Parameter(
              description = "Filter by notification email (exact match)",
              example = "notification_email")
          @RequestParam(required = false)
          String notification_email,
      @Parameter(
              description = "Filter by currency code (e.g., USD, MYR)",
              example = "currency_code")
          @RequestParam(required = false)
          String currencyCode,
      @Parameter(description = "Filter by timezone (e.g., America/New_York)", example = "timezone")
          @RequestParam(required = false)
          String timezone,
      @Parameter(
              description = "Filter by parent company ID to get all child companies",
              example = "parent_company_id")
          @RequestParam(required = false)
          String parentCompanyId,
      @Parameter(
              description = "Filter by company ID(s) - comma-separated UUIDs for multiple",
              example = "company_id")
          @RequestParam(required = false)
          String companyId,
      @Parameter(
              description =
                  "Comma-separated additional fields: domain,seat_id,external_id,country_code,company_type,logo_url,currency_code,timezone",
              example = "domain,seat_id")
          @RequestParam(required = false)
          String fields) {

    try {
      String token = securityContextService.getBearerToken();

      // Validate limit (max 100)
      if (limit != null && limit > 100) {
        limit = 100;
      }

      CompanyLookupResponseDTO.CompanyLookupListResponse response =
          iamCompanyApiClient.companyLookup(
              token,
              limit,
              offset,
              search,
              company_type,
              country,
              domain,
              notification_email,
              currencyCode,
              timezone,
              parentCompanyId,
              companyId,
              fields);

      return ApiResponse.success(response.getData(), response.getMeta(), response.getMessage());
    } catch (AuthenticationException e) {
      log.error("Authentication error in company lookup: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error in company lookup", e);
      throw new AuthenticationException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Failed to perform company lookup: " + e.getMessage());
    }
  }
}
