package com.mw.planner.controller;

import static com.mw.planner.dto.SelectCampaignInventoryRequestDTO.OperationType.DESELECT;
import static com.mw.planner.dto.SelectCampaignInventoryRequestDTO.OperationType.SELECT;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.*;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.campaign.BulkOperationValidationException;
import com.mw.planner.exception.csv.CsvUploadException;
import com.mw.planner.exception.inventory.InventoryImportException;
import com.mw.planner.exception.inventory.InventoryMeasureApiException;
import com.mw.planner.service.*;
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
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for managing campaign inventory operations. Handles filtering, selection and
 * deselection of campaign inventory schedules.
 */
@RestController
@RequestMapping("/api/v1/campaign-inventory")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(
    name = "Campaign Inventory",
    description = "Operations for filtering, managing campaign inventory schedules & bookings")
@SecurityRequirement(name = "bearerAuth")
public class CampaignInventoryController {

  private final CampaignInventorySchedulesService inventorySchedulesService;
  private final InventoryService inventoryService;
  private final MwMeasureService mwMeasureService;
  private final CampaignService campaignService;
  private final MessageService messageService;
  private final UserService userService;
  private final InventoryImportService inventoryImportService;
  private final CountryService countryService;

  /**
   * Filter campaign inventories with comprehensive criteria.
   *
   * @param campaignId Campaign ID for performance calculations and selection status
   * @param filter Filter criteria for inventory search
   * @param page Page number (0-based)
   * @param size Page size
   * @param sortBy Sort field
   * @param sortDir Sort direction
   * @return Page of filtered campaign inventories
   */
  @PostMapping("/{campaignId}/filter")
  @Operation(
      summary = "Filter campaign inventories with comprehensive criteria",
      description =
          "Filter campaign inventories based on location, demographics, geofencing, and inventory properties. "
              + "Optional: programmaticSupport (YES | NO | ALL), dealTypes (e.g. guaranteed, preferred_deal).")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign inventories filtered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<CampaignInventoryFilterResponseDTO>> filterCampaignInventories(
      @Parameter(
              description = "Campaign ID for performance calculations and selection status",
              example = "campaign123")
          @PathVariable
          String campaignId,
      @Parameter(description = "Filter criteria for campaign inventory search") @RequestBody
          CampaignInventoryFilterDTO filter,
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
        "Filtering campaign inventories for campaignId: {} with criteria: {}", campaignId, filter);

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    log.info(
        "Found {} campaign inventories matching criteria for campaignId: {}",
        result.getTotalElements(),
        campaignId);

    return ApiResponse.success(result);
  }

  /**
   * Get selected inventories for a campaign with optional name and inventoryType filtering and
   * pagination.
   *
   * @param campaignId Campaign ID to get selected inventories for
   * @param name Optional name filter (case-insensitive partial match)
   * @param inventoryType Optional inventoryType filter
   * @param page Page number (0-based)
   * @param size Page size (-1 to return all results without pagination)
   * @param sortBy Sort field
   * @param sortDir Sort direction
   * @return Page of selected campaign inventories
   */
  @GetMapping("/{campaignId}/selected-inventory")
  @Operation(
      summary = "Get selected inventories for a campaign",
      description =
          "Retrieve all selected inventories for a campaign with optional name and inventoryType filtering and pagination. Pass size=-1 to return all results without pagination.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Selected inventories retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<CampaignInventoryFilterResponseDTO>> getSelectedInventories(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
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
      @Parameter(
              description = "Page size (-1 to return all results without pagination)",
              example = "10")
          @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "name") @RequestParam(defaultValue = "name")
          String sortBy,
      @Parameter(description = "Sort direction", example = "asc")
          @RequestParam(defaultValue = "asc")
          String sortDir) {

    log.info(
        "Getting selected inventories for campaignId: {}, name filter: {}, inventoryType filter: {}, page: {}, size: {}",
        campaignId,
        name,
        inventoryType,
        page,
        size);

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

    // Handle pagination: if size is -1, return all results without pagination (use large page size)
    Pageable pageable;
    if (size == -1) {
      // Use a very large page size to effectively return all results while maintaining sorting
      pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
    } else {
      // Validate and fix pagination parameters
      int validPage = Math.max(0, page);
      int validSize = Math.max(1, size);
      pageable = PageRequest.of(validPage, validSize, sort);
    }

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, name, inventoryType, pageable);

    log.info(
        "Found {} selected inventories for campaignId: {}", result.getTotalElements(), campaignId);

    return ApiResponse.success(result);
  }

  @PostMapping("/{campaignId}/selected-inventory")
  @Operation(
      summary = "Get selected inventories for a campaign (with media-owner filter)",
      description =
          "Retrieve all selected inventories for a campaign with optional name and inventoryType filtering and pagination. Accepts an optional request body to filter by media owner IDs. Pass size=-1 to return all results without pagination.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Selected inventories retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<CampaignInventoryFilterResponseDTO>> getSelectedInventoriesFiltered(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
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
      @Parameter(
              description = "Page size (-1 to return all results without pagination)",
              example = "10")
          @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "name") @RequestParam(defaultValue = "name")
          String sortBy,
      @Parameter(description = "Sort direction", example = "asc")
          @RequestParam(defaultValue = "asc")
          String sortDir,
      @Parameter(description = "Optional media-owner filter") @Valid @RequestBody(required = false)
          MediaOwnerFilterRequestDTO request) {

    log.info(
        "Getting selected inventories (filtered) for campaignId: {}, name filter: {}, inventoryType filter: {}, page: {}, size: {}",
        campaignId,
        name,
        inventoryType,
        page,
        size);

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

    // Handle pagination: if size is -1, return all results without pagination (use large page size)
    Pageable pageable;
    if (size == -1) {
      pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
    } else {
      int validPage = Math.max(0, page);
      int validSize = Math.max(1, size);
      pageable = PageRequest.of(validPage, validSize, sort);
    }

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, name, inventoryType, pageable, request);

    log.info(
        "Found {} selected inventories for campaignId: {}", result.getTotalElements(), campaignId);

    return ApiResponse.success(result);
  }

  /**
   * Get all selected inventories for a campaign as slim summaries without pagination.
   *
   * @param campaignId Campaign ID to get selected inventories for
   * @return List of all selected inventory summaries
   */
  @GetMapping("/{campaignId}/selected-inventory/all")
  @Operation(
      summary = "Get all selected inventories for a campaign (no pagination)",
      description =
          "Retrieve all selected inventory records for a campaign as a slim summary containing inventoryId, referenceId and performance. No pagination or filtering.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Selected inventories retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<SelectedInventorySummaryResponseDTO>> getAllSelectedInventories(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId) {

    log.info("Getting all selected inventories for campaignId: {}", campaignId);

    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId);

    log.info("Found {} selected inventories for campaignId: {}", result.size(), campaignId);

    return ApiResponse.success(result);
  }

  @PostMapping("/{campaignId}/selected-inventory/all")
  @Operation(
      summary =
          "Get all selected inventories for a campaign (no pagination, with media-owner filter)",
      description =
          "Retrieve all selected inventory records for a campaign as a slim summary containing inventoryId, referenceId and performance. Accepts an optional request body to filter by media owner IDs.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Selected inventories retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<SelectedInventorySummaryResponseDTO>> getAllSelectedInventoriesFiltered(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Optional media-owner filter") @Valid @RequestBody(required = false)
          MediaOwnerFilterRequestDTO request) {

    log.info("Getting all selected inventories (filtered) for campaignId: {}", campaignId);

    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId, request);

    log.info("Found {} selected inventories for campaignId: {}", result.size(), campaignId);

    return ApiResponse.success(result);
  }

  /**
   * Select or deselect campaign inventory for scheduling.
   *
   * @param campaignId Campaign ID from path variable
   * @param request The campaign inventory selection request
   * @return Response indicating success or failure
   */
  @PostMapping("/{campaignId}/select")
  @Operation(
      summary = "Select/Deselect Campaign Inventory",
      description = "Select or deselect campaign inventory for scheduling operations")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Operation completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> selectCampaignInventory(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody SelectCampaignInventoryRequestDTO request) {

    // Set campaignId from path variable into filter DTO
    request.setCampaignId(campaignId);

    log.info(
        "Processing {} operation for campaignId: {}, inventoryId: {}",
        request.getOperationType(),
        request.getCampaignId(),
        request.getInventoryId());

    Locale locale = getUserLocale();
    String successMessage;

    if (request.getOperationType() == SELECT) {
      inventorySchedulesService.selectInventory(request);
      log.info("Successfully processed SELECT operation for campaign inventory");
      successMessage = messageService.getMessage("success.inventory_selected", locale);
    } else if (request.getOperationType() == DESELECT) {
      //   inventorySchedulesService.deselectInventoryV2(request);
      inventorySchedulesService.deselectInventory(request);
      log.info("Successfully processed DESELECT operation for campaign inventory");
      successMessage = messageService.getMessage("success.inventory_deselected", locale);
    } else {
      successMessage = messageService.getMessage("success.operation_completed", locale);
    }

    return ApiResponse.success(successMessage);
  }

  /**
   * Bulk select or deselect campaign inventories based on filter criteria. For DESELECT, cache
   * eviction is parallelized across virtual threads and the recommendation-engine sync is deferred
   * off the request path, so large batches don't block the response. SOV is automatically
   * calculated: CLASSIC inventory = 100%, all others = 10%. This is an all-or-nothing operation -
   * if any inventory fails, the entire operation rolls back and throws an exception.
   *
   * @param campaignId Campaign ID from path variable
   * @param operationType Operation type (SELECT or DESELECT) as query parameter
   * @param filter Filter criteria for inventory selection
   * @return Success message with total count of inventories processed
   */
  @PostMapping("/{campaignId}/select-all")
  @Operation(
      summary = "Bulk Select/Deselect Campaign Inventories",
      description =
          "Perform bulk select or deselect operations on campaign inventories based on filter criteria. "
              + "For DESELECT, cache eviction is parallelized and the recommendation-engine sync is deferred "
              + "so large batches don't block the response. "
              + "This is an all-or-nothing operation - if any inventory fails, the entire operation rolls back. "
              + "Required fields: operationType. "
              + "SOV is automatically calculated based on inventory type (CLASSIC=100%, others=10%).")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Bulk operation completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description =
                "Invalid request data or bulk operation failed - all changes rolled back"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> bulkSelectCampaignInventories(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Operation Type", example = "SELECT")
          @RequestParam(defaultValue = "SELECT")
          SelectCampaignInventoryRequestDTO.OperationType operationType,
      @Valid @RequestBody CampaignInventoryFilterDTO filter) {

    log.info(
        "Processing bulk {} operation for campaignId: {} with filter criteria",
        operationType,
        campaignId);

    // Validate operation type
    if (operationType == null) {
      throw new BulkOperationValidationException(
          ErrorCode.BULK_OPERATION_TYPE_REQUIRED, "Operation type is required for bulk operation");
    }

    int totalProcessed =
        inventorySchedulesService.bulkSelectDeselectInventories(campaignId, filter, operationType);

    // Get localized success message
    String messageKey =
        operationType == SelectCampaignInventoryRequestDTO.OperationType.SELECT
            ? "success.bulk_operation_select"
            : "success.bulk_operation_deselect";

    java.util.Locale locale = getUserLocale();
    String successMessage =
        messageService.getMessage(messageKey, locale, totalProcessed, campaignId);

    log.info(
        "Bulk {} operation completed successfully for campaignId: {} - Total processed: {}",
        operationType,
        campaignId,
        totalProcessed);

    return ApiResponse.success(successMessage);
  }

  @PostMapping("/{campaignId}/bulk-select")
  @Operation(
      summary = "Bulk Select/Deselect Campaign Inventories by IDs",
      description =
          "Select or deselect multiple campaign inventories in a single optimized batch, given an "
              + "explicit list of IDs. Provide exactly one of inventoryIds (Mongo IDs) or "
              + "referenceIds (external reference IDs) - supplying both is rejected. Unlike "
              + "/select-all (filter-based), this endpoint operates on the provided IDs. A single "
              + "Reach & Frequency (Measure API) call and bulk database writes are used for the "
              + "whole list. This is an all-or-nothing operation - if any inventory fails, the "
              + "entire operation is aborted. Required fields: one of (inventoryIds | "
              + "referenceIds), operationType.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Bulk operation completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data or bulk operation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> bulkSelectDeselectCampaignInventoriesByIds(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody BulkSelectCampaignInventoryRequestDTO request) {

    // Set campaignId from path variable into request DTO
    request.setCampaignId(campaignId);

    // Exactly one of inventoryIds / referenceIds is present (enforced by validation). When
    // referenceIds is supplied, inventories are resolved by referenceId instead of Mongo _id;
    // the rest of the flow (schedules, pricing, recommendation sync) is identical.
    boolean useReferenceIds =
        request.getReferenceIds() != null && !request.getReferenceIds().isEmpty();
    List<String> ids = useReferenceIds ? request.getReferenceIds() : request.getInventoryIds();

    log.info(
        "Processing bulk {} operation by {} for campaignId: {} with {} IDs",
        request.getOperationType(),
        useReferenceIds ? "referenceIds" : "inventoryIds",
        campaignId,
        ids != null ? ids.size() : 0);

    int totalProcessed;
    List<String> notFoundReferenceIds = List.of();

    if (request.getOperationType() == SELECT) {
      if (useReferenceIds) {
        var result = inventorySchedulesService.bulkSelectInventoriesByReferenceIds(campaignId, ids);
        totalProcessed = result.count();
        notFoundReferenceIds = result.notFoundReferenceIds();
      } else {
        totalProcessed = inventorySchedulesService.bulkSelectInventoriesByIds(campaignId, ids);
      }
    } else {
      totalProcessed =
          useReferenceIds
              ? inventorySchedulesService.bulkDeselectInventoriesByReferenceIds(campaignId, ids)
              : inventorySchedulesService.bulkDeselectInventoriesByIds(campaignId, ids);
    }

    // Get localized success message
    Locale locale = getUserLocale();
    String messageKey;
    if (request.getOperationType() == SELECT) {
      messageKey =
          totalProcessed == 1
              ? "success.bulk_operation_select_single"
              : "success.bulk_operation_select";
    } else {
      messageKey =
          totalProcessed == 1
              ? "success.bulk_operation_deselect_single"
              : "success.bulk_operation_deselect";
    }
    String successMessage =
        messageService.getMessage(messageKey, locale, totalProcessed, campaignId);

    if (!notFoundReferenceIds.isEmpty()) {
      String notFoundMessage =
          messageService.getMessage(
              "warning.bulk_operation_reference_ids_not_found",
              locale,
              notFoundReferenceIds.toString());
      successMessage = successMessage + ". " + notFoundMessage;
    }

    log.info(
        "Bulk {} operation by IDs completed successfully for campaignId: {} - Total processed: {}",
        request.getOperationType(),
        campaignId,
        totalProcessed);

    return ApiResponse.success(successMessage);
  }

  /**
   * Get the user's locale from the user context. Falls back to English if not available.
   *
   * @return User's locale or English as default
   */
  private Locale getUserLocale() {
    try {
      return userService.getIamUserContext().getLocale();
    } catch (Exception e) {
      log.debug("Could not get user locale, falling back to English", e);
      return Locale.ENGLISH;
    }
  }

  /**
   * Verify CSV file containing inventory reference IDs. Validates inventory existence, country
   * match, and detects duplicates (both within CSV and already selected in campaign).
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing CSV file and country name
   * @return InventoryImportStatusResponseDTO with validation results for each inventory reference
   *     ID
   */
  @PostMapping(
      value = "/{campaignId}/verify-csv",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Verify CSV file for inventory import",
      description =
          "Verify CSV file containing inventory_id column. Validates inventory existence, country match, and detects duplicates (both within CSV and already selected in campaign).")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "CSV verification completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid CSV file or request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<InventoryImportStatusResponseDTO> verifyCsvFile(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @ModelAttribute InventoryImportRequestDTO request) {

    log.info(
        "Verifying CSV file: {} for campaignId: {} and country: {}",
        request.getCsvFile().getOriginalFilename(),
        campaignId,
        request.getCountryName());

    // Validate Request Data
    validateRequestData(request, campaignId);

    try {
      InventoryImportStatusResponseDTO result =
          inventoryImportService.verifyCsvFile(
              request.getCsvFile(), campaignId, request.getCountryName());

      log.info(
          "CSV verification completed - Total: {}, Valid: {}, Invalid: {}, Duplicate: {}",
          result.getResults().size(),
          result.getResults().stream()
              .filter(r -> r.getType() == InventoryImportStatusResponseDTO.ValidationType.VALID)
              .count(),
          result.getResults().stream()
              .filter(r -> r.getType() == InventoryImportStatusResponseDTO.ValidationType.INVALID)
              .count(),
          result.getResults().stream()
              .filter(r -> r.getType() == InventoryImportStatusResponseDTO.ValidationType.DUPLICATE)
              .count());

      return ApiResponse.success(result);

    } catch (IOException e) {
      log.error("Failed to read CSV file", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PARSE_ERROR,
          "Failed to parse CSV file: " + e.getMessage(),
          e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error verifying CSV file", e);
      throw new CsvUploadException(
          ErrorCode.CSV_VERIFY_PROCESSING_ERROR,
          "Failed to verify CSV file: " + e.getMessage(),
          e.getMessage());
    }
  }

  /**
   * Upload CSV file containing inventory reference IDs and save to database. Validates all data If
   * any data is invalid, returns a single error message. If all data is valid, saves to
   * SelectInventoryImports table and select all inventories.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing CSV file and country name
   * @return Success response
   */
  @PostMapping(
      value = "/{campaignId}/upload-csv",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Upload CSV file for inventory import",
      description =
          "Upload CSV file containing inventory_id column. Validates inventory existence, country match, and detects duplicates. If all validations pass, saves to SelectInventoryImports table.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "CSV file uploaded and saved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid CSV file or request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Void> uploadCsvFile(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @ModelAttribute InventoryImportRequestDTO request) {

    log.info(
        "Uploading CSV file: {} for campaignId: {} and country: {}",
        request.getCsvFile().getOriginalFilename(),
        campaignId,
        request.getCountryName());

    // Validate Request Data
    validateRequestData(request, campaignId);

    try {
      // Get companyId from userContext
      String companyId = userService.getActingCompanyId();

      // Upload and save CSV file
      inventoryImportService.uploadCsvFile(
          request.getCsvFile(), campaignId, request.getCountryName(), companyId);

      log.info(
          "CSV file uploaded and saved successfully: {} for campaignId: {}",
          request.getCsvFile().getOriginalFilename(),
          campaignId);

      return ApiResponse.success(null);

    } catch (IOException e) {
      log.error("Failed to read CSV file", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PARSE_ERROR,
          "Failed to parse CSV file: " + e.getMessage(),
          e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error upload CSV file", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PROCESSING_ERROR,
          "Failed to upload CSV file: " + e.getMessage(),
          e.getMessage());
    }
  }

  /**
   * Get inventory import files by country name with pagination and sorting.
   *
   * @param countryName Country name to filter by
   * @param page Page number (0-based)
   * @param size Page size
   * @param sortBy Sort field (id, fileName, inventoryRefIdCount, createdBy, createdAt)
   * @param sortDir Sort direction (asc/desc)
   * @return Page of inventory import file data
   */
  @GetMapping("/inventory-imports")
  @Operation(
      summary = "Get inventory import files by country name",
      description =
          "Fetch paginated inventory import file data filtered by country name and company ID (from user context)")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory import files retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<InventoryImportFileResponseDTO>> getInventoryImportFilesByCountry(
      @Parameter(
              description = "Country name to filter by",
              example = "United States",
              required = true)
          @RequestParam
          String countryName,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "createdAt")
          @RequestParam(defaultValue = "createdAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir) {

    log.info(
        "Fetching inventory import files for country: {} with pagination: page={}, size={}, sortBy={}, sortDir={}",
        countryName,
        page,
        size,
        sortBy,
        sortDir);

    // Validate country exists
    countryService.validateCountryExists(countryName);

    // Get companyId from userContext
    String companyId = userService.getActingCompanyId();

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Build sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<InventoryImportFileResponseDTO> result =
        inventoryImportService.getInventoryImportFilesByCountry(companyId, countryName, pageable);

    log.info(
        "Retrieved {} inventory import files for country: {} (total: {})",
        result.getContent().size(),
        countryName,
        result.getTotalElements());

    return ApiResponse.success(result);
  }

  private void validateRequestData(InventoryImportRequestDTO request, String campaignId) {
    if (request.getCsvFile().isEmpty()) {
      log.warn("Empty CSV file uploaded");
      throw new CsvUploadException(ErrorCode.CSV_UPLOAD_FILE_EMPTY, "CSV file is empty");
    }

    // Validate campaign id and country
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    countryService.validateCountryExists(request.getCountryName());

    // Validate file type
    String contentType = request.getCsvFile().getContentType();
    String originalFilename = request.getCsvFile().getOriginalFilename();
    if (contentType == null
        || (!contentType.equals("text/csv")
            && !contentType.equals("application/csv")
            && (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")))) {
      log.warn("Invalid file type: {}", contentType);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_INVALID_FILE_TYPE, "Invalid file type. Only CSV files are allowed");
    }
  }

  /**
   * Use existing inventory import by ID. Fetches import data, validates inventory reference IDs,
   * and adds them to selected inventory.
   *
   * @param campaignId Campaign ID from path variable
   * @param importId Import ID
   * @return Success response
   */
  @PostMapping("/{campaignId}/import/{importId}/use")
  @Operation(
      summary = "Use existing inventory import by ID",
      description =
          "Fetches import data from SelectInventoryImports by importId, validates inventory reference IDs, "
              + "verifies if already selected, and adds fetched inventory items into Select Inventory data.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory import used successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid import data or validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or inventory import not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Void> useInventoryImport(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Import ID", example = "import123") @PathVariable String importId) {

    log.info("Using inventory import with ID: {} for campaignId: {}", importId, campaignId);

    // Validate campaignId
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    try {
      inventoryImportService.useInventoryImport(campaignId, importId);

      log.info(
          "Successfully used inventory import with ID: {} for campaignId: {}",
          importId,
          campaignId);
      return ApiResponse.success(null);

    } catch (InventoryImportException e) {
      log.error("Error using inventory import: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error using inventory import", e);
      throw InventoryImportException.useFailed(importId, e);
    }
  }

  /**
   * Download CSV file for inventory import data by ID.
   *
   * @param importId Import ID
   * @return CSV file download response
   */
  @GetMapping("/import/{importId}/download")
  @Operation(
      summary = "Download CSV file for inventory import data by ID",
      description =
          "Fetches all inventory data linked with the given importId and generates a downloadable CSV file. "
              + "Uses inventory_id as referenceId in the CSV.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "CSV file downloaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Inventory import not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<byte[]> downloadInventoryImportCsv(
      @Parameter(description = "Import ID", example = "import123") @PathVariable String importId) {

    log.info("Downloading CSV file for inventory import ID: {}", importId);

    try {
      InventoryImportService.CsvFileResult csvResult =
          inventoryImportService.generateInventoryImportCsv(importId);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType("text/csv"));
      headers.setContentDispositionFormData("attachment", csvResult.fileName());
      headers.setContentLength(csvResult.content().length);

      log.info(
          "Successfully generated CSV file for inventory import ID: {} with filename: {}",
          importId,
          csvResult.fileName());
      return ResponseEntity.ok().headers(headers).body(csvResult.content());

    } catch (InventoryImportException e) {
      log.error("Error downloading inventory import CSV: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error downloading inventory import CSV", e);
      throw InventoryImportException.downloadFailed(importId, e);
    }
  }

  /**
   * Delete inventory import data by ID.
   *
   * @param importId Import ID
   * @return Success response
   */
  @DeleteMapping("/import/{importId}")
  @Operation(
      summary = "Delete inventory import data by ID",
      description =
          "Deletes inventory import record from SelectInventoryImports by importId. "
              + "Logs delete activity in audit logs.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory import deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Inventory import not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Void> deleteInventoryImport(
      @Parameter(description = "Import ID", example = "import123") @PathVariable String importId) {

    log.info("Deleting inventory import with ID: {}", importId);

    try {
      inventoryImportService.deleteInventoryImport(importId);

      log.info("Successfully deleted inventory import with ID: {}", importId);
      return ApiResponse.success(null);

    } catch (InventoryImportException e) {
      log.error("Error deleting inventory import: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error deleting inventory import", e);
      throw InventoryImportException.deleteFailed(importId, e);
    }
  }

  /**
   * Get reach and frequency data with individual site level aggregation support.
   *
   * @param request The request containing inventory data, duration, and optional date range
   * @param aggregate If true, returns individual site level data; if false, returns aggregated
   *     campaign level data
   * @return List of site-level responses when aggregate=true, or single aggregated response when
   *     aggregate=false
   */
  @PostMapping("/reach-and-frequency")
  @Operation(
      summary = "Get reach and frequency data with individual site level aggregation",
      description =
          "Proxy endpoint to fetch impression, reach & frequency details from the Measure API with support for individual site level data aggregation")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Reach and frequency data retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing authentication token"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<MeasureReachFrequencyResponseDTO>> getReachAndFrequencyBySites(
      @Parameter(
              description = "Request containing inventory data, duration, and optional date range")
          @Valid
          @RequestBody
          MeasureReachFrequencyRequestDTO request,
      @Parameter(
              description =
                  "If true, returns individual site level data; if false, returns aggregated campaign level data",
              example = "true")
          @RequestParam(value = "aggregate", defaultValue = "false")
          boolean aggregate) {

    log.info(
        "Processing reach and frequency by sites request with {} inventories, duration {} days, aggregate={}",
        request.getInventories() != null ? request.getInventories().size() : 0,
        request.getDuration(),
        aggregate);

    try {
      List<MeasureReachFrequencyResponseDTO> response =
          mwMeasureService.getReachAndFrequencyBySites(request, aggregate);

      log.info(
          "Successfully retrieved reach and frequency data for {} sites",
          response != null ? response.size() : 0);

      return ApiResponse.success(response);

    } catch (InventoryMeasureApiException e) {
      log.error("Reach and frequency API error: {}", e.getMessage(), e);
      throw e; // Re-throw the specific exception to be handled by GlobalExceptionHandler
    } catch (Exception e) {
      log.error("Unexpected error retrieving reach and frequency data", e);
      throw new RuntimeException(
          "Failed to retrieve reach and frequency data: " + e.getMessage(), e);
    }
  }

  /**
   * Get forecast data for a campaign including estimated impressions, reach, frequency, and costs.
   *
   * @param campaignId Campaign ID to get forecast for
   * @return ForecastResponseDTO with forecast metrics
   */
  @GetMapping("/{campaignId}/forecast")
  @Operation(
      summary = "Get campaign forecast data",
      description =
          "Retrieve forecast metrics including impressions, reach, frequency, and costs for a campaign")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Forecast data retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<CampaignForecastDTO> getCampaignForecast(
      @Parameter(description = "Campaign ID to get forecast for", example = "campaign123")
          @PathVariable
          String campaignId,
      @Parameter(
              description =
                  "When true, bypass any stored performance snapshot and recompute the forecast. "
                      + "Absent, empty, or false keeps the existing behavior.")
          @RequestParam(defaultValue = "false")
          boolean forceRegenerate) {

    log.info(
        "Getting forecast data for campaignId: {} (forceRegenerate={})",
        campaignId,
        forceRegenerate);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    CampaignForecastDTO forecast =
        campaignService.calculateCampaignForecast(campaign, forceRegenerate);

    log.info(
        "Successfully calculated forecast for campaignId: {} - Impressions: {}, Reach: {}, Frequency: {}",
        campaignId,
        forecast.getEstimatedImpression(),
        forecast.getEstimatedReach(),
        forecast.getEstimatedFrequency());

    return ApiResponse.success(forecast);
  }

  @PostMapping("/{campaignId}/forecast")
  @Operation(
      summary = "Get campaign forecast data (with media-owner filter)",
      description =
          "Retrieve forecast metrics including impressions, reach, frequency, and costs for a campaign. Accepts an optional request body to filter by media owner IDs.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Forecast data retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<CampaignForecastDTO> getCampaignForecastFiltered(
      @Parameter(description = "Campaign ID to get forecast for", example = "campaign123")
          @PathVariable
          String campaignId,
      @Parameter(
              description =
                  "When true, bypass any stored performance snapshot and recompute the forecast. "
                      + "Absent, empty, or false keeps the existing behavior.")
          @RequestParam(defaultValue = "false")
          boolean forceRegenerate,
      @Parameter(description = "Optional media-owner filter") @Valid @RequestBody(required = false)
          MediaOwnerFilterRequestDTO request) {

    log.info(
        "Getting forecast data (filtered) for campaignId: {} (forceRegenerate={})",
        campaignId,
        forceRegenerate);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);
    CampaignForecastDTO forecast =
        campaignService.calculateCampaignForecast(campaign, request, forceRegenerate);

    log.info(
        "Successfully calculated forecast for campaignId: {} - Impressions: {}, Reach: {}, Frequency: {}",
        campaignId,
        forecast.getEstimatedImpression(),
        forecast.getEstimatedReach(),
        forecast.getEstimatedFrequency());

    return ApiResponse.success(forecast);
  }

  /**
   * Get detailed inventory data for a specific import record using its importId. Supports
   * pagination and sorting (default by createdAt descending).
   *
   * @param importId Import record ID
   * @param page Page number (0-based)
   * @param size Page size
   * @param sortBy Sort field
   * @param sortDir Sort direction
   * @return Page of inventory details
   */
  @GetMapping("/import/{importId}/inventories")
  @Operation(
      summary = "Get inventory details for an import record",
      description =
          "Fetch detailed inventory data for a specific import record using its importId. "
              + "Supports pagination and sorting (default by createdAt descending). "
              + "Returns only required fields: id, inventoryName, referenceId, type, category, location, thumbnail.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory details retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true,\"data\": {\"content\": [{\"id\": \"6909d3ae3d84b1e34bdbffc0\",\"inventoryName\": \"Raffles Hotel - 1611\",\"referenceId\": \"SG-CLE-189768-C-0032\",\"type\": \"CLASSIC\",\"category\": \"OUTDOOR\",\"location\": {\"address\": \"11 Beach Rd, Singapore 189675\",\"zipcode\": \"189768\",\"latitude\": 1.29529638630566,\"longitude\": 103.8553903997,\"state\": \"North\",\"country\": \"Singapore\" },\"thumbnail\": \"https://cdn.movingwalls.com/classic_billboard_5a693add4f45b63cf681cbd8.jpg\" } ],\"total\": 1,\"last\": false,\"totalElements\": 15,\"totalPages\": 2,\"first\": true,\"size\": 10,\"number\": 0,\"numberOfElements\": 10,\"sort\": {\"orders\": [ {\"direction\": \"DESC\",\"property\": \"createdAt\",\"ignoreCase\": false,\"nullHandling\": \"NATIVE\",\"descending\": true,\"ascending\": false } ],\"empty\": false,\"sorted\": true,\"unsorted\": false },\"empty\": false }}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": false,\"error\": {\"errorCode\": \"ERR_100010\",\"message\": \"Import record not found\",\"path\": \"/api/v1/campaign-inventory/import/6914605346a8088d31ae6665/inventories\",\"timestamp\": \"2025-11-13T17:45:51.3114022\" }}")))
      })
  public ApiResponse<Page<ImportInventoryDetailResponseDTO>> getImportInventories(
      @Parameter(description = "Import record ID", example = "import123") @PathVariable
          String importId,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "createdAt")
          @RequestParam(defaultValue = "createdAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir) {

    log.info(
        "Getting inventory details for importId: {} with pagination: page={}, size={}, sortBy={}, sortDir={}",
        importId,
        page,
        size,
        sortBy,
        sortDir);

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Create sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<ImportInventoryDetailResponseDTO> result =
        inventoryImportService.getInventoriesByImportId(importId, pageable);

    log.info(
        "Successfully retrieved {} inventories for importId: {} (page {} of {})",
        result.getContent().size(),
        importId,
        result.getNumber(),
        result.getTotalPages());

    return ApiResponse.success(result);
  }

  /**
   * Import geo coordinates from request body and save to database.
   *
   * @param request Request containing fileName, countryName, and geoDetails list
   * @return Success response
   */
  @PostMapping(value = "/import-geo-coordinates")
  @Operation(
      summary = "Import geo coordinates from request body",
      description =
          "Import geo coordinates from request body containing fileName, countryName, and geoDetails list. "
              + "Saves the geo coordinate data to CampaignGeoImportFile collection.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Geo coordinates imported successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> importGeoCoordinates(
      @Valid @RequestBody GeoCoordinatesImportRequestDTO request) {

    log.info(
        "Importing geo coordinates from request: {} for country: {}",
        request.getFileName(),
        request.getCountryName());

    // Validate country exists
    countryService.validateCountryExists(request.getCountryName());

    try {
      // Get companyId from userContext
      String companyId = userService.getActingCompanyId();

      // Import geo coordinates
      inventoryImportService.importGeoCoordinates(request, companyId);

      log.info(
          "Geo coordinates imported successfully from request: {} for country: {}",
          request.getFileName(),
          request.getCountryName());

      return ApiResponse.success("File imported successfully");

    } catch (CsvUploadException e) {
      log.error("Error importing geo coordinates: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error importing geo coordinates", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PROCESSING_ERROR,
          "Failed to import geo coordinates: " + e.getMessage(),
          e.getMessage());
    }
  }

  /**
   * Get geo coordinates import file by ID.
   *
   * @param geoImportId Geo import file ID
   * @return List of GeoDetailsDTO
   */
  @GetMapping("/import-geo-coordinates/{geoImportId}")
  @Operation(
      summary = "Get geo coordinates by import file ID",
      description =
          "Retrieve geo coordinate details by import file ID. Returns list of geo coordinate entries.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Geo coordinates retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Geo import file not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<GeoImportFileResponseDTO.GeoDetailsDTO>> getImportGeoCoordinatesById(
      @Parameter(description = "Geo import file ID", example = "geo_import_123456") @PathVariable
          String geoImportId) {

    log.info("Getting geo coordinates by import file ID: {}", geoImportId);

    List<GeoImportFileResponseDTO.GeoDetailsDTO> result =
        inventoryImportService.getGeoImportFileById(geoImportId);

    log.info(
        "Successfully retrieved {} geo details for import file: {}", result.size(), geoImportId);

    return ApiResponse.success(result);
  }

  /**
   * Download CSV file for geo coordinates import data by ID.
   *
   * @param geoImportId Geo import file ID
   * @return CSV file download response
   */
  @GetMapping("/import-geo-coordinates/{geoImportId}/download")
  @Operation(
      summary = "Download CSV file for geo coordinates import data by ID",
      description =
          "Fetches all geo coordinate data linked with the given geoImportId and generates a downloadable CSV file. "
              + "CSV contains Location, Latitude, Longitude, Type, and Radius columns.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "CSV file downloaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Geo import file not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ResponseEntity<byte[]> downloadGeoImportCsv(
      @Parameter(description = "Geo import file ID", example = "geo_import_123456") @PathVariable
          String geoImportId) {

    log.info("Downloading CSV file for geo import ID: {}", geoImportId);

    try {
      InventoryImportService.CsvFileResult csvResult =
          inventoryImportService.generateGeoImportCsv(geoImportId);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType("text/csv"));
      headers.setContentDispositionFormData("attachment", csvResult.fileName());
      headers.setContentLength(csvResult.content().length);

      log.info(
          "Successfully generated CSV file for geo import ID: {} with filename: {}",
          geoImportId,
          csvResult.fileName());
      return ResponseEntity.ok().headers(headers).body(csvResult.content());

    } catch (CsvUploadException e) {
      log.error("Error downloading geo import CSV: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error downloading geo import CSV", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PROCESSING_ERROR,
          "Failed to download geo import CSV: " + e.getMessage(),
          e.getMessage());
    }
  }

  /**
   * Delete geo coordinates import file by ID.
   *
   * @param geoImportId Geo import file ID
   * @return Success response
   */
  @DeleteMapping("/import-geo-coordinates/{geoImportId}")
  @Operation(
      summary = "Delete geo coordinates import file by ID",
      description =
          "Deletes geo coordinates import file from CampaignGeoImportFile collection by geoImportId.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Geo import file deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Geo import file not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> deleteGeoImportById(
      @Parameter(description = "Geo import file ID", example = "geo_import_123456") @PathVariable
          String geoImportId) {

    log.info("Deleting geo import file with ID: {}", geoImportId);

    try {
      inventoryImportService.deleteGeoImportFileById(geoImportId);

      log.info("Successfully deleted geo import file with ID: {}", geoImportId);
      return ApiResponse.success("File deleted successfully");

    } catch (CsvUploadException e) {
      log.error("Error deleting geo import file: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error deleting geo import file", e);
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_PROCESSING_ERROR,
          "Failed to delete geo import file: " + e.getMessage(),
          e.getMessage());
    }
  }

  /**
   * Get geo import files by country name with pagination and sorting.
   *
   * @param countryName Country name to filter by
   * @param page Page number (0-based)
   * @param size Page size
   * @param sortBy Sort field (id, fileName, countOfCoordinates, createdBy, createdAt)
   * @param sortDir Sort direction (asc/desc)
   * @return Page of geo import file data
   */
  @GetMapping("/geo-imports")
  @Operation(
      summary = "Get geo import files by country name",
      description =
          "Fetch paginated geo import file data filtered by country name and company ID (from user context)")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Geo import files retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<Page<GeoImportFileResponseDTO>> getGeoListExistingFile(
      @Parameter(description = "Country name to filter by", example = "Singapore", required = true)
          @RequestParam
          String countryName,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "createdAt")
          @RequestParam(defaultValue = "createdAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir) {

    log.info(
        "Fetching geo import files for country: {} with pagination: page={}, size={}, sortBy={}, sortDir={}",
        countryName,
        page,
        size,
        sortBy,
        sortDir);

    // Validate country exists
    countryService.validateCountryExists(countryName);

    // Get companyId from userContext
    String companyId = userService.getActingCompanyId();

    // Validate and fix pagination parameters
    int validPage = Math.max(0, page);
    int validSize = Math.max(1, size);

    // Build sort
    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(validPage, validSize, sort);

    Page<GeoImportFileResponseDTO> result =
        inventoryImportService.getGeoImportFilesByCountry(companyId, countryName, pageable);

    log.info(
        "Retrieved {} geo import files for country: {} (total: {})",
        result.getContent().size(),
        countryName,
        result.getTotalElements());

    return ApiResponse.success(result);
  }

  /**
   * Generate campaign comments by uploading files and saving comment data to the database.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request DTO containing comment and taggedCompanyIds
   * @param files List of multipart files to upload
   * @return Success response
   */
  @PostMapping(
      value = "/{campaignId}/comment",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate campaign comments with file uploads",
      description =
          "Added a campaign comment with optional file uploads. Files and taggedCompanyIds are optional. If taggedCompanyIds is not provided, the user's companyId from context will be used.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Multipart request containing JSON data and optional files",
      required = true)
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign comment added successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": \"Campaign comment added successfully.\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> generateCampaignComments(
      @Parameter(description = "Campaign ID", example = "691d8b66f70fcd0aeb0ed203")
          @PathVariable("campaignId")
          String campaignId,
      @Parameter(
              description = "Request body containing comment and taggedCompanyIds",
              required = true,
              content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
          @RequestPart(value = "request")
          @Valid
          CampaignCommentsRequestDTO request,
      @Parameter(description = "List of files to upload")
          @RequestPart(value = "files", required = false)
          List<MultipartFile> files) {

    log.info(
        "Generating campaign comment for campaignId: {} with {} files",
        campaignId,
        files != null ? files.size() : 0);

    // Get companyId from user context
    String companyId = userService.getActingCompanyId();

    // Create campaign comment (file uploads are handled in the service)
    campaignService.createCampaignComment(
        campaignId, request.getComment(), files, request.getTaggedCompanyIds(), companyId);

    log.info("Campaign comment created successfully for campaignId: {}", campaignId);

    return ApiResponse.success("Campaign comment added successfully.");
  }

  /**
   * Get campaign comments by campaign ID.
   *
   * @param campaignId Campaign ID from path variable
   * @return List of campaign comment response DTOs
   */
  @GetMapping("/{campaignId}/comments")
  @Operation(
      summary = "Get campaign comments by campaign ID",
      description =
          "Retrieve all comments for a campaign. The businessType is determined using the user's companyId stored in the taggedCompanyIds field.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Campaign comments retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<CampaignCommentsResponseDTO>> getCommentsByCampaignId(
      @Parameter(description = "Campaign ID", example = "691d8b66f70fcd0aeb0ed203")
          @PathVariable("campaignId")
          String campaignId) {

    log.info("Getting comments for campaignId: {}", campaignId);

    // Get comments from service (businessType is determined from companyId stored in each comment)
    List<CampaignCommentsResponseDTO> comments =
        campaignService.getCommentsByCampaignId(campaignId);

    log.info("Retrieved {} comments for campaignId: {}", comments.size(), campaignId);

    return ApiResponse.success(comments);
  }

  // ============================================================================
  // Schedule Management
  // ============================================================================

  /**
   * Get schedules for given inventory IDs. Returns schedules grouped by inventory ID. If no
   * inventory IDs are provided, returns all schedules for the campaign. Supports optional filtering
   * by inventoryType.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing optional list of inventory IDs and optional inventoryType
   *     filter
   * @return List of InventorySchedulesResponseDTO containing schedules grouped by inventory ID
   */
  @PostMapping("/{campaignId}/schedules")
  @Operation(
      summary = "Get schedules by inventory IDs",
      description =
          "Retrieve all schedules from CampaignInventorySchedules for given inventory IDs. "
              + "If inventoryIds is empty or not provided, returns all schedules for the campaign. "
              + "Supports optional filtering by inventoryType. "
              + "Returns schedules grouped by inventory ID with complete schedule details including "
              + "dates, days, duration, spots, and ad play configurations.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Schedules retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<List<InventorySchedulesResponseDTO>> getSchedulesByInventoryIds(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody GetSchedulesRequestDTO request) {

    log.info(
        "Getting schedules for campaignId: {} with {} inventory IDs and inventoryType filter: {}",
        campaignId,
        request.getInventoryIds() != null ? request.getInventoryIds().size() : 0,
        request.getInventoryType());

    // Validate campaign exists
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    List<InventorySchedulesResponseDTO> schedules =
        inventorySchedulesService.getSchedulesByInventoryIds(
            campaignId, request.getInventoryIds(), request.getInventoryType());

    log.info(
        "Successfully retrieved schedules for {} inventories for campaignId: {}",
        schedules.size(),
        campaignId);

    return ApiResponse.success(schedules);
  }

  /**
   * Create or update schedules for multiple inventories in bulk. This endpoint enables manual
   * optimized scheduling for multiple inventories, ensuring consistent schedule structure, dynamic
   * schedule naming, and inventory-based metadata auto-population.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing inventory IDs, clearSchedules flag, and schedule details
   * @return Success response
   */
  @PostMapping("/{campaignId}/inventory/bulk-schedules")
  @Operation(
      summary = "Bulk create/update schedules for multiple inventories",
      description =
          "Create or update schedules for multiple inventories in bulk. "
              + "When clearSchedules=true, all existing schedules are cleared and a new Schedule is created. "
              + "When clearSchedules=false, a new schedule is appended. "
              + "Backend-managed fields (duration, spotsPerLoop, spotsPerHour, adPlays) are automatically computed from inventory configuration.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Bulk schedules created/updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> bulkSchedules(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody BulkSchedulesRequestDTO request) {

    log.info(
        "Processing bulk schedules for campaignId: {} with {} inventories, clearSchedules: {}",
        campaignId,
        request.getInventoryIds().size(),
        request.isClearSchedules());

    // Validate campaign exists
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    inventorySchedulesService.bulkSchedules(campaignId, request);

    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.operation_completed", locale);

    log.info(
        "Successfully processed bulk schedules for {} inventories in campaignId: {}",
        request.getInventoryIds().size(),
        campaignId);

    return ApiResponse.success(successMessage);
  }

  /**
   * Add a new schedule for a campaign and inventory. Creates the schedule and adds it to the
   * CampaignInventorySchedules.
   *
   * @param campaignId Campaign ID from path variable
   * @param request Request containing inventoryId and schedule details
   * @return Success response
   */
  @PostMapping("/{campaignId}/schedules/add")
  @Operation(
      summary = "Add new schedule",
      description =
          "Add a new schedule for a campaign and inventory. Automatically generates schedule name if not provided "
              + "and recalculates computed fields (duration, spotsPerHour, adPlays, plannedSot, totalSot) based on inventory configuration.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Schedule added successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or inventory not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> addSchedule(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Valid @RequestBody AddScheduleRequestDTO request) {

    log.info(
        "Adding new schedule for campaignId: {}, inventoryId: {}",
        campaignId,
        request.getInventoryId());

    // Validate campaign exists
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    inventorySchedulesService.addSchedule(campaignId, request);

    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.schedule_completed", locale);

    log.info(
        "Successfully added schedule for campaignId: {}, inventoryId: {}",
        campaignId,
        request.getInventoryId());

    return ApiResponse.success(successMessage);
  }

  /**
   * Edit a schedule by its ID. Updates the schedule with new data and recalculates computed fields.
   *
   * @param campaignId Campaign ID from path variable
   * @param scheduleId Schedule ID to edit
   * @param request Request containing schedule update data
   * @return Success response
   */
  @PutMapping("/{campaignId}/schedules/{scheduleId}")
  @Operation(
      summary = "Edit schedule by ID",
      description =
          "Edit a schedule by its ID. Updates the schedule with new data and automatically recalculates "
              + "computed fields (duration, spotsPerHour, adPlays, plannedSot, totalSot) based on inventory configuration.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Schedule edited successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or schedule not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> editScheduleById(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Schedule ID", example = "schedule123") @PathVariable
          String scheduleId,
      @Valid @RequestBody EditScheduleRequestDTO request) {

    log.info("Editing schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    // Validate campaign exists
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    inventorySchedulesService.editScheduleById(campaignId, scheduleId, request);

    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.schedule_completed", locale);

    log.info("Successfully edited schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    return ApiResponse.success(successMessage);
  }

  /**
   * Delete a schedule by its ID. Removes the schedule and updates the CampaignInventorySchedules.
   *
   * @param campaignId Campaign ID from path variable
   * @param scheduleId Schedule ID to delete
   * @return Success response
   */
  @DeleteMapping("/{campaignId}/schedules/{scheduleId}")
  @Operation(
      summary = "Delete schedule by ID",
      description =
          "Delete a schedule by its ID. Removes the schedule from the database and updates the "
              + "CampaignInventorySchedules to remove the schedule ID from the list.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Schedule deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign or schedule not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error")
      })
  public ApiResponse<String> deleteScheduleById(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId,
      @Parameter(description = "Schedule ID", example = "schedule123") @PathVariable
          String scheduleId) {

    log.info("Deleting schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    // Validate campaign exists
    // Guarded load: validates existence, data mode, and acting-company participation.
    campaignService.findByIdForCurrentMode(campaignId);

    inventorySchedulesService.deleteScheduleById(campaignId, scheduleId);

    Locale locale = getUserLocale();
    String successMessage = messageService.getMessage("success.operation_completed", locale);

    log.info(
        "Successfully deleted schedule with ID: {} for campaignId: {}", scheduleId, campaignId);

    return ApiResponse.success(successMessage);
  }

  @GetMapping("/{campaignId}/inventories-mapping")
  @Operation(
      summary = "Get inventories mapping",
      description =
          "Returns each selected campaign inventory mapped to its nearest geofencing location"
              + " (from campaign targeting) with computed distance in meters, sorted ascending.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventories mapping retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Campaign not found")
      })
  public ApiResponse<List<InventoriesMappingResponseDTO>> getInventoriesMapping(
      @Parameter(description = "Campaign ID", example = "campaign123") @PathVariable
          String campaignId) {

    log.info("Getting inventories mapping for campaignId: {}", campaignId);

    Campaign campaign = campaignService.findByIdForCurrentMode(campaignId);

    List<Campaign.Targeting.Geofencing.Location> geoLocations =
        (campaign.getTargeting() != null && campaign.getTargeting().getGeofencing() != null)
            ? campaign.getTargeting().getGeofencing().getLocations()
            : java.util.Collections.emptyList();

    return ApiResponse.success(
        inventoryImportService.getInventoriesMapping(campaignId, geoLocations));
  }
}
