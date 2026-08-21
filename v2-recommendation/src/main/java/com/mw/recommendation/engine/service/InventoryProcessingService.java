package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.Inventory;
import com.mw.recommendation.engine.dto.ExternalInventoryMessageDTO;
import com.mw.recommendation.engine.rabbitmq.ExternalInventoryMessageConverter;
import com.mw.recommendation.engine.repository.InventoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryProcessingService {

  private final ExternalInventoryMessageConverter messageConverter;
  private final InventoryRepository inventoryRepository;
  private final MongoTemplate mongoTemplate;
  private final Optional<CachingConnectionFactory> connectionFactoryOpt;

  public void processInventoryMessage(ExternalInventoryMessageDTO message) {
    String referenceId =
        message.getExternalIds() != null && !message.getExternalIds().isEmpty()
            ? message.getExternalIds().get(0).getExternalId()
            : null;
    log.info("Processing inventory message for referenceId: {}", referenceId);

    try {
      Inventory inventory = messageConverter.convertToInventory(message);
      saveInventory(inventory);
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

  public void deleteInventory(String inventoryId) {
    log.info("Deleting inventory with inventoryId: {}", inventoryId);
    try {
      inventoryRepository.deleteByInventoryId(inventoryId);
      log.info("Successfully deleted inventory with inventoryId: {}", inventoryId);
    } catch (Exception e) {
      log.error(
          "Error deleting inventory with inventoryId: {}: {}", inventoryId, e.getMessage(), e);
      throw new RuntimeException("Failed to delete inventory", e);
    }
  }

  /**
   * Atomically upserts inventory using MongoTemplate to prevent duplicate documents under
   * concurrent message delivery. Replaces the previous read-then-write pattern which was
   * susceptible to race conditions when 5 concurrent listeners processed refresh events for the
   * same inventory simultaneously.
   */
  private void saveInventory(Inventory inventory) {
    if (inventory.getInventoryId() == null && inventory.getReferenceId() == null) {
      log.warn(
          "Skipping inventory upsert - both inventoryId and referenceId are null, cannot identify document");
      return;
    }

    Query query =
        inventory.getInventoryId() != null
            ? Query.query(Criteria.where("inventoryId").is(inventory.getInventoryId()))
            : Query.query(Criteria.where("referenceId").is(inventory.getReferenceId()));

    Update update = buildUpdate(inventory);
    mongoTemplate.upsert(query, update, Inventory.class);

    log.info(
        "Upserted inventory for inventoryId: {}, referenceId: {}",
        inventory.getInventoryId(),
        inventory.getReferenceId());
  }

  private Update buildUpdate(Inventory inventory) {
    Update update = new Update();

    if (inventory.getInventoryId() != null) update.set("inventoryId", inventory.getInventoryId());
    if (inventory.getReferenceId() != null) update.set("referenceId", inventory.getReferenceId());
    if (inventory.getName() != null) update.set("name", inventory.getName());
    if (inventory.getClassification() != null)
      update.set("classification", inventory.getClassification());
    if (inventory.getType() != null) update.set("type", inventory.getType());
    if (inventory.getFormat() != null) update.set("format", inventory.getFormat());
    if (inventory.getEnvironment() != null) update.set("environment", inventory.getEnvironment());
    if (inventory.getViewingDistance() != null)
      update.set("viewingDistance", inventory.getViewingDistance());
    if (inventory.getVenueTypes() != null) update.set("venueTypes", inventory.getVenueTypes());
    if (inventory.getVenueTypeIds() != null)
      update.set("venueTypeIds", inventory.getVenueTypeIds());
    if (inventory.getArchived() != null) {
      update.set("archived", inventory.getArchived());
      update.set("active", !inventory.getArchived());
    }
    if (inventory.getLocationCoordinates() != null)
      update.set("locationCoordinates", inventory.getLocationCoordinates());
    if (inventory.getAddress() != null) update.set("address", inventory.getAddress());
    if (inventory.getLocationHierarchy() != null)
      update.set("locationHierarchy", inventory.getLocationHierarchy());
    if (inventory.getPanels() != null) update.set("panels", inventory.getPanels());
    if (inventory.getMediaOwnerId() != null)
      update.set("mediaOwnerId", inventory.getMediaOwnerId());
    if (inventory.getMediaOwnerName() != null)
      update.set("mediaOwnerName", inventory.getMediaOwnerName());
    if (inventory.getThumbnailUrl() != null)
      update.set("thumbnailUrl", inventory.getThumbnailUrl());
    if (inventory.getOperatingTimes() != null)
      update.set("operatingTimes", inventory.getOperatingTimes());
    if (inventory.getSellingTerm() != null) update.set("sellingTerm", inventory.getSellingTerm());
    if (inventory.getOrientation() != null) update.set("orientation", inventory.getOrientation());
    if (inventory.getTimeZone() != null) update.set("timeZone", inventory.getTimeZone());
    if (inventory.getRequiresContentApproval() != null)
      update.set("requiresContentApproval", inventory.getRequiresContentApproval());
    if (inventory.getProgrammaticDealTypes() != null)
      update.set("programmaticDealTypes", inventory.getProgrammaticDealTypes());
    if (inventory.getCreativeFormats() != null)
      update.set("creativeFormats", inventory.getCreativeFormats());
    if (inventory.getPrices() != null) update.set("prices", inventory.getPrices());
    if (inventory.getDigitalFields() != null)
      update.set("digitalFields", inventory.getDigitalFields());
    if (inventory.getDsps() != null) update.set("dsps", inventory.getDsps());
    if (inventory.getContentExclusions() != null)
      update.set("contentExclusions", inventory.getContentExclusions());
    if (inventory.getMedias() != null) update.set("medias", inventory.getMedias());
    if (inventory.getTags() != null) update.set("tags", inventory.getTags());
    // Guard against EMPTY (not just null): a refresh event that sends externalIds=[] must NOT
    // overwrite the stored ids — that wipe drops the CMS "Device ID" (extractDeviceId reads the
    // platform=="CMS" entry) and re-adding the device doesn't help, since the next empty refresh
    // clears it again. Preserve on empty; only a populated list updates.
    if (inventory.getExternalIds() != null && !inventory.getExternalIds().isEmpty())
      update.set("externalIds", inventory.getExternalIds());
    if (inventory.getBlackouts() != null) update.set("blackouts", inventory.getBlackouts());
    if (inventory.getSize() != null) update.set("size", inventory.getSize());
    if (inventory.getInventoryCluster() != null)
      update.set("inventoryCluster", inventory.getInventoryCluster());
    if (inventory.getLastSyncedAt() != null)
      update.set("lastSyncedAt", inventory.getLastSyncedAt());

    String connectionInfo =
        connectionFactoryOpt.map(cf -> cf.getHost() + ":" + cf.getPort()).orElse("unknown");
    update.set("lastSyncedFrom", connectionInfo);

    return update;
  }
}
