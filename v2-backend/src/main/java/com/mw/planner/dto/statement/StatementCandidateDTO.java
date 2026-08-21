package com.mw.planner.dto.statement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row in {@code GET /api/v1/statements/candidates} — a campaign eligible (or not) for billing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementCandidateDTO {
  private String campaignId;
  private String campaignName;
  private boolean eligible;

  /** Why it's excluded, e.g. "Pending" when not all line items are approved. Null when eligible. */
  private String exclusionReason;

  private Double mediaCost;
}
