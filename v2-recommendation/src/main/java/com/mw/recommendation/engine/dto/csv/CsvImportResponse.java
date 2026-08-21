package com.mw.recommendation.engine.dto.csv;

/** Result of persisting a verified CSV import: the new import id plus the verification detail. */
public record CsvImportResponse(String importId, CsvVerifyResponse result) {}
