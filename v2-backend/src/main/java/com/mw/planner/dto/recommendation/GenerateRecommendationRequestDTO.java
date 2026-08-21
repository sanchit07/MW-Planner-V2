package com.mw.planner.dto.recommendation;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRecommendationRequestDTO {
  private List<String> mediaOwnerIds;
}
