package com.mw.planner.service.dashboard;

import com.mw.planner.dto.CompanyDto;
import com.mw.planner.dto.DashboardWidgetConfigItem;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.DashboardWidgetKey;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DashboardWidgetDefaultsProvider {

  private static final List<DashboardWidgetConfigItem> MEDIA_OWNER_DEFAULTS =
      List.of(
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.SALES_OVERVIEW)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.SALES_PERFORMANCE_SUMMARY)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.SALES_PIPELINE_FUNNEL)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.REVENUE_DISTRIBUTION)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.INVENTORY_OVERVIEW)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.UTILIZATION_BREAKDOWN)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.CREATIVE_STATUS)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.REGIONAL_INVENTORY_SNAPSHOT)
              .isEnable(true)
              .build());

  private static final List<DashboardWidgetConfigItem> AGENCY_DEFAULTS =
      List.of(
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.CAMPAIGN_OVERVIEW)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.CAMPAIGN_PERFORMANCE)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.BUDGET_OVERVIEW)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.INVENTORY_OVERVIEW)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.UTILIZATION_BREAKDOWN)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.BUDGET_PERFORMANCE_SUMMARY)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.AUDIENCE_REACH_PERFORMANCE)
              .isEnable(true)
              .build(),
          DashboardWidgetConfigItem.builder()
              .key(DashboardWidgetKey.CREATIVE_STATUS)
              .isEnable(true)
              .build());

  public List<DashboardWidgetConfigItem> defaultsFor(IamUserContext userContext) {
    return defaultsFor(resolveBusinessType(userContext));
  }

  /**
   * Returns default widgets based on the company's business type.
   *
   * <p>Logic mapping (kept intentionally simple to preserve existing behavior):
   *
   * <ul>
   *   <li>Supplier-side business types → Media Owner defaults
   *   <li>Everything else (including null/ALL) → Agency defaults
   * </ul>
   */
  public List<DashboardWidgetConfigItem> defaultsFor(CompanyDto.BusinessType businessType) {
    // Lists are immutable, safe to share (caller shouldn't mutate).
    // return isSupplierSide(businessType) ? MEDIA_OWNER_DEFAULTS : AGENCY_DEFAULTS;
    return AGENCY_DEFAULTS; // Everything else (including null/ALL) → Agency defaults
  }

  private static CompanyDto.BusinessType resolveBusinessType(IamUserContext ctx) {
    // Preserve historical behavior: null context behaves like agency.
    if (ctx == null) {
      return CompanyDto.BusinessType.MEDIA_AGENCY;
    }
    return Boolean.TRUE.equals(ctx.getIsSupplierSide())
        ? CompanyDto.BusinessType.MEDIA_OWNER
        : CompanyDto.BusinessType.MEDIA_AGENCY;
  }

  private static boolean isSupplierSide(CompanyDto.BusinessType businessType) {
    if (businessType == null) {
      return false;
    }
    return switch (businessType) {
      case MEDIA_OWNER, MEDIA_OPERATOR -> true;
      case MEDIA_BUYER, MEDIA_AGENCY, ALL -> false;
    };
  }
}
