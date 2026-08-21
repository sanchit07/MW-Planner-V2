package com.mw.planner.domain;

import java.time.Instant;
import java.util.Map;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Canonical per-inventory availability document synced from IMS. The payload is stored in the exact
 * shape the frontend availability views consume (bookings, blackouts, schedule, loop config), keyed
 * by the inventory's external id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_availability")
public class InventoryAvailabilityRecord {

  @Id private String id;

  /** External inventory id (the id the frontend requests availability by). */
  @Indexed(unique = true)
  private String externalId;

  /** Availability payload in the inventory-api response shape. */
  private Map<String, Object> payload;

  /** When this record was last refreshed from the IMS feed. */
  private Instant syncedAt;

  /** Feed source identifier (e.g. "IMS"). */
  private String source;
}
