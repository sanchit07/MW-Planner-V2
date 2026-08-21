package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
  private String errorCode;
  private String message;
  private String path;
  private LocalDateTime timestamp;
  private Map<String, Object> details;

  public ErrorResponse() {
    this.timestamp = LocalDateTime.now();
  }

  public ErrorResponse(String errorCode, String message, String path) {
    this();
    this.errorCode = errorCode;
    this.message = message;
    this.path = path;
  }
}
