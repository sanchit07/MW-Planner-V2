package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CountryMarketDetailsDTO;
import com.mw.planner.dto.CountryRequestDTO;
import com.mw.planner.dto.CountryResponseDTO;
import com.mw.planner.dto.DistrictResponseDTO;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.DistrictService;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Location Management",
    description =
        "Location related operations for countries and districts (geographical and tax information)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LocationController {

  private final CountryService countryService;
  private final UserService userService;
  private final DistrictService districtService;

  @PostMapping("/countries")
  @Operation(
      summary = "Create a new country",
      description =
          "Creates a new country with the provided information including geographical coordinates, tax information, and country details.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Country created successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States\", \"iso\": \"USA\", \"active\": true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid country data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Country already exists",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CountryResponseDTO> createCountry(
      @Valid @RequestBody CountryRequestDTO countryRequestDTO) {
    CountryResponseDTO createdCountry = countryService.createCountry(countryRequestDTO);
    return ApiResponse.success(createdCountry);
  }

  @GetMapping("/countries/{id}")
  @Operation(
      summary = "Get country by ID",
      description =
          "Returns detailed country information by country ID including geographical coordinates, tax information, and country details.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Country found successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States\", \"latitude\": 39.8283, \"longitude\": -98.5795, \"iso\": \"USA\", \"active\": true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Country not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CountryResponseDTO> getCountryById(
      @Parameter(description = "Country ID", example = "country_123456") @PathVariable String id) {
    return ApiResponse.success(countryService.getCountryById(id));
  }

  @GetMapping("/countries/name/{countryId}")
  @Operation(
      summary = "Get country by country ID",
      description =
          "Returns detailed country information by country ID including geographical coordinates, tax information, and country details.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Country found successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States\", \"latitude\": 39.8283, \"longitude\": -98.5795, \"iso\": \"USA\", \"active\": true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Country not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CountryResponseDTO> getCountryByCountryId(
      @Parameter(description = "Country ID", example = "country_123456") @PathVariable
          String countryId) {
    return ApiResponse.success(countryService.getCountryByCountryId(countryId));
  }

  @PutMapping("/countries/{id}")
  @Operation(
      summary = "Update country",
      description =
          "Updates country information including geographical coordinates, tax information, and country details.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Country updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States of America\", \"updatedAt\": \"2024-01-15 14:45:00\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Country not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid country data",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Country already exists",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<CountryResponseDTO> updateCountry(
      @Parameter(description = "Country ID", example = "country_123456") @PathVariable String id,
      @Valid @RequestBody CountryRequestDTO countryRequestDTO) {
    CountryResponseDTO updatedCountry = countryService.updateCountry(id, countryRequestDTO);
    return ApiResponse.success(updatedCountry);
  }

  @GetMapping("/countries")
  @Operation(
      summary = "Get all countries with pagination",
      description =
          "Returns a paginated list of all countries. Supports filtering and sorting for better country management.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Countries retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"content\": [{\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States\",\"latitude\": 38.65399,\"longitude\": -101.16121,\"zoom\": 12,\"population\": 334233854, \"iso\": \"USA\", \"postalformat\": \"99999\",\"postalname\": \"ZIP codes\",\"active\": true,\"dialingCode\": \"+1\",\"tax\": {\"label\": \"VAT\",\"percent\": 15.0 },\"updatedAt\": \"2025-11-04 12:49:57\"}, {\"id\": \"country_123456\", \"countryId\": \"US\", \"name\": \"United States\",\"latitude\": 36.553085,\"longitude\": 103.97543,\"zoom\": 12,\"population\": 14393237760, \"iso\": \"USA\", \"postalformat\": \"999999\",\"postalname\": \"Postal code\",\"active\": true,\"dialingCode\": \"+86\",\"tax\": {\"label\": \"VAT\",\"percent\": 17.0 },\"updatedAt\": \"2025-11-04 12:49:58\"}], \"totalElements\": 25, \"totalPages\": 3}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid pagination parameters",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<Page<CountryResponseDTO>> getAllCountries(
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

    return ApiResponse.success(countryService.getAllCountries(pageable));
  }

  @GetMapping("/countries/market-details")
  @Operation(
      summary = "Get country market details for company's accessible countries",
      description =
          "Returns market details for countries that the user's company has access to. "
              + "This includes country information, population, inventories count and impressions. "
              + "Duration parameter controls the time period for impression calculations (default: 30 days).")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Country Market details retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": [{\"country_id\": \"US\", \"country_name\": \"United States\", \"population\": 331000000, \"inventories_count\": 0, \"impressions\": 0}]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Company not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<List<CountryMarketDetailsDTO>> getMarketDetails(
      @Parameter(description = "Optional list of country IDs to filter by (comma-separated)")
          @RequestParam(value = "countryIds", required = false)
          List<String> countryIds,
      @Parameter(
              description =
                  "Optional list of country ISO codes to filter by (comma-separated). Ignored when countryIds is provided.")
          @RequestParam(value = "countryIso", required = false)
          List<String> countryIso) {
    log.info("Fetching country market details for user's company");

    // Get user context to get company ID
    var userContext = userService.getIamUserContext();
    String companyId = userContext.getCompanyId();

    if (companyId == null) {
      log.warn("No company ID found for user: {}", userContext.getUsername());
      return ApiResponse.success(List.of());
    }

    List<CountryMarketDetailsDTO> marketDetails =
        countryService.getCountryMarketDetails(
            companyId, normalizeFilter(countryIds), normalizeFilter(countryIso));
    log.info(
        "Retrieved {} country market details for company: {}", marketDetails.size(), companyId);

    return ApiResponse.success(marketDetails);
  }

  /** Trims entries and drops blanks; returns null when nothing usable remains. */
  private static List<String> normalizeFilter(List<String> values) {
    if (values == null) {
      return null;
    }
    List<String> normalized = values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    return normalized.isEmpty() ? null : normalized;
  }

  @GetMapping("/districts")
  @Operation(
      summary = "Get all districts with pagination",
      description =
          "Returns a paginated list of districts. Optionally filter by district name (case-insensitive). "
              + "When countryName is provided, returns only districts belonging to that country.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Districts retrieved successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"content\": [{\"id\": \"district_123\", \"name\": \"Kabupaten Bandung\", \"stateId\": \"state_456\", \"type\": \"Kabupaten\", \"latitude\": -6.9175, \"longitude\": 107.6191, \"zoom\": 12, \"population\": 2500000, \"iso\": \"ID\", \"locale\": \"en\", \"createdAt\": \"2024-01-15 10:30:00\", \"updatedAt\": \"2024-01-15 14:45:00\"}], \"totalElements\": 25, \"totalPages\": 3}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid pagination parameters",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Country not found (when countryName is provided)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  public ApiResponse<Page<DistrictResponseDTO>> getAllDistricts(
      @Parameter(
              description = "Filter by district name (optional, case-insensitive)",
              example = "Kabupaten")
          @RequestParam(required = false)
          String name,
      @Parameter(
              description =
                  "Filter by country name (optional); returns only districts belonging to that country",
              example = "Indonesia")
          @RequestParam(required = false)
          String countryName,
      @Parameter(description = "Page number (0-based)", example = "0")
          @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10")
          int size,
      @Parameter(description = "Sort field", example = "name") @RequestParam(defaultValue = "name")
          String sortBy,
      @Parameter(description = "Sort direction", example = "asc")
          @RequestParam(defaultValue = "asc")
          String sortDir) {

    Sort sort =
        sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
    Pageable pageable = PageRequest.of(page, size, sort);

    return ApiResponse.success(districtService.getAllDistricts(name, pageable, countryName));
  }
}
