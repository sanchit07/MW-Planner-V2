package com.mw.planner.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mongodb.client.result.UpdateResult;
import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.CampaignFilterDTO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class CampaignRepositoryTest {

  @Mock private MongoTemplate mongoTemplate;

  private CampaignRepositoryImpl campaignRepositoryImpl;
  private Campaign testCampaign;
  private CampaignFilterDTO testFilter;

  @BeforeEach
  void setUp() {
    campaignRepositoryImpl = new CampaignRepositoryImpl(mongoTemplate);

    testCampaign =
        Campaign.builder()
            .name("Test Campaign")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    testCampaign.setId("campaign123");

    testFilter =
        CampaignFilterDTO.builder()
            .nameContains("Test")
            .companyId("company123")
            .startDateFrom(LocalDate.now())
            .startDateTo(LocalDate.now().plusDays(30))
            .build();
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(mongoTemplate);
  }

  @Test
  void findCampaignsWithFilters_WithAllFilters_ShouldReturnFilteredCampaigns() {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);
    Page<Campaign> expectedPage = new PageImpl<>(campaigns, pageable, 1);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(testFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getName()).isEqualTo("Test Campaign");
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);

    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithNameContainsFilter_ShouldApplyRegexQuery() {
    // Given
    CampaignFilterDTO filter = CampaignFilterDTO.builder().nameContains("Summer").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithStatusFilter_ShouldApplyStatusQuery() {
    // Given
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder()
            .statuses(List.of(Campaign.Status.DRAFT, Campaign.Status.APPROVED))
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithGoalTypesFilter_ShouldApplyGoalTypeQuery() {
    // Given
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder()
            .goalTypes(List.of(Campaign.Goals.GoalType.IMPRESSIONS, Campaign.Goals.GoalType.REACH))
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithUserIdsFilter_ShouldApplyUserIdQuery() {
    // Given
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder().userIds(List.of("user1", "user2")).build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithDateRangeFilter_ShouldApplyDateQuery() {
    // Given
    CampaignFilterDTO filter =
        CampaignFilterDTO.builder()
            .startDateFrom(LocalDate.now())
            .startDateTo(LocalDate.now().plusDays(30))
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithCompanyIdFilter_ShouldApplyCompanyIdOrCompanyAccessQuery() {
    // Given
    CampaignFilterDTO filter = CampaignFilterDTO.builder().companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithCompanyIdMatching_ShouldReturnCampaign() {
    // Given - Campaign with matching companyId
    Campaign campaignWithCompanyId =
        Campaign.builder()
            .name("Campaign with Company ID")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .build();
    campaignWithCompanyId.setId("campaign1");

    CampaignFilterDTO filter = CampaignFilterDTO.builder().companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(campaignWithCompanyId);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getCompanyId()).isEqualTo("company123");
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithCompanyAccessMatching_ShouldReturnCampaign() {
    // Given - Campaign with companyAccess containing the companyId
    Campaign campaignWithCompanyAccess =
        Campaign.builder()
            .name("Campaign with Company Access")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("differentCompany")
            .companyAccess(List.of("company123", "company456"))
            .build();
    campaignWithCompanyAccess.setId("campaign2");

    CampaignFilterDTO filter = CampaignFilterDTO.builder().companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(campaignWithCompanyAccess);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getCompanyAccess()).contains("company123");
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithBothCompanyIdAndCompanyAccessMatching_ShouldReturnCampaign() {
    // Given - Campaign with both companyId and companyAccess matching
    Campaign campaignWithBoth =
        Campaign.builder()
            .name("Campaign with Both")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .companyAccess(List.of("company123", "company456"))
            .build();
    campaignWithBoth.setId("campaign3");

    CampaignFilterDTO filter = CampaignFilterDTO.builder().companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(campaignWithBoth);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getCompanyId()).isEqualTo("company123");
    assertThat(result.getContent().getFirst().getCompanyAccess()).contains("company123");
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithNoCompanyMatch_ShouldNotReturnCampaign() {
    // Given - Campaign with neither companyId nor companyAccess matching
    Campaign campaignNoMatch =
        Campaign.builder()
            .name("Campaign with No Match")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("differentCompany")
            .companyAccess(List.of("company456", "company789"))
            .build();
    campaignNoMatch.setId("campaign4");

    CampaignFilterDTO filter = CampaignFilterDTO.builder().companyId("company123").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = new ArrayList<>(); // Empty list - no matches

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(0L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(filter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithEmptyFilters_ShouldReturnAllCampaigns() {
    // Given
    CampaignFilterDTO emptyFilter = CampaignFilterDTO.builder().build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(emptyFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithPagination_ShouldApplyPagination() {
    // Given
    Pageable pageable = PageRequest.of(1, 5);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(10L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(testFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getNumber()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(5);
    assertThat(result.getTotalElements()).isEqualTo(6);
    assertThat(result.getTotalPages()).isEqualTo(2);

    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithComplexFilters_ShouldApplyAllFilters() {
    // Given
    CampaignFilterDTO complexFilter =
        CampaignFilterDTO.builder()
            .nameContains("Summer")
            .statuses(List.of(Campaign.Status.DRAFT, Campaign.Status.APPROVED))
            .goalTypes(List.of(Campaign.Goals.GoalType.IMPRESSIONS))
            .userIds(List.of("user1", "user2"))
            .startDateFrom(LocalDate.now())
            .startDateTo(LocalDate.now().plusDays(30))
            .companyId("company123")
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result =
        campaignRepositoryImpl.findCampaignsWithFilters(complexFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithNullFilters_ShouldHandleGracefully() {
    // Given
    CampaignFilterDTO nullFilter =
        CampaignFilterDTO.builder()
            .nameContains(null)
            .statuses(null)
            .goalTypes(null)
            .userIds(null)
            .startDateFrom(null)
            .startDateTo(null)
            .companyId(null)
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(nullFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void findCampaignsWithFilters_WithEmptyStringFilters_ShouldHandleGracefully() {
    // Given
    CampaignFilterDTO emptyStringFilter =
        CampaignFilterDTO.builder().nameContains("").companyId("").build();
    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(testCampaign);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result =
        campaignRepositoryImpl.findCampaignsWithFilters(emptyStringFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void
      findCampaignsWithFilters_WithCampaignContainingGeometryWithPoiAndMetadata_ShouldReturnCampaign() {
    // Given
    List<String> poiList = List.of("poi1", "poi2", "poi3");
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value2");

    Campaign.Targeting.Geofencing.Geometry geometry =
        Campaign.Targeting.Geofencing.Geometry.builder()
            .name("Test Geometry")
            .type("Polygon")
            .coordinates(
                List.of(List.of(0.0, 0.0), List.of(1.0, 0.0), List.of(1.0, 1.0), List.of(0.0, 1.0)))
            .isIncluded(true)
            .poi(poiList)
            .metadata(metadata)
            .build();

    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().geometries(List.of(geometry)).build();

    Campaign.Targeting targeting = Campaign.Targeting.builder().geofencing(geofencing).build();

    Campaign campaignWithGeometry =
        Campaign.builder()
            .name("Campaign with Geometry")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();
    campaignWithGeometry.setId("campaign456");

    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(campaignWithGeometry);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(testFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    Campaign returnedCampaign = result.getContent().getFirst();
    assertThat(returnedCampaign.getTargeting()).isNotNull();
    assertThat(returnedCampaign.getTargeting().getGeofencing()).isNotNull();
    assertThat(returnedCampaign.getTargeting().getGeofencing().getGeometries()).hasSize(1);

    Campaign.Targeting.Geofencing.Geometry returnedGeometry =
        returnedCampaign.getTargeting().getGeofencing().getGeometries().getFirst();
    assertThat(returnedGeometry.getPoi()).isNotNull();
    assertThat(returnedGeometry.getPoi()).hasSize(3);
    assertThat(returnedGeometry.getPoi()).containsExactly("poi1", "poi2", "poi3");
    assertThat(returnedGeometry.getMetadata()).isNotNull();
    assertThat(returnedGeometry.getMetadata()).hasSize(2);
    assertThat(returnedGeometry.getMetadata().get("key1")).isEqualTo("value1");
    assertThat(returnedGeometry.getMetadata().get("key2")).isEqualTo("value2");

    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void
      findCampaignsWithFilters_WithCampaignContainingLocationWithPoiAndMetadata_ShouldReturnCampaign() {
    // Given
    List<String> poiList = List.of("location_poi1", "location_poi2");
    Map<String, String> metadata = new HashMap<>();
    metadata.put("location_key1", "location_value1");
    metadata.put("location_key2", "location_value2");

    Campaign.Targeting.Geofencing.Location location =
        Campaign.Targeting.Geofencing.Location.builder()
            .name("Test Location")
            .lat(40.7128)
            .lng(-74.0060)
            .radius(1000.0)
            .address("123 Test St, New York, NY")
            .isIncluded(true)
            .poi(poiList)
            .metadata(metadata)
            .build();

    Campaign.Targeting.Geofencing geofencing =
        Campaign.Targeting.Geofencing.builder().locations(List.of(location)).build();

    Campaign.Targeting targeting = Campaign.Targeting.builder().geofencing(geofencing).build();

    Campaign campaignWithLocation =
        Campaign.builder()
            .name("Campaign with Location")
            .description("Test Description")
            .status(Campaign.Status.DRAFT)
            .budget(10000.0)
            .currency("USD")
            .startDate(LocalDate.now().plusDays(1))
            .endDate(LocalDate.now().plusDays(30))
            .userId("user123")
            .brand(Campaign.CampaignBrand.builder().id("brand123").build())
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company123")
            .targeting(targeting)
            .build();
    campaignWithLocation.setId("campaign789");

    Pageable pageable = PageRequest.of(0, 10);
    List<Campaign> campaigns = List.of(campaignWithLocation);

    when(mongoTemplate.find(any(Query.class), eq(Campaign.class))).thenReturn(campaigns);
    when(mongoTemplate.count(any(Query.class), eq(Campaign.class))).thenReturn(1L);

    // When
    Page<Campaign> result = campaignRepositoryImpl.findCampaignsWithFilters(testFilter, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    Campaign returnedCampaign = result.getContent().getFirst();
    assertThat(returnedCampaign.getTargeting()).isNotNull();
    assertThat(returnedCampaign.getTargeting().getGeofencing()).isNotNull();
    assertThat(returnedCampaign.getTargeting().getGeofencing().getLocations()).hasSize(1);

    Campaign.Targeting.Geofencing.Location returnedLocation =
        returnedCampaign.getTargeting().getGeofencing().getLocations().getFirst();
    assertThat(returnedLocation.getPoi()).isNotNull();
    assertThat(returnedLocation.getPoi()).hasSize(2);
    assertThat(returnedLocation.getPoi()).containsExactly("location_poi1", "location_poi2");
    assertThat(returnedLocation.getMetadata()).isNotNull();
    assertThat(returnedLocation.getMetadata()).hasSize(2);
    assertThat(returnedLocation.getMetadata().get("location_key1")).isEqualTo("location_value1");
    assertThat(returnedLocation.getMetadata().get("location_key2")).isEqualTo("location_value2");

    verify(mongoTemplate).find(any(Query.class), eq(Campaign.class));
    verify(mongoTemplate).count(any(Query.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_WithStartDate_ShouldUpdateCampaigns() {
    // Given
    LocalDate today = LocalDate.now();
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(5L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);

    // Then
    assertThat(result).isEqualTo(5);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_WithEndDate_ShouldUpdateCampaigns() {
    // Given
    LocalDate yesterday = LocalDate.now().minusDays(1);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(3L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.COMPLETED, null, yesterday);

    // Then
    assertThat(result).isEqualTo(3);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_WithBothDates_ShouldUpdateCampaigns() {
    // Given
    LocalDate startDate = LocalDate.now();
    LocalDate endDate = LocalDate.now().plusDays(30);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(2L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.DRAFT, Campaign.Status.APPROVED, startDate, endDate);

    // Then
    assertThat(result).isEqualTo(2);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_WithNoDates_ShouldUpdateCampaigns() {
    // Given
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(10L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.DRAFT, Campaign.Status.ARCHIVED, null, null);

    // Then
    assertThat(result).isEqualTo(10);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_WithZeroUpdates_ShouldReturnZero() {
    // Given
    LocalDate today = LocalDate.now();
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(0L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.APPROVED, Campaign.Status.ACTIVE, today, null);

    // Then
    assertThat(result).isEqualTo(0);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }

  @Test
  void bulkUpdateStatus_ForArchiving_ShouldUpdateCompletedCampaigns() {
    // Given
    LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(7L);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Campaign.class)))
        .thenReturn(updateResult);

    // When
    int result =
        campaignRepositoryImpl.bulkUpdateStatus(
            Campaign.Status.COMPLETED, Campaign.Status.ARCHIVED, null, thirtyDaysAgo);

    // Then
    assertThat(result).isEqualTo(7);
    verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Campaign.class));
  }
}
