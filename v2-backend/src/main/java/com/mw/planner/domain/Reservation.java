package com.mw.planner.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A short-lived hold on one inventory line item (PRD §9 "Reservations"). Consolidates V1's two
 * parallel, inconsistent tables (`campaignInventoryReservations` and `inventoryReservations` — see
 * the reservation V1-vs-V2 research note) into a single model, with the state-machine gaps V1 left
 * unwired (the sweeper, hold-requested creation, auto-release-on-reject) actually built.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "reservations")
@CompoundIndexes({
  @CompoundIndex(name = "campaign_owner_idx", def = "{'campaignId': 1, 'mediaOwnerId': 1}"),
  @CompoundIndex(name = "status_expiry_idx", def = "{'status': 1, 'expiresAt': 1}")
})
public class Reservation extends BaseEntity<String> {

  private String campaignId;
  private String mediaOwnerId;
  private String inventoryId;
  private String lineItemId; // CampaignInventorySchedules id
  private String requestedBy; // buyer userId

  @Builder.Default private Status status = Status.PENDING;

  private LocalDateTime reservedAt;
  private LocalDateTime expiresAt;

  @Builder.Default private int extensionCount = 0;
  private String lastExtendedBy;
  private LocalDateTime lastExtendedAt;

  private String declineReason;

  @Builder.Default private List<Comment> comments = new ArrayList<>();

  public enum Status {
    /** Added to the plan in the wizard but the campaign has not yet been submitted. */
    PENDING,
    /** Campaign submitted; awaiting the media owner's response. */
    HOLD_REQUESTED,
    /** Media owner approved; 7-day expiry clock running. */
    RESERVED,
    /** 7-day window passed without conversion. */
    EXPIRED,
    /** Buyer voluntarily released the hold. */
    RELEASED,
    /** Media owner declined. */
    DECLINED,
    /**
     * Campaign reached Approved — reservation-local terminal state, not a Campaign.Status value.
     */
    BOOKED
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Comment {
    private String userId;
    private String companyId;
    private String text;
    private LocalDateTime createdAt;
  }
}
