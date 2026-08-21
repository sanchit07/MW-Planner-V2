package com.mw.planner.service;

import static com.mw.planner.constants.CampaignActivityKey.*;

import com.mw.brand.lib.service.BrandService;
import com.mw.planner.constants.CampaignApprovalAction;
import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignActivity;
import com.mw.planner.dto.*;
import com.mw.planner.dto.CompanyDto;
import com.mw.planner.repository.CampaignActivityRepository;
import com.mw.planner.service.config.ConfigService;
import com.mw.planner.service.config.DefaultConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service for managing campaign activity/history tracking. Handles logging of all campaign events
 * including creation, updates, autoSaves, approval workflow actions, select/deselect inventory,
 * schedules update and cron job status updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignActivityService {

  private final CampaignActivityRepository campaignActivityRepository;
  private final UserService userService;
  private final BrandService brandService;
  private final AgencyService agencyService;
  private final MessageService messageService;
  private final DefaultConfigurationService defaultConfigurationService;
  private final ConfigService configService;
  private final CompanyService companyService;

  private static final String SYSTEM_CRON = "System";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  /**
   * Log campaign activity. Stores operationType and values for later localized message generation.
   *
   * @param campaignId Campaign ID
   * @param operationType Operation type (Created, Updated, Added, Removed)
   * @param values Map of field changes (key: field name, value: field value)
   * @param updatedBy User name or SYSTEM_CRON
   * @param userId User ID (null if SYSTEM_CRON)
   * @param companyId Company ID (null if SYSTEM_CRON)
   */
  public void logActivity(
      String campaignId,
      OperationType operationType,
      Map<String, Object> values,
      String updatedBy,
      String userId,
      String companyId) {
    log.debug(
        "Logging activity for campaignId: {}, operationType: {}, updatedBy: {}",
        campaignId,
        operationType,
        updatedBy);

    CampaignActivity activity =
        CampaignActivity.builder()
            .campaignId(campaignId)
            .userId(userId != null ? userId : SYSTEM_CRON)
            .companyId(companyId != null ? companyId : "")
            .updatedBy(updatedBy)
            .operationType(operationType.name())
            .values(values != null ? new LinkedHashMap<>(values) : new LinkedHashMap<>())
            .build();

    campaignActivityRepository.save(activity);
    log.debug("Activity logged successfully for campaignId: {}", campaignId);
  }

  /**
   * Log campaign activity using UserContext (for user-triggered events)
   *
   * @param campaignId Campaign ID
   * @param operationType Operation type
   * @param values Map of field changes
   */
  public void logActivity(
      String campaignId, OperationType operationType, Map<String, Object> values) {
    try {
      IamUserContext userContext = userService.getIamUserContext();
      String updatedBy = userContext.getFirstName() + " " + userContext.getLastName();
      logActivity(
          campaignId,
          operationType,
          values,
          updatedBy,
          userContext.getId(),
          userContext.getCompanyId());
    } catch (Exception e) {
      log.warn("Could not get user context, logging with system user", e);
      logActivity(campaignId, operationType, values, SYSTEM_CRON, null, null);
    }
  }

  /**
   * Convenience overload to log activity with inline key/value pairs.
   *
   * <p>Example: logActivity(campaignId, UPDATED, STATUS_FROM.key(), oldStatus, STATUS_TO.key(),
   * newStatus);
   */
  public void logActivity(String campaignId, OperationType operationType, Object... keyValuePairs) {
    logActivity(campaignId, operationType, buildActivityValues(keyValuePairs));
  }

  /**
   * Build a LinkedHashMap from varargs key/value pairs to preserve insertion order used in history.
   */
  private Map<String, Object> buildActivityValues(Object... keyValuePairs) {
    Map<String, Object> values = new LinkedHashMap<>();

    if (keyValuePairs == null || keyValuePairs.length == 0) {
      return values;
    }

    if (keyValuePairs.length % 2 != 0) {
      log.warn("Ignoring last activity value due to uneven key/value pairs: {}", keyValuePairs);
    }

    for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
      Object keyObj = keyValuePairs[i];
      Object val = keyValuePairs[i + 1];
      if (keyObj instanceof String key && val != null) {
        values.put(key, val);
      } else {
        log.debug("Skipping activity pair at index {} due to invalid key or null value", i);
      }
    }
    return values;
  }

  /**
   * Log campaign activity for cron jobs
   *
   * @param campaignId Campaign ID
   * @param operationType Operation type
   * @param values Map of field changes
   */
  public void logCronActivity(
      String campaignId, OperationType operationType, Map<String, Object> values) {
    logActivity(campaignId, operationType, values, SYSTEM_CRON, null, null);
  }

  /**
   * Build a map of companyId to business type role (display name). Fetches companies in batch for
   * performance.
   *
   * @param activities List of campaign activities
   * @param locale User's locale for role name localization
   * @return Map of companyId to role display name
   */
  private Map<String, String> buildCompanyRoleMap(
      List<CampaignActivity> activities, Locale locale) {
    Map<String, String> companyIdToRoleMap = new HashMap<>();

    // Collect unique company IDs (excluding SYSTEM_CRON and empty strings)
    Set<String> companyIds =
        activities.stream()
            .map(CampaignActivity::getCompanyId)
            .filter(
                id ->
                    id != null && !id.trim().isEmpty() && !id.equals(SYSTEM_CRON) && !id.equals(""))
            .collect(Collectors.toSet());

    if (companyIds.isEmpty()) {
      return companyIdToRoleMap;
    }

    // Batch fetch companies
    try {
      List<CompanyDto> companies = companyService.getCompaniesByIds(new ArrayList<>(companyIds));

      // Build map of companyId to business type role
      for (CompanyDto companyDto : companies) {
        if (companyDto.getBusinessType() != null) {
          String role = getBusinessTypeDisplayName(companyDto.getBusinessType(), locale);
          companyIdToRoleMap.put(companyDto.getId(), role);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to fetch company business types for campaign history: {}", e.getMessage());
    }

    return companyIdToRoleMap;
  }

  /**
   * Get localized display name for business type.
   *
   * @param businessType Business type enum
   * @param locale User's locale
   * @return Localized display name (e.g., "Media Owner", "Agency", "Media Buyer")
   */
  private String getBusinessTypeDisplayName(CompanyDto.BusinessType businessType, Locale locale) {
    String messageKey = "campaign.activity.role." + businessType.name().toLowerCase();
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      // Fallback to formatted enum name
      return formatBusinessTypeName(businessType);
    }
  }

  /**
   * Format business type enum name to display name.
   *
   * @param businessType Business type enum
   * @return Formatted display name (e.g., "Media Owner", "Agency", "Media Buyer")
   */
  private String formatBusinessTypeName(CompanyDto.BusinessType businessType) {
    return switch (businessType) {
      case MEDIA_OWNER -> "Media Owner";
      case MEDIA_AGENCY -> "Agency";
      case MEDIA_BUYER -> "Media Buyer";
      case MEDIA_OPERATOR -> "Media Operator";
      case ALL -> "All";
    };
  }

  /**
   * Get paginated campaign history entries ordered by updatedAt descending (newest first). Messages
   * are generated on-the-fly based on user's locale. Business types are resolved in batch for
   * performance - only fetches companies for the current page to optimize DB calls.
   *
   * @param campaignId Campaign ID
   * @param locale User's locale for message generation
   * @param pageable Pagination and sort criteria
   * @return Page of campaign activity DTOs with localized messages and business types
   */
  public Page<CampaignActivityResponseDTO> getCampaignHistory(
      String campaignId, Locale locale, Pageable pageable) {
    log.debug(
        "Fetching paginated campaign history for campaignId: {} with locale: {}, pageable: {}",
        campaignId,
        locale,
        pageable);

    // Fetch paginated activities from database
    Page<CampaignActivity> activitiesPage =
        campaignActivityRepository.findByCampaignId(campaignId, pageable);

    // Batch fetch company business types only for the current page (performance optimization)
    List<CampaignActivity> activities = activitiesPage.getContent();
    Map<String, String> companyIdToRoleMap = buildCompanyRoleMap(activities, locale);

    // Convert to DTOs maintaining pagination metadata
    return activitiesPage.map(
        activity -> {
          String role = companyIdToRoleMap.getOrDefault(activity.getCompanyId(), null);
          return CampaignActivityResponseDTO.fromEntity(activity, locale, this, role);
        });
  }

  /**
   * Get paginated campaign history entries using user's locale from context.
   *
   * @param campaignId Campaign ID
   * @param pageable Pagination and sort criteria
   * @return Page of campaign activity DTOs with localized messages
   */
  public Page<CampaignActivityResponseDTO> getCampaignHistory(
      String campaignId, Pageable pageable) {
    Locale locale = getUserLocale();
    return getCampaignHistory(campaignId, locale, pageable);
  }

  /**
   * Get user's locale from context, fallback to English.
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
   * Generate localized message from operation type and values. This method is called when fetching
   * history to generate messages on-the-fly based on user's locale.
   *
   * @param operationType Operation type (CREATED, UPDATED, ADDED, REMOVED)
   * @param values Map of field changes
   * @param locale User's locale for message generation
   * @return Localized human-readable message
   */
  public String generateLocalizedMessage(
      String operationType, Map<String, Object> values, Locale locale) {
    if (values == null || values.isEmpty()) {
      return getOperationTypeMessage(operationType, locale);
    }

    OperationType opType = OperationType.valueOf(operationType);

    // Special handling for status changes
    if (values.containsKey(STATUS_FROM.key()) && values.containsKey(STATUS_TO.key())) {
      return formatStatusChangeMessage(values, locale);
    }

    // Special handling for approval workflow
    if (values.containsKey(APPROVAL_AUTHORITY.key()) && values.containsKey(APPROVAL_ACTION.key())) {
      return formatApprovalWorkflowMessage(values, locale);
    }

    // Special handling for CSV upload operations
    if (values.containsKey(CSV_UPLOAD_SELECTED_COUNT.key())
        && values.containsKey(CSV_UPLOAD_FILENAME.key())) {
      return formatCsvUploadMessage(values, locale);
    }

    // Special handling for inventory import operations
    if (values.containsKey(INVENTORY_IMPORT_SELECTED_COUNT.key())
        && values.containsKey(INVENTORY_IMPORT_FILENAME.key())) {
      return formatInventoryImportMessage(values, locale);
    }
    if (values.containsKey(INVENTORY_IMPORT_DELETED_FILENAME.key())) {
      return formatInventoryImportDeleteMessage(values, locale);
    }

    // Special handling for bulk schedules
    if (values.containsKey(BULK_SCHEDULES_OPTIMIZATION_TYPE.key())) {
      return formatBulkSchedulesMessage(values, locale);
    }

    // Special handling for schedule updates
    if (values.containsKey(SCHEDULES_UPDATED_COUNT.key())) {
      return formatScheduleUpdateMessage(values, locale);
    }

    // Special handling for campaign comments
    if (values.containsKey(COMMENT_FILE_COUNT.key())) {
      return formatCampaignCommentMessage(values, locale);
    }

    // Special handling for inventory operations
    if (values.containsKey(INVENTORY_REFERENCE_ID.key())) {
      return formatInventoryOperationMessage(opType, values, locale);
    }
    if (values.containsKey(SELECTED_INVENTORY_COUNT.key())) {
      return formatBulkInventorySelectMessage(values, locale);
    }
    if (values.containsKey(DESELECTED_INVENTORY_COUNT.key())) {
      return formatBulkInventoryDeselectMessage(values, locale);
    }

    String operationMessage = getOperationTypeMessage(operationType, locale);

    List<String> fieldDescriptions = buildFieldDescriptions(values, locale);

    if (fieldDescriptions.isEmpty()) {
      return operationMessage;
    }

    String fieldsText = String.join(", ", fieldDescriptions);

    // For all operations, just append fields
    return operationMessage + " " + fieldsText;
  }

  /**
   * Format message for single inventory select/deselect operations.
   *
   * @param operationType Operation type (ADDED or REMOVED)
   * @param values Map containing inventory_reference_id
   * @param locale User's locale
   * @return Formatted message like "Selected inventory: {inv_ref_id}" or "Deselected inventory:
   *     {inv_ref_id}"
   */
  private String formatInventoryOperationMessage(
      OperationType operationType, Map<String, Object> values, Locale locale) {
    String referenceId = String.valueOf(values.get(INVENTORY_REFERENCE_ID.key()));
    if (operationType == OperationType.ADDED) {
      String messageKey = "campaign.activity.inventory.selected";
      try {
        return messageService.getMessage(messageKey, locale, referenceId);
      } catch (Exception e) {
        return "Selected inventory: " + referenceId;
      }
    } else {
      String messageKey = "campaign.activity.inventory.deselected";
      try {
        return messageService.getMessage(messageKey, locale, referenceId);
      } catch (Exception e) {
        return "Deselected inventory: " + referenceId;
      }
    }
  }

  /**
   * Format message for bulk inventory select operations.
   *
   * @param values Map containing selected_inventory_count
   * @param locale User's locale
   * @return Formatted message like "Selected {count} inventory based on filters."
   */
  private String formatBulkInventorySelectMessage(Map<String, Object> values, Locale locale) {
    int count = ((Number) values.get(SELECTED_INVENTORY_COUNT.key())).intValue();
    String messageKey = "campaign.activity.inventory.bulk_selected";
    try {
      return messageService.getMessage(messageKey, locale, count);
    } catch (Exception e) {
      return "Selected " + count + " inventory based on filters.";
    }
  }

  /**
   * Format message for bulk inventory deselect operations.
   *
   * @param values Map containing deselected_inventory_count
   * @param locale User's locale
   * @return Formatted message like "Deselected {count} inventory based on filters."
   */
  private String formatBulkInventoryDeselectMessage(Map<String, Object> values, Locale locale) {
    int count = ((Number) values.get(DESELECTED_INVENTORY_COUNT.key())).intValue();
    String messageKey = "campaign.activity.inventory.bulk_deselected";
    try {
      return messageService.getMessage(messageKey, locale, count);
    } catch (Exception e) {
      return "Deselected " + count + " inventory based on filters.";
    }
  }

  /**
   * Format message for status change operations.
   *
   * @param values Map containing status_from and status_to
   * @param locale User's locale
   * @return Formatted message like "Updated campaign status from Draft to Planned"
   */
  private String formatStatusChangeMessage(Map<String, Object> values, Locale locale) {
    String statusFrom = String.valueOf(values.get(STATUS_FROM.key()));
    String statusTo = String.valueOf(values.get(STATUS_TO.key()));
    String messageKey = "campaign.activity.status.changed";
    try {
      return messageService.getMessage(messageKey, locale, statusFrom, statusTo);
    } catch (Exception e) {
      return "Updated campaign status from " + statusFrom + " to " + statusTo;
    }
  }

  /**
   * Format message for approval workflow operations.
   *
   * @param values Map containing approval_authority and approval_action
   * @param locale User's locale
   * @return Formatted message like "Agency approved the campaign" or "Media Owner rejected the
   *     campaign"
   */
  private String formatApprovalWorkflowMessage(Map<String, Object> values, Locale locale) {
    String authority = String.valueOf(values.get(APPROVAL_AUTHORITY.key()));
    String action = String.valueOf(values.get(APPROVAL_ACTION.key()));

    // Map authority enum to display name
    String authorityDisplayName = getApprovalAuthorityDisplayName(authority, locale);

    // Map action to message key suffix (handle "Approved", "Rejected", "Requested Changes")
    String actionKey = mapApprovalActionToKey(action);
    String messageKey = "campaign.activity.approval." + actionKey;

    try {
      return messageService.getMessage(messageKey, locale, authorityDisplayName);
    } catch (Exception e) {
      // Fallback message
      return authorityDisplayName + " " + action.toLowerCase() + " the campaign";
    }
  }

  /**
   * Map approval action string to message key suffix.
   *
   * @param action Action string (Approved, Rejected, Requested Changes)
   * @return Message key suffix (approved, rejected, requested_changes)
   */
  private String mapApprovalActionToKey(String action) {
    if (action == null) {
      return "approved";
    }
    String normalized = action.trim();
    if (normalized.equalsIgnoreCase(CampaignApprovalAction.APPROVED.label())) {
      return "approved";
    } else if (normalized.equalsIgnoreCase(CampaignApprovalAction.REJECTED.label())) {
      return "rejected";
    } else if (normalized.equalsIgnoreCase(CampaignApprovalAction.REQUESTED_CHANGES.label())) {
      return "requested_changes";
    }
    // Fallback: convert to lowercase and replace spaces with underscores
    return normalized.toLowerCase().replace(" ", "_");
  }

  /**
   * Get localized display name for approval authority.
   *
   * @param authority Authority enum name (AGENCY, INTERNAL, MEDIA_OWNER)
   * @param locale User's locale
   * @return Localized display name (Agency, Internal, Media Owner)
   */
  private String getApprovalAuthorityDisplayName(String authority, Locale locale) {
    String messageKey = "campaign.activity.approval.authority." + authority.toLowerCase();
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      // Fallback to formatted enum name
      return formatApprovalAuthorityName(authority);
    }
  }

  /**
   * Format approval authority enum name to display name.
   *
   * @param authority Authority enum name (AGENCY, INTERNAL, MEDIA_OWNER)
   * @return Formatted display name (Agency, Internal, Media Owner)
   */
  private String formatApprovalAuthorityName(String authority) {
    return switch (authority) {
      case "AGENCY" -> "Agency";
      case "INTERNAL" -> "Internal";
      case "MEDIA_OWNER" -> "Media Owner";
      default -> authority;
    };
  }

  /**
   * Format message for CSV upload operations.
   *
   * @param values Map containing csv_upload_selected_count and csv_upload_filename
   * @param locale User's locale
   * @return Formatted message like "Uploaded CSV file: filename.csv and selected 5 inventory"
   */
  private String formatCsvUploadMessage(Map<String, Object> values, Locale locale) {
    int count = ((Number) values.get(CSV_UPLOAD_SELECTED_COUNT.key())).intValue();
    String filename = String.valueOf(values.get(CSV_UPLOAD_FILENAME.key()));
    String messageKey = "campaign.activity.csv_upload";
    try {
      return messageService.getMessage(messageKey, locale, filename, count);
    } catch (Exception e) {
      return "Uploaded CSV file: " + filename + " and selected " + count + " inventory";
    }
  }

  /**
   * Format message for inventory import usage operations.
   *
   * @param values Map containing inventory_import_selected_count and inventory_import_filename
   * @param locale User's locale
   * @return Formatted message like "Used inventory import: filename.csv and selected 5 inventory"
   */
  private String formatInventoryImportMessage(Map<String, Object> values, Locale locale) {
    int count = ((Number) values.get(INVENTORY_IMPORT_SELECTED_COUNT.key())).intValue();
    String filename = String.valueOf(values.get(INVENTORY_IMPORT_FILENAME.key()));
    String messageKey = "campaign.activity.inventory_import.used";
    try {
      return messageService.getMessage(messageKey, locale, filename, count);
    } catch (Exception e) {
      return "Used inventory import: " + filename + " and selected " + count + " inventory";
    }
  }

  /**
   * Format message for inventory import deletion operations.
   *
   * @param values Map containing inventory_import_deleted_filename
   * @param locale User's locale
   * @return Formatted message like "Deleted inventory import: filename.csv"
   */
  private String formatInventoryImportDeleteMessage(Map<String, Object> values, Locale locale) {
    String filename = String.valueOf(values.get(INVENTORY_IMPORT_DELETED_FILENAME.key()));
    String messageKey = "campaign.activity.inventory_import.deleted";
    try {
      return messageService.getMessage(messageKey, locale, filename);
    } catch (Exception e) {
      return "Deleted inventory import: " + filename;
    }
  }

  /**
   * Format message for bulk schedules operations.
   *
   * @param values Map containing bulk_schedules_optimization_type
   * @param locale User's locale
   * @return Formatted message like "Created schedules with Optimization: Manually"
   */
  private String formatBulkSchedulesMessage(Map<String, Object> values, Locale locale) {
    int count =
        values.containsKey(BULK_SCHEDULES_COUNT.key())
            ? ((Number) values.get(BULK_SCHEDULES_COUNT.key())).intValue()
            : 0;
    String messageKey = "campaign.activity.bulk_schedules.created";
    try {
      return messageService.getMessage(messageKey, locale, count);
    } catch (Exception e) {
      return "Created schedules with Optimization Manually for " + count + " inventory";
    }
  }

  /**
   * Format message for schedule update operations.
   *
   * @param values Map containing schedules_updated_count
   * @param locale User's locale
   * @return Formatted message like "Updated 3 schedules"
   */
  private String formatScheduleUpdateMessage(Map<String, Object> values, Locale locale) {
    int count = ((Number) values.get(SCHEDULES_UPDATED_COUNT.key())).intValue();
    String inventoryName = String.valueOf(values.get(INVENTORY_NAME.key()));
    String messageKey = "campaign.activity.schedules.updated";
    try {
      return messageService.getMessage(messageKey, locale, count, inventoryName);
    } catch (Exception e) {
      return "Updated " + count + " schedules for inventory: " + inventoryName;
    }
  }

  /**
   * Format message for campaign comment operations.
   *
   * @param values Map containing comment_file_count
   * @param locale User's locale
   * @return Formatted message like "Added campaign comment with 2 files"
   */
  private String formatCampaignCommentMessage(Map<String, Object> values, Locale locale) {
    int fileCount = ((Number) values.get(COMMENT_FILE_COUNT.key())).intValue();
    String messageKey =
        (fileCount != 0)
            ? "campaign.activity.comment.added.file"
            : "campaign.activity.comment.added";
    try {
      return messageService.getMessage(messageKey, locale, fileCount);
    } catch (Exception e) {
      return "Added campaign comment with " + fileCount + " files";
    }
  }

  /**
   * Get localized operation type message.
   *
   * @param operationType Operation type string
   * @param locale User's locale
   * @return Localized operation message
   */
  private String getOperationTypeMessage(String operationType, Locale locale) {
    String messageKey = "campaign.activity." + operationType.toLowerCase();
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      // Fallback to enum verb if translation not found
      return OperationType.valueOf(operationType).getVerb() + " the Campaign";
    }
  }

  /**
   * Build field descriptions from values map with localization support.
   *
   * @param values Map of field names and values
   * @param locale User's locale
   * @return List of formatted field descriptions
   */
  private List<String> buildFieldDescriptions(Map<String, Object> values, Locale locale) {
    List<String> fieldDescriptions = new ArrayList<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String fieldDesc = formatFieldLocalized(entry.getKey(), entry.getValue(), locale);
      if (fieldDesc != null) {
        fieldDescriptions.add(fieldDesc);
      }
    }
    return fieldDescriptions;
  }

  /**
   * Format a field name and value into a localized readable description.
   *
   * @param fieldName Field name (will be localized)
   * @param value Field value
   * @param locale User's locale
   * @return Formatted field description or null if should be skipped
   */
  private String formatFieldLocalized(String fieldName, Object value, Locale locale) {
    if (value == null) {
      return null;
    }

    String localizedFieldName = getLocalizedFieldName(fieldName, locale);

    // For targeting_demographics, use locale-aware formatting with resolved values
    String formattedValue;
    if (fieldName.equals(TARGETING_DEMOGRAPHICS.key()) && value instanceof Map) {
      formattedValue = formatTargetingDemographics((Map<String, Object>) value, locale);
    } else {
      formattedValue = formatValue(fieldName, value, locale);
    }

    if (formattedValue == null) {
      return null;
    }

    String separatorKey = "campaign.activity.field.separator";
    try {
      String separator = messageService.getMessage(separatorKey, locale);
      return localizedFieldName + separator + formattedValue;
    } catch (Exception e) {
      return localizedFieldName + ": " + formattedValue;
    }
  }

  /**
   * Get localized field name from message properties.
   *
   * @param fieldName Field name key
   * @param locale User's locale
   * @return Localized field name
   */
  private String getLocalizedFieldName(String fieldName, Locale locale) {
    String messageKey = "campaign.activity.field." + fieldName.toLowerCase().replace(" ", ".");
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      // Fallback to formatted field name if translation not found
      return formatFieldName(fieldName);
    }
  }

  /**
   * Format field name to human-readable format
   *
   * @param fieldName Field name
   * @return Formatted field name
   */
  private String formatFieldName(String fieldName) {
    // Convert camelCase to Title Case
    return Arrays.stream(fieldName.split("(?=[A-Z])"))
        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  /**
   * Format field value based on field type.
   *
   * @param fieldName Field name
   * @param value Field value
   * @return Formatted value string
   */
  private String formatValue(String fieldName, Object value, Locale locale) {
    // Note: targeting_demographics is handled in formatFieldLocalized with locale support

    // Localize goal type values
    if (fieldName.equals(GOAL_TYPE.key())) {
      return formatGoalTypeValue(value, locale);
    }

    // Localize campaign status values
    if (fieldName.equals(STATUS.key())) {
      String messageKey = "campaign.status." + value.toString().toLowerCase();
      String translated = messageService.getMessage(messageKey, locale);
      return messageKey.equals(translated) ? value.toString() : translated;
    }

    // Handle date ranges (string format)
    if (fieldName.equals(DATES.key()) && value instanceof String) {
      return value.toString();
    }

    // Handle single dates - support both LocalDate and java.util.Date
    if (fieldName.contains("date") || fieldName.contains("Date")) {
      LocalDate localDate = convertToLocalDate(value);
      if (localDate != null) {
        return localDate.format(DATE_FORMATTER);
      }
    }

    // Handle date ranges (startDate - endDate) - support both LocalDate and java.util.Date
    if (fieldName.equals(DATES.key()) && value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> dateMap = (Map<String, Object>) value;
      LocalDate startDate = convertToLocalDate(dateMap.get(START_DATE.key()));
      LocalDate endDate = convertToLocalDate(dateMap.get(END_DATE.key()));
      if (startDate != null && endDate != null) {
        return startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER);
      }
    }

    // Handle enums - use name directly
    if (value instanceof Enum) {
      return ((Enum<?>) value).name();
    }

    // Handle collections - join with comma
    if (value instanceof Collection<?> collection) {
      if (collection.isEmpty()) {
        return null;
      }
      return collection.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    // Default: convert to string
    return value.toString();
  }

  /**
   * Format goal type to a user-facing label. Falls back to enum display name when localization is
   * unavailable to keep messages readable.
   */
  private String formatGoalTypeValue(Object value, Locale locale) {
    if (value == null) {
      return null;
    }

    String keyPart;
    if (value instanceof Campaign.Goals.GoalType goalType) {
      keyPart = goalType.name().toLowerCase();
    } else {
      keyPart = value.toString().trim().toLowerCase();
    }

    String messageKey = "campaign.activity.goal_type." + keyPart;
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      if (value instanceof Campaign.Goals.GoalType goalType) {
        return goalType.getName();
      }
      return value.toString();
    }
  }

  /**
   * Format targeting demographics with localized field names and resolved values. Uses
   * DefaultConfigurationService to resolve demographic keys to display names.
   *
   * @param demographicsMap Map of demographic types to lists of keys
   * @param locale User's locale for field name localization
   * @return Formatted string like "{ Age Group = [18-24 Years, 25-34 Years], Gender = [Male] }"
   */
  private String formatTargetingDemographics(Map<String, Object> demographicsMap, Locale locale) {
    if (demographicsMap == null || demographicsMap.isEmpty()) {
      return "{}";
    }

    try {
      // Get demographics with localized names for value resolution
      DemographicsGroupedResponseDTO defaultDemographics =
          configService.getGroupedDemographics(locale);

      // Create a map to resolve keys to names
      Map<String, Map<String, String>> demographicKeyToNameMap =
          buildDemographicKeyToNameMap(defaultDemographics);

      List<String> formattedFields = new ArrayList<>();

      // Process each demographic type
      for (Map.Entry<String, Object> entry : demographicsMap.entrySet()) {
        String demographicType = entry.getKey(); // e.g., "age", "gender", "income"
        Object value = entry.getValue();

        if (value == null) {
          continue;
        }

        // Get localized field name
        String localizedFieldName = getLocalizedDemographicFieldName(demographicType, locale);

        // Resolve values
        List<String> resolvedValues =
            resolveDemographicValues(demographicType, value, demographicKeyToNameMap);

        if (!resolvedValues.isEmpty()) {
          String formattedValue = "[" + String.join(", ", resolvedValues) + "]";
          formattedFields.add(localizedFieldName + " = " + formattedValue);
        }
      }

      if (formattedFields.isEmpty()) {
        return "{}";
      }

      return "{ " + String.join(", ", formattedFields) + " }";
    } catch (Exception e) {
      log.warn("Error formatting targeting demographics, falling back to default format", e);
      return demographicsMap.toString();
    }
  }

  /**
   * Build a map for quick lookup of demographic keys to names. Structure: Map<demographicType,
   * Map<demoKey, name>>
   */
  private Map<String, Map<String, String>> buildDemographicKeyToNameMap(
      DemographicsGroupedResponseDTO demographics) {
    Map<String, Map<String, String>> keyToNameMap = new HashMap<>();

    // Age
    if (demographics.getAge() != null) {
      Map<String, String> ageMap = new HashMap<>();
      for (DemographicItemDTO item : demographics.getAge()) {
        ageMap.put(item.getDemoKey(), item.getName());
      }
      keyToNameMap.put("age", ageMap);
    }

    // Gender
    if (demographics.getGender() != null) {
      Map<String, String> genderMap = new HashMap<>();
      for (DemographicItemDTO item : demographics.getGender()) {
        genderMap.put(item.getDemoKey(), item.getName());
      }
      keyToNameMap.put("gender", genderMap);
    }

    // Income
    if (demographics.getIncome() != null) {
      Map<String, String> incomeMap = new HashMap<>();
      for (DemographicItemDTO item : demographics.getIncome()) {
        incomeMap.put(item.getDemoKey(), item.getName());
      }
      keyToNameMap.put("income", incomeMap);
    }

    // Interests
    if (demographics.getInterests() != null) {
      Map<String, String> interestsMap = new HashMap<>();
      for (DemographicItemDTO item : demographics.getInterests()) {
        interestsMap.put(item.getDemoKey(), item.getName());
      }
      keyToNameMap.put("interests", interestsMap);
    }

    // Behavior
    if (demographics.getBehavior() != null) {
      Map<String, String> behaviorMap = new HashMap<>();
      for (DemographicItemDTO item : demographics.getBehavior()) {
        behaviorMap.put(item.getDemoKey(), item.getName());
      }
      keyToNameMap.put("behavior", behaviorMap);
    }

    // Venues
    if (demographics.getVenues() != null) {
      Map<String, String> venuesMap = new HashMap<>();
      for (VenueItemDTO item : demographics.getVenues()) {
        if (item.getEnumerationId() != null) {
          venuesMap.put(item.getEnumerationId().toString(), item.getName());
        }
      }
      keyToNameMap.put("venues", venuesMap);
    }

    return keyToNameMap;
  }

  /**
   * Resolve demographic values from keys to display names.
   *
   * @param demographicType Type of demographic (age, gender, income, etc.)
   * @param value Value object (should be List<String> of keys)
   * @param keyToNameMap Map for resolving keys to names
   * @return List of resolved display names
   */
  @SuppressWarnings("unchecked")
  private List<String> resolveDemographicValues(
      String demographicType, Object value, Map<String, Map<String, String>> keyToNameMap) {
    List<String> resolvedValues = new ArrayList<>();

    if (!(value instanceof List)) {
      return resolvedValues;
    }

    List<String> keys = (List<String>) value;
    Map<String, String> typeMap = keyToNameMap.get(demographicType);

    if (typeMap == null) {
      // Fallback: use keys as-is
      return keys;
    }

    for (String key : keys) {
      String displayName = typeMap.get(key);
      if (displayName != null) {
        resolvedValues.add(displayName);
      } else {
        // Fallback: use key if not found
        resolvedValues.add(key);
      }
    }

    return resolvedValues;
  }

  /**
   * Get localized field name for demographic type. Uses i18n message keys.
   *
   * @param demographicType Demographic type (age, gender, income, interests, venues, behavior)
   * @param locale User's locale
   * @return Localized field name
   */
  private String getLocalizedDemographicFieldName(String demographicType, Locale locale) {
    String messageKey = "campaign.activity.field.demographic." + demographicType.toLowerCase();
    try {
      return messageService.getMessage(messageKey, locale);
    } catch (Exception e) {
      // Fallback to formatted field name
      return formatFieldName(demographicType);
    }
  }

  /**
   * Convert various date types to LocalDate. Handles java.util.Date, LocalDate, and other date
   * formats.
   *
   * @param value Date value (can be LocalDate, java.util.Date, or other types)
   * @return LocalDate or null if conversion not possible
   */
  private LocalDate convertToLocalDate(Object value) {
    if (value == null) {
      return null;
    }

    // Already LocalDate
    if (value instanceof LocalDate) {
      return (LocalDate) value;
    }

    // Convert java.util.Date to LocalDate
    if (value instanceof Date date) {
      return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // Handle Instant
    if (value instanceof Instant) {
      return ((Instant) value).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // Try to parse as string (ISO format)
    if (value instanceof String) {
      try {
        return LocalDate.parse((String) value);
      } catch (Exception e) {
        log.debug("Could not parse date string: {}", value);
      }
    }

    return null;
  }

  /**
   * Build changes map for campaign creation.
   *
   * @param campaign Campaign entity
   * @return Map of field changes
   */
  public Map<String, Object> buildCreationChanges(Campaign campaign) {
    Map<String, Object> changes = new LinkedHashMap<>();

    // Basic campaign fields
    addIfNotNull(changes, NAME.key(), campaign.getName());
    addIfNotNull(changes, CLIENT_TYPE.key(), campaign.getClientType());
    addDatesIfPresent(changes, campaign.getStartDate(), campaign.getEndDate());
    addIfNotNull(changes, COUNTRY.key(), campaign.getCountryId());
    addIfNotNull(changes, CURRENCY.key(), campaign.getCurrency());
    addIfNotNull(changes, BUDGET_AMOUNT.key(), campaign.getBudget());

    // Goals
    addGoalsIfPresent(changes, campaign.getGoals());

    // Targeting
    addTargetingIfPresent(changes, campaign.getTargeting());

    // Resolve names from IDs
    if (campaign.getBrand() != null) {
      addIfNotNull(changes, BRAND.key(), campaign.getBrand().getName());
    }
    if (campaign.getAgency() != null) {
      addIfNotNull(changes, AGENCY.key(), campaign.getAgency().getName());
    }

    return changes;
  }

  /**
   * Add field to changes map if value is not null. Reusable helper method.
   *
   * @param changes Changes map
   * @param fieldName Field name
   * @param value Field value
   */
  private void addIfNotNull(Map<String, Object> changes, String fieldName, Object value) {
    if (value != null) {
      changes.put(fieldName, value);
    }
  }

  /**
   * Add dates to changes map if both dates are present.
   *
   * @param changes Changes map
   * @param startDate Start date
   * @param endDate End date
   */
  private void addDatesIfPresent(
      Map<String, Object> changes, LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null) {
      changes.put(DATES.key(), Map.of(START_DATE.key(), startDate, END_DATE.key(), endDate));
    }
  }

  /**
   * Add goals to changes map if present.
   *
   * @param changes Changes map
   * @param goals Goals object
   */
  private void addGoalsIfPresent(Map<String, Object> changes, Campaign.Goals goals) {
    if (goals != null) {
      addIfNotNull(changes, GOAL_TYPE.key(), goals.getGoalType());
      addIfNotNull(changes, GOAL_VALUE.key(), goals.getTargetValue());
      addIfNotNull(changes, GOAL_TARGET_NAME.key(), goals.getTargetName());
    }
  }

  /**
   * Add targeting information to changes map if present.
   *
   * @param changes Changes map
   * @param targeting Targeting object
   */
  private void addTargetingIfPresent(Map<String, Object> changes, Campaign.Targeting targeting) {
    if (targeting == null) {
      return;
    }

    // Add demographics
    if (targeting.getDemographics() != null && !targeting.getDemographics().isEmpty()) {
      changes.put(TARGETING_DEMOGRAPHICS.key(), targeting.getDemographics());
    }

    // Add geographics
    if (targeting.getGeofencing() != null) {
      List<String> geographicInfo = extractGeographicInfo(targeting.getGeofencing());
      if (!geographicInfo.isEmpty()) {
        changes.put(TARGETING_GEOGRAPHICS.key(), geographicInfo);
      }
    }

    // Add signals
    if (targeting.getSignals() != null && !targeting.getSignals().isEmpty()) {
      changes.put(TARGETING_SIGNALS.key(), targeting.getSignals());
    }

    // Add inventory cluster
    if (targeting.getInventoryCluster() != null && !targeting.getInventoryCluster().isEmpty()) {
      changes.put(TARGETING_INVENTORY_CLUSTER.key(), targeting.getInventoryCluster());
    }
  }

  /**
   * Extract geographic information (location and geometry names) from geofencing.
   *
   * @param geofencing Geofencing object
   * @return List of geographic location/geometry names
   */
  private List<String> extractGeographicInfo(Campaign.Targeting.Geofencing geofencing) {
    List<String> geographicInfo = new ArrayList<>();

    // Extract location names
    if (geofencing.getLocations() != null) {
      geofencing.getLocations().stream()
          .map(Campaign.Targeting.Geofencing.Location::getName)
          .filter(Objects::nonNull)
          .forEach(geographicInfo::add);
    }

    // Extract geometry names
    if (geofencing.getGeometries() != null) {
      geofencing.getGeometries().stream()
          .map(Campaign.Targeting.Geofencing.Geometry::getName)
          .filter(Objects::nonNull)
          .forEach(geographicInfo::add);
    }

    return geographicInfo;
  }

  private void updateBrandName(String brandId, Map<String, Object> changes) {
    try {
      brandService
          .getBrandById(brandId)
          .ifPresent(
              brand -> changes.put(BRAND.key(), brand.getName() != null ? brand.getName() : ""));
    } catch (Exception e) {
      log.debug("Could not fetch brand name for brandId: {}", brandId);
    }
  }

  /**
   * Build changes map for campaign updates by comparing old and new values. Tracks all campaign
   * fields comprehensively and optimizes DB calls by batching brand/agency name fetches.
   *
   * @param oldCampaign Old campaign entity
   * @param newCampaign New campaign entity
   * @return Map of changed fields
   */
  public Map<String, Object> buildUpdateChanges(Campaign oldCampaign, Campaign newCampaign) {
    Map<String, Object> changes = new LinkedHashMap<>();

    // Basic campaign fields
    compareAndAddIfChanged(changes, NAME.key(), oldCampaign.getName(), newCampaign.getName());
    compareAndAddIfChanged(
        changes, DESCRIPTION.key(), oldCampaign.getDescription(), newCampaign.getDescription());
    compareAndAddIfChanged(changes, STATUS.key(), oldCampaign.getStatus(), newCampaign.getStatus());
    compareAndAddIfChanged(
        changes, BUDGET_AMOUNT.key(), oldCampaign.getBudget(), newCampaign.getBudget());
    compareAndAddIfChanged(
        changes, CURRENCY.key(), oldCampaign.getCurrency(), newCampaign.getCurrency());
    compareAndAddIfChanged(
        changes, COUNTRY.key(), oldCampaign.getCountryId(), newCampaign.getCountryId());
    compareAndAddIfChanged(
        changes, CLIENT_TYPE.key(), oldCampaign.getClientType(), newCampaign.getClientType());

    // Dates comparison
    compareDatesIfChanged(changes, oldCampaign, newCampaign);

    // Brand — name is already on the campaign object, no service call needed
    String oldBrandId = oldCampaign.getBrand() != null ? oldCampaign.getBrand().getId() : null;
    String newBrandId = newCampaign.getBrand() != null ? newCampaign.getBrand().getId() : null;
    if (!Objects.equals(oldBrandId, newBrandId) && newCampaign.getBrand() != null) {
      addIfNotNull(changes, BRAND.key(), newCampaign.getBrand().getName());
    }

    String oldAgencyId = oldCampaign.getAgency() != null ? oldCampaign.getAgency().getId() : null;
    String newAgencyId = newCampaign.getAgency() != null ? newCampaign.getAgency().getId() : null;
    if (!Objects.equals(oldAgencyId, newAgencyId) && newCampaign.getAgency() != null) {
      addIfNotNull(changes, AGENCY.key(), newCampaign.getAgency().getName());
    }

    // Goals comparison
    compareGoalsIfChanged(changes, oldCampaign.getGoals(), newCampaign.getGoals());

    // Targeting comparison
    compareTargetingIfChanged(changes, oldCampaign.getTargeting(), newCampaign.getTargeting());

    // Budget allocation comparison
    compareBudgetAllocationIfChanged(
        changes, oldCampaign.getBudgetAllocation(), newCampaign.getBudgetAllocation());

    // Company access comparison
    compareCompanyAccessIfChanged(
        changes, oldCampaign.getCompanyAccess(), newCampaign.getCompanyAccess());

    return changes;
  }

  /**
   * Compare and add field to changes map if values differ. Reusable helper method.
   *
   * @param changes Changes map
   * @param fieldName Field name
   * @param oldValue Old value
   * @param newValue New value
   */
  private void compareAndAddIfChanged(
      Map<String, Object> changes, String fieldName, Object oldValue, Object newValue) {
    if (!Objects.equals(oldValue, newValue) && newValue != null) {
      changes.put(fieldName, newValue);
    }
  }

  /**
   * Compare dates and add to changes if modified.
   *
   * @param changes Changes map
   * @param oldCampaign Old campaign
   * @param newCampaign New campaign
   */
  private void compareDatesIfChanged(
      Map<String, Object> changes, Campaign oldCampaign, Campaign newCampaign) {
    if ((!Objects.equals(oldCampaign.getStartDate(), newCampaign.getStartDate())
            || !Objects.equals(oldCampaign.getEndDate(), newCampaign.getEndDate()))
        && newCampaign.getStartDate() != null
        && newCampaign.getEndDate() != null) {
      changes.put(
          DATES.key(),
          Map.of(
              START_DATE.key(),
              newCampaign.getStartDate(),
              END_DATE.key(),
              newCampaign.getEndDate()));
    }
  }

  /**
   * Compare goals and add to changes if modified.
   *
   * @param changes Changes map
   * @param oldGoals Old goals
   * @param newGoals New goals
   */
  private void compareGoalsIfChanged(
      Map<String, Object> changes, Campaign.Goals oldGoals, Campaign.Goals newGoals) {
    if (Objects.equals(oldGoals, newGoals)) {
      return;
    }

    if (newGoals != null) {
      if (oldGoals == null || !Objects.equals(oldGoals.getGoalType(), newGoals.getGoalType())) {
        addIfNotNull(changes, GOAL_TYPE.key(), newGoals.getGoalType());
      }
      if (oldGoals == null
          || !Objects.equals(oldGoals.getTargetValue(), newGoals.getTargetValue())) {
        addIfNotNull(changes, GOAL_VALUE.key(), newGoals.getTargetValue());
      }
      if (oldGoals == null || !Objects.equals(oldGoals.getTargetName(), newGoals.getTargetName())) {
        addIfNotNull(changes, GOAL_TARGET_NAME.key(), newGoals.getTargetName());
      }
    }
  }

  /**
   * Compare targeting and add to changes if modified.
   *
   * @param changes Changes map
   * @param oldTargeting Old targeting
   * @param newTargeting New targeting
   */
  private void compareTargetingIfChanged(
      Map<String, Object> changes,
      Campaign.Targeting oldTargeting,
      Campaign.Targeting newTargeting) {
    if (Objects.equals(oldTargeting, newTargeting)) {
      return;
    }

    if (newTargeting != null) {
      // Compare demographics
      if (oldTargeting == null
          || !Objects.equals(oldTargeting.getDemographics(), newTargeting.getDemographics())) {
        if (newTargeting.getDemographics() != null && !newTargeting.getDemographics().isEmpty()) {
          changes.put(TARGETING_DEMOGRAPHICS.key(), newTargeting.getDemographics());
        }
      }

      // Compare geofencing
      if (oldTargeting == null
          || !Objects.equals(oldTargeting.getGeofencing(), newTargeting.getGeofencing())) {
        if (newTargeting.getGeofencing() != null) {
          List<String> geographicInfo = extractGeographicInfo(newTargeting.getGeofencing());
          if (!geographicInfo.isEmpty()) {
            changes.put(TARGETING_GEOGRAPHICS.key(), geographicInfo);
          }
        }
      }

      // Compare signals
      if (oldTargeting == null
          || !Objects.equals(oldTargeting.getSignals(), newTargeting.getSignals())) {
        if (newTargeting.getSignals() != null && !newTargeting.getSignals().isEmpty()) {
          changes.put(TARGETING_SIGNALS.key(), newTargeting.getSignals());
        }
      }

      // Compare inventory cluster
      if (oldTargeting == null
          || !Objects.equals(
              oldTargeting.getInventoryCluster(), newTargeting.getInventoryCluster())) {
        if (newTargeting.getInventoryCluster() != null
            && !newTargeting.getInventoryCluster().isEmpty()) {
          changes.put(TARGETING_INVENTORY_CLUSTER.key(), newTargeting.getInventoryCluster());
        }
      }
    }
  }

  /**
   * Compare budget allocation and add to changes if modified.
   *
   * @param changes Changes map
   * @param oldBudgetAllocation Old budget allocation
   * @param newBudgetAllocation New budget allocation
   */
  private void compareBudgetAllocationIfChanged(
      Map<String, Object> changes,
      Map<String, Double> oldBudgetAllocation,
      Map<String, Double> newBudgetAllocation) {
    if (!Objects.equals(oldBudgetAllocation, newBudgetAllocation)
        && newBudgetAllocation != null
        && !newBudgetAllocation.isEmpty()) {
      changes.put(BUDGET_ALLOCATION.key(), newBudgetAllocation);
    }
  }

  /**
   * Compare company access and add to changes if modified.
   *
   * @param changes Changes map
   * @param oldCompanyAccess Old company access list
   * @param newCompanyAccess New company access list
   */
  private void compareCompanyAccessIfChanged(
      Map<String, Object> changes, List<String> oldCompanyAccess, List<String> newCompanyAccess) {
    if (!Objects.equals(oldCompanyAccess, newCompanyAccess)
        && newCompanyAccess != null
        && !newCompanyAccess.isEmpty()) {
      changes.put(COMPANY_ACCESS.key(), newCompanyAccess);
    }
  }

  /** Operation types for campaign activities */
  @Getter
  public enum OperationType {
    CREATED("Created"),
    UPDATED("Updated"),
    ADDED("Added"),
    REMOVED("Removed");

    private final String verb;

    OperationType(String verb) {
      this.verb = verb;
    }
  }
}
