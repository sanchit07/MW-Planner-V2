package com.mw.recommendation.engine.v3.controller;

import com.mw.recommendation.engine.dto.ApiResponse;
import com.mw.recommendation.engine.dto.ErrorResponse;
import com.mw.recommendation.engine.v3.support.V3ErrorCode;
import com.mw.recommendation.engine.v3.support.V3Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handling for the v3 controller only ({@code assignableTypes} scoping) — the shared
 * {@code GlobalExceptionHandler} is untouched and continues to serve v1/v2 (including v3 bean
 * validation, which keeps the same 400 envelope as v1 for consistency).
 */
@RestControllerAdvice(assignableTypes = RecommendationV3Controller.class)
@Order(0)
@Slf4j
public class V3ExceptionAdvice {

  @ExceptionHandler(V3Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleV3Exception(V3Exception ex) {
    log.warn("v3 request failed: {} - {}", ex.getErrorCode(), ex.getMessage());
    HttpStatus status =
        switch (ex.getErrorCode()) {
          case RUN_NOT_FOUND -> HttpStatus.NOT_FOUND;
          case RUN_IN_PROGRESS -> HttpStatus.CONFLICT;
          case NO_DATES, NO_COUNTRY, NO_BUDGET, BUDGET_TOO_LOW, GOAL_UNREACHABLE ->
              HttpStatus.BAD_REQUEST;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return ResponseEntity.status(status)
        .body(
            ApiResponse.error(
                ErrorResponse.builder()
                    .code(ex.getErrorCode().name())
                    .message(ex.getMessage())
                    .build()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                ErrorResponse.builder()
                    .code(V3ErrorCode.INTERNAL_ERROR.name())
                    .message(ex.getMessage())
                    .build()));
  }
}
