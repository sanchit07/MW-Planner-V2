package com.mw.planner.controller;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.*;
import com.mw.planner.enums.PerformanceSummaryType;
import com.mw.planner.enums.SalesPerformanceShowBy;
import com.mw.planner.service.DashboardService;
import com.mw.planner.service.UserService;
import com.mw.planner.service.iam.IamCompanyApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard Management", description = "Dashboard operations for Campaign Overview")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;
  private final UserService userService;
  private final IamCompanyApiClient iamCompanyApiClient;

  @GetMapping("/campaign-overview-by-status")
  @Operation(
      summary = "Get campaign statistics for Campaign Overview by Status",
      description =
          "Returns comprehensive campaign statistics including total campaigns and counts by status. "
              + "Filters by the date range: only campaigns that overlap the range [startDate, endDate] are included.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign statistics retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Success",
                            value =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "totalCampaigns": 42,
                                    "draftCampaigns": 3,
                                    "plannedCampaigns": 8,
                                    "reviewingCampaigns": 2,
                                    "negotiatingCampaigns": 1,
                                    "pendingCampaigns": 4,
                                    "approvedCampaigns": 7,
                                    "dealRequestedCampaigns": 1,
                                    "activeCampaigns": 9,
                                    "pauseCampaigns": 1,
                                    "completedCampaigns": 5,
                                    "rejectedCampaigns": 1,
                                    "archivedCampaigns": 0
                                  }
                                }
                                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description =
                "Invalid request (e.g. missing dates, invalid date format, startDate after endDate)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            name = "ValidationError",
                            value =
                                """
                                {
                                  "success": false,
                                  "error": {
                                    "errorCode": "MWPL_1007",
                                    "message": "Validation error",
                                    "path": "/api/v1/dashboard/campaign-overview-by-status",
                                    "details": {
                                      "parameter": "startDate",
                                      "parameterType": "LocalDate"
                                    }
                                  }
                                }
                                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<CampaignStatistics> getCampaignOverviewByStatus(
      @Parameter(
              description =
                  "Filter range start (ISO date). Includes campaigns that overlap [startDate, endDate].",
              required = true,
              example = "2026-01-01")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate startDate,
      @Parameter(
              description = "Filter range end (ISO date)",
              required = true,
              example = "2026-01-31")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) String companyId) {
    IamUserContext userContext = userService.getIamUserContext();
    if (companyId == null || companyId.isBlank()) {
      companyId = userContext.getCompanyId();
    }
    CampaignStatistics statistics =
        dashboardService.getCampaignOverviewByStatus(companyId, startDate, endDate);
    return ApiResponse.success(statistics);
  }

  @GetMapping("/performance-summary")
  @Operation(
      summary = "Get Cost and Reach Performance Summary",
      description =
          "Returns date-wise budget, total cost, remaining and/or reach, impressions for the company in the given date range. "
              + "Optional type: 'cost' (budget/totalCost/remaining only), 'reach' (reach/impressions only), or omit for both.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Budget performance summary retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description =
                "Invalid date range (startDate/endDate required, startDate must not be after endDate)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<BudgetPerformanceSummaryResponse> getPerformanceSummary(
      @Parameter(description = "Start date (ISO date)", required = true)
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date (ISO date)", required = true)
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate endDate,
      @Parameter(
              description =
                  "Optional: 'cost' (cost data only), 'reach' (reach/impressions only). Omit for both.",
              example = "cost")
          @RequestParam(required = false)
          String type,
      @Parameter(
              description = "Optional: comma-separated campaign statuses to filter by",
              example = "ACTIVE")
          @RequestParam(required = false)
          String statuses,
      @Parameter(
              description = "Company ID to filter data (optional, defaults to user's company)",
              example = "company-123")
          @RequestParam(required = false)
          String companyId) {

    if (companyId == null || companyId.isBlank()) {
      IamUserContext userContext = userService.getIamUserContext();
      companyId = userContext.getCompanyId();
    }

    CompanyLookupResponseDTO lookupResponseDTO =
        iamCompanyApiClient.getCompanyCurrencyCodeWithCompanyId(companyId);

    String currencyCode =
        Optional.ofNullable(lookupResponseDTO)
            .map(CompanyLookupResponseDTO::getCurrencyCode)
            .filter(code -> !code.isBlank())
            .orElse("USD");

    PerformanceSummaryType performanceType = PerformanceSummaryType.fromString(type);

    List<Campaign.Status> statusFilter = null;
    if (statuses != null && !statuses.trim().isEmpty()) {
      statusFilter =
          Arrays.stream(statuses.split(","))
              .map(String::trim)
              .map(Campaign.Status::valueOf)
              .toList();
    }

    BudgetPerformanceSummaryResponse summary =
        dashboardService.getPerformanceSummary(
            companyId, currencyCode, startDate, endDate, performanceType, statusFilter);
    return ApiResponse.success(summary);
  }

  @GetMapping("/creative-status-tracker")
  @Operation(
      summary = "Get the Creative Status Tracker",
      description =
          "Real aggregation over line items on Approved/Active campaigns and their creative "
              + "assignments, grouped by creative format, replacing the previous mock endpoint.")
  public ApiResponse<com.mw.planner.dto.creative.CreativeStatusTrackerDTO>
      getCreativeStatusTracker() {
    return ApiResponse.success(
        dashboardService.getCreativeStatusTracker(userService.getActingCompanyId()));
  }

  @GetMapping("/creative-tier1-summary")
  @Operation(
      summary = "Get the Tier 1 creative approval summary",
      description =
          "Counts of the acting company's creative library by Tier 1 status "
              + "(Processing/Accepted/Inadequate), plus per-format acceptance readiness.")
  public ApiResponse<com.mw.planner.dto.creative.CreativeTier1SummaryDTO>
      getCreativeTier1Summary() {
    return ApiResponse.success(
        dashboardService.getCreativeTier1Summary(userService.getActingCompanyId()));
  }

  @GetMapping("/widgets")
  @Operation(
      summary = "Get available dashboard widgets",
      description =
          "Returns widgets visibility config for the logged-in user (userId + companyId). "
              + "If not configured yet, returns defaults based on current company type.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Widget configuration retrieved successfully")
      })
  public ApiResponse<List<DashboardWidgetConfigItem>> getWidgets() {
    IamUserContext userContext = userService.getIamUserContext();
    return ApiResponse.success(dashboardService.getAvailableWidgets(userContext));
  }

  @PutMapping("/widgets")
  @Operation(
      summary = "Update dashboard widgets",
      description =
          "Upserts widgets visibility config for the logged-in user (userId + companyId). "
              + "Returns the updated configuration.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Widget configuration updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid widget configuration")
      })
  public ApiResponse<List<DashboardWidgetConfigItem>> updateWidgets(
      @Valid @RequestBody List<DashboardWidgetConfigItem> widgets) {
    IamUserContext userContext = userService.getIamUserContext();
    return ApiResponse.success(dashboardService.upsertWidgets(userContext, widgets));
  }

  @GetMapping("/campaign-performance")
  @Operation(
      summary = "Get top campaigns by total cost with secondary sort",
      description =
          "Calculates total cost for all campaigns in the date range, selects the top N by total cost "
              + "(default 5), then sorts only those top N by: Impressions, Reach, Revenue (total cost), SOV. "
              + "Response includes name, start/end date, "
              + "brand name, impressions, reach, total cost, SOV, status.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign summaries returned successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            name = "Success",
                            value =
                                """
                                {
                                  "success": true,
                                  "data": [
                                    {
                                      "id": "campaign123",
                                      "name": "Q1 Launch Campaign",
                                      "brandName": "Nike",
                                      "status": "active",
                                      "startDate": "2026-01-01",
                                      "endDate": "2026-01-31",
                                      "estimatedImpression": 1000000,
                                      "estimatedReach": 50000,
                                      "totalCost": 12000.5,
                                      "sov": 15.5
                                    }
                                  ]
                                }
                                """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request (e.g. missing dates or startDate after endDate)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            name = "ValidationError",
                            value =
                                """
                                {
                                  "success": false,
                                  "error": {
                                    "errorCode": "MWPL_1007",
                                    "message": "Validation error",
                                    "path": "/api/v1/dashboard/campaign-performance",
                                    "details": {
                                      "parameter": "endDate",
                                      "parameterType": "LocalDate"
                                    }
                                  }
                                }
                                """)))
      })
  public ApiResponse<List<CampaignFilterResponseDTO>> getCampaignPerformanceByTotalCost(
      @Parameter(
              description = "Start date (inclusive), ISO format.",
              required = true,
              example = "2026-01-01")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate startDate,
      @Parameter(
              description = "End date (inclusive), ISO format.",
              required = true,
              example = "2026-01-31")
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate endDate,
      @Parameter(description = "Filter by campaign status", example = "APPROVED")
          @RequestParam(required = false)
          Campaign.Status status,
      @Parameter(description = "Sort field (totalCost)", example = "totalCost")
          @RequestParam(required = false, defaultValue = "totalCost")
          String sortBy,
      @Parameter(description = "Sort direction: asc or desc", example = "desc")
          @RequestParam(required = false, defaultValue = "desc")
          String sortDir,
      @Parameter(description = "Maximum number of campaigns to return (1-500)", example = "5")
          @RequestParam(required = false, defaultValue = "5")
          int limit,
      @Parameter(
              description = "Company ID to filter campaigns (optional, defaults to user's company)")
          @RequestParam(required = false)
          String companyId) {
    int validLimit = Math.max(1, Math.min(500, limit));
    // Data scope follows the acting (switched) company; an explicit companyId override
    // must pass the central acting-company check (acting company or global admin only).
    String scopedCompanyId =
        companyId != null && !companyId.isBlank() ? companyId : userService.getActingCompanyId();
    if (scopedCompanyId != null) {
      userService.assertCanActForCompany(scopedCompanyId);
    }
    CampaignSummaryRequestDTO request =
        CampaignSummaryRequestDTO.builder()
            .startDate(startDate)
            .endDate(endDate)
            .statuses(status != null ? List.of(status) : null)
            .sortBy(sortBy != null ? sortBy : "totalCost")
            .sortDir(sortDir != null ? sortDir : "desc")
            .limit(validLimit)
            .companyId(scopedCompanyId)
            .build();
    List<CampaignFilterResponseDTO> summaries =
        dashboardService.getCampaignPerformanceByTotalCost(request);
    return ApiResponse.success(summaries);
  }

  @GetMapping("/sales-performance-summary")
  @Operation(
      summary = "Sales Performance Summary",
      description =
          "Returns Sales Performance Summary for the requested date range, grouped by showBy. "
              + "showBy options return grouped summaries. Supports sortBy and sortDir.")
  public ApiResponse<Object> getSalesPerformanceSummary(
      @Parameter(description = "Start date (inclusive), ISO format.", required = true)
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate startDate,
      @Parameter(description = "End date (inclusive), ISO format.", required = true)
          @RequestParam
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate endDate,
      @Parameter(description = "Group by: country, city, advertiser, agency, team")
          @RequestParam(required = false, defaultValue = "country")
          String showBy,
      @Parameter(
              description =
                  "Sort field: conversion, cost, countCampaigns, country, inventories, revenue,"
                      + " utilization, city, adPlays, impressions, name (or advertiser name / agency name), share, sov")
          @RequestParam(required = false)
          String sortBy,
      @Parameter(description = "Sort direction: asc or desc") @RequestParam(required = false)
          String sortDir,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(required = false, defaultValue = "0")
          int page,
      @Parameter(description = "Page size (1-500)", example = "10")
          @RequestParam(required = false, defaultValue = "10")
          int size) {
    Object result =
        dashboardService.getSalesPerformanceSummary(
            startDate,
            endDate,
            SalesPerformanceShowBy.fromString(showBy),
            sortBy,
            sortDir,
            page,
            size);
    return ApiResponse.success(result);
  }
}
