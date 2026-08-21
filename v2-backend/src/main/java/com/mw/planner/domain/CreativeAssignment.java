package com.mw.planner.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Binds one {@link Creative} to one campaign line item ({@link CampaignInventorySchedules}). One
 * creative per line item, matching V1's single nullable FK — but with the five gating rules from
 * PRD §11.1 actually enforced (V1 enforced none of them; see {@code creatives-v1-vs-v2} research).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "creative_assignments")
public class CreativeAssignment extends BaseEntity<String> {

  private String creativeId;

  /** The {@link CampaignInventorySchedules} id this creative is bound to — one per line item. */
  @Indexed(unique = true)
  private String lineItemId;

  @Indexed private String campaignId;
  private String mediaOwnerId;
  private String inventoryId;

  private BindingStatus bindingStatus;

  private boolean forcedMatch;
  private String forcedMatchReason;
  private String forcedMatchBy;
  private java.time.LocalDateTime forcedMatchAt;

  /** Creative spec at bind time, used to detect a same-spec vs. different-spec swap later. */
  private SpecSnapshot specSnapshot;

  public enum BindingStatus {
    BOUND,
    FORCED_MATCH,
    PENDING_REAPPROVAL,
    REJECTED
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SpecSnapshot {
    private String aspectRatio;
    private Integer durationSeconds;
    private Long fileSizeBytes;

    public boolean sameSpecAs(SpecSnapshot other) {
      if (other == null) return false;
      return java.util.Objects.equals(aspectRatio, other.aspectRatio)
          && java.util.Objects.equals(durationSeconds, other.durationSeconds);
    }
  }
}
