package com.mw.recommendation.engine.exception;

import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.dto.ErrorResponse;
import com.mw.recommendation.engine.enums.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  /** Handle custom application exceptions */
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ApiResponse<Void>> handleBaseException(
      BaseException ex, WebRequest request) {
    log.error("Application exception occurred: {}", ex.getMessage(), ex);

    ErrorCode errorCode = ex.getErrorCode();
    HttpStatus httpStatus = mapErrorCodeToHttpStatus(errorCode);
    String message = getLocalizedMessage(errorCode.getMessageKey());

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(message)
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(message)
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(httpStatus).body(apiResponse);
  }

  /** Handle validation exceptions */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException ex, WebRequest request) {
    log.error("Validation exception occurred: {}", ex.getMessage(), ex);

    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = getLocalizedMessage(error.getDefaultMessage());
              validationErrors.put(fieldName, errorMessage);
            });

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.VALIDATION_FAILED.getCode())
            .message(getLocalizedMessage(ErrorCode.VALIDATION_FAILED.getMessageKey()))
            .details(validationErrors.toString())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.VALIDATION_FAILED.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.badRequest().body(apiResponse);
  }

  /** Handle illegal argument exceptions */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException ex, WebRequest request) {
    log.error("Illegal argument exception occurred: {}", ex.getMessage(), ex);

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.INVALID_INPUT.getCode())
            .message(getLocalizedMessage(ErrorCode.INVALID_INPUT.getMessageKey()))
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.INVALID_INPUT.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.badRequest().body(apiResponse);
  }

  /** Handle runtime exceptions */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
      RuntimeException ex, WebRequest request) {
    log.error("Runtime exception occurred: {}", ex.getMessage(), ex);

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
            .message(getLocalizedMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey()))
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
  }

  /** Handle Spring Security authentication failures (e.g. invalid JWT, subscription mismatch). */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
      AuthenticationException ex, WebRequest request) {
    Throwable cause = ex.getCause();
    if (cause instanceof BaseException baseEx) {
      return handleBaseException(baseEx, request);
    }
    log.warn("Authentication failed: {}", ex.getMessage());

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.UNAUTHORIZED.getCode())
            .message(getLocalizedMessage(ErrorCode.UNAUTHORIZED.getMessageKey()))
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.UNAUTHORIZED.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiResponse);
  }

  /** Handle Spring Security access denied (e.g. insufficient permissions). */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
      AccessDeniedException ex, WebRequest request) {
    log.warn("Access denied: {}", ex.getMessage());

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.FORBIDDEN.getCode())
            .message(getLocalizedMessage(ErrorCode.FORBIDDEN.getMessageKey()))
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.FORBIDDEN.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiResponse);
  }

  /** Handle multipart uploads that exceed the configured size limit → 413 Payload Too Large. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex, WebRequest request) {
    log.warn("Upload exceeded maximum size: {}", ex.getMessage());

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.FILE_TOO_LARGE.getCode())
            .message(getLocalizedMessage(ErrorCode.FILE_TOO_LARGE.getMessageKey()))
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.FILE_TOO_LARGE.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(apiResponse);
  }

  /** Handle all other exceptions */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(
      Exception ex, WebRequest request) {
    log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
            .message(getLocalizedMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey()))
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();

    ApiResponse<Void> apiResponse =
        ApiResponse.<Void>builder()
            .success(false)
            .data(null)
            .message(getLocalizedMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey()))
            .error(errorResponse)
            .timestamp(LocalDateTime.now())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
  }

  /** Map error codes to HTTP status codes */
  private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
    return switch (errorCode) {
      // Global System Errors (1000-1999)
      case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN, PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case BAD_REQUEST, VALIDATION_FAILED, INVALID_INPUT, MISSING_REQUIRED_FIELD, INVALID_FORMAT ->
          HttpStatus.BAD_REQUEST;
      case CONFLICT -> HttpStatus.CONFLICT;
      case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case EXTERNAL_SERVICE_ERROR, EXTERNAL_SERVICE_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
      case EXTERNAL_SERVICE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case JWT_NO_SUBSCRIPTIONS, JWT_SUBSCRIPTION_MISMATCH -> HttpStatus.FORBIDDEN;

      // Recommendation Errors (2000-2999)
      case RECOMMENDATION_IN_PROGRESS -> HttpStatus.CONFLICT;
      case RECOMMENDATION_RUN_NOT_FOUND -> HttpStatus.NOT_FOUND;

      // CSV Inventory Import Errors (3000-3999)
      case MISSING_HEADER, EMPTY_FILE, INVALID_FILE -> HttpStatus.BAD_REQUEST;
      case IMPORT_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case TOO_MANY_ROWS -> HttpStatus.PAYLOAD_TOO_LARGE;
    };
  }

  /** Get localized message for the given key */
  private String getLocalizedMessage(String messageKey) {
    try {
      return messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
    } catch (Exception e) {
      log.warn("Failed to get localized message for key: {}", messageKey, e);
      return messageKey; // Fallback to the key itself
    }
  }
}
