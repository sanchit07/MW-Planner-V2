package com.mw.planner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mw.planner.dto.CountryMarketDetailsDTO;
import com.mw.planner.dto.CountryRequestDTO;
import com.mw.planner.dto.CountryResponseDTO;
import com.mw.planner.dto.IamUserContext;
import com.mw.planner.exception.GlobalExceptionHandler;
import com.mw.planner.service.CountryService;
import com.mw.planner.service.DistrictService;
import com.mw.planner.service.MessageService;
import com.mw.planner.service.MetricsService;
import com.mw.planner.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CountryControllerTest {

  @Mock private CountryService countryService;
  @Mock private DistrictService districtService;
  @Mock private UserService userService;
  @Mock private MessageService messageService;
  @Mock private MetricsService metricsService;

  @InjectMocks private LocationController locationController;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private CountryResponseDTO testCountryResponse;
  private CountryRequestDTO testCountryRequest;
  private IamUserContext testUserContext;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(locationController)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageService, metricsService, userService))
            .build();
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    testCountryResponse = createTestCountryResponse();
    testCountryRequest = createTestCountryRequest();
    testUserContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .locale(Locale.ENGLISH)
            .build();

    // Mock the iamUserContextService.getIamUserContext() call that GlobalExceptionHandler makes
    when(userService.getIamUserContext()).thenReturn(testUserContext);
  }

  @AfterEach
  void tearDown() {
    // Reset mocks to clear any interactions for next test
    org.mockito.Mockito.reset(
        countryService, districtService, userService, messageService, metricsService);
  }

  // ========== Create Country Tests ==========

  @Test
  void createCountry_WithValidData_ShouldReturnCreatedCountry() throws Exception {
    // Given
    when(countryService.createCountry(any(CountryRequestDTO.class)))
        .thenReturn(testCountryResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCountryRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("country123"))
        .andExpect(jsonPath("$.data.countryId").value("US"))
        .andExpect(jsonPath("$.data.name").value("United States"))
        .andExpect(jsonPath("$.data.iso").value("USA"))
        .andExpect(jsonPath("$.data.active").value(true));
  }

  @Test
  void createCountry_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CountryRequestDTO invalidRequest = new CountryRequestDTO();
    invalidRequest.setName(""); // Empty name should fail validation

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createCountry_WithMissingRequiredFields_ShouldReturnBadRequest() throws Exception {
    // Given
    CountryRequestDTO incompleteRequest = new CountryRequestDTO();
    // Missing required fields

    // When & Then
    mockMvc
        .perform(
            post("/api/v1/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incompleteRequest)))
        .andExpect(status().isBadRequest());
  }

  // ========== Get Country by ID Tests ==========

  @Test
  void getCountryById_WhenCountryExists_ShouldReturnCountry() throws Exception {
    // Given
    when(countryService.getCountryById("country123")).thenReturn(testCountryResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/country123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("country123"))
        .andExpect(jsonPath("$.data.countryId").value("US"))
        .andExpect(jsonPath("$.data.name").value("United States"));
  }

  @Test
  void getCountryById_WhenCountryNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    when(countryService.getCountryById("nonexistent"))
        .thenThrow(new com.mw.planner.exception.country.CountryNotFoundException("nonexistent"));

    // When & Then
    mockMvc.perform(get("/api/v1/countries/nonexistent")).andExpect(status().isNotFound());
  }

  // ========== Get Country by Country ID Tests ==========

  @Test
  void getCountryByCountryId_WhenCountryExists_ShouldReturnCountry() throws Exception {
    // Given
    when(countryService.getCountryByCountryId("US")).thenReturn(testCountryResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/name/US"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("country123"))
        .andExpect(jsonPath("$.data.countryId").value("US"))
        .andExpect(jsonPath("$.data.name").value("United States"))
        .andExpect(jsonPath("$.data.iso").value("USA"))
        .andExpect(jsonPath("$.data.active").value(true));
  }

  @Test
  void getCountryByCountryId_WhenCountryNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    when(countryService.getCountryByCountryId("XX"))
        .thenThrow(new com.mw.planner.exception.country.CountryNotFoundException("XX"));

    // When & Then
    mockMvc.perform(get("/api/v1/countries/name/XX")).andExpect(status().isNotFound());
  }

  @Test
  void getCountryByCountryId_WithLowerCaseCountryId_ShouldReturnCountry() throws Exception {
    // Given
    when(countryService.getCountryByCountryId("ca")).thenReturn(testCountryResponse);

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/name/ca"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.countryId").value("US"));
  }

  @Test
  void getCountryByCountryId_WithSpecialCharacters_ShouldHandleGracefully() throws Exception {
    // Given
    when(countryService.getCountryByCountryId("US-1"))
        .thenThrow(new com.mw.planner.exception.country.CountryNotFoundException("US-1"));

    // When & Then
    mockMvc.perform(get("/api/v1/countries/name/US-1")).andExpect(status().isNotFound());
  }

  // ========== Update Country Tests ==========

  @Test
  void updateCountry_WithValidData_ShouldReturnUpdatedCountry() throws Exception {
    // Given
    when(countryService.updateCountry(eq("country123"), any(CountryRequestDTO.class)))
        .thenReturn(testCountryResponse);

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/country123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCountryRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value("country123"))
        .andExpect(jsonPath("$.data.countryId").value("US"))
        .andExpect(jsonPath("$.data.name").value("United States"));
  }

  @Test
  void updateCountry_WithInvalidData_ShouldReturnBadRequest() throws Exception {
    // Given
    CountryRequestDTO invalidRequest = new CountryRequestDTO();
    invalidRequest.setName(""); // Empty name should fail validation

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/country123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCountry_WhenCountryNotFound_ShouldReturnNotFound() throws Exception {
    // Given
    when(countryService.updateCountry(eq("nonexistent"), any(CountryRequestDTO.class)))
        .thenThrow(new com.mw.planner.exception.country.CountryNotFoundException("nonexistent"));

    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/nonexistent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCountryRequest)))
        .andExpect(status().isNotFound());
  }

  // ========== Get All Countries Tests ==========

  @Test
  void getAllCountries_WithValidPagination_ShouldReturnCountries() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<CountryResponseDTO> countriesPage =
        new PageImpl<>(java.util.List.of(testCountryResponse), pageable, 1);
    when(countryService.getAllCountries(any(Pageable.class))).thenReturn(countriesPage);

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value("country123"))
        .andExpect(jsonPath("$.data.content[0].countryId").value("US"))
        .andExpect(jsonPath("$.data.content[0].name").value("United States"));
  }

  @Test
  void getAllCountries_WithSorting_ShouldReturnSortedCountries() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<CountryResponseDTO> countriesPage =
        new PageImpl<>(java.util.List.of(testCountryResponse), pageable, 1);
    when(countryService.getAllCountries(any(Pageable.class))).thenReturn(countriesPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/countries")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray());
  }

  // ========== Get Market Details Tests ==========

  @Test
  void getMarketDetails_WhenUserHasCompany_ShouldReturnMarketDetails() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    CountryMarketDetailsDTO marketDetail = new CountryMarketDetailsDTO();
    marketDetail.setId("country123");
    marketDetail.setCountryId("US");
    marketDetail.setCountryName("United States");
    marketDetail.setPopulation(331000000L);
    marketDetail.setInventoryCount(5L);
    marketDetail.setImpressions(1000L);

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", null, null))
        .thenReturn(List.of(marketDetail));

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].countryId").value("US"))
        .andExpect(jsonPath("$.data[0].countryName").value("United States"))
        .andExpect(jsonPath("$.data[0].population").value(331000000))
        .andExpect(jsonPath("$.data[0].inventoryCount").value(5))
        .andExpect(jsonPath("$.data[0].impressions").value(1000));

    verify(countryService).getCountryMarketDetails("company123", null, null);
  }

  @Test
  void getMarketDetails_WithCountryIds_ShouldPassParsedIdsToService() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", List.of("c1", "c2", "c3"), null))
        .thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details").param("countryIds", "c1,c2,c3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(countryService).getCountryMarketDetails("company123", List.of("c1", "c2", "c3"), null);
  }

  @Test
  void getMarketDetails_WithBlankCountryIdEntries_ShouldDropBlanksAndTrim() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", List.of("c1", "c2"), null))
        .thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details").param("countryIds", "c1, ,c2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(countryService).getCountryMarketDetails("company123", List.of("c1", "c2"), null);
  }

  @Test
  void getMarketDetails_WithOnlyBlankCountryIds_ShouldFallBackToUnfiltered() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", null, null)).thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details").param("countryIds", " , "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(countryService).getCountryMarketDetails("company123", null, null);
  }

  @Test
  void getMarketDetails_WithCountryIso_ShouldPassParsedIsoCodesToService() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", null, List.of("US", "SG", "IN")))
        .thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details").param("countryIso", "US,SG,IN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(countryService).getCountryMarketDetails("company123", null, List.of("US", "SG", "IN"));
  }

  @Test
  void getMarketDetails_WithBlankCountryIsoEntries_ShouldDropBlanksAndTrim() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", null, List.of("US", "SG")))
        .thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details").param("countryIso", "US, ,SG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    verify(countryService).getCountryMarketDetails("company123", null, List.of("US", "SG"));
  }

  @Test
  void getMarketDetails_WithCountryIdsAndCountryIso_ShouldPassBothToService() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", List.of("c1"), List.of("US")))
        .thenReturn(List.of());

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/countries/market-details")
                .param("countryIds", "c1")
                .param("countryIso", "US"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    // Both are passed through verbatim; precedence is decided in the service layer
    verify(countryService).getCountryMarketDetails("company123", List.of("c1"), List.of("US"));
  }

  @Test
  void getMarketDetails_WhenUserHasNoCompany_ShouldReturnEmptyList() throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId(null)
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void getMarketDetails_WhenServiceThrowsException_ShouldReturnInternalServerError()
      throws Exception {
    // Given
    IamUserContext userContext =
        IamUserContext.builder()
            .id("user123")
            .companyId("company123")
            .username("testuser")
            .locale(Locale.ENGLISH)
            .build();

    when(userService.getIamUserContext()).thenReturn(userContext);
    when(countryService.getCountryMarketDetails("company123", null, null))
        .thenThrow(new RuntimeException("Service error"));

    // When & Then
    mockMvc
        .perform(get("/api/v1/countries/market-details"))
        .andExpect(status().isInternalServerError());
  }

  // ========== Edge Cases and Error Handling Tests ==========

  @Test
  void createCountry_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCountry_WithNullRequestBody_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/country123").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAllCountries_WithInvalidPagination_ShouldReturnBadRequest() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/api/v1/countries").param("page", "-1").param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAllCountries_WithInvalidSortDirection_ShouldUseDefaultSort() throws Exception {
    // Given
    Pageable pageable = PageRequest.of(0, 10);
    Page<CountryResponseDTO> countriesPage =
        new PageImpl<>(java.util.List.of(testCountryResponse), pageable, 1);
    when(countryService.getAllCountries(any(Pageable.class))).thenReturn(countriesPage);

    // When & Then
    mockMvc
        .perform(
            get("/api/v1/countries")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDir", "invalid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void getCountryById_WithEmptyId_ShouldReturnNotFound() throws Exception {
    // When & Then
    mockMvc.perform(get("/api/v1/countries/")).andExpect(status().isNotFound());
  }

  @Test
  void updateCountry_WithEmptyId_ShouldReturnNotFound() throws Exception {
    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCountryRequest)))
        .andExpect(status().isNotFound());
  }

  // ========== Content Type and Media Type Tests ==========

  @Test
  void createCountry_WithWrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {
    // When & Then
    mockMvc
        .perform(
            post("/api/v1/countries").contentType(MediaType.TEXT_PLAIN).content("invalid content"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void updateCountry_WithWrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {
    // When & Then
    mockMvc
        .perform(
            put("/api/v1/countries/country123")
                .contentType(MediaType.TEXT_PLAIN)
                .content("invalid content"))
        .andExpect(status().isUnsupportedMediaType());
  }

  // ========== Helper Methods ==========

  private CountryResponseDTO createTestCountryResponse() {
    CountryResponseDTO.Tax tax = new CountryResponseDTO.Tax();
    tax.setLabel("VAT");
    tax.setPercent(8.5);

    return CountryResponseDTO.builder()
        .id("country123")
        .countryId("US")
        .name("United States")
        .latitude(39.8283)
        .longitude(-98.5795)
        .zoom(4)
        .iso("USA")
        .active(true)
        .dialingCode("+1")
        .timezone("America/New_York")
        .tax(tax)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private CountryRequestDTO createTestCountryRequest() {
    CountryRequestDTO.Tax tax = new CountryRequestDTO.Tax();
    tax.setLabel("VAT");
    tax.setPercent(8.5);

    return CountryRequestDTO.builder()
        .countryId("US")
        .name("United States")
        .latitude(39.8283)
        .longitude(-98.5795)
        .zoom(4)
        .iso("USA")
        .active(true)
        .dialingCode("+1")
        .timezone("America/New_York")
        .tax(tax)
        .build();
  }
}
