package com.mw.planner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // Global Error Codes (1000-1999)
  INTERNAL_SERVER_ERROR("ERR_1000", "error.internal_server"),
  VALIDATION_ERROR("ERR_1001", "error.validation_failed"),
  METHOD_NOT_ALLOWED("ERR_1002", "error.method_not_allowed"),
  UNSUPPORTED_MEDIA_TYPE("ERR_1003", "error.media_type_unsupported"),
  REQUEST_TIMEOUT("ERR_1004", "error.request_timeout"),
  UNAUTHORIZED("ERR_1005", "error.unauthorized"),
  FORBIDDEN("ERR_1006", "error.forbidden"),
  INVALID_CREDENTIALS("ERR_1007", "error.invalid_credentials"),
  TOKEN_INVALID("ERR_1008", "error.token_invalid"),
  INVALID_DATE_FORMAT("ERR_1009", "error.invalid_date_format"),
  JWT_NO_SUBSCRIPTIONS("ERR_1010", "error.jwt_no_subscriptions"),
  JWT_SUBSCRIPTION_MISMATCH("ERR_1011", "error.jwt_subscription_mismatch"),
  USER_CONTEXT_INVALID("ERR_1012", "error.user_context_invalid"),

  // Inventory Management Error Codes (2000-2999)
  INVENTORY_NOT_FOUND("ERR_2001", "error.inventory_not_found"),
  INVENTORY_ALREADY_EXISTS("ERR_2002", "error.inventory_already_exists"),
  INVENTORY_OUT_OF_STOCK("ERR_2003", "error.inventory_out_of_stock"),
  INVENTORY_INSUFFICIENT_QUANTITY("ERR_2004", "error.inventory_insufficient_quantity"),
  INVENTORY_INVALID_STATUS("ERR_2005", "error.inventory_invalid_status"),
  INVENTORY_UPDATE_FAILED("ERR_2006", "error.inventory_update_failed"),
  INVENTORY_DELETE_FAILED("ERR_2007", "error.inventory_delete_failed"),
  INVENTORY_CREATION_FAILED("ERR_2008", "error.inventory_creation_failed"),
  INVENTORY_MEASURE_API_EXCEPTION("ERR_2009", "error.inventory_measure_api_error"),
  INVENTORY_MEASURE_API_TIMEOUT("ERR_2010", "error.inventory_measure_api_timeout"),
  INVENTORY_MEASURE_API_UNAUTHORIZED("ERR_2011", "error.inventory_measure_api_unauthorized"),
  INVENTORY_MEASURE_API_FORBIDDEN("ERR_2012", "error.inventory_measure_api_forbidden"),
  INVENTORY_MEASURE_API_NOT_FOUND("ERR_2013", "error.inventory_measure_api_not_found"),
  INVENTORY_MEASURE_API_BAD_REQUEST("ERR_2014", "error.inventory_measure_api_bad_request"),

  // Campaign Management Error Codes (3000-3999)
  CAMPAIGN_NOT_FOUND("ERR_3001", "error.campaign_not_found"),
  CAMPAIGN_ALREADY_EXISTS("ERR_3002", "error.campaign_already_exists"),
  CAMPAIGN_INVALID_STATUS("ERR_3003", "error.campaign_invalid_status"),
  CAMPAIGN_EXPIRED("ERR_3004", "error.campaign_expired"),
  CAMPAIGN_NOT_STARTED("ERR_3005", "error.campaign_not_started"),
  CAMPAIGN_BUDGET_EXCEEDED("ERR_3006", "error.campaign_budget_exceeded"),
  CAMPAIGN_UPDATE_FAILED("ERR_3007", "error.campaign_update_failed"),
  CAMPAIGN_DELETE_FAILED("ERR_3008", "error.campaign_delete_failed"),
  CAMPAIGN_VALIDATION_FAILED("ERR_3009", "error.campaign_validation_failed"),
  CAMPAIGN_INVALID_DATE_RANGE("ERR_3010", "error.campaign_invalid_date_range"),
  CAMPAIGN_INVALID_BUDGET("ERR_3011", "error.campaign_invalid_budget"),
  CAMPAIGN_INVALID_CLIENT_TYPE("ERR_3012", "error.campaign_invalid_client_type"),
  CAMPAIGN_AGENCY_ID_NOT_VALID("ERR_3013", "error.campaign_agency_id_not_valid"),
  CAMPAIGN_INVALID_GOAL_TYPE("ERR_3014", "error.campaign_invalid_goal_type"),
  CAMPAIGN_INVENTORY_VALIDATION_FAILED("ERR_3015", "error.campaign_inventory_validation_failed"),
  CAMPAIGN_SUBMIT_NOT_CREATOR("ERR_3016", "error.campaign_submit_not_creator"),
  CAMPAIGN_SUBMIT_INVALID_STATUS("ERR_3017", "error.campaign_submit_invalid_status"),
  CAMPAIGN_SUBMIT_PRICES_NOT_APPROVED("ERR_3018", "error.campaign_submit_prices_not_approved"),
  PERFORMANCE_BACKFILL_ALREADY_RUNNING("ERR_3019", "error.performance_backfill_already_running"),
  PERFORMANCE_BACKFILL_JOB_NOT_FOUND("ERR_3020", "error.performance_backfill_job_not_found"),

  // Creative Management Error Codes (4000-4999)
  CREATIVE_NOT_FOUND("ERR_4001", "error.creative_not_found"),
  CREATIVE_ALREADY_EXISTS("ERR_4002", "error.creative_already_exists"),
  CREATIVE_INVALID_FORMAT("ERR_4003", "error.creative_invalid_format"),
  CREATIVE_FILE_TOO_LARGE("ERR_4004", "error.creative_file_too_large"),
  CREATIVE_UPLOAD_FAILED("ERR_4005", "error.creative_upload_failed"),
  CREATIVE_PROCESSING_FAILED("ERR_4006", "error.creative_processing_failed"),
  CREATIVE_UPDATE_FAILED("ERR_4007", "error.creative_update_failed"),
  CREATIVE_DELETE_FAILED("ERR_4008", "error.creative_delete_failed"),
  CREATIVE_INVALID_DIMENSIONS("ERR_4009", "error.creative_invalid_dimensions"),
  CREATIVE_ASSIGNMENT_NOT_FOUND("ERR_4010", "error.creative_assignment_not_found"),
  CREATIVE_ASPECT_RATIO_MISMATCH("ERR_4011", "error.creative_aspect_ratio_mismatch"),
  CREATIVE_DURATION_MISMATCH("ERR_4012", "error.creative_duration_mismatch"),
  CREATIVE_CAMPAIGN_STATUS_INELIGIBLE("ERR_4013", "error.creative_campaign_status_ineligible"),
  CREATIVE_NOT_ACCEPTED("ERR_4014", "error.creative_not_accepted"),
  CREATIVE_TIER1_REASON_REQUIRED("ERR_4015", "error.creative_tier1_reason_required"),

  // User Management Error Codes (5000-5999)
  USER_NOT_FOUND("ERR_5001", "error.user_not_found"),
  USER_ALREADY_EXISTS("ERR_5002", "error.user_already_exists"),
  USER_VALIDATION_FAILED("ERR_5003", "error.user_validation_failed"),
  USER_UPDATE_FAILED("ERR_5004", "error.user_update_failed"),
  USER_CREATION_FAILED("ERR_5005", "error.user_creation_failed"),

  // Company Management Error Codes (6000-6999)
  COMPANY_NOT_FOUND("ERR_6001", "error.company_not_found"),
  COMPANY_ALREADY_EXISTS("ERR_6002", "error.company_already_exists"),
  COMPANY_INVALID_DATA("ERR_6003", "error.company_invalid_data"),
  COMPANY_VALIDATION_FAILED("ERR_6004", "error.company_validation_failed"),
  COMPANY_CREATION_FAILED("ERR_6005", "error.company_creation_failed"),

  // Agency Management Error Codes (7000-7999)
  AGENCY_NOT_FOUND("ERR_7001", "error.agency_not_found"),
  AGENCY_ALREADY_EXISTS("ERR_7002", "error.agency_already_exists"),
  AGENCY_VALIDATION_FAILED("ERR_7003", "error.agency_validation_failed"),
  AGENCY_UPDATE_FAILED("ERR_7004", "error.agency_update_failed"),
  AGENCY_CREATION_FAILED("ERR_7005", "error.agency_creation_failed"),

  // Demographics Management Error Codes (8000-8999)
  DEMOGRAPHICS_NOT_FOUND("ERR_8001", "error.demographics_not_found"),
  DEMOGRAPHICS_INVALID_TYPE("ERR_8002", "error.demographics_invalid_type"),
  DEMOGRAPHICS_INVALID_KEY("ERR_8003", "error.demographics_invalid_key"),
  DEMOGRAPHICS_VALIDATION_FAILED("ERR_8004", "error.demographics_validation_failed"),

  // Campaign Inventory Schedules Error Codes (9000-9999)
  CAMPAIGN_INVENTORY_SCHEDULES_NOT_FOUND(
      "ERR_9001", "error.campaign_inventory_schedules_not_found"),
  BULK_OPERATION_CAMPAIGN_ID_REQUIRED("ERR_9002", "error.bulk_operation_campaign_id_required"),
  BULK_OPERATION_TYPE_REQUIRED("ERR_9003", "error.bulk_operation_type_required"),
  BULK_OPERATION_FAILED("ERR_9004", "error.bulk_operation_failed"),
  SCHEDULE_IDS_EMPTY("ERR_9005", "error.schedule_ids_empty"),
  SCHEDULE_IDS_NOT_FOUND("ERR_9006", "error.schedule_ids_not_found"),
  SCHEDULE_IDS_NOT_BELONG_TO_CAMPAIGN("ERR_9007", "error.schedule_ids_not_belong_to_campaign"),
  USER_CONTEXT_NOT_AVAILABLE("ERR_9008", "error.user_context_not_available"),
  INVENTORY_PRICES_ALREADY_ACCEPTED("ERR_9009", "error.inventory_prices_already_accepted"),
  INVALID_APPROVAL("ERR_9010", "error.invalid_approval"),

  // CSV Upload Error Codes (10000-10999)
  CSV_UPLOAD_FILE_EMPTY("ERR_10001", "error.csv_upload_file_empty"),
  CSV_UPLOAD_INVALID_FILE_TYPE("ERR_10002", "error.csv_upload_invalid_file_type"),
  CSV_UPLOAD_PARSE_ERROR("ERR_10003", "error.csv_upload_parse_error"),
  CSV_UPLOAD_NO_DATA("ERR_10004", "error.csv_upload_no_data"),
  CSV_UPLOAD_INVENTORY_NOT_FOUND("ERR_10005", "error.csv_upload_inventory_not_found"),
  CSV_UPLOAD_INVALID_SOV("ERR_10006", "error.csv_upload_invalid_sov"),
  CSV_UPLOAD_PROCESSING_ERROR("ERR_10007", "error.csv_upload_processing_error"),
  CSV_VERIFY_PROCESSING_ERROR("ERR_10008", "error.csv_verify_processing_error"),
  CSV_UPLOAD_INVALID_FILE("ERR_10009", "error.csv_upload_invalid_file"),

  // Inventory Import Error Codes (10010-10019)
  INVENTORY_IMPORT_NOT_FOUND("ERR_10010", "error.inventory_import_not_found"),
  INVENTORY_IMPORT_ALREADY_SELECTED("ERR_10011", "error.inventory_import_already_selected"),
  INVENTORY_IMPORT_VALIDATION_FAILED("ERR_10012", "error.inventory_import_validation_failed"),
  INVENTORY_IMPORT_USE_FAILED("ERR_10013", "error.inventory_import_use_failed"),
  INVENTORY_IMPORT_DOWNLOAD_FAILED("ERR_10014", "error.inventory_import_download_failed"),
  INVENTORY_IMPORT_DELETE_FAILED("ERR_10015", "error.inventory_import_delete_failed"),
  INVENTORY_IMPORT_EMPTY("ERR_10016", "error.inventory_import_empty"),
  INVENTORY_IMPORTS_NOT_FOUND("ERR_10017", "error.inventory_imports_not_found"),

  // Country Management Error Codes (11000-11999)
  COUNTRY_NOT_FOUND("ERR_11001", "error.country_not_found"),
  COUNTRY_ALREADY_EXISTS("ERR_11002", "error.country_already_exists"),
  COUNTRY_VALIDATION_FAILED("ERR_11003", "error.country_validation_failed"),

  // Master Data API Error Codes (12000-12999)
  MASTER_DATA_API_ERROR("ERR_12001", "error.master_data_api_error"),
  MASTER_DATA_API_TIMEOUT("ERR_12002", "error.master_data_api_timeout"),
  MASTER_DATA_API_UNAUTHORIZED("ERR_12003", "error.master_data_api_unauthorized"),
  MASTER_DATA_API_FORBIDDEN("ERR_12004", "error.master_data_api_forbidden"),
  MASTER_DATA_API_NOT_FOUND("ERR_12005", "error.master_data_api_not_found"),
  MASTER_DATA_API_BAD_REQUEST("ERR_12006", "error.master_data_api_bad_request"),

  // ADS Integration Error Codes (13000-13999)
  CAMPAIGN_NOT_APPROVED_FOR_ADS("ERR_13001", "error.campaign_not_approved_for_ads"),
  ADS_API_ERROR("ERR_13002", "error.ads_api_error"),
  ADS_API_TIMEOUT("ERR_13003", "error.ads_api_timeout"),
  ADS_API_UNAUTHORIZED("ERR_13004", "error.ads_api_unauthorized"),
  ADS_API_FORBIDDEN("ERR_13005", "error.ads_api_forbidden"),
  ADS_API_NOT_FOUND("ERR_13006", "error.ads_api_not_found"),
  ADS_API_BAD_REQUEST("ERR_13007", "error.ads_api_bad_request"),
  ADS_CAMPAIGN_SUBMISSION_FAILED("ERR_13008", "error.ads_campaign_submission_failed"),
  ADS_INVALID_CAMPAIGN_DATA("ERR_13009", "error.ads_invalid_campaign_data"),

  // Storage Errors (14000-14999)
  STORAGE_UPLOAD_FAILED("ERR_14001", "error.storage_upload_failed"),
  STORAGE_DOWNLOAD_FAILED("ERR_14002", "error.storage_download_failed"),
  STORAGE_DELETE_FAILED("ERR_14003", "error.storage_delete_failed"),
  STORAGE_ACCESS_DENIED("ERR_14004", "error.storage_access_denied"),
  STORAGE_QUOTA_EXCEEDED("ERR_14005", "error.storage_quota_exceeded"),
  STORAGE_SERVICE_UNAVAILABLE("ERR_14006", "error.storage_service_unavailable"),
  STORAGE_FILE_CORRUPTED("ERR_14007", "error.storage_file_corrupted"),
  STORAGE_PATH_INVALID("ERR_14008", "error.storage_path_invalid"),

  // Approval Workflow and Proposal Error Codes (15000-15999)
  PROPOSAL_NOT_FOUND("ERR_15001", "error.proposal_not_found"),
  WORKFLOW_INVALID_STATUS("ERR_15002", "error.workflow_invalid_status"),

  // Public Access Token Error Codes (16000-16999)
  PUBLIC_ACCESS_TOKEN_NOT_FOUND("ERR_16001", "error.public_access_token_not_found"),
  PUBLIC_ACCESS_TOKEN_INVALID("ERR_16002", "error.public_access_token_invalid"),

  // Custom Fee Management Error Codes (17000-17999)
  CUSTOM_FEE_NOT_FOUND("ERR_17001", "error.custom_fee_not_found"),
  CUSTOM_FEE_ALREADY_EXISTS("ERR_17002", "error.custom_fee_already_exists"),
  CUSTOM_FEE_VALIDATION_FAILED("ERR_17003", "error.custom_fee_validation_failed"),
  CUSTOM_FEE_UPDATE_FAILED("ERR_17004", "error.custom_fee_update_failed"),
  CUSTOM_FEE_CREATION_FAILED("ERR_17005", "error.custom_fee_creation_failed"),
  CUSTOM_FEE_ENTITY_REFERENCE_ID_REQUIRED(
      "ERR_17006", "error.custom_fee_entity_reference_id_required"),

  // Reservation Error Codes (18000-18499)
  RESERVATION_NOT_FOUND("ERR_18001", "error.reservation_not_found"),
  RESERVATION_ALREADY_EXISTS("ERR_18002", "error.reservation_already_exists"),
  RESERVATION_INVALID_STATUS_TRANSITION("ERR_18003", "error.reservation_invalid_status_transition"),
  RESERVATION_NOT_OWNER("ERR_18004", "error.reservation_not_owner"),

  // Statement Error Codes (18500-18999)
  STATEMENT_NOT_FOUND("ERR_18501", "error.statement_not_found"),
  STATEMENT_LOCKED("ERR_18502", "error.statement_locked"),
  STATEMENT_NO_ELIGIBLE_CAMPAIGNS("ERR_18503", "error.statement_no_eligible_campaigns"),
  STATEMENT_INVALID_SPLIT("ERR_18504", "error.statement_invalid_split"),

  // Planner Configuration / Settings Error Codes (19000-19499)
  CONFIGURATION_VALIDATION_FAILED("ERR_19001", "error.configuration_validation_failed"),
  CONFIGURATION_BONUS_WORKFLOW_FORBIDDEN(
      "ERR_19002", "error.configuration_bonus_workflow_forbidden"),
  COMPANY_BRANDING_FORBIDDEN("ERR_19003", "error.company_branding_forbidden");

  private final String code;
  private final String messageKey;
}
