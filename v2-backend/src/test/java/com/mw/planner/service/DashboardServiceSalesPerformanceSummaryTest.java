package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CompanyLookupResponseDTO;
import com.mw.planner.dto.CustomFeesContext;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.dto.UserResponseDTO;
import com.mw.planner.dto.sales.SalesPerformanceCompanyItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceLocationItemDTO;
import com.mw.planner.dto.sales.SalesPerformanceTeamItemDTO;
import com.mw.planner.enums.SalesPerformanceShowBy;
import com.mw.planner.repository.CampaignRepository;
import com.mw.planner.repository.ScheduleRepository;
import com.mw.planner.repository.UserDashboardConfigRepository;
import com.mw.planner.service.dashboard.DashboardWidgetDefaultsProvider;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class DashboardServiceSalesPerformanceSummaryTest {

  @Mock private CampaignService campaignService;
  @Mock private UserDashboardConfigRepository userDashboardConfigRepository;
  @Mock private DashboardWidgetDefaultsProvider dashboardWidgetDefaultsProvider;
  @Mock private CampaignRepository campaignRepository;
  @Mock private UserService userService;
  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private InventoryService inventoryService;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private CustomFeeService customFeeService;
  @Mock private CompanyService companyService;
  @Mock private AgencyService agencyService;
  @Mock private TestModeService testModeService;

  @InjectMocks private DashboardService dashboardService;

  @Test
  void country_shouldAggregateAcrossDays_toTotalCostAndRevenue() {
    // We no longer support "overview". This test verifies that the remaining groupings still
    // aggregate schedule allocations correctly across the requested date range.
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-13");
    String companyId = "company-1";

    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(false).build());

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .companyId(companyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(start.minusDays(5))
            .endDate(end.plusDays(5))
            .build();
    campaign.setId("c1");

    when(campaignRepository.findCampaignsOverlappingRange(companyId, start, end, null))
        .thenReturn(List.of(campaign));

    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .inventoryId("inv1")
            .mediaOwnerId("mo1")
            .scheduleIds(List.of("s1"))
            .build();
    cis.setId("cis1");

    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c1")))
        .thenReturn(List.of(cis));

    Inventory inv = new Inventory();
    inv.setId("inv1");
    when(inventoryService.findAllByIds(List.of("inv1"))).thenReturn(List.of(inv));

    Schedule schedule =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(139346.0)
            .bookingMatrix(
                Map.of(
                    "2026-02-11", List.of(0, 1),
                    "2026-02-12", List.of(2),
                    "2026-02-13", List.of(3, 4, 5)))
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(schedule));

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("c1", CustomFeesContext.builder().build()));

    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cis), eq(inv), eq(campaign), eq(companyId), any(CustomFeesContext.class), anyMap()))
        .thenReturn(150000.0);

    Object result =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);

    assertThat(result).isInstanceOf(Page.class);
    @SuppressWarnings("unchecked")
    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);
    // No inventory location set in this setup => grouped under "Unknown".
    assertThat(page.getContent().get(0).getCountry()).isEqualTo("Unknown");
    // showBy=country includes classification counts; this inventory has no classification set =>
    // "Unknown".
    assertThat(page.getContent().get(0).getClassification())
        .containsExactlyInAnyOrderEntriesOf(Map.of("Unknown", 1L));
    // Base total is 139346. Proposed total is 150000 => scale converts cost to proposed total.
    assertThat(page.getContent().get(0).getCost()).isCloseTo(150000.0, Offset.offset(0.0001));
    assertThat(page.getContent().get(0).getRevenue()).isCloseTo(150000.0, Offset.offset(0.0001));
  }

  @Test
  void getSalesPerformanceSummary_withMissingDates_shouldThrowIllegalArgumentException() {
    // startDate/endDate are required for the sales summary endpoint (service-level validation).
    assertThatThrownBy(
            () ->
                dashboardService.getSalesPerformanceSummary(
                    null, LocalDate.now(), SalesPerformanceShowBy.COUNTRY, null, null, 0, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startDate and endDate are required");
  }

  @Test
  void country_whenNoCampaigns_shouldReturnEmptyPage() {
    // Non-overview showBy values return a Page<?>. When there is no data, return an empty page
    // (preserving requested paging semantics after clamping).
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-13");
    String companyId = "company-1";

    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(false).build());
    when(campaignRepository.findCampaignsOverlappingRange(companyId, start, end, null))
        .thenReturn(Collections.emptyList());

    Object result =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, -5, 0);

    assertThat(result).isInstanceOf(Page.class);
    @SuppressWarnings("unchecked")
    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).isEmpty();
    // Page is clamped to non-negative; size is clamped to at least 1.
    assertThat(page.getNumber()).isEqualTo(0);
    assertThat(page.getSize()).isEqualTo(1);
  }

  @Test
  void country_forSupplierSide_shouldUseBaseRevenueInsteadOfScaledCost() {
    // Business rule: for supplier-side users, "revenue" represents base price share (not the
    // scaled/proposed cost share). Cost is still returned as scaled cost.
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-13");
    String companyId = "company-1";

    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(true).build());

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .companyId(companyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(start.minusDays(5))
            .endDate(end.plusDays(5))
            .build();
    campaign.setId("c1");

    when(campaignRepository.findCampaignsOverlappingRange(companyId, start, end, null))
        .thenReturn(List.of(campaign));

    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .inventoryId("inv1")
            .mediaOwnerId(companyId)
            .scheduleIds(List.of("s1"))
            .build();

    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c1")))
        .thenReturn(List.of(cis));

    Inventory inv = new Inventory();
    inv.setId("inv1");
    when(inventoryService.findAllByIds(List.of("inv1"))).thenReturn(List.of(inv));

    Schedule schedule =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(139346.0)
            .bookingMatrix(
                Map.of(
                    "2026-02-11", List.of(0, 1),
                    "2026-02-12", List.of(2),
                    "2026-02-13", List.of(3, 4, 5)))
            .build();
    schedule.setId("s1");
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(schedule));

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("c1", CustomFeesContext.builder().build()));
    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cis), eq(inv), eq(campaign), eq(companyId), any(CustomFeesContext.class), anyMap()))
        .thenReturn(150000.0);

    Object result =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);

    assertThat(result).isInstanceOf(Page.class);
    @SuppressWarnings("unchecked")
    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);

    // Supplier-side: revenue uses base totals, cost uses scaled/proposed totals.
    assertThat(page.getContent().get(0).getCost()).isCloseTo(150000.0, Offset.offset(0.0001));
    assertThat(page.getContent().get(0).getRevenue()).isCloseTo(139346.0, Offset.offset(0.0001));
  }

  @Test
  void country_shouldAggregateByLocation_andSortByRevenueDesc() {
    // This verifies one of the non-overview groupings:
    // - group by inventory location (country)
    // - compute utilization/conversion/countCampaigns/cost/revenue
    // - sort results by revenue descending before paging
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-11");
    String companyId = "company-1";

    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(false).build());

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .companyId(companyId) // creator => all CIS entries are relevant
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(start.minusDays(5))
            .endDate(end.plusDays(5))
            .build();
    campaign.setId("c1");
    when(campaignRepository.findCampaignsOverlappingRange(companyId, start, end, null))
        .thenReturn(List.of(campaign));

    CampaignInventorySchedules cis1 =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .inventoryId("inv-india")
            .mediaOwnerId(companyId)
            .scheduleIds(List.of("s-india"))
            .build();
    CampaignInventorySchedules cis2 =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .inventoryId("inv-usa")
            .mediaOwnerId(companyId)
            .scheduleIds(List.of("s-usa"))
            .build();
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c1")))
        .thenReturn(List.of(cis1, cis2));

    Inventory invIndia = new Inventory();
    invIndia.setId("inv-india");
    invIndia.setLocation(Inventory.Location.builder().country("India").city("Mumbai").build());
    Inventory invUsa = new Inventory();
    invUsa.setId("inv-usa");
    invUsa.setLocation(Inventory.Location.builder().country("USA").city("NYC").build());
    when(inventoryService.findAllByIds(List.of("inv-india", "inv-usa")))
        .thenReturn(List.of(invIndia, invUsa));

    // Both schedules are fully within the requested range; we use a single day with 2 hours so
    // totalHoursAll=2 and share=1 for that day.
    Schedule sIndia =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(100.0)
            .spotsPerHour(10L)
            .impressions(1000L)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    sIndia.setId("s-india");
    Schedule sUsa =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(300.0)
            .spotsPerHour(10L)
            .impressions(1000L)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    sUsa.setId("s-usa");

    when(scheduleRepository.findAllById(List.of("s-india", "s-usa")))
        .thenReturn(List.of(sIndia, sUsa));
    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("c1", CustomFeesContext.builder().build()));

    // Proposed price per CIS is set equal to base price => scale=1 (keeps arithmetic simple).
    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cis1),
            eq(invIndia),
            eq(campaign),
            eq(companyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(100.0);
    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cis2),
            eq(invUsa),
            eq(campaign),
            eq(companyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(300.0);

    Object result =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);

    assertThat(result).isInstanceOf(Page.class);
    @SuppressWarnings("unchecked")
    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;

    assertThat(page.getContent()).hasSize(2);
    // USA has higher basePrice => higher revenue, so it should appear first.
    assertThat(page.getContent().get(0).getCountry()).isEqualTo("USA");
    assertThat(page.getContent().get(0).getRevenue())
        .isGreaterThan(page.getContent().get(1).getRevenue());
  }

  @Test
  void country_shouldIncludeClassificationCountsInResponse() {
    // When showBy=country, each country row includes a classification map: count per inventory
    // classification (e.g. CLASSIC_NETWORK: 2, Digital: 1, Transit: 2).
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-11");
    String companyId = "company-1";

    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(false).build());

    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .companyId(companyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(start.minusDays(5))
            .endDate(end.plusDays(5))
            .build();
    campaign.setId("c1");
    when(campaignRepository.findCampaignsOverlappingRange(companyId, start, end, null))
        .thenReturn(List.of(campaign));

    // 5 inventories in the same country: 2 CLASSIC_NETWORK, 1 Digital, 2 Transit
    List<CampaignInventorySchedules> cisList =
        List.of(
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .inventoryId("inv1")
                .mediaOwnerId(companyId)
                .scheduleIds(List.of("s1"))
                .build(),
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .inventoryId("inv2")
                .mediaOwnerId(companyId)
                .scheduleIds(List.of("s2"))
                .build(),
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .inventoryId("inv3")
                .mediaOwnerId(companyId)
                .scheduleIds(List.of("s3"))
                .build(),
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .inventoryId("inv4")
                .mediaOwnerId(companyId)
                .scheduleIds(List.of("s4"))
                .build(),
            CampaignInventorySchedules.builder()
                .campaignId("c1")
                .inventoryId("inv5")
                .mediaOwnerId(companyId)
                .scheduleIds(List.of("s5"))
                .build());
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c1"))).thenReturn(cisList);

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setLocation(Inventory.Location.builder().country("Japan").city("Tokyo").build());
    inv1.setClassification("CLASSIC_NETWORK");
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setLocation(Inventory.Location.builder().country("Japan").city("Osaka").build());
    inv2.setClassification("CLASSIC_NETWORK");
    Inventory inv3 = new Inventory();
    inv3.setId("inv3");
    inv3.setLocation(Inventory.Location.builder().country("Japan").city("Nagoya").build());
    inv3.setClassification("Digital");
    Inventory inv4 = new Inventory();
    inv4.setId("inv4");
    inv4.setLocation(Inventory.Location.builder().country("Japan").city("Fukuoka").build());
    inv4.setClassification("Transit");
    Inventory inv5 = new Inventory();
    inv5.setId("inv5");
    inv5.setLocation(Inventory.Location.builder().country("Japan").city("Sapporo").build());
    inv5.setClassification("Transit");

    when(inventoryService.findAllByIds(List.of("inv1", "inv2", "inv3", "inv4", "inv5")))
        .thenReturn(List.of(inv1, inv2, inv3, inv4, inv5));

    Schedule baseSchedule =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(100.0)
            .bookingMatrix(Map.of("2026-02-11", List.of(0)))
            .build();
    List<Schedule> schedules =
        List.of(
            copySchedule(baseSchedule, "s1"),
            copySchedule(baseSchedule, "s2"),
            copySchedule(baseSchedule, "s3"),
            copySchedule(baseSchedule, "s4"),
            copySchedule(baseSchedule, "s5"));
    when(scheduleRepository.findAllById(List.of("s1", "s2", "s3", "s4", "s5")))
        .thenReturn(schedules);

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("c1", CustomFeesContext.builder().build()));
    for (int i = 0; i < cisList.size(); i++) {
      CampaignInventorySchedules cis = cisList.get(i);
      Inventory inv = List.of(inv1, inv2, inv3, inv4, inv5).get(i);
      when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
              eq(cis),
              eq(inv),
              eq(campaign),
              eq(companyId),
              any(CustomFeesContext.class),
              anyMap()))
          .thenReturn(100.0);
    }

    Object result =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);

    assertThat(result).isInstanceOf(Page.class);
    @SuppressWarnings("unchecked")
    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);
    SalesPerformanceLocationItemDTO item = page.getContent().get(0);
    assertThat(item.getCountry()).isEqualTo("Japan");
    assertThat(item.getInventories()).isEqualTo(5);
    assertThat(item.getClassification())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("CLASSIC_NETWORK", 2L, "Digital", 1L, "Transit", 2L));
  }

  private static Schedule copySchedule(Schedule source, String id) {
    Schedule s =
        Schedule.builder()
            .startDate(source.getStartDate())
            .endDate(source.getEndDate())
            .basePrice(source.getBasePrice())
            .bookingMatrix(source.getBookingMatrix())
            .build();
    s.setId(id);
    return s;
  }

  @Test
  void advertiser_agency_and_team_shouldReturnPagedGroupSummaries() {
    // This is a compact "smoke test" that exercises the remaining showBy branches:
    // advertiser, agency and team. We intentionally keep the math simple (share=1) so the test
    // focuses on grouping, naming, paging, and sorting.
    LocalDate start = LocalDate.parse("2026-02-11");
    LocalDate end = LocalDate.parse("2026-02-11");
    String mediaOwnerCompanyId = "mo-1";

    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder().companyId(mediaOwnerCompanyId).isSupplierSide(false).build());

    Campaign advCampaign =
        Campaign.builder()
            .name("Adv Campaign")
            .companyId("adv-1") // used as advertiser grouping key
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(start.minusDays(1))
            .endDate(end.plusDays(1))
            .build();
    advCampaign.setId("c-adv");

    Campaign agencyCampaign =
        Campaign.builder()
            .name("Agency Campaign")
            .companyId("buyer-1")
            .clientType(Campaign.ClientType.AGENCY)
            .agency(Campaign.CampaignAgency.builder().id("agency-1").build())
            .userId("u2")
            .startDate(start.minusDays(1))
            .endDate(end.plusDays(1))
            .build();
    agencyCampaign.setId("c-agency");

    when(campaignRepository.findCampaignsOverlappingRange(mediaOwnerCompanyId, start, end, null))
        .thenReturn(List.of(advCampaign, agencyCampaign));

    CampaignInventorySchedules cisAdv =
        CampaignInventorySchedules.builder()
            .campaignId("c-adv")
            .inventoryId("inv1")
            .mediaOwnerId(mediaOwnerCompanyId)
            .scheduleIds(List.of("s1"))
            .build();
    CampaignInventorySchedules cisAgency =
        CampaignInventorySchedules.builder()
            .campaignId("c-agency")
            .inventoryId("inv2")
            .mediaOwnerId(mediaOwnerCompanyId)
            .scheduleIds(List.of("s2"))
            .build();
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c-adv", "c-agency")))
        .thenReturn(List.of(cisAdv, cisAgency));

    Inventory inv1 = new Inventory();
    inv1.setId("inv1");
    inv1.setLocation(Inventory.Location.builder().country("India").city("Mumbai").build());
    Inventory inv2 = new Inventory();
    inv2.setId("inv2");
    inv2.setLocation(Inventory.Location.builder().country("USA").city("NYC").build());
    when(inventoryService.findAllByIds(List.of("inv1", "inv2"))).thenReturn(List.of(inv1, inv2));

    // share=1 for each campaign because each schedule has only one day with all of its hours.
    Schedule s1 =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(100.0)
            .adPlays(10L)
            .impressions(1000L)
            .plannedSot(1.0)
            .totalSot(10.0)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    s1.setId("s1");
    Schedule s2 =
        Schedule.builder()
            .startDate(start)
            .endDate(end)
            .basePrice(200.0)
            .adPlays(10L)
            .impressions(1000L)
            .plannedSot(1.0)
            .totalSot(10.0)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    s2.setId("s2");
    when(scheduleRepository.findAllById(List.of("s1", "s2"))).thenReturn(List.of(s1, s2));

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(
            Map.of(
                "c-adv", CustomFeesContext.builder().build(),
                "c-agency", CustomFeesContext.builder().build()));

    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cisAdv),
            eq(inv1),
            eq(advCampaign),
            eq(mediaOwnerCompanyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(100.0);
    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cisAgency),
            eq(inv2),
            eq(agencyCampaign),
            eq(mediaOwnerCompanyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(200.0);

    when(companyService.getCompanyLookupWithCompanyId("adv-1"))
        .thenReturn(CompanyLookupResponseDTO.builder().id("adv-1").name("Acme Advertiser").build());
    when(agencyService.getNameById("agency-1")).thenReturn("Best Agency");
    when(userService.getUserById("u1"))
        .thenReturn(
            UserResponseDTO.builder()
                .id("u1")
                .firstName("Jay")
                .lastName("R")
                .location("West")
                .build());
    when(userService.getUserById("u2"))
        .thenReturn(
            UserResponseDTO.builder()
                .id("u2")
                .firstName("Sam")
                .lastName("P")
                .countryId("IN")
                .build());

    Object advertiserResult =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.ADVERTISER, null, null, 0, 10);
    Object agencyResult =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.AGENCY, null, null, 0, 10);
    Object teamResult =
        dashboardService.getSalesPerformanceSummary(
            start, end, SalesPerformanceShowBy.TEAM, null, null, 0, 10);

    assertThat(advertiserResult).isInstanceOf(Page.class);
    assertThat(agencyResult).isInstanceOf(Page.class);
    assertThat(teamResult).isInstanceOf(Page.class);

    @SuppressWarnings("unchecked")
    Page<SalesPerformanceCompanyItemDTO> advertiserPage =
        (Page<SalesPerformanceCompanyItemDTO>) advertiserResult;
    assertThat(advertiserPage.getContent()).isNotEmpty();
    assertThat(advertiserPage.getContent().get(0).getName()).isNotBlank();

    @SuppressWarnings("unchecked")
    Page<SalesPerformanceCompanyItemDTO> agencyPage =
        (Page<SalesPerformanceCompanyItemDTO>) agencyResult;
    assertThat(agencyPage.getContent()).isNotEmpty();
    assertThat(agencyPage.getContent().get(0).getName()).isNotBlank();

    @SuppressWarnings("unchecked")
    Page<SalesPerformanceTeamItemDTO> teamPage = (Page<SalesPerformanceTeamItemDTO>) teamResult;
    assertThat(teamPage.getContent()).isNotEmpty();
    assertThat(teamPage.getContent().get(0).getName()).isNotBlank();
    assertThat(teamPage.getContent().get(0).getRegion()).isNotBlank();
  }

  @Test
  void advertiser_withClassicAndDigitalCampaigns_weightsCompanySovByPlannedSot() {
    // One advertiser with two campaigns rolling into one company row: an all-classic campaign
    // (SOV always 100%) and an all-digital campaign booking 1 of 4 slots per loop (SOV 25%).
    // Company SOV must be the plannedSot-weighted average of the two, not a flat average or a
    // reapplied time ratio: (100*100 + 25*300) / (100+300) = 43.75.
    LocalDate day = LocalDate.parse("2026-02-11");
    String mediaOwnerCompanyId = "mo-1";
    String advertiserCompanyId = "adv-1";

    when(userService.getIamUserContext())
        .thenReturn(
            IamUserContext.builder().companyId(mediaOwnerCompanyId).isSupplierSide(false).build());

    Campaign classicCampaign =
        Campaign.builder()
            .name("Classic Campaign")
            .companyId(advertiserCompanyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(day)
            .endDate(day)
            .build();
    classicCampaign.setId("c-classic");

    Campaign digitalCampaign =
        Campaign.builder()
            .name("Digital Campaign")
            .companyId(advertiserCompanyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(day)
            .endDate(day)
            .build();
    digitalCampaign.setId("c-digital");

    when(campaignRepository.findCampaignsOverlappingRange(mediaOwnerCompanyId, day, day, null))
        .thenReturn(List.of(classicCampaign, digitalCampaign));

    CampaignInventorySchedules cisClassic =
        CampaignInventorySchedules.builder()
            .campaignId("c-classic")
            .inventoryId("classic-inv")
            .mediaOwnerId(mediaOwnerCompanyId)
            .scheduleIds(List.of("sClassic"))
            .build();
    CampaignInventorySchedules cisDigital =
        CampaignInventorySchedules.builder()
            .campaignId("c-digital")
            .inventoryId("digital-inv")
            .mediaOwnerId(mediaOwnerCompanyId)
            .scheduleIds(List.of("sDigital"))
            .build();
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c-classic", "c-digital")))
        .thenReturn(List.of(cisClassic, cisDigital));

    Inventory classicInv = new Inventory();
    classicInv.setId("classic-inv");
    classicInv.setClassification("Classic");
    Inventory.DigitalFields digitalFields = new Inventory.DigitalFields();
    digitalFields.setSpotsPerLoop(4);
    Inventory digitalInv = new Inventory();
    digitalInv.setId("digital-inv");
    digitalInv.setClassification("Digital");
    digitalInv.setDigitalFields(digitalFields);
    when(inventoryService.findAllByIds(List.of("classic-inv", "digital-inv")))
        .thenReturn(List.of(classicInv, digitalInv));

    // Single day, all booked hours in range => share=1.0, so weight == raw plannedSot.
    Schedule sClassic =
        Schedule.builder()
            .startDate(day)
            .endDate(day)
            .basePrice(100.0)
            .plannedSot(100.0)
            .totalSot(100.0)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    sClassic.setId("sClassic");
    Schedule sDigital =
        Schedule.builder()
            .startDate(day)
            .endDate(day)
            .basePrice(200.0)
            .plannedSot(300.0)
            .totalSot(300.0)
            .spotsPerLoop(1L)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1)))
            .build();
    sDigital.setId("sDigital");
    when(scheduleRepository.findAllById(List.of("sClassic", "sDigital")))
        .thenReturn(List.of(sClassic, sDigital));

    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(
            Map.of(
                "c-classic", CustomFeesContext.builder().build(),
                "c-digital", CustomFeesContext.builder().build()));

    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cisClassic),
            eq(classicInv),
            eq(classicCampaign),
            eq(mediaOwnerCompanyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(100.0);
    when(campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
            eq(cisDigital),
            eq(digitalInv),
            eq(digitalCampaign),
            eq(mediaOwnerCompanyId),
            any(CustomFeesContext.class),
            anyMap()))
        .thenReturn(200.0);

    when(companyService.getCompanyLookupWithCompanyId(advertiserCompanyId))
        .thenReturn(
            CompanyLookupResponseDTO.builder().id(advertiserCompanyId).name("Acme").build());

    Object result =
        dashboardService.getSalesPerformanceSummary(
            day, day, SalesPerformanceShowBy.ADVERTISER, null, null, 0, 10);

    @SuppressWarnings("unchecked")
    Page<SalesPerformanceCompanyItemDTO> page = (Page<SalesPerformanceCompanyItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getSov()).isEqualTo(43.75);
  }

  // ========== §2C additional branches: CITY, resolve-catch, scale-skip ==========

  private static final LocalDate SP_START = LocalDate.parse("2026-02-11");
  private static final LocalDate SP_END = LocalDate.parse("2026-02-13");

  private Campaign salesCampaign(String companyId) {
    Campaign campaign =
        Campaign.builder()
            .name("SP Campaign")
            .companyId(companyId)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .userId("u1")
            .startDate(SP_START.minusDays(5))
            .endDate(SP_END.plusDays(5))
            .build();
    campaign.setId("c1");
    return campaign;
  }

  private void stubSalesCommon(
      Campaign campaign, Inventory inv, Schedule schedule, String companyId, Double proposedPrice) {
    when(userService.getIamUserContext())
        .thenReturn(IamUserContext.builder().companyId(companyId).isSupplierSide(false).build());
    when(campaignRepository.findCampaignsOverlappingRange(companyId, SP_START, SP_END, null))
        .thenReturn(List.of(campaign));
    CampaignInventorySchedules cis =
        CampaignInventorySchedules.builder()
            .campaignId("c1")
            .inventoryId("inv1")
            .mediaOwnerId("mo1")
            .scheduleIds(List.of("s1"))
            .build();
    cis.setId("cis1");
    when(campaignInventorySchedulesService.findByCampaignIds(List.of("c1")))
        .thenReturn(List.of(cis));
    when(inventoryService.findAllByIds(List.of("inv1"))).thenReturn(List.of(inv));
    when(scheduleRepository.findAllById(List.of("s1"))).thenReturn(List.of(schedule));
    when(customFeeService.getActiveCustomFeesContextForCampaigns(anyList()))
        .thenReturn(Map.of("c1", CustomFeesContext.builder().build()));
    lenient()
        .when(
            campaignInventorySchedulesService.calculateCampaignInventorySchedulesProposedPrice(
                any(), any(), any(), any(), any(), anyMap()))
        .thenReturn(proposedPrice);
  }

  private Schedule salesSchedule(double basePrice) {
    Schedule schedule =
        Schedule.builder()
            .startDate(SP_START)
            .endDate(SP_END)
            .basePrice(basePrice)
            .bookingMatrix(Map.of("2026-02-11", List.of(0, 1), "2026-02-12", List.of(2)))
            .build();
    schedule.setId("s1");
    return schedule;
  }

  @Test
  @SuppressWarnings("unchecked")
  void city_shouldSplitCompositeKeyIntoCityAndCountry() {
    Campaign campaign = salesCampaign("company-1");
    Inventory inv = new Inventory();
    inv.setId("inv1");
    Inventory.Location location = new Inventory.Location();
    location.setCity("Downtown");
    location.setCountry("SG");
    inv.setLocation(location);
    stubSalesCommon(campaign, inv, salesSchedule(100000.0), "company-1", 120000.0);

    Object result =
        dashboardService.getSalesPerformanceSummary(
            SP_START, SP_END, SalesPerformanceShowBy.CITY, null, null, 0, 10);

    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getCity()).isEqualTo("Downtown");
    assertThat(page.getContent().get(0).getCountry()).isEqualTo("SG");
  }

  @Test
  @SuppressWarnings("unchecked")
  void advertiser_whenCompanyLookupThrows_resolvesNameToUnknown() {
    Campaign campaign = salesCampaign("company-1");
    Inventory inv = new Inventory();
    inv.setId("inv1");
    stubSalesCommon(campaign, inv, salesSchedule(100000.0), "company-1", 120000.0);
    when(companyService.getCompanyLookupWithCompanyId("company-1"))
        .thenThrow(new RuntimeException("company service down"));

    Object result =
        dashboardService.getSalesPerformanceSummary(
            SP_START, SP_END, SalesPerformanceShowBy.ADVERTISER, null, null, 0, 10);

    Page<SalesPerformanceCompanyItemDTO> page = (Page<SalesPerformanceCompanyItemDTO>) result;
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getName()).isEqualTo("Unknown");
  }

  @Test
  @SuppressWarnings("unchecked")
  void country_whenBaseTotalZero_scaleIsZeroAndCampaignExcluded() {
    Campaign campaign = salesCampaign("company-1");
    Inventory inv = new Inventory();
    inv.setId("inv1");
    // basePrice 0 -> baseTotal 0 -> scale 0 -> campaign excluded from aggregation
    stubSalesCommon(campaign, inv, salesSchedule(0.0), "company-1", 120000.0);

    Object result =
        dashboardService.getSalesPerformanceSummary(
            SP_START, SP_END, SalesPerformanceShowBy.COUNTRY, null, null, 0, 10);

    Page<SalesPerformanceLocationItemDTO> page = (Page<SalesPerformanceLocationItemDTO>) result;
    assertThat(page.getContent()).isEmpty();
  }
}
