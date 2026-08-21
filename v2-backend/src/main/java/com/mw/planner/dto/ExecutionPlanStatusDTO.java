package com.mw.planner.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight execution state for the campaign view. Unlike the full execution-plan endpoint this
 * never generates a baseline plan as a side effect.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlanStatusDTO {

  private String campaignId;

  /** Whether an execution plan document exists for this campaign at all. */
  private boolean exists;

  /** True once the plan has been pushed (locked). */
  private boolean locked;

  private LocalDateTime pushedAt;
  private int lineCount;
  private int acknowledgedCount;
  private int failedCount;

  /** Lines currently in flight (queued or sent). */
  private int inProgressCount;
}
