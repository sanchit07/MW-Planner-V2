package com.mw.planner.exception;

import com.mw.brand.lib.exception.BrandBaseException;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.ErrorResponse;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageService messageService;
  private final MetricsService metricsService;
  private final UserService userService;

  private Locale getCurrentLocale() {
    try {
      return userService.getIamUserContext().getLocale();
    } catch (Exception e) {
      return Locale.ENGLISH;
    }
  }

  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ApiResponse<Void>> handleBaseException(
      BaseException ex, HttpServletRequest request) {

    log.error("Business exception occurred: {}", ex.getMessage(), ex);

    Locale locale = getCurrentLocale();

    String message =
        messageService.getMessage(ex.getErrorCode().getMessageKey(), locale, ex.getArgs());
    ErrorResponse errorResponse =
        new ErrorResponse(ex.getErrorCode().getCode(), message, request.getRequestURI());

    // Record metrics
    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ex.getErrorCode().getCode());

    HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
    return new ResponseEntity<>(ApiResponse.error(errorResponse), status);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    log.error("Validation exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    Map<String, Object> validationErrors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String key;
              if (error instanceof FieldError fieldError) {
                key = fieldError.getField();
              } else if (error instanceof ObjectError objectError) {
                key = objectError.getObjectName();
              } else {
                key = "validation";
              }
              String errorMessage = messageService.getMessage(error.getDefaultMessage(), locale);
              validationErrors.put(key, errorMessage);
            });

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());
    errorResponse.setDetails(validationErrors);

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  /**
   * Spring 6+ can raise {@link HandlerMethodValidationException} for certain validation scenarios.
   * Treat it as a validation error and respond with 400.
   */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, HttpServletRequest request) {

    log.error("Handler method validation exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
      ConstraintViolationException ex, HttpServletRequest request) {

    log.error("Constraint violation exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    Map<String, Object> violations = new HashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation -> {
              violations.put(
                  violation.getPropertyPath().toString(),
                  messageService.getMessage(violation.getMessage(), locale));
            });

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());
    errorResponse.setDetails(violations);

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

    log.error("Type mismatch exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());

    Map<String, Object> details = new HashMap<>();
    details.put("parameter", ex.getName());
    details.put("value", ex.getValue());
    details.put(
        "requiredType",
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
    errorResponse.setDetails(details);

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex, HttpServletRequest request) {

    log.error("HTTP message not readable exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException ex, HttpServletRequest request) {

    log.error("Illegal argument exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException ex, HttpServletRequest request) {

    log.error("Missing request parameter exception occurred: {}", ex.getMessage());

    Locale locale = getCurrentLocale();

    String message = messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), message, request.getRequestURI());

    Map<String, Object> details = new HashMap<>();
    details.put("parameter", ex.getParameterName());
    details.put("parameterType", ex.getParameterType());
    errorResponse.setDetails(details);

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.VALIDATION_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

    log.error("Unsupported media type: {}", ex.getMessage());

    Locale locale = getCurrentLocale();
    String message =
        messageService.getMessage(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(
            ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(), message, request.getRequestURI());

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode());

    return new ResponseEntity<>(
        ApiResponse.error(errorResponse), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(
      NoHandlerFoundException ex, HttpServletRequest request) {

    log.error("Endpoint not found: {}", ex.getMessage());

    Locale locale = getCurrentLocale();
    String message =
        messageService.getMessage("error.endpoint.not.found", locale, ex.getRequestURL());
    ErrorResponse errorResponse = new ErrorResponse("ERR_404", message, request.getRequestURI());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {

    log.warn("Access denied for request: {} - {}", request.getRequestURI(), ex.getMessage());

    Locale locale = getCurrentLocale();
    String message = messageService.getMessage(ErrorCode.FORBIDDEN.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.FORBIDDEN.getCode(), message, request.getRequestURI());

    // Record metrics
    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.FORBIDDEN.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
      AuthenticationException ex, HttpServletRequest request) {

    log.warn(
        "Authentication failed for request: {} - {}", request.getRequestURI(), ex.getMessage());

    Locale locale = getCurrentLocale();
    String message = messageService.getMessage(ErrorCode.UNAUTHORIZED.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(ErrorCode.UNAUTHORIZED.getCode(), message, request.getRequestURI());

    // Record metrics
    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.UNAUTHORIZED.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(
      Exception ex, HttpServletRequest request) {

    log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

    Locale locale = getCurrentLocale();

    String message =
        messageService.getMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessageKey(), locale);
    ErrorResponse errorResponse =
        new ErrorResponse(
            ErrorCode.INTERNAL_SERVER_ERROR.getCode(), message, request.getRequestURI());

    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ErrorCode.INTERNAL_SERVER_ERROR.getCode());

    return new ResponseEntity<>(ApiResponse.error(errorResponse), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(BrandBaseException.class)
  public ResponseEntity<ApiResponse<Void>> handleBrandBaseException(
      BrandBaseException ex, HttpServletRequest request) {

    log.error("Business exception occurred: {}", ex.getMessage(), ex);

    Locale locale = getCurrentLocale();

    String message =
        messageService.getMessage(ex.getErrorCode().getMessageKey(), locale, ex.getArgs());
    ErrorResponse errorResponse =
        new ErrorResponse(ex.getErrorCode().getCode(), message, request.getRequestURI());

    // Record metrics
    metricsService.incrementApiRequestError(
        request.getRequestURI(), request.getMethod(), ex.getErrorCode().getCode());

    HttpStatus status = mapBrandErrorCodeToHttpStatus(ex.getErrorCode());
    return new ResponseEntity<>(ApiResponse.error(errorResponse), status);
  }

  private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
    // Extract numeric part - handle both 4-digit and 5-digit error codes
    String numericPart = errorCode.getCode().substring(4);
    return switch (numericPart) {
      case "1005", "1008", "1010", "1011", "13004", "16001", "16002" -> HttpStatus.UNAUTHORIZED;
      case "1006", "13005", "14004", "19002", "19003", "18004" -> HttpStatus.FORBIDDEN;
      case "2001",
              "3001",
              "4001",
              "4010",
              "5001",
              "6001",
              "7001",
              "8001",
              "9001",
              "10010",
              "11001",
              "13006",
              "17001",
              "18001",
              "18501",
              "3020" ->
          HttpStatus.NOT_FOUND;
      case "2002",
              "3002",
              "3019",
              "4002",
              "7002",
              "11002",
              "17002",
              "4013",
              "4014",
              "18002",
              "18003",
              "18502" ->
          HttpStatus.CONFLICT;
      case "1007",
              "2003",
              "2004",
              "3003",
              "3006",
              "3010",
              "4004",
              "1012",
              "7003",
              "9002",
              "9003",
              "9004",
              "10001",
              "10002",
              "10003",
              "10004",
              "10005",
              "10006",
              "10007",
              "10011",
              "10012",
              "10013",
              "10014",
              "10015",
              "10016",
              "11003",
              "13001",
              "13007",
              "13009",
              "14001",
              "14008",
              "17003",
              "17004",
              "17005",
              "17006",
              "19001",
              "4011",
              "4012",
              "4015",
              "18503",
              "18504" ->
          HttpStatus.BAD_REQUEST;
      case "13003" -> HttpStatus.REQUEST_TIMEOUT;
      case "14005" -> HttpStatus.PAYLOAD_TOO_LARGE;
      case "14006" -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private HttpStatus mapBrandErrorCodeToHttpStatus(com.mw.brand.lib.enums.ErrorCode errorCode) {
    // Extract numeric part - handle both 4-digit and 5-digit error codes
    String numericPart = errorCode.getCode().substring(4);
    return switch (numericPart) {
      case "20001" -> HttpStatus.NOT_FOUND;
      case "20002" -> HttpStatus.CONFLICT;
      case "20003" -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
