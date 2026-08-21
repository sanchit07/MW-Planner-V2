package com.mw.planner.enums;

/** Enum for pricing approval actions. */
public enum PricingAction {
  PENDING, // Initial state
  RATE_CARD, // Agency - Rate card pricing
  PROPOSED, // Agency - Proposed pricing
  COUNTERED, // Media Owner - Countered pricing
  ACCEPTED // Media Owner - Accepted pricing
}
