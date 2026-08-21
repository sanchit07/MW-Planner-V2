package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for budget setup */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Budget setup details")
public class BudgetSetupDTO {

  @JsonProperty("currency")
  @Schema(description = "Currency code", example = "USD")
  private String currency;

  @JsonProperty("budgetAmount")
  @Schema(description = "Budget amount", example = "250000")
  private Double budgetAmount;

  @JsonProperty("budgetType")
  @Schema(description = "Budget type", example = "FLEXIBLE")
  private String budgetType;
}
