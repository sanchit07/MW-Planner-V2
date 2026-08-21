package com.mw.planner.repository;

import com.mw.planner.domain.Reservation;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository
    extends MongoRepository<Reservation, String>, ReservationRepositoryCustom {

  List<Reservation> findByCampaignId(String campaignId);

  List<Reservation> findByMediaOwnerId(String mediaOwnerId);

  List<Reservation> findByCampaignIdAndStatusIn(
      String campaignId, List<Reservation.Status> statuses);

  List<Reservation> findByRequestedBy(String requestedBy);
}
