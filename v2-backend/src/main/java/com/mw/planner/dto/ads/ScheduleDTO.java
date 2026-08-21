package com.mw.planner.dto.ads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for schedule configuration */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Schedule configuration")
public class ScheduleDTO {

  @JsonProperty("type")
  @Schema(
      description = "Schedule type",
      example = "DEFAULT",
      allowableValues = {"DEFAULT", "WEEKDAY", "WEEKEND", "CUSTOM"})
  private String type;

  @JsonProperty("priority")
  @Schema(description = "Schedule priority", example = "1")
  private Integer priority;

  @JsonProperty("daysOfWeek")
  @Schema(description = "Days of week (1=Monday, 7=Sunday)", example = "[1, 2, 3, 4, 5]")
  private List<Integer> daysOfWeek;

  @JsonProperty("validity")
  @Schema(description = "Schedule validity period")
  private ValidityDTO validity;

  @JsonProperty("date")
  @Schema(description = "Specific date for CUSTOM type", example = "2025-02-14")
  private String date;

  @JsonProperty("hours")
  @Schema(description = "List of hour ranges")
  private List<HourRangeDTO> hours;

  @JsonProperty("spotsPerLoop")
  @Schema(description = "How many times the ad plays per rotation", example = "1")
  private Long spotsPerLoop;

  @JsonProperty("spotsPerHour")
  @Schema(description = "Total plays per hour", example = "10")
  private Long spotsPerHour;

  /** Validity DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Schedule validity period")
  public static class ValidityDTO {
    @JsonProperty("startDate")
    @Schema(description = "Start date", example = "2025-02-01")
    private String startDate;

    @JsonProperty("endDate")
    @Schema(description = "End date", example = "2025-04-30")
    private String endDate;
  }

  /** Hour Range DTO */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Hour range")
  public static class HourRangeDTO {
    @JsonProperty("start")
    @Schema(description = "Start hour in HH:MM format", example = "06:00")
    private String start;

    @JsonProperty("end")
    @Schema(description = "End hour in HH:MM format", example = "23:00")
    private String end;
  }
}
