package com.mw.recommendation.engine.dto.csv;

import java.util.List;

/**
 * Outcome of a single CSV data row. {@code row} is the 1-based data-row index (blank lines are
 * skipped, header excluded). {@code referenceId} is the trimmed {@code inventory_id} cell value.
 * {@code messages} holds EVERY reason for the outcome — an INVALID row that fails multiple checks
 * (e.g. wrong country AND wrong media owner) carries one message per failed check; VALID/DUPLICATE
 * /not-found rows carry a single message.
 */
public record CsvRowResult(int row, String referenceId, CsvRowType type, List<String> messages) {}
