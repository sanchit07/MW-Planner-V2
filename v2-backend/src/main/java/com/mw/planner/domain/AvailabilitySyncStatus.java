package com.mw.planner.domain;

import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Status of the IMS availability sync pipeline (single document, id = "ims-availability"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "availability_sync_status")
public class AvailabilitySyncStatus {

  public static final String IMS_AVAILABILITY_ID = "ims-availability";

  public enum State {
    RUNNING,
    SUCCESS,
    FAILED
  }

  public enum Trigger {
    SCHEDULED,
    MANUAL,
    ON_DEMAND
  }

  @Id private String id;

  private State state;
  private Trigger trigger;
  private Instant startedAt;
  private Instant completedAt;

  /** Completion time of the most recent successful full sync. */
  private Instant lastSuccessAt;

  /** Number of inventories ingested in the last completed run. */
  private Integer inventoryCount;

  /** Error message of the last failed run (null when the last run succeeded). */
  private String error;
}
