package com.mw.planner.service;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Reservation;
import com.mw.planner.dto.reservation.ReservationDTO;
import com.mw.planner.exception.reservation.ReservationInvalidStatusTransitionException;
import com.mw.planner.exception.reservation.ReservationNotFoundException;
import com.mw.planner.exception.reservation.ReservationNotOwnerException;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reservations — short-lived holds on inventory (PRD §9). Consolidates V1's two parallel tables
 * into one model and, unlike V1, actually wires hold-requested creation on submission, a real
 * expiry sweep (see {@link ReservationExpiryScheduler}), and auto-release on campaign rejection —
 * see the reservation V1-vs-V2 research note for what V1 left unbuilt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

  private static final int DEFAULT_RESERVATION_DAYS = 7;

  private final ReservationRepository reservationRepository;
  private final CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  private final CampaignRepository campaignRepository;
  private final UserService userService;

  // ---- Campaign-lifecycle side effects (called from CampaignService.changeCampaignStatus) ----

  /** Draft/Planned/Negotiating → Reviewing (Submit Plan): one HOLD_REQUESTED row per line item. */
  public void createHoldRequestsForCampaign(String campaignId, String requestedBy) {
    List<CampaignInventorySchedules> lineItems =
        campaignInventorySchedulesRepository.findByCampaignId(campaignId);
    for (CampaignInventorySchedules lineItem : lineItems) {
      boolean alreadyRequested =
          reservationRepository
              .findByCampaignIdAndStatusIn(
                  campaignId,
                  List.of(
                      Reservation.Status.HOLD_REQUESTED,
                      Reservation.Status.RESERVED,
                      Reservation.Status.BOOKED))
              .stream()
              .anyMatch(r -> lineItem.getId().equals(r.getLineItemId()));
      if (alreadyRequested) continue; // resubmission after a counter — don't duplicate live holds

      Reservation reservation =
          Reservation.builder()
              .campaignId(campaignId)
              .mediaOwnerId(lineItem.getMediaOwnerId())
              .inventoryId(lineItem.getInventoryId())
              .lineItemId(lineItem.getId())
              .requestedBy(requestedBy)
              .status(Reservation.Status.HOLD_REQUESTED)
              .build();
      reservationRepository.save(reservation);
    }
    log.info("Created hold requests for campaign {} ({} line items)", campaignId, lineItems.size());
  }

  /** Campaign reaches Approved: every Reserved row for the campaign flips to Booked. */
  public void bookReservationsForCampaign(String campaignId) {
    long updated =
        reservationRepository.bulkUpdateStatusForCampaign(
            campaignId, List.of(Reservation.Status.RESERVED), Reservation.Status.BOOKED);
    log.info("Booked {} reservations for approved campaign {}", updated, campaignId);
  }

  /** Campaign rejected at any stage: all open holds release back to the pool. */
  public void releaseReservationsForCampaign(String campaignId) {
    long updated =
        reservationRepository.bulkUpdateStatusForCampaign(
            campaignId,
            List.of(Reservation.Status.HOLD_REQUESTED, Reservation.Status.RESERVED),
            Reservation.Status.RELEASED);
    log.info("Released {} reservations for rejected campaign {}", updated, campaignId);
  }

  // ---- Buyer/seller-facing operations ----

  public List<ReservationDTO> listForCampaign(String campaignId) {
    return reservationRepository.findByCampaignId(campaignId).stream()
        .map(ReservationDTO::from)
        .toList();
  }

  /** Seller queue — every hold requested against the acting (media owner) company's inventory. */
  public List<ReservationDTO> listForMediaOwner() {
    String companyId = userService.getActingCompanyId();
    return reservationRepository.findByMediaOwnerId(companyId).stream()
        .map(ReservationDTO::from)
        .toList();
  }

  public ReservationDTO approve(String reservationId) {
    Reservation reservation = getOwnedByActingCompany(reservationId);
    requireStatus(reservation, Reservation.Status.HOLD_REQUESTED, "approve");
    reservation.setStatus(Reservation.Status.RESERVED);
    reservation.setReservedAt(LocalDateTime.now());
    reservation.setExpiresAt(LocalDateTime.now().plusDays(DEFAULT_RESERVATION_DAYS));
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  public ReservationDTO approveWithConditions(String reservationId, String comment) {
    Reservation reservation = getOwnedByActingCompany(reservationId);
    requireStatus(reservation, Reservation.Status.HOLD_REQUESTED, "approve with conditions");
    var context = userService.getIamUserContext();
    reservation
        .getComments()
        .add(
            Reservation.Comment.builder()
                .userId(context.getUserId())
                .companyId(userService.getActingCompanyId())
                .text(comment)
                .createdAt(LocalDateTime.now())
                .build());
    // The hold itself is not honoured until the buyer responds to the condition and the seller
    // re-approves — status stays HOLD_REQUESTED; the comment thread carries the negotiation.
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  public ReservationDTO decline(String reservationId, String reason) {
    Reservation reservation = getOwnedByActingCompany(reservationId);
    requireStatus(reservation, Reservation.Status.HOLD_REQUESTED, "decline");
    reservation.setStatus(Reservation.Status.DECLINED);
    reservation.setDeclineReason(reason);
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  /**
   * Buyer-side: extends a Reserved hold's expiry. Seller can also just re-approve via decline+redo.
   */
  public ReservationDTO extend(String reservationId, int additionalDays) {
    Reservation reservation = getOwnedByActingBuyer(reservationId);
    requireStatus(reservation, Reservation.Status.RESERVED, "extend");
    var context = userService.getIamUserContext();
    reservation.setExpiresAt(reservation.getExpiresAt().plusDays(additionalDays));
    reservation.setExtensionCount(reservation.getExtensionCount() + 1);
    reservation.setLastExtendedBy(context.getUserId());
    reservation.setLastExtendedAt(LocalDateTime.now());
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  /** Buyer voluntarily releases a Reserved hold. Irreversible — a fresh hold must be requested. */
  public ReservationDTO release(String reservationId) {
    Reservation reservation = getOwnedByActingBuyer(reservationId);
    requireStatus(reservation, Reservation.Status.RESERVED, "release");
    reservation.setStatus(Reservation.Status.RELEASED);
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  /**
   * Manual escape hatch for the rare case where the automatic Approved→Booked flip fails to fire.
   */
  public ReservationDTO convertToBooking(String reservationId) {
    Reservation reservation = getOwnedByActingBuyer(reservationId);
    requireStatus(reservation, Reservation.Status.RESERVED, "convert to booking");
    reservation.setStatus(Reservation.Status.BOOKED);
    reservation.setExpiresAt(null);
    return ReservationDTO.from(reservationRepository.save(reservation));
  }

  // ---- Dashboard widgets ----

  public long pendingHoldRequestsCount() {
    return reservationRepository.countPendingHoldRequests(userService.getActingCompanyId());
  }

  public long expiringHoldsCount(int withinHours) {
    return reservationRepository.countExpiringHolds(userService.getActingCompanyId(), withinHours);
  }

  public com.mw.planner.dto.reservation.ReservationDashboardWidgetsDTO getDashboardWidgets() {
    return com.mw.planner.dto.reservation.ReservationDashboardWidgetsDTO.builder()
        .pendingHoldRequests(pendingHoldRequestsCount())
        .expiringHolds(expiringHoldsCount(72)) // "Aging Holds" style window — 3-day lookahead
        .build();
  }

  // ---- helpers ----

  private Reservation getById(String reservationId) {
    return reservationRepository
        .findById(reservationId)
        .orElseThrow(() -> new ReservationNotFoundException(reservationId));
  }

  /**
   * Loads a reservation and asserts the acting company owns the inventory it's against — closes the
   * gap V1's own code admitted ("for now, allow any media owner user to accept").
   */
  private Reservation getOwnedByActingCompany(String reservationId) {
    Reservation reservation = getById(reservationId);
    String actingCompanyId = userService.getActingCompanyId();
    if (!reservation.getMediaOwnerId().equals(actingCompanyId)
        && !userService.isCurrentUserGlobalAdmin()) {
      throw new ReservationNotOwnerException(reservationId);
    }
    return reservation;
  }

  /**
   * Loads a reservation and asserts the acting company is the campaign's buyer (creator or
   * shared-access company) — the three buyer-initiated actions (extend/release/convertToBooking)
   * must not be callable by an unrelated company just by guessing a reservation ID. Mirrors
   * ExecutionPlanService's isBuyerSide check.
   */
  private Reservation getOwnedByActingBuyer(String reservationId) {
    Reservation reservation = getById(reservationId);
    if (userService.isCurrentUserGlobalAdmin()) {
      return reservation;
    }
    String actingCompanyId = userService.getActingCompanyId();
    Campaign campaign =
        campaignRepository
            .findById(reservation.getCampaignId())
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    boolean isBuyer =
        actingCompanyId != null
            && (actingCompanyId.equals(campaign.getCompanyId())
                || (campaign.getCompanyAccess() != null
                    && campaign.getCompanyAccess().contains(actingCompanyId)));
    if (!isBuyer) {
      throw new ReservationNotOwnerException(reservationId);
    }
    return reservation;
  }

  private void requireStatus(Reservation reservation, Reservation.Status required, String action) {
    if (reservation.getStatus() != required) {
      throw new ReservationInvalidStatusTransitionException(reservation.getStatus(), action);
    }
  }
}
