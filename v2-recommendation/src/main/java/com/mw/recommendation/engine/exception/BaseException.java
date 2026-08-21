package com.mw.recommendation.engine.exception;

import com.mw.recommendation.engine.enums.ErrorCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class BaseException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String message;
  private final Object[] parameters;
  private final LocalDateTime timestamp;
  private final Map<String, Object> context;

  public BaseException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = null;
    this.timestamp = LocalDateTime.now();
    this.context = null;
    logException();
  }

  public BaseException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = null;
    this.timestamp = LocalDateTime.now();
    this.context = null;
    logException();
  }

  public BaseException(ErrorCode errorCode, String message, Object... parameters) {
    super(message);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = parameters;
    this.timestamp = LocalDateTime.now();
    this.context = null;
    logException();
  }

  public BaseException(ErrorCode errorCode, String message, Throwable cause, Object... parameters) {
    super(message, cause);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = parameters;
    this.timestamp = LocalDateTime.now();
    this.context = null;
    logException();
  }

  public BaseException(ErrorCode errorCode, String message, Map<String, Object> context) {
    super(message);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = null;
    this.timestamp = LocalDateTime.now();
    this.context = context;
    logException();
  }

  public BaseException(
      ErrorCode errorCode, String message, Throwable cause, Map<String, Object> context) {
    super(message, cause);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = null;
    this.timestamp = LocalDateTime.now();
    this.context = context;
    logException();
  }

  public BaseException(
      ErrorCode errorCode, String message, Object[] parameters, Map<String, Object> context) {
    super(message);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = parameters;
    this.timestamp = LocalDateTime.now();
    this.context = context;
    logException();
  }

  public BaseException(
      ErrorCode errorCode,
      String message,
      Throwable cause,
      Object[] parameters,
      Map<String, Object> context) {
    super(message, cause);
    this.errorCode = errorCode;
    this.message = message;
    this.parameters = parameters;
    this.timestamp = LocalDateTime.now();
    this.context = context;
    logException();
  }

  private void logException() {
    if (getCause() != null) {
      log.error(
          "Exception occurred - Code: {}, Message: {}, Parameters: {}, Context: {}, Timestamp: {}",
          errorCode.getCode(),
          message,
          Arrays.toString(parameters != null ? parameters : new Object[0]),
          context,
          timestamp,
          getCause());
    } else {
      log.error(
          "Exception occurred - Code: {}, Message: {}, Parameters: {}, Context: {}, Timestamp: {}",
          errorCode.getCode(),
          message,
          Arrays.toString(parameters != null ? parameters : new Object[0]),
          context,
          timestamp);
    }
  }

  public String getFormattedMessage() {
    if (parameters != null && parameters.length > 0) {
      return String.format(message, parameters);
    }
    return message;
  }
}
