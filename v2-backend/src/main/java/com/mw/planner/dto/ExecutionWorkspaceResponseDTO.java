package com.mw.planner.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Media-owner Execution Workspace: everything one media owner needs to set up their line items and
 * hand the campaign off to Influence/OMS — scoped strictly to the viewer's own slice of the plan
 * (their inventories, schedules, costs and lines only).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionWorkspaceResponseDTO {

  private String campaignId;
  private String campaignName;
  private String planNumber;
  private String campaignStatus;
  private String agencyName;
  private String goalType;
  private Double goalTarget;
  private LocalDate startDate;
  private LocalDate endDate;
  private String currency;

  // ---- viewer gate ----
  /** True when the viewing media owner has approved the plan (workspace enabled). */
  private boolean approvedByViewer;

  /** Viewer's proposal status (PENDING/APPROVED/NEGOTIATING/REJECTED). */
  private String viewerProposalStatus;

  /** Whether the viewer's company has access to MW Influence. */
  private boolean hasInfluenceAccess;

  private boolean locked;
  private LocalDateTime pushedAt;
  private boolean canPush;
  private String pushBlockedReason;

  private Summary summary;
  private List<InventoryDetail> inventories;
  private List<LineItem> lines;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Summary {
    /** Approved media cost for the viewer's inventories (sum of schedule prices, no fees). */
    private Double approvedCost;

    /** Impressions the buyer's plan expects from the viewer's inventories. */
    private Long plannedImpressions;

    /** Max impressions deliverable given current availability (planned + free capacity). */
    private Long potentialImpressions;

    private Long plannedAdPlays;
    private int inventoryCount;
    private int lineCount;

    /** Sum of line targetImpressions (delivery committed so far across the viewer's lines). */
    private Long committedImpressions;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryDetail {
    private String id;
    private String name;
    private String classification;
    private String type;
    private String format;

    /** Loop capacity (spots per loop); null for classic inventory. */
    private Integer spotsPerLoop;

    private Double approvedCost;
    private Long plannedImpressions;
    private Long plannedAdPlays;
    private LocalDate scheduleStart;
    private LocalDate scheduleEnd;

    /** Estimated impressions one spot delivers per day on this screen. */
    private Long impressionsPerSpotPerDay;

    /** Planned + free-capacity impressions across the flight. */
    private Long potentialImpressions;

    /** Day-by-day loop occupancy across the campaign flight. */
    private List<TimelineDay> timeline;
  }

  /** One day of loop occupancy for an inventory. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TimelineDay {
    private LocalDate date;
    private int capacity;

    /** Spots consumed by this campaign's schedule. */
    private int bookedOwn;

    /** Spots consumed by other campaigns on the same screen. */
    private int bookedOther;

    private int free;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LineItem {
    private String id;
    private String classification;
    private String destination;
    private String purchaseType;
    private List<String> inventoryIds;
    private Double plannedCost;
    private Long plannedImpressions;
    private Long targetImpressions;
    private Double floorRate;
    private String handoffStatus;
    private String handoffError;
    private LocalDateTime handedOffAt;

    /** Max impressions this line's inventories can deliver (availability-aware ceiling). */
    private Long capacityImpressions;
  }
}
