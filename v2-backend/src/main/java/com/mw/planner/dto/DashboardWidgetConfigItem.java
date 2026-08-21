package com.mw.planner.dto;

import com.mw.planner.enums.DashboardWidgetKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single dashboard widget visibility configuration")
public class DashboardWidgetConfigItem {

  @NotNull
  @Schema(
      description = "Widget key",
      example = "campaign-overview",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private DashboardWidgetKey key;

  @NotNull
  @Schema(
      description = "Whether widget should be shown",
      example = "true",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean isEnable;
}
