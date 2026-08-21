package com.mw.planner.controller;

import com.mw.planner.dto.*;
import com.mw.planner.service.AgencyService;
import com.mw.planner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agencies")
@Tag(
    name = "Agency Management",
    description = "Agency related operations for managing agencies with simplified structure")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AgencyController {

  private final AgencyService agencyService;
  private final UserService userService;

  @PostMapping
  @Operation(
      summary = "Create a new agency",
      description =
          "Creates a new agency with the provided information including name, media owner ID, company email, country ID, company ID, and optional seat ID and brand reference ID.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Agency created successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"agency_123456\", \"name\": \"Creative Media Agency\", \"mediaOwnerId\": \"MO_001\", \"activated\": true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid agency data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Agency already exists",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AgencyResponseDTO> createAgency(
      @Valid @RequestBody AgencyRequestDTO agencyRequestDTO) {

    String mediaOwnerId = userService.getIamUserContext().getId();
    agencyRequestDTO.setMediaOwnerId(mediaOwnerId);
    AgencyResponseDTO createdAgency = agencyService.createAgency(agencyRequestDTO);
    return ApiResponse.success(createdAgency);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get agency by ID",
      description = "Returns detailed agency information by agency ID including all agency fields.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Agency found successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"agency_123456\", \"name\": \"Creative Media Agency\", \"mediaOwnerId\": \"MO_001\", \"contactDetails\": {\"companyEmail\": \"info@creativeagency.com\"}, \"activated\": true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Agency not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<AgencyResponseDTO> getAgencyById(
      @Parameter(description = "Agency ID", example = "agency_123456") @PathVariable String id) {
    return ApiResponse.success(agencyService.getAgencyById(id));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Update agency",
      description =
          "Updates agency information including name, media owner ID, company email, country ID, company ID, and optional seat ID and brand reference ID.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Agency updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"agency_123456\", \"name\": \"Updated Agency Name\", \"mediaOwnerId\": \"MO_002\", \"updatedAt\": \"2024-01-15 14:45:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Agency not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid agency data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<AgencyResponseDTO> updateAgency(
      @Parameter(description = "Agency ID", example = "agency_123456") @PathVariable String id,
      @Valid @RequestBody AgencyRequestDTO agencyRequestDTO) {

    String mediaOwnerId = userService.getIamUserContext().getId();
    agencyRequestDTO.setMediaOwnerId(mediaOwnerId);
    AgencyResponseDTO updatedAgency = agencyService.updateAgency(id, agencyRequestDTO);
    return ApiResponse.success(updatedAgency);
  }

  @GetMapping
  @Operation(
      summary = "Get all agencies with pagination",
      description =
          "Returns a paginated list of all agencies. Supports filtering by name or country name, and sorting for better agency management.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Agencies retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"content\": [{\"id\": \"agency_123456\", \"name\": \"Creative Media Agency\", \"mediaOwnerId\": \"MO_001\"}, {\"id\": \"agency_789012\", \"name\": \"Brand Solutions Inc\", \"mediaOwnerId\": \"MO_002\"}], \"totalElements\": 25, \"totalPages\": 3}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid pagination parameters",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<Page<AgencyResponseDTO>> getAllAgencies(
      @Parameter(
              description = "Search term to filter agencies by name, or country name",
              example = "Creative")
          @RequestParam(required = false)
          String search,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "updatedAt")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "Sort direction", example = "desc")
          @RequestParam(defaultValue = "desc")
          String sortDir) {

    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(page, size, sort);

    return ApiResponse.success(agencyService.getAllAgencies(pageable, search));
  }
}
