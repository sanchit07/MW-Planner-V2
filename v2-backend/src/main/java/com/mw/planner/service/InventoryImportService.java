package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignGeoImportFile;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.SelectInventoryImports;
import com.mw.planner.dto.GeoCoordinatesImportRequestDTO;
import com.mw.planner.dto.GeoImportFileResponseDTO;
import com.mw.planner.dto.ImportInventoryDetailResponseDTO;
import com.mw.planner.dto.InventoriesMappingResponseDTO;
import com.mw.planner.dto.InventoryImportFileResponseDTO;
import com.mw.planner.dto.InventoryImportStatusResponseDTO;
import com.mw.planner.dto.InventoryImportStatusResponseDTO.ValidationResult;
import com.mw.planner.dto.InventoryImportStatusResponseDTO.ValidationType;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.csv.CsvUploadException;
import com.mw.planner.exception.inventory.InventoryImportException;
import com.mw.planner.repository.CampaignGeoImportFileRepository;
import com.mw.planner.repository.InventoryRepository;
import com.mw.planner.repository.SelectInventoryImportsRepository;
import com.mw.planner.service.CsvParsingService.InventoryIdRecord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for inventory import functionality. Handles CSV file validation, inventory import and
 * select inventory
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryImportService {

  private final CsvParsingService csvParsingService;
  private final InventoryService inventoryService;
  private final SelectInventoryImportsRepository selectInventoryImportsRepository;
  private final CampaignInventorySchedulesService campaignInventorySchedulesService;
  private final InventoryRepository inventoryRepository;
  private final CampaignGeoImportFileRepository campaignGeoImportFileRepository;
  private final CampaignActivityService campaignActivityService;

  /**
   * Verify CSV file containing inventory reference IDs against the database. Performs bulk
   * validation: - Checks if inventory reference IDs exist - Validates country match - Detects
   * duplicate reference IDs within CSV - Detects duplicate reference IDs already selected in
   * campaign
   *
   * @param csvFile CSV file with inventory_id header
   * @param campaignId Campaign ID to check for already selected inventories
   * @param countryName Country name for validation
   * @return List of validation results for each inventory reference ID
   * @throws IOException if file cannot be read
   */
  public InventoryImportStatusResponseDTO verifyCsvFile(
      MultipartFile csvFile, String campaignId, String countryName) throws IOException {
    log.info(
        "Verifying CSV file: {} for campaignId: {} and country: {}",
        csvFile.getOriginalFilename(),
        campaignId,
        countryName);

    // Parse CSV file
    List<InventoryIdRecord> records = parseCsvFile(csvFile);

    if (records.isEmpty()) {
      log.warn("No inventory reference IDs found in CSV file");
      return InventoryImportStatusResponseDTO.builder().results(new ArrayList<>()).build();
    }

    // Prepare validation data
    ValidationContext context = prepareValidationContext(records, campaignId);

    // Build validation results
    List<ValidationResult> results = validateRecords(records, context, countryName);

    log.info(
        "CSV verification completed: {} total records, {} valid, {} invalid, {} duplicate",
        results.size(),
        results.stream().filter(r -> r.getType() == ValidationType.VALID).count(),
        results.stream().filter(r -> r.getType() == ValidationType.INVALID).count(),
        results.stream().filter(r -> r.getType() == ValidationType.DUPLICATE).count());

    return InventoryImportStatusResponseDTO.builder().results(results).build();
  }

  /**
   * Upload and save CSV file containing inventory reference IDs. Validates all data using the same
   * logic as verifyCsvFile. If any data is invalid, throws a single exception. If all data is
   * valid, saves to SelectInventoryImports table.
   *
   * @param csvFile CSV file with inventory_id header
   * @param campaignId Campaign ID
   * @param countryName Country name for validation
   * @param companyId Company ID
   * @throws CsvUploadException if any validation fails
   * @throws IOException if file cannot be read
   */
  public void uploadCsvFile(
      MultipartFile csvFile, String campaignId, String countryName, String companyId)
      throws IOException {
    log.info(
        "Uploading CSV file: {} for campaignId: {} and country: {}",
        csvFile.getOriginalFilename(),
        campaignId,
        countryName);

    // Parse CSV file
    List<InventoryIdRecord> records = parseCsvFile(csvFile);

    if (records.isEmpty()) {
      log.warn("No inventory reference IDs found in CSV file");
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_INVALID_FILE, "Invalid file data please verify");
    }

    // Prepare validation data
    ValidationContext context = prepareValidationContext(records, campaignId);

    // Validate all records - if any is invalid, throw exception
    validateAllRecords(records, context, countryName);

    // All validations passed - extract unique valid reference IDs and get inventory IDs
    List<String> validReferenceIds =
        records.stream().map(InventoryIdRecord::getId).distinct().collect(Collectors.toList());

    // Get inventory IDs from reference IDs
    List<String> inventoryIds = getInventoryIdsFromReferenceIds(validReferenceIds, context);

    // Bulk select inventories before saving to SelectInventoryImports
    int selectedCount =
        campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    log.info(
        "Bulk selected {} inventories for campaignId: {} from CSV file", selectedCount, campaignId);

    // Save to SelectInventoryImports
    SelectInventoryImports selectInventoryImport =
        SelectInventoryImports.builder()
            .companyId(companyId)
            .campaignId(campaignId)
            .fileName(csvFile.getOriginalFilename())
            .inventoryRefIds(validReferenceIds)
            .countryName(countryName)
            .build();

    selectInventoryImportsRepository.save(selectInventoryImport);

    // Log activity for CSV upload
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.ADDED,
          CSV_UPLOAD_SELECTED_COUNT.key(),
          selectedCount,
          CSV_UPLOAD_FILENAME.key(),
          csvFile.getOriginalFilename());
    } catch (Exception e) {
      log.warn("Failed to log CSV upload activity: {}", e.getMessage());
    }

    log.info(
        "Successfully uploaded and saved CSV file: {} with {} valid inventory reference IDs",
        csvFile.getOriginalFilename(),
        validReferenceIds.size());
  }

  /**
   * Get inventory IDs from reference IDs using the validation context.
   *
   * @param referenceIds List of reference IDs
   * @param context Validation context containing inventory map
   * @return List of inventory IDs
   */
  private List<String> getInventoryIdsFromReferenceIds(
      List<String> referenceIds, ValidationContext context) {
    return referenceIds.stream()
        .map(context.inventoryMap::get)
        .filter(Objects::nonNull)
        .map(Inventory::getId)
        .collect(Collectors.toList());
  }

  /**
   * Parse CSV file and return list of inventory ID records.
   *
   * @param csvFile CSV file to parse
   * @return List of inventory ID records
   * @throws IOException if file cannot be read
   */
  private List<InventoryIdRecord> parseCsvFile(MultipartFile csvFile) throws IOException {
    InputStream inputStream = csvFile.getInputStream();
    return csvParsingService.parseInventoryIdCsvFile(inputStream);
  }

  /**
   * Prepare validation context by fetching inventories and selected reference IDs.
   *
   * @param records CSV records
   * @param campaignId Campaign ID
   * @return Validation context with inventory map, selected reference IDs, and duplicate IDs
   */
  private ValidationContext prepareValidationContext(
      List<InventoryIdRecord> records, String campaignId) {
    // Extract unique reference IDs for bulk query
    List<String> referenceIds =
        records.stream().map(InventoryIdRecord::getId).distinct().collect(Collectors.toList());

    log.info("Found {} unique inventory reference IDs in CSV file", referenceIds.size());

    // Build inventory map
    Map<String, Inventory> inventoryMap = buildInventoryMap(referenceIds);

    // Get selected reference IDs for campaign
    Set<String> selectedReferenceIds = getSelectedReferenceIds(campaignId);

    // Find duplicate IDs in CSV
    Set<String> duplicateIdsInCsv = findDuplicateIdsInCsv(records);

    return new ValidationContext(inventoryMap, selectedReferenceIds, duplicateIdsInCsv);
  }

  /**
   * Build a map of reference ID to Inventory for quick lookup.
   *
   * @param referenceIds List of reference IDs
   * @return Map of reference ID to Inventory
   */
  private Map<String, Inventory> buildInventoryMap(List<String> referenceIds) {
    List<Inventory> inventories = inventoryService.findAllByReferenceIds(referenceIds);
    return inventories.stream()
        .filter(inv -> inv.getReferenceId() != null)
        .collect(Collectors.toMap(Inventory::getReferenceId, inv -> inv));
  }

  /**
   * Get set of selected reference IDs for a campaign.
   *
   * @param campaignId Campaign ID
   * @return Set of selected reference IDs
   */
  private Set<String> getSelectedReferenceIds(String campaignId) {
    List<String> selectedInventoryIds =
        campaignInventorySchedulesService.findByCampaignId(campaignId).stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .collect(Collectors.toList());

    if (selectedInventoryIds.isEmpty()) {
      return new HashSet<>();
    }

    List<Inventory> selectedInventories = inventoryService.findAllByIds(selectedInventoryIds);
    return selectedInventories.stream()
        .map(Inventory::getReferenceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Find duplicate reference IDs within CSV records.
   *
   * @param records CSV records
   * @return Set of duplicate reference IDs
   */
  private Set<String> findDuplicateIdsInCsv(List<InventoryIdRecord> records) {
    Map<String, Integer> idCountMap = new HashMap<>();
    for (InventoryIdRecord record : records) {
      idCountMap.put(record.getId(), idCountMap.getOrDefault(record.getId(), 0) + 1);
    }

    return idCountMap.entrySet().stream()
        .filter(entry -> entry.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Validate all records and return validation results.
   *
   * @param records CSV records
   * @param context Validation context
   * @param countryName Country name for validation
   * @return List of validation results
   */
  private List<ValidationResult> validateRecords(
      List<InventoryIdRecord> records, ValidationContext context, String countryName) {
    List<ValidationResult> results = new ArrayList<>();

    for (InventoryIdRecord record : records) {
      String referenceId = record.getId();
      Integer row = record.getRow();

      ValidationResult result = validateSingleRecord(referenceId, row, context, countryName, true);
      results.add(result);
    }

    return results;
  }

  /**
   * Validate all records and throw exception if any is invalid.
   *
   * @param records CSV records
   * @param context Validation context
   * @param countryName Country name for validation
   * @throws CsvUploadException if any validation fails
   */
  private void validateAllRecords(
      List<InventoryIdRecord> records, ValidationContext context, String countryName) {
    for (InventoryIdRecord record : records) {
      String referenceId = record.getId();
      validateSingleRecord(referenceId, record.getRow(), context, countryName, false);
    }
  }

  /**
   * Validate a single inventory record.
   *
   * @param referenceId Reference ID to validate
   * @param row Row number in CSV
   * @param context Validation context
   * @param countryName Country name for validation
   * @param returnResult If true, returns validation result; if false, throws exception on
   *     validation failure
   * @return Validation result (only if returnResult is true)
   * @throws CsvUploadException if validation fails and returnResult is false
   */
  private ValidationResult validateSingleRecord(
      String referenceId,
      Integer row,
      ValidationContext context,
      String countryName,
      boolean returnResult) {
    // Check if inventory exists
    Inventory inventory = context.inventoryMap.get(referenceId);
    if (inventory == null) {
      log.warn("Inventory reference ID does not exist: {}", referenceId);
      if (returnResult) {
        return ValidationResult.builder()
            .id(referenceId)
            .type(ValidationType.INVALID)
            .message("Inventory reference ID does not exist")
            .row(row)
            .build();
      } else {
        throw new CsvUploadException(
            ErrorCode.CSV_UPLOAD_INVALID_FILE, "Invalid file data please verify");
      }
    }

    // Check country match
    String inventoryCountry =
        inventory.getLocation() != null ? inventory.getLocation().getCountry() : null;
    if (inventoryCountry == null || !inventoryCountry.equalsIgnoreCase(countryName)) {
      log.warn(
          "Inventory country '{}' does not match required country '{}' for reference ID: {}",
          inventoryCountry != null ? inventoryCountry : "unknown",
          countryName,
          referenceId);
      if (returnResult) {
        return ValidationResult.builder()
            .id(referenceId)
            .type(ValidationType.INVALID)
            .message(
                String.format(
                    "Inventory country '%s' does not match required country '%s'",
                    inventoryCountry != null ? inventoryCountry : "unknown", countryName))
            .row(row)
            .build();
      } else {
        throw new CsvUploadException(
            ErrorCode.CSV_UPLOAD_INVALID_FILE, "Invalid file data please verify");
      }
    }

    // At this point, inventory is valid (exists and country matches)
    // Now check for duplicates

    // Check if inventory is already selected in campaign
    if (context.selectedReferenceIds.contains(referenceId)) {
      log.warn("Inventory reference ID is already selected in the campaign: {}", referenceId);
      if (returnResult) {
        return ValidationResult.builder()
            .id(referenceId)
            .type(ValidationType.DUPLICATE)
            .message("Inventory reference ID is already selected in the campaign")
            .row(row)
            .build();
      } else {
        log.info("Inventory already selected, skipping: {}", referenceId);
        return null;
      }
    }

    // Check for duplicates within CSV
    if (context.duplicateIdsInCsv.contains(referenceId)) {
      log.warn("Inventory reference ID appears multiple times in CSV file: {}", referenceId);
      if (returnResult) {
        return ValidationResult.builder()
            .id(referenceId)
            .type(ValidationType.DUPLICATE)
            .message("Inventory reference ID appears multiple times in CSV file")
            .row(row)
            .build();
      } else {
        log.info("Duplicate inventory ID in CSV, skipping: {}", referenceId);
        return null;
      }
    }

    // Valid inventory (exists, country matches, not duplicate)
    if (returnResult) {
      return ValidationResult.builder()
          .id(referenceId)
          .type(ValidationType.VALID)
          .message("Inventory is valid")
          .row(row)
          .build();
    }
    return null;
  }

  /**
   * Get paginated inventory details for a specific import record.
   *
   * @param importId Import record ID
   * @param pageable Pagination and sorting information
   * @return Page of inventory details
   */
  public Page<ImportInventoryDetailResponseDTO> getInventoriesByImportId(
      String importId, Pageable pageable) {
    log.info("Fetching inventories for importId: {} with pagination: {}", importId, pageable);

    // Find the import record
    SelectInventoryImports importRecord =
        selectInventoryImportsRepository
            .findById(importId)
            .orElseThrow(
                () -> {
                  log.warn("Import record not found: {}", importId);
                  return new CsvUploadException(
                      ErrorCode.INVENTORY_IMPORTS_NOT_FOUND, "Import record not found");
                });

    // Get reference IDs from import record
    List<String> referenceIds = importRecord.getInventoryRefIds();
    if (referenceIds == null || referenceIds.isEmpty()) {
      log.info("No inventory reference IDs found for importId: {}", importId);
      return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }

    // Fetch inventories with pagination
    Page<Inventory> inventoryPage = inventoryRepository.findByReferenceIdIn(referenceIds, pageable);

    // Convert to DTOs
    List<ImportInventoryDetailResponseDTO> responseDTOs =
        inventoryPage.getContent().stream()
            .map(ImportInventoryDetailResponseDTO::fromEntity)
            .collect(Collectors.toList());

    log.info(
        "Found {} inventories for importId: {} (page {} of {})",
        responseDTOs.size(),
        importId,
        pageable.getPageNumber(),
        inventoryPage.getTotalPages());

    return new PageImpl<>(responseDTOs, pageable, inventoryPage.getTotalElements());
  }

  /**
   * Get paginated inventory import files by company ID and country name.
   *
   * @param companyId Company ID to filter by
   * @param countryName Country name to filter by
   * @param pageable Pagination and sorting parameters
   * @return Page of InventoryImportFileResponseDTO
   */
  public Page<InventoryImportFileResponseDTO> getInventoryImportFilesByCountry(
      String companyId, String countryName, Pageable pageable) {
    log.debug(
        "Fetching inventory import files for companyId: {} and countryName: {}",
        companyId,
        countryName);

    Page<SelectInventoryImports> importsPage =
        selectInventoryImportsRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable);

    List<InventoryImportFileResponseDTO> responseList =
        importsPage.getContent().stream()
            .map(
                importFile ->
                    InventoryImportFileResponseDTO.builder()
                        .id(importFile.getId())
                        .fileName(importFile.getFileName())
                        .inventoryRefIdCount(
                            importFile.getInventoryRefIds() != null
                                ? importFile.getInventoryRefIds().size()
                                : 0)
                        .createdBy(importFile.getCreatedBy())
                        .createdAt(importFile.getCreatedAt())
                        .build())
            .collect(Collectors.toList());

    return new PageImpl<>(responseList, pageable, importsPage.getTotalElements());
  }

  /**
   * Use existing inventory import by ID. Fetches import data, validates inventory reference IDs,
   * and adds them to selected inventory.
   *
   * @param campaignId Campaign ID from path parameter
   * @param importId Import ID
   * @throws InventoryImportException if validation fails or import not found
   */
  public void useInventoryImport(String campaignId, String importId) {
    log.info("Using inventory import with ID: {} for campaignId: {}", importId, campaignId);

    // Fetch import data
    SelectInventoryImports importData =
        selectInventoryImportsRepository
            .findById(importId)
            .orElseThrow(() -> InventoryImportException.notFound(importId));

    List<String> referenceIds = importData.getInventoryRefIds();
    if (referenceIds == null || referenceIds.isEmpty()) {
      log.warn("No inventory reference IDs found in import: {}", importId);
      throw InventoryImportException.empty(importId);
    }

    String countryName = importData.getCountryName();

    // Build inventory map for validation - single DB call
    Map<String, Inventory> inventoryMap = buildInventoryMap(referenceIds);

    // Validate reference IDs - check existence, country match, and if already selected
    for (String referenceId : referenceIds) {
      Inventory inventory = inventoryMap.get(referenceId);
      if (inventory == null) {
        log.warn("Inventory reference ID does not exist: {}", referenceId);
        throw InventoryImportException.validationFailed(
            "Inventory reference ID does not exist: " + referenceId);
      }

      // Check country match
      String inventoryCountry =
          inventory.getLocation() != null ? inventory.getLocation().getCountry() : null;
      if (inventoryCountry == null || !inventoryCountry.equalsIgnoreCase(countryName)) {
        log.warn(
            "Inventory country '{}' does not match required country '{}' for reference ID: {}",
            inventoryCountry != null ? inventoryCountry : "unknown",
            countryName,
            referenceId);
        throw InventoryImportException.validationFailed(
            String.format(
                "Inventory country '%s' does not match required country '%s' for reference ID: %s",
                inventoryCountry != null ? inventoryCountry : "unknown", countryName, referenceId));
      }
    }

    // All validations passed - get inventory IDs from reference IDs
    List<String> inventoryIds =
        referenceIds.stream()
            .map(inventoryMap::get)
            .filter(Objects::nonNull)
            .map(Inventory::getId)
            .collect(Collectors.toList());

    // Bulk select inventories
    int selectedCount =
        campaignInventorySchedulesService.bulkSelectInventoriesByIds(campaignId, inventoryIds);

    // Log activity for using inventory import
    try {
      campaignActivityService.logActivity(
          campaignId,
          CampaignActivityService.OperationType.ADDED,
          INVENTORY_IMPORT_SELECTED_COUNT.key(),
          selectedCount,
          INVENTORY_IMPORT_FILENAME.key(),
          importData.getFileName());
    } catch (Exception e) {
      log.warn("Failed to log inventory import usage activity: {}", e.getMessage());
    }

    log.info(
        "Successfully used inventory import {} for campaignId: {} - Selected {} inventories",
        importId,
        campaignId,
        selectedCount);
  }

  /**
   * Generate CSV file content for inventory import data by ID.
   *
   * @param importId Import ID
   * @return CSV file content as byte array and filename
   * @throws InventoryImportException if import not found
   */
  public CsvFileResult generateInventoryImportCsv(String importId) {
    log.info("Generating CSV file for inventory import ID: {}", importId);

    // Fetch import data
    SelectInventoryImports importData =
        selectInventoryImportsRepository
            .findById(importId)
            .orElseThrow(() -> InventoryImportException.notFound(importId));

    String fileName = importData.getFileName();
    if (fileName == null || fileName.trim().isEmpty()) {
      // Fallback to default filename if not set
      fileName = "inventory_import_" + importId + ".csv";
      log.warn("FileName not found in import data, using default: {}", fileName);
    }

    List<String> referenceIds = importData.getInventoryRefIds();
    if (referenceIds == null || referenceIds.isEmpty()) {
      log.warn("No inventory reference IDs found in import: {}", importId);
      return new CsvFileResult(fileName, generateCsvContent(new ArrayList<>()));
    }

    // Generate CSV with inventory_id header and reference IDs as data
    return new CsvFileResult(fileName, generateCsvContent(referenceIds));
  }

  /** Result class for CSV file generation containing filename and content. */
  public record CsvFileResult(String fileName, byte[] content) {}

  /**
   * Delete inventory import by ID.
   *
   * @param importId Import ID
   * @throws InventoryImportException if import not found
   */
  public void deleteInventoryImport(String importId) {
    log.info("Deleting inventory import with ID: {}", importId);

    // Fetch import data before deletion to get campaignId and filename for activity logging
    SelectInventoryImports importData =
        selectInventoryImportsRepository
            .findById(importId)
            .orElseThrow(() -> InventoryImportException.notFound(importId));

    String campaignId = importData.getCampaignId();
    String fileName = importData.getFileName();

    // Delete import
    selectInventoryImportsRepository.deleteById(importId);

    // Log activity for deleting inventory import
    if (campaignId != null) {
      try {
        campaignActivityService.logActivity(
            campaignId,
            CampaignActivityService.OperationType.REMOVED,
            INVENTORY_IMPORT_DELETED_FILENAME.key(),
            fileName);
      } catch (Exception e) {
        log.warn("Failed to log inventory import deletion activity: {}", e.getMessage());
      }
    }

    log.info("Successfully deleted inventory import with ID: {}", importId);
  }

  /**
   * Generate CSV content from list of reference IDs.
   *
   * @param referenceIds List of reference IDs
   * @return CSV file content as byte array
   */
  private byte[] generateCsvContent(List<String> referenceIds) {
    StringBuilder csvContent = new StringBuilder();
    // Add header
    csvContent.append("inventory_id\n");

    // Add data rows
    for (String referenceId : referenceIds) {
      csvContent.append(referenceId).append("\n");
    }

    return csvContent.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Internal class to hold validation context data. */
  private record ValidationContext(
      Map<String, Inventory> inventoryMap,
      Set<String> selectedReferenceIds,
      Set<String> duplicateIdsInCsv) {}

  /**
   * Import geo coordinates from request DTO and save to database.
   *
   * @param request Geo coordinates import request DTO
   * @param companyId Company ID
   * @throws CsvUploadException if validation fails
   */
  public void importGeoCoordinates(GeoCoordinatesImportRequestDTO request, String companyId) {
    log.info(
        "Importing geo coordinates from request: {} for country: {}",
        request.getFileName(),
        request.getCountryName());

    // Validate and convert DTO to domain model
    List<CampaignGeoImportFile.GeoDetails> geoDetailsList =
        convertGeoDetailsFromDTO(request.getGeoDetails());

    if (geoDetailsList.isEmpty()) {
      log.warn("No geo coordinates found in request");
      throw new CsvUploadException(
          ErrorCode.CSV_UPLOAD_INVALID_FILE, "Geo details list cannot be empty");
    }

    // Save to CampaignGeoImportFile
    CampaignGeoImportFile geoImportFile =
        CampaignGeoImportFile.builder()
            .fileName(request.getFileName())
            .countryName(request.getCountryName())
            .companyId(companyId)
            .geoDetails(geoDetailsList)
            .build();

    campaignGeoImportFileRepository.save(geoImportFile);

    log.info(
        "Successfully imported {} geo coordinates from request: {}",
        geoDetailsList.size(),
        request.getFileName());
  }

  /**
   * Convert GeoDetailDTO list to CampaignGeoImportFile.GeoDetails list with validation.
   *
   * @param geoDetailDTOs List of GeoDetailDTO
   * @return List of CampaignGeoImportFile.GeoDetails
   * @throws CsvUploadException if validation fails
   */
  private List<CampaignGeoImportFile.GeoDetails> convertGeoDetailsFromDTO(
      List<GeoCoordinatesImportRequestDTO.GeoDetailDTO> geoDetailDTOs) {
    List<CampaignGeoImportFile.GeoDetails> geoDetailsList = new ArrayList<>();

    for (GeoCoordinatesImportRequestDTO.GeoDetailDTO dto : geoDetailDTOs) {
      CampaignGeoImportFile.GeoDetails geoDetails =
          CampaignGeoImportFile.GeoDetails.builder()
              .locationName(dto.getLocationName().trim())
              .latitude(dto.getLatitude().trim())
              .longitude(dto.getLongitude().trim())
              .siteType(dto.getSiteType() != null ? dto.getSiteType().trim() : null)
              .radius(dto.getRadius() != null ? dto.getRadius().trim() : null)
              .build();
      geoDetailsList.add(geoDetails);
    }

    return geoDetailsList;
  }

  /**
   * Get geo details list by geo import file ID.
   *
   * @param geoImportId Geo import file ID
   * @return List of GeoDetailsDTO
   * @throws CsvUploadException if geo import file not found
   */
  public List<GeoImportFileResponseDTO.GeoDetailsDTO> getGeoImportFileById(String geoImportId) {
    log.info("Fetching geo import file by ID: {}", geoImportId);

    CampaignGeoImportFile geoImportFile =
        campaignGeoImportFileRepository
            .findById(geoImportId)
            .orElseThrow(
                () -> {
                  log.warn("Geo import file not found: {}", geoImportId);
                  return new CsvUploadException(
                      ErrorCode.INVENTORY_IMPORTS_NOT_FOUND, "Geo import file not found");
                });

    List<GeoImportFileResponseDTO.GeoDetailsDTO> geoDetailsList =
        GeoImportFileResponseDTO.toGeoDetailsList(geoImportFile);

    log.info(
        "Successfully retrieved {} geo details for geo import file: {}",
        geoDetailsList.size(),
        geoImportId);

    return geoDetailsList;
  }

  /**
   * Generate CSV file content for geo import data by ID.
   *
   * @param geoImportId Geo import file ID
   * @return CSV file content as byte array and filename
   * @throws CsvUploadException if geo import file not found
   */
  public CsvFileResult generateGeoImportCsv(String geoImportId) {
    log.info("Generating CSV file for geo import ID: {}", geoImportId);

    // Fetch geo import data
    CampaignGeoImportFile geoImportFile =
        campaignGeoImportFileRepository
            .findById(geoImportId)
            .orElseThrow(
                () -> {
                  log.warn("Geo import file not found: {}", geoImportId);
                  return new CsvUploadException(
                      ErrorCode.INVENTORY_IMPORTS_NOT_FOUND, "Geo import file not found");
                });

    String fileName = geoImportFile.getFileName();
    if (fileName == null || fileName.trim().isEmpty()) {
      // Fallback to default filename if not set
      fileName = "geo_import_" + geoImportId + ".csv";
      log.warn("FileName not found in geo import data, using default: {}", fileName);
    }

    List<CampaignGeoImportFile.GeoDetails> geoDetails = geoImportFile.getGeoDetails();
    if (geoDetails == null || geoDetails.isEmpty()) {
      log.warn("No geo details found in import: {}", geoImportId);
      return new CsvFileResult(fileName, generateGeoCoordinatesCsvContent(new ArrayList<>()));
    }

    // Generate CSV with Location, Latitude, Longitude, Type, Radius headers
    return new CsvFileResult(fileName, generateGeoCoordinatesCsvContent(geoDetails));
  }

  /**
   * Generate CSV content from list of geo details.
   *
   * @param geoDetails List of geo details
   * @return CSV file content as byte array
   */
  private byte[] generateGeoCoordinatesCsvContent(
      List<CampaignGeoImportFile.GeoDetails> geoDetails) {
    StringBuilder csvContent = new StringBuilder();
    // Add header
    csvContent.append("Location,Latitude,Longitude,Type,Radius\n");

    // Add data rows
    for (CampaignGeoImportFile.GeoDetails geoDetail : geoDetails) {
      csvContent
          .append(escapeCsvValue(geoDetail.getLocationName()))
          .append(",")
          .append(escapeCsvValue(geoDetail.getLatitude()))
          .append(",")
          .append(escapeCsvValue(geoDetail.getLongitude()))
          .append(",")
          .append(escapeCsvValue(geoDetail.getSiteType()))
          .append(",")
          .append(escapeCsvValue(geoDetail.getRadius()))
          .append("\n");
    }

    return csvContent.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Escape CSV value if it contains commas, quotes, or newlines.
   *
   * @param value Value to escape
   * @return Escaped value
   */
  private String escapeCsvValue(String value) {
    if (value == null) {
      return "";
    }
    // If value contains comma, quote, or newline, wrap in quotes and escape quotes
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  /**
   * Delete geo import file by ID.
   *
   * @param geoImportId Geo import file ID
   * @throws CsvUploadException if geo import file not found
   */
  public void deleteGeoImportFileById(String geoImportId) {
    log.info("Deleting geo import file with ID: {}", geoImportId);

    // Check if geo import file exists
    if (!campaignGeoImportFileRepository.existsById(geoImportId)) {
      log.warn("Geo import file not found: {}", geoImportId);
      throw new CsvUploadException(
          ErrorCode.INVENTORY_IMPORTS_NOT_FOUND, "Geo import file not found");
    }

    // Delete geo import file
    campaignGeoImportFileRepository.deleteById(geoImportId);

    log.info("Successfully deleted geo import file with ID: {}", geoImportId);
  }

  /**
   * Get paginated geo import files by company ID and country name.
   *
   * @param companyId Company ID to filter by
   * @param countryName Country name to filter by
   * @param pageable Pagination and sorting parameters
   * @return Page of GeoImportFileResponseDTO
   */
  public Page<GeoImportFileResponseDTO> getGeoImportFilesByCountry(
      String companyId, String countryName, Pageable pageable) {
    log.debug(
        "Fetching geo import files for companyId: {} and countryName: {}", companyId, countryName);

    Page<CampaignGeoImportFile> geoImportsPage =
        campaignGeoImportFileRepository.findByCompanyIdAndCountryName(
            companyId, countryName, pageable);

    List<GeoImportFileResponseDTO> responseList =
        geoImportsPage.getContent().stream()
            .map(
                geoImportFile ->
                    GeoImportFileResponseDTO.builder()
                        .id(geoImportFile.getId())
                        .fileName(geoImportFile.getFileName())
                        .countryName(geoImportFile.getCountryName())
                        .companyId(geoImportFile.getCompanyId())
                        .geoDetails(null) // Don't include full list in list view for performance
                        .countOfCoordinates(
                            geoImportFile.getGeoDetails() != null
                                ? geoImportFile.getGeoDetails().size()
                                : 0)
                        .createdBy(geoImportFile.getCreatedBy())
                        .lastModifiedBy(geoImportFile.getLastModifiedBy())
                        .createdAt(geoImportFile.getCreatedAt())
                        .updatedAt(geoImportFile.getUpdatedAt())
                        .build())
            .collect(Collectors.toList());

    return new PageImpl<>(responseList, pageable, geoImportsPage.getTotalElements());
  }

  /**
   * Returns each campaign inventory mapped to its nearest geofencing location (from campaign
   * targeting) with computed Haversine distance in meters, sorted by distance ascending.
   *
   * @param campaignId Campaign ID
   * @param geoLocations Geofencing locations from the campaign's targeting
   * @return List of inventory-to-location mappings
   */
  public List<InventoriesMappingResponseDTO> getInventoriesMapping(
      String campaignId, List<Campaign.Targeting.Geofencing.Location> geoLocations) {
    if (geoLocations == null || geoLocations.isEmpty()) {
      log.info("No geofencing locations configured for campaignId: {}", campaignId);
      return Collections.emptyList();
    }

    List<CampaignInventorySchedules> schedules =
        campaignInventorySchedulesService.findByCampaignId(campaignId);
    if (schedules.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> inventoryIds =
        schedules.stream()
            .map(CampaignInventorySchedules::getInventoryId)
            .distinct()
            .collect(Collectors.toList());

    List<Inventory> inventories = inventoryRepository.findAllById(inventoryIds);
    List<InventoriesMappingResponseDTO> result = new ArrayList<>();

    for (Inventory inventory : inventories) {
      if (inventory.getLocation() == null
          || inventory.getLocation().getLocationCoordinates() == null) {
        continue;
      }

      double[] invLatLng = extractPointLatLng(inventory.getLocation().getLocationCoordinates());
      if (invLatLng == null) continue;

      Campaign.Targeting.Geofencing.Location nearest = null;
      double minDistance = Double.MAX_VALUE;

      for (Campaign.Targeting.Geofencing.Location geo : geoLocations) {
        if (geo.getLat() == null || geo.getLng() == null) continue;
        double distance =
            haversineDistanceMeters(invLatLng[0], invLatLng[1], geo.getLat(), geo.getLng());
        if (distance < minDistance) {
          minDistance = distance;
          nearest = geo;
        }
      }

      if (nearest != null) {
        result.add(
            InventoriesMappingResponseDTO.builder()
                .name(nearest.getName())
                .latitude(String.valueOf(nearest.getLat()))
                .longitude(String.valueOf(nearest.getLng()))
                .billboardName(inventory.getName())
                .referenceId(inventory.getReferenceId())
                .distanceMeters(Math.round(minDistance * 100.0) / 100.0)
                .stateName(inventory.getLocation().getState())
                .districtName(inventory.getLocation().getCity())
                .build());
      }
    }

    result.sort(Comparator.comparingDouble(InventoriesMappingResponseDTO::getDistanceMeters));
    return result;
  }

  @SuppressWarnings("unchecked")
  private double[] extractPointLatLng(Object locationCoordinates) {
    if (!(locationCoordinates instanceof Map)) return null;
    Map<String, Object> coordMap = (Map<String, Object>) locationCoordinates;
    if (!"Point".equals(coordMap.get("type"))) return null;
    Object coordsObj = coordMap.get("coordinates");
    if (!(coordsObj instanceof List)) return null;
    List<Object> coords = (List<Object>) coordsObj;
    if (coords.size() < 2) return null;
    Double lng = toDouble(coords.get(0));
    Double lat = toDouble(coords.get(1));
    return (lat != null && lng != null) ? new double[] {lat, lng} : null;
  }

  private Double toDouble(Object value) {
    if (value instanceof Number) return ((Number) value).doubleValue();
    if (value instanceof String) {
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  private double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
    final double R = 6_371_000.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
