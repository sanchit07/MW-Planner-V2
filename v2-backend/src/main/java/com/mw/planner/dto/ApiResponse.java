package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  private boolean success;
  private String message;
  private T data;
  private Object meta;
  private ErrorResponse error;

  public ApiResponse() {}

  public static <T> ApiResponse<T> success(T data) {
    ApiResponse<T> response = new ApiResponse<>();
    response.success = true;
    response.data = data;
    return response;
  }

  public static <T> ApiResponse<T> success(
      T data, CompanyLookupResponseDTO.Meta meta, String message) {
    ApiResponse<T> response = new ApiResponse<>();
    response.success = true;
    response.message = String.valueOf(message);
    response.data = data;
    response.meta = meta;
    return response;
  }

  public static <T> ApiResponse<T> error(ErrorResponse error) {
    ApiResponse<T> response = new ApiResponse<>();
    response.success = false;
    response.error = error;
    return response;
  }
}
