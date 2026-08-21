package com.mw.recommendation.engine.dto.csv;

/** Per-row outcome of a CSV inventory import verification. */
public enum CsvRowType {
  VALID,
  INVALID,
  DUPLICATE
}
