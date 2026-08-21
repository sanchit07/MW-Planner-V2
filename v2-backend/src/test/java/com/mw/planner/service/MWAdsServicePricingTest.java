package com.mw.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.Inventory;
import com.mw.planner.dto.ads.ExternalInventoryDTO;
import com.mw.planner.repository.CampaignInventorySchedulesRepository;
import com.mw.planner.repository.ScheduleRepository;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for pricing-related methods in MWAdsService, specifically testing the goal-based
 * pricing logic introduced in March 2026.
 */
@ExtendWith(MockitoExtension.class)
class MWAdsServicePricingTest {

  @Mock private com.mw.planner.config.MwPlannerProperties mwPlannerProperties;
  @Mock private org.springframework.web.client.RestTemplate restTemplate;
  @Mock private CampaignService campaignService;
  @Mock private CampaignInventorySchedulesRepository campaignInventorySchedulesRepository;
  @Mock private InventoryService inventoryService;
  @Mock private CountryService countryService;
  @Mock private com.mw.brand.lib.service.BrandService brandService;
  @Mock private CompanyService companyService;
  @Mock private UserService userService;
  @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Mock private ScheduleRepository scheduleRepository;

  @InjectMocks private MWAdsService mwAdsService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(mwPlannerProperties.getAds())
        .thenReturn(new com.mw.planner.config.MwPlannerProperties.Ads());
  }

  // ==================== isCpmPricingModel() Tests ====================

  @Test
  @DisplayName("isCpmPricingModel - Should return true for IMPRESSIONS goal type")
  void isCpmPricingModel_WithImpressionsGoal_ReturnsTrue() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(100000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("isCpmPricingModel - Should return true for REACH goal type")
  void isCpmPricingModel_WithReachGoal_ReturnsTrue() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.REACH)
                    .targetValue(50000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("isCpmPricingModel - Should return false for ADPLAYS goal type")
  void isCpmPricingModel_WithAdPlaysGoal_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.ADPLAYS)
                    .targetValue(10000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isCpmPricingModel - Should return false for SOV goal type")
  void isCpmPricingModel_WithSovGoal_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.SOV)
                    .targetValue(75.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isCpmPricingModel - Should return false when goals is null")
  void isCpmPricingModel_WithNullGoals_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(null)
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isCpmPricingModel - Should return false when goal type is null")
  void isCpmPricingModel_WithNullGoalType_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(Campaign.Goals.builder().build())
            .build();

    boolean result = invokePrivateMethod("isCpmPricingModel", campaign);

    assertThat(result).isFalse();
  }

  // ==================== isCpsPricingModel() Tests ====================

  @Test
  @DisplayName("isCpsPricingModel - Should return true for ADPLAYS goal type")
  void isCpsPricingModel_WithAdPlaysGoal_ReturnsTrue() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.ADPLAYS)
                    .targetValue(10000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpsPricingModel", campaign);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("isCpsPricingModel - Should return true for SOV goal type")
  void isCpsPricingModel_WithSovGoal_ReturnsTrue() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.SOV)
                    .targetValue(80.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpsPricingModel", campaign);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("isCpsPricingModel - Should return false for IMPRESSIONS goal type")
  void isCpsPricingModel_WithImpressionsGoal_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(100000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpsPricingModel", campaign);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isCpsPricingModel - Should return false for REACH goal type")
  void isCpsPricingModel_WithReachGoal_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.REACH)
                    .targetValue(50000.0)
                    .build())
            .build();

    boolean result = invokePrivateMethod("isCpsPricingModel", campaign);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isCpsPricingModel - Should return false when goals is null")
  void isCpsPricingModel_WithNullGoals_ReturnsFalse() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(null)
            .build();

    boolean result = invokePrivateMethod("isCpsPricingModel", campaign);

    assertThat(result).isFalse();
  }

  // ==================== buildCpmPricing() Tests ====================

  @Test
  @DisplayName("buildCpmPricing - Should calculate estimated cost using updated formula")
  void buildCpmPricing_WithValidData_CalculatesEstimatedCost() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(10.0); // $10 CPM
    inventory.setPrices(List.of(price));

    long impressions = 5000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildCpmPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(10.0);
    // Formula: (cpm / 1000) * impressions = (10.0 / 1000) * 5000 = 50.0
    // Standard CPM formula: Cost = (CPM × Impressions) / 1000
    // Cost = (10.0 × 5000) / 1000 = 50,000 / 1000 = 50.0
    assertThat(result.getEstimatedCost()).isEqualTo(50.0);
  }

  @Test
  @DisplayName("buildCpmPricing - Should return zero estimated cost when impressions is zero")
  void buildCpmPricing_WithZeroImpressions_ReturnsZeroEstimatedCost() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(10.0);
    inventory.setPrices(List.of(price));

    long impressions = 0L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildCpmPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(10.0);
    assertThat(result.getEstimatedCost()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("buildCpmPricing - Should handle null CPM from inventory")
  void buildCpmPricing_WithNullCpm_UsesDefaultZero() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    inventory.setPrices(null); // No pricing info

    long impressions = 5000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildCpmPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(0.0);
    assertThat(result.getEstimatedCost()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("buildCpmPricing - Should calculate correctly with large impression numbers")
  void buildCpmPricing_WithLargeImpressions_CalculatesCorrectly() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(25.0); // $25 CPM
    inventory.setPrices(List.of(price));

    long impressions = 1_000_000L; // 1 million impressions

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildCpmPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(25.0);
    // Formula: (cpm / 1000) * impressions = (25.0 / 1000) * 1,000,000 = 25000.0
    // Standard CPM formula: Cost = (CPM × Impressions) / 1000
    // Cost = (25.0 × 1,000,000) / 1000 = 25,000,000 / 1000 = 25,000.0
    assertThat(result.getEstimatedCost()).isEqualTo(25000.0);
  }

  // ==================== buildCpsPricing() Tests ====================

  @Test
  @DisplayName("buildCpsPricing - Should return null (placeholder implementation)")
  void buildCpsPricing_ReturnsNull() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .build();
    Inventory inventory = new Inventory();
    long impressions = 5000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildCpsPricing", campaign, inventory, impressions);

    assertThat(result).isNull();
  }

  // ==================== buildPricing() Integration Tests ====================

  @Test
  @DisplayName("buildPricing - Should use CPM pricing for IMPRESSIONS goal")
  void buildPricing_WithImpressionsGoal_UsesCpmPricing() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.IMPRESSIONS)
                    .targetValue(100000.0)
                    .build())
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(15.0);
    inventory.setPrices(List.of(price));

    long impressions = 10000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(15.0);
    assertThat(result.getEstimatedCost()).isGreaterThan(0.0);
  }

  @Test
  @DisplayName("buildPricing - Should use CPM pricing for REACH goal")
  void buildPricing_WithReachGoal_UsesCpmPricing() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.REACH)
                    .targetValue(50000.0)
                    .build())
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(12.0);
    inventory.setPrices(List.of(price));

    long impressions = 8000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(12.0);
  }

  @Test
  @DisplayName("buildPricing - Should return null for CPS pricing (ADPLAYS goal)")
  void buildPricing_WithAdPlaysGoal_ReturnsNull() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(
                Campaign.Goals.builder()
                    .goalType(Campaign.Goals.GoalType.ADPLAYS)
                    .targetValue(10000.0)
                    .build())
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");

    long impressions = 5000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildPricing", campaign, inventory, impressions);

    assertThat(result).isNull(); // CPS pricing not yet implemented
  }

  @Test
  @DisplayName("buildPricing - Should default to CPM pricing when goal type is unrecognized")
  void buildPricing_WithUnknownGoal_DefaultsToCpmPricing() throws Exception {
    Campaign campaign =
        Campaign.builder()
            .name("Test Campaign")
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .userId("user-1")
            .clientType(Campaign.ClientType.DIRECT_ADVERTISER)
            .companyId("company-1")
            .goals(null)
            .build();

    Inventory inventory = new Inventory();
    inventory.setId("inv-1");
    Inventory.Price price = new Inventory.Price();
    price.setCpm(20.0);
    inventory.setPrices(List.of(price));

    long impressions = 3000L;

    ExternalInventoryDTO.PricingDTO result =
        invokePrivateMethod("buildPricing", campaign, inventory, impressions);

    assertThat(result).isNotNull();
    assertThat(result.getCpm()).isEqualTo(20.0);
  }

  // ==================== Helper Methods ====================

  /**
   * Invokes a private method on MWAdsService using reflection for testing purposes
   *
   * @param methodName Name of the private method to invoke
   * @param args Arguments to pass to the method
   * @return Result of the method invocation
   */
  @SuppressWarnings("unchecked")
  private <T> T invokePrivateMethod(String methodName, Object... args) throws Exception {
    // Try to find the method by iterating through all declared methods
    for (Method method : MWAdsService.class.getDeclaredMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
        method.setAccessible(true);
        try {
          return (T) method.invoke(mwAdsService, args);
        } catch (IllegalArgumentException e) {
          // Try next method with same name but different parameters
          continue;
        }
      }
    }
    throw new NoSuchMethodException(
        "Could not find method " + methodName + " with " + args.length + " parameters");
  }
}
