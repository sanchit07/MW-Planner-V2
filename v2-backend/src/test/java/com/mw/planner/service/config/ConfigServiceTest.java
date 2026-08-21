package com.mw.planner.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mw.planner.domain.Campaign;
import com.mw.planner.domain.Demographics;
import com.mw.planner.dto.*;
import com.mw.planner.repository.DemographicsRepository;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.VenuesService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

  @Mock private DemographicsRepository demographicsRepository;
  @Mock private VenuesService venuesService;
  @Mock private DefaultConfigurationService defaultConfigurationService;
  @Mock private MessageService messageService;

  private ConfigService configService;

  private List<Demographics> mockDemographics;
  private List<VenueItemDTO> mockVenues;

  @BeforeEach
  void setUp() {
    // Initialize ConfigService with mocked dependencies
    configService =
        new ConfigService(
            demographicsRepository, venuesService, defaultConfigurationService, messageService);
    // Create mock demographics data
    Demographics ageDemo = new Demographics();
    ageDemo.setDemoKey("18-24");
    ageDemo.setName("18-24 years");
    ageDemo.setDescription("Young adults");
    ageDemo.setDemoType(com.mw.planner.enums.DemographicsType.AGE);

    Demographics genderDemo = new Demographics();
    genderDemo.setDemoKey("male");
    genderDemo.setName("Male");
    genderDemo.setDescription("Male gender");
    genderDemo.setDemoType(com.mw.planner.enums.DemographicsType.GENDER);

    Demographics incomeDemo = new Demographics();
    incomeDemo.setDemoKey("high");
    incomeDemo.setName("High Income");
    incomeDemo.setDescription("High income bracket");
    incomeDemo.setDemoType(com.mw.planner.enums.DemographicsType.INCOME);

    Demographics interestDemo = new Demographics();
    interestDemo.setDemoKey("sports");
    interestDemo.setName("Sports");
    interestDemo.setDescription("Sports interest");
    interestDemo.setDemoType(com.mw.planner.enums.DemographicsType.INTEREST);

    Demographics behaviorDemo = new Demographics();
    behaviorDemo.setDemoKey("online_shopper");
    behaviorDemo.setName("Online Shopper");
    behaviorDemo.setDescription("Frequently shops online");
    behaviorDemo.setDemoType(com.mw.planner.enums.DemographicsType.BEHAVIOR);

    mockDemographics = Arrays.asList(ageDemo, genderDemo, incomeDemo, interestDemo, behaviorDemo);

    // Create mock venues data
    VenueItemDTO venue1 = new VenueItemDTO();
    venue1.setEnumerationId(205);
    venue1.setTier(2);
    venue1.setName("Shopping Mall");
    venue1.setDefinition("Large shopping mall");
    venue1.setStringValue("retail.malls");
    venue1.setChildren(Collections.emptyList());

    VenueItemDTO venue2 = new VenueItemDTO();
    venue2.setEnumerationId(805);
    venue2.setTier(2);
    venue2.setName("Restaurant");
    venue2.setDefinition("Fine dining restaurant");
    venue2.setStringValue("entertainment.casual_dining");
    venue2.setChildren(Collections.emptyList());

    mockVenues = Arrays.asList(venue1, venue2);
  }

  private DemographicsGroupedResponseDTO createDefaultDemographics() {
    // Create default age demographics
    List<DemographicItemDTO> defaultAge =
        Arrays.asList(
            new DemographicItemDTO("18_24", "18-24", "Young adults aged 18 to 24 years"),
            new DemographicItemDTO("25_34", "25–34", ""),
            new DemographicItemDTO("35_44", "35–44", ""),
            new DemographicItemDTO("45_54", "45–54", ""),
            new DemographicItemDTO("55_64", "55–64", ""),
            new DemographicItemDTO("65+", "65+", ""));

    // Create default gender demographics
    List<DemographicItemDTO> defaultGender =
        Arrays.asList(
            new DemographicItemDTO("male", "Male", ""),
            new DemographicItemDTO("female", "Female", ""),
            new DemographicItemDTO("other", "Other", ""));

    // Create default income demographics
    List<DemographicItemDTO> defaultIncome =
        Arrays.asList(
            new DemographicItemDTO("low", "Low income", "<30,000"),
            new DemographicItemDTO("lower_middle", "Lower-middle income", "30,000–50,000"),
            new DemographicItemDTO("middle", "Middle income", "50,000–100,000"),
            new DemographicItemDTO("upper_middle", "Upper-middle income", "100,000–150,000"),
            new DemographicItemDTO("high", "High income", ">150,000"));

    // Create default interests demographics
    List<DemographicItemDTO> defaultInterests =
        Arrays.asList(new DemographicItemDTO("Sports & Fitness", "Sports & Fitness", ""));

    // Create default behavior demographics
    List<DemographicItemDTO> defaultBehavior =
        Arrays.asList(
            new DemographicItemDTO(
                "Commuters", "Commuters", "People traveling to or from work during peak hours"),
            new DemographicItemDTO(
                "Shoppers", "Shoppers", "Active shoppers in retail environments"));

    // Create default venues
    VenueItemDTO transit = new VenueItemDTO();
    transit.setEnumerationId(1);
    transit.setTier(1);
    transit.setName("Transit");
    transit.setDefinition("Transportation & mobility venues");
    transit.setStringValue("transit");
    transit.setChildren(Collections.emptyList());

    List<VenueItemDTO> defaultVenues = Arrays.asList(transit);

    return new DemographicsGroupedResponseDTO(
        defaultAge, defaultGender, defaultIncome, defaultInterests, defaultBehavior, defaultVenues);
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    org.mockito.Mockito.reset(demographicsRepository, venuesService, defaultConfigurationService);
  }

  @Test
  void getGroupedDemographics_ShouldReturnGroupedDemographics() {
    // Given
    DemographicsGroupedResponseDTO defaultDemographics = createDefaultDemographics();
    when(demographicsRepository.findAll()).thenReturn(mockDemographics);
    when(venuesService.getHierarchicalVenues()).thenReturn(mockVenues);
    when(defaultConfigurationService.getDefaultDemographics()).thenReturn(defaultDemographics);

    // When
    DemographicsGroupedResponseDTO result = configService.getGroupedDemographics(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).hasSize(1);
    assertThat(result.getGender()).hasSize(1);
    assertThat(result.getIncome()).hasSize(1);
    assertThat(result.getInterests()).hasSize(1);
    assertThat(result.getBehavior()).hasSize(1);
    assertThat(result.getVenues()).hasSize(2);

    // Verify age demographics
    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("18-24");
    assertThat(result.getAge().get(0).getName()).isEqualTo("18-24 years");
    assertThat(result.getAge().get(0).getDescription()).isEqualTo("Young adults");

    // Verify gender demographics
    assertThat(result.getGender().get(0).getDemoKey()).isEqualTo("male");
    assertThat(result.getGender().get(0).getName()).isEqualTo("Male");
    assertThat(result.getGender().get(0).getDescription()).isEqualTo("Male gender");

    // Verify income demographics
    assertThat(result.getIncome().get(0).getDemoKey()).isEqualTo("high");
    assertThat(result.getIncome().get(0).getName()).isEqualTo("High Income");
    assertThat(result.getIncome().get(0).getDescription()).isEqualTo("High income bracket");

    // Verify interests demographics
    assertThat(result.getInterests().get(0).getDemoKey()).isEqualTo("sports");
    assertThat(result.getInterests().get(0).getName()).isEqualTo("Sports");
    assertThat(result.getInterests().get(0).getDescription()).isEqualTo("Sports interest");

    // Verify behavior demographics
    assertThat(result.getBehavior().get(0).getDemoKey()).isEqualTo("online_shopper");
    assertThat(result.getBehavior().get(0).getName()).isEqualTo("Online Shopper");
    assertThat(result.getBehavior().get(0).getDescription()).isEqualTo("Frequently shops online");

    // Verify venues
    assertThat(result.getVenues().get(0).getName()).isEqualTo("Shopping Mall");
    assertThat(result.getVenues().get(1).getName()).isEqualTo("Restaurant");

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getGroupedDemographics_WithEmptyData_ShouldReturnDefaultData() {
    // Given
    when(demographicsRepository.findAll()).thenReturn(Collections.emptyList());
    when(venuesService.getHierarchicalVenues()).thenReturn(Collections.emptyList());

    // Create a real DefaultConfigurationService instance for this test
    DefaultConfigurationService realDefaultService = new DefaultConfigurationService();
    when(defaultConfigurationService.getDefaultDemographics())
        .thenReturn(realDefaultService.getDefaultDemographics());
    when(defaultConfigurationService.getDefaultVenues())
        .thenReturn(realDefaultService.getDefaultVenues());

    // When
    DemographicsGroupedResponseDTO result = configService.getGroupedDemographics(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).hasSize(6); // Default age groups
    assertThat(result.getGender()).hasSize(3); // Default gender options
    assertThat(result.getIncome()).hasSize(5); // Default income brackets
    assertThat(result.getInterests()).hasSize(9); // Default interests (9 categories)
    assertThat(result.getBehavior()).hasSize(8); // Default behavior options (8 categories)
    assertThat(result.getVenues()).hasSize(11); // Default venues (11 main categories)

    // Verify default age demographics
    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("18_24");
    assertThat(result.getAge().get(0).getName()).isEqualTo("18-24 Years");
    assertThat(result.getAge().get(0).getDescription()).isEmpty();

    // Verify default gender demographics
    assertThat(result.getGender().get(0).getDemoKey()).isEqualTo("male");
    assertThat(result.getGender().get(0).getName()).isEqualTo("Male");

    // Verify default income demographics
    assertThat(result.getIncome().get(0).getDemoKey()).isEqualTo("low");
    assertThat(result.getIncome().get(0).getName()).isEqualTo("Low income");
    assertThat(result.getIncome().get(0).getDescription()).isEqualTo("<30,000");

    // Verify default venues
    assertThat(result.getVenues().get(0).getName()).isEqualTo("Transit");
    assertThat(result.getVenues().get(0).getEnumerationId()).isEqualTo(1);

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getGroupedDemographics_WithNullDemographics_ShouldReturnDefaultData() {
    // Given
    when(demographicsRepository.findAll()).thenReturn(null);
    when(venuesService.getHierarchicalVenues()).thenReturn(mockVenues);

    // Create a real DefaultConfigurationService instance for this test
    DefaultConfigurationService realDefaultService = new DefaultConfigurationService();
    when(defaultConfigurationService.getDefaultDemographics())
        .thenReturn(realDefaultService.getDefaultDemographics());

    // When
    DemographicsGroupedResponseDTO result = configService.getGroupedDemographics(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAge()).hasSize(6); // Default age groups
    assertThat(result.getGender()).hasSize(3); // Default gender options
    assertThat(result.getIncome()).hasSize(5); // Default income brackets
    assertThat(result.getInterests()).hasSize(9); // Default interests (9 categories)
    assertThat(result.getBehavior()).hasSize(8); // Default behavior options (8 categories)
    assertThat(result.getVenues()).hasSize(2); // Database venues (not defaults)

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getConfigurationData_ShouldReturnCompleteConfiguration() {
    // Given
    DemographicsGroupedResponseDTO defaultDemographics = createDefaultDemographics();
    when(demographicsRepository.findAll()).thenReturn(mockDemographics);
    when(venuesService.getHierarchicalVenues()).thenReturn(mockVenues);
    when(defaultConfigurationService.getDefaultDemographics()).thenReturn(defaultDemographics);

    // When
    Map<String, Object> result = configService.getConfigurationData(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).containsKey("demographics");
    assertThat(result).containsKey("campaign_status");

    // Verify demographics data
    DemographicsGroupedResponseDTO demographics =
        (DemographicsGroupedResponseDTO) result.get("demographics");
    assertThat(demographics).isNotNull();
    assertThat(demographics.getAge()).hasSize(1);
    assertThat(demographics.getGender()).hasSize(1);
    assertThat(demographics.getIncome()).hasSize(1);
    assertThat(demographics.getInterests()).hasSize(1);
    assertThat(demographics.getBehavior()).hasSize(1);
    assertThat(demographics.getVenues()).hasSize(2);

    // Verify campaign status data
    Campaign.Status[] campaignStatuses = (Campaign.Status[]) result.get("campaign_status");
    assertThat(campaignStatuses).isNotNull();
    assertThat(campaignStatuses).hasSize(Campaign.Status.values().length);
    assertThat(campaignStatuses).containsExactly(Campaign.Status.values());

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getConfigurationData_WithEmptyDemographics_ShouldReturnDefaultConfiguration() {
    // Given
    when(demographicsRepository.findAll()).thenReturn(Collections.emptyList());
    when(venuesService.getHierarchicalVenues()).thenReturn(Collections.emptyList());

    // Create a real DefaultConfigurationService instance for this test
    DefaultConfigurationService realDefaultService = new DefaultConfigurationService();
    when(defaultConfigurationService.getDefaultDemographics())
        .thenReturn(realDefaultService.getDefaultDemographics());
    when(defaultConfigurationService.getDefaultVenues())
        .thenReturn(realDefaultService.getDefaultVenues());

    // When
    Map<String, Object> result = configService.getConfigurationData(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).containsKey("demographics");
    assertThat(result).containsKey("campaign_status");

    // Verify default demographics data
    DemographicsGroupedResponseDTO demographics =
        (DemographicsGroupedResponseDTO) result.get("demographics");
    assertThat(demographics).isNotNull();
    assertThat(demographics.getAge()).hasSize(6); // Default age groups
    assertThat(demographics.getGender()).hasSize(3); // Default gender options
    assertThat(demographics.getIncome()).hasSize(5); // Default income brackets
    assertThat(demographics.getInterests()).hasSize(9); // Default interests (9 categories)
    assertThat(demographics.getBehavior()).hasSize(8); // Default behavior options (8 categories)
    assertThat(demographics.getVenues()).hasSize(11); // Default venues (11 main categories)

    // Verify campaign status data
    Campaign.Status[] campaignStatuses = (Campaign.Status[]) result.get("campaign_status");
    assertThat(campaignStatuses).isNotNull();
    assertThat(campaignStatuses).hasSize(Campaign.Status.values().length);

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getConfigurationData_WhenRepositoryThrowsException_ShouldPropagateException() {
    // Given
    RuntimeException repositoryException = new RuntimeException("Database error");
    when(demographicsRepository.findAll()).thenThrow(repositoryException);

    // When & Then
    try {
      configService.getConfigurationData(Locale.ENGLISH);
    } catch (RuntimeException e) {
      assertThat(e).isEqualTo(repositoryException);
    }

    verify(demographicsRepository).findAll();
  }

  @Test
  void getConfigurationData_WhenVenuesServiceThrowsException_ShouldPropagateException() {
    // Given
    DemographicsGroupedResponseDTO defaultDemographics = createDefaultDemographics();
    when(demographicsRepository.findAll()).thenReturn(mockDemographics);
    when(defaultConfigurationService.getDefaultDemographics()).thenReturn(defaultDemographics);
    RuntimeException venuesException = new RuntimeException("Venues service error");
    when(venuesService.getHierarchicalVenues()).thenThrow(venuesException);

    // When & Then
    try {
      configService.getConfigurationData(Locale.ENGLISH);
    } catch (RuntimeException e) {
      assertThat(e).isEqualTo(venuesException);
    }

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
  }

  @Test
  void getGroupedDemographics_WithPartialData_ShouldUseDefaultsForEmptyTypes() {
    // Given - Only age and gender data in database, others empty
    Demographics ageDemo = new Demographics();
    ageDemo.setDemoKey("25_30");
    ageDemo.setName("25-30 years");
    ageDemo.setDescription("Young adults");
    ageDemo.setDemoType(com.mw.planner.enums.DemographicsType.AGE);

    Demographics genderDemo = new Demographics();
    genderDemo.setDemoKey("female");
    genderDemo.setName("Female");
    genderDemo.setDescription("Female gender");
    genderDemo.setDemoType(com.mw.planner.enums.DemographicsType.GENDER);

    List<Demographics> partialDemographics = Arrays.asList(ageDemo, genderDemo);
    DemographicsGroupedResponseDTO defaultDemographics = createDefaultDemographics();

    when(demographicsRepository.findAll()).thenReturn(partialDemographics);
    when(venuesService.getHierarchicalVenues()).thenReturn(mockVenues);
    when(defaultConfigurationService.getDefaultDemographics()).thenReturn(defaultDemographics);

    // When
    DemographicsGroupedResponseDTO result = configService.getGroupedDemographics(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();

    // Database data should be used for age and gender
    assertThat(result.getAge()).hasSize(1);
    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("25_30");
    assertThat(result.getAge().get(0).getName()).isEqualTo("25-30 years");

    assertThat(result.getGender()).hasSize(1);
    assertThat(result.getGender().get(0).getDemoKey()).isEqualTo("female");
    assertThat(result.getGender().get(0).getName()).isEqualTo("Female");

    // Default data should be used for empty types
    assertThat(result.getIncome()).hasSize(5); // Default income brackets
    assertThat(result.getInterests()).hasSize(1); // Default interests
    assertThat(result.getBehavior()).hasSize(2); // Default behavior options

    // Verify default income demographics
    assertThat(result.getIncome().get(0).getDemoKey()).isEqualTo("low");
    assertThat(result.getIncome().get(0).getName()).isEqualTo("Low income");

    // Verify default interests demographics
    assertThat(result.getInterests().get(0).getDemoKey()).isEqualTo("Sports & Fitness");
    assertThat(result.getInterests().get(0).getName()).isEqualTo("Sports & Fitness");

    // Verify default behavior demographics
    assertThat(result.getBehavior().get(0).getDemoKey()).isEqualTo("Commuters");
    assertThat(result.getBehavior().get(0).getName()).isEqualTo("Commuters");

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
    verify(defaultConfigurationService).getDefaultDemographics();
  }

  @Test
  void getGroupedDemographics_WithOnlyAgeData_ShouldUseDefaultsForOtherTypes() {
    // Given - Only age data in database
    Demographics ageDemo = new Demographics();
    ageDemo.setDemoKey("35_40");
    ageDemo.setName("35-40 years");
    ageDemo.setDescription("Middle-aged adults");
    ageDemo.setDemoType(com.mw.planner.enums.DemographicsType.AGE);

    List<Demographics> ageOnlyDemographics = Arrays.asList(ageDemo);
    DemographicsGroupedResponseDTO defaultDemographics = createDefaultDemographics();

    when(demographicsRepository.findAll()).thenReturn(ageOnlyDemographics);
    when(venuesService.getHierarchicalVenues()).thenReturn(mockVenues);
    when(defaultConfigurationService.getDefaultDemographics()).thenReturn(defaultDemographics);

    // When
    DemographicsGroupedResponseDTO result = configService.getGroupedDemographics(Locale.ENGLISH);

    // Then
    assertThat(result).isNotNull();

    // Database data should be used for age
    assertThat(result.getAge()).hasSize(1);
    assertThat(result.getAge().get(0).getDemoKey()).isEqualTo("35_40");
    assertThat(result.getAge().get(0).getName()).isEqualTo("35-40 years");

    // Default data should be used for all other types
    assertThat(result.getGender()).hasSize(3); // Default gender options
    assertThat(result.getIncome()).hasSize(5); // Default income brackets
    assertThat(result.getInterests()).hasSize(1); // Default interests
    assertThat(result.getBehavior()).hasSize(2); // Default behavior options

    verify(demographicsRepository).findAll();
    verify(venuesService).getHierarchicalVenues();
    verify(defaultConfigurationService).getDefaultDemographics();
  }
}
