package com.mw.planner.dto.creative;

import com.mw.planner.domain.Creative;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreativeTier1StatusRequestDTO {

  @NotNull private Creative.Tier1Status tier1Status;

  /** Required when tier1Status is INADEQUATE. */
  private String rejectionReason;
}
