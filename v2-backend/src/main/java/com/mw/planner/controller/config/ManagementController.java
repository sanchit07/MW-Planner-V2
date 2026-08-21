package com.mw.planner.controller.config;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.CountrySyncResponseDTO;
import com.mw.planner.dto.PerformanceBackfillJobStatusDTO;
import com.mw.planner.dto.PlanNumberBackfillResultDTO;
import com.mw.planner.dto.SyncResponseDTO;
import com.mw.planner.service.CampaignPerformanceBackfillService;
import com.mw.planner.service.CampaignService;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.DistrictService;
import com.mw.planner.service.InventoryCountrySummaryService;
import com.mw.planner.service.StateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/management")
@Tag(
    name = "Management Operations",
    description = "Master data management operations for syncing external data sources")
@SecurityRequirement(name = "basicAuth")
@RequiredArgsConstructor
public class ManagementController {

  private final CountryService countryService;
  private final StateService stateService;
  private final DistrictService districtService;
  private final InventoryCountrySummaryService inventoryCountrySummaryService;
  private final CampaignPerformanceBackfillService campaignPerformanceBackfillService;
  private final CampaignService campaignService;

  @PostMapping("/sync/countries")
  @Operation(
      summary = "Sync countries from MovingWalls API",
      description =
          "Fetches country data from the MovingWalls API and synchronizes it with the planner database. "
              + "This operation will update existing countries and add new ones based on the external data source.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Countries synchronized successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"syncedCount\": 195, \"updatedCount\": 12, \"createdCount\": 3, \"message\": \"Country sync completed successfully\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to sync countries from MovingWalls API",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<CountrySyncResponseDTO> syncCountries() {
    log.info("Starting country sync operation");
    CountrySyncResponseDTO syncResult = countryService.syncCountriesFromExternalApi();
    log.info(
        "Country sync completed successfully. Synced: {}, Updated: {}, Created: {}",
        syncResult.getSyncedCount(),
        syncResult.getUpdatedCount(),
        syncResult.getCreatedCount());

    return ApiResponse.success(syncResult);
  }

  @PostMapping("/sync/states")
  @Operation(
      summary = "Sync states from MovingWalls API",
      description =
          "Triggers an async job to fetch state data from the MovingWalls API for all countries and synchronizes it with the planner database. "
              + "Returns immediately while processing happens in the background.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "State sync job started successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"message\": \"State sync started successfully\", \"type\": \"STATE\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to start state sync job",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<SyncResponseDTO> syncStates() {
    log.info("Starting state sync job");

    stateService.syncAllStatesAsync();

    SyncResponseDTO response = new SyncResponseDTO("State sync started successfully", "STATE");

    log.info("State sync job started");
    return ApiResponse.success(response);
  }

  @PostMapping("/sync/districts")
  @Operation(
      summary = "Sync districts from MovingWalls API",
      description =
          "Triggers an async job to fetch district data from the MovingWalls API for all states and synchronizes it with the planner database. "
              + "Returns immediately while processing happens in the background.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "District sync job started successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"message\": \"District sync started successfully\", \"type\": \"DISTRICT\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to start district sync job",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<SyncResponseDTO> syncDistricts() {
    log.info("Starting district sync job");

    districtService.syncAllDistrictsAsync();

    SyncResponseDTO response =
        new SyncResponseDTO("District sync started successfully", "DISTRICT");

    log.info("District sync job started");
    return ApiResponse.success(response);
  }

  @PostMapping("/inventory-summary/rebuild")
  @Operation(
      summary = "Rebuild the inventory country summary",
      description =
          "Recomputes the materialized per-country inventory count summary "
              + "(inventory_country_summary) for every country in a single pass. Intended as a "
              + "one-time seed after deployment and as an on-demand reconcile if drift is suspected. "
              + "Runs synchronously and may take some time on large inventory datasets.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Inventory country summary rebuilt successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"message\": \"Rebuilt inventory country summary for 257 countries\", \"type\": \"INVENTORY_SUMMARY\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to rebuild inventory country summary",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class)))
      })
  @ResponseStatus(HttpStatus.OK)
  public ApiResponse<SyncResponseDTO> rebuildInventorySummary() {
    log.info("Starting inventory country summary rebuild");
    int countries = inventoryCountrySummaryService.rebuildAll();
    log.info("Inventory country summary rebuild completed for {} countries", countries);

    SyncResponseDTO response =
        new SyncResponseDTO(
            "Rebuilt inventory country summary for " + countries + " countries",
            "INVENTORY_SUMMARY");
    return ApiResponse.success(response);
  }

  @PostMapping("/campaigns/performance-backfill")
  @Operation(
      summary = "Backfill missing campaign performance snapshots (one-time)",
      description =
          "Starts an async sweep over campaigns whose performance snapshot is null and persists a "
              + "freshly generated forecast for each, using the same forecast path as the campaign "
              + "listing (MW Measure API). Only one sweep can run at a time; re-running resumes "
              + "whatever is still null. By default DRAFT campaigns are excluded (they are actively "
              + "edited via autosave); pass an explicit statuses filter to override. Requires a "
              + "valid user JWT in the X-Measure-Authorization header for the Measure API calls. "
              + "Note: single-campaign GETs may serve a cached entity until the campaigns cache "
              + "TTL expires; the listing itself reads from MongoDB.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "Backfill job started",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"success\": true, \"data\": {\"jobId\": \"1b2f...\", \"state\": \"RUNNING\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Missing or blank X-Measure-Authorization header"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "A backfill sweep is already running")
      })
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<PerformanceBackfillJobStatusDTO> startPerformanceBackfill(
      @RequestHeader(value = "X-Measure-Authorization", required = false)
          String measureAuthorization,
      @RequestParam(value = "statuses", required = false) List<Campaign.Status> statuses) {
    String bearerToken = extractBearerToken(measureAuthorization);
    log.info("Starting campaign performance backfill (statuses={})", statuses);
    return ApiResponse.success(
        campaignPerformanceBackfillService.startBackfill(
            statuses, bearerToken, "performance-backfill"));
  }

  @GetMapping("/campaigns/performance-backfill/{jobId}")
  @Operation(
      summary = "Get the status of a campaign performance backfill job",
      description = "Returns progress counters and state for a previously started backfill job.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Job status"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Unknown job id")
      })
  public ApiResponse<PerformanceBackfillJobStatusDTO> getPerformanceBackfillStatus(
      @PathVariable String jobId) {
    return ApiResponse.success(campaignPerformanceBackfillService.getJobStatus(jobId));
  }

  @PostMapping("/campaigns/plan-number-backfill")
  @Operation(
      summary = "Backfill missing numeric plan IDs on legacy campaigns (one-time)",
      description =
          "Assigns a 12-digit plan number (date + daily sequence) to every campaign created "
              + "before this field existed. Runs synchronously in batches — unlike the performance "
              + "backfill, this is cheap in-process computation with no external API calls, so it "
              + "needs no async job/lock machinery. Safe to re-run: only campaigns still missing a "
              + "plan number are touched.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Backfill completed")
      })
  public ApiResponse<PlanNumberBackfillResultDTO> backfillPlanNumbers(
      @RequestParam(value = "batchSize", required = false, defaultValue = "500") int batchSize) {
    log.info("Starting plan-number backfill (batchSize={})", batchSize);
    return ApiResponse.success(campaignService.backfillPlanNumbers(batchSize));
  }

  private String extractBearerToken(String measureAuthorization) {
    if (measureAuthorization == null || measureAuthorization.isBlank()) {
      throw new IllegalArgumentException("X-Measure-Authorization header is required");
    }
    String token =
        measureAuthorization.startsWith("Bearer ")
            ? measureAuthorization.substring(7)
            : measureAuthorization;
    if (token.isBlank()) {
      throw new IllegalArgumentException("X-Measure-Authorization header carries no token");
    }
    return token.trim();
  }
}
