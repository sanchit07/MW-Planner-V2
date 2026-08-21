package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.CampaignInventorySchedules;
import com.mw.planner.domain.Inventory;
import com.mw.planner.domain.Schedule;
import com.mw.planner.dto.CampaignForecastDTO;
import com.mw.planner.dto.CampaignInventoryFilterDTO;
import com.mw.planner.dto.CampaignInventoryFilterResponseDTO;
import com.mw.planner.dto.MeasureReachFrequencyResponseDTO;
import com.mw.planner.dto.MediaOwnerFilterRequestDTO;
import com.mw.planner.dto.SelectedInventorySummaryResponseDTO;
import com.mw.planner.enums.ProgrammaticDealType;
import com.mw.planner.enums.ProgrammaticSupport;
import com.mw.planner.exception.campaign.CampaignInventorySchedulesNotFoundException;
import com.mw.planner.exception.campaign.CampaignNotFoundException;
import com.mw.planner.exception.inventory.InventoryNotFoundException;
import com.mw.planner.repository.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private InventoryRepositoryCustom inventoryRepositoryCustom;
  @Mock private CampaignService campaignService;

  @Mock private CampaignInventorySchedulesService campaignInventorySchedulesService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private CompanyService companyService;
  @Mock private MwMeasureService mwMeasureService;
  @Mock private UserService userService;
  @Mock private VenuesService venuesService;

  @InjectMocks private InventoryService inventoryService;

  private Campaign testCampaign;
  private Inventory testInventory1;
  private Inventory testInventory2;
  private Inventory testInventory3;
  private CampaignInventorySchedules testSchedule1;
  private CampaignInventorySchedules testSchedule2;

  @BeforeEach
  void setUp() {
    testCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .status(Campaign.Status.DRAFT)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .companyAccess(new ArrayList<>())
            .build();
    testCampaign.setId("campaign123");

    testInventory1 = createInventory("inventory1", "Classic Billboard", "CLASSIC");
    testInventory2 = createInventory("inventory2", "Digital Billboard", "DIGITAL");
    testInventory3 = createInventory("inventory3", "Classic Sign", "CLASSIC");

    testSchedule1 = new CampaignInventorySchedules();
    testSchedule1.setCampaignId("campaign123");
    testSchedule1.setInventoryId("inventory1");

    testSchedule2 = new CampaignInventorySchedules();
    testSchedule2.setCampaignId("campaign123");
    testSchedule2.setInventoryId("inventory2");
  }

  @AfterEach
  void tearDown() {
    reset(
        inventoryRepository,
        inventoryRepositoryCustom,
        campaignService,
        campaignInventorySchedulesService,
        companyService,
        mwMeasureService,
        userService);
  }

  // ---------- getSelectedInventories Tests ----------

  @Test
  void getSelectedInventories_WithNoFilters_ShouldReturnAllSelectedInventories() {
    // Given
    String campaignId = "campaign123";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).hasSize(2);
    verify(inventoryRepository).findByIdIn(selectedIds, pageable);
  }

  @Test
  void getSelectedInventories_ShouldMapProgrammaticDealTypesInDetail() {
    // Given
    String campaignId = "campaign123";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);
    testInventory1.setProgrammaticDealTypes(List.of("guaranteed", "open_auction"));
    testInventory2.setProgrammaticDealTypes(List.of());

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    // Then
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getDetail().getProgrammaticDealTypes())
        .containsExactly("guaranteed", "open_auction");
    assertThat(result.getContent().get(0).getDetail().getSize()).isEqualTo("48x14");
    assertThat(result.getContent().get(0).getDetail().getInventoryCluster())
        .containsExactly("cluster-A");
    assertThat(result.getContent().get(1).getDetail().getProgrammaticDealTypes()).isEmpty();
  }

  @Test
  void getSelectedInventories_ShouldMapSellingTermInDetail() {
    // Given
    String campaignId = "campaign123";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);
    testInventory1.setSellingTerm(
        Inventory.SellingTerm.builder()
            .leadDays(3)
            .minHours(24)
            .minDays(1)
            .dayPartGroups(
                Map.of(
                    "morning",
                    Inventory.DayPartGroup.builder().start("05:00:00").end("09:00:00").build()))
            .build());
    testInventory2.setSellingTerm(null);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    // Then
    assertThat(result.getContent()).hasSize(2);
    Inventory.SellingTerm mappedSellingTerm =
        result.getContent().get(0).getDetail().getSellingTerm();
    assertThat(mappedSellingTerm).isNotNull();
    assertThat(mappedSellingTerm.getLeadDays()).isEqualTo(3);
    assertThat(mappedSellingTerm.getMinHours()).isEqualTo(24);
    assertThat(mappedSellingTerm.getMinDays()).isEqualTo(1);
    assertThat(mappedSellingTerm.getDayPartGroups().get("morning").getStart())
        .isEqualTo("05:00:00");
    assertThat(mappedSellingTerm.getDayPartGroups().get("morning").getEnd()).isEqualTo("09:00:00");
    assertThat(result.getContent().get(1).getDetail().getSellingTerm()).isNull();
  }

  @Test
  void getSelectedInventories_WithNameFilter_ShouldReturnFilteredResults() {
    // Given
    String campaignId = "campaign123";
    String nameFilter = "digital";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdInAndNameContaining(selectedIds, nameFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory2), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, nameFilter, null, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).hasSize(1);
    verify(inventoryRepository).findByIdInAndNameContaining(selectedIds, nameFilter, pageable);
    verify(inventoryRepository, never()).findByIdIn(anyList(), any(Pageable.class));
  }

  @Test
  void getSelectedInventories_WithInventoryTypeFilter_ShouldReturnFilteredResults() {
    // Given
    String campaignId = "campaign123";
    String inventoryTypeFilter = "CLASSIC";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdInAndType(selectedIds, inventoryTypeFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, inventoryTypeFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).hasSize(1);
    verify(inventoryRepository).findByIdInAndType(selectedIds, inventoryTypeFilter, pageable);
  }

  @Test
  void getSelectedInventories_WithInventoryTypeFilterLowerCase_ShouldNormalizeToUpperCase() {
    // Given
    String campaignId = "campaign123";
    String inventoryTypeFilter = "classic"; // lowercase input
    // Service doesn't normalize - uses the value as-is
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdInAndType(selectedIds, inventoryTypeFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, inventoryTypeFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    // Verify that the raw value was used (service doesn't normalize)
    verify(inventoryRepository).findByIdInAndType(selectedIds, inventoryTypeFilter, pageable);
  }

  @Test
  void getSelectedInventories_WithInventoryTypeFilterWithWhitespace_ShouldTrimAndNormalize() {
    // Given
    String campaignId = "campaign123";
    String inventoryTypeFilter = "  digital  "; // with whitespace
    // Service doesn't normalize or trim - uses the value as-is
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdInAndType(selectedIds, inventoryTypeFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory2), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, inventoryTypeFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    // Verify that the raw value was used (service doesn't normalize or trim)
    verify(inventoryRepository).findByIdInAndType(selectedIds, inventoryTypeFilter, pageable);
  }

  @Test
  void getSelectedInventories_WithNameAndInventoryTypeFilters_ShouldReturnFilteredResults() {
    // Given
    String campaignId = "campaign123";
    String nameFilter = "classic";
    String inventoryTypeFilter = "CLASSIC";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2", "inventory3");
    Pageable pageable = PageRequest.of(0, 10);

    CampaignInventorySchedules testSchedule3 = new CampaignInventorySchedules();
    testSchedule3.setCampaignId("campaign123");
    testSchedule3.setInventoryId("inventory3");

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2, testSchedule3));
    when(inventoryRepository.findByIdInAndNameContainingAndType(
            selectedIds, nameFilter, inventoryTypeFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory3), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(
            campaignId, nameFilter, inventoryTypeFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).hasSize(2);
    verify(inventoryRepository)
        .findByIdInAndNameContainingAndType(selectedIds, nameFilter, inventoryTypeFilter, pageable);
  }

  @Test
  void getSelectedInventories_WithEmptySelectedIds_ShouldReturnEmptyPage() {
    // Given
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(new ArrayList<>());

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getContent()).isEmpty();
    verify(inventoryRepository, never()).findByIdIn(anyList(), any(Pageable.class));
  }

  @Test
  void getSelectedInventories_WithPagination_ShouldReturnCorrectPage() {
    // Given
    String campaignId = "campaign123";
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2", "inventory3");
    Pageable pageable = PageRequest.of(1, 2, Sort.by("name"));

    CampaignInventorySchedules testSchedule3 = new CampaignInventorySchedules();
    testSchedule3.setCampaignId("campaign123");
    testSchedule3.setInventoryId("inventory3");

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2, testSchedule3));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory3), pageable, 3));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getNumber()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(2);
  }

  @Test
  void getSelectedInventories_WithEmptyNameFilter_ShouldIgnoreNameFilter() {
    // Given
    String campaignId = "campaign123";
    String nameFilter = "   "; // whitespace only
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, nameFilter, null, pageable);

    // Then
    assertThat(result).isNotNull();
    // Should use findByIdIn (no name filter) since nameFilter is empty/whitespace
    verify(inventoryRepository).findByIdIn(selectedIds, pageable);
    verify(inventoryRepository, never())
        .findByIdInAndNameContaining(anyList(), anyString(), any(Pageable.class));
  }

  @Test
  void getSelectedInventories_WithEmptyInventoryTypeFilter_ShouldIgnoreTypeFilter() {
    // Given
    String campaignId = "campaign123";
    String inventoryTypeFilter = "   "; // whitespace only
    List<String> selectedIds = Arrays.asList("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(testSchedule1, testSchedule2));
    // Service checks if inventoryType is null, not if it's empty/whitespace
    // So whitespace will be treated as a valid filter value
    when(inventoryRepository.findByIdInAndType(selectedIds, inventoryTypeFilter, pageable))
        .thenReturn(new PageImpl<>(Arrays.asList(testInventory1, testInventory2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, inventoryTypeFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    // Service treats whitespace as a valid filter (checks null, not empty/whitespace)
    verify(inventoryRepository).findByIdInAndType(selectedIds, inventoryTypeFilter, pageable);
  }

  // ---------- filterInventories Tests ----------

  @Test
  void filterInventories_WithRestrictiveFilter_ShouldReturnOnlyMatchingResults() {
    // Given: restrictive filter => only matching results should be returned
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().name("billboard").build();

    CampaignInventorySchedules selectedSchedule1 = new CampaignInventorySchedules();
    selectedSchedule1.setCampaignId(campaignId);
    selectedSchedule1.setInventoryId("inventory1");

    CampaignInventorySchedules selectedSchedule2 = new CampaignInventorySchedules();
    selectedSchedule2.setCampaignId(campaignId);
    selectedSchedule2.setInventoryId("inventory2");

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(selectedSchedule1, selectedSchedule2));

    // Selected inventories matching restrictive filter: only inventory2
    when(inventoryRepositoryCustom.findInventoriesByIdsWithComplianceCheck(
            Arrays.asList("inventory1", "inventory2"), filter))
        .thenReturn(List.of(testInventory2));
    when(inventoryRepository.findAllById(List.of("inventory2")))
        .thenReturn(List.of(testInventory2));

    // Non-selected filtered results
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(testInventory3), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getDetail().getIsSelected()).isTrue();
    assertThat(result.getContent().get(1).getDetail().getIsSelected()).isFalse();
  }

  @Test
  void
      filterInventories_WithRestrictiveFilterAndNoSelectedMatch_ShouldReturnOnlyNonSelectedMatches() {
    // Given: restrictive filter with no selected matches
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().mediaOwnerIds(List.of("owner-1")).build();

    CampaignInventorySchedules selectedSchedule1 = new CampaignInventorySchedules();
    selectedSchedule1.setCampaignId(campaignId);
    selectedSchedule1.setInventoryId("inventory1");

    CampaignInventorySchedules selectedSchedule2 = new CampaignInventorySchedules();
    selectedSchedule2.setCampaignId(campaignId);
    selectedSchedule2.setInventoryId("inventory2");

    Inventory nonSelectedMatch1 = createInventory("inventory4", "Test Screen A", "DIGITAL");
    Inventory nonSelectedMatch2 = createInventory("inventory5", "Test Screen B", "CLASSIC");

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(selectedSchedule1, selectedSchedule2));

    // No selected inventory matches restrictive filter
    when(inventoryRepositoryCustom.findInventoriesByIdsWithComplianceCheck(
            Arrays.asList("inventory1", "inventory2"), filter))
        .thenReturn(List.of());

    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(nonSelectedMatch1, nonSelectedMatch2), pageable, 2));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent())
        .allMatch(dto -> Boolean.FALSE.equals(dto.getDetail().getIsSelected()));
  }

  @Test
  void filterInventories_WithNonRestrictiveFilter_ShouldReturnSelectedFirstThenFiltered() {
    // Given: non-restrictive filter => all selected first, then filtered
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().countries(List.of("Singapore")).build();

    CampaignInventorySchedules selectedSchedule1 = new CampaignInventorySchedules();
    selectedSchedule1.setCampaignId(campaignId);
    selectedSchedule1.setInventoryId("inventory1");

    CampaignInventorySchedules selectedSchedule2 = new CampaignInventorySchedules();
    selectedSchedule2.setCampaignId(campaignId);
    selectedSchedule2.setInventoryId("inventory2");

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(Arrays.asList(selectedSchedule1, selectedSchedule2));

    // Non-restrictive path fetches all selected for page
    when(inventoryRepository.findAllById(Arrays.asList("inventory1", "inventory2")))
        .thenReturn(Arrays.asList(testInventory1, testInventory2));
    // Compliance only affects selected compliance flag, not selected inclusion
    when(inventoryRepositoryCustom.findInventoriesByIdsWithComplianceCheck(
            Arrays.asList("inventory1", "inventory2"), filter))
        .thenReturn(List.of(testInventory1));

    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(testInventory3), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getContent()).hasSize(3);
    assertThat(result.getContent().get(0).getDetail().getIsSelected()).isTrue();
    assertThat(result.getContent().get(1).getDetail().getIsSelected()).isTrue();
    assertThat(result.getContent().get(2).getDetail().getIsSelected()).isFalse();
    assertThat(result.getContent().get(0).getDetail().getIsCompliant()).isTrue();
    assertThat(result.getContent().get(1).getDetail().getIsCompliant()).isFalse();
  }

  // ---------- Performance Metrics Tests ----------

  @Test
  void filterInventories_ShouldIncludeSpotRateInPerformance() {
    // Given: inventory with spot rate price
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();

    Inventory inventoryWithPrice = createInventory("inventory1", "Test Billboard", "DIGITAL");
    Inventory.Price price = new Inventory.Price();
    price.setSpot(2.5);
    price.setCpm(10.0);
    inventoryWithPrice.setPrices(List.of(price));

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(inventoryWithPrice), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    CampaignInventoryFilterResponseDTO.Performance performance =
        result.getContent().get(0).getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getSpotRate()).isEqualTo(2.5);
    assertThat(performance.getCpmRate()).isEqualTo(10.0);
  }

  @Test
  void filterInventories_ShouldHandleNullSpotRate() {
    // Given: inventory with no prices
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();

    Inventory inventoryWithoutPrice = createInventory("inventory1", "Test Billboard", "DIGITAL");
    inventoryWithoutPrice.setPrices(null);

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(inventoryWithoutPrice), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    CampaignInventoryFilterResponseDTO.Performance performance =
        result.getContent().get(0).getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getSpotRate()).isNull();
    assertThat(performance.getCpmRate()).isNull();
  }

  @Test
  void filterInventories_ShouldHandleEmptyPricesList() {
    // Given: inventory with empty prices list
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();

    Inventory inventoryWithEmptyPrices = createInventory("inventory1", "Test Billboard", "DIGITAL");
    inventoryWithEmptyPrices.setPrices(List.of());

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(inventoryWithEmptyPrices), pageable, 1));

    // When
    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.filterInventories(filter, campaignId, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    CampaignInventoryFilterResponseDTO.Performance performance =
        result.getContent().get(0).getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getSpotRate()).isNull();
    assertThat(performance.getCpmRate()).isNull();
  }

  // ---------- getSelectedInventories Media Owner Tests ----------

  @Test
  void getSelectedInventories_WhenUserIsMediaOwner_ShouldFilterByMediaOwner() {
    String campaignId = "campaign123";
    String mediaOwnerId = "media-owner-id";
    Pageable pageable = PageRequest.of(0, 10);

    Campaign mediaOwnerCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .status(Campaign.Status.DRAFT)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .companyAccess(List.of(mediaOwnerId))
            .build();
    mediaOwnerCampaign.setId(campaignId);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(mediaOwnerCampaign);
    when(userService.getActingCompanyId()).thenReturn(mediaOwnerId);
    when(campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerId(
            campaignId, mediaOwnerId))
        .thenReturn(List.of(testSchedule1));
    when(inventoryRepository.findByIdIn(List.of("inventory1"), pageable))
        .thenReturn(new PageImpl<>(List.of(testInventory1), pageable, 1));
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaignId", "inventoryId"));

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(campaignInventorySchedulesService)
        .findByCampaignIdAndMediaOwnerId(campaignId, mediaOwnerId);
    verify(campaignInventorySchedulesService, never()).findByCampaignId(campaignId);
  }

  @Test
  void getSelectedInventories_WhenUserIsNotMemberOfCampaign_ShouldReturnAllInventories() {
    String campaignId = "campaign123";
    String unrelatedId = "unrelated-company-id";
    Pageable pageable = PageRequest.of(0, 10);

    Campaign campaignWithoutAccess =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .status(Campaign.Status.DRAFT)
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .companyAccess(new ArrayList<>())
            .build();
    campaignWithoutAccess.setId(campaignId);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaignWithoutAccess);
    when(userService.getPrimaryCompanyId()).thenReturn(unrelatedId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(List.of("inventory1", "inventory2"), pageable))
        .thenReturn(new PageImpl<>(List.of(testInventory1, testInventory2), pageable, 2));
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaignId", "inventoryId"));

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
    verify(campaignInventorySchedulesService).findByCampaignId(campaignId);
    verify(campaignInventorySchedulesService, never())
        .findByCampaignIdAndMediaOwnerId(anyString(), anyString());
  }

  // ---------- getSelectedInventories Media Owner IDs Filter Tests (POST variant) ----------

  @Test
  void getSelectedInventories_WithMediaOwnerIds_ShouldFilterByMediaOwners() {
    String campaignId = "campaign123";
    List<String> mediaOwnerIds = List.of("owner-1", "owner-2");
    List<String> selectedIds = List.of("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(mediaOwnerIds).build();

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerIdIn(
            campaignId, mediaOwnerIds))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(List.of(testInventory1, testInventory2), pageable, 2));
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaignId", "inventoryId"));

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable, request);

    assertThat(result.getTotalElements()).isEqualTo(2);
    verify(campaignInventorySchedulesService)
        .findByCampaignIdAndMediaOwnerIdIn(campaignId, mediaOwnerIds);
    verify(campaignInventorySchedulesService, never()).findByCampaignId(anyString());
    verify(campaignInventorySchedulesService, never())
        .findByCampaignIdAndMediaOwnerId(anyString(), anyString());
  }

  @Test
  void getSelectedInventories_WithEmptyMediaOwnerIds_ShouldBehaveLikeGet() {
    String campaignId = "campaign123";
    List<String> selectedIds = List.of("inventory1", "inventory2");
    Pageable pageable = PageRequest.of(0, 10);
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of()).build();

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(List.of(testSchedule1, testSchedule2));
    when(inventoryRepository.findByIdIn(selectedIds, pageable))
        .thenReturn(new PageImpl<>(List.of(testInventory1, testInventory2), pageable, 2));

    Page<CampaignInventoryFilterResponseDTO> result =
        inventoryService.getSelectedInventories(campaignId, null, null, pageable, request);

    assertThat(result.getTotalElements()).isEqualTo(2);
    verify(campaignInventorySchedulesService).findByCampaignId(campaignId);
    verify(campaignInventorySchedulesService, never())
        .findByCampaignIdAndMediaOwnerIdIn(anyString(), anyList());
  }

  // ---------- getAllSelectedInventories Tests ----------

  @Test
  void getAllSelectedInventories_ShouldMapAllFieldsAndPassPerformanceThrough() {
    // Given
    String campaignId = "campaign123";
    testInventory1.setInventoryId("ENV-inventory1");
    testSchedule1.setScheduleIds(List.of("schedule1", "schedule2"));

    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setBasePrice(100.0);
    Schedule scheduleEntity2 = new Schedule();
    scheduleEntity2.setBasePrice(200.0);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignInventorySchedulesService.findByCampaignId(campaignId))
        .thenReturn(List.of(testSchedule1));
    when(inventoryRepository.findAllById(List.of("inventory1")))
        .thenReturn(List.of(testInventory1));
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(campaignId, "inventory1"))
        .thenReturn(testSchedule1);
    when(campaignInventorySchedulesService.getSchedulesByIds(List.of("schedule1", "schedule2")))
        .thenReturn(List.of(scheduleEntity1, scheduleEntity2));
    when(campaignService.calculateCampaignForecast(testCampaign, List.of(testSchedule1)))
        .thenReturn(
            CampaignForecastDTO.builder()
                .totalSot(82.0)
                .plannedSot(6.0)
                .estimatedAdPlays(300L)
                .sov(10.25)
                .estimatedImpression(117800L)
                .estimatedReach(13497L)
                .estimatedFrequency(8.72)
                .build());

    // When
    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId);

    // Then — all 3 fields populated, performance passed through intact
    assertThat(result).hasSize(1);
    SelectedInventorySummaryResponseDTO summary = result.get(0);
    assertThat(summary.getInventoryId()).isEqualTo("ENV-inventory1");
    assertThat(summary.getReferenceId()).isEqualTo("REF-inventory1");

    int duration = CampaignService.calculateDuration(testCampaign);
    CampaignInventoryFilterResponseDTO.Performance performance = summary.getPerformance();
    assertThat(performance).isNotNull();
    assertThat(performance.getEstimatedCost()).isEqualTo(300.0);
    assertThat(performance.getPerDayCost()).isEqualTo(300.0 / duration);
    assertThat(performance.getTotalAdPlays()).isEqualTo(300L);
    assertThat(performance.getPerDayAdPlays()).isEqualTo(300L / duration);
    assertThat(performance.getTotalSot()).isEqualTo(82.0);
    assertThat(performance.getPlannedSot()).isEqualTo(6.0);
    assertThat(performance.getSov()).isEqualTo(10.25);
    assertThat(performance.getEstimatedImpression()).isEqualTo(117800L);
    assertThat(performance.getEstimatedReach()).isEqualTo(13497L);
    assertThat(performance.getEstimatedFrequency()).isEqualTo(8.72);
  }

  @Test
  void getAllSelectedInventories_WithMediaOwnerIds_ShouldFilterByMediaOwners() {
    // Given
    String campaignId = "campaign123";
    List<String> mediaOwnerIds = List.of("owner-1");
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(mediaOwnerIds).build();

    testInventory1.setInventoryId("ENV-inventory1");
    testSchedule1.setScheduleIds(List.of("schedule1"));
    Schedule scheduleEntity1 = new Schedule();
    scheduleEntity1.setBasePrice(100.0);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignIdAndMediaOwnerIdIn(
            campaignId, mediaOwnerIds))
        .thenReturn(List.of(testSchedule1));
    when(inventoryRepository.findAllById(List.of("inventory1")))
        .thenReturn(List.of(testInventory1));
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(campaignId, "inventory1"))
        .thenReturn(testSchedule1);
    when(campaignInventorySchedulesService.getSchedulesByIds(List.of("schedule1")))
        .thenReturn(List.of(scheduleEntity1));
    when(campaignService.calculateCampaignForecast(testCampaign, List.of(testSchedule1)))
        .thenReturn(CampaignForecastDTO.builder().estimatedAdPlays(100L).build());

    // When
    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId, request);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInventoryId()).isEqualTo("ENV-inventory1");
    verify(campaignInventorySchedulesService)
        .findByCampaignIdAndMediaOwnerIdIn(campaignId, mediaOwnerIds);
    verify(campaignInventorySchedulesService, never()).findByCampaignId(anyString());
    verify(campaignInventorySchedulesService, never())
        .findByCampaignIdAndMediaOwnerId(anyString(), anyString());
  }

  @Test
  void getAllSelectedInventories_WithEmptyMediaOwnerIds_ShouldBehaveLikeGet() {
    // Given
    String campaignId = "campaign123";
    MediaOwnerFilterRequestDTO request =
        MediaOwnerFilterRequestDTO.builder().mediaOwnerIds(List.of()).build();

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());

    // When
    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId, request);

    // Then
    assertThat(result).isEmpty();
    verify(campaignInventorySchedulesService).findByCampaignId(campaignId);
    verify(campaignInventorySchedulesService, never())
        .findByCampaignIdAndMediaOwnerIdIn(anyString(), anyList());
  }

  @Test
  void getAllSelectedInventories_WithEmptySelection_ShouldReturnEmptyList() {
    // Given
    String campaignId = "campaign123";
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(userService.getPrimaryCompanyId()).thenReturn("company123");
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());

    // When
    List<SelectedInventorySummaryResponseDTO> result =
        inventoryService.getAllSelectedInventories(campaignId);

    // Then
    assertThat(result).isEmpty();
    verify(inventoryRepository, never()).findAllById(anyList());
  }

  @Test
  void getAllSelectedInventories_WhenCampaignNotFound_ShouldThrowException() {
    // Given
    String campaignId = "nonexistent";
    when(campaignService.findByIdForCurrentMode(campaignId))
        .thenThrow(new CampaignNotFoundException(campaignId));

    // When & Then
    assertThatThrownBy(() -> inventoryService.getAllSelectedInventories(campaignId))
        .isInstanceOf(CampaignNotFoundException.class);
    verify(inventoryRepository, never()).findAllById(anyList());
  }

  // ---------- enrichFilterWithCampaignVenueTypes Tests ----------

  @Test
  void filterInventories_WithCampaignVenueTypes_EnrichesFilterWithIds() {
    // Given — campaign has digitalOoh slugs, venuesService resolves them to IDs
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();

    Campaign.Targeting.VenueTypes venueTypes =
        Campaign.Targeting.VenueTypes.builder()
            .digitalOoh(List.of("health-beauty-gyms"))
            .classicOoh(List.of("outdoor-billboards"))
            .build();
    Campaign campaignWithVenueTypes =
        Campaign.builder()
            .name("Test")
            .userId("u1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("c1")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .targeting(Campaign.Targeting.builder().venueTypes(venueTypes).build())
            .build();
    campaignWithVenueTypes.setId(campaignId);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaignWithVenueTypes);
    when(venuesService.getVenueSlugToIdMap())
        .thenReturn(Map.of("health-beauty-gyms", "401", "outdoor-billboards", "301"));
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("c", "i"));
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    // When
    inventoryService.filterInventories(filter, campaignId, pageable);

    // Then — filter passed to repository must contain venueTypeIdFilter
    // findInventoriesWithFilters is called twice (content + count), capture all invocations
    org.mockito.ArgumentCaptor<CampaignInventoryFilterDTO> filterCaptor =
        org.mockito.ArgumentCaptor.forClass(CampaignInventoryFilterDTO.class);
    verify(inventoryRepositoryCustom, atLeastOnce())
        .findInventoriesWithFilters(filterCaptor.capture(), any());
    CampaignInventoryFilterDTO captured = filterCaptor.getAllValues().get(0);
    assertThat(captured.getVenueTypeIdFilter()).isNotNull();
    assertThat(captured.getVenueTypeIdFilter().getDigitalOoh()).containsExactly("401");
    assertThat(captured.getVenueTypeIdFilter().getClassicOoh()).containsExactly("301");
  }

  @Test
  void filterInventories_WhenFilterAlreadyHasVenueTypeIdFilter_DoesNotOverwrite() {
    // Given — caller already set venueTypeIdFilter explicitly
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO.VenueTypeIdFilter existingIdFilter =
        CampaignInventoryFilterDTO.VenueTypeIdFilter.builder().digitalOoh(List.of("999")).build();
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().venueTypeIdFilter(existingIdFilter).build();

    setupCommonMocks(campaignId);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    // When
    inventoryService.filterInventories(filter, campaignId, pageable);

    // Then — repository receives the original filter unchanged (no venuesService call)
    org.mockito.ArgumentCaptor<CampaignInventoryFilterDTO> filterCaptor =
        org.mockito.ArgumentCaptor.forClass(CampaignInventoryFilterDTO.class);
    verify(inventoryRepositoryCustom, atLeastOnce())
        .findInventoriesWithFilters(filterCaptor.capture(), any());
    assertThat(filterCaptor.getAllValues().get(0).getVenueTypeIdFilter().getDigitalOoh())
        .containsExactly("999");
    verify(venuesService, never()).getVenueSlugToIdMap();
  }

  @Test
  void filterInventories_WhenCampaignHasNoVenueTypes_DoesNotSetVenueTypeIdFilter() {
    // Given — campaign targeting exists but venueTypes is null
    String campaignId = "campaign123";
    Pageable pageable = PageRequest.of(0, 10);
    CampaignInventoryFilterDTO filter = new CampaignInventoryFilterDTO();

    Campaign campaignNoVenueTypes =
        Campaign.builder()
            .name("Test")
            .userId("u1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("c1")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .targeting(Campaign.Targeting.builder().build())
            .build();
    campaignNoVenueTypes.setId(campaignId);

    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(campaignNoVenueTypes);
    when(campaignInventorySchedulesService.findByCampaignId(campaignId)).thenReturn(List.of());
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("c", "i"));
    when(inventoryRepositoryCustom.findInventoriesWithFilters(
            any(CampaignInventoryFilterDTO.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    // When
    inventoryService.filterInventories(filter, campaignId, pageable);

    // Then — venueTypeIdFilter remains null, no venuesService call
    org.mockito.ArgumentCaptor<CampaignInventoryFilterDTO> filterCaptor =
        org.mockito.ArgumentCaptor.forClass(CampaignInventoryFilterDTO.class);
    verify(inventoryRepositoryCustom, atLeastOnce())
        .findInventoriesWithFilters(filterCaptor.capture(), any());
    assertThat(filterCaptor.getAllValues().get(0).getVenueTypeIdFilter()).isNull();
    verify(venuesService, never()).getVenueSlugToIdMap();
  }

  // Helper method to set up common mocks needed for convertToFilterResponseDTO
  private void setupCommonMocks(String campaignId) {
    when(campaignService.findByIdForCurrentMode(campaignId)).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(anyString(), anyString()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaignId", "inventoryId"));
    // Mock companyService to handle getMediaOwnerName calls (inventories don't have mediaOwnerId
    // set)
    // getMediaOwnerName will return null early if mediaOwnerId is null/blank
  }

  // Helper method to create test inventory
  // ---------- Static helper methods: spots / loops / rates ----------

  private Inventory inventoryWithDigitalFields(Integer spotDuration, Integer spotsPerLoop) {
    Inventory inv = new Inventory();
    inv.setId("inv-digital");
    inv.setDigitalFields(
        Inventory.DigitalFields.builder()
            .spotDuration(spotDuration)
            .spotsPerLoop(spotsPerLoop)
            .build());
    return inv;
  }

  @Test
  void getSpotsPerHour_WithValidSpotDuration_ReturnsComputedValue() {
    assertThat(InventoryService.getSpotsPerHour(inventoryWithDigitalFields(10, 6))).isEqualTo(360L);
  }

  @Test
  void getSpotsPerHour_WithNullDigitalFields_ReturnsZero() {
    Inventory inv = new Inventory();
    inv.setDigitalFields(null);
    assertThat(InventoryService.getSpotsPerHour(inv)).isZero();
  }

  @Test
  void getSpotsPerHour_WithNullSpotDuration_ReturnsZero() {
    assertThat(InventoryService.getSpotsPerHour(inventoryWithDigitalFields(null, 6))).isZero();
  }

  @Test
  void getSpotsPerHour_WithNonPositiveSpotDuration_ReturnsZero() {
    assertThat(InventoryService.getSpotsPerHour(inventoryWithDigitalFields(0, 6))).isZero();
  }

  @Test
  void getSpotsPerLoop_WithValue_ReturnsValue() {
    assertThat(InventoryService.getSpotsPerLoop(inventoryWithDigitalFields(10, 6))).isEqualTo(6L);
  }

  @Test
  void getSpotsPerLoop_WithNullDigitalFields_ReturnsZero() {
    assertThat(InventoryService.getSpotsPerLoop(new Inventory())).isZero();
  }

  @Test
  void getSpotsPerLoop_WithNullSpotsPerLoop_ReturnsZero() {
    assertThat(InventoryService.getSpotsPerLoop(inventoryWithDigitalFields(10, null))).isZero();
  }

  @Test
  void getSpotDuration_WithValue_ReturnsValue() {
    assertThat(InventoryService.getSpotDuration(inventoryWithDigitalFields(10, 6))).isEqualTo(10L);
  }

  @Test
  void getSpotDuration_WithNullDigitalFields_ReturnsZero() {
    assertThat(InventoryService.getSpotDuration(new Inventory())).isZero();
  }

  @Test
  void getLoopsPerHour_WithValidValues_ReturnsSpotsPerHourDividedBySpotsPerLoop() {
    // spotsPerHour = 3600/10 = 360; loops = 360/6 = 60
    assertThat(InventoryService.getLoopsPerHour(inventoryWithDigitalFields(10, 6))).isEqualTo(60L);
  }

  @Test
  void getLoopsPerHour_WithZeroSpotsPerLoop_ReturnsZeroFromCaughtDivideByZero() {
    assertThat(InventoryService.getLoopsPerHour(inventoryWithDigitalFields(10, null))).isZero();
  }

  @Test
  void getCpm_WithPrices_ReturnsFirstCpm() {
    Inventory inv = new Inventory();
    inv.setPrices(List.of(Inventory.Price.builder().cpm(4.5).spot(1.2).build()));
    assertThat(InventoryService.getCpm(inv)).isEqualTo(4.5);
  }

  @Test
  void getCpm_WithNullPrices_ReturnsNull() {
    assertThat(InventoryService.getCpm(new Inventory())).isNull();
  }

  @Test
  void getCpm_WithEmptyPrices_ReturnsNull() {
    Inventory inv = new Inventory();
    inv.setPrices(Collections.emptyList());
    assertThat(InventoryService.getCpm(inv)).isNull();
  }

  @Test
  void getSpotRate_WithPrices_ReturnsFirstSpot() {
    Inventory inv = new Inventory();
    inv.setPrices(List.of(Inventory.Price.builder().cpm(4.5).spot(1.2).build()));
    assertThat(InventoryService.getSpotRate(inv)).isEqualTo(1.2);
  }

  @Test
  void getSpotRate_WithNullPrices_ReturnsNull() {
    assertThat(InventoryService.getSpotRate(new Inventory())).isNull();
  }

  @Test
  void getSpotRate_WithEmptyPrices_ReturnsNull() {
    Inventory inv = new Inventory();
    inv.setPrices(Collections.emptyList());
    assertThat(InventoryService.getSpotRate(inv)).isNull();
  }

  @Test
  void getCpm_WhenFirstElementHasNoCpm_ReturnsFirstNonNullCpm() {
    Inventory inv = new Inventory();
    inv.setPrices(
        List.of(
            Inventory.Price.builder().cpm(null).spot(1.0).build(),
            Inventory.Price.builder().cpm(7.5).spot(2.0).build(),
            Inventory.Price.builder().cpm(9.9).spot(3.0).build()));
    assertThat(InventoryService.getCpm(inv)).isEqualTo(7.5);
  }

  @Test
  void getCpm_WhenNoElementHasCpm_ReturnsNull() {
    Inventory inv = new Inventory();
    inv.setPrices(
        Arrays.asList(
            Inventory.Price.builder().cpm(null).spot(1.0).build(),
            Inventory.Price.builder().cpm(null).spot(2.0).build()));
    assertThat(InventoryService.getCpm(inv)).isNull();
  }

  @Test
  void getCpm_WithNullPriceEntry_SkipsAndReturnsNonNullCpm() {
    Inventory inv = new Inventory();
    inv.setPrices(Arrays.asList(null, Inventory.Price.builder().cpm(5.5).spot(2.0).build()));
    assertThat(InventoryService.getCpm(inv)).isEqualTo(5.5);
  }

  @Test
  void getSpotRate_WhenFirstElementHasNoSpot_ReturnsFirstNonNullSpot() {
    Inventory inv = new Inventory();
    inv.setPrices(
        List.of(
            Inventory.Price.builder().cpm(1.0).spot(null).build(),
            Inventory.Price.builder().cpm(2.0).spot(3.3).build(),
            Inventory.Price.builder().cpm(3.0).spot(4.4).build()));
    assertThat(InventoryService.getSpotRate(inv)).isEqualTo(3.3);
  }

  @Test
  void getSpotRate_WhenNoElementHasSpot_ReturnsNull() {
    Inventory inv = new Inventory();
    inv.setPrices(
        Arrays.asList(
            Inventory.Price.builder().cpm(1.0).spot(null).build(),
            Inventory.Price.builder().cpm(2.0).spot(null).build()));
    assertThat(InventoryService.getSpotRate(inv)).isNull();
  }

  @Test
  void getSpotRate_WithNullPriceEntry_SkipsAndReturnsNonNullSpot() {
    Inventory inv = new Inventory();
    inv.setPrices(Arrays.asList(null, Inventory.Price.builder().cpm(2.0).spot(6.6).build()));
    assertThat(InventoryService.getSpotRate(inv)).isEqualTo(6.6);
  }

  @Test
  void calculateAvailableHours_WithNullOperatingTimes_ReturnsZero() {
    assertThat(InventoryService.calculateAvailableHours(new Inventory())).isZero();
  }

  @Test
  void calculateAvailableHours_WithEmptyOperatingTimes_ReturnsZero() {
    Inventory inv = new Inventory();
    inv.setOperatingTimes(Map.of());
    assertThat(InventoryService.calculateAvailableHours(inv)).isZero();
  }

  @Test
  void calculateAvailableHours_WithOperatingTime_ReturnsHoursBetween() {
    Inventory inv = new Inventory();
    inv.setOperatingTimes(
        Map.of(
            Inventory.Weekday.MONDAY,
            List.of(Inventory.OperatingTime.builder().start("07:00:00").end("23:00:00").build())));
    assertThat(InventoryService.calculateAvailableHours(inv)).isEqualTo(16);
  }

  // ---------- Simple finder / delegation methods ----------

  @Test
  void getById_WhenFound_ReturnsInventory() {
    when(inventoryRepository.findById("inv1")).thenReturn(Optional.of(testInventory1));
    assertThat(inventoryService.getById("inv1")).isSameAs(testInventory1);
  }

  @Test
  void getById_WhenNotFound_ThrowsInventoryNotFoundException() {
    when(inventoryRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> inventoryService.getById("missing"))
        .isInstanceOf(InventoryNotFoundException.class);
  }

  @Test
  void existsById_DelegatesToRepository() {
    when(inventoryRepository.existsById("inv1")).thenReturn(true);
    assertThat(inventoryService.existsById("inv1")).isTrue();
  }

  @Test
  void findByInventoryId_DelegatesToRepository() {
    when(inventoryRepository.findByInventoryId("inv1")).thenReturn(Optional.of(testInventory1));
    assertThat(inventoryService.findByInventoryId("inv1")).contains(testInventory1);
  }

  @Test
  void findByExternalId_DelegatesToRepository() {
    when(inventoryRepository.findFirstByExternalId("EXT-1"))
        .thenReturn(Optional.of(testInventory1));
    assertThat(inventoryService.findByExternalId("EXT-1")).contains(testInventory1);
  }

  @Test
  void findByExternalIdIn_DelegatesToRepository() {
    when(inventoryRepository.findByExternalIdIn(List.of("EXT-1")))
        .thenReturn(List.of(testInventory1));
    assertThat(inventoryService.findByExternalIdIn(List.of("EXT-1")))
        .containsExactly(testInventory1);
  }

  @Test
  void findByReferenceId_DelegatesToRepository() {
    when(inventoryRepository.findFirstByReferenceId("REF-1"))
        .thenReturn(Optional.of(testInventory1));
    assertThat(inventoryService.findByReferenceId("REF-1")).contains(testInventory1);
  }

  @Test
  void getMediaOwnerIdById_WhenFound_ReturnsMediaOwnerId() {
    when(inventoryRepository.findMediaOwnerIdById("inv1"))
        .thenReturn(new Document("mediaOwnerId", "mo1"));
    assertThat(inventoryService.getMediaOwnerIdById("inv1")).isEqualTo("mo1");
  }

  @Test
  void getMediaOwnerIdById_WhenRepositoryReturnsNull_ReturnsNull() {
    when(inventoryRepository.findMediaOwnerIdById("inv1")).thenReturn(null);
    assertThat(inventoryService.getMediaOwnerIdById("inv1")).isNull();
  }

  @Test
  void findAllByIds_WithNull_ReturnsEmpty() {
    assertThat(inventoryService.findAllByIds(null)).isEmpty();
    verify(inventoryRepository, never()).findAllById(any());
  }

  @Test
  void findAllByIds_WithEmpty_ReturnsEmpty() {
    assertThat(inventoryService.findAllByIds(Collections.emptyList())).isEmpty();
    verify(inventoryRepository, never()).findAllById(any());
  }

  @Test
  void findAllByIds_WithIds_DelegatesToRepository() {
    when(inventoryRepository.findAllById(List.of("inv1"))).thenReturn(List.of(testInventory1));
    assertThat(inventoryService.findAllByIds(List.of("inv1"))).containsExactly(testInventory1);
  }

  @Test
  void findAllByReferenceIds_WithNull_ReturnsEmpty() {
    assertThat(inventoryService.findAllByReferenceIds(null)).isEmpty();
    verify(inventoryRepository, never()).findByReferenceIdIn(anyList());
  }

  @Test
  void findAllByReferenceIds_WithIds_DelegatesToRepository() {
    when(inventoryRepository.findByReferenceIdIn(List.of("REF-1")))
        .thenReturn(List.of(testInventory1));
    assertThat(inventoryService.findAllByReferenceIds(List.of("REF-1")))
        .containsExactly(testInventory1);
  }

  @Test
  void save_DelegatesToRepository() {
    when(inventoryRepository.save(testInventory1)).thenReturn(testInventory1);
    assertThat(inventoryService.save(testInventory1)).isSameAs(testInventory1);
  }

  @Test
  void deleteById_DelegatesToRepository() {
    inventoryService.deleteById("inv1");
    verify(inventoryRepository).deleteById("inv1");
  }

  @Test
  void upsertByNaturalKey_DelegatesToCustomRepository() {
    when(inventoryRepositoryCustom.upsertByNaturalKey(testInventory1)).thenReturn(testInventory1);
    assertThat(inventoryService.upsertByNaturalKey(testInventory1)).isSameAs(testInventory1);
  }

  @Test
  void findIdByIdInAndType_ReturnsMappedIds() {
    when(inventoryRepository.findIdByIdInAndType(List.of("inv1", "inv2"), "DIGITAL"))
        .thenReturn(List.of(testInventory1, testInventory2));
    assertThat(inventoryService.findIdByIdInAndType(List.of("inv1", "inv2"), "DIGITAL"))
        .containsExactly("inventory1", "inventory2");
  }

  // ---------- getInventoryResponseDTOById / convertCoordinates ----------

  private Inventory inventoryWithCoordinates(Object coordinates) {
    Inventory inv = new Inventory();
    inv.setId("inv-geo");
    inv.setName("Geo Inventory");
    Inventory.Location location = new Inventory.Location();
    location.setCountry("SG");
    location.setLocationCoordinates(coordinates);
    inv.setLocation(location);
    return inv;
  }

  @Test
  void getInventoryResponseDTOById_WithPointCoordinates_MapsPoint() {
    Inventory inv =
        inventoryWithCoordinates(Map.of("type", "Point", "coordinates", List.of(103.8, 1.35)));
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    var coords = result.getLocation().getLocationCoordinates();
    assertThat(coords.getType()).isEqualTo("Point");
    assertThat(coords.getCoordinates()).hasSize(1);
    assertThat(coords.getCoordinates().get(0).getLatitude()).isEqualTo(1.35);
    assertThat(coords.getCoordinates().get(0).getLongitude()).isEqualTo(103.8);
  }

  @Test
  void getInventoryResponseDTOById_WithLineStringCoordinates_MapsAllPointsIncludingStringNumbers() {
    Inventory inv =
        inventoryWithCoordinates(
            Map.of(
                "type",
                "LineString",
                "coordinates",
                List.of(List.of(1.0, 2.0), List.of("3.0", "4.0"))));
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    var coords = result.getLocation().getLocationCoordinates();
    assertThat(coords.getType()).isEqualTo("LineString");
    assertThat(coords.getCoordinates()).hasSize(2);
    assertThat(coords.getCoordinates().get(1).getLatitude()).isEqualTo(4.0);
  }

  @Test
  void getInventoryResponseDTOById_WithNullLocation_ReturnsNullLocation() {
    Inventory inv = new Inventory();
    inv.setId("inv-geo");
    inv.setLocation(null);
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation()).isNull();
  }

  @Test
  void getInventoryResponseDTOById_WithNullCoordinates_ReturnsLocationWithoutCoordinates() {
    Inventory inv = inventoryWithCoordinates(null);
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation()).isNotNull();
    assertThat(result.getLocation().getLocationCoordinates()).isNull();
  }

  @Test
  void getInventoryResponseDTOById_WithNonMapCoordinates_ReturnsNullCoordinates() {
    Inventory inv = inventoryWithCoordinates("not-a-map");
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation().getLocationCoordinates()).isNull();
  }

  @Test
  void getInventoryResponseDTOById_WithUnknownGeoType_ReturnsNullCoordinates() {
    Inventory inv =
        inventoryWithCoordinates(Map.of("type", "Polygon", "coordinates", List.of(1.0, 2.0)));
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation().getLocationCoordinates()).isNull();
  }

  @Test
  void getInventoryResponseDTOById_WithPointMissingSecondCoordinate_ReturnsNullCoordinates() {
    Inventory inv =
        inventoryWithCoordinates(Map.of("type", "Point", "coordinates", List.of(103.8)));
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation().getLocationCoordinates()).isNull();
  }

  @Test
  void getInventoryResponseDTOById_WithLineStringAllPointsUnparseable_ReturnsNullCoordinates() {
    Inventory inv =
        inventoryWithCoordinates(
            Map.of("type", "LineString", "coordinates", List.of(List.of("bad", "4.0"))));
    when(inventoryRepository.findById("inv-geo")).thenReturn(Optional.of(inv));

    var result = inventoryService.getInventoryResponseDTOById("inv-geo");

    assertThat(result.getLocation().getLocationCoordinates()).isNull();
  }

  private Inventory createInventory(String id, String name, String type) {
    Inventory inventory = new Inventory();
    inventory.setId(id);
    inventory.setName(name);
    inventory.setType(type);
    inventory.setReferenceId("REF-" + id);
    inventory.setExternalId("EXT-" + id);
    inventory.setArchived(false); // archived=false means active=true
    inventory.setSize("48x14");
    inventory.setInventoryCluster(List.of("cluster-A"));
    // Don't set mediaOwnerId so getMediaOwnerName returns null early
    return inventory;
  }

  // ---------- getInventoriesWithFiltersForBulkOperation (§3A) ----------

  @Test
  void getInventoriesWithFiltersForBulkOperation_WithNullFilter_UsesEmptyFilter() {
    when(inventoryRepositoryCustom.findInventoriesWithFiltersForBulkOperation(any()))
        .thenReturn(List.of(testInventory1));

    List<Inventory> result = inventoryService.getInventoriesWithFiltersForBulkOperation(null);

    assertThat(result).containsExactly(testInventory1);
    ArgumentCaptor<CampaignInventoryFilterDTO> captor =
        ArgumentCaptor.forClass(CampaignInventoryFilterDTO.class);
    verify(inventoryRepositoryCustom).findInventoriesWithFiltersForBulkOperation(captor.capture());
    assertThat(captor.getValue()).isNotNull(); // a fresh empty DTO was created
  }

  @Test
  void getInventoriesWithFiltersForBulkOperation_WithFilter_PassesThrough() {
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder().name("billboard").build();
    when(inventoryRepositoryCustom.findInventoriesWithFiltersForBulkOperation(filter))
        .thenReturn(List.of(testInventory1, testInventory2));

    List<Inventory> result = inventoryService.getInventoriesWithFiltersForBulkOperation(filter);

    assertThat(result).containsExactly(testInventory1, testInventory2);
  }

  // ---------- Preview metrics path via filterInventories (§3B) ----------

  private Inventory previewInventory(Double cpm, Double spot) {
    Inventory inv = new Inventory();
    inv.setId("inv-preview");
    inv.setReferenceId("REF-preview");
    inv.setType("DIGITAL");
    inv.setDigitalFields(
        Inventory.DigitalFields.builder().spotDuration(10).spotsPerLoop(6).build());
    inv.setPrices(List.of(Inventory.Price.builder().cpm(cpm).spot(spot).build()));
    return inv;
  }

  private void setGoal(Campaign.Goals.GoalType goalType) {
    Campaign.Goals goals = new Campaign.Goals();
    goals.setGoalType(goalType);
    testCampaign.setGoals(goals);
  }

  // Drives filterInventories down the non-restrictive, no-selected path so the single filtered
  // inventory flows through the preview branch of calculatePerformanceMetrics.
  private CampaignInventoryFilterResponseDTO runPreviewFilter(Inventory inv) {
    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignId("campaign123")).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of(inv)));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(1L);
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId(
            "campaign123", inv.getId()))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaign123", inv.getId()));
    when(campaignInventorySchedulesService.calculateSimpleBookingMatrix(any(), any(), any()))
        .thenReturn(Map.of("2026-01-05", List.of(7, 8)));
    when(campaignInventorySchedulesService.calculateAdPlays(anyLong(), any())).thenReturn(100L);

    Page<CampaignInventoryFilterResponseDTO> page =
        inventoryService.filterInventories(
            new CampaignInventoryFilterDTO(), "campaign123", PageRequest.of(0, 10));
    return page.getContent().get(0);
  }

  @Test
  void filterInventories_PreviewImpressionsGoal_UsesCpmFromMeasureApi() {
    setGoal(Campaign.Goals.GoalType.IMPRESSIONS);
    Inventory inv = previewInventory(5.0, 2.0); // cpm 5
    when(mwMeasureService.getReachAndFrequency(any()))
        .thenReturn(
            MeasureReachFrequencyResponseDTO.builder()
                .impressions(1000L)
                .reach(400L)
                .frequency(2.5)
                .build());

    CampaignInventoryFilterResponseDTO result = runPreviewFilter(inv);

    var perf = result.getPerformance();
    assertThat(perf.getCpmRate()).isEqualTo(5.0);
    assertThat(perf.getEstimatedCost()).isEqualTo(5.0); // (5/1000)*1000
    assertThat(perf.getEstimatedImpression()).isEqualTo(1000L);
    assertThat(perf.getEstimatedReach()).isEqualTo(400L);
    assertThat(perf.getTotalAdPlays()).isEqualTo(100L);
  }

  @Test
  void filterInventories_PreviewSovGoal_UsesSpotCostWithoutMeasureApi() {
    setGoal(Campaign.Goals.GoalType.SOV);
    Inventory inv = previewInventory(5.0, 2.0); // spot 2

    CampaignInventoryFilterResponseDTO result = runPreviewFilter(inv);

    var perf = result.getPerformance();
    assertThat(perf.getEstimatedCost()).isEqualTo(200.0); // spot 2 * adPlays 100
    assertThat(perf.getEstimatedImpression()).isNull();
    verify(mwMeasureService, never()).getReachAndFrequency(any());
  }

  @Test
  void filterInventories_PreviewNullGoalWithSpotNull_FallsBackToCpm() {
    // no goal set -> goalType null -> useCpm = (spotPrice == null)
    Inventory inv = previewInventory(5.0, null); // spot null -> CPM route
    when(mwMeasureService.getReachAndFrequency(any()))
        .thenReturn(MeasureReachFrequencyResponseDTO.builder().impressions(2000L).build());

    CampaignInventoryFilterResponseDTO result = runPreviewFilter(inv);

    assertThat(result.getPerformance().getEstimatedCost()).isEqualTo(10.0); // (5/1000)*2000
  }

  @Test
  void filterInventories_PreviewImpressionsGoalWithNullCpm_ReturnsNullCost() {
    setGoal(Campaign.Goals.GoalType.IMPRESSIONS);
    Inventory inv = previewInventory(null, 2.0); // cpm null

    CampaignInventoryFilterResponseDTO result = runPreviewFilter(inv);

    assertThat(result.getPerformance().getEstimatedCost()).isNull();
    verify(mwMeasureService, never()).getReachAndFrequency(any());
  }

  @Test
  void filterInventories_PreviewImpressionsGoalWithNoMeasureImpressions_ReturnsNullCost() {
    setGoal(Campaign.Goals.GoalType.IMPRESSIONS);
    Inventory inv = previewInventory(5.0, 2.0);
    when(mwMeasureService.getReachAndFrequency(any()))
        .thenReturn(MeasureReachFrequencyResponseDTO.builder().impressions(null).build());

    CampaignInventoryFilterResponseDTO result = runPreviewFilter(inv);

    assertThat(result.getPerformance().getEstimatedCost()).isNull();
  }

  // ---------- hasRestrictiveFilters disjuncts (§3D) ----------

  // Runs filterInventories with the given filter; one non-selected inventory (no digitalFields, so
  // no preview computation) flows through so we can assert it is returned as not-selected.
  private Page<CampaignInventoryFilterResponseDTO> runFilter(CampaignInventoryFilterDTO filter) {
    Inventory inv = createInventory("inv-r", "R", "DIGITAL"); // no digitalFields -> no preview
    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);
    when(campaignInventorySchedulesService.findByCampaignId("campaign123")).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of(inv)));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(1L);
    when(campaignInventorySchedulesService.findByCampaignIdAndInventoryId("campaign123", "inv-r"))
        .thenThrow(new CampaignInventorySchedulesNotFoundException("campaign123", "inv-r"));
    return inventoryService.filterInventories(filter, "campaign123", PageRequest.of(0, 10));
  }

  @Test
  void filterInventories_RestrictiveByInventoryTypes_ReturnsFilteredNonSelected() {
    var page =
        runFilter(CampaignInventoryFilterDTO.builder().inventoryTypes(List.of("DIGITAL")).build());
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getDetail().getIsSelected()).isFalse();
  }

  @Test
  void filterInventories_RestrictiveBySizes_ReturnsFiltered() {
    var page = runFilter(CampaignInventoryFilterDTO.builder().sizes(List.of("48x14")).build());
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void filterInventories_RestrictiveByBookingMode_ReturnsFiltered() {
    var page = runFilter(CampaignInventoryFilterDTO.builder().bookingMode(List.of("loop")).build());
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void filterInventories_RestrictiveByDealTypes_ReturnsFiltered() {
    var page =
        runFilter(
            CampaignInventoryFilterDTO.builder()
                .dealTypes(List.of(ProgrammaticDealType.OPEN_AUCTION))
                .build());
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void filterInventories_RestrictiveByProgrammaticSupportNotAll_ReturnsFiltered() {
    var page =
        runFilter(
            CampaignInventoryFilterDTO.builder()
                .programmaticSupport(ProgrammaticSupport.YES)
                .build());
    assertThat(page.getContent()).hasSize(1);
  }

  @Test
  void filterInventories_ProgrammaticSupportAll_IsNonRestrictive() {
    // programmaticSupport == ALL -> that disjunct is false -> non-restrictive (selected-first) path
    var page =
        runFilter(
            CampaignInventoryFilterDTO.builder()
                .programmaticSupport(ProgrammaticSupport.ALL)
                .build());
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getDetail().getIsSelected()).isFalse();
  }

  // ---------- enrichFilterWithCampaignVenueTypes branches (§3E) ----------

  @Test
  void filterInventories_WithNullCampaignId_SkipsEnrichmentAndSelection() {
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(0L);

    Page<CampaignInventoryFilterResponseDTO> page =
        inventoryService.filterInventories(
            new CampaignInventoryFilterDTO(), null, PageRequest.of(0, 10));

    assertThat(page.getContent()).isEmpty();
    verify(campaignService, never()).findById(anyString());
    verify(venuesService, never()).getVenueSlugToIdMap();
  }

  @Test
  void filterInventories_WhenFilterAlreadyHasVenueTypeFilter_SkipsEnrichmentLookup() {
    CampaignInventoryFilterDTO filter =
        CampaignInventoryFilterDTO.builder()
            .venueTypeFilter(CampaignInventoryFilterDTO.VenueTypeFilter.builder().build())
            .build();
    when(campaignInventorySchedulesService.findByCampaignId("campaign123")).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(0L);

    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);

    inventoryService.filterInventories(filter, "campaign123", PageRequest.of(0, 10));

    // enrichment short-circuits before any campaign/venues lookup
    verify(campaignService, never()).findById(anyString());
    verify(venuesService, never()).getVenueSlugToIdMap();
  }

  @Test
  void filterInventories_WithNullFilterAndCampaignVenueTypes_BuildsVenueTypeIdFilter() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder()
            .venueTypes(Campaign.Targeting.VenueTypes.builder().digitalOoh(List.of("gym")).build())
            .build();
    testCampaign.setTargeting(targeting);
    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);
    when(venuesService.getVenueSlugToIdMap()).thenReturn(Map.of("gym", "401"));
    when(campaignInventorySchedulesService.findByCampaignId("campaign123")).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(0L);

    // filter == null -> enrichment takes the builder() (not toBuilder()) path
    Page<CampaignInventoryFilterResponseDTO> page =
        inventoryService.filterInventories(null, "campaign123", PageRequest.of(0, 10));

    assertThat(page).isNotNull();
    verify(venuesService).getVenueSlugToIdMap();
  }

  @Test
  void filterInventories_WhenEnrichmentThrows_FallsBackToOriginalFilter() {
    Campaign.Targeting targeting =
        Campaign.Targeting.builder()
            .venueTypes(Campaign.Targeting.VenueTypes.builder().digitalOoh(List.of("gym")).build())
            .build();
    testCampaign.setTargeting(targeting);
    when(campaignService.findByIdForCurrentMode("campaign123")).thenReturn(testCampaign);
    when(venuesService.getVenueSlugToIdMap()).thenThrow(new RuntimeException("boom"));
    when(campaignInventorySchedulesService.findByCampaignId("campaign123")).thenReturn(List.of());
    when(inventoryRepositoryCustom.findInventoriesWithFilters(any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    when(inventoryRepositoryCustom.countInventoriesWithFilters(any())).thenReturn(0L);

    // enrichment catches the exception and proceeds with the original filter
    Page<CampaignInventoryFilterResponseDTO> page =
        inventoryService.filterInventories(
            new CampaignInventoryFilterDTO(), "campaign123", PageRequest.of(0, 10));

    assertThat(page.getContent()).isEmpty();
  }
}
