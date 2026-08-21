package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.VenueItemDTO;
import com.mw.planner.service.VenuesService;
import com.mw.planner.util.LocaleUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/venues")
@Tag(
    name = "Venues",
    description =
        "Venue taxonomy operations based on OpenOOH Venue Taxonomy v1.1. Returns a hierarchical tree of parent, child, and grandchild venue types.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VenuesController {

  private final VenuesService venuesService;

  @GetMapping
  @Operation(
      summary = "Get venue taxonomy",
      description =
          "Returns the full OpenOOH venue taxonomy as a hierarchical tree (parent → child → grandchild). Used to populate venue type dropdowns in campaign targeting.")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Venue taxonomy retrieved successfully",
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
  public ApiResponse<List<VenueItemDTO>> getVenues(HttpServletRequest request) {
    log.info("Fetching venue taxonomy");
    List<VenueItemDTO> venues = venuesService.getHierarchicalVenues(LocaleUtil.resolve(request));
    log.info("Retrieved {} root venue categories", venues.size());
    return ApiResponse.success(venues);
  }
}
