package com.mw.planner.controller;

import com.mw.planner.dto.*;
import com.mw.planner.service.DemographicsService;
import com.mw.planner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demographics")
@Tag(
    name = "Demographics Management",
    description = "Demographics related operations for managing demographic data")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DemographicsController {

  private final DemographicsService demographicsService;
  private final UserService userService;

  @GetMapping
  @Operation(
      summary = "Get demographics",
      description = "Retrieves demographics config by type in the specified format.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Config demographics retrieved successfully",
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
  public ApiResponse<DemographicsConfigResponseDTO> getConfigDemographics() {
    DemographicsConfigResponseDTO response = demographicsService.getConfigDemographics();
    return ApiResponse.success(response);
  }

  @PatchMapping("/autosave")
  @Operation(
      summary = "Auto-save demographic data",
      description = "Automatically saves demographic data for the current user's country.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Demographic data auto-saved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad request - Invalid input data",
            content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Authentication required",
            content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = "application/json"))
      })
  public ApiResponse<DemographicsResponseDTO> autoSaveCampaign(
      @Valid
          @RequestBody
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Demographic data to be auto-saved",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = DemographicAutoSaveRequestDTO.class)))
          DemographicAutoSaveRequestDTO autoSaveRequestDTO) {
    IamUserContext userContext = userService.getIamUserContext();

    // TODO add country id
    DemographicsResponseDTO autoSavedCampaign =
        demographicsService.autoSaveDemographic(autoSaveRequestDTO, "countryId");
    return ApiResponse.success(autoSavedCampaign);
  }
}
