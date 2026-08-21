package com.mw.planner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for district information")
public class DistrictResponseDTO {

  @Schema(description = "Unique district identifier", example = "district_123456")
  private String id;

  @Schema(description = "District name", example = "Kabupaten Bandung")
  private String name;

  @Schema(description = "State ID this district belongs to", example = "state_123")
  private String stateId;

  @Schema(description = "District type", example = "Kabupaten")
  private String type;

  @Schema(description = "District latitude coordinate", example = "-6.9175")
  private Double latitude;

  @Schema(description = "District longitude coordinate", example = "107.6191")
  private Double longitude;

  @Schema(description = "Map zoom level", example = "12")
  private Integer zoom;

  @Schema(description = "District population", example = "2500000")
  private Long population;

  @Schema(description = "ISO code", example = "ID")
  private String iso;

  @Schema(description = "Locale", example = "en")
  private String locale;

  @JsonProperty("createdAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Creation timestamp", example = "2024-01-15 10:30:00")
  private LocalDateTime createdAt;

  @JsonProperty("updatedAt")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "Last update timestamp", example = "2024-01-15 14:45:00")
  private LocalDateTime updatedAt;
}
