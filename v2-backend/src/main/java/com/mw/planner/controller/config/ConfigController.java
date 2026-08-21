package com.mw.planner.controller.config;

import com.mw.planner.dto.*;
import com.mw.planner.service.config.ConfigService;
import com.mw.planner.util.LocaleUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/config")
@Tag(
    name = "Configuration Management",
    description =
        "Configuration related operations for managing system configuration data including demographics and planner status")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ConfigController {

  private final ConfigService configService;

  @GetMapping
  @Operation(
      summary = "Get configuration data",
      description = "Retrieves complete configuration data.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Configuration data retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json"))
      })
  public ApiResponse<Map<String, Object>> getConfigurationData(HttpServletRequest request) {
    return ApiResponse.success(configService.getConfigurationData(LocaleUtil.resolve(request)));
  }

  @GetMapping("/brand/categories")
  @Operation(
      summary = "Get all IAB brand categories",
      description =
          "Retrieves all IAB (Interactive Advertising Bureau) category mappings with their corresponding codes.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "IAB brand categories retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json"))
      })
  public ApiResponse<List<BrandIabCategory>> getBrandCategories(HttpServletRequest request) {
    return ApiResponse.success(configService.getBrandCategoriesData(LocaleUtil.resolve(request)));
  }
}
