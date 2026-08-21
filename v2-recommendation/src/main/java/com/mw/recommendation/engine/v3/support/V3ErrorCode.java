package com.mw.recommendation.engine.v3.support;

/**
 * Structured v3 error codes per PRD §14.6. Kept separate from the shared {@code ErrorCode} enum so
 * v1/v2 error handling is untouched. Codes surface on the run status (async failures cannot throw
 * HTTP errors) and via the v3 exception advice for synchronous failures.
 */
public enum V3ErrorCode {
  NO_DATES,
  NO_COUNTRY,
  NO_BUDGET,
  NO_INVENTORY,
  INSUFFICIENT_DATA,
  BUDGET_TOO_LOW,
  GOAL_UNREACHABLE,
  RUN_NOT_FOUND,
  RUN_IN_PROGRESS,
  INTERNAL_ERROR
}
