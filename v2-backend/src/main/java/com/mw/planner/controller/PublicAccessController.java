package com.mw.planner.controller;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.PublicAccessToken;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.CampaignInventoryFilterResponseDTO;
import com.mw.planner.dto.CampaignMediaPlanResponseDTO;
import com.mw.planner.dto.CampaignPriceSummaryResponseDTO;
import com.mw.planner.dto.CampaignResponseDTO;
import com.mw.planner.dto.CostSplitByResponseDTO;
import com.mw.planner.dto.PublicAccessTokenResponseDTO;
import com.mw.planner.dto.SelectedInventorySummaryResponseDTO;
import com.mw.planner.enums.CostSplit;
import com.mw.planner.service.CampaignInventorySchedulesService;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.PublicAccessTokenService;
import com.mw.planner.util.LocaleUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for public access endpoints. The generateToken endpoint requires authentication, while
 * the getInventories endpoint is public and uses token-based access control.
 */
@RestController
@RequestMapping("/api/v1/public-access")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Public Access",
    description =
        "Public access endpoints for campaign inventory details using token-based authentication")
public class PublicAccessController {

  private final PublicAccessTokenService publicAccessTokenService;
  private final InventoryService inventoryService;
  private final CampaignService campaignService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;

  /**
   * Generate a public access token for a campaign.
   *
   * @param campaignId Campaign ID for which public access is enabled
   * @param request HTTP request to extract domain name
   * @return Response containing the generated token ID
   */
  @PostMapping("/{campaignId}/generate-token")
  @Operation(
      summary = "Generate public access token for a campaign",
      description =
          "Creates a public access token for the specified campaign. Requires authentication.")
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token generated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<PublicAccessTokenResponseDTO> generateToken(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable @NotBlank
          String campaignId,
      HttpServletRequest request) {

    log.info("Generating public access token for campaignId: {}", campaignId);

    String tokenId = publicAccessTokenService.getOrCreatePublicAccessToken(campaignId, request);

    PublicAccessTokenResponseDTO response =
        PublicAccessTokenResponseDTO.builder().publicToken(tokenId).build();

    log.info(
        "Successfully generated public access token: {} for campaignId: {}", tokenId, campaignId);

    return ApiResponse.success(response);
  }

  /**
   * Get selected inventory details using public token.
   *
   * @param publicToken Public access token from X-PUBLIC-TOKEN header
   * @param request HTTP request to extract and validate domain name
   * @param name Optional name filter (case-insensitive partial match)
   * @param inventoryType Optional inventoryType filter
   * @param page Page number (0-based)
   * @param size Page size
   * @param sortBy Sort field
   * @param sortDir Sort direction
   * @return Page of selected campaign inventories
   */
  @GetMapping("/inventories")
  @Operation(
      summary = "Get selected inventory details using public token",
      description =
          "Retrieve selected inventory details for a campaign using a public access token. "
              + "The token must be provided in the X-PUBLIC-TOKEN header. "
              + "The domain name from the request must match the domain name stored with the token. "
              + "No authentication is required for this endpoint.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory details retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid token or domain mismatch"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<CampaignInventoryFilterResponseDTO>> getInventories(
      @Parameter(
              description = "Public access token",
              example = "6944ddd13554c15e56fbf2a6",
              required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request,
      @Parameter(
              description = "Optional name filter (case-insensitive partial match)",
              example = "billboard")
          @RequestParam(required = false)
          String name,
      @Parameter(description = "Optional inventoryType filter", example = "CLASSIC")
          @RequestParam(required = false)
          String inventoryType,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "name") @RequestParam(defaultValue = "name")
          String sortBy,
      @Parameter(description = "Sort direction", example = "asc")
          @RequestParam(defaultValue = "asc")
          String sortDir) {

    log.info(
        "Getting inventories with public token. Token: {}, name filter: {}, inventoryType filter: {}, page: {}, size: {}",
        publicToken.substring(0, Math.min(8, publicToken.length())) + "...",
        name,
        inventoryType,
        page,
        size);

    // Validate token and get campaign ID
    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);

    // Validate domain name matches the stored domain
    publicAccessTokenService.validateDomainName(tokenData, request);

    String campaignId = tokenData.getCampaignId();

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    // Get selected inventories using existing service method
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, name, inventoryType, pageable);

    log.info(
        "Successfully retrieved {} inventories for campaignId: {} using public token",
        result.getTotalElements(),
        campaignId);

    return ApiResponse.success(result);
  }

  @GetMapping("/media-plan")
  @Operation(
      summary = "Get campaign media plan using public token",
      description = "Retrieve the media plan for a campaign using a public access token.")
  public ApiResponse<CampaignMediaPlanResponseDTO> getMediaPlan(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting media plan for campaignId: {} using public token", campaignId);
    return ApiResponse.success(campaignService.getCampaignMediaPlanDetails(campaignId));
  }

  @GetMapping("/cost-split-by")
  @Operation(
      summary = "Get campaign cost split using public token",
      description = "Retrieve cost split breakdown for a campaign using a public access token.")
  public ApiResponse<List<CostSplitByResponseDTO>> getCostSplitBy(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      @Parameter(description = "Dimension to split costs by", required = true) @RequestParam
          CostSplit splitBy,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting cost split by {} for campaignId: {} using public token", splitBy, campaignId);
    return ApiResponse.success(
        campaignService.getCampaignCostSplitBy(campaignId, splitBy, LocaleUtil.resolve(request)));
  }

  @GetMapping("/forecast")
  @Operation(
      summary = "Get campaign forecast using public token",
      description = "Retrieve the campaign forecast using a public access token.")
  public ApiResponse<CampaignForecastDTO> getForecast(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting forecast for campaignId: {} using public token", campaignId);
    Campaign campaign = campaignService.findById(campaignId);
    return ApiResponse.success(campaignService.calculateCampaignForecast(campaign, false));
  }

  @GetMapping("/price-summary")
  @Operation(
      summary = "Get campaign price summary using public token",
      description = "Retrieve the price summary for a campaign using a public access token.")
  public ApiResponse<CampaignPriceSummaryResponseDTO> getPriceSummary(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting price summary for campaignId: {} using public token", campaignId);
    return ApiResponse.success(
        campaignInventorySchedulesService.getCampaignPriceSummary(campaignId));
  }

  @GetMapping("/campaign")
  @Operation(
      summary = "Get campaign details using public token",
      description = "Retrieve campaign information for a campaign using a public access token.")
  public ApiResponse<CampaignResponseDTO> getCampaign(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    // publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting campaign details for campaignId: {} using public token", campaignId);
    return ApiResponse.success(campaignService.getCampaignByIdForPublicAccess(campaignId));
  }

  @GetMapping("/selected-inventory/all")
  @Operation(
      summary = "Get all selected inventories for a campaign using public token",
      description =
          "Retrieve all selected inventory records for a campaign as slim summaries using a public "
              + "access token. No pagination or filtering.")
  public ApiResponse<List<SelectedInventorySummaryResponseDTO>> getAllSelectedInventories(
      @Parameter(description = "Public access token", required = true)
          @RequestHeader("X-PUBLIC-TOKEN")
          @NotBlank
          String publicToken,
      HttpServletRequest request) {

    PublicAccessToken tokenData = publicAccessTokenService.validateAndGetToken(publicToken);
    // publicAccessTokenService.validateDomainName(tokenData, request);
    String campaignId = tokenData.getCampaignId();

    log.info("Getting all selected inventories for campaignId: {} using public token", campaignId);
    return ApiResponse.success(inventoryService.getAllSelectedInventories(campaignId));
  }
}
