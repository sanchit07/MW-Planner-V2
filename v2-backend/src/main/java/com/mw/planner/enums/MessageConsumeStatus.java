package com.mw.planner.enums;

/**
 * Outcome of an inventory message passing through {@code InventoryMessageConsumer}. Persisted on
 * {@code InventoryMessageLog} purely for observability/debugging.
 */
public enum MessageConsumeStatus {
  RECEIVED,
  PROCESSED,
  SKIPPED,
  DUPLICATE,
  FAILED
}
