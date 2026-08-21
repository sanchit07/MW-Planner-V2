package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mw.planner.dto.sales.SalesPerformanceCompanyItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceLocationItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceTeamItemDTO;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class SalesPerformanceSummarySortTest {

  @Test
  void locationItems_sortByRevenueDesc_defaultBehavior() {
    SalesPerformanceLocationItemDTO low =
        SalesPerformanceLocationItemDTO.builder().country("IN").revenue(100.0).build();
    SalesPerformanceLocationItemDTO high =
        SalesPerformanceLocationItemDTO.builder().country("US").revenue(200.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator(null, null);
    assertThat(cmp.compare(high, low)).isLessThan(0);
    assertThat(cmp.compare(low, high)).isGreaterThan(0);
  }

  @Test
  void locationItems_sortByCountryAsc() {
    SalesPerformanceLocationItemDTO usa =
        SalesPerformanceLocationItemDTO.builder().country("USA").revenue(100.0).build();
    SalesPerformanceLocationItemDTO india =
        SalesPerformanceLocationItemDTO.builder().country("India").revenue(200.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("country", "asc");
    assertThat(cmp.compare(india, usa)).isLessThan(0);
    assertThat(cmp.compare(usa, india)).isGreaterThan(0);
  }

  @Test
  void locationItems_sortByCostDesc() {
    SalesPerformanceLocationItemDTO low =
        SalesPerformanceLocationItemDTO.builder().country("IN").cost(50.0).build();
    SalesPerformanceLocationItemDTO high =
        SalesPerformanceLocationItemDTO.builder().country("US").cost(150.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("cost", "desc");
    assertThat(cmp.compare(high, low)).isLessThan(0);
  }

  @Test
  void companyItems_sortByName_asc() {
    SalesPerformanceCompanyItemDTO a =
        SalesPerformanceCompanyItemDTO.builder().name("Alpha").revenue(100.0).build();
    SalesPerformanceCompanyItemDTO z =
        SalesPerformanceCompanyItemDTO.builder().name("Zeta").revenue(200.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("name", "asc");
    assertThat(cmp.compare(a, z)).isLessThan(0);
  }

  @Test
  void companyItems_advertiserName_normalizedToName() {
    SalesPerformanceCompanyItemDTO a =
        SalesPerformanceCompanyItemDTO.builder().name("Alpha").revenue(100.0).build();
    SalesPerformanceCompanyItemDTO z =
        SalesPerformanceCompanyItemDTO.builder().name("Zeta").revenue(200.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("advertiser name", "asc");
    assertThat(cmp.compare(a, z)).isLessThan(0);
  }

  @Test
  void companyItems_sortByShareDesc() {
    SalesPerformanceCompanyItemDTO low =
        SalesPerformanceCompanyItemDTO.builder().name("A").share(10.0).revenue(100.0).build();
    SalesPerformanceCompanyItemDTO high =
        SalesPerformanceCompanyItemDTO.builder().name("B").share(50.0).revenue(200.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("share", "desc");
    assertThat(cmp.compare(high, low)).isLessThan(0);
  }

  @Test
  void teamItems_sortByConversionDesc() {
    SalesPerformanceTeamItemDTO low =
        SalesPerformanceTeamItemDTO.builder().name("A").conversion(0.1).revenue(100.0).build();
    SalesPerformanceTeamItemDTO high =
        SalesPerformanceTeamItemDTO.builder().name("B").conversion(0.9).revenue(50.0).build();

    Comparator<Object> cmp = SalesPerformanceSummarySort.getComparator("conversion", "desc");
    assertThat(cmp.compare(high, low)).isLessThan(0);
  }

  @Test
  void defaultSortFieldAndDir() {
    assertThat(SalesPerformanceSummarySort.defaultSortField()).isEqualTo("revenue");
    assertThat(SalesPerformanceSummarySort.defaultSortDir()).isEqualTo("desc");
  }
}
