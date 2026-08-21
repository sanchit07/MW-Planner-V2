package com.mw.planner.controller.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.dto.*;
import com.mw.planner.service.config.ConfigService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

  @Mock private ConfigService configService;

  @InjectMocks private ConfigController configController;

  private DemographicsGroupedResponseDTO groupedResponseDTO;
  private Map<String, Object> configurationData;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "en");

    // Create demographic items
    DemographicItemDTO ageItem = new DemographicItemDTO("18-24", "18-24 years", "Young adults");
    DemographicItemDTO genderItem = new DemographicItemDTO("male", "Male", "Male gender");
    DemographicItemDTO incomeItem =
        new DemographicItemDTO("high", "High Income", "High income bracket");
    DemographicItemDTO interestItem = new DemographicItemDTO("sports", "Sports", "Sports interest");
    DemographicItemDTO behaviorItem =
        new DemographicItemDTO("online_shopper", "Online Shopper", "Frequently shops online");

    // Create venue item
    VenueItemDTO venueItem = new VenueItemDTO();
    venueItem.setEnumerationId(101);
    venueItem.setTier(2);
    venueItem.setName("Test Venue");
    venueItem.setDefinition("Test Venue Description");
    venueItem.setStringValue("test_value");
    venueItem.setChildren(Collections.emptyList());

    // Create grouped response
    groupedResponseDTO =
        new DemographicsGroupedResponseDTO(
            Arrays.asList(ageItem),
            Arrays.asList(genderItem),
            Arrays.asList(incomeItem),
            Arrays.asList(interestItem),
            Arrays.asList(behaviorItem),
            Arrays.asList(venueItem));

    // Create configuration data map with campaign statuses
    configurationData =
        Map.of("demographics", groupedResponseDTO, "campaign_status", Campaign.Status.values());
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(configService);
  }

  @Test
  void getConfigurationData_ShouldReturnConfigurationData() {
    // Given
    when(configService.getConfigurationData(any(Locale.class))).thenReturn(configurationData);

    // When
    ApiResponse<Map<String, Object>> result = configController.getConfigurationData(request);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).containsKey("demographics");
    assertThat(result.getData()).containsKey("campaign_status");

    // Verify demographics data
    DemographicsGroupedResponseDTO demographics =
        (DemographicsGroupedResponseDTO) result.getData().get("demographics");
    assertThat(demographics).isNotNull();
    assertThat(demographics.getAge()).hasSize(1);
    assertThat(demographics.getGender()).hasSize(1);
    assertThat(demographics.getIncome()).hasSize(1);
    assertThat(demographics.getInterests()).hasSize(1);
    assertThat(demographics.getBehavior()).hasSize(1);
    assertThat(demographics.getVenues()).hasSize(1);

    // Verify campaign status data
    Campaign.Status[] campaignStatuses =
        (Campaign.Status[]) result.getData().get("campaign_status");
    assertThat(campaignStatuses).isNotNull();
    assertThat(campaignStatuses).hasSize(Campaign.Status.values().length);

    verify(configService).getConfigurationData(any(Locale.class));
  }

  @Test
  void getConfigurationData_WithEmptyData_ShouldReturnEmptyConfiguration() {
    // Given
    DemographicsGroupedResponseDTO emptyDemographics =
        new DemographicsGroupedResponseDTO(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    Map<String, Object> emptyConfigurationData =
        Map.of("demographics", emptyDemographics, "campaign_status", Campaign.Status.values());

    when(configService.getConfigurationData(any(Locale.class))).thenReturn(emptyConfigurationData);

    // When
    ApiResponse<Map<String, Object>> result = configController.getConfigurationData(request);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).containsKey("demographics");
    assertThat(result.getData()).containsKey("campaign_status");

    // Verify empty demographics data
    DemographicsGroupedResponseDTO demographics =
        (DemographicsGroupedResponseDTO) result.getData().get("demographics");
    assertThat(demographics).isNotNull();
    assertThat(demographics.getAge()).isEmpty();
    assertThat(demographics.getGender()).isEmpty();
    assertThat(demographics.getIncome()).isEmpty();
    assertThat(demographics.getInterests()).isEmpty();
    assertThat(demographics.getBehavior()).isEmpty();
    assertThat(demographics.getVenues()).isEmpty();

    // Verify campaign status data
    Campaign.Status[] campaignStatuses =
        (Campaign.Status[]) result.getData().get("campaign_status");
    assertThat(campaignStatuses).isNotNull();
    assertThat(campaignStatuses).hasSize(Campaign.Status.values().length);

    verify(configService).getConfigurationData(any(Locale.class));
  }

  @Test
  void getConfigurationData_WhenServiceThrowsException_ShouldPropagateException() {
    // Given
    RuntimeException serviceException = new RuntimeException("Service error");
    when(configService.getConfigurationData(any(Locale.class))).thenThrow(serviceException);

    // When & Then
    try {
      configController.getConfigurationData(request);
    } catch (RuntimeException e) {
      assertThat(e).isEqualTo(serviceException);
    }

    verify(configService).getConfigurationData(any(Locale.class));
  }

  @Test
  void getBrandCategories_ShouldReturnSuccessResponse() {
    // Given
    List<BrandIabCategory> brandCategories =
        Arrays.asList(
            BrandIabCategory.builder().code("IAB1").name("Arts & Entertainment").build(),
            BrandIabCategory.builder().code("IAB2").name("Automotive").build(),
            BrandIabCategory.builder().code("IAB3").name("Business").build());
    when(configService.getBrandCategoriesData(any(Locale.class))).thenReturn(brandCategories);

    // When
    ApiResponse<List<BrandIabCategory>> result = configController.getBrandCategories(request);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isEqualTo(brandCategories);
    assertThat(result.getData()).hasSize(3);

    // Verify specific items
    assertThat(result.getData().get(0).code()).isEqualTo("IAB1");
    assertThat(result.getData().get(0).name()).isEqualTo("Arts & Entertainment");
    assertThat(result.getData().get(1).code()).isEqualTo("IAB2");
    assertThat(result.getData().get(1).name()).isEqualTo("Automotive");
    assertThat(result.getData().get(2).code()).isEqualTo("IAB3");
    assertThat(result.getData().get(2).name()).isEqualTo("Business");

    verify(configService).getBrandCategoriesData(any(Locale.class));
  }

  @Test
  void getBrandCategories_WithEmptyData_ShouldReturnEmptyList() {
    // Given
    List<BrandIabCategory> emptyCategories = Collections.emptyList();
    when(configService.getBrandCategoriesData(any(Locale.class))).thenReturn(emptyCategories);

    // When
    ApiResponse<List<BrandIabCategory>> result = configController.getBrandCategories(request);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isEmpty();
    verify(configService).getBrandCategoriesData(any(Locale.class));
  }

  @Test
  void getBrandCategories_WhenServiceThrowsException_ShouldPropagateException() {
    // Given
    RuntimeException serviceException = new RuntimeException("Service error");
    when(configService.getBrandCategoriesData(any(Locale.class))).thenThrow(serviceException);

    // When & Then
    try {
      configController.getBrandCategories(request);
    } catch (RuntimeException e) {
      assertThat(e).isEqualTo(serviceException);
    }

    verify(configService).getBrandCategoriesData(any(Locale.class));
  }
}
