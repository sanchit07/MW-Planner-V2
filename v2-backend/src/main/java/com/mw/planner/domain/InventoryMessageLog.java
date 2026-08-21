package com.mw.planner.domain;

import com.mw.planner.enums.MessageConsumeStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Observability record for every message received by the inventory RabbitMQ consumer. Persisted to
 * confirm whether a given message was actually received and consumed, queryable by {@code
 * inventoryId}, with the raw payload and timestamps.
 *
 * <p>Intentionally does <b>not</b> extend {@link BaseEntity}: the RabbitMQ consumer thread has no
 * {@code SecurityContext}, so the auditor-populated {@code createdBy}/{@code lastModifiedBy} fields
 * would always be null and carry no meaning. {@link #receivedAt} is the explicit TTL anchor.
 *
 * <p>No index annotations are declared here on purpose — indexes (the 3-day TTL on {@code
 * receivedAt} and a plain index on {@code inventoryId}) are created programmatically at startup by
 * {@code InventoryMessageLogIndexInitializer}, which is the sole creator for this collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_message_logs")
public class InventoryMessageLog {

  @Id private String id;

  /** Inventory id parsed off the message; may be null when unavailable. */
  private String inventoryId;

  /** "refresh" / "delete" / unsupported value / null. */
  private String operation;

  /** Raw AMQP body as received, before any deserialization (null-safe). */
  private String rawPayload;

  /** AMQP {@code messageId} header, if present (nullable). */
  private String messageId;

  /** AMQP {@code correlationId} header, if present (nullable). */
  private String correlationId;

  /** Outcome of consumption. */
  private MessageConsumeStatus status;

  /** Error detail when {@link #status} is {@code FAILED} (nullable). */
  private String errorMessage;

  /** When the consumer received the message — the TTL anchor. */
  private Instant receivedAt;

  /** When processing succeeded; null unless {@link #status} is {@code PROCESSED}. */
  private Instant processedAt;
}
