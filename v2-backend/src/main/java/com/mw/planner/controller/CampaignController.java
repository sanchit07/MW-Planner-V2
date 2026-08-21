package com.mw.planner.controller;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.*;
import com.mw.planner.dto.CampaignActivityResponseDTO;
import com.mw.planner.enums.CostSplit;
import com.mw.planner.service.CampaignActivityService;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.UserService;
import com.mw.planner.util.LocaleUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Campaign Management", description = "Campaign related operations")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CampaignController {

  private final CampaignService campaignService;
  private final UserService userService;
  private final CampaignActivityService campaignActivityService;

  @PostMapping
  @PreAuthorize("hasRole('planner:plans:create')")
  @Operation(
      summary = "Create a new campaign",
      description =
          "Creates a new campaign with the provided information. Campaign will be created in DRAFT status by default.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Campaign created successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"campaign123\", \"name\": \"Campaign_Sep_17_25_001\",\"description\": \"campaigning for product\", \"status\": \"DRAFT\",\"budget\": 100000,\"currency\": \"USD\",\"startDate\": \"2025-11-06\",\"endDate\": \"2025-12-06\",\"userId\": \"689ea4d4fafbc66da5044378\",\"brandId\": \"68f71ad1eff4e2cb54e01047\",\"clientType\": \"DIRECT_ADVERTISER\",\"agencyId\": \"69032fc2fb3b7c6a82c3866d\",\"companyId\": \"5ea826a860e5e930d8d6cf47\",\"countryId\": \"Malaysia\",\"goals\": {\"goalType\": \"IMPRESSIONS\",\"targetName\": \"\",\"targetValue\": 20000.0,\"typeName\": \"Impressions\"},\"targeting\": {\"demographics\": {\"age\": [\"18_24\",\"25_34\" ],\"gender\": [\"male\",\"female\" ],\"income\": [\"lower_middle\",\"middle\" ],\"interests\": [\"Food & Dining\",\"Technology\",\"Sports & Fitness\" ],\"venues\": [\"transit\",\"transit.airports\",\"transit.airports.arrivals_hall\" ],\"behavior\": [\"commuters\" ] },\"geofencing\": {\"geometries\": [ {\"name\": \"LineString 1\",\"type\": \"LineString\",\"coordinates\": [ [ 73.19900631744142, 22.304483082845422 ], [ 73.20059418517943, 22.29955964808792 ] ],\"isIncluded\": true,\"poi\": [\"house_complex\", \"establishment\"],   \"metadata\": {\"type\": \"circle\"},\"included\": true } ],\"locations\": [ {\"name\": \"Vadodara\",\"lat\": 22.299917,\"lng\": 73.201195,\"radius\": 500.0,\"address\": \"Vadodara, Gujarat, India\",\"isIncluded\": true,  \"poi\": [\"house_complex\", \"establishment\"],\"metadata\": {\"type\": \"circle\"},\"included\": true } ] },\"signals\": [\"footfallPattern\"]},\"createdAt\": \"2025-11-06T11:49:31.812\",\"updatedAt\": \"2025-11-06T12:15:07.389147715\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid campaign data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_1001\",\"message\": \"Validation failed\",\"path\": \"/api/v1/campaigns\",\"timestamp\": \"2025-11-07T11:29:56.819083\"}}")))
      })
  public ApiResponse<CampaignResponseDTO> createCampaign(
      @Valid @RequestBody CampaignRequestDTO campaignRequestDTO) {
    IamUserContext iamUserContext = userService.getIamUserContext();
    campaignRequestDTO.setUserId(iamUserContext.getId());
    if (campaignRequestDTO.getCompanyId() == null || campaignRequestDTO.getCompanyId().isBlank()) {
      // Acting company (X-Company-Id aware), so plans created while switched into a
      // secondary company are attributed to that company, never the primary.
      campaignRequestDTO.setCompanyId(userService.getActingCompanyId());
    } else {
      // Never trust a client-supplied owner company blindly: it must be a company the
      // caller may act for (acting company, membership/child company, or global admin).
      userService.assertCanActForCompany(campaignRequestDTO.getCompanyId());
    }
    CampaignResponseDTO createdCampaign = campaignService.createCampaign(campaignRequestDTO);
    return ApiResponse.success(createdCampaign);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign by ID",
      description = "Returns campaign information by campaign ID")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign found successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": { \"id\": \"campaign123\",\"name\": \"Campaign_Nov_07_25_001\",\"description\": \"\",\"status\": \"DRAFT\",\"currency\": \"USD\",\"startDate\": \"2025-11-07\",\"endDate\": \"2025-12-07\",\"userId\": \"689ea4d4fafbc66da5044378\",\"brandId\": \"6902198cfb3b7c6a82c38594\",\"clientType\": \"AGENCY\",\"agencyId\": \"6904b60f7baf60072a036c76\",\"companyId\": \"5ea826a860e5e930d8d6cf47\",\"countryId\": \"Singapore\",\"createdAt\": \"2025-11-07T04:46:08.128\",\"updatedAt\": \"2025-11-07T04:46:14.849\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_3001\",\"message\": \"Campaign not found\",\"path\": \"/api/v1/campaigns/campaign123\",\"timestamp\": \"2025-11-07T10:59:19.1375777\"}}")))
      })
  public ApiResponse<CampaignResponseDTO> getCampaignById(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id) {
    CampaignResponseDTO campaign = campaignService.getCampaignById(id);
    return ApiResponse.success(campaign);
  }

  @GetMapping("/name/{name}")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign by name",
      description = "Returns campaign information by campaign name (case-insensitive)")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"campaign123\", \"name\": \"Campaign_Sep_17_25_001\",\"description\": \"campaigning for product\", \"status\": \"DRAFT\",\"budget\": 100000,\"currency\": \"USD\",\"startDate\": \"2025-11-06\",\"endDate\": \"2025-12-06\",\"userId\": \"689ea4d4fafbc66da5044378\",\"brandId\": \"68f71ad1eff4e2cb54e01047\",\"clientType\": \"DIRECT_ADVERTISER\",\"agencyId\": \"69032fc2fb3b7c6a82c3866d\",\"companyId\": \"5ea826a860e5e930d8d6cf47\",\"countryId\": \"Malaysia\",\"goals\": {\"goalType\": \"IMPRESSIONS\",\"targetName\": \"\",\"targetValue\": 20000.0,\"typeName\": \"Impressions\"},\"targeting\": {\"demographics\": {\"age\": [\"18_24\",\"25_34\" ],\"gender\": [\"male\",\"female\" ],\"income\": [\"lower_middle\",\"middle\" ],\"interests\": [\"Food & Dining\",\"Technology\",\"Sports & Fitness\" ],\"venues\": [\"transit\",\"transit.airports\",\"transit.airports.arrivals_hall\" ],\"behavior\": [\"commuters\" ] },\"geofencing\": {\"geometries\": [ {\"name\": \"LineString 1\",\"type\": \"LineString\",\"coordinates\": [ [ 73.19900631744142, 22.304483082845422 ], [ 73.20059418517943, 22.29955964808792 ] ],\"isIncluded\": true,\"poi\": [\"house_complex\", \"establishment\"],   \"metadata\": {\"type\": \"circle\"},\"included\": true } ],\"locations\": [ {\"name\": \"Vadodara\",\"lat\": 22.299917,\"lng\": 73.201195,\"radius\": 500.0,\"address\": \"Vadodara, Gujarat, India\",\"isIncluded\": true,  \"poi\": [\"house_complex\", \"establishment\"],\"metadata\": {\"type\": \"circle\"},\"included\": true } ] },\"signals\": [\"footfallPattern\"]},\"createdAt\": \"2025-11-06T11:49:31.812\",\"updatedAt\": \"2025-11-06T12:15:07.389147715\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_3001\",\"message\": \"Campaign not found\",\"path\": \"/api/v1/campaigns/name/Campaign_Sep_17_25_00\",\"timestamp\": \"2025-11-07T10:59:19.1375777\"}}")))
      })
  public ApiResponse<CampaignResponseDTO> getCampaignByName(
      @Parameter(description = "Campaign name", example = "Campaign_Sep_17_25_001") @PathVariable
          String name) {
    CampaignResponseDTO campaign = campaignService.getCampaignByName(name);
    return ApiResponse.success(campaign);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(
      summary = "Update campaign",
      description = "Updates campaign information by campaign ID")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"campaign123\", \"name\": \"Campaign_Sep_17_25_001\",\"description\": \"campaigning for product\", \"status\": \"DRAFT\",\"budget\": 100000,\"currency\": \"USD\",\"startDate\": \"2025-11-06\",\"endDate\": \"2025-12-06\",\"userId\": \"689ea4d4fafbc66da5044378\",\"brandId\": \"68f71ad1eff4e2cb54e01047\",\"clientType\": \"DIRECT_ADVERTISER\",\"agencyId\": \"69032fc2fb3b7c6a82c3866d\",\"companyId\": \"5ea826a860e5e930d8d6cf47\",\"countryId\": \"Malaysia\",\"goals\": {\"goalType\": \"IMPRESSIONS\",\"targetName\": \"\",\"targetValue\": 20000.0,\"typeName\": \"Impressions\"},\"targeting\": {\"demographics\": {\"age\": [\"18_24\",\"25_34\" ],\"gender\": [\"male\",\"female\" ],\"income\": [\"lower_middle\",\"middle\" ],\"interests\": [\"Food & Dining\",\"Technology\",\"Sports & Fitness\" ],\"venues\": [\"transit\",\"transit.airports\",\"transit.airports.arrivals_hall\" ],\"behavior\": [\"commuters\" ] },\"geofencing\": {\"geometries\": [ {\"name\": \"LineString 1\",\"type\": \"LineString\",\"coordinates\": [ [ 73.19900631744142, 22.304483082845422 ], [ 73.20059418517943, 22.29955964808792 ] ],\"isIncluded\": true,\"poi\": [\"house_complex\", \"establishment\"],   \"metadata\": {\"type\": \"circle\"},\"included\": true } ],\"locations\": [ {\"name\": \"Vadodara\",\"lat\": 22.299917,\"lng\": 73.201195,\"radius\": 500.0,\"address\": \"Vadodara, Gujarat, India\",\"isIncluded\": true,  \"poi\": [\"house_complex\", \"establishment\"],\"metadata\": {\"type\": \"circle\"},\"included\": true } ] },\"signals\": [\"footfallPattern\"]},\"createdAt\": \"2025-11-06T11:49:31.812\",\"updatedAt\": \"2025-11-06T12:15:07.389147715\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_3001\",\"message\": \"Campaign not found\",\"path\": \"/api/v1/campaigns/campaign123\",\"timestamp\": \"2025-11-07T12:40:52.0644009\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid campaign data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_1001\",\"message\": \"Validation failed\",\"path\": \"/api/v1/campaigns/campaign123\",\"timestamp\": \"2025-11-07T12:40:52.0644009\"}}")))
      })
  public ApiResponse<CampaignResponseDTO> updateCampaign(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id,
      @Valid @RequestBody CampaignRequestDTO campaignRequestDTO) {
    CampaignResponseDTO updatedCampaign = campaignService.updateCampaign(id, campaignRequestDTO);
    return ApiResponse.success(updatedCampaign);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('planner:plans:delete')")
  @Operation(summary = "Delete campaign", description = "Deletes a campaign by campaign ID")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign deleted successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples = @ExampleObject(value = "{\"success\": true}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_3001\",\"message\": \"Campaign not found\",\"path\": \"/api/v1/campaigns/campaign123\",\"timestamp\": \"2025-11-07T10:59:19.1375777\"}}")))
      })
  public ApiResponse<Void> deleteCampaign(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id) {
    campaignService.deleteCampaign(id);
    return ApiResponse.success(null);
  }

  @GetMapping
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaigns with optional filters",
      description =
          "Returns paginated list of campaigns with optional filtering by name, status, goals, user IDs and date range")
  public ApiResponse<Page<CampaignFilterResponseDTO>> getCampaignsWithFilters(
      @Parameter(
              description = "Campaign name contains this string (case-insensitive)",
              example = "Summer")
          @RequestParam(required = false)
          String nameContains,
      @Parameter(description = "List of campaign statuses to filter by", example = "DRAFT,APPROVED")
          @RequestParam(required = false)
          String statuses,
      @Parameter(description = "List of goal types to filter by", example = "IMPRESSIONS,REACH")
          @RequestParam(required = false)
          String goalTypes,
      @Parameter(description = "List of user IDs to filter by", example = "user1,user2")
          @RequestParam(required = false)
          String userIds,
      @Parameter(
              description = "Start date for date range filter (inclusive)",
              example = "2024-01-01")
          @RequestParam(required = false)
          String startDateFrom,
      @Parameter(description = "End date for date range filter (inclusive)", example = "2024-12-31")
          @RequestParam(required = false)
          String startDateTo,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "updatedAt")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir,
      @Parameter(
              description = "Company ID to filter campaigns (optional, defaults to user's company)",
              example = "company-123")
          @RequestParam(required = false)
          String companyId) {

    if (companyId != null && !companyId.isBlank()) {
      // An explicit company filter must be one the caller may act for.
      userService.assertCanActForCompany(companyId);
    }

    // Build filter DTO with date validation
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder()
            .nameContains(nameContains)
            .companyId(
                companyId != null && !companyId.isBlank()
                    ? companyId
                    : userService.getActingCompanyId())
            .startDateFrom(parseDate(startDateFrom))
            .startDateTo(parseDate(startDateTo))
            .build();

    // Parse comma-separated lists
    if (statuses != null && !statuses.trim().isEmpty()) {
      filter.setStatuses(
          Arrays.stream(statuses.split(","))
              .map(String::trim)
              .map(Campaign.Status::valueOf)
              .toList());
    }

    if (goalTypes != null && !goalTypes.trim().isEmpty()) {
      filter.setGoalTypes(
          Arrays.stream(goalTypes.split(","))
              .map(String::trim)
              .map(Campaign.Goals.GoalType::valueOf)
              .toList());
    }

    if (userIds != null && !userIds.trim().isEmpty()) {
      filter.setUserIds(Arrays.stream(userIds.split(",")).map(String::trim).toList());
    }

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);
    Page<CampaignFilterResponseDTO> campaigns =
        campaignService.getCampaignsWithFilters(filter, pageable);
    return ApiResponse.success(campaigns);
  }

  @PostMapping("/bulk-actions")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(
      summary = "Perform bulk actions on campaigns",
      description = "Perform bulk operations (duplicate, archive, delete) on multiple campaigns")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Bulk action completed successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"totalProcessed\": 3, \"successCount\": 2, \"failureCount\": 1, \"successfulCampaignIds\": [\"campaign1\", \"campaign2\"], \"failedCampaignIds\": [\"campaign3\"], \"errorMessages\": [\"Campaign campaign3: Campaign not found\"], \"newCampaignIds\": [\"newCampaign1\", \"newCampaign2\"]}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CampaignBulkActionResponseDTO> performBulkAction(
      @Valid @RequestBody CampaignBulkActionRequestDTO request) {
    IamUserContext userContext = userService.getIamUserContext();
    CampaignBulkActionResponseDTO result = campaignService.performBulkAction(request, userContext);
    return ApiResponse.success(result);
  }

  @PatchMapping("/{id}/autosave")
  @PreAuthorize("hasRole('planner:plans:update')")
  @Operation(
      summary = "Autosave campaign draft",
      description =
          "Saves campaign data as a draft without validation. Only updates provided fields and maintains DRAFT status.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign draft autosaved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"campaign123\", \"name\": \"Campaign_Sep_17_25_001\",\"description\": \"campaigning for product\", \"status\": \"DRAFT\",\"budget\": 100000,\"currency\": \"USD\",\"startDate\": \"2025-11-06\",\"endDate\": \"2025-12-06\",\"userId\": \"689ea4d4fafbc66da5044378\",\"brandId\": \"68f71ad1eff4e2cb54e01047\",\"clientType\": \"DIRECT_ADVERTISER\",\"agencyId\": \"69032fc2fb3b7c6a82c3866d\",\"companyId\": \"5ea826a860e5e930d8d6cf47\",\"countryId\": \"Malaysia\",\"goals\": {\"goalType\": \"IMPRESSIONS\",\"targetName\": \"\",\"targetValue\": 20000.0,\"typeName\": \"Impressions\"},\"targeting\": {\"demographics\": {\"age\": [\"18_24\",\"25_34\" ],\"gender\": [\"male\",\"female\" ],\"income\": [\"lower_middle\",\"middle\" ],\"interests\": [\"Food & Dining\",\"Technology\",\"Sports & Fitness\" ],\"venues\": [\"transit\",\"transit.airports\",\"transit.airports.arrivals_hall\" ],\"behavior\": [\"commuters\" ] },\"geofencing\": {\"geometries\": [ {\"name\": \"LineString 1\",\"type\": \"LineString\",\"coordinates\": [ [ 73.19900631744142, 22.304483082845422 ], [ 73.20059418517943, 22.29955964808792 ] ],\"isIncluded\": true,\"poi\": [\"house_complex\", \"establishment\"],   \"metadata\": {\"type\": \"circle\"},\"included\": true } ],\"locations\": [ {\"name\": \"Vadodara\",\"lat\": 22.299917,\"lng\": 73.201195,\"radius\": 500.0,\"address\": \"Vadodara, Gujarat, India\",\"isIncluded\": true,  \"poi\": [\"house_complex\", \"establishment\"],\"metadata\": {\"type\": \"circle\"},\"included\": true } ] },\"signals\": [\"footfallPattern\"]},\"createdAt\": \"2025-11-06T11:49:31.812\",\"updatedAt\": \"2025-11-06T12:15:07.389147715\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_3001\",\"message\": \"Campaign not found\",\"path\": \"/api/v1/campaigns/campaign123/autosave\",\"timestamp\": \"2025-11-07T12:40:52.0644009\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid autosave data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false, \"error\": { \"errorCode\": \"ERR_1001\",\"message\": \"Validation failed\",\"path\": \"/api/v1/campaigns/campaign123/autosave\",\"timestamp\": \"2025-11-07T12:40:52.0644009\"}}")))
      })
  public ApiResponse<CampaignResponseDTO> autosaveCampaign(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id,
      @Valid @RequestBody CampaignAutosaveRequestDTO autosaveRequestDTO) {
    CampaignResponseDTO autosavedCampaign =
        campaignService.autosaveCampaign(id, autosaveRequestDTO);
    return ApiResponse.success(autosavedCampaign);
  }

  @GetMapping("/{id}/media-plan")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign Media Plan details",
      description = "Returns detailed Media Plan information for a specific campaign")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Media Plan details fetched successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CampaignMediaPlanResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CampaignMediaPlanResponseDTO> getCampaignMediaPlanDetails(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id) {
    CampaignMediaPlanResponseDTO mediaPlanDetails = campaignService.getCampaignMediaPlanDetails(id);
    return ApiResponse.success(mediaPlanDetails);
  }

  /**
   * Parse date string with validation
   *
   * @param dateString the date string to parse
   * @return LocalDate or null if dateString is null/empty
   * @throws IllegalArgumentException if dateString is invalid
   */
  private LocalDate parseDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(dateString);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid date format: " + dateString);
    }
  }

  @GetMapping("/{id}/view-campaign")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign view details",
      description = "Returns detailed view information for a specific campaign")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign view details fetched successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CampaignViewResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CampaignViewResponseDTO> getCampaignViewDetails(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id) {
    CampaignViewResponseDTO viewDetails = campaignService.getCampaignViewDetails(id);
    return ApiResponse.success(viewDetails);
  }

  @GetMapping("/{id}/cost-split-by")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign cost split by specified dimension",
      description = "Returns cost breakdown of a campaign split by the specified dimension")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign cost split fetched successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": [ { \"splitByValue\": \"Location A\", \"cost\": 5000.0 }, { \"splitByValue\": \"Location B\", \"cost\": 3000.0 } ]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<List<CostSplitByResponseDTO>> getCampaignCostSplitBy(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable String id,
      @RequestParam CostSplit splitBy,
      HttpServletRequest request) {
    List<CostSplitByResponseDTO> costSplitBy =
        campaignService.getCampaignCostSplitBy(id, splitBy, LocaleUtil.resolve(request));
    return ApiResponse.success(costSplitBy);
  }

  @GetMapping("/{campaignId}/history")
  @PreAuthorize("hasRole('planner:plans:read')")
  @Operation(
      summary = "Get campaign history with pagination",
      description =
          "Returns paginated history entries for a campaign, ordered by updatedAt descending (newest first) by default. "
              + "Includes autosave events, approval workflow actions, submit for review, and cron job status updates. "
              + "Supports pagination to improve performance for campaigns with large history.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign history retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<Page<CampaignActivityResponseDTO>> getCampaignHistory(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "updatedAt")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir,
      HttpServletRequest request) {
    // Validate campaign exists AND the acting company participates in it (central tenant
    // guard inside findByIdForCurrentMode; unrelated companies get 404).
    campaignService.findByIdForCurrentMode(campaignId);

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Create sort and pageable
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<CampaignActivityResponseDTO> history =
        campaignActivityService.getCampaignHistory(
            campaignId, LocaleUtil.resolve(request), pageable);
    return ApiResponse.success(history);
  }
}
