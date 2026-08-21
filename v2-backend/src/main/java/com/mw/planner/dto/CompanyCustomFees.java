package com.mw.planner.dto;

import com.mw.planner.domain.CustomFee;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds custom fees for a single company split by visibility (hidden vs visible in media plan).
 * Used by {@link CustomFeesContext} for both company-level and campaign-level fees.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCustomFees {

  @Builder.Default private List<CustomFee> hidden = Collections.emptyList();
  @Builder.Default private List<CustomFee> visible = Collections.emptyList();
}
