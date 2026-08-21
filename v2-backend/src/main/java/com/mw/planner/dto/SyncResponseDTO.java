package com.mw.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Simple sync response DTO")
public class SyncResponseDTO {

  @Schema(description = "Success message", example = "Sync started successfully")
  private String message;

  @Schema(description = "Sync type", example = "STATE")
  private String type;
}
