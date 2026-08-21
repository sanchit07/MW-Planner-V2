package com.mw.planner.domain;

import com.mw.planner.enums.PricingAction;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "campaign_inventory_schedules")
@CompoundIndexes({
  @CompoundIndex(name = "campaignId_inventoryId", def = "{'campaignId': 1, 'inventoryId': 1}"),
  @CompoundIndex(name = "campaignId_mediaOwnerId", def = "{'campaignId': 1, 'mediaOwnerId': 1}")
})
public class CampaignInventorySchedules extends BaseEntity<String> {

  @NonNull @Indexed private String campaignId;

  @NonNull @Indexed private String mediaOwnerId;

  @NonNull @Indexed private String inventoryId;

  private List<String> scheduleIds;

  /** List of approved schedule IDs for pricing approval */
  private List<String> approvedScheduleIds;

  /** Pricing approval history */
  private List<History> history;

  /** User ID who approved the pricing */
  private String approvedBy;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class History {
    private String userId;
    private String companyId;
    private PricingAction action;
    private LocalDateTime date;
    private Double effectiveDiscountPercentage;
  }
}
