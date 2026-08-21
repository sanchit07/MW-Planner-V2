package com.mw.planner.domain;

import com.mw.planner.enums.DashboardWidgetKey;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_dashboard_config")
@CompoundIndex(name = "ux_user_company", def = "{'userId': 1, 'companyId': 1}", unique = true)
public class UserDashboardConfig extends BaseEntity<String> {

  private String userId;
  private String companyId;

  /** List of widget visibility configuration items. */
  private List<DashboardWidgetConfig> widgets;

  /** Embedded document representing a single widget visibility config. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DashboardWidgetConfig {
    private DashboardWidgetKey key;
    private Boolean isEnable;
  }
}
