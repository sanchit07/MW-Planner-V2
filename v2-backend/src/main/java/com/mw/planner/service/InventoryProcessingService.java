package com.mw.planner.service;

import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.ExternalInventoryMessageDTO;
import com.mw.planner.rabbitmq.ExternalInventoryMessageConverter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for processing inventory messages and handling all business logic related to
 * inventory data and their persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryProcessingService {

  private final ExternalInventoryMessageConverter messageConverter;
  private final InventoryService inventoryService;
  private final InventoryCountrySummaryService inventoryCountrySummaryService;
  private final VirtualThreadService virtualThreadService;

  /**
   * Process an inventory message and handle all related business logic.
   *
   * @param message The external inventory message to process
   */
  @Transactional
  public void deleteInventoryByExternalId(String inventoryId) {
    log.info("Deleting inventory with externalId: {}", inventoryId);
    inventoryService
        .findByExternalId(inventoryId)
        .ifPresentOrElse(
            inventory -> {
              String country = countryOf(inventory);
              inventoryService.deleteById(inventory.getId());
              log.info("Deleted inventory id: {} externalId: {}", inventory.getId(), inventoryId);
              refreshCountrySummaries(country);
            },
            () -> log.warn("Inventory not found for deletion, externalId: {}", inventoryId));
  }

  @Transactional
  public void processInventoryMessage(ExternalInventoryMessageDTO message, String inventoryId) {
    String referenceId = message.getReferenceId();
    log.info(
        "Processing inventory message for referenceId: {} inventoryId: {}",
        referenceId,
        inventoryId);

    try {
      // Validate and resolve mediaOwner
      String mediaOwnerId = message.getMediaOwnerId();

      // Convert to internal entity
      Inventory inventory = messageConverter.convertToInventory(message);
      inventory.setMediaOwnerId(mediaOwnerId);
      inventory.setMediaOwnerName(message.getMediaOwnerName());

      // Capture the previous country before the upsert overwrites it, so a country change
      // refreshes both the old and the new country summaries.
      String previousCountry = findExistingCountry(inventory);
      inventory.setInventoryId(inventoryId);

      // Save inventory
      saveInventory(inventory);

      refreshCountrySummaries(previousCountry, countryOf(inventory));

      log.info("Successfully processed inventory message for referenceId: {}", referenceId);

    } catch (Exception e) {
      log.error(
          "Error processing inventory message for referenceId: {}: {}",
          referenceId,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to process inventory message", e);
    }
  }

  /**
   * Save inventory to database via a single atomic upsert keyed on the natural key (externalId,
   * else referenceId). This replaces the previous non-atomic find-then-save logic so that
   * concurrent inventory-sync messages for the same inventory can no longer create duplicate
   * documents. Partial-update semantics (only non-null fields are written) are preserved inside the
   * upsert layer.
   */
  private void saveInventory(Inventory inventory) {
    try {
      Inventory upserted = inventoryService.upsertByNaturalKey(inventory);
      log.info(
          "Upserted inventory with ID: {} for referenceId: {}",
          upserted != null ? upserted.getId() : null,
          inventory.getReferenceId());
    } catch (Exception e) {
      log.error(
          "Error saving inventory for referenceId: {}: {}",
          inventory.getReferenceId(),
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to save inventory", e);
    }
  }

  /** Look up the country of the currently persisted inventory (by its natural key), if any. */
  private String findExistingCountry(Inventory inventory) {
    Optional<Inventory> existing = Optional.empty();
    if (inventory.getExternalId() != null) {
      existing = inventoryService.findByExternalId(inventory.getExternalId());
    } else if (inventory.getReferenceId() != null) {
      existing = inventoryService.findByReferenceId(inventory.getReferenceId());
    }
    return existing.map(InventoryProcessingService::countryOf).orElse(null);
  }

  private static String countryOf(Inventory inventory) {
    return inventory != null && inventory.getLocation() != null
        ? inventory.getLocation().getCountry()
        : null;
  }

  /**
   * Fire-and-forget refresh of the {@code inventory_country_summary} read-model for the affected
   * countries. Runs on a virtual thread so it never blocks message ack / retry throughput, and is
   * fully error-isolated so a summary failure can never disrupt inventory processing.
   */
  private void refreshCountrySummaries(String... countries) {
    Set<String> distinct =
        Arrays.stream(countries).filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
    for (String country : distinct) {
      virtualThreadService.runAsync(
          () -> {
            try {
              inventoryCountrySummaryService.refreshSummaryByCountry(country);
            } catch (Exception e) {
              log.warn(
                  "Failed to refresh inventory country summary for '{}': {}",
                  country,
                  e.getMessage());
            }
          });
    }
  }
}
