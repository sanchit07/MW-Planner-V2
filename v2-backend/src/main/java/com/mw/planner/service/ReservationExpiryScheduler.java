package com.mw.planner.service;

import com.mw.planner.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sweeps Reserved holds past their 7-day expiry window. V1 only ever exposed this as a manual
 * `/api/reservations/expire` endpoint (see the reservation V1-vs-V2 research note) — nothing called
 * it automatically. This is the real cron, mirroring {@link CampaignStatusScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

  private final ReservationRepository reservationRepository;

  @Scheduled(cron = "${mw-planner.scheduler.reservation-expiry.cron}")
  public void expireReservations() {
    try {
      long count = reservationRepository.bulkExpireReservations();
      if (count > 0) {
        log.info("Expired {} reservations past their 7-day hold window", count);
      }
    } catch (Exception e) {
      log.error("Error during scheduled reservation expiry sweep", e);
    }
  }
}
