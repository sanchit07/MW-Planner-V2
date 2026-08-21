package com.mw.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payloads for media-owner line-item editing in the Execution Workspace. One class with
 * optional fields keeps the controller surface small; each endpoint reads only what it needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLineRequestDTO {

  // PATCH line
  private Double floorRate;
  private Long targetImpressions;
  private String purchaseType;

  // POST create line
  private String classification;

  // POST move inventory
  private String inventoryId;
  private String fromLineId;
}
