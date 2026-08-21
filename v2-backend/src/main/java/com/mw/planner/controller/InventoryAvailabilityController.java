package com.mw.planner.controller;

import com.mw.planner.domain.AvailabilitySyncStatus;
import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.service.availability.ImsAvailabilitySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory availability served from the IMS-synced canonical store.
 *
 * <p>The frontend calls availability through the generic downstream proxy path
 * (`/proxy/inventory-api/...`); these more-specific mappings intercept that path so availability
 * reads come from the synced store instead of a live pass-through, guaranteeing that every Step 4
 * surface shows the same data the sync ingested.
 */
@Slf4j
@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(
    name = "Inventory Availability",
    description = "IMS-synced inventory availability and sync controls")
public class InventoryAvailabilityController {

  private static final String PROXY_BASE = "/proxy/inventory-api/api/v1/inventories/availability";

  private final ImsAvailabilitySyncService imsAvailabilitySyncService;

  @PostMapping(PROXY_BASE)
  @Operation(
      summary = "Get inventory availability (IMS-synced store)",
      description =
          "Per-inventory availability from the canonical store synced from IMS, with sync"
              + " metadata (lastSyncedAt, status, error).")
  public ResponseEntity<Map<String, Object>> getAvailability(
      @RequestBody InventoryAvailabilityRequestDTO request) {
    return ResponseEntity.ok(imsAvailabilitySyncService.getAvailability(request));
  }

  @PostMapping(PROXY_BASE + "/sync")
  @Operation(
      summary = "Trigger IMS availability sync",
      description =
          "Starts a full IMS availability sync in the background and returns 202 with the current"
              + " status; poll sync-status for completion.")
  @PreAuthorize("hasRole('planner:plans:update')")
  public ResponseEntity<Map<String, Object>> triggerSync() {
    boolean started =
        imsAvailabilitySyncService.syncAllAsync(AvailabilitySyncStatus.Trigger.MANUAL);
    Map<String, Object> body = toStatusResponse(imsAvailabilitySyncService.getStatus());
    body.put("started", started);
    if (!started) {
      // A sync is already running: single-flight — do not queue another full-catalog run.
      return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
    return ResponseEntity.accepted().body(body);
  }

  @GetMapping(PROXY_BASE + "/sync-status")
  @Operation(summary = "Get IMS availability sync status")
  public ResponseEntity<Map<String, Object>> getSyncStatus() {
    return ResponseEntity.ok(toStatusResponse(imsAvailabilitySyncService.getStatus()));
  }

  private Map<String, Object> toStatusResponse(AvailabilitySyncStatus status) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (status == null) {
      out.put("status", "NEVER_RUN");
      return out;
    }
    out.put("status", status.getState() != null ? status.getState().name() : null);
    out.put("trigger", status.getTrigger() != null ? status.getTrigger().name() : null);
    out.put("startedAt", status.getStartedAt() != null ? status.getStartedAt().toString() : null);
    out.put(
        "completedAt", status.getCompletedAt() != null ? status.getCompletedAt().toString() : null);
    out.put(
        "lastSuccessAt",
        status.getLastSuccessAt() != null ? status.getLastSuccessAt().toString() : null);
    out.put("inventoryCount", status.getInventoryCount());
    out.put("error", status.getError());
    return out;
  }
}
