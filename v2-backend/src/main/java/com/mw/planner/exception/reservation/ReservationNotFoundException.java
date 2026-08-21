package com.mw.planner.exception.reservation;

import com.mw.planner.enums.ErrorCode;
import com.mw.planner.exception.BaseException;

public class ReservationNotFoundException extends BaseException {

  public ReservationNotFoundException(String reservationId) {
    super(
        ErrorCode.RESERVATION_NOT_FOUND,
        "Reservation not found with ID: " + reservationId,
        reservationId);
  }
}
