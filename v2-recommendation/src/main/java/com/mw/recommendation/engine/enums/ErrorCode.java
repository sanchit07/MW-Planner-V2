package com.mw.recommendation.engine.enums;

public enum ErrorCode {
  // Global System Errors (1000-1999)
  INTERNAL_SERVER_ERROR("ERR_1001", "error.internal_server_error"),
  SERVICE_UNAVAILABLE("ERR_1002", "error.service_unavailable"),
  TIMEOUT("ERR_1003", "error.timeout"),
  UNAUTHORIZED("ERR_1004", "error.unauthorized"),
  FORBIDDEN("ERR_1005", "error.forbidden"),
  NOT_FOUND("ERR_1006", "error.not_found"),
  BAD_REQUEST("ERR_1007", "error.bad_request"),
  CONFLICT("ERR_1008", "error.conflict"),
  VALIDATION_FAILED("ERR_1009", "error.validation_failed"),
  INVALID_INPUT("ERR_1010", "error.invalid_input"),
  MISSING_REQUIRED_FIELD("ERR_1011", "error.missing_required_field"),
  INVALID_FORMAT("ERR_1012", "error.invalid_format"),
  FILE_TOO_LARGE("ERR_1013", "error.file_too_large"),
  PERMISSION_DENIED("ERR_1014", "error.permission_denied"),
  EXTERNAL_SERVICE_ERROR("ERR_1015", "error.external_service_error"),
  EXTERNAL_SERVICE_TIMEOUT("ERR_1016", "error.external_service_timeout"),
  EXTERNAL_SERVICE_UNAVAILABLE("ERR_1017", "error.external_service_unavailable"),
  JWT_NO_SUBSCRIPTIONS("ERR_1018", "error.jwt_no_subscriptions"),
  JWT_SUBSCRIPTION_MISMATCH("ERR_1019", "error.jwt_subscription_mismatch"),

  // Recommendation Errors (2000-2999)
  RECOMMENDATION_IN_PROGRESS("ERR_2001", "error.recommendation_in_progress"),
  RECOMMENDATION_RUN_NOT_FOUND("ERR_2002", "error.recommendation_run_not_found"),

  // CSV Inventory Import Errors (3000-3999)
  MISSING_HEADER("ERR_3001", "error.missing_header"),
  EMPTY_FILE("ERR_3002", "error.empty_file"),
  INVALID_FILE("ERR_3003", "error.invalid_file"),
  IMPORT_NOT_FOUND("ERR_3004", "error.import_not_found"),
  TOO_MANY_ROWS("ERR_3005", "error.too_many_rows");

  private final String code;
  private final String messageKey;

  ErrorCode(String code, String messageKey) {
    this.code = code;
    this.messageKey = messageKey;
  }

  public String getCode() {
    return code;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
