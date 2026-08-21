package com.mw.planner.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for the push endpoint: when retryLineIds is set, only those (failed) lines are
 * retried.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlanPushRequestDTO {
  private List<String> retryLineIds;

  /**
   * Optional subset for the initial push: only these lines are queued (media-owner workspace pushes
   * just its own lines). Null/empty = push every pending line.
   */
  private List<String> lineIds;
}
