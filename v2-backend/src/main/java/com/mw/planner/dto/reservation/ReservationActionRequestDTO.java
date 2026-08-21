package com.mw.planner.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shared body shape for the reservation action endpoints that need a comment/reason/day count. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationActionRequestDTO {
  private String comment;
  private String reason;
  private Integer additionalDays;
}
