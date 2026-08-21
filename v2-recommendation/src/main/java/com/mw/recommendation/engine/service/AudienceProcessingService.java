package com.mw.recommendation.engine.service;

import com.mw.recommendation.engine.domain.AudienceData;
import com.mw.recommendation.engine.dto.ExternalAudienceMessageDTO;
import com.mw.recommendation.engine.rabbitmq.ExternalAudienceMessageConverter;
import com.mw.recommendation.engine.repository.AudienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing audience messages and handling all business logic related to
 * audience data and their persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudienceProcessingService {

  private final ExternalAudienceMessageConverter messageConverter;
  private final AudienceRepository audienceRepository;

  /**
   * Process an audience message and handle all related business logic. Note: MongoDB operations are
   * atomic at the document level, so @Transactional is not needed.
   *
   * @param message The external audience message to process
   */
  public void processAudienceMessage(ExternalAudienceMessageDTO message) {
    String inventoryId = message.getInventoryId();
    log.info("Processing audience message for inventoryId: {}", inventoryId);

    try {
      // Convert to internal entity
      AudienceData audienceData = messageConverter.convertToAudienceData(message);

      // Save audience data
      saveAudienceData(audienceData);

      log.info("Successfully processed audience message for inventoryId: {}", inventoryId);

    } catch (Exception e) {
      log.error(
          "Error processing audience message for inventoryId: {}: {}",
          inventoryId,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to process audience message", e);
    }
  }

  /** Save audience data to database with upsert logic */
  private void saveAudienceData(AudienceData audienceData) {
    try {
      // Check if audience data already exists by inventory ID or reference ID
      AudienceData existingAudienceData = null;

      if (audienceData.getInventoryId() != null) {
        existingAudienceData =
            audienceRepository.findByInventoryId(audienceData.getInventoryId()).orElse(null);
      }

      if (existingAudienceData == null && audienceData.getReferenceId() != null) {
        existingAudienceData =
            audienceRepository.findByReferenceId(audienceData.getReferenceId()).orElse(null);
      }

      if (existingAudienceData != null) {
        // Update existing audience data
        log.info(
            "Updating existing audience data with ID: {} for inventoryId: {}",
            existingAudienceData.getId(),
            audienceData.getInventoryId());

        // Update fields from new message
        updateExistingAudienceData(existingAudienceData, audienceData);
        audienceRepository.save(existingAudienceData);

        log.info(
            "Updated audience data with ID: {} for inventoryId: {}",
            existingAudienceData.getId(),
            audienceData.getInventoryId());
      } else {
        // Create new audience data
        log.info("Creating new audience data for inventoryId: {}", audienceData.getInventoryId());
        AudienceData savedAudienceData = audienceRepository.save(audienceData);

        log.info(
            "Created new audience data with ID: {} for inventoryId: {}",
            savedAudienceData.getId(),
            savedAudienceData.getInventoryId());
      }

    } catch (Exception e) {
      log.error(
          "Error saving audience data for inventoryId: {}: {}",
          audienceData.getInventoryId(),
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to save audience data", e);
    }
  }

  /** Update existing audience data with new data */
  private void updateExistingAudienceData(AudienceData existing, AudienceData newData) {
    // Update core identifiers
    if (newData.getInventoryId() != null) {
      existing.setInventoryId(newData.getInventoryId());
    }
    if (newData.getReferenceId() != null) {
      existing.setReferenceId(newData.getReferenceId());
    }
    if (newData.getBillboardObjectId() != null) {
      existing.setBillboardObjectId(newData.getBillboardObjectId());
    }

    // Update monthly summary
    if (newData.getMonthlySummary() != null) {
      existing.setMonthlySummary(newData.getMonthlySummary());
    }

    // Update daily summary
    if (newData.getDailySummary() != null) {
      existing.setDailySummary(newData.getDailySummary());
    }

    // Update hourly summary
    if (newData.getHourlySummary() != null) {
      existing.setHourlySummary(newData.getHourlySummary());
    }

    // Update audience segments
    if (newData.getAudienceSegments() != null) {
      existing.setAudienceSegments(newData.getAudienceSegments());
    }

    // Update frequency model
    if (newData.getFrequencyModel() != null) {
      existing.setFrequencyModel(newData.getFrequencyModel());
    }

    // Update metadata
    if (newData.getUpdatedAt() != null) {
      existing.setUpdatedAt(newData.getUpdatedAt());
    }
    if (newData.getDataSource() != null) {
      existing.setDataSource(newData.getDataSource());
    }
  }
}
