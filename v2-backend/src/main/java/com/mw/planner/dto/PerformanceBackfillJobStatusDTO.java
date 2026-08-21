package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status of a campaign performance backfill job")
public class PerformanceBackfillJobStatusDTO {

  public enum State {
    RUNNING,
    COMPLETED,
    FAILED
  }

  @Schema(description = "Unique job identifier", example = "b3f1c2d4-...")
  private String jobId;

  @Schema(description = "Current job state")
  private State state;

  @Schema(description = "Campaign statuses included in the sweep")
  private List<String> statuses;

  @Schema(description = "Number of campaigns processed so far")
  private long processed;

  @Schema(description = "Number of forecasts persisted")
  private long persisted;

  @Schema(description = "Number of campaigns skipped because the generated forecast was invalid")
  private long skippedInvalid;

  @Schema(description = "Number of campaigns skipped because performance was already populated")
  private long skippedAlreadyPopulated;

  @Schema(description = "Number of campaigns that failed forecast generation")
  private long failed;

  @Schema(description = "Job start time")
  private Instant startedAt;

  @Schema(description = "Job finish time (null while running)")
  private Instant finishedAt;

  @Schema(description = "Last error message, if any")
  private String lastError;
}
