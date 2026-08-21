package com.mw.recommendation.engine.dto.csv;

import java.time.LocalDateTime;

/**
 * A row in the "Existing Files" list. {@code inventoryCount} is derived from {@code
 * inventoryRefIds.size()} (no stored count column).
 */
public record InventoryImportSummary(
    String importId,
    String campaignId,
    String fileName,
    String countryName,
    int inventoryCount,
    String createdBy,
    LocalDateTime createdAt) {}
