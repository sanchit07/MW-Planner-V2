package com.mw.planner.repository;

import com.mw.planner.domain.Reservation;
import java.util.List;

public interface ReservationRepositoryCustom {

  /** Bulk-flips RESERVED rows whose expiresAt has passed to EXPIRED. Returns the count updated. */
  long bulkExpireReservations();

  /** Count of HOLD_REQUESTED rows awaiting this media owner's response. */
  long countPendingHoldRequests(String mediaOwnerId);

  /** Count of RESERVED rows for this media owner expiring within the next N hours. */
  long countExpiringHolds(String mediaOwnerId, int withinHours);

  /** Bulk status update for every reservation on a campaign — used by book/release side effects. */
  long bulkUpdateStatusForCampaign(
      String campaignId, List<Reservation.Status> from, Reservation.Status to);
}
