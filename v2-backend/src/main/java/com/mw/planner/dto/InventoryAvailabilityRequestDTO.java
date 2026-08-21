package com.mw.planner.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAvailabilityRequestDTO {
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private List<String> inventoryIds;
}
