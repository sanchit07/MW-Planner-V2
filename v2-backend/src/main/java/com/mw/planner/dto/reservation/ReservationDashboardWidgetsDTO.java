package com.mw.planner.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDashboardWidgetsDTO {
  private long pendingHoldRequests;
  private long expiringHolds;
}
