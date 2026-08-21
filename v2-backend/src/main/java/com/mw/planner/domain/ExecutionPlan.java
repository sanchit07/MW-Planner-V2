package com.mw.planner.domain;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Execution Plan for a campaign: the handoff of the finalized media plan to downstream execution
 * systems. Lines are grouped per media owner and per inventory classification — digital inventory
 * is handed off to Influence (or a DSP deal when the campaign has a DSP), classic inventory to the
 * OMS. Pushing the plan locks it and takes the campaign live (status ACTIVE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "execution_plans")
public class ExecutionPlan extends BaseEntity<String> {

  @NonNull
  @Indexed(unique = true)
  private String campaignId;

  private List<Line> lines;

  /** Once any line is handed off, the plan is locked (no edits/reset, only retry of failures). */
  private boolean locked;

  private LocalDateTime pushedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Line {
    private String id;
    private String mediaOwnerId;
    private String mediaOwnerName;
    private Classification classification;
    private Destination destination;
    private PurchaseType purchaseType;
    private List<String> inventoryIds;
    private Double plannedCost;
    private Long plannedImpressions;
    private HandoffStatus handoffStatus;
    private String handoffError;
    private LocalDateTime handedOffAt;

    /** When the line was last (re-)queued for handoff; drives the simulated progression. */
    private LocalDateTime queuedAt;

    /** Number of handoff attempts so far (0 = never pushed). */
    private Integer attemptCount;

    /** Media-owner set floor rate (CPM) for the line; used by downstream deal setup. */
    private Double floorRate;

    /**
     * Impressions this line commits to deliver. Required for GUARANTEED lines (must fit within
     * projected capacity); optional pacing hint for other purchase types.
     */
    private Long targetImpressions;
  }

  public enum Classification {
    DIGITAL,
    CLASSIC
  }

  public enum Destination {
    INFLUENCE,
    OMS
  }

  public enum PurchaseType {
    GUARANTEED,
    DIRECT,
    ORDER
  }

  /**
   * Lifecycle of a line handoff. PENDING_HANDOFF → QUEUED → SENT → ACKNOWLEDGED, or FAILED at the
   * SENT step (retryable). HANDED_OFF is a legacy terminal value from before the staged lifecycle;
   * it is normalized to ACKNOWLEDGED on read.
   */
  public enum HandoffStatus {
    PENDING_HANDOFF,
    QUEUED,
    SENT,
    ACKNOWLEDGED,
    FAILED,
    HANDED_OFF
  }
}
