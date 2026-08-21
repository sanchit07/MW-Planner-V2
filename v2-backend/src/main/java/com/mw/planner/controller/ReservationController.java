package com.mw.planner.controller;

import com.mw.planner.dto.ApiResponse;
import com.mw.planner.dto.reservation.ReservationActionRequestDTO;
import com.mw.planner.dto.reservation.ReservationDTO;
import com.mw.planner.dto.reservation.ReservationDashboardWidgetsDTO;
import com.mw.planner.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Short-lived inventory holds (PRD §9)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  @GetMapping("/campaigns/{campaignId}")
  @PreAuthorize("hasRole('planner:reservations:read')")
  @Operation(summary = "Buyer view — every reservation on a campaign")
  public ApiResponse<List<ReservationDTO>> listForCampaign(@PathVariable String campaignId) {
    return ApiResponse.success(reservationService.listForCampaign(campaignId));
  }

  @GetMapping("/media-owner")
  @PreAuthorize("hasRole('planner:reservations:read')")
  @Operation(summary = "Seller queue — every hold requested against the acting company's inventory")
  public ApiResponse<List<ReservationDTO>> listForMediaOwner() {
    return ApiResponse.success(reservationService.listForMediaOwner());
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(
      summary = "Seller approves a hold request",
      description = "Hold → Reserved, starts the 7-day expiry clock.")
  public ApiResponse<ReservationDTO> approve(@PathVariable String id) {
    return ApiResponse.success(reservationService.approve(id));
  }

  @PostMapping("/{id}/approve-with-conditions")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(
      summary = "Seller approves with a condition",
      description = "Opens a comment thread; the hold stays pending until the buyer responds.")
  public ApiResponse<ReservationDTO> approveWithConditions(
      @PathVariable String id, @RequestBody ReservationActionRequestDTO request) {
    return ApiResponse.success(reservationService.approveWithConditions(id, request.getComment()));
  }

  @PostMapping("/{id}/decline")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(summary = "Seller declines a hold request")
  public ApiResponse<ReservationDTO> decline(
      @PathVariable String id, @RequestBody ReservationActionRequestDTO request) {
    return ApiResponse.success(reservationService.decline(id, request.getReason()));
  }

  @PostMapping("/{id}/extend")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(summary = "Buyer extends a Reserved hold's expiry")
  public ApiResponse<ReservationDTO> extend(
      @PathVariable String id, @RequestBody ReservationActionRequestDTO request) {
    int days = request.getAdditionalDays() != null ? request.getAdditionalDays() : 7;
    return ApiResponse.success(reservationService.extend(id, days));
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(summary = "Buyer voluntarily releases a Reserved hold")
  public ApiResponse<ReservationDTO> release(@PathVariable String id) {
    return ApiResponse.success(reservationService.release(id));
  }

  @PostMapping("/{id}/convert-to-booking")
  @PreAuthorize("hasRole('planner:reservations:update')")
  @Operation(
      summary = "Manually converts a Reserved hold to Booked",
      description =
          "Escape hatch for the rare case the automatic Approved→Booked flip doesn't fire.")
  public ApiResponse<ReservationDTO> convertToBooking(@PathVariable String id) {
    return ApiResponse.success(reservationService.convertToBooking(id));
  }

  @GetMapping("/dashboard-widgets")
  @PreAuthorize("hasRole('planner:reservations:read')")
  @Operation(summary = "Pending Hold Requests / Expiring Holds counts for the dashboard")
  public ApiResponse<ReservationDashboardWidgetsDTO> getDashboardWidgets() {
    return ApiResponse.success(reservationService.getDashboardWidgets());
  }
}
