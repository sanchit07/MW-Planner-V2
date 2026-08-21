package com.mw.planner.controller;

import com.mw.planner.dto.InventoryAvailabilityRequestDTO;
import com.mw.planner.dto.InventoryResponseDTO;
import com.mw.planner.service.InventoryService;
import com.mw.planner.service.availability.ImsAvailabilitySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for inventory operations and performance metrics. Provides endpoints for inventory
 * management and analysis.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inventories")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(
    name = "Inventory Management",
    description = "Operations for inventory analysis and performance metrics")
public class InventoryController {

  private final InventoryService inventoryService;
  private final ImsAvailabilitySyncService imsAvailabilitySyncService;

  @GetMapping("/{inventoryId}")
  @Operation(
      summary = "Get inventory by ID",
      description = "Retrieve inventory details with performance metrics by inventory ID")
  public ResponseEntity<InventoryResponseDTO> getInventoryById(
      @Parameter(description = "Inventory ID") @PathVariable String inventoryId) {

    log.info("Getting inventory by ID: {}", inventoryId);

    InventoryResponseDTO inventory = inventoryService.getInventoryResponseDTOById(inventoryId);

    return ResponseEntity.ok(inventory);
  }

  @PostMapping("/availability")
  @Operation(
      summary = "Get inventory availability",
      description =
          "Per-inventory availability from the canonical store synced from IMS, with sync"
              + " metadata (lastSyncedAt, status, error).")
  public ResponseEntity<Map<String, Object>> getInventoryAvailability(
      @RequestBody InventoryAvailabilityRequestDTO request) {
    return ResponseEntity.ok(imsAvailabilitySyncService.getAvailability(request));
  }
}
