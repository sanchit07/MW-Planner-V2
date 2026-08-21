package com.mw.planner.dto;

import com.mw.planner.domain.ExecutionPlan;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlanResponseDTO {

  private String campaignId;
  private String campaignName;
  private String campaignStatus;
  private Double budget;
  private String currency;
  private boolean locked;
  private LocalDateTime pushedAt;

  /** Whether an initial push is currently allowed (guardrails mirror the server-side checks). */
  private boolean canPush;

  /** When canPush is false and the plan is unlocked: NOT_APPROVED, UNACCEPTED_PRICES, NO_LINES. */
  private String pushBlockedReason;

  private Summary summary;
  private List<LineDTO> lines;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Summary {
    private int lineCount;
    private int inventoryCount;
    private Double totalPlannedCost;
    private Long totalPlannedImpressions;
    private int queuedCount;
    private int sentCount;
    private int acknowledgedCount;
    private int failedCount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LineDTO {
    private String id;
    private String mediaOwnerId;
    private String mediaOwnerName;
    private String classification;
    private String destination;
    private String purchaseType;
    private int inventoryCount;
    private List<InventoryItemDTO> inventories;
    private Double plannedCost;
    private Long plannedImpressions;
    private String handoffStatus;
    private String handoffError;
    private LocalDateTime handedOffAt;
    private Integer attemptCount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryItemDTO {
    private String id;
    private String name;
    private String classification;
    private String type;
    private String format;
  }

  public static ExecutionPlanResponseDTO.LineDTO mapLine(
      ExecutionPlan.Line line, List<InventoryItemDTO> inventories) {
    return LineDTO.builder()
        .id(line.getId())
        .mediaOwnerId(line.getMediaOwnerId())
        .mediaOwnerName(line.getMediaOwnerName())
        .classification(line.getClassification() != null ? line.getClassification().name() : null)
        .destination(line.getDestination() != null ? line.getDestination().name() : null)
        .purchaseType(line.getPurchaseType() != null ? line.getPurchaseType().name() : null)
        .inventoryCount(line.getInventoryIds() != null ? line.getInventoryIds().size() : 0)
        .inventories(inventories)
        .plannedCost(line.getPlannedCost())
        .plannedImpressions(line.getPlannedImpressions())
        .handoffStatus(
            line.getHandoffStatus() != null
                ? (line.getHandoffStatus() == ExecutionPlan.HandoffStatus.HANDED_OFF
                        ? ExecutionPlan.HandoffStatus.ACKNOWLEDGED
                        : line.getHandoffStatus())
                    .name()
                : null)
        .handoffError(line.getHandoffError())
        .handedOffAt(line.getHandedOffAt())
        .attemptCount(line.getAttemptCount())
        .build();
  }
}
