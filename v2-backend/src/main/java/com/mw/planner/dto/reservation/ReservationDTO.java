package com.mw.planner.dto.reservation;

import com.mw.planner.domain.Reservation;
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
public class ReservationDTO {

  private String id;
  private String campaignId;
  private String mediaOwnerId;
  private String inventoryId;
  private String lineItemId;
  private String requestedBy;
  private Reservation.Status status;
  private LocalDateTime reservedAt;
  private LocalDateTime expiresAt;
  private int extensionCount;
  private String declineReason;
  private List<Reservation.Comment> comments;

  public static ReservationDTO from(Reservation r) {
    return ReservationDTO.builder()
        .id(r.getId())
        .campaignId(r.getCampaignId())
        .mediaOwnerId(r.getMediaOwnerId())
        .inventoryId(r.getInventoryId())
        .lineItemId(r.getLineItemId())
        .requestedBy(r.getRequestedBy())
        .status(r.getStatus())
        .reservedAt(r.getReservedAt())
        .expiresAt(r.getExpiresAt())
        .extensionCount(r.getExtensionCount())
        .declineReason(r.getDeclineReason())
        .comments(r.getComments())
        .build();
  }
}
