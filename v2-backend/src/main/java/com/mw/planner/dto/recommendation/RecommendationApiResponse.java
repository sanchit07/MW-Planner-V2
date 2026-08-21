package com.mw.planner.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationApiResponse<T> {
  private boolean success;
  private T data;
  private String message;
  private ErrorResponse error;
  private LocalDateTime timestamp;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorResponse {
    private String code;
    private String message;
    private String details;
    private LocalDateTime timestamp;
  }
}
