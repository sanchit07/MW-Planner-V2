package com.mw.planner.repository.impl;

import com.mw.planner.domain.Reservation;
import com.mw.planner.repository.ReservationRepositoryCustom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public long bulkExpireReservations() {
    Query query =
        new Query(
            Criteria.where("status")
                .is(Reservation.Status.RESERVED)
                .and("expiresAt")
                .lt(LocalDateTime.now()));
    Update update = new Update().set("status", Reservation.Status.EXPIRED);
    return mongoTemplate.updateMulti(query, update, Reservation.class).getModifiedCount();
  }

  @Override
  public long countPendingHoldRequests(String mediaOwnerId) {
    Query query =
        new Query(
            Criteria.where("mediaOwnerId")
                .is(mediaOwnerId)
                .and("status")
                .is(Reservation.Status.HOLD_REQUESTED));
    return mongoTemplate.count(query, Reservation.class);
  }

  @Override
  public long countExpiringHolds(String mediaOwnerId, int withinHours) {
    Query query =
        new Query(
            Criteria.where("mediaOwnerId")
                .is(mediaOwnerId)
                .and("status")
                .is(Reservation.Status.RESERVED)
                .and("expiresAt")
                .lt(LocalDateTime.now().plusHours(withinHours)));
    return mongoTemplate.count(query, Reservation.class);
  }

  @Override
  public long bulkUpdateStatusForCampaign(
      String campaignId, java.util.List<Reservation.Status> from, Reservation.Status to) {
    Query query = new Query(Criteria.where("campaignId").is(campaignId).and("status").in(from));
    Update update = new Update().set("status", to);
    if (to == Reservation.Status.BOOKED || to == Reservation.Status.RELEASED) {
      // Terminal states — the expiry countdown is no longer meaningful.
      update.unset("expiresAt");
    }
    return mongoTemplate.updateMulti(query, update, Reservation.class).getModifiedCount();
  }
}
