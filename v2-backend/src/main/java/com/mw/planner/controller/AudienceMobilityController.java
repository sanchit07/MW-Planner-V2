package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.mobility.MobilityHeatmapResponseDTO;
import com.mw.planner.service.AudienceMobilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/mobility")
@Tag(
    name = "Audience Mobility",
    description = "Audience mobility (footfall) data powering planning-map heatmaps")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AudienceMobilityController {

  private final AudienceMobilityService audienceMobilityService;

  @GetMapping("/heatmap")
  @Operation(
      summary = "Get an aggregated mobility heatmap",
      description =
          "Returns geo points with normalized footfall weights for a country, optionally filtered"
              + " by time-of-day bucket (MORNING/AFTERNOON/EVENING/NIGHT) and bounding box."
              + " Points are aggregated and capped server-side for map performance.")
  public ApiResponse<MobilityHeatmapResponseDTO> getHeatmap(
      @Parameter(description = "Country slug from the countries master, e.g. 'malaysia'")
          @RequestParam
          String countryId,
      @Parameter(description = "MORNING | AFTERNOON | EVENING | NIGHT | ALL (default ALL)")
          @RequestParam(required = false)
          String timeBucket,
      @RequestParam(required = false) Double minLat,
      @RequestParam(required = false) Double maxLat,
      @RequestParam(required = false) Double minLng,
      @RequestParam(required = false) Double maxLng) {
    return ApiResponse.success(
        audienceMobilityService.getHeatmap(countryId, timeBucket, minLat, maxLat, minLng, maxLng));
  }
}
