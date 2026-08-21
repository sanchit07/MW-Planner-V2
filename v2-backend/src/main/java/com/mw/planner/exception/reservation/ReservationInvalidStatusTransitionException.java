package com.mw.planner.exception.reservation;

import com.mw.planner.domain.Reservation;
import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class ReservationInvalidStatusTransitionException extends BaseException {

  public ReservationInvalidStatusTransitionException(
      Reservation.Status from, String attemptedAction) {
    super(
        ErrorCode.RESERVATION_INVALID_STATUS_TRANSITION,
        "Cannot " + attemptedAction + " a reservation currently in status " + from,
        from.name(),
        attemptedAction);
  }
}
