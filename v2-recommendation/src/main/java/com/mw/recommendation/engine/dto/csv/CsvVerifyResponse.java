package com.mw.recommendation.engine.dto.csv;

import com.mw.recommendation.engine.dto.PaginatedRecommendationResponseDTO.RecommendedInventory;
import java.util.List;

/**
 * Result of verifying a CSV of inventory reference ids. {@code matchedInventories} are the full
 * inventory objects (same shape as browse/results) for the deduped VALID rows — the frontend feeds
 * these into its selection so ids survive the deals-api attach payload.
 */
public record CsvVerifyResponse(
    List<CsvRowResult> rows,
    List<RecommendedInventory> matchedInventories,
    String countryName,
    int totalRows,
    int validCount,
    int invalidCount,
    int duplicateCount) {}
