package com.mw.recommendation.engine.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for marking selected inventories for a recommendation run */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectedInventoriesDTO {
  private List<String> inventoryIds; // List of selected inventory IDs
}
