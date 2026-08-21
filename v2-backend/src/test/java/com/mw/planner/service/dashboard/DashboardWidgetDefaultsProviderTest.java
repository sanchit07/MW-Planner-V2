package com.mw.planner.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.dto.CompanyDto;
import com.mw.planner.dto.DashboardWidgetConfigItem;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.enums.DashboardWidgetKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardWidgetDefaultsProviderTest {

  @InjectMocks private DashboardWidgetDefaultsProvider provider;

  private List<DashboardWidgetKey> keysOf(List<DashboardWidgetConfigItem> items) {
    return items.stream().map(DashboardWidgetConfigItem::getKey).toList();
  }

  // --- defaultsFor(BusinessType) ---

  // Media-owner/media-operator defaults are temporarily disabled in
  // DashboardWidgetDefaultsProvider.defaultsFor(BusinessType) — every business type currently
  // falls back to Agency defaults, so this covers all enum values rather than just buyer-side.
  @ParameterizedTest
  @EnumSource(CompanyDto.BusinessType.class)
  void defaultsForBusinessType_AlwaysReturnsAgencyDefaults(CompanyDto.BusinessType businessType) {
    List<DashboardWidgetConfigItem> result = provider.defaultsFor(businessType);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
    assertThat(keysOf(result)).doesNotContain(DashboardWidgetKey.SALES_OVERVIEW);
  }

  @Test
  void defaultsForBusinessType_WhenNull_ReturnsAgencyDefaults() {
    List<DashboardWidgetConfigItem> result = provider.defaultsFor((CompanyDto.BusinessType) null);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
    assertThat(keysOf(result)).doesNotContain(DashboardWidgetKey.SALES_OVERVIEW);
  }

  // --- defaultsFor(IamUserContext) ---

  @Test
  void defaultsForUserContext_WhenNull_ReturnsAgencyDefaults() {
    List<DashboardWidgetConfigItem> result = provider.defaultsFor((IamUserContext) null);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
  }

  // Supplier-side media-owner defaults are temporarily disabled — see comment above.
  @Test
  void defaultsForUserContext_WhenSupplierSideTrue_ReturnsAgencyDefaults() {
    IamUserContext ctx = IamUserContext.builder().isSupplierSide(Boolean.TRUE).build();

    List<DashboardWidgetConfigItem> result = provider.defaultsFor(ctx);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
    assertThat(keysOf(result)).doesNotContain(DashboardWidgetKey.SALES_OVERVIEW);
  }

  @Test
  void defaultsForUserContext_WhenSupplierSideFalse_ReturnsAgencyDefaults() {
    IamUserContext ctx = IamUserContext.builder().isSupplierSide(Boolean.FALSE).build();

    List<DashboardWidgetConfigItem> result = provider.defaultsFor(ctx);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
  }

  @Test
  void defaultsForUserContext_WhenSupplierSideNull_ReturnsAgencyDefaults() {
    IamUserContext ctx = IamUserContext.builder().isSupplierSide(null).build();

    List<DashboardWidgetConfigItem> result = provider.defaultsFor(ctx);

    assertThat(keysOf(result)).contains(DashboardWidgetKey.CAMPAIGN_OVERVIEW);
  }
}
