package com.mw.planner.controller;

import com.mw.planner.dto.AcceptInventoryPricesRequestDTO;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.ApplyAdjustmentRequestDTO;
import com.mw.planner.dto.BulkCustomFeeRequestDTO;
import com.mw.planner.dto.CampaignPriceSummaryResponseDTO;
import com.mw.planner.dto.CampaignSchedulePriceFilterDTO;
import com.mw.planner.dto.CampaignSchedulePriceResponseDTO;
import com.mw.planner.dto.CustomFeeRequestDTO;
import com.mw.planner.dto.CustomFeeResponseDTO;
import com.mw.planner.dto.PriceHistoryResponseDTO;
import com.mw.planner.dto.UpdateDiscountRequestDTO;
import com.mw.planner.exception.customfee.CustomFeeValidationException;
import com.mw.planner.service.CampaignInventorySchedulesService;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.CustomFeeService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/price-management")
@Tag(
    name = "Price Management",
    description =
        "Operations for managing custom fees, discounts and approval flow for campaigns and companies")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PriceManagementController {

  private final CustomFeeService customFeeService;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final CampaignService campaignService;
  private final MessageService messageService;
  private final UserService userService;

  /**
   * Test Mode partition guard for campaign-scoped price-management data: when a campaignId is
   * present, the campaign must exist in the caller's data mode (cross-mode behaves as not-found).
   * Company-level fees (no campaignId) are unpartitioned.
   */
  private void assertCampaignInCallerMode(String campaignId) {
    if (campaignId != null && !campaignId.isBlank()) {
      campaignService.findByIdForCurrentMode(campaignId);
    }
  }

  @PostMapping("/custom-fees")
  @Operation(
      summary = "Create a new custom fee",
      description =
          "Creates a new custom fee. companyId is derived from the logged-in user's active company. "
              + "If campaignId is provided, the fee is for that campaign; if null/omitted, the fee is for the company.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Custom fee created successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"fee_123456\", \"name\": \"Service Fee\", \"type\": \"PERCENTAGE\", \"value\": 10.5, \"companyId\": \"company_123\", \"campaignId\": \"campaign_123\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid custom fee data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Custom fee already exists",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CustomFeeResponseDTO> createCustomFee(
      @Valid @RequestBody CustomFeeRequestDTO customFeeRequestDTO) {
    log.info("Creating custom fee: {}", customFeeRequestDTO.getName());
    assertCampaignInCallerMode(customFeeRequestDTO.getCampaignId());
    CustomFeeResponseDTO createdCustomFee = customFeeService.createCustomFee(customFeeRequestDTO);
    return ApiResponse.success(createdCustomFee);
  }

  @GetMapping("/custom-fees/{id}")
  @Operation(
      summary = "Get custom fee by ID",
      description = "Returns detailed custom fee information by custom fee ID.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Custom fee found successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"fee_123456\", \"name\": \"Service Fee\", \"type\": \"PERCENTAGE\", \"value\": 10.5, \"companyId\": \"company_123\", \"campaignId\": \"campaign_123\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Custom fee not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CustomFeeResponseDTO> getCustomFeeById(
      @Parameter(description = "Custom fee ID", example = "fee_123456") @PathVariable String id) {
    log.info("Getting custom fee by ID: {}", id);
    CustomFeeResponseDTO fee = customFeeService.getCustomFeeById(id);
    // Campaign-level fees are partitioned with their campaign (cross-mode behaves as not-found).
    assertCampaignInCallerMode(fee.getCampaignId());
    return ApiResponse.success(fee);
  }

  @GetMapping("/custom-fees")
  @Operation(
      summary = "Get custom fees for logged-in user's company",
      description =
          "Returns all custom fees for the logged-in user's active company. "
              + "companyId is derived from the logged-in user. "
              + "If campaignId is provided, returns campaign-level fees; if omitted, returns company-level fees.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Custom fees retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": [{\"id\": \"fee_123456\", \"name\": \"Service Fee\", \"type\": \"PERCENTAGE\", \"value\": 10.5}]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Unable to determine primary company for the current user",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
      })
  public ApiResponse<List<CustomFeeResponseDTO>> getCustomFees(
      @Parameter(
              description = "Campaign ID (optional; omit for company-level fees)",
              example = "campaign_123")
          @RequestParam(required = false)
          String campaignId) {
    String companyId = userService.getActingCompanyId();
    if (companyId == null || companyId.trim().isEmpty()) {
      throw new CustomFeeValidationException(
          "Unable to determine primary company ID for the current user");
    }
    log.info("Getting custom fees by companyId={}, campaignId={}", companyId, campaignId);
    assertCampaignInCallerMode(campaignId);
    return ApiResponse.success(
        customFeeService.getCustomFeesByCompanyAndCampaign(companyId, campaignId));
  }

  @PutMapping("/custom-fees/{id}")
  @Operation(
      summary = "Update custom fee",
      description =
          "Updates custom fee information. Company and campaign scope are not changed on update.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Custom fee updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"fee_123456\", \"name\": \"Updated Service Fee\", \"updatedAt\": \"2024-01-15 14:45:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Custom fee not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid custom fee data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Custom fee already exists",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CustomFeeResponseDTO> updateCustomFee(
      @Parameter(description = "Custom fee ID", example = "fee_123456") @PathVariable String id,
      @Valid @RequestBody CustomFeeRequestDTO customFeeRequestDTO) {
    log.info("Updating custom fee with ID: {}", id);
    // Guard both the existing fee's campaign and any campaign in the request payload.
    assertCampaignInCallerMode(customFeeService.getCustomFeeById(id).getCampaignId());
    assertCampaignInCallerMode(customFeeRequestDTO.getCampaignId());
    CustomFeeResponseDTO updatedCustomFee =
        customFeeService.updateCustomFee(id, customFeeRequestDTO);
    return ApiResponse.success(updatedCustomFee);
  }

  @PutMapping("/campaign-inventory-schedules/{id}/update-discount")
  @Operation(
      summary = "Update discount on schedules of a CampaignInventorySchedules",
      description =
          "Updates discount on schedules of a CampaignInventorySchedules based on a proposed price. "
              + "If scheduleId is provided, discount is applied only to that specific schedule. "
              + "If scheduleId is not provided, calculates the total discount from current price and proposed price, "
              + "then distributes it proportionally across all schedules based on their price weightage. "
              + "The discount is stored as a percentage on each schedule.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Discount updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": \"Discount updated successfully\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description =
                "Invalid request data (e.g., proposed price must be positive and less than current price)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "CampaignInventorySchedules or schedule not found",
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
  public ApiResponse<String> updateDiscount(
      @Parameter(
              description = "CampaignInventorySchedules ID",
              example = "campaign_inventory_schedule_123")
          @PathVariable
          String id,
      @Valid @RequestBody UpdateDiscountRequestDTO request) {
    log.info(
        "Updating discount for CampaignInventorySchedules: {} with proposed price: {} and scheduleId: {}",
        id,
        request.getProposedPrice(),
        request.getScheduleId());
    campaignInventorySchedulesService.updateDiscountByProposedPrice(
        id, request.getProposedPrice(), request.getScheduleId());
    return ApiResponse.success("Discount updated successfully");
  }

  @PostMapping("/campaigns/{campaignId}/schedules/apply-discount-or-bonus")
  @Operation(
      summary = "Apply discount or bonus to selected campaign schedules",
      description =
          "Apply discount or bonus to selected schedules for a campaign. "
              + "For DISCOUNT: calculates proposedPrice based on discountType and value applied to actualPrice, "
              + "sets discount details, and adds history entry. "
              + "For BONUS: sets bonusType with provided bonus description.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Discount or bonus applied successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or schedules not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> applyDiscountOrBonusToSchedules(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody ApplyAdjustmentRequestDTO request) {
    // Validate campaign exists in the caller's Test Mode partition (cross-mode = 404)
    campaignService.findByIdForCurrentMode(campaignId);
    campaignInventorySchedulesService.applyAdjustment(campaignId, request);
    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.adjustment_applied", locale);
    return ApiResponse.success(successMessage);
  }

  @GetMapping("/campaigns/{campaignId}/price-summary")
  @Operation(
      summary = "Get campaign price summary",
      description =
          "Returns aggregated price summary for all schedules in a campaign. "
              + "Includes current price, proposed price, change in price, change in percentage, "
              + "media cost, discounted media cost, standard fees, and visible custom fees.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign price summary retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"currentPrice\": 10000.0, \"proposedPrice\": 9000.0, \"changeInPrice\": 1000.0, \"changeInPercentage\": 10.0, \"mediaCost\": 8000.0, \"discountedMediaCost\": 7200.0, \"standardFees\": 1800.0, \"customFees\": []}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found",
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
  public ApiResponse<CampaignPriceSummaryResponseDTO> getCampaignPriceSummary(
      @Parameter(description = "Campaign ID", example = "campaign_123") @PathVariable
          String campaignId) {
    log.info("Getting campaign price summary for campaignId: {}", campaignId);
    CampaignPriceSummaryResponseDTO summary =
        campaignInventorySchedulesService.getCampaignPriceSummary(campaignId);
    return ApiResponse.success(summary);
  }

  @PostMapping("/custom-fees/bulk")
  @Operation(
      summary = "Bulk create or update custom fees",
      description =
          "Creates new custom fees or updates existing ones based on the provided list. "
              + "If id is provided and not null, the custom fee will be updated. "
              + "If id is null, a new custom fee will be created. "
              + "All provided IDs are validated to exist before any database operations.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Custom fees created/updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": [{\"id\": \"fee_123456\", \"name\": \"Service Fee\", \"type\": \"PERCENTAGE\", \"value\": 10.5}]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid custom fee data or empty list",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "One or more custom fee IDs not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Custom fee already exists (for create operations)",
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
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<List<CustomFeeResponseDTO>> bulkCreateOrUpdateCustomFees(
      @Valid @RequestBody List<BulkCustomFeeRequestDTO> bulkCustomFeeRequestDTOs) {
    log.info("Bulk creating/updating {} custom fees", bulkCustomFeeRequestDTOs.size());
    bulkCustomFeeRequestDTOs.stream()
        .map(BulkCustomFeeRequestDTO::getCampaignId)
        .distinct()
        .forEach(this::assertCampaignInCallerMode);
    // For updates, also guard the EXISTING fee's campaign — otherwise a cross-mode fee could be
    // mutated by submitting its id with a null/own-mode campaignId in the payload.
    bulkCustomFeeRequestDTOs.stream()
        .map(BulkCustomFeeRequestDTO::getId)
        .filter(feeId -> feeId != null && !feeId.isBlank())
        .distinct()
        .forEach(
            feeId ->
                assertCampaignInCallerMode(
                    customFeeService.getCustomFeeById(feeId).getCampaignId()));
    List<CustomFeeResponseDTO> results =
        customFeeService.bulkCreateOrUpdateCustomFees(bulkCustomFeeRequestDTOs);
    return ApiResponse.success(results);
  }

  /**
   * Get campaign schedule prices with filtering and pagination. Fetches approved campaign inventory
   * schedules with comprehensive pricing information.
   *
   * @param campaignId Campaign ID (required) as path variable
   * @param filter Request body containing filters and pagination
   * @return Page of campaign schedule price responses
   */
  @PostMapping("/campaigns/{campaignId}/schedule-prices")
  @Operation(
      summary = "Get campaign schedule prices with filtering and pagination",
      description =
          "Fetch approved campaign inventory schedules with comprehensive pricing information, "
              + "including inventory details, schedules, pricing calculations, and forecast data")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign schedule prices retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<CampaignSchedulePriceResponseDTO>> getCampaignSchedulePrices(
      @Parameter(description = "Campaign ID (required)", example = "campaign123", required = true)
          @PathVariable
          String campaignId,
      @Parameter(description = "Request body containing filters and pagination")
          @RequestBody(required = false)
          CampaignSchedulePriceFilterDTO filter,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "name")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "asc")
          @RequestParam(defaultValue = "asc")
          String sortDir) {

    log.info(
        "Getting campaign schedule prices for campaignId: {} with request: {}", campaignId, filter);

    // Validate campaign exists in the caller's Test Mode partition (cross-mode = 404)
    campaignService.findByIdForCurrentMode(campaignId);

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<CampaignSchedulePriceResponseDTO> result =
        campaignInventorySchedulesService.getCampaignSchedulePrices(campaignId, filter, pageable);

    log.info(
        "Found {} campaign schedule prices for campaignId: {}",
        result.getTotalElements(),
        campaignId);

    return ApiResponse.success(result);
  }

  /**
   * Accept inventory prices for selected CampaignInventorySchedules. Updates approval data and logs
   * acceptance in history.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing list of CampaignInventorySchedules IDs to accept
   * @return Success response
   */
  @PostMapping("/campaigns/{campaignId}/accept")
  @Operation(
      summary = "Accept inventory prices for selected CampaignInventorySchedules",
      description =
          "Accept inventory prices for selected CampaignInventorySchedules. If CampaignInventorySchedulesIds is not provided, accepts all CampaignInventorySchedules for the campaign. "
              + "Updates approvedScheduleIds and approvedBy fields, and adds an ACCEPTED action entry to the price history. "
              + "Operation is atomic - all updates succeed or fail together.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory prices accepted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data or validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or CampaignInventorySchedules not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> acceptInventoryPrices(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody AcceptInventoryPricesRequestDTO request) {

    int count =
        request.getCampaignInventorySchedulesIds() != null
            ? request.getCampaignInventorySchedulesIds().size()
            : 0;
    log.info(
        "Accepting inventory prices for campaignId: {} with {} CampaignInventorySchedules IDs",
        campaignId,
        count > 0 ? count : "all");

    // Validate campaign exists in the caller's Test Mode partition (cross-mode = 404)
    campaignService.findByIdForCurrentMode(campaignId);

    campaignInventorySchedulesService.acceptInventoryPrices(
        campaignId, request.getCampaignInventorySchedulesIds());

    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.operation_completed", locale);

    log.info(
        "Successfully accepted inventory prices for {} CampaignInventorySchedules in campaignId: {}",
        count > 0 ? count : "all",
        campaignId);

    return ApiResponse.success(successMessage);
  }

  /**
   * Get price history for a specific campaign inventory schedule with pagination support. Returns
   * all price history entries including audit information (createdBy, role, createdAt) sorted by
   * latest activity first.
   *
   * @param id Campaign Inventory Schedule ID
   * @param page Page number (0-based)
   * @param size Page size
   * @return Page of price history entries
   */
  @GetMapping(
      value = "/campaign-inventory-schedules/{id}/price-history",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get price history for a specific campaign inventory schedule",
      description =
          "Retrieve price history details for a specific campaign inventory schedule, including audit information "
              + "(oldPrice, newPrice, action, createdBy, role, createdAt). Results are sorted by latest activity first.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Price history retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign inventory schedule not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<PriceHistoryResponseDTO>> getPriceHistory(
      @Parameter(
              description = "Campaign Inventory Schedule ID",
              example = "campaign_inventory_schedule_123")
          @PathVariable
          String id,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size) {

    log.info(
        "Getting price history for campaignInventoryScheduleId: {}, with pagination: page={}, size={}",
        id,
        page,
        size);

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Create sort by createdAt descending (latest first)
    Sort sort = Sort.by("createdAt").descending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<PriceHistoryResponseDTO> result =
        campaignInventorySchedulesService.getPriceHistory(id, pageable);

    log.info(
        "Successfully retrieved {} price history entries for campaignInventoryScheduleId: {},  (page {} of {})",
        result.getTotalElements(),
        id,
        result.getNumber(),
        result.getTotalPages());

    return ApiResponse.success(result);
  }

  private Locale getUserLocale() {
    try {
      return userService.getIamUserContext().getLocale();
    } catch (Exception e) {
      log.warn("Could not get user locale, defaulting to English", e);
      return Locale.ENGLISH;
    }
  }
}
