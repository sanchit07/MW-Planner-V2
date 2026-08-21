package com.mw.planner.constants;

/**
 * Keys used when logging campaign activities. Centralized to keep activity history field names
 * consistent across services.
 */
public enum CampaignActivityKey {
  // Generic campaign fields
  NAME("name"),
  DESCRIPTION("description"),
  STATUS("status"),
  BUDGET_AMOUNT("budget_amount"),
  CURRENCY("currency"),
  DATES("dates"),
  START_DATE("startDate"),
  END_DATE("endDate"),
  COUNTRY("country"),
  CLIENT_TYPE("client_type"),
  BRAND("brand"),
  AGENCY("agency"),

  // Goals
  GOAL_TYPE("goal_type"),
  GOAL_VALUE("goal_value"),
  GOAL_TARGET_NAME("goal_target_name"),

  // Targeting
  TARGETING_DEMOGRAPHICS("targeting_demographics"),
  TARGETING_GEOGRAPHICS("targeting_geographics"),
  TARGETING_SIGNALS("targeting_signals"),
  TARGETING_INVENTORY_CLUSTER("targeting_inventory_cluster"),

  // Budget / fees / access
  BUDGET_ALLOCATION("budget_allocation"),
  CUSTOM_FEES("custom_fees"),
  COMPANY_ACCESS("company_access"),

  // Inventory actions
  INVENTORY_REFERENCE_ID("inventory_reference_id"),
  SELECTED_INVENTORY_COUNT("selected_inventory_count"),
  DESELECTED_INVENTORY_COUNT("deselected_inventory_count"),
  INVENTORY_NAME("inventory_name"),

  // Schedule actions
  BULK_SCHEDULES_OPTIMIZATION_TYPE("bulk_schedules_optimization_type"),
  BULK_SCHEDULES_COUNT("bulk_schedules_count"),
  SCHEDULES_UPDATED_COUNT("schedules_updated_count"),

  // Imports / uploads
  CSV_UPLOAD_SELECTED_COUNT("csv_upload_selected_count"),
  CSV_UPLOAD_FILENAME("csv_upload_filename"),
  INVENTORY_IMPORT_SELECTED_COUNT("inventory_import_selected_count"),
  INVENTORY_IMPORT_FILENAME("inventory_import_filename"),
  INVENTORY_IMPORT_DELETED_FILENAME("inventory_import_deleted_filename"),

  // Approval workflow
  APPROVAL_AUTHORITY("approval_authority"),
  APPROVAL_ACTION("approval_action"),

  // Status transitions
  STATUS_FROM("status_from"),
  STATUS_TO("status_to"),

  // Comments
  COMMENT_FILE_COUNT("comment_file_count");

  private final String key;

  CampaignActivityKey(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
