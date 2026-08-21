package com.mw.planner.exception.reservation;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

/** Raised when a caller acts on a reservation for inventory their company does not own. */
public class ReservationNotOwnerException extends BaseException {

  public ReservationNotOwnerException(String reservationId) {
    super(
        ErrorCode.RESERVATION_NOT_OWNER,
        "Caller's company does not own the inventory for reservation " + reservationId,
        reservationId);
  }
}
